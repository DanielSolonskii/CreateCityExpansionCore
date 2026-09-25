package com.example.examplemod.railway;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RailLine {
    private final UUID lineId;
    private final UUID cityId;
    private String name;
    private RailwayTypes.LineType type;
    private boolean cyclic;
    private int revision;
    private List<RailLineStop> stops;

    public RailLine(UUID lineId, UUID cityId, String name, RailwayTypes.LineType type,
                    boolean cyclic, int revision, List<RailLineStop> stops) {
        this.lineId = Objects.requireNonNull(lineId);
        this.cityId = Objects.requireNonNull(cityId);
        this.name = sanitizeName(name);
        this.type = Objects.requireNonNull(type);
        this.cyclic = cyclic;
        this.revision = Math.max(1, revision);
        this.stops = new ArrayList<>(stops);
    }

    public UUID lineId() { return lineId; }
    public UUID cityId() { return cityId; }
    public String name() { return name; }
    public RailwayTypes.LineType type() { return type; }
    public boolean cyclic() { return cyclic; }
    public int revision() { return revision; }
    public List<RailLineStop> stops() { return List.copyOf(stops); }

    public boolean update(String name, RailwayTypes.LineType type, boolean cyclic, List<RailLineStop> stops) {
        if (stops.size() < 2 || stops.size() > 64) return false;
        this.name = sanitizeName(name);
        this.type = Objects.requireNonNull(type);
        this.cyclic = cyclic;
        this.stops = new ArrayList<>(stops);
        revision++;
        return true;
    }

    private static String sanitizeName(String name) {
        String value = Objects.requireNonNullElse(name, "").strip();
        return value.isEmpty() ? "Rail Line" : value.substring(0, Math.min(64, value.length()));
    }
}
