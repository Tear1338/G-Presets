package tests;
import extension.GPresets;
import extension.logger.Logger;
import extension.tools.GPresetImporter;
import extension.tools.preview.PresetPreviewSession;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.wired.PresetWireds;
import game.FloorState;
import gearth.extensions.ExtensionBase;
import gearth.protocol.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Exercise real importer interception with a disconnected, non-network fake extension. */
public final class PreviewImportFlow {
   private static void require(boolean condition,String text) { if(!condition)throw new AssertionError(text); }
   private static final class FakeExtension extends GPresets {
      final Map<String,ExtensionBase.MessageListener> callbacks=new HashMap<>();
      int serverPackets;int room=42;
      PresetPreviewSession session;BooleanSupplier confirm,ready;Runnable cancel;
      final FloorState floor=new FloorState(this,new Logger(),()->{},()->{}) {
         @Override public int getRoomId() { return room; }
         @Override public boolean inRoom() { return false; }
      };
      @Override public void intercept(HMessage.Direction direction,String name,ExtensionBase.MessageListener listener) { callbacks.put(direction+":"+name,listener); }
      @Override public FloorState getFloorState() { return floor; }
      @Override public boolean sendToServer(HPacket packet) { serverPackets++;return true; }
      @Override public void sendVisualChatInfo(String text) { }
      @Override public void updatePreviewAvailability() { }
      @Override public boolean previewBeforeImport() { return true; }
      @Override public void showImportPreview(PresetPreviewSession session,BooleanSupplier ready,BooleanSupplier confirm,Runnable cancel) { this.session=session;this.ready=ready;this.confirm=confirm;this.cancel=cancel; }
      void chat(String text) {
         HMessage message=new HMessage(new HPacket(1,text),HMessage.Direction.TOSERVER,0);
         callbacks.get("TOSERVER:Chat").act(message);
         require(message.isBlocked(),"preview/import command must stay local");
      }
   }
   public static void run() {
      FakeExtension extension=new FakeExtension();
      GPresetImporter importer=new GPresetImporter(extension) { @Override public boolean isReady() { return true; } };
      PresetConfig preset=new PresetConfig(new ArrayList<>(),new ArrayList<>(),new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray())),new ArrayList<>(),new ArrayList<>());
      importer.setPresetConfig(preset);
      extension.chat(":ip 12,13");
      require(importer.getState()==GPresetImporter.BuildingImportState.AWAITING_PREVIEW,"ip must stop before preparation");
      require(extension.session.rootX==12 && extension.session.rootY==13,"explicit root forwarded to preview");
      require(extension.serverPackets==0,"opening preview must send no game packets");
      extension.cancel.run();
      require(importer.getState()==GPresetImporter.BuildingImportState.NONE && !extension.confirm.getAsBoolean(),"closing preview cancels pending import");
      extension.chat(":pp");require(extension.serverPackets==0,"preview-only command must be local");
      extension.chat(":a");
      require(importer.getState()==GPresetImporter.BuildingImportState.NONE && extension.session.isClosed(),"abort invalidates preview");
      require(!extension.confirm.getAsBoolean() && extension.serverPackets==0,"late confirmation after abort must not start import");
      extension.chat(":ip");extension.room=43;
      require(!extension.ready.getAsBoolean() && !extension.confirm.getAsBoolean(),"room change blocks actual importer confirmation");
      require(extension.serverPackets==0,"room-change rejection must send no packets");extension.cancel.run();
      extension.chat(":ip");importer.setPresetConfig(new PresetConfig(preset.toJsonObject()));
      require(!extension.confirm.getAsBoolean() && extension.serverPackets==0,"selection change blocks actual importer confirmation");extension.cancel.run();
      System.out.println("PASS: real import command preview gate, cancel, abort and stale contexts send no game packets");
   }
}
