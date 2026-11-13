package com.example.examplemod.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Immutable, compact instrument readings shared by machine controllers and their displays. */
public record MachineTelemetry(boolean assembled, int tier, ResourceLocation product, String state, String message,
                               Status status, Power power, Production production) {
    public enum Status { INCOMPLETE, NO_DEPOSIT, NO_POWER, WARNING, READY, UNSAFE }
    public record Power(double voltage, double current, double watts, double nominalWatts,
                        double minVoltage, double maxVoltage, double minCurrent, double maxCurrent,
                        double minWatts, double maxWatts) {
        public double fraction() { return nominalWatts <= 0 ? 0 : Math.clamp(watts / nominalWatts, 0, 1); }
    }
    public record Production(double progress, double cycleSeconds, double perMinute, int stored, int capacity) {
        public double fullness() { return capacity <= 0 ? 0 : Math.clamp((double) stored / capacity, 0, 1); }
    }
    public static final MachineTelemetry EMPTY = new MachineTelemetry(false, 1, null, "STOPPED", "",
            Status.INCOMPLETE, new Power(0,0,0,0,0,0,0,0,0,0), new Production(0,0,0,0,0));

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Assembled", assembled); tag.putInt("Tier", tier);
        if (product != null) tag.putString("Product", product.toString());
        tag.putString("State", state); tag.putString("Message", message); tag.putString("Status", status.name());
        double[] values = {power.voltage, power.current, power.watts, power.nominalWatts,
                power.minVoltage, power.maxVoltage, power.minCurrent, power.maxCurrent, power.minWatts, power.maxWatts,
                production.progress, production.cycleSeconds, production.perMinute};
        for (int i = 0; i < values.length; i++) tag.putDouble("v" + i, values[i]);
        tag.putInt("Stored", production.stored); tag.putInt("Capacity", production.capacity);
        return tag;
    }

    public static MachineTelemetry read(CompoundTag tag) {
        if (tag.isEmpty()) return EMPTY;
        double[] v = new double[13];
        for (int i = 0; i < v.length; i++) {
            double value = tag.getDouble("v" + i);
            v[i] = Double.isFinite(value) ? Math.max(0, value) : 0;
        }
        Status status;
        try { status = Status.valueOf(tag.getString("Status")); } catch (IllegalArgumentException e) { status = Status.INCOMPLETE; }
        return new MachineTelemetry(tag.getBoolean("Assembled"), Math.max(1, tag.getInt("Tier")),
                ResourceLocation.tryParse(tag.getString("Product")), tag.getString("State"), tag.getString("Message"), status,
                new Power(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9]),
                new Production(Math.min(1,v[10]),v[11],v[12],Math.max(0,tag.getInt("Stored")),Math.max(0,tag.getInt("Capacity"))));
    }
}
