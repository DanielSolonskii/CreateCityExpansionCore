package com.example.examplemod.mixin;

import com.example.examplemod.block.TransportProtection;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Защита mounted-блоков contraption'ов (поезда, физические машины).
 *
 * <p>Create обрабатывает использование блоков внутри движущихся
 * конструкций этим пакетом: вскрытие mounted-инвентарей, контролы,
 * сиденья, клапаны. Точка серверная и авторитетная, поэтому mixin
 * на HEAD полностью закрывает взаимодействие чужих игроков с
 * защищённым транспортом. Вождение остаётся публичным: сиденья и
 * контролы TransportProtection пропускает.
 */
@Mixin(ContraptionInteractionPacket.class)
public abstract class ContraptionInteractionPacketMixin {

    @Shadow
    @Final
    private int target;

    @Shadow
    @Final
    private BlockPos localPos;

    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true)
    private void ccec$enforceTransportProtection(ServerPlayer player, CallbackInfo ci) {
        if (player == null) {
            return;
        }
        if (!(player.level().getEntity(target) instanceof AbstractContraptionEntity contraptionEntity)) {
            return;
        }
        if (TransportProtection.canInteractWithContraption(player, contraptionEntity, localPos)) {
            return;
        }
        TransportProtection.deny(player);
        ci.cancel();
    }
}
