package com.example.examplemod.passenger;

import com.example.examplemod.client.screen.PassengerLinesScreen;
import java.util.UUID;
import java.util.WeakHashMap;
import com.simibubi.create.content.trains.RailwaySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="createcityexpansioncore", bus=EventBusSubscriber.Bus.MOD)
public final class PassengerEditorNetwork {
    // The ordinal is on the wire. Append new actions after the existing ones.
    public enum Action { LIST, PLATFORM, CREATE, APPEND, POP, START, STOP, APPEND_REGISTERED, START_TRAIN, CLEAR_STOPS, DELETE, SET_COLOR, SELECT_TRAIN, GET_BINDER }
    private static final WeakHashMap<ServerPlayer,Long> LAST = new WeakHashMap<>();
    public record Request(UUID nonce, Action action, UUID line, String name, int dwell) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("passenger_editor_request_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC = StreamCodec.of((buf,p) -> {
            buf.writeUUID(p.nonce); buf.writeEnum(p.action); buf.writeUUID(p.line); buf.writeUtf(p.name,64); buf.writeVarInt(p.dwell);
        }, buf -> new Request(buf.readUUID(),buf.readEnum(Action.class),buf.readUUID(),buf.readUtf(64),buf.readVarInt()));
        public Type<Request> type(){return TYPE;}
    }
    public record Reply(CompoundTag data) implements CustomPacketPayload {
        static final Type<Reply> TYPE = new Type<>(id("passenger_editor_reply_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC = StreamCodec.of((buf,p)->buf.writeNbt(p.data), buf->new Reply(buf.readNbt()));
        public Type<Reply> type(){return TYPE;}
    }
    /** Negotiated independently of the original editor's enum and 64-character command argument. */
    public record DescriptionRequest(UUID nonce, UUID line, String expected, String description) implements CustomPacketPayload {
        public static final Type<DescriptionRequest> TYPE = new Type<>(id("passenger_editor_description_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf, DescriptionRequest> CODEC = StreamCodec.of((buf, request) -> {
            buf.writeUUID(request.nonce);
            buf.writeUUID(request.line);
            buf.writeUtf(request.expected, PassengerService.MAX_DESCRIPTION);
            buf.writeUtf(request.description, PassengerService.MAX_DESCRIPTION);
        }, buf -> new DescriptionRequest(buf.readUUID(), buf.readUUID(), buf.readUtf(PassengerService.MAX_DESCRIPTION),
                buf.readUtf(PassengerService.MAX_DESCRIPTION)));
        @Override public Type<DescriptionRequest> type() { return TYPE; }
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("createcityexpansioncore",path);}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("passenger-editor-1").optional();
        registrar.playToServer(Request.TYPE,Request.CODEC,(packet,ctx)->ctx.enqueueWork(()->{
            if(ctx.player() instanceof ServerPlayer player) handle(player,packet);
        }));
        registrar.playToServer(DescriptionRequest.TYPE, DescriptionRequest.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer actor && actor.connection.hasChannel(Reply.TYPE)) {
                handle(actor, packet.nonce, packet.line, () -> {
                    PassengerService.setDescription(actor, packet.line, packet.expected, packet.description);
                    return packet.line;
                });
            }
        }));
        registrar.playToClient(Reply.TYPE,Reply.CODEC,(packet,ctx)->ctx.enqueueWork(()->{
            if(com.example.examplemod.client.CityPublicDirectory.currentConnection(ctx.connection())) PassengerLinesScreen.receive(packet.data);
        }));
    }
    private static void handle(ServerPlayer actor,Request request){
        handle(actor, request.nonce, request.line, () -> execute(actor, request));
    }

    private static UUID execute(ServerPlayer actor, Request request) {
        UUID selected = request.line;
        switch(request.action){
            case LIST -> { }
            case PLATFORM -> PassengerService.platform(actor,false);
            case CREATE -> selected=PassengerService.create(actor,request.name.strip());
            case APPEND -> PassengerService.append(actor,request.line,null,true);
            case APPEND_REGISTERED -> PassengerService.append(actor,request.line,UUID.fromString(request.name),false);
            case POP -> PassengerService.pop(actor,request.line);
            case START -> PassengerService.start(actor,request.line,request.dwell);
            case START_TRAIN -> PassengerService.start(actor,request.line,request.dwell,UUID.fromString(request.name));
            case STOP -> PassengerService.close(actor,request.line,false);
            case CLEAR_STOPS -> PassengerService.clearStops(actor,request.line);
            case DELETE -> PassengerService.close(actor,request.line,true);
            case SET_COLOR -> PassengerService.setColor(actor,request.line,request.name);
            case SELECT_TRAIN -> PassengerService.chooseTrain(actor,request.line,request.name.isEmpty()?null:UUID.fromString(request.name));
            case GET_BINDER -> {
                var item=com.example.examplemod.CreateCityExpansionCore.RAILWAY_BINDER.get();
                if (!actor.getInventory().contains(stack->stack.is(item)) && !actor.getInventory().add(item.getDefaultInstance()))
                    throw new IllegalArgumentException("inventory_full");
            }
        }
        return selected;
    }

    private static void handle(ServerPlayer actor, UUID nonce, UUID selected, java.util.function.Supplier<UUID> mutation) {
        long now=net.minecraft.Util.getMillis();
        if(!actor.isAlive())return;
        boolean operator = actor.hasPermissions(2);
        if(!operator || now-LAST.getOrDefault(actor,now-250L)<250){
            var limited=new CompoundTag();
            limited.putUUID("Nonce",nonce);
            limited.putUUID("Selected",selected);
            limited.putString("Status",operator ? "rate" : "op_required");
            PacketDistributor.sendToPlayer(actor,new Reply(limited));
            return;
        }
        LAST.put(actor,now);
        String status="ok";
        try {
            selected = mutation.get();
        } catch(IllegalArgumentException e){
            status=e.getMessage()!=null&&e.getMessage().matches("[a-z_]{1,32}")?e.getMessage():"unavailable";
        }
        var data=PassengerData.get(actor.getServer());
        var publicStops = PassengerService.stops(actor);
        var names = new java.util.HashMap<UUID,String>();
        var stations = new ListTag();
        var marked = RailwayBinder.selected(actor,RailwayBinder.STATIONS);
        for (var stop : publicStops) {
            names.put(stop.id(),stop.name());
            if (!actor.level().dimension().location().equals(stop.dimension())) continue;
            var row = new CompoundTag();
            row.putUUID("Id",stop.id()); row.putString("Name",stop.name());
            row.putString("Position",stop.stationPos().toShortString());
            row.putBoolean("Marked",marked.contains(stop.id()));
            stations.add(row);
        }
        var lines=new ListTag();
        for(var line:data.lines.values()){
            if(!line.owner.equals(actor.getUUID())&&!actor.hasPermissions(2))continue;
            var row=new CompoundTag(); row.putUUID("Id",line.id);row.putString("Name",line.name);
            row.putString("Description", line.description);
            row.putString("Color",line.color.getName());
            row.putBoolean("StopsFull",line.stops.size() >= PassengerTimetable.MAX_STOPS);
            row.putBoolean("Assigned",line.train!=null);
            row.putString("Status",line.train==null?"draft":PassengerService.running(actor.getServer(),line.id,line.train)?"verified":line.status);
            var stops=new ListTag();
            var ids=new ListTag();
            for(var id:line.stops){
                var station=data.platforms.get(id);
                if(station==null)continue;
                stops.add(net.minecraft.nbt.StringTag.valueOf(names.getOrDefault(id,station.stationPos().toShortString())));
                ids.add(net.minecraft.nbt.NbtUtils.createUUID(id));
            }
            var candidate=line.train==null?PassengerService.departureStation(actor,data,line):null;
            var chosen=line.train==null?line.chosenTrain:line.train;
            if (line.chosenTrain!=null) row.putUUID("ChosenTrain",line.chosenTrain);
            var train=chosen!=null?RailwaySavedData.load(actor.getServer()).getTrains().get(chosen)
                    :candidate!=null?candidate.getPresentTrain():null;
            if(train!=null){
                row.putUUID("Train",train.id);
                var name=train.name.getString(96);
                row.putString("TrainName",name);
            }
            row.put("Stops",stops);row.put("StopIds",ids);lines.add(row);
        }
        var result=new CompoundTag();result.putUUID("Nonce",nonce);result.putUUID("Selected",selected);
        result.putString("Status",status);result.put("Lines",lines);result.put("Stations",stations);
        var trains=new ListTag();
        for(var id:RailwayBinder.selected(actor,RailwayBinder.TRAINS)){
            var train=RailwaySavedData.load(actor.getServer()).getTrains().get(id);
            if(train==null || train.invalid || train.derailed || !com.example.examplemod.block.TransportProtection.canModifyTrain(actor,train)) continue;
            var row=new CompoundTag();row.putUUID("Id",id);row.putString("Name",train.name.getString(96));
            trains.add(row);
        }
        result.put("Trains",trains);
        result.putBoolean("CanBindAndColor",true);
        result.putBoolean("CanEditDescription", actor.connection.hasChannel(DescriptionRequest.TYPE));
        result.putBoolean("CanClearAndDelete",true);
        PacketDistributor.sendToPlayer(actor,new Reply(result));
    }
    private PassengerEditorNetwork(){}
}
