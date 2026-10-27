package extension.tools.presetconfig.wired;

import gearth.protocol.HPacket;
import java.util.List;
import org.json.JSONObject;

public class PresetWiredCondition extends PresetWiredBase {
   private int quantifier;

   public PresetWiredCondition(HPacket packet) {
      super(packet);
   }

   public PresetWiredCondition(int wiredId, List<Integer> options, String stringConfig, List<Integer> items, List<Integer> secondItems, int quantifier, List<Integer> pickedFurniSources, List<Integer> pickedUserSources, List<String> variableIds) {
      super(wiredId, options, stringConfig, items, secondItems, pickedFurniSources, pickedUserSources, variableIds);
      this.quantifier = quantifier;
   }

   public PresetWiredCondition(PresetWiredCondition condition) {
      super((PresetWiredBase)condition);
      this.quantifier = condition.quantifier;
   }

   public PresetWiredCondition(JSONObject object) {
      super(object);
      this.quantifier = object.optInt("quantifier");
   }

   protected void readTypeSpecific(HPacket packet) {
      this.quantifier = packet.readInteger();
   }

   protected void appendJsonFields(JSONObject object) {
      object.put("quantifier", this.quantifier);
   }

   protected String getPacketName() {
      return "UpdateCondition";
   }

   protected void applyTypeSpecificWiredConfig(HPacket packet) {
      packet.appendInt(this.quantifier);
   }

   public PresetWiredCondition clone() {
      return new PresetWiredCondition(this);
   }
}
