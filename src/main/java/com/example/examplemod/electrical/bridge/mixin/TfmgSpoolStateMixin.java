package com.example.examplemod.electrical.bridge.mixin;

import com.example.examplemod.electrical.bridge.TfmgTerminal;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="com.drmangotea.tfmg.content.machinery.misc.winding_machine.SpoolItem",remap=false)
abstract class TfmgSpoolStateMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="useOn",at=@At("HEAD"),cancellable=true)
    private void ccec$eeIsNotTfmg(net.minecraft.world.item.context.UseOnContext context,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof com.example.examplemod.electrical.bridge.DcBridgeEntity bridge
                && !bridge.isTfmgSection()) cir.setReturnValue(net.minecraft.world.InteractionResult.PASS);
    }
    @Redirect(method="useOn",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState ccec$terminalState(Level level, BlockPos pos) {
        var endpoint=TfmgTerminal.view(level.getBlockEntity(pos));
        return endpoint instanceof TfmgTerminal ? endpoint.getBlockState() : level.getBlockState(pos);
    }
}
