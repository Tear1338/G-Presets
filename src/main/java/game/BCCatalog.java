package game;

import extension.GPresets;
import extension.logger.Logger;
import furnidata.FurniDataTools;
import gearth.extensions.parsers.HProductType;
import gearth.extensions.parsers.catalog.HCatalogIndex;
import gearth.extensions.parsers.catalog.HCatalogPage;
import gearth.extensions.parsers.catalog.HCatalogPageIndex;
import gearth.extensions.parsers.catalog.HProduct;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import utils.Callback;
import utils.Utils;

public class BCCatalog {
   private Logger logger;
   private Callback stateChangeCallback;
   private final GPresets extension;
   private CatalogState state;
   private final Map<Integer, SingleFurniProduct> floorTypeIdToProduct;
   private final Map<Integer, Map<String, SingleFurniProduct>> wallTypeIdToProducts;

   public BCCatalog(GPresets extension, Logger logger, Callback stateChangeCallback) {
      this.state = BCCatalog.CatalogState.NONE;
      this.floorTypeIdToProduct = new HashMap();
      this.wallTypeIdToProducts = new HashMap();
      this.extension = extension;
      this.logger = logger;
      this.stateChangeCallback = stateChangeCallback;
      extension.intercept(HMessage.Direction.TOCLIENT, "CatalogIndex", this::onCatalogIndex);
      extension.intercept(HMessage.Direction.TOSERVER, "GetCatalogPage", (m) -> {
         if (this.state == BCCatalog.CatalogState.COLLECTING_PAGES) {
            m.setBlocked(true);
         }

      });
      extension.intercept(HMessage.Direction.TOCLIENT, "CatalogPage", this::onCatalogPage);
   }

   private void onCatalogPage(HMessage hMessage) {
      if (this.state == BCCatalog.CatalogState.COLLECTING_PAGES) {
         HCatalogPage page = new HCatalogPage(hMessage.getPacket());
         page.getOffers().forEach((catalogOffer) -> {
            if (!catalogOffer.isPet() && catalogOffer.getProducts().size() == 1) {
               HProduct product = (HProduct)catalogOffer.getProducts().get(0);
               if (product.getProductType() == HProductType.FloorItem) {
                  synchronized(this.floorTypeIdToProduct) {
                     this.floorTypeIdToProduct.put(product.getFurniClassId(), new SingleFurniProduct(page.getPageId(), catalogOffer.getOfferId(), product.getExtraParam()));
                  }
               } else if (product.getProductType() == HProductType.WallItem) {
                  synchronized(this.wallTypeIdToProducts) {
                     ((Map)this.wallTypeIdToProducts.computeIfAbsent(product.getFurniClassId(), (k) -> new HashMap())).put(product.getExtraParam(), new SingleFurniProduct(page.getPageId(), catalogOffer.getOfferId(), product.getExtraParam()));
                  }
               }
            }

         });
      }

   }

   public SingleFurniProduct getFloorProduct(int typeId) {
      return (SingleFurniProduct)this.floorTypeIdToProduct.get(typeId);
   }

   public SingleFurniProduct getWallProduct(int typeId, String extraParam) {
      Map<String, SingleFurniProduct> variants = (Map)this.wallTypeIdToProducts.get(typeId);
      if (variants == null) {
         return null;
      } else {
         SingleFurniProduct product = (SingleFurniProduct)variants.get(extraParam);
         if (product != null) {
            return product;
         } else {
            return variants.size() == 1 ? (SingleFurniProduct)variants.values().iterator().next() : (SingleFurniProduct)variants.get("");
         }
      }
   }

   public SingleFurniProduct getAnyWallProduct(int typeId) {
      Map<String, SingleFurniProduct> variants = (Map)this.wallTypeIdToProducts.get(typeId);
      return variants != null && !variants.isEmpty() ? (SingleFurniProduct)variants.values().iterator().next() : null;
   }

