package gearth.protocol;

import gearth.misc.StringifyAble;
import gearth.services.packet_info.PacketInfo;
import gearth.services.packet_info.PacketInfoManager;
import gearth.services.packet_representation.InvalidPacketException;
import gearth.services.packet_representation.PacketStringUtils;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Optional;

public class HPacket implements StringifyAble {
   protected boolean isEdited;
   protected HPacketFormat packetFormat;
   protected byte[] packetInBytes;
   protected int readIndex;
   private String identifier;
   private HMessage.Direction identifierDirection;

   public HPacket(byte[] packet) {
      this.isEdited = false;
      this.packetFormat = HPacketFormat.EVA_WIRE;
      this.readIndex = 6;
      this.identifier = null;
      this.identifierDirection = null;
      this.packetInBytes = packet.clone();
   }

   public HPacket(HPacket packet) {
      this.isEdited = false;
      this.packetFormat = HPacketFormat.EVA_WIRE;
      this.readIndex = 6;
      this.identifier = null;
      this.identifierDirection = null;
      this.packetInBytes = (byte[])packet.packetInBytes.clone();
      this.isEdited = packet.isEdited;
   }

   public HPacket(String packet) {
      this(packet, HPacketFormat.EVA_WIRE);
   }

   public HPacket(String packet, HPacketFormat format) {
      this.isEdited = false;
      this.packetFormat = HPacketFormat.EVA_WIRE;
      this.readIndex = 6;
      this.identifier = null;
      this.identifierDirection = null;

      try {
         HPacket packetFromString = PacketStringUtils.fromString(packet, format);
         this.packetInBytes = packetFromString.packetInBytes;
         this.identifier = packetFromString.identifier;
         this.identifierDirection = packetFromString.identifierDirection;
      } catch (InvalidPacketException var4) {
         this.packetInBytes = new byte[0];
      }

   }

   public HPacket(int header) {
      this.isEdited = false;
      this.packetFormat = HPacketFormat.EVA_WIRE;
      this.readIndex = 6;
      this.identifier = null;
      this.identifierDirection = null;
      this.initPacket(header);
      this.isEdited = false;
   }

   public HPacket(int header, byte[] bytes) {
      this(header);
      this.appendBytes(bytes);
      this.isEdited = false;
   }

   public HPacket(int header, Object... objects) throws InvalidParameterException {
      this(header);
      this.appendObjects(objects);
      this.isEdited = false;
   }

   public HPacket(String identifier, HMessage.Direction direction) throws InvalidParameterException {
      this.isEdited = false;
      this.packetFormat = HPacketFormat.EVA_WIRE;
      this.readIndex = 6;
      this.identifier = null;
      this.identifierDirection = null;
      this.initPacket(0);
      this.identifier = identifier;
      this.identifierDirection = direction;
   }

   public HPacket(String identifier, HMessage.Direction direction, Object... objects) throws InvalidParameterException {
      this(identifier, direction);
      this.appendObjects(objects);
      this.isEdited = false;
   }

   protected void initPacket(int header) {
      this.packetInBytes = new byte[]{0, 0, 0, 2, 0, 0};
      this.replacePacketId((short)header);
   }

   public HPacketFormat getFormat() {
      return this.packetFormat;
   }

   public String toString() {
      return PacketStringUtils.toString(this.packetInBytes);
   }

   public boolean structureEquals(String structure) {
      return PacketStringUtils.structureEquals(this, structure);
   }

   public int isEOF() {
      if (this.readIndex < this.getBytesLength()) {
         return 0;
      } else {
         return this.readIndex == this.getBytesLength() ? 1 : 2;
      }
   }

   public HPacket skip(String structure) {
      for(char c : structure.toCharArray()) {
         switch (c) {
            case 'B':
               this.readBoolean();
               break;
            case 'b':
               this.readByte();
               break;
            case 'd':
               this.readDouble();
               break;
            case 'i':
               this.readInteger();
               break;
            case 'l':
               this.readLong();
               break;
            case 's':
               this.readString();
               break;
            case 'u':
               this.readShort();
         }
      }

      return this;
   }

   public void setIdentifier(String identifier) {
      this.identifier = identifier;
   }

