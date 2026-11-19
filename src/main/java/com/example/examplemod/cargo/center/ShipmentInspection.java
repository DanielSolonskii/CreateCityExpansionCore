package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.CargoPointRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.Predicate;

/** Read model for the map. Observations are physical handoffs; a route describes a plan. */
public final class ShipmentInspection {
    private ShipmentInspection() {}

    public static boolean permits(ServerPlayer actor, CargoShipmentRegistry.Shipment shipment) {
        if(shipment==null) return false;
        var server=actor.getServer();
        UUID viewer=actor.getUUID();
        return actor.hasPermissions(2) || shipment.creator().equals(viewer) || shipment.owner().operates(server,viewer)
                || CargoWarehouseRegistry.get(server).find(shipment.sourceWarehouse())
                        .filter(w -> CargoWarehouseRegistry.access(server,w).owner().operates(server,viewer)).isPresent();
    }

    /** Resolve recorded coordinates against a currently authorized router or station. */
    public static CargoInfrastructure.Location location(ServerPlayer actor, CompoundTag observation, Predicate<UUID> visible) {
        if (!observation.contains("Location")) return null;
        var recorded=CargoInfrastructure.Location.load(observation.getCompound("Location"));
        var server=actor.getServer();
        if (observation.hasUUID("ObservedStation")) {
            UUID station=observation.getUUID("ObservedStation");
            UUID point=CargoLineRegistry.get(server).stations().get(station);
            if (point==null || !visible.test(point)) return null;
            var match=CargoRailConnections.station(server,station);
            return match!=null && recorded.dimension().equals(match.station().getBlockEntityDimension().location())
                    && recorded.pos().equals(match.station().getBlockEntityPos()) ? recorded : null;
        }
        if (!observation.hasUUID("Point")) return null;
        UUID id=observation.getUUID("Point");
        if (!visible.test(id)) return null;
        var point=CargoPointRegistry.find(server,id).orElse(null);
        return point!=null && point.routers().stream().anyMatch(r->r.dimension().equals(recorded.dimension())
                && r.pos().equals(recorded.pos())) ? recorded : null;
    }

    static boolean visibleLeg(CargoRoutePlanner.Leg leg, Set<UUID> points, Predicate<UUID> allowedLine) {
        return points.contains(leg.from()) && points.contains(leg.next()) && allowedLine.test(leg.line());
    }

    /** Highlight only the next planned leg following a contiguous set of actual handoff receipts. */
    static int nextLeg(int[] completed,int count) {
        if (count<1 || count>32) return -1;
        var seen=new BitSet(count);
        for(int h:completed) if(h>=0 && h<count) seen.set(h);
        int next=seen.nextClearBit(0);
        return next<count?next:-1;
    }

    /** Legacy history stores mutable train and point names as plain text without an access identity. */
    static String historyLabel(String event) {
        int space=event.indexOf(' ');
        if(space<0) return event;
        String kind=event.substring(0,space);
        return switch(kind) {
            case "IN_TRANSIT","UNLOADED","WAITING_POINT","WAITING_ROUTE","WAITING_TRAIN","READY","ARRIVED","ORDINARY" -> kind;
            default -> event;
        };
    }
    static String waitReason(CargoShipmentRegistry.Stage stage,String observation,String recorded) {
        if (stage==CargoShipmentRegistry.Stage.BLOCKED || stage==CargoShipmentRegistry.Stage.FAILED) {
            return switch(recorded) {
                case "charter_dispatch_blocked" -> "dispatch";
                case "Receiving warehouse ownership changed","Warehouse ownership changed before packaging" -> "ownership";
                case "Physical quantity mismatch","Unexpected parcel contents","Parcel contents changed","Unexpected first parcel observation" -> "parcel";
                case "Create request interrupted; no automatic retry" -> "interrupted";
                case "NO_VALID_NEXT_POINT" -> "route";
                case "DENIED" -> "denied";
                case "INSUFFICIENT_STOCK" -> "stock";
                case "CREATE_REJECTED","INVALID_REQUEST" -> "rejected";
                case "master_import_reconciliation_required","master_buffer_unavailable" -> "reconcile";
                default -> "recorded_block";
            };
        }
        if (stage==CargoShipmentRegistry.Stage.DELIVERED) return "received";
        if (stage==CargoShipmentRegistry.Stage.COMPLETED || stage==CargoShipmentRegistry.Stage.CANCELLED) return "finished";
        return switch(observation) {
            case "WAITING_POINT" -> "point";
            case "WAITING_ROUTE" -> "route";
            case "WAITING_TRAIN" -> "train";
            default -> "unreported";
        };
    }

