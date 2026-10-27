package extension.ui;

import javafx.scene.Parent;

/** Use the original G-Earth dark stylesheet in every extension window. */
public final class ReadableTheme {
   private ReadableTheme() { }
   public static void apply(Parent root) {
      String css=ReadableTheme.class.getResource("styles.css").toExternalForm();
      if(!root.getStylesheets().contains(css))root.getStylesheets().add(css);
      root.getStyleClass().removeAll("gpresets-readable","g-light");
      if(!root.getStyleClass().contains("gpresets-theme"))root.getStyleClass().add("gpresets-theme");
      if(!root.getStyleClass().contains("g-dark"))root.getStyleClass().add("g-dark");
   }
}
