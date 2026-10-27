package extension.tools.presetconfig.wired.incoming;

import extension.parsers.HWiredContext;
import gearth.protocol.HPacket;
import extension.tools.presetconfig.wired.PresetWiredBase;
import java.util.stream.Collectors;
import java.util.List;
import utils.Utils;

public interface RetrievedWired {
   int getTypeId();

   static <T extends RetrievedWired> T fromPacket(HPacket packet, Class<T> cls) {
      packet.readInteger();
      List<Integer> items = Utils.readIntList(packet);
      List<Integer> secondItems = Utils.readIntList(packet);
      int typeId = packet.readInteger();
      int wiredId = packet.readInteger();
      String configString = packet.readString();
      List<Integer> options = Utils.readIntList(packet);
      List<String> variableIds = Utils.readStringList(packet);
      List<Integer> furniSources = Utils.readIntList(packet);
      List<Integer> userSources = Utils.readIntList(packet);
      packet.readInteger();
      DefinitionSpecifics specifics = new DefinitionSpecifics();
      if (cls == RetrievedWiredEffect.class) {
         specifics.delay = packet.readInteger();
      } else if (cls == RetrievedWiredCondition.class) {
         specifics.quantifier = packet.readInteger();
      } else if (cls == RetrievedWiredSelector.class) {
         specifics.filter = packet.readBoolean();
         specifics.invert = packet.readBoolean();
      }

      boolean advancedMode = packet.readBoolean();
      readAllowedSources(packet);
      readAllowedSources(packet);
      readDefaultSources(packet);
      readDefaultSources(packet);
      boolean allowWallFurni = packet.readBoolean();
      if (cls == RetrievedWiredCondition.class) {
         specifics.quantifierType = packet.readByte();
         specifics.isInvert = packet.readBoolean();
      }

      HWiredContext wiredContext = null;
      if (packet.isEOF() == 0) {
         wiredContext = new HWiredContext(packet);
      }

      if (packet.isEOF() == 0) {
         Utils.readIntList(packet); // optional default integer parameters, following the context
      }
      if (packet.isCorrupted() || packet.isEOF() != 1) {
         throw new IllegalArgumentException("Unsupported incoming WIRED configuration layout");
      }

      if (cls == RetrievedWiredAddon.class) {
         return preserveExternalReferences(wiredContext, cls.cast(new RetrievedWiredAddon(wiredId, options, configString, items, secondItems, typeId, furniSources, userSources, variableIds, wiredContext)));
      } else if (cls == RetrievedWiredTrigger.class) {
         return preserveExternalReferences(wiredContext, cls.cast(new RetrievedWiredTrigger(wiredId, options, configString, items, secondItems, typeId, furniSources, userSources, variableIds, wiredContext)));
      } else if (cls == RetrievedWiredEffect.class) {
         return preserveExternalReferences(wiredContext, cls.cast(new RetrievedWiredEffect(wiredId, options, configString, items, secondItems, specifics.delay, typeId, furniSources, userSources, variableIds, wiredContext)));
      } else if (cls == RetrievedWiredCondition.class) {
         return preserveExternalReferences(wiredContext, cls.cast(new RetrievedWiredCondition(wiredId, options, configString, items, secondItems, typeId, specifics.quantifier, furniSources, userSources, variableIds, wiredContext)));
      } else if (cls == RetrievedWiredSelector.class) {
         return preserveExternalReferences(wiredContext, cls.cast(new RetrievedWiredSelector(wiredId, options, configString, items, secondItems, typeId, specifics.filter, specifics.invert, furniSources, userSources, variableIds, wiredContext)));
      } else if (cls == RetrievedWiredVariable.class) {
         return preserveExternalReferences(wiredContext, cls.cast(new RetrievedWiredVariable(wiredId, options, configString, items, secondItems, typeId, furniSources, userSources, variableIds, wiredContext)));
      }
      throw new IllegalArgumentException("Unsupported WIRED family: " + cls.getName());
   }

   static <T extends RetrievedWired> T preserveExternalReferences(HWiredContext context, T wired) {
      if (context != null && context.referenceVariablesList != null) {
         ((PresetWiredBase)wired).setExternalVariableIds(context.referenceVariablesList.sharedVariables.stream()
            .map(shared -> shared.wiredVariable.id).collect(Collectors.toList()));
      }
      return wired;
   }

   static void readAllowedSources(HPacket packet) {
      int count = Utils.readCount(packet, 4);

      for(int i = 0; i < count; ++i) {
         Utils.readIntList(packet);
      }

   }

   static void readDefaultSources(HPacket packet) {
      Utils.readIntList(packet);
   }

   public static class DefinitionSpecifics {
      public int quantifier;
      public int delay;
      public boolean filter;
      public boolean invert;
      public byte quantifierType;
      public boolean isInvert;
   }
}
