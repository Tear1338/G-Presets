package extension.tools.presetconfig.wired;

/** Classify by protocol family, never a frozen list of furniture sprite IDs. */
public enum WiredKind {
   TRIGGER("trg"), CONDITION("cnd"), EFFECT("act"), ADDON("xtra"), SELECTOR("slc"), VARIABLE("var"), NONE("");

   private final String code;
   WiredKind(String code) { this.code = code; }

   public static WiredKind fromClassName(String name) {
      if (name == null || !name.startsWith("wf_")) return NONE;
      String suffix = name.substring(3);
      if (suffix.startsWith("ltdproto_")) suffix = suffix.substring(9);
      else if (suffix.startsWith("proto_")) suffix = suffix.substring(6);
      if (suffix.startsWith("test_")) suffix = suffix.substring(5);
      for (WiredKind kind : values()) {
         if (kind != NONE && (suffix.equals(kind.code) || suffix.startsWith(kind.code + "_"))) return kind;
      }
      return NONE;
   }
}
