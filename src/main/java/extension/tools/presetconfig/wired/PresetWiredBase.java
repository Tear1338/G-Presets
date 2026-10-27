package extension.tools.presetconfig.wired;

import extension.tools.presetconfig.PresetJsonConfigurable;
import gearth.extensions.IExtension;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONObject;
import utils.Utils;

public abstract class PresetWiredBase implements PresetJsonConfigurable, Cloneable {
   protected int wiredId;
   protected List<Integer> options;
   protected String stringConfig;
   protected List<Integer> items;
   protected List<Integer> items2;
   protected List<Integer> pickedFurniSources;
   protected List<Integer> pickedUserSources;
   protected List<String> variableIds;
   private List<String> externalVariableIds = new ArrayList<>();
   private List<Integer> roomCoordinateIndexes = new ArrayList<>();
   private byte[] unrecognizedSaveData = new byte[0];

   public PresetWiredBase(HPacket packet) {
      this.wiredId = packet.readInteger();
      this.options = Utils.readIntList(packet);
      this.stringConfig = Utils.readString(packet);
      this.items = Utils.readIntList(packet);
      this.readTypeSpecific(packet);
      // Older presets/client saves can end at a field boundary. A partial field is an error.
      this.pickedFurniSources = packet.isEOF() == 1 ? new ArrayList<>() : Utils.readIntList(packet);
      this.pickedUserSources = packet.isEOF() == 1 ? new ArrayList<>() : Utils.readIntList(packet);
      this.variableIds = packet.isEOF() == 1 ? new ArrayList<>() : Utils.readStringList(packet);
      this.items2 = packet.isEOF() == 1 ? new ArrayList<>() : Utils.readIntList(packet);
      if (packet.isEOF() == 0) {
         this.unrecognizedSaveData = packet.readBytes(packet.getBytesLength() - packet.getReadIndex());
      }
      if (packet.isCorrupted() || packet.isEOF() != 1) {
         throw new IllegalArgumentException("Malformed WIRED save packet");
      }
   }

   public PresetWiredBase(PresetWiredBase base) {
      this.wiredId = base.getWiredId();
      this.options = new ArrayList(base.options);
      this.stringConfig = base.stringConfig;
      this.items = new ArrayList(base.items);
      this.items2 = new ArrayList(base.items2);
      this.pickedFurniSources = new ArrayList(base.pickedFurniSources);
      this.pickedUserSources = new ArrayList(base.pickedUserSources);
      this.variableIds = new ArrayList<>(base.variableIds);
      this.unrecognizedSaveData = base.unrecognizedSaveData.clone();
      this.externalVariableIds = new ArrayList<>(base.externalVariableIds);
      this.roomCoordinateIndexes = new ArrayList<>(base.roomCoordinateIndexes);
   }

   public PresetWiredBase(int wiredId, List<Integer> options, String stringConfig, List<Integer> items, List<Integer> items2, List<Integer> pickedFurniSources, List<Integer> pickedUserSources, List<String> variableIds) {
      this.wiredId = wiredId;
      this.options = new ArrayList<>(options);
      this.stringConfig = stringConfig;
      this.items = new ArrayList<>(items);
      this.items2 = new ArrayList<>(items2);
      this.pickedFurniSources = new ArrayList<>(pickedFurniSources);
      this.pickedUserSources = new ArrayList<>(pickedUserSources);
      this.variableIds = new ArrayList<>(variableIds);
   }

   public PresetWiredBase(JSONObject object) {
      this.wiredId = object.getInt("wiredId");
      this.roomCoordinateIndexes = readIntegerArray(object.optJSONArray("roomCoordinateIndexes"));
      this.options = readIntegerArray(object.getJSONArray("options"));
      this.stringConfig = object.getString("config");
      this.items = readIntegerArray(object.getJSONArray("items"));
      this.items2 = readIntegerArray(object.optJSONArray("secondItems"));
      this.pickedFurniSources = readIntegerArray(object.optJSONArray("furniSources"));
      this.pickedUserSources = readIntegerArray(object.optJSONArray("userSources"));
      this.variableIds = new ArrayList<>();
      JSONArray ids = object.optJSONArray("variableIds");
      if (ids != null) {
         for (Object id : ids) {
            if (!(id instanceof String) && !(id instanceof Number)) {
               throw new IllegalArgumentException("A WIRED variable ID must be a string");
            }
            this.variableIds.add(id.toString());
         }
      }
      JSONArray externalIds = object.optJSONArray("externalVariableIds");
      if (externalIds != null) for (Object id : externalIds) this.externalVariableIds.add(id.toString());
      if (object.has("unrecognizedSaveData")) {
         this.unrecognizedSaveData = Base64.getDecoder().decode(object.getString("unrecognizedSaveData"));
      }
   }

