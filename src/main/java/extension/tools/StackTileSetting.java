package extension.tools;

import java.util.Arrays;

public enum StackTileSetting {
   Small("tile_stackmagic", 1),
   Medium("tile_stackmagic1", -1),
   Large("tile_stackmagic2", 2),
   XL("tile_stackmagic4x4", 4),
   XXL("tile_stackmagic6x6", 6),
   XXXL("tile_stackmagic8x8", 8);

   private String className;
   private int dimension;

   private StackTileSetting(String className, int dimension) {
      this.className = className;
      this.dimension = dimension;
   }

   public String getClassName() {
      return this.className;
   }

   public int getDimension() {
      return this.dimension;
   }

   public static StackTileSetting fromString(String s) {
      switch (s) {
         case "1x1":
            return Small;
         case "1x2":
            return Medium;
         case "2x2":
            return Large;
         case "4x4":
            return XL;
         case "6x6":
            return XXL;
         case "8x8":
            return XXXL;
         default:
            return Large;
      }
   }

   public static StackTileSetting fromClassName(String className) {
      return (StackTileSetting)Arrays.stream(values()).filter((v) -> v.getClassName().equals(className)).findFirst().orElse(null);
   }

   public static StackTileSetting forDimension(int requiredDimension) {
      switch (requiredDimension) {
         case -1:
            return Medium;
         case 0:
         case 3:
         case 5:
         default:
            return XXXL;
         case 1:
            return Small;
         case 2:
            return Large;
         case 4:
            return XL;
         case 6:
            return XXL;
      }
   }

   public String toString() {
      return String.format("%dx%d", this.dimension, this.dimension);
   }

   // $FF: synthetic method
   private static StackTileSetting[] $values() {
      return new StackTileSetting[]{Small, Medium, Large, XL, XXL, XXXL};
   }
}