   public void setIdentifierDirection(HMessage.Direction identifierDirection) {
      this.identifierDirection = identifierDirection;
   }

   public String packetIncompleteIdentifier() {
      return this.identifier;
   }

   public void completePacket(PacketInfoManager packetInfoManager) {
      if (!this.isCorrupted() && this.identifier != null && packetInfoManager != null) {
         PacketInfo packetInfo = packetInfoManager.getPacketInfoFromName(this.identifierDirection, this.identifier);
         if (packetInfo == null) {
            packetInfo = packetInfoManager.getPacketInfoFromHash(this.identifierDirection, this.identifier);
            if (packetInfo == null) {
               return;
            }
         }

         boolean wasEdited = this.isEdited;
         this.replacePacketId((short)packetInfo.getHeaderId());
         this.identifier = null;
         this.isEdited = wasEdited;
      }
   }

   public boolean canSendToClient() {
      return this.identifierDirection == null || this.identifierDirection == HMessage.Direction.TOCLIENT;
   }

   public boolean canSendToServer() {
      return this.identifierDirection == null || this.identifierDirection == HMessage.Direction.TOSERVER;
   }

   public boolean canComplete(PacketInfoManager packetInfoManager) {
      if (!this.isCorrupted() && this.identifier != null) {
         PacketInfo packetInfo = packetInfoManager.getPacketInfoFromName(this.identifierDirection, this.identifier);
         if (packetInfo == null) {
            packetInfo = packetInfoManager.getPacketInfoFromHash(this.identifierDirection, this.identifier);
            return packetInfo != null;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean isPacketComplete() {
      return this.identifier == null;
   }

   public byte[] toBytes() {
      return this.packetInBytes;
   }

   public int getReadIndex() {
      return this.readIndex;
   }

   public void setReadIndex(int number) {
      this.readIndex = number;
   }

   public void resetReadIndex() {
      this.setReadIndex(6);
   }

   public boolean isCorrupted() {
      return this.packetInBytes.length < 6 || this.length() != this.getBytesLength() - 4;
   }

   public byte readByte() {
      ++this.readIndex;
      return this.packetInBytes[this.readIndex - 1];
   }

   public byte readByte(int index) {
      return this.packetInBytes[index];
   }

   public short readShort() {
      byte[] btarray = new byte[]{this.packetInBytes[this.readIndex], this.packetInBytes[this.readIndex + 1]};
      this.readIndex += 2;
      return ByteBuffer.wrap(btarray).getShort();
   }

   public short readShort(int index) {
      byte[] btarray = new byte[]{this.packetInBytes[index], this.packetInBytes[index + 1]};
      return ByteBuffer.wrap(btarray).getShort();
   }

   public int readUshort() {
      byte[] btarray = new byte[]{0, 0, this.packetInBytes[this.readIndex], this.packetInBytes[this.readIndex + 1]};
      this.readIndex += 2;
      return ByteBuffer.wrap(btarray).getInt();
   }

   public int readUshort(int index) {
      byte[] btarray = new byte[]{0, 0, this.packetInBytes[index], this.packetInBytes[index + 1]};
      return ByteBuffer.wrap(btarray).getInt();
   }

   public int headerId() {
      return this.readShort(4);
   }

   public int readInteger() {
      byte[] btarray = new byte[]{this.packetInBytes[this.readIndex], this.packetInBytes[this.readIndex + 1], this.packetInBytes[this.readIndex + 2], this.packetInBytes[this.readIndex + 3]};
      this.readIndex += 4;
      return ByteBuffer.wrap(btarray).getInt();
   }

   public int readInteger(int index) {
      byte[] btarray = new byte[]{this.packetInBytes[index], this.packetInBytes[index + 1], this.packetInBytes[index + 2], this.packetInBytes[index + 3]};
      return ByteBuffer.wrap(btarray).getInt();
   }

   public double readDouble() {
      double result = this.readDouble(this.readIndex);
      this.readIndex += 8;
      return result;
   }

   public double readDouble(int index) {
      return ByteBuffer.wrap(this.packetInBytes).getDouble(index);
   }

   public float readFloat() {
      float result = this.readFloat(this.readIndex);
      this.readIndex += 4;
      return result;
   }

   public float readFloat(int index) {
      return ByteBuffer.wrap(this.packetInBytes).getFloat(index);
   }

   public int length() {
      return this.readInteger(0);
   }

   public int getBytesLength() {
      return this.packetInBytes.length;
   }

   public byte[] readBytes(int length) {
      byte[] newbytes = new byte[length];

      for(int i = 0; i < length; ++i) {
         newbytes[i] = this.packetInBytes[i + this.readIndex];
      }

      this.readIndex += length;
      return newbytes;
   }

   public byte[] readBytes(int length, int index) {
      byte[] newbytes = new byte[length];

      for(int i = 0; i < length; ++i) {
         newbytes[i] = this.packetInBytes[i + index];
      }

      return newbytes;
   }

   public long readLong() {
      byte[] btarray = this.readBytes(8);
      return ByteBuffer.wrap(btarray).getLong();
   }

   public long readLong(int index) {
      byte[] btarray = this.readBytes(8, index);
      return ByteBuffer.wrap(btarray).getLong();
   }

   public String readString(Charset charset) {
      String r = this.readString(this.readIndex, charset);
      this.readIndex += 2 + this.readUshort(this.readIndex);
      return r;
   }

   public String readString(int index, Charset charset) {
      int length = this.readUshort(index);
      index += 2;
      return this.readString(index, length, charset);
   }

   public String readString() {
      return this.readString(StandardCharsets.UTF_8);
   }

   public String readString(int index) {
      return this.readString(index, StandardCharsets.UTF_8);
   }

   private String readString(int index, int length, Charset charset) {
      byte[] x = new byte[length];

      for(int i = 0; i < x.length; ++i) {
         x[i] = this.readByte(index);
         ++index;
      }

      return new String(x, charset);
   }

   public String readLongString(Charset charset) {
      String r = this.readLongString(this.readIndex, charset);
      this.readIndex += 4 + this.readInteger(this.readIndex);
      return r;
   }

   public String readLongString(int index, Charset charset) {
      int length = this.readInteger(index);
      index += 4;
      return this.readString(index, length, charset);
   }

   public String readLongString() {
      return this.readLongString(StandardCharsets.UTF_8);
   }

   public String readLongString(int index) {
      return this.readLongString(index, StandardCharsets.UTF_8);
   }

   public boolean readBoolean() {
      return this.readByte() != 0;
   }

   public boolean readBoolean(int index) {
      return this.readByte(index) != 0;
   }

   protected void replacePacketId(short headerId) {
      this.replaceShort(4, headerId);
   }

   public HPacket replaceBoolean(int index, boolean b) {
      this.isEdited = true;
      this.packetInBytes[index] = (byte)(b ? 1 : 0);
      return this;
   }

   public HPacket replaceInt(int index, int i) {
      this.isEdited = true;
      ByteBuffer b = ByteBuffer.allocate(4).putInt(i);

      for(int j = 0; j < 4; ++j) {
         this.packetInBytes[index + j] = b.array()[j];
      }

      return this;
   }

   public HPacket replaceLong(int index, long l) {
      this.isEdited = true;
      ByteBuffer b = ByteBuffer.allocate(8).putLong(l);

      for(int j = 0; j < 8; ++j) {
         this.packetInBytes[index + j] = b.array()[j];
      }

      return this;
   }

   public HPacket replaceDouble(int index, double d) {
      this.isEdited = true;
      ByteBuffer b = ByteBuffer.allocate(8).putDouble(d);

      for(int j = 0; j < 8; ++j) {
         this.packetInBytes[index + j] = b.array()[j];
      }

      return this;
   }

   public HPacket replaceFloat(int index, float f) {
      this.isEdited = true;
      ByteBuffer b = ByteBuffer.allocate(4).putFloat(f);

      for(int j = 0; j < 4; ++j) {
         this.packetInBytes[index + j] = b.array()[j];
      }

      return this;
   }

   public HPacket replaceByte(int index, byte b) {
      this.isEdited = true;
      this.packetInBytes[index] = b;
      return this;
   }

   public HPacket replaceBytes(int index, byte[] bytes) {
      this.isEdited = true;

      for(int i = 0; index + i < this.packetInBytes.length && i < bytes.length; ++i) {
         this.replaceByte(index + i, bytes[i]);
      }

      return this;
   }

   public HPacket replaceUShort(int index, int ushort) {
      this.isEdited = true;
      ByteBuffer b = ByteBuffer.allocate(4).putInt(ushort);
      this.packetInBytes[index] = b.array()[2];
      this.packetInBytes[index + 1] = b.array()[3];
      return this;
   }

   public HPacket replaceShort(int index, short s) {
      this.isEdited = true;
      ByteBuffer b = ByteBuffer.allocate(2).putShort(s);
      this.packetInBytes[index] = b.array()[0];
      this.packetInBytes[index + 1] = b.array()[1];
      return this;
   }

   public HPacket replaceString(int index, String s, Charset charset) {
      this.isEdited = true;
      byte[] sbytes = s.getBytes(charset);
      int mover = sbytes.length - this.readUshort(index);
      if (mover != 0) {
         byte[] newPacket = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + mover);
         if (mover > 0) {
            for(int i = newPacket.length - 1; i > index + mover + 2; --i) {
               newPacket[i] = this.packetInBytes[i - mover];
            }
         } else {
            for(int i = index + 2 + sbytes.length; i < newPacket.length; ++i) {
               newPacket[i] = this.packetInBytes[i - mover];
            }
         }

         this.packetInBytes = newPacket;
         this.fixLength();
      }

      this.replaceUShort(index, sbytes.length);

      for(int i = 0; i < sbytes.length; ++i) {
         this.packetInBytes[index + 2 + i] = sbytes[i];
      }

      return this;
   }

   public HPacket replaceString(int index, String s) {
      return this.replaceString(index, s, StandardCharsets.UTF_8);
   }

   private boolean canReadString(int index) {
      if (index < this.packetInBytes.length - 1) {
         int l = this.readUshort(index);
         if (index + 1 + l < this.packetInBytes.length) {
            return true;
         }
      }

      return false;
   }

   public HPacket replaceFirstString(String oldS, String newS) {
      return this.replaceXStrings(oldS, newS, 1);
   }

   public HPacket replaceXStrings(String oldS, String newS, int amount) {
      if (amount == 0) {
         return this;
      } else {
         for(int i = 6; i < this.packetInBytes.length - 1 - oldS.length(); ++i) {
            if (this.readUshort(i) == oldS.length() && this.readString(i).equals(oldS)) {
               this.replaceString(i, newS);
               i += 1 + newS.length();
               --amount;
               if (amount == 0) {
                  return this;
               }
            }
         }

         return this;
      }
   }

   public HPacket replaceAllStrings(String oldS, String newS) {
      return this.replaceXStrings(oldS, newS, -1);
   }

   public HPacket replaceFirstSubstring(String oldS, String newS) {
      return this.replaceXSubstrings(oldS, newS, 1);
   }

   public HPacket replaceXSubstrings(String oldS, String newS, int amount) {
      if (amount == 0) {
         return this;
      } else {
         int max = this.packetInBytes.length;

         for(int i = this.packetInBytes.length - 2 - oldS.length(); i >= 6; --i) {
            if (this.canReadString(i)) {
               String s = this.readString(i);
               System.out.println(s.contains(oldS));
               if (s.contains(oldS) && i + 2 + s.length() <= max) {
                  String replacement = s.replaceAll(oldS, newS);
                  this.replaceString(i, replacement);
                  i -= 1 + oldS.length();
                  --amount;
                  if (amount == 0) {
                     return this;
                  }

                  max = i;
               }
            }
         }

         return this;
      }
   }

   public HPacket replaceAllSubstrings(String oldS, String newS) {
      return this.replaceXSubstrings(oldS, newS, -1);
   }

   public HPacket replaceAllIntegers(int val, int replacement) {
      for(int i = 6; i < this.packetInBytes.length - 3; ++i) {
         if (this.readInteger(i) == val) {
            this.replaceInt(i, replacement);
            i += 3;
         }
      }

      return this;
   }

   public HPacket appendInt(int i) {
      this.isEdited = true;
      this.packetInBytes = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + 4);
      ByteBuffer byteBuffer = ByteBuffer.allocate(4).putInt(i);

      for(int j = 0; j < 4; ++j) {
         this.packetInBytes[this.packetInBytes.length - 4 + j] = byteBuffer.array()[j];
      }

      this.fixLength();
      return this;
   }

   public HPacket appendLong(long l) {
      this.isEdited = true;
      this.packetInBytes = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + 8);
      ByteBuffer byteBuffer = ByteBuffer.allocate(8).putLong(l);

      for(int j = 0; j < 8; ++j) {
         this.packetInBytes[this.packetInBytes.length - 8 + j] = byteBuffer.array()[j];
      }

      this.fixLength();
      return this;
   }

