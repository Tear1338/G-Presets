package extension.tools.sharing;

import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class PreviewImages {
   private PreviewImages() { }
   public static String png(Canvas canvas) throws java.io.IOException {
      WritableImage image=canvas.snapshot(null,null);
      int w=(int)image.getWidth(),h=(int)image.getHeight();
      if(w>4096 || h>4096)throw new IllegalArgumentException("Preview is too large");
      BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
      var pixels=image.getPixelReader();
      for(int y=0;y<h;y++)for(int x=0;x<w;x++)result.setRGB(x,y,pixels.getArgb(x,y));
      ByteArrayOutputStream output=new ByteArrayOutputStream();
      javax.imageio.ImageIO.write(result,"png",output);
      return Base64.getEncoder().encodeToString(output.toByteArray());
   }
}
