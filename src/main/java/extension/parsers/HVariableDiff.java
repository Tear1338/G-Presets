package extension.parsers;

import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.List;
import utils.Utils;

/** Shared codec: variable IDs are protocol strings, including removed IDs. */
public final class HVariableDiff {
   public final int hash;
   public final boolean lastChunk;
   public final List<String> removed;
   public final List<HWiredVariable> updated = new ArrayList<>();

   public HVariableDiff(HPacket packet) {
      Utils.requireBytes(packet, 5);
      hash = packet.readInteger();
      lastChunk = packet.readBoolean();
      removed = Utils.readStringList(packet);
      int count = Utils.readCount(packet, 4);
      for (int i = 0; i < count; i++) {
         packet.readInteger(); // added/updated marker
         updated.add(new HWiredVariable(packet));
      }
      if (packet.isCorrupted() || packet.isEOF() != 1) {
         throw new IllegalArgumentException("Unsupported WiredAllVariablesDiffs layout");
      }
   }
}
