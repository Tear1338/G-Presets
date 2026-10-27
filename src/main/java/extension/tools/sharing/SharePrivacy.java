package extension.tools.sharing;
import extension.tools.presetconfig.PresetConfig;
import org.json.*;
/** Explicit allow-list for optional metadata. Game configuration is never silently removed. */
public final class SharePrivacy {
 private SharePrivacy() {}
 public static PresetConfig prepare(PresetConfig source,String name,String description,String png,boolean includeName,boolean includeDescription,boolean includeImage) {
  PresetConfig copy=new PresetConfig(source.toJsonObject());copy.setMetadata(null);
  if(includeName || includeDescription || includeImage)copy.setMetadata(new PresetMetadata(includeName?name:"",includeDescription?description:"",includeImage?png:""));
  return copy;
 }
 public static String summary(PresetConfig preset) {
  JSONObject json=preset.toJsonObject();StringBuilder out=new StringBuilder("Review the exact JSON below. This is a local file export; no upload occurs.\n\n");
  out.append("Floor furniture: ").append(preset.getFurniture().size()).append("; wall furniture: ").append(preset.getWallFurniture().size()).append("; WIRED configurations: ").append(preset.getPresetWireds().getAll().size()).append("\n");
  PresetMetadata metadata=preset.getMetadata();out.append("Optional metadata: ").append(metadata==null?"none":("name "+!metadata.name().isEmpty()+", description "+!metadata.description().isEmpty()+", image "+!metadata.imagePng().isEmpty())).append("\n");
  out.append("Room links: ").append(preset.getRoomLinks().size()).append("; ad backgrounds: ").append(preset.getAdsBackgrounds().size()).append("; object-variable groups: ").append(preset.getObjectVariables().size()).append("\n");
  out.append("Furniture names, saved IDs, WIRED text, variable names/values, URLs and room destinations can remain in gameplay configuration. Review these fields; removing them may break the preset. Local history, inventory and import reports are not attached.\n");
  return out.toString();
 }
}
