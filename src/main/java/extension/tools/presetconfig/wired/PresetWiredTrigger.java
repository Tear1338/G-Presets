package extension.tools.presetconfig.wired;

import gearth.protocol.HPacket;
import java.util.List;
import org.json.JSONObject;

public class PresetWiredTrigger extends PresetWiredBase {
   public PresetWiredTrigger(HPacket packet) {
      super(packet);
   }

   public PresetWiredTrigger(int wiredId, List<Integer> options, String stringConfig, List<Integer> items, List<Integer> secondItems, List<Integer> pickedFurniSources, List<Integer> pickedUserSources, List<String> variableIds) {
      super(wiredId, options, stringConfig, items, secondItems, pickedFurniSources, pickedUserSources, variableIds);
   }

   public PresetWiredTrigger(PresetWiredTrigger trigger) {
      super((PresetWiredBase)trigger);
   }

   public PresetWiredTrigger(JSONObject object) {
      super(object);
   }

   protected void readTypeSpecific(HPacket packet) {
   }

   protected void appendJsonFields(JSONObject object) {
   }

   protected String getPacketName() {
      return "UpdateTrigger";
   }

   protected void applyTypeSpecificWiredConfig(HPacket packet) {
   }

   public PresetWiredTrigger clone() {
      return new PresetWiredTrigger(this);
   }
}
