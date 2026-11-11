package com.example.examplemod.cargo.center;
import com.example.examplemod.city.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import java.util.*;
public record OwnerRef(Kind kind,UUID id) {
    public enum Kind { PLAYER,CITY }
    public OwnerRef { Objects.requireNonNull(kind);Objects.requireNonNull(id); }
    public static OwnerRef player(UUID id){return new OwnerRef(Kind.PLAYER,id);}
    public boolean available(MinecraftServer s){return kind==Kind.PLAYER||s!=null&&CityManager.get(s.overworld()).getCity(id)!=null;}
    public boolean manages(MinecraftServer s,UUID actor){if(kind==Kind.PLAYER)return id.equals(actor);var city=CityManager.get(s.overworld()).getCity(id);return city!=null&&city.isAdmin(actor);}
    public boolean operates(MinecraftServer s,UUID actor){if(kind==Kind.PLAYER)return id.equals(actor);var city=CityManager.get(s.overworld()).getCity(id);return city!=null&&city.getMember(actor)!=null;}
    public String name(MinecraftServer s){if(kind==Kind.PLAYER)return com.example.examplemod.auth.PlayerNames.name(s,id);var city=CityManager.get(s.overworld()).getCity(id);return city==null?"Удалённый город":city.getName();}
    public CompoundTag save(){var t=new CompoundTag();t.putString("Kind",kind.name());t.putUUID("Id",id);return t;}
    public static OwnerRef load(CompoundTag t){return new OwnerRef(Kind.valueOf(t.getString("Kind")),t.getUUID("Id"));}
}
