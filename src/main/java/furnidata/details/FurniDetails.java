package furnidata.details;

import org.json.JSONObject;

abstract class FurniDetails {
   public final String className;
   public final String category;
   public final String name;
   public final String description;
   public final String furniline;
   public final String adUrl;
   public final String environment;
   public final int id;
   public final int revision;
   public final int offerId;
   public final int rentOfferId;
   public final boolean isBC;
   public final boolean isRare;
   public final boolean isBuyOut;
   public final boolean isRentBuyOut;
   public final boolean isExcludedDynamic;

   public FurniDetails(JSONObject jsonObject) {
      this.className = jsonObject.optString("classname", (String)null);
      this.category = jsonObject.optString("category", (String)null);
      this.name = jsonObject.optString("name", (String)null);
      this.description = jsonObject.optString("description", (String)null);
      this.furniline = jsonObject.optString("furniline", (String)null);
      this.adUrl = jsonObject.optString("adurl", (String)null);
      this.environment = jsonObject.optString("environment", (String)null);
      this.id = jsonObject.getInt("id");
      this.revision = jsonObject.getInt("revision");
      this.offerId = jsonObject.getInt("offerid");
      this.rentOfferId = jsonObject.getInt("rentofferid");
      this.isBC = jsonObject.getBoolean("bc");
      this.isRare = jsonObject.getBoolean("rare");
      this.isBuyOut = jsonObject.getBoolean("buyout");
      this.isRentBuyOut = jsonObject.getBoolean("rentbuyout");
      this.isExcludedDynamic = jsonObject.getBoolean("excludeddynamic");
   }
}
