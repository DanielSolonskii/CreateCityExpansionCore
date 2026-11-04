package com.example.examplemod.mixin;

import com.example.examplemod.cargo.CargoUnpackingObserver;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** One observational hook, including original box argument; no overwrite of Create's physical algorithm. */
@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerCargoReceiptMixin {
    @Redirect(method = "unwrapBox", at = @At(value = "INVOKE", remap = false,
            target = "Lcom/simibubi/create/api/packager/unpacking/UnpackingHandler;unpack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Ljava/util/List;Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;Z)Z"),
            remap = false, require = 1, allow = 1)
    private boolean ccec$observeNativeUnpack(UnpackingHandler handler, Level level, BlockPos target, BlockState state,
                                            Direction side, List<ItemStack> items, PackageOrderWithCrafts context, boolean simulate,
                                            ItemStack originalBox, boolean originalSimulate) {
        return CargoUnpackingObserver.unpack(handler, level, target, state, side, items, context, simulate,
                originalBox, ((PackagerBlockEntity) (Object) this).getBlockPos());
    }
}
