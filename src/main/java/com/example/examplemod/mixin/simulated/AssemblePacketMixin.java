package com.example.examplemod.mixin.simulated;

import com.example.examplemod.block.TransportProtection;
import com.example.examplemod.block.TransportSubLevelProtection;
import dev.simulated_team.simulated.network.packets.AssemblePacket;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Запрет сборки/разборки защищённых физических конструкций.
 *
 * <p>AssemblePacket — серверная точка рычага physics assembler:
 * игрок дёргает рычаг, пакет просит assembleOrDisassemble на
 * позиции сборщика. Активный sublevel резолвится тем же путём, что
 * и внутренний assembly preventer Simulated. Разборка/пересборка
 * чужой зарегистрированной конструкции запрещена участникам не
 * города-владельца и не его союзников.
 *
 * <p>Применяется только при установленном Simulated — см.
 * {@link com.example.examplemod.mixin.TransportMixinPlugin}.
 */
@Mixin(AssemblePacket.class)
public abstract class AssemblePacketMixin {

    @Shadow
    @Final
    private BlockPos pos;

    @Inject(method = "handle(Lfoundry/veil/api/network/handler/ServerPacketContext;)V", at = @At("HEAD"), cancellable = true)
    private void ccec$enforceTransportProtection(ServerPacketContext context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (player == null) {
            return;
        }
        if (TransportSubLevelProtection.canModifySubLevelAt(player, player.serverLevel(), pos)) {
            return;
        }
        TransportProtection.deny(player);
        ci.cancel();
    }
}
