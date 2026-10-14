package com.example.examplemod.city;

import java.util.UUID;

/** Shared, server-side city access policy. Relations grant access only where an owner opted into this policy. */
public final class CityAccessPolicy {
    public enum Relation { SELF, PARTNER, TRUSTED, NONE }
    public enum Service { RAILWAY_PRIVATE, FREIGHT, SERVICE }

    private CityAccessPolicy() {}

    public static Relation relation(CityManager cities, UUID ownerCityId, UUID requesterCityId) {
        if (ownerCityId == null || requesterCityId == null || cities == null) return Relation.NONE;
        if (ownerCityId.equals(requesterCityId)) return Relation.SELF;
        boolean trusted = cities.trusts(ownerCityId, requesterCityId);
        if (trusted && cities.trusts(requesterCityId, ownerCityId)) return Relation.PARTNER;
        return trusted ? Relation.TRUSTED : Relation.NONE;
    }

    public static boolean canAccess(CityManager cities, UUID ownerCityId, UUID requesterCityId,
                                    Service service, boolean allowTrusted) {
        Relation relation = relation(cities, ownerCityId, requesterCityId);
        return relation == Relation.SELF || allowTrusted
                && (relation == Relation.TRUSTED || relation == Relation.PARTNER);
    }

    /** Extension hook. Relations currently grant no discount; only own infrastructure is exempt. */
    public static int tariffBasisPoints(CityManager cities, UUID ownerCityId, UUID requesterCityId) {
        return relation(cities, ownerCityId, requesterCityId) == Relation.SELF ? 0 : 10_000;
    }
}
