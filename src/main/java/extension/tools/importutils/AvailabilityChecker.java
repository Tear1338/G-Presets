package extension.tools.importutils;

import extension.logger.Logger;
import extension.tools.postconfig.ItemSource;
import extension.tools.presetconfig.furni.PresetWallFurni;
import furnidata.ExternalTexts;
import furnidata.FurniDataTools;
import furnidata.details.FloorItemDetails;
import furnidata.details.WallItemDetails;
import game.BCCatalog;
import game.FloorState;
import game.Inventory;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.HInventoryItem;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AvailabilityChecker {
   public static Map<String, Integer> missingItems(List<FurniDropInfo> furniDrops, Inventory inventory, FurniDataTools furniDataTools, FloorState floorState, boolean useRoomFurni) {
      return missingItems(furniDrops,inventory,furniDataTools,floorState,useRoomFurni,null);
   }

   public static Map<String, Integer> missingItems(List<FurniDropInfo> furniDrops, Inventory inventory, FurniDataTools furniDataTools, FloorState floorState, boolean useRoomFurni,BCCatalog catalog) {
      if (inventory.getState() == Inventory.InventoryState.LOADED && furniDataTools.isReady()) {
         Map<Integer, Integer> usedInventorySpots = new HashMap();
         Map<Integer, Integer> missingItemCounts = new HashMap();

         for(FurniDropInfo furniDropInfo : furniDrops) {
            int typeId = furniDropInfo.getTypeId();
            usedInventorySpots.putIfAbsent(typeId, 0);
            ItemSource src = furniDropInfo.getItemSource();
            List<HInventoryItem> invItems = inventory.getFloorItemsByType(typeId);
            FloorItemDetails floorItemDetails = furniDataTools.getFloorItemDetails(furniDataTools.getFloorItemName(typeId));
            boolean bcAvailable=(catalog!=null && catalog.getFloorProduct(typeId)!=null) || (floorItemDetails!=null && floorItemDetails.bcOfferId!=-1);
            boolean isMissing = false;
            boolean useInventorySpace = false;
            if (src == ItemSource.ONLY_BC) {
               if (!bcAvailable) {
                  isMissing = true;
               }
            } else if (src == ItemSource.PREFER_BC) {
               if (!bcAvailable) {
                  if ((Integer)usedInventorySpots.get(typeId) < invItems.size()) {
                     useInventorySpace = true;
                  } else {
                     isMissing = true;
                  }
               }
            } else if (src == ItemSource.PREFER_INVENTORY) {
               if ((Integer)usedInventorySpots.get(typeId) < invItems.size()) {
                  useInventorySpace = true;
               } else {
                  isMissing = !bcAvailable;
               }
            } else if (src == ItemSource.ONLY_INVENTORY) {
               if ((Integer)usedInventorySpots.get(typeId) < invItems.size()) {
                  useInventorySpace = true;
               } else {
                  isMissing = true;
               }
            }

            if (isMissing && useRoomFurni) {
               List<HFloorItem> itemsInFloor = floorState.getItemsFromType(typeId);
               long roomItemCount = itemsInFloor.stream().filter((furni) -> furni.getTile().getX() != furniDropInfo.getX() || furni.getTile().getY() != furniDropInfo.getY()).count();
               if ((long)(Integer)usedInventorySpots.get(typeId) < roomItemCount) {
                  useInventorySpace = true;
                  isMissing = false;
               }
            }

            if (isMissing) {
               missingItemCounts.putIfAbsent(typeId, 0);
               missingItemCounts.put(typeId, (Integer)missingItemCounts.get(typeId) + 1);
            }

            if (useInventorySpace) {
               usedInventorySpots.put(typeId, (Integer)usedInventorySpots.get(typeId) + 1);
            }
         }

         Map<String, Integer> missingItemCountsByName = new HashMap();

         for(int typeId : missingItemCounts.keySet()) {
            String className = furniDataTools.getFloorItemName(typeId);
            missingItemCountsByName.put(className, (Integer)missingItemCounts.get(typeId));
         }

         return missingItemCountsByName;
      } else {
         return null;
      }
   }

   public static void printAvailability(Logger logger, List<FurniDropInfo> furniDrops, Inventory inventory, FurniDataTools furniDataTools, FloorState floorState, boolean useRoomFurni) {
      Map<String, Integer> missing = missingItems(furniDrops, inventory, furniDataTools, floorState, useRoomFurni);
      if (inventory.getState() == Inventory.InventoryState.LOADED && furniDataTools.isReady() && missing != null) {
         List<String> allItems = (List)furniDrops.stream().map((i) -> furniDataTools.getFloorItemName(i.getTypeId())).sorted().distinct().collect(Collectors.toList());
         logger.log("Required furniture: ", "black");

         for(String className : allItems) {
            boolean isMissing = missing.containsKey(className) && (Integer)missing.get(className) > 0;
            int totalNeeded = (int)furniDrops.stream().filter((i) -> furniDataTools.getFloorItemName(i.getTypeId()).equals(className)).count();
            int available = isMissing ? totalNeeded - (Integer)missing.get(className) : totalNeeded;
            logger.logNoNewline(String.format("* %s ", furniDataTools.getFloorItemDetails(className).name), "black");
            logger.log(String.format("(%d/%d)", available, totalNeeded), isMissing ? "red" : "green");
         }
      } else {
         logger.log("Availability check failed, check if everything is loaded", "red");
      }

   }

   public static void printWallItemAvailability(Logger logger, List<PresetWallFurni> wallFurniture, Inventory inventory, FurniDataTools furniDataTools, BCCatalog catalog, ItemSource itemSource) {
      if (wallFurniture != null && !wallFurniture.isEmpty()) {
         if (furniDataTools.isReady()) {
            Map<String, Integer> requiredCounts = new LinkedHashMap();

            for(PresetWallFurni wf : wallFurniture) {
               boolean isPoster = "poster".equals(wf.getClassName());
               String stateKey = isPoster && wf.getState() != null ? wf.getState() : "";
               String key = wf.getClassName() + "|" + stateKey;
               requiredCounts.merge(key, 1, Integer::sum);
            }

            logger.log("Required wall items: ", "black");

            for(Map.Entry<String, Integer> entry : requiredCounts.entrySet()) {
               String[] parts = ((String)entry.getKey()).split("\\|", -1);
               String className = parts[0];
               String state = parts.length > 1 ? parts[1] : "";
               int totalNeeded = (Integer)entry.getValue();
               Integer typeId = furniDataTools.getWallTypeId(className);
               if (typeId == null) {
                  logger.logNoNewline(String.format("* %s ", className), "black");
                  logger.log(String.format("(0/%d) - unknown", totalNeeded), "red");
               } else {
                  WallItemDetails details = furniDataTools.getWallItemDetails(className);
                  String displayName = details != null && details.name != null ? details.name : className;
                  ExternalTexts extTexts = furniDataTools.getExternalTexts();
                  if (extTexts != null && !state.isEmpty()) {
                     String variantName = extTexts.getWallItemName(className, state);
                     if (variantName != null) {
                        displayName = variantName;
                     }
                  }

                  boolean isPoster = "poster".equals(className);
                  int invAvailable = 0;
                  if (inventory.getState() == Inventory.InventoryState.LOADED) {
                     List<HInventoryItem> invItems = inventory.getWallItemsByType(typeId);
                     if (isPoster && !state.isEmpty()) {
                        invAvailable = (int)invItems.stream().filter((item) -> {
                           try {
                              String itemState = item.getStuff() != null ? item.getStuff().getLegacyString() : "";
                              return state.equals(itemState);
                           } catch (Exception var3) {
                              return false;
                           }
                        }).count();
                     } else {
                        invAvailable = invItems.size();
                     }
                  }

                  boolean bcAvailable = false;
                  if (catalog != null && catalog.getState() == BCCatalog.CatalogState.COLLECTED) {
                     bcAvailable = catalog.getWallProduct(typeId, state) != null;
                  } else if (details != null && details.isBC) {
                     bcAvailable = true;
                  }

                  int available;
                  boolean isMissing;
                  if (itemSource == ItemSource.ONLY_BC) {
                     available = bcAvailable ? totalNeeded : 0;
                     isMissing = !bcAvailable;
                  } else if (itemSource == ItemSource.ONLY_INVENTORY) {
                     available = Math.min(invAvailable, totalNeeded);
                     isMissing = invAvailable < totalNeeded;
                  } else if (itemSource == ItemSource.PREFER_BC) {
                     available = bcAvailable ? totalNeeded : Math.min(invAvailable, totalNeeded);
                     isMissing = !bcAvailable && invAvailable < totalNeeded;
                  } else {
                     available = Math.min(invAvailable, totalNeeded);
                     if (available < totalNeeded && bcAvailable) {
                        available = totalNeeded;
                     }

                     isMissing = invAvailable < totalNeeded && !bcAvailable;
                  }

                  logger.logNoNewline(String.format("* %s ", displayName), "black");
                  logger.log(String.format("(%d/%d)", available, totalNeeded), isMissing ? "red" : "green");
               }
            }

         }
      }
   }
}
