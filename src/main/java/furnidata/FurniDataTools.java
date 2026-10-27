package furnidata;

import furnidata.details.FloorItemDetails;
import furnidata.details.WallItemDetails;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.net.URLConnection;
import java.io.InputStream;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.Callback;

public class FurniDataTools {
   private static final Set<String> UNSTACKABLE_FURNI = new HashSet(Arrays.asList("fball_gate", "es_tile", "bb_patch1", "es_skating_ice", "snowb_slope", "val11_floor", "easter11_grasspatch", "queue_tile1*0", "queue_tile1*1", "queue_tile1*2", "queue_tile1*3", "queue_tile1*4", "queue_tile1*5", "queue_tile1*6", "queue_tile1*7", "queue_tile1*8", "queue_tile1*9", "hc_rllr", "sf_roller", "room_info15_roller", "lt_c26_roller", "hblooza14_track", "hblooza14_track_crr", "hblooza14_track_crl", "easter_c20_rapids", "hween12_track_crl", "hween12_track_crl", "hween12_track", "bw_water_2", "bw_water_1", "hween_c15_sdwater", "val15_water", "jungle_c16_watertile", "jungle_c16_watertrap", "thai_c21_crystalwater", "bw_nt_water_2", "hween10_pond", "sunsetcafe_c20_shallow", "val13_water", "hole", "hole2", "hole1x1", "hole3", "hole4", "hole1x1test", "pet_breeding_bear", "pet_breeding_terrier", "pet_breeding_dog", "pet_breeding_pig", "pet_breeding_cat"));
   private static Map<String, String> codeToDomainMap = new HashMap();
   private String countryCode;
   private volatile boolean isReady = false;
   private Map<String, Integer> nameToTypeidFloor = new HashMap();
   private Map<String, Integer> nameToTypeidWall = new HashMap();
   private Map<Integer, String> typeIdToNameFloor = new HashMap();
   private Map<Integer, String> typeIdToNameWall = new HashMap();
   private Map<String, FloorItemDetails> nameToFloorItems = new HashMap();
   private Map<String, WallItemDetails> nameToWallItems = new HashMap();
   private ExternalTexts externalTexts = null;

   public FurniDataTools(String host, Callback onLoadListener) {
      this.countryCode = resolveCountryCode(host);
      Thread loader = new Thread(() -> {
         try {
            this.fetch(onLoadListener);
         } catch (Exception e) {
            e.printStackTrace();
         }

      }, "gpresets-furnidata");
      loader.setDaemon(true);
      loader.start();
   }

   public static String resolveCountryCode(String endpoint) {
      if (endpoint == null || endpoint.isBlank()) throw new IllegalArgumentException("Missing Habbo host");
      String value = endpoint.trim().toLowerCase(Locale.ROOT);
      URI uri = URI.create(value.contains("://") ? value : "https://" + value);
      String host = uri.getHost();
      if (host == null) throw new IllegalArgumentException("Invalid Habbo host: " + endpoint);
      if (host.equals("sandbox.habbo.com") || host.equals("game-s2.habbo.com")) return "s2";
      for (Map.Entry<String, String> hotel : codeToDomainMap.entrySet()) {
         if (host.equals("game-" + hotel.getKey() + ".habbo.com") ||
             host.equals("www.habbo" + hotel.getValue()) || host.equals("habbo" + hotel.getValue())) return hotel.getKey();
      }
      throw new IllegalArgumentException("Unsupported Habbo host: " + host);
   }

   static String readUrl(String address) throws IOException {
      URLConnection connection = new URL(address).openConnection();
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(15000);
      try (InputStream stream = connection.getInputStream()) {
         return IOUtils.toString(stream, StandardCharsets.UTF_8);
      }
   }

   private void fetch(Callback onLoad) throws IOException {
      JSONObject object = new JSONObject(readUrl(this.furnidataUrl()));
      JSONArray floorJson = object.getJSONObject("roomitemtypes").getJSONArray("furnitype");
      floorJson.forEach((o) -> {
         JSONObject item = (JSONObject)o;
         this.nameToTypeidFloor.put(item.getString("classname"), item.getInt("id"));
         this.typeIdToNameFloor.put(item.getInt("id"), item.getString("classname"));
         this.nameToFloorItems.put(item.getString("classname"), new FloorItemDetails(item));
      });
      JSONArray wallJson = object.getJSONObject("wallitemtypes").getJSONArray("furnitype");
      wallJson.forEach((o) -> {
         JSONObject item = (JSONObject)o;
         this.nameToTypeidWall.put(item.getString("classname"), item.getInt("id"));
         this.typeIdToNameWall.put(item.getInt("id"), item.getString("classname"));
         this.nameToWallItems.put(item.getString("classname"), new WallItemDetails(item));
      });

      try {
         this.externalTexts = new ExternalTexts(this.externalTextsUrl());
      } catch (Exception e) {
         System.err.println("Failed to load external_flash_texts: " + e.getMessage());
      }

      this.isReady = true;
      onLoad.call();
   }

   private String furnidataUrl() {
      return this.countryCode.equals("s2") ? "https://sandbox.habbo.com/gamedata/furnidata_json/1" : String.format("https://www.habbo%s/gamedata/furnidata_json/1", codeToDomainMap.get(this.countryCode));
   }

   private String externalTextsUrl() {
      return this.countryCode.equals("s2") ? "https://sandbox.habbo.com/gamedata/external_flash_texts/1" : String.format("https://www.habbo%s/gamedata/external_flash_texts/1", codeToDomainMap.get(this.countryCode));
   }

   public boolean isReady() {
      return this.isReady;
   }

   public Integer getFloorTypeId(String furniName) {
      return (Integer)this.nameToTypeidFloor.get(furniName);
   }

   public Integer getWallTypeId(String furniName) {
      return (Integer)this.nameToTypeidWall.get(furniName);
   }

   public String getFloorItemName(int typeId) {
      return (String)this.typeIdToNameFloor.get(typeId);
   }

   public String getWallItemName(int typeId) {
      return (String)this.typeIdToNameWall.get(typeId);
   }

   public FloorItemDetails getFloorItemDetails(String furniName) {
      return (FloorItemDetails)this.nameToFloorItems.get(furniName);
   }

   public WallItemDetails getWallItemDetails(String furniName) {
      return (WallItemDetails)this.nameToWallItems.get(furniName);
   }

   public boolean isStackable(String furniName) {
      return !UNSTACKABLE_FURNI.contains(furniName);
   }

   public ExternalTexts getExternalTexts() {
      return this.externalTexts;
   }

   static {
      codeToDomainMap.put("br", ".com.br");
      codeToDomainMap.put("de", ".de");
      codeToDomainMap.put("es", ".es");
      codeToDomainMap.put("fi", ".fi");
      codeToDomainMap.put("fr", ".fr");
      codeToDomainMap.put("it", ".it");
      codeToDomainMap.put("nl", ".nl");
      codeToDomainMap.put("tr", ".com.tr");
      codeToDomainMap.put("us", ".com");
   }
}
