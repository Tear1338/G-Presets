package extension.parsers;

import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.List;
import utils.Utils;

/** WIRED context tag 8. This is editor metadata, not the saved leaderboard configuration. */
public final class HVariableLeaderboardContext {
   public final int overlayInstanceId;
   public final int tileX;
   public final int tileY;
   public final int totalCandidateCount;
   public final List<Choice> choices = new ArrayList<>();

   public HVariableLeaderboardContext(HPacket packet) {
      Utils.requireBytes(packet, 16);
      overlayInstanceId = packet.readInteger();
      tileX = packet.readInteger();
      tileY = packet.readInteger();
      totalCandidateCount = packet.readInteger();
      int count = Utils.readCount(packet, 10);
      for (int i = 0; i < count; i++) {
         String id = Utils.readString(packet);
         String name = Utils.readString(packet);
         Utils.requireBytes(packet, 6);
         choices.add(new Choice(id, name, packet.readBoolean(), packet.readBoolean(), packet.readInteger()));
      }
   }

   public static final class Choice {
      public final String variableId;
      public final String technicalName;
      public final boolean persistent;
      public final boolean hasValue;
      public final int applicableLevelUpCount;

      private Choice(String id, String name, boolean persistent, boolean hasValue, int levelUps) {
         this.variableId = id;
         this.technicalName = name;
         this.persistent = persistent;
         this.hasValue = hasValue;
         this.applicableLevelUpCount = levelUps;
      }
   }
}
