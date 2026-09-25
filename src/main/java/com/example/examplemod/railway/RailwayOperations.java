package com.example.examplemod.railway;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class RailwayOperations {
    private RailwayOperations() {}

    public static boolean assign(ServerLevel level, RailwayManager manager, CityFleetTrain fleetTrain, RailLine line) {
        if (fleetTrain.cityId() == null || !fleetTrain.cityId().equals(line.cityId()) || fleetTrain.activeLineId() != null) return false;
        if (!routeAvailable(fleetTrain, line, manager)) return false;
        return compileAndAssign(level, manager, fleetTrain, line);
    }

    public static boolean restore(ServerLevel level, RailwayManager manager, CityFleetTrain fleetTrain) {
        RailLine line = fleetTrain.activeLineId() == null ? null : manager.line(fleetTrain.activeLineId());
        return line != null && compileAndAssign(level, manager, fleetTrain, line);
    }

    private static boolean compileAndAssign(ServerLevel level, RailwayManager manager,
                                            CityFleetTrain fleetTrain, RailLine line) {
        Train train = CreateRailwayAdapter.train(fleetTrain.createTrainId());
        if (train == null) return false;
        try {
            RailLineScheduleCompiler.CompiledSchedule compiled = RailLineScheduleCompiler.compile(line, manager, level.registryAccess());
            train.runtime.setSchedule(compiled.schedule(), true);
            fleetTrain.assign(line.lineId(), line.revision(), compiled.fingerprint());
            manager.markChanged(); return true;
        } catch (RailLineScheduleCompiler.CompileException exception) { return false; }
    }

    public static boolean unassign(RailwayManager manager, CityFleetTrain fleetTrain) {
        if (fleetTrain.activeLineId() == null) return false;
        Train train = CreateRailwayAdapter.train(fleetTrain.createTrainId());
        if (train != null && !fleetTrain.automationSuspended()
                && RailLineScheduleCompiler.fingerprint(train.runtime.getSchedule()).equals(fleetTrain.compiledScheduleFingerprint())) {
            train.runtime.discardSchedule();
        }
        fleetTrain.unassign(); manager.markChanged(); return true;
    }

    public static boolean routeAvailable(CityFleetTrain fleetTrain, RailLine line, RailwayManager manager) {
        Train train = CreateRailwayAdapter.train(fleetTrain.createTrainId());
        if (train == null || train.graph == null) return false;
        for (RailLineStop stop : line.stops()) {
            CityRailStation registration = manager.station(stop.stationRegistrationId());
            var station = registration == null ? null : CreateRailwayAdapter.station(registration.createStationId());
            if (station == null || train.navigation.findPathTo(station, Double.MAX_VALUE) == null) return false;
        }
        return true;
    }
}
