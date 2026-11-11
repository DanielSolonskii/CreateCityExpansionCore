package com.example.examplemod.city;

import java.util.UUID;

/**
 * One pending invitation.
 *
 * <p>The inviter is part of the record because the sender has to be told how their invite ended —
 * accepted, declined — and that is impossible if the invite only remembers who it was addressed to.
 * Stored alongside the invite, so it survives a restart exactly like the invite itself.
 */
public class CityInvite {

    private final UUID playerId;
    private UUID token = UUID.randomUUID();
    public UUID token(){ return token; }
    public void restoreToken(UUID token){ this.token = java.util.Objects.requireNonNull(token); }

    /** Who sent it. Null only for an invite loaded from data written before this field existed. */
    private final UUID inviterId;

    public CityInvite(UUID playerId) {
        this(playerId, null);
    }

    public CityInvite(UUID playerId, UUID inviterId) {
        this.playerId = playerId;
        this.inviterId = inviterId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getInvokerId() {
        return inviterId;
    }
}