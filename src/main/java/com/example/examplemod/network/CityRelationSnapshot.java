package com.example.examplemod.network;

import java.util.UUID;

public record CityRelationSnapshot(UUID cityId, String cityName, Status status) {
    public enum Status { NONE, TRUSTED, TRUSTS_US, PARTNER }
}
