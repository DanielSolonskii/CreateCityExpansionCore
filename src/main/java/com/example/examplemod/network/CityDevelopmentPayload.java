package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CityDevelopmentPayload(BlockPos pylonPos, Action action)
        implements CustomPacketPayload {
    public enum Action { CONTRIBUTE, COMPLETE_LEVEL_TWO }
    public static final Type<CityDevelopmentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "city_development")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CityDevelopmentPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
                        buffer.writeEnum(payload.action());
                    },
                    buffer -> new CityDevelopmentPayload(
                            BlockPos.STREAM_CODEC.decode(buffer), buffer.readEnum(Action.class)
                    )
            );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
