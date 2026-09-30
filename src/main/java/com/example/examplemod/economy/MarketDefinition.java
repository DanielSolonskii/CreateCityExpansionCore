package com.example.examplemod.economy;

import net.minecraft.resources.ResourceLocation;

public record MarketDefinition(ResourceLocation id, String selector, String category,
                               boolean sellToMaster, boolean buyFromMaster, long referenceValue,
                               long targetDemand, long targetSupply, long recoveryPerHour,
                               int minimumPriceBps, int maximumPriceBps, int importSpreadBps,
                               long minimumOffer, long maximumOffer, long deadlineTicks,
                               int bondBps) {
    public MarketDefinition {
        if(id==null||selector==null||selector.isBlank()||referenceValue<=0||targetDemand<0||targetSupply<0
                ||recoveryPerHour<0||minimumPriceBps<=0||maximumPriceBps<minimumPriceBps
                ||importSpreadBps<0||minimumOffer<=0||maximumOffer<minimumOffer||deadlineTicks<0
                ||bondBps<0||bondBps>10_000) throw new IllegalArgumentException("Invalid market definition");
        category=category==null?"other":category;
    }
}
