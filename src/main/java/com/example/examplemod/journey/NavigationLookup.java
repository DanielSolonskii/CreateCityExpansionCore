package com.example.examplemod.journey;

import java.util.UUID;

/** Optional scoped read carried by the existing 64-character Journey STATE argument. */
public record NavigationLookup(Kind kind, UUID id, long sequence) {
    public enum Kind { CITY, INDUSTRY, PROJECT, STATION, WAREHOUSE, POINT, TRAIN, SHIPMENT, ROAD, OFFER, DEMAND, INTAKE, PLACE }

    public String token() {
        return "n/" + kind.name() + "/" + id + "/" + Long.toUnsignedString(sequence, 36);
    }

    public static NavigationLookup parse(String token) {
        if (token == null || token.length() > 64 || !token.startsWith("n/")) return null;
        String[] parts = token.split("/", -1);
        if (parts.length != 4 || parts[2].length() != 36) return null;
        try {
            var value = new NavigationLookup(Kind.valueOf(parts[1]), UUID.fromString(parts[2]),
                    Long.parseUnsignedLong(parts[3], 36));
            return value.token().equals(token) ? value : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
