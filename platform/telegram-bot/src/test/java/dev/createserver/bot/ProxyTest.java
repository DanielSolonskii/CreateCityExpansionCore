package dev.createserver.bot;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProxyTest {
    private Map<String,String> settings(int port) {
        return Map.of("TELEGRAM_PROXY_TYPE","HTTP","TELEGRAM_PROXY_HOST","127.0.0.1",
                "TELEGRAM_PROXY_PORT",Integer.toString(port));
    }
    @Test void absentAndEmptySettingsAreExplicitlyDirect() {
        assertThat(ProxyConfiguration.fromEnvironment(Map.of()).enabled()).isFalse();
        assertThat(BotHttpClients.telegram(ProxyConfiguration.fromEnvironment(Map.of())).proxy()).isEqualTo(Proxy.NO_PROXY);
        assertThat(ProxyConfiguration.fromEnvironment(Map.of("TELEGRAM_PROXY_TYPE","","TELEGRAM_PROXY_HOST","",
                "TELEGRAM_PROXY_PORT","","TELEGRAM_PROXY_USERNAME","","TELEGRAM_PROXY_PASSWORD","")).enabled()).isFalse();
    }
    @Test void validAuthenticatedConfigurationIsRedacted() {
        var env=new HashMap<>(settings(3128));
        env.put("TELEGRAM_PROXY_USERNAME","fake-user"); env.put("TELEGRAM_PROXY_PASSWORD","fake-password");
        var config=ProxyConfiguration.fromEnvironment(env);
        assertThat(config.enabled()).isTrue(); assertThat(config.port()).isEqualTo(3128);
        assertThat(config.toString()).isEqualTo("ProxyConfiguration[redacted]");
    }
    @Test void invalidAndPartialConfigurationFailsWithoutLeakingValues() {
        for(var env:List.of(Map.of("TELEGRAM_PROXY_PASSWORD","private-fake-password"),
                Map.of("TELEGRAM_PROXY_TYPE","SOCKS5","TELEGRAM_PROXY_HOST","host","TELEGRAM_PROXY_PORT","1080"),
                Map.of("TELEGRAM_PROXY_TYPE","HTTP","TELEGRAM_PROXY_HOST","http://user:secret@host","TELEGRAM_PROXY_PORT","3128"),
                Map.of("TELEGRAM_PROXY_TYPE","HTTP","TELEGRAM_PROXY_HOST","host","TELEGRAM_PROXY_PORT","0"),
                Map.of("TELEGRAM_PROXY_TYPE","HTTP","TELEGRAM_PROXY_HOST","host","TELEGRAM_PROXY_PORT","65536"),
                Map.of("TELEGRAM_PROXY_TYPE","HTTP","TELEGRAM_PROXY_HOST","host","TELEGRAM_PROXY_PORT","abc"),
                Map.of("TELEGRAM_PROXY_TYPE","HTTP","TELEGRAM_PROXY_HOST","host","TELEGRAM_PROXY_PORT","3128","TELEGRAM_PROXY_USERNAME","user"))) {
            assertThatThrownBy(()->ProxyConfiguration.fromEnvironment(env)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid Telegram proxy configuration");
        }
    }
    @Test void backendBypassesProxyAndNeverSendsProxyCredentials() throws Exception {
        try(var proxy=new Tunnel(0,false)) {
            HttpServer backend=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            backend.createContext("/api/internal/telegram/users/123",exchange->{
                assertThat(exchange.getRequestHeaders().getFirst("Proxy-Authorization")).isNull();
                assertThat(exchange.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("fake-secret");
                exchange.sendResponseHeaders(200,-1); exchange.close();
            });
            backend.start(); ProxySelector previous=ProxySelector.getDefault();
            try {
                ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress("127.0.0.1",proxy.port())));
                var telegram=BotHttpClients.telegram(ProxyConfiguration.fromEnvironment(settings(proxy.port())));
                assertThat(telegram.proxy().type()).isEqualTo(Proxy.Type.HTTP);
                var http=BotHttpClients.backend();
                assertThat(http.proxy().orElseThrow().select(URI.create("https://example.com"))).containsExactly(Proxy.NO_PROXY);
                assertThat(http.authenticator()).isEmpty();
                assertThat(new BackendClient(URI.create("http://127.0.0.1:"+backend.getAddress().getPort()),
                        "fake-secret",http).linkedUser(123)).isEqualTo(200);
                assertThat(proxy.connections.get()).isZero();
            } finally {ProxySelector.setDefault(previous); backend.stop(0);}
        }
    }
    @Test void realHttpsConnectCarriesAuthOnlyOnTunnelAndPreservesTelegramContract() throws Exception {
        var certificate=new HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build();
        var serverTls=new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        var clientTls=new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        try(var origin=new MockWebServer()) {
            origin.useHttps(serverTls.sslSocketFactory(),false); origin.start();
            origin.enqueue(new MockResponse().setBody("{\"ok\":true,\"result\":[]}"));
            try(var proxy=new Tunnel(origin.getPort(),false)) {
                var env=new HashMap<>(settings(proxy.port()));
                env.put("TELEGRAM_PROXY_USERNAME","fake-user"); env.put("TELEGRAM_PROXY_PASSWORD","fake-pass");
                var http=BotHttpClients.telegram(ProxyConfiguration.fromEnvironment(env)).newBuilder()
                        .sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager()).build();
                // Explicit localhost avoids platform-dependent reverse DNS in MockWebServer.url().
                var client=new TelegramClient(URI.create("https://localhost:"+origin.getPort()+"/botfake/"),http);
                assertThat(client.updates(42).isArray()).isTrue();
                String connect=proxy.headers.get(5,TimeUnit.SECONDS);
                assertThat(connect).startsWith("CONNECT localhost:"+origin.getPort()+" HTTP/1.1")
                        .contains("Proxy-Authorization: "+Credentials.basic("fake-user","fake-pass"))
                        .doesNotContain("botfake", "fake-secret");
                var request=origin.takeRequest(5,TimeUnit.SECONDS);
                assertThat(request).isNotNull(); assertThat(request.getPath()).isEqualTo("/botfake/getUpdates");
                assertThat(request.getHeader("Proxy-Authorization")).isNull();
                assertThat(request.getHeader("Authorization")).isNull();
                assertThat(request.getBody().readUtf8()).contains("\"offset\":42", "\"timeout\":30", "message");
                http.connectionPool().evictAll(); http.dispatcher().executorService().shutdown();
            }
        }
    }
    @Test void wrongProxyCredentialsDoNotLoopOrLeakInErrors() throws Exception {
        try(var proxy=new Tunnel(0,true)) {
            var env=new HashMap<>(settings(proxy.port()));
            env.put("TELEGRAM_PROXY_USERNAME","fake-user"); env.put("TELEGRAM_PROXY_PASSWORD","fake-password");
            var client=new TelegramClient("fake-token",BotHttpClients.telegram(ProxyConfiguration.fromEnvironment(env)));
            assertThatThrownBy(()->client.updates(0)).isInstanceOf(IOException.class)
                    .hasMessage("Telegram communication failed").hasNoCause();
            assertThat(proxy.connections.get()).isEqualTo(1);
        }
    }
    @Test void unauthenticatedProxyConnectOmitsAuthHeader() throws Exception {
        try(var proxy=new Tunnel(0,true)) {
            var client=new TelegramClient("fake-token",BotHttpClients.telegram(ProxyConfiguration.fromEnvironment(settings(proxy.port()))));
            assertThatThrownBy(()->client.updates(0)).isInstanceOf(IOException.class);
            assertThat(proxy.headers.get(5,TimeUnit.SECONDS)).doesNotContain("Proxy-Authorization");
        }
    }
    @Test void unauthenticatedHttpsConnectSucceeds() throws Exception {
        var certificate=new HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build();
        var serverTls=new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        var clientTls=new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
        try(var origin=new MockWebServer()) {
            origin.useHttps(serverTls.sslSocketFactory(),false);origin.start();
            origin.enqueue(new MockResponse().setBody("{\"ok\":true,\"result\":[]}"));
            try(var proxy=new Tunnel(origin.getPort(),false)) {
                var http=BotHttpClients.telegram(ProxyConfiguration.fromEnvironment(settings(proxy.port()))).newBuilder()
                        .sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager()).build();
                var client=new TelegramClient(URI.create("https://localhost:"+origin.getPort()+"/"),http);
                assertThat(client.updates(0).isArray()).isTrue();
                assertThat(proxy.headers.get(5,TimeUnit.SECONDS)).doesNotContain("Proxy-Authorization");
                assertThat(origin.takeRequest(5,TimeUnit.SECONDS).getHeader("Proxy-Authorization")).isNull();
                http.connectionPool().evictAll();http.dispatcher().executorService().shutdown();
            }
        }
    }
    @Test void directTelegramStillWorksWithHostileGlobalProxy() throws Exception {
        try(var server=new MockWebServer();var proxy=new Tunnel(0,true)) {
            server.start();server.enqueue(new MockResponse().setBody("{\"ok\":true,\"result\":[]}"));
            ProxySelector previous=ProxySelector.getDefault();
            try {
                ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress("127.0.0.1",proxy.port())));
                var client=new TelegramClient(server.url("/").uri(),BotHttpClients.telegram(ProxyConfiguration.fromEnvironment(Map.of())));
                assertThat(client.updates(0).isArray()).isTrue();assertThat(proxy.connections.get()).isZero();
            } finally {ProxySelector.setDefault(previous);}
        }
    }
    /** Loopback-only test CONNECT proxy; no external connections or real credentials. */
    private static final class Tunnel implements AutoCloseable {
        final ServerSocket listener=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
        final ExecutorService workers=Executors.newCachedThreadPool();
        final CompletableFuture<String> headers=new CompletableFuture<>();
        final AtomicInteger connections=new AtomicInteger();
        final List<Socket> sockets=new CopyOnWriteArrayList<>();
        Tunnel(int targetPort,boolean reject) throws IOException {
            workers.submit(()->{
                try {
                    Socket client=listener.accept(); sockets.add(client);client.setSoTimeout(10000);connections.incrementAndGet();
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    String text="";
                    while(!text.endsWith("\r\n\r\n") && bytes.size()<16384) {
                        int b=client.getInputStream().read();if(b<0)throw new EOFException();
                        bytes.write(b);text=bytes.toString(StandardCharsets.ISO_8859_1);
                    }
                    headers.complete(text);
                    if(reject) {
                        client.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"test\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                        client.close();return;
                    }
                    Socket origin=new Socket("127.0.0.1",targetPort);sockets.add(origin);
                    client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    workers.submit(()->copy(client,origin));workers.submit(()->copy(origin,client));
                } catch(Exception error) {headers.completeExceptionally(error);}
            });
        }
        int port(){return listener.getLocalPort();}
        private static void copy(Socket from,Socket to) {
            try{from.getInputStream().transferTo(to.getOutputStream());}catch(IOException ignored){}
        }
        @Override public void close() throws IOException {
            listener.close();for(var socket:sockets)socket.close();workers.shutdownNow();
        }
    }
}
