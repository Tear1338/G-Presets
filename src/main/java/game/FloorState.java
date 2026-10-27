package game;

import extension.logger.Logger;
import furnidata.FurniDataTools;
import gearth.extensions.ExtensionBase;
import gearth.extensions.IExtension;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.HPoint;
import gearth.extensions.parsers.HWallItem;
import gearth.extensions.parsers.stuffdata.IStuffData;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import utils.Callback;

public class FloorState {
   private final Object lock = new Object();
   private Callback onFloorStateChange;
   private Callback onRoomLeave;
   private Logger logger;
   private long latestRequestTimestamp = -1L;
   private volatile int roomId;
   private volatile int[][] heightmap = null;
   private volatile Map<Integer, HFloorItem> furniIdToItem = null;
   private volatile Map<Integer, Set<HFloorItem>> typeIdToItems = null;
   private volatile List<List<Map<Integer, HFloorItem>>> furnimap = null;
   private volatile char[][] floorplan = null;
   private volatile String rawFloorplan = null;
   private volatile int floorScale = 0;
   private volatile int floorWallHeight = 0;
   private volatile String roomModelName = null;
   private volatile Map<Integer, HWallItem> wallIdToItem = null;
   private volatile Map<Integer, Set<Consumer<HFloorItem>>> stateUpdateListeners = new HashMap();

   public FloorState(IExtension extension, Logger logger, Callback onFloorItemsChange, Callback onRoomLeave) {
      this.logger = logger;
      this.onFloorStateChange = onFloorItemsChange;
      this.onRoomLeave = onRoomLeave;
      extension.intercept(HMessage.Direction.TOCLIENT, "Objects", this::parseFloorItems);
      extension.intercept(HMessage.Direction.TOCLIENT, "ObjectAdd", this::onObjectAdd);
      extension.intercept(HMessage.Direction.TOCLIENT, "ObjectRemove", this::onObjectRemove);
      extension.intercept(HMessage.Direction.TOCLIENT, "ObjectUpdate", this::onObjectUpdate);
      extension.intercept(HMessage.Direction.TOCLIENT, "SlideObjectBundle", this::onSlide);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredFurniMove", this::onFurniMove);
      extension.intercept(HMessage.Direction.TOCLIENT, "WiredMovements", this::onWiredMovements);
      extension.intercept(HMessage.Direction.TOCLIENT, "ObjectDataUpdate", this::onDataUpdate);
      extension.intercept(HMessage.Direction.TOCLIENT, "ObjectsDataUpdate", this::onDataUpdates);
      extension.intercept(HMessage.Direction.TOCLIENT, "Items", this::parseWallItems);
      extension.intercept(HMessage.Direction.TOCLIENT, "ItemAdd", this::onWallItemAdd);
      extension.intercept(HMessage.Direction.TOCLIENT, "ItemRemove", this::onWallItemRemove);
      extension.intercept(HMessage.Direction.TOCLIENT, "ItemUpdate", this::onWallItemUpdate);
      extension.intercept(HMessage.Direction.TOCLIENT, "ItemDataUpdate", this::onWallDataUpdate);
      extension.intercept(HMessage.Direction.TOCLIENT, "HeightMap", this::parseHeightmap);
      extension.intercept(HMessage.Direction.TOCLIENT, "HeightMapUpdate", this::heightmapUpdate);
      extension.intercept(HMessage.Direction.TOCLIENT, "FloorHeightMap", this::parseFloorPlan);
      extension.intercept(HMessage.Direction.TOCLIENT, "RoomEntryInfo", this::roomEntryInfo);
      extension.intercept(HMessage.Direction.TOCLIENT, "CloseConnection", (m) -> this.reset());
      extension.intercept(HMessage.Direction.TOSERVER, "Quit", (m) -> this.reset());
      extension.intercept(HMessage.Direction.TOCLIENT, "RoomReady", this::onRoomReady);
   }

