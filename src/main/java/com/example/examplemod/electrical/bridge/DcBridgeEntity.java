package com.example.examplemod.electrical.bridge;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.example.examplemod.machine.GoggleTooltip;
import net.minecraft.ChatFormatting;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

public class DcBridgeEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String LANG = "gui.createcityexpansioncore.powerbridge.";
    private static BlockEntityType.BlockEntitySupplier<DcBridgeEntity> factory = DcBridgeEntity::new;
    private boolean eeToTfmg = true;
    private int redstoneMode;
    private long restartAt;
    private double joules;
    private double inputVolts;
    private double inputAmps;
    private double outputVolts;
    private double outputAmps;
    private double inputWatts;
    private double outputWatts;
    private boolean acDetected;
    private boolean enabled;
    private int syncIn;
    private int selectedVoltage;
    private double eeDemandWatts = 2;
    private int shortTicks;
    private long trippedUntil;

    public boolean isTfmgSection() { return getBlockState().getValue(DcBridgeBlock.TFMG); }

    public DcBridgeEntity controller() {
        if (isTfmgSection() && level != null) {
            var pos = DcBridgeBlock.partner(worldPosition, getBlockState());
            if (level.hasChunkAt(pos) && DcBridgeBlock.matches(getBlockState(), level.getBlockState(pos))
                    && level.getBlockEntity(pos) instanceof DcBridgeEntity root) return root;
        }
        return this;
    }

    public boolean assembled() {
        var pos = DcBridgeBlock.partner(worldPosition, getBlockState());
        return level != null && level.hasChunkAt(pos) && DcBridgeBlock.matches(getBlockState(), level.getBlockState(pos));
    }

    static DcBridgeEntity create(BlockPos pos, BlockState state) {
        return factory.create(pos, state);
    }

    static void useNativeFactory(BlockEntityType.BlockEntitySupplier<DcBridgeEntity> nativeFactory) {
        factory = nativeFactory;
    }

    DcBridgeEntity(BlockPos pos, BlockState state) {
        super(PowerBridge.ENTITY.get(), pos, state);
        selectedVoltage = 230;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        if (!isTfmgSection()) behaviours.add(new DcBridgeVoltage(this));
    }

    @Override
    public void tick() {
        super.tick();
        var partner = DcBridgeBlock.partner(worldPosition, getBlockState());
        // Finish dismantling after a chunk boundary reload, without force-loading chunks.
        if (level.hasChunkAt(partner) && !assembled()) {
            level.destroyBlock(worldPosition, !isTfmgSection());
            return;
        }
        if (isTfmgSection()) return;
        boolean signal = level.hasNeighborSignal(worldPosition) || (assembled() && level.hasNeighborSignal(partner));
        boolean active = this instanceof ActivePort && assembled() && level.getGameTime() >= restartAt
                && (redstoneMode == 0 || signal == (redstoneMode == 1));
        if (enabled != active) {
            enabled = active;
            if (!active) clearEnergy();
            notifyUpdate();
        }
        joules = Math.min(joules, capacityJoules());
        if (++syncIn >= 10) {
            syncIn = 0;
            sendData();
        }
    }

    void reverse() {
        eeToTfmg = !eeToTfmg;
        interrupt();
    }

    void cycleRedstone() {
        redstoneMode = (redstoneMode + 1) % 3;
        interrupt();
    }

    private void interrupt() {
        if (level == null || level.isClientSide) return;
        restartAt = level.getGameTime() + 2;
        enabled = false;
        clearEnergy();
        notifyUpdate();
    }

    private void clearEnergy() {
        joules = inputVolts = inputAmps = inputWatts = outputVolts = outputAmps = outputWatts = 0;
        acDetected = false;
        eeDemandWatts = 2;
        shortTicks = 0;
    }

    public int redstoneMode() { return redstoneMode; }
    public boolean eeToTfmg() { return eeToTfmg; }
    boolean enabled() { return enabled; }
    boolean acDetected() { return acDetected; }
    double inputVoltage() { return inputVolts; }
    double outputWatts() { return outputWatts; }
    boolean tripped() { return level != null && level.getGameTime() < trippedUntil; }
    double eeDemandWatts() { return eeDemandWatts; }
    public int volts() { return PowerBridge.outputVoltage(selectedVoltage); }

    int selectedVoltage() { return selectedVoltage; }

    void setSelectedVoltage(int value) {
        if (selectedVoltage == value) return;
        selectedVoltage = value;
        interrupt();
    }

    double outputLimitWatts(int volts) {
        return Math.min(PowerBridge.MAX_WATTS.get(), volts * PowerBridge.MAX_AMPS.get());
    }

    double availableWatts() {
        return Math.min(joules * 20, outputLimitWatts(volts()));
    }

    double requestedInput(double desiredOutputWatts) {
        double demand = Math.min(outputLimitWatts(volts()), desiredOutputWatts);
        double wantedJoules = demand / 10;
        double refill = Math.min(demand, Math.max(0, wantedJoules - joules) * 20);
        return Math.min(PowerBridge.MAX_WATTS.get(), refill / PowerBridge.EFFICIENCY.get());
    }

    private double capacityJoules() {
        // EE and TFMG settle at different phases of the game tick.
        return outputLimitWatts(volts()) / 10;
    }

    void recordInput(double u, double i, double watts) {
        inputVolts = u;
        inputAmps = i;
        inputWatts = watts;
        joules = Math.min(capacityJoules(), joules + watts * PowerBridge.EFFICIENCY.get() / 20);
    }

    void recordOutput(double u, double i, double watts) {
        outputVolts = u;
        outputAmps = i;
        outputWatts = watts;
        joules = Math.max(0, joules - watts / 20);
    }

    void setAcDetected(boolean detected) {
        acDetected = detected;
    }

    void observeEeLoad(double source, double terminalVolts, double amps) {
        if (source <= 0) return;
        double load = terminalVolts > 1e-6 ? amps / terminalVolts : amps > 1e-6 ? 100 : 0;
        eeDemandWatts = Math.max(2, load * volts() * volts());
        observeShort(load > 50 && amps > 1e-6);
    }

    void observeShort(boolean shorted) {
        shortTicks = shorted ? shortTicks + 1 : 0;
        if (shortTicks >= 10) {
            clearEnergy();
            trippedUntil = level.getGameTime() + 40;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("EeToTfmg", eeToTfmg);
        tag.putInt("RedstoneMode", redstoneMode);
        tag.putInt("ScrollValue", selectedVoltage);
        if (clientPacket) {
            tag.putBoolean("Enabled", enabled);
            tag.putBoolean("AC", acDetected);
            tag.putLong("TrippedUntil", trippedUntil);
            tag.putDouble("InputVolts", inputVolts);
            tag.putDouble("InputAmps", inputAmps);
            tag.putDouble("InputWatts", inputWatts);
            tag.putDouble("OutputVolts", outputVolts);
            tag.putDouble("OutputAmps", outputAmps);
            tag.putDouble("OutputWatts", outputWatts);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        eeToTfmg = !tag.contains("EeToTfmg") || tag.getBoolean("EeToTfmg");
        redstoneMode = Math.clamp(tag.getInt("RedstoneMode"), 0, 2);
        selectedVoltage = tag.contains("ScrollValue") ? Math.clamp(tag.getInt("ScrollValue"), 0, 20_000) : 230;
        if (clientPacket) {
            enabled = tag.getBoolean("Enabled");
            acDetected = tag.getBoolean("AC");
            trippedUntil = tag.getLong("TrippedUntil");
            inputVolts = tag.getDouble("InputVolts");
            inputAmps = tag.getDouble("InputAmps");
            inputWatts = tag.getDouble("InputWatts");
            outputVolts = tag.getDouble("OutputVolts");
            outputAmps = tag.getDouble("OutputAmps");
            outputWatts = tag.getDouble("OutputWatts");
        }
    }

    public String directionLabel() { return eeToTfmg ? "EE > TFMG" : "TFMG > EE"; }

    public Component status() {
        String key = !PowerBridge.tfmgSupported() ? "missing" : !enabled ? "off"
                : tripped() ? "tripped" : acDetected ? "ac" : outputWatts > .01 ? "running"
                : inputVolts < 1 ? "no_input" : "idle";
        return Component.translatable(LANG + key);
    }

    public String readings(boolean input) {
        return String.format(Locale.ROOT, "%.0fV %.1fA %.0fW", input ? inputVolts : outputVolts,
                input ? inputAmps : outputAmps, input ? inputWatts : outputWatts);
    }

    public double readingVolts(boolean input) { return input ? inputVolts : outputVolts; }
    public double readingAmps(boolean input) { return input ? inputAmps : outputAmps; }
    public double readingWatts(boolean input) { return input ? inputWatts : outputWatts; }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        if (controller() != this) return controller().addToGoggleTooltip(tooltip, sneaking);
        GoggleTooltip.add(tooltip, getBlockState().getBlock().getName().withStyle(ChatFormatting.GOLD), 0);
        GoggleTooltip.add(tooltip, Component.literal(directionLabel()).withStyle(ChatFormatting.GRAY), 1);
        ChatFormatting statusColor = tripped() || acDetected ? ChatFormatting.RED : ChatFormatting.GRAY;
        GoggleTooltip.add(tooltip, status().copy().withStyle(statusColor), 1);
        addElectricalReading(tooltip, "input", inputVolts, inputAmps, inputWatts);
        addElectricalReading(tooltip, "output", outputVolts, outputAmps, outputWatts);
        return true;
    }

    private static void addElectricalReading(List<Component> tooltip, String side, double volts, double amps, double watts) {
        var values = Component.translatable(LANG + "goggles_reading",
                String.format(Locale.ROOT, "%.0f", volts),
                String.format(Locale.ROOT, "%.1f", amps),
                String.format(Locale.ROOT, "%.0f", watts)).withStyle(ChatFormatting.AQUA);
        GoggleTooltip.add(tooltip, Component.translatable(LANG + side, values).withStyle(ChatFormatting.GRAY), 1);
    }

    interface ActivePort {
        double tfmgDemandWatts();
    }

}
