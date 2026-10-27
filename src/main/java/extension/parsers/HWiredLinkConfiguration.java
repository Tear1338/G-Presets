package extension.parsers;

import extension.tools.presetconfig.wired.PresetWiredLink;
import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.List;
import utils.Utils;

/** Layout checked against WiredLinkConfigurationMessageParser in WIN63-202610201032. */
public final class HWiredLinkConfiguration {
   public final int roomId;
   public final PresetWiredLink configuration;
   public final List<Network> networks = new ArrayList<>();

   public HWiredLinkConfiguration(HPacket packet) {
      Utils.requireBytes(packet, 8);
      roomId = packet.readInteger();
      int furniId = packet.readInteger();
      String selectedNetwork = Utils.readString(packet);
      Utils.requireBytes(packet, 1);
      configuration = new PresetWiredLink(furniId, selectedNetwork, packet.readBoolean());
      int count = Utils.readCount(packet, 9);
      for (int i = 0; i < count; i++) {
         String id = Utils.readString(packet);
         String name = Utils.readString(packet);
         Utils.requireBytes(packet, 5);
         networks.add(new Network(id, name, packet.readInteger(), packet.readBoolean()));
      }
      if (packet.isCorrupted() || packet.isEOF() != 1) throw new IllegalArgumentException("Unsupported WIRED Link configuration layout");
   }

   public boolean hasNetwork(String id) {
      return "0".equals(id) || networks.stream().anyMatch(network -> network.id.equals(id));
   }

   public static final class Network {
      public final String id;
      public final String name;
      public final int connectionCount;
      public final boolean linkedInCurrentRoom;
      private Network(String id, String name, int count, boolean linked) {
         this.id=id; this.name=name; this.connectionCount=count; this.linkedInCurrentRoom=linked;
      }
   }
}
