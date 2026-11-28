package com.example.examplemod.network;

import com.example.examplemod.city.CityGazette;
import com.example.examplemod.client.screen.CityPylonWorkspace;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="createcityexpansioncore", bus=EventBusSubscriber.Bus.MOD)
public record GazetteEditionPayload(int container, List<CityGazette.Story> stories) implements CustomPacketPayload {
    public static final Type<GazetteEditionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","gazette_edition"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GazetteEditionPayload> CODEC = StreamCodec.of((buf,p) -> {
        buf.writeVarInt(p.container);
        buf.writeCollection(p.stories,(b,s) -> {
            b.writeEnum(s.kind()); b.writeUtf(s.subject(),128); b.writeUtf(s.place(),128); b.writeLong(s.time());
        });
    }, buf -> new GazetteEditionPayload(buf.readVarInt(),buf.readList(b -> new CityGazette.Story(
            b.readEnum(CityGazette.Kind.class),b.readUtf(128),b.readUtf(128),b.readLong()))));
    public GazetteEditionPayload {
        stories = List.copyOf(stories);
        if (stories.size() > CityGazette.EDITION) throw new IllegalArgumentException("edition");
    }
    @Override public Type<GazetteEditionPayload> type() { return TYPE; }
    public record V2(GazetteEditionPayload edition) implements CustomPacketPayload {
        public static final Type<V2> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","gazette_edition_v2"));
        @Override public Type<V2> type() { return TYPE; }
    }
    public record V3(GazetteEditionPayload edition) implements CustomPacketPayload {
        public static final Type<V3> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","gazette_edition_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf,V3> CODEC = StreamCodec.of((buf,p) -> {
            GazetteEditionPayload.CODEC.encode(buf,p.edition);
            for (var s : p.edition.stories) buf.writeUtf(s.detail(),128);
        },buf -> {
            var base = GazetteEditionPayload.CODEC.decode(buf);
            var rows = base.stories.stream().map(s -> new CityGazette.Story(s.kind(),s.subject(),s.place(),buf.readUtf(128),s.time())).toList();
            return new V3(new GazetteEditionPayload(base.container,rows));
        });
        @Override public Type<V3> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(V3.TYPE,V3.CODEC,(packet,ctx) -> ctx.enqueueWork(() -> {
            if (com.example.examplemod.client.CityPublicDirectory.currentConnection(ctx.connection()))
                CityPylonWorkspace.receiveEdition(packet.edition());
        }));
        event.registrar("1").optional().playToClient(V2.TYPE,CODEC.map(V2::new,V2::edition),(packet,ctx) -> ctx.enqueueWork(() -> {
            if (com.example.examplemod.client.CityPublicDirectory.currentConnection(ctx.connection()))
                CityPylonWorkspace.receiveEdition(packet.edition());
        }));
        event.registrar("1").optional().playToClient(TYPE,CODEC,(packet,ctx) -> ctx.enqueueWork(() -> {
            if (com.example.examplemod.client.CityPublicDirectory.currentConnection(ctx.connection()))
                CityPylonWorkspace.receiveEdition(packet);
        }));
    }
}
