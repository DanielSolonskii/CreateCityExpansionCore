package com.example.examplemod.economy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MarketDefinitions {
    private static final Map<ResourceLocation,MarketDefinition> VALUES=new LinkedHashMap<>();
    private static long lastLoad=Long.MIN_VALUE;
    private MarketDefinitions(){}
    public static Map<ResourceLocation,MarketDefinition> values(ServerLevel level){long now=level.getGameTime();if(VALUES.isEmpty()||now<lastLoad||now-lastLoad>=1200)reload(level);return Map.copyOf(VALUES);}
    private static void reload(ServerLevel level){Map<ResourceLocation,MarketDefinition> loaded=new LinkedHashMap<>();level.getServer().getResourceManager().listResources("economy/market",id->id.getPath().endsWith(".json")).forEach((id,resource)->{try(Reader reader=resource.openAsReader()){JsonObject j=JsonParser.parseReader(reader).getAsJsonObject();ResourceLocation definitionId=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),id.getPath().substring("economy/market/".length(),id.getPath().length()-5));MarketDefinition d=new MarketDefinition(definitionId,j.get("selector").getAsString(),string(j,"category","other"),bool(j,"sell_to_master",true),bool(j,"buy_from_master",false),j.get("reference_value").getAsLong(),lng(j,"target_demand",0),lng(j,"target_supply",0),lng(j,"recovery_per_hour",0),integer(j,"minimum_price_bps",7500),integer(j,"maximum_price_bps",15000),integer(j,"import_spread_bps",1500),lng(j,"minimum_offer",1),lng(j,"maximum_offer",64),lng(j,"deadline_ticks",72000),integer(j,"bond_bps",0));loaded.put(definitionId,d);}catch(Exception ignored){}});VALUES.clear();VALUES.putAll(loaded);lastLoad=level.getGameTime();}
    private static long lng(JsonObject j,String k,long d){return j.has(k)?j.get(k).getAsLong():d;} private static int integer(JsonObject j,String k,int d){return j.has(k)?j.get(k).getAsInt():d;} private static boolean bool(JsonObject j,String k,boolean d){return j.has(k)?j.get(k).getAsBoolean():d;} private static String string(JsonObject j,String k,String d){return j.has(k)?j.get(k).getAsString():d;}
}
