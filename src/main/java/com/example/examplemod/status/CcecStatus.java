package com.example.examplemod.status;

import net.minecraft.nbt.CompoundTag;

/** Stable presentation status selected from server-authoritative domain state. */
public record CcecStatus(Category category, Code code, Action action) {
    public enum Category { SUCCESS, ACTION_REQUIRED, BLOCKED, WARNING }
    public enum Action { NONE, SELECT_ROUTE, CONFIRM_DEPARTURE, FIX_PROBLEM, OPEN_FREIGHT_POINT, ASSIGN_TRAIN, START_DISPATCH,
        /** Repair the registered Freight Point: pick a compatible Postbox/Packager/station pair. */
        REBIND_POINT,
        /** Register a Create train into CCEC so routes/tariffs/dispatch can see it. */
        REGISTER_TRAIN }
    public enum Code {
        READY, WAITING_FOR_ROUTE, WAITING_FOR_CARGO, INSUFFICIENT_FUNDS,
        ROUTE_INVALIDATED, PRIVATE_ACCESS_DENIED, IN_TRANSIT, ARRIVED,
        NETWORK_OFFLINE, SECTION_OFFLINE, ROUTE_NOT_SELECTED, TRAIN_NOT_ASSIGNED,
        READY_TO_DISPATCH, DELIVERED, SOURCE_OFFLINE, DESTINATION_OFFLINE,
        RAIL_ROUTE_UNAVAILABLE, SHIPMENT_INVALIDATED, SHIPMENT_REVOKED,
        // Freight Point states. Appended so saved snapshots keep their existing ordinals.
        POINT_READY, POINT_POSTBOX_MISSING, POINT_POSTBOX_INCOMPATIBLE,
        POINT_PACKAGER_MISSING, POINT_PACKAGER_INCOMPATIBLE,
        POINT_STATION_MISSING, POINT_STATION_MISMATCH, POINT_ADDRESS_MISMATCH,
        POINT_RECEIVER_MISSING, POINT_UNLOADED, POINT_DISABLED, POINT_REMOVED,
        // Train management states. Appended so saved snapshots keep their existing ordinals.
        TRAIN_UNREGISTERED, TRAIN_ASSIGNED, TRAIN_IN_USE,
        // Why the train has not left yet. Appended so saved snapshots keep their existing ordinals.
        CARGO_NOT_COUNTED, TRAIN_NOT_AT_SOURCE
    }
    public static CcecStatus of(Category category, Code code, Action action) { return new CcecStatus(category, code, action); }
    public CompoundTag save() { CompoundTag t=new CompoundTag();t.putString("Category",category.name());t.putString("Code",code.name());t.putString("Action",action.name());return t; }
    public static CcecStatus load(CompoundTag t) { try{return of(Category.valueOf(t.getString("Category")),Code.valueOf(t.getString("Code")),Action.valueOf(t.getString("Action")));}catch(IllegalArgumentException ignored){return of(Category.WARNING,Code.NETWORK_OFFLINE,Action.FIX_PROBLEM);} }
    public String key() { return code.name().toLowerCase(java.util.Locale.ROOT); }
}
