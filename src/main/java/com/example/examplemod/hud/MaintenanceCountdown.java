package com.example.examplemod.hud;

import java.util.Locale;

/** A monotonic wall-time deadline, not a count of server ticks. No scheduler or background thread. */
public final class MaintenanceCountdown {
    public static final int MAX_SECONDS = 86_400;
    public static final int MAX_REASON = 256;
    public enum Mode { ANNOUNCE, RESTART }
    public record Frame(int seconds, float progress, boolean warning, boolean finished) {}
    private final Mode mode;
    private final String reason;
    private final long started;
    private final int duration;
    private int previous;

    public MaintenanceCountdown(Mode mode, int seconds, String reason, long now) {
        if (mode == null || seconds < 1 || seconds > MAX_SECONDS || !validReason(reason)) {
            throw new IllegalArgumentException("Invalid maintenance countdown");
        }
        this.mode = mode;
        this.reason = reason.strip();
        this.started = now;
        this.duration = seconds;
        this.previous = seconds;
    }
    public Mode mode() { return mode; }
    public String reason() { return reason; }
    public int remaining(long now) {
        long elapsed = Math.max(0L, now - started);
        return (int) Math.max(0L, duration - elapsed / 1000L);
    }
    public float progress(long now) { return remaining(now) / (float) duration; }
    /** Null means no changed second and therefore no HUD packets. Missed warning marks coalesce. */
    public Frame poll(long now) {
        int seconds = remaining(now);
        if (seconds == previous) return null;
        boolean warning = false;
        for (int mark : new int[]{60, 30, 10, 5}) {
            if (previous > mark && seconds <= mark && seconds > 0) warning = true;
        }
        previous = seconds;
        return new Frame(seconds, seconds / (float) duration, warning, seconds == 0);
    }
    public static String time(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60);
    }
    public static boolean validReason(String reason) {
        return reason != null && !reason.isBlank() && reason.length() <= MAX_REASON
                && reason.codePoints().noneMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT || c == '\u00a7');
    }
}
