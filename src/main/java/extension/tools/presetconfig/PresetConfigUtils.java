package extension.tools.presetconfig;

import extension.GPresets;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class PresetConfigUtils {
   public static final String PRESET_EXT = ".json";
   private static volatile boolean migrationChecked = false;

   public static String baseDir() {
      String override = System.getProperty("gpresets.dataDir");
      if (override != null && !override.isBlank()) {
         new File(override).mkdirs();
         return override;
      }
      String os = System.getProperty("os.name", "").toLowerCase();
      String base;
      if (os.contains("win")) {
         String appData = System.getenv("APPDATA");
         if (appData == null || appData.isEmpty()) {
            appData = Paths.get(System.getProperty("user.home"), "AppData", "Roaming").toString();
         }

         base = Paths.get(appData, "G-Presets").toString();
      } else if (os.contains("mac")) {
         base = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "G-Presets").toString();
      } else {
         String xdg = System.getenv("XDG_DATA_HOME");
         base = xdg != null && !xdg.isBlank() ? Paths.get(xdg, "G-Presets").toString() :
            Paths.get(System.getProperty("user.home"), ".local", "share", "G-Presets").toString();
      }

      (new File(base)).mkdirs();
      return base;
   }

   public static String presetPath() {
      String path = Paths.get(baseDir(), "presets").toString();
      (new File(path)).mkdirs();
      migrateLegacyPresetsIfNeeded(path);
      return path;
   }

   private static synchronized void migrateLegacyPresetsIfNeeded(String newPresetsDir) {
      if (!migrationChecked) {
         migrationChecked = true;

         try {
            File legacyDir = legacyPresetsDir();
            if (legacyDir == null || !legacyDir.isDirectory()) {
               return;
            }

            File[] legacyFiles = legacyDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (legacyFiles == null || legacyFiles.length == 0) {
               return;
            }

            File target = new File(newPresetsDir);
            target.mkdirs();
            int copied = 0;

            for(File src : legacyFiles) {
               File dst = new File(target, src.getName());
               if (!dst.exists()) {
                  try {
                     Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                     ++copied;
                  } catch (IOException var11) {
                  }
               }
            }

            if (copied > 0) {
               System.out.println("[G-Presets] migrated " + copied + " preset(s) from " + legacyDir + " to " + target);
            }
         } catch (Exception var12) {
         }

      }
   }

   private static File legacyPresetsDir() {
      try {
         File codeDir = (new File(GPresets.class.getProtectionDomain().getCodeSource().getLocation().toURI())).getParentFile();
         if (codeDir == null) {
            return null;
         } else {
            File legacy = new File(codeDir, "presets");
            return legacy;
         }
      } catch (URISyntaxException var2) {
         return null;
      }
   }

   public static boolean presetExists(String name) {
      File f = resolvePresetFile(name).toFile();
      return f.isFile();
   }

   public static List<String> listPresets() {
      List<String> presets=new ArrayList<>();
      Path directory=Paths.get(presetPath());
      try (var stream=Files.list(directory)) {
         for(Path path:(Iterable<Path>)stream::iterator) {
            if(Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS) && path.getFileName().toString().endsWith(PRESET_EXT)) {
               String n=path.getFileName().toString();presets.add(n.substring(0,n.length()-PRESET_EXT.length()));
            } else if(Files.isDirectory(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
               try(var children=Files.list(path)) {
                  for(Path child:(Iterable<Path>)children::iterator) {
                     String n=child.getFileName().toString();
                     if(Files.isRegularFile(child,java.nio.file.LinkOption.NOFOLLOW_LINKS) && n.endsWith(PRESET_EXT))
                        presets.add(path.getFileName().toString()+"/"+n.substring(0,n.length()-PRESET_EXT.length()));
                  }
               }
            }
         }
      } catch(IOException error) {System.err.println("Cannot list presets: "+error.getMessage());}
      return presets;
   }

   private static void validateComponent(String name) {
      if(name==null || name.isBlank() || name.contains("/") || name.equals(".") || name.equals("..") || name.matches(".*[\\\\:*?\"<>|\\p{Cntrl}].*"))
         throw new IllegalArgumentException("Invalid preset name");
   }

   public static Path resolvePresetFile(String name) {
      if(name==null)throw new IllegalArgumentException("Invalid preset name");
      String[] parts=name.split("/",-1);
      if(parts.length<1 || parts.length>2)throw new IllegalArgumentException("Invalid preset path");
      for(String part:parts)validateComponent(part);
      Path directory=Paths.get(presetPath()).toAbsolutePath().normalize();
      if(parts.length==2) {
         directory=directory.resolve(parts[0]);
         if(!Files.isDirectory(directory,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Preset folder not found");
      }
      Path file=directory.resolve(parts[parts.length-1]+PRESET_EXT).normalize();
      if(!file.getParent().equals(directory) || Files.isSymbolicLink(file))throw new IllegalArgumentException("Invalid preset path");
      return file;
   }

   public static void moveToFolder(String preset,String folder) throws IOException {
      validateComponent(folder);
      Path source=resolvePresetFile(preset);
      Path root=Paths.get(presetPath()).toAbsolutePath().normalize();
      Path directory=root.resolve(folder);
      if(!Files.exists(directory,java.nio.file.LinkOption.NOFOLLOW_LINKS))Files.createDirectory(directory);
      if(!Files.isDirectory(directory,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid destination folder");
      Path destination=directory.resolve(source.getFileName());
      if(!source.equals(destination))Files.move(source,destination);
   }

   public static boolean savePreset(String name, PresetConfig config) {
      Path temporary = null;
      try {
         Path destination = resolvePresetFile(name);
         // Serialize first: a bad preset must never truncate an existing good export.
         String contents = config.toJsonObject().toString(4);
         temporary = Files.createTempFile(destination.getParent(), ".preset-", ".tmp");
         Files.write(temporary, contents.getBytes(StandardCharsets.UTF_8));
         try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
         }
         return true;
      } catch (IOException | RuntimeException error) {
         System.err.println("Cannot save preset: " + error.getMessage());
         return false;
      } finally {
         if (temporary != null) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
         }
      }
   }

   public static PresetConfig loadPreset(String name) {
      File file = resolvePresetFile(name).toFile();
      if (file.exists() && file.isFile()) {
         try {
            String contents = String.join("\n", Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
            return new PresetConfig(new JSONObject(contents));
         } catch (IOException | RuntimeException e) {
            System.err.println("Cannot load preset: " + e.getMessage());
         }
      }

      return null;
   }
}
