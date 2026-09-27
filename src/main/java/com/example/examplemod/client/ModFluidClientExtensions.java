package com.example.examplemod.client;

import com.example.examplemod.fluid.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class ModFluidClientExtensions {
    private ModFluidClientExtensions() {}
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() {
                return ResourceLocation.withDefaultNamespace("block/lava_still");
            }
            @Override public ResourceLocation getFlowingTexture() {
                return ResourceLocation.withDefaultNamespace("block/lava_flow");
            }
            @Override public int getTintColor() { return 0xFFFF6A00; }
        }, ModFluids.HOT_METAL_TYPE.get());
    }
}
