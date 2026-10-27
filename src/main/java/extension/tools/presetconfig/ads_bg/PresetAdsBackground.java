package extension.tools.presetconfig.ads_bg;

import org.json.JSONObject;

public class PresetAdsBackground {
   private int furniId;
   private String imageUrl;
   private String offsetX;
   private String offsetY;
   private String offsetZ;

   public PresetAdsBackground(int furniId, String imageUrl, String offsetX, String offsetY, String offsetZ) {
      this.furniId = furniId;
      this.imageUrl = imageUrl;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.offsetZ = offsetZ;
   }

   public PresetAdsBackground(JSONObject object) {
      this.furniId = object.getInt("furniId");
      this.imageUrl = object.getString("imageUrl");
      this.offsetX = object.getString("offsetX");
      this.offsetY = object.getString("offsetY");
      this.offsetZ = object.getString("offsetZ");
   }

   public JSONObject toJsonObject() {
      return new JSONObject().put("furniId", furniId).put("imageUrl", imageUrl)
         .put("offsetX", offsetX).put("offsetY", offsetY).put("offsetZ", offsetZ);
   }

   public String getImageUrl() {
      return this.imageUrl;
   }

   public String getOffsetX() {
      return this.offsetX;
   }

   public String getOffsetY() {
      return this.offsetY;
   }

   public String getOffsetZ() {
      return this.offsetZ;
   }

   public int getFurniId() {
      return this.furniId;
   }

   public void setFurniId(int furniId) {
      this.furniId = furniId;
   }
}
