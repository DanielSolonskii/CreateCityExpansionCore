package com.example.examplemod.economy;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** A declared physical train trip for one industrial contract. */
public final class IndustrialShipment {
    private final UUID shipmentId;
    private final UUID contractId;
    private final UUID contractorCityId;
    private final UUID createdByPlayerId;
    private final UUID fleetTrainId;
    private final UUID destinationIntakeId;
    private final ResourceLocation expectedItem;
    private final long declaredAmount;
    private final long createdAt;
    private long verifiedDeliveredAmount;
    private long departedAt;
    private long arrivedAt;
    private long completedAt;
    private EconomyTypes.ShipmentStatus status;

    public IndustrialShipment(UUID shipmentId, UUID contractId, UUID contractorCityId,
                              UUID createdByPlayerId, UUID fleetTrainId, UUID destinationIntakeId,
                              ResourceLocation expectedItem, long declaredAmount, long createdAt) {
        this.shipmentId=Objects.requireNonNull(shipmentId);
        this.contractId=Objects.requireNonNull(contractId);
        this.contractorCityId=Objects.requireNonNull(contractorCityId);
        this.createdByPlayerId=Objects.requireNonNull(createdByPlayerId);
        this.fleetTrainId=Objects.requireNonNull(fleetTrainId);
        this.destinationIntakeId=Objects.requireNonNull(destinationIntakeId);
        this.expectedItem=Objects.requireNonNull(expectedItem);
        this.declaredAmount=EconomyMath.checkedPositive(declaredAmount,100_000_000L,"declaredAmount");
        this.createdAt=Math.max(0L,createdAt);
        this.status=EconomyTypes.ShipmentStatus.PREPARING;
    }

    public boolean depart(long now){if(status!=EconomyTypes.ShipmentStatus.PREPARING)return false;status=EconomyTypes.ShipmentStatus.IN_TRANSIT;departedAt=Math.max(0L,now);return true;}
    public long verify(ResourceLocation item,long amount,long now){
        if(!active()||!expectedItem.equals(item)||amount<=0L)return 0L;
        long accepted=Math.min(amount,remainingAmount());if(accepted<=0L)return 0L;
        if(arrivedAt==0L)arrivedAt=Math.max(0L,now);
        verifiedDeliveredAmount+=accepted;
        if(verifiedDeliveredAmount>=declaredAmount){verifiedDeliveredAmount=declaredAmount;status=EconomyTypes.ShipmentStatus.COMPLETED;completedAt=Math.max(0L,now);}
        else status=EconomyTypes.ShipmentStatus.UNLOADING;
        return accepted;
    }
    public boolean cancel(){if(!active())return false;status=EconomyTypes.ShipmentStatus.CANCELLED;return true;}
    public boolean fail(){if(!active())return false;status=EconomyTypes.ShipmentStatus.FAILED;return true;}
    public boolean active(){return status==EconomyTypes.ShipmentStatus.PREPARING||status==EconomyTypes.ShipmentStatus.IN_TRANSIT||status==EconomyTypes.ShipmentStatus.UNLOADING;}
    public long remainingAmount(){return declaredAmount-verifiedDeliveredAmount;}

    void restore(EconomyTypes.ShipmentStatus restoredStatus,long verified,long departed,long arrived,long completed){
        verifiedDeliveredAmount=Math.max(0L,Math.min(declaredAmount,verified));departedAt=Math.max(0L,departed);arrivedAt=Math.max(0L,arrived);completedAt=Math.max(0L,completed);
        status=restoredStatus==null?EconomyTypes.ShipmentStatus.CANCELLED:restoredStatus;
        if(status==EconomyTypes.ShipmentStatus.COMPLETED||verifiedDeliveredAmount>=declaredAmount){verifiedDeliveredAmount=declaredAmount;status=EconomyTypes.ShipmentStatus.COMPLETED;}
    }

    public UUID shipmentId(){return shipmentId;} public UUID contractId(){return contractId;}
    public UUID contractorCityId(){return contractorCityId;} public UUID createdByPlayerId(){return createdByPlayerId;}
    public UUID fleetTrainId(){return fleetTrainId;} public UUID destinationIntakeId(){return destinationIntakeId;}
    public ResourceLocation expectedItem(){return expectedItem;} public long declaredAmount(){return declaredAmount;}
    public long verifiedDeliveredAmount(){return verifiedDeliveredAmount;} public long createdAt(){return createdAt;}
    public long departedAt(){return departedAt;} public long arrivedAt(){return arrivedAt;} public long completedAt(){return completedAt;}
    public EconomyTypes.ShipmentStatus status(){return status;}
}
