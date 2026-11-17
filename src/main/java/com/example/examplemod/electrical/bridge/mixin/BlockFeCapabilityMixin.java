package com.example.examplemod.electrical.bridge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockCapability.class, remap = false)
abstract class BlockFeCapabilityMixin {
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private void ccec$disableServerFe(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity,
                                      Object context, CallbackInfoReturnable<Object> cir) {
        if (!level.isClientSide && (Object) this == Capabilities.EnergyStorage.BLOCK) cir.setReturnValue(null);
    }
}
