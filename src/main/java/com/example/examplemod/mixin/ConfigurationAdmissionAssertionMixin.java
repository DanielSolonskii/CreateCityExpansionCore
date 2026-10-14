package com.example.examplemod.mixin;
import com.example.examplemod.auth.AuthenticationBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ConfigurationAdmissionAssertionMixin {
    @Inject(method="handleConfigurationFinished",at=@At("HEAD"),cancellable=true,require=1)
    private void recreated$assert(ServerboundFinishConfigurationPacket packet,CallbackInfo ci){
        var listener=(ServerConfigurationPacketListenerImpl)(Object)this;
        if(!AuthenticationBootstrap.verified(listener.getOwner(),listener.getConnection())){
            listener.disconnect(Component.literal("Вход не подтверждён"));ci.cancel();
        }
    }
}
