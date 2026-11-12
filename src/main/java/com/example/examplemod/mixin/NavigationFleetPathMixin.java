package com.example.examplemod.mixin;
import com.example.examplemod.cargo.center.PublicFleetRegistry;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;

/** All native replanning for an opted-in live job remains within its approved physical corridor. */
@Mixin(value=Navigation.class,remap=false)
public abstract class NavigationFleetPathMixin {
    @Shadow(remap=false) public Train train;
    @Inject(method="findPathTo(Ljava/util/ArrayList;D)Lcom/simibubi/create/content/trains/graph/DiscoveredPath;",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void ccec$approvedPath(ArrayList<GlobalStation> destinations,double maxCost,CallbackInfoReturnable<DiscoveredPath> ci){var s=ServerLifecycleHooks.getCurrentServer();if(s!=null&&s.isSameThread()&&PublicFleetRegistry.get(s).hasJob(train.id))ci.setReturnValue(PublicFleetRegistry.get(s).navigation(s,train));}
}
