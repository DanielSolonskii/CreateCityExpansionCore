package com.example.examplemod.cargo.center;
import com.example.examplemod.city.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import java.util.*;
public record OwnerRef(Kind kind,UUID id) {
    public enum Kind { PLAYER,CITY,COMPANY,FOREIGN_TRADE }
    public OwnerRef { Objects.requireNonNull(kind);Objects.requireNonNull(id); }
    public static OwnerRef player(UUID id){return new OwnerRef(Kind.PLAYER,id);}
    public boolean available(MinecraftServer s){return switch(kind){case PLAYER->true;case CITY->s!=null&&CityManager.get(s.overworld()).getCity(id)!=null;case COMPANY->s!=null&&companies(s).available(id);case FOREIGN_TRADE->MasterCityTradeBook.ACCOUNT_ID.equals(id);};}
    public boolean manages(MinecraftServer s,UUID actor){return switch(kind){case PLAYER->id.equals(actor);case CITY->{var city=s==null?null:CityManager.get(s.overworld()).getCity(id);yield city!=null&&city.canManageInfrastructure(actor);}case COMPANY->s!=null&&companies(s).permits(id,actor,com.example.examplemod.economy.company.CompanyBook.Permission.ASSETS);case FOREIGN_TRADE->false;};}
    public boolean canSpend(MinecraftServer s,UUID actor){return switch(kind){case PLAYER->id.equals(actor);case CITY->{var city=s==null?null:CityManager.get(s.overworld()).getCity(id);yield city!=null&&city.isAdmin(actor);}case COMPANY->s!=null&&companies(s).permits(id,actor,com.example.examplemod.economy.company.CompanyBook.Permission.SPEND);case FOREIGN_TRADE->false;};}
    public boolean operates(MinecraftServer s,UUID actor){return switch(kind){case PLAYER->id.equals(actor);case CITY->{var city=s==null?null:CityManager.get(s.overworld()).getCity(id);yield city!=null&&city.getMember(actor)!=null;}case COMPANY->s!=null&&companies(s).permits(id,actor,com.example.examplemod.economy.company.CompanyBook.Permission.VIEW);case FOREIGN_TRADE->false;};}
    public String name(MinecraftServer s){return switch(kind){case PLAYER->com.example.examplemod.auth.PlayerNames.name(s,id);case CITY->{var city=s==null?null:CityManager.get(s.overworld()).getCity(id);yield city==null?"Удалённый город":city.getName();}case COMPANY->s==null?"Компания":companies(s).find(id).map(com.example.examplemod.economy.company.CompanyBook.Company::name).orElse("Архивная компания");case FOREIGN_TRADE->net.minecraft.network.chat.Component.translatable("ccec.center.owner.foreign_trade").getString();};}
    private static com.example.examplemod.economy.company.CompanyBook companies(MinecraftServer s){return com.example.examplemod.economy.EconomyManager.get(s.overworld()).companies();}
    public CompoundTag save(){var t=new CompoundTag();t.putString("Kind",kind.name());t.putUUID("Id",id);return t;}
    public static OwnerRef load(CompoundTag t){return new OwnerRef(Kind.valueOf(t.getString("Kind")),t.getUUID("Id"));}
}
