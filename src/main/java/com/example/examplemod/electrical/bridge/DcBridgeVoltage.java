package com.example.examplemod.electrical.bridge;

import com.example.examplemod.city.CityProtectionManager;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class DcBridgeVoltage extends ScrollValueBehaviour {
    private static final String LANG = "gui.createcityexpansioncore.powerbridge.";
    private static final int BOARD_MAX = 99;
    private static final int MAX_SELECTION = 10_999;
    private final DcBridgeEntity bridge;

    DcBridgeVoltage(DcBridgeEntity bridge) {
        super(Component.translatable(LANG + "voltage"), bridge, new VoltageBox());
        this.bridge = bridge;
        onlyActiveWhen(PowerBridge::tfmgSupported);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
        int volts = Math.min(bridge.selectedVoltage(), MAX_SELECTION);
        var title = new VoltageTotal(bridge.selectedVoltage());
        return new ValueSettingsBoard(title, BOARD_MAX, 99, List.of(
                Component.translatable(LANG + "voltage_units", volts % 100),
                Component.translatable(LANG + "voltage_hundreds", volts / 100 % 10 * 100),
                Component.translatable(LANG + "voltage_thousands", volts / 1000)),
                new ValueSettingsFormatter(setting -> {
                    title.update(withRow(volts, setting));
                    int contribution = rowVolts(setting);
                    return setting.row() == 2
                            ? Component.translatable(LANG + "voltage_value_kv", contribution / 1000)
                            : Component.translatable(LANG + "voltage_value", contribution);
                }));
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, Math.min(bridge.selectedVoltage(), MAX_SELECTION) % 100);
    }

    @Override
    public String formatValue() {
        return bridge.volts() + " V";
    }

    @Override
    public void setValueSettings(Player player, ValueSettings setting, boolean ctrlDown) {
        if (setting.row() < 0 || setting.row() > 2 || setting.value() < 0 || setting.value() > BOARD_MAX
                || !canConfigure(player)) return;
        int previous = bridge.selectedVoltage();
        int selection = withRow(Math.min(previous, MAX_SELECTION), setting);
        if (previous != selection) {
            bridge.setSelectedVoltage(selection);
            playFeedbackSound(this);
        }
    }

    private static int withRow(int volts, ValueSettings setting) {
        int contribution = rowVolts(setting);
        return switch (setting.row()) {
            case 0 -> volts - volts % 100 + contribution;
            case 1 -> volts - volts / 100 % 10 * 100 + contribution;
            case 2 -> volts % 1000 + contribution;
            default -> volts;
        };
    }

    private static int rowVolts(ValueSettings setting) {
        // Create shares one horizontal scale across rows. Map it to each row's number of steps.
        int position = setting.value();
        return switch (setting.row()) {
            case 0 -> position;
            case 1 -> Math.round(position * 9f / BOARD_MAX) * 100;
            case 2 -> Math.round(position * 10f / BOARD_MAX) * 1000;
            default -> 0;
        };
    }

    private boolean canConfigure(Player player) {
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
        if (!tag.contains("Voltage", net.minecraft.nbt.Tag.TAG_INT) || !canConfigure(player)) return false;
        int voltage = tag.getInt("Voltage");
        if (voltage < 0 || voltage > 20_000) return false;
        if (!simulate) bridge.setSelectedVoltage(voltage);
        return true;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {}

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {}

    private static final class VoltageTotal implements Component {
        private int volts = -1;
        private Component text;

        VoltageTotal(int volts) {
            update(volts);
        }

        void update(int value) {
            if (volts == value) return;
            volts = value;
            text = Component.translatable(LANG + "voltage_total", volts);
        }

        @Override
        public ComponentContents getContents() { return text.getContents(); }

        @Override
        public Style getStyle() { return text.getStyle(); }

        @Override
        public List<Component> getSiblings() { return text.getSiblings(); }

        @Override
        public FormattedCharSequence getVisualOrderText() { return text.getVisualOrderText(); }
    }

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
