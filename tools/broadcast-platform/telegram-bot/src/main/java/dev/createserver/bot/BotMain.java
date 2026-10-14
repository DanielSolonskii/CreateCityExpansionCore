package dev.createserver.bot;

import java.net.URI;
import java.util.Map;

public final class BotMain {
    private BotMain() {}
    public static void main(String[] args) throws InterruptedException {
        Map<String, String> env = System.getenv();
        String token = required(env, "TELEGRAM_BOT_TOKEN");
        String username = required(env, "TELEGRAM_BOT_USERNAME");
        String secret = required(env, "BOT_BACKEND_SECRET");
        if (!token.matches("[0-9]+:[A-Za-z0-9_-]+") || !username.matches("[A-Za-z0-9_]{5,32}") ||
                secret.length() < 32) { throw new IllegalArgumentException("Invalid bot configuration"); }
        URI backendUri = URI.create(env.getOrDefault("BACKEND_URL", "http://127.0.0.1:8080"));
        String launcher = launcherUrl(env);
        requireSafeTransport(backendUri);
        ProxyConfiguration proxy = ProxyConfiguration.fromEnvironment(env);
        TelegramClient telegram = new TelegramClient(token, BotHttpClients.telegram(proxy));
        BotHandler handler = new BotHandler(username, launcher,
                new BackendClient(backendUri, secret, BotHttpClients.backend()), telegram,ReportAdminPanel.configured(env));
        long offset = 0;
        var broadcasts=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"broadcast-delivery");t.setDaemon(true);return t;});
        broadcasts.scheduleWithFixedDelay(()->{
            try{handler.notifyBroadcasts();}catch(InterruptedException stopped){Thread.currentThread().interrupt();}
            catch(RuntimeException failed){System.err.println("Broadcast worker deferred.");}
        },5,5,java.util.concurrent.TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(broadcasts::shutdownNow));
        System.out.println("Create Server Telegram bot started (long polling).");
        while (!Thread.currentThread().isInterrupted()) {
            handler.notifyReports();
            try {
                var updates = telegram.updates(offset);
                for (var update : updates) {
                    handler.handle(update);
                    offset = update.path("update_id").asLong() + 1;
                }
            } catch (java.io.IOException | RuntimeException exception) {
                // Never print exception messages/URLs: they can contain the Telegram API token.
                System.err.println("Communication failed; retrying.");
                Thread.sleep(3000);
            }
        }
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) { throw new IllegalArgumentException("Required environment variable: " + name); }
        return value;
    }
    static String launcherUrl(Map<String, String> env) {
        String value = env.get("LAUNCHER_DOWNLOAD_URL");
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null ||
                    uri.getFragment() != null) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Launcher download URL: HTTPS without credentials required");
        }
    }
    private static void requireSafeTransport(URI uri) {
        boolean local = "127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost());
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && local)) {
            throw new IllegalArgumentException("Backend URL must use HTTPS or loopback HTTP");
        }
    }
}
