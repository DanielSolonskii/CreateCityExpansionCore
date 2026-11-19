package com.example.examplemod.passenger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Observes physical arrivals and departures in a directed repeating schedule, in server game ticks. */
public final class PassengerTimetable {
    public record Connection(int from, int to, long departure, long arrival, long fromVisit, long toVisit) {}
    static final int MAX_STOPS = 6;
    private static final int MAX_SEGMENT = 20 * 60 * 15;
    private final long[] dwell, travel;
    private int last = -1;
    private boolean parked;
    private long changed;
    private java.util.UUID run = java.util.UUID.randomUUID();
    private long visit;

    public PassengerTimetable(int stops) {
        if (stops < 2 || stops > MAX_STOPS) throw new IllegalArgumentException();
        dwell = new long[stops];
        travel = new long[stops];
    }

    public void reset() {
        if (last >= 0) run = java.util.UUID.randomUUID();
        visit = 0;
        Arrays.fill(dwell, 0);
        Arrays.fill(travel, 0);
        last = -1;
        parked = false;
    }

    /** -1 means in transit. -2 is a physical stop outside the registered direction. */
    public void observe(int station, long tick) {
        if (station < -1 || station >= dwell.length || tick < changed || tick - changed > MAX_SEGMENT) reset();
        if (station < -1 || station >= dwell.length) return;
        if (last < 0) {
            if (station >= 0) {
                last = station;
                parked = true;
                changed = tick;
                // First sighting may be halfway through a dwell. Measure from the next real arrival.
                dwell[last] = -1;
            }
            return;
        }
        if (parked && station == last || !parked && station == -1) return;
        long elapsed = tick - changed;
        int next = (last + 1) % dwell.length;
        if (elapsed <= 0 || parked && station != -1 || !parked && station != next) {
            reset();
            if (station >= 0) observe(station, tick);
            return;
        }
        if (parked) {
            dwell[last] = dwell[last] < 0 ? 0 : elapsed;
            parked = false;
        } else {
            travel[last] = elapsed;
            last = station;
            parked = true;
            visit++;
        }
        changed = tick;
    }

    public java.util.UUID run() { return run; }

    /** The native train can arrive between the five-tick samples. Only the expected next station can advance the ordinal. */
    long visitAt(int nativeIndex) {
        if (last < 0 || nativeIndex < -1) return -1;
        if (nativeIndex == -1 || parked && nativeIndex == last) return visit;
        return !parked && nativeIndex == (last + 1) % dwell.length ? visit + 1 : -1;
    }

    boolean calibrated() {
        for (int i = 0; i < dwell.length; i++) if (dwell[i] <= 0 || travel[i] <= 0) return false;
        return true;
    }

    public boolean measured(long now) {
        if (last < 0 || now < changed || !calibrated()) return false;
        long expected = parked ? dwell[last] : travel[last];
        return now - changed <= expected + Math.clamp(expected / 3, 20, 200);
    }

    /** Two future boardings per stop, each with all later stops reachable on that same train. */
    public List<Connection> departures(long now) {
        if (!measured(now)) return List.of();
        int size = dwell.length;
        int index = parked ? last : (last + 1) % size;
        long boardVisit = parked ? visit : visit + 1;
        long departure = parked ? changed + dwell[last] : changed + travel[last] + dwell[index];
        if (departure < now && parked) return List.of();
        var results = new ArrayList<Connection>(2 * size * (size - 1));
        for (int board = 0; board < size * 2; board++) {
            long time = departure;
            int stop = index;
            for (int step = 1; step < size; step++) {
                time += travel[stop];
                stop = (stop + 1) % size;
                if (departure >= now) results.add(new Connection(index, stop, departure, time, boardVisit + board, boardVisit + board + step));
                time += dwell[stop];
            }
            departure += travel[index];
            index = (index + 1) % size;
            departure += dwell[index];
        }
        return List.copyOf(results);
    }
}
