package extension.tools.presetconfig.furni;

import extension.tools.presetconfig.PresetJsonConfigurable;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import utils.WallPosition;

public class PresetWallFurni implements PresetJsonConfigurable {
   private int furniId;
   private String className;
   private WallPosition location;
   private String state;
   private Map<String, Integer> variables;
   private String furniName = null;
   private Double srcFloorHeight = null;

   public PresetWallFurni(int furniId, String className, WallPosition location, String state) {
      this.furniId = furniId;
      this.className = className;
      this.location = location;
      this.state = state;
   }

   public PresetWallFurni(PresetWallFurni furni) {
      this.furniId = furni.furniId;
      this.className = furni.className;
      this.location = new WallPosition(furni.location.getX(), furni.location.getY(), furni.location.getOffsetX(), furni.location.getOffsetY(), furni.location.getDirection(), furni.location.getAltitude());
      this.state = furni.state;
      this.furniName = furni.furniName;
      this.srcFloorHeight = furni.srcFloorHeight;
      this.variables = furni.variables == null ? null : new HashMap(furni.variables);
   }

   public PresetWallFurni(JSONObject jsonObject) {
      this.furniId = jsonObject.getInt("id");
      this.className = jsonObject.getString("className");
      JSONObject loc = jsonObject.getJSONObject("location");
      this.location = new WallPosition(loc.getInt("x"), loc.getInt("y"), loc.getInt("offsetX"), loc.getInt("offsetY"), loc.getString("direction").charAt(0), loc.getInt("altitude"));
      this.state = jsonObject.has("state") ? jsonObject.getString("state") : null;
      this.furniName = jsonObject.getString("name");
      if (jsonObject.has("srcFloorHeight") && !jsonObject.isNull("srcFloorHeight")) {
         this.srcFloorHeight = jsonObject.getDouble("srcFloorHeight");
      }

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
      jsonLocation.put("offsetX", this.location.getOffsetX());
      jsonLocation.put("offsetY", this.location.getOffsetY());
      jsonLocation.put("direction", String.valueOf(this.location.getDirection()));
      jsonLocation.put("altitude", this.location.getAltitude());
      object.put("location", jsonLocation);
      if (this.state != null) {
         object.put("state", this.state);
      }

      object.put("name", this.furniName);
      if (this.srcFloorHeight != null) {
         object.put("srcFloorHeight", this.srcFloorHeight);
      }

      if (this.variables != null && !this.variables.isEmpty()) {
         object.put("variables", new JSONObject(this.variables));
      }

      return object;
   }

   public int getFurniId() {
      return this.furniId;
   }

   public void setFurniId(int furniId) {
      this.furniId = furniId;
   }

   public String getClassName() {
      return this.className;
   }

   public void setClassName(String className) {
      this.className = className;
   }

   public WallPosition getLocation() {
      return this.location;
   }

   public void setLocation(WallPosition location) {
      this.location = location;
   }

   public String getState() {
      return this.state;
   }

   public void setState(String state) {
      this.state = state;
   }

   public String getFurniName() {
      return this.furniName;
   }

   public void setFurniName(String furniName) {
      this.furniName = furniName;
   }

   public Double getSrcFloorHeight() {
      return this.srcFloorHeight;
   }

   public void setSrcFloorHeight(Double srcFloorHeight) {
      this.srcFloorHeight = srcFloorHeight;
   }

   public Map<String, Integer> getVariables() {
      return this.variables;
   }

   public void setVariables(Map<String, Integer> variables) {
      this.variables = variables;
   }
}
