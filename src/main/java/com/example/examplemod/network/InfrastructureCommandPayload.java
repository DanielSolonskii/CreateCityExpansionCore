package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InfrastructureCommandPayload(BlockPos pos,Action action) implements CustomPacketPayload {
    public enum Action { REQUEST, TOGGLE_GATEWAY }
    public static final Type<InfrastructureCommandPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"infrastructure_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InfrastructureCommandPayload> STREAM_CODEC=StreamCodec.of(
            (buffer,payload)->{BlockPos.STREAM_CODEC.encode(buffer,payload.pos);buffer.writeEnum(payload.action);},
            buffer->new InfrastructureCommandPayload(BlockPos.STREAM_CODEC.decode(buffer),buffer.readEnum(Action.class)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
