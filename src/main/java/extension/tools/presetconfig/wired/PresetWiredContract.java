package extension.tools.presetconfig.wired;

import furnidata.FurniDataTools;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.*;
import org.json.*;
import utils.Utils;

/** Contract editor definition only. Does not initiate transactions or move assets. */
public final class PresetWiredContract {
   public final int furniId;
   private final JSONObject data;

   public PresetWiredContract(JSONObject json) {
      data = new JSONObject(json.toString());
      furniId = data.getInt("furniId");
      int type = data.getInt("type");
      if (type < 0 || type > 2) throw new IllegalArgumentException("Unknown contract type " + type);
      // Validate all nested values before any furniture is placed.
      savePacket(null);
   }

   public static PresetWiredContract fromPacket(HPacket p, FurniDataTools furnidata) {
      Utils.requireBytes(p, 6);
      JSONObject j = new JSONObject().put("furniId", p.readInteger()).put("type", (int)p.readShort());
      Utils.requireBytes(p, 1);
      if (p.readBoolean()) {
         JSONArray give = new JSONArray();
         int count = Utils.readCount(p, 4);
         for (int i=0;i<count;i++) give.put(readRule(p, furnidata));
         j.put("give", give);
      }
      Utils.requireBytes(p, 1);
      if (p.readBoolean()) j.put("get", readRule(p, furnidata));
      switch(j.getInt("type")) {
         case 0:
            Utils.requireBytes(p, 2); j.put("paymentMode", (int)p.readShort());
            j.put("receiveText", Utils.readString(p)).put("layoutType", Utils.readString(p)); break;
         case 1: break;
         case 2:
            Utils.requireBytes(p, 3); j.put("rewardCategory", (int)p.readShort()).put("showDialog", p.readBoolean());
            j.put("rewardText", Utils.readString(p)); break;
         default: throw new IllegalArgumentException("Unknown contract type");
      }
      if (p.isCorrupted() || p.isEOF()!=1) throw new IllegalArgumentException("Unknown contract packet layout");
      return new PresetWiredContract(j);
   }

   private static JSONArray readRule(HPacket p, FurniDataTools furnidata) {
      JSONArray nodes = new JSONArray();
      int count = Utils.readCount(p, 5);
      for(int i=0;i<count;i++) {
         Utils.requireBytes(p, 5);
         int type=p.readByte() & 255, amount=p.readInteger();
         JSONObject node=new JSONObject().put("type",type).put("amount",amount);
         if(type==1) {
            Utils.requireBytes(p,5); boolean wall=p.readBoolean(); int typeId=p.readInteger();
            node.put("wall",wall).put("typeId",typeId).put("poster",Utils.readString(p));
            if(furnidata!=null) {
               String name=wall?furnidata.getWallItemName(typeId):furnidata.getFloorItemName(typeId);
               if(name==null) throw new IllegalArgumentException("Unknown contract furniture type " + typeId);
               node.put("className",name);
            }
         } else if(type!=0) throw new IllegalArgumentException("Unknown contract node type " + type);
         nodes.put(node);
      }
      return nodes;
   }

   private static void writeRule(HPacket p, JSONArray nodes, FurniDataTools furnidata) {
      p.appendInt(nodes.length());
      for(Object value:nodes) {
         JSONObject n=(JSONObject)value;
         int type=n.getInt("type"), amount=n.getInt("amount");
         if((type!=0 && type!=1) || amount<0) throw new IllegalArgumentException("Invalid contract node");
         p.appendByte((byte)type).appendInt(amount);
         if(type==1) {
            boolean wall=n.getBoolean("wall");
            int typeId=n.getInt("typeId");
            if(furnidata!=null) {
               String name=n.optString("className",null);
               if(name==null) throw new IllegalArgumentException("Contract lacks furniture class name; re-export it");
               Integer resolved=wall?furnidata.getWallTypeId(name):furnidata.getFloorTypeId(name);
               if(resolved==null) throw new IllegalArgumentException("Contract furniture unavailable: " + name);
               typeId=resolved;
            }
            p.appendBoolean(wall).appendInt(typeId).appendString(n.getString("poster"));
         }
      }
   }

   private short number(String key) {
      int value=data.getInt(key);
      if(value<Short.MIN_VALUE || value>Short.MAX_VALUE) throw new IllegalArgumentException("Contract short out of range: " + key);
      return (short)value;
   }

   public HPacket savePacket(FurniDataTools furnidata) {
      HPacket p=new HPacket("WiredUpdateContract", HMessage.Direction.TOSERVER, furniId, number("type"));
      boolean give=data.has("give"); p.appendBoolean(give);
      if(give) {
         JSONArray rules=data.getJSONArray("give"); p.appendInt(rules.length());
         for(Object rule:rules) writeRule(p,(JSONArray)rule,furnidata);
      }
      boolean get=data.has("get"); p.appendBoolean(get);
      if(get) writeRule(p,data.getJSONArray("get"),furnidata);
      if(data.getInt("type")==0) p.appendShort(number("paymentMode")).appendString(data.getString("receiveText")).appendString(data.getString("layoutType"));
      if(data.getInt("type")==2) p.appendShort(number("rewardCategory")).appendBoolean(data.getBoolean("showDialog")).appendString(data.getString("rewardText"));
      return p;
   }

   public PresetWiredContract withFurniId(int id) { return new PresetWiredContract(toJsonObject().put("furniId",id)); }
   public JSONObject toJsonObject() { return new JSONObject(data.toString()); }
   public static HPacket requestPacket(int id) { return new HPacket("WiredOpenContract",HMessage.Direction.TOSERVER,id); }
}
