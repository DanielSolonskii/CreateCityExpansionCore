package com.example.examplemod.status;

import com.example.examplemod.railway.*;
import com.simibubi.create.content.trains.entity.Train;
import java.util.Comparator;

/** Pure mapping from authoritative railway state to shared UI semantics. */
public final class CcecStatusResolver {
    private CcecStatusResolver() {}

    /** A Create train that has no CCEC registration: nothing else in the stack can see it yet. */
    public static CcecStatus unregisteredTrain() {
        return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.TRAIN_UNREGISTERED,CcecStatus.Action.REGISTER_TRAIN);
    }

    /** Shipment-free overload kept for callers that only know the railway side of the world. */
    public static CcecStatus railway(RailwayManager manager, CityFleetTrain fleet, Train train, String raw) {
        return railway(manager, fleet, train, raw, false);
    }

    /**
     * @param activeShipment the train currently carries an {@code IndustrialShipment} that has not
     *                       settled yet. An active trip wins over a shipment because it already
     *                       tells the player the train is somewhere on a route.
     */
    public static CcecStatus railway(RailwayManager manager, CityFleetTrain fleet, Train train, String raw,
                                     boolean activeShipment) {
        return railway(manager, fleet, train == null ? "missing" : raw, activeShipment, false);
    }

    /**
     * Trainless entry point so headless checks can drive the exact ordering the live resolver uses
     * without constructing a Create {@code Train}.
     *
     * @param trackBroken Create reports the train is missing, derailed or off-track.
     */
    public static CcecStatus railway(RailwayManager manager, CityFleetTrain fleet, String raw,
                                     boolean activeShipment, boolean trackBroken) {
        if(trackBroken||"missing".equals(raw)||"derailed".equals(raw)||"track_issue".equals(raw))return CcecStatus.of(CcecStatus.Category.WARNING,CcecStatus.Code.NETWORK_OFFLINE,CcecStatus.Action.FIX_PROBLEM);
        RailwayTripLedger active=manager.activeTrip(fleet.createTrainId());
        if(active!=null)return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.IN_TRANSIT,CcecStatus.Action.OPEN_FREIGHT_POINT);
        RailwayTripLedger latest=manager.tripLedgers().stream().filter(t->t.fleetTrainId().equals(fleet.fleetTrainId())).max(Comparator.comparingLong(t->Math.max(t.startedAt(),t.settledAt()))).orElse(null);
        if(latest!=null&&latest.status()==RailwayTripLedger.Status.ROUTE_INVALIDATED)return CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.ROUTE_INVALIDATED,CcecStatus.Action.SELECT_ROUTE);
        if(latest!=null&&latest.status()==RailwayTripLedger.Status.COMPLETED&&"at_station".equals(raw))return CcecStatus.of(CcecStatus.Category.SUCCESS,CcecStatus.Code.ARRIVED,CcecStatus.Action.NONE);
        if("cargo".equals(raw))return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.WAITING_FOR_CARGO,CcecStatus.Action.OPEN_FREIGHT_POINT);
        if(manager.selectedRoute(fleet.fleetTrainId())!=null)return CcecStatus.of(CcecStatus.Category.SUCCESS,CcecStatus.Code.READY,CcecStatus.Action.CONFIRM_DEPARTURE);
        if(activeShipment)return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.TRAIN_IN_USE,CcecStatus.Action.OPEN_FREIGHT_POINT);
        if("in_transit".equals(raw))return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.IN_TRANSIT,CcecStatus.Action.OPEN_FREIGHT_POINT);
        // No route selected: name the actual next step instead of a generic "needs a route".
        if(fleet.activeLineId()!=null)return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.TRAIN_ASSIGNED,CcecStatus.Action.SELECT_ROUTE);
        return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.WAITING_FOR_ROUTE,CcecStatus.Action.SELECT_ROUTE);
    }
}
