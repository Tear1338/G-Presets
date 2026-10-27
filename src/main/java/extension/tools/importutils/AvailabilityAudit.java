package extension.tools.importutils;

import extension.tools.postconfig.*;
import extension.tools.presetconfig.*;
import furnidata.FurniDataTools;
import game.*;
import org.json.*;
import java.util.*;

/** Read-only preflight. A catalog offer is not proof of BC quota or room permission. */
public final class AvailabilityAudit {
   private AvailabilityAudit() { }
   public static String evaluate(int needed,int inventory,Boolean bc,ItemSource source) {
      if(source==ItemSource.ONLY_INVENTORY)return inventory>=needed?"AVAILABLE":"MISSING";
      if(source!=ItemSource.ONLY_BC && inventory>=needed)return "AVAILABLE";
      return bc==null?"UNKNOWN":bc?"CATALOG_OFFER":"MISSING";
   }
   public static JSONObject inspect(PresetConfig source,PostConfig options,FurniDataTools data,Inventory inventory,BCCatalog catalog,boolean roomReuse) {
      PresetConfig preset=new PresetConfig(source.toJsonObject());
      int reused=preset.applyPostConfig(new PostConfig(options)).size();
      JSONObject report=new JSONObject().put("source",options.getItemSource().toString()).put("reusedItems",reused);
      JSONArray rows=new JSONArray();report.put("items",rows);
      boolean ready=data!=null && data.isReady() && inventory!=null && inventory.getState()==Inventory.InventoryState.LOADED;
      Map<String,Integer> counts=new TreeMap<>();
      for(var item:preset.getFurniture())counts.merge("Floor|"+item.getClassName()+"|",1,Integer::sum);
      for(var item:preset.getWallFurniture())counts.merge("Wall|"+item.getClassName()+"|"+("poster".equals(item.getClassName()) && item.getState()!=null?item.getState():""),1,Integer::sum);
      for(var entry:counts.entrySet()) {
         String[] key=entry.getKey().split("\\|",-1);boolean wall=key[0].equals("Wall");String classname=key[1],variant=key[2];
         JSONObject row=new JSONObject().put("kind",key[0]).put("className",classname).put("variant",variant).put("required",entry.getValue());rows.put(row);
         if(!ready){row.put("status","UNKNOWN");continue;}
         Integer type=wall?data.getWallTypeId(classname):data.getFloorTypeId(classname);
         if(type==null){row.put("status","UNKNOWN_FURNITURE");continue;}
         int available;
         if(wall)available=(int)inventory.getWallItemsByType(type).stream().filter(i->{try{return variant.isEmpty() || variant.equals(i.getStuff().getLegacyString());}catch(RuntimeException e){return false;}}).count();
         else available=inventory.getFloorItemsByType(type).size();
         Boolean bc=null;
         if(catalog!=null && catalog.getState()==BCCatalog.CatalogState.COLLECTED) {
            if(wall)bc=catalog.getWallProduct(type,variant)!=null;
            else {var details=data.getFloorItemDetails(classname);bc=catalog.getFloorProduct(type)!=null || details!=null && details.bcOfferId!=-1;}
         }
         row.put("inventory",available).put("catalogOffer",bc==null?JSONObject.NULL:bc);
         String status=evaluate(entry.getValue(),available,bc,options.getItemSource());
         if(roomReuse && status.equals("MISSING") && !wall)status="ROOM_SELECTION_REQUIRED";
         row.put("status",status);
      }
      report.put("note","Catalog offers require active BC, sufficient quota and room rights. Reused room items must be selected and verified during import.");
      return report;
   }
   public static String text(JSONObject report) {
      StringBuilder s=new StringBuilder("Furniture availability — source: "+report.getString("source")+"\n");
      for(Object value:report.getJSONArray("items")) {JSONObject row=(JSONObject)value;s.append(row.getString("kind")).append(" · ").append(row.getString("className"));if(!row.getString("variant").isEmpty())s.append(" [").append(row.getString("variant")).append("]");s.append(" ×").append(row.getInt("required")).append(" — ").append(row.getString("status").replace('_',' '));if(row.has("inventory"))s.append(" (inventory: ").append(row.getInt("inventory")).append(")");s.append('\n');}
      return s.append("\nReused items: ").append(report.getInt("reusedItems")).append("\n\n").append(report.getString("note")).toString();
   }
}
