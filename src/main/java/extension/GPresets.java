package extension;

import extension.logger.Logger;
import extension.tools.AutoDonator;
import extension.tools.preview.PresetPreviewModel;
import extension.tools.preview.PresetPreviewPane;
import extension.tools.preview.PresetPreviewSession;
import furnidata.details.FloorItemDetails;
import java.util.function.BooleanSupplier;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.Scene;
import extension.tools.GPresetExporter;
import extension.tools.GPresetImporter;
import extension.tools.PresetUtils;
import extension.tools.StackTileSetting;
import extension.tools.importutils.AvailabilityChecker;
import extension.tools.importutils.FurniDropInfo;
import extension.tools.postconfig.FurniPostConfig;
import extension.tools.postconfig.ItemSource;
import extension.tools.postconfig.PostConfig;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.PresetConfigUtils;
import extension.tools.presetconfig.furni.PresetFurni;
import furnidata.FurniDataTools;
import game.BCCatalog;
import game.FloorState;
import game.Inventory;
import game.RoomPermissions;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.HPoint;
import gearth.misc.Cacher;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import org.json.JSONObject;
import utils.Utils;

@ExtensionInfo(
   Title = "G-Presets",
   Description = "edit by 1desirable1337",
   Version = "1.6.1",
   Author = "sirjonasxx & 1desirable1337"
)
public class GPresets extends ExtensionForm {
   public BorderPane logsBorderPane;
   public Label cndConnectedLbl;
   public Label cndRoomLbl;
   public Label cndInventoryLbl;
   public Label cndStackTileLbl;
   public Label cndFurnidataLbl;
   public Label cndPermissionsLbl;
   public Label cndBCShopLbl;
   public ToggleGroup stacktile_tgl;
   public RadioButton onlyInvCbx;
   public RadioButton preferInvCbx;
   public RadioButton preferBcCbx;
   public RadioButton onlyBcCbx;
   public ToggleGroup item_src_tgl;
   public ListView<String> presetListView;
   public TextField presetSearch;
   public Label donateLabel;
   public RadioButton donateMissing;
   public RadioButton donateAll;
   public ToggleGroup donate_tgl;
   public Button availabilityBtn;
   public Button previewBtn;
   public Button pauseImportBtn;
   public Label importProgressLabel;
   public ProgressBar importProgressBar;
   public CheckBox previewBeforeImportCbx;
   private Stage previewStage;
   private String selectedPresetName;
   private final javafx.animation.PauseTransition imagePreloadDelay=new javafx.animation.PauseTransition(Duration.millis(250));
   public Button selfDonateBtn;
   public Button currentPresetBtn;
   public CheckBox allowIncompleteBuildsCbx;
   public TextField furniNamePC_txt;
   public TextField replacementIdPC_txt;
   public TableView<FurniPostConfig> postconfigTable;
   public GridPane pcgrid;
   public Label postconfigErrorLbl;
   public CheckBox noExportWiredCbx;
   public CheckBox noExportFurniVariablesCbx;
   public Slider ratelimiter;
   public CheckBox onTopCbx;
   public CheckBox useRoomFurniCbx;
   public CheckBox noExportWallItemsCbx;
   private List<FurniPostConfig> furniPostConfigs = new ArrayList();
   private Logger logger = new Logger();
   private FurniDataTools furniDataTools = null;
   private Inventory inventory = null;
   private FloorState floorState = null;
   private RoomPermissions permissions = null;
   private BCCatalog catalog = null;
   private volatile boolean isConnected = false;
   private volatile int ownUserId;
   public int getOwnUserId(){return ownUserId;}
   private GPresetExporter exporter = null;
   private GPresetImporter importer = null;
   private AutoDonator autoDonator = null;
   private volatile long latestPingTimestamp = -1L;
   private volatile int ping = 45;
   private volatile double pingVariation = (double)10.0F;
   private StackTileSetting stackTileSetting;

   public GPresets() {
      this.stackTileSetting = StackTileSetting.Large;
   }

