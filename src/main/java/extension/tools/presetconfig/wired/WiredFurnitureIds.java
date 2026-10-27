package extension.tools.presetconfig.wired;
import java.util.*;
/** Keep floor/wall namespaces distinct even when their native numeric IDs coincide. */
public final class WiredFurnitureIds {
   private WiredFurnitureIds() { }
   public static Map<Integer,Integer> sourceMap(List<Integer> floor,List<Integer> walls) {
      Map<Integer,Integer> ids=new LinkedHashMap<>();int next=1;
      for(int id:floor) { if(id<=0 || ids.put(id,next++)!=null) throw new IllegalArgumentException("Invalid/duplicate floor object ID " + id); }
      for(int id:walls) { if(id<=0 || ids.put(-id,-next++)!=null) throw new IllegalArgumentException("Invalid/duplicate wall object ID " + id); }
      return ids;
   }
}
