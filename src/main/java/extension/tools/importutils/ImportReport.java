package extension.tools.importutils;

import extension.tools.presetconfig.*;
import game.FloorState;
import gearth.extensions.parsers.*;
import org.json.*;
import utils.WallPosition;
import java.util.*;

/** Read-only snapshot of observed items, never a gameplay success claim. */
public final class ImportReport {
   private ImportReport() { }
   public static boolean floorMatches(double x,double y,double z,int rotation,double actualX,double actualY,double actualZ,int actualRotation) {
      return Math.abs(x-actualX)<0.01 && Math.abs(y-actualY)<0.01 && Math.abs(z-actualZ)<0.02 && Math.floorMod(rotation,8)==Math.floorMod(actualRotation,8);
   }
   public static boolean wallMatches(WallPosition expected,WallPosition actual) {
      return expected.getX()==actual.getX() && expected.getY()==actual.getY() && expected.getOffsetX()==actual.getOffsetX() && expected.getOffsetY()==actual.getOffsetY() && expected.getDirection()==actual.getDirection();
   }
   public static JSONObject capture(String outcome,PresetConfig preset,Map<Integer,Integer> floorIds,Map<Integer,Integer> wallIds,Set<Integer> confirmed,FloorState room,HPoint root,double zDelta,int expectedRoom) {
      JSONArray rows=new JSONArray();boolean sameRoom=room.getRoomId()==expectedRoom,loaded=room.inRoom();
      Map<Integer,HFloorItem> observedFloor=new HashMap<>();for(HFloorItem item:room.getItems())observedFloor.put(item.getId(),item);
      Map<Integer,HWallItem> observedWall=new HashMap<>();for(HWallItem item:room.getWallItems())observedWall.put(item.getId(),item);
      int ox=root==null?0:root.getX(),oy=root==null?0:root.getY();
      for(var item:preset.getFurniture()) {
         Integer id=floorIds.get(item.getFurniId());
         JSONObject row=new JSONObject().put("kind","Floor").put("presetId",item.getFurniId()).put("className",item.getClassName()).put("realId",id==null?JSONObject.NULL:id);rows.put(row);
         HFloorItem actual=id==null || !sameRoom?null:observedFloor.get(id);
         if(actual==null){row.put("status",!sameRoom?"ROOM_CHANGED":!loaded?"ROOM_NOT_READY":"NOT_OBSERVED");continue;}
         var position=actual.getTile();double x=item.getLocation().getX()+ox,y=item.getLocation().getY()+oy,z=item.getLocation().getZ()+zDelta;
         row.put("expected",new JSONObject().put("x",x).put("y",y).put("z",z).put("rotation",item.getRotation()));
         row.put("observed",new JSONObject().put("x",position.getX()).put("y",position.getY()).put("z",position.getZ()).put("rotation",actual.getFacing().ordinal()));
         boolean matched=floorMatches(x,y,z,item.getRotation(),position.getX(),position.getY(),position.getZ(),actual.getFacing().ordinal());
         row.put("status",matched?"POSITION_MATCH":"POSITION_MISMATCH");
         try {String state=utils.StateExtractor.stateFromItem(actual);row.put("stateExpected",item.getState()==null?JSONObject.NULL:item.getState()).put("stateObserved",state==null?JSONObject.NULL:state).put("stateCheck",item.getState()==null?"NOT_REQUESTED":Objects.equals(state,item.getState())?"MATCH":"MISMATCH");}catch(RuntimeException e){row.put("stateCheck","UNKNOWN");}
      }
      for(var item:preset.getWallFurniture()) {
         Integer id=wallIds.get(item.getFurniId());JSONObject row=new JSONObject().put("kind","Wall").put("presetId",item.getFurniId()).put("className",item.getClassName()).put("realId",id==null?JSONObject.NULL:id);rows.put(row);
         HWallItem actual=id==null || !sameRoom?null:observedWall.get(id);
         if(actual==null){row.put("status",!sameRoom?"ROOM_CHANGED":!loaded?"ROOM_NOT_READY":"NOT_OBSERVED");continue;}
         var relative=item.getLocation();var expected=new WallPosition(relative.getX()+ox,relative.getY()+oy,relative.getOffsetX(),relative.getOffsetY(),relative.getDirection(),relative.getAltitude());
         row.put("expected",expected.toFullString()).put("observed",actual.getLocation());
         try {var position=new WallPosition(actual.getLocation());boolean match=wallMatches(expected,position);row.put("status",match?"POSITION_MATCH":"POSITION_MISMATCH");row.put("altitudeCheck",actual.getLocation().contains(" a=")?(expected.getAltitude()==position.getAltitude()?"MATCH":"MISMATCH"):"UNKNOWN");}catch(RuntimeException e){row.put("status","UNKNOWN_POSITION");}
         row.put("stateCheck",item.getState()==null?"NOT_REQUESTED":Objects.equals(item.getState(),actual.getState())?"MATCH":"MISMATCH");
      }
      JSONArray wired=new JSONArray();for(var box:preset.getPresetWireds().getAll())wired.put(new JSONObject().put("presetId",box.getWiredId()).put("status",confirmed.contains(box.getWiredId())?"SAVE_ACKNOWLEDGED":"NOT_CONFIRMED"));
      return new JSONObject().put("outcome",outcome).put("capturedAt",java.time.Instant.now().toString()).put("roomId",expectedRoom).put("items",rows).put("wired",wired)
         .put("otherConfigurations",new JSONObject().put("contracts",preset.getContracts().size()).put("chests",preset.getChests().size()).put("links",preset.getWiredLinks().size()).put("labels",preset.getFurnitureLabels().size()).put("objectVariables",preset.getObjectVariables().size()).put("roomLinks",preset.getRoomLinks().size()).put("roomSettings",preset.getWiredRoomSettings()!=null && preset.getWiredRoomSettings().completeRoom?1:0).put("status","Confirmation counters reflect operation acknowledgements or re-read comparisons, not gameplay"))
         .put("note","Observed room state at capture time. Late server replies may change the result. Save acknowledgement does not prove WIRED gameplay.");
   }
   public static String text(JSONObject report) {
      StringBuilder s=new StringBuilder(report.getString("outcome")+"\n\n");
      Map<String,Integer> totals=new TreeMap<>();
      for(Object value:report.getJSONArray("items")){JSONObject row=(JSONObject)value;String status=row.getString("status");totals.merge(row.getString("kind")+" "+status,1,Integer::sum);s.append(row.getString("kind")).append(" ").append(row.getString("className")).append(" [").append(row.getInt("presetId")).append("] — ").append(status.replace('_',' '));if(row.has("stateCheck"))s.append("; state: ").append(row.getString("stateCheck"));if(row.has("altitudeCheck"))s.append("; height: ").append(row.getString("altitudeCheck"));s.append('\n');}
      JSONArray omissions=report.optJSONArray("omissions");
      if(omissions!=null && !omissions.isEmpty()) {
         s.append("\nSkipped because furniture/dependencies were unavailable\n");
         for(Object value:omissions){JSONObject row=(JSONObject)value;s.append(row.getString("category")).append(" [").append(row.getInt("presetId")).append("]: ").append(row.getString("reason")).append('\n');}
      }
      s.append("\nTotals\n");totals.forEach((key,value)->s.append(key.replace('_',' ')).append(": ").append(value).append('\n'));
      for(Object value:report.getJSONArray("wired")){JSONObject row=(JSONObject)value;s.append("WIRED ").append(row.getInt("presetId")).append(": ").append(row.getString("status").replace('_',' ')).append('\n');}
      JSONObject other=report.getJSONObject("otherConfigurations"),confirmed=other.optJSONObject("confirmed");
      s.append("\nOther configurations - confirmed / requested\n");
      for(String key:new TreeSet<>(other.keySet()))if(other.opt(key) instanceof Number)s.append(key).append(": ").append(confirmed==null?0:confirmed.optInt(key,0)).append(" / ").append(other.getInt(key)).append('\n');
      return s.append("\n").append(other.getString("status")).append("\n\n").append(report.getString("note")).toString();
   }
}
