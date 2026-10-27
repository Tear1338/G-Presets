package extension.tools.presetconfig;

import extension.tools.postconfig.FurniPostConfig;
import extension.tools.postconfig.PostConfig;
import extension.tools.presetconfig.ads_bg.PresetAdsBackground;
import extension.tools.presetconfig.binding.PresetWiredFurniBinding;
import extension.tools.presetconfig.furni.PresetFurni;
import extension.tools.presetconfig.furni.PresetChest;
import extension.tools.presetconfig.wired.PresetWiredContract;
import extension.tools.presetconfig.furni.PresetFurniLabel;
import extension.tools.presetconfig.furni.PresetWallFurni;
import extension.tools.presetconfig.wired.PresetWireds;
import extension.tools.presetconfig.wired.PresetWiredLink;
import extension.tools.presetconfig.wired.PresetWiredRoomSettings;
import extension.tools.presetconfig.wired.PresetRoomLink;
import extension.tools.presetconfig.wired.PresetObjectVariables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

public class PresetConfig implements PresetJsonConfigurable {
   private List<PresetObjectVariables> objectVariables = new ArrayList<>();
   private PresetWiredRoomSettings wiredRoomSettings;
   private List<PresetRoomLink> roomLinks = new ArrayList<>();
   private List<PresetChest> chests = new ArrayList<>();
   private List<PresetWiredContract> contracts = new ArrayList<>();
   private List<PresetFurni> furniture;
   private List<PresetWallFurni> wallFurniture;
   private PresetWireds presetWireds;
   private List<PresetWiredFurniBinding> bindings;
   private List<PresetAdsBackground> adsBackgrounds;
   private RoomLayoutInfo roomLayout;
   private Double srcAnchorFloorHeight;
   private List<PresetWiredLink> wiredLinks = new ArrayList<>();
   private List<PresetFurniLabel> furnitureLabels = new ArrayList<>();

   public PresetConfig(List<PresetFurni> furniture, List<PresetWallFurni> wallFurniture, PresetWireds presetWireds, List<PresetWiredFurniBinding> bindings, List<PresetAdsBackground> adsBackgrounds) {
      this.furniture = furniture;
      this.wallFurniture = wallFurniture;
      this.presetWireds = presetWireds;
      this.bindings = bindings;
      this.adsBackgrounds = adsBackgrounds;
   }

   public PresetConfig(JSONObject object) {
      if(object.has("objectVariables")) for(Object item:object.getJSONArray("objectVariables")) objectVariables.add(new PresetObjectVariables((JSONObject)item));
      if(object.has("wiredRoomSettings")) wiredRoomSettings=new PresetWiredRoomSettings(object.getJSONObject("wiredRoomSettings"));
      if (object.has("roomLinks")) for(Object item:object.getJSONArray("roomLinks")) roomLinks.add(new PresetRoomLink((JSONObject)item));
      if (object.has("chests")) for(Object item:object.getJSONArray("chests")) chests.add(new PresetChest((JSONObject)item));
      if (object.has("contracts")) for(Object item:object.getJSONArray("contracts")) contracts.add(new PresetWiredContract((JSONObject)item));
      this.furniture = (List)object.getJSONArray("furni").toList().stream().map((o) -> new PresetFurni(new JSONObject((Map)o))).collect(Collectors.toList());
      this.wallFurniture = (List<PresetWallFurni>)(!object.has("wallFurni") ? new ArrayList() : (List)object.getJSONArray("wallFurni").toList().stream().map((o) -> new PresetWallFurni(new JSONObject((Map)o))).collect(Collectors.toList()));
      this.presetWireds = new PresetWireds(object.getJSONObject("wired"));
      this.bindings = (List)object.getJSONArray("bindings").toList().stream().map((o) -> new PresetWiredFurniBinding(new JSONObject((Map)o))).collect(Collectors.toList());
      this.adsBackgrounds = (List<PresetAdsBackground>)(!object.has("adsBackgrounds") ? new ArrayList() : (List)object.getJSONArray("adsBackgrounds").toList().stream().map((o) -> new PresetAdsBackground(new JSONObject((Map)o))).collect(Collectors.toList()));
      if (object.has("wiredLinks")) {
         for (Object link : object.getJSONArray("wiredLinks")) this.wiredLinks.add(new PresetWiredLink((JSONObject)link));
      }
      if (object.has("furnitureLabels")) {
         for (Object label : object.getJSONArray("furnitureLabels")) {
            this.furnitureLabels.add(new PresetFurniLabel((JSONObject)label));
         }
      }
      if (object.has("roomLayout") && !object.isNull("roomLayout")) {
         this.roomLayout = new RoomLayoutInfo(object.getJSONObject("roomLayout"));
      }

      if (object.has("srcAnchorFloorHeight") && !object.isNull("srcAnchorFloorHeight")) {
         this.srcAnchorFloorHeight = object.getDouble("srcAnchorFloorHeight");
      }

   }

