package extension.tools.preview;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.postconfig.PostConfig;
import gearth.extensions.parsers.HPoint;
import org.json.JSONObject;

/** One-shot confirmation for the exact preset/options/room shown in the preview. */
public final class PresetPreviewSession {
   private final PresetConfig originalPreset;
   private final PostConfig originalOptions;
   private final String json;
   private final PostConfig options;
   public final int roomId;
   public final Integer rootX,rootY;
   private boolean closed;
   public PresetPreviewSession(PresetConfig preset,PostConfig post,int roomId,HPoint root) {
      originalPreset=preset;originalOptions=post;json=preset.toJsonObject().toString();options=new PostConfig(post);
      this.roomId=roomId;rootX=root==null?null:root.getX();rootY=root==null?null:root.getY();
   }
   public PresetConfig preset() { return new PresetConfig(new JSONObject(json)); }
   public PostConfig options() { return new PostConfig(options); }
   public HPoint root() { return rootX==null?null:new HPoint(rootX,rootY); }
   public synchronized boolean isCurrent(PresetConfig preset,PostConfig post,int room) { return !closed && originalPreset==preset && originalOptions==post && roomId==room; }
   public synchronized boolean confirm(PresetConfig preset,PostConfig post,int room,boolean ready,Runnable launch) {
      if(!ready || !isCurrent(preset,post,room))return false;
      closed=true;launch.run();return true;
   }
   public synchronized void cancel() { closed=true; }
   public synchronized boolean isClosed() { return closed; }
}
