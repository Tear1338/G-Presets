package extension.tools.importutils;
import java.util.*;
import gearth.protocol.*;
/** Records only new, individually identified placements; never existing/reused furniture. */
public final class ImportUndoJournal {
 public record Entry(boolean wall,int id,int type,int owner,boolean bc) {}
 private int room,owner;private Set<Integer> beforeFloor=Set.of(),beforeWall=Set.of();
 private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
 public synchronized void begin(int room,int owner,Set<Integer> floor,Set<Integer> wall) {this.room=room;this.owner=owner;beforeFloor=Set.copyOf(floor);beforeWall=Set.copyOf(wall);entries.clear();}
 public synchronized int room(){return room;}
 public synchronized int owner(){return owner;}
 public synchronized boolean record(Entry entry) {
  if(room==0 || (entry.wall()?beforeWall:beforeFloor).contains(entry.id()))return false;
  if(entry.bc() && (owner<=0 || entry.owner()!=owner))return false;
  entries.put(key(entry),entry);return true;
 }
 public synchronized List<Entry> entries(){List<Entry> out=new ArrayList<>(entries.values());Collections.reverse(out);return List.copyOf(out);}
 public synchronized boolean eligible(Entry entry,int currentRoom,int currentOwner,int type,int itemOwner,boolean active) {
  return active && room!=0 && currentRoom==room && (owner<=0 || currentOwner==owner) && entry.equals(entries.get(key(entry))) && entry.type()==type && entry.owner()==itemOwner && !(entry.wall()?beforeWall:beforeFloor).contains(entry.id());
 }
 public synchronized void removed(Entry entry){entries.remove(key(entry));}
 public synchronized void clear(){entries.clear();room=0;owner=0;}
 private static String key(Entry entry){return (entry.wall()?"W:":"F:")+entry.id();}
 public static HPacket pickup(Entry entry){return new HPacket("PickupObject",HMessage.Direction.TOSERVER,entry.wall()?1:2,entry.id());}
}
