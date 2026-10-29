package extension.tools.importutils;

import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.wired.*;
import java.util.*;
import java.util.function.ToIntFunction;
import org.json.*;

/** Exclude unavailable dependencies without changing the requested preset or its report totals. */
public final class PartialImportPlan {
   public record Omission(String category,int presetId,String reason) {
      public JSONObject json(){return new JSONObject().put("category",category).put("presetId",presetId).put("reason",reason);}
   }
   private final PresetConfig effective;
   private final List<Omission> omissions=new ArrayList<>();
   private final Map<Integer,String> skippedWired=new LinkedHashMap<>();
   private final Set<Integer> missing=new HashSet<>();
   private final Set<String> missingVariables=new HashSet<>();
   private PartialImportPlan(PresetConfig requested,Map<Integer,Integer> furniture,Map<Integer,Integer> walls) {
      effective=new PresetConfig(requested.toJsonObject());
      for(var f:requested.getFurniture()) if(!furniture.containsKey(f.getFurniId())) {
         missing.add(f.getFurniId());omit("Floor",f.getFurniId(),"Not placed: "+f.getClassName());
      }
      for(var f:requested.getWallFurniture()) if(!walls.containsKey(f.getFurniId())) {
         missing.add(-Math.abs(f.getFurniId()));omit("Wall",f.getFurniId(),"Not placed: "+f.getClassName());
      }
      var wired=effective.getPresetWireds();
      // A missing snapshot/selection must not silently change what a WIRED selects.
      for(var box:wired.getAll()) {
         String reason=null;
         if(!furniture.containsKey(box.getWiredId()))reason="WIRED furniture was not placed";
         for(int id:box.getItems()) if(missing.contains(id) || !furniture.containsKey(id))reason="Missing selected furniture "+id;
         for(int id:box.getSecondItems()) if(missing.contains(id) || !furniture.containsKey(id))reason="Missing second-selection furniture "+id;
         for(var binding:effective.getBindings()) if(binding.getWiredId()==box.getWiredId() && (missing.contains(binding.getFurniId()) || !furniture.containsKey(binding.getFurniId())))reason="Missing snapshot furniture "+binding.getFurniId();
         if(reason!=null)skippedWired.put(box.getWiredId(),reason);
      }
      // Propagate unavailable variable definitions through dependent definitions and WIRED.
      boolean changed;
      do {
         changed=false;
         for(var variable:wired.getVariables()) if(skippedWired.containsKey(variable.getWiredId()))missingVariables.addAll(variableAliases(variable));
         for(var box:wired.getAll()) if(!skippedWired.containsKey(box.getWiredId())) {
            for(String id:box.getVariableIds()) if(missingVariables.contains(id) && !box.getExternalVariableIds().contains(id)) {
               skippedWired.put(box.getWiredId(),"Unavailable preset variable "+id);changed=true;break;
            }
         }
      }while(changed);
      skippedWired.forEach((id,reason)->omit("WIRED",id,reason));
      wired.getConditions().removeIf(b->skippedWired.containsKey(b.getWiredId()));
      wired.getEffects().removeIf(b->skippedWired.containsKey(b.getWiredId()));
      wired.getTriggers().removeIf(b->skippedWired.containsKey(b.getWiredId()));
      wired.getAddons().removeIf(b->skippedWired.containsKey(b.getWiredId()));
      wired.getSelectors().removeIf(b->skippedWired.containsKey(b.getWiredId()));
      wired.getVariables().removeIf(b->skippedWired.containsKey(b.getWiredId()));
      Set<String> missingNames=new HashSet<>();
      wired.getVariablesMap().forEach((name,id)->{if(missingVariables.contains(id))missingNames.add(name);});
      for(var item:effective.getFurniture())item.setVariables(legacyValues(item.getVariables(),missingNames,item.getFurniId()));
      for(var item:effective.getWallFurniture())item.setVariables(legacyValues(item.getVariables(),missingNames,-Math.abs(item.getFurniId())));
      wired.getVariablesMap().entrySet().removeIf(e->missingVariables.contains(e.getValue()));
      effective.getBindings().removeIf(b->skippedWired.containsKey(b.getWiredId()));
      effective.getRoomLinks().removeIf(link->{String reason=link.unavailableReason(furniture);if(reason==null)return false;omit("roomLinks",link.furniId,reason);return true;});
      exclude(effective.getChests(),b->b.furniId,"chests",furniture);
      exclude(effective.getContracts(),b->b.furniId,"contracts",furniture);
      exclude(effective.getWiredLinks(),b->b.furniId,"links",furniture);
      exclude(effective.getFurnitureLabels(),b->b.furniId,"labels",furniture);
      exclude(effective.getAdsBackgrounds(),b->b.getFurniId(),"adsBackgrounds",furniture);
      List<PresetObjectVariables> holders=new ArrayList<>();
      for(var holder:effective.getObjectVariables()) {
         if(holder.target==0 && (missing.contains(holder.objectId) || !furniture.containsKey(holder.objectId))) {
            omit("objectVariables",holder.objectId,"Variable holder was not placed");continue;
         }
         JSONObject json=holder.toJsonObject(),values=json.getJSONObject("values");
         Set<String> removed=new HashSet<>();
         for(String key:new ArrayList<>(values.keySet())) {
            boolean missingReference=false;
            for(Object ref:json.getJSONArray("furnitureReferenceKeys")) if(key.equals(ref) && (missing.contains(values.getInt(key)) || !furniture.containsKey(values.getInt(key))))missingReference=true;
            if(missingVariables.contains(key) || missingReference) {
               values.remove(key);removed.add(key);
               omit("objectVariables",holder.objectId,"Skipped value "+key+": unavailable "+(missingReference?"furniture destination":"preset variable"));
            }
         }
         for(String array:List.of("furnitureReferenceKeys","writeOnlyVariableIds")) {
            JSONArray kept=new JSONArray();for(Object key:json.getJSONArray(array))if(!removed.contains(key))kept.put(key);json.put(array,kept);
         }
         if(!values.isEmpty() || removed.isEmpty())holders.add(new PresetObjectVariables(json));
      }
      effective.setObjectVariables(holders);
   }
   private Set<String> variableAliases(PresetWiredVariable variable) {
      Set<String> ids=new HashSet<>();
      if(PresetWiredBase.isLocalVariableId(variable.variableId) && !variable.getExternalVariableIds().contains(variable.variableId))ids.add(variable.variableId);
      if(effective.getPresetWireds().getVariables().stream().filter(v->v.getVariableName().equals(variable.getVariableName())).count()==1) {
         String alias=effective.getPresetWireds().getVariablesMap().get(variable.getVariableName());
         if(PresetWiredBase.isLocalVariableId(alias) && !variable.getExternalVariableIds().contains(alias))ids.add(alias);
      }
      return ids;
   }
   /** A rejected definition invalidates its local aliases, not the shared variable it references. */
   public void rejectWired(int id,String reason) {
      if(skippedWired.putIfAbsent(id,reason)!=null)return;
      var wired=effective.getPresetWireds();
      boolean changed;
      do {
         changed=false;
         for(var variable:wired.getVariables())if(skippedWired.containsKey(variable.getWiredId()))missingVariables.addAll(variableAliases(variable));
         for(var box:wired.getAll())if(!skippedWired.containsKey(box.getWiredId())) {
            for(String dependency:box.getVariableIds())if(missingVariables.contains(dependency) && !box.getExternalVariableIds().contains(dependency)) {
               skippedWired.put(box.getWiredId(),"Unavailable preset variable "+dependency);changed=true;break;
            }
         }
      }while(changed);
      for(var box:wired.getAll())if(skippedWired.containsKey(box.getWiredId()))omit("WIRED",box.getWiredId(),skippedWired.get(box.getWiredId()));
      Set<String> names=new HashSet<>();
      wired.getVariablesMap().forEach((name,value)->{if(missingVariables.contains(value))names.add(name);});
      wired.getVariablesMap().entrySet().removeIf(e->missingVariables.contains(e.getValue()));
      for(var item:effective.getFurniture())item.setVariables(legacyValues(item.getVariables(),names,item.getFurniId()));
      for(var item:effective.getWallFurniture())item.setVariables(legacyValues(item.getVariables(),names,-Math.abs(item.getFurniId())));
      List<PresetObjectVariables> holders=new ArrayList<>();
      for(var holder:effective.getObjectVariables()) {
         JSONObject json=holder.toJsonObject(),values=json.getJSONObject("values");
         for(String key:new ArrayList<>(values.keySet()))if(missingVariables.contains(key)) {
            values.remove(key);omit("objectVariables",holder.objectId,"Skipped value "+key+": rejected preset variable");
         }
         for(String array:List.of("furnitureReferenceKeys","writeOnlyVariableIds")) {
            JSONArray kept=new JSONArray();for(Object key:json.getJSONArray(array))if(values.has(key.toString()))kept.put(key);json.put(array,kept);
         }
         if(!values.isEmpty())holders.add(new PresetObjectVariables(json));
      }
      effective.setObjectVariables(holders);
      effective.getBindings().removeIf(b->skippedWired.containsKey(b.getWiredId()));
   }
   private Map<String,Integer> legacyValues(Map<String,Integer> values,Set<String> names,int id) {
      if(values==null)return null;
      Map<String,Integer> kept=new HashMap<>(values);
      for(String name:values.keySet())if(names.contains(name) || missingVariables.contains(name)) {
         kept.remove(name);omit("objectVariables",id,"Skipped legacy value "+name+": unavailable preset variable");
      }
      return kept;
   }
   public void noteUnconfirmed(String category,int id,String reason){omit(category,id,reason);}
   private void omit(String category,int id,String reason){var omission=new Omission(category,id,reason);if(!omissions.contains(omission))omissions.add(omission);}
   private <T> void exclude(List<T> items,ToIntFunction<T> id,String category,Map<Integer,Integer> furniture) {
      items.removeIf(item->{int source=id.applyAsInt(item);if(furniture.containsKey(source))return false;omit(category,source,"Owner furniture was not placed");return true;});
   }
   public static PartialImportPlan create(PresetConfig requested,Map<Integer,Integer> furniture,Map<Integer,Integer> walls){return new PartialImportPlan(requested,furniture,walls);}
   public PresetConfig effective(){return effective;}
   public List<Omission> omissions(){return List.copyOf(omissions);}
   public Map<Integer,String> skippedWired(){return Map.copyOf(skippedWired);}
}
