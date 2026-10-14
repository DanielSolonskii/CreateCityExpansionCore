package dev.createserver.bot;

import java.util.Optional;

public record StartCommand(String registrationToken) {
    public static Optional<StartCommand> parse(String text, String botUsername) {
        if (text == null) { return Optional.empty(); }
        String[] parts = text.trim().split("\\s+", 2);
        String command = parts[0];
        if (!command.equals("/start") && !command.equalsIgnoreCase("/start@" + botUsername)) {
            return Optional.empty();
        }
        if (parts.length == 1) { return Optional.of(new StartCommand(null)); }
        if (!parts[1].matches("[A-Za-z0-9_-]{43}")) { return Optional.empty(); }
        return Optional.of(new StartCommand(parts[1]));
    }
    @Override public String toString() { return "StartCommand[redacted]"; }
}
