package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CityRelationsStatePayload(BlockPos pylonPos, UUID cityId, boolean canManage,
                                        List<CityRelationSnapshot> cities) implements CustomPacketPayload {
    public static final Type<CityRelationsStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CreateCityExpansionCore.MODID, "city_relations_state_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityRelationsStatePayload> STREAM_CODEC =
            StreamCodec.of(CityRelationsStatePayload::encode, CityRelationsStatePayload::decode);
    public CityRelationsStatePayload { cities = List.copyOf(cities); }
    private static void encode(RegistryFriendlyByteBuf buffer, CityRelationsStatePayload value) {
        BlockPos.STREAM_CODEC.encode(buffer, value.pylonPos); buffer.writeUUID(value.cityId);
        buffer.writeBoolean(value.canManage); buffer.writeVarInt(value.cities.size());
        for (CityRelationSnapshot city : value.cities) {
            buffer.writeUUID(city.cityId()); buffer.writeUtf(city.cityName(), 128); buffer.writeEnum(city.status()); buffer.writeBoolean(city.territoryAccess()); buffer.writeBoolean(city.incomingTerritoryAccess());
        }
    }
    private static CityRelationsStatePayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer); UUID city = buffer.readUUID(); boolean manage = buffer.readBoolean();
        int count = Math.min(4096, Math.max(0, buffer.readVarInt())); List<CityRelationSnapshot> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new CityRelationSnapshot(buffer.readUUID(), buffer.readUtf(128),
                buffer.readEnum(CityRelationSnapshot.Status.class),buffer.readBoolean(),buffer.readBoolean()));
        return new CityRelationsStatePayload(pos, city, manage, result);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
