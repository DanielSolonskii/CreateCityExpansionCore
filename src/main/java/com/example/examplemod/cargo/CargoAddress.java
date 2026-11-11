package com.example.examplemod.cargo;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stable point identities, independent of display names and intermediate train assignments. */
public record CargoAddress(UUID nextPoint, UUID finalPoint, UUID shipment) {
    public CargoAddress(UUID nextPoint, UUID finalPoint) { this(nextPoint, finalPoint, null); }
    public CargoAddress withNext(UUID next) { return new CargoAddress(next, finalPoint, shipment); }
    public CargoAddress { Objects.requireNonNull(nextPoint); Objects.requireNonNull(finalPoint); }
    public static boolean reserved(String address) { return address != null && address.startsWith("ccec/"); }
    public static Optional<CargoAddress> parse(String address) {
        if (address == null) return Optional.empty();
        String[] parts = address.split("/", -1);
        if ((parts.length != 4 && parts.length != 5) || !parts[0].equals("ccec") || !parts[1].equals("v1")) return Optional.empty();
        try {
            UUID next = UUID.fromString(parts[2]), last = UUID.fromString(parts[3]);
            if (!next.toString().equals(parts[2]) || !last.toString().equals(parts[3])) return Optional.empty();
            UUID shipment = parts.length == 5 ? UUID.fromString(parts[4]) : null;
            if (shipment != null && !shipment.toString().equals(parts[4])) return Optional.empty();
            return Optional.of(new CargoAddress(next, last, shipment));
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    public static String postboxFilter(UUID point) { return "ccec/v1/" + Objects.requireNonNull(point) + "/*"; }
    @Override public String toString() { return "ccec/v1/" + nextPoint + "/" + finalPoint + (shipment == null ? "" : "/" + shipment); }

    /** Legacy -> legacy remains supported; CCEC final destination can never be silently replaced. */
    public static boolean mayForward(String current, String requested) {
        var before = parse(current);
        var after = parse(requested);
        if (reserved(current) && before.isEmpty() || reserved(requested) && after.isEmpty()) return false;
        return before.isEmpty() || after.isPresent() && before.get().finalPoint.equals(after.get().finalPoint)
                && Objects.equals(before.get().shipment, after.get().shipment);
    }
}
