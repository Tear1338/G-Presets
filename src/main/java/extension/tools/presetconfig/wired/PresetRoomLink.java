package extension.tools.presetconfig.wired;

import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/** ~103 is teleport.target_id (a furniture ID), never a room destination. */
public final class PresetRoomLink {
   public final int furniId;
   public final String destination;
   public final String destinationType;
   private static final Set<String> TYPES=Set.of("legacy","furniture","externalFurniture","none","resolvedFurniture");
   /** Old snapshots stored an absolute source-world furniture ID without its meaning. */
   public PresetRoomLink(int id,String destination){this(id,destination,"legacy");}
   private PresetRoomLink(int id,String destination,String type) {
      this.furniId=id;this.destination=destination;this.destinationType=type;
      if(destination==null || (!destination.isEmpty() && !destination.matches("[0-9]+")) || !TYPES.contains(type))throw new IllegalArgumentException("Invalid linked furniture destination");
      if(!destination.isEmpty())Integer.parseInt(destination);
      if(type.equals("none") && !destination.equals("0") || type.equals("furniture") && destination.isEmpty())throw new IllegalArgumentException("Invalid linked furniture reference");
   }
   public PresetRoomLink(JSONObject json){this(json.getInt("furniId"),json.getString("destination"),json.optString("destinationType","legacy"));if(destinationType.equals("resolvedFurniture"))throw new IllegalArgumentException("Resolved furniture IDs cannot be supplied by preset JSON");}
   public static PresetRoomLink capture(int owner,int target,Map<Integer,Integer> furniture) {
      Integer local=furniture.get(target);
      return new PresetRoomLink(owner,Integer.toString(local!=null?local:target),local!=null?"furniture":target==0?"none":"externalFurniture");
   }
   public String unavailableReason(Map<Integer,Integer> furniture) {
      if(!furniture.containsKey(furniId))return "Room linker furniture was not placed: "+furniId;
      if(destinationType.equals("legacy"))return "Legacy room linker snapshot contains a source furniture ID, not a room ID; its native pairing is retained. Re-export to capture a portable local link";
      if(destinationType.equals("externalFurniture"))return "Linked teleport furniture is outside this preset; native pairing retained";
      if(destinationType.equals("furniture") && !furniture.containsKey(Integer.parseInt(destination)))return "Linked destination furniture was not placed: "+destination;
      return null;
   }
   public PresetRoomLink remap(Map<Integer,Integer> furniture) {
      String reason=unavailableReason(furniture);if(reason!=null)throw new IllegalStateException(reason);
      int target=destinationType.equals("none")?0:destinationType.equals("resolvedFurniture")?Integer.parseInt(destination):furniture.get(Integer.parseInt(destination));
      return new PresetRoomLink(furniture.get(furniId),Integer.toString(target),"resolvedFurniture");
   }
   public JSONObject toJsonObject(){JSONObject json=new JSONObject().put("furniId",furniId).put("destination",destination);if(!destinationType.equals("legacy"))json.put("destinationType",destinationType);return json;}
   public PresetRoomLink withFurniId(int id){return new PresetRoomLink(id,destination,destinationType);}
   public HPacket savePacket() {
      if(!destinationType.equals("resolvedFurniture"))throw new IllegalStateException("Room linker furniture references must be resolved before saving");
      return new HPacket("WiredSetObjectVariableValue",HMessage.Direction.TOSERVER,0,furniId,"~103",Integer.parseInt(destination),0);
   }
}
