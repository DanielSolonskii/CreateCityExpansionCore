package com.example.examplemod.places;

import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.screen.AtlasPlaces;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class PlaceNetwork {
    public enum Action { LIST, SAVE, DELETE }
    public record Request(UUID nonce, UUID view, UUID world, ResourceLocation dimension, Action action,
                          UUID id, long expected, AtlasPlace value, AtlasPlace.Source probe) implements CustomPacketPayload {
        public Request {
            if(expected<0 || (action==Action.SAVE)!=(value!=null) || value!=null &&
                    (!id.equals(value.id()) || !dimension.equals(value.dimension()) || expected!=value.version()))
                throw new IllegalArgumentException("Place request");
        }
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","place_request_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,r)->{
            b.writeUUID(r.nonce);b.writeUUID(r.view);b.writeUUID(r.world);b.writeUtf(r.dimension.toString(),160);b.writeEnum(r.action);
            b.writeUUID(r.id);b.writeVarLong(r.expected);if(r.value!=null)AtlasPlace.write(b,r.value);
            b.writeBoolean(r.probe!=null);if(r.probe!=null){b.writeEnum(r.probe.kind());b.writeUUID(r.probe.id());}
        },b->{
            var nonce=b.readUUID();var view=b.readUUID();var world=b.readUUID();var dim=ResourceLocation.parse(b.readUtf(160));
            var action=b.readEnum(Action.class);var id=b.readUUID();var version=b.readVarLong();
            return new Request(nonce,view,world,dim,action,id,version,action==Action.SAVE?AtlasPlace.read(b):null,
                    b.readBoolean()?new AtlasPlace.Source(b.readEnum(AtlasPlace.SourceKind.class),b.readUUID()):null);
        });
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Reply(UUID nonce,String status,List<AtlasPlace> rows,boolean canLink) implements CustomPacketPayload {
        public Reply {rows=List.copyOf(rows);if(rows.size()>PlaceBook.MAX)throw new IllegalArgumentException("Place count");}
        public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","place_reply_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,r)->{
            b.writeUUID(r.nonce);b.writeUtf(r.status,24);b.writeVarInt(r.rows.size());r.rows.forEach(p->AtlasPlace.write(b,p));b.writeBoolean(r.canLink);
        },b->{
            var nonce=b.readUUID();var status=b.readUtf(24);int count=b.readVarInt();
            if(count<0||count>PlaceBook.MAX)throw new IllegalArgumentException("Place count");
            List<AtlasPlace> rows=new ArrayList<>(count);for(int i=0;i<count;i++)rows.add(AtlasPlace.read(b));
            return new Reply(nonce,status,rows,b.readBoolean());
        });
        @Override public Type<Reply> type(){return TYPE;}
    }
    /** Negotiated separately because older clients decode place categories by enum ordinal. */
    public record CategorizedReply(Reply value) implements CustomPacketPayload {
        public static final Type<CategorizedReply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                "createcityexpansioncore", "place_reply_v2"));
        static final StreamCodec<RegistryFriendlyByteBuf,CategorizedReply> CODEC =
                Reply.CODEC.map(CategorizedReply::new, CategorizedReply::value);
        @Override public Type<CategorizedReply> type() { return TYPE; }
    }

    private static Reply legacyCategories(Reply reply) {
        if (reply.rows().stream().noneMatch(p -> p.category() == AtlasPlace.Category.STATION)) return reply;
        var rows = reply.rows().stream().map(p -> p.category() == AtlasPlace.Category.STATION
                ? new AtlasPlace(p.id(), p.owner(), p.version(), p.name(), AtlasPlace.Category.PUBLIC, p.description(),
                        p.dimension(), p.icon(), p.entrances(), p.published(), p.source())
                : p).toList();
        return new Reply(reply.nonce(), reply.status(), rows, reply.canLink());
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("places-1").optional();
        r.playToServer(Request.TYPE,Request.CODEC,(packet,ctx)->ctx.enqueueWork(()->{
            if(ctx.player() instanceof ServerPlayer player) {
                var reply = PlaceService.handle(player,packet);
                PacketDistributor.sendToPlayer(player, player.connection.hasChannel(CategorizedReply.TYPE)
                        ? new CategorizedReply(reply) : legacyCategories(reply));
            }
        }));
        r.playToClient(Reply.TYPE,Reply.CODEC,(packet,ctx)->ctx.enqueueWork(()->{
            if(CityPublicDirectory.currentConnection(ctx.connection()))AtlasPlaces.receive(packet);
        }));
        r.playToClient(CategorizedReply.TYPE,CategorizedReply.CODEC,(packet,ctx)->ctx.enqueueWork(()->{
            if(CityPublicDirectory.currentConnection(ctx.connection()))AtlasPlaces.receive(packet.value());
        }));
    }
    private PlaceNetwork(){}
}
