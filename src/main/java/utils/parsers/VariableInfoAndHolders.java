package utils.parsers;

import gearth.protocol.HPacket;
import java.util.HashMap;
import java.util.Map;

public class VariableInfoAndHolders {
   private WiredVariable variable;
   private Map<Integer, Integer> holders;

   public VariableInfoAndHolders(HPacket packet) {
      this.variable = new WiredVariable(packet);
      int count = packet.readInteger();
      this.holders = new HashMap();

      for(int i = 0; i < count; ++i) {
         this.holders.put(packet.readInteger(), packet.readInteger());
      }

   }
}
