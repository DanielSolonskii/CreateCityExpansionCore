package com.example.examplemod.mixin;

import com.example.examplemod.block.TransportProtection;
import com.example.examplemod.railway.CreateRailwayAdapter;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainRelocationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Запрет переноса защищённых поездов.
 *
 * <p>TrainRelocationPacket — серверная точка перетаскивания поезда
 * гайковёртом (разборка со сборкой в новом месте). Для поезда с
 * зарегистрированным терминалом действие разрешено только участникам
 * города-владельца и его союзникам.
 */
@Mixin(TrainRelocationPacket.class)
public abstract class TrainRelocationPacketMixin {

    @Shadow
    @Final
    private UUID trainId;

    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true)
    private void ccec$enforceTransportProtection(ServerPlayer player, CallbackInfo ci) {
        if (player == null) {
            return;
        }
        Train train = CreateRailwayAdapter.train(trainId);
        if (train == null) {
            return;
        }
        if (TransportProtection.canModifyTrain(player, train)) {
            return;
        }
        TransportProtection.deny(player);
        ci.cancel();
    }
}
