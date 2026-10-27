package tests;

import extension.parsers.*;
import extension.tools.presetconfig.furni.PresetFurniLabel;
import extension.tools.presetconfig.furni.PresetChest;
import extension.tools.presetconfig.*;
import extension.tools.preview.*;
import extension.tools.postconfig.*;
import extension.tools.presetconfig.furni.*;
import gearth.extensions.parsers.HPoint;
import utils.WallPosition;
import extension.tools.presetconfig.ads_bg.PresetAdsBackground;
import extension.tools.presetconfig.wired.*;
import extension.tools.presetconfig.wired.incoming.*;
import furnidata.FurniDataTools;
import gearth.protocol.HPacket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import org.json.*;
import utils.Utils;

public final class RegressionTests {
   private static int passed;
   private static void check(boolean success, String description) {
      if (!success) throw new AssertionError(description);
      passed++;
   }
   private static void fails(Runnable action, String description) {
      try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) {
         passed++; return;
      }
      throw new AssertionError(description);
   }
   private static HPacket body(String base64) { return new HPacket(1, Base64.getDecoder().decode(base64)); }
   private static byte[] payload(HPacket packet) { return Arrays.copyOfRange(packet.toBytes(), 6, packet.getBytesLength()); }
   private static JSONObject definition() {
      return new JSONObject().put("wiredId", 123).put("options", new JSONArray(List.of(1, 2)))
         .put("config", "x").put("items", new JSONArray(List.of(7))).put("secondItems", new JSONArray(List.of(8)))
         .put("furniSources", new JSONArray(List.of(0))).put("userSources", new JSONArray(List.of(1)))
         .put("variableIds", new JSONArray(List.of("999999999999")));
   }
   private static PresetConfig emptyPreset() {
      return new PresetConfig(List.of(), List.of(), new PresetWireds(new JSONObject()
         .put("conditions",new JSONArray()).put("effects",new JSONArray()).put("triggers",new JSONArray())),
         List.of(), List.of());
   }

   private static void outgoingProtocol(JSONObject golden) {
      Map<String, Function<HPacket, PresetWiredBase>> readers = new LinkedHashMap<>();
      readers.put("UpdateTrigger", PresetWiredTrigger::new);
      readers.put("UpdateAction", PresetWiredEffect::new);
      readers.put("UpdateCondition", PresetWiredCondition::new);
      readers.put("UpdateSelector", PresetWiredSelector::new);
      readers.put("UpdateAddon", PresetWiredAddon::new);
      readers.put("UpdateVariable", PresetWiredVariable::new);
      for (Map.Entry<String, Function<HPacket, PresetWiredBase>> entry : readers.entrySet()) {
         byte[] expected = Base64.getDecoder().decode(golden.getString(entry.getKey()));
         HPacket input = new HPacket(1, expected);
         PresetWiredBase wired = entry.getValue().apply(input);
         check(input.isEOF() == 1, entry.getKey() + " consumes complete reference payload");
         check(wired.getOptions().equals(List.of(1,2)), entry.getKey() + " integer parameters");
         check(wired.getVariableIds().equals(List.of("999999999999")), entry.getKey() + " string variable ID");
         check(wired.getSecondItems().equals(List.of(8)), entry.getKey() + " second selection");
         check(Arrays.equals(expected, payload(wired.toPacket())), entry.getKey() + " matches independent native codec bytes");
         PresetWiredBase jsonCopy = switch(entry.getKey()) {
            case "UpdateAction" -> new PresetWiredEffect(wired.toJsonObject());
            case "UpdateCondition" -> new PresetWiredCondition(wired.toJsonObject());
            case "UpdateSelector" -> new PresetWiredSelector(wired.toJsonObject());
            case "UpdateAddon" -> new PresetWiredAddon(wired.toJsonObject());
            case "UpdateVariable" -> new PresetWiredVariable(wired.toJsonObject());
            default -> new PresetWiredTrigger(wired.toJsonObject());
         };
         check(Arrays.equals(expected, payload(jsonCopy.toPacket())), entry.getKey() + " JSON round trip");
      }
   }

   private static void modernContexts() {
      HPacket input = body("AAAAAgAAAAcAAAABAAdtZXNzYWdlAAAAAQAHY2FwdHVyZQAAAAgAAABkAAAAAwAAAAQAAAACAAAAAQADdmFyAARuYW1lAQAAAAAH");
      HWiredContext context = new HWiredContext(input);
      check(context.textPlaceholders.names.equals(List.of("message")), "tag 7 output placeholders");
      check(context.textPlaceholders.capturerNames.equals(List.of("capture")), "tag 7 named captures");
      check(context.variableLeaderboard.overlayInstanceId == 100, "tag 8 overlay ID");
      check(context.variableLeaderboard.choices.get(0).applicableLevelUpCount == 7, "tag 8 full choice metadata");
      check(input.isEOF() == 1, "modern contexts consume exact reference bytes");
      fails(() -> new HWiredContext(new HPacket(1, 1, 99, 0)), "unknown context tag must not desynchronize parsing");
      HPacket action = body("AAAAAAAAAAEAAAAHAAAAAQAAAAgAAAHIAAAAewABeAAAAAIAAAABAAAAAgAAAAEAA3ZhcgAAAAEAAAAAAAAAAQAAAAEAAABAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAACQAAAAo=");
      RetrievedWiredEffect effect = RetrievedWiredEffect.fromPacket(action);
      check(effect.getWiredId() == 123 && effect.getTypeId() == 456, "server configuration identities");
      check(effect.getDelay() == 4, "server action delay");
      check(effect.getOptions().equals(List.of(1,2)), "server options distinct from defaults");
      check(effect.getVariableIds().equals(List.of("var")), "server string variable references");
      check(action.isEOF() == 1, "optional integer defaults consumed after context");
      HWiredVariable metadata = new HWiredVariable(body("AAJpZAAAAAAABG5hbWUAAAAAAAAAAAAAAAAAAAAAAAA="));
      check(metadata.id.equals("id") && metadata.name.equals("name"), "native Flash variable metadata");
      HWiredVariable modern = new HWiredVariable(body("AAJpZAAAAAAABG5hbWUAAAAAAAAAAAAAAAAAAAEAAAEAAAABAAAABwAFaGVsbG8BAAhmYWxsYmFjaw=="));
      check(modern.requiresExecutionContext, "modern execution-context flag read before connector");
      check(modern.textConnector.get(7).equals("hello"), "modern text connector entries");
      check("fallback".equals(modern.textConnectorFallback), "modern text connector fallback");
      HWiredVariable legacy = new HWiredVariable(body("AAJpZAAAAAAABG5hbWUAAAAAAAAAAAAAAAAAAAAAAA=="), false);
      check(legacy.id.equals("id") && !legacy.requiresExecutionContext, "legacy metadata available through explicit layout");
   }

   private static void allIncomingFamilies() throws Exception {
      JSONObject golden = new JSONObject(Files.readString(Path.of("tests/fixtures/qx-incoming.json")).replace("\uFEFF", ""));
      Map<String, Function<HPacket, PresetWiredBase>> readers = new LinkedHashMap<>();
      readers.put("WiredTriggerConfig", RetrievedWiredTrigger::fromPacket);
      readers.put("WiredActionConfig", RetrievedWiredEffect::fromPacket);
      readers.put("WiredConditionConfig", RetrievedWiredCondition::fromPacket);
      readers.put("WiredSelectorConfig", RetrievedWiredSelector::fromPacket);
      readers.put("WiredAddonConfig", RetrievedWiredAddon::fromPacket);
      readers.put("WiredVariableConfig", RetrievedWiredVariable::fromPacket);
      for (Map.Entry<String, Function<HPacket, PresetWiredBase>> entry : readers.entrySet()) {
         HPacket packet = body(golden.getString(entry.getKey()));
         PresetWiredBase wired = entry.getValue().apply(packet);
         check(packet.isEOF() == 1, entry.getKey() + " exact modern packet consumption");
         check(wired.getWiredId() == 123 && ((RetrievedWired)wired).getTypeId() == 456, entry.getKey() + " identity");
         check(wired.getOptions().equals(List.of(1, 2)), entry.getKey() + " options separate from server defaults");
         check(wired.getItems().equals(List.of(7)) && wired.getSecondItems().equals(List.of(8)), entry.getKey() + " both selections");
         check(wired.getVariableIds().equals(List.of("var")), entry.getKey() + " references preserved across modern contexts");
         if (wired instanceof PresetWiredSelector) {
            JSONObject object = wired.toJsonObject();
            check(object.getBoolean("filter") && object.getBoolean("inverse"), "incoming selector modifiers retained");
         }
         if (wired instanceof PresetWiredCondition) check(wired.toJsonObject().getInt("quantifier") == 2, "incoming condition quantifier retained");
      }
   }

   private static void variableDiffs() {
      HPacket packet = new HPacket(1, 10, false, 3, "17", "999999999999", "reference:name", 1, 20);
      packet.appendBytes(Base64.getDecoder().decode("AAJpZAAAAAAABG5hbWUAAAAAAAAAAAAAAAAAAAAAAAA="));
      HVariableDiff diff = new HVariableDiff(packet);
      check(diff.removed.equals(List.of("17","999999999999","reference:name")), "removed IDs are strings, not longs");
      check(!diff.lastChunk && diff.updated.get(0).id.equals("id"), "non-final variable chunk retained");
      check(new HVariableDiff(new HPacket(1, 20, true, 0, 0)).lastChunk, "empty final chunk completes snapshot");
      fails(() -> new HVariableDiff(new HPacket(1, 0, true, -1, 0)), "negative diff count rejected");
      fails(() -> Utils.readIntList(new HPacket(1, Integer.MAX_VALUE)), "oversized count rejected before allocation");
      fails(() -> Utils.readStringList(new HPacket(1, 1, (short)9, (byte)1)), "truncated string rejected");
   }

   private static PresetWiredVariable variable(int furniId, String id, String name, List<String> dependencies) {
      JSONObject object=definition().put("wiredId", furniId).put("variableId",id).put("config",name)
         .put("variableIds",new JSONArray(dependencies));
      return new PresetWiredVariable(object);
   }

   private static void mapping() {
      PresetWiredVariable source=variable(123,"definition-id","pointer",List.of("999999999999","-123","0"));
      Map<Integer,Integer> furniture=Map.of(123,500,7,700,8,800);
      Map<String,String> variableIds=new HashMap<>(Map.of("999999999999","new-accessor"));
      PresetWiredBase result=source.remap(furniture,variableIds);
      check(result.getWiredId()==500 && result.getItems().equals(List.of(700)), "new room furniture mapping");
      check(result.getSecondItems().equals(List.of(800)), "second selection remapped");
      check(result.getVariableIds().equals(List.of("new-accessor","-123","0")), "local references remapped, built-ins preserved");
      check(variableIds.size()==1 && !variableIds.containsKey("definition-id"), "mapping never mutates caller map");
      check(source.getVariableIds().get(0).equals("999999999999"), "source preset is reusable");
      fails(() -> source.remap(furniture,Map.of()), "missing accessor cannot silently become zero");
      source.setExternalVariableIds(List.of("999999999999"));
      check(source.remap(furniture,Map.of()).getVariableIds().get(0).equals("999999999999"), "explicit shared-room reference retains external identity");
      PresetWiredVariable sharedCopy=new PresetWiredVariable(source.toJsonObject());
      check(sharedCopy.getExternalVariableIds().equals(List.of("999999999999")), "external reference metadata survives JSON");
      source.setExternalVariableIds(List.of());
      check(source.remap(Map.of(),Map.of())==null, "excluded furniture is skipped");
      PresetWiredVariable accessor=variable(1,"a","accessor",List.of());
      PresetWiredVariable pointer=variable(2,"b","pointer",List.of("a"));
      check(WiredImportPlanner.orderVariables(List.of(pointer,accessor)).equals(List.of(accessor,pointer)), "pointer configured after accessor");
      fails(() -> WiredImportPlanner.orderVariables(List.of(variable(1,"a","cycle-a",List.of("b")),variable(2,"b","cycle-b",List.of("a")))), "cyclic definitions rejected");
   }

   private static void legacyAndFuture(JSONObject golden) {
      JSONObject legacy=definition();
      for(String key:List.of("secondItems","furniSources","userSources","variableIds")) legacy.remove(key);
      PresetWiredTrigger wired=new PresetWiredTrigger(legacy);
      check(wired.getVariableIds().isEmpty() && wired.getSecondItems().isEmpty(), "old JSON accepts absent newer fields");
      wired.setItems(new ArrayList<>(List.of(7)));
      wired.getItems().add(8);
      check(wired.getItems().equals(List.of(7,8)), "legacy collections remain writable");
      check(new PresetWiredTrigger(definition().put("options",new JSONArray(List.of(1L,2L)))).getOptions().equals(List.of(1,2)), "valid JSON numbers accepted");
      fails(() -> new PresetWiredTrigger(definition().put("options",new JSONArray(List.of(1.5)))), "fractional options rejected");
      HPacket future=body(golden.getString("UpdateTrigger")); future.appendInt(999);
      PresetWiredTrigger preserved=new PresetWiredTrigger(future);
      check(preserved.hasUnrecognizedSaveData(), "new protocol fields preserved");
      PresetWiredTrigger reloaded=new PresetWiredTrigger(preserved.toJsonObject());
      check(reloaded.hasUnrecognizedSaveData(), "new fields survive JSON persistence");
      fails(reloaded::toPacket, "unknown fields cannot be silently discarded on import");
      fails(() -> new PresetWiredTrigger(definition().put("config", "x".repeat(65536))).toPacket(), "oversized UTF-8 text rejected before composing");
      JSONObject oldWireds = new JSONObject().put("conditions",new JSONArray()).put("effects",new JSONArray())
         .put("triggers",new JSONArray()).put("variables_map",new JSONObject().put("legacy",9223372036854775807L));
      check(new PresetWireds(oldWireds).getVariablesMap().get("legacy").equals("9223372036854775807"),"historical numeric variable IDs preserved");
   }

   private static void catalog(JSONObject golden) {
      JSONArray classes=new JSONArray(golden.getString("CATALOG"));
      int configurable=0;
      for(Object value:classes) {
         String name=value.toString();
         if(name.matches("wf_(trg|act|cnd|xtra|slc|var)_.*") || name.matches("wf_(ltdproto|proto)_(trg|act|cnd|xtra|slc|var)_.*") || name.matches("wf_test_(trg|act|cnd|xtra|slc|var)")) {
            check(WiredKind.fromClassName(name)!=WiredKind.NONE, "catalog family recognized: "+name);
            configurable++;
         }
      }
      check(WiredKind.fromClassName("wf_var_pointer")==WiredKind.VARIABLE,"pointer variable recognized");
      check(WiredKind.fromClassName("wf_xtra_var_leaderboard")==WiredKind.ADDON,"leaderboard recognized");
      check(WiredKind.fromClassName("wf_ltdproto_act_toggle_state")==WiredKind.EFFECT,"LTD prototype recognized");
      check(WiredKind.fromClassName("wf_proto_trg_at_given_time")==WiredKind.TRIGGER,"prototype trigger recognized");
      check(WiredKind.fromClassName(null)==WiredKind.NONE,"missing metadata is null-safe");
      System.out.println("Catalog: "+classes.length()+" WIRED-related furniture, "+configurable+" configuration-family classes checked");
   }

   private static void links() {
      HPacket packet = new HPacket(1, 99, 123, "network-42", true, 2,
         "network-42", "Primary", 3, false, "other", "Other", 7, true);
      HWiredLinkConfiguration parsed = new HWiredLinkConfiguration(packet);
      check(parsed.roomId == 99 && parsed.configuration.furniId == 123, "WIRED Link response target");
      check(parsed.configuration.networkId.equals("network-42") && parsed.configuration.receiveLocal, "WIRED Link source configuration");
      check(parsed.networks.size() == 2 && parsed.networks.get(1).linkedInCurrentRoom, "WIRED Link available networks decoded");
      check(parsed.hasNetwork("network-42") && parsed.hasNetwork("0") && !parsed.hasNetwork("missing"), "WIRED Link availability without creating networks");
      check(packet.isEOF() == 1, "WIRED Link entire response consumed");
      PresetWiredLink link = parsed.configuration.withFurniId(555);
      HPacket save = link.savePacket();
      check(save.readInteger() == 555 && save.readString().equals("network-42") && save.readBoolean() && save.isEOF() == 1,
         "WIRED Link save follows client composer argument order");
      HPacket request = PresetWiredLink.requestPacket(555);
      check(request.readInteger() == 555 && request.isEOF() == 1, "WIRED Link request uses mapped furniture identity");
      PresetConfig config = emptyPreset(); config.setWiredLinks(List.of(link));
      PresetConfig reloaded = new PresetConfig(config.toJsonObject());
      check(reloaded.getWiredLinks().get(0).furniId == 555 && reloaded.getWiredLinks().get(0).networkId.equals("network-42"), "WIRED Link JSON round trip");
      check(emptyPreset().getWiredLinks().isEmpty(), "legacy preset has no implicit links");
      fails(() -> new HWiredLinkConfiguration(new HPacket(1,99,123,"id",false,Integer.MAX_VALUE)), "invalid WIRED Link count rejected");
   }

   private static void labels() {
      HPacket input=body("AAAAAAAAAHsAB2V4YW1wbGUAADA5AAAAAQE=");
      PresetFurniLabel label=PresetFurniLabel.fromPacket(input);
      check(label.furniId==123 && label.text.equals("example"),"label discriminator zero and 32-bit furniture ID");
      check(label.color==12345 && label.visibility==1 && label.alwaysOpen,"label presentation preserved");
      check(input.isEOF()==1,"label codec consumes complete payload");
      check(Arrays.equals(payload(label.textPacket()),Base64.getDecoder().decode("AAAAewAHZXhhbXBsZQ==")),"label text matches native setter bytes");
      check(Arrays.equals(payload(label.settingsPacket()),Base64.getDecoder().decode("AAAAewAAMDkAAAABAQ==")),"label settings match native setter bytes");
      PresetConfig config=emptyPreset();config.setFurnitureLabels(List.of(label));
      PresetConfig reloaded=new PresetConfig(config.toJsonObject());
      check(reloaded.getFurnitureLabels().get(0).text.equals("example"),"label JSON round trip");
      check(label.withFurniId(555).furniId==555 && label.furniId==123,"label remapping preserves source");
   }

   private static JSONObject fixture(String name) throws Exception {
      return new JSONObject(Files.readString(Path.of("tests/fixtures/"+name)).replace("\uFEFF",""));
   }
   private static void auxiliaryCodecs() throws Exception {
      JSONObject golden=fixture("auxiliary-codecs.json");
      PresetConfig preset=emptyPreset(); List<PresetWiredContract> contracts=new ArrayList<>();
      for(int type=0;type<3;type++) {
         HPacket input=body(golden.getString("contract-"+type));
         PresetWiredContract contract=PresetWiredContract.fromPacket(input,null);
         check(contract.furniId==123 && contract.toJsonObject().getInt("type")==type,"contract identities "+type);
         check(input.isEOF()==1,"contract consumes entire native contents "+type);
         check(Arrays.equals(payload(contract.savePacket(null)),Base64.getDecoder().decode(golden.getString("update-"+type))),"contract matches native save codec "+type);
         check(contract.toJsonObject().getJSONArray("give").getJSONArray(0).getJSONObject(1).getInt("typeId")==456,"contract furniture type is not a room object ID");
         check(contract.withFurniId(555).furniId==555 && contract.furniId==123,"contract remaps only the editor furniture ID");
         JSONObject changed=contract.toJsonObject(); changed.put("type",9);
         fails(()->new PresetWiredContract(changed),"unknown contract type rejected");
         byte[] data=payload(contract.savePacket(null));
         fails(()->PresetWiredContract.fromPacket(new HPacket(1,Arrays.copyOf(data,data.length-1)),null),"truncated contract rejected");
         contracts.add(contract);
      }
      preset.setContracts(contracts);
      Map<String,String> settings=new HashMap<>();
      String[] allKeys=java.util.stream.Stream.of(PresetChest.OPTIONS,PresetChest.PREFERENCES,PresetChest.NOTIFICATIONS).flatMap(Arrays::stream).toArray(String[]::new);
      String[] values={"1","0","99","name","desc","1","0","2","3","4","1","2","1","0","1","0","1"};
      for(int i=0;i<allKeys.length;i++)settings.put(allKeys[i],values[i]);
      PresetChest chest=new PresetChest(123,settings);
      String[] names={"chest-options","chest-preferences","chest-notifications"};
      for(int i=0;i<names.length;i++) check(Arrays.equals(payload(chest.packets().get(i)),Base64.getDecoder().decode(golden.getString(names[i]))),"chest native codec "+names[i]);
      check(chest.matches(settings),"chest readback compares all captured settings");
      Map<String,String> wrong=new HashMap<>(settings);wrong.put("capacity","1");
      check(!chest.matches(wrong),"chest capacity refusal detected");
      wrong.remove("preview_mode"); fails(()->new PresetChest(123,wrong),"incomplete chest group rejected");
      Map<String,String> missingGroup=new HashMap<>(settings);for(String key:PresetChest.NOTIFICATIONS)missingGroup.remove(key);
      fails(()->new PresetChest(123,missingGroup),"missing private chest preferences cannot be silently omitted");
      check(chest.withFurniId(555).furniId==555,"chest furniture remapped");
      check(!chest.toJsonObject().toString().contains("coins"),"chest configuration carries no balances");
      preset.setChests(List.of(chest));
      PresetConfig copy=new PresetConfig(preset.toJsonObject());
      check(copy.getContracts().size()==3 && copy.getChests().size()==1,"auxiliary settings survive preset JSON");
      check(Arrays.equals(payload(copy.getContracts().get(2).savePacket(null)),payload(contracts.get(2).savePacket(null))),"contract nested rules survive JSON");
   }

   private static void regionsAndUnicode() throws Exception {
      JSONObject golden=fixture("region-codecs.json");
      HPacket incoming=body(golden.getString("labels"));check(incoming.readInteger()==2,"mixed label/region native fixture count");
      PresetFurniLabel label=PresetFurniLabel.fromPacket(incoming),region=PresetFurniLabel.fromPacket(incoming);
      check(!label.isRegion() && region.isRegion() && region.furniId==456,"native label discriminator distinguishes region");
      check(incoming.isEOF()==1,"mixed labels and regions consume entire native packet");
      check(region.toJsonObject().getJSONObject("region").getJSONArray("areas").getJSONObject(0).getInt("width")==7,"region rectangles retained");
      PresetFurniLabel shifted=region.translate(-3,-4).withFurniId(999);
      check(shifted.toJsonObject().getJSONObject("region").getJSONArray("areas").getJSONObject(0).getInt("x")==2,"region export translates tile X");
      check(region.matches(shifted.translate(3,4).withFurniId(456)),"region import restores coordinates without altering source");
      // Native region composer is the incoming entry without the leading type discriminator.
      byte[] raw=payload(body(golden.getString("labels")));
      int offset=4+(4+4+2+7+4+4+1);
      check(Arrays.equals(payload(region.textPacket()),Arrays.copyOfRange(raw,offset+4,raw.length)),"region save packet matches installed Flash composer field order");
      PresetConfig preset=emptyPreset();preset.setFurnitureLabels(List.of(label,region));
      check(new PresetConfig(preset.toJsonObject()).getFurnitureLabels().get(1).matches(region),"region survives JSON");
      PresetFurniLabel utf=new PresetFurniLabel(123,"caf\u00e9\uD83D\uDD14",0,0,false);
      check(Arrays.equals(payload(utf.textPacket()),Base64.getDecoder().decode(golden.getString("utf-label"))),"UTF-8 accents and emoji match independent native codec");
      HPacket packet=body(golden.getString("utf-label"));packet.readInteger();
      check(packet.readString().equals("caf\u00e9\uD83D\uDD14"),"default packet decoder reads UTF-8");
      HPacket replacement=new HPacket(1,"old",42);replacement.replaceString(6,"caf\u00e9");
      check(replacement.readString().equals("caf\u00e9") && replacement.readInteger()==42,"UTF-8 replacement maintains following field offsets");
      HPacket binary=new HPacket(1,new byte[]{(byte)0xff,(byte)0x80,0,42});
      HPacket restored=new HPacket(1);restored.constructFromString(binary.stringify());
      check(Arrays.equals(binary.toBytes(),restored.toBytes()),"G-Earth IPC binary serialization stays byte preserving");
      PresetWiredRoomSettings settings=PresetWiredRoomSettings.fromPacket(body(golden.getString("room-settings")));
      check(settings.modifyMask==3 && settings.readMask==7 && settings.timezone.equals("Europe/Paris"),"native WIRED room settings decoded");
      check(Arrays.equals(payload(settings.savePacket()),Base64.getDecoder().decode(golden.getString("room-settings"))),"room settings match native save codec");
      check(!settings.completeRoom && settings.forScope(true).completeRoom,"room-wide settings require complete-room scope");
      preset.setWiredRoomSettings(settings.forScope(true));
      check(new PresetConfig(preset.toJsonObject()).getWiredRoomSettings().completeRoom,"complete-room scope persists");
      fails(()->PresetFurniLabel.fromPacket(new HPacket(1,9,123,"x")),"unknown label type rejected");
   }

   private static void allFormCodecs() throws Exception {
      JSONArray forms=new JSONArray(Files.readString(Path.of("tests/fixtures/all-form-codecs.json")));
      Set<String> identities=new HashSet<>();
      for(Object value:forms) {
         JSONObject form=(JSONObject)value;String name=form.getString("category")+":"+form.getInt("code");
         check(identities.add(name),"unique native form fixture "+name);
         HPacket incoming=body(form.getString("incoming"));
         PresetWiredBase config=switch(form.getString("category")) {
            case "trigger" -> RetrievedWiredTrigger.fromPacket(incoming);
            case "action" -> RetrievedWiredEffect.fromPacket(incoming);
            case "condition" -> RetrievedWiredCondition.fromPacket(incoming);
            case "selector" -> RetrievedWiredSelector.fromPacket(incoming);
            case "addon" -> RetrievedWiredAddon.fromPacket(incoming);
            case "variable" -> RetrievedWiredVariable.fromPacket(incoming);
            default -> throw new AssertionError(name);
         };
         check(incoming.isEOF()==1,"complete native incoming form "+name);
         check(config.getItems().equals(List.of(7,-8)) && config.getSecondItems().equals(List.of(9,-10)),"both floor/wall selections preserved "+name);
         check(Arrays.equals(payload(config.toPacket()),Base64.getDecoder().decode(form.getString("save"))),"incoming-to-save matches independent native codec "+name);
      }
      check(forms.length()==188,"all inventoried form codes tested");
      System.out.println("Native form-code fixtures: "+forms.length()+" (wire transport, not gameplay semantics)");
   }

   private static void builtinValues() {
      Map<Integer,Integer> ids=WiredFurnitureIds.sourceMap(List.of(7,8),List.of(7,9));
      check(ids.get(7)==1 && ids.get(-7)==-3,"overlapping floor/wall numeric IDs have separate source namespaces");
      check(new HashSet<>(ids.values()).size()==4,"all selected objects keep unique local IDs");
      fails(()->WiredFurnitureIds.sourceMap(List.of(7,7),List.of()),"duplicate source floor object rejected");
      check(!PresetWiredBase.isLocalVariableId("~103"),"special builtin IDs are not room-local references");
      PresetObjectVariables teleport=new PresetObjectVariables(0,7,Map.of("~103",8)).withFurnitureReference("~103");
      check(teleport.remap(Map.of(7,700,8,800),Map.of()).values.get("~103")==800,"internal teleporter target resolves to cloned furniture");
      check(new PresetObjectVariables(teleport.toJsonObject()).remap(Map.of(7,700,8,800),Map.of()).values.get("~103")==800,"teleporter reference metadata survives JSON");
      fails(()->teleport.remap(Map.of(7,700),Map.of()),"missing internal teleporter endpoint rejected");
      PresetObjectVariables custom=new PresetObjectVariables(0,7,Map.of("old-var",99));
      check(custom.remap(Map.of(7,700),Map.of("old-var","new-var")).values.get("new-var")==99,"furniture values remap by ID even with duplicate names");
      fails(()->custom.remap(Map.of(7,700),Map.of()),"unresolved furniture variable cannot become a new numeric-name variable");
      PresetWiredBase overlay=new PresetWiredAddon(definition().put("options",new JSONArray(List.of(0,1,2,3,4,5,6,7,8,9,20,30))));
      overlay.relativizeOverlay("wf_xtra_var_leaderboard",15,25);
      check(overlay.getOptions().subList(10,12).equals(List.of(5,5)),"leaderboard overlay anchor made relative");
      PresetWiredBase overlayCopy=new PresetWiredAddon(overlay.toJsonObject());overlayCopy.translateRoomCoordinates(50,60);
      check(overlayCopy.getOptions().subList(10,12).equals(List.of(55,65)),"leaderboard overlay follows imported origin");
      check(overlay.getOptions().subList(10,12).equals(List.of(5,5)),"overlay translation keeps source immutable");
      fails(()->new HPacket(1,"a".repeat(65536)),"oversize UTF-8 string rejected before length wraps");
      PresetWiredBase config=new PresetWiredTrigger(definition().put("variableIds",new JSONArray(List.of("~103","-110"))).put("items",new JSONArray(List.of(-8))).put("secondItems",new JSONArray(List.of(-8))));
      PresetWiredBase mapped=config.remap(Map.of(123,555,-8,-800),Map.of());
      check(mapped.getItems().equals(List.of(-800)) && mapped.getSecondItems().equals(List.of(-800)),"signed wall IDs remap in both selections");
      check(mapped.getVariableIds().equals(List.of("~103","-110")),"native builtin IDs survive remapping");
      HWiredVariablesForObject global=new HWiredVariablesForObject(body("////9gAAAAEACGdsb2JhbC0xAAAAKg=="));
      check(global.type==-10 && global.objectId==0 && global.variables.get("global-1")==42,"native global variable inspection");
      PresetObjectVariables v=new PresetObjectVariables(0,-8,Map.of("~110",5,"~111",6,"~146",12345));
      PresetObjectVariables translated=v.translate(-3,-4).remap(Map.of(-8,-800),Map.of());
      check(translated.objectId==-800 && translated.values.get("~110")==2 && translated.values.get("~111")==2,"builtin area roots translate with preset");
      check(translated.values.get("~146")==12345 && v.values.get("~110")==5,"builtin colors retained and source immutable");
      HPacket write=translated.packets().get(0);write.readInteger();write.readInteger();write.readString();write.readInteger();
      check(write.readInteger()==0,"builtins use write operation");
      PresetObjectVariables room=new PresetObjectVariables(-10,0,Map.of("old",42));
      PresetObjectVariables resolved=room.remap(Map.of(),Map.of("old","global-1"));
      check(resolved.matches(Map.of("global-1",42)),"room values use remapped variable IDs");
      check(Arrays.equals(payload(resolved.packets().get(0)),Base64.getDecoder().decode("////9gAAAAAACGdsb2JhbC0xAAAAKgAAAAA=")),"global variable restoration uses native Write operation");
      PresetObjectVariables automatic=new PresetObjectVariables(0,7,Map.of("old",99)).withWriteOnlyVariables(Set.of("old")).remap(Map.of(7,700),Map.of("old","new"));
      byte[] automaticBytes=payload(automatic.packets().get(0));
      check(automaticBytes[automaticBytes.length-1]==0,"always-available furniture variable uses Write instead of Create");
      fails(()->room.remap(Map.of(),Map.of()),"missing room variable blocks restoration");
      PresetConfig preset=emptyPreset();preset.setObjectVariables(List.of(v,room));
      check(new PresetConfig(preset.toJsonObject()).getObjectVariables().get(0).matches(v.values),"builtin property JSON round trip");
      PresetRoomLink link=new PresetRoomLink(123,"9999");HPacket packet=link.savePacket();
      check(packet.readInteger()==0 && packet.readInteger()==123 && packet.readString().equals("~103") && packet.readInteger()==9999 && packet.readInteger()==0,"room linker uses native writable property");
      fails(()->new PresetRoomLink(123,"invalid"),"invalid room linker rejected");
      fails(()->new PresetRoomLink(123,"9999999999999"),"room destination overflow rejected before placing furniture");
      fails(()->new HWiredVariablesForObject(new HPacket(1,0,123,-1)),"negative inspection count rejected");
   }

   private static void filesAndHosts() throws Exception {
      check(FurniDataTools.resolveCountryCode("game-fr.habbo.com").equals("fr"),"French game host");
      check(FurniDataTools.resolveCountryCode("https://www.habbo.com.br/").equals("br"),"Brazil hotel URL");
      check(FurniDataTools.resolveCountryCode("www.habbo.com.tr").equals("tr"),"Turkey hotel URL");
      check(FurniDataTools.resolveCountryCode("sandbox.habbo.com").equals("s2"),"sandbox host");
      fails(() -> FurniDataTools.resolveCountryCode("game-fr.habbo.com.evil.test"),"unrecognized domain cannot select a hotel");
      Path directory=Files.createTempDirectory(Path.of("target"),"preset-test-");
      System.setProperty("gpresets.dataDir",directory.toAbsolutePath().toString());
      PresetConfig preset=emptyPreset();
      preset.setAdsBackgrounds(List.of(new PresetAdsBackground(2,"https://example.test/a.png","1","2","3")));
      check(PresetConfigUtils.savePreset("regression",preset),"atomic export saves");
      PresetConfig loaded=PresetConfigUtils.loadPreset("regression");
      check(loaded!=null && loaded.getAdsBackgrounds().get(0).getFurniId()==2,"ad backgrounds survive serialization");
      Path file=PresetConfigUtils.resolvePresetFile("regression");
      String original=Files.readString(file);
      check(!PresetConfigUtils.savePreset("regression",null),"failed serialization returns failure");
      check(Files.readString(file).equals(original),"failed serialization preserves existing export");
      fails(() -> PresetConfigUtils.resolvePresetFile("../outside"),"preset path stays inside preset directory");
      Files.writeString(file,"{invalid");
      check(PresetConfigUtils.loadPreset("regression")==null,"malformed JSON handled without crashing UI");
   }

   private static void preview() {
      PresetFurni table=new PresetFurni(1,"table",new HPoint(-2,3,1.25),2,"1");table.setFurniName("table[0]");
      PresetFurni wired=new PresetFurni(2,"wf_act_send_message",new HPoint(1,4,0),0,null);wired.setFurniName("wired[0]");
      PresetWallFurni wall=new PresetWallFurni(3,"poster",new WallPosition(2,4,12,34,'l',250),"7");wall.setFurniName("poster[0]");
      PresetConfig preset=emptyPreset();preset.setFurniture(new ArrayList<>(List.of(table,wired)));preset.setWallFurniture(new ArrayList<>(List.of(wall)));
      String original=preset.toJsonObject().toString();PostConfig options=new PostConfig();
      PresetPreviewModel model=new PresetPreviewModel(preset,options,name->name.equals("table")?new PresetPreviewModel.Footprint(2,3):null);
      check(model.floorCount==2 && model.wallCount==1 && model.wiredCount==1,"preview counts floors, walls and WIRED");
      PresetPreviewModel.Item item=model.items.stream().filter(i->i.id()==1).findFirst().orElseThrow();
      check(item.width()==3 && item.depth()==2,"preview rotates a rectangular footprint at rotation 2");
      check(item.x()==-2 && item.y()==3 && item.z()==1.25,"preview preserves relative and stacked coordinates");
      check(model.items.stream().filter(PresetPreviewModel.Item::wall).findFirst().orElseThrow().wallLocation().contains("12,34"),"preview retains exact wall offsets in details");
      check(model.approximateCount==1,"offline preview marks unknown floor dimensions");
      check(preset.toJsonObject().toString().equals(original),"preview does not change selected preset");
      boolean immutable=false;try { model.items.add(item); } catch(UnsupportedOperationException expected) { immutable=true; }
      check(immutable,"preview item list immutable");
      PostConfig mapped=new PostConfig();mapped.addClassNameMapping("table","chair");mapped.addFurniPostConfig(new FurniPostConfig("wired[0]",true,999,null,null,null,null,new HashMap<>(),new HashMap<>()));
      PresetPreviewModel post=new PresetPreviewModel(preset,mapped,name->new PresetPreviewModel.Footprint(1,1));
      check(post.reusedCount==1 && post.floorCount==1 && post.items.stream().anyMatch(i->i.className().equals("chair")),"preview applies replacements and excludes existing unmoved furniture");
      check(preset.getFurniture().size()==2 && preset.getFurniture().get(0).getClassName().equals("table"),"post-config preview keeps source intact");
      PresetPreviewSession session=new PresetPreviewSession(preset,options,42,new HPoint(12,13));
      PresetConfig detached=session.preset();detached.getFurniture().clear();
      check(session.preset().getFurniture().size()==2,"preview session freezes the exact preset");
      PostConfig detachedOptions=session.options();detachedOptions.addClassNameMapping("table","other");
      check(session.options().getClassMapping("table")==null,"preview options snapshot is detached");
      check(session.root().getX()==12 && session.root().getY()==13,"preview remembers explicit import origin");
      int[] launches={0};Runnable launch=()->launches[0]++;
      check(!session.confirm(preset,options,42,false,launch) && launches[0]==0,"unready preview cannot launch import");
      check(!session.confirm(preset,options,43,true,launch) && launches[0]==0,"room change blocks preview confirmation");
      check(!session.confirm(new PresetConfig(preset.toJsonObject()),options,42,true,launch),"changed preset blocks confirmation");
      check(!session.confirm(preset,new PostConfig(options),42,true,launch),"changed import options block confirmation");
      check(session.confirm(preset,options,42,true,launch) && launches[0]==1,"explicit valid confirmation starts once");
      check(!session.confirm(preset,options,42,true,launch) && launches[0]==1,"double confirmation cannot start a second import");
      PresetPreviewSession cancelled=new PresetPreviewSession(preset,options,42,null);cancelled.cancel();
      check(cancelled.isClosed() && !cancelled.confirm(preset,options,42,true,launch),"closing or aborting preview never launches");
      check(new PresetPreviewModel(emptyPreset(),options,name->null).isEmpty(),"empty preview handled");
      PresetConfig wallOnly=emptyPreset();wallOnly.setWallFurniture(List.of(wall));
      check(new PresetPreviewModel(wallOnly,options,name->null).wallCount==1,"wall-only preview supported");
      PresetFurni invalid=new PresetFurni(table);invalid.setLocation(new HPoint(0,0,Double.NaN));
      PresetConfig malformed=emptyPreset();malformed.setFurniture(List.of(invalid));
      boolean invalidRejected=false;try {new PresetPreviewModel(malformed,options,name->null);} catch(RuntimeException expected) { invalidRejected=true; }
      check(invalidRejected,"invalid height cannot break projection");
      fails(()->new PresetPreviewModel.Footprint(0,1),"invalid furniture dimensions rejected");
   }

   public static void main(String[] args) throws Exception {
      byte[] wireBytes = new byte[256]; for (int i=0;i<256;i++) wireBytes[i]=(byte)i;
      String wireString = new String(wireBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
      HPacket envelope = new HPacket(1); envelope.appendLongString(wireString);
      check(Arrays.equals(wireBytes, Arrays.copyOfRange(envelope.toBytes(), 10, envelope.getBytesLength())), "G-Earth transport encodes all bytes losslessly");
      check(envelope.readLongString().equals(wireString), "G-Earth transport decodes all bytes losslessly");
      check(envelope.readLongString(6).equals(wireString), "indexed G-Earth transport decode");

      JSONObject golden=new JSONObject(Files.readString(Path.of("tests/fixtures/qx-reference.json")).replace("\uFEFF",""));
      outgoingProtocol(golden);
      modernContexts();
      variableDiffs();
      allIncomingFamilies();
      mapping();
      legacyAndFuture(golden);
      catalog(golden);
      filesAndHosts();
      auxiliaryCodecs();
      regionsAndUnicode();
      allFormCodecs();
      builtinValues();
      preview();
      labels();
      links();
      System.out.println("PASS: "+passed+" regression assertions; native-codec fixtures, JSON, variables, contexts, catalog, and file persistence");
      System.out.println("Live Habbo import/export and gameplay: NOT TESTED");
   }
}
