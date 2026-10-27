package extension.tools.presetconfig.binding;

import extension.tools.presetconfig.PresetJsonConfigurable;
import gearth.extensions.parsers.HPoint;
import org.json.JSONObject;

public class PresetWiredFurniBinding implements PresetJsonConfigurable {
   private int furniId;
   private int wiredId;
   private HPoint location;
   private Integer rotation;
   private String state;
   private Integer altitude;

   public PresetWiredFurniBinding(int furniId, int wiredId, HPoint location, Integer rotation, String state, Integer altitude) {
      this.furniId = furniId;
      this.wiredId = wiredId;
      this.location = location;
      this.rotation = rotation;
      this.state = state;
      this.altitude = altitude;
   }

   public PresetWiredFurniBinding(PresetWiredFurniBinding binding) {
      this.furniId = binding.furniId;
      this.wiredId = binding.wiredId;
      this.location = binding.location == null ? null : new HPoint(binding.location.getX(), binding.location.getY());
      this.rotation = binding.rotation;
      this.state = binding.state;
      this.altitude = binding.altitude;
   }

   public PresetWiredFurniBinding(JSONObject object) {
      this.furniId = object.getInt("furniId");
      this.wiredId = object.getInt("wiredId");
      this.location = object.has("location") ? new HPoint(object.getJSONObject("location").getInt("x"), object.getJSONObject("location").getInt("y")) : null;
      this.rotation = object.has("rotation") ? object.getInt("rotation") : null;
      this.state = object.has("state") ? object.getString("state") : null;
      this.altitude = object.has("altitude") ? object.getInt("altitude") : null;
   }

   public JSONObject toJsonObject() {
      JSONObject object = new JSONObject();
      object.put("furniId", this.furniId);
      object.put("wiredId", this.wiredId);
      if (this.location != null) {
         JSONObject jsonLocation = new JSONObject();
         jsonLocation.put("x", this.location.getX());
         jsonLocation.put("y", this.location.getY());
         object.put("location", jsonLocation);
      }

      if (this.rotation != null) {
         object.put("rotation", this.rotation);
      }

      if (this.state != null) {
         object.put("state", this.state);
      }

      if (this.altitude != null) {
         object.put("altitude", this.altitude);
      }

      return object;
   }

   public int getFurniId() {
      return this.furniId;
   }

   public void setFurniId(int furniId) {
      this.furniId = furniId;
   }

   public int getWiredId() {
      return this.wiredId;
   }

   public void setWiredId(int wiredId) {
      this.wiredId = wiredId;
   }

   public HPoint getLocation() {
      return this.location;
   }

   public void setLocation(HPoint location) {
      this.location = location;
   }

   public Integer getRotation() {
      return this.rotation;
   }

   public void setRotation(Integer rotation) {
      this.rotation = rotation;
   }

   public String getState() {
      return this.state;
   }

   public void setState(String state) {
      this.state = state;
   }

   public Integer getAltitude() {
      return this.altitude;
   }

   public void setAltitude(Integer altitude) {
      this.altitude = altitude;
   }
}
