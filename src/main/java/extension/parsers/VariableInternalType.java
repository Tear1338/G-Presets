package extension.parsers;

public enum VariableInternalType {
   USER_CREATED(0),
   INTERNAL(1),
   SUB(2),
   SMART(3),
   UNKNOWN(-1);

   private final int type;

   private VariableInternalType(int type) {
      this.type = type;
   }

   public int toInt() {
      return this.type;
   }

   public static VariableInternalType fromInt(int type) {
      for(VariableInternalType variableType : values()) {
         if (variableType.type == type) {
            return variableType;
         }
      }

      return UNKNOWN;
   }

   // $FF: synthetic method
   private static VariableInternalType[] $values() {
      return new VariableInternalType[]{USER_CREATED, INTERNAL, SUB, SMART};
   }
}
