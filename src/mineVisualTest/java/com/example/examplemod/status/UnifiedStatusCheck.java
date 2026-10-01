package com.example.examplemod.status;

import com.example.examplemod.client.status.CcecStatusUi;
import com.example.examplemod.economy.FreightPointRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Headless contract checks for the shared Railway/Logistics presentation model. */
public final class UnifiedStatusCheck {
    private static int checks;
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}

    /** Freight Point statuses the classifier can emit; every one needs a lang entry. */
    private static final List<String> POINT_STATUSES=List.of(
            "online","removed","disabled","unloaded","postbox_missing","postbox_incompatible",
            "packager_missing","packager_incompatible","station_missing","station_changed",
            "receiver_missing","address_changed");

    /** Reasons register/rebind report back; surfaced through feedback. */
    private static final List<String> COMMAND_KEYS=List.of(
            "ok","permission","invalid","wrong_city","wrong_dimension","unloaded","limit","duplicate",
            "postbox_missing","postbox_incompatible","packager_missing","packager_incompatible",
            "packager_too_far","station_missing","receiver_missing");

    /** Reasons revoke reports back; surfaced through feedback and the inline shipment hint. */
    private static final List<String> REVOKE_KEYS=List.of(
            "freight_revoked","freight_revoke_already","freight_revoke_denied",
            "freight_revoke_state","freight_revoke_unknown");

    private static final Map<String,CcecStatus.Code> EXPECTED_CODE=Map.ofEntries(
            Map.entry("online",CcecStatus.Code.POINT_READY),
            Map.entry("removed",CcecStatus.Code.POINT_REMOVED),
            Map.entry("disabled",CcecStatus.Code.POINT_DISABLED),
            Map.entry("unloaded",CcecStatus.Code.POINT_UNLOADED),
            Map.entry("postbox_missing",CcecStatus.Code.POINT_POSTBOX_MISSING),
            Map.entry("postbox_incompatible",CcecStatus.Code.POINT_POSTBOX_INCOMPATIBLE),
            Map.entry("packager_missing",CcecStatus.Code.POINT_PACKAGER_MISSING),
            Map.entry("packager_incompatible",CcecStatus.Code.POINT_PACKAGER_INCOMPATIBLE),
            Map.entry("station_missing",CcecStatus.Code.POINT_STATION_MISSING),
            Map.entry("station_changed",CcecStatus.Code.POINT_STATION_MISMATCH),
            Map.entry("receiver_missing",CcecStatus.Code.POINT_RECEIVER_MISSING),
            Map.entry("address_changed",CcecStatus.Code.POINT_ADDRESS_MISMATCH));

    public static void main(String[] args){
        for(CcecStatus.Code code:CcecStatus.Code.values()){
            CcecStatus original=CcecStatus.of(category(code),code,action(code));
            CcecStatus restored=CcecStatus.load(original.save());
            require(original.equals(restored),"NBT round-trip: "+code);
            require(original.key().matches("[a-z0-9_]+"),"translation-safe key: "+code);
        }
        require(EnumSet.allOf(CcecStatus.Category.class).size()==4,"four visual categories");
        require(CcecStatus.Code.valueOf("INSUFFICIENT_FUNDS")!=null,"funds state represented");
        require(CcecStatus.Code.valueOf("PRIVATE_ACCESS_DENIED")!=null,"private access state represented");
        require(CcecStatus.Code.valueOf("SHIPMENT_REVOKED")!=null,"revoked shipment state represented");

        // The pure point classifier and this catalog table must agree, and it must never leak the
        // raw status string into a card the player reads.
        for(String status:POINT_STATUSES){
            CcecStatus fromRules=FreightPointRules.ui(status);
            require(fromRules.key().matches("[a-z0-9_]+"),"translation-safe point key: "+status);
            require(fromRules.code()==EXPECTED_CODE.get(status),
                    "point status maps onto its own code: "+status+" -> "+fromRules.code());
            require(fromRules.equals(CcecStatus.of(category(fromRules.code()),fromRules.code(),action(fromRules.code()))),
                    "point status uses the catalog category/action: "+status);
        }
        require(EXPECTED_CODE.size()==POINT_STATUSES.size(),"point status table is complete");
        require(FreightPointRules.ui("online").category()==CcecStatus.Category.SUCCESS,"online reads as success");
        require(FreightPointRules.ui("station_changed").action()==CcecStatus.Action.REBIND_POINT,"station mismatch asks for a rebind");

        // Toast policy: only meaningful transitions, never one per refresh.
        require(CcecStatusUi.decision(null,"online")==CcecStatusUi.PointToast.SILENT,"first observation primes only");
        require(CcecStatusUi.decision("online","online")==CcecStatusUi.PointToast.SILENT,"steady state is silent");
        require(CcecStatusUi.decision("unloaded","online")==CcecStatusUi.PointToast.SILENT,"chunk reload is not a recovery event");
        require(CcecStatusUi.decision("disabled","online")==CcecStatusUi.PointToast.SILENT,"re-enabling is acknowledged by the toggle itself");
        require(CcecStatusUi.decision("online","postbox_missing")==CcecStatusUi.PointToast.DEGRADED,"breaking a point is announced");
        require(CcecStatusUi.decision("postbox_missing","packager_missing")==CcecStatusUi.PointToast.DEGRADED,"reason change while broken is announced");
        require(CcecStatusUi.decision("postbox_missing","online")==CcecStatusUi.PointToast.RESTORED,"recovery is announced");
        require(CcecStatusUi.decision("station_changed","online")==CcecStatusUi.PointToast.RESTORED,"rebind success is announced");
        require(CcecStatusUi.decision("online","unloaded")==CcecStatusUi.PointToast.SILENT,"chunk unload is quiet");

        // Language completeness for the whole catalog and for every reason the player can see.
        Map<String,Map<String,String>> langs=loadLang();
        Map<String,String> en=langs.get("en_us"),ru=langs.get("ru_ru");
        require(en!=null&&!en.isEmpty(),"en_us.json loaded");
        require(ru!=null&&!ru.isEmpty(),"ru_ru.json loaded");
        Set<String> missing=new TreeSet<>();
        for(CcecStatus.Code code:CcecStatus.Code.values()){
            for(String part:List.of("title","reason","hint"))checkKey(en,ru,"status.createcityexpansioncore.unified."+code.name().toLowerCase(java.util.Locale.ROOT)+"."+part,missing);
            checkKey(en,ru,"status.createcityexpansioncore.action."+action(code).name().toLowerCase(java.util.Locale.ROOT),missing);
        }
        checkKey(en,ru,"status.createcityexpansioncore.action.rebind_point",missing);
        for(String status:POINT_STATUSES)checkKey(en,ru,"gui.createcityexpansioncore.freight."+status,missing);
        for(String reason:COMMAND_KEYS)checkKey(en,ru,"gui.createcityexpansioncore.freight."+reason,missing);
        for(String reason:REVOKE_KEYS)checkKey(en,ru,"message.createcityexpansioncore.economy."+reason,missing);
        for(String console:List.of("rebind","rebind.title","rebind.step","rebind.help","rebind_confirm","rebind.failed",
                "station_mismatch_at","check.mismatch","revoke","revoke_warning","revoke_help","confirm_revoke"))
            checkKey(en,ru,"gui.createcityexpansioncore.freight.console."+console,missing);
        checkKey(en,ru,"gui.createcityexpansioncore.freight.point_restored",missing);
        require(missing.isEmpty(),"missing translation keys: "+missing);

        System.out.println("PASS: "+checks+" unified status serialization, catalog, toast policy and lang completeness checks");
    }

    private static void checkKey(Map<String,String> en,Map<String,String> ru,String key,Set<String> missing){
        if(!en.containsKey(key))missing.add("en_us "+key);
        if(!ru.containsKey(key))missing.add("ru_ru "+key);
    }

    private static final Pattern KEY=Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:");

    /** Reads the shipped lang files directly; tests run with a build/checks working directory. */
    private static Map<String,Map<String,String>> loadLang(){
        Path root=Paths.get("").toAbsolutePath();
        Path lang=null;
        for(int i=0;i<8&&root!=null&&lang==null;i++){
            Path candidate=root.resolve("src/main/resources/assets/createcityexpansioncore/lang");
            if(Files.isRegularFile(candidate.resolve("en_us.json")))lang=candidate;
            root=root.getParent();
        }
        Map<String,Map<String,String>> langs=new HashMap<>();
        if(lang!=null){
            langs.put("en_us",keys(lang.resolve("en_us.json")));
            langs.put("ru_ru",keys(lang.resolve("ru_ru.json")));
        }
        return langs;
    }

    private static Map<String,String> keys(Path file){
        try{
            String text=Files.readString(file);
            Matcher matcher=KEY.matcher(text);
            List<String> found=new ArrayList<>();
            while(matcher.find())found.add(matcher.group(1).replace("\\\"","\"").replace("\\\\","\\"));
            Map<String,String> map=new HashMap<>();
            // Values are not needed for presence checks; the last write wins, duplicates are harmless.
            for(String key:found)map.putIfAbsent(key,key);
            return map;
        }catch(Exception ex){
            throw new AssertionError("cannot read "+file+": "+ex);
        }
    }

    private static CcecStatus.Category category(CcecStatus.Code code){return switch(code){
        case READY,ARRIVED,READY_TO_DISPATCH,DELIVERED,POINT_READY->CcecStatus.Category.SUCCESS;
        case NETWORK_OFFLINE,SECTION_OFFLINE,SOURCE_OFFLINE,DESTINATION_OFFLINE,
             POINT_STATION_MISSING,POINT_UNLOADED,POINT_DISABLED,POINT_REMOVED->CcecStatus.Category.WARNING;
        case ROUTE_INVALIDATED,PRIVATE_ACCESS_DENIED,INSUFFICIENT_FUNDS,RAIL_ROUTE_UNAVAILABLE,SHIPMENT_INVALIDATED,SHIPMENT_REVOKED,
             POINT_STATION_MISMATCH->CcecStatus.Category.BLOCKED;
        default->CcecStatus.Category.ACTION_REQUIRED;
    };}
    private static CcecStatus.Action action(CcecStatus.Code code){return switch(code){
        case WAITING_FOR_ROUTE,ROUTE_INVALIDATED,ROUTE_NOT_SELECTED->CcecStatus.Action.SELECT_ROUTE;
        case READY->CcecStatus.Action.CONFIRM_DEPARTURE;
        case READY_TO_DISPATCH->CcecStatus.Action.START_DISPATCH;
        case IN_TRANSIT,WAITING_FOR_CARGO,TRAIN_NOT_ASSIGNED->CcecStatus.Action.OPEN_FREIGHT_POINT;
        case ARRIVED,DELIVERED,POINT_READY,POINT_UNLOADED,POINT_DISABLED,POINT_REMOVED->CcecStatus.Action.NONE;
        case POINT_POSTBOX_INCOMPATIBLE,POINT_PACKAGER_INCOMPATIBLE,POINT_STATION_MISMATCH,POINT_ADDRESS_MISMATCH->CcecStatus.Action.REBIND_POINT;
        default->CcecStatus.Action.FIX_PROBLEM;
    };}
}
