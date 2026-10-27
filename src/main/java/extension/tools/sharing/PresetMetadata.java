package extension.tools.sharing;

import java.util.Base64;
import org.json.JSONObject;

/** Optional sharing data, kept out of all packet serializers. */
public record PresetMetadata(String name, String description, String imagePng) {
   public PresetMetadata {
      if(name==null || name.isBlank() || name.length()>120)throw new IllegalArgumentException("Name must contain 1 to 120 characters");
      if(description==null || description.length()>4000)throw new IllegalArgumentException("Description is limited to 4000 characters");
      if(imagePng==null || imagePng.length()>2800000)throw new IllegalArgumentException("Preview image is too large");
      if(!imagePng.isEmpty()) {
         byte[] bytes=Base64.getDecoder().decode(imagePng);
         byte[] magic={(byte)137,80,78,71,13,10,26,10};
         if(bytes.length<24 || bytes.length>2000000)throw new IllegalArgumentException("Invalid PNG image");
         for(int i=0;i<magic.length;i++)if(bytes[i]!=magic[i])throw new IllegalArgumentException("Expected a PNG image");
         java.nio.ByteBuffer header=java.nio.ByteBuffer.wrap(bytes);
         int w=header.getInt(16),h=header.getInt(20);
         if(w<1 || h<1 || w>4096 || h>4096)throw new IllegalArgumentException("Preview image dimensions exceed 4096 pixels");
      }
   }
   public PresetMetadata(JSONObject data) {this(data.getString("name"),data.optString("description",""),data.optString("imagePng",""));}
   public JSONObject toJsonObject() {return new JSONObject().put("name",name).put("description",description).put("imagePng",imagePng).put("imageSource","G-Presets preview; furniture artwork: HabboAssets / Sulake");}
}
