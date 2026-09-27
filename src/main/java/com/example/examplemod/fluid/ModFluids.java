package com.example.examplemod.fluid;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, CreateCityExpansionCore.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, CreateCityExpansionCore.MODID);

    public static final DeferredHolder<FluidType, FluidType> HOT_METAL_TYPE = FLUID_TYPES.register(
            "hot_metal", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.createcityexpansioncore.hot_metal")
                    .density(7000).viscosity(6000).temperature(1800).lightLevel(12))
    );

    public static final DeferredHolder<Fluid, FlowingFluid> HOT_METAL = FLUIDS.register(
            "hot_metal", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_HOT_METAL = FLUIDS.register(
            "flowing_hot_metal", () -> new BaseFlowingFluid.Flowing(properties()));

    private ModFluids() {
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(HOT_METAL_TYPE, HOT_METAL, FLOWING_HOT_METAL)
                .slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(30);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
