package utils;

import gearth.protocol.HPacket;
import java.util.ArrayList;
import java.util.List;

public class Utils {
   private static volatile int extraSleepTime = 0;

   public static void sleep(int ms) {
      try {
         Thread.sleep((long)(ms + extraSleepTime));
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

   }

   public static void setExtraSleepTime(int extraSleepTime) {
      Utils.extraSleepTime = Math.max(0, extraSleepTime);
   }

   /** Validate against remaining bytes before allocating or looping over a packet count. */
   public static int readCount(HPacket packet, int minimumBytesPerEntry) {
      requireBytes(packet, 4);
      int count = packet.readInteger();
      int remaining = packet.getBytesLength() - packet.getReadIndex();
      if (count < 0 || count > remaining / minimumBytesPerEntry) {
         throw new IllegalArgumentException("Invalid packet list count: " + count);
      }
      return count;
   }

   public static void requireBytes(HPacket packet, int count) {
      if (count < 0 || packet.getReadIndex() > packet.getBytesLength() - count) {
         throw new IllegalArgumentException("Truncated packet at byte " + packet.getReadIndex());
      }
   }

   public static String readString(HPacket packet) {
      requireBytes(packet, 2);
      int index = packet.getReadIndex();
      int size = packet.readUshort();
      requireBytes(packet, size);
      packet.setReadIndex(index);
      return packet.readString();
   }

   public static List<Integer> readIntList(HPacket packet) {
      int size = readCount(packet, 4);
      List<Integer> intList = new ArrayList();

      for(int i = 0; i < size; ++i) {
         intList.add(packet.readInteger());
      }

      return intList;
   }

   public static List<String> readStringList(HPacket packet) {
      int size = readCount(packet, 2);
      List<String> stringList = new ArrayList();

      for(int i = 0; i < size; ++i) {
         stringList.add(readString(packet));
      }

      return stringList;
   }

   public static List<Long> readLongList(HPacket packet) {
      int size = readCount(packet, 8);
      List<Long> longList = new ArrayList();

      for(int i = 0; i < size; ++i) {
         longList.add(packet.readLong());
      }

      return longList;
   }
}
