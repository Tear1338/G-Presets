package extension.tools.presetconfig.wired;

import gearth.protocol.HPacket;
import java.util.List;
import org.json.JSONObject;

public class PresetWiredAddon extends PresetWiredBase {
   public PresetWiredAddon(HPacket packet) {
      super(packet);
   }

   public PresetWiredAddon(int wiredId, List<Integer> options, String stringConfig, List<Integer> items, List<Integer> secondItems, List<Integer> pickedFurniSources, List<Integer> pickedUserSources, List<String> variableIds) {
      super(wiredId, options, stringConfig, items, secondItems, pickedFurniSources, pickedUserSources, variableIds);
   }

   public PresetWiredAddon(PresetWiredAddon addon) {
      super((PresetWiredBase)addon);
   }

   public PresetWiredAddon(JSONObject object) {
      super(object);
   }

   protected void readTypeSpecific(HPacket packet) {
   }

   protected void appendJsonFields(JSONObject object) {
   }

   protected String getPacketName() {
      return "UpdateAddon";
   }

   protected void applyTypeSpecificWiredConfig(HPacket packet) {
   }

   public PresetWiredAddon clone() {
      return new PresetWiredAddon(this);
   }
}
