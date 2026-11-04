package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Opt-in automatic routing supplements, rather than replaces, manual Router rules. */
public final class CargoNavigation {
    public record Dispatch(UUID train,OwnerRef carrier,OwnerRef customer,UUID line,UUID next) {}
    private CargoNavigation() {}
    public static Dispatch dispatch(MinecraftServer s,CargoRouterBlockEntity router,CargoAddress address){
        if(!router.automaticRouting()||address==null)return null;var point=router.pointId().orElse(null);if(point==null||point.equals(address.finalPoint()))return null;
        var shipment=address.shipment()==null?null:CargoShipmentRegistry.get(s).find(address.shipment()).orElse(null);
        OwnerRef customer=shipment==null?router.logisticsOwner():shipment.owner();CargoRoutePlanner.Leg leg=null;
        if(shipment!=null){if(shipment.stage()==CargoShipmentRegistry.Stage.FAILED||shipment.stage()==CargoShipmentRegistry.Stage.COMPLETED||shipment.stage()==CargoShipmentRegistry.Stage.CANCELLED||!shipment.finalPoint().equals(address.finalPoint()))return null;for(var candidate:shipment.route())if(candidate.from().equals(point)){leg=candidate;break;}}
        else {var route=CargoRoutePlanner.plan(s,customer,point,address.finalPoint(),CargoRoutePlanner.Preference.CHEAPEST);if(route.isPresent()&&!route.get().legs().isEmpty())leg=route.get().legs().getFirst();}
        if(leg==null)return null;var line=CargoLineRegistry.get(s).find(leg.line()).orElse(null);if(line==null)return null;
        if(shipment!=null){var order=com.example.examplemod.economy.EconomyManager.get(s.overworld()).cargo().order(shipment.id()).orElse(null);var planned=leg;
            if(order!=null&&order.fees().stream().filter(f->f.line().equals(planned.line())).anyMatch(f->!f.recipient().equals(CargoLineRegistry.access(s,line).owner())))return null;}
        UUID train=CargoLineRegistry.get(s).availableTrain(s,line,customer,point,leg.next());if(train==null)return null;
        return new Dispatch(train,CargoLineRegistry.access(s,line).owner(),customer,line.id(),leg.next());
    }
    public static boolean serviceAllowed(MinecraftServer s,UUID lineId,OwnerRef customer,OwnerRef carrier,UUID train){var line=CargoLineRegistry.get(s).find(lineId).orElse(null);return line!=null&&line.enabled()&&line.trains().contains(train)&&CargoLineRegistry.access(s,line).owner().equals(carrier)&&CargoOwners.permits(s,customer,CargoLineRegistry.access(s,line));}
}
