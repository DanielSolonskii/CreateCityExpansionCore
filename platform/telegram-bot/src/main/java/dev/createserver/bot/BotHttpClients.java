package dev.createserver.bot;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import okhttp3.*;

public final class BotHttpClients {
    private BotHttpClients() {}
    private static final ProxySelector DIRECT = new ProxySelector() {
        @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress address, IOException error) {}
    };
    public static HttpClient backend() {
        // Explicit direct selector ignores JVM/system proxy defaults and never has proxy auth.
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).proxy(DIRECT).build();
    }
    public static OkHttpClient telegram(ProxyConfiguration config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(45))
                .writeTimeout(Duration.ofSeconds(10)).callTimeout(Duration.ofSeconds(45))
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .authenticator(okhttp3.Authenticator.NONE).proxyAuthenticator(okhttp3.Authenticator.NONE)
                .proxy(config.enabled() ? new Proxy(Proxy.Type.HTTP,
                        InetSocketAddress.createUnresolved(config.host(), config.port())) : Proxy.NO_PROXY);
        if (config.username() != null) {
            builder.proxyAuthenticator((route, response) -> {
                // Credentials belong only to CONNECT, never to Telegram/backend origin requests.
                if (!response.request().method().equals("CONNECT") ||
                        response.request().header("Proxy-Authorization") != null) { return null; }
                return response.request().newBuilder().header("Proxy-Authorization",
                        Credentials.basic(config.username(), config.password())).build();
            });
        }
        return builder.build();
    }
}
