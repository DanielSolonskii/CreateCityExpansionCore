package com.example.examplemod.client;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/** Local presentation preferences and monotonic cooldowns; never a source of game events. */
public final class CommunityFeedbackSettings {
    public enum Option {
        INTERFACE, NAVIGATION, EVENTS, PROJECT_BANNERS, SOCIAL_BANNERS, DO_NOT_DISTURB;
        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    private enum Channel { CONTROL, RESULT, NAVIGATION, EVENT }
    private final EnumMap<Option, Boolean> options = new EnumMap<>(Option.class);
    private final EnumMap<Channel, Long> last = new EnumMap<>(Channel.class);
    private final LinkedHashSet<UUID> presented = new LinkedHashSet<>();
    private long mutedUntil;

    public boolean enabled(Option key) { return options.getOrDefault(key, key != Option.DO_NOT_DISTURB); }
    public void toggle(Option key) { options.put(key, !enabled(key)); }
    public boolean snoozed(long wallTime) { return wallTime < mutedUntil; }
    public void toggleSnooze(long wallTime) { mutedUntil = snoozed(wallTime) ? 0 : wallTime + 15 * 60_000; }

    public void load(Properties props) {
        for (Option key : Option.values()) options.put(key, Boolean.parseBoolean(
                props.getProperty("feedback." + key.key(), Boolean.toString(key != Option.DO_NOT_DISTURB))));
        try { mutedUntil = Long.parseLong(props.getProperty("feedback.mutedUntil", "0")); }
        catch (NumberFormatException ignored) { mutedUntil = 0; }
        resetSession();
    }

    public void save(Properties props) {
        for (Option key : Option.values()) props.setProperty("feedback." + key.key(), Boolean.toString(enabled(key)));
        props.setProperty("feedback.mutedUntil", Long.toString(mutedUntil));
    }

    public boolean banners(boolean project, long wallTime) {
        return !enabled(Option.DO_NOT_DISTURB) && !snoozed(wallTime)
                && enabled(project ? Option.PROJECT_BANNERS : Option.SOCIAL_BANNERS);
    }

    public boolean projectNotice(UUID id, long wallTime) {
        if (!presented.add(id)) return false;
        if (presented.size() > 256) presented.removeFirst();
        return banners(true, wallTime);
    }

    public void resetSession() {
        presented.clear();
        last.clear();
    }

    public boolean sound(String kind, long monotonicTime, long wallTime) {
        if (enabled(Option.DO_NOT_DISTURB) || snoozed(wallTime)) return false;
        Channel channel = switch (kind) {
            case "success", "error", "map_ok", "map_denied", "map_save" -> Channel.RESULT;
            case "map_navigation" -> Channel.NAVIGATION;
            case "map_warning", "map_event", "invite", "message" -> Channel.EVENT;
            default -> Channel.CONTROL;
        };
        Option category = switch (channel) {
            case NAVIGATION -> Option.NAVIGATION;
            case EVENT -> Option.EVENTS;
            default -> Option.INTERFACE;
        };
        if (!enabled(category)) return false;
        long interval = channel == Channel.EVENT ? 2500 : channel == Channel.NAVIGATION ? 450 : 85;
        Long previous = last.get(channel);
        if (previous != null && monotonicTime - previous < interval) return false;
        last.put(channel, monotonicTime);
        return true;
    }
}
