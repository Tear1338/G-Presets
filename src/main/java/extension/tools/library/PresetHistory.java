package extension.tools.library;
import extension.tools.presetconfig.*;
import org.json.JSONObject;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.io.IOException;
/** Local append-only revisions, never included in shared presets. */
public final class PresetHistory {
 private PresetHistory() {}
 private static Path directory(String name) throws IOException {
  PresetConfigUtils.resolvePresetFile(name);
  try {
   String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8)));
   Path root=Path.of(PresetConfigUtils.baseDir(),"history");Files.createDirectories(root);
   if(Files.isSymbolicLink(root))throw new IOException("Invalid history directory");
   Path folder=root.resolve(key);Files.createDirectories(folder);
   if(Files.isSymbolicLink(folder))throw new IOException("Invalid history directory");
   return folder;
  }catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
 }
 public static synchronized List<String> list(String name) throws IOException {
  try(var stream=Files.list(directory(name))) {
   return stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).map(p->p.getFileName().toString()).filter(n->n.matches("[0-9]{13}-[a-f0-9-]{36}\\.json")).sorted(Comparator.reverseOrder()).toList();
  }
 }
 public static synchronized void checkpoint(String name,PresetConfig preset) throws IOException {
  List<String> revisions=list(name);
  if(!revisions.isEmpty() && PresetComparison.canonical(read(name,revisions.get(0)).toJsonObject()).equals(PresetComparison.canonical(preset.toJsonObject())))return;
  long timestamp=revisions.isEmpty()?System.currentTimeMillis():Math.max(System.currentTimeMillis(),Long.parseLong(revisions.get(0).substring(0,13))+1);
  Files.writeString(directory(name).resolve(timestamp+"-"+UUID.randomUUID()+".json"),preset.toJsonObject().toString(2),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
 }
 public static synchronized PresetConfig read(String name,String revision) throws IOException {
  if(!list(name).contains(revision))throw new IllegalArgumentException("Unknown history revision");
  return new PresetConfig(new JSONObject(Files.readString(directory(name).resolve(revision),StandardCharsets.UTF_8)));
 }
 public static synchronized void move(String oldName,String newName) throws IOException {
  for(String revision:list(oldName)) {Path target=directory(newName).resolve(revision);if(!Files.exists(target))Files.copy(directory(oldName).resolve(revision),target);}
 }
 public static String label(String revision) {
  return java.time.Instant.ofEpochMilli(Long.parseLong(revision.substring(0,13))).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' ')+"  "+revision.substring(14,22);
 }
}
