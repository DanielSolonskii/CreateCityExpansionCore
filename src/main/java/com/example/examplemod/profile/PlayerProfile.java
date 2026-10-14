package com.example.examplemod.profile;

import java.util.*;

/** Deliberately public projection. No economy, position, auth credentials or full project records. */
public record PlayerProfile(UUID id, String name, boolean online, String city, String cityRole,
                            Settings settings, String skinName, String texture, String signature,
                            List<Long> values, int completed, int page, List<ProjectEntry> projects) {
    public static final int PAGE_SIZE = 8;
    public static final long UNAVAILABLE = -1, HIDDEN = -2;
    public enum Metric { PLAY_TIME, MINED, DISTANCE, PROJECTS, JUMPS, MOB_KILLS }
    public static final List<Metric> DEFAULT_SHOWCASE = List.of(Metric.PLAY_TIME, Metric.MINED, Metric.PROJECTS);
    public record Settings(String about, List<Metric> showcase, long revision) {
        public static final int MAX_ABOUT = 180;
        public Settings {
            Objects.requireNonNull(about);
            showcase = List.copyOf(showcase);
            if (about.length() > MAX_ABOUT || about.codePoints().anyMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT || c == 0xA7)
                    || showcase.size() != 3 || new HashSet<>(showcase).size() != 3 || revision < 0)
                throw new IllegalArgumentException("Invalid profile settings");
        }
        public static Settings defaults() { return new Settings("", DEFAULT_SHOWCASE, 0); }
    }
    /** Roles are explicit: author is not presented as builder; a city is represented by one player. */
    public record ProjectEntry(UUID id, String title, boolean server, int roles) {
        public static final int AUTHOR = 1, EXECUTOR = 2, REPRESENTATIVE = 4;
        public ProjectEntry {
            if (title == null || title.isBlank() || title.length() > 96 || roles < 1 || roles > 7)
                throw new IllegalArgumentException("Invalid public project");
        }
    }
    public PlayerProfile {
        values = List.copyOf(values); projects = List.copyOf(projects);
        if (values.size() != Metric.values().length || projects.size() > PAGE_SIZE || page < 0 || completed < projects.size())
            throw new IllegalArgumentException("Invalid profile projection");
    }
    public static List<Long> publicValues(List<Long> values, Settings settings, boolean owner) {
        if (values.size() != Metric.values().length) throw new IllegalArgumentException("Invalid metrics");
        List<Long> result = new ArrayList<>();
        for (Metric metric : Metric.values()) result.add(owner || settings.showcase().contains(metric) ? values.get(metric.ordinal()) : HIDDEN);
        return List.copyOf(result);
    }
    public static boolean mayEdit(UUID actor, UUID target) { return actor.equals(target); }
}
