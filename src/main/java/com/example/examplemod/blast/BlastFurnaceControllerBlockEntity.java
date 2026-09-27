package com.example.examplemod.blast;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.electrical.MinePowerPortDevice;
import com.example.examplemod.mine.MinePowerDemand;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class BlastFurnaceControllerBlockEntity extends BlockEntity {
    public static final int PROCESS_DURATION_TICKS = 300;
    public static final int OUTPUT_PER_BATCH_MB = 8_000;
    public static final double REQUIRED_POWER_WATTS = 60_000.0;
    public static final double STANDBY_POWER_WATTS = 200.0;
    public static final double MIN_WORKING_POWER_WATTS = 51_000.0;
    public static final double MIN_VOLTAGE = 500.0;
    public static final double MAX_VOLTAGE = 600.0;
    public static final double MAX_CURRENT = 150.0;
    public static final double MAX_SAFE_POWER_WATTS = 90_000.0;

    private BlastFurnaceState state = BlastFurnaceState.STRUCTURE_INVALID;
    private int processTicks;
    private BlastFurnaceMultiblock.ValidationResult validation;
    private long nextValidationTick;
    private double voltage;
    private double current;
    private double power;

    public BlastFurnaceControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.BLAST_FURNACE_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState blockState,
                                  BlastFurnaceControllerBlockEntity controller) {
        controller.tickServer(level, pos);
    }

    private void tickServer(ServerLevel level, BlockPos pos) {
        long gameTime = level.getGameTime();
        if (validation == null || gameTime >= nextValidationTick) {
            validation = BlastFurnaceMultiblock.validate(level, pos);
            nextValidationTick = gameTime + (processTicks > 0 ? 20L : 100L);
        }
        if (!validation.assembled()) {
            state = BlastFurnaceState.STRUCTURE_INVALID;
            resetProgress();
            publishDemand(level, STANDBY_POWER_WATTS);
            return;
        }

        BlastFurnaceInputBlockEntity input = level.getBlockEntity(validation.itemInput())
                instanceof BlastFurnaceInputBlockEntity found ? found : null;
        BlastFurnaceOutputBlockEntity output = level.getBlockEntity(validation.fluidOutput())
                instanceof BlastFurnaceOutputBlockEntity found ? found : null;
        if (input == null || output == null) {
            state = BlastFurnaceState.STRUCTURE_INVALID;
            resetProgress();
            publishDemand(level, STANDBY_POWER_WATTS);
            return;
        }
        if (!input.hasRecipe()) {
            state = BlastFurnaceState.NO_INPUT;
            resetProgress();
            publishDemand(level, STANDBY_POWER_WATTS);
            readPower(level);
            return;
        }
        if (output.capacityForHotMetal() < OUTPUT_PER_BATCH_MB) {
            state = BlastFurnaceState.OUTPUT_BLOCKED;
            publishDemand(level, STANDBY_POWER_WATTS);
            readPower(level);
            return;
        }

        publishDemand(level, REQUIRED_POWER_WATTS);
        readPower(level);
        if (!hasWorkingPower()) {
            state = BlastFurnaceState.NO_POWER;
            return;
        }

        state = BlastFurnaceState.RUNNING;
        processTicks++;
        if (processTicks % 20 == 0) setChanged();
        if (processTicks < PROCESS_DURATION_TICKS) return;

        if (!input.hasRecipe() || output.capacityForHotMetal() < OUTPUT_PER_BATCH_MB) return;
        if (!input.consumeRecipe()) {
            state = BlastFurnaceState.NO_INPUT;
            return;
        }
        int inserted = output.insertHotMetal(OUTPUT_PER_BATCH_MB, IFluidHandler.FluidAction.EXECUTE);
        if (inserted != OUTPUT_PER_BATCH_MB) {
            if (inserted > 0) output.fluidHandler().drain(inserted, IFluidHandler.FluidAction.EXECUTE);
            input.restoreRecipe();
            CreateCityExpansionCore.LOGGER.error("Blast Furnace transaction rolled back at {}", worldPosition);
            state = BlastFurnaceState.OUTPUT_BLOCKED;
            return;
        }
        processTicks = 0;
        setChanged();
    }

    public BlastFurnaceMultiblock.ValidationResult validateNow(ServerLevel level) {
        validation = BlastFurnaceMultiblock.validate(level, worldPosition);
        nextValidationTick = level.getGameTime() + 20L;
        return validation;
    }

    private void publishDemand(ServerLevel level, double watts) {
        if (validation != null) MinePowerDemand.publishWatts(level, validation.powerPort(), watts);
    }

    private void readPower(ServerLevel level) {
        MinePowerPortDevice device = validation == null || validation.powerPort() == null ? null
                : DevicesSavedData.load(level).getDevice(validation.powerPort(), MinePowerPortDevice.class);
        if (device == null || !device.isValid()) {
            voltage = current = power = 0.0;
            return;
        }
        voltage = finite(device.getVoltage());
        current = finite(device.getCurrent());
        power = finite(device.getPower());
    }

    private boolean hasWorkingPower() {
        return voltage >= MIN_VOLTAGE && voltage <= MAX_VOLTAGE && current <= MAX_CURRENT
                && power >= MIN_WORKING_POWER_WATTS && power <= MAX_SAFE_POWER_WATTS;
    }

    private static double finite(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    private void resetProgress() {
        if (processTicks != 0) { processTicks = 0; setChanged(); }
    }

    public BlastFurnaceState state() { return state; }
    public double progress() { return (double) processTicks / PROCESS_DURATION_TICKS; }
    public double power() { return power; }
    public double voltage() { return voltage; }
    public double current() { return current; }
    public BlastFurnaceMultiblock.ValidationResult validation() { return validation; }

    public InputSummary inputSummary(ServerLevel level) {
        if (validation != null && level.getBlockEntity(validation.itemInput())
                instanceof BlastFurnaceInputBlockEntity input)
            return new InputSummary(input.ironCount(), input.cokeCount(), input.calciteCount());
        return new InputSummary(0, 0, 0);
    }
    public int outputAmount(ServerLevel level) {
        return validation != null && level.getBlockEntity(validation.fluidOutput())
                instanceof BlastFurnaceOutputBlockEntity output ? output.amount() : 0;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ProcessTicks", processTicks);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        processTicks = Math.max(0, Math.min(PROCESS_DURATION_TICKS - 1, tag.getInt("ProcessTicks")));
        state = BlastFurnaceState.STRUCTURE_INVALID;
        validation = null;
        voltage = current = power = 0.0;
    }

    public record InputSummary(int iron, int coke, int calcite) {}
}
