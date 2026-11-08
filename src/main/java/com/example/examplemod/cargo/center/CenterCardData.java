package com.example.examplemod.cargo.center;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Typed presentation data. Names are player content; labels and states are client translations. */
public final class CenterCardData {
    private CenterCardData() {}
    public static void part(ListTag list,String label,String name,String state,String detail) {
        if(list.size()>=256)return;
        var row=new CompoundTag();row.putString("Label",label);row.putString("Name",name);
        row.putString("State",state);row.putString("Detail",detail);list.add(row);
    }
    public static void point(CompoundTag row,ListTag routers,ListTag stations,int routerCount,int stationCount,int automaticCount,int unavailableCount) {
        row.putInt("RouterCount",routerCount);row.putInt("StationCount",stationCount);
        row.putString("Automatic",unavailableCount>0?"UNKNOWN":automaticCount==0?"OFF":automaticCount==routerCount?"ON":"PARTIAL");
        var details=new ListTag();stations.forEach(t->{if(details.size()<256)details.add(t.copy());});routers.forEach(t->{if(details.size()<256)details.add(t.copy());});
        row.putBoolean("DetailsTruncated",routerCount*2+stationCount>details.size());
        row.put("Details",details);
    }
}
