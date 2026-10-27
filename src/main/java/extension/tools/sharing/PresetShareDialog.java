package extension.tools.sharing;

import extension.tools.preview.*;
import extension.tools.presetconfig.PresetConfig;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;

public final class PresetShareDialog {
   private PresetShareDialog() { }
   public static void show(Window owner,PresetConfig preset,PresetPreviewModel model,String defaultName) {
      Dialog<ButtonType> dialog=new Dialog<>();extension.ui.ReadableTheme.apply(dialog.getDialogPane());dialog.initOwner(owner);dialog.setTitle("Share preset");dialog.setResizable(true);
      PresetMetadata previous=preset.getMetadata();
      TextField name=new TextField(previous==null?defaultName:previous.name());
      TextArea description=new TextArea(previous==null?"":previous.description());description.setPrefRowCount(2);description.setWrapText(true);
      PresetPreviewPane preview=new PresetPreviewPane(model,defaultName,null,null);preview.setShareMode();
      preview.setPrefSize(850,430);
      Label info=new Label("Adjust the view and let any new images finish loading, then save. The file includes the name, description, preview image and preset.");info.setWrapText(true);
      Label error=new Label();error.setWrapText(true);
      VBox content=new VBox(8,new Label("Name"),name,new Label("Description"),description,preview,info,error);VBox.setVgrow(preview,Priority.ALWAYS);
      dialog.getDialogPane().setContent(content);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
      Button save=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);save.setText("Save shared preset");
      save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
         e.consume();
         try {
            PresetMetadata data=new PresetMetadata(name.getText().strip(),description.getText(),PreviewImages.png(preview.getCanvas()));
            PresetConfig copy=new PresetConfig(preset.toJsonObject());copy.setMetadata(data);
            FileChooser chooser=new FileChooser();chooser.setTitle("Save shared preset");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-Presets JSON","*.json"));chooser.setInitialFileName("shared-preset.json");
            java.io.File target=chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if(target==null)return;
            Files.writeString(target.toPath(),copy.toJsonObject().toString(2),java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            dialog.close();
         } catch(Exception ex){error.setText("Cannot save: "+ex.getMessage()+". Existing files are preserved.");}
      });
      dialog.setOnHidden(e->preview.close());dialog.showAndWait();
   }
}
