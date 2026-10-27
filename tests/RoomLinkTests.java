package tests;
import extension.tools.importutils.PartialImportPlan;
import extension.tools.presetconfig.PresetConfig;
import extension.tools.presetconfig.wired.*;
import gearth.protocol.HPacket;
import java.util.*;
import org.json.*;

public final class RoomLinkTests {
 private static int checks;
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 private static void rejects(Runnable action,String message){try{action.run();}catch(IllegalArgumentException|IllegalStateException e){checks++;return;}throw new AssertionError(message);}
 public static int run() {
  checks=0;var ids=Map.of(7,107,8,108);
  var legacy=new PresetRoomLink(new JSONObject().put("furniId",7).put("destination","420000001"));
  check(legacy.unavailableReason(ids).contains("source furniture ID"),"legacy room linker is recognized as an unmapped source furniture reference");
  rejects(legacy::savePacket,"raw legacy target is never sent as a room ID");rejects(()->legacy.remap(ids),"unknown legacy destination cannot be rebound implicitly");
  check(!legacy.toJsonObject().has("destinationType"),"legacy JSON remains round-trip compatible and is not silently rewritten");
  var local=PresetRoomLink.capture(7,900,Map.of(900,8));
  check(local.destinationType.equals("furniture") && local.destination.equals("8"),"new export stores the destination as a preset furniture ID");
  local=new PresetRoomLink(local.toJsonObject());var mapped=local.remap(ids);HPacket packet=mapped.savePacket();
  check(packet.readInteger()==0 && packet.readInteger()==107 && packet.readString().equals("~103") && packet.readInteger()==108 && packet.readInteger()==0,"both owner and paired furniture IDs are mapped before sending the native setter");
  var missing=PresetRoomLink.capture(7,900,Map.of(900,8));check(missing.unavailableReason(Map.of(7,107)).contains("not placed"),"missing local target is diagnosed before a save");
  var external=PresetRoomLink.capture(7,900,Map.of());check(external.destinationType.equals("externalFurniture"),"external pairing is explicit in new exports");rejects(()->external.remap(ids),"external pairing keeps the native destination instead of writing a foreign object ID");
  var empty=PresetRoomLink.capture(7,0,Map.of()).remap(ids).savePacket();empty.readInteger();empty.readInteger();empty.readString();check(empty.readInteger()==0,"explicit unpaired source can be restored without resolving a non-existent target");
  var wired=new PresetWireds(new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray()));
  var preset=new PresetConfig(new ArrayList<>(),new ArrayList<>(),wired,new ArrayList<>(),new ArrayList<>());preset.setRoomLinks(new ArrayList<>(List.of(legacy)));
  var plan=PartialImportPlan.create(preset,ids,Map.of());
  check(plan.effective().getRoomLinks().isEmpty() && plan.omissions().size()==1,"partial build excludes the unsupported legacy pairing without excluding other configuration");
  check(plan.omissions().get(0).category().equals("roomLinks") && preset.getRoomLinks().size()==1,"report records the omitted pairing and source preset remains intact");
  preset.setRoomLinks(new ArrayList<>(List.of(PresetRoomLink.capture(7,900,Map.of(900,8)))));
  check(PartialImportPlan.create(preset,ids,Map.of()).omissions().isEmpty(),"available portable pairings remain eligible for save and verification");
  rejects(()->new PresetRoomLink(new JSONObject().put("furniId",7).put("destination","999").put("destinationType","resolvedFurniture")),"preset JSON cannot bypass furniture remapping with a runtime-only resolved marker");
  rejects(()->new PresetRoomLink(new JSONObject().put("furniId",7).put("destination","999").put("destinationType","none")),"an unpaired marker cannot silently discard a nonzero destination");
  var clock=new PresetObjectVariables(0,7,Map.of("~121",12,"~100",20));
  check(!clock.matches(Map.of("~121",13,"~100",20)) && clock.onlyClockPulseUnconfirmed(Map.of("~121",13,"~100",20)),"a changing clock value remains unconfirmed rather than being counted as a successful snapshot");
  check(!clock.onlyClockPulseUnconfirmed(Map.of("~121",13,"~100",21)),"another mismatching property remains a real validation failure");
  check(!clock.onlyClockPulseUnconfirmed(Map.of("~100",20)),"missing clock inspection is not mistaken for a moving counter");
  check(!clock.onlyClockPulseUnconfirmed(Map.of("~121",12,"~100",20)),"fully matching clock snapshot remains fully confirmed");
  return checks;
 }
}
