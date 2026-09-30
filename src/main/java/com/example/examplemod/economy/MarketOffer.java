package com.example.examplemod.economy;

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public final class MarketOffer {
    private final UUID id;
    private final ResourceLocation definitionId;
    private final ResourceLocation itemId;
    private final EconomyTypes.MarketDirection direction;
    private final long quantity;
    private final long unitPrice;
    private final long createdAt;
    private final long expiresAt;
    private long bond;
    private long remaining;
    private long acceptedQuantity;
    private long dispatchedAmount;
    private UUID acceptedCity;
    private UUID acceptedBy;
    private EconomyTypes.MarketOfferStatus status;

    public MarketOffer(UUID id, ResourceLocation definitionId, ResourceLocation itemId,
                       EconomyTypes.MarketDirection direction, long quantity, long unitPrice,
                       long createdAt, long expiresAt, long bond) {
        this.id=id;this.definitionId=definitionId;this.itemId=itemId;this.direction=direction;
        this.quantity=quantity;this.remaining=quantity;this.unitPrice=unitPrice;
        this.createdAt=createdAt;this.expiresAt=expiresAt;this.bond=bond;
        this.status=EconomyTypes.MarketOfferStatus.OPEN;
    }

    public boolean reserve(UUID city, UUID player, long amount, long reservedBond) {
        if(status!=EconomyTypes.MarketOfferStatus.OPEN||city==null||player==null||amount<=0||amount>remaining)return false;
        acceptedCity=city;acceptedBy=player;remaining=amount;acceptedQuantity=amount;dispatchedAmount=0L;
        bond=Math.max(0,reservedBond);status=EconomyTypes.MarketOfferStatus.RESERVED;return true;
    }

    /** Existing export-side physical completion path. */
    public long applyPhysical(long amount) {
        if((status!=EconomyTypes.MarketOfferStatus.RESERVED&&status!=EconomyTypes.MarketOfferStatus.DISPATCHED
                &&status!=EconomyTypes.MarketOfferStatus.IN_PROGRESS)||amount<=0)return 0;
        long used=Math.min(amount,remaining);remaining-=used;
        status=remaining==0?EconomyTypes.MarketOfferStatus.COMPLETED:EconomyTypes.MarketOfferStatus.IN_PROGRESS;
        return used;
    }

    public long availableToDispatch() {
        if(direction!=EconomyTypes.MarketDirection.BUY_FROM_MASTER||status.terminal())return 0L;
        return Math.max(0L,acceptedQuantity-dispatchedAmount);
    }

    public boolean markDispatched(long amount) {
        if(amount<=0L||amount>availableToDispatch())return false;
        dispatchedAmount=Math.addExact(dispatchedAmount,amount);status=EconomyTypes.MarketOfferStatus.DISPATCHED;return true;
    }

    public boolean receiveImport(long amount) {
        if(direction!=EconomyTypes.MarketDirection.BUY_FROM_MASTER||amount<=0L
                ||(status!=EconomyTypes.MarketOfferStatus.DISPATCHED&&status!=EconomyTypes.MarketOfferStatus.IN_PROGRESS))return false;
        long received=Math.max(0L,acceptedQuantity-remaining),inFlight=Math.max(0L,dispatchedAmount-received);
        if(amount>inFlight||amount>remaining)return false;
        remaining-=amount;status=remaining==0L?EconomyTypes.MarketOfferStatus.COMPLETED:EconomyTypes.MarketOfferStatus.IN_PROGRESS;
        return true;
    }

    void restore(long remaining,UUID city,UUID player,EconomyTypes.MarketOfferStatus status){
        restore(remaining,city,player,status,status==EconomyTypes.MarketOfferStatus.OPEN?0L:remaining,0L);
    }
    void restore(long remaining,UUID city,UUID player,EconomyTypes.MarketOfferStatus status,long acceptedQuantity,long dispatchedAmount){
        this.remaining=Math.max(0,Math.min(quantity,remaining));this.acceptedCity=city;this.acceptedBy=player;this.status=status;
        this.acceptedQuantity=Math.max(this.remaining,Math.min(quantity,acceptedQuantity));
        this.dispatchedAmount=Math.max(0L,Math.min(this.acceptedQuantity,dispatchedAmount));
    }

    public UUID id(){return id;} public ResourceLocation definitionId(){return definitionId;}
    public ResourceLocation itemId(){return itemId;} public EconomyTypes.MarketDirection direction(){return direction;}
    public long quantity(){return quantity;} public long remaining(){return remaining;}
    public long acceptedQuantity(){return acceptedQuantity;} public long dispatchedAmount(){return dispatchedAmount;}
    public long unitPrice(){return unitPrice;} public long createdAt(){return createdAt;} public long expiresAt(){return expiresAt;}
    public long bond(){return bond;} public UUID acceptedCity(){return acceptedCity;} public UUID acceptedBy(){return acceptedBy;}
    public EconomyTypes.MarketOfferStatus status(){return status;}
}
