package extension.tools.preview;

import extension.tools.presetconfig.*;
import javafx.scene.image.Image;
import java.net.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** One image cache and download budget for every preview/share window. */
public final class FurnitureSprites {
   private FurnitureSprites() { }
   private static final AtomicLong blockedUntil=new AtomicLong();
   private static final class Holder {
      static final FurnitureSpriteStore<Image> STORE=new FurnitureSpriteStore<>(Path.of(PresetConfigUtils.baseDir(),"cache","furniture-v1"),FurnitureSprites::download,bytes->{
         Image image=new Image(new ByteArrayInputStream(bytes));
         if(image.isError() || image.getWidth()<1)throw new IllegalArgumentException("Image could not be decoded");return image;
      });
   }
   public static FurnitureSpriteStore<Image> shared(){return Holder.STORE;}
   public static void preload(PresetConfig preset) {
      LinkedHashSet<FurnitureSpriteStore.Key> keys=new LinkedHashSet<>();
      for(var item:preset.getFurniture())try{keys.add(new FurnitureSpriteStore.Key(item.getClassName(),item.getRotation(),item.getState()));}catch(IllegalArgumentException ignored){}
      for(var item:preset.getWallFurniture())try{keys.add(new FurnitureSpriteStore.Key(item.getClassName(),item.getLocation().getDirection()=='l'?2:4,item.getState()));}catch(IllegalArgumentException ignored){}
      keys.stream().limit(256).forEach(key->shared().load(key));
   }
   public static long retryDelayMillis(String header,long now) {
      if(header==null)return 30000;
      try{return Math.max(1,Math.min(300,Long.parseLong(header.trim())))*1000;}catch(NumberFormatException ignored){}
      try{return Math.max(1000,Math.min(300000,java.time.ZonedDateTime.parse(header,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now));}catch(RuntimeException ignored){return 30000;}
   }
   private static byte[] download(FurnitureSpriteStore.Key key) throws Exception {
      for(int attempt=0;attempt<3;attempt++) {
         long remaining;while((remaining=blockedUntil.get()-System.currentTimeMillis())>0)Thread.sleep(Math.min(1000,remaining));
         HttpURLConnection connection=(HttpURLConnection)new URL(key.url()).openConnection();
         connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setRequestProperty("Accept","image/png");
         connection.setRequestProperty("User-Agent","G-Presets/1.5.2 (furniture preview)");
         try {
            int status=connection.getResponseCode();
            if(status==429 || status==503) {
               long resume=System.currentTimeMillis()+retryDelayMillis(connection.getHeaderField("Retry-After"),System.currentTimeMillis());blockedUntil.accumulateAndGet(resume,Math::max);continue;
            }
            if(status!=200)throw new IOException("Image server returned HTTP "+status);
            if(connection.getContentLengthLong()>2000000)throw new IOException("Image too large");
            try(InputStream input=connection.getInputStream()){return input.readNBytes(2000001);}
         }finally{connection.disconnect();}
      }
      throw new IOException("Image server is busy; reopen the preview to retry");
   }
}
