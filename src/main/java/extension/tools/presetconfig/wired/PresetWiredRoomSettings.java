package extension.tools.presetconfig.wired;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import org.json.JSONObject;
import utils.Utils;
/** Room-wide settings are applied only by a complete-room preset. */
public final class PresetWiredRoomSettings {
   public final int modifyMask,readMask;
   public final String timezone;
   public final boolean completeRoom;
   public PresetWiredRoomSettings(int modifyMask,int readMask,String timezone,boolean completeRoom) {
      this.modifyMask=modifyMask;this.readMask=readMask;this.timezone=timezone;this.completeRoom=completeRoom;
   }
   public PresetWiredRoomSettings(JSONObject j) { this(j.getInt("modifyMask"),j.getInt("readMask"),j.getString("timezone"),j.optBoolean("completeRoom",false)); }
   public static PresetWiredRoomSettings fromPacket(HPacket p) {
      Utils.requireBytes(p,8);int modify=p.readInteger(),read=p.readInteger();String timezone=Utils.readString(p);
      if(p.isEOF()!=1 || p.isCorrupted()) throw new IllegalArgumentException("Unknown WIRED room settings layout");
      return new PresetWiredRoomSettings(modify,read,timezone,false);
   }
   public PresetWiredRoomSettings forScope(boolean complete) { return new PresetWiredRoomSettings(modifyMask,readMask,timezone,complete); }
   public boolean matches(PresetWiredRoomSettings actual) { return actual!=null && actual.modifyMask==modifyMask && actual.readMask==readMask && actual.timezone.equals(timezone); }
   public HPacket savePacket() { return new HPacket("WiredSetRoomSettings",HMessage.Direction.TOSERVER,modifyMask,readMask,timezone); }
   public static HPacket requestPacket() { return new HPacket("WiredGetRoomSettings",HMessage.Direction.TOSERVER); }
   public JSONObject toJsonObject() { return new JSONObject().put("modifyMask",modifyMask).put("readMask",readMask).put("timezone",timezone).put("completeRoom",completeRoom); }
}
