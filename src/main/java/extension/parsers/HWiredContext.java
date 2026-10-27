package extension.parsers;

import gearth.protocol.HPacket;
import utils.Utils;

public class HWiredContext {
   public HAllVariablesInRoom roomVariablesList;
   public HVariableInfoAndHolders furniVariableInfo;
   public HVariableInfoAndHolders userVariableInfo;
   public HVariableInfoAndValue globalVariableInfo;
   public HSharedVariableList referenceVariablesList;
   public HVariableList rulesetVariables;
   public HSharedGlobalPlaceholderList referencePlaceholderList;
   public HWiredTextPlaceholders textPlaceholders;
   public HVariableLeaderboardContext variableLeaderboard;

   public HWiredContext(HPacket packet) {
      int amount = Utils.readCount(packet, 4);

      for(int i = 0; i < amount; ++i) {
         int type = packet.readInteger();
         switch (type) {
            case 0:
               this.roomVariablesList = new HAllVariablesInRoom(packet);
               break;
            case 1:
               this.furniVariableInfo = new HVariableInfoAndHolders(packet);
               break;
            case 2:
               this.userVariableInfo = new HVariableInfoAndHolders(packet);
               break;
            case 3:
               this.globalVariableInfo = new HVariableInfoAndValue(packet);
               break;
            case 4:
               this.referenceVariablesList = new HSharedVariableList(packet);
               break;
            case 5:
               this.rulesetVariables = new HVariableList(packet);
               break;
            case 6:
               this.referencePlaceholderList = new HSharedGlobalPlaceholderList(packet);
               break;
            case 7:
               this.textPlaceholders = new HWiredTextPlaceholders(packet);
               break;
            case 8:
               this.variableLeaderboard = new HVariableLeaderboardContext(packet);
               break;
            default:
               throw new IllegalArgumentException("Unsupported WIRED context tag: " + type);
         }
      }

   }
}
