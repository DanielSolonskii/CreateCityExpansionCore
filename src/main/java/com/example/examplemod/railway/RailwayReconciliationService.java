package com.example.examplemod.railway;

import com.example.examplemod.CreateCityExpansionCore;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class RailwayReconciliationService {
    private static final int INTERVAL = 100;
    private static int ticks;
    private RailwayReconciliationService() {}

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (++ticks % INTERVAL != 0) return;
        reconcile(event.getServer());
    }

    public static void reconcile(MinecraftServer server) {
        ServerLevel level = server.overworld();
        RailwayManager manager = RailwayManager.get(level);
        boolean changed = false;
        Set<UUID> renamedStations = new HashSet<>();
        for (CityRailStation registration : manager.stationsForCityIdsAndPersonal()) {
            var station = CreateRailwayAdapter.station(registration.createStationId());
            if (station == null) continue;
            if (!registration.lastKnownName().equals(station.name)
                    || !registration.lastKnownDimension().equals(station.getBlockEntityDimension().location())
                    || !registration.lastKnownPos().equals(station.getBlockEntityPos())) {
                if (!registration.lastKnownName().equals(station.name)) {
                    renamedStations.add(registration.registrationId());
                }
                registration.updateSnapshot(station.name, station.getBlockEntityDimension().location(), station.getBlockEntityPos());
                changed = true;
            }
        }
        for (CityFleetTrain fleetTrain : manager.trainsForCityIdsAndPersonal()) {
            Train train = CreateRailwayAdapter.train(fleetTrain.createTrainId());
            if (train == null) continue;
            if (!fleetTrain.lastKnownName().equals(train.name.getString())) { fleetTrain.setLastKnownName(train.name.getString()); changed = true; }
            if (fleetTrain.activeLineId() == null || fleetTrain.automationSuspended()) continue;
            RailLine line = manager.line(fleetTrain.activeLineId());
            if (line == null) { fleetTrain.unassign(); changed = true; continue; }
            boolean renamedStop = line.stops().stream()
                    .anyMatch(stop -> renamedStations.contains(stop.stationRegistrationId()));
            if (fleetTrain.compiledLineRevision() != line.revision() || renamedStop) {
                if (RailwayOperations.restore(level, manager, fleetTrain)) {
                    changed = true;
                } else {
                    fleetTrain.setAutomationSuspended(true);
                    changed = true;
                }
                continue;
            }
            String actual = RailLineScheduleCompiler.fingerprint(train.runtime.getSchedule());
            if (!actual.equals(fleetTrain.compiledScheduleFingerprint())) {
                fleetTrain.setAutomationSuspended(true); changed = true;
            }
        }
        if (changed) manager.markChanged();
    }
}
