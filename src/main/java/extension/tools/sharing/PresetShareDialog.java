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
      CheckBox includeName=new CheckBox("Include name"),includeDescription=new CheckBox("Include description"),includeImage=new CheckBox("Include preview image");
      includeName.setSelected(true);includeDescription.setSelected(true);includeImage.setSelected(true);
      Label error=new Label();error.setWrapText(true);
      VBox content=new VBox(8,new Label("Name"),name,new Label("Description"),description,new FlowPane(12,8,includeName,includeDescription,includeImage),preview,info,error);VBox.setVgrow(preview,Priority.ALWAYS);
      dialog.getDialogPane().setContent(content);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
      Button save=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);save.setText("Review & save shared preset");
      save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
         e.consume();
         try {
            PresetConfig copy=SharePrivacy.prepare(preset,name.getText().strip(),description.getText(),includeImage.isSelected()?PreviewImages.png(preview.getCanvas()):"",includeName.isSelected(),includeDescription.isSelected(),includeImage.isSelected());
            String payload=copy.toJsonObject().toString(2);
            Dialog<ButtonType> review=new Dialog<>();review.initOwner(dialog.getDialogPane().getScene().getWindow());review.setTitle("Privacy review - exact exported file");review.setResizable(true);extension.ui.ReadableTheme.apply(review.getDialogPane());
            TextArea summary=new TextArea(SharePrivacy.summary(copy));summary.setEditable(false);summary.setWrapText(true);summary.setPrefRowCount(7);
            TextArea json=new TextArea(payload);json.setEditable(false);json.setPrefRowCount(18);
            VBox reviewed=new VBox(8,summary,json);reviewed.setPrefWidth(780);VBox.setVgrow(json,Priority.ALWAYS);review.getDialogPane().setContent(reviewed);review.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
            ((Button)review.getDialogPane().lookupButton(ButtonType.OK)).setText("Choose export file");
            if(review.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
            FileChooser chooser=new FileChooser();chooser.setTitle("Save shared preset");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-Presets JSON","*.json"));chooser.setInitialFileName("shared-preset.json");
            java.io.File target=chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if(target==null)return;
            Files.writeString(target.toPath(),payload,java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            dialog.close();
         } catch(Exception ex){error.setText("Cannot save: "+ex.getMessage()+". Existing files are preserved.");}
      });
      dialog.setOnHidden(e->preview.close());dialog.showAndWait();
   }
}