   private void fetchPagesLoop(List<Integer> pageIds, String saveHash) {
      this.logger.log(String.format("Scraping %d BC catalog pages..", pageIds.size()), "blue");
      int i = 0;

      while(this.state == BCCatalog.CatalogState.COLLECTING_PAGES && i < pageIds.size()) {
         this.extension.sendToServer(new HPacket("GetCatalogPage", HMessage.Direction.TOSERVER, new Object[]{pageIds.get(i), -1, "BUILDERS_CLUB"}));
         Utils.sleep(180);
         ++i;
         if (i % 10 == 0) {
            this.logger.log(String.format("Scraping page %d of %d", i, pageIds.size()), "blue");
         }
      }

      if (this.state == BCCatalog.CatalogState.COLLECTING_PAGES) {
         (new Thread(() -> {
            Utils.sleep(300);
            if (this.state == BCCatalog.CatalogState.COLLECTING_PAGES) {
               this.setState(BCCatalog.CatalogState.COLLECTED);
            }

            int totalProducts = this.floorTypeIdToProduct.size() + this.wallTypeIdToProducts.values().stream().mapToInt(Map::size).sum();
            this.logger.log(String.format("Collected %d BC products (%d floor, %d wall variants)", totalProducts, this.floorTypeIdToProduct.size(), this.wallTypeIdToProducts.values().stream().mapToInt(Map::size).sum()), "blue");
            this.saveCatalog(saveHash);
         })).start();
      }

   }

   private void findRelevantPageIds(List<Integer> pageIds, HCatalogPageIndex node) {
      if (node.getPageId() != -1 && node.getOfferIds().size() > 0) {
         pageIds.add(node.getPageId());
      }

      node.getChildren().forEach((s) -> this.findRelevantPageIds(pageIds, s));
   }

   private void onCatalogIndex(HMessage hMessage) {
      if (this.state == BCCatalog.CatalogState.AWAITING_INDEX) {
         HCatalogIndex index = new HCatalogIndex(hMessage.getPacket());
         if (index.getCatalogType().equals("BUILDERS_CLUB")) {
            List<Integer> relevantPageIds = new ArrayList();
            this.findRelevantPageIds(relevantPageIds, index.getRoot());
            this.setState(BCCatalog.CatalogState.COLLECTING_PAGES);
            String hash = "" + ((String)relevantPageIds.stream().map((integer) -> "" + integer).collect(Collectors.joining(","))).hashCode();
            if (this.loadCatalog(hash)) {
               this.setState(BCCatalog.CatalogState.COLLECTED);
               int totalProducts = this.floorTypeIdToProduct.size() + this.wallTypeIdToProducts.values().stream().mapToInt(Map::size).sum();
               this.logger.log(String.format("Loaded %d BC products from cache (%d floor, %d wall variants)", totalProducts, this.floorTypeIdToProduct.size(), this.wallTypeIdToProducts.values().stream().mapToInt(Map::size).sum()), "blue");
            } else {
               (new Thread(() -> this.fetchPagesLoop(relevantPageIds, hash))).start();
            }
         }
      }

   }

   public void requestIndex() {
      this.clear();
      this.setState(BCCatalog.CatalogState.AWAITING_INDEX);
      this.extension.sendToServer(new HPacket("GetCatalogIndex", HMessage.Direction.TOSERVER, new Object[]{"BUILDERS_CLUB"}));
      this.stateChangeCallback.call();
   }

   private void setState(CatalogState state) {
      this.state = state;
      this.stateChangeCallback.call();
   }

   public void clear() {
      this.setState(BCCatalog.CatalogState.NONE);
      this.floorTypeIdToProduct.clear();
      this.wallTypeIdToProducts.clear();
      this.stateChangeCallback.call();
   }

   public CatalogState getState() {
      return this.state;
   }

   private String fileNameForHash(String hash) throws URISyntaxException {
      String path = (new File(GPresets.class.getProtectionDomain().getCodeSource().getLocation().toURI())).getParentFile().toString();
      String filename = "BC_CATALOG_" + hash + ".txt";
      File root = new File(Paths.get(path, "catalog").toString());
      if (!root.exists()) {
         root.mkdir();
      }

      return Paths.get(path, "catalog", filename).toString();
   }

