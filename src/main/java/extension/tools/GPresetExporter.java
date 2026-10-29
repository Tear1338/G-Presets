package extension.tools;

import extension.GPresets;
import extension.parsers.HWiredVariable;
import extension.parsers.HVariableDiff;
import extension.parsers.HWiredLinkConfiguration;
import extension.parsers.HWiredVariablesForObject;
import extension.parsers.VariableInternalType;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.PresetConfigUtils;
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
import extension.tools.presetconfig.wired.WiredFurnitureIds;
import extension.tools.presetconfig.wired.PresetWiredCondition;
import extension.tools.presetconfig.wired.PresetWiredEffect;
import extension.tools.presetconfig.wired.PresetWiredSelector;
import extension.tools.presetconfig.wired.PresetWiredTrigger;
import extension.tools.presetconfig.wired.PresetWiredVariable;
import extension.tools.presetconfig.wired.PresetWireds;
import extension.tools.presetconfig.wired.incoming.RetrievedWiredAddon;
import extension.tools.presetconfig.wired.incoming.RetrievedWiredCondition;
import extension.tools.presetconfig.wired.incoming.RetrievedWiredEffect;
import extension.tools.presetconfig.wired.incoming.RetrievedWiredSelector;
import extension.tools.presetconfig.wired.incoming.RetrievedWiredTrigger;
import extension.tools.presetconfig.wired.incoming.RetrievedWiredVariable;
import furnidata.FurniDataTools;
import game.FloorState;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.HPoint;
import gearth.extensions.parsers.HWallItem;
import gearth.extensions.parsers.stuffdata.MapStuffData;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import utils.StateExtractor;
import utils.Utils;
import utils.WallPosition;

public class GPresetExporter {
   private final Object lock = new Object();
   private HashMap<String, String> variablesMap = new HashMap();
   private HashMap<String, String> variableIdToName = new HashMap();
   private final Map<String, HWiredVariable> variableInfoById = Collections.synchronizedMap(new HashMap());
   private final Map<Integer, Map<String, Integer>> furniVariablesByFurniId = Collections.synchronizedMap(new HashMap());
   private final Set<Integer> pendingFurniVariableRequests = Collections.synchronizedSet(new HashSet());
   private final Set<String> pendingVariableHolderRequests = Collections.synchronizedSet(new HashSet());
   private final Set<Integer> exportAreaFurniIds = Collections.synchronizedSet(new HashSet());
   private volatile long lastVariableActivityTime = 0L;
   private volatile int totalVariableRequests = 0;
   private volatile int sentVariableRequests = 0;
   private volatile boolean variableRequestPhaseStarted = false;
   private final AtomicReference<String> awaitingVariableId = new AtomicReference((Object)null);
   private final Semaphore variableResponseSemaphore = new Semaphore(0);
   private final AtomicReference<Integer> awaitingFurniInspectionId = new AtomicReference((Object)null);
   private final Semaphore furniInspectionResponseSemaphore = new Semaphore(0);
   private boolean hasVariableMap = false;
   private static final long VARIABLE_GRACE_WINDOW_MS = 3000L;
   private static final long VARIABLE_REQUEST_INTERVAL_MS = 80L;
   private static final long VARIABLE_RESPONSE_TIMEOUT_MS = 1500L;
   private static final int VARIABLE_MAX_RETRIES = 1;
   private static final int VARIABLE_TYPE_FURNI = 0;
   private volatile HPoint rectCorner1 = null;
   private volatile HPoint rectCorner2 = null;
   private volatile String exportName = null;
   private volatile boolean wallOnlyExport = false;
   private volatile String pendingExportName = null;
   private volatile int pendingExportX;
   private volatile int pendingExportY;
   private volatile int pendingExportDimX;
   private volatile int pendingExportDimY;
   private volatile boolean blockNextChat = false;
   private volatile PresetExportState state;
   private final Map<String, PresetWiredCondition> wiredConditionConfigs;
   private final Map<String, PresetWiredEffect> wiredEffectConfigs;
   private final Map<String, PresetWiredTrigger> wiredTriggerConfigs;
   private final Map<String, PresetWiredAddon> wiredAddonConfigs;
   private final Map<String, PresetWiredSelector> wiredSelectorConfigs;
   private final Map<String, PresetWiredVariable> wiredVariableConfigs;
   private static final Set<String> requireBindings = new HashSet(Arrays.asList("wf_act_match_to_sshot", "wf_cnd_match_snapshot", "wf_cnd_not_match_snap", "wf_trg_stuff_state"));
   private final Map<String, List<PresetWiredFurniBinding>> wiredFurniBindings;
   private final GPresets extension;
   private volatile boolean hasLabelSnapshot;
   private volatile Map<String,Integer> globalVariableValues;
   private final Semaphore globalVariablesResponse=new Semaphore(0);
   private volatile PresetWiredRoomSettings roomSettings;
   private final Semaphore roomSettingsResponse = new Semaphore(0);
   private final Map<Integer, PresetWiredContract> contracts = Collections.synchronizedMap(new HashMap<>());
   private final Map<Integer, PresetWiredLink> wiredLinks = Collections.synchronizedMap(new HashMap<>());
   private final Map<Integer, PresetFurniLabel> furnitureLabels = Collections.synchronizedMap(new HashMap<>());

