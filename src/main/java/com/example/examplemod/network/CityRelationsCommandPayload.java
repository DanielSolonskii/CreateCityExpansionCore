package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CityRelationsCommandPayload(BlockPos pylonPos, Action action, UUID targetCityId)
        implements CustomPacketPayload {
    public enum Action { REQUEST_STATE, ADD_TRUST, REMOVE_TRUST }
    public static final Type<CityRelationsCommandPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CreateCityExpansionCore.MODID, "city_relations_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityRelationsCommandPayload> STREAM_CODEC =
            StreamCodec.of(CityRelationsCommandPayload::encode, CityRelationsCommandPayload::decode);
    private static void encode(RegistryFriendlyByteBuf buffer, CityRelationsCommandPayload value) {
        BlockPos.STREAM_CODEC.encode(buffer, value.pylonPos);
        buffer.writeEnum(value.action);
        buffer.writeBoolean(value.targetCityId != null);
        if (value.targetCityId != null) buffer.writeUUID(value.targetCityId);
    }
    private static CityRelationsCommandPayload decode(RegistryFriendlyByteBuf buffer) {
        return new CityRelationsCommandPayload(BlockPos.STREAM_CODEC.decode(buffer), buffer.readEnum(Action.class),
                buffer.readBoolean() ? buffer.readUUID() : null);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
