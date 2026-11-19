package com.example.examplemod.navigation;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class TripNetwork {
    public enum Action { PLAN, BEGIN, POLL, CANCEL }
    public record Query(Action action, UUID request, UUID world, TripRef start, TripRef goal, TripPlanner.Mode mode, int option)
            implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","trip_request_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC = StreamCodec.of((b,v) -> {
            b.writeEnum(v.action); b.writeUUID(v.request); b.writeUUID(v.world); v.start.write(b); v.goal.write(b);
            b.writeEnum(v.mode); b.writeByte(v.option);
        }, b -> new Query(b.readEnum(Action.class),b.readUUID(),b.readUUID(),TripRef.read(b),TripRef.read(b),
                b.readEnum(TripPlanner.Mode.class),b.readUnsignedByte()));
        public Type<Query> type() { return TYPE; }
        public Query action(Action next,int index) { return new Query(next,request,world,start,goal,mode,index); }
    }
    public record Reply(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","trip_reply_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC = StreamCodec.of((b,v)->b.writeNbt(v.data),
                b->{
                    if(b.readNbt(NbtAccounter.create(96 * 1024)) instanceof CompoundTag data)return new Reply(data);
                    throw new IllegalArgumentException("Invalid trip response");
                });
        public Type<Reply> type() { return TYPE; }
    }
    public static java.util.function.BiConsumer<Connection,Reply> receiver = (c,r) -> {};
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("trip-1").optional();
        registrar.playToServer(Query.TYPE,Query.CODEC,(q,c)->c.enqueueWork(()->{
            if(c.player() instanceof ServerPlayer actor) TripServer.handle(actor,q);
        }));
        registrar.playToClient(Reply.TYPE,Reply.CODEC,(r,c)->c.enqueueWork(()->receiver.accept(c.connection(),r)));
    }
    private TripNetwork() {}
}
