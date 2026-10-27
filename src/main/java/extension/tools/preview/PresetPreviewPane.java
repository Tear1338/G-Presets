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

/** Isometric footprint preview; intentionally independent of all hotel/network APIs. */
public final class PresetPreviewPane extends BorderPane {
   private final PresetPreviewModel model;
   private final Canvas canvas=new Canvas(680,440);
   private final ListView<PresetPreviewModel.Item> items=new ListView<>();
   private final Label details=new Label("S\u00e9lectionnez un mobi pour voir ses coordonn\u00e9es.");
   private final Label status=new Label();
   private final Button launch=new Button("Continuer vers l'import");
   private final Button cancel=new Button("Annuler");
   private final Slider zoom=new Slider(0.5,4,1);
   private final CheckBox heights=new CheckBox("Afficher les hauteurs");
   private final List<Hit> hits=new ArrayList<>();
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
      Label title=new Label("Aper\u00e7u du preset"+(name==null?"":" \u00b7 "+name));
      title.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");
      Label counts=new Label(model.floorCount+" mobis \u00e0 poser au sol  \u00b7  "+model.wallCount+" muraux  \u00b7  "+model.wiredCount+" WIRED  \u00b7  "+model.configurationCount+" configurations");
      String origin=originX==null?"Origine : \u00e0 choisir dans Habbo apr\u00e8s confirmation.":"Origine demand\u00e9e : ("+originX+", "+originY+").";
      Label scope=new Label(origin+"  "+model.reusedCount+" mobi(s) existant(s) r\u00e9utilis\u00e9(s), non d\u00e9plac\u00e9(s).");scope.setWrapText(true);
      Label note=new Label("Plan sch\u00e9matique des empreintes, pas le rendu graphique exact des mobis. Aucun placement avant confirmation.");note.setWrapText(true);note.setStyle("-fx-text-fill:#475569;");
      VBox header=new VBox(5,title,counts,scope,note);header.setPadding(new Insets(0,0,14,0));setTop(header);
      StackPane surface=new StackPane(canvas);surface.setMinSize(280,200);surface.setPrefSize(680,440);
      canvas.widthProperty().bind(surface.widthProperty());canvas.heightProperty().bind(surface.heightProperty());
      canvas.widthProperty().addListener(o->draw());canvas.heightProperty().addListener(o->draw());
      heights.setSelected(true);heights.selectedProperty().addListener(o->draw());
      zoom.setPrefWidth(160);zoom.valueProperty().addListener(o->draw());
      Button fit=new Button("Recentrer");fit.setOnAction(e->{panX=0;panY=0;zoom.setValue(1);draw();});
      HBox tools=new HBox(10,new Label("Zoom"),zoom,fit,heights);tools.setAlignment(Pos.CENTER_LEFT);tools.setPadding(new Insets(9,0,9,0));
      Label legend=new Label("Bleu : mobilier  \u00b7  Violet : WIRED  \u00b7  Orange : rep\u00e8res muraux  \u00b7  Glisser : d\u00e9placer  \u00b7  Molette : zoomer");legend.setWrapText(true);legend.setStyle("-fx-text-fill:#475569;");
      VBox view=new VBox(tools,surface,legend);VBox.setVgrow(surface,Priority.ALWAYS);setCenter(view);
      TextField search=new TextField();search.setPromptText("Chercher un mobi ou un ID");
      items.setItems(FXCollections.observableArrayList(model.items));
      search.textProperty().addListener((o,a,b)->{
         String q=b.toLowerCase(Locale.ROOT);
         items.setItems(FXCollections.observableArrayList(model.items.stream().filter(i->(i.name()+" "+i.className()+" "+i.id()).toLowerCase(Locale.ROOT).contains(q)).collect(Collectors.toList())));
      });
      items.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{showDetails(b);draw();});
      details.setWrapText(true);details.setMinHeight(115);details.setStyle("-fx-text-fill:#334155;");
      VBox sidebar=new VBox(9,new Label("Mobilier pr\u00e9vu"),search,items,details);sidebar.setPrefWidth(270);sidebar.setMinWidth(220);sidebar.setPadding(new Insets(9,0,0,14));VBox.setVgrow(items,Priority.ALWAYS);setRight(sidebar);
      status.setWrapText(true);status.setStyle("-fx-text-fill:#92400e;");
      String warning=model.approximateCount==0?"":"Dimensions inconnues pour "+model.approximateCount+" mobi(s) : rep\u00e8res 1\u00d71. Chargez le furnidata pour les empreintes exactes.";
      if(model.wallCount>0)warning+=(warning.isEmpty()?"":" ")+"Les rep\u00e8res muraux sont indicatifs ; leurs offsets exacts figurent dans les d\u00e9tails.";
      if(model.isEmpty())warning="Ce preset est vide.";
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
   public Canvas getCanvas() { return canvas; }
   public ListView<PresetPreviewModel.Item> getItemList() { return items; }
   public Button getLaunchButton() { return launch; }
   public Button getCancelButton() { return cancel; }
   public void updateLaunchAvailability(boolean ready) {
      launch.setDisable(!ready || model.isEmpty());
      status.setText(ready?"La confirmation lance la pr\u00e9paration ; les cases de placement seront demand\u00e9es dans Habbo.":"Aper\u00e7u seul : connectez-vous et chargez l'inventaire, le catalogue et les droits requis, puis rouvrez l'aper\u00e7u.");
   }
   public void setActions(BooleanSupplier confirm,Runnable close) {
      cancel.setOnAction(e->close.run());
      launch.setOnAction(e->{
         try { if(confirm.getAsBoolean())close.run();else {launch.setDisable(true);status.setText("Le preset, les options ou la salle ont chang\u00e9. Fermez puis rouvrez l'aper\u00e7u.");} }
         catch(RuntimeException error) {status.setText("Import impossible : "+error.getMessage());}
      });
   }
   private void showDetails(PresetPreviewModel.Item item) {
      if(item==null)return;
      String position=String.format(Locale.ROOT,"X %.2f  Y %.2f  Z preset %.2f",item.x()+(originX==null?0:originX),item.y()+(originY==null?0:originY),item.z());
      details.setText(item.name()+"\n"+item.className()+"  ["+item.id()+"]\n"+position+(originX==null?" (relatif)":"")+"\nRotation "+item.rotation()+"  \u00b7  "+item.width()+"\u00d7"+item.depth()+"\n"+(item.wall()?item.wallLocation():"\u00c9tat : "+(item.state()==null?"par d\u00e9faut":item.state())));
   }
   private Point project(double x,double y,double z) { return new Point((x-y)*32,(x+y)*16-(heights.isSelected()?z*16:0)); }
   private Point screen(double x,double y,double z) {Point p=project(x,y,z);return new Point(offsetX+p.x()*scale,offsetY+p.y()*scale);}
   private void draw() {
      double width=canvas.getWidth(),height=canvas.getHeight();if(width<2 || height<2)return;
      GraphicsContext g=canvas.getGraphicsContext2D();g.setFill(Color.web("#101827"));g.fillRect(0,0,width,height);hits.clear();
      if(model.items.isEmpty()){g.setFill(Color.web("#cbd5e1"));g.setFont(Font.font(15));g.fillText(model.reusedCount>0?"Mobilier existant uniquement : positions conserv\u00e9es.":"Aucun mobilier dans ce preset.",22,40);return;}
      double lowX=Double.POSITIVE_INFINITY,lowY=Double.POSITIVE_INFINITY,highX=Double.NEGATIVE_INFINITY,highY=Double.NEGATIVE_INFINITY;
      for(PresetPreviewModel.Item item:model.items)for(int corner=0;corner<4;corner++) {
         double x=item.x()+((corner==1 || corner==2)?item.width():0),y=item.y()+(corner>=2?item.depth():0);
         for(double z:new double[]{0,item.z()}){Point p=project(x,y,z);lowX=Math.min(lowX,p.x());highX=Math.max(highX,p.x());lowY=Math.min(lowY,p.y());highY=Math.max(highY,p.y());}
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
         g.setFill(fill.deriveColor(0,1,1,item==selected?0.95:0.72));g.fillPolygon(xs,ys,4);
         g.setStroke(item==selected?Color.web("#67e8f9"):fill.brighter());g.setLineWidth(item==selected?3:1);g.strokePolygon(xs,ys,4);
         hits.add(new Hit(item,xs,ys));
         if(scale>0.30){Point center=screen(item.x()+item.width()/2.0,item.y()+item.depth()/2.0,item.z());double angle=item.rotation()*Math.PI/4;Point end=screen(item.x()+item.width()/2.0+Math.sin(angle)*0.30,item.y()+item.depth()/2.0-Math.cos(angle)*0.30,item.z());g.setStroke(Color.web("#f8fafc"));g.setLineWidth(1.4);g.strokeLine(center.x(),center.y(),end.x(),end.y());}
      }
   }
   private static boolean contains(Hit h,double x,double y) {
      boolean inside=false;for(int i=0,j=3;i<4;j=i++)if((h.y()[i]>y)!=(h.y()[j]>y) && x<(h.x()[j]-h.x()[i])*(y-h.y()[i])/(h.y()[j]-h.y()[i])+h.x()[i])inside=!inside;return inside;
   }
}
