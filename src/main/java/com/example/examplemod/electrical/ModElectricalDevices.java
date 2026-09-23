package com.example.examplemod.electrical;

import com.example.examplemod.CreateCityExpansionCore;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModElectricalDevices {

    public static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
            DeferredRegister.create(
                    CEERegistries.SIMULATED_DEVICE_TYPE,
                    CreateCityExpansionCore.MODID
            );

    public static final DeferredHolder<
            SimulatedDeviceType<?>,
            SimulatedDeviceType<MinePowerPortDevice>
            > MINE_POWER_PORT =
            DEVICES.register(
                    "mine_power_port",
                    () -> new SimulatedDeviceType<>(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    CreateCityExpansionCore.MODID,
                                    "mine_power_port"
                            ),
                            (type, level, pos, data) ->
                                    new MinePowerPortDevice(
                                            level,
                                            pos,
                                            data,
                                            type
                                    )
                    )
            );

    public static void register(IEventBus modEventBus) {
        DEVICES.register(modEventBus);
    }
}