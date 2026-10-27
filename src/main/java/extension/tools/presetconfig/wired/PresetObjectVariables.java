package extension.tools.presetconfig.wired;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;
/** Writable furniture properties and values of room variables defined by this preset. */
public final class PresetObjectVariables {
   public static final Set<String> BUILTINS=Set.of("-126","~113","~110","~111","~112","~100","~102","~101","~121","~146","~103");
   public final int target,objectId;
   public final Map<String,Integer> values;
   private Set<String> furnitureReferenceKeys = new HashSet<>();
   private Set<String> writeOnlyVariableIds = new HashSet<>();
   public PresetObjectVariables(int target,int id,Map<String,Integer> values) {
      if(target!=0 && target!=-10) throw new IllegalArgumentException("Unsupported preset variable holder");
      for(String key:values.keySet()) if(key==null || key.isEmpty() || (target==0 && !BUILTINS.contains(key) && !PresetWiredBase.isLocalVariableId(key))) throw new IllegalArgumentException("Unknown writable furniture property");
      this.target=target;this.objectId=id;this.values=Collections.unmodifiableMap(new LinkedHashMap<>(values));
   }
   public PresetObjectVariables(JSONObject j) {
      this(j.getInt("target"),j.getInt("objectId"),read(j.getJSONObject("values")));
      JSONArray writes=j.optJSONArray("writeOnlyVariableIds");
      if(writes!=null) for(Object key:writes) { if(!values.containsKey(key)) throw new IllegalArgumentException("Unknown write-only variable");writeOnlyVariableIds.add(key.toString()); }
      JSONArray refs=j.optJSONArray("furnitureReferenceKeys");
      if(refs!=null) for(Object key:refs) {
         if(!"~103".equals(key) || !values.containsKey(key)) throw new IllegalArgumentException("Unknown furniture reference property");
         furnitureReferenceKeys.add(key.toString());
      }
   }
   public PresetObjectVariables withWriteOnlyVariables(Set<String> ids) {
      if(!values.keySet().containsAll(ids)) throw new IllegalArgumentException("Unknown write-only variable");
      PresetObjectVariables copy=new PresetObjectVariables(toJsonObject());copy.writeOnlyVariableIds.addAll(ids);return copy;
   }
   public PresetObjectVariables withFurnitureReference(String key) {
      if(!"~103".equals(key) || !values.containsKey(key)) throw new IllegalArgumentException("Unknown furniture reference property");
      PresetObjectVariables copy=new PresetObjectVariables(toJsonObject());copy.furnitureReferenceKeys.add(key);return copy;
   }
   private static Map<String,Integer> read(JSONObject j) { Map<String,Integer> m=new LinkedHashMap<>();for(String key:j.keySet())m.put(key,j.getInt(key));return m; }
   public PresetObjectVariables translate(int dx,int dy) {
      Map<String,Integer> m=new LinkedHashMap<>(values);
      if(m.containsKey("~110"))m.put("~110",Math.addExact(m.get("~110"),dx));
      if(m.containsKey("~111"))m.put("~111",Math.addExact(m.get("~111"),dy));
      PresetObjectVariables copy=new PresetObjectVariables(target,objectId,m);copy.furnitureReferenceKeys.addAll(furnitureReferenceKeys);copy.writeOnlyVariableIds.addAll(writeOnlyVariableIds);return copy;
   }
   public PresetObjectVariables remap(Map<Integer,Integer> furniture,Map<String,String> variables) {
      Integer id=target==-10?0:furniture.get(objectId);
      if(id==null) throw new IllegalArgumentException("Missing variable holder " + objectId);
      Map<String,Integer> m=new LinkedHashMap<>();
      for(Map.Entry<String,Integer> e:values.entrySet()) {
         String key=target==0 && BUILTINS.contains(e.getKey())?e.getKey():variables.get(e.getKey());
         if(key==null)throw new IllegalArgumentException("Unresolved room variable " + e.getKey());
         Integer value=furnitureReferenceKeys.contains(e.getKey())?furniture.get(e.getValue()):e.getValue();
         if(value==null)throw new IllegalArgumentException("Missing teleporter destination " + e.getValue());
         m.put(key,value);
      }
      PresetObjectVariables copy=new PresetObjectVariables(target,id,m);
      for(String old:writeOnlyVariableIds) copy.writeOnlyVariableIds.add(target==0 && BUILTINS.contains(old)?old:variables.get(old));
      return copy;
   }
   public List<HPacket> packets() {
      List<HPacket> packets=new ArrayList<>();
      for(Map.Entry<String,Integer> e:values.entrySet())packets.add(new HPacket("WiredSetObjectVariableValue",HMessage.Direction.TOSERVER,target,objectId,e.getKey(),e.getValue(),target==-10 || BUILTINS.contains(e.getKey()) || writeOnlyVariableIds.contains(e.getKey())?0:1));
      return packets;
   }
   public JSONObject toJsonObject() { return new JSONObject().put("target",target).put("objectId",objectId).put("values",new JSONObject(values)).put("furnitureReferenceKeys",new JSONArray(furnitureReferenceKeys)).put("writeOnlyVariableIds",new JSONArray(writeOnlyVariableIds)); }
   public boolean matches(Map<String,Integer> actual) { return values.entrySet().stream().allMatch(e->e.getValue().equals(actual.get(e.getKey()))); }
}
