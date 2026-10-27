package extension.tools.library;
import extension.tools.presetconfig.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.List;
public final class PresetLibraryDialog {
 private PresetLibraryDialog() {}
 public static void show(Window owner,String selected,Runnable reloaded) {
  Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Compare presets & history");dialog.setResizable(true);extension.ui.ReadableTheme.apply(dialog.getDialogPane());
  List<String> names=PresetConfigUtils.listPresets().stream().sorted().toList();
  ComboBox<String> left=new ComboBox<>(),right=new ComboBox<>();left.getItems().setAll(names);right.getItems().setAll(names);left.setValue(selected);right.setValue(selected);
  TextArea result=new TextArea();result.setEditable(false);result.setWrapText(true);
  Button compare=new Button("Compare A -> B");compare.setOnAction(e->{try{result.setText(PresetComparison.compare(load(left.getValue()),load(right.getValue())));}catch(Exception ex){result.setText(ex.getMessage());}});
  ListView<String> revisions=new ListView<>();revisions.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(String s,boolean empty){super.updateItem(s,empty);setText(empty?null:PresetHistory.label(s));}});
  Runnable refresh=()->{try{if(left.getValue()!=null){PresetHistory.checkpoint(left.getValue(),load(left.getValue()));revisions.getItems().setAll(PresetHistory.list(left.getValue()));}}catch(Exception ex){result.setText("History: "+ex.getMessage());}};
  left.valueProperty().addListener((o,a,b)->refresh.run());refresh.run();
  Button snapshot=new Button("Save revision");snapshot.setOnAction(e->refresh.run());
  Button inspect=new Button("Compare revision to current");inspect.setOnAction(e->{try{result.setText(PresetComparison.compare(PresetHistory.read(left.getValue(),revisions.getSelectionModel().getSelectedItem()),load(left.getValue())));}catch(Exception ex){result.setText("Choose a revision: "+ex.getMessage());}});
  Button restore=new Button("Restore revision");restore.setOnAction(e->{try {
   String name=left.getValue();PresetConfig revision=PresetHistory.read(name,revisions.getSelectionModel().getSelectedItem());
   Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Restore this revision? The current file is saved in history first.",ButtonType.CANCEL,ButtonType.OK);confirm.initOwner(dialog.getDialogPane().getScene().getWindow());extension.ui.ReadableTheme.apply(confirm.getDialogPane());
   if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
   if(!PresetConfigUtils.savePreset(name,revision))throw new IllegalStateException("Restore failed; current file preserved");
   refresh.run();reloaded.run();result.setText("Revision restored. Select the preset again before importing.");
  }catch(Exception ex){result.setText("Cannot restore: "+ex.getMessage());}});
  VBox content=new VBox(10,new Label("A / history of"),left,new Label("B"),right,compare,new Label("Local revisions (automatic on save; not shared)"),revisions,new FlowPane(8,8,snapshot,inspect,restore),result);VBox.setVgrow(result,Priority.ALWAYS);content.setPrefSize(780,600);revisions.setPrefHeight(140);
  dialog.getDialogPane().setContent(content);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.showAndWait();
 }
 private static PresetConfig load(String name){PresetConfig preset=name==null?null:PresetConfigUtils.loadPreset(name);if(preset==null)throw new IllegalArgumentException("Select a readable preset");return preset;}
}
