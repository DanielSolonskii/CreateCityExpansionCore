package dev.createserver.bot;

import java.util.Map;

/** Telegram-only HTTP CONNECT settings. Values are never included in errors/toString. */
public record ProxyConfiguration(String host, int port, String username, String password) {
    public ProxyConfiguration {
        if (host == null) {
            if (port != 0 || username != null || password != null) { throw invalid(); }
        } else {
            if (host.isBlank() || !host.matches("[A-Za-z0-9._:-]+") || port < 1 || port > 65535 ||
                    (username == null) != (password == null) ||
                    (username != null && (username.isBlank() || username.contains(":") ||
                            username.chars().anyMatch(Character::isISOControl) || password.isEmpty() ||
                            password.chars().anyMatch(Character::isISOControl)))) { throw invalid(); }
        }
    }
    public static ProxyConfiguration fromEnvironment(Map<String, String> env) {
        String type = env.get("TELEGRAM_PROXY_TYPE");
        String host = env.get("TELEGRAM_PROXY_HOST");
        String port = env.get("TELEGRAM_PROXY_PORT");
        String user = env.get("TELEGRAM_PROXY_USERNAME");
        String pass = env.get("TELEGRAM_PROXY_PASSWORD");
        if ((type == null || type.isEmpty()) && (host == null || host.isEmpty()) &&
                (port == null || port.isEmpty()) && (user == null || user.isEmpty()) &&
                (pass == null || pass.isEmpty())) { return new ProxyConfiguration(null, 0, null, null); }
        if (type == null || !type.equalsIgnoreCase("HTTP") || host == null || port == null ||
                !port.matches("[0-9]{1,5}")) { throw invalid(); }
        if (user != null && user.isEmpty()) { user = null; }
        if (pass != null && pass.isEmpty()) { pass = null; }
        return new ProxyConfiguration(host, Integer.parseInt(port), user, pass);
    }
    public boolean enabled() { return host != null; }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid Telegram proxy configuration");
    }
    @Override public String toString() { return "ProxyConfiguration[redacted]"; }
}
