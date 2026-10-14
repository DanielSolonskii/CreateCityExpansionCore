package com.example.examplemod.client.auth;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import net.minecraft.network.Connection;

/** Standard TLS in the actual Minecraft channel; remote address remains the public server. */
public final class GameTlsTransport {
    private GameTlsTransport() {}
    public static void initialize(Channel channel) throws Exception {
        String host=System.getProperty("recreated.game.tlsHost","");
        int port=Integer.getInteger("recreated.game.tlsPort",0);
        if(host.isEmpty() || !(channel.remoteAddress() instanceof InetSocketAddress remote) || remote.getPort()!=port) return;
        if(port<1 || port>65535 || !host.matches("[A-Za-z0-9.-]{1,253}")
                || Arrays.stream(InetAddress.getAllByName(host)).noneMatch(remote.getAddress()::equals))
            throw new IllegalStateException("Invalid game TLS endpoint");
        var engine=SSLContext.getDefault().createSSLEngine(host,port);
        engine.setUseClientMode(true);
        var params=engine.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");engine.setSSLParameters(params);
        var handler=new SslHandler(engine);handler.setHandshakeTimeoutMillis(5000);
        channel.pipeline().addFirst("recreated_tls",handler);
        if(Boolean.getBoolean("recreated.auth.spike")) handler.handshakeFuture().addListener(future -> {
            if(future.isSuccess()) System.out.println("RECREATED_TLS_REMOTE "+remote.getAddress().getHostAddress()+":"+remote.getPort());
        });
    }
    public static boolean protectedConnection(Connection connection) {
        if(connection.channel()==null) return false;
        var tls=connection.channel().pipeline().get(SslHandler.class);
        if(tls!=null && tls.handshakeFuture().isSuccess()) return true;
        return Boolean.getBoolean("recreated.auth.localIntegration")
                && connection.getRemoteAddress() instanceof InetSocketAddress remote && remote.getAddress().isLoopbackAddress();
    }
}
