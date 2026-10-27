package tests;
import extension.GPresets;
import extension.tools.preview.*;
import extension.tools.postconfig.PostConfig;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.furni.PresetFurni;
import extension.tools.presetconfig.wired.PresetWireds;
import gearth.extensions.parsers.HPoint;
import java.util.*;
import org.json.*;
import javafx.scene.Scene;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
/** Loads the packaged FXML and CSS without a visible window or a hotel connection. */
public final class UiResourceSmoke {
   public static void main(String[] args) throws Exception {
      CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
      Platform.startup(()->{
         try {
            FXMLLoader loader=new FXMLLoader(GPresets.class.getResource("ui/gpresets.fxml"));
            loader.setControllerFactory(type->new GPresets(){ @Override public void initialize() { } });
            Parent root=loader.load();GPresets controller=loader.getController();
            if(controller.presetSearch==null)throw new AssertionError("Preset search missing");
            if(controller.importProgressLabel==null || controller.importProgressBar==null) throw new AssertionError("Import progress UI missing");
            if(root==null || controller.noExportFurniVariablesCbx==null || controller.logsBorderPane==null) throw new AssertionError("Missing FXML bindings");
            if(controller.previewBtn==null || controller.previewBeforeImportCbx==null || !controller.previewBeforeImportCbx.isSelected())throw new AssertionError("Preview controls/default missing");
            if(GPresets.class.getResource("ui/styles.css")==null)throw new AssertionError("Missing packaged CSS");
            javafx.scene.control.TabPane tabs=(javafx.scene.control.TabPane)root;
            Scene mainScene=new Scene(root,686,380);
            tabs.getSelectionModel().select(1);root.applyCss();root.layout();
            javafx.geometry.Bounds box=controller.previewBeforeImportCbx.localToScene(controller.previewBeforeImportCbx.getBoundsInLocal());
            if(box.getMinY()<0 || box.getMaxY()>mainScene.getHeight())throw new AssertionError("Preview setting is clipped: "+box);
            if(!controller.previewBeforeImportCbx.getText().equals("Preview before import (:ip)"))throw new AssertionError("Preview setting label");

            PresetFurni f=new PresetFurni(1,"wf_act_send_message",new HPoint(1,2,0.5),2,null);f.setFurniName("wired[0]");
            PresetFurni table=new PresetFurni(2,"table",new HPoint(2,3,0),2,"0");table.setFurniName("table[0]");
            PresetFurni flower=new PresetFurni(3,"flower",new HPoint(4,2,0),0,"1");flower.setFurniName("flower[0]");
            PresetFurni stacked=new PresetFurni(4,"wf_trg_says_something",new HPoint(1,2,1.5),0,null);stacked.setFurniName("wired[1]");
            PresetConfig sample=new PresetConfig(new ArrayList<>(List.of(f,table,flower,stacked)),new ArrayList<>(),new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray())),new ArrayList<>(),new ArrayList<>());
            PresetPreviewPane pane=new PresetPreviewPane(new PresetPreviewModel(sample,new PostConfig(),name->name.equals("table")?new PresetPreviewModel.Footprint(2,1):new PresetPreviewModel.Footprint(1,1)),"Exemple",12,13);
            pane.setPresetDescription("A shared preset description");
            Scene scene=new Scene(pane,1040,720);pane.applyCss();pane.layout();
            if(pane.getCanvas().getWidth()<200 || pane.getItemList().getItems().size()!=4)throw new AssertionError("Preview layout missing");
            pane.getItemList().getSelectionModel().select(0);
            var turn=(javafx.scene.control.Button)pane.lookup("#turnView");if(turn==null)throw new AssertionError("Camera rotation button missing");turn.fire();turn.fire();turn.fire();turn.fire();
            String png=extension.tools.sharing.PreviewImages.png(pane.getCanvas());
            var shared=new extension.tools.sharing.PresetMetadata("UI snapshot","Description",png);
            sample.setMetadata(shared);
            pane.setSharedMetadata(shared);
            pane.applyCss();pane.layout();
            if(!new PresetConfig(sample.toJsonObject()).getMetadata().equals(shared))throw new AssertionError("Shared image round trip failed");

            int[] confirmations={0},closes={0};
            pane.setActions(()->{confirmations[0]++;return true;},()->closes[0]++);
            int[] availabilityChecks={0}; pane.setAvailabilityAction(()->{availabilityChecks[0]++;pane.setAvailabilitySummary("Test summary");});
            javafx.scene.control.Button check=(javafx.scene.control.Button)pane.lookup("#checkAvailability");
            if(check==null)throw new AssertionError("Availability button missing");
            check.fire(); if(availabilityChecks[0]!=1)throw new AssertionError("Availability action missing");

            pane.updateLaunchAvailability(false);pane.getLaunchButton().fire();
            if(confirmations[0]!=0)throw new AssertionError("Disabled preview launched");
            pane.updateLaunchAvailability(true);pane.getLaunchButton().fire();
            if(confirmations[0]!=1 || closes[0]!=1)throw new AssertionError("Preview confirmation not routed");
            pane.getCancelButton().fire();if(confirmations[0]!=1 || closes[0]!=2)throw new AssertionError("Cancel launched import");
            javafx.scene.image.WritableImage snapshot=pane.snapshot(null,null);
            java.awt.image.BufferedImage screenshot=new java.awt.image.BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            javafx.scene.image.PixelReader reader=snapshot.getPixelReader();
            for(int y=0;y<screenshot.getHeight();y++)for(int x=0;x<screenshot.getWidth();x++)screenshot.setRGB(x,y,reader.getArgb(x,y));
            javax.imageio.ImageIO.write(screenshot,"png",new java.io.File("target/preview-ui-smoke.png"));
            pane.close();
            PreviewImportFlow.run();
            System.out.println("PASS: preview canvas/layout, selection, launch availability, confirm and cancel");
            System.out.println("PASS: packaged JavaFX FXML, controller bindings and CSS; no hotel connection");
         } catch(Throwable error) { failure.set(error); }
         finally { done.countDown(); }
      });
      if(!done.await(20,TimeUnit.SECONDS))throw new AssertionError("JavaFX initialization timed out");
      Platform.exit();
      if(failure.get()!=null)throw new AssertionError("Packaged UI failed to load",failure.get());
   }
}
