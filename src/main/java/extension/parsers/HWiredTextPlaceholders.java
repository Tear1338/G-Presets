package extension.parsers;

import gearth.protocol.HPacket;
import java.util.List;
import utils.Utils;

/** WIRED context tag 7: outputs and named text captures (October 2026 client). */
public final class HWiredTextPlaceholders {
   public final List<String> names;
   public final List<String> capturerNames;

   public HWiredTextPlaceholders(HPacket packet) {
      names = Utils.readStringList(packet);
      capturerNames = Utils.readStringList(packet);
   }
}
