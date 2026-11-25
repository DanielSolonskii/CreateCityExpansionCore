package com.example.examplemod.passenger;

import com.example.examplemod.city.CityPublicAtlas;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.map.CityMapFeatures;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.*;

@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class PassengerRailNetwork {
    private PassengerRailNetwork() {}
    public static final int PAGE_SIZE=512;

    public record Request(UUID nonce,UUID view,UUID world,ResourceLocation dimension,long revision,int offset) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","passenger_map_request_v3"));
        static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,r)->{
            b.writeUUID(r.nonce); b.writeUUID(r.view); b.writeUUID(r.world); b.writeResourceLocation(r.dimension);
            b.writeVarLong(r.revision); b.writeVarInt(r.offset);
        },b->new Request(b.readUUID(),b.readUUID(),b.readUUID(),b.readResourceLocation(),b.readVarLong(),b.readVarInt()));
        public Request { if (offset < -1 || revision<0) throw new IllegalArgumentException("invalid page"); }
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Reply(UUID nonce,long revision,int offset,boolean last,boolean pending,List<PassengerRailMap.LineInfo> lines,
                        List<PassengerRailMap.Segment> segments) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","passenger_map_reply_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of(Reply::write,Reply::read);
        public Reply {
            lines=List.copyOf(lines); segments=List.copyOf(segments);
            if (offset< -1 || revision<0 || lines.size()>PassengerData.MAX_LINES || segments.size()>PAGE_SIZE
                    || offset==-1 && (!segments.isEmpty() || !last)) throw new IllegalArgumentException("invalid page");
        }
        private static void write(RegistryFriendlyByteBuf b,Reply page) {
            b.writeUUID(page.nonce); b.writeVarLong(page.revision); b.writeVarInt(page.offset);
            b.writeBoolean(page.last); b.writeBoolean(page.pending); b.writeVarInt(page.lines.size());
            for (var l:page.lines) {
                b.writeUUID(l.id()); b.writeUtf(l.name(),64); b.writeUtf(l.description(),PassengerService.MAX_DESCRIPTION);
                b.writeInt(l.color()); b.writeVarInt(l.stops().size());
                for (var stop:l.stops()) { b.writeUUID(stop.id()); b.writeUtf(stop.name(),96); b.writeBlockPos(stop.entrance()); }
                b.writeUUID(l.trainId()); b.writeUtf(l.trainName(),96);
                b.writeNullable(l.train(),(buf,p)->{buf.writeDouble(p.x());buf.writeDouble(p.y());buf.writeDouble(p.z());});
                b.writeUtf(l.status(),64); b.writeVarInt(l.unavailableLegs());
            }
            b.writeVarInt(page.segments.size());
            for (var s:page.segments) {
                b.writeUUID(s.line()); b.writeInt(s.color()); b.writeDouble(s.x()); b.writeDouble(s.z()); b.writeDouble(s.toX()); b.writeDouble(s.toZ());
            }
        }
        private static int count(RegistryFriendlyByteBuf b,int max) {
            int size=b.readVarInt(); if (size<0 || size>max) throw new IllegalArgumentException("invalid page");
            return size;
        }
        private static Reply read(RegistryFriendlyByteBuf b) {
            var nonce=b.readUUID(); long revision=b.readVarLong(); int offset=b.readVarInt();
            boolean last=b.readBoolean(), pending=b.readBoolean();
            int count=count(b,PassengerData.MAX_LINES);
            var lines=new ArrayList<PassengerRailMap.LineInfo>();
            for (int i=0;i<count;i++) {
                var id=b.readUUID(); var name=b.readUtf(64); var description=b.readUtf(PassengerService.MAX_DESCRIPTION); int color=b.readInt();
                int n=count(b,PassengerTimetable.MAX_STOPS);
                var stops=new ArrayList<PassengerRailMap.StopInfo>();
                for (int j=0;j<n;j++) stops.add(new PassengerRailMap.StopInfo(b.readUUID(),b.readUtf(96),b.readBlockPos()));
                var train=b.readUUID(); var trainName=b.readUtf(96);
                var location=b.readNullable(buf->new PassengerRailMap.TrainLocation(buf.readDouble(),buf.readDouble(),buf.readDouble()));
                lines.add(new PassengerRailMap.LineInfo(id,name,description,color,stops,train,trainName,location,b.readUtf(64),count(b,n)));
            }
            count=count(b,PAGE_SIZE);
            var segments=new ArrayList<PassengerRailMap.Segment>();
            for (int i=0;i<count;i++) segments.add(new PassengerRailMap.Segment(b.readUUID(),b.readInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));
            return new Reply(nonce,revision,offset,last,pending,lines,segments);
        }
        @Override public Type<Reply> type() { return TYPE; }
    }

    public static Reply page(Request request,PassengerRailMap.Snapshot snapshot) {
        int offset=snapshot.revision()==request.revision()?request.offset():0;
        int size=snapshot.segments().size();
        if (offset>size) offset=0;
        int end=offset<0?0:offset+Math.min(size-offset,PAGE_SIZE);
        return new Reply(request.nonce(),snapshot.revision(),offset,offset<0 || end==size,snapshot.pending(),snapshot.lines(),
                offset<0?List.of():snapshot.segments().subList(offset,end));
    }

    private static final Map<ServerPlayer,Integer> lastTicks=new WeakHashMap<>();
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registry=event.registrar("passenger-map-3").optional();
        registry.playToServer(Request.TYPE,Request.CODEC,(packet,ctx)->ctx.enqueueWork(()->{
            if (!(ctx.player() instanceof ServerPlayer actor) || actor instanceof FakePlayer || !actor.connection.hasChannel(Reply.TYPE)) return;
            var view=CityPublicAtlas.view(actor,packet.view());
            if (view==null || !view.worldId().equals(packet.world()) || !view.dimension().equals(packet.dimension())) return;
            int now=actor.getServer().getTickCount();
            if (Objects.equals(lastTicks.put(actor,now),now)) return;
            PacketDistributor.sendToPlayer(actor,page(packet,PassengerRailMap.forAtlas(actor,packet.dimension())));
        }));
        registry.playToClient(Reply.TYPE,Reply.CODEC,(packet,ctx)->ctx.enqueueWork(()->{
            if (CityPublicDirectory.currentConnection(ctx.connection())) CityMapFeatures.receiveRail(packet);
        }));
    }
}
