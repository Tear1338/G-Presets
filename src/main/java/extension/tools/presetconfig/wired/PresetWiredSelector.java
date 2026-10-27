package extension.tools.presetconfig.wired;

import gearth.protocol.HPacket;
import java.util.List;
import org.json.JSONObject;

public class PresetWiredSelector extends PresetWiredBase {
   private boolean filter;
   private boolean inverse;

   public PresetWiredSelector(HPacket packet) {
      super(packet);
   }

   public PresetWiredSelector(int wiredId, List<Integer> options, String stringConfig, List<Integer> items, List<Integer> secondItems, boolean filter, boolean inverse, List<Integer> furniSources, List<Integer> userSources, List<String> variableIds) {
      super(wiredId, options, stringConfig, items, secondItems, furniSources, userSources, variableIds);
      this.filter = filter;
      this.inverse = inverse;
   }

   public PresetWiredSelector(PresetWiredSelector selector) {
      super((PresetWiredBase)selector);
      this.filter = selector.filter;
      this.inverse = selector.inverse;
   }

   public PresetWiredSelector(JSONObject object) {
      super(object);
      this.filter = object.optBoolean("filter");
      this.inverse = object.optBoolean("inverse");
   }

   protected void readTypeSpecific(HPacket packet) {
      this.filter = packet.readBoolean();
      this.inverse = packet.readBoolean();
   }

   protected void appendJsonFields(JSONObject object) {
      object.put("filter", this.filter);
      object.put("inverse", this.inverse);
   }

   protected String getPacketName() {
      return "UpdateSelector";
   }

   protected void applyTypeSpecificWiredConfig(HPacket packet) {
      packet.appendBoolean(this.filter);
      packet.appendBoolean(this.inverse);
   }

   public PresetWiredSelector clone() {
      return new PresetWiredSelector(this);
   }
}
