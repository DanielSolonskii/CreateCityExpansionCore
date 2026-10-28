package com.example.examplemod.mixin.simulated;

import com.example.examplemod.block.TransportProtection;
import com.example.examplemod.block.TransportSubLevelProtection;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Запрет прямого доступа к physics assembler на защищённой
 * конструкции.
 *
 * <p>Правый клик по сборщику и активация деплоером приходят через
 * {@code useWithoutItem} — единственная точка с игроком в области
 * видимости вне пакета рычага. Пакетный путь отдельно закрыт
 * {@link AssemblePacketMixin}. Путь деплоера молчаливо запрещается
 * (фейковому игроку некому показывать сообщение, но проверка прав —
 * та же).
 *
 * <p>Применяется только при установленном Simulated — см.
 * {@link com.example.examplemod.mixin.TransportMixinPlugin}.
 */
@Mixin(PhysicsAssemblerBlock.class)
public abstract class PhysicsAssemblerBlockMixin {

    @Inject(method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void ccec$enforceTransportProtection(
            BlockState state,
            Level level,
            BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!TransportSubLevelProtection.canModifySubLevelAt(serverPlayer, serverPlayer.serverLevel(), pos)) {
            TransportProtection.deny(serverPlayer);
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
