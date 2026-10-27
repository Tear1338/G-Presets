package tests;
import extension.tools.importutils.*;
import extension.tools.library.PresetComparison;
import extension.tools.postconfig.ItemSource;
import extension.tools.presetconfig.*;
import extension.tools.presetconfig.furni.*;
import extension.tools.presetconfig.wired.*;
import extension.tools.presetconfig.binding.*;
import gearth.extensions.parsers.HPoint;
import org.json.*;
import java.util.*;

public final class PartialImportTests {
 private static int checks;
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
 private static JSONObject box(int id,List<Integer> items,List<Integer> second,List<String> variables){return new JSONObject().put("wiredId",id).put("options",new JSONArray()).put("config","").put("items",items).put("secondItems",second).put("variableIds",variables).put("delay",0);}
 public static int run() {
  checks=0;
  for(var source:ItemSource.values()) for(boolean bc:List.of(false,true))for(boolean inv:List.of(false,true)) {
   boolean expected=switch(source){case ONLY_BC->true;case ONLY_INVENTORY->false;case PREFER_BC->bc;case PREFER_INVENTORY->!inv && bc;};
   check(source.usesBC(bc,inv)==expected,"source priority "+source+" bc="+bc+" inventory="+inv);
  }
  var wired=new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray()));
  List<PresetFurni> furni=new ArrayList<>();
  for(int id:List.of(1,2,3,4,5,6,7,8,9,220,331,357)){var f=new PresetFurni(id,id>=220?"bw_tele":"item",new HPoint(0,0),0,null);f.setFurniName("test"+id);furni.add(f);}
  var wall=new PresetWallFurni(1,"poster",new utils.WallPosition(0,0,0,0,'l',0),"0");wall.setFurniName("wall");
  var preset=new PresetConfig(furni,new ArrayList<>(List.of(wall)),wired,new ArrayList<>(),new ArrayList<>());
  wired.getEffects().add(new PresetWiredEffect(box(1,List.of(2),List.of(),List.of())));
  wired.getEffects().add(new PresetWiredEffect(box(3,List.of(220),List.of(),List.of())));
  wired.getEffects().add(new PresetWiredEffect(box(4,List.of(),List.of(331),List.of())));
  wired.getEffects().add(new PresetWiredEffect(box(5,List.of(),List.of(),List.of("v2"))));
  wired.getEffects().add(new PresetWiredEffect(box(6,List.of(),List.of(),List.of("v1")).put("externalVariableIds",List.of("v1"))));
  wired.getVariables().add(new PresetWiredVariable(box(357,List.of(),List.of(),List.of()).put("variableId","v1")));
  wired.getVariables().add(new PresetWiredVariable(box(7,List.of(),List.of(),List.of("v1")).put("variableId","v2")));
  wired.getVariablesMap().put("missing","v1");wired.getVariablesMap().put("dependent","v2");
  var values=new LinkedHashMap<String,Integer>();values.put("~100",1);values.put("v1",99);values.put("~103",220);
  preset.setObjectVariables(new ArrayList<>(List.of(new PresetObjectVariables(0,2,values).withFurnitureReference("~103"),new PresetObjectVariables(0,220,Map.of("~100",1)))));
  preset.setFurnitureLabels(new ArrayList<>(List.of(new PresetFurniLabel(2,"keep",0,0,false),new PresetFurniLabel(331,"skip",0,0,false))));
  wired.getEffects().add(new PresetWiredEffect(box(8,List.of(-1),List.of(),List.of())));
  wired.getEffects().add(new PresetWiredEffect(box(9,List.of(),List.of(),List.of())));
  preset.getBindings().add(new PresetWiredFurniBinding(357,9,new HPoint(1,1),0,null,null));
  preset.getFurniture().get(1).setVariables(Map.of("missing",99,"valid",10));
  String before=PresetComparison.canonical(preset.toJsonObject());Map<Integer,Integer> ids=new HashMap<>();for(int i=1;i<=9;i++)ids.put(i,100+i);
  var plan=PartialImportPlan.create(preset,ids,Map.of());var effective=plan.effective();
  check(effective.getFurniture().size()==12 && effective.getWallFurniture().size()==1,"full requested furniture retained for placement and reporting");
  check(plan.omissions().stream().filter(o->o.category().equals("Floor")).count()==3,"all three unavailable teleporters identified");
  check(plan.omissions().stream().anyMatch(o->o.category().equals("Wall") && o.presetId()==1),"floor mapping cannot hide missing wall with same source ID");
  check(effective.getPresetWireds().getEffects().stream().map(PresetWiredBase::getWiredId).toList().equals(List.of(1,6)),"independent and explicitly external WIRED continue; missing selections and variable consumers skipped");
  check(effective.getPresetWireds().getVariables().isEmpty() && effective.getPresetWireds().getVariablesMap().isEmpty(),"missing variable definitions propagate through dependent definitions");
  check(plan.skippedWired().keySet().containsAll(List.of(3,4,5,7,8,9,357)),"report records each skipped WIRED");
  check(effective.getObjectVariables().size()==1 && effective.getObjectVariables().get(0).values.equals(Map.of("~100",1)),"valid holder values retained while missing variable and teleporter references skipped");
  check(effective.getObjectVariables().get(0).remap(ids,Map.of()).values.equals(Map.of("~100",1)),"remaining holder configuration remaps without missing-dependency failure");
  check(effective.getFurnitureLabels().size()==1 && effective.getFurnitureLabels().get(0).furniId==2,"available furniture labels finish");
  check(PresetComparison.canonical(preset.toJsonObject()).equals(before),"partial planning does not mutate source preset");
  check(effective.getFurniture().get(1).getVariables().equals(Map.of("valid",10)),"legacy variable names cannot fall back to an unavailable definition");
  check(effective.getBindings().isEmpty(),"WIRED with missing snapshot target is skipped before moving snapshot furniture");
  var full=new HashMap<>(ids);full.put(220,320);full.put(331,431);full.put(357,457);full.put(-1,-1001);
  var complete=PartialImportPlan.create(preset,full,Map.of(1,1001));
  check(complete.omissions().isEmpty() && complete.effective().getPresetWireds().getAll().size()==9,"fully supplied preset keeps all configuration");
  check(effective.getPresetWireds().getEffects().get(0).remap(ids,Map.of()).getItems().equals(List.of(102)),"remaining WIRED uses actual destination room IDs");
  return checks;
 }
}
