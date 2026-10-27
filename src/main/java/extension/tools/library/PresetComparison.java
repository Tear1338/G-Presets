package extension.tools.library;
import extension.tools.presetconfig.PresetConfig;
import org.json.*;
import java.util.*;
/** Compare every serialized field; object keys are unordered, array order is preserved. */
public final class PresetComparison {
 private PresetComparison() {}
 public static String canonical(Object value) {
  if(value==null)return "<missing>";
  if(value instanceof JSONObject object)return object.keySet().stream().sorted().map(k->JSONObject.quote(k)+":"+canonical(object.get(k))).collect(java.util.stream.Collectors.joining(",","{","}"));
  if(value instanceof JSONArray array){List<String> values=new ArrayList<>();for(Object v:array)values.add(canonical(v));return "["+String.join(",",values)+"]";}
  return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
 }
 public static String compare(PresetConfig before,PresetConfig after) {
  StringBuilder out=new StringBuilder("Preset comparison: A -> B\nFurniture matched by saved ID within floor/wall sections. Independent exports may use different IDs.\n\n");
  if(canonical(before.toJsonObject()).equals(canonical(after.toJsonObject())))out.append("No differences.\n");else diff("",before.toJsonObject(),after.toJsonObject(),out);
  return out.toString();
 }
 private static void diff(String path,Object a,Object b,StringBuilder out) {
  if(canonical(a).equals(canonical(b)))return;
  if(a instanceof JSONObject x && b instanceof JSONObject y) {
   Set<String> keys=new TreeSet<>(x.keySet());keys.addAll(y.keySet());for(String key:keys)diff(path.isEmpty()?key:path+"."+key,x.has(key)?x.get(key):null,y.has(key)?y.get(key):null,out);
  }else if(a instanceof JSONArray x && b instanceof JSONArray y && keyed(x) && keyed(y)) {
   Map<String,Object> left=index(x),right=index(y);Set<String> keys=new TreeSet<>(left.keySet());keys.addAll(right.keySet());for(String key:keys)diff(path+"["+key+"]",left.get(key),right.get(key),out);
  }else out.append(a==null?"ADDED":b==null?"REMOVED":"CHANGED").append(" ").append(path).append("\n  A: ").append(display(path,a)).append("\n  B: ").append(display(path,b)).append("\n\n");
 }
 private static String display(String path,Object v){return path.endsWith("imagePng")?"[image "+(v==null?0:String.valueOf(v).length())+" characters]":canonical(v);}
 private static boolean keyed(JSONArray values) {
  Set<String> ids=new HashSet<>();for(Object value:values){if(!(value instanceof JSONObject o))return false;String key=o.has("id")?"id":o.has("wiredId")?"wiredId":null;if(key==null || !ids.add(key+"="+o.get(key)))return false;}return true;
 }
 private static Map<String,Object> index(JSONArray array){Map<String,Object> result=new TreeMap<>();for(Object v:array){JSONObject o=(JSONObject)v;String key=o.has("id")?"id":"wiredId";result.put(key+"="+o.get(key),o);}return result;}
}
