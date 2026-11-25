package com.example.examplemod.navigation;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** A source reference carries no coordinates or access claims from the client. */
public record TripRef(Kind kind, UUID id) {
    public enum Kind { PLAYER, PLACE, CITY, PROJECT, INDUSTRY, STATION, WAREHOUSE, POINT, TRAIN, SHIPMENT, OFFER, DEMAND, INTAKE, ROAD, PIN, CARTOGRAPHY }
    public static final TripRef PLAYER = new TripRef(Kind.PLAYER, new UUID(0, 0));
    public String source() { return kind.name().toLowerCase(Locale.ROOT); }
    void write(FriendlyByteBuf b) { b.writeEnum(kind); b.writeUUID(id); }
    static TripRef read(FriendlyByteBuf b) { return new TripRef(b.readEnum(Kind.class), b.readUUID()); }
}
