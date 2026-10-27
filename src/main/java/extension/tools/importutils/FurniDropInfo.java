package extension.tools.importutils;

import extension.tools.postconfig.ItemSource;

public class FurniDropInfo {
   private int x;
   private int y;
   private int typeId;
   private ItemSource itemSource;
   private int rotation;
   private int furniId;

   public FurniDropInfo(int x, int y, int typeId, ItemSource itemSource, int rotation) {
      this(x, y, typeId, itemSource, rotation, -1);
   }

   public FurniDropInfo(int x, int y, int typeId, ItemSource itemSource, int rotation, int furniId) {
      this.x = x;
      this.y = y;
      this.typeId = typeId;
      this.itemSource = itemSource;
      this.rotation = rotation;
      this.furniId = furniId;
   }

   public int getX() {
      return this.x;
   }

   public int getY() {
      return this.y;
   }

   public int getTypeId() {
      return this.typeId;
   }

   public ItemSource getItemSource() {
      return this.itemSource;
   }

   public int getRotation() {
      return this.rotation;
   }

   public int getFurniId() {
      return this.furniId;
   }
}
