package furnidata.details;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONObject;

public class FloorItemDetails extends FurniDetails {
   public final String customParams;
   public final int xDim;
   public final int yDim;
   public final int defaultDir;
   public final int specialType;
   public final int bcOfferId;
   public final boolean canStandOn;
   public final boolean canSitOn;
   public final boolean canLayOn;
   public final List<String> partColors;

   public FloorItemDetails(JSONObject jsonObject) {
      super(jsonObject);
      this.customParams = jsonObject.optString("customparams", (String)null);
      this.xDim = jsonObject.getInt("xdim");
      this.yDim = jsonObject.getInt("ydim");
      this.defaultDir = jsonObject.getInt("defaultdir");
      this.specialType = jsonObject.getInt("specialtype");
      this.bcOfferId = jsonObject.getInt("bcofferid");
      this.canStandOn = jsonObject.getBoolean("canstandon");
      this.canSitOn = jsonObject.getBoolean("cansiton");
      this.canLayOn = jsonObject.getBoolean("canlayon");
      this.partColors = jsonObject.has("partcolors") ? Collections.unmodifiableList((List)jsonObject.getJSONObject("partcolors").getJSONArray("color").toList().stream().map((o) -> (String)o).collect(Collectors.toList())) : null;
   }
}
