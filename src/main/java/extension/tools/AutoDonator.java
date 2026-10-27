package extension.tools;

import extension.GPresets;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.furni.PresetFurni;
import furnidata.FurniDataTools;
import game.Inventory;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import utils.Utils;

public class AutoDonator {
   private final GPresets extension;
   private volatile AutoDonateState state;

   public AutoDonator(GPresets extension) {
      this.state = AutoDonator.AutoDonateState.NONE;
      this.extension = extension;
      extension.intercept(HMessage.Direction.TOSERVER, "Chat", this::onChat);
      extension.intercept(HMessage.Direction.TOSERVER, "SelfDonateItem", this::onSelfDonateItem);
      extension.intercept(HMessage.Direction.TOCLIENT, "SelfDonationResult", this::onSelfDonationResult);
   }

   private void onChat(HMessage hMessage) {
      String text = hMessage.getPacket().readString();
      if (text.equals(":abort") || text.equals(":a")) {
         hMessage.setBlocked(true);
         if (this.state != AutoDonator.AutoDonateState.NONE) {
            this.state = AutoDonator.AutoDonateState.NONE;
            this.extension.sendVisualChatInfo("Aborted auto donate");
         }
      }

   }

   private void onSelfDonateItem(HMessage hMessage) {
      if (this.state == AutoDonator.AutoDonateState.SELF_DONATING) {
         this.extension.sendVisualChatInfo("Please wait for the auto donate to finish before donating yourself furni!");
         hMessage.setBlocked(true);
      }

   }

   private void onSelfDonationResult(HMessage hMessage) {
      if (this.state == AutoDonator.AutoDonateState.SELF_DONATING) {
         hMessage.setBlocked(true);
      }

   }

   public boolean isDonating() {
      return this.state == AutoDonator.AutoDonateState.SELF_DONATING;
   }

   public void donateAll(boolean missingOnly) {
      if (this.isDonating()) {
         this.extension.getLogger().log("Already donating, wait for it to finish, or say :abort in game!", "red");
      } else {
         PresetConfig preset = this.extension.getImporter().getPresetConfig();
         if (preset == null) {
            this.extension.getLogger().log("Failed to start auto donate, no preset selected", "red");
         } else {
            this.state = AutoDonator.AutoDonateState.SELF_DONATING;
            Map<Integer, Integer> countByTypeId = this.countFloorItemsByTypeId(preset.getFurniture());
            if (missingOnly) {
               Inventory inventory = this.extension.getInventory();
               if (inventory == null) {
                  this.extension.getLogger().log("Failed to start auto donate, inventory is not loaded", "red");
                  return;
               }

               countByTypeId = (Map)countByTypeId.entrySet().stream().peek((entry) -> entry.setValue((Integer)entry.getValue() - inventory.getFloorItemsByType((Integer)entry.getKey()).size())).filter((entry) -> (Integer)entry.getValue() > 0).collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));
               this.extension.getLogger().log("Donating missing furni, wait for it to finish", "orange");
            } else {
               this.extension.getLogger().log("Donating all, wait for it to finish", "orange");
            }

            this.donateFloorFurni(countByTypeId);
         }
      }
   }

   public Map<Integer, Integer> countFloorItemsByTypeId(List<PresetFurni> presetFurniList) {
      Map<Integer, Integer> map = new HashMap();
      FurniDataTools furniDataTools = this.extension.getFurniDataTools();

      for(PresetFurni presetFurni : presetFurniList) {
         int classId = furniDataTools.getFloorTypeId(presetFurni.getClassName());
         map.putIfAbsent(classId, 0);
         map.compute(classId, (k, v) -> v + 1);
      }

      return map;
   }

   private void donateFloorFurni(Map<Integer, Integer> countsByTypeId) {
      (new Thread(() -> {
         int counted = 0;
         int totalCount = (Integer)countsByTypeId.values().stream().reduce(0, Integer::sum);
         int lastReportedCount = 0;

         for(Map.Entry<Integer, Integer> entry : countsByTypeId.entrySet()) {
            if (this.state == AutoDonator.AutoDonateState.NONE) {
               this.extension.getLogger().log("Aborted auto donate", "red");
               return;
            }

            this.donateFurni(false, (Integer)entry.getKey(), "", (Integer)entry.getValue());
            counted += (Integer)entry.getValue();
            if (counted - lastReportedCount > 50) {
               lastReportedCount = counted;
               this.extension.getLogger().log(String.format("Donated %d/%d floor items", counted, totalCount), "orange");
            }

            Utils.sleep(500);
         }

         this.extension.getLogger().log("Donated all (donatable) floor items", "green");
         this.state = AutoDonator.AutoDonateState.NONE;
      })).start();
   }

   private void donateFurni(boolean isWallItem, int typeId, String special, int amount) {
      this.extension.sendToServer(new HPacket("SelfDonateItem", HMessage.Direction.TOSERVER, new Object[]{isWallItem, typeId, special, amount}));
   }

   public static enum AutoDonateState {
      NONE,
      SELF_DONATING;

      // $FF: synthetic method
      private static AutoDonateState[] $values() {
         return new AutoDonateState[]{NONE, SELF_DONATING};
      }
   }
}
