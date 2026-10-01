package com.example.examplemod.railway;

import com.example.examplemod.economy.FreightDispatchPolicy;
import com.example.examplemod.status.CcecStatus;
import com.example.examplemod.status.CcecStatusResolver;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Headless contract for the Train Management pass: a Create train becomes visible to the rest of
 * the stack only after registration, registration is idempotent and restart-safe, only the
 * registrant can manage it, and the status vocabulary the management UI renders is exactly what
 * the server-authoritative model produces.
 */
public final class TrainManagementCheck {
    private static int checks;
    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    private static RailwayManager reload(CompoundTag saved) throws Exception {
        var load = RailwayManager.class.getDeclaredMethod(
                "load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        return (RailwayManager) load.invoke(null, saved, null);
    }

    private static RailwayRouteOption option() {
        return new RailwayRouteOption(UUID.randomUUID(), RailwayRouteStrategy.CHEAPEST,
                UUID.randomUUID(), UUID.randomUUID(), false, List.of(), 12.5, 40L, 200L,
                List.of(), 1L);
    }

    public static void main(String[] args) throws Exception {
        UUID registrant = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID city = UUID.randomUUID();
        UUID createId = UUID.randomUUID();
        RailwayManager manager = new RailwayManager();

        // --- TYPE_NOT_SET is not a state this model can reach: a type is mandatory and defaulted.
        Set<RailwayTypes.TrainType> types = EnumSet.allOf(RailwayTypes.TrainType.class);
        require(types.contains(RailwayTypes.TrainType.FREIGHT), "FREIGHT type exists");
        require(types.contains(RailwayTypes.TrainType.MIXED), "MIXED type exists");
        require(types.contains(RailwayTypes.TrainType.PASSENGER), "PASSENGER type exists");
        require(types.contains(RailwayTypes.TrainType.SERVICE), "SERVICE type exists");
        require(types.size() == 4, "no unexpected extra types in the cycle");

        // --- 1. A Create train with no registration is invisible to the fleet.
        require(manager.trainsForCity(city).isEmpty(), "no city trains before registration");
        require(manager.personalTrains(registrant).isEmpty(), "no personal trains before registration");
        require(manager.trainByCreateId(createId) == null, "unregistered Create train has no fleet entry");

        // --- 2. Registration creates exactly one managed train, visible to its registrant.
        CityFleetTrain registered = manager.registerTrain(createId, registrant, "Hopper");
        require(registered != null, "register returns a fleet train");
        require(manager.trainByCreateId(createId) == registered, "lookup by Create id finds the registration");
        require(registered.type() == RailwayTypes.TrainType.MIXED, "type is assigned immediately (defaults to MIXED)");
        require(registered.cityId() == null, "a fresh registration is personal, not city-owned");
        require(manager.personalTrains(registrant).contains(registered), "registrant sees their own train");
        require(manager.personalTrains(outsider).isEmpty(), "another player does not see it");
        require(manager.trainsForCity(city).isEmpty(), "it joins the city fleet only after attach");

        CityFleetTrain again = manager.registerTrain(createId, registrant, "Hopper Renamed");
        require(again == registered, "registering the same Create train twice is idempotent");
        require(again.lastKnownName().equals("Hopper"), "the second registration does not overwrite the name");

        // --- 3. Permissions: only the registrant may bind it to a city.
        require(!manager.attachTrainToCity(registered.fleetTrainId(), outsider, city),
                "an outsider cannot attach someone else's train");
        require(registered.cityId() == null, "a refused attach leaves the train personal");
        require(manager.attachTrainToCity(registered.fleetTrainId(), registrant, city),
                "the registrant attaches it to the pylon's city");
        require(city.equals(registered.cityId()), "the train is bound to the requesting city");
        require(manager.trainsForCity(city).contains(registered), "the city fleet now contains it");
        require(!manager.attachTrainToCity(registered.fleetTrainId(), registrant, city),
                "a second attach to a city is rejected (no silent re-homing)");

        // --- 4. Type can be set and changed by the owner.
        registered.setType(RailwayTypes.TrainType.FREIGHT);
        require(registered.type() == RailwayTypes.TrainType.FREIGHT, "type can be set to FREIGHT");
        registered.setType(RailwayTypes.TrainType.SERVICE);
        require(registered.type() == RailwayTypes.TrainType.SERVICE, "type can be changed again");
        registered.setType(RailwayTypes.TrainType.FREIGHT);
        require(registered.type() == RailwayTypes.TrainType.FREIGHT, "type can be changed back");

        // --- 5. Freight Dispatch sees the registered train only through the city fleet, and only
        //        for the two types it accepts.
        require(FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.FREIGHT, true, true, false, false),
                "dispatch accepts a registered FREIGHT train");
        require(FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.MIXED, true, true, false, false),
                "dispatch accepts a registered MIXED train");
        require(!FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.PASSENGER, true, true, false, false),
                "dispatch still rejects PASSENGER");
        require(!FreightDispatchPolicy.eligibleTrain(RailwayTypes.TrainType.SERVICE, true, true, false, false),
                "dispatch still rejects SERVICE");
        require(manager.trainsForCity(city).stream()
                        .anyMatch(t -> t.type() == RailwayTypes.TrainType.FREIGHT),
                "dispatch's city fleet lookup finds the registered FREIGHT train");
        require(manager.trainsForCity(city).size() == 1,
                "the fleet walk does not invent trains that were never registered");

        // --- 6. A registered train survives a restart: identity, type, city and registrant.
        CompoundTag saved = manager.save(new CompoundTag(), null);
        require(saved.getList("Trains", 10).size() == 1, "exactly one train is persisted");
        RailwayManager reloaded = reload(saved);
        CityFleetTrain survivor = reloaded.trainByCreateId(createId);
        require(survivor != null, "the registered train survives a restart");
        require(survivor.fleetTrainId().equals(registered.fleetTrainId()), "the fleet id is stable across restart");
        require(survivor.type() == RailwayTypes.TrainType.FREIGHT, "the chosen type survives a restart");
        require(city.equals(survivor.cityId()), "the city binding survives a restart");
        require(registrant.equals(survivor.registeredBy()), "the registrant survives a restart");
        require(reloaded.trainsForCity(city).contains(survivor), "the city fleet still exposes it after restart");
        require(reloaded.trainByCreateId(createId) == survivor, "registration stays unique across restart");

        // --- 7. Status vocabulary the management UI renders.
        CcecStatus unregistered = CcecStatusResolver.unregisteredTrain();
        require(unregistered.code() == CcecStatus.Code.TRAIN_UNREGISTERED, "unregistered train has its own code");
        require(unregistered.action() == CcecStatus.Action.REGISTER_TRAIN, "unregistered train offers REGISTER_TRAIN");
        require(unregistered.category() == CcecStatus.Category.ACTION_REQUIRED, "unregistered train asks for action");

        // No line yet -> the pre-existing "waiting for route" state, unchanged.
        CcecStatus waiting = CcecStatusResolver.railway(reloaded, survivor, "at_station", false, false);
        require(waiting.code() == CcecStatus.Code.WAITING_FOR_ROUTE, "a train without a line still waits for a route");
        require(waiting.action() == CcecStatus.Action.SELECT_ROUTE, "and still prompts to select a route");

        // Assigned to a line -> the more precise ASSIGNED state, same action.
        survivor.assign(UUID.randomUUID(), 1, "fingerprint");
        CcecStatus assigned = CcecStatusResolver.railway(reloaded, survivor, "at_station", false, false);
        require(assigned.code() == CcecStatus.Code.TRAIN_ASSIGNED, "a line-assigned train reports ASSIGNED");
        require(assigned.action() == CcecStatus.Action.SELECT_ROUTE, "ASSIGNED still prompts to select a route");

        // Carrying a shipment -> IN_USE.
        CcecStatus inUse = CcecStatusResolver.railway(reloaded, survivor, "at_station", true, false);
        require(inUse.code() == CcecStatus.Code.TRAIN_IN_USE, "an active shipment reports IN_USE");

        // On an active trip -> IN_TRANSIT wins over the shipment.
        reloaded.addTrip(new RailwayTripLedger(UUID.randomUUID(), createId, survivor.fleetTrainId(),
                RailwayOwnershipTypes.OwnerType.CITY, city, UUID.randomUUID(), UUID.randomUUID(),
                10.0, 0L, 1L, List.of()));
        CcecStatus inTransit = CcecStatusResolver.railway(reloaded, survivor, "in_transit", true, false);
        require(inTransit.code() == CcecStatus.Code.IN_TRANSIT, "an active trip reports IN_TRANSIT");
        require(reloaded.activeTrip(createId) != null, "the active trip is reachable by Create id");

        // Off-track -> warning, unchanged.
        CcecStatus broken = CcecStatusResolver.railway(reloaded, survivor, "derailed", false, false);
        require(broken.code() == CcecStatus.Code.NETWORK_OFFLINE, "a derailed train still warns");

        // --- 8. Route selection behaviour is unchanged: propose -> select -> consume -> cleared.
        UUID optionId = UUID.randomUUID();
        RailwayRouteOption chosen = option();
        reloaded.proposeRoutes(survivor.fleetTrainId(), List.of(
                new RailwayRouteOption(UUID.randomUUID(), RailwayRouteStrategy.FASTEST,
                        chosen.graphId(), chosen.destinationStationId(), false, List.of(), 9.0, 60L, 140L,
                        List.of(), 2L),
                chosen));
        require(reloaded.proposedRoutes(survivor.fleetTrainId()).size() == 2, "proposed routes are kept");
        require(!reloaded.selectRoute(survivor.fleetTrainId(), UUID.randomUUID(), 5L),
                "selecting an unknown route option is refused");
        require(reloaded.selectedRoute(survivor.fleetTrainId()) == null, "and selects nothing");
        require(reloaded.selectRoute(survivor.fleetTrainId(), chosen.id(), 5L), "selecting a proposed option works");
        require(reloaded.selectedRoute(survivor.fleetTrainId()) != null, "the selection is stored");
        reloaded.consumeSelectedRoute(survivor.fleetTrainId());
        require(reloaded.selectedRoute(survivor.fleetTrainId()) == null, "consuming clears the selection");
        require(reloaded.proposedRoutes(survivor.fleetTrainId()).isEmpty(), "and clears the proposal");
        require(reloaded.selectRoute(survivor.fleetTrainId(), chosen.id(), 6L) == false,
                "a consumed option can no longer be reselected");

        // --- 9. Unregistration is available and leaves nothing behind.
        reloaded.removeTrain(survivor.fleetTrainId());
        require(reloaded.trainByCreateId(createId) == null, "unregistering removes the fleet entry");
        require(reloaded.trainsForCity(city).isEmpty(), "and empties the city fleet");
        require(reloaded.train(survivor.fleetTrainId()) == null, "and drops the fleet id");
        require(reloaded.save(new CompoundTag(), null).getList("Trains", 10).isEmpty(),
                "nothing is persisted after unregistration");

        System.out.println("PASS: " + checks + " train management registration, permission, dispatch and status checks");
    }
}
