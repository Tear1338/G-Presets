package extension.tools.presetconfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONObject;

public class RoomLayoutInfo implements PresetJsonConfigurable {
   private final String roomModelName;
   private final int floorplanWidth;
   private final int floorplanHeight;
   private final int scale;
   private final int wallHeight;
   private final String floorplanSignature;

   public RoomLayoutInfo(String roomModelName, int floorplanWidth, int floorplanHeight, int scale, int wallHeight, String rawFloorplan) {
      this.roomModelName = roomModelName;
      this.floorplanWidth = floorplanWidth;
      this.floorplanHeight = floorplanHeight;
      this.scale = scale;
      this.wallHeight = wallHeight;
      this.floorplanSignature = signature(scale, wallHeight, rawFloorplan);
   }

   public RoomLayoutInfo(JSONObject obj) {
      this.roomModelName = obj.optString("modelName", (String)null);
      this.floorplanWidth = obj.optInt("floorplanWidth", 0);
      this.floorplanHeight = obj.optInt("floorplanHeight", 0);
      this.scale = obj.optInt("scale", 0);
      this.wallHeight = obj.optInt("wallHeight", 0);
      this.floorplanSignature = obj.optString("floorplanSignature", (String)null);
   }

   public JSONObject toJsonObject() {
      JSONObject obj = new JSONObject();
      if (this.roomModelName != null) {
         obj.put("modelName", this.roomModelName);
      }

      obj.put("floorplanWidth", this.floorplanWidth);
      obj.put("floorplanHeight", this.floorplanHeight);
      obj.put("scale", this.scale);
      obj.put("wallHeight", this.wallHeight);
      if (this.floorplanSignature != null) {
         obj.put("floorplanSignature", this.floorplanSignature);
      }

      return obj;
   }

   public String getRoomModelName() {
      return this.roomModelName;
   }

   public int getFloorplanWidth() {
      return this.floorplanWidth;
   }

   public int getFloorplanHeight() {
      return this.floorplanHeight;
   }

   public int getScale() {
      return this.scale;
   }

   public int getWallHeight() {
      return this.wallHeight;
   }

   public String getFloorplanSignature() {
      return this.floorplanSignature;
   }

   public boolean matches(RoomLayoutInfo other) {
      if (other == null) {
         return false;
      } else if (this.floorplanWidth != other.floorplanWidth) {
         return false;
      } else if (this.floorplanHeight != other.floorplanHeight) {
         return false;
      } else if (this.scale != other.scale) {
         return false;
      } else if (this.wallHeight != other.wallHeight) {
         return false;
      } else {
         return this.floorplanSignature == null || other.floorplanSignature == null || this.floorplanSignature.equals(other.floorplanSignature);
      }
   }

   public static String signature(int scale, int wallHeight, String rawFloorplan) {
      if (rawFloorplan == null) {
         return null;
      } else {
         String input = scale + "|" + wallHeight + "|" + rawFloorplan;

         try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);

            for(byte b : digest) {
               sb.append(String.format("%02x", b));
            }

            return sb.substring(0, 16);
         } catch (NoSuchAlgorithmException var11) {
            return Integer.toHexString(input.hashCode());
         }
      }
   }
}
