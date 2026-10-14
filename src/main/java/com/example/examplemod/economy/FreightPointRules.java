package com.example.examplemod.economy;

import com.example.examplemod.status.CcecStatus;

/**
 * Pure, level-free classification of a registered Freight Point.
 *
 * <p>The server observes the world (block entities, chunks, station identity) and builds an
 * {@link Observation}; this class decides what that observation means for the point and for the
 * unified status card. Keeping the decision free of {@code ServerLevel} is what lets the headless
 * checks drive the exact remove/restore/rebind transitions the client will render.
 */
public final class FreightPointRules {
    private FreightPointRules() {}

    /** What actually sits at a registered block position. */
    public enum Probe { MISSING, INCOMPATIBLE, PRESENT }

    /** Snapshot of the physical world for one registered point. */
    public record Observation(boolean removed, boolean enabled, boolean chunksLoaded, Probe postbox,
                              Probe packager, boolean stationPresent, boolean stationMatched,
                              boolean receiverOk, boolean addressMatches) {}

    public static final String ONLINE = "online";

    /**
     * Precise cause of unavailability, most specific first. Never returns raw debug text: every
     * value is a translation-safe key with a matching {@code gui.*.freight.<key>} lang entry.
     */
    public static String status(Observation o) {
        if (o.removed()) return "removed";
        if (!o.enabled()) return "disabled";
        if (!o.chunksLoaded()) return "unloaded";
        if (o.postbox() != Probe.PRESENT)
            return o.postbox() == Probe.MISSING ? "postbox_missing" : "postbox_incompatible";
        if (o.packager() != Probe.PRESENT)
            return o.packager() == Probe.MISSING ? "packager_missing" : "packager_incompatible";
        if (!o.stationPresent()) return "station_missing";
        if (!o.stationMatched()) return "station_changed";
        if (!o.receiverOk()) return "receiver_missing";
        if (!o.addressMatches()) return "address_changed";
        return ONLINE;
    }

    /**
     * A Postbox replaced in place boots with an empty {@code addressFilter}. At the registered
     * position that is unambiguous evidence the box is ours, so the registered address is restored
     * instead of leaving the point in {@code address_changed} forever. A non-empty foreign filter
     * is a deliberate reconfiguration and is left alone for an explicit rebind.
     *
     * <p>The other healed case is Create's own truncation: its package-port screen caps the address
     * field at 25 characters and unconditionally re-sends the field when the screen closes
     * ({@code PackagePortScreen#removed}), while a registered CCEC address is 70 characters
     * ({@code CCEC_} + city uuid + {@code _} + point uuid). Opening and closing that screen on a
     * registered Postbox therefore writes back the first 25 characters of our own address — short by
     * an upstream field limit, not another configuration. That is healed exactly like an empty
     * filter. A complete foreign address is never a prefix of ours, so it still demands the
     * explicit rebind it always did.
     */
    public static boolean healAddress(String registered, String present) {
        if (registered == null || registered.isEmpty()) return false;
        if (present == null || present.isEmpty()) return true;
        return present.length() < registered.length() && registered.startsWith(present);
    }

    /** Pre-flight shared by register ({@code overLimit=true} only for new points) and rebind. */
    public static String pairFailure(boolean insidePostbox, boolean insidePackager, boolean tooFar,
                                     boolean chunksLoaded, Probe postbox, Probe packager,
                                     boolean otherPointUsesPair, boolean overLimit, boolean stationFound) {
        if (!insidePostbox || !insidePackager) return "wrong_city";
        if (tooFar) return "packager_too_far";
        if (!chunksLoaded) return "unloaded";
        if (postbox != Probe.PRESENT)
            return postbox == Probe.MISSING ? "postbox_missing" : "postbox_incompatible";
        if (packager != Probe.PRESENT)
            return packager == Probe.MISSING ? "packager_missing" : "packager_incompatible";
        if (otherPointUsesPair) return "duplicate";
        if (overLimit) return "limit";
        if (!stationFound) return "station_missing";
        return "";
    }

    /** Unified status card for a point status string; never exposes the raw string to the player. */
    public static CcecStatus ui(String status) {
        String key = status == null ? "" : status;
        return switch (key) {
            case ONLINE -> CcecStatus.of(CcecStatus.Category.SUCCESS, CcecStatus.Code.POINT_READY, CcecStatus.Action.NONE);
            case "removed" -> CcecStatus.of(CcecStatus.Category.WARNING, CcecStatus.Code.POINT_REMOVED, CcecStatus.Action.NONE);
            case "disabled" -> CcecStatus.of(CcecStatus.Category.WARNING, CcecStatus.Code.POINT_DISABLED, CcecStatus.Action.NONE);
            case "unloaded" -> CcecStatus.of(CcecStatus.Category.WARNING, CcecStatus.Code.POINT_UNLOADED, CcecStatus.Action.NONE);
            case "postbox_missing" -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED, CcecStatus.Code.POINT_POSTBOX_MISSING, CcecStatus.Action.FIX_PROBLEM);
            case "postbox_incompatible" -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED, CcecStatus.Code.POINT_POSTBOX_INCOMPATIBLE, CcecStatus.Action.REBIND_POINT);
            case "packager_missing" -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED, CcecStatus.Code.POINT_PACKAGER_MISSING, CcecStatus.Action.FIX_PROBLEM);
            case "packager_incompatible" -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED, CcecStatus.Code.POINT_PACKAGER_INCOMPATIBLE, CcecStatus.Action.REBIND_POINT);
            case "station_missing" -> CcecStatus.of(CcecStatus.Category.WARNING, CcecStatus.Code.POINT_STATION_MISSING, CcecStatus.Action.FIX_PROBLEM);
            case "station_changed" -> CcecStatus.of(CcecStatus.Category.BLOCKED, CcecStatus.Code.POINT_STATION_MISMATCH, CcecStatus.Action.REBIND_POINT);
            case "receiver_missing" -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED, CcecStatus.Code.POINT_RECEIVER_MISSING, CcecStatus.Action.FIX_PROBLEM);
            case "address_changed" -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED, CcecStatus.Code.POINT_ADDRESS_MISMATCH, CcecStatus.Action.REBIND_POINT);
            default -> CcecStatus.of(CcecStatus.Category.WARNING, CcecStatus.Code.POINT_UNLOADED, CcecStatus.Action.NONE);
        };
    }
}
