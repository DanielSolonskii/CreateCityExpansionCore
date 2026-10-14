package com.example.examplemod.city;

import java.util.UUID;

public class CityMember {

    public enum Role {
        OWNER,
        ADMIN,
        BUILDER,
        RESIDENT
    }

    private final UUID playerId;

    private Role role;
    private String displayName = "";
    public String getDisplayName() { return displayName; }
    public CityMember(UUID playerId, Role role, String displayName) {
        this(playerId, role);
        this.displayName = displayName;
    }

    public CityMember(
            UUID playerId,
            Role role
    ) {
        this.playerId = playerId;
        this.role = role;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isOwner() {
        return role == Role.OWNER;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isBuilder() {
        return role == Role.BUILDER;
    }

    public boolean isResident() {
        return role == Role.RESIDENT;
    }
}
