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

/** Isometric footprint preview; intentionally independent of all hotel/network APIs. */
public final class PresetPreviewPane extends BorderPane {
   private final PresetPreviewModel model;
   private final Canvas canvas=new Canvas(680,440);
   private final ListView<PresetPreviewModel.Item> items=new ListView<>();
   private final Label details=new Label("Select an item to see its coordinates.");
   private final Label status=new Label();
   private final Label availability=new Label("Availability: not checked. Select Check availability.");
   private final Button launch=new Button("Continue to import");
   private final Button cancel=new Button("Cancel");
   private final Slider zoom=new Slider(0.5,4,1);
   private final CheckBox heights=new CheckBox("Show heights");
   private final List<Hit> hits=new ArrayList<>();
   private final Map<String,Image> sprites=new HashMap<>();
   private final Set<String> requestedSprites=new HashSet<>();
   private final FurnitureSpriteStore<Image> spriteStore=FurnitureSprites.shared();
   private final Set<String> unavailableSprites=new HashSet<>();
   private volatile boolean closed;
   private boolean redrawPending;
   private int viewTurns;
   private final CheckBox isolate=new CheckBox("Selected item only");
   private final Slider layer=new Slider();
   private final Label imageStatus=new Label("Images loading as needed");
   private final CheckBox showSprites=new CheckBox("Furniture images");
   private double panX,panY,dragX,dragY,dragDistance;
   private double scale,offsetX,offsetY;
   private Integer originX,originY;
   private final Label originScope=new Label();
   private Map<Integer,String> placementIssues=Map.of();
   private Runnable placementRefresh;
   public void refreshPlacement() {if(placementRefresh!=null && !closed)placementRefresh.run();}
   public void configurePlacement(java.util.function.BiFunction<Integer,Integer,PlacementAudit.Result> check,java.util.function.BiConsumer<Integer,Integer> selected) {
      CheckBox choose=new CheckBox("Choose origin here");choose.setSelected(originX!=null);
      Spinner<Integer> x=new Spinner<>(0,129,originX==null?0:Math.max(0,Math.min(129,originX))),y=new Spinner<>(0,129,originY==null?0:Math.max(0,Math.min(129,originY)));
      x.setId("placementX");y.setId("placementY");choose.setId("choosePlacement");x.setPrefWidth(85);y.setPrefWidth(85);
      Label outcome=new Label();outcome.setWrapText(true);outcome.setId("placementSummary");
      placementRefresh=()->{
         if(choose.isSelected()) {
            originX=x.getValue();originY=y.getValue();selected.accept(originX,originY);
            try{var result=check.apply(originX,originY);placementIssues=result.issues();outcome.setText(result.summary());}
            catch(RuntimeException error){placementIssues=Map.of();outcome.setText("Room placement unknown: "+error.getMessage());}
         }else {originX=null;originY=null;selected.accept(null,null);placementIssues=Map.of();outcome.setText("Choose an origin to check room collisions, or select it in Habbo after confirmation.");}
         originScope.setText((originX==null?"Origin: choose in Habbo after confirmation.":"Requested origin: ("+originX+", "+originY+").")+"  "+model.reusedCount+" existing items reused without moving.");
         x.setDisable(!choose.isSelected());y.setDisable(!choose.isSelected());showDetails(items.getSelectionModel().getSelectedItem());draw();
      };
      choose.selectedProperty().addListener(o->refreshPlacement());x.valueProperty().addListener(o->refreshPlacement());y.valueProperty().addListener(o->refreshPlacement());
      if(getTop() instanceof VBox header)header.getChildren().addAll(new FlowPane(10,8,choose,new Label("X"),x,new Label("Y"),y),outcome);
      refreshPlacement();
   }
   private Runnable availabilityAction;
   private Runnable compatibilityAction;
   private record Point(double x,double y) { }
   private record Hit(PresetPreviewModel.Item item,double[] x,double[] y) { }

