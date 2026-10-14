package com.example.examplemod.mixin;
import com.example.examplemod.auth.AuthenticationBootstrap;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginAdmissionMixin {
    @Shadow @Final private Connection connection;
    @Shadow public abstract void disconnect(Component reason);
    @Inject(method="verifyLoginAndFinishConnectionSetup",at=@At("HEAD"),cancellable=true,require=1)
    private void recreated$reserve(GameProfile profile,CallbackInfo ci){
        if(!AuthenticationBootstrap.reserve(profile,connection)){
            disconnect(Component.literal("Предыдущее подключение активно или сервис входа недоступен"));ci.cancel();
        }
    }
}