   private void parseFloorPlan(HMessage hMessage) {
      synchronized(this.lock) {
         HPacket packet = hMessage.getPacket();
         int scale = packet.readByte();
         int wallHeight = packet.readInteger();
         String raw = packet.readString();
         String[] split = raw.split("\r");
         this.floorplan = new char[split[0].length()][];

         for(int x = 0; x < split[0].length(); ++x) {
            this.floorplan[x] = new char[split.length];

            for(int y = 0; y < split.length; ++y) {
               this.floorplan[x][y] = split[y].charAt(x);
            }
         }

         this.rawFloorplan = raw;
         this.floorScale = scale;
         this.floorWallHeight = wallHeight;
      }

      this.logger.log("Parsed floorplan", "blue");
   }

   private void onRoomReady(HMessage hMessage) {
      String model = null;

      try {
         HPacket pk = hMessage.getPacket();
         int saved = pk.getReadIndex();

         try {
            model = pk.readString();
         } finally {
            pk.setReadIndex(saved);
         }
      } catch (Exception var12) {
      }

      this.reset();
      synchronized(this.lock) {
         this.roomModelName = model;
      }
   }

   private void roomEntryInfo(HMessage hMessage) {
      synchronized(this.lock) {
         this.roomId = hMessage.getPacket().readInteger();
      }

      if (this.latestRequestTimestamp > System.currentTimeMillis() - 400L) {
         hMessage.setBlocked(true);
         this.latestRequestTimestamp = -1L;
      }

      this.onFloorStateChange.call();
   }

   public boolean inRoom() {
      return this.furnimap != null && this.wallIdToItem != null && this.floorplan != null && this.heightmap != null && this.roomId != 0;
   }

   public void reset() {
      if (this.heightmap != null || this.furnimap != null || this.floorplan != null || this.wallIdToItem != null) {
         synchronized(this.lock) {
            this.heightmap = null;
            this.furniIdToItem = null;
            this.furnimap = null;
            this.floorplan = null;
            this.typeIdToItems = null;
            this.wallIdToItem = null;
            this.roomId = 0;
            this.rawFloorplan = null;
            this.floorScale = 0;
            this.floorWallHeight = 0;
            this.roomModelName = null;
         }

         this.onRoomLeave.call();
         this.onFloorStateChange.call();
      }

   }

   private void parseHeightmap(HMessage hMessage) {
      HPacket packet = hMessage.getPacket();
      int columns = packet.readInteger();
      int tiles = packet.readInteger();
      int rows = tiles / columns;
      int[][] heightmap = new int[columns][];

      for(int col = 0; col < columns; ++col) {
         heightmap[col] = new int[rows];
      }

      for(int row = 0; row < rows; ++row) {
         for(int col = 0; col < columns; ++col) {
            heightmap[col][row] = packet.readUshort();
         }
      }

      synchronized(this.lock) {
         this.heightmap = heightmap;
      }

      this.logger.log("Parsed heightmap", "blue");
   }

   private void heightmapUpdate(HMessage hMessage) {
      if (this.heightmap != null) {
         HPacket packet = hMessage.getPacket();
         int updates = packet.readByte();

         for(int i = 0; i < updates; ++i) {
            int x = packet.readByte();
            int y = packet.readByte();
            int height = packet.readUshort();
            this.heightmap[x][y] = height;
         }
      }

   }

   private void parseFloorItems(HMessage hMessage) {
      HFloorItem[] floorItems = HFloorItem.parse(hMessage.getPacket());
      synchronized(this.lock) {
         if (this.furnimap == null) {
            this.furniIdToItem = new HashMap();
            this.furnimap = new ArrayList();
            this.typeIdToItems = new HashMap();

            for(int i = 0; i < 130; ++i) {
               this.furnimap.add(new ArrayList());

               for(int j = 0; j < 130; ++j) {
                  ((List)this.furnimap.get(i)).add(new HashMap());
               }
            }
         }

         Arrays.stream(floorItems).forEach(this::addObject);
      }

      this.onFloorStateChange.call();
      this.logger.log("Parsed floor items", "blue");
   }

