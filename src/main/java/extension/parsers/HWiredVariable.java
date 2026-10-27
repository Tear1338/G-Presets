package extension.parsers;

import gearth.protocol.HPacket;
import java.util.HashMap;
import utils.Utils;

public class HWiredVariable {
   public final String id;
   public final VariableInternalType variableInternalType;
   public final int variableInternalTypeId;
   public final String name;
   public final int availabilityType;
   public final int variableType;
   public final boolean alwaysAvailable;
   public final boolean canCreateAndDelete;
   public final boolean hasValue;
   public final boolean canWriteValue;
   public final boolean canInterceptChanges;
   public final boolean isInvisible;
   public final boolean canReadCreationTime;
   public final boolean canReadLastUpdateTime;
   public HashMap<Integer, String> textConnector;
   public final boolean requiresExecutionContext;
   public String textConnectorFallback;

   public HWiredVariable(HPacket packet) {
      this(packet, !Boolean.getBoolean("gpresets.legacyVariables"));
   }

   /** Modern layout verified against the installed 2026-10-20 HabboAir.swf constructor. */
   public HWiredVariable(HPacket packet, boolean modernLayout) {
      this.id = Utils.readString(packet);
      this.variableInternalTypeId = packet.readInteger();
      this.variableInternalType = VariableInternalType.fromInt(this.variableInternalTypeId);
      this.name = Utils.readString(packet);
      Utils.requireBytes(packet, modernLayout ? 18 : 17);
      this.availabilityType = packet.readInteger();
      this.variableType = packet.readInteger();
      this.alwaysAvailable = packet.readBoolean();
      this.canCreateAndDelete = packet.readBoolean();
      this.hasValue = packet.readBoolean();
      this.canWriteValue = packet.readBoolean();
      this.canInterceptChanges = packet.readBoolean();
      this.isInvisible = packet.readBoolean();
      this.requiresExecutionContext = modernLayout && packet.readBoolean();
      this.canReadCreationTime = packet.readBoolean();
      this.canReadLastUpdateTime = packet.readBoolean();
      if (packet.readBoolean()) {
         this.textConnector = new HashMap();
         int textConnectorCount = Utils.readCount(packet, 6);

         for(int i = 0; i < textConnectorCount; ++i) {
            this.textConnector.put(packet.readInteger(), Utils.readString(packet));
         }
         if (modernLayout) {
            Utils.requireBytes(packet, 1);
            if (packet.readBoolean()) this.textConnectorFallback = Utils.readString(packet);
         }
      }

   }
}
