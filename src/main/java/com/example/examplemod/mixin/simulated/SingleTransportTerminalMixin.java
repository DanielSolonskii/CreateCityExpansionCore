package com.example.examplemod.mixin.simulated;

import com.example.examplemod.block.TransportTerminalPolicy;
import com.simibubi.create.content.contraptions.AssemblyException;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;

/** Search has completed; no blocks have been transferred yet. */
@Mixin(SimAssemblyContraption.class)
public abstract class SingleTransportTerminalMixin {
    @Shadow public abstract Collection<BlockPos> getBlocks();
    @Inject(method="searchMovedStructure(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at=@At("RETURN"))
    private void ccec$validateRegistration(Level level, BlockPos start, CallbackInfoReturnable<Boolean> cir)
            throws AssemblyException {
        if (!(level instanceof ServerLevel server) || getBlocks().isEmpty()) return;
        String failure = TransportTerminalPolicy.assemblyFailure(TransportTerminalPolicy.registeredCount(server, getBlocks()));
        if (failure != null) throw new AssemblyException(Component.translatable(failure).withStyle(net.minecraft.ChatFormatting.RED));
    }
}
