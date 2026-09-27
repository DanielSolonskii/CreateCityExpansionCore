package com.example.examplemod.coke;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.electrical.MinePowerPortDevice;
import com.example.examplemod.mine.MinePowerAccess;
import com.example.examplemod.mine.MinePowerDemand;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative processor for one fixed-size coke battery. */
public final class CokeBatteryControllerBlockEntity extends BlockEntity {
    public static final int BATCH_SIZE = 8;
    /** Provisional balance value: one batch every ten seconds, pending Mixer benchmarking. */
    public static final int PROCESS_DURATION_TICKS = 200;
    public static final double REQUIRED_POWER_WATTS = 25_000.0;
    public static final double MIN_WORKING_POWER_WATTS = 21_250.0;
    public static final double MIN_VOLTAGE = 500.0;
    public static final double MAX_VOLTAGE = 600.0;
    public static final double MAX_CURRENT = 65.0;
    public static final double MAX_SAFE_POWER_WATTS = 37_500.0;

    private CokeBatteryState state = CokeBatteryState.STRUCTURE_INVALID;
    private int processTicks;
    private CokeBatteryMultiblock.ValidationResult validation;
    private long nextValidationTick;
    private double voltage;
    private double current;
    private double power;

    public CokeBatteryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.COKE_BATTERY_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(
            ServerLevel level, BlockPos pos, BlockState blockState,
            CokeBatteryControllerBlockEntity controller
    ) {
        controller.tickServer(level, pos);
    }

    private void tickServer(ServerLevel level, BlockPos pos) {
        long gameTime = level.getGameTime();
        if (validation == null || gameTime >= nextValidationTick) {
            validation = CokeBatteryMultiblock.validate(level, pos);
            nextValidationTick = gameTime + (processTicks > 0 ? 20L : 100L);
        }
        if (!validation.assembled()) {
            state = CokeBatteryState.STRUCTURE_INVALID;
            resetProgress();
            publishDemand(level, MinePowerAccess.STANDBY_POWER_WATTS);
            return;
        }

        CokeBatteryInputBlockEntity input = level.getBlockEntity(validation.inputPort())
                instanceof CokeBatteryInputBlockEntity found ? found : null;
        CokeBatteryOutputBlockEntity output = level.getBlockEntity(validation.outputPort())
                instanceof CokeBatteryOutputBlockEntity found ? found : null;
        if (input == null || output == null) {
            state = CokeBatteryState.STRUCTURE_INVALID;
            resetProgress();
            publishDemand(level, MinePowerAccess.STANDBY_POWER_WATTS);
            return;
        }

        int possibleBatch = Math.min(BATCH_SIZE, input.coalCount());
        if (possibleBatch <= 0) {
            state = CokeBatteryState.NO_INPUT;
            resetProgress();
            publishDemand(level, MinePowerAccess.STANDBY_POWER_WATTS);
            readPower(level);
            return;
        }
        possibleBatch = Math.min(possibleBatch, output.insertCapacity(possibleBatch));
        if (possibleBatch <= 0) {
            state = CokeBatteryState.OUTPUT_BLOCKED;
            publishDemand(level, MinePowerAccess.STANDBY_POWER_WATTS);
            readPower(level);
            return;
        }

        publishDemand(level, REQUIRED_POWER_WATTS);
        readPower(level);
        if (!hasWorkingPower()) {
            state = CokeBatteryState.NO_POWER;
            return;
        }

        state = CokeBatteryState.RUNNING;
        processTicks++;
        if (processTicks % 20 == 0) setChanged();
        if (processTicks < PROCESS_DURATION_TICKS) return;

        int batch = Math.min(BATCH_SIZE, input.coalCount());
        batch = Math.min(batch, output.insertCapacity(batch));
        if (batch <= 0) {
            state = CokeBatteryState.OUTPUT_BLOCKED;
            return;
        }
        if (input.consumeCoal(batch)) {
            if (!output.insertCoke(batch)) {
                input.restoreCoal(batch);
                CreateCityExpansionCore.LOGGER.error("Coke Battery output changed during atomic cycle at {}", worldPosition);
                state = CokeBatteryState.OUTPUT_BLOCKED;
                return;
            }
            processTicks = 0;
            setChanged();
        }
    }

    public CokeBatteryMultiblock.ValidationResult validateNow(ServerLevel level) {
        validation = CokeBatteryMultiblock.validate(level, worldPosition);
        nextValidationTick = level.getGameTime() + 20L;
        return validation;
    }

    private void publishDemand(ServerLevel level, double watts) {
        if (validation != null) MinePowerDemand.publishWatts(level, validation.powerPort(), watts);
    }

    private void resetProgress() {
        if (processTicks != 0) {
            processTicks = 0;
            setChanged();
        }
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
        return voltage >= MIN_VOLTAGE && voltage <= MAX_VOLTAGE
                && current <= MAX_CURRENT
                && power >= MIN_WORKING_POWER_WATTS && power <= MAX_SAFE_POWER_WATTS;
    }

    private static double finite(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    public CokeBatteryState state() { return state; }
    public int processTicks() { return processTicks; }
    public double progress() { return (double) processTicks / PROCESS_DURATION_TICKS; }
    public double voltage() { return voltage; }
    public double current() { return current; }
    public double power() { return power; }
    public CokeBatteryMultiblock.ValidationResult validation() { return validation; }

    public int inputCount(ServerLevel level) {
        return validation != null && level.getBlockEntity(validation.inputPort())
                instanceof CokeBatteryInputBlockEntity input ? input.coalCount() : 0;
    }

    public int outputCount(ServerLevel level) {
        return validation != null && level.getBlockEntity(validation.outputPort())
                instanceof CokeBatteryOutputBlockEntity output ? output.cokeCount() : 0;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ProcessTicks", processTicks);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        processTicks = Math.max(0, Math.min(PROCESS_DURATION_TICKS - 1, tag.getInt("ProcessTicks")));
        state = CokeBatteryState.STRUCTURE_INVALID;
        validation = null;
        voltage = current = power = 0.0;
    }
}
