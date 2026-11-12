package com.example.examplemod.mixin;
import com.example.examplemod.cargo.center.RailTravelObserver;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=Carriage.class,remap=false)
public abstract class CarriageRailTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),remap=false,require=1)
    private void ccec$beginMeter(Level level,TrackGraph graph,double distance,TravellingPoint front,TravellingPoint back,int type,CallbackInfoReturnable<Double> ci){RailTravelObserver.begin((Carriage)(Object)this,level);}
    @Inject(method="travel",at=@At("RETURN"),remap=false,require=1)
    private void ccec$endMeter(Level level,TrackGraph graph,double distance,TravellingPoint front,TravellingPoint back,int type,CallbackInfoReturnable<Double> ci){RailTravelObserver.end();}
}
