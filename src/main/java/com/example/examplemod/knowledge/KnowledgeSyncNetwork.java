package com.example.examplemod.knowledge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class KnowledgeSyncNetwork {
    public static final UUID NONE=new UUID(0,0);
    public static final int BEGIN=0, MANIFEST=1, FILE=2, ERROR=3;
    public static final int HELLO=0, GET=1, FINISH=2, CANCEL=3;
    public record Data(UUID session,int kind,String name,int offset,int total,byte[] bytes) implements CustomPacketPayload {
        public static final Type<Data> TYPE=new Type<>(ResourceLocation.parse("createcityexpansioncore:knowledge_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Data> CODEC=StreamCodec.of(
                (b,p)->{b.writeUUID(p.session);b.writeByte(p.kind);b.writeUtf(p.name,256);b.writeVarInt(p.offset);b.writeVarInt(p.total);b.writeByteArray(p.bytes);},
                b->new Data(b.readUUID(),b.readUnsignedByte(),b.readUtf(256),b.readVarInt(),b.readVarInt(),b.readByteArray(KnowledgeBundle.CHUNK)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Request(UUID session,int kind,String hash) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.parse("createcityexpansioncore:knowledge_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of(
                (b,p)->{b.writeUUID(p.session);b.writeByte(p.kind);b.writeUtf(p.hash,64);},
                b->new Request(b.readUUID(),b.readUnsignedByte(),b.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("1");
        r.playToClient(Data.TYPE,Data.CODEC,(p,c)->c.enqueueWork(()->KnowledgeSyncClient.accept(p)));
        r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->{
            if(c.player() instanceof ServerPlayer player)KnowledgeSyncServer.request(player,p);
        }));
    }
}
