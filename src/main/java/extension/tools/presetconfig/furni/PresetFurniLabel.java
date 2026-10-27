package extension.tools.presetconfig.furni;

import gearth.protocol.HPacket;
import gearth.protocol.HMessage;
import org.json.*;
import utils.Utils;

/** Native label type 0 and region type 1. Region areas use room tile coordinates. */
public final class PresetFurniLabel {
   public final int furniId;
   public final String text;
   public final int color;
   public final int visibility;
   public final boolean alwaysOpen;
   private final JSONObject region;

   public PresetFurniLabel(int id,String text,int color,int visibility,boolean alwaysOpen) {
      this(id,text,color,visibility,alwaysOpen,null);
   }
   private PresetFurniLabel(int id,String text,int color,int visibility,boolean alwaysOpen,JSONObject region) {
      this.furniId=id;this.text=text;this.color=color;this.visibility=visibility;this.alwaysOpen=alwaysOpen;
      this.region=region==null?null:new JSONObject(region.toString());
   }
   public PresetFurniLabel(JSONObject json) {
      this(json.getInt("furniId"),json.getString("text"),json.getInt("color"),json.getInt("visibility"),json.getBoolean("alwaysOpen"),json.optJSONObject("region"));
   }
   public boolean isRegion() { return region!=null; }
   public static PresetFurniLabel fromPacket(HPacket packet) {
      Utils.requireBytes(packet,8);
      int type=packet.readInteger(),id=packet.readInteger();
      String text=Utils.readString(packet);
      if(type==0) {
         Utils.requireBytes(packet,9);
         return new PresetFurniLabel(id,text,packet.readInteger(),packet.readInteger(),packet.readBoolean());
      }
      if(type!=1) throw new IllegalArgumentException("Unknown furniture label type " + type);
      JSONObject region=new JSONObject().put("description",Utils.readString(packet));
      Utils.requireBytes(packet,16);
      region.put("offsetX",packet.readInteger()).put("offsetY",packet.readInteger()).put("minWidth",packet.readInteger()).put("minHeight",packet.readInteger());
      region.put("color",Utils.readString(packet));
      int count=Utils.readCount(packet,16); JSONArray areas=new JSONArray();
      for(int i=0;i<count;i++) areas.put(new JSONObject().put("x",packet.readInteger()).put("y",packet.readInteger()).put("width",packet.readInteger()).put("height",packet.readInteger()));
      region.put("areas",areas);
      return new PresetFurniLabel(id,text,0,0,false,region);
   }
   public PresetFurniLabel withFurniId(int id) { return new PresetFurniLabel(id,text,color,visibility,alwaysOpen,region); }
   public PresetFurniLabel translate(int dx,int dy) {
      PresetFurniLabel copy=withFurniId(furniId);
      if(copy.region!=null) for(Object value:copy.region.getJSONArray("areas")) {
         JSONObject area=(JSONObject)value;
         area.put("x",Math.addExact(area.getInt("x"),dx)).put("y",Math.addExact(area.getInt("y"),dy));
      }
      return copy;
   }
   public boolean matches(PresetFurniLabel actual) {
      return actual!=null && toJsonObject().similar(actual.toJsonObject());
   }
   public JSONObject toJsonObject() {
      JSONObject j=new JSONObject().put("furniId",furniId).put("text",text).put("color",color).put("visibility",visibility).put("alwaysOpen",alwaysOpen);
      if(region!=null) j.put("region",new JSONObject(region.toString()));
      return j;
   }
   public HPacket textPacket() {
      if(region==null) return new HPacket("SetFurnitureLabelData",HMessage.Direction.TOSERVER,furniId,text);
      HPacket p=new HPacket("SetFurnitureRegionData",HMessage.Direction.TOSERVER,furniId,text,region.getString("description"),region.getInt("offsetX"),region.getInt("offsetY"),region.getInt("minWidth"),region.getInt("minHeight"),region.getString("color"));
      JSONArray areas=region.getJSONArray("areas"); p.appendInt(areas.length());
      for(Object value:areas) {
         JSONObject a=(JSONObject)value;p.appendInt(a.getInt("x")).appendInt(a.getInt("y")).appendInt(a.getInt("width")).appendInt(a.getInt("height"));
      }
      return p;
   }
   public HPacket settingsPacket() {
      if(region!=null) throw new IllegalStateException("A region has its own settings packet");
      return new HPacket("SetFurnitureLabelSettings",HMessage.Direction.TOSERVER,furniId,color,visibility,alwaysOpen);
   }
}
