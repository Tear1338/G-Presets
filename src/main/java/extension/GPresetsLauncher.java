package extension;

import extension.logger.Logger;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.ThemedExtensionFormCreator;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.stage.Stage;

public class GPresetsLauncher extends ThemedExtensionFormCreator {
   protected String getTitle() {
      return "G-Presets - Building & Wired Presets - " + ((ExtensionInfo)GPresets.class.getAnnotation(ExtensionInfo.class)).Version();
   }

   protected URL getFormResource() {
      return this.getClass().getResource("ui/gpresets.fxml");
   }

   protected void initialize(Stage primaryStage) {
      extension.ui.ReadableTheme.apply(primaryStage.getScene().getRoot());
   }

   public static void main(String[] args) {
      boolean debug = false;

      for(String a : args) {
         if ("-d".equals(a) || "--debug".equals(a)) {
            debug = true;
            break;
         }
      }

      if (debug) {
         Logger.setVerboseConsole(true);
         Path logFile = Paths.get(System.getProperty("user.dir"), "app.log");
         Logger.enableFileOutput(logFile);
         System.out.println("[G-Presets] debug mode enabled - mirroring logs to console and " + logFile);
      }

      runExtensionForm(args, GPresetsLauncher.class);
   }
}