   public PresetPreviewPane(PresetPreviewModel model,String name,Integer originX,Integer originY) {
      this.model=model;this.originX=originX;this.originY=originY;
      imageStatus.setId("imageProgress");
      extension.ui.ReadableTheme.apply(this);
      cancel.setCancelButton(true);
      setStyle("-fx-font-family:'Segoe UI'; -fx-font-size:12px;");
      setPadding(new Insets(16));
      canvas.getGraphicsContext2D().setImageSmoothing(false);
      Label title=new Label("Preset preview"+(name==null?"":" \u00b7 "+name));
      title.setStyle("-fx-font-size:20px; -fx-font-weight:bold; ");
      Label counts=new Label(model.floorCount+" floor items  \u00b7  "+model.wallCount+" wall items  \u00b7  "+model.wiredCount+" WIRED  \u00b7  "+model.configurationCount+" configurations");
      String origin=originX==null?"Origin: choose in Habbo after confirmation.":"Requested origin: ("+originX+", "+originY+").";
      Label scope=originScope;scope.setText(origin+"  "+model.reusedCount+" existing items reused without moving.");scope.setWrapText(true);
      Label note=new Label("Furniture images: HabboAssets. Positions and wall items are approximate. Nothing is placed before confirmation.");note.setWrapText(true);note.setStyle("");
      VBox header=new VBox(5,title,counts,scope,note);header.setPadding(new Insets(0,0,14,0));setTop(header);
      StackPane surface=new StackPane(canvas);surface.setMinSize(280,200);surface.setPrefSize(680,440);
      canvas.widthProperty().bind(surface.widthProperty());canvas.heightProperty().bind(surface.heightProperty());
      canvas.widthProperty().addListener(o->draw());canvas.heightProperty().addListener(o->draw());
      heights.setSelected(true);heights.selectedProperty().addListener(o->draw());
      zoom.setPrefWidth(160);zoom.valueProperty().addListener(o->draw());
      Button fit=new Button("Reset view");fit.setOnAction(e->{panX=0;panY=0;zoom.setValue(1);draw();});
      showSprites.setSelected(true); showSprites.selectedProperty().addListener(o->draw());
      Button turn=new Button("Rotate view");turn.setId("turnView");turn.setOnAction(e->{viewTurns=(viewTurns+1)%4;draw();});
      isolate.selectedProperty().addListener(o->draw());
      layer.setMin(0);layer.setMax(Math.max(0,model.maxZ));layer.setValue(layer.getMax());layer.setPrefWidth(110);layer.valueProperty().addListener(o->draw());
      FlowPane tools=new FlowPane(10,8,new Label("Zoom"),zoom,fit,turn,heights,showSprites,isolate,new Label("Max height"),layer,imageStatus);tools.setPadding(new Insets(9,0,9,0));
      Label legend=new Label("Blue: furniture  \u00b7  Purple: WIRED  \u00b7  Orange: wall items  \u00b7  Drag: pan  \u00b7  Scroll: zoom");legend.setWrapText(true);legend.setStyle("");
      VBox view=new VBox(tools,surface,legend);VBox.setVgrow(surface,Priority.ALWAYS);setCenter(view);
      TextField search=new TextField();search.setPromptText("Search furniture or ID");
      items.setItems(FXCollections.observableArrayList(model.items));
      search.textProperty().addListener((o,a,b)->{
         String q=b.toLowerCase(Locale.ROOT);
         items.setItems(FXCollections.observableArrayList(model.items.stream().filter(i->(i.name()+" "+i.className()+" "+i.id()).toLowerCase(Locale.ROOT).contains(q)).collect(Collectors.toList())));
      });
      items.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{showDetails(b);draw();});
      details.setWrapText(true);details.setMinHeight(115);details.setStyle("");
      VBox sidebar=new VBox(9,new Label("Furniture"),search,items,details);sidebar.setPrefWidth(270);sidebar.setMinWidth(220);sidebar.setPadding(new Insets(9,0,0,14));VBox.setVgrow(items,Priority.ALWAYS);setRight(sidebar);
      status.setWrapText(true);status.setStyle("");
      String warning=model.approximateCount==0?"":"Unknown dimensions for "+model.approximateCount+" item(s): shown as 1\u00d71. Load furnidata for exact footprints.";
      if(model.wallCount>0)warning+=(warning.isEmpty()?"":" ")+"Wall markers are approximate; exact offsets appear in the details.";
      if(model.isEmpty())warning="This preset is empty.";
      Label limitations=new Label(warning);limitations.setWrapText(true);limitations.setStyle("");
      launch.setStyle(" -fx-font-weight:bold;");
      HBox actions=new HBox(10,cancel,launch);actions.setAlignment(Pos.CENTER_RIGHT);
      availability.setWrapText(true); availability.setStyle("");
      Button checkAvailability=new Button("Check availability"); checkAvailability.setId("checkAvailability"); checkAvailability.setOnAction(e->{if(availabilityAction!=null)availabilityAction.run();});
      actions.getChildren().add(0,checkAvailability);
      Button checkCompatibility=new Button("Check WIRED");
      checkCompatibility.setOnAction(e->{if(compatibilityAction!=null)compatibilityAction.run();});
      actions.getChildren().add(1,checkCompatibility);
      VBox footer=new VBox(8,limitations,availability,status,actions);footer.setPadding(new Insets(12,0,0,0));setBottom(footer);
      canvas.setOnScroll(e->{zoom.setValue(Math.max(zoom.getMin(),Math.min(zoom.getMax(),zoom.getValue()*(e.getDeltaY()>0?1.12:1/1.12))));e.consume();});
      canvas.setOnMousePressed(e->{dragX=e.getX();dragY=e.getY();dragDistance=0;});
      canvas.setOnMouseDragged(e->{double dx=e.getX()-dragX,dy=e.getY()-dragY;panX+=dx;panY+=dy;dragDistance+=Math.abs(dx)+Math.abs(dy);dragX=e.getX();dragY=e.getY();draw();});
      canvas.setOnMouseClicked(e->{if(dragDistance>5)return;for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(contains(h,e.getX(),e.getY())){items.getSelectionModel().select(h.item());items.scrollTo(h.item());break;}}});
      draw();
   }
   public void setShareMode() {
      setBottom(new Label("The image captures this view. Cached furniture appears immediately; new images load in the background."));
   }
   public void setSharedMetadata(extension.tools.sharing.PresetMetadata metadata) {
      setPresetDescription(metadata.description());
      if(metadata.imagePng().isEmpty() || !(getTop() instanceof VBox header))return;
      Image image=new Image(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(metadata.imagePng())));
      if(image.isError())return;
      javafx.scene.image.ImageView thumbnail=new javafx.scene.image.ImageView(image);thumbnail.setFitWidth(90);thumbnail.setFitHeight(50);thumbnail.setPreserveRatio(true);
      Button view=new Button("Shared image",thumbnail);view.setOnAction(e->{
         Dialog<ButtonType> dialog=new Dialog<>();extension.ui.ReadableTheme.apply(dialog.getDialogPane());if(getScene()!=null)dialog.initOwner(getScene().getWindow());dialog.setTitle(metadata.name());
         javafx.scene.image.ImageView full=new javafx.scene.image.ImageView(image);full.setFitWidth(650);full.setFitHeight(440);full.setPreserveRatio(true);
         dialog.getDialogPane().setContent(full);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.show();
      });
      javafx.scene.Node title=header.getChildren().remove(0);HBox line=new HBox(12,title,view);line.setAlignment(Pos.CENTER_LEFT);header.getChildren().add(0,line);
   }
   public void setPresetDescription(String description) {
      if(description!=null && !description.isBlank() && getTop() instanceof VBox header) {
         Label descriptionLabel=new Label(description);descriptionLabel.setWrapText(true);descriptionLabel.setMaxHeight(60);
         header.getChildren().add(descriptionLabel);
      }
   }
   public void setAvailabilityAction(Runnable action) { availabilityAction=action; }
   public void setCompatibilityAction(Runnable action) { compatibilityAction=action; }
   public void setAvailabilitySummary(String summary) { availability.setText(summary); }
   public void close() { closed=true; }
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
      details.setText(item.name()+"\n"+item.className()+"  ["+item.id()+"]\n"+position+(originX==null?" (relative)":"")+"\nRotation "+item.rotation()+"  \u00b7  "+item.width()+"\u00d7"+item.depth()+"\n"+(item.wall()?item.wallLocation():"State: "+(item.state()==null?"default":item.state()))+(!item.wall() && placementIssues.containsKey(item.id())?"\n"+placementIssues.get(item.id()):""));
   }
   private Point project(double x,double y,double z) { var p=PreviewGeometry.project(x,y,z,viewTurns,heights.isSelected());return new Point(p.x(),p.y()); }
   private Point screen(double x,double y,double z) {Point p=project(x,y,z);return new Point(offsetX+p.x()*scale,offsetY+p.y()*scale);}
   private void draw() {
      double width=canvas.getWidth(),height=canvas.getHeight();if(closed || width<2 || height<2)return;
      // Hydrate warm sprites before fitting the view, so the first frame includes their full bounds.
      if(showSprites.isSelected())for(var item:model.items) {
         var request=spriteRequest(item);if(request==null)continue;
         String key=request.url();if(!sprites.containsKey(key)){Image cached=spriteStore.peek(request);if(cached!=null)sprites.put(key,cached);}
      }
      GraphicsContext g=canvas.getGraphicsContext2D();g.setFill(Color.web("#333333"));g.fillRect(0,0,width,height);hits.clear();
      if(model.items.isEmpty()){g.setFill(Color.web("#f0f0f0"));g.setFont(Font.font(15));g.fillText(model.reusedCount>0?"Existing furniture only: positions preserved.":"No furniture in this preset.",22,40);return;}
      double lowX=Double.POSITIVE_INFINITY,lowY=Double.POSITIVE_INFINITY,highX=Double.NEGATIVE_INFINITY,highY=Double.NEGATIVE_INFINITY;
      for(PresetPreviewModel.Item item:model.items)for(int corner=0;corner<4;corner++) {
         double x=item.x()+((corner==1 || corner==2)?item.width():0),y=item.y()+(corner>=2?item.depth():0);
         for(double z:new double[]{0,item.z()}){Point p=project(x,y,z);lowX=Math.min(lowX,p.x());highX=Math.max(highX,p.x());lowY=Math.min(lowY,p.y());highY=Math.max(highY,p.y());}
      }
      if(showSprites.isSelected()) for(PresetPreviewModel.Item item:model.items) {
         Image sprite=sprites.get(spriteKey(item));
         if(sprite!=null) {
            Point center=project(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());
            lowX=Math.min(lowX,center.x()-sprite.getWidth()/2); highX=Math.max(highX,center.x()+sprite.getWidth()/2);
            lowY=Math.min(lowY,center.y()-sprite.getHeight()+16); highY=Math.max(highY,center.y()+16);
         }
      }
      scale=Math.max(0.00001,Math.min((width-64)/Math.max(64,highX-lowX),(height-64)/Math.max(48,highY-lowY)))*zoom.getValue();
      offsetX=(width-(lowX+highX)*scale)/2+panX;offsetY=(height-(lowY+highY)*scale)/2+panY;
      g.setLineWidth(1);g.setStroke(Color.web("#505050"));
      double spanX=model.maxX-model.minX,spanY=model.maxY-model.minY;
      if(spanX<=120 && spanY<=120) {
         for(double x=Math.floor(model.minX);x<=Math.ceil(model.maxX);x++){Point a=screen(x,model.minY,0),b=screen(x,model.maxY,0);g.strokeLine(a.x(),a.y(),b.x(),b.y());}
         for(double y=Math.floor(model.minY);y<=Math.ceil(model.maxY);y++){Point a=screen(model.minX,y,0),b=screen(model.maxX,y,0);g.strokeLine(a.x(),a.y(),b.x(),b.y());}
      }
      PresetPreviewModel.Item selected=items.getSelectionModel().getSelectedItem();
      List<PresetPreviewModel.Item> ordered=new ArrayList<>(model.items);
      ordered.sort(Comparator.comparingDouble((PresetPreviewModel.Item i)->PreviewGeometry.depth(i.x()+i.width()/2.0,i.y()+i.depth()/2.0,viewTurns)).thenComparingDouble(PresetPreviewModel.Item::z));
      for(PresetPreviewModel.Item item:ordered) {
         if(item.z()>layer.getValue()+0.001 || (isolate.isSelected() && item!=selected))continue;
         double[] xs=new double[4],ys=new double[4];
         for(int c=0;c<4;c++){Point p=screen(item.x()+((c==1 || c==2)?item.width():0),item.y()+(c>=2?item.depth():0),item.z());xs[c]=p.x();ys[c]=p.y();}
         if(heights.isSelected() && item.z()!=0){Point a=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,0),b=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());g.setStroke(Color.web("#64748b"));g.setLineWidth(1);g.strokeLine(a.x(),a.y(),b.x(),b.y());}
         Color fill=Color.web(item.wall()?"#f59e0b":item.wired()?"#8b5cf6":"#3b82f6");
         Image sprite=showSprites.isSelected()?spriteFor(item):null;
         g.setFill(fill.deriveColor(0,1,1,sprite!=null?0.15:item==selected?0.95:0.72));g.fillPolygon(xs,ys,4);
         g.setStroke(!item.wall() && placementIssues.containsKey(item.id())?Color.web("#ff5555"):item==selected?Color.web("#56c0e0"):fill.brighter());g.setLineWidth(item==selected || (!item.wall() && placementIssues.containsKey(item.id()))?3:1);g.strokePolygon(xs,ys,4);
         if(showSprites.isSelected()) {
            if(sprite!=null) {
               Point center=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());
               double imageWidth=sprite.getWidth()*scale, imageHeight=sprite.getHeight()*scale;
               double left=center.x()-imageWidth/2,top=center.y()-imageHeight+16*scale;
               g.drawImage(sprite,left,top,imageWidth,imageHeight);
               hits.add(new Hit(item,new double[]{left,left+imageWidth,left+imageWidth,left},new double[]{top,top,top+imageHeight,top+imageHeight}));
            }
         }
         hits.add(new Hit(item,xs,ys));
         if(sprite==null && scale>0.30){Point center=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());double angle=item.rotation()*Math.PI/4;Point end=screen(item.x()+item.width()/2.0+Math.sin(angle)*0.30,item.y()+item.depth()/2.0-Math.cos(angle)*0.30,item.z());g.setStroke(Color.web("#f0f0f0"));g.setLineWidth(1.4);g.strokeLine(center.x(),center.y(),end.x(),end.y());}
      }
      updateImageStatus();
   }
   private FurnitureSpriteStore.Key spriteRequest(PresetPreviewModel.Item item) {
      try{return new FurnitureSpriteStore.Key(item.className(),item.rotation()+viewTurns*2,item.state());}
      catch(IllegalArgumentException invalid){return null;}
   }
   private String spriteKey(PresetPreviewModel.Item item) {var request=spriteRequest(item);return request==null?null:request.url();}
   private Image spriteFor(PresetPreviewModel.Item item) {
      var request=spriteRequest(item);if(request==null)return null;
      String key=request.url();Image image=sprites.get(key);
      if(image==null && !closed && requestedSprites.size()<256 && requestedSprites.add(key)) {
         java.lang.ref.WeakReference<PresetPreviewPane> owner=new java.lang.ref.WeakReference<>(this);
         spriteStore.load(request).whenComplete((loaded,error)->{
            PresetPreviewPane pane=owner.get();if(pane==null || pane.closed)return;
            Platform.runLater(()->{
               if(pane.closed)return;
               if(error==null)pane.sprites.put(key,loaded);else pane.unavailableSprites.add(key);
               if(!pane.redrawPending){pane.redrawPending=true;Platform.runLater(()->{pane.redrawPending=false;pane.draw();});}
            });
         });
      }
      return image;
   }
   private void updateImageStatus() {
      Set<String> wanted=model.items.stream().map(this::spriteKey).filter(Objects::nonNull).collect(Collectors.toSet());
      long loaded=wanted.stream().filter(sprites::containsKey).count(),failed=wanted.stream().filter(unavailableSprites::contains).count();
      imageStatus.setText("Images: "+loaded+"/"+wanted.size()+(failed>0?" ("+failed+" unavailable)":"")+(wanted.size()>256?"; up to 256 variants per view":""));
   }
   private static boolean contains(Hit h,double x,double y) {
      boolean inside=false;for(int i=0,j=3;i<4;j=i++)if((h.y()[i]>y)!=(h.y()[j]>y) && x<(h.x()[j]-h.x()[i])*(y-h.y()[i])/(h.y()[j]-h.y()[i])+h.x()[i])inside=!inside;return inside;
   }
}
