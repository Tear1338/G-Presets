package tests;
import extension.GPresets;
import extension.logger.Logger;
import extension.tools.GPresetImporter;
import extension.tools.importutils.ImportReport;
import extension.tools.importutils.ImportUndoJournal;
import extension.tools.importutils.AvailabilityAudit;
import extension.tools.importutils.WiredDiagnostics;
import extension.tools.preview.PresetPreviewSession;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.wired.PresetWireds;
import game.FloorState;
import gearth.extensions.ExtensionBase;
import gearth.protocol.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Exercise real importer interception with a disconnected, non-network fake extension. */
public final class PreviewImportFlow {
   private static void require(boolean condition,String text) { if(!condition)throw new AssertionError(text); }
   private static final class FakeExtension extends GPresets {
      final Map<String,ExtensionBase.MessageListener> callbacks=new HashMap<>();
      java.util.function.Function<HPacket,Boolean> sendHook;
      final extension.tools.GPresetExporter exporter=new extension.tools.GPresetExporter(this);
      @Override public extension.tools.GPresetExporter getExporter(){return exporter;}
      int serverPackets;int room=42;boolean roomLoaded;Runnable onPickup;JSONObject lastReport;
      final Map<Integer,gearth.extensions.parsers.HFloorItem> floorItems=new HashMap<>();
      final Map<Integer,gearth.extensions.parsers.HWallItem> wallItems=new HashMap<>();
      final List<String> pickups=new ArrayList<>();
      final game.RoomPermissions permissions=new game.RoomPermissions(this,new Logger(),()->{}){@Override public boolean canMoveFurni(){return true;}};
      @Override public game.RoomPermissions getPermissions(){return permissions;}
      @Override public int getOwnUserId(){return 5;}
      PresetPreviewSession session;BooleanSupplier confirm,ready;Runnable cancel;
      final FloorState floor=new FloorState(this,new Logger(),()->{},()->{}) {
         @Override public java.util.List<gearth.extensions.parsers.HFloorItem> getFurniOnTile(int x,int y){return floorItems.values().stream().filter(f->f.getTile().getX()==x && f.getTile().getY()==y).toList();}
         @Override public int getRoomId() { return room; }
         @Override public boolean inRoom() { return roomLoaded; }
         @Override public java.util.List<gearth.extensions.parsers.HFloorItem> getItems(){return new ArrayList<>(floorItems.values());}
         @Override public gearth.extensions.parsers.HFloorItem furniFromId(int id){return floorItems.get(id);}
         @Override public gearth.extensions.parsers.HWallItem wallItemFromId(int id){return wallItems.get(id);}
      };
      @Override public void intercept(HMessage.Direction direction,String name,ExtensionBase.MessageListener listener) { callbacks.put(direction+":"+name,listener); }
      @Override public FloorState getFloorState() { return floor; }
      @Override public boolean sendToServer(HPacket packet) {
         serverPackets++;if(sendHook!=null)return sendHook.apply(packet);HPacket decoded=new HPacket(packet);int category=decoded.readInteger(),id=decoded.readInteger();pickups.add(category+":"+id);
         if(category==1)wallItems.remove(id);else floorItems.remove(id);if(onPickup!=null)onPickup.run();return true;
      }
      @Override public void sendVisualChatInfo(String text) { }
      @Override public void showImportReport(JSONObject report){lastReport=report;}
      @Override public void updatePreviewAvailability() { }
      @Override public boolean previewBeforeImport() { return true; }
      @Override public void showImportPreview(PresetPreviewSession session,BooleanSupplier ready,BooleanSupplier confirm,Runnable cancel) { this.session=session;this.ready=ready;this.confirm=confirm;this.cancel=cancel; }
      void chat(String text) {
         HMessage message=new HMessage(new HPacket(1,text),HMessage.Direction.TOSERVER,0);
         callbacks.get("TOSERVER:Chat").act(message);
         require(message.isBlocked(),"preview/import command must stay local");
      }
   }
   public static void run() {
      FakeExtension extension=new FakeExtension();
      GPresetImporter importer=new GPresetImporter(extension) { @Override public boolean isReady() { return true; } };
      PresetConfig preset=new PresetConfig(new ArrayList<>(),new ArrayList<>(),new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray())),new ArrayList<>(),new ArrayList<>());
      var floorItem=new extension.tools.presetconfig.furni.PresetFurni(7,"table",new gearth.extensions.parsers.HPoint(1,2,0),0,null);floorItem.setFurniName("table[0]");
      var wallItem=new extension.tools.presetconfig.furni.PresetWallFurni(7,"poster",new utils.WallPosition(1,2,0,0,'l',0),"1");wallItem.setFurniName("poster[0]");
      PresetConfig reporting=new PresetConfig(preset.toJsonObject());reporting.getFurniture().add(floorItem);reporting.getWallFurniture().add(wallItem);
      JSONObject report=ImportReport.capture("Test",reporting,Map.of(7,100),Map.of(7,200),Set.of(),extension.floor,new gearth.extensions.parsers.HPoint(0,0),0,42);
      require(report.getJSONArray("items").length()==2,"report tracks both floor and wall namespaces");
      require(report.getJSONArray("items").getJSONObject(0).getString("status").equals("ROOM_NOT_READY"),"unloaded room reported without dereferencing missing state");
      require(report.getJSONArray("items").getJSONObject(0).getInt("realId")==100 && report.getJSONArray("items").getJSONObject(1).getInt("realId")==200,"report preserves distinct mappings for identical source IDs");
      AvailabilityAudit.inspect(reporting,new extension.tools.postconfig.PostConfig(),null,null,null,false);
      WiredDiagnostics.inspect(reporting);
      require(extension.serverPackets==0,"preflight and report capture send no game packets");

      importer.setPresetConfig(preset);
      extension.chat(":ip 12,13");
      require(importer.getState()==GPresetImporter.BuildingImportState.AWAITING_PREVIEW,"ip must stop before preparation");
      require(extension.session.rootX==12 && extension.session.rootY==13,"explicit root forwarded to preview");
      require(extension.serverPackets==0,"opening preview must send no game packets");
      extension.cancel.run();
      require(importer.getState()==GPresetImporter.BuildingImportState.NONE && !extension.confirm.getAsBoolean(),"closing preview cancels pending import");
      extension.chat(":pp");require(extension.serverPackets==0,"preview-only command must be local");
      extension.chat(":a");
      require(importer.getState()==GPresetImporter.BuildingImportState.NONE && extension.session.isClosed(),"abort invalidates preview");
      require(!extension.confirm.getAsBoolean() && extension.serverPackets==0,"late confirmation after abort must not start import");
      extension.chat(":ip");extension.room=43;
      require(!extension.ready.getAsBoolean() && !extension.confirm.getAsBoolean(),"room change blocks actual importer confirmation");
      require(extension.serverPackets==0,"room-change rejection must send no packets");extension.cancel.run();
      extension.chat(":ip");importer.setPresetConfig(new PresetConfig(preset.toJsonObject()));
      require(!extension.confirm.getAsBoolean() && extension.serverPackets==0,"selection change blocks actual importer confirmation");extension.cancel.run();
      importer.setPresetConfig(preset);
      require(importer.applySessionReplacements(preset,new PresetConfig(preset.toJsonObject())),"session substitutions applied while idle");
      require(!importer.applySessionReplacements(preset,preset),"stale substitution dialog cannot replace a new selection");
      extension.chat(":pp");PresetConfig active=importer.getPresetConfig();
      require(!importer.applySessionReplacements(active,preset),"substitutions cannot change an active preview/import");extension.cancel.run();
      undoWorker(extension,importer);
      incompletePolicy(extension,importer);
      legacyLinkContinuation(extension,importer);
      rejectedWiredContinuation();
      placementAcknowledgements();
      availabilityCounts();
      finalPositionCheck();
      System.out.println("PASS: real import command preview gate, cancel, abort and stale contexts send no game packets");
   }
   private static void undoWorker(FakeExtension extension,GPresetImporter importer) {
      try {
         var field=GPresetImporter.class.getDeclaredField("state");field.setAccessible(true);
         var method=GPresetImporter.class.getDeclaredMethod("undoLastImport");method.setAccessible(true);
         extension.roomLoaded=true;extension.room=42;
         var floorItem=new gearth.extensions.parsers.HFloorItem(new HPacket(1,101,10,1,2,0,"0","1",0,0,"0",-1,0,5));
         var existing=new gearth.extensions.parsers.HFloorItem(new HPacket(1,1,10,1,2,0,"0","1",0,0,"0",-1,0,5));
         var wallItem=new gearth.extensions.parsers.HWallItem(new HPacket(1,"101",11,":w=1,2 l=0,0 l","0",-1,0,5));
         extension.floorItems.put(101,floorItem);extension.floorItems.put(1,existing);extension.wallItems.put(101,wallItem);
         var journal=importer.getUndoJournal();journal.begin(42,5,Set.of(1),Set.of());
         journal.record(new ImportUndoJournal.Entry(false,101,10,5,false));
         journal.record(new ImportUndoJournal.Entry(true,101,11,5,false));
         field.set(importer,GPresetImporter.BuildingImportState.UNDOING);method.invoke(importer);
         require(extension.pickups.equals(List.of("1:101","2:101")),"actual undo worker routes distinct floor/wall native categories");
         require(extension.floorItems.containsKey(1) && journal.entries().isEmpty(),"actual undo worker preserves existing furniture and acknowledges individual removals");
         extension.floorItems.put(101,floorItem);journal.begin(42,5,Set.of(1),Set.of());journal.record(new ImportUndoJournal.Entry(false,101,10,5,false));
         extension.room=43;int sent=extension.serverPackets;field.set(importer,GPresetImporter.BuildingImportState.UNDOING);method.invoke(importer);
         require(extension.serverPackets==sent && journal.entries().size()==1,"actual undo worker rejects changed room without losing retry entry");
         extension.room=42;floorItem.setOwnerId(6);field.set(importer,GPresetImporter.BuildingImportState.UNDOING);method.invoke(importer);
         require(extension.serverPackets==sent && journal.entries().size()==1,"actual undo worker rechecks current furniture owner");floorItem.setOwnerId(5);
         journal.record(new ImportUndoJournal.Entry(true,101,11,5,false));extension.wallItems.put(101,wallItem);
         extension.onPickup=()->importer.reset();field.set(importer,GPresetImporter.BuildingImportState.UNDOING);method.invoke(importer);
         require(extension.serverPackets==sent+1 && extension.floorItems.containsKey(101),"abort during real undo worker stops before the next pickup");
         extension.onPickup=null;extension.roomLoaded=false;
         System.out.println("PASS: real undo worker native routing, existing-item preservation, room/owner guards, acknowledgement and abort");
      }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   }

   private static void put(GPresetImporter importer,String name,Object value) throws ReflectiveOperationException {
      var f=GPresetImporter.class.getDeclaredField(name);f.setAccessible(true);f.set(importer,value);
   }
   private static void incompletePolicy(FakeExtension extension,GPresetImporter importer) {
      try {
         var prepare=GPresetImporter.class.getDeclaredMethod("preparePlacedConfigurations");prepare.setAccessible(true);
         var finish=GPresetImporter.class.getDeclaredMethod("finishImport");finish.setAccessible(true);
         var wired=new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray()));
         var available=new extension.tools.presetconfig.furni.PresetFurni(1,"table",new gearth.extensions.parsers.HPoint(1,2),0,null);available.setFurniName("table");
         var missing=new extension.tools.presetconfig.furni.PresetFurni(220,"bw_tele",new gearth.extensions.parsers.HPoint(3,2),0,null);missing.setFurniName("teleporter");
         var preset=new PresetConfig(new ArrayList<>(List.of(available,missing)),new ArrayList<>(),wired,new ArrayList<>(),new ArrayList<>());
         put(importer,"workingPresetConfig",preset);put(importer,"realFurniIdMap",new HashMap<>(Map.of(1,101)));put(importer,"incompleteBuildAllowed",true);
         put(importer,"activeImportRoomId",42);put(importer,"state",GPresetImporter.BuildingImportState.SETUP_WIRED);extension.roomLoaded=true;
         int sent=extension.serverPackets;
         require((Boolean)prepare.invoke(importer),"actual importer continues after missing furniture when incomplete builds enabled");
         finish.invoke(importer);
         require(importer.getState()==GPresetImporter.BuildingImportState.NONE,"actual partial completion returns importer to idle");
         require(extension.lastReport.getString("outcome").equals("Import finished with omissions"),"actual completion cannot claim full success when items missing");
         require(extension.lastReport.getJSONArray("items").length()==2 && extension.lastReport.getJSONArray("omissions").length()==1,"actual completion retains original requested counts and missing item details");
         require(ImportReport.text(extension.lastReport).contains("Not placed: bw_tele"),"exportable text report includes omission reason");
         put(importer,"workingPresetConfig",preset);put(importer,"incompleteBuildAllowed",false);put(importer,"state",GPresetImporter.BuildingImportState.SETUP_WIRED);
         require(!(Boolean)prepare.invoke(importer),"strict mode still rejects missing furniture");
         require(extension.lastReport.getString("outcome").contains("Not placed: bw_tele"),"actual strict failure report retains the exact failure reason");
         require(extension.serverPackets==sent,"missing-item policy and completion never invent placement/configuration packets");
         put(importer,"workingPresetConfig",preset);put(importer,"realFurniIdMap",new HashMap<>(Map.of(1,101,220,2200)));put(importer,"incompleteBuildAllowed",true);
         require((Boolean)prepare.invoke(importer),"fully supplied subsequent import has no inherited omissions");finish.invoke(importer);
         require(extension.lastReport.getString("outcome").equals("Import finished"),"subsequent complete build resets previous partial outcome");
         extension.roomLoaded=false;
         System.out.println("PASS: real importer incomplete/strict policy, finish outcome, original totals, omission/error reasons and next-import isolation");
      }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   }

   private static void legacyLinkContinuation(FakeExtension extension,GPresetImporter importer) {
      try {
         var wired=new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray()));
         // Explicitly reused furniture is already mapped, and absent from the remaining placement list.
         var preset=new PresetConfig(new ArrayList<>(),new ArrayList<>(),wired,new ArrayList<>(),new ArrayList<>());
         preset.setRoomLinks(new ArrayList<>(List.of(new extension.tools.presetconfig.wired.PresetRoomLink(7,"420000001"))));
         put(importer,"workingPresetConfig",preset);put(importer,"realFurniIdMap",new HashMap<>(Map.of(7,101)));put(importer,"incompleteBuildAllowed",true);
         put(importer,"rootLocation",new gearth.extensions.parsers.HPoint(0,0));put(importer,"stackTileLocation",new gearth.extensions.parsers.HPoint(20,20));put(importer,"allAvailableStackTiles",new ArrayList<>());
         put(importer,"activeImportRoomId",42);put(importer,"state",GPresetImporter.BuildingImportState.SETUP_WIRED);extension.roomLoaded=true;
         int sent=extension.serverPackets;var setup=GPresetImporter.class.getDeclaredMethod("setupWired");setup.setAccessible(true);setup.invoke(importer);
         require(importer.getState()==GPresetImporter.BuildingImportState.NONE,"actual setup pipeline reaches final placement/completion after an unavailable legacy pairing");
         require(extension.serverPackets==sent,"legacy foreign pairing is never written to the hotel");
         require(extension.lastReport.getString("outcome").equals("Import finished with omissions"),"continuation does not claim legacy pairing was restored");
         require(extension.lastReport.getJSONObject("otherConfigurations").getInt("roomLinks")==1 && ImportReport.text(extension.lastReport).contains("native pairing is retained"),"final report preserves requested pairing and reason for its omission");
         require(preset.getRoomLinks().size()==1,"actual import leaves selected preset intact");extension.roomLoaded=false;
         System.out.println("PASS: actual WIRED setup-to-final-placement pipeline continues past an unavailable legacy room-linker pairing");
      }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   }

   private static JSONObject box(int id,String name,List<String> variables) {
      return new JSONObject().put("wiredId",id).put("options",List.of(0)).put("config",name).put("items",List.of()).put("variableIds",variables).put("delay",0);
   }
   private static void rejectedWiredContinuation() {
      try {
         for(boolean partial:List.of(true,false)) {
            FakeExtension e=new FakeExtension();GPresetImporter importer=new GPresetImporter(e);
            var wired=new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray()));
            // Reproduces the legacy export: shared source id differs from the room-local alias.
            wired.getVariables().add(new extension.tools.presetconfig.wired.PresetWiredVariable(box(149,"lives",List.of("9001")).put("variableId","9001").put("externalVariableIds",List.of("9001"))));
            wired.getVariablesMap().put("lives","9002");
            wired.getEffects().add(new extension.tools.presetconfig.wired.PresetWiredEffect(box(36,"dependent",List.of("9002"))));
            wired.getEffects().add(new extension.tools.presetconfig.wired.PresetWiredEffect(box(7,"independent",List.of())));
            var preset=new PresetConfig(new ArrayList<>(),new ArrayList<>(),wired,new ArrayList<>(),new ArrayList<>());
            put(importer,"workingPresetConfig",preset);put(importer,"realFurniIdMap",new HashMap<>(Map.of(149,1149,36,1036,7,1007)));put(importer,"incompleteBuildAllowed",partial);
            put(importer,"rootLocation",new gearth.extensions.parsers.HPoint(0,0));put(importer,"stackTileLocation",new gearth.extensions.parsers.HPoint(20,20));put(importer,"allAvailableStackTiles",new ArrayList<>());
            put(importer,"activeImportRoomId",42);put(importer,"state",GPresetImporter.BuildingImportState.SETUP_WIRED);e.roomLoaded=true;
            List<String> sent=new ArrayList<>();
            e.sendHook=packet->{
               sent.add(packet.packetIncompleteIdentifier());
               if("UpdateVariable".equals(packet.packetIncompleteIdentifier())) {
                  var parsed=new extension.tools.presetconfig.wired.PresetWiredVariable(new HPacket(packet));
                  require(parsed.getVariableIds().equals(List.of("9001")),"shared source identity remains intact");
                  e.callbacks.get("TOCLIENT:WiredValidationError").act(new HMessage(new HPacket(1,"shared_reference_unavailable"),HMessage.Direction.TOCLIENT,0));
               } else if("UpdateAction".equals(packet.packetIncompleteIdentifier())) {
                  require(new HPacket(packet).readInteger()==1007,"rejected alias consumer must never be sent");
                  e.callbacks.get("TOCLIENT:WiredSaveSuccess").act(new HMessage(new HPacket(1),HMessage.Direction.TOCLIENT,0));
               } else throw new AssertionError("Unexpected packet: "+packet.packetIncompleteIdentifier());
               return true;
            };
            var setup=GPresetImporter.class.getDeclaredMethod("setupWired");setup.setAccessible(true);setup.invoke(importer);
            require(importer.getState()==GPresetImporter.BuildingImportState.NONE,"rejected WIRED must not leave importer stuck");
            if(partial) {
               require(sent.equals(List.of("UpdateVariable","UpdateAction")),"partial flow saves independent WIRED and reaches movement/completion");
               require(e.lastReport.getString("outcome").equals("Import finished with omissions"),"rejected configurations never count as full success");
               var rows=e.lastReport.getJSONArray("wired");
               require(rows.getJSONObject(0).getString("status").equals("SKIPPED") && rows.getJSONObject(1).getString("status").equals("SKIPPED") && rows.getJSONObject(2).getString("status").equals("SAVE_ACKNOWLEDGED"),"report distinguishes rejected reference, dependent box and acknowledged save");
            } else {
               require(sent.equals(List.of("UpdateVariable")),"strict mode stops at rejection");
               require(e.lastReport.getString("outcome").contains("shared_reference_unavailable"),"native validation detail survives in failure report");
            }
            require(preset.getPresetWireds().getAll().size()==3,"source preset remains unchanged");
         }
         System.out.println("PASS: actual rejected-reference import continues through independent WIRED to completion; strict mode stops with native error detail");
      }catch(ReflectiveOperationException error){throw new AssertionError(error);}
   }
   private static void placementAcknowledgements() {
      try {
         FakeExtension e=new FakeExtension();GPresetImporter importer=new GPresetImporter(e);
         Map<Integer,Integer> mapped=new HashMap<>();Map<String,LinkedList<Integer>> pending=new HashMap<>();pending.put("1|2|10",new LinkedList<>(List.of(1,2)));
         put(importer,"realFurniIdMap",mapped);put(importer,"expectFurniDrops",pending);put(importer,"undoBaselineFloors",Set.of(99));put(importer,"state",GPresetImporter.BuildingImportState.ADD_FURNITURE);
         java.util.function.Consumer<Integer> added=id->e.callbacks.get("TOCLIENT:ObjectAdd").act(new HMessage(new HPacket(1,id,10,1,2,0,"0","1",0,0,"0",-1,0,5),HMessage.Direction.TOCLIENT,0));
         added.accept(99);require(mapped.isEmpty(),"pre-existing furniture update cannot satisfy a new placement");
         added.accept(101);added.accept(101);require(mapped.equals(Map.of(1,101)),"duplicate ObjectAdd/ObjectUpdate cannot consume a second preset item");
         added.accept(102);require(mapped.equals(Map.of(1,101,2,102)) && pending.isEmpty(),"distinct arrivals retain one-to-one preset identities");
         pending.put("1|2|10",new LinkedList<>(List.of(3,4)));
         var forget=GPresetImporter.class.getDeclaredMethod("forgetExpectedDrop",extension.tools.importutils.FurniDropInfo.class);forget.setAccessible(true);
         forget.invoke(importer,new extension.tools.importutils.FurniDropInfo(1,2,10,extension.tools.postconfig.ItemSource.PREFER_BC,0,3));
         added.accept(104);require(mapped.get(4)==104 && !mapped.containsKey(3),"skipped placement does not steal the next available furniture");
         var wallMapping=GPresetImporter.class.getDeclaredMethod("recordWallMapping",int.class,int.class);wallMapping.setAccessible(true);
         wallMapping.invoke(importer,1,901);
         require(mapped.get(1)==101 && mapped.get(-1)==-901,"wall placement cannot overwrite floor id with same preset number");
         System.out.println("PASS: duplicate/baseline placement acknowledgements and skipped queue entries preserve furniture identities");
      }catch(ReflectiveOperationException error){throw new AssertionError(error);}
   }

   private static void availabilityCounts() {
      FakeExtension e=new FakeExtension();
      class Data extends furnidata.FurniDataTools {
         Data(){super();}
         @Override public boolean isReady(){return true;}
         @Override public String getFloorItemName(int id){return "chair";}
         @Override public furnidata.details.FloorItemDetails getFloorItemDetails(String name){return null;}
      }
      var data=new Data();
      var inventory=new game.Inventory(e,new Logger(),()->{}) {
         @Override public InventoryState getState(){return InventoryState.LOADED;}
         @Override public List<gearth.extensions.parsers.HInventoryItem> getFloorItemsByType(int type){return java.util.Collections.singletonList(null);}
      };
      var floor=new FloorState(e,new Logger(),()->{},()->{}) {
         @Override public List<gearth.extensions.parsers.HFloorItem> getItemsFromType(int type){return List.of(new gearth.extensions.parsers.HFloorItem(new HPacket(1,101,10,1,2,0,"0","1",0,0,"0",-1,0,5)));}
      };
      var drop=new extension.tools.importutils.FurniDropInfo(-1,-1,10,extension.tools.postconfig.ItemSource.PREFER_BC,0,1);
      require(extension.tools.importutils.AvailabilityChecker.missingItems(List.of(drop,drop),inventory,data,floor,true).isEmpty(),"one inventory item plus one room item satisfy two requests independently");
      require(extension.tools.importutils.AvailabilityChecker.missingItems(List.of(drop,drop,drop),inventory,data,floor,true).equals(Map.of("chair",1)),"room backup is consumed once and cannot fabricate extra supply");
      require(extension.tools.importutils.AvailabilityChecker.missingItems(List.of(drop,drop),inventory,data,floor,false).equals(Map.of("chair",1)),"room backup requires explicit use-room option");
      System.out.println("PASS: actual availability checker combines inventory and room resources without double consumption");
   }

   private static void finalPositionCheck() {
      try {
         for(boolean partial:List.of(true,false)) {
            FakeExtension e=new FakeExtension();e.roomLoaded=true;GPresetImporter importer=new GPresetImporter(e);
            var wired=new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray()));
            var item=new extension.tools.presetconfig.furni.PresetFurni(1,"table",new gearth.extensions.parsers.HPoint(1,2,3),0,null);item.setFurniName("table");
            var preset=new PresetConfig(new ArrayList<>(List.of(item)),new ArrayList<>(),wired,new ArrayList<>(),new ArrayList<>());
            e.floorItems.put(101,new gearth.extensions.parsers.HFloorItem(new HPacket(1,101,10,1,2,0,"0","1",0,0,"0",-1,0,5)));
            put(importer,"workingPresetConfig",preset);put(importer,"realFurniIdMap",new HashMap<>(Map.of(1,101)));put(importer,"rootLocation",new gearth.extensions.parsers.HPoint(0,0));put(importer,"incompleteBuildAllowed",partial);put(importer,"activeImportRoomId",42);put(importer,"state",GPresetImporter.BuildingImportState.MOVE_FURNITURE);
            var prepare=GPresetImporter.class.getDeclaredMethod("preparePlacedConfigurations");prepare.setAccessible(true);prepare.invoke(importer);
            var finish=GPresetImporter.class.getDeclaredMethod("finishImport");finish.setAccessible(true);finish.invoke(importer);
            require(importer.getState()==GPresetImporter.BuildingImportState.NONE,"final mismatch leaves importer idle");
            require(e.lastReport.getString("outcome").startsWith(partial?"Import finished with omissions":"Import incomplete"),"wrong final height cannot be reported as full success");
            require(e.lastReport.getJSONArray("items").getJSONObject(0).getString("status").equals("POSITION_MISMATCH"),"final report retains observed mismatch");
         }
         System.out.println("PASS: final height/position mismatches are explicit in partial and strict completion");
      }catch(ReflectiveOperationException error){throw new AssertionError(error);}
   }

}
