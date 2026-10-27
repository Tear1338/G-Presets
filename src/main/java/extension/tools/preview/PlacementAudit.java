package extension.tools.preview;
import game.FloorState;
import furnidata.FurniDataTools;
import gearth.extensions.parsers.HFloorItem;
import java.util.*;
/** Advisory 2D footprint overlap, not a simulation of stacking, walkability or wall placement. */
public final class PlacementAudit {
 private PlacementAudit() {}
 public record Rect(double x,double y,int width,int depth) {}
 public record Result(Map<Integer,String> issues,String summary) {public Result{issues=Map.copyOf(issues);}}
 public static Result inspect(PresetPreviewModel model,int originX,int originY,java.util.function.BiPredicate<Integer,Integer> valid,List<Rect> occupied) {
  Map<Integer,String> issues=new HashMap<>();int outside=0,overlap=0;
  // Bound room occupancy to the supported 130x130 placement area.
  boolean[][] tiles=new boolean[130][130];
  for(Rect rect:occupied)for(int x=Math.max(0,(int)Math.floor(rect.x()));x<Math.min(130,Math.ceil(rect.x()+rect.width()));x++)for(int y=Math.max(0,(int)Math.floor(rect.y()));y<Math.min(130,Math.ceil(rect.y()+rect.depth()));y++)tiles[x][y]=true;
  for(var item:model.items) {
   if(item.wall())continue;
   boolean bad=false,collision=false;
   int left=originX+(int)item.x(),top=originY+(int)item.y();
   if(left<0 || top<0 || left+item.width()>130 || top+item.depth()>130)bad=true;
   else for(int x=left;x<left+item.width();x++)for(int y=top;y<top+item.depth();y++){if(!valid.test(x,y))bad=true;if(tiles[x][y])collision=true;}
   if(bad)outside++;if(collision)overlap++;
   if(bad || collision)issues.put(item.id(),(bad?"Outside usable floor. ":"")+(collision?"Overlaps existing room furniture (2D footprint).":""));
  }
  return new Result(issues,"Origin ("+originX+", "+originY+"): "+outside+" outside floor; "+overlap+" overlapping existing furniture. Red outlines are advisory: stacking, heights, wall offsets and walkability need in-game verification.");
 }
 public static Result inspect(PresetPreviewModel model,FloorState room,FurniDataTools data,int x,int y) {
  if(!room.inRoom())return new Result(Map.of(),"Placement unavailable: no current room.");
  List<Rect> occupied=new ArrayList<>();
  for(HFloorItem item:room.getItems()) {
   var details=data==null?null:data.getFloorItemDetails(data.getFloorItemName(item.getTypeId()));
   int w=details==null?1:Math.max(1,Math.min(128,details.xDim)),d=details==null?1:Math.max(1,Math.min(128,details.yDim));
   int rot=item.getFacing().ordinal();if(rot==2 || rot==6){int swap=w;w=d;d=swap;}
   occupied.add(new Rect(item.getTile().getX(),item.getTile().getY(),w,d));
  }
  return inspect(model,x,y,(tx,ty)->extension.tools.PresetUtils.heightFromChar(room.floorHeight(tx,ty))<256,occupied);
 }
}
