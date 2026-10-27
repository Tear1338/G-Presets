package extension.tools.preview;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.image.Image;
import javafx.application.Platform;
import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Isometric footprint preview; intentionally independent of all hotel/network APIs. */
public final class PresetPreviewPane extends BorderPane {
   private final PresetPreviewModel model;
   private final Canvas canvas=new Canvas(680,440);
   private final ListView<PresetPreviewModel.Item> items=new ListView<>();
   private final Label details=new Label("Select an item to see its coordinates.");
   private final Label status=new Label();
   private final Button launch=new Button("Continue to import");
   private final Button cancel=new Button("Cancel");
   private final Slider zoom=new Slider(0.5,4,1);
   private final CheckBox heights=new CheckBox("Show heights");
   private final List<Hit> hits=new ArrayList<>();
   private final Map<String,Image> sprites=new HashMap<>();
   private final Set<String> requestedSprites=new HashSet<>();
   private final ExecutorService spriteLoader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"gpresets-preview-images");t.setDaemon(true);return t;});
   private final CheckBox showSprites=new CheckBox("Furniture images");
   private double panX,panY,dragX,dragY,dragDistance;
   private double scale,offsetX,offsetY;
   private final Integer originX,originY;
   private record Point(double x,double y) { }
   private record Hit(PresetPreviewModel.Item item,double[] x,double[] y) { }

   public PresetPreviewPane(PresetPreviewModel model,String name,Integer originX,Integer originY) {
      this.model=model;this.originX=originX;this.originY=originY;
      cancel.setCancelButton(true);
      setStyle("-fx-background-color:#f3f5f9; -fx-font-family:'Segoe UI'; -fx-font-size:12px;");
      setPadding(new Insets(16));
      Label title=new Label("Preset preview"+(name==null?"":" \u00b7 "+name));
      title.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");
      Label counts=new Label(model.floorCount+" floor items  \u00b7  "+model.wallCount+" wall items  \u00b7  "+model.wiredCount+" WIRED  \u00b7  "+model.configurationCount+" configurations");
      String origin=originX==null?"Origin: choose in Habbo after confirmation.":"Requested origin: ("+originX+", "+originY+").";
      Label scope=new Label(origin+"  "+model.reusedCount+" existing items reused without moving.");scope.setWrapText(true);
      Label note=new Label("Furniture images: HabboAssets. Positions and wall items are approximate. Nothing is placed before confirmation.");note.setWrapText(true);note.setStyle("-fx-text-fill:#475569;");
      VBox header=new VBox(5,title,counts,scope,note);header.setPadding(new Insets(0,0,14,0));setTop(header);
      StackPane surface=new StackPane(canvas);surface.setMinSize(280,200);surface.setPrefSize(680,440);
      canvas.widthProperty().bind(surface.widthProperty());canvas.heightProperty().bind(surface.heightProperty());
      canvas.widthProperty().addListener(o->draw());canvas.heightProperty().addListener(o->draw());
      heights.setSelected(true);heights.selectedProperty().addListener(o->draw());
      zoom.setPrefWidth(160);zoom.valueProperty().addListener(o->draw());
      Button fit=new Button("Reset view");fit.setOnAction(e->{panX=0;panY=0;zoom.setValue(1);draw();});
      showSprites.setSelected(true); showSprites.selectedProperty().addListener(o->draw());
      HBox tools=new HBox(10,new Label("Zoom"),zoom,fit,heights,showSprites);tools.setAlignment(Pos.CENTER_LEFT);tools.setPadding(new Insets(9,0,9,0));
      Label legend=new Label("Blue: furniture  \u00b7  Purple: WIRED  \u00b7  Orange: wall items  \u00b7  Drag: pan  \u00b7  Scroll: zoom");legend.setWrapText(true);legend.setStyle("-fx-text-fill:#475569;");
      VBox view=new VBox(tools,surface,legend);VBox.setVgrow(surface,Priority.ALWAYS);setCenter(view);
      TextField search=new TextField();search.setPromptText("Search furniture or ID");
      items.setItems(FXCollections.observableArrayList(model.items));
      search.textProperty().addListener((o,a,b)->{
         String q=b.toLowerCase(Locale.ROOT);
         items.setItems(FXCollections.observableArrayList(model.items.stream().filter(i->(i.name()+" "+i.className()+" "+i.id()).toLowerCase(Locale.ROOT).contains(q)).collect(Collectors.toList())));
      });
      items.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{showDetails(b);draw();});
      details.setWrapText(true);details.setMinHeight(115);details.setStyle("-fx-text-fill:#334155;");
      VBox sidebar=new VBox(9,new Label("Furniture"),search,items,details);sidebar.setPrefWidth(270);sidebar.setMinWidth(220);sidebar.setPadding(new Insets(9,0,0,14));VBox.setVgrow(items,Priority.ALWAYS);setRight(sidebar);
      status.setWrapText(true);status.setStyle("-fx-text-fill:#92400e;");
      String warning=model.approximateCount==0?"":"Unknown dimensions for "+model.approximateCount+" item(s): shown as 1\u00d71. Load furnidata for exact footprints.";
      if(model.wallCount>0)warning+=(warning.isEmpty()?"":" ")+"Wall markers are approximate; exact offsets appear in the details.";
      if(model.isEmpty())warning="This preset is empty.";
      Label limitations=new Label(warning);limitations.setWrapText(true);limitations.setStyle("-fx-text-fill:#475569;");
      launch.setStyle("-fx-background-color:#7c3aed; -fx-text-fill:white; -fx-font-weight:bold;");
      HBox actions=new HBox(10,cancel,launch);actions.setAlignment(Pos.CENTER_RIGHT);
      VBox footer=new VBox(8,limitations,status,actions);footer.setPadding(new Insets(12,0,0,0));setBottom(footer);
      canvas.setOnScroll(e->{zoom.setValue(Math.max(zoom.getMin(),Math.min(zoom.getMax(),zoom.getValue()*(e.getDeltaY()>0?1.12:1/1.12))));e.consume();});
      canvas.setOnMousePressed(e->{dragX=e.getX();dragY=e.getY();dragDistance=0;});
      canvas.setOnMouseDragged(e->{double dx=e.getX()-dragX,dy=e.getY()-dragY;panX+=dx;panY+=dy;dragDistance+=Math.abs(dx)+Math.abs(dy);dragX=e.getX();dragY=e.getY();draw();});
      canvas.setOnMouseClicked(e->{if(dragDistance>5)return;for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(contains(h,e.getX(),e.getY())){items.getSelectionModel().select(h.item());items.scrollTo(h.item());break;}}});
      draw();
   }
   public void close() { spriteLoader.shutdownNow(); }
   public Canvas getCanvas() { return canvas; }
   public ListView<PresetPreviewModel.Item> getItemList() { return items; }
   public Button getLaunchButton() { return launch; }
   public Button getCancelButton() { return cancel; }
   public void updateLaunchAvailability(boolean ready) {
      launch.setDisable(!ready || model.isEmpty());
      status.setText(ready?"Confirm to prepare the import; placement tiles will be requested in Habbo.":"Preview only: connect and load inventory, catalog, and required permissions, then reopen the preview.");
   }
   public void setActions(BooleanSupplier confirm,Runnable close) {
      cancel.setOnAction(e->close.run());
      launch.setOnAction(e->{
         try { if(confirm.getAsBoolean())close.run();else {launch.setDisable(true);status.setText("The preset, options or room changed. Close and reopen the preview.");} }
         catch(RuntimeException error) {status.setText("Cannot import: "+error.getMessage());}
      });
   }
   private void showDetails(PresetPreviewModel.Item item) {
      if(item==null)return;
      String position=String.format(Locale.ROOT,"X %.2f  Y %.2f  Z preset %.2f",item.x()+(originX==null?0:originX),item.y()+(originY==null?0:originY),item.z());
      details.setText(item.name()+"\n"+item.className()+"  ["+item.id()+"]\n"+position+(originX==null?" (relative)":"")+"\nRotation "+item.rotation()+"  \u00b7  "+item.width()+"\u00d7"+item.depth()+"\n"+(item.wall()?item.wallLocation():"State: "+(item.state()==null?"default":item.state())));
   }
   private Point project(double x,double y,double z) { return new Point((x-y)*32,(x+y)*16-(heights.isSelected()?z*16:0)); }
   private Point screen(double x,double y,double z) {Point p=project(x,y,z);return new Point(offsetX+p.x()*scale,offsetY+p.y()*scale);}
   private void draw() {
      double width=canvas.getWidth(),height=canvas.getHeight();if(width<2 || height<2)return;
      GraphicsContext g=canvas.getGraphicsContext2D();g.setFill(Color.web("#101827"));g.fillRect(0,0,width,height);hits.clear();
      if(model.items.isEmpty()){g.setFill(Color.web("#cbd5e1"));g.setFont(Font.font(15));g.fillText(model.reusedCount>0?"Existing furniture only: positions preserved.":"No furniture in this preset.",22,40);return;}
      double lowX=Double.POSITIVE_INFINITY,lowY=Double.POSITIVE_INFINITY,highX=Double.NEGATIVE_INFINITY,highY=Double.NEGATIVE_INFINITY;
      for(PresetPreviewModel.Item item:model.items)for(int corner=0;corner<4;corner++) {
         double x=item.x()+((corner==1 || corner==2)?item.width():0),y=item.y()+(corner>=2?item.depth():0);
         for(double z:new double[]{0,item.z()}){Point p=project(x,y,z);lowX=Math.min(lowX,p.x());highX=Math.max(highX,p.x());lowY=Math.min(lowY,p.y());highY=Math.max(highY,p.y());}
      }
      if(showSprites.isSelected()) for(PresetPreviewModel.Item item:model.items) {
         Image sprite=sprites.get(spriteKey(item));
         if(sprite!=null && !item.wall()) {
            Point center=project(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());
            lowX=Math.min(lowX,center.x()-sprite.getWidth()/2); highX=Math.max(highX,center.x()+sprite.getWidth()/2);
            lowY=Math.min(lowY,center.y()-sprite.getHeight()+16); highY=Math.max(highY,center.y()+16);
         }
      }
      scale=Math.max(0.00001,Math.min((width-64)/Math.max(64,highX-lowX),(height-64)/Math.max(48,highY-lowY)))*zoom.getValue();
      offsetX=(width-(lowX+highX)*scale)/2+panX;offsetY=(height-(lowY+highY)*scale)/2+panY;
      g.setLineWidth(1);g.setStroke(Color.web("#263449"));
      double spanX=model.maxX-model.minX,spanY=model.maxY-model.minY;
      if(spanX<=120 && spanY<=120) {
         for(double x=Math.floor(model.minX);x<=Math.ceil(model.maxX);x++){Point a=screen(x,model.minY,0),b=screen(x,model.maxY,0);g.strokeLine(a.x(),a.y(),b.x(),b.y());}
         for(double y=Math.floor(model.minY);y<=Math.ceil(model.maxY);y++){Point a=screen(model.minX,y,0),b=screen(model.maxX,y,0);g.strokeLine(a.x(),a.y(),b.x(),b.y());}
      }
      PresetPreviewModel.Item selected=items.getSelectionModel().getSelectedItem();
      for(PresetPreviewModel.Item item:model.items) {
         double[] xs=new double[4],ys=new double[4];
         for(int c=0;c<4;c++){Point p=screen(item.x()+((c==1 || c==2)?item.width():0),item.y()+(c>=2?item.depth():0),item.z());xs[c]=p.x();ys[c]=p.y();}
         if(heights.isSelected() && item.z()!=0){Point a=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,0),b=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());g.setStroke(Color.web("#64748b"));g.setLineWidth(1);g.strokeLine(a.x(),a.y(),b.x(),b.y());}
         Color fill=Color.web(item.wall()?"#f59e0b":item.wired()?"#8b5cf6":"#3b82f6");
         Image sprite=showSprites.isSelected() && !item.wall()?spriteFor(item):null;
         g.setFill(fill.deriveColor(0,1,1,sprite!=null?0.15:item==selected?0.95:0.72));g.fillPolygon(xs,ys,4);
         g.setStroke(item==selected?Color.web("#67e8f9"):fill.brighter());g.setLineWidth(item==selected?3:1);g.strokePolygon(xs,ys,4);
         if(showSprites.isSelected() && !item.wall()) {
            if(sprite!=null) {
               Point center=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());
               double imageWidth=sprite.getWidth()*scale, imageHeight=sprite.getHeight()*scale;
               g.drawImage(sprite,center.x()-imageWidth/2,center.y()-imageHeight+16*scale,imageWidth,imageHeight);
            }
         }
         hits.add(new Hit(item,xs,ys));
         if(sprite==null && scale>0.30){Point center=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());double angle=item.rotation()*Math.PI/4;Point end=screen(item.x()+item.width()/2.0+Math.sin(angle)*0.30,item.y()+item.depth()/2.0-Math.cos(angle)*0.30,item.z());g.setStroke(Color.web("#f8fafc"));g.setLineWidth(1.4);g.strokeLine(center.x(),center.y(),end.x(),end.y());}
      }
   }
   private String spriteKey(PresetPreviewModel.Item item) { return item.className()+"|"+item.rotation()+"|"+(item.state()==null?"":item.state()); }
   private Image spriteFor(PresetPreviewModel.Item item) {
      String classname=item.className();
      if(classname==null || !classname.matches("[A-Za-z0-9_.*-]{1,120}"))return null;
      String key=spriteKey(item);
      Image image=sprites.get(key);
      if(image==null && !spriteLoader.isShutdown() && requestedSprites.size()<1000 && requestedSprites.add(key)) {
         spriteLoader.submit(()->{
            try {
               String state=item.state()!=null && item.state().matches("[0-9]{1,3}")?"&state="+item.state():"";
               String url="https://www.habboassets.com/imaging/furniture/"+classname+".png?direction="+item.rotation()+state;
               URLConnection connection=new URL(url).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
               byte[] bytes;try(InputStream stream=connection.getInputStream()){bytes=stream.readNBytes(2_000_001);}
               if(bytes.length>2_000_000)throw new IllegalArgumentException("Image too large");
               Image loaded=new Image(new java.io.ByteArrayInputStream(bytes));
               if(!loaded.isError() && loaded.getWidth()>0)Platform.runLater(()->{sprites.put(key,loaded);draw();});
            } catch(Exception ignored) { /* The footprint remains visible offline. */ }
            try {Thread.sleep(1100);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
         });
      }
      return image;
   }
   private static boolean contains(Hit h,double x,double y) {
      boolean inside=false;for(int i=0,j=3;i<4;j=i++)if((h.y()[i]>y)!=(h.y()[j]>y) && x<(h.x()[j]-h.x()[i])*(y-h.y()[i])/(h.y()[j]-h.y()[i])+h.x()[i])inside=!inside;return inside;
   }
}
