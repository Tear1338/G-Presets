package extension.tools;

import extension.GPresets;
import extension.tools.preview.PresetPreviewSession;
import extension.parsers.HWiredVariable;
import extension.parsers.HVariableDiff;
import extension.parsers.HWiredLinkConfiguration;
import extension.parsers.HWiredVariablesForObject;
import extension.tools.importutils.AvailabilityChecker;
import extension.tools.importutils.FurniDropInfo;
import extension.tools.importutils.FurniMoveInfo;
import extension.tools.importutils.StackTileInfo;
import extension.tools.importutils.StackTileUtils;
import extension.tools.postconfig.ItemSource;
import extension.tools.postconfig.PostConfig;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.RoomLayoutInfo;
import extension.tools.presetconfig.ads_bg.PresetAdsBackground;
import extension.tools.presetconfig.binding.PresetWiredFurniBinding;
import extension.tools.presetconfig.furni.PresetFurni;
import extension.tools.presetconfig.furni.PresetChest;
import extension.tools.presetconfig.wired.PresetWiredContract;
import extension.tools.presetconfig.furni.PresetFurniLabel;
import extension.tools.presetconfig.furni.PresetWallFurni;
import extension.tools.presetconfig.wired.PresetWiredAddon;
import extension.tools.presetconfig.wired.PresetWiredBase;
import extension.tools.presetconfig.wired.WiredKind;
import extension.tools.presetconfig.wired.PresetWiredLink;
import extension.tools.presetconfig.wired.PresetWiredRoomSettings;
import extension.tools.presetconfig.wired.PresetRoomLink;
import extension.tools.presetconfig.wired.PresetObjectVariables;
import extension.tools.presetconfig.wired.WiredImportPlanner;
import extension.tools.presetconfig.wired.PresetWiredCondition;
import extension.tools.presetconfig.wired.PresetWiredEffect;
import extension.tools.presetconfig.wired.PresetWiredSelector;
import extension.tools.presetconfig.wired.PresetWiredTrigger;
import extension.tools.presetconfig.wired.PresetWiredVariable;
import extension.tools.presetconfig.wired.PresetWireds;
import furnidata.FurniDataTools;
import furnidata.details.FloorItemDetails;
import game.BCCatalog;
import game.FloorState;
import game.Inventory;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.stuffdata.MapStuffData;
import gearth.extensions.parsers.HInventoryItem;
import gearth.extensions.parsers.HPoint;
import gearth.extensions.parsers.HWallItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import utils.StateExtractor;
import utils.Utils;
import utils.WallPosition;

public class GPresetImporter {
   private final Object lock = new Object();
   private GPresets extension = null;
   private List<String> needVariableIds = new ArrayList();
   private int variablesProcessed;
   private int variablesToProcess;
   private PresetConfig presetConfig = null;
   private PostConfig postConfig = new PostConfig();
   private PostConfig activePostConfig;
   private PresetPreviewSession pendingPreview;
   private PresetConfig workingPresetConfig = null;
   private Map<Integer, Integer> realFurniIdMap = null;
   private Map<String, String> realVariableIdMap = Collections.synchronizedMap(new HashMap<>());
   private final Map<String, HWiredVariable> variableSnapshot = new HashMap<>();
   private final Semaphore roomSettingsResponse=new Semaphore(0);
   private volatile PresetWiredRoomSettings receivedRoomSettings;
   private volatile boolean awaitingRoomSettings;
   private volatile boolean wallFurniturePlaced;
   private final Semaphore contractResponse = new Semaphore(0);
   private volatile Integer pendingContractId;
   private volatile PresetWiredContract receivedContract;
   private volatile String variableReadError;
   private volatile String wiredSaveError;
   private final Semaphore labelSaveConfirmation = new Semaphore(0);
   private volatile PresetFurniLabel pendingLabel;
   private final Semaphore linkResponse = new Semaphore(0);
   private volatile Integer pendingLinkId;
   private volatile HWiredLinkConfiguration receivedLink;
   private Map<String, LinkedList<Integer>> expectFurniDrops = null;
   private volatile BuildingImportState state;
   private final extension.tools.importutils.ImportPauseGate pauseGate=new extension.tools.importutils.ImportPauseGate();
   private volatile int activeImportRoomId;
   private boolean checkpoint(BuildingImportState expected) {
      boolean valid=pauseGate.checkpoint(()->this.state==expected && this.extension.getFloorState().getRoomId()==activeImportRoomId);
      if(!valid && this.state==expected) {
         this.extension.getLogger().log("Import stopped: room or connection changed during pause.","red");
         this.reset();
      }
      return valid;
   }
   public boolean togglePause() {
      BuildingImportState current=this.state;
      if(current!=BuildingImportState.ADD_UNSTACKABLES && current!=BuildingImportState.ADD_FURNITURE && current!=BuildingImportState.SETUP_ADS && current!=BuildingImportState.SETUP_WIRED && current!=BuildingImportState.MOVE_FURNITURE) return false;
      if(pauseGate.isPaused()) {pauseGate.resume();this.extension.getLogger().log("Import resumed", "blue");}
      else {pauseGate.requestPause();this.extension.getLogger().log("Pause requested; current operation will finish first", "orange");}
      return true;
   }
   public boolean isPauseRequested() { return pauseGate.isPaused(); }
   private volatile int confirmedWiredCount;
   private volatile int mainStackTile;
   private volatile int mainStackDimensions;
   private HPoint useRoomFurniRectCorner1;
   private HPoint useRoomFurniRectCorner2;
   private HPoint reservedSpace;
   private HPoint rootLocation;
   private double importZDelta;
   private HPoint stackTileLocation;
   private HPoint stateChangerLocation;
   private List<StackTileInfo> allAvailableStackTiles;
   private volatile String stackTilePrepFailureReason;
   private final Set<Integer> deferredRequiredStackTileDimensions;
   private final Set<Integer> acquiredStackTileFurniIds;
   private List<HFloorItem> roomItemsList;
   private final AtomicInteger internalPacketBypass;
   private static final long WIRED_SET_VAR_MIN_INTERVAL_MS = 350L;
   private final Object wiredSetVarThrottleLock;
   private long lastWiredSetVarSendMs;
   private final Map<Integer, Map<String, Integer>> inspectedFurniVariables;
   private final AtomicReference<Integer> awaitingInspectionForFurniId;
   private final Semaphore inspectionResponseSemaphore;
   Map<Integer, LinkedList<HInventoryItem>> inventoryCache;
   Map<String, LinkedList<HInventoryItem>> wallInventoryCache;
   private long latestConditionSave;
   private long latestTriggerSave;
   private long latestEffectSave;
   private long latestAddonSave;
   private long latestSelectorSave;
   private long latestVariableSave;
   private Semaphore wiredSaveConfirmation;
   private Semaphore wiredVariableConfirmation;

   private void throttleWiredSetObjectVariableValue() {
      synchronized(this.wiredSetVarThrottleLock) {
         long now = System.currentTimeMillis();
         long earliest = this.lastWiredSetVarSendMs + 350L;
         if (now < earliest) {
            try {
               this.wiredSetVarThrottleLock.wait(earliest - now);
            } catch (InterruptedException var8) {
               Thread.currentThread().interrupt();
            }

            now = System.currentTimeMillis();
         }

         this.lastWiredSetVarSendMs = now;
      }
   }

   private void onInspectVariablesForObject(HMessage hMessage) {
      try {
         HWiredVariablesForObject inspection = new HWiredVariablesForObject(hMessage.getPacket());
         if(inspection.type==-10) {
            this.inspectedFurniVariables.put(0,new HashMap<>(inspection.variables));
            if(Integer.valueOf(0).equals(this.awaitingInspectionForFurniId.get()))this.inspectionResponseSemaphore.release();
            return;
         }
         if (inspection.type != 0) {
            return;
         }

         int furniId = inspection.objectId;
         if (furniId == 0) {
            return;
         }

         this.inspectedFurniVariables.put(furniId, new HashMap(inspection.variables));
         Integer waiting = (Integer)this.awaitingInspectionForFurniId.get();
         if (waiting != null && waiting == furniId) {
            this.inspectionResponseSemaphore.release();
         }
      } catch (Exception var5) {
      }

   }

   private boolean fetchWallItemVariables(int furniId, long timeoutMs) { return fetchObjectVariables(0,-furniId,timeoutMs); }

   private boolean fetchObjectVariables(int target,int objectId,long timeoutMs) {
      int furniId=objectId;
      this.inspectionResponseSemaphore.drainPermits();
      this.awaitingInspectionForFurniId.set(furniId);

      boolean var5;
      try {
         this.extension.sendToServer(new HPacket("WiredGetVariablesForObject", HMessage.Direction.TOSERVER, new Object[]{target, objectId}));

         try {
            boolean var4 = this.inspectionResponseSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            return var4;
         } catch (InterruptedException var9) {
            Thread.currentThread().interrupt();
            var5 = false;
         }
      } finally {
         this.awaitingInspectionForFurniId.set(null);
      }

      return var5;
   }

