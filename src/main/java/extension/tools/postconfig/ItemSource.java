package extension.tools.postconfig;

public enum ItemSource {
   ONLY_BC,
   PREFER_BC,
   PREFER_INVENTORY,
   ONLY_INVENTORY;

   // $FF: synthetic method
   private static ItemSource[] $values() {
      return new ItemSource[]{ONLY_BC, PREFER_BC, PREFER_INVENTORY, ONLY_INVENTORY};
   }
}