   public void initialize() {
      this.setupCache();
      this.previewBeforeImportCbx.selectedProperty().addListener(o->Cacher.put("previewBeforeImport",this.previewBeforeImportCbx.isSelected()));
      this.logsBorderPane.setPadding(new Insets((double)5.0F, (double)5.0F, (double)5.0F, (double)5.0F));
      this.logger.initialize(this.logsBorderPane);
      this.logger.log("Welcome to G-Presets!", "purple");
      this.logger.log("Use the following commands:", "purple");
      this.logger.log("* :exportpreset [all] / :ep [all]", "purple");
      this.logger.log("* :exportpresetwalls / :epw", "purple");
      this.logger.log("* :importpreset [x,y] / :ip [x,y]", "purple");
      this.logger.log("* :abort / :a", "purple");
      this.stacktile_tgl.selectedToggleProperty().addListener((observable) -> {
         String option = ((RadioButton)this.stacktile_tgl.getSelectedToggle()).getText();
         Cacher.put("stacktile", option);
         this.stackTileSetting = StackTileSetting.fromString(option);
         this.updateUI();
      });
      this.donate_tgl.selectedToggleProperty().addListener((observable) -> {
         Object option = ((RadioButton)this.donate_tgl.getSelectedToggle()).getUserData();
         Cacher.put("donationMode", option);
      });
      this.noExportWiredCbx.selectedProperty().addListener((observable) -> {
         Cacher.put("noExportWired", this.noExportWiredCbx.isSelected());
         this.updateUI();
      });
      this.noExportFurniVariablesCbx.selectedProperty().addListener((observable) -> Cacher.put("noExportFurniVariables", this.noExportFurniVariablesCbx.isSelected()));
      this.allowIncompleteBuildsCbx.selectedProperty().addListener((observable) -> Cacher.put("allowIncompleteBuilds", this.allowIncompleteBuildsCbx.isSelected()));
      this.noExportWallItemsCbx.selectedProperty().addListener((observable) -> Cacher.put("noExportWallItems", this.noExportWallItemsCbx.isSelected()));
      this.useRoomFurniCbx.selectedProperty().addListener((observable) -> Cacher.put("useRoomFurni", this.useRoomFurniCbx.isSelected()));
      this.postconfigTable = new TableView();
      this.postconfigTable.setStyle("-fx-focus-color: white;");
      TableColumn<FurniPostConfig, String> furniNameColumn = new TableColumn("Furni name");
      furniNameColumn.setCellValueFactory(new PropertyValueFactory("furniIdentifier"));
      furniNameColumn.setPrefWidth((double)110.0F);
      TableColumn<FurniPostConfig, Integer> existingFurniIdColumn = new TableColumn("Existing furni ID");
      existingFurniIdColumn.setCellValueFactory(new PropertyValueFactory("existingFurniId"));
      existingFurniIdColumn.setPrefWidth((double)120.0F);
      this.postconfigTable.getColumns().addAll(Arrays.asList(furniNameColumn, existingFurniIdColumn));
      this.postconfigTable.setRowFactory((tableView) -> {
         TableRow<FurniPostConfig> row = new TableRow();
         ContextMenu contextMenu = new ContextMenu();
         MenuItem removeMenuItem = new MenuItem("Remove");
         removeMenuItem.setOnAction((event) -> {
            FurniPostConfig config = (FurniPostConfig)row.getItem();
            if (config != null) {
               this.furniPostConfigs.remove(config);
               this.updatePostConfig();
               this.updateFurniPostConfigsView();
            }

         });
         contextMenu.getItems().add(removeMenuItem);
         row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu)null).otherwise(contextMenu));
         return row;
      });
      this.pcgrid.add(this.postconfigTable, 0, 0);
      this.ratelimiter.valueProperty().addListener((observable, oldValue, newValue) -> {
         int val = newValue.intValue();
         Utils.setExtraSleepTime(val);
         Cacher.put("ratelimit", val);
      });
   }

   protected void initExtension() {
      this.floorState = new FloorState(this, this.logger, this::updateUI, () -> {
         this.updateUI();
         this.exporter.reset();
         this.logger.log("Leaving room..", "blue");
      });
      this.inventory = new Inventory(this, this.logger, this::updateUI);
      this.permissions = new RoomPermissions(this, this.logger, this::updateUI);
      this.catalog = new BCCatalog(this, this.logger, this::updateUI);
      this.exporter = new GPresetExporter(this);
      this.importer = new GPresetImporter(this);
      this.autoDonator = new AutoDonator(this);
      this.intercept(HMessage.Direction.TOCLIENT,"UserObject",message->{var packet=message.getPacket();int index=packet.getReadIndex();try{ownUserId=packet.readInteger();}finally{packet.setReadIndex(index);}});
      this.onConnect((host, i, s1, s2, hClient) -> {
         this.showSelfDonateBtn("game-s2.habbo.com".equals(host));
         this.furniDataTools = new FurniDataTools(host, this::updateUI);
      });
      this.intercept(HMessage.Direction.TOSERVER, "LatencyPingRequest", (hMessage) -> this.latestPingTimestamp = System.currentTimeMillis());
      this.intercept(HMessage.Direction.TOCLIENT, "LatencyPingResponse", (hMessage) -> {
         if (this.latestPingTimestamp != -1L) {
            int newPing = (int)(System.currentTimeMillis() - this.latestPingTimestamp) / 2;
            this.pingVariation = this.pingVariation * 0.66 + (double)Math.abs(this.ping - newPing) * 0.34;
            if (this.pingVariation > (double)10.0F) {
               this.pingVariation = (double)10.0F;
            }

            this.ping = newPing;
         }

      });
      this.floorState.requestRoom(this);
      this.updateUI();
      this.updateInstalledPresets();
      this.presetSearch.textProperty().addListener((o,oldValue,newValue)->this.updateInstalledPresets());
      this.item_src_tgl.selectedToggleProperty().addListener((o) -> this.updatePostConfig());
      this.presetListView.setOnMouseClicked((event) -> {
         if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
            String presetName = (String)this.presetListView.getSelectionModel().getSelectedItem();
            PresetConfig preset = PresetConfigUtils.loadPreset(presetName);
            if (preset != null) {
               this.selectPreset(preset, presetName);
            }
         }

         this.updateUI();
      });
      this.updatePostConfig();
   }

   private void setupCache() {
      Cacher.setCacheDir(PresetConfigUtils.baseDir() + File.separator + "Cache");
      this.loadCache();
   }

   private void loadCache() {
      JSONObject cache = Cacher.getCacheContents();
      String stackTileKey = cache.optString("stacktile", "2x2");
      this.stackTileSetting = StackTileSetting.fromString(stackTileKey);
      ((Toggle)this.stacktile_tgl.getToggles().stream().filter((s) -> ((RadioButton)s).getText().equals(stackTileKey)).findFirst().orElseGet(() -> (Toggle)this.stacktile_tgl.getToggles().get(2))).setSelected(true);
      String itemSourceKey = cache.optString("itemSource", "ONLY_INVENTORY");
      ((Toggle)this.item_src_tgl.getToggles().stream().filter((s) -> itemSourceKey.equals(s.getUserData())).findFirst().orElseGet(() -> (Toggle)this.item_src_tgl.getToggles().get(0))).setSelected(true);
      String donationModeKey = cache.optString("donationMode", "MISSING_ITEMS");
      ((Toggle)this.donate_tgl.getToggles().stream().filter((s) -> donationModeKey.equals(s.getUserData())).findFirst().orElseGet(() -> (Toggle)this.donate_tgl.getToggles().get(0))).setSelected(true);
      this.ratelimiter.setValue((double)cache.optInt("ratelimit", 22));
      Utils.setExtraSleepTime(cache.optInt("ratelimit", 22));
      this.noExportWiredCbx.setSelected(cache.optBoolean("noExportWired"));
      this.previewBeforeImportCbx.setSelected(cache.optBoolean("previewBeforeImport",true));
      this.noExportFurniVariablesCbx.setSelected(cache.optBoolean("noExportFurniVariables", false));
      this.noExportWallItemsCbx.setSelected(cache.optBoolean("noExportWallItems"));
      this.allowIncompleteBuildsCbx.setSelected(cache.optBoolean("allowIncompleteBuilds"));
      this.useRoomFurniCbx.setSelected(cache.optBoolean("useRoomFurni"));
   }

   private void selectPreset(PresetConfig preset, String name) {
      this.logger.log(String.format("Selected \"%s\" preset", name), "green");
      this.selectedPresetName=name;
      this.imagePreloadDelay.stop();
      this.imagePreloadDelay.setOnFinished(e->extension.tools.preview.FurnitureSprites.preload(preset));
      this.imagePreloadDelay.playFromStart();
      this.importer.setPresetConfig(preset);
      HPoint dim = PresetUtils.presetDimensions(preset);
      this.logger.log(String.format("Preset dimensions: %dx%d", dim.getX(), dim.getY()), "green");
      this.updateUI();
   }

   protected void onStartConnection() {
      this.latestPingTimestamp = System.currentTimeMillis();
      this.isConnected = true;
      this.updateUI();
   }

   protected void onEndConnection() {
      this.isConnected = false;
      this.ownUserId=0;
      if(this.importer!=null)this.importer.getUndoJournal().clear();
      this.furniDataTools = null;
      this.floorState.reset();
      this.inventory.clear();
      this.exporter.reset();
      this.importer.reset();
      this.updateUI();
   }

   public HFloorItem stackTile() {
      if (this.floorState.inRoom() && this.furniDataTools.isReady()) {
         List<HFloorItem> items = this.floorState.getItemsFromType(this.furniDataTools, this.stackTileSetting.getClassName());
         return items.size() == 0 ? null : (HFloorItem)items.get(0);
      } else {
         return null;
      }
   }

   public boolean furniDataReady() {
      return this.furniDataTools != null && this.furniDataTools.isReady();
   }

   private boolean BCCatalogAvailable() {
      return this.catalog.getState() == BCCatalog.CatalogState.COLLECTED;
   }

   private void updateLabel(Label lbl, boolean isFullfilled, boolean isBusy, boolean isOptional) {
      lbl.getStyleClass().removeAll((Collection)lbl.getStyleClass().stream().filter((p) -> p.startsWith("lbl")).collect(Collectors.toList()));
      lbl.getStyleClass().add(isFullfilled ? "lblgreen" : (isBusy ? "lblorange" : (isOptional ? "lblgrey" : "lblred")));
   }

   private void updateLabel(Label lbl, boolean isFullfilled, boolean isBusy) {
      this.updateLabel(lbl, isFullfilled, isBusy, false);
   }

   private void updateLabel(Label lbl, boolean isFullfilled) {
      this.updateLabel(lbl, isFullfilled, false);
   }

   private void updateUI() {
      Platform.runLater(() -> {
         this.previewBtn.setDisable(this.importer.getPresetConfig()==null || this.importer.getState()!=GPresetImporter.BuildingImportState.NONE);
         boolean presetHasWallItems = this.importer.getPresetConfig() != null && this.importer.getPresetConfig().getWallFurniture() != null && !this.importer.getPresetConfig().getWallFurniture().isEmpty();
         boolean presetHasFloorItems = this.importer.getPresetConfig() != null && !this.importer.getPresetConfig().getFurniture().isEmpty();
         this.updateLabel(this.cndConnectedLbl, this.isConnected);
         this.updateLabel(this.cndRoomLbl, this.floorState.inRoom());
         this.updateLabel(this.cndFurnidataLbl, this.furniDataReady());
         this.updateLabel(this.cndInventoryLbl, this.inventory.getState() == Inventory.InventoryState.LOADED, this.inventory.getState() == Inventory.InventoryState.LOADING);
         this.updateLabel(this.cndStackTileLbl, this.stackTile() != null, false, !presetHasFloorItems);
         this.updateLabel(this.cndBCShopLbl, this.catalog.getState() == BCCatalog.CatalogState.COLLECTED, this.catalog.getState() == BCCatalog.CatalogState.COLLECTING_PAGES, !presetHasWallItems);
         this.updateLabel(this.cndPermissionsLbl, this.permissions.canMoveFurni() && (this.noExportWiredCbx.isSelected() || this.permissions.canModifyWired()));
         this.availabilityBtn.setDisable(this.importer.getPresetConfig() == null);
         this.selfDonateBtn.setDisable(this.importer.getPresetConfig() == null);
         this.currentPresetBtn.setDisable(this.presetListView.getSelectionModel().getSelectedItem() == null);
      });
   }

   public void updateInstalledPresets() {
      List<String> installed = PresetConfigUtils.listPresets();
      Platform.runLater(() -> {
         String selected=this.presetListView.getSelectionModel().getSelectedItem();
         String query=this.presetSearch==null?"":this.presetSearch.getText().strip().toLowerCase(java.util.Locale.ROOT);
         List<String> filtered=installed.stream().filter(name->name.toLowerCase(java.util.Locale.ROOT).contains(query)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
         this.presetListView.getItems().setAll(filtered);
         if(selected!=null && filtered.contains(selected))this.presetListView.getSelectionModel().select(selected);
      });
   }

   public void sendVisualChatInfo(String text) {
      this.sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, new Object[]{-1, text, 0, 30, 0, -1}));
   }

   public void loadInventoryClick(ActionEvent actionEvent) {
      if(this.getPacketInfoManager()!=null && this.getPacketInfoManager().getPacketInfoFromName(HMessage.Direction.TOSERVER,"InfoRetrieve")!=null)this.sendToServer(new HPacket("InfoRetrieve",HMessage.Direction.TOSERVER));
      this.inventory.requestInventory();
   }

   public void loadBCClick(ActionEvent actionEvent) {
      this.catalog.requestIndex();
   }

   public void clearBCClick(ActionEvent actionEvent) throws URISyntaxException {
      this.catalog.clearCache();
   }

   public FurniDataTools getFurniDataTools() {
      return this.furniDataTools;
   }

   public Logger getLogger() {
      return this.logger;
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   public BCCatalog getCatalog() {
      return this.catalog;
   }

   public FloorState getFloorState() {
      return this.floorState;
   }

   public RoomPermissions getPermissions() {
      return this.permissions;
   }

   public StackTileSetting getStackTileSetting() {
      return this.stackTileSetting;
   }

   public int getSafeFeedbackTimeout() {
      return (int)(((double)this.ping + this.pingVariation + (double)10.0F) * (double)3.0F);
   }

   private void updatePostConfig() {
      this.importer.setPostConfig(this.createPostConfig());
      this.updateUI();
   }

   private PostConfig createPostConfig() {
      PostConfig postConfig = new PostConfig();
      ItemSource itemSource = ItemSource.valueOf((String)this.item_src_tgl.getSelectedToggle().getUserData());
      Cacher.put("itemSource", itemSource);
      postConfig.setItemSource(itemSource);
      List var10000 = this.furniPostConfigs;
      Objects.requireNonNull(postConfig);
      var10000.forEach(value -> postConfig.addFurniPostConfig((FurniPostConfig)value));
      return postConfig;
   }

   public void setImportProgress(String phase,int completed,int total) {
      Platform.runLater(()->{
         if(pauseImportBtn!=null && (phase.equals("Import complete") || phase.equals("Import stopped or reset"))) pauseImportBtn.setText("Pause import");
         if(importProgressLabel!=null) importProgressLabel.setText(phase + (total>0 ? " " + completed + "/" + total : ""));
         if(importProgressBar!=null) importProgressBar.setProgress(total>0 ? Math.min(1.0,Math.max(0.0,(double)completed/total)) : ProgressBar.INDETERMINATE_PROGRESS);
      });
   }

   public void updatePreviewAvailability() {
      Platform.runLater(()->{
         if(this.previewBtn!=null && this.importer!=null)this.previewBtn.setDisable(this.importer.getPresetConfig()==null || this.importer.getState()!=GPresetImporter.BuildingImportState.NONE);
      });
   }

   public boolean previewBeforeImport() { return this.previewBeforeImportCbx==null || this.previewBeforeImportCbx.isSelected(); }

   public void previewBtnClick(ActionEvent event) {
      if(this.importer!=null)this.importer.previewSelectedPreset();
   }

   public void showImportPreview(PresetPreviewSession session,BooleanSupplier canConfirm,BooleanSupplier confirm,Runnable cancel) {
      Platform.runLater(()->{
         if(session.isClosed())return;
         try {
            PresetPreviewModel model=new PresetPreviewModel(session.preset(),session.options(),name->{
               FloorItemDetails details=this.furniDataReady()?this.furniDataTools.getFloorItemDetails(name):null;
               return details==null || details.xDim<1 || details.yDim<1 || details.xDim>128 || details.yDim>128?null:new PresetPreviewModel.Footprint(details.xDim,details.yDim);
            });
            if(this.previewStage!=null)this.previewStage.close();
            Stage stage=new Stage();this.previewStage=stage;
            if(this.primaryStage!=null){stage.initOwner(this.primaryStage);stage.initModality(Modality.WINDOW_MODAL);}
            stage.setTitle("G-Presets - Preset Preview");
            PresetPreviewPane pane=new PresetPreviewPane(model,this.selectedPresetName,session.rootX,session.rootY);
            if(session.preset().getMetadata()!=null)pane.setSharedMetadata(session.preset().getMetadata());
            pane.configurePlacement((x,y)->extension.tools.preview.PlacementAudit.inspect(model,this.floorState,this.furniDataTools,x,y),(x,y)->session.chooseRoot(x,y));
             pane.setActions(confirm,stage::close);pane.setAvailabilityAction(()->pane.setAvailabilitySummary(previewAvailability(session)));pane.setCompatibilityAction(()->pane.setAvailabilitySummary(previewWiredCompatibility(session)));pane.updateLaunchAvailability(canConfirm.getAsBoolean());
            try {
               var audit=extension.tools.importutils.AvailabilityAudit.inspect(session.preset(),session.options(),furniDataTools,inventory,catalog,useRoomFurni());
               long missing=audit.getJSONArray("items").toList().stream().filter(row->"MISSING".equals(((java.util.Map<?,?>)row).get("status"))).count();
               long unknown=audit.getJSONArray("items").toList().stream().filter(row->String.valueOf(((java.util.Map<?,?>)row).get("status")).contains("UNKNOWN")).count();
               pane.setAvailabilitySummary("Before import: "+missing+" furniture type(s) missing; "+unknown+" unknown. Check availability for floor/wall details and BC conditions.");
            }catch(RuntimeException error){pane.setAvailabilitySummary("Availability unknown: "+error.getMessage());}
            Timeline refresh=new Timeline(new KeyFrame(Duration.millis(400),e->{if(session.isClosed())stage.close();else {pane.updateLaunchAvailability(canConfirm.getAsBoolean());pane.refreshPlacement();}}));
            refresh.setCycleCount(Timeline.INDEFINITE);
            stage.setOnHidden(e->{refresh.stop();pane.close();cancel.run();if(this.previewStage==stage)this.previewStage=null;});
            javafx.geometry.Rectangle2D bounds=javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setScene(new Scene(pane,Math.min(1040,bounds.getWidth()),Math.min(720,bounds.getHeight())));
            stage.setMinWidth(Math.min(700,bounds.getWidth()));stage.setMinHeight(Math.min(480,bounds.getHeight()));
            stage.show();refresh.play();
         } catch(RuntimeException error) {
            cancel.run();this.logger.log("Cannot open preset preview: "+error.getMessage(),"red");
         }
      });
   }

   private String previewWiredCompatibility(PresetPreviewSession session) {
      try {
         var report=extension.tools.importutils.WiredDiagnostics.inspect(session.preset());
         extension.tools.importutils.WiredDiagnostics.checkMessages(session.preset(),report,(direction,name)->this.getPacketInfoManager()!=null && this.getPacketInfoManager().getPacketInfoFromName(direction,name)!=null);
         for(var contract:session.preset().getContracts())try{contract.savePacket(this.furniDataTools);}catch(RuntimeException error){report.getJSONArray("issues").put(new org.json.JSONObject().put("severity","WARNING").put("message","Contract "+contract.furniId+": "+error.getMessage()));}

         String text=extension.tools.importutils.WiredDiagnostics.text(report);
         extension.tools.importutils.ReportWindow.show(this.previewStage,"WIRED checks",text,report);
         return "WIRED checks: "+report.getInt("serialized")+"/"+report.getInt("configurations")+" serialized. See the detailed report.";
      }catch(RuntimeException error){return "WIRED compatibility unknown: "+error.getMessage();}
   }

   private String previewAvailability(PresetPreviewSession session) {
      try {
         var report=extension.tools.importutils.AvailabilityAudit.inspect(session.preset(),session.options(),furniDataTools,inventory,catalog,useRoomFurni());
         extension.tools.importutils.ReportWindow.show(this.previewStage,"Before import",extension.tools.importutils.AvailabilityAudit.text(report),report);
         return "Floor and wall furniture checked. See the detailed report; unknown availability needs verification.";
      }catch(RuntimeException error){return "Availability unknown: "+error.getMessage();}
   }
   public void showImportReport(org.json.JSONObject report) {
      String text=extension.tools.importutils.ImportReport.text(report);
      this.logger.log(text,"blue");
      extension.tools.importutils.ReportWindow.show(this.primaryStage,"Import report",text,report);
   }

   public void availabilityBtnClick(ActionEvent actionEvent) {
      PresetConfig presetConfig = this.importer.getPresetConfig();
      PostConfig postConfig = this.createPostConfig();
      if (presetConfig != null && this.furniDataReady()) {
         PresetConfig combined = new PresetConfig(presetConfig.toJsonObject());
         combined.applyPostConfig(postConfig);
         List<FurniDropInfo> fakeDropInfo = new ArrayList();

         for(PresetFurni f : combined.getFurniture()) {
            fakeDropInfo.add(new FurniDropInfo(-1, -1, this.furniDataTools.getFloorTypeId(f.getClassName()), postConfig.getItemSource(), -1));
         }

         AvailabilityChecker.printAvailability(this.logger, fakeDropInfo, this.inventory, this.furniDataTools, this.floorState, this.useRoomFurni());
         AvailabilityChecker.printWallItemAvailability(this.logger, combined.getWallFurniture(), this.inventory, this.furniDataTools, this.catalog, postConfig.getItemSource());
      } else {
         this.logger.log("No preset chosen or furnidata not ready", "red");
      }

   }

   public void selfDonateBtnClick(ActionEvent actionEvent) {
      if (this.exporter.getState() != GPresetExporter.PresetExportState.NONE) {
         this.logger.log("Can't autodonate while export is in progress", "red");
      } else if (this.importer.getState() != GPresetImporter.BuildingImportState.NONE) {
         this.logger.log("Can't autodonate while import is in progress", "red");
      } else {
         this.autoDonator.donateAll(this.donateMissing.isSelected());
      }
   }

   public void showSelfDonateBtn(boolean show) {
      Platform.runLater(()->{
         this.selfDonateBtn.setVisible(show);
         this.donateLabel.setVisible(show);
         this.donateAll.setVisible(show);
         this.donateMissing.setVisible(show);
         this.presetListView.setPrefHeight(show?102:136);
      });
   }

   public void openPresetsFolderClick(ActionEvent actionEvent) {
      try {
         Desktop.getDesktop().open(new File(PresetConfigUtils.presetPath()));
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   public void openCurrentPresetClick(ActionEvent actionEvent) {
      String selectedPreset = (String)this.presetListView.getSelectionModel().getSelectedItem();
      if (selectedPreset != null) {
         try {
            Desktop.getDesktop().edit(new File(PresetConfigUtils.presetPath(), selectedPreset + ".json"));
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

   }

   public void sharePresetClick(ActionEvent event) {
      String name=this.presetListView.getSelectionModel().getSelectedItem();
      if(name==null){this.logger.log("Select a preset first", "orange");return;}
      try {
         PresetConfig preset=PresetConfigUtils.loadPreset(name);
         if(preset==null)throw new IllegalArgumentException("Preset cannot be loaded");
         PresetPreviewModel model=new PresetPreviewModel(preset,new PostConfig(),classname->{
            FloorItemDetails item=this.furniDataReady()?furniDataTools.getFloorItemDetails(classname):null;
            return item==null || item.xDim<1 || item.yDim<1 || item.xDim>128 || item.yDim>128?null:new PresetPreviewModel.Footprint(item.xDim,item.yDim);
         });
         extension.tools.sharing.PresetShareDialog.show(this.primaryStage,preset,model,name);
      }catch(Exception error){this.logger.log("Cannot share preset: "+error.getMessage(),"red");}
   }

   public void movePresetToFolderClick(ActionEvent event) {
      String selected=this.presetListView.getSelectionModel().getSelectedItem();
      if(selected==null){this.logger.log("Select a preset first", "orange");return;}
      javafx.scene.control.TextInputDialog dialog=new javafx.scene.control.TextInputDialog();
      extension.ui.ReadableTheme.apply(dialog.getDialogPane());
      dialog.initOwner(this.primaryStage); dialog.setTitle("Organize presets");
      dialog.setHeaderText("Move the selected preset to a folder");dialog.setContentText("Folder name:");
      dialog.showAndWait().ifPresent(folder->{
         try {PresetConfigUtils.moveToFolder(selected,folder.strip());this.updateInstalledPresets();this.logger.log("Preset moved to folder", "green");}
         catch(Exception error){this.logger.log("Cannot move preset: "+error.getMessage(), "red");}
      });
   }

   public void pauseImportClick(ActionEvent event) {
      if(this.importer==null || !this.importer.togglePause()) {this.logger.log("Pause is available during an active import", "orange");return;}
      this.pauseImportBtn.setText(this.importer.isPauseRequested()?"Resume import":"Pause import");
   }

   
   public void presetToolsClick(ActionEvent event) {
      extension.tools.library.PresetLibraryDialog.show(this.primaryStage,this.presetListView.getSelectionModel().getSelectedItem(),this::updateInstalledPresets);
   }
   public void replaceMissingClick(ActionEvent event) {
      if(this.importer==null || this.importer.getState()!=GPresetImporter.BuildingImportState.NONE){this.logger.log("Finish or cancel the current import first","orange");return;}
      PresetConfig source=this.importer.getPresetConfig();if(source==null){this.logger.log("Select a preset first","orange");return;}
      try {
         PresetConfig copy=extension.tools.importutils.ReplacementDialog.show(this.primaryStage,source,this.createPostConfig(),this.furniDataTools,this.inventory,this.catalog);
         if(copy!=null){if(!this.importer.applySessionReplacements(source,copy))throw new IllegalStateException("Import or selection changed; reopen replacements");this.imagePreloadDelay.stop();extension.tools.preview.FurnitureSprites.preload(copy);this.logger.log("Session replacements applied. The original preset file is unchanged; reopen Preview and Check WIRED before importing.","green");this.updateUI();}
      }catch(Exception error){this.logger.log("Cannot replace furniture: "+error.getMessage(),"red");}
   }
   public void undoImportClick(ActionEvent event) {
      if(this.importer==null || this.importer.getState()!=GPresetImporter.BuildingImportState.NONE || this.exporter.getState()!=GPresetExporter.PresetExportState.NONE){this.logger.log("Finish or abort the current operation before undo","orange");return;}
      var journal=this.importer.getUndoJournal();
      if(journal.entries().isEmpty() || journal.room()!=this.floorState.getRoomId()){this.logger.log("No confirmed placements from the last import in this room. Unidentified items and reused furniture are excluded.","orange");return;}
      javafx.scene.control.Alert confirm=new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,"Recover "+journal.entries().size()+" individually identified placement(s) from the last import in room "+journal.room()+"? Existing/reused furniture and configuration changes are excluded. Unconfirmed removals remain available to retry.",javafx.scene.control.ButtonType.CANCEL,javafx.scene.control.ButtonType.OK);
      confirm.initOwner(this.primaryStage);extension.ui.ReadableTheme.apply(confirm.getDialogPane());confirm.setTitle("Undo last import");
      if(confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)==javafx.scene.control.ButtonType.OK)try{if(!this.importer.startUndo())this.logger.log("Cannot undo: room, permissions or import state changed","orange");}catch(Exception error){this.logger.log("Cannot undo: "+error.getMessage(),"red");}
   }

   public void reloadPresetsClick(ActionEvent actionEvent) {
      this.updateInstalledPresets();
      this.updateUI();
   }

   public boolean allowIncompleteBuilds() {
      return this.allowIncompleteBuildsCbx.isSelected();
   }

   public boolean useRoomFurni() {
      return this.useRoomFurniCbx.isSelected();
   }

   private void updateFurniPostConfigsView() {
      Platform.runLater(() -> {
         this.postconfigTable.getItems().clear();
         this.postconfigTable.getItems().addAll(this.furniPostConfigs);
      });
      this.updateUI();
   }

   public void updatePostconfigClick(ActionEvent actionEvent) {
      try {
         if (this.furniNamePC_txt.getText().isEmpty() || this.replacementIdPC_txt.getText().isEmpty()) {
            throw new Exception();
         }

         FurniPostConfig furniPostConfig = new FurniPostConfig(this.furniNamePC_txt.getText(), true, Integer.parseInt(this.replacementIdPC_txt.getText()), (String)null, (HPoint)null, (Integer)null, (String)null, new HashMap(), new HashMap());
         this.furniPostConfigs = (List)this.furniPostConfigs.stream().filter((c) -> !c.getFurniIdentifier().equals(furniPostConfig.getFurniIdentifier())).collect(Collectors.toList());
         this.furniPostConfigs.add(furniPostConfig);
         this.updatePostConfig();
         this.updateFurniPostConfigsView();
         Platform.runLater(() -> this.postconfigErrorLbl.setText(""));
      } catch (Exception var3) {
         Platform.runLater(() -> this.postconfigErrorLbl.setText("You entered invalid information!"));
      }

   }

   public boolean shouldExportWired() {
      return !this.noExportWiredCbx.isSelected();
   }

   public boolean shouldExportFurniVariables() {
      return !this.noExportFurniVariablesCbx.isSelected();
   }

   public boolean shouldExportWallItems() {
      return !this.noExportWallItemsCbx.isSelected();
   }

   public GPresetExporter getExporter() {
      return this.exporter;
   }

   public GPresetImporter getImporter() {
      return this.importer;
   }

   public void alwaysOnTopClick(ActionEvent actionEvent) {
      this.primaryStage.setAlwaysOnTop(this.onTopCbx.isSelected());
   }

   public void clearWiredClick(ActionEvent actionEvent) {
      this.exporter.clearCache();
   }
}
