package com.example.examplemod.railway;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.IdleCargoCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.condition.TimedWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class RailLineScheduleCompiler {
    private RailLineScheduleCompiler() {}

    public static CompiledSchedule compile(RailLine line, RailwayManager manager,
                                           HolderLookup.Provider registries) throws CompileException {
        List<ScheduleEntry> entries = new ArrayList<>();
        StringBuilder semantic = new StringBuilder("v1|cyclic=").append(line.cyclic());
        for (RailLineStop stop : line.stops()) {
            CityRailStation registration = manager.station(stop.stationRegistrationId());
            if (registration == null) throw new CompileException("missing registration " + stop.stationRegistrationId());
            var station = CreateRailwayAdapter.station(registration.createStationId());
            if (station == null) throw new CompileException("missing Create station " + registration.createStationId());
            if (!CreateRailwayAdapter.hasUniqueScheduleName(station.id, station.name)) {
                throw new CompileException("station name is ambiguous for Create schedule: " + station.name);
            }

            DestinationInstruction destination = new DestinationInstruction();
            CompoundTag destinationData = new CompoundTag();
            destinationData.putString("Text", station.name);
            destination.setData(registries, destinationData);

            ScheduleWaitCondition condition = switch (stop.mode()) {
                case CARGO_IDLE -> new IdleCargoCondition();
                case TIMED -> delay(registries, stop.waitSeconds(), TimedWaitCondition.TimeUnit.SECONDS);
                case IMMEDIATE -> delay(registries, 0, TimedWaitCondition.TimeUnit.TICKS);
            };
            entries.add(new ScheduleEntry(destination, List.of(List.of(condition))));
            semantic.append('|').append(escape(station.name)).append(':').append(stop.mode().name())
                    .append(':').append(stop.waitSeconds());
        }
        if (entries.size() < 2) throw new CompileException("line has fewer than two stops");
        Schedule schedule = new Schedule(entries, line.cyclic(), 0);
        return new CompiledSchedule(schedule, sha256(semantic.toString()));
    }

    public static String fingerprint(Schedule schedule) {
        if (schedule == null) return "";
        StringBuilder semantic = new StringBuilder("v1|cyclic=").append(schedule.cyclic);
        for (ScheduleEntry entry : schedule.entries) {
            if (!(entry.instruction instanceof DestinationInstruction destination)) return "foreign";
            semantic.append('|').append(escape(destination.getFilter())).append(':');
            if (entry.conditions.size() != 1 || entry.conditions.getFirst().size() != 1) return "foreign";
            ScheduleWaitCondition condition = entry.conditions.getFirst().getFirst();
            if (condition instanceof IdleCargoCondition) semantic.append(RailwayTypes.StopMode.CARGO_IDLE).append(":0");
            else if (condition instanceof ScheduledDelay delay) {
                int value = delay.getValue();
                if (value == 0) semantic.append(RailwayTypes.StopMode.IMMEDIATE).append(":0");
                else if (delay.getUnit() == TimedWaitCondition.TimeUnit.SECONDS)
                    semantic.append(RailwayTypes.StopMode.TIMED).append(':').append(value);
                else return "foreign";
            } else return "foreign";
        }
        return sha256(semantic.toString());
    }

    private static ScheduledDelay delay(HolderLookup.Provider registries, int value,
                                        TimedWaitCondition.TimeUnit unit) {
        ScheduledDelay delay = new ScheduledDelay();
        CompoundTag data = new CompoundTag(); data.putInt("Value", Math.max(0, value));
        data.putInt("TimeUnit", unit.ordinal()); delay.setData(registries, data); return delay;
    }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("|", "\\|").replace(":", "\\:"); }
    private static String sha256(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record CompiledSchedule(Schedule schedule, String fingerprint) {}
    public static final class CompileException extends Exception { public CompileException(String message) { super(message); } }
}
