package com.example.examplemod.economy;

import com.example.examplemod.railway.RailwayTypes;

/** Pure dispatch gates shared by runtime orchestration and headless checks. */
public final class FreightDispatchPolicy {
    public enum Readiness { TRAIN_NOT_ASSIGNED, ROUTE_NOT_SELECTED, INSUFFICIENT_FUNDS, WAITING_FOR_CARGO, READY }
    private FreightDispatchPolicy(){}
    public static boolean eligibleTrain(RailwayTypes.TrainType type,boolean ownedBySender,boolean atSource,boolean activeTrip,boolean reservedByOther){
        return (type==RailwayTypes.TrainType.FREIGHT||type==RailwayTypes.TrainType.MIXED)&&ownedBySender&&atSource&&!activeTrip&&!reservedByOther;
    }
    public static Readiness readiness(boolean trainAssigned,boolean routeCurrent,long availableFunds,long routeCost,long loaded,long remaining){
        if(!trainAssigned)return Readiness.TRAIN_NOT_ASSIGNED;if(!routeCurrent)return Readiness.ROUTE_NOT_SELECTED;
        if(availableFunds<routeCost)return Readiness.INSUFFICIENT_FUNDS;
        if(remaining<=0||loaded<=0)return Readiness.WAITING_FOR_CARGO;return Readiness.READY;
    }
}
