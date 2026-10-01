package com.example.examplemod.economy;

import com.example.examplemod.railway.RailwayTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import java.util.UUID;

public final class FreightDispatchCheck {
    private static int checks;
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        require(FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.FREIGHT,true,true,false,false),"freight train eligible");
        require(FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.MIXED,true,true,false,false),"mixed train eligible");
        require(!FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.PASSENGER,true,true,false,false),"passenger excluded");
        require(!FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.FREIGHT,true,false,false,false),"train must be at source");
        require(!FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.FREIGHT,true,true,true,false),"active trip excluded");
        require(FreightDispatchPolicy.readiness(false,false,100,10,64,64)==FreightDispatchPolicy.Readiness.TRAIN_NOT_ASSIGNED,"train selection gate");
        require(FreightDispatchPolicy.readiness(true,false,100,10,64,64)==FreightDispatchPolicy.Readiness.ROUTE_NOT_SELECTED,"route selection / invalidation gate");
        require(FreightDispatchPolicy.readiness(true,true,9,10,64,64)==FreightDispatchPolicy.Readiness.INSUFFICIENT_FUNDS,"funds gate");
        require(FreightDispatchPolicy.readiness(true,true,100,10,63,64)==FreightDispatchPolicy.Readiness.WAITING_FOR_CARGO,"full physical load required");
        require(FreightDispatchPolicy.readiness(true,true,100,10,64,64)==FreightDispatchPolicy.Readiness.READY,"dispatch ready");
        UUID city=UUID.randomUUID(),subject=UUID.randomUUID(),source=UUID.randomUUID(),train=UUID.randomUUID(),selection=UUID.randomUUID();
        FreightAssignment assignment=new FreightAssignment(subject,source,new FreightPoint(UUID.randomUUID(),city,ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO,BlockPos.ZERO,"Destination",FreightPoint.Mode.RECEIVE));assignment.train=train;assignment.routeSelection=selection;assignment.running=true;assignment.dispatchError="insufficient_funds";
        CompoundTag saved=assignment.save();FreightAssignment restored=FreightAssignment.load(saved);
        require(train.equals(restored.train)&&selection.equals(restored.routeSelection)&&restored.running&&"insufficient_funds".equals(restored.dispatchError),"dispatch selection survives restart");
        System.out.println("PASS: "+checks+" freight route/train selection, readiness, funds, invalidation and restart checks");
    }
}