   private void onObjectRemove(HMessage hMessage) {
      if (this.inRoom()) {
         HPacket packet = hMessage.getPacket();
         int furniid = Integer.parseInt(packet.readString());
         this.removeObject(furniid);
         this.onFloorStateChange.call();
      }

   }

   private void removeObject(int furniId) {
      synchronized(this.lock) {
         HFloorItem item = (HFloorItem)this.furniIdToItem.remove(furniId);
         if (item != null) {
            ((Map)((List)this.furnimap.get(item.getTile().getX())).get(item.getTile().getY())).remove(item.getId());
            ((Set)this.typeIdToItems.get(item.getTypeId())).remove(item);
         }

      }
   }

   private void onObjectAdd(HMessage hMessage) {
      if (this.inRoom()) {
         this.addObject(hMessage.getPacket(), (String)null);
         this.onFloorStateChange.call();
      }

   }

   private void addObject(HPacket packet, String ownerName) {
      synchronized(this.lock) {
         HFloorItem item = new HFloorItem(packet);
         if (ownerName == null) {
            ownerName = packet.readString();
         }

         item.setOwnerName(ownerName);
         this.addObject(item);
      }
   }

   private void addObject(HFloorItem item) {
      ((Map)((List)this.furnimap.get(item.getTile().getX())).get(item.getTile().getY())).put(item.getId(), item);
      this.furniIdToItem.put(item.getId(), item);
      if (!this.typeIdToItems.containsKey(item.getTypeId())) {
         this.typeIdToItems.put(item.getTypeId(), new HashSet());
      }

      ((Set)this.typeIdToItems.get(item.getTypeId())).add(item);
   }

   private void onObjectUpdate(HMessage hMessage) {
      if (this.inRoom()) {
         HFloorItem newItem = new HFloorItem(hMessage.getPacket());
         HFloorItem old = (HFloorItem)this.furniIdToItem.get(newItem.getId());
         String owner = "";
         if (old != null) {
            owner = old.getOwnerName();
         }

         this.removeObject(newItem.getId());
         hMessage.getPacket().resetReadIndex();
         this.addObject(hMessage.getPacket(), owner);
      }

   }

   private void onSlide(HMessage hMessage) {
      if (this.inRoom()) {
         HPacket packet = hMessage.getPacket();
         int oldX = packet.readInteger();
         int oldY = packet.readInteger();
         int newX = packet.readInteger();
         int newY = packet.readInteger();
         int amount = packet.readInteger();
         synchronized(this.lock) {
            for(int i = 0; i < amount; ++i) {
               int furniId = packet.readInteger();
               String oldZ = packet.readString();
               String newZ = packet.readString();
               this.updateFurniPosition(furniId, newX, newY, newZ);
            }
         }
      }

   }

   private void onFurniMove(HMessage hMessage) {
      if (this.inRoom()) {
         HPacket packet = hMessage.getPacket();
         synchronized(this.lock) {
            int oldX = packet.readInteger();
            int oldY = packet.readInteger();
            int newX = packet.readInteger();
            int newY = packet.readInteger();
            String oldZ = packet.readString();
            String newZ = packet.readString();
            int furniId = packet.readInteger();
            this.updateFurniPosition(furniId, newX, newY, newZ);
         }
      }

   }

   private void updateFurniPosition(int furniId, int newX, int newY, String newZ) {
      HFloorItem item = (HFloorItem)this.furniIdToItem.get(furniId);
      if (item != null) {
         ((Map)((List)this.furnimap.get(item.getTile().getX())).get(item.getTile().getY())).remove(item.getId());
         item.setTile(new HPoint(newX, newY, Double.parseDouble(newZ)));
         ((Map)((List)this.furnimap.get(newX)).get(newY)).put(item.getId(), item);
      }

   }

