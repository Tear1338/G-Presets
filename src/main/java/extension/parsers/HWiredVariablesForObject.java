package extension.parsers;

import gearth.protocol.HPacket;
import utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HWiredVariablesForObject {
   public static final int TYPE_OBJECT = 0;
   public static final int TYPE_USER = 1;
   public final int type;
   public final int objectId;
   public final int userIndex;
   public final Map<String, Integer> variables;
   public final List<Integer> configuredInWireds;

   public HWiredVariablesForObject(HPacket packet) {
      Utils.requireBytes(packet,8);
      this.type = packet.readInteger();
      if (this.type == 0) {
         this.objectId = packet.readInteger();
         this.userIndex = -1;
      } else if (this.type == 1) {
         this.userIndex = packet.readInteger();
         this.objectId = -1;
      } else if(this.type == -10) {
         this.objectId=0; this.userIndex=-1;
      } else {
         throw new IllegalArgumentException("Unknown inspection object type " + this.type);
      }

      this.variables = new HashMap();
      int count = Utils.readCount(packet,6);

      for(int i = 0; i < count; ++i) {
         String id=Utils.readString(packet); Utils.requireBytes(packet,4);
         this.variables.put(id, packet.readInteger());
      }

      if (this.type == 0) {
         int wiredCount = Utils.readCount(packet,4);
         this.configuredInWireds = new ArrayList(wiredCount);

         for(int i = 0; i < wiredCount; ++i) {
            this.configuredInWireds.add(packet.readInteger());
         }
      } else {
         this.configuredInWireds = new ArrayList();
      }
      if(packet.isCorrupted() || packet.isEOF()!=1) throw new IllegalArgumentException("Unknown variable inspection layout");
   }
}
