package com.example.examplemod.client.logistics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

final class ShipmentInspectionText {
    private ShipmentInspectionText() {}
    static Component text(String key,Object... args) {
        return Component.translatable("gui.createcityexpansioncore.atlas4.shipment."+key,args);
    }
    static List<Component> lines(CompoundTag trace,long elapsedSeconds) {
        if(trace.getInt("Version")!=1) return List.of(text("waiting"));
        var rows=new ArrayList<Component>();
        rows.add(text("sender",place(trace.getString("Sender"))));
        rows.add(text("loading",trace.getInt("Packaged"),trace.getInt("Quantity")));
        rows.add(text("transport",LogisticsSkin.value(trace.getString("Status"))));
        for(var raw:trace.getList("Legs",Tag.TAG_COMPOUND)) {
            var leg=(CompoundTag)raw;
            rows.add(text("leg",place(leg.getString("From")),place(leg.getString("To")),leg.getInt("Units")));
        }
        rows.add(text("receiver",place(trace.getString("Recipient")),trace.getInt("Delivered"),trace.getInt("Quantity")));
        String observation=trace.getString("Observation");
        if (observation.isBlank()) rows.add(text("no_observation"));
        else {
            rows.add(text("observation",LogisticsSkin.value(observation)));
            long age=trace.getLong("Age");
            long currentAge=age+Math.max(0,elapsedSeconds);
            rows.add(age<0 ? text("unknown_age") : text(currentAge>=120?"older":"age",currentAge));
        }
        rows.add(text("reason."+trace.getString("Reason")));
        rows.add(text("geometry."+trace.getString("Geometry")));
        return List.copyOf(rows);
    }
    private static Component place(String name) {
        return name.isBlank()?text("unknown_place"):Component.literal(name);
    }
}