   public JSONObject toJsonObject() {
      JSONObject object = new JSONObject();
      JSONArray jsonFurni = new JSONArray((Collection)this.furniture.stream().map(PresetFurni::toJsonObject).collect(Collectors.toList()));
      JSONArray jsonBindings = new JSONArray((Collection)this.bindings.stream().map(PresetWiredFurniBinding::toJsonObject).collect(Collectors.toList()));
      object.put("furni", jsonFurni);
      if(!objectVariables.isEmpty()) object.put("objectVariables",new JSONArray(objectVariables.stream().map(PresetObjectVariables::toJsonObject).collect(Collectors.toList())));
      if(wiredRoomSettings!=null) object.put("wiredRoomSettings",wiredRoomSettings.toJsonObject());
      if(!roomLinks.isEmpty()) object.put("roomLinks",new JSONArray(roomLinks.stream().map(PresetRoomLink::toJsonObject).collect(Collectors.toList())));
      if (!chests.isEmpty()) object.put("chests",new JSONArray(chests.stream().map(PresetChest::toJsonObject).collect(Collectors.toList())));
      if (!contracts.isEmpty()) object.put("contracts",new JSONArray(contracts.stream().map(PresetWiredContract::toJsonObject).collect(Collectors.toList())));
      if (!this.wallFurniture.isEmpty()) {
         JSONArray jsonWallFurni = new JSONArray((Collection)this.wallFurniture.stream().map(PresetWallFurni::toJsonObject).collect(Collectors.toList()));
         object.put("wallFurni", jsonWallFurni);
      }

      object.put("wired", this.presetWireds.toJsonObject());
      object.put("bindings", jsonBindings);
      if (!this.wiredLinks.isEmpty()) object.put("wiredLinks", new JSONArray(this.wiredLinks.stream()
         .map(PresetWiredLink::toJsonObject).collect(Collectors.toList())));
      if (!this.furnitureLabels.isEmpty()) {
         object.put("furnitureLabels", new JSONArray(this.furnitureLabels.stream()
            .map(PresetFurniLabel::toJsonObject).collect(Collectors.toList())));
      }
      if (!this.adsBackgrounds.isEmpty()) {
         object.put("adsBackgrounds", new JSONArray(this.adsBackgrounds.stream().map(PresetAdsBackground::toJsonObject).collect(Collectors.toList())));
      }

      if (this.roomLayout != null) {
         object.put("roomLayout", this.roomLayout.toJsonObject());
      }

      if (this.srcAnchorFloorHeight != null) {
         object.put("srcAnchorFloorHeight", this.srcAnchorFloorHeight);
      }

      return object;
   }

   public Map<Integer, Integer> applyPostConfig(PostConfig postConfig) {
      Map<Integer, Integer> furniIdMap = new HashMap();

      for(int i = this.furniture.size() - 1; i >= 0; --i) {
         PresetFurni p = (PresetFurni)this.furniture.get(i);
         FurniPostConfig c = postConfig.getFurniPostConfig(p.getFurniName());
         if (c != null && c.isUseExistingFurni()) {
            furniIdMap.put(p.getFurniId(), c.getExistingFurniId());
            this.furniture.remove(i);
         } else if (postConfig.getClassMapping(p.getClassName()) != null) {
            p.setClassName(postConfig.getClassMapping(p.getClassName()));
         }
      }

      return furniIdMap;
   }

   public List<PresetObjectVariables> getObjectVariables() { return objectVariables; }
   public void setObjectVariables(List<PresetObjectVariables> values) { objectVariables=new ArrayList<>(values); }
   public PresetWiredRoomSettings getWiredRoomSettings() { return wiredRoomSettings; }
   public void setWiredRoomSettings(PresetWiredRoomSettings settings) { wiredRoomSettings=settings; }
   public List<PresetRoomLink> getRoomLinks() { return roomLinks; }
   public void setRoomLinks(List<PresetRoomLink> values) { roomLinks = new ArrayList<>(values); }
   public List<PresetChest> getChests() { return chests; }
   public void setChests(List<PresetChest> values) { chests = new ArrayList<>(values); }
   public List<PresetWiredContract> getContracts() { return contracts; }
   public void setContracts(List<PresetWiredContract> values) { contracts = new ArrayList<>(values); }

   public List<PresetWiredLink> getWiredLinks() { return this.wiredLinks; }

   public void setWiredLinks(List<PresetWiredLink> links) { this.wiredLinks = new ArrayList<>(links); }

   public List<PresetFurniLabel> getFurnitureLabels() { return this.furnitureLabels; }

   public void setFurnitureLabels(List<PresetFurniLabel> labels) { this.furnitureLabels = new ArrayList<>(labels); }

   public List<PresetFurni> getFurniture() {
      return this.furniture;
   }

   public void setFurniture(List<PresetFurni> furniture) {
      this.furniture = furniture;
   }

   public List<PresetWallFurni> getWallFurniture() {
      return this.wallFurniture;
   }

   public void setWallFurniture(List<PresetWallFurni> wallFurniture) {
      this.wallFurniture = wallFurniture;
   }

   public PresetWireds getPresetWireds() {
      return this.presetWireds;
   }

   public void setPresetWireds(PresetWireds presetWireds) {
      this.presetWireds = presetWireds;
   }

   public List<PresetWiredFurniBinding> getBindings() {
      return this.bindings;
   }

   public void setBindings(List<PresetWiredFurniBinding> bindings) {
      this.bindings = bindings;
   }

   public List<PresetAdsBackground> getAdsBackgrounds() {
      return this.adsBackgrounds;
   }

   public void setAdsBackgrounds(List<PresetAdsBackground> adsBackgrounds) {
      this.adsBackgrounds = adsBackgrounds;
   }

   public RoomLayoutInfo getRoomLayout() {
      return this.roomLayout;
   }

   public void setRoomLayout(RoomLayoutInfo roomLayout) {
      this.roomLayout = roomLayout;
   }

   public Double getSrcAnchorFloorHeight() {
      return this.srcAnchorFloorHeight;
   }

   public void setSrcAnchorFloorHeight(Double srcAnchorFloorHeight) {
      this.srcAnchorFloorHeight = srcAnchorFloorHeight;
   }
}
