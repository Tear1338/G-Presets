package extension.tools.postconfig;

public enum ItemSource {
   ONLY_BC,
   PREFER_BC,
   PREFER_INVENTORY,
   ONLY_INVENTORY;

   /** Prefer modes may use both sources; ONLY modes never cross to the other source. */
   public boolean usesBC(boolean bcAvailable,boolean inventoryAvailable) {
      return this==ONLY_BC || this==PREFER_BC && bcAvailable || this==PREFER_INVENTORY && !inventoryAvailable && bcAvailable;
   }

   // $FF: synthetic method
   private static ItemSource[] $values() {
      return new ItemSource[]{ONLY_BC, PREFER_BC, PREFER_INVENTORY, ONLY_INVENTORY};
   }
}
