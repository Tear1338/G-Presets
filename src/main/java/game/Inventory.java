package game;

import extension.logger.Logger;
import gearth.extensions.IExtension;
import gearth.extensions.parsers.HInventoryItem;
import gearth.extensions.parsers.HProductType;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import utils.Callback;

public class Inventory {
   private final Callback onInventoryStateChange;
   private final IExtension extension;
   private final Logger logger;
   private List<HInventoryItem> buffer = null;
   private Map<Integer, HInventoryItem> itemPlacements = new HashMap();
   private Map<Integer, Map<Integer, HInventoryItem>> wallItemsByType = new HashMap();
   private Map<Integer, Map<Integer, HInventoryItem>> floorItemsByType = new HashMap();
   private InventoryState state;
   private volatile boolean virtualRequest;

   public Inventory(IExtension extension, Logger logger, Callback onInventoryStateChange) {
      this.state = Inventory.InventoryState.UNAVAILABLE;
      this.virtualRequest = false;
      this.extension = extension;
      this.logger = logger;
      this.onInventoryStateChange = onInventoryStateChange;
      extension.intercept(HMessage.Direction.TOCLIENT, "FurniList", this::loadItems);
      extension.intercept(HMessage.Direction.TOCLIENT, "FurniListAddOrUpdate", (m) -> {
         HPacket packet = m.getPacket();
         int count = packet.readInteger();

         for(int i = 0; i < count; ++i) {
            HInventoryItem item = new HInventoryItem(m.getPacket());
            this.updateOrAddItem(item);
         }

      });
      extension.intercept(HMessage.Direction.TOCLIENT, "FurniListRemove", (m) -> this.removeItem(m.getPacket().readInteger()));
   }

   public InventoryState getState() {
      return this.state;
   }

   private void loadItems(HMessage hMessage) {
      if (this.virtualRequest) {
         hMessage.setBlocked(true);
      }

      boolean stateChanged = false;
      HPacket inventoryLoadPacket = hMessage.getPacket();
      int total = inventoryLoadPacket.readInteger();
      int i = inventoryLoadPacket.readInteger();
      if (i == 0) {
         this.clear();
         this.logger.log((this.itemPlacements.isEmpty() ? "Loading" : "Updating") + " inventory...", "blue");
         this.buffer = new ArrayList();
         stateChanged = true;
         this.state = Inventory.InventoryState.LOADING;
      }

      inventoryLoadPacket.resetReadIndex();
      HInventoryItem[] items = HInventoryItem.parse(inventoryLoadPacket);
      this.buffer.addAll(Arrays.asList(items));
      boolean inventoryComplete = i == total - 1;
      if (inventoryComplete) {
         this.buffer.forEach(this::updateOrAddItem);
         this.logger.log(String.format("Inventory loaded: found %d items.", this.itemPlacements.size()), "blue");
         this.buffer = null;
         stateChanged = true;
         this.virtualRequest = false;
         this.state = Inventory.InventoryState.LOADED;
      }

      if (stateChanged) {
         this.onInventoryStateChange.call();
      }

   }

   private void updateOrAddItem(HInventoryItem item) {
      if (this.state != Inventory.InventoryState.UNAVAILABLE) {
         this.itemPlacements.put(item.getPlacementId(), item);
         Map<Integer, Map<Integer, HInventoryItem>> map = item.getType() == HProductType.FloorItem ? this.floorItemsByType : this.wallItemsByType;
         if (!map.containsKey(item.getTypeId())) {
            map.put(item.getTypeId(), new HashMap());
         }

         ((Map)map.get(item.getTypeId())).put(item.getId(), item);
      }

   }

   private void removeItem(int id) {
      if (this.state == Inventory.InventoryState.LOADED) {
         HInventoryItem item = (HInventoryItem)this.itemPlacements.remove(id);
         if (item != null) {
            if (item.getType() == HProductType.FloorItem) {
               if (this.floorItemsByType.containsKey(item.getTypeId())) {
                  ((Map)this.floorItemsByType.get(item.getTypeId())).remove(item.getId());
               }
            } else if (this.wallItemsByType.containsKey(item.getTypeId())) {
               ((Map)this.wallItemsByType.get(item.getTypeId())).remove(item.getId());
            }
         }
      }

   }

   public List<HInventoryItem> getInventoryItems() {
      return new ArrayList(this.itemPlacements.values());
   }

   public List<HInventoryItem> getFloorItemsByType(int typeId) {
      Map<Integer, HInventoryItem> items = (Map)this.floorItemsByType.get(typeId);
      return items == null ? Collections.emptyList() : new ArrayList<>(items.values());
   }

   public List<HInventoryItem> getWallItemsByType(int typeId) {
      Map<Integer, HInventoryItem> items = (Map)this.wallItemsByType.get(typeId);
      return items == null ? Collections.emptyList() : new ArrayList<>(items.values());
   }

   public void clear() {
      this.buffer = null;
      this.itemPlacements.clear();
      this.floorItemsByType.clear();
      this.wallItemsByType.clear();
      this.state = Inventory.InventoryState.UNAVAILABLE;
      this.onInventoryStateChange.call();
   }

   public void requestInventory() {
      this.clear();
      this.virtualRequest = true;
      this.extension.sendToServer(new HPacket("RequestFurniInventory", HMessage.Direction.TOSERVER));
   }

   public static enum InventoryState {
      UNAVAILABLE,
      LOADING,
      LOADED;

      // $FF: synthetic method
      private static InventoryState[] $values() {
         return new InventoryState[]{UNAVAILABLE, LOADING, LOADED};
      }
   }
}