   private static List<Integer> readIntegerArray(JSONArray array) {
      List<Integer> values = new ArrayList<>();
      if (array != null) {
         for (Object value : array) {
            if (!(value instanceof Number)) {
               throw new IllegalArgumentException("WIRED list contains a non-integer");
            }
            Number number = (Number)value;
            long integer = number.longValue();
            if (integer < Integer.MIN_VALUE || integer > Integer.MAX_VALUE || number.doubleValue() != integer) {
               throw new IllegalArgumentException("WIRED integer out of range: " + value);
            }
            values.add((int)integer);
         }
      }
      return values;
   }

   public JSONObject toJsonObject() {
      JSONObject object = new JSONObject();
      object.put("wiredId", this.wiredId);
      if(!this.roomCoordinateIndexes.isEmpty()) object.put("roomCoordinateIndexes",this.roomCoordinateIndexes);
      object.put("options", this.options);
      object.put("config", this.stringConfig);
      object.put("items", this.items);
      object.put("secondItems", this.items2);
      object.put("furniSources", this.pickedFurniSources);
      object.put("userSources", this.pickedUserSources);
      object.put("variableIds", this.variableIds);
      if (!this.externalVariableIds.isEmpty()) object.put("externalVariableIds", this.externalVariableIds);
      if (this.unrecognizedSaveData.length > 0) {
         object.put("unrecognizedSaveData", Base64.getEncoder().encodeToString(this.unrecognizedSaveData));
      }
      this.appendJsonFields(object);
      return object;
   }

   protected abstract void readTypeSpecific(HPacket var1);

   protected abstract void appendJsonFields(JSONObject var1);

   protected abstract String getPacketName();

   /** Build without sending, so protocol round-trips can be tested offline. */
   public HPacket toPacket() {
      if (hasUnrecognizedSaveData()) {
         throw new IllegalStateException("WIRED " + wiredId + " contains unsupported save fields; preserved in JSON, import stopped");
      }
      if (this.stringConfig == null || this.stringConfig.getBytes(StandardCharsets.UTF_8).length > 65535) {
         throw new IllegalArgumentException("WIRED text exceeds the protocol limit");
      }
      for (String id : this.variableIds) {
         if (id == null || id.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new IllegalArgumentException("Invalid WIRED variable ID");
         }
      }
      HPacket packet = new HPacket(this.getPacketName(), HMessage.Direction.TOSERVER, this.wiredId);
      appendIntegers(packet, this.options);
      packet.appendString(this.stringConfig);
      appendIntegers(packet, this.items);
      this.applyTypeSpecificWiredConfig(packet);
      appendIntegers(packet, this.pickedFurniSources);
      appendIntegers(packet, this.pickedUserSources);
      packet.appendInt(this.variableIds.size());
      this.variableIds.forEach(packet::appendString);
      appendIntegers(packet, this.items2);
      return packet;
   }

   private static void appendIntegers(HPacket packet, List<Integer> values) {
      packet.appendInt(values.size());
      values.forEach(packet::appendInt);
   }

   public boolean hasUnrecognizedSaveData() {
      return this.unrecognizedSaveData.length > 0;
   }

   public void applyWiredConfig(IExtension extension) {
      extension.sendToServer(toPacket());
   }

   protected abstract void applyTypeSpecificWiredConfig(HPacket var1);

   public List<String> getExternalVariableIds() {
      return Collections.unmodifiableList(this.externalVariableIds);
   }