   public HPacket appendDouble(double d) {
      this.isEdited = true;
      this.packetInBytes = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + 8);
      ByteBuffer byteBuffer = ByteBuffer.allocate(8).putDouble(d);

      for(int j = 0; j < 8; ++j) {
         this.packetInBytes[this.packetInBytes.length - 8 + j] = byteBuffer.array()[j];
      }

      this.fixLength();
      return this;
   }

   public HPacket appendFloat(float f) {
      this.isEdited = true;
      this.packetInBytes = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + 4);
      ByteBuffer byteBuffer = ByteBuffer.allocate(4).putFloat(f);

      for(int j = 0; j < 4; ++j) {
         this.packetInBytes[this.packetInBytes.length - 4 + j] = byteBuffer.array()[j];
      }

      this.fixLength();
      return this;
   }

   public HPacket appendByte(byte b) {
      this.isEdited = true;
      this.packetInBytes = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + 1);
      this.packetInBytes[this.packetInBytes.length - 1] = b;
      this.fixLength();
      return this;
   }

   public HPacket appendBytes(byte[] bytes) {
      this.isEdited = true;
      this.packetInBytes = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + bytes.length);

      for(int i = 0; i < bytes.length; ++i) {
         this.packetInBytes[this.packetInBytes.length - bytes.length + i] = bytes[i];
      }

      this.fixLength();
      return this;
   }

   public HPacket appendBoolean(boolean b) {
      this.isEdited = true;
      this.appendByte((byte)(b ? 1 : 0));
      return this;
   }

   public HPacket appendUShort(int ushort) {
      this.isEdited = true;
      this.packetInBytes = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + 2);
      ByteBuffer byteBuffer = ByteBuffer.allocate(4).putInt(ushort);

      for(int j = 2; j < 4; ++j) {
         this.packetInBytes[this.packetInBytes.length - 4 + j] = byteBuffer.array()[j];
      }

      this.fixLength();
      return this;
   }

   public HPacket appendShort(short s) {
      this.isEdited = true;
      this.packetInBytes = Arrays.copyOf(this.packetInBytes, this.packetInBytes.length + 2);
      ByteBuffer byteBuffer = ByteBuffer.allocate(2).putShort(s);

      for(int j = 0; j < 2; ++j) {
         this.packetInBytes[this.packetInBytes.length - 2 + j] = byteBuffer.array()[j];
      }

      this.fixLength();
      return this;
   }

   public HPacket appendString(String s, Charset charset) {
      if(s==null || s.getBytes(charset).length>65535) throw new IllegalArgumentException("Packet string exceeds 65535 encoded bytes");
      this.isEdited = true;
      this.appendUShort(s.getBytes(charset).length);
      this.appendBytes(s.getBytes(charset));
      return this;
   }

   public HPacket appendString(String s) {
      return this.appendString(s, StandardCharsets.UTF_8);
   }

   public HPacket appendLongString(String s, Charset charset) {
      this.isEdited = true;
      this.appendInt(s.getBytes(charset).length);
      this.appendBytes(s.getBytes(charset));
      return this;
   }

   public HPacket appendLongString(String s) {
      return this.appendLongString(s, StandardCharsets.UTF_8);
   }

   public HPacket appendObjects(Object... objects) {
      for(Object object : objects) {
         this.appendObject(object);
      }

      return this;
   }

   public HPacket appendObject(Object o) throws InvalidParameterException {
      this.isEdited = true;
      if (o instanceof Byte) {
         this.appendByte((Byte)o);
      } else if (o instanceof Integer) {
         this.appendInt((Integer)o);
      } else if (o instanceof Short) {
         this.appendShort((Short)o);
      } else if (o instanceof String) {
         this.appendString((String)o, StandardCharsets.UTF_8);
      } else if (o instanceof Boolean) {
         this.appendBoolean((Boolean)o);
      } else if (o instanceof Long) {
         this.appendLong((Long)o);
      } else if (o instanceof Float) {
         this.appendFloat((Float)o);
      } else {
         if (!(o instanceof Double)) {
            throw new InvalidParameterException();
         }

         this.appendDouble((Double)o);
      }

      return this;
   }

   public boolean isReplaced() {
      return this.isEdited;
   }

   public void fixLength() {
      boolean remember = this.isEdited;
      this.replaceInt(0, this.packetInBytes.length - 4);
      this.isEdited = remember;
   }

   public void overrideEditedField(boolean edited) {
      this.isEdited = edited;
   }

   private PacketInfo getPacketInfo(HMessage.Direction direction, PacketInfoManager packetInfoManager) {
      if (packetInfoManager == null) {
         return null;
      } else {
         Optional<PacketInfo> packetInfoMaybe = packetInfoManager.getAllPacketInfoFromHeaderId(direction, this.headerId()).stream().filter((packetInfo) -> packetInfo.getStructure() != null).findFirst();
         return (PacketInfo)packetInfoMaybe.orElseGet(() -> packetInfoManager.getPacketInfoFromHeaderId(direction, this.headerId()));
      }
   }

   public String toExpression(HMessage.Direction direction, PacketInfoManager packetInfoManager, boolean removeShuffle) {
      if (this.isCorrupted()) {
         return "";
      } else {
         PacketInfo packetInfo = this.getPacketInfo(direction, packetInfoManager);
         return packetInfo != null && packetInfo.getStructure() != null ? PacketStringUtils.toExpressionFromGivenStructure(this, packetInfo.getStructure(), removeShuffle ? packetInfo : null) : PacketStringUtils.predictedExpression(this, removeShuffle ? packetInfo : null);
      }
   }

   public String toExpression(PacketInfoManager packetInfoManager, boolean removeShuffle) {
      if (this.isCorrupted()) {
         return "";
      } else {
         PacketInfo maybe1 = this.getPacketInfo(HMessage.Direction.TOCLIENT, packetInfoManager);
         PacketInfo maybe2 = this.getPacketInfo(HMessage.Direction.TOSERVER, packetInfoManager);
         PacketInfo packetInfo = null;
         if (maybe1 != null && maybe2 == null) {
            packetInfo = maybe1;
         } else if (maybe1 == null && maybe2 != null) {
            packetInfo = maybe2;
         }

         return packetInfo != null && packetInfo.getStructure() != null ? PacketStringUtils.toExpressionFromGivenStructure(this, packetInfo.getStructure(), removeShuffle ? packetInfo : null) : PacketStringUtils.predictedExpression(this, removeShuffle ? packetInfo : null);
      }
   }

   public String toExpression() {
      return this.isCorrupted() ? "" : PacketStringUtils.predictedExpression(this, this.dummyPacketInfo());
   }

   private PacketInfo dummyPacketInfo() {
      return this.isPacketComplete() ? null : new PacketInfo(this.identifierDirection, -1, "", this.identifier, (String)null, "");
   }

   public void setBytes(byte[] bytes) {
      this.isEdited = true;
      this.packetInBytes = bytes;
   }

   public String stringify() {
      String st = null;
      st = (this.isEdited ? "1" : "0") + new String(this.packetInBytes, StandardCharsets.ISO_8859_1);
      return st;
   }

   public void constructFromString(String str) {
      this.isEdited = str.charAt(0) == '1';
      this.packetInBytes = str.substring(1).getBytes(StandardCharsets.ISO_8859_1);
   }

   public boolean equals(Object object) {
      if (!(object instanceof HPacket)) {
         return false;
      } else {
         HPacket packet2 = (HPacket)object;
         return Arrays.equals(this.packetInBytes, packet2.packetInBytes) && this.isEdited == packet2.isEdited;
      }
   }

   public HPacket copy() {
      return new HPacket(this);
   }
}
