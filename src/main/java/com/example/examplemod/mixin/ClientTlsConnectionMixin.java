package com.example.examplemod.mixin;
import com.example.examplemod.client.auth.GameTlsTransport;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ClientTlsConnectionMixin {
    @Inject(method="channelActive",at=@At("HEAD"),require=1)
    private void recreated$tls(ChannelHandlerContext context,CallbackInfo ci) throws Exception {
        if(((Connection)(Object)this).getReceiving()==PacketFlow.CLIENTBOUND) GameTlsTransport.initialize(context.channel());
    }
}
