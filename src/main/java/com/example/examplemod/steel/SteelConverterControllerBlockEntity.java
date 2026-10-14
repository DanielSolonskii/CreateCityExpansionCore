package com.example.examplemod.steel;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.electrical.MinePowerPortDevice;
import com.example.examplemod.mine.MinePowerDemand;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SteelConverterControllerBlockEntity extends BlockEntity {
    public static final int INPUT_MB = 8_000;
    public static final int OUTPUT_BILLETS = 8;
    public static final int PROCESS_TICKS = 300;
    public static final double REQUIRED_POWER_WATTS = 80_000;
    public static final double MIN_VOLTAGE = 500;
    public static final double MAX_VOLTAGE = 600;

    private SteelConverterState state = SteelConverterState.STRUCTURE_INVALID;
    private int processTicks;
    private double voltage;
    private double power;
    private SteelConverterMultiblock.ValidationResult validation;
    private long nextValidation;

    public SteelConverterControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.STEEL_CONVERTER_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  SteelConverterControllerBlockEntity blockEntity) {
        blockEntity.tick(level);
    }

    private void tick(ServerLevel level) {
        long time = level.getGameTime();
        if (validation == null || time >= nextValidation) {
            validation = SteelConverterMultiblock.validate(level, worldPosition);
            nextValidation = time + 20;
        }
        if (!validation.assembled()) {
            state = SteelConverterState.STRUCTURE_INVALID;
            publish(level, 200);
            sync(level);
            return;
        }

        var fluidInput = level.getBlockEntity(validation.fluidInput()) instanceof SteelConverterFluidInputBlockEntity input
                ? input : null;
        var output = level.getBlockEntity(validation.fluidOutput()) instanceof SteelConverterOutputBlockEntity buffer
                ? buffer : null;
        if (fluidInput == null || output == null) {
            state = SteelConverterState.STRUCTURE_INVALID;
            publish(level, 200);
            sync(level);
            return;
        }

        readPower(level);
        if (!fluidInput.canDrain(INPUT_MB)) {
            state = SteelConverterState.CHARGING;
            publish(level, 200);
            sync(level);
            return;
        }
        if (!output.canAccept(OUTPUT_BILLETS)) {
            state = SteelConverterState.OUTPUT_BLOCKED;
            publish(level, 200);
            sync(level);
            return;
        }

        publish(level, REQUIRED_POWER_WATTS);
        if (!hasPower()) {
            state = SteelConverterState.PAUSED_POWER;
            sync(level);
            return;
        }

        state = SteelConverterState.BLOWING;
        processTicks++;
        setChanged();
        if (processTicks >= PROCESS_TICKS) {
            state = SteelConverterState.TAPPING;
            if (commitBatch(fluidInput, output)) processTicks = 0;
        }
        if (time % 10 == 0 || processTicks == 0) sync(level);
    }

    private boolean commitBatch(SteelConverterFluidInputBlockEntity fluidInput,
                                SteelConverterOutputBlockEntity output) {
        if (!fluidInput.canDrain(INPUT_MB) || !output.canAccept(OUTPUT_BILLETS)) return false;
        if (!output.insert(OUTPUT_BILLETS)) return false;

        FluidStack drained = fluidInput.drain(INPUT_MB);
        if (drained.getAmount() != INPUT_MB) {
            output.rollback(OUTPUT_BILLETS);
            if (!drained.isEmpty()) fluidInput.restore(drained);
            return false;
        }
        setChanged();
        return true;
    }

    private void publish(ServerLevel level, double watts) {
        MinePowerDemand.publishWatts(level, validation.powerPort(), watts);
    }

    private void readPower(ServerLevel level) {
        MinePowerPortDevice device = DevicesSavedData.load(level)
                .getDevice(validation.powerPort(), MinePowerPortDevice.class);
        voltage = device != null && device.isValid() ? finite(device.getVoltage()) : 0;
        power = device != null && device.isValid() ? finite(device.getPower()) : 0;
    }

    private boolean hasPower() {
        return voltage >= MIN_VOLTAGE && voltage <= MAX_VOLTAGE && power >= REQUIRED_POWER_WATTS;
    }

    private static double finite(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    private void sync(ServerLevel level) {
        if (level.getGameTime() % 10 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public SteelConverterMultiblock.ValidationResult validateNow(ServerLevel level) {
        validation = SteelConverterMultiblock.validate(level, worldPosition);
        return validation;
    }

    public SteelConverterState state() { return state; }
    public float progress() { return (float) processTicks / PROCESS_TICKS; }
    public double power() { return power; }
    public double voltage() { return voltage; }

    public int inputAmount(ServerLevel level) {
        if (validation == null) return 0;
        return level.getBlockEntity(validation.fluidInput()) instanceof SteelConverterFluidInputBlockEntity input
                ? input.amount() : 0;
    }

    public int outputAmount(ServerLevel level) {
        if (validation == null) return 0;
        return level.getBlockEntity(validation.fluidOutput()) instanceof SteelConverterOutputBlockEntity output
                ? output.amount() : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ProcessTicks", processTicks);
        tag.putString("State", state.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ProcessTicks", Tag.TAG_INT)) {
            processTicks = Math.clamp(tag.getInt("ProcessTicks"), 0, PROCESS_TICKS);
        } else {
            processTicks = Math.clamp(Math.round(tag.getFloat("Progress") * PROCESS_TICKS), 0, PROCESS_TICKS);
        }
        try {
            state = SteelConverterState.valueOf(tag.getString("State"));
        } catch (Exception ignored) {
            state = SteelConverterState.STRUCTURE_INVALID;
        }
        validation = null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
