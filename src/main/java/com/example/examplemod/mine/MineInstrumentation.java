package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.machine.*;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Mine-specific adapter into the shared machine instrument contract. */
final class MineInstrumentation {
    static void publish(ServerLevel level, MineControllerBlockEntity controller,
                        MineMultiblock.ValidationResult validation, Tier tier, MinePowerSnapshot measured) {
        var pos = controller.getBlockPos();
        Tier actualTier = tier == null ? Tier.I : tier;
        MineEffectLayout.publish(level, controller, validation.assembled(), actualTier);
        var deposit = GeologyManager.get(level).isStorageAvailable()
                ? controller.getDisplayDepositId().flatMap(GeologyManager.getRegion(level, pos)::getDeposit).orElse(null) : null;
        var profile = deposit == null ? MineProductionProfile.unavailable() : MineProductionProfile.from(deposit, pos.getX(), pos.getZ(), actualTier);
        ResourceLocation product = deposit == null ? null : MineOutputMapping.outputItem(deposit.getResourceId())
                .map(BuiltInRegistries.ITEM::getKey).orElse(null);
        if (product != null && product.equals(BuiltInRegistries.ITEM.getKey(
                com.example.examplemod.item.IndustrialMaterials.IRON_ORE_RAW.get()))) {
            product = BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.RAW_IRON);
        }
        double scale = actualTier.powerPercent()/100.0;
        var power = new MachineTelemetry.Power(measured.voltage(), measured.current(), measured.power(),
                MinePowerAccess.NOMINAL_POWER_WATTS*scale, MinePowerAccess.MIN_VOLTAGE, MinePowerAccess.MAX_VOLTAGE,
                MinePowerAccess.MIN_CURRENT*scale, MinePowerAccess.MAX_CURRENT*scale,
                MinePowerAccess.MIN_WORKING_POWER_WATTS*scale, MinePowerAccess.MAX_SAFE_POWER_WATTS*scale);
        var status = !validation.assembled() ? MachineTelemetry.Status.INCOMPLETE
                : deposit == null || !profile.viable() ? MachineTelemetry.Status.NO_DEPOSIT
                : MinePowerAccess.isUnsafe(controller.getElectricalState())
                    || MinePowerAccess.isUnsafe(MinePowerAccess.classify(measured, true, actualTier)) ? MachineTelemetry.Status.UNSAFE
                : measured.voltage() <= .01 ? MachineTelemetry.Status.NO_POWER
                : MinePowerAccess.classify(measured, true, actualTier) == MineElectricalState.NORMAL
                ? MachineTelemetry.Status.READY : MachineTelemetry.Status.WARNING;
        var gateway = validation.gateway() != null && level.getBlockEntity(validation.gateway()) instanceof MineGatewayBlockEntity entity ? entity : null;
        var output = new GatewayMineOutputPort(gateway);
        controller.publishTelemetry(new MachineTelemetry(validation.assembled(), actualTier.level(), product,
                controller.getRuntimeState().name(), validation.assembled() ? controller.getStatusMessage() : validation.message(), status, power,
                new MachineTelemetry.Production(controller.miningProgress(profile.exactCycleTicks()), profile.exactCycleTicks()/20,
                        profile.itemsPerMinute(), output.itemCount(), output.totalCapacity())));
        if (validation.assembled()) {
            var layout = MineBlueprints.current();
            var facing = controller.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos origin = MineMultiblock.getOrigin(pos, facing);
            for (var role : java.util.List.of(MachineDisplaySource.Role.ENERGY, MachineDisplaySource.Role.OUTPUT)) {
                boolean energy = role == MachineDisplaySource.Role.ENERGY;
                BlockPos displayPos;
                if (layout.legacyBase()) {
                    BlockPos port = energy ? validation.powerPort() : validation.gateway();
                    displayPos = MachineDisplayBlock.adjacentDisplay(port, role,
                            cell -> level.hasChunkAt(cell) ? level.getBlockState(cell) : null);
                } else {
                    BlockPos localPort = energy ? layout.powerPort() : layout.gateway();
                    BlockPos local = MachineDisplayBlock.adjacentDisplay(localPort, role, layout::baseState);
                    displayPos = MineMultiblock.getWorldPosition(origin, facing, local.getX(), local.getY(), local.getZ());
                }
                if (displayPos != null && level.getBlockEntity(displayPos) instanceof MachineDisplayBlockEntity screen) {
                    screen.bind(pos);
                }
            }
        }
    }
}
