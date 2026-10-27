package extension.tools.importutils;
import extension.tools.presetconfig.*;
import extension.tools.postconfig.*;
import furnidata.FurniDataTools;
import game.*;
import gearth.extensions.parsers.HProductType;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;
import org.json.*;
public final class ReplacementDialog {
 private ReplacementDialog() {}
 public static PresetConfig show(Window owner,PresetConfig preset,PostConfig options,FurniDataTools data,Inventory inventory,BCCatalog catalog) {
  if(data==null || !data.isReady() || inventory.getState()!=Inventory.InventoryState.LOADED)throw new IllegalStateException("Load furnidata and inventory first");
  JSONObject audit=AvailabilityAudit.inspect(preset,options,data,inventory,catalog,false);
  Map<Boolean,SortedSet<String>> candidates=Map.of(false,new TreeSet<>(),true,new TreeSet<>());
  for(var item:inventory.getInventoryItems()) {
   boolean wall=item.getType()!=HProductType.FloorItem;String name=wall?data.getWallItemName(item.getTypeId()):data.getFloorItemName(item.getTypeId());
   if(name!=null && !name.startsWith("wf_") && !name.equals("poster") && extension.tools.presetconfig.wired.WiredKind.fromClassName(name)==extension.tools.presetconfig.wired.WiredKind.NONE)candidates.get(wall).add(name);
  }
  Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Replace missing furniture");dialog.setResizable(true);extension.ui.ReadableTheme.apply(dialog.getDialogPane());
  VBox rows=new VBox(10);Map<PresetReplacements.Key,ComboBox<String>> choices=new LinkedHashMap<>();
  for(Object value:audit.getJSONArray("items")) {
   JSONObject row=(JSONObject)value;String status=row.getString("status");
   if(!status.equals("MISSING") && !status.equals("UNKNOWN_FURNITURE"))continue;
   var key=new PresetReplacements.Key(row.getString("kind").equals("Wall"),row.getString("className"));
   Label label=new Label(key+" - required: "+row.getInt("required")+", inventory: "+row.optInt("inventory",0));
   if(PresetReplacements.protectedClass(preset,key)){rows.getChildren().add(new VBox(3,label,new Label("Protected configuration/variant: obtain the original furniture.")));continue;}
   ComboBox<String> box=new ComboBox<>();box.getItems().setAll(candidates.get(key.wall()));box.setPromptText("Keep original (or choose inventory furniture)");box.setMaxWidth(Double.MAX_VALUE);choices.put(key,box);rows.getChildren().add(new VBox(3,label,box));
  }
  if(rows.getChildren().isEmpty())rows.getChildren().add(new Label("No missing furniture reported for the selected item source."));
  Label warning=new Label("Replaces all items of the chosen class, for this session only. IDs and WIRED references are retained; furniture states are reset. Dimensions, interactions, variables and WIRED conditions may behave differently. Inventory quantities are checked again before import. Configured furniture and WIRED boxes are protected.");warning.setWrapText(true);
  CheckBox accept=new CheckBox("I reviewed the replacements and their possible effect on WIRED");
  Label error=new Label();error.setWrapText(true);
  ScrollPane scroll=new ScrollPane(rows);scroll.setFitToWidth(true);scroll.setPrefHeight(340);
  VBox content=new VBox(12,warning,scroll,accept,error);content.setPrefWidth(680);dialog.getDialogPane().setContent(content);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
  PresetConfig[] result={null};Button apply=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);apply.setText("Apply to session");apply.disableProperty().bind(accept.selectedProperty().not());
  apply.addEventFilter(javafx.event.ActionEvent.ACTION,e->{try {
   Map<PresetReplacements.Key,String> mapping=new LinkedHashMap<>();choices.forEach((k,v)->{if(v.getValue()!=null)mapping.put(k,v.getValue());});
   if(mapping.isEmpty())throw new IllegalArgumentException("Choose at least one replacement");
   result[0]=PresetReplacements.apply(preset,mapping);
  }catch(Exception ex){e.consume();error.setText(ex.getMessage());}});
  return dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK?result[0]:null;
 }
}
