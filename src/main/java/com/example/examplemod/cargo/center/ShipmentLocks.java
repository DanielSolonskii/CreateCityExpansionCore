package com.example.examplemod.cargo.center;

import java.util.*;

/** Administrative metadata changes must not orphan physical parcels or held settlement. */
public final class ShipmentLocks {
    private ShipmentLocks() {}
    public record References(UUID sourceWarehouse,UUID destinationWarehouse,UUID source,UUID destination,List<CargoRoutePlanner.Leg> route){
        public References{route=List.copyOf(route);}
    }
    public static boolean active(CargoShipmentRegistry.Stage stage){return stage!=CargoShipmentRegistry.Stage.COMPLETED&&stage!=CargoShipmentRegistry.Stage.CANCELLED;}
    public static boolean blocks(CargoShipmentRegistry.Stage stage,String kind,UUID id,References refs){
        if(!active(stage)||id==null)return false;
        return switch(kind){
            case "warehouse"->id.equals(refs.sourceWarehouse())||id.equals(refs.destinationWarehouse());
            case "point"->id.equals(refs.source())||id.equals(refs.destination())||refs.route().stream().anyMatch(leg->id.equals(leg.from())||id.equals(leg.next()));
            case "line"->refs.route().stream().anyMatch(leg->id.equals(leg.line()));
            case "train"->refs.route().stream().anyMatch(leg->id.equals(leg.train()));
            default->false;
        };
    }
}
