package com.example.examplemod.cargo.center;

import com.example.examplemod.client.logistics.*;
import com.google.gson.*;
import net.createmod.catnip.data.Glob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Data, localization, persistence and pinned Create API contracts; no simulated train movement. */
public final class CenterEquipmentCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}require(rejected,message);}
    private static JsonObject language(String locale)throws Exception{
        try(var input=CenterEquipmentCheck.class.getClassLoader().getResourceAsStream("assets/createcityexpansioncore/lang/"+locale+".json")){
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static List<CompoundTag> forms(String section,CompoundTag row){return CenterForms.forms(section,row).stream().map(t->(CompoundTag)t).toList();}
    private static void bindings() {
        require(CenterEquipmentSelection.unambiguous(List.of()).isEmpty(),"equipment autofind never guesses when no eligible postbox exists");
        require(CenterEquipmentSelection.unambiguous(List.of("one")).orElseThrow().equals("one"),"one eligible postbox can be proposed automatically");
        require(CenterEquipmentSelection.unambiguous(List.of("one","two")).isEmpty(),"ambiguous postboxes require explicit player selection");
        require(CenterEquipmentSelection.withinReach(64)&&!CenterEquipmentSelection.withinReach(64.0001),"world picker rejects out-of-reach block clicks");
        var row=new CompoundTag();row.putString("Name","Причал");row.putString("Kind","point");row.putString("Status","CONFIGURED");
        var routers=new ListTag();CenterCardData.part(routers,"router","","CONNECTED","10, 64, 20");CenterCardData.part(routers,"postbox","","CONNECTED","10, 65, 20");
        var stations=new ListTag();CenterCardData.part(stations,"station","Моржеград","CONNECTED","");CenterCardData.part(stations,"station","Депо","MISSING","");
        CenterCardData.point(row,routers,stations,1,2,1,0);
        require(row.getInt("StationCount")==2&&row.getInt("RouterCount")==1,"actual station bindings and router counts are independent");
        require(row.getList("Details",10).size()==4&&row.getList("Details",10).getCompound(0).getString("Name").equals("Моржеград"),"bound stations are present and take priority in cards");
        require(!row.getBoolean("DetailsTruncated")&&row.getString("Automatic").equals("ON"),"complete equipment snapshot reports enabled automatic routing");
        stations.getCompound(0).putString("Name","mutated");require(row.getList("Details",10).getCompound(0).getString("Name").equals("Моржеград"),"snapshot does not retain mutable source entries");
        CenterCardData.point(row,routers,new ListTag(),2,0,1,0);require(row.getString("Automatic").equals("PARTIAL"),"mixed automatic routing is explicit");
        CenterCardData.point(row,routers,new ListTag(),2,0,0,1);require(row.getString("Automatic").equals("UNKNOWN"),"unloaded router never falsely reports automatic routing disabled");
        CenterCardData.point(row,new ListTag(),new ListTag(),0,0,0,0);require(row.getString("Automatic").equals("OFF"),"empty terminal has no automatic routing");
        var many=new ListTag();for(int i=0;i<300;i++)CenterCardData.part(many,"router","R"+i,"CONNECTED","");
        require(many.size()==256,"equipment lists are bounded");CenterCardData.point(row,many,stations,300,2,0,0);
        require(row.getList("Details",10).size()==256&&row.getBoolean("DetailsTruncated"),"large card is bounded and explicitly reports truncation");

        row=new CompoundTag();row.putString("Kind","warehouse");row.putString("Receiver","CONNECTED");
        require(forms("warehouses",row).stream().noneMatch(f->f.getString("Action").equals("WAREHOUSE_RECEIVER_REMOVE")),"read-only warehouse cannot advertise detaching");
        row.putBoolean("Manage",true);require(forms("warehouses",row).stream().anyMatch(f->f.getString("Action").equals("WAREHOUSE_RECEIVER_REMOVE")),"manager can detach receiving packager");
        row.putString("Receiver","NOT_CONFIGURED");require(forms("warehouses",row).stream().noneMatch(f->f.getString("Action").equals("WAREHOUSE_RECEIVER_REMOVE")),"unbound warehouse does not advertise detaching");
        row.putString("Kind","point");
        for(var form:forms("points",row))for(Tag raw:form.getList("Fields",10)){
            var field=(CompoundTag)raw;
            if(field.getString("Key").equals("Router"))require(field.getString("Source").equals("BoundRouters"),"router actions choose only bindings of the selected terminal");
            if(form.getString("Action").equals("STATION_REMOVE"))require(field.getString("Source").equals("BoundStations"),"station detaching selects only bindings of this terminal");
        }
        row.putString("Kind","line");row.putBoolean("Manage",false);
        require(forms("lines",row).stream().noneMatch(f->f.getString("Action").equals("LINE_SCHEDULE")),"read-only carrier access cannot assign schedules");
        row.putBoolean("Manage",true);require(forms("lines",row).stream().anyMatch(f->f.getString("Action").equals("LINE_SCHEDULE")),"line manager has explicit schedule action");

        var location=new CargoInfrastructure.Location(ResourceLocation.parse("minecraft:overworld"),new BlockPos(10,64,20));
        var warehouse=new CargoWarehouseRegistry.Warehouse(UUID.randomUUID(),UUID.randomUUID(),"Склад",UUID.randomUUID(),location,UUID.randomUUID(),UUID.randomUUID(),location);
        var detached=CargoWarehouseRegistry.withoutReceiver(warehouse);
        require(warehouse.receiver()!=null&&detached.receiver()==null,"detaching does not mutate original registration");
        require(detached.id().equals(warehouse.id())&&detached.creator().equals(warehouse.creator())&&detached.point().equals(warehouse.point())&&detached.ticker().equals(warehouse.ticker())&&detached.tickerInstance().equals(warehouse.tickerInstance())&&detached.network().equals(warehouse.network())&&detached.name().equals(warehouse.name()),"detaching preserves warehouse identity, stock network and physical ticker");
        var encoded=new CompoundTag();encoded.putInt("Version",1);var entries=new ListTag();var entry=new CompoundTag();entry.putUUID("Id",detached.id());entry.putUUID("Creator",detached.creator());entry.putUUID("Point",detached.point());entry.putString("Name",detached.name());entry.put("Ticker",detached.ticker().save());entry.putUUID("Instance",detached.tickerInstance());entry.putUUID("Network",detached.network());entries.add(entry);encoded.put("Entries",entries);
        var loaded=CargoWarehouseRegistry.load(encoded,null);require(loaded.find(detached.id()).orElseThrow().equals(detached),"detached warehouse survives persistence without a Receiver tag");
        require(CargoWarehouseRegistry.load(loaded.save(new CompoundTag(),null),null).find(detached.id()).orElseThrow().equals(detached),"detached state survives repeated saves");
    }
    private static void schedules()throws Exception {
        require(CargoSchedulePlan.cycle(List.of("А","Б"),15).equals(List.of("А","Б")),"two stops cycle without duplicated endpoints");
        require(CargoSchedulePlan.cycle(List.of("А","Б","В"),15).equals(List.of("А","Б","В","Б")),"return route includes intermediate stop");
        require(CargoSchedulePlan.cycle(List.of("А","Б","В","Г"),15).equals(List.of("А","Б","В","Г","В","Б")),"long return route is ordered");
        for(int seconds:new int[]{10,15,300})require(CargoSchedulePlan.cycle(List.of("А","Б"),seconds).size()==2,"valid dwell time accepted");
        for(int seconds:new int[]{-1,0,9,301,Integer.MAX_VALUE})rejects(()->CargoSchedulePlan.cycle(List.of("А","Б"),seconds),"invalid dwell time rejected");
        rejects(()->CargoSchedulePlan.cycle(List.of(),15),"empty route rejected");rejects(()->CargoSchedulePlan.cycle(List.of("А"),15),"one stop rejected");
        rejects(()->CargoSchedulePlan.cycle(List.of("А","А"),15),"duplicate station names rejected");
        for(String name:List.of(""," ","Станция*","Станция?","x".repeat(257)))rejects(()->CargoSchedulePlan.cycle(List.of("А",name),15),"ambiguous or invalid station name rejected");
        var maximum=new ArrayList<String>();for(int i=0;i<32;i++)maximum.add("Станция "+i);
        require(CargoSchedulePlan.cycle(maximum,15).size()==62,"maximum route has bounded return journey");maximum.add("Лишняя");rejects(()->CargoSchedulePlan.cycle(maximum,15),"route stop bound enforced");
        var planned=new CompoundTag();planned.putString("Route","А-Б");var progressed=planned.copy();progressed.putInt("Progress",5);
        require(CargoSchedulePlan.shape(progressed).equals(planned)&&progressed.contains("Progress"),"progress ignored without mutating current schedule");
        require(CargoSchedulePlan.replaceable(null,null,false),"empty schedule accepts generated route");
        require(!CargoSchedulePlan.replaceable(planned,planned,false),"manual schedule cannot be overwritten even with identical contents");
        require(!CargoSchedulePlan.replaceable(planned,null,true),"unowned automatic schedule cannot be overwritten");
        require(CargoSchedulePlan.replaceable(progressed,planned,true),"own generated route may be refreshed after progress");
        var changed=planned.copy();changed.putString("Route","А-В");require(!CargoSchedulePlan.replaceable(changed,planned,true),"edited schedule is protected");

        for(String station:List.of("Моржеград","Причал [2]","Депо {Восток}","Путь \\ 1","Станция (2)","a.b+c")){
            String pattern=Glob.toRegexPattern(CargoSchedulePlan.filter(station),"");
            require(station.matches(pattern)&&!"Чужая станция".matches(pattern),"actual Create glob selects literal station name");
        }
        String bracketPattern=Glob.toRegexPattern(CargoSchedulePlan.filter("Причал [2]"),"");
        require(!"Причал 2".matches(bracketPattern),"brackets never broaden station matching");
        var scheduleType=bytecode("com/example/examplemod/cargo/center/CargoSchedules");
        var build=scheduleType.methods.stream().filter(m->m.name.equals("build")).findFirst().orElseThrow();
        require(call(build,"com/simibubi/create/content/trains/schedule/destination/DestinationInstruction","setData")>=0&&literal(build,"Text"),"builder uses pinned native destination metadata");
        require(call(build,"com/simibubi/create/content/trains/schedule/condition/ScheduledDelay","setData")>=0&&literal(build,"Value")&&literal(build,"TimeUnit"),"builder uses pinned native timed wait metadata");
        require(java.util.stream.StreamSupport.stream(build.instructions.spliterator(),false).anyMatch(i->i instanceof FieldInsnNode f&&f.owner.endsWith("TimedWaitCondition$TimeUnit")&&f.name.equals("SECONDS")),"wait unit comes from actual Create seconds enum");
        var apply=scheduleType.methods.stream().filter(m->m.name.equals("apply")).findFirst().orElseThrow();int assignment=call(apply,"java/util/LinkedHashMap","forEach");
        require(assignment>call(apply,"com/example/examplemod/cargo/center/CargoSchedulePlan","replaceable")&&assignment>call(apply,"com/simibubi/create/content/trains/entity/Train","hasForwardConductor")&&assignment>call(apply,"com/example/examplemod/cargo/center/CargoRailConnections","path"),"complete fleet is checked before native assignment stage");
        UUID id=UUID.randomUUID();var storage=new CompoundTag();storage.putInt("Version",1);var records=new ListTag();var record=new CompoundTag();record.putUUID("Id",id);record.put("Schedule",planned.copy());records.add(record);storage.put("Trains",records);
        var saved=CargoSchedules.load(storage,null);var copy=saved.save(new CompoundTag(),null);require(copy.equals(storage),"generated schedule provenance survives save/load");
        copy.getList("Trains",10).getCompound(0).getCompound("Schedule").putString("Route","forged");require(saved.save(new CompoundTag(),null).equals(storage),"saved schedule snapshots cannot mutate provenance");
        records.add(record.copy());rejects(()->CargoSchedules.load(storage,null),"duplicate persisted train identity rejected");
    }
    private static ClassNode bytecode(String name)throws Exception {var type=new ClassNode();try(var input=CenterEquipmentCheck.class.getClassLoader().getResourceAsStream(name+".class")){new ClassReader(Objects.requireNonNull(input)).accept(type,0);}return type;}
    private static int call(MethodNode method,String owner,String name){for(int i=0;i<method.instructions.size();i++)if(method.instructions.get(i) instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))return i;return -1;}
    private static boolean literal(MethodNode method,String text){for(var i:method.instructions)if(i instanceof LdcInsnNode c&&text.equals(c.cst))return true;return false;}
    private static void localization()throws Exception {
        var ru=language("ru_ru");var en=language("en_us");
        for(var entry:ru.entrySet())if(entry.getKey().startsWith("ccec.center.")){
            require(en.has(entry.getKey()),"new logistics translations exist in both locales");
            require(!entry.getValue().getAsString().matches("(?s).*(?:Router|Postbox|Packager|Ticker|Logistics Center|WAITING_TRAIN|PACKAGED|REQUESTED).*"),"Russian UI contains no untranslated equipment or state names");
        }
        Function<String,String> language=key->key.equals("item.minecraft.iron_ingot")?"Железный слиток":ru.has(key)?ru.get(key).getAsString():key;
        for(var stage:CargoShipmentRegistry.Stage.values())require(!CenterText.value(stage.name(),language).equals(ru.get("ccec.center.unknown_state").getAsString()),"every shipment state is localized");
        for(var state:CargoCommerceBook.State.values())require(!CenterText.value(state.name(),language).equals(ru.get("ccec.center.unknown_state").getAsString()),"every payment state is localized");
        require(CenterText.history("PACKAGED 1/4",language).equals("Упаковано · 1/4"),"packaging progress is readable");
        require(CenterText.history("WAITING_TRAIN Еуыеу",language).equals("Ожидает поезда · Еуыеу"),"train wait translated while player-supplied name is preserved");
        require(CenterText.history("IN_TRANSIT Express",language).equals("В пути · Express"),"train names are not mistranslated as protocol codes");
        require(CenterText.history("FAILED: Physical quantity mismatch",language).equals("Ошибка · Количество предметов не соответствует отправке"),"failure reason translated");
        require(!CenterText.history("Internal request detail",language).contains("Internal"),"unknown internal reason is not leaked into UI");
        var row=new CompoundTag();row.putString("Name","Iron Ingot");row.putString("ItemKey","item.minecraft.iron_ingot");row.putInt("ItemCount",4);
        require(CenterText.name(row,language).equals("Железный слиток × 4"),"server-side English item name translated on client");row.remove("ItemKey");row.putString("Name","Мой груз");require(CenterText.name(row,language).equals("Мой груз × 4"),"custom item name preserved and quantity shown once");
        row=new CompoundTag();row.putString("Name","Причал");row.putString("Kind","point");row.putString("Status","CONFIGURED");var stations=new ListTag();CenterCardData.part(stations,"station","Моржеград","CONNECTED","");CenterCardData.point(row,new ListTag(),stations,0,1,0,0);
        var fields=CenterText.fields(row,language);require(fields.stream().anyMatch(f->f.label().equals("Станция")&&f.value().contains("Моржеград")&&f.value().contains("Подключено")),"full card shows actual bound station and connectivity");
        require(fields.stream().anyMatch(f->f.label().equals("Привязано станций")&&f.value().equals("1")),"full card shows station count");
        require(fields.stream().noneMatch(f->f.label().startsWith("ccec.")||f.value().contains("CONNECTED")),"typed card has no untranslated labels or state codes");
        var cancelled=new CompoundTag();cancelled.putString("Kind","shipment");cancelled.putString("OrderState","CANCELLING");cancelled.putInt("Quantity",12);cancelled.putInt("Recovered",4);
        var cancellationFields=CenterText.fields(cancelled,language);
        require(cancellationFields.stream().anyMatch(f->f.label().equals(ru.get("ccec.center.cancel_reserve").getAsString())&&f.value().equals(ru.get("ccec.center.cancel_reserve_help").getAsString())),"pending cancellation explains why physical cargo reserve remains held");
        require(cancellationFields.stream().anyMatch(f->f.label().equals(ru.get("ccec.center.recovered").getAsString())&&f.value().equals("4 / 12")),"recovery progress remains distinct from delivered goods");
        for(int width:new int[]{320,426,500,639,640,854,1280}){
            int columns=CenterActionCatalog.columns(width),button=CenterActionCatalog.buttonWidth(width),panel=Math.min(680,width-24);
            require(columns*button+(columns-1)*10+24<=panel&&button>0,"action menu buttons fit scaled window");
        }
        require(CenterActionCatalog.group("REMOVE")==4&&CenterActionCatalog.group("LINE_SCHEDULE")==1&&CenterActionCatalog.group("ACCESS")==3,"destructive, transport and access actions are separate groups");
        require(CenterActionCatalog.icon("LINE_SCHEDULE")==11&&CenterActionCatalog.icon("WAREHOUSE_RECEIVER_REMOVE")==10,"schedule and detach actions have semantic icons");
    }
    private static void guardContracts()throws Exception {
        var type=new ClassNode();try(var input=CenterEquipmentCheck.class.getClassLoader().getResourceAsStream("com/example/examplemod/cargo/center/CargoWarehouseRegistry.class")){new ClassReader(Objects.requireNonNull(input)).accept(type,0);}
        var attach=type.methods.stream().filter(m->m.name.equals("attachReceiverAt")).findFirst().orElseThrow();
        int receiver=call(attach,"com/example/examplemod/cargo/center/CargoWarehouseRegistry","receivingInventory"),write=call(attach,"java/util/Map","put");
        require(receiver>=0&&write>receiver,"receiver physical validation runs before registration update");
        var fulfillment=bytecode("com/example/examplemod/cargo/center/CargoFulfillment");
        var prepare=fulfillment.methods.stream().filter(m->m.name.equals("prepare")).findFirst().orElseThrow();
        require(call(prepare,"com/example/examplemod/cargo/center/CargoWarehouseRegistry","receivingInventory")>=0,"quote preparation checks actual receiving inventory");
        var confirm=fulfillment.methods.stream().filter(m->m.name.equals("confirm")).findFirst().orElseThrow();
        int preflight=call(confirm,"com/example/examplemod/cargo/center/CargoFulfillment","prepare"),reserve=call(confirm,"com/example/examplemod/cargo/center/CargoCommerceBook","reserve");
        require(preflight>=0&&reserve>preflight,"confirmation rechecks physical receiver before debiting escrow");
        var method=type.methods.stream().filter(m->m.name.equals("detachReceiver")).findFirst().orElseThrow();int guard=-1,update=-1,manage=-1;
        for(int i=0;i<method.instructions.size();i++)if(method.instructions.get(i) instanceof MethodInsnNode call){if(call.name.equals("manages"))manage=i;if(call.name.equals("requireUnused"))guard=i;if(call.owner.equals("java/util/Map")&&call.name.equals("put"))update=i;}
        require(manage>=0&&guard>manage&&update>guard,"receiver mutation has authorization and active shipment guard before update");
    }
    public static void run()throws Exception {bindings();schedules();localization();guardContracts();System.out.println("PASS: "+checks+" equipment cards, detach, schedule and localization checks");}
}
