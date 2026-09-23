package com.example.examplemod.electrical;

import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class MinePowerPortBlock
        extends SimpleElectricalDeviceBlock<MinePowerPortDevice> {

    public MinePowerPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public com.george_vi.electroenergetics.devices.device.SimulatedDeviceType<MinePowerPortDevice>
    getDevice() {

        return ModElectricalDevices.MINE_POWER_PORT.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(
            Level level,
            BlockPos pos,
            BlockState state
    ) {

        return Map.of(
                0, new Vec3(0.0, 0.5, 0.5),
                1, new Vec3(1.0, 0.5, 0.5)
        );
    }

    @Override
    public Vec3 getNodePosition(
            Level level,
            BlockPos pos,
            BlockState state,
            int node
    ) {

        return switch (node) {
            case 0 -> new Vec3(0.0, 0.5, 0.5);
            case 1 -> new Vec3(1.0, 0.5, 0.5);
            default -> Vec3.ZERO;
        };
    }
}
