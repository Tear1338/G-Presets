package extension.tools.presetconfig.wired;

import extension.parsers.HSharedVariable;
import extension.parsers.HWiredContext;
import extension.parsers.HWiredVariable;
import gearth.protocol.HPacket;
import java.util.List;
import org.json.JSONObject;

public class PresetWiredVariable extends PresetWiredBase {
   public String variableId;
   private Integer variableTarget;

   public PresetWiredVariable(HPacket packet) {
      super(packet);
   }

   public PresetWiredVariable(int wiredId, List<Integer> options, String stringConfig, List<Integer> items, List<Integer> secondItems, List<Integer> pickedFurniSources, List<Integer> pickedUserSources, List<String> variableIds, HWiredContext wiredContext) {
      super(wiredId, options, stringConfig, items, secondItems, pickedFurniSources, pickedUserSources, variableIds);
      this.variableId = "";
      if (wiredContext != null) {
         if (wiredContext.roomVariablesList != null && !wiredContext.roomVariablesList.variables.isEmpty()) {
            for(HWiredVariable var : wiredContext.roomVariablesList.variables.values()) {
               if (var.name.equals(getVariableName())) {
                  this.variableId = var.id;
                  this.variableTarget = var.variableType;
               }
            }
         }
         if (wiredContext.furniVariableInfo != null && wiredContext.furniVariableInfo.variable.name.equals(getVariableName())) {
            this.variableId = wiredContext.furniVariableInfo.variable.id;
            this.variableTarget = wiredContext.furniVariableInfo.variable.variableType;
         }
         if (wiredContext.userVariableInfo != null && wiredContext.userVariableInfo.variable.name.equals(getVariableName())) {
            this.variableId = wiredContext.userVariableInfo.variable.id;
            this.variableTarget = wiredContext.userVariableInfo.variable.variableType;
         }
         if (wiredContext.globalVariableInfo != null && wiredContext.globalVariableInfo.variable.name.equals(getVariableName())) {
            this.variableId = wiredContext.globalVariableInfo.variable.id;
            this.variableTarget = wiredContext.globalVariableInfo.variable.variableType;
         }
         if (wiredContext.referenceVariablesList != null && !wiredContext.referenceVariablesList.sharedVariables.isEmpty()) {
            for(HSharedVariable var : wiredContext.referenceVariablesList.sharedVariables) {
               if (var.wiredVariable.name.equals(getVariableName())) {
                  // A shared source is not the local alias created by this box.
                  // Keep the room-context ID; export resolves a missing alias from the room map.
                  if (this.variableTarget == null) this.variableTarget = var.wiredVariable.variableType;
               }
            }
         }
      }

   }

   public PresetWiredVariable(PresetWiredVariable variable) {
      super((PresetWiredBase)variable);
      this.variableId = variable.variableId;
      this.variableTarget = variable.variableTarget;
   }

   public PresetWiredVariable(JSONObject object) {
      super(object);

      this.variableId = object.optString("variableId", "");
      this.variableTarget = object.has("variableTarget") ? object.getInt("variableTarget") : null;
   }

   public String getVariableName() {
      return this.stringConfig.split("\t", -1)[0];
   }

   public Integer getVariableTarget() {
      return this.variableTarget;
   }

   protected void readTypeSpecific(HPacket packet) {
   }

   protected void appendJsonFields(JSONObject object) {
      object.put("variableId", this.variableId);
      if (this.variableTarget != null) object.put("variableTarget", this.variableTarget);
   }

   protected String getPacketName() {
      return "UpdateVariable";
   }

   protected void applyTypeSpecificWiredConfig(HPacket packet) {
   }

   public PresetWiredVariable clone() {
      return new PresetWiredVariable(this);
   }
}
