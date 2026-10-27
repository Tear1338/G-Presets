package extension.tools.preview;

import extension.tools.presetconfig.PresetConfigUtils;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Small, independent background budget for pinned official furniture geometry. */
public final class FurnitureGeometryStore {
   private FurnitureGeometryStore() { }
   private static final Map<String,CompletableFuture<FurnitureAssetGeometry>> entries=new ConcurrentHashMap<>();
   private static final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"gpresets-furniture-geometry");t.setDaemon(true);return t;});
   static {workers.allowCoreThreadTimeOut(true);}
   public static CompletableFuture<FurnitureAssetGeometry> load(String classname,int revision) {
      if(classname==null || !classname.matches("[A-Za-z0-9_.*-]{1,120}") || revision<1)return CompletableFuture.failedFuture(new IOException("Furniture revision unavailable"));
      String base=classname.split("\\*",2)[0],key=base+"-"+revision;
      CompletableFuture<FurnitureAssetGeometry> future=new CompletableFuture<>(),existing=entries.putIfAbsent(key,future);if(existing!=null)return existing;
      if(entries.size()>512){entries.remove(key,future);future.completeExceptionally(new IOException("Geometry cache budget reached"));return future;}
      try {workers.execute(()->{
         try {
            Path directory=Path.of(PresetConfigUtils.baseDir(),"cache","geometry-v1"),file=directory.resolve(key+".swf");byte[] bytes=null;
            if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && Files.size(file)<=8_000_000)bytes=Files.readAllBytes(file);
            FurnitureAssetGeometry geometry=null;
            if(bytes!=null)try{geometry=FurnitureAssetGeometry.parse(base,bytes);}catch(Exception invalid){bytes=null;}
            if(bytes==null) {
               HttpURLConnection connection=(HttpURLConnection)new URL("https://images.habbo.com/dcr/hof_furni/"+revision+"/"+base+".swf").openConnection();
               connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setRequestProperty("User-Agent","G-Presets/1.6.2 (preview geometry)");
               try {if(connection.getResponseCode()!=200 || connection.getContentLengthLong()>8_000_000)throw new IOException("Furniture geometry unavailable");try(InputStream in=connection.getInputStream()){bytes=in.readNBytes(8_000_001);}}finally{connection.disconnect();}
               geometry=FurnitureAssetGeometry.parse(base,bytes);
               try {Files.createDirectories(directory);
               // Keep this optional cache within a fixed disk budget. Geometry remains usable if caching fails.
               try(var files=Files.list(directory)) {
                  List<Path> cached=files.filter(f->f.getFileName().toString().endsWith(".swf")).sorted(Comparator.comparingLong(f->{try{return Files.getLastModifiedTime(f).toMillis();}catch(IOException e){return 0L;}})).toList();
                  long total=0;for(Path f:cached)total+=Files.size(f);
                  for(Path f:cached){if(total+bytes.length<=32L*1024*1024)break;long length=Files.size(f);Files.deleteIfExists(f);total-=length;}
               }
               Path temporary=Files.createTempFile(directory,"geometry-",".tmp");try{Files.write(temporary,bytes);Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
               }catch(IOException cacheUnavailable){ /* Optional disk cache; keep parsed geometry. */ }
            }
            future.complete(geometry);
         }catch(Exception error){future.completeExceptionally(error);}
      });}catch(RejectedExecutionException full){entries.remove(key,future);future.completeExceptionally(full);}
      return future;
   }
}
