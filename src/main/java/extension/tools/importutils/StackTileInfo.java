package extension.tools.importutils;

import gearth.extensions.parsers.HPoint;

public class StackTileInfo {
   private int furniId;
   private HPoint location;
   private int rotation;
   private int dimension;

   public StackTileInfo(int furniId, HPoint location, int rotation, int dimension) {
      this.furniId = furniId;
      this.location = location;
      this.rotation = rotation;
      this.dimension = dimension;
   }

   public int getFurniId() {
      return this.furniId;
   }

   public HPoint getLocation() {
      return this.location;
   }

   public int getRotation() {
      return this.rotation;
   }

   public int getDimension() {
      return this.dimension;
   }
}
