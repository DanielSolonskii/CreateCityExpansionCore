package com.example.examplemod.electrical.bridge;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.util.DataPacker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.BitSet;

public final class EeBridgeDevice extends SimpleElectricalDevice {
    private final InWorldNode negative;
    private final InWorldNode positive;
    private DcBridgeEntity pending;
    private boolean input;
    private double resistance;
    private double sourceVoltage;
    private double lastVoltage;
    private boolean reversePolarity;
    private double[] negativeSamples;
    private double[] positiveSamples;

    EeBridgeDevice(SimulatedDeviceType<EeBridgeDevice> type, Level level, BlockPos pos, DevicesSavedData data) {
        super(level, pos, data, type);
        negative = new InWorldNode(0, pos);
        positive = new InWorldNode(1, pos);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        pending = null;
        resistance = sourceVoltage = 0;
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof DcBridgeEntity bridge)
                || bridge.isTfmgSection() || !bridge.assembled() || !bridge.enabled() || bridge.tripped()) return;

        pending = bridge;
        input = bridge.eeToTfmg();
        if (bridge.acDetected()) return;
        if (input && bridge instanceof DcBridgeEntity.ActivePort port) {
            double request = bridge.requestedInput(port.tfmgDemandWatts());
            if (request > 0 && lastVoltage > 1) {
                resistance = Math.max(lastVoltage * lastVoltage / request, lastVoltage / PowerBridge.MAX_AMPS.get());
                bridges.builder(pos).resistor(0, 1, resistance);
            }
        } else if (!input) {
            double budget = bridge.availableWatts();
            int target = bridge.volts();
            if (budget > .001 && lastVoltage <= target + 1 && !reversePolarity) {
                sourceVoltage = target;
                resistance = Math.max(target / PowerBridge.MAX_AMPS.get(), target * (double) target / (4 * budget));
                bridges.builder(pos).voltageSourceWithResistance(0, 1, resistance, sourceVoltage);
            }
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        if (pending == null || pending.isRemoved() || !pending.enabled() || input != pending.eeToTfmg()) {
            lastVoltage = 0;
            reversePolarity = false;
            return;
        }

        negativeSamples = results.getVoltages(negative, negativeSamples);
        positiveSamples = results.getVoltages(positive, positiveSamples);
        double voltage = 0;
        double current = 0;
        double watts = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < positiveSamples.length; i++) {
            double v = positiveSamples[i] - negativeSamples[i];
            if (!Double.isFinite(v)) return;
            min = Math.min(min, v);
            max = Math.max(max, v);
            voltage += v;
            if (resistance <= 0) continue;
            double amps = input ? Math.abs(v) / resistance : (sourceVoltage - v) / resistance;
            current += Math.max(0, amps);
            watts += input ? Math.abs(v) * amps : Math.max(0, v * amps);
        }

        int samples = positiveSamples.length;
        voltage /= samples;
        current /= samples;
        watts /= samples;
        boolean ac = max - min > Math.max(1, Math.abs(voltage) * .02) || connectedAcSource(results.circuitBuilder);
        pending.setAcDetected(ac);
        lastVoltage = ac ? 0 : Math.abs(voltage);
        reversePolarity = voltage < -1;
        if (input) {
            double limit = Math.min(PowerBridge.MAX_WATTS.get(), lastVoltage * PowerBridge.MAX_AMPS.get());
            double accepted = ac ? 0 : Math.min(watts, limit);
            pending.recordInput(lastVoltage, lastVoltage == 0 ? 0 : accepted / lastVoltage, accepted);
        } else {
            // Charge every outward transfer to the reservoir even if an AC source was just connected.
            pending.recordOutput(Math.abs(voltage), current, watts);
            pending.observeEeLoad(sourceVoltage, voltage, current);
        }
    }

    private boolean connectedAcSource(CircuitBuilder circuit) {
        var alternatingNodes = new BitSet();
        for (var entry : circuit.microTickers.long2ObjectEntrySet()) {
            String type = entry.getValue().getClass().getName();
            if (type.endsWith("$ACSource") || type.endsWith("$PhaseWindingProperties")) {
                alternatingNodes.set(DataPacker.unpackFirstI(entry.getLongKey()));
                alternatingNodes.set(DataPacker.unpackSecondI(entry.getLongKey()));
            }
        }
        if (alternatingNodes.isEmpty()) return false;
        var visited = new BitSet();
        var queue = new ArrayDeque<Integer>();
        var a = circuit.getNode(negative);
        var b = circuit.getNode(positive);
        if (a != null) queue.add(a.ordinal);
        if (b != null) queue.add(b.ordinal);
        while (!queue.isEmpty()) {
            int ordinal = queue.removeFirst();
            if (visited.get(ordinal)) continue;
            if (alternatingNodes.get(ordinal)) return true;
            visited.set(ordinal);
            var node = circuit.getNode(ordinal);
            for (int next : node.adjacency.keySet()) {
                if (!visited.get(next)) queue.add(next);
            }
            for (int next : node.invisibleAdjacency) {
                if (!visited.get(next)) queue.add(next);
            }
        }
        return false;
    }
}
