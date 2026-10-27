package extension.tools.presetconfig.wired.incoming;

import extension.parsers.HWiredContext;
import extension.tools.presetconfig.wired.PresetWiredTrigger;
import gearth.protocol.HPacket;
import java.util.List;

public class RetrievedWiredTrigger extends PresetWiredTrigger implements RetrievedWired {
   private final int typeId;

   public RetrievedWiredTrigger(int wiredId, List<Integer> options, String stringConfig, List<Integer> items, List<Integer> secondItems, int typeId, List<Integer> pickedFurniSources, List<Integer> pickedUserSources, List<String> variableIds, HWiredContext wiredContext) {
      super(wiredId, options, stringConfig, items, secondItems, pickedFurniSources, pickedUserSources, variableIds);
      this.typeId = typeId;
   }

   public int getTypeId() {
      return this.typeId;
   }

   public static RetrievedWiredTrigger fromPacket(HPacket packet) {
      return (RetrievedWiredTrigger)RetrievedWired.fromPacket(packet, RetrievedWiredTrigger.class);
   }
}
