package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestCityMapTilePayload(
        BlockPos pylonPos,
        ResourceLocation dimensionId,
        int tileX,
        int tileZ
) implements CustomPacketPayload {

    public static final Type<RequestCityMapTilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "request_city_map_tile"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCityMapTilePayload>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
                        buffer.writeResourceLocation(payload.dimensionId());
                        buffer.writeInt(payload.tileX());
                        buffer.writeInt(payload.tileZ());
                    },
                    buffer -> new RequestCityMapTilePayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            buffer.readResourceLocation(),
                            buffer.readInt(),
                            buffer.readInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
