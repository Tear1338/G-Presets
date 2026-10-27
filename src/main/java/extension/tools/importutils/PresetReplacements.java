package extension.tools.importutils;
import extension.tools.presetconfig.*;
import extension.tools.presetconfig.wired.*;
import java.util.*;
/** Session-only substitutions retain furniture IDs so references still point to the same item. */
public final class PresetReplacements {
 private PresetReplacements() {}
 public record Key(boolean wall,String classname) {public String toString(){return (wall?"Wall: ":"Floor: ")+classname;}}
 public static boolean protectedClass(PresetConfig preset,Key key) {
  if(key.classname().startsWith("wf_") || WiredKind.fromClassName(key.classname())!=WiredKind.NONE || key.classname().equals("poster"))return true;
  if(key.wall())return false;
  Set<Integer> ids=new HashSet<>();for(var f:preset.getFurniture())if(f.getClassName().equals(key.classname()))ids.add(f.getFurniId());
  return preset.getPresetWireds().getAll().stream().anyMatch(w->ids.contains(w.getWiredId())) || preset.getContracts().stream().anyMatch(c->ids.contains(c.furniId)) || preset.getChests().stream().anyMatch(c->ids.contains(c.furniId)) || preset.getFurnitureLabels().stream().anyMatch(c->ids.contains(c.furniId)) || preset.getAdsBackgrounds().stream().anyMatch(c->ids.contains(c.getFurniId()));
 }
 public static PresetConfig apply(PresetConfig source,Map<Key,String> mapping) {
  PresetConfig copy=new PresetConfig(source.toJsonObject());
  for(var entry:mapping.entrySet()) {
   String target=entry.getValue();Key key=entry.getKey();
   if(target==null || target.isBlank())throw new IllegalArgumentException("Choose a replacement");
   if(target.equals(key.classname()))continue;
   if(protectedClass(source,key) || target.startsWith("wf_") || WiredKind.fromClassName(target)!=WiredKind.NONE || target.equals("poster"))throw new IllegalArgumentException("Configured furniture, WIRED and poster variants cannot be substituted automatically");
  }
  for(var f:copy.getFurniture()){String target=mapping.get(new Key(false,f.getClassName()));if(target!=null && !target.equals(f.getClassName())){f.setClassName(target);f.setState(null);}}
  for(var f:copy.getWallFurniture()){String target=mapping.get(new Key(true,f.getClassName()));if(target!=null && !target.equals(f.getClassName())){f.setClassName(target);f.setState(null);}}
  return copy;
 }
}
