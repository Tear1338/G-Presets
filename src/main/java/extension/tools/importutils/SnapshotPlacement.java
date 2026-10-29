package extension.tools.importutils;

import extension.tools.presetconfig.binding.PresetWiredFurniBinding;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.HPoint;

/** Translate stored snapshot coordinates once; preserve current coordinates for rotation-only captures. */
public final class SnapshotPlacement {
   private SnapshotPlacement() { }
   public static FurniMoveInfo target(int id,PresetWiredFurniBinding binding,HFloorItem current,HPoint root,double zDelta,boolean stack) {
      int x=binding.getLocation()==null?current.getTile().getX():Math.addExact(binding.getLocation().getX(),root.getX());
      int y=binding.getLocation()==null?current.getTile().getY():Math.addExact(binding.getLocation().getY(),root.getY());
      int rotation=binding.getRotation()==null?current.getFacing().ordinal():binding.getRotation();
      Integer height=binding.getAltitude()==null?null:Math.addExact(binding.getAltitude(),(int)Math.round(zDelta*100));
      return new FurniMoveInfo(id,x,y,rotation,height,stack);
   }
   public static FurniMoveInfo restore(HFloorItem current,boolean stack) {
      return new FurniMoveInfo(current.getId(),current.getTile().getX(),current.getTile().getY(),current.getFacing().ordinal(),(int)Math.round(current.getTile().getZ()*100),stack);
   }
}
