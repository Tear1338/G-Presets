package extension.tools.presetconfig.wired;

import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Association with an existing WIRED Link network; never creates or deletes a network. */
public final class PresetWiredLink {
   public final int furniId;
   public final String networkId;
   public final boolean receiveLocal;

   public PresetWiredLink(int furniId, String networkId, boolean receiveLocal) {
      if (networkId == null || networkId.getBytes(StandardCharsets.UTF_8).length > 65535) {
         throw new IllegalArgumentException("Invalid WIRED Link network ID");
      }
      this.furniId = furniId;
      this.networkId = networkId;
      this.receiveLocal = receiveLocal;
   }

   public PresetWiredLink(JSONObject object) {
      this(object.getInt("furniId"), object.getString("networkId"), object.getBoolean("receiveLocal"));
   }

   public PresetWiredLink withFurniId(int id) { return new PresetWiredLink(id, networkId, receiveLocal); }

   public JSONObject toJsonObject() {
      return new JSONObject().put("furniId", furniId).put("networkId", networkId).put("receiveLocal", receiveLocal);
   }

   public HPacket savePacket() {
      return new HPacket("WiredLinkSaveConfiguration", HMessage.Direction.TOSERVER, furniId, networkId, receiveLocal);
   }

   public static HPacket requestPacket(int furniId) {
      return new HPacket("WiredLinkGetConfiguration", HMessage.Direction.TOSERVER, furniId);
   }
}
