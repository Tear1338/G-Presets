package extension.tools.presetconfig.wired;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import org.json.JSONObject;
/** Destination is a room ID, independent of furniture IDs. */
public final class PresetRoomLink {
   public final int furniId;
   public final String destination;
   public PresetRoomLink(int id,String destination) {
      this.furniId=id;this.destination=destination;
      if(destination==null || (!destination.isEmpty() && !destination.matches("[0-9]+"))) throw new IllegalArgumentException("Invalid room linker destination");
      if(!destination.isEmpty()) Integer.parseInt(destination);
   }
   public PresetRoomLink(JSONObject json) { this(json.getInt("furniId"),json.getString("destination")); }
   public JSONObject toJsonObject() { return new JSONObject().put("furniId",furniId).put("destination",destination); }
   public PresetRoomLink withFurniId(int id) { return new PresetRoomLink(id,destination); }
   public HPacket savePacket() { return new HPacket("WiredSetObjectVariableValue",HMessage.Direction.TOSERVER,0,furniId,"~103",destination.isEmpty()?0:Integer.parseInt(destination),0); }
}
