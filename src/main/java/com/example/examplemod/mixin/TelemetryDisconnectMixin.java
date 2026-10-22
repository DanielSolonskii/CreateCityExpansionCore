package com.example.examplemod.mixin;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe a server-initiated disconnect without storing its free-text reason. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class TelemetryDisconnectMixin {
 @Inject(method="disconnect(Lnet/minecraft/network/chat/Component;)V",at=@At("HEAD"),require=0)
 private void ccec$disconnect(Component reason,CallbackInfo callback){if((Object)this instanceof ServerGamePacketListenerImpl game)com.example.examplemod.auth.ServerTelemetryBridge.disconnectReason(game.player,reason);}
}
