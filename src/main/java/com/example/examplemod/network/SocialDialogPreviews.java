package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.screen.SocialScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Preview metadata for the viewer's current page of private dialogs. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public record SocialDialogPreviews(UUID request, List<Entry> entries) implements CustomPacketPayload {
    public record Entry(UUID peer, long messageId, long time, String text) {}

    public static final int MAX_TEXT = 120;
    private static final int MAX_ENTRIES = 16;
    public static final Type<SocialDialogPreviews> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "social_dialog_previews"));

    public SocialDialogPreviews {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many dialog previews");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SocialDialogPreviews> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.request());
                buf.writeVarInt(payload.entries().size());
                for (var entry : payload.entries()) {
                    buf.writeUUID(entry.peer());
                    buf.writeVarLong(entry.messageId());
                    buf.writeLong(entry.time());
                    buf.writeUtf(entry.text(), MAX_TEXT);
                }
            },
            buf -> {
                UUID request = buf.readUUID();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Too many dialog previews");
                var entries = new ArrayList<Entry>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(new Entry(buf.readUUID(), buf.readVarLong(), buf.readLong(), buf.readUtf(MAX_TEXT)));
                }
                return new SocialDialogPreviews(request, entries);
            });

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("social-dialog-previews-1").optional().playToClient(TYPE, STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> SocialScreen.acceptPreviews(payload)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
