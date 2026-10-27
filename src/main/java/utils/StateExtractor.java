package utils;

import gearth.extensions.parsers.HFloorItem;

public class StateExtractor {
   public static String stateFromItem(HFloorItem floorItem) {
      if (floorItem.getUsagePolicy() < 1) {
         return null;
      } else {
         int state = floorItem.getStuff().getState();
         return state < 0 ? null : Integer.toString(state);
      }
   }
}