   public GPresetImporter(GPresets extension) {
      this.state = GPresetImporter.BuildingImportState.NONE;
      this.mainStackTile = -1;
      this.mainStackDimensions = 0;
      this.useRoomFurniRectCorner1 = null;
      this.useRoomFurniRectCorner2 = null;
      this.reservedSpace = null;
      this.rootLocation = null;
      this.importZDelta = (double)0.0F;
      this.stackTileLocation = null;
      this.stateChangerLocation = null;
      this.allAvailableStackTiles = null;
      this.stackTilePrepFailureReason = null;
      this.deferredRequiredStackTileDimensions = new HashSet();
      this.acquiredStackTileFurniIds = new HashSet();
      this.roomItemsList = new ArrayList();
      this.internalPacketBypass = new AtomicInteger(0);
      this.wiredSetVarThrottleLock = new Object();
      this.lastWiredSetVarSendMs = 0L;
      this.inspectedFurniVariables = Collections.synchronizedMap(new HashMap());
      this.awaitingInspectionForFurniId = new AtomicReference((Object)null);
      this.inspectionResponseSemaphore = new Semaphore(0);
      this.inventoryCache = new HashMap();
      this.wallInventoryCache = new HashMap();
      this.latestConditionSave = -1L;
      this.latestTriggerSave = -1L;
      this.latestEffectSave = -1L;
      this.latestAddonSave = -1L;
      this.latestSelectorSave = -1L;
      this.latestVariableSave = -1L;
      this.wiredSaveConfirmation = new Semaphore(0);
      this.wiredVariableConfirmation = new Semaphore(0);
      this.extension = extension;
      extension.intercept(HMessage.Direction.TOSERVER, "Chat", this::onChat);
      extension.intercept(HMessage.Direction.TOSERVER, "MoveAvatar", this::moveAvatar);
      extension.intercept(HMessage.Direction.TOCLIENT, "ObjectAdd", this::onObjectAdd);
      extension.intercept(HMessage.Direction.TOCLIENT, "ObjectUpdate", this::onObjectAdd);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredAllVariablesDiffs", this::onWiredAllVariables);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredVariablesForObject", this::onInspectVariablesForObject);
      extension.intercept(HMessage.Direction.TOSERVER, "PlaceObject", this::maybeBlockPlacements);
      extension.intercept(HMessage.Direction.TOSERVER, "BuildersClubPlaceRoomItem", this::maybeBlockPlacements);
      extension.intercept(HMessage.Direction.TOCLIENT, "BuildersClubPlacementWarning", (m) -> {
         synchronized(this.lock) {
            if (this.state != GPresetImporter.BuildingImportState.NONE) {
               m.setBlocked(true);
            }

         }
      });
      extension.intercept(HMessage.Direction.TOSERVER, "UseFurniture", this::blockFurniAdjustments);
      extension.intercept(HMessage.Direction.TOSERVER, "SetCustomStackingHeight", this::blockFurniAdjustments);
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateCondition", this::blockFurniAdjustments);
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateTrigger", this::blockFurniAdjustments);
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateAction", this::blockFurniAdjustments);
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateAddon", this::blockFurniAdjustments);
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateSelector", this::blockFurniAdjustments);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredLinkConfiguration", this::linkConfigurationReceived);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredContractContents", this::contractReceived);
      extension.intercept(HMessage.Direction.TOCLIENT,"WiredRoomSettings",message -> {
         if(!this.awaitingRoomSettings) return;
         try {
            this.receivedRoomSettings=PresetWiredRoomSettings.fromPacket(new HPacket(message.getPacket()));
            this.roomSettingsResponse.release();
         } catch(RuntimeException error) { this.extension.getLogger().log("Cannot verify room settings: " + error.getMessage(),"red"); }
      });
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredSaveSuccess", this::wiredSaved);
      extension.intercept(HMessage.Direction.TOCLIENT, "FurnitureLabelsAndRegionsUpdate", this::labelUpdated);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredValidationError", message -> {
         if (this.state == BuildingImportState.SETUP_WIRED) {
            this.wiredSaveError = "Habbo rejected the WIRED configuration";
            this.wiredSaveConfirmation.release();
         }
      });
   }

   private void onWiredAllVariables(HMessage hMessage) {
      if (this.state != BuildingImportState.SETUP_WIRED) return;
      try {
         HVariableDiff diff = new HVariableDiff(hMessage.getPacket());
         synchronized (this.lock) {
            for (String id : diff.removed) this.variableSnapshot.remove(id);
            for (HWiredVariable variable : diff.updated) this.variableSnapshot.put(variable.id, variable);
            if (!diff.lastChunk) return;
            PresetWireds wireds = this.workingPresetConfig == null ? null : this.workingPresetConfig.getPresetWireds();
            if (wireds != null) {
               for (Map.Entry<String, String> original : wireds.getVariablesMap().entrySet()) {
                  List<HWiredVariable> matches = this.variableSnapshot.values().stream()
                     .filter(v -> v.name.equals(original.getKey())).collect(Collectors.toList());
                  // Same-name variables in different scopes must not be chosen at random.
                  if (matches.size() == 1) this.realVariableIdMap.put(original.getValue(), matches.get(0).id);
               }
               for (PresetWiredVariable original : wireds.getVariables()) {
                  if (!PresetWiredBase.isLocalVariableId(original.variableId)) continue;
                  List<HWiredVariable> matches = this.variableSnapshot.values().stream()
                     .filter(v -> v.name.equals(original.getVariableName()))
                     .filter(v -> original.getVariableTarget() == null || original.getVariableTarget() == v.variableType)
                     .collect(Collectors.toList());
                  if (matches.size() == 1) this.realVariableIdMap.put(original.variableId, matches.get(0).id);
               }
            }
         }
      } catch (RuntimeException error) {
         this.variableReadError = error.getMessage();
         this.extension.getLogger().log("Cannot read WIRED variables: " + error.getMessage(), "red");
      }
      this.wiredVariableConfirmation.release();
   }

   private boolean refreshVariableMap() {
      synchronized (this.lock) {
         this.variableSnapshot.clear();
         this.variableReadError = null;
         this.wiredVariableConfirmation.drainPermits();
      }
      this.extension.sendToServer(new HPacket("WiredGetAllVariablesDiffs", HMessage.Direction.TOSERVER, 0));
      try {
         return this.wiredVariableConfirmation.tryAcquire(5L, TimeUnit.SECONDS) && this.variableReadError == null;
      } catch (InterruptedException interrupted) {
         Thread.currentThread().interrupt();
         return false;
      }
   }

   private void blockFurniAdjustments(HMessage message) {
      if (this.internalPacketBypass.get() > 0) {
         int remaining = this.internalPacketBypass.decrementAndGet();
         if (remaining < 0) {
            this.internalPacketBypass.set(0);
         }

      } else {
         synchronized(this.lock) {
            if (this.state == GPresetImporter.BuildingImportState.SETUP_WIRED || this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
               message.setBlocked(true);
               this.extension.sendVisualChatInfo("Don't adjust furniture while the importer is active!");
            }

         }
      }
   }

   private void maybeBlockPlacements(HMessage hMessage) {
      synchronized(this.lock) {
         if (this.state != GPresetImporter.BuildingImportState.NONE && this.state != GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
            hMessage.setBlocked(true);
            this.extension.sendVisualChatInfo("Can't drop furniture while the importer is active, please await the procedure or abort");
         }

      }
   }

   private boolean isPlacing() {
      return this.state == GPresetImporter.BuildingImportState.ADD_UNSTACKABLES || this.state == GPresetImporter.BuildingImportState.ADD_FURNITURE;
   }

   private void onObjectAdd(HMessage hMessage) {
      synchronized(this.lock) {
         if (this.isPlacing()) {
            HFloorItem item = new HFloorItem(hMessage.getPacket());
            String dropKey = String.format("%d|%d|%d", item.getTile().getX(), item.getTile().getY(), item.getTypeId());
            LinkedList<Integer> awaitMatchingDropIds = (LinkedList)this.expectFurniDrops.get(dropKey);
            if (awaitMatchingDropIds != null && awaitMatchingDropIds.size() > 0) {
               int assignedFurniId = (Integer)awaitMatchingDropIds.pollFirst();
               this.realFurniIdMap.put(assignedFurniId, item.getId());
               if (awaitMatchingDropIds.size() == 0) {
                  this.expectFurniDrops.remove(dropKey);
               }
            }
         }

      }
   }

   public boolean isReady() {
      boolean hasFloorItems = this.presetConfig != null && !this.presetConfig.getFurniture().isEmpty();
      boolean hasWallItems = this.presetConfig != null && this.presetConfig.getWallFurniture() != null && !this.presetConfig.getWallFurniture().isEmpty();
      boolean bcReady = !hasFloorItems && !hasWallItems || this.extension.getCatalog() != null && this.extension.getCatalog().getState() == BCCatalog.CatalogState.COLLECTED;
      return this.extension.getFloorState().inRoom() && this.extension.furniDataReady() && this.extension.getInventory().getState() == Inventory.InventoryState.LOADED && this.presetConfig != null && bcReady && this.extension.getPermissions().canMoveFurni() && (!this.extension.shouldExportWired() || this.extension.getPermissions().canModifyWired());
   }

   private void onChat(HMessage hMessage) {
      String text = hMessage.getPacket().readString();
      synchronized(this.lock) {
         if(text.equals(":preview") || text.equals(":pp")) {
            hMessage.setBlocked(true);this.previewSelectedPreset();return;
         }
         if (text.startsWith(":ip") || text.startsWith(":importpreset")) {
            hMessage.setBlocked(true);
            if (this.state != GPresetImporter.BuildingImportState.NONE) {
               this.extension.sendVisualChatInfo("Already importing preset.. finish up or abort first");
            } else if (!this.isReady()) {
               if (this.presetConfig == null) {
                  this.extension.sendVisualChatInfo("ERROR: select the preset first");
               } else {
                  this.extension.sendVisualChatInfo("ERROR: extension not fully initialized yet");
               }
            } else {
               HPoint parsedRoot = null;

               try {
                  String[] split = text.split(" ", 2);
                  String[] loc = split[1].split(", ?");
                  int rootX = Integer.parseInt(loc[0]);
                  int rootY = Integer.parseInt(loc[1]);
                  parsedRoot = new HPoint(rootX, rootY);
               } catch (Exception var10) {
               }

               if(this.extension.previewBeforeImport()) this.requestPreview(parsedRoot);
               else this.beginImport(parsedRoot,new PresetConfig(this.presetConfig.toJsonObject()),new PostConfig(this.postConfig));
            }
         }

         if (text.equals(":abort") || text.equals(":a")) {
            hMessage.setBlocked(true);
            if (this.state != GPresetImporter.BuildingImportState.NONE) {
               this.reportImport("Import aborted");
               this.reset();
               this.extension.sendVisualChatInfo("Successfully aborted importing");
            }
         }

         if (text.equals("all") && (this.state.equals(GPresetImporter.BuildingImportState.AWAITING_USE_ROOM_FURNI_RECT1) || this.state.equals(GPresetImporter.BuildingImportState.AWAITING_USE_ROOM_FURNI_RECT2))) {
            hMessage.setBlocked(true);
            this.useRoomFurniRectCorner1 = new HPoint(0, 0);
            this.useRoomFurniRectCorner2 = new HPoint(100, 100);
            if (this.state != GPresetImporter.BuildingImportState.AWAITING_UNOCCUPIED_SPACE) {
               this.state = GPresetImporter.BuildingImportState.AWAITING_UNOCCUPIED_SPACE;
               this.extension.sendVisualChatInfo("Select where the stack tile should be placed (empty area)");
            }
         }

      }
   }

   public void previewSelectedPreset() {
      synchronized(this.lock) {
         if(this.state!=BuildingImportState.NONE) { this.extension.sendVisualChatInfo("Finish or abort the current operation first");return; }
         if(this.presetConfig==null) { this.extension.sendVisualChatInfo("Select a preset first");return; }
         this.requestPreview(null);
      }
   }

   private void requestPreview(HPoint root) {
      PresetPreviewSession session=new PresetPreviewSession(this.presetConfig,this.postConfig,this.extension.getFloorState().getRoomId(),root);
      this.pendingPreview=session;this.state=BuildingImportState.AWAITING_PREVIEW;
      this.extension.updatePreviewAvailability();
      this.extension.showImportPreview(session,()->this.canConfirmPreview(session),()->this.confirmPreview(session),()->this.cancelPreview(session));
   }

   private boolean canConfirmPreview(PresetPreviewSession session) {
      synchronized(this.lock) {
         return this.pendingPreview==session && this.state==BuildingImportState.AWAITING_PREVIEW && this.isReady() &&
            session.isCurrent(this.presetConfig,this.postConfig,this.extension.getFloorState().getRoomId());
      }
   }

   private boolean confirmPreview(PresetPreviewSession session) {
      synchronized(this.lock) {
         if(!canConfirmPreview(session))return false;
         return session.confirm(this.presetConfig,this.postConfig,this.extension.getFloorState().getRoomId(),true,()->{
            this.pendingPreview=null;this.state=BuildingImportState.NONE;
            this.beginImport(session.root(),session.preset(),session.options());
         });
      }
   }

   private void cancelPreview(PresetPreviewSession session) {
      synchronized(this.lock) {
         session.cancel();
         if(this.pendingPreview==session) { this.pendingPreview=null;if(this.state==BuildingImportState.AWAITING_PREVIEW)this.state=BuildingImportState.NONE; }
         this.extension.updatePreviewAvailability();
      }
   }

   private void beginImport(HPoint root,PresetConfig preset,PostConfig options) {
      this.pauseGate.cancel();this.activeImportRoomId=this.extension.getFloorState().getRoomId();
      try { this.prepare(root!=null,preset,options); } finally { this.extension.updatePreviewAvailability(); }
      if(this.state==BuildingImportState.NONE)return;
      if(root!=null) { this.rootLocation=root;this.computeImportZDelta(); }
      if(this.rootLocation!=null && this.state==BuildingImportState.AWAITING_ROOT_LOCATION && this.workingPresetConfig!=null && this.workingPresetConfig.getFurniture().isEmpty())this.startWallOnlyImport();
   }

   private PostConfig importPostConfig() { return this.activePostConfig==null?this.postConfig:this.activePostConfig; }

   private void requirePacket(HMessage.Direction direction,String name) {
      if(this.extension.getPacketInfoManager().getPacketInfoFromName(direction,name)==null) throw new IllegalStateException("G-Earth packet catalog needs updating: " + name);
   }

   private void prepare(boolean hasInlineCoords,PresetConfig source,PostConfig options) {
      if (source != null) {
         this.activePostConfig=new PostConfig(options);
         this.workingPresetConfig = new PresetConfig(source.toJsonObject());
         this.wallFurniturePlaced = false;
         this.confirmedWiredCount=0;
         this.realVariableIdMap.clear();
         this.variableSnapshot.clear();
         this.variableReadError = null;
         this.realFurniIdMap = this.workingPresetConfig.applyPostConfig(this.importPostConfig());
         try {
            if (!this.workingPresetConfig.getWiredLinks().isEmpty() &&
                (this.extension.getPacketInfoManager().getPacketInfoFromName(HMessage.Direction.TOSERVER, "WiredLinkGetConfiguration") == null ||
                 this.extension.getPacketInfoManager().getPacketInfoFromName(HMessage.Direction.TOSERVER, "WiredLinkSaveConfiguration") == null ||
                 this.extension.getPacketInfoManager().getPacketInfoFromName(HMessage.Direction.TOCLIENT, "WiredLinkConfiguration") == null)) {
               throw new IllegalStateException("G-Earth does not know the WIRED Link messages for this client; update its packet catalog");
            }
            if(this.workingPresetConfig.getWiredRoomSettings()!=null && this.workingPresetConfig.getWiredRoomSettings().completeRoom) {
               requirePacket(HMessage.Direction.TOSERVER,"WiredSetRoomSettings");
               requirePacket(HMessage.Direction.TOSERVER,"WiredGetRoomSettings");
               requirePacket(HMessage.Direction.TOCLIENT,"WiredRoomSettings");
            }
            if(!this.workingPresetConfig.getRoomLinks().isEmpty() || !this.workingPresetConfig.getObjectVariables().isEmpty()) {
               requirePacket(HMessage.Direction.TOSERVER,"WiredSetObjectVariableValue");
               requirePacket(HMessage.Direction.TOSERVER,"WiredGetVariablesForObject");
               requirePacket(HMessage.Direction.TOCLIENT,"WiredVariablesForObject");
            }
            for(PresetFurniLabel label:this.workingPresetConfig.getFurnitureLabels()) {
               requirePacket(HMessage.Direction.TOSERVER,label.isRegion()?"SetFurnitureRegionData":"SetFurnitureLabelData");
               if(!label.isRegion()) requirePacket(HMessage.Direction.TOSERVER,"SetFurnitureLabelSettings");
               requirePacket(HMessage.Direction.TOCLIENT,"FurnitureLabelsAndRegionsUpdate");
               label.textPacket();
            }
            for(PresetWiredContract contract:this.workingPresetConfig.getContracts()) {
               requirePacket(HMessage.Direction.TOSERVER,"WiredOpenContract");
               requirePacket(HMessage.Direction.TOSERVER,"WiredUpdateContract");
               requirePacket(HMessage.Direction.TOCLIENT,"WiredContractContents");
               contract.savePacket(this.extension.getFurniDataTools());
            }
            for(PresetChest chest:this.workingPresetConfig.getChests()) {
               requirePacket(HMessage.Direction.TOSERVER,"SetChestOptions");
               requirePacket(HMessage.Direction.TOSERVER,"SetChestPreferences");
               requirePacket(HMessage.Direction.TOSERVER,"SetChestNotificationPreferences");
               chest.packets();
            }
            for (PresetWiredBase wired : this.workingPresetConfig.getPresetWireds().getAll()) wired.toPacket();
            WiredImportPlanner.orderVariables(this.workingPresetConfig.getPresetWireds().getVariables());
         } catch (RuntimeException error) {
            failWiredImport(error.getMessage());
            return;
         }
         this.expectFurniDrops = new HashMap();
         this.inventoryCache.clear();
         this.wallInventoryCache.clear();
         this.rootLocation = null;
         this.importZDelta = (double)0.0F;
         this.stateChangerLocation = null;
         boolean wallOnly = this.workingPresetConfig.getFurniture().isEmpty();
         if (wallOnly) {
            this.state = GPresetImporter.BuildingImportState.AWAITING_ROOT_LOCATION;
            if (!hasInlineCoords) {
               this.extension.sendVisualChatInfo("Select where the preset should be imported");
            }

            return;
         }

         FurniDataTools furniData = this.extension.getFurniDataTools();
         FloorState floorState = this.extension.getFloorState();
         this.roomItemsList = new ArrayList(floorState.getItems());
         List<FurniDropInfo> fakeDropInfo = new ArrayList();

         for(PresetFurni f : this.workingPresetConfig.getFurniture()) {
            fakeDropInfo.add(new FurniDropInfo(-1, -1, furniData.getFloorTypeId(f.getClassName()), this.importPostConfig().getItemSource(), -1));
         }

         Map<String, Integer> missing = AvailabilityChecker.missingItems(fakeDropInfo, this.extension.getInventory(), furniData, floorState, this.extension.useRoomFurni());
         if (missing == null) {
            this.extension.sendVisualChatInfo("ERROR: Inventory, catalog or furnidata is unavailable");
         } else {
            if (!missing.isEmpty()) {
               if (!this.extension.allowIncompleteBuilds()) {
                  this.extension.sendVisualChatInfo("ERROR: Some items were not available, check the availability first!");
                  return;
               }

               this.extension.sendVisualChatInfo("Some items were not available, building anyways..");
            }

            this.deferredRequiredStackTileDimensions.clear();
            this.acquiredStackTileFurniIds.clear();
            this.stackTilePrepFailureReason = null;
            this.refreshAvailableStackTiles();
            Set<Integer> requiredStackTileDimensions = this.collectRequiredStackTileDimensions();
            if (!this.allRequiredStackTilesAlreadyAvailable(requiredStackTileDimensions)) {
               this.deferredRequiredStackTileDimensions.addAll(requiredStackTileDimensions);
            }

            this.selectMainStackTile();
            if (!this.allAvailableStackTiles.isEmpty()) {
               this.extension.getLogger().log(String.format("Detected %d available types of stacktiles", this.allAvailableStackTiles.size()), "green");
            }

            if (this.extension.useRoomFurni()) {
               this.state = GPresetImporter.BuildingImportState.AWAITING_USE_ROOM_FURNI_RECT1;
               this.extension.sendVisualChatInfo("You've enabled use Room Furni, please select the start rectangle around the furni you want to use as backup or type 'all'");
            } else {
               this.state = GPresetImporter.BuildingImportState.AWAITING_UNOCCUPIED_SPACE;
               this.extension.sendVisualChatInfo("Select where the stack tile should be placed (empty area)");
            }
         }
      } else {
         this.extension.sendVisualChatInfo("ERROR: No preset selected!");
      }

   }

   private void moveAvatar(HMessage hMessage) {
      synchronized(this.lock) {
         boolean startAddingFurni = false;
         if (this.state == GPresetImporter.BuildingImportState.AWAITING_USE_ROOM_FURNI_RECT1) {
            hMessage.setBlocked(true);
            this.useRoomFurniRectCorner1 = new HPoint(hMessage.getPacket().readInteger(), hMessage.getPacket().readInteger());
            if (this.useRoomFurniRectCorner2 == null) {
               this.state = GPresetImporter.BuildingImportState.AWAITING_USE_ROOM_FURNI_RECT2;
               this.extension.sendVisualChatInfo("Select the end of the rectangle where the furni is");
            }
         } else if (this.state == GPresetImporter.BuildingImportState.AWAITING_USE_ROOM_FURNI_RECT2) {
            hMessage.setBlocked(true);
            this.useRoomFurniRectCorner2 = new HPoint(hMessage.getPacket().readInteger(), hMessage.getPacket().readInteger());
            if (this.reservedSpace == null) {
               this.state = GPresetImporter.BuildingImportState.AWAITING_UNOCCUPIED_SPACE;
               this.extension.sendVisualChatInfo("Select where the stack tile should be placed (empty area)");
            }
         } else if (this.state == GPresetImporter.BuildingImportState.AWAITING_UNOCCUPIED_SPACE) {
            hMessage.setBlocked(true);
            int sx = hMessage.getPacket().readInteger();
            int sy = hMessage.getPacket().readInteger();
            if (!isValidStagingArea(sx, sy)) {
               this.extension.getLogger().log("Choose a flat staging area with an adjacent valid tile for WIRED triggers", "orange");
               return;
            }
            this.reservedSpace = new HPoint(sx, sy);
            this.stackTileLocation = new HPoint(sx, sy);
            if (!this.deferredRequiredStackTileDimensions.isEmpty()) {
               boolean needRootAfterAcquire = this.rootLocation == null;
               this.extension.sendVisualChatInfo("Preparing required stacktiles from BC...");
               (new Thread(() -> {
                  if (this.ensureDeferredStackTilesIfNeeded()) {
                     synchronized(this.lock) {
                        if (this.state == GPresetImporter.BuildingImportState.AWAITING_UNOCCUPIED_SPACE) {
                           if (needRootAfterAcquire) {
                              this.state = GPresetImporter.BuildingImportState.AWAITING_ROOT_LOCATION;
                              this.extension.sendVisualChatInfo("Select where the preset should be imported");
                           } else if (this.workingPresetConfig != null && !this.workingPresetConfig.getFurniture().isEmpty()) {
                              this.state = GPresetImporter.BuildingImportState.ADD_UNSTACKABLES;
                              (new Thread(this::addUnstackables)).start();
                              this.extension.sendVisualChatInfo("Adding furniture...");
                           }

                        }
                     }
                  }
               })).start();
               return;
            }

            if (this.rootLocation == null) {
               this.state = GPresetImporter.BuildingImportState.AWAITING_ROOT_LOCATION;
               this.extension.sendVisualChatInfo("Select where the preset should be imported");
            } else {
               startAddingFurni = true;
            }
         } else if (this.state == GPresetImporter.BuildingImportState.AWAITING_ROOT_LOCATION) {
            hMessage.setBlocked(true);
            int x = hMessage.getPacket().readInteger();
            int y = hMessage.getPacket().readInteger();
            this.rootLocation = new HPoint(x, y);
            this.computeImportZDelta();
            if (!this.canUseWiredVariables() && this.presetNeedsStateChanges()) {
               this.state = GPresetImporter.BuildingImportState.AWAITING_UNOCCUPIED_SPACE_STATE_CHANGER;
               this.extension.sendVisualChatInfo("No wired permissions - select a tile for state changing furni (empty area near you)");
            } else {
               startAddingFurni = true;
            }
         } else if (this.state == GPresetImporter.BuildingImportState.AWAITING_UNOCCUPIED_SPACE_STATE_CHANGER) {
            hMessage.setBlocked(true);
            int scx = hMessage.getPacket().readInteger();
            int scy = hMessage.getPacket().readInteger();
            this.stateChangerLocation = new HPoint(scx, scy);
            startAddingFurni = true;
         }

         if (startAddingFurni) {
            if (this.workingPresetConfig.getFurniture().isEmpty()) {
               this.startWallOnlyImport();
            } else {
               this.state = GPresetImporter.BuildingImportState.ADD_UNSTACKABLES;
               (new Thread(this::addUnstackables)).start();
               this.extension.sendVisualChatInfo("Adding furniture...");
            }
         }

      }
   }

   private void computeImportZDelta() {
      this.importZDelta = (double)0.0F;
      if (this.workingPresetConfig != null && this.rootLocation != null) {
         Double srcAnchorH = this.workingPresetConfig.getSrcAnchorFloorHeight();
         if (srcAnchorH != null) {
            try {
               int dstAnchorH = PresetUtils.lowestFloorPoint(this.extension.getFloorState(), this.workingPresetConfig, this.rootLocation);
               if (dstAnchorH < 0 || dstAnchorH >= 256) {
                  return;
               }

               this.importZDelta = (double)dstAnchorH - srcAnchorH;
               if (Math.abs(this.importZDelta) > 0.001) {
                  this.extension.getLogger().log(String.format("Floor height delta: src=%.2f dst=%d -> shifting z by %+.2f", srcAnchorH, dstAnchorH, this.importZDelta), "orange");
               }
            } catch (Exception var3) {
               this.importZDelta = (double)0.0F;
            }

         }
      }
   }

   private void startWallOnlyImport() {
      this.state = GPresetImporter.BuildingImportState.MOVE_FURNITURE;
      (new Thread(() -> {
         this.placeWallItems();
         synchronized(this.lock) {
            if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
               this.state = GPresetImporter.BuildingImportState.NONE;
               this.extension.updatePreviewAvailability();
               this.extension.sendVisualChatInfo("Imported the preset successfully");
               this.extension.getLogger().log("Finished importing the preset!", "green");
            this.reportImport("Import finished");
            this.pauseGate.cancel();
            this.extension.setImportProgress("Import complete",1,1);
            }

         }
      })).start();
   }

   private boolean isStackTileClass(String className) {
      return className != null && className.startsWith("tile_stackmagic");
   }

   private boolean canUseWiredVariables() {
      return this.extension.getPermissions().canModifyWired() || this.extension.getPermissions().canMoveFurni();
   }

   private void refreshAvailableStackTiles() {
      FurniDataTools furniData = this.extension.getFurniDataTools();
      this.allAvailableStackTiles = new ArrayList();

      for(String c : Arrays.stream(StackTileSetting.values()).map(StackTileSetting::getClassName).collect(Collectors.toSet())) {
         List<HFloorItem> stackTiles = this.extension.getFloorState().getItemsFromType(furniData, c);
         if (!stackTiles.isEmpty()) {
            StackTileSetting setting = StackTileSetting.fromClassName(c);
            if (setting != null) {
               for(HFloorItem stackTile : stackTiles) {
                  this.allAvailableStackTiles.add(new StackTileInfo(stackTile.getId(), stackTile.getTile(), stackTile.getFacing().ordinal(), setting.getDimension()));
               }
            }
         }
      }

   }

   private int requiredStackTileDimension(String className) {
      if (className == null) {
         return 1;
      } else {
         FloorItemDetails details = this.extension.getFurniDataTools().getFloorItemDetails(className);
         if (details == null) {
            return 1;
         } else {
            int xDim = Math.max(1, details.xDim);
            int yDim = Math.max(1, details.yDim);
            if ((xDim != 1 || yDim != 2) && (xDim != 2 || yDim != 1)) {
               int maxDimension = Math.max(xDim, yDim);
               if (maxDimension <= 1) {
                  return 1;
               } else if (maxDimension <= 2) {
                  return 2;
               } else if (maxDimension <= 4) {
                  return 4;
               } else {
                  return maxDimension <= 6 ? 6 : 8;
               }
            } else {
               return -1;
            }
         }
      }
   }

   private Set<Integer> collectRequiredStackTileDimensions() {
      Set<Integer> dimensions = new HashSet();
      if (this.workingPresetConfig == null) {
         return dimensions;
      } else {
         FurniDataTools furniData = this.extension.getFurniDataTools();

         for(PresetFurni furni : this.workingPresetConfig.getFurniture()) {
            String className = furni.getClassName();
            if (!this.isStackTileClass(className) && furniData.isStackable(className)) {
               dimensions.add(this.requiredStackTileDimension(className));
            }
         }

         if (dimensions.isEmpty()) {
            dimensions.add(1);
         }

         return dimensions;
      }
   }

   private boolean hasStackTileForDimension(int requiredDimension) {
      if (this.allAvailableStackTiles != null && !this.allAvailableStackTiles.isEmpty()) {
         for(StackTileInfo info : this.allAvailableStackTiles) {
            int tileDimension = info.getDimension();
            if (requiredDimension == -1) {
               if (tileDimension == -1 || tileDimension >= 2) {
                  return true;
               }
            } else if (tileDimension == -1) {
               if (requiredDimension <= 2) {
                  return true;
               }
            } else if (tileDimension >= requiredDimension) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean allRequiredStackTilesAlreadyAvailable(Set<Integer> requiredDimensions) {
      this.refreshAvailableStackTiles();

      for(Integer requiredDimension : requiredDimensions) {
         if (!this.hasStackTileForDimension(requiredDimension)) {
            return false;
         }
      }

      return true;
   }

   private boolean stacktileMeetsRequiredDimension(String stacktileClass, int requiredDimension) {
      StackTileSetting setting = StackTileSetting.fromClassName(stacktileClass);
      if (setting == null) {
         return false;
      } else {
         int tileDimension = setting.getDimension();
         if (requiredDimension != -1) {
            if (tileDimension == -1) {
               return requiredDimension <= 2;
            } else {
               return tileDimension >= requiredDimension;
            }
         } else {
            return tileDimension == -1 || tileDimension >= 2;
         }
      }
   }

   private boolean ensureRequiredStackTilesAvailable(Set<Integer> requiredDimensions) {
      this.stackTilePrepFailureReason = null;
      this.refreshAvailableStackTiles();

      for(Integer requiredDimension : requiredDimensions) {
         if (!this.hasStackTileForDimension(requiredDimension) && !this.acquireStackTileFromBC(requiredDimension)) {
            return false;
         }
      }

      this.refreshAvailableStackTiles();
      return true;
   }

   private boolean acquireStackTileFromBC(int requiredDimension) {
      FurniDataTools furniData = this.extension.getFurniDataTools();
      FloorState floor = this.extension.getFloorState();
      BCCatalog catalog = this.extension.getCatalog();
      List<StackTileSetting> candidates = new ArrayList();
      if (requiredDimension == -1) {
         candidates.add(StackTileSetting.Medium);
         candidates.add(StackTileSetting.Large);
         candidates.add(StackTileSetting.XL);
         candidates.add(StackTileSetting.XXL);
         candidates.add(StackTileSetting.XXXL);
      } else {
         for(StackTileSetting setting : Arrays.asList(StackTileSetting.Small, StackTileSetting.Large, StackTileSetting.XL, StackTileSetting.XXL, StackTileSetting.XXXL)) {
            if (setting.getDimension() >= requiredDimension) {
               candidates.add(setting);
            }
         }
      }

      if (candidates.isEmpty()) {
         this.stackTilePrepFailureReason = String.format("No stacktile candidate for required size %d", requiredDimension);
         return false;
      } else {
         HPoint dropLocation = this.reservedSpace != null ? this.reservedSpace : (this.rootLocation != null ? this.rootLocation : new HPoint(0, 0));

         for(StackTileSetting candidate : candidates) {
            String className = candidate.getClassName();
            Integer typeId = furniData.getFloorTypeId(className);
            if (typeId != null) {
               BCCatalog.SingleFurniProduct bcProduct = catalog != null ? catalog.getFloorProduct(typeId) : null;
               int offerId = bcProduct != null ? bcProduct.getOfferId() : -1;
               if (offerId == -1) {
                  FloorItemDetails details = furniData.getFloorItemDetails(className);
                  if (details != null) {
                     offerId = details.bcOfferId;
                  }
               }

               if (offerId != -1) {
                  Set<Integer> beforeIds = (Set)floor.getItemsFromType(furniData, className).stream().map(HFloorItem::getId).collect(Collectors.toSet());

                  for(int attempt = 1; attempt <= 3; ++attempt) {
                     Set<Integer> beforeAllIds = (Set)floor.getItems().stream().map(HFloorItem::getId).collect(Collectors.toSet());
                     this.extension.sendToServer(new HPacket("BuildersClubPlaceRoomItem", HMessage.Direction.TOSERVER, new Object[]{-1, offerId, "", dropLocation.getX(), dropLocation.getY(), 0}));
                     long deadline = System.currentTimeMillis() + 7000L;

                     while(System.currentTimeMillis() < deadline) {
                        Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 150));
                        HFloorItem newStacktile = (HFloorItem)floor.getItemsFromType(furniData, className).stream().filter((item) -> !beforeIds.contains(item.getId())).findFirst().orElse(null);
                        if (newStacktile != null) {
                           this.acquiredStackTileFurniIds.add(newStacktile.getId());
                           return true;
                        }

                        HFloorItem compatibleNewStacktile = (HFloorItem)floor.getItems().stream().filter((item) -> !beforeAllIds.contains(item.getId())).filter((item) -> {
                           String floorClass = furniData.getFloorItemName(item.getTypeId());
                           return this.isStackTileClass(floorClass) && this.stacktileMeetsRequiredDimension(floorClass, requiredDimension);
                        }).findFirst().orElse(null);
                        if (compatibleNewStacktile != null) {
                           String placedClass = furniData.getFloorItemName(compatibleNewStacktile.getTypeId());
                           this.acquiredStackTileFurniIds.add(compatibleNewStacktile.getId());
                           this.extension.getLogger().log(String.format("Acquired compatible stacktile %s (id=%d) for required size %d", placedClass, compatibleNewStacktile.getId(), requiredDimension), "green");
                           return true;
                        }
                     }
                  }
               }
            }
         }

         StackTileSetting preferred = StackTileSetting.forDimension(requiredDimension);
         this.stackTilePrepFailureReason = String.format("Timed out acquiring stacktile %s from BC", preferred.getClassName());
         return false;
      }
   }

   private void selectMainStackTile() {
      if (this.allAvailableStackTiles != null && !this.allAvailableStackTiles.isEmpty()) {
         StackTileInfo best = (StackTileInfo)this.allAvailableStackTiles.stream().min(Comparator.comparingInt((s) -> {
            int d = s.getDimension();
            return d == -1 ? 2 : d;
         })).orElse((StackTileInfo)this.allAvailableStackTiles.get(0));
         this.mainStackTile = best.getFurniId();
         this.mainStackDimensions = best.getDimension() == -1 ? 2 : best.getDimension();
      } else {
         this.mainStackTile = -1;
         this.mainStackDimensions = 1;
      }
   }

   private boolean ensureDeferredStackTilesIfNeeded() {
      if (this.deferredRequiredStackTileDimensions.isEmpty()) {
         return true;
      } else {
         Set<Integer> requiredNow = new HashSet(this.deferredRequiredStackTileDimensions);
         this.deferredRequiredStackTileDimensions.clear();
         boolean prepared = this.ensureRequiredStackTilesAvailable(requiredNow);
         this.selectMainStackTile();
         if (prepared && this.mainStackTile != -1) {
            this.extension.getLogger().log(String.format("Prepared %d stacktile type(s) for import", this.allAvailableStackTiles.size()), "green");
            return true;
         } else {
            String reason = this.stackTilePrepFailureReason != null ? this.stackTilePrepFailureReason : "Could not prepare required stack tiles from BC";
            this.extension.getLogger().log(reason, "red");
            System.err.println("[G-Presets][Importer] " + reason);
            this.extension.sendVisualChatInfo("ERROR: " + reason);
            this.state = GPresetImporter.BuildingImportState.NONE;
            return false;
         }
      }
   }

   private void dropFurni(FurniDropInfo dropInfo) {
      FurniDataTools furniData = this.extension.getFurniDataTools();
      int typeId = dropInfo.getTypeId();
      String className = furniData.getFloorItemName(typeId);
      ItemSource source = dropInfo.getItemSource();
      Inventory inventory = this.extension.getInventory();
      int offerId = furniData.getFloorItemDetails(className).bcOfferId;
      if (!this.inventoryCache.containsKey(typeId)) {
         this.inventoryCache.put(typeId, new LinkedList(inventory.getFloorItemsByType(typeId)));
      }

      LinkedList<HInventoryItem> inventoryItems = (LinkedList)this.inventoryCache.get(typeId);
      boolean useBC = source == ItemSource.ONLY_BC || source == ItemSource.PREFER_BC && offerId != -1 || source == ItemSource.PREFER_INVENTORY && inventoryItems.isEmpty();
      boolean useRoomFurni = this.extension.useRoomFurni();
      if (useBC) {
         if (offerId == -1) {
            if (!this.extension.allowIncompleteBuilds()) {
               this.state = GPresetImporter.BuildingImportState.NONE;
               this.extension.sendVisualChatInfo(String.format("ERROR: Couldn't find the item '%s' in BC warehouse.. aborting", className));
            } else {
               this.extension.sendVisualChatInfo(String.format("Couldn't find the item '%s' in BC warehouse.. continuing", className));
            }

            return;
         }

         this.extension.sendToServer(new HPacket("BuildersClubPlaceRoomItem", HMessage.Direction.TOSERVER, new Object[]{-1, offerId, "", dropInfo.getX(), dropInfo.getY(), dropInfo.getRotation()}));
         Utils.sleep(230);
      } else {
         if (inventoryItems.isEmpty() && !useRoomFurni) {
            if (!this.extension.allowIncompleteBuilds()) {
               this.state = GPresetImporter.BuildingImportState.NONE;
               this.extension.sendVisualChatInfo(String.format("ERROR: Couldn't find '%s' in inventory.. aborting", className));
            } else {
               this.extension.sendVisualChatInfo(String.format("Couldn't find '%s' in inventory.. continuing", className));
            }

            return;
         }

         if (useRoomFurni && inventoryItems.isEmpty()) {
            this.extension.getLogger().log(String.format("[UseRoomFurni] Looking for '%s' (typeId=%d) in room rectangle (furniId=%d)", className, dropInfo.getTypeId(), dropInfo.getFurniId()), "orange");
            HFloorItem floorItem = (HFloorItem)this.roomItemsList.stream().filter((furni) -> furni.getTypeId() == dropInfo.getTypeId() && this.isInsideUseRoomRectangle(furni.getTile().getX(), furni.getTile().getY())).findFirst().orElse(null);
            if (floorItem != null) {
               this.roomItemsList.removeIf((itemx) -> itemx.getId() == floorItem.getId());
               int furniMapKey = dropInfo.getFurniId() >= 0 ? dropInfo.getFurniId() : dropInfo.getTypeId();
               this.realFurniIdMap.put(furniMapKey, floorItem.getId());
               this.extension.getLogger().log(String.format("[UseRoomFurni] Found '%s' (id=%d at %d,%d), mapping presetFurniId=%d -> roomId=%d, moving to %d,%d rot=%d", className, floorItem.getId(), floorItem.getTile().getX(), floorItem.getTile().getY(), furniMapKey, floorItem.getId(), dropInfo.getX(), dropInfo.getY(), dropInfo.getRotation()), "orange");
               HPacket packet = new HPacket("MoveObject", HMessage.Direction.TOSERVER, new Object[]{floorItem.getId(), dropInfo.getX(), dropInfo.getY(), dropInfo.getRotation()});
               this.extension.sendToServer(packet);
               Utils.sleep(600);
            } else if (!this.extension.allowIncompleteBuilds()) {
               this.state = GPresetImporter.BuildingImportState.NONE;
               this.extension.getLogger().log(String.format("[UseRoomFurni] ERROR: '%s' not found in rectangle (roomItems remaining=%d)", className, this.roomItemsList.size()), "red");
               this.extension.sendVisualChatInfo(String.format("ERROR: Couldn't find '%s' in inventory or room.. aborting", className));
            } else {
               this.extension.getLogger().log(String.format("[UseRoomFurni] WARN: '%s' not found in rectangle, continuing", className), "orange");
               this.extension.sendVisualChatInfo(String.format("Couldn't find '%s' in inventory or room.. continuing", className));
            }

            return;
         }

         HInventoryItem item = (HInventoryItem)inventoryItems.pollFirst();
         this.extension.sendToServer(new HPacket("PlaceObject", HMessage.Direction.TOSERVER, new Object[]{String.format("-%d %d %d %d", item.getId(), dropInfo.getX(), dropInfo.getY(), dropInfo.getRotation())}));
         Utils.sleep(150);
      }

   }

   private boolean isInsideUseRoomRectangle(int px, int py) {
      if (this.useRoomFurniRectCorner1 != null && this.useRoomFurniRectCorner2 != null) {
         int x1 = this.useRoomFurniRectCorner1.getX();
         int y1 = this.useRoomFurniRectCorner1.getY();
         int x2 = this.useRoomFurniRectCorner2.getX();
         int y2 = this.useRoomFurniRectCorner2.getY();
         int minX = Math.min(x1, x2);
         int maxX = Math.max(x1, x2);
         int minY = Math.min(y1, y2);
         int maxY = Math.max(y1, y2);
         return px >= minX && px <= maxX && py >= minY && py <= maxY;
      } else {
         return false;
      }
   }

   private void moveFurni(int furniId, int x, int y, int rot, boolean moveStackTile, double height) {
      boolean stackTileMoved = false;
      if (moveStackTile) {
         StackTileInfo stackInfo = StackTileUtils.findBestDropLocation(x, y, this.allAvailableStackTiles, this.extension.getFloorState());
         if (stackInfo != null) {
            this.moveFurni(stackInfo.getFurniId(), stackInfo.getLocation().getX(), stackInfo.getLocation().getY(), stackInfo.getRotation(), false, (double)-1.0F);
            stackTileMoved = true;
            if (height != (double)-1.0F) {
               int heightCenti = (int)Math.round(height * (double)100.0F);
               this.extension.getLogger().verbose("moveFurni", String.format("setting stacktile %d height to %d (raw=%.4f) for item %d at (%d,%d)", stackInfo.getFurniId(), heightCenti, height, furniId, x, y));
               this.internalPacketBypass.incrementAndGet();
               this.extension.sendToServer(new HPacket("SetCustomStackingHeight", HMessage.Direction.TOSERVER, new Object[]{stackInfo.getFurniId(), heightCenti}));
               Utils.sleep(40);
            }
         }
      }

      if (stackTileMoved) {
         Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 200));
      }

      this.extension.sendToServer(new HPacket("MoveObject", HMessage.Direction.TOSERVER, new Object[]{furniId, x, y, rot}));
      Utils.sleep(60);
   }

   private boolean presetNeedsStateChanges() {
      if (this.workingPresetConfig == null) {
         return false;
      } else {
         for(PresetFurni f : this.workingPresetConfig.getFurniture()) {
            if (f.getState() != null) {
               return true;
            }
         }

         return false;
      }
   }

   private void attemptSetState(int furniId, String targetState) {
      FloorState floor = this.extension.getFloorState();
      FurniDataTools furniData = this.extension.getFurniDataTools();
      if (floor != null && furniData != null && floor.furniFromId(furniId) != null) {
         HFloorItem item = floor.furniFromId(furniId);
         boolean isStackable = furniData.isStackable(furniData.getFloorItemName(item.getTypeId()));
         HPoint originalLocation = item.getTile();
         int originalRotation = item.getFacing().ordinal();
         boolean movedForStateChange = false;
         String currentState = StateExtractor.stateFromItem(item);
         if (currentState != null && currentState.equals(targetState)) {
            this.extension.getLogger().verbose("setState", String.format("furniId=%d already at state=%s, skipping", furniId, targetState));
         } else {
            boolean wiredAvailable = this.canUseWiredVariables();
            boolean stateApplied = false;
            this.extension.getLogger().verbose("setState", String.format("furniId=%d typeId=%d at (%d,%d) currentState=%s targetState=%s wiredAvailable=%s (canModifyWired=%s canMoveFurni=%s)", furniId, item.getTypeId(), originalLocation.getX(), originalLocation.getY(), currentState, targetState, wiredAvailable, this.extension.getPermissions().canModifyWired(), this.extension.getPermissions().canMoveFurni()));
            if (wiredAvailable) {
               try {
                  int targetStateInt = Integer.parseInt(targetState);
                  this.throttleWiredSetObjectVariableValue();
                  this.extension.sendToServer(new HPacket("WiredSetObjectVariableValue", HMessage.Direction.TOSERVER, new Object[]{0, furniId, "-110", targetStateInt}));
                  Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 60));
                  HFloorItem itemAfterWired = floor.furniFromId(furniId);
                  String stateAfterWired = itemAfterWired != null ? StateExtractor.stateFromItem(itemAfterWired) : null;
                  stateApplied = stateAfterWired != null && stateAfterWired.equals(targetState);
                  this.extension.getLogger().verbose("setState", String.format("furniId=%d wired write -> stateAfter=%s applied=%s", furniId, stateAfterWired, stateApplied));
               } catch (NumberFormatException var20) {
                  this.extension.getLogger().verbose("setState", String.format("furniId=%d non-numeric targetState=%s, falling back to manual cycling", furniId, targetState));
               }
            }

            if (!stateApplied) {
               HPoint stateLocation = this.stateChangerLocation != null ? this.stateChangerLocation : this.stackTileLocation;
               if (stateLocation == null) {
                  this.extension.getLogger().verbose("setState", String.format("furniId=%d no state-changer tile available, cannot fall back to manual cycling", furniId));
                  return;
               }

               synchronized(this.lock) {
                  if (this.state != GPresetImporter.BuildingImportState.NONE && (originalLocation.getX() != stateLocation.getX() || originalLocation.getY() != stateLocation.getY())) {
                     this.extension.getLogger().verbose("setState", String.format("furniId=%d relocating to state-changer tile (%d,%d) for manual cycling", furniId, stateLocation.getX(), stateLocation.getY()));
                     this.moveFurni(furniId, stateLocation.getX(), stateLocation.getY(), 0, false, (double)-1.0F);
                     movedForStateChange = true;
                  }
               }

               HFloorItem itemNow = floor.furniFromId(furniId);
               String cycleState = itemNow != null ? StateExtractor.stateFromItem(itemNow) : currentState;
               String previous = "-1";

               int i;
               for(i = 0; this.state != GPresetImporter.BuildingImportState.NONE && cycleState != null && !cycleState.equals(targetState) && !cycleState.equals(previous) && i < 20; ++i) {
                  previous = cycleState;
                  this.internalPacketBypass.incrementAndGet();
                  this.extension.sendToServer(new HPacket("UseFurniture", HMessage.Direction.TOSERVER, new Object[]{furniId, 0}));
                  Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 60));
                  HFloorItem itemNew = floor.furniFromId(furniId);
                  cycleState = itemNew != null ? StateExtractor.stateFromItem(itemNew) : null;
               }

               this.extension.getLogger().verbose("setState", String.format("furniId=%d manual cycling done after %d clicks, finalState=%s targetState=%s", furniId, i, cycleState, targetState));
            }

            if (movedForStateChange) {
               this.moveFurni(furniId, originalLocation.getX(), originalLocation.getY(), originalRotation, isStackable, (double)-1.0F);
            }

         }
      }
   }

   private void wiredSaved(HMessage hMessage) {
      if (this.state == GPresetImporter.BuildingImportState.SETUP_WIRED) {
         this.wiredSaveConfirmation.release();
      }

   }

   private boolean saveWired(PresetWiredBase presetWired, int ignoredAttempt) {
      for (int attempt = 0; attempt < 3 && this.state == BuildingImportState.SETUP_WIRED; attempt++) {
         boolean unresolved = presetWired.getVariableIds().stream()
            .anyMatch(id -> PresetWiredBase.isLocalVariableId(id) && !presetWired.getExternalVariableIds().contains(id) && !this.realVariableIdMap.containsKey(id));
         if (unresolved && !refreshVariableMap()) {
            return failWiredImport("Timed out reading WIRED variables; import stopped");
         }
         PresetWiredBase resolved;
         try {
            resolved = presetWired.remap(this.realFurniIdMap, this.realVariableIdMap);
            if (resolved == null) return true; // furniture explicitly excluded by post-configuration
            resolved.translateRoomCoordinates(this.rootLocation.getX(),this.rootLocation.getY());
            resolved.toPacket(); // validate before waiting for or sending anything
         } catch (RuntimeException error) {
            return failWiredImport(error.getMessage());
         }
         Utils.sleep(300);
         if (this.state != BuildingImportState.SETUP_WIRED) return false;
         this.wiredSaveConfirmation.drainPermits();
         this.wiredSaveError = null;
         resolved.applyWiredConfig(this.extension);
         boolean confirmed;
         try {
            confirmed = this.wiredSaveConfirmation.tryAcquire(5L, TimeUnit.SECONDS);
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return failWiredImport("WIRED import interrupted");
         }
         if (this.state != BuildingImportState.SETUP_WIRED) return false;
         if (this.wiredSaveError != null) return failWiredImport(this.wiredSaveError + " (preset WIRED " + presetWired.getWiredId() + ")");
         if (confirmed) {
            this.extension.getExporter().cacheWiredConfig(resolved);
            if (presetWired instanceof PresetWiredVariable && !refreshVariableMap()) {
               return failWiredImport("Variable saved, but its new ID could not be read; import stopped");
            }
            return true;
         }
      }
      return failWiredImport("No save confirmation for WIRED " + presetWired.getWiredId() + " after 3 attempts");
   }

   private void reportImport(String outcome) {
      if(this.workingPresetConfig==null)return;
      java.util.List<PresetFurni> unresolved=new ArrayList<>();
      int resolved=0;
      synchronized(this.lock) {
         for(PresetFurni item:this.workingPresetConfig.getFurniture()) {
            if(this.realFurniIdMap.containsKey(item.getFurniId()))resolved++;else unresolved.add(item);
         }
      }
      this.extension.getLogger().log(outcome + ": " + resolved + "/" + this.workingPresetConfig.getFurniture().size() + " floor furniture IDs resolved; " + unresolved.size() + " unconfirmed.","orange");
      if(!unresolved.isEmpty()) {
         String names=unresolved.stream().limit(20).map(PresetFurni::getFurniName).map(String::valueOf).collect(Collectors.joining(", "));
         this.extension.getLogger().log("Unconfirmed: " + names + (unresolved.size()>20?" ...":""),"orange");
      }
      this.extension.getLogger().log("WIRED configurations confirmed: " + confirmedWiredCount + "; wall items are not included in this report.","orange");
   }

   private boolean failWiredImport(String reason) {
      this.extension.getLogger().log("Import incomplete: " + reason, "red");
      this.reportImport("Import incomplete");
      this.extension.sendVisualChatInfo("Import incomplete; see G-Presets logs");
      this.reset();
      return false;
   }

   private void moveFurniture() {
      if(!checkpoint(BuildingImportState.MOVE_FURNITURE))return;
      FurniDataTools furniData = this.extension.getFurniDataTools();
      List<PresetFurni> moveList = new ArrayList();
      synchronized(this.lock) {
         this.workingPresetConfig.getFurniture().forEach((px) -> {
            if (furniData.isStackable(px.getClassName()) && WiredKind.fromClassName(px.getClassName()) != WiredKind.TRIGGER && !this.isStackTileClass(px.getClassName()) && this.realFurniIdMap.containsKey(px.getFurniId())) {
               moveList.add(px);
            }

         });
         this.workingPresetConfig.getFurniture().forEach((px) -> {
            if (furniData.isStackable(px.getClassName()) && WiredKind.fromClassName(px.getClassName()) == WiredKind.TRIGGER && this.realFurniIdMap.containsKey(px.getFurniId())) {
               moveList.add(px);
            }

         });
      }

      this.extension.getLogger().verbose("import", String.format("moveFurniture: moveList=%d rootLocation=(%d,%d) stateChangerLocation=%s stackTileLocation=%s wiredAvailable=%s", moveList.size(), this.rootLocation.getX(), this.rootLocation.getY(), this.stateChangerLocation == null ? "null" : "(" + this.stateChangerLocation.getX() + "," + this.stateChangerLocation.getY() + ")", this.stackTileLocation == null ? "null" : "(" + this.stackTileLocation.getX() + "," + this.stackTileLocation.getY() + ")", this.canUseWiredVariables()));
      Map<Integer, PresetFurni> movesByRealId = new HashMap();
      long stateChangeCount = moveList.stream().filter((m) -> m.getState() != null && this.realFurniIdMap.containsKey(m.getFurniId())).count();
      if (stateChangeCount > 0L) {
         this.extension.getLogger().log(String.format("Changing states for %d furni...", stateChangeCount), "orange");
      }

      for(PresetFurni movedFurni : moveList) {
         if(!checkpoint(BuildingImportState.MOVE_FURNITURE))return;
         if (this.state != GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
            break;
         }

         if (movedFurni.getState() != null) {
            Integer realFurniId = (Integer)this.realFurniIdMap.get(movedFurni.getFurniId());
            if (realFurniId != null) {
               this.attemptSetState(realFurniId, movedFurni.getState());
            }
         }
      }

      int i = 0;

      while(this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE && i < moveList.size()) {
         if(!checkpoint(BuildingImportState.MOVE_FURNITURE))return;
         this.extension.setImportProgress("Sending furniture moves", i, moveList.size());
         PresetFurni moveFurni = (PresetFurni)moveList.get(i);
         ++i;
         int realFurniId = (Integer)this.realFurniIdMap.get(moveFurni.getFurniId());
         movesByRealId.put(realFurniId, moveFurni);
         this.moveFurni(realFurniId, moveFurni.getLocation().getX() + this.rootLocation.getX(), moveFurni.getLocation().getY() + this.rootLocation.getY(), moveFurni.getRotation(), true, moveFurni.getLocation().getZ() + this.importZDelta);
      }

      Utils.sleep(300 + this.extension.getSafeFeedbackTimeout());
      if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
         List<HFloorItem> maybeNotMovedItems = new ArrayList<>(this.extension.getFloorState().getFurniOnTile(this.stackTileLocation.getX(), this.stackTileLocation.getY()));
         maybeNotMovedItems.addAll(this.extension.getFloorState().getFurniOnTile(this.stackTileLocation.getX() + 1, this.stackTileLocation.getY()));

         for(HFloorItem o : maybeNotMovedItems.stream().filter((h) -> {
            PresetFurni p = (PresetFurni)movesByRealId.get(h.getId());
            if (p == null) {
               return false;
            } else {
               return p.getLocation().getX() + this.rootLocation.getX() != h.getTile().getX() || p.getLocation().getY() + this.rootLocation.getY() != h.getTile().getY() || p.getRotation() != h.getFacing().ordinal();
            }
         }).collect(Collectors.toList())) {
            if (this.state != GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
               break;
            }

            PresetFurni p = (PresetFurni)movesByRealId.get(o.getId());
            this.moveFurni(o.getId(), p.getLocation().getX() + this.rootLocation.getX(), p.getLocation().getY() + this.rootLocation.getY(), p.getRotation(), true, p.getLocation().getZ() + this.importZDelta);
         }
      }

      if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
         for(StackTileInfo stackTileInfo : this.allAvailableStackTiles) {
            this.moveFurni(stackTileInfo.getFurniId(), stackTileInfo.getLocation().getX(), stackTileInfo.getLocation().getY(), stackTileInfo.getRotation(), false, (double)-1.0F);
         }
      }

      if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
         this.placeWallItems();
      }

      if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
         this.placePresetStackTiles();
      }

      if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
         this.applyFloorFurniVariables();
      }

      if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
         this.applyWallFurniVariables();
      }
      if(this.state==BuildingImportState.MOVE_FURNITURE) this.applyObjectVariables();
      if (this.state == BuildingImportState.MOVE_FURNITURE) {
         this.applyFurnitureLabels();
      }
      if(this.state==BuildingImportState.MOVE_FURNITURE) this.applyRoomSettings();

      synchronized(this.lock) {
         if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
            this.state = GPresetImporter.BuildingImportState.NONE;
            this.extension.updatePreviewAvailability();
               this.extension.sendVisualChatInfo("Imported the preset successfully");
            this.extension.getLogger().log("Finished importing the preset!", "green");
            this.reportImport("Import finished");
            this.pauseGate.cancel();
            this.extension.setImportProgress("Import complete",1,1);
         }

      }
   }

   private void applyRoomSettings() {
      PresetWiredRoomSettings settings=this.workingPresetConfig.getWiredRoomSettings();
      if(settings==null || !settings.completeRoom) return;
      this.receivedRoomSettings=null;this.roomSettingsResponse.drainPermits();this.awaitingRoomSettings=true;
      try {
         if(!this.extension.sendToServer(settings.savePacket())) { failWiredImport("Cannot save WIRED room settings");return; }
         Utils.sleep(Math.max(300,this.extension.getSafeFeedbackTimeout()));
         if(this.state!=BuildingImportState.MOVE_FURNITURE) return;
         if(!this.extension.sendToServer(PresetWiredRoomSettings.requestPacket()) || !this.roomSettingsResponse.tryAcquire(5L,TimeUnit.SECONDS) || !settings.matches(this.receivedRoomSettings)) {
            failWiredImport("WIRED room settings were not confirmed");
         }
      } catch(InterruptedException error) { Thread.currentThread().interrupt();failWiredImport("Room settings import interrupted"); }
      finally { this.awaitingRoomSettings=false; }
   }

   private void contractReceived(HMessage message) {
      Integer expected=this.pendingContractId;
      if(expected==null) return;
      try {
         PresetWiredContract c=PresetWiredContract.fromPacket(new HPacket(message.getPacket()),this.extension.getFurniDataTools());
         if(c.furniId==expected) {
            message.setBlocked(true); this.receivedContract=c; this.contractResponse.release();
         }
      } catch(RuntimeException error) {
         this.extension.getLogger().log("Cannot read contract: " + error.getMessage(),"red");
      }
   }

   private PresetWiredContract requestContract(int id) {
      this.receivedContract=null; this.contractResponse.drainPermits(); this.pendingContractId=id;
      try {
         if(!this.extension.sendToServer(PresetWiredContract.requestPacket(id))) return null;
         return this.contractResponse.tryAcquire(5L,TimeUnit.SECONDS)?this.receivedContract:null;
      } catch(InterruptedException error) { Thread.currentThread().interrupt(); return null; }
      finally { this.pendingContractId=null; }
   }

   private static byte[] packetPayload(HPacket p) { return java.util.Arrays.copyOfRange(p.toBytes(),6,p.getBytesLength()); }

   private boolean applyContracts() {
      for(PresetWiredContract original:this.workingPresetConfig.getContracts()) {
         if(!checkpoint(BuildingImportState.SETUP_WIRED))return false;
         if(this.state!=BuildingImportState.SETUP_WIRED) return false;
         Integer id=this.realFurniIdMap.get(original.furniId);
         if(id==null) return failWiredImport("Missing contract furniture " + original.furniId);
         PresetWiredContract mapped=original.withFurniId(id);
         HPacket save=mapped.savePacket(this.extension.getFurniDataTools());
         if(!this.extension.sendToServer(save)) return failWiredImport("Cannot save contract " + id);
         Utils.sleep(Math.max(300,this.extension.getSafeFeedbackTimeout()));
         PresetWiredContract actual=requestContract(id);
         if(this.state!=BuildingImportState.SETUP_WIRED) return false;
         if(actual==null || !java.util.Arrays.equals(packetPayload(save),packetPayload(actual.savePacket(this.extension.getFurniDataTools())))) {
            return failWiredImport("Contract settings were not confirmed for " + id);
         }
      }
      return this.state==BuildingImportState.SETUP_WIRED;
   }

   private void applyObjectVariables() {
      for(PresetObjectVariables original:this.workingPresetConfig.getObjectVariables()) {
         if(!checkpoint(BuildingImportState.MOVE_FURNITURE))return;
         if(this.state!=BuildingImportState.MOVE_FURNITURE) return;
         try {
            PresetObjectVariables mapped=original.translate(this.rootLocation.getX(),this.rootLocation.getY()).remap(this.realFurniIdMap,this.realVariableIdMap);
            for(HPacket p:mapped.packets()) {
               if(this.state!=BuildingImportState.MOVE_FURNITURE) return;
               this.throttleWiredSetObjectVariableValue();
               if(!this.extension.sendToServer(p)) { failWiredImport("Cannot save variable holder " + mapped.objectId);return; }
            }
            Utils.sleep(Math.max(200,this.extension.getSafeFeedbackTimeout()));
            if(!fetchObjectVariables(mapped.target,mapped.objectId,3000L) || !mapped.matches(this.inspectedFurniVariables.getOrDefault(mapped.objectId,Collections.emptyMap()))) {
               failWiredImport("Variable values not confirmed for holder " + mapped.objectId);return;
            }
         } catch(RuntimeException error) { failWiredImport(error.getMessage());return; }
      }
   }

   private boolean applyRoomLinks() {
      for(PresetRoomLink original:this.workingPresetConfig.getRoomLinks()) {
         if(!checkpoint(BuildingImportState.SETUP_WIRED))return false;
         if(this.state!=BuildingImportState.SETUP_WIRED) return false;
         Integer id=this.realFurniIdMap.get(original.furniId);
         if(id==null) return failWiredImport("Missing room linker " + original.furniId);
         if(!this.extension.sendToServer(original.withFurniId(id).savePacket())) return failWiredImport("Cannot save room linker " + id);
         Utils.sleep(Math.max(200,this.extension.getSafeFeedbackTimeout()));
         boolean confirmed=fetchObjectVariables(0,id,3000L) && this.inspectedFurniVariables.getOrDefault(id,Collections.emptyMap()).getOrDefault("~103",Integer.MIN_VALUE)==(original.destination.isEmpty()?0:Integer.parseInt(original.destination));
         if(!confirmed) return failWiredImport("Room linker destination not confirmed for " + id);
      }
      return this.state==BuildingImportState.SETUP_WIRED;
   }

   private boolean applyChests() {
      for(PresetChest original:this.workingPresetConfig.getChests()) {
         if(!checkpoint(BuildingImportState.SETUP_WIRED))return false;
         Integer id=this.realFurniIdMap.get(original.furniId);
         if(id==null) return failWiredImport("Missing chest furniture " + original.furniId);
         PresetChest mapped=original.withFurniId(id);
         for(HPacket packet:mapped.packets()) {
            if(this.state!=BuildingImportState.SETUP_WIRED) return false;
            if(!this.extension.sendToServer(packet)) return failWiredImport("Cannot save chest settings for " + id);
            Utils.sleep(Math.max(300,this.extension.getSafeFeedbackTimeout()));
         }
         boolean confirmed=false;
         for(int attempt=0;attempt<25 && this.state==BuildingImportState.SETUP_WIRED;attempt++) {
            HFloorItem item=this.extension.getFloorState().furniFromId(id);
            if(item!=null && item.getStuff() instanceof MapStuffData && mapped.matches((MapStuffData)item.getStuff())) { confirmed=true; break; }
            Utils.sleep(200);
         }
         if(!confirmed) return failWiredImport("Chest settings/capacity not confirmed for " + id + "; capacity upgrades are not purchased automatically");
      }
      return this.state==BuildingImportState.SETUP_WIRED;
   }

   private void linkConfigurationReceived(HMessage message) {
      Integer expected = this.pendingLinkId;
      if (expected == null) return;
      try {
         HWiredLinkConfiguration received = new HWiredLinkConfiguration(new HPacket(message.getPacket()));
         if (received.configuration.furniId == expected && received.roomId == this.extension.getFloorState().getRoomId()) {
            message.setBlocked(true);
            this.receivedLink = received;
            this.linkResponse.release();
         }
      } catch (RuntimeException error) {
         this.extension.getLogger().log("Cannot read WIRED Link: " + error.getMessage(), "red");
      }
   }

   private HWiredLinkConfiguration requestLink(int id) {
      this.receivedLink = null;
      this.linkResponse.drainPermits();
      this.pendingLinkId = id;
      try {
         if (!this.extension.sendToServer(PresetWiredLink.requestPacket(id))) return null;
         return this.linkResponse.tryAcquire(5L, TimeUnit.SECONDS) ? this.receivedLink : null;
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
         return null;
      } finally {
         this.pendingLinkId = null;
      }
   }

   private boolean applyWiredLinks() {
      for (PresetWiredLink original : this.workingPresetConfig.getWiredLinks()) {
         if(!checkpoint(BuildingImportState.SETUP_WIRED))return false;
         if (this.state != BuildingImportState.SETUP_WIRED) return false;
         Integer id = this.realFurniIdMap.get(original.furniId);
         if (id == null) continue;
         HWiredLinkConfiguration available = requestLink(id);
         if (available == null || this.state != BuildingImportState.SETUP_WIRED) return failWiredImport("No WIRED Link configuration for " + id);
         if (!available.hasNetwork(original.networkId)) return failWiredImport("WIRED Link network " + original.networkId + " is unavailable in this room/account");
         PresetWiredLink mapped = original.withFurniId(id);
         if (!this.extension.sendToServer(mapped.savePacket())) return failWiredImport("Cannot save WIRED Link " + id);
         Utils.sleep(Math.max(300, this.extension.getSafeFeedbackTimeout()));
         HWiredLinkConfiguration actual = requestLink(id);
         if (actual == null || !mapped.networkId.equals(actual.configuration.networkId) || mapped.receiveLocal != actual.configuration.receiveLocal) {
            return failWiredImport("WIRED Link settings were not confirmed for " + id);
         }
      }
      return this.state == BuildingImportState.SETUP_WIRED;
   }

   private void labelUpdated(HMessage message) {
      PresetFurniLabel expected = this.pendingLabel;
      if (expected == null) return;
      try {
         HPacket packet = new HPacket(message.getPacket());
         int count = Utils.readCount(packet, 19);
         for (int i = 0; i < count; i++) {
            PresetFurniLabel actual = PresetFurniLabel.fromPacket(packet);
            if (expected.matches(actual)) this.labelSaveConfirmation.release();
         }
      } catch (RuntimeException error) {
         this.extension.getLogger().log("Cannot verify WIRED label: " + error.getMessage(), "red");
      }
   }

   private void applyFurnitureLabels() {
      if (this.workingPresetConfig.getFurnitureLabels().isEmpty()) return;
      if (!this.canUseWiredVariables()) {
         failWiredImport("Missing permissions to configure WIRED labels");
         return;
      }
      for (PresetFurniLabel original : this.workingPresetConfig.getFurnitureLabels()) {
         if(!checkpoint(BuildingImportState.MOVE_FURNITURE))return;
         if (this.state != BuildingImportState.MOVE_FURNITURE) return;
         Integer id = this.realFurniIdMap.get(original.furniId);
         if (id == null) continue;
         PresetFurniLabel label = original.withFurniId(id).translate(this.rootLocation.getX(),this.rootLocation.getY());
         this.labelSaveConfirmation.drainPermits();
         this.pendingLabel = label;
         try {
            if(!this.extension.sendToServer(label.textPacket())) { failWiredImport("Cannot save label/region " + id); return; }
            Utils.sleep(Math.max(300, this.extension.getSafeFeedbackTimeout()));
            if (this.state != BuildingImportState.MOVE_FURNITURE) return;
            if(!label.isRegion() && !this.extension.sendToServer(label.settingsPacket())) { failWiredImport("Cannot save label settings " + id); return; }
            if (!this.labelSaveConfirmation.tryAcquire(5L, TimeUnit.SECONDS)) {
               failWiredImport("No confirmation for WIRED label " + id);
               return;
            }
         } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failWiredImport("WIRED label import interrupted");
            return;
         } finally {
            this.pendingLabel = null;
         }
      }
   }

   private void sendWiredVariable(int objectId, String variableId, int value) {
      this.throttleWiredSetObjectVariableValue();
      this.extension.sendToServer(new HPacket("WiredSetObjectVariableValue", HMessage.Direction.TOSERVER, new Object[]{0, -objectId, variableId, value, 0}));
   }

   private void nudgeWallItem(int wallItemId, int x, int y, int offsetX, int offsetY, char direction) {
      String origLoc = String.format(":w=%d,%d l=%d,%d %c", x, y, offsetX, offsetY, direction);
      String nudgedLoc = String.format(":w=%d,%d l=%d,%d %c", x, y, offsetX + 1, offsetY, direction);
      this.extension.sendToServer(new HPacket("MoveWallItem", HMessage.Direction.TOSERVER, new Object[]{wallItemId, nudgedLoc}));
      Utils.sleep(60);
      this.extension.sendToServer(new HPacket("MoveWallItem", HMessage.Direction.TOSERVER, new Object[]{wallItemId, origLoc}));
      Utils.sleep(60);
   }

   private void sendFloorObjectVariableById(int objectId, String variableId, int value) {
      this.throttleWiredSetObjectVariableValue();
      this.extension.sendToServer(new HPacket("WiredSetObjectVariableValue", HMessage.Direction.TOSERVER, new Object[]{0, objectId, variableId, value, 1}));
   }

   private void sendFloorObjectVariable(int objectId, String variableName, int value) {
      this.throttleWiredSetObjectVariableValue();
      this.extension.sendToServer(new HPacket("WiredSetObjectVariableValue", HMessage.Direction.TOSERVER, new Object[]{0, objectId, variableName, value, 1}));
   }

   private boolean hasModernValues(int presetId) {
      return this.workingPresetConfig.getObjectVariables().stream().anyMatch(v->v.target==0 && v.objectId==presetId);
   }

   private void applyFloorFurniVariables() {
      if (this.workingPresetConfig != null && this.workingPresetConfig.getFurniture() != null && !this.workingPresetConfig.getFurniture().isEmpty()) {
         if (!this.canUseWiredVariables()) {
            this.extension.getLogger().log("Skipping floor furni variables - no wired permissions", "orange");
         } else {
            if (this.workingPresetConfig != null && this.workingPresetConfig.getPresetWireds() != null) {
               HashMap<String, String> nameToOldId = this.workingPresetConfig.getPresetWireds().getVariablesMap();
               boolean needAny = false;
               if (nameToOldId != null) {
                  for(PresetFurni pf : this.workingPresetConfig.getFurniture()) {
                     Map<String, Integer> vars = pf.getVariables();
                     if (vars != null) {
                        for(String name : vars.keySet()) {
                           String oldId = (String)nameToOldId.get(name);
                           if (oldId != null && !this.realVariableIdMap.containsKey(oldId)) {
                              needAny = true;
                              break;
                           }
                        }

                        if (needAny) {
                           break;
                        }
                     }
                  }
               }

               if (needAny) {
                  BuildingImportState prev = this.state;
                  this.state = GPresetImporter.BuildingImportState.SETUP_WIRED;
                  this.wiredVariableConfirmation.drainPermits();
                  this.extension.sendToServer(new HPacket("WiredGetAllVariablesDiffs", HMessage.Direction.TOSERVER, new Object[]{0}));

                  try {
                     this.wiredVariableConfirmation.tryAcquire(5L, TimeUnit.SECONDS);
                  } catch (InterruptedException var16) {
                  }

                  this.state = prev;
               }
            }

            List<PresetFurni> furniture = this.workingPresetConfig.getFurniture();
            int totalAssignments = 0;

            for(PresetFurni presetFurni : furniture) {
               if(hasModernValues(presetFurni.getFurniId())) continue;
               Map<String, Integer> variables = presetFurni.getVariables();
               if (variables != null && !variables.isEmpty()) {
                  for(Map.Entry<String, Integer> entry : variables.entrySet()) {
                     String variableName = (String)entry.getKey();
                     Integer value = (Integer)entry.getValue();
                     if (variableName != null && !variableName.isEmpty() && value != null && !variableName.startsWith("@")) {
                        ++totalAssignments;
                     }
                  }
               }
            }

            if (totalAssignments > 0) {
               this.extension.sendVisualChatInfo(String.format("Applying %d floor furni variable assignment(s)..", totalAssignments));
               this.extension.getLogger().log(String.format("Applying floor furni variables.. (%d left)", totalAssignments), "orange");
            }

            int assignments = 0;
            int sinceLastProgressLog = 0;

            for(PresetFurni presetFurni : furniture) {
               if(hasModernValues(presetFurni.getFurniId())) continue;
               if (this.state != GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
                  return;
               }

               Map<String, Integer> variables = presetFurni.getVariables();
               if (variables != null && !variables.isEmpty()) {
                  Integer realId = (Integer)this.realFurniIdMap.get(presetFurni.getFurniId());
                  if (realId != null) {
                     for(Map.Entry<String, Integer> entry : variables.entrySet()) {
                        if (this.state != GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
                           return;
                        }

                        String variableName = (String)entry.getKey();
                        Integer value = (Integer)entry.getValue();
                        if (variableName != null && !variableName.isEmpty() && value != null && !variableName.startsWith("@")) {
                           String resolvedRealId = null;
                           HashMap<String, String> nameToOldId = this.workingPresetConfig.getPresetWireds() == null ? null : this.workingPresetConfig.getPresetWireds().getVariablesMap();
                           if (nameToOldId != null) {
                              String oldId = (String)nameToOldId.get(variableName);
                              if (oldId != null) {
                                 resolvedRealId = (String)this.realVariableIdMap.get(oldId);
                              }
                           }

                           if (resolvedRealId != null) {
                              this.sendFloorObjectVariableById(realId, resolvedRealId, value);
                           } else {
                              this.sendFloorObjectVariable(realId, variableName, value);
                           }

                           ++assignments;
                           ++sinceLastProgressLog;
                           if (sinceLastProgressLog >= 10 || assignments == totalAssignments) {
                              int left = Math.max(0, totalAssignments - assignments);
                              this.extension.getLogger().log(String.format("Applying floor furni variables.. (%d left)", left), "orange");
                              sinceLastProgressLog = 0;
                           }

                           Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 60));
                        }
                     }
                  }
               }
            }

            if (assignments > 0) {
               this.extension.getLogger().log(String.format("Applied %d floor furni variable assignment(s)", assignments), "orange");
            }

         }
      }
   }

   private void applyWallFurniVariables() {
      if (this.workingPresetConfig != null && this.workingPresetConfig.getWallFurniture() != null && !this.workingPresetConfig.getWallFurniture().isEmpty()) {
         if (!this.canUseWiredVariables()) {
            this.extension.getLogger().log("Skipping wall furni variables - no wired permissions", "orange");
         } else {
            List<PresetWallFurni> wallFurniture = this.workingPresetConfig.getWallFurniture();
            if (this.workingPresetConfig.getPresetWireds() != null) {
               HashMap<String, String> nameToOldId = this.workingPresetConfig.getPresetWireds().getVariablesMap();
               boolean needAny = false;
               if (nameToOldId != null) {
                  for(PresetWallFurni pf : wallFurniture) {
                     Map<String, Integer> vars = pf.getVariables();
                     if (vars != null) {
                        for(String name : vars.keySet()) {
                           String oldId = (String)nameToOldId.get(name);
                           if (oldId != null && !this.realVariableIdMap.containsKey(oldId)) {
                              needAny = true;
                              break;
                           }
                        }

                        if (needAny) {
                           break;
                        }
                     }
                  }
               }

               if (needAny) {
                  BuildingImportState prev = this.state;
                  this.state = GPresetImporter.BuildingImportState.SETUP_WIRED;
                  this.wiredVariableConfirmation.drainPermits();
                  this.extension.sendToServer(new HPacket("WiredGetAllVariablesDiffs", HMessage.Direction.TOSERVER, new Object[]{0}));

                  try {
                     this.wiredVariableConfirmation.tryAcquire(5L, TimeUnit.SECONDS);
                  } catch (InterruptedException var17) {
                  }

                  this.state = prev;
               }
            }

            int totalAssignments = 0;

            for(PresetWallFurni presetWallFurni : wallFurniture) {
               if(hasModernValues(-presetWallFurni.getFurniId())) continue;
               Map<String, Integer> variables = presetWallFurni.getVariables();
               if (variables != null && !variables.isEmpty()) {
                  for(Map.Entry<String, Integer> entry : variables.entrySet()) {
                     String variableName = (String)entry.getKey();
                     Integer value = (Integer)entry.getValue();
                     if (variableName != null && !variableName.isEmpty() && value != null && !variableName.startsWith("@") && !variableName.startsWith("-")) {
                        ++totalAssignments;
                     }
                  }
               }
            }

            if (totalAssignments != 0) {
               this.extension.sendVisualChatInfo(String.format("Applying %d wall furni variable assignment(s)..", totalAssignments));
               this.extension.getLogger().log(String.format("Applying wall furni variables.. (%d left)", totalAssignments), "orange");
               int assignments = 0;
               int sinceLastProgressLog = 0;

               for(PresetWallFurni presetWallFurni : wallFurniture) {
               if(hasModernValues(-presetWallFurni.getFurniId())) continue;
                  if (this.state != GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
                     return;
                  }

                  Map<String, Integer> variables = presetWallFurni.getVariables();
                  if (variables != null && !variables.isEmpty()) {
                     Integer realId = (Integer)this.realFurniIdMap.get(presetWallFurni.getFurniId());
                     if (realId != null) {
                        for(Map.Entry<String, Integer> entry : variables.entrySet()) {
                           if (this.state != GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
                              return;
                           }

                           String variableName = (String)entry.getKey();
                           Integer value = (Integer)entry.getValue();
                           if (variableName != null && !variableName.isEmpty() && value != null && !variableName.startsWith("@") && !variableName.startsWith("-")) {
                              String resolvedRealId = null;
                              HashMap<String, String> nameToOldId = this.workingPresetConfig.getPresetWireds() == null ? null : this.workingPresetConfig.getPresetWireds().getVariablesMap();
                              if (nameToOldId != null) {
                                 String oldId = (String)nameToOldId.get(variableName);
                                 if (oldId != null) {
                                    resolvedRealId = (String)this.realVariableIdMap.get(oldId);
                                 }
                              }

                              int wireId = -realId;
                              if (resolvedRealId != null) {
                                 this.throttleWiredSetObjectVariableValue();
                                 this.extension.sendToServer(new HPacket("WiredSetObjectVariableValue", HMessage.Direction.TOSERVER, new Object[]{0, wireId, resolvedRealId, value, 1}));
                              } else {
                                 this.throttleWiredSetObjectVariableValue();
                                 this.extension.sendToServer(new HPacket("WiredSetObjectVariableValue", HMessage.Direction.TOSERVER, new Object[]{0, wireId, variableName, value, 1}));
                              }

                              ++assignments;
                              ++sinceLastProgressLog;
                              if (sinceLastProgressLog >= 10 || assignments == totalAssignments) {
                                 int left = Math.max(0, totalAssignments - assignments);
                                 this.extension.getLogger().log(String.format("Applying wall furni variables.. (%d left)", left), "orange");
                                 sinceLastProgressLog = 0;
                              }

                              Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 60));
                           }
                        }
                     }
                  }
               }

               if (assignments > 0) {
                  this.extension.getLogger().log(String.format("Applied %d wall furni variable assignment(s)", assignments), "orange");
               }

            }
         }
      }
   }

   private void placeWallItems() { placeWallItems(BuildingImportState.MOVE_FURNITURE); }

   private void placeWallItems(BuildingImportState phase) {
      if (this.wallFurniturePlaced || this.state != phase) return;
      List<PresetWallFurni> wallFurniture = this.workingPresetConfig.getWallFurniture();
      if (wallFurniture != null && !wallFurniture.isEmpty()) {
         try {
            RoomLayoutInfo srcLayout = this.workingPresetConfig.getRoomLayout();
            FloorState floorForLayout = this.extension.getFloorState();
            String dstRaw = floorForLayout.getRawFloorplan();
            RoomLayoutInfo dstLayout = dstRaw == null ? null : new RoomLayoutInfo(floorForLayout.getRoomModelName(), floorForLayout.getFloorplanWidth(), floorForLayout.getFloorplanHeight(), floorForLayout.getFloorScale(), floorForLayout.getFloorWallHeight(), dstRaw);
            if (srcLayout == null) {
               this.extension.getLogger().log("Preset has no source room layout info (legacy export). Wall items may drift if the original room used a different model.", "orange");
            } else if (dstLayout != null && !srcLayout.matches(dstLayout)) {
               String srcModel = srcLayout.getRoomModelName() != null ? srcLayout.getRoomModelName() : "(unknown)";
               String dstModel = dstLayout.getRoomModelName() != null ? dstLayout.getRoomModelName() : "(unknown)";
               this.extension.getLogger().log("WARNING: Room model mismatch! Wall items will likely be misaligned.", "red");
               this.extension.getLogger().log(String.format("  Preset was exported from model='%s' (%dx%d, scale=%d, wallH=%d, sig=%s)", srcModel, srcLayout.getFloorplanWidth(), srcLayout.getFloorplanHeight(), srcLayout.getScale(), srcLayout.getWallHeight(), srcLayout.getFloorplanSignature()), "orange");
               this.extension.getLogger().log(String.format("  Current room is        model='%s' (%dx%d, scale=%d, wallH=%d, sig=%s)", dstModel, dstLayout.getFloorplanWidth(), dstLayout.getFloorplanHeight(), dstLayout.getScale(), dstLayout.getWallHeight(), dstLayout.getFloorplanSignature()), "orange");
               this.extension.getLogger().log(String.format("  For pixel-perfect wall placement, recreate the room with model '%s'.", srcModel), "orange");
            }
         } catch (Exception var44) {
         }

         this.extension.sendVisualChatInfo("Placing wall items..");
         this.extension.getLogger().log(String.format("Placing %d wall item(s)...", wallFurniture.size()), "orange");
         FurniDataTools furniData = this.extension.getFurniDataTools();
         Inventory inventory = this.extension.getInventory();
         BCCatalog catalog = this.extension.getCatalog();
         ItemSource source = this.importPostConfig().getItemSource();
         FloorState floor = this.extension.getFloorState();
         boolean canWired = this.canUseWiredVariables();
         List<int[]> wallItemStateQueue = new ArrayList();
         List<int[]> placedWallTracking = new ArrayList();
         int wiredCorrectionCount = 0;
         int wallIndex = 0;

         for(PresetWallFurni wallFurni : wallFurniture) {
            if(!checkpoint(phase))return;
            this.extension.setImportProgress("Processing wall furniture", wallIndex, wallFurniture.size());
            if (this.state != phase) {
               break;
            }

            ++wallIndex;
            if (wallIndex % 10 == 0 || wallIndex == 1) {
               this.extension.getLogger().log(String.format("Placing wall items.. (%d/%d)", wallIndex, wallFurniture.size()), "orange");
            }

            String className = wallFurni.getClassName();
            String wallState = wallFurni.getState() != null ? wallFurni.getState() : "";
            Integer typeId = furniData.getWallTypeId(className);
            if (typeId == null) {
               this.extension.sendVisualChatInfo(String.format("WARNING: Unknown wall item '%s', skipping", className));
            } else {
               WallPosition relPos = wallFurni.getLocation();
               int absX = relPos.getX() + this.rootLocation.getX();
               int absY = relPos.getY() + this.rootLocation.getY();
               int absAltitude = relPos.getAltitude();
               int offY = relPos.getOffsetY();
               WallPosition absPos = new WallPosition(absX, absY, relPos.getOffsetX(), offY, relPos.getDirection(), absAltitude);
               String locationString = absPos.toString();
               boolean isPoster = "poster".equals(className);
               String invCacheKey = isPoster ? typeId + ":" + wallState : String.valueOf(typeId);
               LinkedList<HInventoryItem> inventoryItems = (LinkedList)this.wallInventoryCache.computeIfAbsent(invCacheKey, (k) -> {
                  List<HInventoryItem> allOfType = inventory.getWallItemsByType(typeId);
                  return isPoster && !wallState.isEmpty() ? (LinkedList)allOfType.stream().filter((item) -> {
                     try {
                        String itemState = item.getStuff() != null ? item.getStuff().getLegacyString() : "";
                        return wallState.equals(itemState);
                     } catch (Exception var3) {
                        return false;
                     }
                  }).collect(Collectors.toCollection(LinkedList::new)) : new LinkedList(allOfType);
               });
               BCCatalog.SingleFurniProduct bcProduct = null;
               if (catalog != null && catalog.getState() == BCCatalog.CatalogState.COLLECTED) {
                  bcProduct = catalog.getWallProduct(typeId, wallState);
               }

               int offerId = bcProduct != null ? bcProduct.getOfferId() : -1;
               int pageId = bcProduct != null ? bcProduct.getPageId() : -1;
               if (offerId == -1 && furniData.getWallItemDetails(className) != null && furniData.getWallItemDetails(className).isBC) {
                  offerId = furniData.getWallItemDetails(className).offerId;
                  pageId = -1;
               }

               boolean useBC;
               if (source == ItemSource.ONLY_BC) {
                  useBC = true;
               } else if (source == ItemSource.PREFER_BC) {
                  if (bcProduct != null) {
                     useBC = true;
                  } else if (!inventoryItems.isEmpty()) {
                     useBC = false;
                  } else {
                     useBC = offerId != -1;
                  }
               } else if (source == ItemSource.PREFER_INVENTORY) {
                  useBC = inventoryItems.isEmpty() && offerId != -1;
               } else {
                  useBC = false;
               }

               Set<Integer> wallIdsBefore = new HashSet();
               String safeLocationString = null;

               for(HWallItem wi : floor.getWallItems()) {
                  wallIdsBefore.add(wi.getId());
                  if (safeLocationString == null) {
                     safeLocationString = wi.getLocation();
                  }
               }

               if (safeLocationString == null) {
                  safeLocationString = ":w=0,0 l=0,0 l";
               }

               boolean placedAtSafeLocation = false;
               if (useBC) {
                  if (offerId == -1) {
                     if (!this.extension.allowIncompleteBuilds()) {
                        this.state = GPresetImporter.BuildingImportState.NONE;
                        this.extension.sendVisualChatInfo(String.format("ERROR: Couldn't find wall item '%s' in BC catalog.. aborting", className));
                     } else {
                        this.extension.sendVisualChatInfo(String.format("Couldn't find wall item '%s' in BC catalog.. continuing", className));
                     }
                     continue;
                  }

                  this.extension.sendToServer(new HPacket("BuildersClubPlaceWallItem", HMessage.Direction.TOSERVER, new Object[]{pageId, offerId, wallState, locationString, false}));
                  Utils.sleep(230);
                  boolean found = false;

                  for(HWallItem wi : floor.getWallItems()) {
                     if (!wallIdsBefore.contains(wi.getId())) {
                        found = true;
                        break;
                     }
                  }

                  if (!found && (canWired || this.extension.getPermissions().canMoveFurni())) {
                     this.extension.sendToServer(new HPacket("BuildersClubPlaceWallItem", HMessage.Direction.TOSERVER, new Object[]{pageId, offerId, wallState, safeLocationString, false}));
                     Utils.sleep(230);
                     placedAtSafeLocation = true;
                  }
               } else {
                  if (inventoryItems.isEmpty()) {
                     if (!this.extension.allowIncompleteBuilds()) {
                        this.extension.sendVisualChatInfo(String.format("ERROR: Couldn't find wall item '%s' (state=%s) in inventory.. aborting", className, wallState));
                        this.state = GPresetImporter.BuildingImportState.NONE;
                        return;
                     }

                     this.extension.sendVisualChatInfo(String.format("Couldn't find wall item '%s' (state=%s) in inventory.. continuing", className, wallState));
                     continue;
                  }

                  HInventoryItem item = (HInventoryItem)inventoryItems.pollFirst();
                  String placeCmd = item.getId() + " " + locationString;
                  this.extension.sendToServer(new HPacket("PlaceObject", HMessage.Direction.TOSERVER, new Object[]{placeCmd}));
                  Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 100));
                  boolean found = false;

                  for(HWallItem wi : floor.getWallItems()) {
                     if (!wallIdsBefore.contains(wi.getId())) {
                        found = true;
                        break;
                     }
                  }

                  if (!found && (canWired || this.extension.getPermissions().canMoveFurni())) {
                     String safePlaceCmd = item.getId() + " " + safeLocationString;
                     this.extension.sendToServer(new HPacket("PlaceObject", HMessage.Direction.TOSERVER, new Object[]{safePlaceCmd}));
                     Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 100));
                     placedAtSafeLocation = true;
                  }
               }

               int newWallItemId = -1;

               for(HWallItem wi : floor.getWallItems()) {
                  if (!wallIdsBefore.contains(wi.getId())) {
                     newWallItemId = wi.getId();
                     if (!isPoster && !wallState.isEmpty() && !wallState.equals(wi.getState())) {
                        try {
                           int targetStateInt = Integer.parseInt(wallState);
                           wallItemStateQueue.add(new int[]{wi.getId(), targetStateInt});
                        } catch (NumberFormatException var43) {
                        }
                     }
                     break;
                  }
               }

               if (newWallItemId != -1) {
                  this.realFurniIdMap.put(wallFurni.getFurniId(), newWallItemId);
                  this.realFurniIdMap.put(-wallFurni.getFurniId(), -newWallItemId);
                  placedWallTracking.add(new int[]{newWallItemId, absX, absY, absAltitude, relPos.getOffsetX(), offY, relPos.getDirection()});
                  int actualX = -1;
                  int actualY = -1;
                  int actualAlt = -1;
                  int actualOffsetY = Integer.MIN_VALUE;
                  boolean hasMismatch = placedAtSafeLocation;
                  if (!placedAtSafeLocation) {
                     HWallItem placedItem = floor.wallItemFromId(newWallItemId);
                     if (placedItem != null) {
                        try {
                           WallPosition actualPos = new WallPosition(placedItem.getLocation());
                           actualX = actualPos.getX();
                           actualY = actualPos.getY();
                           actualAlt = actualPos.getAltitude();
                           actualOffsetY = actualPos.getOffsetY();
                           hasMismatch = actualX != absX || actualY != absY || actualAlt != absAltitude || actualOffsetY != offY;
                        } catch (IllegalArgumentException var46) {
                        }
                     }
                  }

                  if (hasMismatch) {
                     if (canWired) {
                        ++wiredCorrectionCount;
                        int sleepTime = Math.max(this.extension.getSafeFeedbackTimeout(), 100);
                        Utils.sleep(300);
                        if (placedAtSafeLocation || actualAlt != absAltitude) {
                           this.sendWiredVariable(newWallItemId, "-123", absAltitude);
                           Utils.sleep(sleepTime);
                        }
                     } else if (this.extension.getPermissions().canMoveFurni()) {
                        ++wiredCorrectionCount;
                        this.extension.sendToServer(new HPacket("MoveWallItem", HMessage.Direction.TOSERVER, new Object[]{newWallItemId, locationString}));
                        Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 100));
                     }
                  }
               }
            }
         }

         if (wiredCorrectionCount > 0) {
            this.extension.getLogger().log(String.format("Corrected %d wall item position(s) via wired", wiredCorrectionCount), "orange");
         }

         if (!placedWallTracking.isEmpty() && this.state == phase) {
            Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 200));
            int totalFixCount = 0;
            int sleepTime = Math.max(this.extension.getSafeFeedbackTimeout(), 100);
            int MAX_VALIDATE_ATTEMPTS = 4;
            int[] settleAfterAttemptMs = new int[]{350, 600, 900, 1200};
            List<int[]> toCheck = new ArrayList(placedWallTracking);

            for(int attempt = 1; attempt <= 4 && this.state == phase && !toCheck.isEmpty(); ++attempt) {
               List<int[]> stillWrong = new ArrayList();
               int attemptFixCount = 0;

               for(int[] entry : toCheck) {
                  if (this.state != phase) {
                     break;
                  }

                  int wid = entry[0];
                  int expX = entry[1];
                  int expY = entry[2];
                  int expAlt = entry[3];
                  int expOffX = entry[4];
                  int expOffY = entry[5];
                  char expDir = (char)entry[6];
                  HWallItem wi = floor.wallItemFromId(wid);
                  if (wi == null) {
                     this.extension.getLogger().verbose("wallValidate", String.format("attempt=%d id=%d MISSING from floor state (expected x=%d y=%d alt=%d offX=%d offY=%d dir=%c)", attempt, wid, expX, expY, expAlt, expOffX, expOffY, expDir));
                  } else {
                     int curX = -1;
                     int curY = -1;
                     int curOffX = -1;
                     int curOffY = -1;
                     char curDir = '?';

                     try {
                        WallPosition cur = new WallPosition(wi.getLocation());
                        curX = cur.getX();
                        curY = cur.getY();
                        curOffX = cur.getOffsetX();
                        curOffY = cur.getOffsetY();
                        curDir = cur.getDirection();
                     } catch (IllegalArgumentException var45) {
                        this.extension.getLogger().verbose("wallValidate", String.format("attempt=%d id=%d unparseable location '%s'", attempt, wid, wi.getLocation()));
                        continue;
                     }

                     Integer curAlt = null;
                     Integer curOffXWired = null;
                     if (canWired) {
                        boolean ok = this.fetchWallItemVariables(wid, 600L);
                        Map<String, Integer> vars = (Map)this.inspectedFurniVariables.get(-wid);
                        if (ok && vars != null) {
                           curAlt = (Integer)vars.get("-123");
                           curOffXWired = (Integer)vars.get("-190");
                           if (curOffXWired != null) {
                              curOffX = curOffXWired;
                           }
                        }
                     }

                     boolean xWrong = curX != expX;
                     boolean yWrong = curY != expY;
                     boolean offXWrong = curOffX != expOffX;
                     boolean offYWrong = curOffY != expOffY;
                     boolean altWrong = curAlt != null && curAlt != expAlt;
                     if (!xWrong && !yWrong && !offXWrong && !offYWrong && !altWrong) {
                        this.extension.getLogger().verbose("wallValidate", String.format("attempt=%d id=%d OK (x=%d y=%d alt=%s offX=%d offY=%d dir=%c)", attempt, wid, curX, curY, curAlt == null ? "?" : curAlt.toString(), curOffX, curOffY, curDir));
                     } else {
                        this.extension.getLogger().verbose("wallValidate", String.format("attempt=%d id=%d MISMATCH expected(x=%d y=%d alt=%d offX=%d offY=%d dir=%c) actual(x=%d y=%d alt=%s offX=%d offY=%d dir=%c) canWired=%s canMove=%s", attempt, wid, expX, expY, expAlt, expOffX, expOffY, expDir, curX, curY, curAlt == null ? "?" : curAlt.toString(), curOffX, curOffY, curDir, canWired, this.extension.getPermissions().canMoveFurni()));
                        if (canWired) {
                           ++attemptFixCount;
                           if (altWrong) {
                              this.sendWiredVariable(wid, "-123", expAlt);
                              Utils.sleep(sleepTime);
                           }

                           stillWrong.add(entry);
                        } else if (this.extension.getPermissions().canMoveFurni()) {
                           ++attemptFixCount;
                           String loc = String.format(":w=%d,%d l=%d,%d %c", expX, expY, expOffX, expOffY, expDir);
                           this.extension.sendToServer(new HPacket("MoveWallItem", HMessage.Direction.TOSERVER, new Object[]{wid, loc}));
                           Utils.sleep(sleepTime);
                           stillWrong.add(entry);
                        }
                     }
                  }
               }

               totalFixCount += attemptFixCount;
               if (attemptFixCount == 0) {
                  break;
               }

               if (attempt < 4 && !stillWrong.isEmpty()) {
                  this.extension.getLogger().verbose("wallValidate", String.format("attempt=%d sent %d correction(s); waiting %dms before re-validating %d item(s)", attempt, attemptFixCount, settleAfterAttemptMs[attempt - 1], stillWrong.size()));
                  Utils.sleep(settleAfterAttemptMs[attempt - 1]);
               }

               toCheck = stillWrong;
            }

            if (totalFixCount > 0) {
               this.extension.getLogger().log(String.format("Validation pass corrected %d wall item(s)%s", totalFixCount, toCheck.isEmpty() ? "" : String.format(" (%d still mismatched after retries)", toCheck.size())), "orange");
            } else {
               this.extension.getLogger().verbose("wallValidate", "all placed wall items match the preset");
            }
         }

         if (!wallItemStateQueue.isEmpty()) {
            this.extension.sendVisualChatInfo("Setting wall item states..");
            this.extension.getLogger().log(String.format("Setting %d wall item state(s)...", wallItemStateQueue.size()), "orange");

            for(int i = 0; i < wallItemStateQueue.size() && this.state == phase; ++i) {
               int[] entry = (int[])wallItemStateQueue.get(i);
               int wallItemId = entry[0];
               int targetState = entry[1];
               HWallItem wallItem = floor.wallItemFromId(wallItemId);
               if (wallItem != null) {
                  if (canWired) {
                     this.sendWiredVariable(wallItemId, "-110", targetState);
                     Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 60));
                  } else {
                     String currentState = wallItem.getState();

                     for(int attempts = 0; this.state != GPresetImporter.BuildingImportState.NONE && !String.valueOf(targetState).equals(currentState) && attempts < 20; ++attempts) {
                        this.extension.sendToServer(new HPacket("UseWallItem", HMessage.Direction.TOSERVER, new Object[]{wallItemId, 0}));
                        Utils.sleep(Math.max(this.extension.getSafeFeedbackTimeout(), 100));
                        wallItem = floor.wallItemFromId(wallItemId);
                        currentState = wallItem != null ? wallItem.getState() : currentState;
                     }
                  }

                  if ((i + 1) % 10 == 0 || i == wallItemStateQueue.size() - 1) {
                     this.extension.getLogger().log(String.format("Setting wall item states.. (%d/%d)", i + 1, wallItemStateQueue.size()), "orange");
                  }
               }
            }
         }

         for(PresetWallFurni item:wallFurniture) {
            if(!this.realFurniIdMap.containsKey(item.getFurniId())) {
               failWiredImport("Wall furniture was not placed: " + item.getClassName()); return;
            }
         }
         this.wallFurniturePlaced = this.state == phase;
         this.extension.getLogger().log("Wall items placement complete", "green");
      }
   }

   private void placePresetStackTiles() {
      FurniDataTools furniData = this.extension.getFurniDataTools();
      List<PresetFurni> presetStackTiles = new ArrayList();
      synchronized(this.lock) {
         for(PresetFurni f : this.workingPresetConfig.getFurniture()) {
            if (this.isStackTileClass(f.getClassName()) && furniData.isStackable(f.getClassName()) && this.realFurniIdMap.containsKey(f.getFurniId())) {
               presetStackTiles.add(f);
            }
         }
      }

      if (!presetStackTiles.isEmpty()) {
         this.extension.sendVisualChatInfo(String.format("Placing %d stack tile(s)...", presetStackTiles.size()));

         for(PresetFurni stackTileFurni : presetStackTiles) {
         if(!checkpoint(BuildingImportState.MOVE_FURNITURE))return;
            if (this.state != GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
               break;
            }

            int targetX = stackTileFurni.getLocation().getX() + this.rootLocation.getX();
            int targetY = stackTileFurni.getLocation().getY() + this.rootLocation.getY();
            int targetRot = stackTileFurni.getRotation();
            int realId = (Integer)this.realFurniIdMap.get(stackTileFurni.getFurniId());
            if (stackTileFurni.getState() != null) {
               this.attemptSetState(realId, stackTileFurni.getState());
            }

            this.moveFurni(realId, targetX, targetY, targetRot, false, (double)-1.0F);
            double z = stackTileFurni.getLocation().getZ() + this.importZDelta;
            int heightCenti = (int)Math.round(z * (double)100.0F);
            this.extension.getLogger().verbose("placePresetStackTiles", String.format("stacktile %d at (%d,%d) height=%d (raw=%.4f)", realId, targetX, targetY, heightCenti, z));
            this.internalPacketBypass.incrementAndGet();
            this.extension.sendToServer(new HPacket("SetCustomStackingHeight", HMessage.Direction.TOSERVER, new Object[]{realId, heightCenti}));
            Utils.sleep(60);
         }

      }
   }

   private void setupWired() {
      if(!checkpoint(BuildingImportState.SETUP_WIRED))return;
      this.placeWallItems(BuildingImportState.SETUP_WIRED);
      if (this.state != BuildingImportState.SETUP_WIRED) return;
      for(PresetFurni item:this.workingPresetConfig.getFurniture()) if(!this.realFurniIdMap.containsKey(item.getFurniId())) { failWiredImport("Furniture was not placed: " + item.getFurniName());return; }
      if (!this.applyRoomLinks() || !this.applyChests() || !this.applyContracts() || !this.applyWiredLinks()) return;
      List<PresetWiredBase> allWireds = new ArrayList();
      Map<Integer, List<PresetWiredFurniBinding>> wiredBindings = new HashMap();
      Map<Integer, PresetFurni> furni = new HashMap();
      synchronized(this.lock) {
         PresetWireds presetWireds = this.workingPresetConfig.getPresetWireds();
         Math.max(presetWireds.getConditions().size(), Math.max(presetWireds.getEffects().size(), Math.max(presetWireds.getTriggers().size(), Math.max(presetWireds.getAddons().size(), Math.max(presetWireds.getSelectors().size(), presetWireds.getVariables().size())))));
         Predicate<PresetWiredBase> filter = (wiredBasex) -> this.realFurniIdMap.containsKey(wiredBasex.getWiredId());
         List<PresetWiredVariable> variables = (List)presetWireds.getVariables().stream().filter(filter).collect(Collectors.toList());
         try {
            allWireds.addAll(WiredImportPlanner.orderVariables(variables));
         } catch (IllegalArgumentException error) {
            failWiredImport(error.getMessage());
            return;
         }
         allWireds.addAll((Collection)presetWireds.getConditions().stream().filter(filter).collect(Collectors.toList()));
         allWireds.addAll((Collection)presetWireds.getEffects().stream().filter(filter).collect(Collectors.toList()));
         allWireds.addAll((Collection)presetWireds.getAddons().stream().filter(filter).collect(Collectors.toList()));
         allWireds.addAll((Collection)presetWireds.getSelectors().stream().filter(filter).collect(Collectors.toList()));
         allWireds.addAll((Collection)presetWireds.getTriggers().stream().filter(filter).collect(Collectors.toList()));
         this.workingPresetConfig.getBindings().forEach((b) -> {
            if (!wiredBindings.containsKey(b.getWiredId())) {
               wiredBindings.put(b.getWiredId(), new ArrayList());
            }

            ((List)wiredBindings.get(b.getWiredId())).add(b);
         });
         this.workingPresetConfig.getFurniture().forEach((p) -> furni.put(p.getFurniId(), p));
         this.variablesProcessed = 0;
         this.variablesToProcess = variables.size();
      }

      int i = 0;

      while(this.state == GPresetImporter.BuildingImportState.SETUP_WIRED && i < allWireds.size()) {
         if(!checkpoint(BuildingImportState.SETUP_WIRED))return;
         this.extension.setImportProgress("Configuring WIRED", i, allWireds.size());
         PresetWiredBase wiredBase = (PresetWiredBase)allWireds.get(i);
         if ((i + 1) % 50 == 0) {
            this.extension.getLogger().log(String.format("Setting up wired.. (%d/%d)", i + 1, allWireds.size()), "orange");
         }

         ++i;
         FloorState floor = this.extension.getFloorState();
         FurniDataTools furniData = this.extension.getFurniDataTools();
         List<PresetWiredFurniBinding> bindings = (List)wiredBindings.get(wiredBase.getWiredId());
         List<FurniMoveInfo> bindMoves = new ArrayList();
         List<FurniMoveInfo> undoBindMoves = new ArrayList();
         if (bindings != null) {
            for(PresetWiredFurniBinding binding : bindings) {
               int furniId;
               synchronized(this.lock) {
                  if (this.state != GPresetImporter.BuildingImportState.SETUP_WIRED) {
                     return;
                  }

                  if (!this.realFurniIdMap.containsKey(binding.getFurniId())) {
                     failWiredImport("Missing snapshot binding furniture " + binding.getFurniId());return;
                  }

                  furniId = (Integer)this.realFurniIdMap.get(binding.getFurniId());
               }

               if (binding.getState() != null) {
                  String targetState = binding.getState();
                  this.attemptSetState(furniId, targetState);
               }

               synchronized(this.lock) {
                  if (this.state != GPresetImporter.BuildingImportState.SETUP_WIRED) {
                     return;
                  }

                  boolean needMovement = binding.getLocation() != null || binding.getRotation() != null || binding.getAltitude() != null;
                  if (needMovement) {
                     HFloorItem floorItem = floor.furniFromId(furniId);
                     if (floorItem != null) {
                        boolean needStacktile = furniData.isStackable(furniData.getFloorItemName(this.extension.getFloorState().furniFromId(furniId).getTypeId())) || binding.getAltitude() != null;
                        FurniMoveInfo undoMovement = new FurniMoveInfo(furniId, floorItem.getTile().getX(), floorItem.getTile().getY(), floorItem.getFacing().ordinal(), (Integer)null, needStacktile);
                        undoBindMoves.add(undoMovement);
                        int targetX = floorItem.getTile().getX() + this.rootLocation.getX();
                        int targetY = floorItem.getTile().getY() + this.rootLocation.getY();
                        int targetRotation = floorItem.getFacing().ordinal();
                        Integer targetAltitude = null;
                        if (binding.getLocation() != null) {
                           targetX = binding.getLocation().getX() + this.rootLocation.getX();
                           targetY = binding.getLocation().getY() + this.rootLocation.getY();
                        }

                        if (binding.getRotation() != null) {
                           targetRotation = binding.getRotation();
                        }

                        if (binding.getAltitude() != null) {
                           targetAltitude = binding.getAltitude();
                        }

                        FurniMoveInfo movement = new FurniMoveInfo(furniId, targetX, targetY, targetRotation, targetAltitude, needStacktile);
                        bindMoves.add(movement);
                     }
                  }
               }
            }
         }

         for(FurniMoveInfo furniMoveInfo : bindMoves) {
            double altitude = furniMoveInfo.getAltitude() == null ? (double)-1.0F : (double)furniMoveInfo.getAltitude() / (double)100.0F;
            this.moveFurni(furniMoveInfo.getFurniId(), furniMoveInfo.getX(), furniMoveInfo.getY(), furniMoveInfo.getRotation(), furniMoveInfo.useStackTile(), altitude);
         }

         boolean saved = this.saveWired(wiredBase, 0);

         for(FurniMoveInfo furniMoveInfo : undoBindMoves) {
            double altitude = furniMoveInfo.getAltitude() == null ? (double)-1.0F : (double)furniMoveInfo.getAltitude() / (double)100.0F;
            this.moveFurni(furniMoveInfo.getFurniId(), furniMoveInfo.getX(), furniMoveInfo.getY(), furniMoveInfo.getRotation(), furniMoveInfo.useStackTile(), altitude);
         }
         if (!saved) return;
         this.confirmedWiredCount++;
      }

      synchronized(this.lock) {
         if (this.state == GPresetImporter.BuildingImportState.SETUP_WIRED) {
            this.state = GPresetImporter.BuildingImportState.MOVE_FURNITURE;
         }
      }

      if (this.state == GPresetImporter.BuildingImportState.MOVE_FURNITURE) {
         this.extension.sendVisualChatInfo("Setting furni in their correct position..");
         this.moveFurniture();
      }

   }

   private void dropAllOtherFurni() {
      FurniDataTools furniData = this.extension.getFurniDataTools();
      List<FurniDropInfo> furniDropInfos = new ArrayList();
      this.moveFurni(this.mainStackTile, this.stackTileLocation.getX(), this.stackTileLocation.getY(), 0, false, (double)-1.0F);
      synchronized(this.lock) {
         this.workingPresetConfig.getFurniture().forEach((f) -> {
            if (furniData.isStackable(f.getClassName())) {
               FurniDropInfo dropInfo = new FurniDropInfo(WiredKind.fromClassName(f.getClassName()) == WiredKind.TRIGGER ? this.stackTileLocation.getX() + 1 : this.stackTileLocation.getX(), this.stackTileLocation.getY(), furniData.getFloorTypeId(f.getClassName()), this.importPostConfig().getItemSource(), 0, f.getFurniId());
               furniDropInfos.add(dropInfo);
               String key = String.format("%d|%d|%d", dropInfo.getX(), dropInfo.getY(), dropInfo.getTypeId());
               if (!this.expectFurniDrops.containsKey(key)) {
                  this.expectFurniDrops.put(key, new LinkedList());
               }

               ((LinkedList)this.expectFurniDrops.get(key)).add(f.getFurniId());
            }

         });
      }

      for(int i = 0; i < furniDropInfos.size() && this.state == GPresetImporter.BuildingImportState.ADD_FURNITURE; ++i) {
         if(!checkpoint(BuildingImportState.ADD_FURNITURE))return;
         this.extension.setImportProgress("Sending furniture placements", i, furniDropInfos.size());
         FurniDropInfo dropInfo = (FurniDropInfo)furniDropInfos.get(i);
         if ((i + 1) % 50 == 0) {
            this.extension.getLogger().log(String.format("Dropping furni.. (%d/%d)", i + 1, furniDropInfos.size()), "orange");
         }

         this.dropFurni(dropInfo);
      }

      if (this.state == GPresetImporter.BuildingImportState.ADD_FURNITURE) {
         int j = 0;

         boolean done;
         do {
            Utils.sleep(500);
            synchronized(this.lock) {
               done = this.state != GPresetImporter.BuildingImportState.ADD_FURNITURE || this.expectFurniDrops.isEmpty() || j++ > 30;
            }
         } while(!done);

         synchronized(this.lock) {
            if (this.state == GPresetImporter.BuildingImportState.ADD_FURNITURE) {
               if (!this.expectFurniDrops.isEmpty() && !this.extension.allowIncompleteBuilds()) {
                  this.state = GPresetImporter.BuildingImportState.NONE;
                  this.extension.sendVisualChatInfo("ERROR: not all furniture were placed");
                  this.extension.getLogger().log("ERROR: not all furniture were placed", "red");
               } else {
                  this.state = GPresetImporter.BuildingImportState.SETUP_ADS;
               }
            }
         }

         if (this.state == GPresetImporter.BuildingImportState.SETUP_ADS) {
            this.setupAds();
         }
      }

   }

   private void setupAds() {
      if(!checkpoint(BuildingImportState.SETUP_ADS))return;
      List<PresetAdsBackground> adsBackgrounds = this.workingPresetConfig.getAdsBackgrounds();
      if (!adsBackgrounds.isEmpty()) {
         this.extension.sendVisualChatInfo("Setting up ads backgrounds..");

         for(int index = 0; index < adsBackgrounds.size() && this.state == GPresetImporter.BuildingImportState.SETUP_ADS; ++index) {
            if(!checkpoint(BuildingImportState.SETUP_ADS))return;
            PresetAdsBackground adsBackground = (PresetAdsBackground)adsBackgrounds.get(index);
            synchronized(this.lock) {
               if (this.state == GPresetImporter.BuildingImportState.SETUP_ADS && this.realFurniIdMap.containsKey(adsBackground.getFurniId())) {
                  int realFurni = (Integer)this.realFurniIdMap.get(adsBackground.getFurniId());
                  this.extension.sendToServer(new HPacket("SetObjectData", HMessage.Direction.TOSERVER, new Object[]{realFurni, 8, "imageUrl", adsBackground.getImageUrl(), "offsetX", adsBackground.getOffsetX(), "offsetY", adsBackground.getOffsetY(), "offsetZ", adsBackground.getOffsetZ()}));
               }
            }

            Utils.sleep(100);
         }
      }

      synchronized(this.lock) {
         if (this.state == GPresetImporter.BuildingImportState.SETUP_ADS) {
            this.state = GPresetImporter.BuildingImportState.SETUP_WIRED;
         }
      }

      if (this.state == GPresetImporter.BuildingImportState.SETUP_WIRED) {
         this.extension.sendVisualChatInfo("Setting up wired..");
         this.setupWired();
      }

   }

   private boolean isValidStagingArea(int x, int y) {
      FloorState floor = this.extension.getFloorState();
      char height = floor.floorHeight(x, y);
      if (height == 'x') return false;
      int dimensions = Math.max(1, this.mainStackDimensions);
      for (int dx = 0; dx < dimensions; dx++) {
         for (int dy = 0; dy < dimensions; dy++) {
            if (floor.floorHeight(x + dx, y + dy) != height) return false;
         }
      }
      boolean hasTriggers = this.workingPresetConfig.getFurniture().stream()
         .anyMatch(f -> WiredKind.fromClassName(f.getClassName()) == WiredKind.TRIGGER);
      return !hasTriggers || floor.floorHeight(x + 1, y) == height;
   }

   private boolean locationContainsWired(int x, int y) {
      FloorState floor = this.extension.getFloorState();
      FurniDataTools furniData = this.extension.getFurniDataTools();
      return floor.getFurniOnTile(x, y).stream().anyMatch((i) -> {
         String className = furniData.getFloorItemName(i.getTypeId());
         return WiredKind.fromClassName(className) == WiredKind.CONDITION || WiredKind.fromClassName(className) == WiredKind.TRIGGER || WiredKind.fromClassName(className) == WiredKind.EFFECT;
      });
   }

   private HPoint findStackTileLocation() {
      FloorState floor = this.extension.getFloorState();
      int centerX = this.rootLocation != null ? this.rootLocation.getX() : 0;
      int centerY = this.rootLocation != null ? this.rootLocation.getY() : 0;
      int maxDist = 64;

      for(int dist = 0; dist < maxDist; ++dist) {
         for(int dx = -dist; dx <= dist; ++dx) {
            for(int dy = -dist; dy <= dist; ++dy) {
               if (Math.abs(dx) == dist || Math.abs(dy) == dist) {
                  int x = centerX + dx;
                  int y = centerY + dy;
                  if (x >= 0 && y >= 0 && x < 64 - this.mainStackDimensions && y < 64 - this.mainStackDimensions) {
                     char reference = floor.floorHeight(x, y);
                     if (reference != 'x') {
                        boolean valid = true;

                        for(int xOffset = 0; xOffset < this.mainStackDimensions && valid; ++xOffset) {
                           for(int yOffset = 0; yOffset < this.mainStackDimensions && valid; ++yOffset) {
                              if (floor.floorHeight(x + xOffset, y + yOffset) != reference) {
                                 valid = false;
                              }
                           }
                        }

                        if (valid && !this.locationContainsWired(x, y) && !this.locationContainsWired(x + 1, y)) {
                           return new HPoint(x, y);
                        }
                     }
                  }
               }
            }
         }
      }

      return new HPoint(centerX, centerY);
   }

   private void addUnstackables() {
      FurniDataTools furniData = this.extension.getFurniDataTools();
      List<FurniDropInfo> furniDropInfos = new ArrayList();
      synchronized(this.lock) {
         this.workingPresetConfig.getFurniture().forEach((f) -> {
            if (!furniData.isStackable(f.getClassName())) {
               FurniDropInfo dropInfo = new FurniDropInfo(f.getLocation().getX() + this.rootLocation.getX(), f.getLocation().getY() + this.rootLocation.getY(), furniData.getFloorTypeId(f.getClassName()), this.importPostConfig().getItemSource(), f.getRotation());
               furniDropInfos.add(dropInfo);
               String key = String.format("%d|%d|%d", dropInfo.getX(), dropInfo.getY(), dropInfo.getTypeId());
               if (!this.expectFurniDrops.containsKey(key)) {
                  this.expectFurniDrops.put(key, new LinkedList());
               }

               ((LinkedList)this.expectFurniDrops.get(key)).add(f.getFurniId());
            }

         });
      }

      for(int i = 0; i < furniDropInfos.size() && this.state == GPresetImporter.BuildingImportState.ADD_UNSTACKABLES; ++i) {
         if(!checkpoint(BuildingImportState.ADD_UNSTACKABLES))return;
         this.extension.setImportProgress("Sending unstackable placements", i, furniDropInfos.size());
         FurniDropInfo dropInfo = (FurniDropInfo)furniDropInfos.get(i);
         this.dropFurni(dropInfo);
      }

      synchronized(this.lock) {
         if (this.state == GPresetImporter.BuildingImportState.ADD_UNSTACKABLES) {
            this.state = GPresetImporter.BuildingImportState.ADD_FURNITURE;
         }
      }

      if (this.state == GPresetImporter.BuildingImportState.ADD_FURNITURE) {
         this.dropAllOtherFurni();
      }

   }

   public void reset() {
      this.pauseGate.cancel();
      this.extension.setImportProgress("Import stopped or reset",0,1);
      PresetPreviewSession preview=this.pendingPreview;
      if(preview!=null)preview.cancel();
      this.pendingPreview=null;
      this.extension.updatePreviewAvailability();
      this.useRoomFurniRectCorner1 = null;
      this.useRoomFurniRectCorner2 = null;
      this.stateChangerLocation = null;
      this.state = GPresetImporter.BuildingImportState.NONE;
      this.pendingLabel = null;
      this.pendingLinkId = null;
      this.pendingContractId = null;
      this.awaitingRoomSettings=false;
      this.roomSettingsResponse.release();
      this.contractResponse.release();
      this.linkResponse.release();
      if (this.wiredSaveConfirmation != null) this.wiredSaveConfirmation.release();
      if (this.wiredVariableConfirmation != null) this.wiredVariableConfirmation.release();
      this.labelSaveConfirmation.release();
   }

   public void setPresetConfig(PresetConfig presetConfig) {
      this.presetConfig = presetConfig;
   }

   public PresetConfig getPresetConfig() {
      return this.presetConfig;
   }

   public void setPostConfig(PostConfig postConfig) {
      this.postConfig = postConfig;
   }

   public BuildingImportState getState() {
      return this.state;
   }

   public static enum BuildingImportState {
      NONE,
      AWAITING_PREVIEW,
      AWAITING_UNOCCUPIED_SPACE,
      AWAITING_USE_ROOM_FURNI_RECT1,
      AWAITING_USE_ROOM_FURNI_RECT2,
      AWAITING_ROOT_LOCATION,
      AWAITING_UNOCCUPIED_SPACE_STATE_CHANGER,
      ADD_UNSTACKABLES,
      ADD_FURNITURE,
      SETUP_ADS,
      SETUP_WIRED,
      MOVE_FURNITURE;

      // $FF: synthetic method
      private static BuildingImportState[] $values() {
         return new BuildingImportState[]{NONE, AWAITING_PREVIEW, AWAITING_UNOCCUPIED_SPACE, AWAITING_USE_ROOM_FURNI_RECT1, AWAITING_USE_ROOM_FURNI_RECT2, AWAITING_ROOT_LOCATION, AWAITING_UNOCCUPIED_SPACE_STATE_CHANGER, ADD_UNSTACKABLES, ADD_FURNITURE, SETUP_ADS, SETUP_WIRED, MOVE_FURNITURE};
      }
   }
}
