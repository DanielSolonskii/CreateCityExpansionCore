package com.example.examplemod.mixin;
import com.example.examplemod.cargo.center.RailTravelObserver;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=TravellingPoint.class,remap=false)
public abstract class TravellingPointRailTollMixin {
    @Inject(method="edgeTraversedFrom",at=@At("RETURN"),remap=false,require=1)
    private void ccec$actualEdgeInterval(TrackGraph graph,boolean forward,TravellingPoint.IEdgePointListener points,TravellingPoint.ITurnListener turns,double previous,double distance,CallbackInfoReturnable<Double> ci){
        RailTravelObserver.moved((TravellingPoint)(Object)this,graph,previous,ci.getReturnValue());
    }
}