   private void onWiredMovements(HMessage hMessage) {
      if (this.inRoom()) {
         HPacket packet = hMessage.getPacket();
         synchronized(this.lock) {
            int count = packet.readInteger();

            for(int i = 0; i < count; ++i) {
               switch (packet.readInteger()) {
                  case 0:
                     packet.skip("iiiissiiiii");
                     if (packet.readBoolean()) {
                        packet.skip("i");
                     }
                     break;
                  case 1:
                     packet.skip("ii");
                     int newX = packet.readInteger();
                     int newY = packet.readInteger();
                     packet.skip("s");
                     String newZ = packet.readString();
                     int furniId = packet.readInteger();
                     packet.skip("ii");
                     if (packet.readBoolean()) {
                        packet.skip("i");
                     }

                     if (packet.readBoolean()) {
                        packet.skip("i");
                     }

                     this.updateFurniPosition(furniId, newX, newY, newZ);
                     break;
                  case 2:
                     packet.skip("iBiiiiiiiii");
                     break;
                  case 3:
                     packet.skip("iii");
               }
            }
         }
      }

   }

   private void onDataUpdate(HPacket hPacket, int id) {
      IStuffData stuff = IStuffData.read(hPacket);
      HFloorItem item = null;
      List<Consumer<HFloorItem>> listeners = null;
      synchronized(this.lock) {
         if (this.inRoom() && this.furniIdToItem.containsKey(id)) {
            item = (HFloorItem)this.furniIdToItem.get(id);
            item.setStuff(stuff);
            if (this.stateUpdateListeners.containsKey(id)) {
               listeners = new ArrayList((Collection)this.stateUpdateListeners.get(id));
            }
         }
      }

      if (listeners != null) {
         for(Consumer<HFloorItem> l : listeners) {
            l.accept(item);
         }
      }

   }

   private void onDataUpdates(HMessage hMessage) {
      HPacket packet = hMessage.getPacket();
      int updatesCount = packet.readInteger();

      for(int i = 0; i < updatesCount; ++i) {
         int id = packet.readInteger();
         this.onDataUpdate(packet, id);
      }

   }

   private void onDataUpdate(HMessage hMessage) {
      HPacket packet = hMessage.getPacket();
      this.onDataUpdate(hMessage.getPacket(), Integer.parseInt(packet.readString()));
   }

   public void requestRoom(ExtensionBase ext) {
      this.latestRequestTimestamp = System.currentTimeMillis();
      ext.sendToServer(new HPacket("GetHeightMap", HMessage.Direction.TOSERVER));
   }

   public HFloorItem furniFromId(int id) {
      synchronized(this.lock) {
         return (HFloorItem)this.furniIdToItem.get(id);
      }
   }

   public List<HFloorItem> getFurniOnTile(int x, int y) {
      synchronized(this.lock) {
         if (this.inRoom()) {
            return new ArrayList(((Map)((List)this.furnimap.get(x)).get(y)).values());
         }
      }

      return new ArrayList();
   }

   public double getTileHeight(int x, int y) {
      synchronized(this.lock) {
         return (double)(this.heightmap[x][y] & 16383) / (double)256.0F;
      }
   }

   public List<HFloorItem> getItemsFromType(FurniDataTools furniDataTools, String furniName) {
      if (furniDataTools.isReady()) {
         int typeId = furniDataTools.getFloorTypeId(furniName);
         return this.getItemsFromType(typeId);
      } else {
         return new ArrayList();
      }
   }

   public List<HFloorItem> getItemsFromType(int typeId) {
      synchronized(this.lock) {
         if (this.inRoom()) {
            Set<HFloorItem> result = (Set)this.typeIdToItems.get(typeId);
            return result == null ? new ArrayList() : new ArrayList(result);
         }
      }

      return new ArrayList();
   }

