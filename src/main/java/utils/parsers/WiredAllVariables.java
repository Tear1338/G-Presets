package utils.parsers;

import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.List;

public class WiredAllVariables {
   private int unused;
   private List<WiredVariable> variables;

   public WiredAllVariables(HPacket packet) {
      this.unused = packet.readInteger();
      int count = packet.readInteger();
      this.variables = new ArrayList();

      for(int i = 0; i < count; ++i) {
         this.variables.add(new WiredVariable(packet));
      }

   }
}
