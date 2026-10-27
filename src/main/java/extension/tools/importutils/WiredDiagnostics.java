package extension.tools.importutils;

import extension.tools.presetconfig.*;
import extension.tools.presetconfig.wired.*;
import org.json.*;
import java.util.*;

/** Diagnostics distinguish invalid local data, external references and live gameplay. */
public final class WiredDiagnostics {
   private WiredDiagnostics() { }
   public static JSONObject inspect(PresetConfig preset) {
      JSONArray issues=new JSONArray();Set<Integer> floor=new HashSet<>(),wall=new HashSet<>();
      for(var f:preset.getFurniture())if(!floor.add(f.getFurniId()))issue(issues,"ERROR","Duplicate floor ID "+f.getFurniId());
      for(var f:preset.getWallFurniture())if(!wall.add(f.getFurniId()))issue(issues,"ERROR","Duplicate wall ID "+f.getFurniId());
      Set<String> variables=new HashSet<>();
      for(var v:preset.getPresetWireds().getVariables())if(v.variableId!=null && !v.variableId.isEmpty() && !variables.add(v.variableId))issue(issues,"ERROR","Duplicate variable definition "+v.variableId);
      try{WiredImportPlanner.orderVariables(preset.getPresetWireds().getVariables());}catch(RuntimeException e){issue(issues,"ERROR",e.getMessage());}
      int generated=0;Map<String,Integer> families=new TreeMap<>();Set<Integer> configured=new HashSet<>();
      for(var wired:preset.getPresetWireds().getAll()) {
         families.merge(wired.getClass().getSimpleName().replace("PresetWired",""),1,Integer::sum);
         if(!configured.add(wired.getWiredId()))issue(issues,"ERROR","Duplicate WIRED configuration "+wired.getWiredId());
         if(!floor.contains(wired.getWiredId()))issue(issues,"WARNING","WIRED owner outside preset: "+wired.getWiredId());
         try{wired.toPacket();generated++;}catch(RuntimeException e){issue(issues,"ERROR","WIRED "+wired.getWiredId()+": "+e.getMessage());}
         Set<Integer> selected=new HashSet<>(wired.getItems());selected.addAll(wired.getSecondItems());
         for(int id:selected)if(id!=0 && !(id<0?wall.contains(-id):floor.contains(id)))issue(issues,"WARNING","WIRED "+wired.getWiredId()+" references furniture outside preset: "+id);
         for(String id:wired.getVariableIds())if(PresetWiredBase.isLocalVariableId(id) && !variables.contains(id))issue(issues,"WARNING","WIRED "+wired.getWiredId()+" needs external variable: "+id);
      }
      for(var label:preset.getFurnitureLabels())try{label.textPacket();if(!label.isRegion())label.settingsPacket();}catch(RuntimeException e){issue(issues,"ERROR","Label/region "+label.furniId+": "+e.getMessage());}
      for(var chest:preset.getChests())try{chest.packets();}catch(RuntimeException e){issue(issues,"ERROR","Chest "+chest.furniId+": "+e.getMessage());}
      for(var link:preset.getWiredLinks())try{link.savePacket();}catch(RuntimeException e){issue(issues,"ERROR","Link "+link.furniId+": "+e.getMessage());}
      return new JSONObject().put("configurations",preset.getPresetWireds().getAll().size()).put("serialized",generated).put("families",families).put("issues",issues).put("gameplay","NOT_TESTED");
   }
   public static void checkMessages(PresetConfig preset,JSONObject report,java.util.function.BiPredicate<gearth.protocol.HMessage.Direction,String> available) {
      Map<String,Set<String>> required=new LinkedHashMap<>();required.put("TOSERVER",new TreeSet<>());required.put("TOCLIENT",new TreeSet<>());
      Map<String,String> saves=Map.of("Variable","UpdateVariable","Condition","UpdateCondition","Effect","UpdateAction","Trigger","UpdateTrigger","Addon","UpdateAddon","Selector","UpdateSelector");
      for(String family:report.getJSONObject("families").keySet())if(saves.containsKey(family))required.get("TOSERVER").add(saves.get(family));
      if(!preset.getPresetWireds().getAll().isEmpty()){required.get("TOCLIENT").add("WiredSaveSuccess");required.get("TOCLIENT").add("WiredValidationError");}
      if(!preset.getWiredLinks().isEmpty()){required.get("TOSERVER").addAll(List.of("WiredLinkGetConfiguration","WiredLinkSaveConfiguration"));required.get("TOCLIENT").add("WiredLinkConfiguration");}
      if(!preset.getContracts().isEmpty()){required.get("TOSERVER").addAll(List.of("WiredOpenContract","WiredUpdateContract"));required.get("TOCLIENT").add("WiredContractContents");}
      if(!preset.getChests().isEmpty())required.get("TOSERVER").addAll(List.of("SetChestOptions","SetChestPreferences","SetChestNotificationPreferences"));
      if(!preset.getRoomLinks().isEmpty() || !preset.getObjectVariables().isEmpty()){required.get("TOSERVER").addAll(List.of("WiredSetObjectVariableValue","WiredGetVariablesForObject"));required.get("TOCLIENT").add("WiredVariablesForObject");}
      if(preset.getWiredRoomSettings()!=null && preset.getWiredRoomSettings().completeRoom){required.get("TOSERVER").addAll(List.of("WiredGetRoomSettings","WiredSetRoomSettings"));required.get("TOCLIENT").add("WiredRoomSettings");}
      for(var label:preset.getFurnitureLabels()){required.get("TOSERVER").add(label.isRegion()?"SetFurnitureRegionData":"SetFurnitureLabelData");if(!label.isRegion())required.get("TOSERVER").add("SetFurnitureLabelSettings");required.get("TOCLIENT").add("FurnitureLabelsAndRegionsUpdate");}
      JSONArray messages=new JSONArray();report.put("messages",messages);
      for(var direction:required.entrySet())for(String name:direction.getValue()) {
         boolean known=available.test(gearth.protocol.HMessage.Direction.valueOf(direction.getKey()),name);
         messages.put(new JSONObject().put("direction",direction.getKey()).put("name",name).put("known",known));
         if(!known)issue(report.getJSONArray("issues"),"WARNING","Packet catalog missing "+direction.getKey()+" "+name+"; connect or update the G-Earth catalog");
      }
   }
   private static void issue(JSONArray issues,String severity,String message){issues.put(new JSONObject().put("severity",severity).put("message",message));}
   public static String text(JSONObject report){StringBuilder s=new StringBuilder("WIRED configuration checks: "+report.getInt("serialized")+"/"+report.getInt("configurations")+" serialized locally\n");JSONObject families=report.getJSONObject("families");for(String family:new TreeSet<>(families.keySet()))s.append(family).append(": ").append(families.getInt(family)).append('\n');for(Object value:report.getJSONArray("issues")){JSONObject issue=(JSONObject)value;s.append(issue.getString("severity")).append(": ").append(issue.getString("message")).append('\n');}if(report.has("messages")){int known=0;for(Object value:report.getJSONArray("messages"))if(((JSONObject)value).getBoolean("known"))known++;s.append("\nPacket catalog: ").append(known).append("/").append(report.getJSONArray("messages").length()).append(" known messages\n");}return s.append("\nThese checks do not prove gameplay compatibility. External references, rights and server behavior need verification in Habbo.").toString();}
}
