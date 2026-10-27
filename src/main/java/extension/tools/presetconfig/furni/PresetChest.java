package extension.tools.presetconfig.furni;

import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.*;
import org.json.JSONObject;

/** Preferences only: balances, contents and purchased upgrades are never transferred. */
public final class PresetChest {
   public static final String[] OPTIONS={"locked","auto_lock","capacity"};
   public static final String[] PREFERENCES={"chest_name","chest_desc","everyone_can_open","everyone_can_donate","state_control_mode","preview_mode","preview_amount","is_wired_enabled"};
   public static final String[] NOTIFICATIONS={"notify_mode","notification_chest_full","notification_donation","notification_someone_withdraws","notification_chest_empty","notification_wired_transaction"};
   public final int furniId;
   private final Map<String,String> settings;
   public PresetChest(int id, Map<String,String> values) {
      furniId=id; settings=new LinkedHashMap<>();
      for(String[] group:List.of(OPTIONS,PREFERENCES,NOTIFICATIONS)) {
         for(String key:group) if(values.containsKey(key)) settings.put(key,values.get(key));
      }
      // A complete export requires every group; missing private preferences must not be silently lost.
      for(String[] group:List.of(OPTIONS,PREFERENCES,NOTIFICATIONS)) {
         long present=Arrays.stream(group).filter(settings::containsKey).count();
         if(present!=group.length) throw new IllegalArgumentException("Incomplete chest settings group: " + Arrays.toString(group));
      }
      if(settings.isEmpty()) throw new IllegalArgumentException("Chest settings were not received");
      packets();
   }
   public PresetChest(JSONObject json) { this(json.getInt("furniId"),strings(json.getJSONObject("settings"))); }
   private static Map<String,String> strings(JSONObject j) {
      Map<String,String> values=new LinkedHashMap<>();
      for(String key:j.keySet()) values.put(key,j.getString(key));
      return values;
   }
   private int integer(String key) { return Integer.parseInt(settings.get(key)); }
   private boolean flag(String key) {
      String value=settings.get(key);
      if(!"0".equals(value) && !"1".equals(value)) throw new IllegalArgumentException("Invalid chest boolean " + key);
      return "1".equals(value);
   }
   public List<HPacket> packets() {
      List<HPacket> packets=new ArrayList<>();
      if(settings.containsKey(OPTIONS[0])) packets.add(new HPacket("SetChestOptions",HMessage.Direction.TOSERVER,furniId,flag("locked"),flag("auto_lock"),integer("capacity")));
      if(settings.containsKey(PREFERENCES[0])) packets.add(new HPacket("SetChestPreferences",HMessage.Direction.TOSERVER,furniId,settings.get("chest_name"),settings.get("chest_desc"),flag("everyone_can_open"),flag("everyone_can_donate"),integer("state_control_mode"),integer("preview_mode"),integer("preview_amount"),flag("is_wired_enabled")));
      if(settings.containsKey(NOTIFICATIONS[0])) packets.add(new HPacket("SetChestNotificationPreferences",HMessage.Direction.TOSERVER,furniId,integer("notify_mode"),flag("notification_chest_full"),flag("notification_donation"),flag("notification_someone_withdraws"),flag("notification_chest_empty"),flag("notification_wired_transaction")));
      return packets;
   }
   public boolean matches(Map<String,String> actual) { return settings.entrySet().stream().allMatch(e->e.getValue().equals(actual.get(e.getKey()))); }
   public PresetChest withFurniId(int id) { return new PresetChest(id,settings); }
   public JSONObject toJsonObject() { return new JSONObject().put("furniId",furniId).put("settings",new JSONObject(settings)); }
   public static boolean isChest(String name) { return name!=null && name.startsWith("wf_storage_"); }
}
