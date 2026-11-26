package com.example.examplemod.electrical.bridge;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.function.Consumer;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DcVoltageNetwork {
    public static Consumer<Open> openScreen = settings -> {};

    static void open(ServerPlayer player, DcBridgeEntity bridge) {
        int maximum = (int) PowerBridge.MAX_VOLTAGE.get().doubleValue();
        PacketDistributor.sendToPlayer(player,
                new Open(bridge.getBlockPos(), bridge.volts(), PowerBridge.outputVoltage(0), maximum));
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Open.TYPE, Open.CODEC,
                (packet, context) -> context.enqueueWork(() -> openScreen.accept(packet)));
        registrar.playToServer(SetVoltage.TYPE, SetVoltage.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !PowerBridge.tfmgSupported()) return;
            var level = player.serverLevel();
            if (!level.isLoaded(packet.pos()) || !player.canInteractWithBlock(packet.pos(), 0)) return;
            if (level.getBlockEntity(packet.pos()) instanceof DcBridgeEntity bridge
                    && !bridge.isTfmgSection() && DcBridgeVoltage.canConfigure(bridge, player)) {
                bridge.setSelectedVoltage(PowerBridge.outputVoltage(packet.voltage()));
            }
        }));
    }

    public record Open(BlockPos pos, int voltage, int minimum, int maximum) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "open_voltage"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::pos, ByteBufCodecs.VAR_INT, Open::voltage, ByteBufCodecs.VAR_INT, Open::minimum, ByteBufCodecs.VAR_INT, Open::maximum, Open::new);
        @Override public Type<Open> type() { return TYPE; }
    }

    public record SetVoltage(BlockPos pos, int voltage) implements CustomPacketPayload {
        public static final Type<SetVoltage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "set_voltage"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetVoltage> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetVoltage::pos, ByteBufCodecs.VAR_INT, SetVoltage::voltage, SetVoltage::new);
        @Override public Type<SetVoltage> type() { return TYPE; }
    }

    private DcVoltageNetwork() {}
}
