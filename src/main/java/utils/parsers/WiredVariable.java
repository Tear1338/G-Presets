package utils.parsers;

import gearth.protocol.HPacket;
import java.util.HashMap;

public class WiredVariable {
   private long variableId;
   private String variableName;
   private int availabilityType;
   private int variableType;
   private boolean alwaysAvailable;
   private boolean canCreateAndDelete;
   private boolean hasValue;
   private boolean canWriteValue;
   private boolean canInterceptChanges;
   private boolean isInvisible;
   private boolean canReadCreationTime;
   private boolean canReadLastUpdateTime;
   private HashMap<Integer, String> textConnector;

   public WiredVariable(HPacket packet) {
      this.variableId = packet.readLong();
      this.variableName = packet.readString();
      this.availabilityType = packet.readInteger();
      this.variableType = packet.readInteger();
      this.alwaysAvailable = packet.readBoolean();
      this.canCreateAndDelete = packet.readBoolean();
      this.hasValue = packet.readBoolean();
      this.canWriteValue = packet.readBoolean();
      this.canInterceptChanges = packet.readBoolean();
      this.isInvisible = packet.readBoolean();
      this.canReadCreationTime = packet.readBoolean();
      this.canReadLastUpdateTime = packet.readBoolean();
      boolean hasTextConnector = packet.readBoolean();
      if (hasTextConnector) {
         int count = packet.readInteger();
         this.textConnector = new HashMap();

         for(int i = 0; i < count; ++i) {
            this.textConnector.put(packet.readInteger(), packet.readString());
         }
      }

   }
}
