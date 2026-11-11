package com.example.examplemod.cargo.center;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** One authorized view per request. Duplicate mutations return their previous result without executing again. */
public final class CenterService {
    private static final Map<ServerPlayer,Long> READS=new WeakHashMap<>();
    private static final Map<ServerPlayer,LinkedHashMap<UUID,String>> RESULTS=new WeakHashMap<>();
    private record View(int key,CompoundTag data,int hash) {}
    private static final Map<ServerPlayer,View> VIEWS=new WeakHashMap<>();
    private CenterService(){}
    public static void handle(ServerPlayer player,CompoundTag request){
        if(!request.hasUUID("Request"))return;
        String action=request.getString("Action"),result="ok";long now=player.serverLevel().getGameTime();
        boolean preview=action.equals("ROUTE_PREVIEW");boolean view=Set.of("VIEW","OPEN").contains(action)||preview,quote=Set.of("QUOTE_BUY","QUOTE_SEND","QUOTE_CONTRACT_SUPPLY","ROAD_PREVIEW").contains(action);
        CompoundTag quoted=null;
        // Cancelling a picker is idempotent metadata cleanup and must work even immediately
        // after Start. Keep the throttled reply small; do not bypass limits for any other action.
        if(action.equals("CANCEL_RECEIVER_PICK")&&now-READS.getOrDefault(player,-100L)<4){
            CenterEquipmentSelection.cancel(player);var reply=new CompoundTag();reply.putUUID("Request",request.getUUID("Request"));
            reply.putString("Section",request.getString("Section"));reply.putString("Result","ok");reply.putBoolean("Unchanged",true);
            PacketDistributor.sendToPlayer(player,new CenterState(reply));return;
        }
        if(now-READS.getOrDefault(player,-100L)<4){var reply=new CompoundTag();reply.putUUID("Request",request.getUUID("Request"));reply.putString("Section",request.getString("Section"));reply.putString("Result",view&&!preview?"ok":"rate_limit");reply.putBoolean("Unchanged",true);PacketDistributor.sendToPlayer(player,new CenterState(reply));return;}READS.put(player,now);
        if(quote){try{quoted=switch(action){case "ROAD_PREVIEW"->RailCenter.previewRoad(player,request.getCompound("Values"));case "QUOTE_CONTRACT_SUPPLY"->MasterCityCenter.quoteSupply(player,request.getUUID("Selected"),request.getCompound("Values"));default->CargoFulfillment.quote(player,request.getUUID("Selected"),action.equals("QUOTE_BUY"),request.getCompound("Values"));};}catch(IllegalArgumentException|NoSuchElementException|NullPointerException|ArithmeticException invalid){result="denied: "+Objects.toString(invalid.getMessage(),"Invalid request");}}
        else if(!view){var history=RESULTS.computeIfAbsent(player,p->new LinkedHashMap<>());String previous=history.get(request.getUUID("Request"));if(previous!=null)result=previous;else{
            try{CenterOperations.execute(player,request);}catch(IllegalArgumentException|NoSuchElementException|NullPointerException|ArithmeticException invalid){result="denied: "+Objects.toString(invalid.getMessage(),"Invalid request");}
            history.put(request.getUUID("Request"),result);if(history.size()>128)history.remove(history.keySet().iterator().next());}}
        CompoundTag data;
        try{data=CenterView.snapshot(player,request);}
        catch(IllegalArgumentException|NoSuchElementException invalid){data=new CompoundTag();data.put("Rows",new ListTag());data.put("Forms",new ListTag());data.put("Choices",new CompoundTag());data.putBoolean("Implemented",true);data.putBoolean("Operator",player.hasPermissions(2));if(result.equals("ok"))result="denied: "+Objects.toString(invalid.getMessage(),"Invalid request");}
        if(preview){var mapRequest=request.copy();mapRequest.put("MapRoute",request.getCompound("Values").copy());try{var map=CargoMapView.snapshot(player,mapRequest,data.getCompound("Choices").getList("Points",10));if(map.getBoolean("RouteFound"))data.put("RoutePreview",map);else result="no_route";}catch(IllegalArgumentException|NoSuchElementException invalid){result="no_route";}}
        int hash=data.hashCode();var full=data.copy();var key=new CompoundTag();for(String name:List.of("Section","Search","Page","Selected","Focus","MapRoute","StockSearch","Dimension","RailsOnly"))if(request.contains(name))key.put(name,request.get(name).copy());
        var previous=VIEWS.get(player);
        if(view&&request.contains("Hash")&&request.getInt("Hash")==hash){data=new CompoundTag();data.putBoolean("Unchanged",true);}
        else if(view&&previous!=null&&previous.key()==key.hashCode()&&request.contains("Hash")&&request.getInt("Hash")==previous.hash()){data=CenterDelta.diff(previous.data(),data);}
        VIEWS.put(player,new View(key.hashCode(),full,hash));
        data.putInt("Hash",hash);data.putUUID("Request",request.getUUID("Request"));data.putString("Section",request.getString("Section"));data.putString("Result",result);data.putBoolean("Open",action.equals("OPEN"));data.putBoolean("Mutation",!view&&!quote);if(quoted!=null)data.put("Quote",quoted);
        if(result.equals("ok")&&Set.of("BUY","SEND").contains(action))data.putUUID("OrderId",UUID.nameUUIDFromBytes((player.getUUID()+"/"+request.getUUID("Request")).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if(result.equals("ok")&&action.equals("CONTRACT_SUPPLY"))data.putUUID("OrderId",MasterCityTradeService.stable("public/"+player.getUUID()+"/"+request.getUUID("Request")));
        if(action.equals("PICK_RECEIVER")&&result.equals("ok")){
            data.putBoolean("PickingEquipment",true);var session=CenterEquipmentSelection.session(player);
            if(session!=null)data.putUUID("EquipmentSession",session);
        }
        PacketDistributor.sendToPlayer(player,new CenterState(data));
    }
    static CompoundTag row(UUID id,String name,String detail,String status){var t=new CompoundTag();t.putUUID("Id",id);t.putString("Name",name);t.putString("Detail",detail);t.putString("Status",status);return t;}
}
