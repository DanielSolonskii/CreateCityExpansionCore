package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.economy.EconomyTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record InfrastructureStatePayload(BlockPos pos, UUID registrationId,
                                         EconomyTypes.InfrastructureType infrastructureType,
                                         EconomyTypes.GatewayMode mode, String cityName, String stationName,
                                         EconomyTypes.IntakeAttributionError lastError) implements CustomPacketPayload {
    public static final Type<InfrastructureStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"infrastructure_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InfrastructureStatePayload> STREAM_CODEC=StreamCodec.of(InfrastructureStatePayload::encode,InfrastructureStatePayload::decode);
    private static void encode(RegistryFriendlyByteBuf b,InfrastructureStatePayload p){BlockPos.STREAM_CODEC.encode(b,p.pos);b.writeUUID(p.registrationId);b.writeEnum(p.infrastructureType);b.writeEnum(p.mode);b.writeUtf(p.cityName,96);b.writeUtf(p.stationName,128);b.writeEnum(p.lastError);}
    private static InfrastructureStatePayload decode(RegistryFriendlyByteBuf b){return new InfrastructureStatePayload(BlockPos.STREAM_CODEC.decode(b),b.readUUID(),b.readEnum(EconomyTypes.InfrastructureType.class),b.readEnum(EconomyTypes.GatewayMode.class),b.readUtf(96),b.readUtf(128),b.readEnum(EconomyTypes.IntakeAttributionError.class));}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
