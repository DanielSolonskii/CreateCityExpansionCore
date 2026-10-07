package com.example.examplemod.city;

/**
 * Why a claim request was refused.
 *
 * <p>The client used to have nothing to report: every refusal was a silent return, so the map could
 * not say why a click did nothing. The server now answers with one of these, and the client renders
 * the matching message — including the failure cases that protect the world from a modified client.
 */
public enum CityClaimResult {

    /** The plot was taken and is now owned by the city. */
    CLAIMED,

    /** The request did not come from a player standing at a real City Pylon of a city. */
    NO_CITY,

    /** The player is neither OWNER nor ADMIN of that city. */
    NO_PERMISSION,

    /** The player is too far from the pylon they claimed to act on it. */
    TOO_FAR,

    /** The requested dimension is not the dimension the pylon (and the player) are in. */
    DIMENSION_MISMATCH,

    /** The plot coordinates are out of range or would overflow block coordinates. */
    INVALID_PLOT,

    /** This city already owns the plot; claiming it again is a no-op, not an error. */
    ALREADY_OWNED,

    /** Another city owns the plot. Claims never overlap. */
    OWNED_BY_OTHER_CITY,

    /** The plot touches the territory only at a corner, or not at all. */
    NOT_ADJACENT;

    public boolean success() {
        return this == CLAIMED;
    }
}
