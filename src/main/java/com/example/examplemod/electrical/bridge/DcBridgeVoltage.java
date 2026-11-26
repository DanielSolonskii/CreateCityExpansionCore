package com.example.examplemod.electrical.bridge;

import com.example.examplemod.city.CityProtectionManager;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;


final class DcBridgeVoltage extends ScrollValueBehaviour {
    private static final String LANG = "gui.createcityexpansioncore.powerbridge.";
    private final DcBridgeEntity bridge;

    DcBridgeVoltage(DcBridgeEntity bridge) {
        super(Component.translatable(LANG + "voltage"), bridge, new VoltageBox());
        this.bridge = bridge;
        onlyActiveWhen(PowerBridge::tfmgSupported);
    }

    @Override
    public boolean acceptsValueSettings() { return false; }

    @Override
    public void onShortInteract(Player player, net.minecraft.world.InteractionHand hand,
                                Direction side, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && canConfigure(bridge, player)) {
            DcVoltageNetwork.open(serverPlayer, bridge);
        }
    }

    @Override
    public String formatValue() { return bridge.volts() + " V"; }
    static boolean canConfigure(DcBridgeEntity bridge, Player player) {
        var level = player.level();
        var pos = bridge.getBlockPos();
        return !player.isSpectator() && level == bridge.getLevel() && !bridge.isRemoved()
                && level.isLoaded(pos) && level.getBlockEntity(pos) == bridge
                && player.canInteractWithBlock(pos, 0) && level.mayInteract(player, pos)
                && (!(player instanceof ServerPlayer serverPlayer)
                    || CityProtectionManager.canInteract(serverPlayer.serverLevel(), serverPlayer, pos));
    }

    @Override
    public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        tag.putInt("Voltage", bridge.selectedVoltage());
        return true;
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player,
                                     Direction side, boolean simulate) {
        if (!tag.contains("Voltage", net.minecraft.nbt.Tag.TAG_INT) || !canConfigure(bridge, player)) return false;
        int voltage = tag.getInt("Voltage");
        if (voltage < 0 || voltage > 20_000) return false;
        if (!simulate) bridge.setSelectedVoltage(voltage);
        return true;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {}

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {}

    private static final class VoltageBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(.5, .19, 1);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
            return state.getValue(DcBridgeBlock.FACING) == side;
        }
    }
}
