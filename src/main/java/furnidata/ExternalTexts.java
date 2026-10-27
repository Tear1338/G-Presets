package furnidata;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;

public class ExternalTexts {
   private final Map<String, String> texts = new HashMap();

   public ExternalTexts(String url) throws IOException {
      String content = FurniDataTools.readUrl(url);

      for(String line : content.split("\\r?\\n")) {
         int eqIdx = line.indexOf(61);
         if (eqIdx > 0) {
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            this.texts.put(key, value);
         }
      }

   }

   public String get(String key) {
      return (String)this.texts.get(key);
   }

   public String getWallItemName(String className, String state) {
      return className != null && state != null && !state.isEmpty() ? (String)this.texts.get(className + "_" + state + "_name") : null;
   }

   public int size() {
      return this.texts.size();
   }
}
