package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologicalRegion;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Exercises the production path with the same deposit across multiple cycles. */
public final class InexhaustibleMineCheck {
    private InexhaustibleMineCheck() {}

    public static void verify(GeologicalRegion region, GeologicalDeposit deposit) {
        MineGeologyAccess geology = new MineGeologyAccess() {
            @Override public boolean storageAvailable() { return true; }
            @Override public GeologicalRegion getRegion() { return region; }
        };
        Optional<ResourceLocation> selection = Optional.of(deposit.getId());
        MineEnergyGate powered = MineEnergyGate.allowed("Powered");
        long outputPerCycle = MineProductionProfile.from(deposit).outputPerCycle();
        Output output = new Output();
        for (int cycle = 0; cycle < 100; cycle++) {
            MineOperationResult result = MineProductionOperation.execute(
                    true, "", selection, outputPerCycle, powered, output, geology);
            if (result.state() != IndustrialMineState.RUNNING
                    || result.extractedAmount() != outputPerCycle
                    || !result.depositId().equals(selection)) {
                throw new AssertionError("An inexhaustible vein must deliver the full cycle output");
            }
        }
        if (output.count != 100 * outputPerCycle) {
            throw new AssertionError("The gateway must receive all produced items");
        }

        output.blocked = true;
        MineOperationResult blocked = MineProductionOperation.execute(
                true, "", selection, outputPerCycle, powered, output, geology);
        MineEnergyGate unpowered = () -> MineEnergyGate.EnergyDecision.denied(
                IndustrialMineState.NO_POWER, "Unpowered");
        MineOperationResult stopped = MineProductionOperation.execute(
                true, "", selection, outputPerCycle, unpowered, output, geology);
        if (blocked.state() != IndustrialMineState.OUTPUT_BLOCKED
                || stopped.state() != IndustrialMineState.NO_POWER
                || output.count != 100 * outputPerCycle) {
            throw new AssertionError("Power and output checks must still prevent production");
        }
    }

    private static final class Output implements MineOutputPort {
        private int count;
        private boolean blocked;

        @Override public boolean available() { return true; }
        @Override public boolean supports(ResourceLocation resourceId) { return true; }
        @Override public boolean canAccept(ResourceLocation resourceId, long amount) { return !blocked; }
        @Override public void insertExact(ResourceLocation resourceId, long amount) { count += Math.toIntExact(amount); }
        @Override public int itemCount() { return count; }
        @Override public int totalCapacity() { return Integer.MAX_VALUE; }
    }
}
