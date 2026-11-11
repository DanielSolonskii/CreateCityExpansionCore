package com.example.examplemod.mixin;

import com.example.examplemod.block.TransportProtection;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Запрет разборки защищённого поезда через станцию.
 *
 * <p>tryDisassembleTrain — серверный вход разбора поезда (кнопка
 * «Разобрать» на экране станции и все остальные пути разбора).
 * Метод сам резолвит поезд через getStation().getPresentTrain(),
 * поэтому mixin проверяет права до того, как Create тронет состав.
 */
@Mixin(StationBlockEntity.class)
public abstract class StationBlockEntityMixin {

    @Inject(method = "tryDisassembleTrain(Lnet/minecraft/server/level/ServerPlayer;)Z", at = @At("HEAD"), cancellable = true)
    private void ccec$enforceTransportProtection(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (player == null) {
            return;
        }
        if (TransportProtection.canDisassembleFromStation(player, (StationBlockEntity) (Object) this)) {
            return;
        }
        TransportProtection.deny(player);
        cir.setReturnValue(false);
    }
}