   public void setExternalVariableIds(List<String> ids) {
      this.externalVariableIds = new ArrayList<>(ids);
   }

   public static boolean isLocalVariableId(String id) {
      return id != null && !id.isEmpty() && !id.equals("0") && !id.startsWith("-") && !id.startsWith("~");
   }

   /** A reference cannot fall back to the source room ID or to the placeholder "0". */
   public PresetWiredBase remap(Map<Integer, Integer> furniIds, Map<String, String> variableIds) {
      if (!furniIds.containsKey(this.wiredId)) {
         return null;
      }
      PresetWiredBase copy = this.clone();
      copy.wiredId = furniIds.get(this.wiredId);
      copy.items = this.items.stream().filter(furniIds::containsKey).map(furniIds::get).collect(Collectors.toList());
      copy.items2 = this.items2.stream().filter(furniIds::containsKey).map(furniIds::get).collect(Collectors.toList());
      copy.variableIds = new ArrayList<>();
      for (String id : this.variableIds) {
         String resolved = !isLocalVariableId(id) ? id : variableIds.getOrDefault(id,
            this.externalVariableIds.contains(id) ? id : null);
         if (resolved == null || resolved.isEmpty()) {
            throw new IllegalStateException("Unresolved variable '" + id + "' for WIRED " + this.wiredId);
         }
         copy.variableIds.add(resolved);
      }
      return copy;
   }

   public <T extends PresetWiredBase> T applyWiredConfig(IExtension extension, Map<Integer, Integer> realFurniIdMap, Map<String, String> realVariableIdMap) {
      PresetWiredBase copy = remap(realFurniIdMap, realVariableIdMap);
      if (copy != null) {
         copy.applyWiredConfig(extension);
      }
      return (T)copy;
   }

   /** Tag absolute overlay anchors at export; preserve sentinel/default anchors unchanged. */
   public void relativizeOverlay(String className,int x,int y) {
      if("wf_xtra_var_leaderboard".equals(className) && this.options.size()>11 && this.options.get(10)>=0 && this.options.get(11)>=0) {
         this.roomCoordinateIndexes=new ArrayList<>(List.of(10,11));
         translateRoomCoordinates(-x,-y);
      }
   }

   public void translateRoomCoordinates(int dx,int dy) {
      if(this.roomCoordinateIndexes.isEmpty()) return;
      if(!this.roomCoordinateIndexes.equals(List.of(10,11)) || this.options.size()<12) throw new IllegalArgumentException("Unknown WIRED room-coordinate fields");
      this.options.set(10,Math.addExact(this.options.get(10),dx));
      this.options.set(11,Math.addExact(this.options.get(11),dy));
   }

   public int getWiredId() {
      return this.wiredId;
   }

   public List<Integer> getOptions() {
      return this.options;
   }

   public String getStringConfig() {
      return this.stringConfig;
   }

   public List<Integer> getItems() {
      return this.items;
   }

   public List<Integer> getSecondItems() {
      return this.items2;
   }

   public List<Integer> getPickedFurniSources() {
      return this.pickedFurniSources;
   }

   public List<Integer> getPickedUserSources() {
      return this.pickedUserSources;
   }

   public List<String> getVariableIds() {
      return this.variableIds;
   }

   public void setWiredId(int wiredId) {
      this.wiredId = wiredId;
   }

   public void setOptions(List<Integer> options) {
      this.options = new ArrayList<>(options);
   }

   public void setStringConfig(String stringConfig) {
      this.stringConfig = stringConfig;
   }

   public void setItems(List<Integer> items) {
      this.items = new ArrayList<>(items);
   }

   public void setSecondItems(List<Integer> items) {
      this.items2 = items;
   }

   public void setPickedFurniSources(List<Integer> pickedFurniSources) {
      this.pickedFurniSources = new ArrayList<>(pickedFurniSources);
   }

   public void setPickedUserSources(List<Integer> pickedUserSources) {
      this.pickedUserSources = new ArrayList<>(pickedUserSources);
   }

   public void setVariableIds(List<String> variableIds) {
      this.variableIds = new ArrayList<>(variableIds);
   }

   protected abstract PresetWiredBase clone();
}
