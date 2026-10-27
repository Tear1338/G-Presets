package extension.tools.preview;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/** Bounded shared cache. All disk access, downloading and decoding happen off the UI thread. */
public final class FurnitureSpriteStore<T> implements AutoCloseable {
   public record Key(String classname,int direction,String state) {
      public Key {
         if(classname==null || !classname.matches("[A-Za-z0-9_.*-]{1,120}"))throw new IllegalArgumentException("Invalid furniture class");
         direction=Math.floorMod(direction,8);
         state=state!=null && state.matches("[0-9]{1,3}")?Integer.toString(Integer.parseInt(state)):"0";
      }
      public String url(){return "https://www.habboassets.com/imaging/furniture/"+classname+".png?size=64&direction="+direction+"&state="+state;}
   }
   @FunctionalInterface public interface Fetcher {byte[] fetch(Key key) throws Exception;}
   private record Entry<T>(T value,long time,long cost) { }
   private static final long TTL=Duration.ofDays(7).toMillis(),MEMORY_LIMIT=64L*1024*1024,DISK_LIMIT=128L*1024*1024;
   private final Path directory;
   private final Fetcher fetcher;
   private final Function<byte[],T> decoder;
   private final ThreadPoolExecutor workers;
   private final Map<Key,CompletableFuture<T>> pending=new ConcurrentHashMap<>();
   private final Map<Key,Long> failures=new ConcurrentHashMap<>();
   private final LinkedHashMap<Key,Entry<T>> memory=new LinkedHashMap<>(32,0.75f,true);
   private long memoryBytes;
   private final AtomicLong nextPrune=new AtomicLong();
   public FurnitureSpriteStore(Path directory,Fetcher fetcher,Function<byte[],T> decoder) {
      this.directory=directory.toAbsolutePath().normalize();this.fetcher=fetcher;this.decoder=decoder;
      workers=new ThreadPoolExecutor(4,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2048),r->{Thread t=new Thread(r,"gpresets-furniture-cache");t.setDaemon(true);return t;});
      workers.allowCoreThreadTimeOut(true);
   }
   public synchronized T peek(Key key) {
      Entry<T> entry=memory.get(key);
      if(entry==null)return null;
      if(System.currentTimeMillis()-entry.time()>TTL){memory.remove(key);memoryBytes-=entry.cost();return null;}
      return entry.value();
   }
   public CompletableFuture<T> load(Key key) {
      T ready=peek(key);if(ready!=null)return CompletableFuture.completedFuture(ready);
      if(failures.getOrDefault(key,0L)>System.currentTimeMillis())return CompletableFuture.failedFuture(new IOException("Image temporarily unavailable"));
      CompletableFuture<T> future=new CompletableFuture<>(),existing=pending.putIfAbsent(key,future);
      if(existing!=null)return existing;
      try {workers.execute(()->{
         try {
            T cached=peek(key);if(cached!=null){future.complete(cached);return;}
            byte[] bytes=readDisk(key);T value=null;
            if(bytes!=null)try{value=decoder.apply(bytes);}catch(RuntimeException corrupt){bytes=null;}
            if(bytes==null){bytes=fetcher.fetch(key);validate(bytes);value=decoder.apply(bytes);writeDisk(key,bytes);}
            remember(key,value,bytes);failures.remove(key);future.complete(value);
         }catch(Exception error){if(failures.size()>=512)failures.clear();failures.put(key,System.currentTimeMillis()+5000);future.completeExceptionally(error);}
         finally{pending.remove(key,future);}
      });}catch(RejectedExecutionException full){pending.remove(key,future);future.completeExceptionally(full);}
      return future;
   }
   private synchronized void remember(Key key,T value,byte[] png) throws IOException {
      if(value==null)throw new IOException("Image decoding failed");
      int[] dimensions=validate(png);long cost=(long)dimensions[0]*dimensions[1]*4+png.length;
      Entry<T> previous=memory.put(key,new Entry<>(value,System.currentTimeMillis(),cost));
      if(previous!=null)memoryBytes-=previous.cost();memoryBytes+=cost;
      var entries=memory.entrySet().iterator();
      while(memoryBytes>MEMORY_LIMIT && entries.hasNext()){memoryBytes-=entries.next().getValue().cost();entries.remove();}
   }
   public Path cacheFile(Key key) {
      try {String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.url().getBytes(java.nio.charset.StandardCharsets.UTF_8)));return directory.resolve(hash+".png");}
      catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
   }
   private byte[] readDisk(Key key) {
      Path file=cacheFile(key);
      try {
         if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || System.currentTimeMillis()-Files.getLastModifiedTime(file).toMillis()>TTL || Files.size(file)>2000000)return null;
         byte[] bytes;try(InputStream input=Files.newInputStream(file)){bytes=input.readNBytes(2000001);}validate(bytes);return bytes;
      }catch(IOException | RuntimeException error){return null;}
   }
   private void writeDisk(Key key,byte[] bytes) {
      Path temporary=null;
      try {
         Files.createDirectories(directory);temporary=Files.createTempFile(directory,"download-",".tmp");Files.write(temporary,bytes);
         try{Files.move(temporary,cacheFile(key),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
         catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,cacheFile(key),StandardCopyOption.REPLACE_EXISTING);}
         prune();
      }catch(IOException | SecurityException ignored){/* A read-only cache must not prevent showing a downloaded image. */}
      finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
   }
   private void prune() throws IOException {
      long now=System.currentTimeMillis(),next=nextPrune.get();
      if(now<next || !nextPrune.compareAndSet(next,now+300000))return;
      record DiskEntry(Path file,long modified,long size) { }
      List<DiskEntry> entries=new ArrayList<>();
      try(var stream=Files.newDirectoryStream(directory,"*.png")){for(Path file:stream){
         if(!file.getFileName().toString().matches("[0-9a-f]{64}\\.png") || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
         var attributes=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
         entries.add(new DiskEntry(file,attributes.lastModifiedTime().toMillis(),attributes.size()));
      }}
      entries.sort(Comparator.comparingLong(DiskEntry::modified));long total=entries.stream().mapToLong(DiskEntry::size).sum();int count=entries.size();
      for(DiskEntry entry:entries){if(total<=DISK_LIMIT && count<=2048)break;Files.deleteIfExists(entry.file());total-=entry.size();count--;}
   }
   private static int[] validate(byte[] bytes) throws IOException {
      byte[] signature={(byte)137,80,78,71,13,10,26,10};
      if(bytes==null || bytes.length<24 || bytes.length>2000000)throw new IOException("Invalid image size");
      for(int i=0;i<signature.length;i++)if(bytes[i]!=signature[i])throw new IOException("Expected PNG image");
      var header=java.nio.ByteBuffer.wrap(bytes);int w=header.getInt(16),h=header.getInt(20);
      if(w<1 || h<1 || w>2048 || h>2048)throw new IOException("Invalid image dimensions");
      return new int[]{w,h};
   }
   @Override public void close(){workers.shutdownNow();for(var future:pending.values())future.completeExceptionally(new CancellationException("Cache closed"));pending.clear();}
}
