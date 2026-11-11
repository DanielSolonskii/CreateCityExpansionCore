package com.example.examplemod.cargo.center;

import com.example.examplemod.city.CityManager;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Routes feature workspaces through the existing correlated Logistics Center protocol. */
public final class EconomicCenter {
    private static final Set<String> SECTIONS=Set.of("companies","fleet","roads","debts","master_city","trade_contracts");
    private EconomicCenter(){}
    public static boolean section(String section){return SECTIONS.contains(section);}
    public static boolean action(String action){return action.startsWith("COMPANY_")||action.startsWith("MASTER_")||action.equals("CONTRACT_SUPPLY")||action.startsWith("ROAD_")||action.startsWith("FLEET_")||action.startsWith("DEBT_");}
    public static void execute(ServerPlayer actor,CompoundTag request){
        String action=request.getString("Action");
        if(action.startsWith("COMPANY_"))CompanyCenter.execute(actor,request);
        else if(action.startsWith("MASTER_")||action.equals("CONTRACT_SUPPLY"))MasterCityCenter.execute(actor,request);
        else RailCenter.execute(actor,request);
    }
    public static CompoundTag snapshot(ServerPlayer actor,CompoundTag request){
        String section=request.getString("Section");
        var data=switch(section){case "companies"->CompanyCenter.snapshot(actor,request);case "master_city","trade_contracts"->MasterCityCenter.snapshot(actor,request);default->RailCenter.snapshot(actor,request);};
        if(!data.contains("Page")){
            var rows=new ArrayList<CompoundTag>();String search=request.getString("Search").toLowerCase(Locale.ROOT);
            for(Tag raw:data.getList("Rows",10)){var row=(CompoundTag)raw;if(row.getString("Name").toLowerCase(Locale.ROOT).contains(search)||row.getString("Detail").toLowerCase(Locale.ROOT).contains(search))rows.add(row.copy());}
            rows.sort(Comparator.comparing(row->row.getString("Name"),String.CASE_INSENSITIVE_ORDER));data.putInt("Total",rows.size());
            int page=Math.clamp(request.getInt("Page"),0,Math.max(0,(rows.size()-1)/32));data.putInt("Page",page);
            var paged=new ListTag();for(int i=page*32;i<Math.min(rows.size(),page*32+32);i++)paged.add(rows.get(i));data.put("Rows",paged);
            data.remove("SelectedRow");if(request.hasUUID("Selected"))rows.stream().filter(row->row.hasUUID("Id")&&row.getUUID("Id").equals(request.getUUID("Selected"))).findFirst().ifPresent(row->data.put("SelectedRow",row.copy()));
        }
        data.putString("Section",section);data.putBoolean("Implemented",true);data.putBoolean("Operator",actor.hasPermissions(2));
        if(!data.contains("Guide")){var guide=new CompoundTag();guide.putString("TitleKey","ccec.center.tab."+section);guide.putString("BodyKey","ccec.center.guide."+section+".body");data.put("Guide",guide);}
        var choices=data.getCompound("Choices");fillOwners(actor,choices);data.put("Choices",choices);return data;
    }
    public static void fillOwners(ServerPlayer actor,CompoundTag choices){
        var owners=new ListTag();var subjects=new ListTag();var payers=new ListTag();
        option(owners,OwnerRef.player(actor.getUUID()),actor.getName().getString());
        for(var player:actor.getServer().getPlayerList().getPlayers())option(subjects,OwnerRef.player(player.getUUID()),player.getName().getString());
        for(var city:CityManager.get(actor.serverLevel()).getCities()){
            var owner=new OwnerRef(OwnerRef.Kind.CITY,city.getId());option(subjects,owner,city.getName());
            if(owner.manages(actor.getServer(),actor.getUUID()))option(owners,owner,city.getName());
        }
        CompanyCenter.appendOwners(actor,owners);CompanyCenter.appendSubjects(actor,subjects);
        var managedOwners=new ListTag();
        for(Tag raw:owners){var row=(CompoundTag)raw;var owner=CenterOperations.owner(row.getString("Value"));if(owner.canSpend(actor.getServer(),actor.getUUID()))payers.add(row.copy());if(owner.manages(actor.getServer(),actor.getUUID()))managedOwners.add(row.copy());}
        choices.put("Owners",managedOwners);choices.put("Subjects",subjects);choices.put("Payers",payers);
    }
    private static void option(ListTag list,OwnerRef owner,String name){if(list.size()>=256)return;var row=new CompoundTag();row.putString("Value",owner.kind()+"/"+owner.id());row.putString("Name",name);row.putString("OwnerKind",owner.kind().name());list.add(row);}
}
