package tests;
import extension.tools.library.*;
import extension.tools.importutils.*;
import extension.tools.presetconfig.*;
import extension.tools.presetconfig.furni.*;
import extension.tools.presetconfig.wired.*;
import extension.tools.postconfig.*;
import extension.tools.preview.*;
import extension.tools.sharing.*;
import gearth.extensions.parsers.HPoint;
import gearth.protocol.HPacket;
import org.json.*;
import java.nio.file.*;
import java.util.*;
public final class PresetToolsTests {
 private static int checks;
 private static void check(boolean result,String message){if(!result)throw new AssertionError(message);checks++;}
 private static void rejects(Runnable action,String message){try{action.run();}catch(IllegalArgumentException|IllegalStateException e){checks++;return;}throw new AssertionError(message);}
 private static PresetConfig sample() {
  var a=new PresetFurni(7,"table",new HPoint(1,2,0),2,"1");a.setFurniName("private table");
  var b=new PresetFurni(8,"chair",new HPoint(3,2,0),0,"0");b.setFurniName("seat");
  var wall=new PresetWallFurni(7,"wall_lamp",new utils.WallPosition(1,2,3,4,'l',0),"0");wall.setFurniName("private wall");
  var wired=new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray()));
  return new PresetConfig(new ArrayList<>(List.of(a,b)),new ArrayList<>(List.of(wall)),wired,new ArrayList<>(),new ArrayList<>());
 }
 public static int run() throws Exception {
  checks=0;PresetConfig source=sample();String original=PresetComparison.canonical(source.toJsonObject());
  PresetConfig copy=PresetReplacements.apply(source,Map.of(new PresetReplacements.Key(false,"table"),"chair",new PresetReplacements.Key(false,"chair"),"plant"));
  check(copy.getFurniture().get(0).getClassName().equals("chair") && copy.getFurniture().get(1).getClassName().equals("plant"),"replacement map is simultaneous, without cascading A->B->C");
  check(copy.getFurniture().get(0).getFurniId()==7 && copy.getFurniture().get(0).getState()==null,"replacement keeps target IDs but clears incompatible furniture state");
  check(copy.getWallFurniture().get(0).getClassName().equals("wall_lamp"),"floor replacement cannot cross wall namespace");
  check(PresetComparison.canonical(source.toJsonObject()).equals(original),"replacement never modifies source preset");
  copy=PresetReplacements.apply(source,Map.of(new PresetReplacements.Key(true,"wall_lamp"),"wall_light"));
  check(copy.getWallFurniture().get(0).getFurniId()==7 && copy.getFurniture().get(0).getClassName().equals("table"),"wall replacement preserves floor furniture with same ID");
  rejects(()->PresetReplacements.apply(source,Map.of(new PresetReplacements.Key(false,"table"),"wf_act_test")),"ordinary furniture cannot become WIRED with an incompatible codec");
  source.setFurnitureLabels(List.of(new PresetFurniLabel(7,"label",0,0,false)));
  rejects(()->PresetReplacements.apply(source,Map.of(new PresetReplacements.Key(false,"table"),"plant")),"configured chest replacement must be blocked");
  source.setFurnitureLabels(List.of());
  check(PresetComparison.compare(source,source).contains("No differences"),"identical preset comparison");
  String changes=PresetComparison.compare(source,copy);
  check(changes.contains("wallFurni[id=7].className") && changes.contains("CHANGED"),"comparison identifies wall change without confusing same floor ID");
  PresetConfig changed=new PresetConfig(source.toJsonObject());changed.getFurniture().remove(0);var extra=new PresetFurni(99,"extra",new HPoint(0,0),0,null);extra.setFurniName("extra");changed.getFurniture().add(extra);
  changes=PresetComparison.compare(source,changed);check(changes.contains("REMOVED furni[id=7]") && changes.contains("ADDED furni[id=99]"),"comparison identifies additions and removals");
  check(PresetComparison.canonical(new JSONObject("{\"a\":1,\"b\":2}")).equals(PresetComparison.canonical(new JSONObject("{\"b\":2,\"a\":1}"))),"JSON key order cannot create false differences");
  source.setMetadata(new PresetMetadata("private title","private description",""));
  var imageBytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",imageBytes);String image=Base64.getEncoder().encodeToString(imageBytes.toByteArray());
  for(int bits=0;bits<8;bits++) {
   PresetConfig shared=SharePrivacy.prepare(source,"name","description",image,(bits&1)!=0,(bits&2)!=0,(bits&4)!=0);
   PresetConfig roundtrip=new PresetConfig(shared.toJsonObject());
   check(bits==0?roundtrip.getMetadata()==null:roundtrip.getMetadata()!=null,"privacy switches round trip "+bits);
   JSONObject metadata=shared.toJsonObject().optJSONObject("metadata");
   check((bits&1)!=0?metadata.has("name"):metadata==null || !metadata.has("name"),"name excluded from exact JSON "+bits);
   check((bits&2)!=0?metadata.has("description"):metadata==null || !metadata.has("description"),"description excluded from exact JSON "+bits);
   check((bits&4)!=0?metadata.has("imagePng"):metadata==null || !metadata.has("imagePng"),"image excluded from exact JSON "+bits);
   check(shared.getFurniture().size()==source.getFurniture().size() && shared.getFurniture().get(0).getFurniName().equals("private table"),"gameplay fields retained for review "+bits);
  }
  check(source.getMetadata().name().equals("private title"),"privacy export never mutates source");
  var model=new PresetPreviewModel(source,new PostConfig(),c->new PresetPreviewModel.Footprint(2,1));
  var audit=PlacementAudit.inspect(model,10,10,(x,y)->x!=11,List.of(new PlacementAudit.Rect(11,12,1,1)));
  check(audit.issues().containsKey(7) && audit.summary().contains("outside floor"),"rotated footprint checks all occupied and invalid floor cells");
  audit=PlacementAudit.inspect(model,120,120,(x,y)->true,List.of());check(audit.issues().isEmpty(),"clear placement has no room overlaps");
  audit=PlacementAudit.inspect(model,129,129,(x,y)->true,List.of());check(audit.issues().containsKey(7),"room bounds are enforced before indexing occupancy map");
  PresetPreviewSession session=new PresetPreviewSession(source,new PostConfig(),42,new HPoint(5,6));session.chooseRoot(10,11);check(session.root().getX()==10 && session.root().getY()==11,"chosen placement reaches confirmed session");
  session.chooseRoot(null,null);check(session.root()==null,"deselecting explicit origin returns to in-game placement selection");
  rejects(()->session.chooseRoot(-1,0),"negative origin rejected");session.cancel();rejects(()->session.chooseRoot(1,2),"closed preview cannot change placement");
  ImportUndoJournal journal=new ImportUndoJournal();journal.begin(42,5,Set.of(1),Set.of(2));
  check(!journal.record(new ImportUndoJournal.Entry(false,1,10,5,false)),"undo excludes existing floor furniture");
  check(!journal.record(new ImportUndoJournal.Entry(true,2,10,5,false)),"undo excludes existing wall furniture");
  var floor=new ImportUndoJournal.Entry(false,444,10,5,false);var wall=new ImportUndoJournal.Entry(true,444,11,5,false);
  check(journal.record(floor) && journal.record(wall) && journal.entries().size()==2,"undo stores floor/wall IDs separately");
  check(!journal.record(new ImportUndoJournal.Entry(false,888,10,6,true)),"BC belonging to another user is never eligible");
  check(journal.eligible(floor,42,5,10,5,true),"valid recorded placement eligible for recovery");
  check(!journal.eligible(floor,43,5,10,5,true) && !journal.eligible(floor,42,6,10,5,true),"undo rejects changed room or session identity");
  check(!journal.eligible(floor,42,5,99,5,true) && !journal.eligible(floor,42,5,10,6,true) && !journal.eligible(floor,42,5,10,5,false),"undo rejects changed type, owner or aborted operation");
  byte[] f=ImportUndoJournal.pickup(floor).toBytes(),w=ImportUndoJournal.pickup(wall).toBytes();
  check(Arrays.equals(Arrays.copyOfRange(f,6,f.length),new byte[]{0,0,0,2,0,0,1,(byte)188}),"native pickup floor payload is category 2 then ID");
  check(Arrays.equals(Arrays.copyOfRange(w,6,w.length),new byte[]{0,0,0,1,0,0,1,(byte)188}),"native pickup wall payload is category 1 then ID");
  journal.removed(floor);check(journal.entries().equals(List.of(wall)),"acknowledged floor removal preserves wall entry with same ID");
  String dataDir=System.getProperty("gpresets.dataDir");Path temp=Files.createTempDirectory(Path.of("target"),"gpresets-history-");System.setProperty("gpresets.dataDir",temp.toString());
  try {
   check(PresetConfigUtils.savePreset("sample",source),"initial save snapshots revision");
   check(PresetHistory.list("sample").size()==1,"initial revision recorded");
   check(PresetConfigUtils.savePreset("sample",source) && PresetHistory.list("sample").size()==1,"identical save deduplicates history");
   check(PresetConfigUtils.savePreset("sample",changed),"modified preset saves");List<String> versions=PresetHistory.list("sample");check(versions.size()==2,"old and new versions retained across immediate saves");
   check(PresetComparison.canonical(PresetHistory.read("sample",versions.get(0)).toJsonObject()).equals(PresetComparison.canonical(changed.toJsonObject())),"latest revision ordering is stable within one millisecond");
   PresetConfig prior=PresetHistory.read("sample",versions.get(1));check(PresetConfigUtils.savePreset("sample",prior),"restoring earlier revision succeeds");
   check(PresetHistory.list("sample").size()==3 && PresetComparison.canonical(PresetConfigUtils.loadPreset("sample").toJsonObject()).equals(PresetComparison.canonical(source.toJsonObject())),"restore preserves replaced version and restores old content");
   PresetConfigUtils.moveToFolder("sample","organized");check(PresetHistory.list("organized/sample").size()==3 && PresetConfigUtils.loadPreset("organized/sample")!=null,"moving a preset keeps history available");
   try{PresetHistory.read("organized/sample","../outside.json");throw new AssertionError("history traversal accepted");}catch(IllegalArgumentException expected){checks++;}
   check(PresetConfigUtils.listPresets().equals(List.of("organized/sample")),"private revisions never appear in preset library");
  }finally{if(dataDir==null)System.clearProperty("gpresets.dataDir");else System.setProperty("gpresets.dataDir",dataDir);}
  System.out.println("PASS: "+checks+" preset tools assertions (undo guards/native payload, privacy, history, comparison, substitutions, placement)");return checks;
 }
}
