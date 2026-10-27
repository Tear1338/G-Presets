package extension.tools.presetconfig.furni;

import extension.tools.presetconfig.PresetJsonConfigurable;
import gearth.extensions.parsers.HPoint;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public class PresetFurni implements PresetJsonConfigurable {
   private int furniId;
   private String className;
   private HPoint location;
   private int rotation;
   private String state;
   private Map<String, Integer> variables;
   private String furniName = null;

   public PresetFurni(int furniId, String className, HPoint location, int rotation, String state) {
      this.furniId = furniId;
      this.className = className;
      this.location = location;
      this.rotation = rotation;
      this.state = state;
   }

   public PresetFurni(PresetFurni furni) {
      this.furniId = furni.furniId;
      this.className = furni.className;
      this.location = new HPoint(furni.location.getX(), furni.location.getY(), furni.location.getZ());
      this.rotation = furni.rotation;
      this.state = furni.state;
      this.furniName = furni.furniName;
      this.variables = furni.variables == null ? null : new HashMap(furni.variables);
   }

   public PresetFurni(JSONObject jsonObject) {
      this.furniId = jsonObject.getInt("id");
      this.className = jsonObject.getString("className");
      JSONObject jsonLocation = jsonObject.getJSONObject("location");
      this.location = new HPoint(jsonLocation.getInt("x"), jsonLocation.getInt("y"), jsonLocation.getDouble("z"));
      this.rotation = jsonObject.getInt("rotation");
      this.state = jsonObject.has("state") ? jsonObject.getString("state") : null;
      this.furniName = jsonObject.getString("name");
      if (jsonObject.has("variables")) {
         this.variables = new HashMap();
         JSONObject vars = jsonObject.getJSONObject("variables");

         for(String key : vars.keySet()) {
            this.variables.put(key, vars.getInt(key));
         }
      }

   }

   public JSONObject toJsonObject() {
      JSONObject object = new JSONObject();
      object.put("id", this.furniId);
      object.put("className", this.className);
      JSONObject jsonLocation = new JSONObject();
      jsonLocation.put("x", this.location.getX());
      jsonLocation.put("y", this.location.getY());
      jsonLocation.put("z", this.location.getZ());
      object.put("location", jsonLocation);
      object.put("rotation", this.rotation);
      if (this.state != null) {
         object.put("state", this.state);
      }

      if (this.variables != null && !this.variables.isEmpty()) {
         object.put("variables", new JSONObject(this.variables));
      }

      object.put("name", this.furniName);
      return object;
   }

   public String getClassName() {
      return this.className;
   }

   public void setClassName(String className) {
      this.className = className;
   }

   public HPoint getLocation() {
      return this.location;
   }

   public void setLocation(HPoint location) {
      this.location = location;
   }

   public int getRotation() {
      return this.rotation;
   }

   public void setRotation(int rotation) {
      this.rotation = rotation;
   }

   public String getState() {
      return this.state;
   }

   public void setState(String state) {
      this.state = state;
   }

   public int getFurniId() {
      return this.furniId;
   }

   public void setFurniId(int furniId) {
      this.furniId = furniId;
   }

   public void setFurniName(String furniName) {
      this.furniName = furniName;
   }

   public String getFurniName() {
      return this.furniName;
   }

   public Map<String, Integer> getVariables() {
      return this.variables;
   }

   public void setVariables(Map<String, Integer> variables) {
      this.variables = variables;
   }
}
