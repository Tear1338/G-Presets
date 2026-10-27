package extension.ui;

import javafx.scene.Parent;

/** Consistent black text on light surfaces, including windows owned by dark G-Earth. */
public final class ReadableTheme {
   private ReadableTheme() { }
   public static void apply(Parent root) {
      String css=ReadableTheme.class.getResource("styles.css").toExternalForm();
      if(!root.getStylesheets().contains(css))root.getStylesheets().add(css);
      if(!root.getStyleClass().contains("gpresets-readable"))root.getStyleClass().add("gpresets-readable");
   }
}
