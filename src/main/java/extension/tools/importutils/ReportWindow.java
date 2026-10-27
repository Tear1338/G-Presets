package extension.tools.importutils;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.json.JSONObject;
import java.nio.file.*;

public final class ReportWindow {
   private ReportWindow() { }
   public static void show(Window owner,String title,String summary,JSONObject report) {
      String snapshot=report.toString(2);
      Platform.runLater(()->{
         Stage stage=new Stage();if(owner!=null)stage.initOwner(owner);stage.setTitle("G-Presets - "+title);
         TextArea text=new TextArea(summary);text.setEditable(false);text.setWrapText(true);
         Label status=new Label();status.setWrapText(true);
         Button save=new Button("Save report");save.setOnAction(e->{try {
            FileChooser chooser=new FileChooser();chooser.setInitialFileName("gpresets-report.json");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON report","*.json"));
            java.io.File target=chooser.showSaveDialog(stage);if(target==null)return;
            Files.writeString(target.toPath(),snapshot,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);status.setText("Report saved");
         }catch(Exception ex){status.setText("Cannot save report: "+ex.getMessage());}});
         Button close=new Button("Close");close.setOnAction(e->stage.close());
         VBox content=new VBox(10,text,status,new HBox(10,save,close));content.setStyle("-fx-padding:16;");VBox.setVgrow(text,Priority.ALWAYS);
         extension.ui.ReadableTheme.apply(content);
         stage.setScene(new Scene(content,650,500));stage.show();
      });
   }
}
