package extension.parsers;

import gearth.protocol.HPacket;
import java.util.HashSet;

public class HSharedVariableList {
   public final HashSet<HSharedVariable> sharedVariables;

   public HSharedVariableList(HPacket packet) {
      int count = packet.readInteger();
      this.sharedVariables = new HashSet();

      for(int i = 0; i < count; ++i) {
         this.sharedVariables.add(new HSharedVariable(packet));
      }

   }
}