   public GPresetExporter(GPresets extension) {
      this.state = GPresetExporter.PresetExportState.NONE;
      this.wiredConditionConfigs = Collections.synchronizedMap(new HashMap());
      this.wiredEffectConfigs = Collections.synchronizedMap(new HashMap());
      this.wiredTriggerConfigs = Collections.synchronizedMap(new HashMap());
      this.wiredAddonConfigs = Collections.synchronizedMap(new HashMap());
      this.wiredSelectorConfigs = Collections.synchronizedMap(new HashMap());
      this.wiredVariableConfigs = Collections.synchronizedMap(new HashMap());
      this.wiredFurniBindings = Collections.synchronizedMap(new HashMap());
      this.extension = extension;
      extension.intercept(HMessage.Direction.TOCLIENT,"WiredRoomSettings",message -> this.readWiredSafely(message,m -> {
         this.roomSettings=PresetWiredRoomSettings.fromPacket(new HPacket(m.getPacket()));
         this.roomSettingsResponse.release();
      }));
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredContractContents", message -> this.readWiredSafely(message, this::onContract));
      extension.intercept(HMessage.Direction.TOSERVER,"WiredUpdateContract",message -> {
         HPacket packet=new HPacket(message.getPacket()); if(packet.getBytesLength()>=10)this.contracts.remove(packet.readInteger());
      });
      extension.intercept(HMessage.Direction.TOSERVER,"WiredLinkSaveConfiguration",message -> {
         HPacket packet=new HPacket(message.getPacket()); if(packet.getBytesLength()>=10)this.wiredLinks.remove(packet.readInteger());
      });
      extension.intercept(HMessage.Direction.TOCLIENT,"WiredLinkConfigurationInvalidated",message -> this.wiredLinks.clear());
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredLinkConfiguration", message -> this.readWiredSafely(message, this::onWiredLink));
      extension.intercept(HMessage.Direction.TOCLIENT, "FurnitureLabelsAndRegions", message -> this.onLabels(message, false));
      extension.intercept(HMessage.Direction.TOCLIENT, "FurnitureLabelsAndRegionsUpdate", message -> this.onLabels(message, true));
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateCondition", message -> this.readWiredSafely(message, this::saveCondition));
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateTrigger", message -> this.readWiredSafely(message, this::saveTrigger));
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateAddon", message -> this.readWiredSafely(message, this::saveAddon));
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateSelector", message -> this.readWiredSafely(message, this::saveSelector));
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateAction", message -> this.readWiredSafely(message, this::saveEffect));
      extension.intercept(HMessage.Direction.TOSERVER, "UpdateVariable", message -> this.readWiredSafely(message, this::saveVariable));
      extension.intercept(HMessage.Direction.TOSERVER, "Chat", this::onChat);
      extension.intercept(HMessage.Direction.TOSERVER, "MoveAvatar", this::moveAvatar);
      extension.intercept(HMessage.Direction.TOSERVER, "Open", this::openWired);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredFurniTrigger", message -> this.readWiredSafely(message, this::retrieveTriggerConf));
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredFurniCondition", message -> this.readWiredSafely(message, this::retrieveConditionConf));
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredFurniAction", message -> this.readWiredSafely(message, this::retrieveActionConf));
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredFurniAddon", message -> this.readWiredSafely(message, this::retrieveAddonConf));
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredFurniSelector", message -> this.readWiredSafely(message, this::retrieveSelectorConf));
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredFurniVariable", message -> this.readWiredSafely(message, this::retrieveVariableConf));
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredAllVariablesDiffs", message -> this.readWiredSafely(message, this::onWiredAllVariables));
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredVariablesForObject", this::onWiredVariablesForObject);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredAllVariableHolders", this::onWiredAllVariableHolders);
      extension.intercept(HMessage.Direction.TOCLIENT, "ObjectRemove", this::onFurniRemoved);
      extension.intercept(HMessage.Direction.TOCLIENT, "RoomReady", this::onRoomReady);
   }

   private void readWiredSafely(HMessage message, Consumer<HMessage> read) {
      try {
         read.accept(message);
      } catch (RuntimeException error) {
         this.extension.getLogger().log("Unsupported or malformed WIRED data: " + error.getMessage(), "red");
         if (this.state == PresetExportState.FETCHING_UNKNOWN_CONFIGS) this.reset();
      }
   }

   private void onContract(HMessage message) {
      PresetWiredContract contract=PresetWiredContract.fromPacket(new HPacket(message.getPacket()),this.extension.getFurniDataTools());
      HFloorItem item=this.extension.getFloorState().furniFromId(contract.furniId);
      if(item==null || !isContract(this.extension.getFurniDataTools().getFloorItemName(item.getTypeId()))) return;
      this.contracts.put(contract.furniId,contract);
      if(this.state==PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
         message.setBlocked(true);
         this.maybeFinishExportAfterRetrieve();
      }
   }

   private static boolean isContract(String name) { return name!=null && name.startsWith("wf_contract_"); }

   private void onWiredLink(HMessage message) {
      HWiredLinkConfiguration link = new HWiredLinkConfiguration(new HPacket(message.getPacket()));
      FloorState room = this.extension.getFloorState();
      if (room == null || room.getRoomId() != link.roomId) return;
      this.wiredLinks.put(link.configuration.furniId, link.configuration);
      if (this.state == PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
         message.setBlocked(true);
         this.maybeFinishExportAfterRetrieve();
      }
   }

   private void onLabels(HMessage message, boolean update) {
      try {
         HPacket packet = message.getPacket();
         Map<Integer, PresetFurniLabel> labels = new HashMap<>();
         int count = Utils.readCount(packet, 19);
         for (int i = 0; i < count; i++) {
            PresetFurniLabel label = PresetFurniLabel.fromPacket(packet);
            labels.put(label.furniId, label);
         }
         List<Integer> removed = update ? Utils.readIntList(packet) : Collections.emptyList();
         if (packet.isCorrupted() || packet.isEOF() != 1) throw new IllegalArgumentException("Unknown label packet layout");
         synchronized (this.furnitureLabels) {
            if (!update) { this.furnitureLabels.clear(); this.hasLabelSnapshot=true; }
            this.furnitureLabels.putAll(labels);
            for (int id : removed) this.furnitureLabels.remove(id);
         }
      } catch (RuntimeException error) {
         this.extension.getLogger().log("Cannot read WIRED labels: " + error.getMessage(), "red");
      }
   }

   private void onRoomReady(HMessage hMessage) {
      this.furnitureLabels.clear();
      this.hasLabelSnapshot=false;
      this.roomSettings=null;
      this.globalVariableValues=null;
      this.globalVariablesResponse.release();
      this.roomSettingsResponse.release();
      this.wiredLinks.clear();
      this.contracts.clear();
      this.variableInfoById.clear();
      this.hasVariableMap = false;
      this.variablesMap = new HashMap();
      this.variableIdToName = new HashMap();
      this.furniVariablesByFurniId.clear();
      this.pendingFurniVariableRequests.clear();
      this.pendingVariableHolderRequests.clear();
      this.exportAreaFurniIds.clear();
      this.variableRequestPhaseStarted = false;
      this.lastVariableActivityTime = 0L;
      this.wiredConditionConfigs.clear();
      this.wiredEffectConfigs.clear();
      this.wiredTriggerConfigs.clear();
      this.wiredAddonConfigs.clear();
      this.wiredSelectorConfigs.clear();
      this.wiredVariableConfigs.clear();
      this.wiredFurniBindings.clear();
      if (this.state != GPresetExporter.PresetExportState.NONE) {
         this.extension.getLogger().log("Room changed during export, resetting export state", "orange");
         this.reset();
      }

   }

   private void onWiredVariablesForObject(HMessage hMessage) {
      HWiredVariablesForObject inspection = new HWiredVariablesForObject(hMessage.getPacket());
      if(inspection.type==-10) { this.globalVariableValues=new HashMap<>(inspection.variables);this.globalVariablesResponse.release();return; }
      if (inspection.type == 0) {
         int furniId = inspection.objectId;
         if (furniId != 0) {
            this.furniVariablesByFurniId.put(furniId, new HashMap(inspection.variables));
            this.pendingFurniVariableRequests.remove(furniId);
            this.lastVariableActivityTime = System.currentTimeMillis();
            Integer waitingFor = (Integer)this.awaitingFurniInspectionId.get();
            if (waitingFor != null && waitingFor == furniId) {
               this.furniInspectionResponseSemaphore.release();
            }

            if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
               this.maybeFinishExportAfterRetrieve();
            }

         }
      }
   }

   private void onWiredAllVariableHolders(HMessage hMessage) {
      if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
         try {
            HPacket packet = hMessage.getPacket();
            packet.readInteger();
            HWiredVariable info = new HWiredVariable(packet);
            String variableId = info.id;
            String variableName = info.name;
            this.variableInfoById.put(variableId, info);
            this.variableIdToName.put(variableId, variableName);
            int holdersCount = Utils.readCount(packet, 8);
            int matched = 0;

            for(int i = 0; i < holdersCount; ++i) {
               int objectId = packet.readInteger();
               int value = packet.readInteger();
               if (this.exportAreaFurniIds.contains(objectId)) {
                  ((Map)this.furniVariablesByFurniId.computeIfAbsent(objectId, (k) -> new HashMap())).put(variableId, value);
                  ++matched;
               }
            }

            this.pendingVariableHolderRequests.remove(variableId);
            this.lastVariableActivityTime = System.currentTimeMillis();
            if (matched > 0) {
               this.extension.getLogger().log(String.format("Found %d furni holding variable '%s'", matched, variableName), "gray");
            }

            String waitingFor = (String)this.awaitingVariableId.get();
            if (waitingFor != null && waitingFor.equals(variableId)) {
               this.variableResponseSemaphore.release();
            }

            this.maybeFinishExportAfterRetrieve();
         } catch (Exception e) {
            this.extension.getLogger().log("Failed to parse WiredAllVariableHolders: " + e.getMessage(), "red");
         }

      }
   }

   private void onWiredAllVariables(HMessage hMessage) {
      if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
         HVariableDiff diff = new HVariableDiff(hMessage.getPacket());
         for (String removedId : diff.removed) {
            this.variableInfoById.remove(removedId);
            this.variableIdToName.remove(removedId);
            this.variablesMap.values().removeIf(removedId::equals);
         }
         List<HWiredVariable> variables = diff.updated;
         variables.forEach((v) -> {
            this.variableIdToName.put(v.id, v.name);
            this.variableInfoById.put(v.id, v);
            if (v.variableInternalType == VariableInternalType.USER_CREATED) {
               this.variablesMap.put(v.name, v.id);
            }

         });
         if (diff.lastChunk) {
            this.hasVariableMap = true;
            this.maybeFinishExportAfterRetrieve();
         }
      }

   }

   private boolean isReady() {
      return this.extension.getFloorState().inRoom() && this.extension.furniDataReady();
   }

   private void onChat(HMessage hMessage) {
      synchronized(this.lock) {
         String text = hMessage.getPacket().readString(StandardCharsets.UTF_8);
         if (this.blockNextChat) {
            this.blockNextChat = false;
            hMessage.setBlocked(true);
         } else {
            if (!text.equals(":abort") && !text.equals(":a")) {
               if (!text.equals(":ep") && !text.equals(":exportpreset") && !text.equals(":ep all") && !text.equals(":exportpreset all")) {
                  if (!text.equals(":epw") && !text.equals(":exportpresetwalls")) {
                     if (this.state == GPresetExporter.PresetExportState.AWAITING_NAME) {
                        hMessage.setBlocked(true);
                        if (!text.matches("[^<>:\"\\/\\\\|?*]*")) {
                           this.extension.sendVisualChatInfo("Invalid characters in name, don't use the following characters: &lt; &gt; : / \\ | ? *");
                           return;
                        }

                        int x1 = this.rectCorner1.getX();
                        int y1 = this.rectCorner1.getY();
                        int x2 = this.rectCorner2.getX();
                        int y2 = this.rectCorner2.getY();
                        int dimX = Math.abs(x1 - x2) + 1;
                        int dimY = Math.abs(y1 - y2) + 1;
                        if (x1 > x2) {
                           x1 = x2;
                        }

                        if (y1 > y2) {
                           y1 = y2;
                        }

                        if (PresetConfigUtils.presetExists(text)) {
                           this.pendingExportName = text;
                           this.pendingExportX = x1;
                           this.pendingExportY = y1;
                           this.pendingExportDimX = dimX;
                           this.pendingExportDimY = dimY;
                           this.state = GPresetExporter.PresetExportState.AWAITING_OVERRIDE_CONFIRMATION;
                           this.extension.sendVisualChatInfo("A preset named '" + text + "' already exists. Type y/yes to override or n/no to cancel.");
                           return;
                        }

                        this.attemptExport(text, x1, y1, dimX, dimY);
                     } else if (this.state == GPresetExporter.PresetExportState.AWAITING_OVERRIDE_CONFIRMATION) {
                        hMessage.setBlocked(true);
                        String low = text.trim().toLowerCase();
                        if (!low.equals("y") && !low.equals("yes")) {
                           if (!low.equals("n") && !low.equals("no")) {
                              this.extension.sendVisualChatInfo("Type y/yes to override or n/no to cancel");
                           } else {
                              this.pendingExportName = null;
                              this.extension.sendVisualChatInfo("Export cancelled");
                              this.reset();
                           }
                        } else {
                           String n = this.pendingExportName;
                           int px = this.pendingExportX;
                           int py = this.pendingExportY;
                           int pdx = this.pendingExportDimX;
                           int pdy = this.pendingExportDimY;
                           this.pendingExportName = null;
                           this.blockNextChat = true;
                           this.state = GPresetExporter.PresetExportState.AWAITING_NAME;
                           this.attemptExport(n, px, py, pdx, pdy);
                        }
                     }
                  } else {
                     hMessage.setBlocked(true);
                     if (this.state != GPresetExporter.PresetExportState.NONE) {
                        this.extension.sendVisualChatInfo("Already exporting preset.. finish up or abort first");
                     } else if (!this.isReady()) {
                        this.extension.sendVisualChatInfo("Error: no room detected or furnidata not available");
                     } else {
                        this.wallOnlyExport = true;
                        this.rectCorner1 = new HPoint(0, 0);
                        this.rectCorner2 = new HPoint(100, 100);
                        this.state = GPresetExporter.PresetExportState.AWAITING_NAME;
                        this.extension.sendVisualChatInfo("Enter the name of the preset");
                     }
                  }
               } else {
                  hMessage.setBlocked(true);
                  if (this.state != GPresetExporter.PresetExportState.NONE) {
                     this.extension.sendVisualChatInfo("Already exporting preset.. finish up or abort first");
                  } else if (!this.isReady()) {
                     this.extension.sendVisualChatInfo("Error: no room detected or furnidata not available");
                  } else if (!text.equals(":ep") && !text.equals(":exportpreset")) {
                     this.wallOnlyExport = false;
                     this.rectCorner1 = new HPoint(0, 0);
                     this.rectCorner2 = new HPoint(100, 100);
                     this.state = GPresetExporter.PresetExportState.AWAITING_NAME;
                     this.extension.sendVisualChatInfo("Enter the name of the preset");
                  } else {
                     this.wallOnlyExport = false;
                     this.state = GPresetExporter.PresetExportState.AWAITING_RECT1;
                     this.extension.sendVisualChatInfo("Select the start of the rectangle");
                  }
               }
            } else {
               hMessage.setBlocked(true);
               if (this.state != GPresetExporter.PresetExportState.NONE) {
                  this.reset();
                  this.extension.sendVisualChatInfo("Aborted preset export");
               }
            }

         }
      }
   }

   private void moveAvatar(HMessage hMessage) {
      synchronized(this.lock) {
         HPacket packet = hMessage.getPacket();
         if (this.state == GPresetExporter.PresetExportState.AWAITING_RECT1) {
            hMessage.setBlocked(true);
            int x = packet.readInteger();
            int y = packet.readInteger();
            this.rectCorner1 = new HPoint(x, y);
            this.state = GPresetExporter.PresetExportState.AWAITING_RECT2;
            this.extension.sendVisualChatInfo("Select the end of the rectangle");
         } else if (this.state == GPresetExporter.PresetExportState.AWAITING_RECT2) {
            hMessage.setBlocked(true);
            int x = packet.readInteger();
            int y = packet.readInteger();
            this.rectCorner2 = new HPoint(x, y);
            this.state = GPresetExporter.PresetExportState.AWAITING_NAME;
            this.extension.sendVisualChatInfo("Enter the name of the preset");
         }

      }
   }

   private void maybeSaveBindings(PresetWiredBase wiredBase) {
      this.wiredFurniBindings.remove(this.wiredCacheKey(wiredBase.getWiredId()));
      FloorState floorState = this.extension.getFloorState();
      FurniDataTools furniDataTools = this.extension.getFurniDataTools();
      HFloorItem wiredItem = floorState.furniFromId(wiredBase.getWiredId());
      String className = furniDataTools.getFloorItemName(wiredItem.getTypeId());
      if (requireBindings.contains(className) && wiredBase.getOptions().size() >= 4) {
         boolean bindState = (Integer)wiredBase.getOptions().get(0) == 1;
         boolean bindDirection = (Integer)wiredBase.getOptions().get(1) == 1;
         boolean bindPosition = (Integer)wiredBase.getOptions().get(2) == 1;
         boolean bindAltitude = (Integer)wiredBase.getOptions().get(3) == 1;
         List<PresetWiredFurniBinding> bindings = new ArrayList();
         wiredBase.getItems().forEach((bindItemId) -> {
            HFloorItem bindItem = floorState.furniFromId(bindItemId);
            if (bindItem != null) {
               PresetWiredFurniBinding binding = new PresetWiredFurniBinding(bindItemId, wiredBase.getWiredId(), !bindPosition ? null : new HPoint(bindItem.getTile().getX(), bindItem.getTile().getY()), !bindDirection ? null : bindItem.getFacing().ordinal(), !bindState ? null : StateExtractor.stateFromItem(bindItem), !bindAltitude ? null : (int)Math.round(bindItem.getTile().getZ() * (double)100.0F));
               bindings.add(binding);
            }

         });
         if (bindings.size() > 0) {
            this.wiredFurniBindings.put(this.wiredCacheKey(wiredBase.getWiredId()), bindings);
         }
      }

   }

   private void saveEffect(HMessage hMessage) {
      if (this.isReady()) {
         PresetWiredEffect preset = new PresetWiredEffect(hMessage.getPacket());
         PresetWiredBase previous = this.wiredEffectConfigs.get(this.wiredCacheKey(preset.getWiredId()));
         if (previous != null) preset.setExternalVariableIds(previous.getExternalVariableIds());
         this.wiredEffectConfigs.put(this.wiredCacheKey(preset.getWiredId()), preset);
         this.maybeSaveBindings(preset);
      }

   }

   private void saveTrigger(HMessage hMessage) {
      if (this.isReady()) {
         PresetWiredTrigger preset = new PresetWiredTrigger(hMessage.getPacket());
         PresetWiredBase previous = this.wiredTriggerConfigs.get(this.wiredCacheKey(preset.getWiredId()));
         if (previous != null) preset.setExternalVariableIds(previous.getExternalVariableIds());
         this.wiredTriggerConfigs.put(this.wiredCacheKey(preset.getWiredId()), preset);
         this.maybeSaveBindings(preset);
      }

   }

   private void saveAddon(HMessage hMessage) {
      if (this.isReady()) {
         PresetWiredAddon preset = new PresetWiredAddon(hMessage.getPacket());
         PresetWiredBase previous = this.wiredAddonConfigs.get(this.wiredCacheKey(preset.getWiredId()));
         if (previous != null) preset.setExternalVariableIds(previous.getExternalVariableIds());
         this.wiredAddonConfigs.put(this.wiredCacheKey(preset.getWiredId()), preset);
         this.maybeSaveBindings(preset);
      }

   }

   private void saveSelector(HMessage hMessage) {
      if (this.isReady()) {
         PresetWiredSelector preset = new PresetWiredSelector(hMessage.getPacket());
         PresetWiredBase previous = this.wiredSelectorConfigs.get(this.wiredCacheKey(preset.getWiredId()));
         if (previous != null) preset.setExternalVariableIds(previous.getExternalVariableIds());
         this.wiredSelectorConfigs.put(this.wiredCacheKey(preset.getWiredId()), preset);
         this.maybeSaveBindings(preset);
      }

   }

   private void saveCondition(HMessage hMessage) {
      if (this.isReady()) {
         PresetWiredCondition preset = new PresetWiredCondition(hMessage.getPacket());
         PresetWiredBase previous = this.wiredConditionConfigs.get(this.wiredCacheKey(preset.getWiredId()));
         if (previous != null) preset.setExternalVariableIds(previous.getExternalVariableIds());
         this.wiredConditionConfigs.put(this.wiredCacheKey(preset.getWiredId()), preset);
         this.maybeSaveBindings(preset);
      }

   }

   private void saveVariable(HMessage hMessage) {
      if (this.isReady()) {
         PresetWiredVariable preset = new PresetWiredVariable(hMessage.getPacket());
         PresetWiredBase previous = this.wiredVariableConfigs.get(this.wiredCacheKey(preset.getWiredId()));
         if (previous != null) preset.setExternalVariableIds(previous.getExternalVariableIds());
         this.wiredVariableConfigs.put(this.wiredCacheKey(preset.getWiredId()), preset);
         this.maybeSaveBindings(preset);
      }

   }

   public void reset() {
      this.rectCorner1 = null;
      this.rectCorner2 = null;
      this.exportName = null;
      this.pendingExportName = null;
      this.blockNextChat = false;
      this.wallOnlyExport = false;
      this.state = GPresetExporter.PresetExportState.NONE;
      this.variableRequestPhaseStarted = false;
      this.lastVariableActivityTime = 0L;
      this.pendingFurniVariableRequests.clear();
      this.pendingVariableHolderRequests.clear();
      this.exportAreaFurniIds.clear();
   }

   public synchronized List<Integer> unRegisteredWiredsInArea(int x, int y, int dimX, int dimY) {
      if (!this.isReady()) {
         return null;
      } else {
         FloorState floor = this.extension.getFloorState();
         FurniDataTools furniDataTools = this.extension.getFurniDataTools();
         List<Integer> unregisteredWired = new ArrayList();

         for(int xi = x; xi < x + dimX; ++xi) {
            for(int yi = y; yi < y + dimY; ++yi) {
               floor.getFurniOnTile(xi, yi).forEach((f) -> {
                  String classname = furniDataTools.getFloorItemName(f.getTypeId());
                  if (("wf_link".equals(classname) && !this.wiredLinks.containsKey(f.getId())) || (isContract(classname) && !this.contracts.containsKey(f.getId())) || WiredKind.fromClassName(classname) == WiredKind.TRIGGER && !this.wiredTriggerConfigs.containsKey(this.wiredCacheKey(f.getId())) || WiredKind.fromClassName(classname) == WiredKind.CONDITION && !this.wiredConditionConfigs.containsKey(this.wiredCacheKey(f.getId())) || WiredKind.fromClassName(classname) == WiredKind.EFFECT && !this.wiredEffectConfigs.containsKey(this.wiredCacheKey(f.getId())) || WiredKind.fromClassName(classname) == WiredKind.ADDON && !this.wiredAddonConfigs.containsKey(this.wiredCacheKey(f.getId())) || WiredKind.fromClassName(classname) == WiredKind.SELECTOR && !this.wiredSelectorConfigs.containsKey(this.wiredCacheKey(f.getId())) || WiredKind.fromClassName(classname) == WiredKind.VARIABLE && !this.wiredVariableConfigs.containsKey(this.wiredCacheKey(f.getId()))) {
                     unregisteredWired.add(f.getId());
                  }

               });
            }
         }

         return unregisteredWired;
      }
   }

   private void export(String name, int x, int y, int dimX, int dimY) {
      if (this.isReady() && (this.wallOnlyExport || this.unRegisteredWiredsInArea(x, y, dimX, dimY).size() == 0 || !this.canExportWired())) {
         FloorState floor = this.extension.getFloorState();
         FurniDataTools furniDataTools = this.extension.getFurniDataTools();
         List<PresetFurni> allFurni = new ArrayList();
         List<PresetWallFurni> allWallFurni = new ArrayList();
         List<PresetWiredCondition> allConditions = new ArrayList();
         List<PresetWiredEffect> allEffects = new ArrayList();
         List<PresetWiredTrigger> allTriggers = new ArrayList();
         List<PresetWiredAddon> allAddons = new ArrayList();
         List<PresetWiredSelector> allSelectors = new ArrayList();
         List<PresetWiredVariable> allVariables = new ArrayList();
         List<List<? extends PresetWiredBase>> wiredLists = Arrays.asList(allConditions, allEffects, allTriggers, allAddons, allSelectors, allVariables);
         List<PresetWiredFurniBinding> allBindings = new ArrayList();
         List<PresetAdsBackground> allAdsBackgrounds = new ArrayList();
         if (!this.wallOnlyExport) {
            for(int xi = x; xi < x + dimX; ++xi) {
               for(int yi = y; yi < y + dimY; ++yi) {
                  for(HFloorItem f : floor.getFurniOnTile(xi, yi)) {
                     String classname = furniDataTools.getFloorItemName(f.getTypeId());
                     PresetFurni presetFurni = new PresetFurni(f.getId(), classname, f.getTile(), f.getFacing().ordinal(), StateExtractor.stateFromItem(f));
                     Map<String, Integer> exportableVariables = this.getExportableFurniVariables(f.getId());
                     if (!exportableVariables.isEmpty()) {
                        presetFurni.setVariables(exportableVariables);
                     }

                     allFurni.add(presetFurni);
                     if (classname.equals("ads_background") && f.getStuff() instanceof MapStuffData) {
                        Map<String, String> stuffDataMap = (MapStuffData)f.getStuff();
                        allAdsBackgrounds.add(new PresetAdsBackground(f.getId(), (String)stuffDataMap.get("imageUrl"), (String)stuffDataMap.get("offsetX"), (String)stuffDataMap.get("offsetY"), (String)stuffDataMap.get("offsetZ")));
                     }

                     if (this.canExportWired()) {
                        String key = this.wiredCacheKey(f.getId());
                        if (WiredKind.fromClassName(classname) == WiredKind.TRIGGER) {
                           allTriggers.add(new PresetWiredTrigger((PresetWiredTrigger)this.wiredTriggerConfigs.get(key)));
                        } else if (WiredKind.fromClassName(classname) == WiredKind.CONDITION) {
                           allConditions.add(new PresetWiredCondition((PresetWiredCondition)this.wiredConditionConfigs.get(key)));
                        } else if (WiredKind.fromClassName(classname) == WiredKind.EFFECT) {
                           allEffects.add(new PresetWiredEffect((PresetWiredEffect)this.wiredEffectConfigs.get(key)));
                        } else if (WiredKind.fromClassName(classname) == WiredKind.ADDON) {
                           allAddons.add(new PresetWiredAddon((PresetWiredAddon)this.wiredAddonConfigs.get(key)));
                        } else if (WiredKind.fromClassName(classname) == WiredKind.SELECTOR) {
                           allSelectors.add(new PresetWiredSelector((PresetWiredSelector)this.wiredSelectorConfigs.get(key)));
                        } else if (WiredKind.fromClassName(classname) == WiredKind.VARIABLE) {
                           PresetWiredVariable presetVariable = new PresetWiredVariable((PresetWiredVariable)this.wiredVariableConfigs.get(key));
                           if (this.hasVariableMap && (presetVariable.variableId == null || presetVariable.variableId.isEmpty() || presetVariable.variableId.equals("0") || presetVariable.getExternalVariableIds().contains(presetVariable.variableId))) {
                              Optional<Map.Entry<String, String>> op = this.variablesMap.entrySet().stream().filter((k) -> ((String)k.getKey()).equals(presetVariable.getVariableName())).findFirst();
                              op.ifPresent((stringStringEntry) -> presetVariable.variableId = (String)stringStringEntry.getValue());
                           }

                           allVariables.add(presetVariable);
                        }

                        if (this.wiredFurniBindings.containsKey(key)) {
                           allBindings.addAll((Collection)this.wiredFurniBindings.get(key).stream().map((b) -> new PresetWiredFurniBinding(b)).collect(Collectors.toList()));
                        }
                     }
                  }
               }
            }
         }

         if (this.extension.shouldExportWallItems() || this.wallOnlyExport) {
            for(HWallItem f : floor.getWallItems()) {
               String classname = furniDataTools.getWallItemName(f.getTypeId());
               String rawLoc = f.getLocation();
               WallPosition parsedLoc = new WallPosition(rawLoc);
               int recoveredAlt = parsedLoc.getAltitude();
               int recoveredOffY = parsedLoc.getOffsetY();
               Map<String, Integer> wallVars = (Map)this.furniVariablesByFurniId.get(-f.getId());
               if (wallVars != null) {
                  Integer altVar = (Integer)wallVars.get("-123");
                  if (altVar != null) {
                     recoveredAlt = altVar;
                  }
               }

               this.extension.getLogger().verbose("export-wall", String.format("id=%d class=%s rawLoc='%s' parsed(x=%d y=%d offX=%d offY=%d dir=%c alt=%d) wiredVars=%s -> stored(alt=%d offY=%d)", f.getId(), classname, rawLoc, parsedLoc.getX(), parsedLoc.getY(), parsedLoc.getOffsetX(), parsedLoc.getOffsetY(), parsedLoc.getDirection(), parsedLoc.getAltitude(), wallVars == null ? "<none>" : wallVars.toString(), recoveredAlt, recoveredOffY));
               WallPosition storedLoc = new WallPosition(parsedLoc.getX(), parsedLoc.getY(), parsedLoc.getOffsetX(), recoveredOffY, parsedLoc.getDirection(), recoveredAlt);
               PresetWallFurni presetWallFurni = new PresetWallFurni(f.getId(), classname, storedLoc, f.getState());
               Map<String, Integer> wallExportableVars = this.getExportableFurniVariables(-f.getId());
               if (!wallExportableVars.isEmpty()) {
                  presetWallFurni.setVariables(wallExportableVars);
               }

               allWallFurni.add(presetWallFurni);
            }
         }

         Set<Integer> allFurniIds = (Set)Stream.concat(allFurni.stream().map(PresetFurni::getFurniId), allWallFurni.stream().map(w->-w.getFurniId())).collect(Collectors.toSet());
         wiredLists.forEach((l) -> l.forEach((w) -> {
               Stream var10001 = w.getItems().stream();
               Objects.requireNonNull(allFurniIds);
               w.setItems((List)var10001.filter(allFurniIds::contains).collect(Collectors.toList()));
               var10001 = w.getSecondItems().stream();
               Objects.requireNonNull(allFurniIds);
               w.setSecondItems((List)var10001.filter(allFurniIds::contains).collect(Collectors.toList()));
            }));
         List<PresetWiredFurniBinding> var33 = (List)allBindings.stream().filter((b) -> allFurniIds.contains(b.getFurniId())).collect(Collectors.toList());
         Map<Integer,Integer> mappedFurniIds=WiredFurnitureIds.sourceMap(allFurni.stream().map(PresetFurni::getFurniId).collect(Collectors.toList()),allWallFurni.stream().map(PresetWallFurni::getFurniId).collect(Collectors.toList()));

         allFurni.forEach((presetFurnix) -> {
            presetFurnix.setFurniId((Integer)mappedFurniIds.get(presetFurnix.getFurniId()));
            HPoint oldLocation = presetFurnix.getLocation();
            presetFurnix.setLocation(new HPoint(oldLocation.getX() - x, oldLocation.getY() - y, oldLocation.getZ()));
         });
         allWallFurni.forEach((presetWallFurnix) -> {
            presetWallFurnix.setFurniId(-mappedFurniIds.get(-presetWallFurnix.getFurniId()));
            WallPosition oldLocation = presetWallFurnix.getLocation();
            presetWallFurnix.setLocation(new WallPosition(oldLocation.getX() - x, oldLocation.getY() - y, oldLocation.getOffsetX(), oldLocation.getOffsetY(), oldLocation.getDirection(), oldLocation.getAltitude()));
         });
         wiredLists.forEach((l) -> l.forEach((w) -> {
               HFloorItem source=floor.furniFromId(w.getWiredId());
               if(source!=null) w.relativizeOverlay(furniDataTools.getFloorItemName(source.getTypeId()),x,y);
               w.setWiredId((Integer)mappedFurniIds.get(w.getWiredId()));
               Stream var10001 = w.getItems().stream();
               Objects.requireNonNull(mappedFurniIds);
               w.setItems((List)var10001.map(mappedFurniIds::get).collect(Collectors.toList()));
               var10001 = w.getSecondItems().stream();
               Objects.requireNonNull(mappedFurniIds);
               w.setSecondItems((List)var10001.map(mappedFurniIds::get).collect(Collectors.toList()));
            }));
         var33.forEach((b) -> {
            b.setFurniId((Integer)mappedFurniIds.get(b.getFurniId()));
            b.setWiredId((Integer)mappedFurniIds.get(b.getWiredId()));
            HPoint oldLocation = b.getLocation();
            Integer oldAltitude = b.getAltitude();
            if (oldLocation != null) {
               b.setLocation(new HPoint(oldLocation.getX() - x, oldLocation.getY() - y));
            }

         });
         allAdsBackgrounds.forEach((a) -> a.setFurniId((Integer)mappedFurniIds.get(a.getFurniId())));
         Map<String, Integer> classToCount = new HashMap();
         allFurni.forEach((presetFurnix) -> {
            String className = presetFurnix.getClassName();
            if (!classToCount.containsKey(className)) {
               classToCount.put(className, 0);
            }

            int number = (Integer)classToCount.get(className);
            classToCount.put(className, number + 1);
            String furniName = String.format("%s[%d]", className, number);
            presetFurnix.setFurniName(furniName);
            if (WiredKind.fromClassName(className) == WiredKind.TRIGGER || WiredKind.fromClassName(className) == WiredKind.CONDITION || WiredKind.fromClassName(className) == WiredKind.EFFECT || WiredKind.fromClassName(className) == WiredKind.ADDON || WiredKind.fromClassName(className) == WiredKind.SELECTOR || WiredKind.fromClassName(className) == WiredKind.VARIABLE) {
               presetFurnix.setState((String)null);
            }

         });
         allWallFurni.forEach((presetWallFurnix) -> {
            String className = presetWallFurnix.getClassName();
            if (!classToCount.containsKey(className)) {
               classToCount.put(className, 0);
            }

            int number = (Integer)classToCount.get(className);
            classToCount.put(className, number + 1);
            String furniName = String.format("%s[%d]", className, number);
            presetWallFurnix.setFurniName(furniName);
         });
         PresetWireds presetWireds = new PresetWireds(allConditions, allEffects, allTriggers, allAddons, allSelectors, allVariables, this.variablesMap);
         PresetConfig presetConfig = new PresetConfig(allFurni, allWallFurni, presetWireds, var33, allAdsBackgrounds);
         List<PresetFurniLabel> exportedLabels = new ArrayList<>();
         if (this.canExportWired()) {
            synchronized (this.furnitureLabels) {
               for (PresetFurniLabel label : this.furnitureLabels.values()) {
                  Integer exportedId = mappedFurniIds.get(label.furniId);
                  if (exportedId != null) exportedLabels.add(label.withFurniId(exportedId).translate(-x, -y));
               }
            }
         }
         presetConfig.setFurnitureLabels(exportedLabels);
         List<PresetWiredLink> exportedLinks = new ArrayList<>();
         if (this.canExportWired()) {
            synchronized (this.wiredLinks) {
               for (PresetWiredLink link : this.wiredLinks.values()) {
                  Integer exportedId = mappedFurniIds.get(link.furniId);
                  if (exportedId != null) exportedLinks.add(link.withFurniId(exportedId));
               }
            }
         }
         presetConfig.setWiredLinks(exportedLinks);
         List<PresetRoomLink> exportedRoomLinks=new ArrayList<>();
         List<PresetWiredContract> exportedContracts=new ArrayList<>();
         List<PresetChest> exportedChests=new ArrayList<>();
         if(this.canExportWired()) {
            synchronized(this.contracts) {
               for(PresetWiredContract c:this.contracts.values()) {
                  Integer id=mappedFurniIds.get(c.furniId);
                  if(id!=null) exportedContracts.add(c.withFurniId(id));
               }
            }
            for(Map.Entry<Integer,Integer> entry:mappedFurniIds.entrySet()) {
               HFloorItem item=entry.getKey()>0?floor.furniFromId(entry.getKey()):null;
               if(item!=null && "wf_room_linker".equals(furniDataTools.getFloorItemName(item.getTypeId()))) {
                  Map<String,Integer> properties=this.furniVariablesByFurniId.get(item.getId());
                  if(properties!=null && properties.containsKey("~103"))exportedRoomLinks.add(PresetRoomLink.capture(entry.getValue(),properties.get("~103"),mappedFurniIds));
               }
               if(item!=null && PresetChest.isChest(furniDataTools.getFloorItemName(item.getTypeId()))) {
                  if(!(item.getStuff() instanceof MapStuffData)) throw new IllegalStateException("Chest settings not loaded for " + item.getId());
                  exportedChests.add(new PresetChest(entry.getValue(), (MapStuffData)item.getStuff()));
               }
            }
         }
         presetConfig.setRoomLinks(exportedRoomLinks);
         List<PresetObjectVariables> exportedValues=new ArrayList<>();
         if(this.canExportWired() && this.extension.shouldExportFurniVariables()) {
            for(Map.Entry<Integer,Integer> entry:mappedFurniIds.entrySet()) {
               // The sign separates floor and wall object namespaces.
               Map<String,Integer> source=this.furniVariablesByFurniId.get(entry.getKey());
               if(source==null) throw new IllegalStateException("Missing furniture properties for " + entry.getKey());
               Map<String,Integer> values=new HashMap<>();
               for(Map.Entry<String,Integer> value:source.entrySet()) {
                  HWiredVariable metadata=this.variableInfoById.get(value.getKey());
                  if(!"-126".equals(value.getKey()) && (PresetObjectVariables.BUILTINS.contains(value.getKey()) || (metadata!=null && metadata.variableInternalType==VariableInternalType.USER_CREATED && metadata.canWriteValue))) values.put(value.getKey(),value.getValue());
               }
               HFloorItem item=entry.getKey()>0?floor.furniFromId(entry.getKey()):null;
               if(item!=null && "wf_room_linker".equals(furniDataTools.getFloorItemName(item.getTypeId()))) values.remove("~103");
               int id=entry.getValue();
               if(!values.isEmpty()) {
                  boolean localTeleport=values.containsKey("~103") && mappedFurniIds.containsKey(values.get("~103"));
                  if(localTeleport) values.put("~103",mappedFurniIds.get(values.get("~103")));
                  Set<String> writeOnly=new HashSet<>();
                  for(String key:values.keySet()) {
                     HWiredVariable metadata=this.variableInfoById.get(key);
                     if(metadata!=null && (metadata.alwaysAvailable || !metadata.canCreateAndDelete)) writeOnly.add(key);
                  }
                  PresetObjectVariables record=new PresetObjectVariables(0,id,values).translate(-x,-y).withWriteOnlyVariables(writeOnly);
                  exportedValues.add(localTeleport?record.withFurnitureReference("~103"):record);
               }
            }
            if(this.globalVariableValues!=null) {
               Set<String> definedIds=allVariables.stream().map(v->v.variableId).collect(Collectors.toSet());
               Map<String,Integer> values=new HashMap<>();
               for(Map.Entry<String,Integer> value:this.globalVariableValues.entrySet()) if(definedIds.contains(value.getKey())) values.put(value.getKey(),value.getValue());
               if(!values.isEmpty()) exportedValues.add(new PresetObjectVariables(-10,0,values));
            }
         }
         presetConfig.setObjectVariables(exportedValues);
         presetConfig.setContracts(exportedContracts);
         presetConfig.setChests(exportedChests);
         if(this.canExportWired() && this.roomSettings!=null) {
            boolean complete=!this.wallOnlyExport && allFurni.size()==floor.getItems().size() && allWallFurni.size()==floor.getWallItems().size();
            presetConfig.setWiredRoomSettings(this.roomSettings.forScope(complete));
         }

         try {
            FloorState layoutFloor = this.extension.getFloorState();
            String raw = layoutFloor.getRawFloorplan();
            if (raw != null) {
               presetConfig.setRoomLayout(new RoomLayoutInfo(layoutFloor.getRoomModelName(), layoutFloor.getFloorplanWidth(), layoutFloor.getFloorplanHeight(), layoutFloor.getFloorScale(), layoutFloor.getFloorWallHeight(), raw));
            }

            try {
               int lowest = PresetUtils.lowestFloorPoint(layoutFloor, new HPoint(x, y), new HPoint(x + dimX - 1, y + dimY - 1));
               if (lowest >= 0 && lowest < 256) {
                  presetConfig.setSrcAnchorFloorHeight((double)lowest);
               }
            } catch (Exception var31) {
            }
         } catch (Exception var32) {
         }

         if (!PresetConfigUtils.savePreset(name, presetConfig)) {
            this.extension.getLogger().log("Could not save preset; previous file preserved", "red");
            this.reset();
            return;
         }
         this.extension.updateInstalledPresets();
         this.extension.getLogger().verbose("export", String.format("preset=%s furni=%d wallFurni=%d wireds(c=%d e=%d t=%d a=%d s=%d v=%d) bindings=%d ads=%d", name, allFurni == null ? 0 : allFurni.size(), allWallFurni == null ? 0 : allWallFurni.size(), allConditions == null ? 0 : allConditions.size(), allEffects == null ? 0 : allEffects.size(), allTriggers == null ? 0 : allTriggers.size(), allAddons == null ? 0 : allAddons.size(), allSelectors == null ? 0 : allSelectors.size(), allVariables == null ? 0 : allVariables.size(), var33 == null ? 0 : var33.size(), allAdsBackgrounds == null ? 0 : allAdsBackgrounds.size()));
         int variableTotal = 0;
         int furniWithVars = 0;

         for(PresetFurni pf : allFurni) {
            Map<String, Integer> vars = pf.getVariables();
            if (vars != null && !vars.isEmpty()) {
               ++furniWithVars;
               variableTotal += vars.size();
            }
         }

         if (variableTotal > 0) {
            this.extension.getLogger().log(String.format("Exported %d furni variable%s across %d furni", variableTotal, variableTotal == 1 ? "" : "s", furniWithVars), "green");
         }

         this.extension.sendVisualChatInfo(String.format("Exported \"%s\" successfully", name));
         this.extension.getLogger().log(String.format("Exported preset \"%s\" successfully", name), "green");
      } else {
         this.extension.sendVisualChatInfo("ERROR - Couldn't export due to unsufficient resources");
         this.extension.getLogger().log("Couldn't export due to unsufficient resources", "red");
      }

      this.reset();
   }

   private void requestWiredConfigsLoop(int x, int y, int dimX, int dimY) {
      try {
         if (this.canExportWired()) {
            if(!this.hasLabelSnapshot) throw new IllegalStateException("Label/region snapshot is missing: re-enter the source room with G-Presets running, then export again");
            if(!this.extension.shouldExportFurniVariables()) this.extension.getLogger().log("Furniture/room values are disabled in settings; this export will omit them", "orange");
            this.roomSettings=null; this.roomSettingsResponse.drainPermits();
            if(!this.extension.sendToServer(PresetWiredRoomSettings.requestPacket()) || !this.roomSettingsResponse.tryAcquire(5L,TimeUnit.SECONDS) || this.roomSettings==null) {
               throw new IllegalStateException("Cannot retrieve WIRED room settings; update G-Earth packet names or check room permissions");
            }
            if(this.state!=PresetExportState.FETCHING_UNKNOWN_CONFIGS) return;
            int originalRemaining;
            synchronized(this.lock) {
               originalRemaining = this.unRegisteredWiredsInArea(x, y, dimX, dimY).size();
            }

            while(this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
               LinkedList<Integer> linkedList;
               synchronized(this.lock) {
                  linkedList = new LinkedList(this.unRegisteredWiredsInArea(x, y, dimX, dimY));
               }

               while(this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS && linkedList.size() > 0) {
                  Integer wiredId = (Integer)linkedList.pollFirst();
                  Utils.sleep(100);
                  HFloorItem item = this.extension.getFloorState().furniFromId(wiredId);
                  String className = item == null ? null : this.extension.getFurniDataTools().getFloorItemName(item.getTypeId());
                  HPacket request = "wf_link".equals(className) ? PresetWiredLink.requestPacket(wiredId) :
                     isContract(className) ? PresetWiredContract.requestPacket(wiredId) : new HPacket("Open", HMessage.Direction.TOSERVER, wiredId);
                  if (!this.extension.sendToServer(request)) {
                     throw new IllegalStateException("Cannot request configuration for " + className + "; update G-Earth packet names");
                  }
               }

               if (this.state != GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
                  return;
               }

               Utils.sleep(300);
               int remainingLeft;
               synchronized(this.lock) {
                  remainingLeft = this.unRegisteredWiredsInArea(x, y, dimX, dimY).size();
               }

               if (remainingLeft == 0) {
                  break;
               }

               if (remainingLeft > originalRemaining / 2) {
                  synchronized(this.lock) {
                     this.extension.sendVisualChatInfo("ERROR - Couldn't export due to missing wired configurations");
                     this.extension.getLogger().log("Couldn't export due to missing wired configurations", "red");
                     this.reset();
                     return;
                  }
               }

               this.extension.sendVisualChatInfo(String.format("WARNING - Did not retrieve all wired. Retrying %d missing wired..", remainingLeft));
               this.extension.getLogger().log(String.format("Did not retrieve all wired. Retrying %d missing wired..", remainingLeft), "orange");
               originalRemaining = remainingLeft;
            }
         }

         if (this.state != GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
            return;
         }

         if (!this.extension.shouldExportFurniVariables()) {
            this.variableRequestPhaseStarted = true;
            this.lastVariableActivityTime = System.currentTimeMillis() - 3000L;
            synchronized(this.lock) {
               if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
                  this.maybeFinishExportAfterRetrieve();
               }

               return;
            }
         }

         this.requestFurniVariablesInArea(x, y, dimX, dimY);

         for(int attempt = 0; attempt <= 1; ++attempt) {
            if (this.state != GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
               return;
            }

            List<String> stillPending;
            synchronized(this.pendingVariableHolderRequests) {
               stillPending = new ArrayList(this.pendingVariableHolderRequests);
            }

            if (stillPending.isEmpty() || attempt == 1) {
               synchronized(this.lock) {
                  if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
                     if (!stillPending.isEmpty()) {
                        this.extension.getLogger().log(String.format("%d variable(s) had no holders in the export area", stillPending.size()), "gray");
                     }

                     this.pendingFurniVariableRequests.clear();
                     this.pendingVariableHolderRequests.clear();
                     this.lastVariableActivityTime = System.currentTimeMillis() - 3000L;
                     this.maybeFinishExportAfterRetrieve();
                  }

                  return;
               }
            }

            this.extension.getLogger().log(String.format("Retrying %d variable(s)..", stillPending.size()), "orange");
            this.lastVariableActivityTime = System.currentTimeMillis();
            this.fetchVariablesSerial(stillPending);
         }
      } catch (Exception var23) {
         synchronized(this.lock) {
            this.extension.sendVisualChatInfo("ERROR - Something went wrong while fetching configurations..");
            this.extension.getLogger().log("Failed to fetch configurations: " + var23.getMessage(), "red");
            this.reset();
         }
      }

   }

   private void maybeFinishExportAfterRetrieve() {
      synchronized(this.lock) {
         if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
            if (!this.isReady()) {
               this.reset();
               return;
            }

            int x1 = this.rectCorner1.getX();
            int y1 = this.rectCorner1.getY();
            int x2 = this.rectCorner2.getX();
            int y2 = this.rectCorner2.getY();
            int dimX = Math.abs(x1 - x2) + 1;
            int dimY = Math.abs(y1 - y2) + 1;
            if (x1 > x2) {
               x1 = x2;
            }

            if (y1 > y2) {
               y1 = y2;
            }

            int remaining = this.canExportWired() ? this.unRegisteredWiredsInArea(x1, y1, dimX, dimY).size() : 0;
            long sinceVarActivity = System.currentTimeMillis() - this.lastVariableActivityTime;
            boolean varGraceElapsed = sinceVarActivity >= 3000L;
            if (remaining == 0 && this.hasVariableMap && this.variableRequestPhaseStarted && varGraceElapsed) {
               this.pendingFurniVariableRequests.clear();
               this.pendingVariableHolderRequests.clear();
               this.export(this.exportName, x1, y1, dimX, dimY);
            } else if (remaining > 0 && remaining % 10 == 0) {
               this.extension.getLogger().log(String.format("%d wired configurations left to retrieve..", remaining), "orange");
            }
         }

      }
   }

   private void maybeRetrieveBindings(int typeId, PresetWiredBase wired) {
      if (this.isReady()) {
         FurniDataTools furniData = this.extension.getFurniDataTools();
         String className = furniData.getFloorItemName(typeId);
         if (requireBindings.contains(className) && !wired.getStringConfig().isEmpty()) {
            List<PresetWiredFurniBinding> bindings = new ArrayList();
            String[] bindingsAsStrings = wired.getStringConfig().split(";");

            for(String bindingAsString : bindingsAsStrings) {
               String[] fields = bindingAsString.split(",");
               int bindFurniId = Integer.parseInt(fields[0]);
               String state = fields[1].equals("N") ? null : fields[1];
               Integer rotation = fields[2].equals("N") ? null : Integer.parseInt(fields[2]);
               HPoint location = !fields[3].equals("N") && !fields[4].equals("N") ? new HPoint(Integer.parseInt(fields[3]), Integer.parseInt(fields[4])) : null;
               Integer altitude = fields.length >= 6 && !fields[5].equals("N") ? Integer.parseInt(fields[5]) : null;
               PresetWiredFurniBinding binding = new PresetWiredFurniBinding(bindFurniId, wired.getWiredId(), location, rotation, state, altitude);
               bindings.add(binding);
            }

            this.wiredFurniBindings.put(this.wiredCacheKey(wired.getWiredId()), bindings);
            wired.setStringConfig("");
         }
      }

   }

   private void retrieveActionConf(HMessage hMessage) {
      if (this.isReady()) {
         if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) hMessage.setBlocked(true);
         RetrievedWiredEffect effect = RetrievedWiredEffect.fromPacket(hMessage.getPacket());
         this.wiredEffectConfigs.put(this.wiredCacheKey(effect.getWiredId()), effect);
         this.maybeRetrieveBindings(effect.getTypeId(), effect);
         this.maybeFinishExportAfterRetrieve();
      }

   }

   private void retrieveAddonConf(HMessage hMessage) {
      if (this.isReady()) {
         if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) hMessage.setBlocked(true);
         RetrievedWiredAddon addon = RetrievedWiredAddon.fromPacket(hMessage.getPacket());
         this.wiredAddonConfigs.put(this.wiredCacheKey(addon.getWiredId()), addon);
         this.maybeRetrieveBindings(addon.getTypeId(), addon);
         this.maybeFinishExportAfterRetrieve();
      }

   }

   private void retrieveSelectorConf(HMessage hMessage) {
      if (this.isReady()) {
         if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) hMessage.setBlocked(true);
         RetrievedWiredSelector selector = RetrievedWiredSelector.fromPacket(hMessage.getPacket());
         this.wiredSelectorConfigs.put(this.wiredCacheKey(selector.getWiredId()), selector);
         this.maybeRetrieveBindings(selector.getTypeId(), selector);
         this.maybeFinishExportAfterRetrieve();
      }

   }

   private void retrieveVariableConf(HMessage hMessage) {
      if (this.isReady()) {
         if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) hMessage.setBlocked(true);
         RetrievedWiredVariable variable = RetrievedWiredVariable.fromPacket(hMessage.getPacket());
         this.wiredVariableConfigs.put(this.wiredCacheKey(variable.getWiredId()), variable);
         this.maybeRetrieveBindings(variable.getTypeId(), variable);
         this.maybeFinishExportAfterRetrieve();
      }

   }

   private void retrieveConditionConf(HMessage hMessage) {
      if (this.isReady()) {
         if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) hMessage.setBlocked(true);
         RetrievedWiredCondition cond = RetrievedWiredCondition.fromPacket(hMessage.getPacket());
         this.wiredConditionConfigs.put(this.wiredCacheKey(cond.getWiredId()), cond);
         this.maybeRetrieveBindings(cond.getTypeId(), cond);
         this.maybeFinishExportAfterRetrieve();
      }

   }

   private void retrieveTriggerConf(HMessage hMessage) {
      if (this.isReady()) {
         if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) hMessage.setBlocked(true);
         RetrievedWiredTrigger trig = RetrievedWiredTrigger.fromPacket(hMessage.getPacket());
         this.wiredTriggerConfigs.put(this.wiredCacheKey(trig.getWiredId()), trig);
         this.maybeRetrieveBindings(trig.getTypeId(), trig);
         this.maybeFinishExportAfterRetrieve();
      }

   }

   private void openWired(HMessage hMessage) {
      if (this.state == GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
         hMessage.setBlocked(true);
         this.extension.sendVisualChatInfo("Do not open wired while the extension is fetching configurations");
      }

   }

   private boolean canExportWired() {
      return this.extension.shouldExportWired() && this.extension.getPermissions() != null && this.extension.getPermissions().canModifyWired();
   }

   private void attemptExport(String name, int x, int y, int dimX, int dimY) {
      if (this.isReady()) {
         if (this.wallOnlyExport) {
            this.furniVariablesByFurniId.clear();
            FloorState floor = this.extension.getFloorState();
            List<HWallItem> wallItems = new ArrayList();
            List<Integer> wallIds = new ArrayList();
            if (floor != null && floor.getWallItems() != null) {
               for(HWallItem wi : floor.getWallItems()) {
                  wallItems.add(wi);
                  wallIds.add(wi.getId());
               }
            }

            if (wallIds.isEmpty()) {
               this.export(name, x, y, dimX, dimY);
               return;
            }

            this.state = GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS;
            (new Thread(() -> {
               this.extension.getLogger().log(String.format("Inspecting %d wall item(s) for altitude/offset variables..", wallIds.size()), "gray");
               this.inspectFurniWithAcks(wallIds, false);
               this.export(name, x, y, dimX, dimY);
            }, "gpresets-wallonly-inspect")).start();
            return;
         }

         this.furniVariablesByFurniId.clear();
         this.pendingFurniVariableRequests.clear();
         this.pendingVariableHolderRequests.clear();
         this.exportAreaFurniIds.clear();
         List<Integer> unregisteredWired = this.unRegisteredWiredsInArea(x, y, dimX, dimY);
         if (this.extension.shouldExportWired() && !unregisteredWired.isEmpty() && (this.extension.getPermissions() == null || !this.extension.getPermissions().canModifyWired())) {
            this.extension.sendVisualChatInfo("WARNING - No wired permissions in this room. Exporting without wired configs.");
            this.extension.getLogger().log("No wired-modify permission - skipping wired config export (" + unregisteredWired.size() + " uncached wired item(s))", "orange");
         }

         if ((!this.hasVariableMap && this.hasWiredVariables() || unregisteredWired.size() > 0) && this.canExportWired()) {
            this.exportName = name;
            this.state = GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS;
            this.hasVariableMap = false;
            this.variablesMap = new HashMap();
            this.variableIdToName = new HashMap();
            this.extension.sendToServer(new HPacket("WiredGetAllVariablesDiffs", HMessage.Direction.TOSERVER, new Object[]{0}));
            this.extension.sendVisualChatInfo(String.format("Fetching additional %s wired configurations before exporting... do not alter the room", unregisteredWired.size()));
            (new Thread(() -> this.requestWiredConfigsLoop(x, y, dimX, dimY))).start();
         } else {
            this.state = GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS;
            this.exportName = name;
            this.hasVariableMap = false;
            this.variablesMap = new HashMap();
            this.variableIdToName = new HashMap();
            this.extension.sendToServer(new HPacket("WiredGetAllVariablesDiffs", HMessage.Direction.TOSERVER, new Object[]{0}));
            (new Thread(() -> this.requestWiredConfigsLoop(x, y, dimX, dimY))).start();
         }
      } else {
         this.reset();
         this.extension.sendVisualChatInfo("ERROR - Couldn't export due to missing floorstate or furnidata");
         this.extension.getLogger().log("Couldn't export due to missing floorstate or furnidata", "red");
      }

   }

   public boolean hasWiredVariables() {
      if (this.wiredVariableConfigs.size() > 0) {
         return true;
      } else {
         for(PresetWiredTrigger preset : this.wiredTriggerConfigs.values()) {
            if (preset.getVariableIds().size() > 0) {
               return true;
            }
         }

         for(PresetWiredAddon preset : this.wiredAddonConfigs.values()) {
            if (preset.getVariableIds().size() > 0) {
               return true;
            }
         }

         for(PresetWiredCondition preset : this.wiredConditionConfigs.values()) {
            if (preset.getVariableIds().size() > 0) {
               return true;
            }
         }

         for(PresetWiredEffect preset : this.wiredEffectConfigs.values()) {
            if (preset.getVariableIds().size() > 0) {
               return true;
            }
         }

         for(PresetWiredSelector preset : this.wiredSelectorConfigs.values()) {
            if (preset.getVariableIds().size() > 0) {
               return true;
            }
         }

         return false;
      }
   }

   public PresetExportState getState() {
      return this.state;
   }

   public void cacheWiredConfig(PresetWiredBase presetWiredBase) {
      FurniDataTools furniData = this.extension.getFurniDataTools();
      FloorState floorState = this.extension.getFloorState();
      if (furniData != null && floorState != null) {
         HFloorItem floorItem = floorState.furniFromId(presetWiredBase.getWiredId());
         if (floorItem != null) {
            int typeId = floorItem.getTypeId();
            String className = furniData.getFloorItemName(typeId);
            if (!requireBindings.contains(className)) {
               if (presetWiredBase instanceof PresetWiredCondition) {
                  PresetWiredCondition condition = (PresetWiredCondition)presetWiredBase;
                  this.wiredConditionConfigs.put(this.wiredCacheKey(condition.getWiredId()), condition);
               }

               if (presetWiredBase instanceof PresetWiredEffect) {
                  PresetWiredEffect effect = (PresetWiredEffect)presetWiredBase;
                  this.wiredEffectConfigs.put(this.wiredCacheKey(effect.getWiredId()), effect);
               }

               if (presetWiredBase instanceof PresetWiredTrigger) {
                  PresetWiredTrigger trigger = (PresetWiredTrigger)presetWiredBase;
                  this.wiredTriggerConfigs.put(this.wiredCacheKey(trigger.getWiredId()), trigger);
               }

               if (presetWiredBase instanceof PresetWiredAddon) {
                  PresetWiredAddon addon = (PresetWiredAddon)presetWiredBase;
                  this.wiredAddonConfigs.put(this.wiredCacheKey(addon.getWiredId()), addon);
               }

               if (presetWiredBase instanceof PresetWiredSelector) {
                  PresetWiredSelector selector = (PresetWiredSelector)presetWiredBase;
                  this.wiredSelectorConfigs.put(this.wiredCacheKey(selector.getWiredId()), selector);
               }

               if (presetWiredBase instanceof PresetWiredVariable) {
                  PresetWiredVariable variable = (PresetWiredVariable)presetWiredBase;
                  this.wiredVariableConfigs.put(this.wiredCacheKey(variable.getWiredId()), variable);
               }

            }
         }
      }
   }

   private void onFurniRemoved(HMessage hMessage) {
      int furniId = Integer.parseInt(hMessage.getPacket().readString());
      String key = this.wiredCacheKey(furniId);
      this.wiredTriggerConfigs.remove(key);
      this.wiredConditionConfigs.remove(key);
      this.wiredEffectConfigs.remove(key);
      this.wiredSelectorConfigs.remove(key);
      this.wiredAddonConfigs.remove(key);
      this.wiredVariableConfigs.remove(key);
   }

   private String wiredCacheKey(int id) {
      return this.extension.getFloorState().getRoomId() + "-" + id;
   }

   private void inspectFurniWithAcks(List<Integer> furniIds, boolean honourFetchingState) { inspectFurniWithAcks(furniIds,honourFetchingState,true); }

   private void inspectFurniWithAcks(List<Integer> furniIds, boolean honourFetchingState,boolean wall) {
      long minSendIntervalMs = 350L;
      long perItemTimeoutMs = 700L;
      long retryTimeoutMs = 1200L;
      int MAX_RETRY_PASSES = 3;
      long lastSendMs = 0L;
      List<Integer> missing = new ArrayList();

      for(Integer wid : furniIds) {
         if (honourFetchingState && this.state != GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
            return;
         }

         if (!this.furniVariablesByFurniId.containsKey(wall?-wid:wid)) {
            long now = System.currentTimeMillis();
            long earliest = lastSendMs + 350L;
            if (now < earliest) {
               Utils.sleep((int)(earliest - now));
            }

            this.furniInspectionResponseSemaphore.drainPermits();
            this.awaitingFurniInspectionId.set(wall?-wid:wid);
            this.extension.sendToServer(new HPacket("WiredGetVariablesForObject", HMessage.Direction.TOSERVER, new Object[]{0, wall?-wid:wid}));
            lastSendMs = System.currentTimeMillis();

            label309: {
               try {
                  this.furniInspectionResponseSemaphore.tryAcquire(700L, TimeUnit.MILLISECONDS);
                  break label309;
               } catch (InterruptedException var33) {
                  Thread.currentThread().interrupt();
                  this.awaitingFurniInspectionId.set(null);
               } finally {
                  this.awaitingFurniInspectionId.set(null);
               }

               return;
            }

            if (!this.furniVariablesByFurniId.containsKey(wall?-wid:wid)) {
               missing.add(wid);
            }
         }
      }

      for(int pass = 1; pass <= 3 && !missing.isEmpty(); ++pass) {
         if (honourFetchingState && this.state != GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
            return;
         }

         this.extension.getLogger().verbose("export-wall-inspect", String.format("retry pass %d/%d for %d wall item(s) without wired-var response", pass, 3, missing.size()));
         Utils.sleep(500);
         List<Integer> stillMissing = new ArrayList();

         for(Integer wid : missing) {
            if (honourFetchingState && this.state != GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
               return;
            }

            if (!this.furniVariablesByFurniId.containsKey(wall?-wid:wid)) {
               long now = System.currentTimeMillis();
               long earliest = lastSendMs + 350L;
               if (now < earliest) {
                  Utils.sleep((int)(earliest - now));
               }

               this.furniInspectionResponseSemaphore.drainPermits();
               this.awaitingFurniInspectionId.set(wall?-wid:wid);
               this.extension.sendToServer(new HPacket("WiredGetVariablesForObject", HMessage.Direction.TOSERVER, new Object[]{0, wall?-wid:wid}));
               lastSendMs = System.currentTimeMillis();

               label288: {
                  try {
                     this.furniInspectionResponseSemaphore.tryAcquire(1200L, TimeUnit.MILLISECONDS);
                     break label288;
                  } catch (InterruptedException var31) {
                     Thread.currentThread().interrupt();
                     this.awaitingFurniInspectionId.set(null);
                  } finally {
                     this.awaitingFurniInspectionId.set(null);
                  }

                  return;
               }

               if (!this.furniVariablesByFurniId.containsKey(wall?-wid:wid)) {
                  stillMissing.add(wid);
               }
            }
         }

         missing = stillMissing;
      }

      if (!missing.isEmpty()) {
         this.extension.getLogger().verbose("export-wall-inspect", String.format("gave up after %d retries; %d wall item(s) still missing wired vars: %s", 3, missing.size(), missing));
      }

   }

   private void requestFurniVariablesInArea(int x, int y, int dimX, int dimY) {
      FloorState floor = this.extension.getFloorState();
      if (floor != null) {
         Set<Integer> areaFurniIds = new HashSet();

         for(int xi = x; xi < x + dimX; ++xi) {
            for(int yi = y; yi < y + dimY; ++yi) {
               floor.getFurniOnTile(xi, yi).forEach((item) -> areaFurniIds.add(item.getId()));
            }
         }

         this.exportAreaFurniIds.clear();
         this.exportAreaFurniIds.addAll(areaFurniIds);
         this.inspectFurniWithAcks(new ArrayList<>(areaFurniIds),true,false);
         for(Integer id:areaFurniIds) if(!this.furniVariablesByFurniId.containsKey(id)) throw new IllegalStateException("Furniture properties were not received for " + id);
         this.globalVariableValues=null;this.globalVariablesResponse.drainPermits();
         try {
            if(!this.extension.sendToServer(new HPacket("WiredGetVariablesForObject",HMessage.Direction.TOSERVER,-10,0)) || !this.globalVariablesResponse.tryAcquire(5L,TimeUnit.SECONDS) || this.globalVariableValues==null) throw new IllegalStateException("Room variable values were not received");
         } catch(InterruptedException error) { Thread.currentThread().interrupt();throw new IllegalStateException("Room variable inspection interrupted",error); }
         if ((this.extension.shouldExportWallItems() || this.wallOnlyExport) && floor.getWallItems() != null) {
            List<HWallItem> wallItems = new ArrayList(floor.getWallItems());
            List<Integer> wallIds = new ArrayList();

            for(HWallItem wi : wallItems) {
               wallIds.add(wi.getId());
            }

            if (!wallIds.isEmpty()) {
               this.extension.getLogger().log(String.format("Inspecting %d wall item(s) for altitude/offset variables..", wallIds.size()), "gray");
               this.pendingFurniVariableRequests.addAll(wallIds.stream().map(id->-id).collect(Collectors.toList()));
               this.lastVariableActivityTime = System.currentTimeMillis();
               this.inspectFurniWithAcks(wallIds, true);
            }
         }

         List<String> variableIds;
         synchronized(this.variablesMap) {
            List<String> all = new ArrayList(this.variablesMap.values());
            variableIds = new ArrayList();
            int skipped = 0;

            for(String id : all) {
               HWiredVariable info = (HWiredVariable)this.variableInfoById.get(id);
               if (info != null && info.variableType == 0) {
                  variableIds.add(id);
               } else {
                  ++skipped;
               }
            }

            if (skipped > 0) {
               this.extension.getLogger().log(String.format("Skipping %d non-furni variable(s) (user/global/context)", skipped), "gray");
            }
         }

         this.pendingVariableHolderRequests.clear();
         this.pendingVariableHolderRequests.addAll(variableIds);
         this.totalVariableRequests = variableIds.size();
         this.sentVariableRequests = 0;
         this.lastVariableActivityTime = System.currentTimeMillis();
         this.variableRequestPhaseStarted = true;
         if (variableIds.isEmpty()) {
            this.extension.getLogger().log("No user-defined wired variables in this room.", "orange");
            this.maybeFinishExportAfterRetrieve();
         } else {
            this.extension.getLogger().log(String.format("Fetching %d wired variable(s)..", variableIds.size()), "orange");
            this.fetchVariablesSerial(variableIds);
         }
      }
   }

   private void fetchVariablesSerial(List<String> variableIds) {
      for(String variableId : variableIds) {
         if (this.state != GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
            return;
         }

         this.variableResponseSemaphore.drainPermits();
         this.awaitingVariableId.set(variableId);
         this.extension.sendToServer(new HPacket("WiredGetAllVariableHolders", HMessage.Direction.TOSERVER, new Object[]{variableId}));
         ++this.sentVariableRequests;
         this.lastVariableActivityTime = System.currentTimeMillis();

         label61: {
            try {
               this.variableResponseSemaphore.tryAcquire(1500L, TimeUnit.MILLISECONDS);
               break label61;
            } catch (InterruptedException var8) {
               Thread.currentThread().interrupt();
            } finally {
               this.awaitingVariableId.set(null);
            }

            return;
         }

         Utils.sleep(80);
      }

   }

   private Map<String, Integer> getExportableFurniVariables(int furniId) {
      Map<String, Integer> source = (Map)this.furniVariablesByFurniId.get(furniId);
      if (source != null && !source.isEmpty()) {
         Map<String, Integer> exportable = new HashMap();
         source.forEach((idOrName, value) -> {
            String resolvedName = (String)this.variableIdToName.getOrDefault(idOrName, idOrName);
            if (!isDefaultVariableName(resolvedName)) {
               exportable.put(resolvedName, value);
            }

         });
         return exportable;
      } else {
         return Collections.emptyMap();
      }
   }

   private static boolean isDefaultVariableName(String name) {
      if (name != null && !name.isEmpty()) {
         char c = name.charAt(0);
         return c == '@' || c == '~' || c == '-';
      } else {
         return true;
      }
   }

   public void clearCache() {
      synchronized(this.lock) {
         if (this.state != GPresetExporter.PresetExportState.FETCHING_UNKNOWN_CONFIGS) {
            this.contracts.clear();
            this.wiredLinks.clear();
            this.wiredTriggerConfigs.clear();
            this.wiredConditionConfigs.clear();
            this.wiredEffectConfigs.clear();
            this.wiredSelectorConfigs.clear();
            this.wiredAddonConfigs.clear();
            this.wiredVariableConfigs.clear();
            this.hasVariableMap = false;
            this.variablesMap = new HashMap();
            this.variableIdToName = new HashMap();
            this.furniVariablesByFurniId.clear();
            this.pendingFurniVariableRequests.clear();
            this.pendingVariableHolderRequests.clear();
            this.exportAreaFurniIds.clear();
            this.variableRequestPhaseStarted = false;
            this.lastVariableActivityTime = 0L;
         }

      }
   }

   public static enum PresetExportState {
      NONE,
      AWAITING_RECT1,
      AWAITING_RECT2,
      AWAITING_NAME,
      AWAITING_OVERRIDE_CONFIRMATION,
      FETCHING_UNKNOWN_CONFIGS;

      // $FF: synthetic method
      private static PresetExportState[] $values() {
         return new PresetExportState[]{NONE, AWAITING_RECT1, AWAITING_RECT2, AWAITING_NAME, AWAITING_OVERRIDE_CONFIRMATION, FETCHING_UNKNOWN_CONFIGS};
      }
   }
}
