package tests;

import extension.tools.preview.*;
import extension.tools.presetconfig.*;
import extension.tools.presetconfig.furni.*;
import extension.tools.presetconfig.wired.*;
import extension.tools.postconfig.PostConfig;
import gearth.extensions.parsers.HPoint;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import org.json.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Exercise actual JavaFX fitting and sprite placement with tall, rectangular and overhanging assets. */
public final class PreviewRenderingTests {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 private static Object call(Object owner,String name)throws Exception{var m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(owner);}
 private static double number(Object owner,String name)throws Exception{return ((Number)call(owner,name)).doubleValue();}
 public static void run()throws Exception {
  String[] names={"gpresets_anchor_tree","gpresets_anchor_sand","gpresets_anchor_water","gpresets_anchor_stage"};
  int[][] dimensions={{82,163,41,146,1,1},{129,70,64,21,2,2},{209,105,104,37,2,2},{98,59,65,26,1,2}};
  Map<String,FurnitureAssetGeometry> geometries=new HashMap<>();var cache=FurnitureSprites.shared();
  for(int i=0;i<names.length;i++) {
   int[] d=dimensions[i];byte[] geometryBytes=PreviewGeometryTests.fixture(names[i],d[0],d[1],d[2],d[3],i==1 || i==2,true,true);
   var geometryCache=java.nio.file.Path.of(PresetConfigUtils.baseDir(),"cache","geometry-v1",names[i]+"-1.swf");java.nio.file.Files.createDirectories(geometryCache.getParent());java.nio.file.Files.write(geometryCache,geometryBytes);
   var loaded=FurnitureGeometryStore.load(names[i],1);check(loaded==FurnitureGeometryStore.load(names[i],1),"duplicate geometry requests share one background operation");
   geometries.put(names[i],loaded.get(5,TimeUnit.SECONDS));
   var png=new java.awt.image.BufferedImage(d[0],d[1],java.awt.image.BufferedImage.TYPE_INT_ARGB);
   for(int y=0;y<d[1];y++)for(int x=d[0]/2-2;x<=d[0]/2+2;x++)png.setRGB(x,y,0xffdf593d);
   for(int direction:new int[]{0,2,4,6}) {
    var key=new FurnitureSpriteStore.Key(names[i],direction,"0");java.nio.file.Files.createDirectories(cache.cacheFile(key).getParent());javax.imageio.ImageIO.write(png,"png",cache.cacheFile(key).toFile());cache.load(key).get(5,TimeUnit.SECONDS);
   }
  }
  List<PresetFurni> furniture=new ArrayList<>();
  for(int i=0;i<names.length;i++){var f=new PresetFurni(i+1,names[i],new HPoint(i*3,i*2,i==3?1:0),0,"0");f.setFurniName(names[i]);furniture.add(f);}
  var upper=new PresetFurni(5,names[0],new HPoint(0,0,2),0,"0");upper.setFurniName("stacked tree");furniture.add(upper);
  var preset=new PresetConfig(furniture,new ArrayList<>(),new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray())),new ArrayList<>(),new ArrayList<>());
  var model=new PresetPreviewModel(preset,new PostConfig(),name->{int i=List.of(names).indexOf(name);return new PresetPreviewModel.Footprint(dimensions[i][4],dimensions[i][5]);});
  var pane=new PresetPreviewPane(model,"Geometry regression",null,null);new Scene(pane,1040,720);pane.applyCss();pane.layout();
  ((Map<String,FurnitureAssetGeometry>)field(pane,"geometry")).putAll(geometries);
  var draw=PresetPreviewPane.class.getDeclaredMethod("draw");draw.setAccessible(true);
  var boxMethod=PresetPreviewPane.class.getDeclaredMethod("spriteBox",PresetPreviewModel.Item.class,Image.class);boxMethod.setAccessible(true);
  var turn=(javafx.scene.control.Button)pane.lookup("#turnView");
  for(int quarter=0;quarter<4;quarter++) {
   draw.invoke(pane);double scale=((Number)field(pane,"scale")).doubleValue(),ox=((Number)field(pane,"offsetX")).doubleValue(),oy=((Number)field(pane,"offsetY")).doubleValue();
   for(var item:model.items) {
    Image image=cache.peek(new FurnitureSpriteStore.Key(item.className(),quarter*2,"0"));Object box=boxMethod.invoke(pane,item,image);
    check((Boolean)call(box,"exact"),"actual pane must use confirmed native anchor");
    var origin=PreviewGeometry.assetOrigin(item.x(),item.y(),item.z(),item.width(),item.depth(),quarter,true);
    var anchor=geometries.get(item.className()).anchor(quarter*2,0,(int)image.getWidth(),(int)image.getHeight()).orElseThrow();
    check(Math.abs(number(box,"left")-(origin.x()-anchor.x()))<0.001 && Math.abs(number(box,"top")-(origin.y()-anchor.y()))<0.001,"actual pane sprite placement must retain native origin at every camera rotation");
    double left=ox+number(box,"left")*scale,top=oy+number(box,"top")*scale;
    check(left>=31 && top>=31 && left+number(box,"width")*scale<=pane.getCanvas().getWidth()-31 && top+number(box,"height")*scale<=pane.getCanvas().getHeight()-31,"fit includes the complete native image bounds");
   }
   turn.fire();
  }
  draw.invoke(pane);var hits=(List<?>)field(pane,"hits");
  for(Object hit:hits)if(call(hit,"sprite")!=null) {
   var contains=PresetPreviewPane.class.getDeclaredMethod("contains",hit.getClass(),double.class,double.class);contains.setAccessible(true);double[] x=(double[])call(hit,"x"),y=(double[])call(hit,"y");
   check(!(Boolean)contains.invoke(null,hit,x[0]+0.01,y[0]+0.01),"transparent sprite margins must not steal clicks");
   check((Boolean)contains.invoke(null,hit,(x[0]+x[1])/2,(y[0]+y[2])/2),"visible sprite pixels remain selectable");
  }
  javax.imageio.ImageIO.write(toImage(pane.getCanvas().snapshot(null,null)),"png",new java.io.File("target/preview-geometry-ui-smoke.png"));pane.close();
  System.out.println("PASS: actual preview native anchors, complete image bounds, four camera rotations, height and transparent click selection");
 }
 private static java.awt.image.BufferedImage toImage(javafx.scene.image.WritableImage source){var result=new java.awt.image.BufferedImage((int)source.getWidth(),(int)source.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<result.getHeight();y++)for(int x=0;x<result.getWidth();x++)result.setRGB(x,y,source.getPixelReader().getArgb(x,y));return result;}
}
