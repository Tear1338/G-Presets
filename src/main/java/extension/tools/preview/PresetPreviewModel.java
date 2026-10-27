package extension.tools.preview;

import extension.tools.postconfig.PostConfig;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.furni.PresetFurni;
import extension.tools.presetconfig.furni.PresetWallFurni;
import extension.tools.presetconfig.wired.WiredKind;
import java.util.*;
import java.util.function.Function;

/** Immutable, local placement plan. No game messages or inventory operations. */
public final class PresetPreviewModel {
   public record Footprint(int width,int depth) {
      public Footprint { if(width<1 || depth<1 || width>128 || depth>128) throw new IllegalArgumentException("Invalid furniture footprint"); }
   }
   public record Item(int id,String className,String name,double x,double y,double z,int rotation,
                      int width,int depth,boolean wall,boolean wired,boolean approximate,String state,String wallLocation) {
      @Override public String toString() { return (wall?"[Wall] ":wired?"[WIRED] ":"")+name; }
   }
   public final List<Item> items;
   public final int floorCount,wallCount,wiredCount,reusedCount,configurationCount,approximateCount;
   public final double minX,minY,maxX,maxY,maxZ;

   public PresetPreviewModel(PresetConfig source,PostConfig postConfig,Function<String,Footprint> footprints) {
      // applyPostConfig works on a private copy; opening the preview never edits the selected preset.
      PresetConfig effective=new PresetConfig(source.toJsonObject());
      reusedCount=effective.applyPostConfig(new PostConfig(postConfig)).size();
      List<Item> entries=new ArrayList<>();int uncertain=0;
      for(PresetFurni f:effective.getFurniture()) {
         Footprint size=footprints.apply(f.getClassName());boolean approximate=size==null;
         if(size==null)size=new Footprint(1,1);
         int rotation=Math.floorMod(f.getRotation(),8);
         int w=size.width(),d=size.depth();
         if(rotation==2 || rotation==6) { int swap=w;w=d;d=swap; }
         double x=f.getLocation().getX(),y=f.getLocation().getY(),z=f.getLocation().getZ();
         requireFinite(x,y,z);
         boolean wired=f.getClassName().startsWith("wf_") || WiredKind.fromClassName(f.getClassName())!=WiredKind.NONE;
         entries.add(new Item(f.getFurniId(),f.getClassName(),displayName(f.getFurniName(),f.getClassName()),x,y,z,rotation,w,d,false,wired,approximate,f.getState(),null));
         if(approximate)uncertain++;
      }
      for(PresetWallFurni f:effective.getWallFurniture()) {
         // Wall offsets are pixels and cannot be converted into floor tile footprints.
         double x=f.getLocation().getX(),y=f.getLocation().getY(),z=f.getLocation().getAltitude()/100.0;
         requireFinite(x,y,z);
         entries.add(new Item(f.getFurniId(),f.getClassName(),displayName(f.getFurniName(),f.getClassName()),x,y,z,0,1,1,true,false,false,f.getState(),f.getLocation().toString()));
      }
      entries.sort(Comparator.comparingDouble((Item i)->i.x()+i.y()).thenComparingDouble(Item::z).thenComparingInt(Item::id));
      items=List.copyOf(entries);approximateCount=uncertain;
      floorCount=effective.getFurniture().size();wallCount=effective.getWallFurniture().size();
      wiredCount=(int)items.stream().filter(Item::wired).count();
      configurationCount=effective.getPresetWireds().getAll().size()+effective.getContracts().size()+effective.getChests().size()+effective.getWiredLinks().size()+effective.getFurnitureLabels().size();
      minX=items.stream().mapToDouble(Item::x).min().orElse(0);
      minY=items.stream().mapToDouble(Item::y).min().orElse(0);
      maxX=items.stream().mapToDouble(i->i.x()+i.width()).max().orElse(1);
      maxY=items.stream().mapToDouble(i->i.y()+i.depth()).max().orElse(1);
      maxZ=items.stream().mapToDouble(Item::z).max().orElse(0);
   }
   private static void requireFinite(double... values) {
      for(double value:values)if(!Double.isFinite(value) || Math.abs(value)>1000000)throw new IllegalArgumentException("Invalid preview coordinate");
   }
   private static String displayName(String name,String fallback) { return name==null || name.isBlank()?fallback:name; }
   public boolean isEmpty() { return items.isEmpty() && reusedCount==0; }
}
