package com.example.examplemod.city;

import java.util.UUID;

public class CityInvite {

    private final UUID playerId;

    public CityInvite(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }
}