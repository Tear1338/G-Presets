package extension.tools.presetconfig.wired;

import gearth.protocol.HPacket;
import java.util.List;
import org.json.JSONObject;

public class PresetWiredEffect extends PresetWiredBase {
   private int delay;

   public PresetWiredEffect(HPacket packet) {
      super(packet);
   }

   public PresetWiredEffect(int wiredId, List<Integer> options, String stringConfig, List<Integer> items, List<Integer> secondItems, int delay, List<Integer> pickedFurniSources, List<Integer> pickedUserSources, List<String> variableIds) {
      super(wiredId, options, stringConfig, items, secondItems, pickedFurniSources, pickedUserSources, variableIds);
      this.delay = delay;
   }

   public PresetWiredEffect(PresetWiredEffect effect) {
      super((PresetWiredBase)effect);
      this.delay = effect.delay;
   }

   public PresetWiredEffect(JSONObject object) {
      super(object);
      this.delay = object.getInt("delay");
   }

   protected void readTypeSpecific(HPacket packet) {
      this.delay = packet.readInteger();
   }

   protected void appendJsonFields(JSONObject object) {
      object.put("delay", this.delay);
   }

   protected String getPacketName() {
      return "UpdateAction";
   }

   protected void applyTypeSpecificWiredConfig(HPacket packet) {
      packet.appendInt(this.delay);
   }

   public PresetWiredEffect clone() {
      return new PresetWiredEffect(this);
   }

   public int getDelay() {
      return this.delay;
   }

   public void setDelay(int delay) {
      this.delay = delay;
   }
}
