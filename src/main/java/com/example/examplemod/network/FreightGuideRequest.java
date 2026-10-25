package com.example.examplemod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** A bounded command, never a client-authored infrastructure observation. */
public record FreightGuideRequest(Action action, UUID session, UUID point, BlockPos pos,
                                  int mode, String name) implements CustomPacketPayload {
    public enum Action { BEGIN, PICK, REFRESH, FINISH, CANCEL, BEGIN_REBIND }
    public FreightGuideRequest {
        if (action == null || pos == null || name == null || name.length() > 64 || mode < 0 || mode > 2)
            throw new IllegalArgumentException("Freight guide packet bounds");
    }
    public static final Type<FreightGuideRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "freight_guide_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreightGuideRequest> STREAM_CODEC = StreamCodec.of((b,p)->{
        b.writeEnum(p.action); optional(b,p.session); optional(b,p.point); b.writeBlockPos(p.pos); b.writeVarInt(p.mode); b.writeUtf(p.name,64);
    }, b->new FreightGuideRequest(b.readEnum(Action.class),optional(b),optional(b),b.readBlockPos(),b.readVarInt(),b.readUtf(64)));
    private static void optional(RegistryFriendlyByteBuf b, UUID id) { b.writeBoolean(id != null); if(id != null)b.writeUUID(id); }
    private static UUID optional(RegistryFriendlyByteBuf b) { return b.readBoolean()?b.readUUID():null; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
