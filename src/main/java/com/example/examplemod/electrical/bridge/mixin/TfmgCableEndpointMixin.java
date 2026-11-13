package com.example.examplemod.electrical.bridge.mixin;

import com.example.examplemod.electrical.bridge.TfmgTerminal;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets={"com.drmangotea.tfmg.content.machinery.misc.winding_machine.SpoolItem",
        "com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity"},remap=false)
abstract class TfmgCableEndpointMixin {
    @Redirect(method="*",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity ccec$terminal(Level level, BlockPos pos) {
        return TfmgTerminal.view(level.getBlockEntity(pos));
    }
}
