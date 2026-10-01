package com.example.examplemod.status;

import com.example.examplemod.railway.*;
import com.simibubi.create.content.trains.entity.Train;
import java.util.Comparator;

/** Pure mapping from authoritative railway state to shared UI semantics. */
public final class CcecStatusResolver {
    private CcecStatusResolver() {}
    public static CcecStatus railway(RailwayManager manager, CityFleetTrain fleet, Train train, String raw) {
        if(train==null||"missing".equals(raw)||"derailed".equals(raw)||"track_issue".equals(raw))return CcecStatus.of(CcecStatus.Category.WARNING,CcecStatus.Code.NETWORK_OFFLINE,CcecStatus.Action.FIX_PROBLEM);
        RailwayTripLedger active=manager.activeTrip(fleet.createTrainId());
        if(active!=null)return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.IN_TRANSIT,CcecStatus.Action.OPEN_FREIGHT_POINT);
        RailwayTripLedger latest=manager.tripLedgers().stream().filter(t->t.fleetTrainId().equals(fleet.fleetTrainId())).max(Comparator.comparingLong(t->Math.max(t.startedAt(),t.settledAt()))).orElse(null);
        if(latest!=null&&latest.status()==RailwayTripLedger.Status.ROUTE_INVALIDATED)return CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.ROUTE_INVALIDATED,CcecStatus.Action.SELECT_ROUTE);
        if(latest!=null&&latest.status()==RailwayTripLedger.Status.COMPLETED&&"at_station".equals(raw))return CcecStatus.of(CcecStatus.Category.SUCCESS,CcecStatus.Code.ARRIVED,CcecStatus.Action.NONE);
        if("cargo".equals(raw))return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.WAITING_FOR_CARGO,CcecStatus.Action.OPEN_FREIGHT_POINT);
        if(manager.selectedRoute(fleet.fleetTrainId())!=null)return CcecStatus.of(CcecStatus.Category.SUCCESS,CcecStatus.Code.READY,CcecStatus.Action.CONFIRM_DEPARTURE);
        if("in_transit".equals(raw))return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.IN_TRANSIT,CcecStatus.Action.OPEN_FREIGHT_POINT);
        return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.WAITING_FOR_ROUTE,CcecStatus.Action.SELECT_ROUTE);
    }
}