    static long ageSeconds(long observed,long now) {
        return observed>0 && now>=observed ? (now-observed)/1000 : -1;
    }

    static CompoundTag describe(ServerPlayer actor,CargoShipmentRegistry.Shipment shipment,CompoundTag parcel,
                                Set<UUID> visible, long now) {
        var result=new CompoundTag();
        result.putInt("Version",1);
        result.putUUID("Shipment",shipment.id());
        if (parcel.hasUUID("Id")) result.putUUID("Parcel",parcel.getUUID("Id"));
        result.putInt("Quantity",shipment.quantity());
        result.putInt("Packaged",shipment.packaged());
        result.putInt("Delivered",shipment.delivered());
        result.putString("Status",shipment.stage().name());
        result.putString("Reason",waitReason(shipment.stage(),parcel.getString("Observation"),shipment.problem()));
        result.putString("Observation",parcel.getString("Observation"));
        result.putLong("Age",ageSeconds(parcel.getLong("ObservedAt"),now));
        String from=endpoint(actor,visible,shipment.sourceWarehouse(),shipment.source());
        String to=endpoint(actor,visible,shipment.destinationWarehouse(),shipment.finalPoint());
        result.putString("Sender",from); result.putString("Recipient",to);
        var rows=new ListTag();
        var legs=shipment.route();
        for(int i=0;i<Math.min(32,legs.size());i++) {
            var leg=legs.get(i);
            if (!visibleLeg(leg,visible,id->permitsLine(actor,id))) continue;
            var row=new CompoundTag();
            row.putString("From",name(actor,visible,leg.from()));
            row.putString("To",name(actor,visible,leg.next()));
            row.putInt("Units",shipment.inspectionUnits(i));
            rows.add(row);
        }
        result.put("Legs",rows);
        return result;
    }

    public static boolean mayShowProgress(ServerPlayer actor,CargoShipmentRegistry.Shipment shipment) {
        if (shipment.route().size()>32 || !CenterView.visiblePoint(actor,shipment.source())
                || !CenterView.visiblePoint(actor,shipment.finalPoint())) return false;
        for(var leg:shipment.route()) {
            if (!permitsLine(actor,leg.line()) || !CenterView.visiblePoint(actor,leg.from()) || !CenterView.visiblePoint(actor,leg.next())) return false;
        }
        return true;
    }

    static boolean permitsLine(ServerPlayer actor,UUID id) {
        var s=actor.getServer();
        var line=CargoLineRegistry.get(s).find(id).orElse(null);
        return line!=null && CargoOwners.permits(s,OwnerRef.player(actor.getUUID()),CargoLineRegistry.access(s,line));
    }
    private static String name(ServerPlayer actor,Set<UUID> visible,UUID id) {
        return visible.contains(id) ? CargoPointRegistry.find(actor.getServer(),id).map(CargoPointRegistry.Point::name).orElse("") : "";
    }
    private static String endpoint(ServerPlayer actor,Set<UUID> visible,UUID warehouse,UUID point) {
        String terminal=name(actor,visible,point);
        return CargoWarehouseRegistry.get(actor.getServer()).find(warehouse).filter(w->CargoWarehouseRegistry.permits(actor,w))
                .map(w->terminal.isBlank() || w.name().equals(terminal)? w.name() : w.name()+" / "+terminal).orElse(terminal);
    }
}
