package com.example.examplemod.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Logical identity survives replacement/unloading of physical infrastructure. */
public final class FreightPoint {
    public enum Mode { SEND, RECEIVE, BOTH; public boolean sends(){return this!=RECEIVE;} public boolean receives(){return this!=SEND;} }
    public final UUID id, cityId;
    public final ResourceLocation dimension;
    public final BlockPos postbox, packager;
    public final String address;
    public String name;
    public Mode mode;
    public boolean enabled=true, removed, defaultReceive, defaultSend;
    public UUID station;
    public String stationName="", status="unloaded";
    public boolean postboxPresent, packagerPresent, stationPresent;

    public FreightPoint(UUID id,UUID cityId,ResourceLocation dimension,BlockPos postbox,BlockPos packager,String name,Mode mode){
        this.id=id;this.cityId=cityId;this.dimension=dimension;this.postbox=postbox.immutable();this.packager=packager.immutable();
        this.name=name.isBlank()?"Freight "+id.toString().substring(0,8):name.substring(0,Math.min(64,name.length()));this.mode=mode;
        this.address="CCEC_"+cityId.toString().replace("-","")+"_"+id.toString().replace("-","");
    }
    public boolean online(){return enabled&&!removed&&status.equals("online");}
    public CompoundTag save(){
        CompoundTag t=new CompoundTag();t.putUUID("Id",id);t.putUUID("City",cityId);t.putString("Dimension",dimension.toString());
        t.putLong("Postbox",postbox.asLong());t.putLong("Packager",packager.asLong());t.putString("Name",name);t.putString("Mode",mode.name());
        t.putBoolean("Enabled",enabled);t.putBoolean("Removed",removed);t.putBoolean("DefaultReceive",defaultReceive);t.putBoolean("DefaultSend",defaultSend);
        if(station!=null)t.putUUID("Station",station);t.putString("StationName",stationName);t.putString("Status",status);
        t.putBoolean("PostboxPresent",postboxPresent);t.putBoolean("PackagerPresent",packagerPresent);t.putBoolean("StationPresent",stationPresent);return t;
    }
    public static FreightPoint load(CompoundTag t){
        FreightPoint p=new FreightPoint(t.getUUID("Id"),t.getUUID("City"),ResourceLocation.parse(t.getString("Dimension")),BlockPos.of(t.getLong("Postbox")),BlockPos.of(t.getLong("Packager")),t.getString("Name"),Mode.valueOf(t.getString("Mode")));
        p.enabled=t.getBoolean("Enabled");p.removed=t.getBoolean("Removed");p.defaultReceive=t.getBoolean("DefaultReceive");p.defaultSend=t.getBoolean("DefaultSend");
        p.station=t.hasUUID("Station")?t.getUUID("Station"):null;p.stationName=t.getString("StationName");p.status=t.getString("Status");
        p.postboxPresent=t.getBoolean("PostboxPresent");p.packagerPresent=t.getBoolean("PackagerPresent");p.stationPresent=t.getBoolean("StationPresent");return p;
    }
}