   public List<HFloorItem> getItems() {
      synchronized(this.lock) {
         if (this.inRoom()) {
            return new ArrayList(this.furniIdToItem.values());
         }
      }

      return new ArrayList();
   }

   private void parseWallItems(HMessage hMessage) {
      HWallItem[] wallItems = HWallItem.parse(hMessage.getPacket());
      synchronized(this.lock) {
         if (this.wallIdToItem == null) {
            this.wallIdToItem = new HashMap();
         }

         Arrays.stream(wallItems).forEach(this::addWallItem);
      }

      this.onFloorStateChange.call();
      this.logger.log("Parsed wall items", "blue");
   }

   private void onWallItemRemove(HMessage hMessage) {
      if (this.wallIdToItem != null) {
         HPacket packet = hMessage.getPacket();
         int itemId = Integer.parseInt(packet.readString());
         this.removeWallItem(itemId);
         this.onFloorStateChange.call();
      }

   }

   private void removeWallItem(int itemId) {
      synchronized(this.lock) {
         this.wallIdToItem.remove(itemId);
      }
   }

   private void onWallItemAdd(HMessage hMessage) {
      if (this.wallIdToItem != null) {
         synchronized(this.lock) {
            HWallItem item = new HWallItem(hMessage.getPacket());
            String ownerName = hMessage.getPacket().readString();
            item.setOwnerName(ownerName);
            this.addWallItem(item);
         }

         this.onFloorStateChange.call();
      }

   }

   private void addWallItem(HWallItem item) {
      this.wallIdToItem.put(item.getId(), item);
   }

   private void onWallItemUpdate(HMessage hMessage) {
      if (this.wallIdToItem != null) {
         HWallItem newItem = new HWallItem(hMessage.getPacket());
         HWallItem old = (HWallItem)this.wallIdToItem.get(newItem.getId());
         String owner = "";
         if (old != null) {
            owner = old.getOwnerName();
         }

         this.removeWallItem(newItem.getId());
         hMessage.getPacket().resetReadIndex();
         HWallItem item = new HWallItem(hMessage.getPacket());
         item.setOwnerName(owner);
         synchronized(this.lock) {
            this.addWallItem(item);
         }
      }

   }

   private void onWallDataUpdate(HMessage hMessage) {
      HPacket packet = hMessage.getPacket();
      int id = Integer.parseInt(packet.readString());
      synchronized(this.lock) {
         HWallItem item = this.wallIdToItem != null ? (HWallItem)this.wallIdToItem.get(id) : null;
         if (item != null) {
            item.setState(packet.readString());
         }

      }
   }

   public HWallItem wallItemFromId(int id) {
      synchronized(this.lock) {
         return this.wallIdToItem != null ? (HWallItem)this.wallIdToItem.get(id) : null;
      }
   }

   public List<HWallItem> getWallItems() {
      synchronized(this.lock) {
         if (this.wallIdToItem != null) {
            return new ArrayList(this.wallIdToItem.values());
         }
      }

      return new ArrayList();
   }

   public char floorHeight(int x, int y) {
      synchronized(this.lock) {
         char result = this.floorplan != null && x >= 0 && y >= 0 && x < this.floorplan.length && y < this.floorplan[x].length ? this.floorplan[x][y] : 120;
         return result;
      }
   }

   public int getRoomId() {
      return this.roomId;
   }

   public String getRawFloorplan() {
      return this.rawFloorplan;
   }

   public int getFloorScale() {
      return this.floorScale;
   }

   public int getFloorWallHeight() {
      return this.floorWallHeight;
   }

   public String getRoomModelName() {
      return this.roomModelName;
   }

   public int getFloorplanWidth() {
      synchronized(this.lock) {
         return this.floorplan != null ? this.floorplan.length : 0;
      }
   }

   public int getFloorplanHeight() {
      synchronized(this.lock) {
         return this.floorplan != null && this.floorplan.length > 0 ? this.floorplan[0].length : 0;
      }
   }
}
