package extension.tools.presetconfig.wired;

import extension.tools.presetconfig.PresetJsonConfigurable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

public class PresetWireds implements PresetJsonConfigurable {
   private final HashMap<String, String> variablesMap;
   private List<PresetWiredCondition> conditions;
   private List<PresetWiredEffect> effects;
   private List<PresetWiredTrigger> triggers;
   private List<PresetWiredAddon> addons;
   private List<PresetWiredSelector> selectors;
   private List<PresetWiredVariable> variables;

   public PresetWireds(List<PresetWiredCondition> conditions, List<PresetWiredEffect> effects, List<PresetWiredTrigger> triggers, List<PresetWiredAddon> addons, List<PresetWiredSelector> selectors, List<PresetWiredVariable> variables, HashMap<String, String> variablesMap) {
      this.conditions = conditions;
      this.effects = effects;
      this.triggers = triggers;
      this.addons = addons;
      this.selectors = selectors;
      this.variables = variables;
      this.variablesMap = variablesMap;
   }

   public PresetWireds(JSONObject object) {
      this.conditions = (List)object.getJSONArray("conditions").toList().stream().map((o) -> new PresetWiredCondition(new JSONObject((Map)o))).collect(Collectors.toList());
      this.effects = (List)object.getJSONArray("effects").toList().stream().map((o) -> new PresetWiredEffect(new JSONObject((Map)o))).collect(Collectors.toList());
      this.triggers = (List)object.getJSONArray("triggers").toList().stream().map((o) -> new PresetWiredTrigger(new JSONObject((Map)o))).collect(Collectors.toList());
      this.addons = (List<PresetWiredAddon>)(object.has("addons") ? (List)object.getJSONArray("addons").toList().stream().map((o) -> new PresetWiredAddon(new JSONObject((Map)o))).collect(Collectors.toList()) : new ArrayList());
      this.selectors = (List<PresetWiredSelector>)(object.has("selectors") ? (List)object.getJSONArray("selectors").toList().stream().map((o) -> new PresetWiredSelector(new JSONObject((Map)o))).collect(Collectors.toList()) : new ArrayList());
      this.variables = (List<PresetWiredVariable>)(object.has("variables") ? (List)object.getJSONArray("variables").toList().stream().map((o) -> new PresetWiredVariable(new JSONObject((Map)o))).collect(Collectors.toList()) : new ArrayList());
      this.variablesMap = new HashMap();
      if (object.has("variables_map")) {
         JSONObject map = object.getJSONObject("variables_map");

         for(String key : map.keySet()) {
            this.variablesMap.put(key, String.valueOf(map.get(key)));
         }
      }

   }

   public JSONObject toJsonObject() {
      JSONObject object = new JSONObject();
      JSONArray jsonConditions = new JSONArray((Collection)this.conditions.stream().map(PresetWiredBase::toJsonObject).collect(Collectors.toList()));
      JSONArray jsonEffects = new JSONArray((Collection)this.effects.stream().map(PresetWiredBase::toJsonObject).collect(Collectors.toList()));
      JSONArray jsonTriggers = new JSONArray((Collection)this.triggers.stream().map(PresetWiredBase::toJsonObject).collect(Collectors.toList()));
      JSONArray jsonAddons = new JSONArray((Collection)this.addons.stream().map(PresetWiredBase::toJsonObject).collect(Collectors.toList()));
      JSONArray jsonSelectors = new JSONArray((Collection)this.selectors.stream().map(PresetWiredBase::toJsonObject).collect(Collectors.toList()));
      JSONArray jsonVariables = new JSONArray((Collection)this.variables.stream().map(PresetWiredBase::toJsonObject).collect(Collectors.toList()));
      JSONObject jsonVariablesMap = new JSONObject();
      HashMap<String, String> var10000 = this.variablesMap;
      Objects.requireNonNull(jsonVariablesMap);
      var10000.forEach(jsonVariablesMap::put);
      object.put("conditions", jsonConditions);
      object.put("effects", jsonEffects);
      object.put("triggers", jsonTriggers);
      object.put("addons", jsonAddons);
      object.put("selectors", jsonSelectors);
      object.put("variables", jsonVariables);
      object.put("variables_map", jsonVariablesMap);
      return object;
   }

   public List<PresetWiredBase> getAll() {
      List<PresetWiredBase> all = new ArrayList<>();
      all.addAll(this.variables);
      all.addAll(this.conditions);
      all.addAll(this.effects);
      all.addAll(this.addons);
      all.addAll(this.selectors);
      all.addAll(this.triggers);
      return all;
   }

   public List<PresetWiredCondition> getConditions() {
      return this.conditions;
   }

   public List<PresetWiredEffect> getEffects() {
      return this.effects;
   }

   public List<PresetWiredTrigger> getTriggers() {
      return this.triggers;
   }

   public List<PresetWiredAddon> getAddons() {
      return this.addons;
   }

   public List<PresetWiredSelector> getSelectors() {
      return this.selectors;
   }

   public List<PresetWiredVariable> getVariables() {
      return this.variables;
   }

   public void setConditions(List<PresetWiredCondition> conditions) {
      this.conditions = conditions;
   }

   public void setEffects(List<PresetWiredEffect> effects) {
      this.effects = effects;
   }

   public void setTriggers(List<PresetWiredTrigger> triggers) {
      this.triggers = triggers;
   }

   public void setAddons(List<PresetWiredAddon> addons) {
      this.addons = addons;
   }

   public void setSelectors(List<PresetWiredSelector> selectors) {
      this.selectors = selectors;
   }

   public void setVariables(List<PresetWiredVariable> variables) {
      this.variables = variables;
   }

   public HashMap<String, String> getVariablesMap() {
      return this.variablesMap;
   }
}