   private boolean saveCatalog(String hash) {
      if (this.state == BCCatalog.CatalogState.COLLECTED && this.extension.furniDataReady()) {
         FurniDataTools furniDataTools = this.extension.getFurniDataTools();
         StringBuilder builder = new StringBuilder();

         for(Integer typeId : this.floorTypeIdToProduct.keySet()) {
            SingleFurniProduct product = (SingleFurniProduct)this.floorTypeIdToProduct.get(typeId);
            String furniName = furniDataTools.getFloorItemName(typeId);
            if (furniName != null) {
               String entry = product.extraParam.isEmpty() ? String.format("F\t%s\t%d\t%d", furniName, product.pageId, product.offerId) : String.format("F\t%s\t%d\t%d\t%s", furniName, product.pageId, product.offerId, product.extraParam);
               builder.append(entry);
               builder.append("\n");
            }
         }

         for(Integer typeId : this.wallTypeIdToProducts.keySet()) {
            Map<String, SingleFurniProduct> variants = (Map)this.wallTypeIdToProducts.get(typeId);
            String furniName = furniDataTools.getWallItemName(typeId);
            if (furniName != null) {
               for(SingleFurniProduct product : variants.values()) {
                  String entry = String.format("W\t%s\t%d\t%d\t%s", furniName, product.pageId, product.offerId, product.extraParam);
                  builder.append(entry);
                  builder.append("\n");
               }
            }
         }

         try {
            FileUtils.writeStringToFile(new File(this.fileNameForHash(hash)), builder.toString(), StandardCharsets.UTF_8);
            return true;
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      return false;
   }

   private boolean loadCatalog(String hash) {
      if (this.state == BCCatalog.CatalogState.COLLECTING_PAGES && this.extension.furniDataReady()) {
         FurniDataTools furniDataTools = this.extension.getFurniDataTools();

         try {
            File file = new File(this.fileNameForHash(hash));
            if (file.exists() && !file.isDirectory()) {
               String[] allLines = FileUtils.readFileToString(file, StandardCharsets.UTF_8).split("\n");

               for(String line : allLines) {
                  if (!line.isEmpty()) {
                     String[] fields = line.split("\t");
                     if (fields.length >= 4) {
                        String type = fields[0];
                        String name = fields[1];
                        int pageId = Integer.parseInt(fields[2]);
                        int offerId = Integer.parseInt(fields[3]);
                        String extraParam = fields.length >= 5 ? fields[4] : "";
                        if ("F".equals(type)) {
                           Integer typeId = furniDataTools.getFloorTypeId(name);
                           if (typeId != null) {
                              this.floorTypeIdToProduct.put(typeId, new SingleFurniProduct(pageId, offerId, extraParam));
                           }
                        } else if ("W".equals(type)) {
                           Integer typeId = furniDataTools.getWallTypeId(name);
                           if (typeId != null) {
                              ((Map)this.wallTypeIdToProducts.computeIfAbsent(typeId, (k) -> new HashMap())).put(extraParam, new SingleFurniProduct(pageId, offerId, extraParam));
                           }
                        }
                     }
                  }
               }

               return true;
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      return false;
   }

   public void clearCache() throws URISyntaxException {
      String path = (new File(GPresets.class.getProtectionDomain().getCodeSource().getLocation().toURI())).getParentFile().toString();
      File root = new File(Paths.get(path, "catalog").toString());
      if (root.exists() && root.listFiles() != null) {
         for(File file : root.listFiles()) {
            System.out.println(file);
            file.delete();
         }
      }

   }

   public static class SingleFurniProduct {
      private final int pageId;
      private final int offerId;
      private final String extraParam;

      SingleFurniProduct(int pageId, int offerId, String extraParam) {
         this.pageId = pageId;
         this.offerId = offerId;
         this.extraParam = extraParam;
      }

      public int getPageId() {
         return this.pageId;
      }

      public int getOfferId() {
         return this.offerId;
      }

      public String getExtraParam() {
         return this.extraParam;
      }
   }

   public static enum CatalogState {
      NONE,
      AWAITING_INDEX,
      COLLECTING_PAGES,
      COLLECTED;

      // $FF: synthetic method
      private static CatalogState[] $values() {
         return new CatalogState[]{NONE, AWAITING_INDEX, COLLECTING_PAGES, COLLECTED};
      }
   }
}
