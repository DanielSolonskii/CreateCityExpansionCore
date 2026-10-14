package com.example.examplemod.mixin;

import com.example.examplemod.auth.AuthenticationBootstrap;
import java.net.InetAddress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Change vanilla TCP bootstrap only. Sable's independent Datagram bootstrap stays untouched. */
@Mixin(ServerConnectionListener.class)
public abstract class PrivateMinecraftTcpMixin {
    @Shadow @Final private MinecraftServer server;
    @ModifyArg(method="startTcpServerListener",at=@At(value="INVOKE",target="Lio/netty/bootstrap/ServerBootstrap;localAddress(Ljava/net/InetAddress;I)Lio/netty/bootstrap/AbstractBootstrap;"),index=0,require=1)
    private InetAddress recreated$privateTcp(InetAddress declaredAddress) {
        return AuthenticationBootstrap.privateTcpAddress(server,declaredAddress);
    }
}
