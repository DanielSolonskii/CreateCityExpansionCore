package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record DecommissionCityProjectPayload(BlockPos pylonPos, UUID projectId)
        implements CustomPacketPayload {
    public static final Type<DecommissionCityProjectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "decommission_city_project")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DecommissionCityProjectPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
                        buffer.writeUUID(payload.projectId());
                    },
                    buffer -> new DecommissionCityProjectPayload(
                            BlockPos.STREAM_CODEC.decode(buffer), buffer.readUUID()
                    )
            );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
