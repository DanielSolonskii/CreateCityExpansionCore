package com.example.examplemod.electrical.bridge;

import com.drmangotea.tfmg.content.electricity.base.ElectricBlockValues;
import com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Adapter for TFMG 1.2.0. A network is settled once after its loaded block entities tick. */
public final class TfmgBridgePort extends DcBridgeEntity implements IElectric, DcBridgeEntity.ActivePort {
    private static final Map<ServerLevel, List<TfmgBridgePort>> TICKED = new IdentityHashMap<>();

    private final ElectricBlockValues data;
    private boolean joined;
    private int advertisedWatts;
    private float inputResistance;
    private double inputRequest;
    private double demandWatts;
    private TfmgTerminal terminal;
    public TfmgTerminal terminal() {
        if (terminal==null) terminal=new TfmgTerminal(this);
        terminal.setLevel(level);
        return terminal;
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(isTfmgSection()?64:0);
    }
    @Override public void onConnected() {
        if (isTfmgSection()) terminal().onConnected();
    }
    @Override protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag,registries,clientPacket);
        if (!isTfmgSection()) return;
        var list=new net.minecraft.nbt.ListTag();
        for(var connection:terminal().connections) list.add(connection.saveConnection());
        tag.put("TerminalCables",list);
    }
    @Override protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag,registries,clientPacket);
        if (!isTfmgSection()) return;
        var endpoint=terminal();endpoint.connections.clear();
        var list=tag.getList("TerminalCables",10);
        for(int i=0;i<list.size();i++) endpoint.connections.add(com.drmangotea.tfmg.content.electricity.connection.cables.CableConnection.loadConnection(list.getCompound(i)));
    }

    TfmgBridgePort(BlockPos pos, BlockState state) {
        super(pos, state);
        data = new ElectricBlockValues(pos.asLong());
    }

    static void registerEvents() {
        DcBridgeEntity.useNativeFactory(TfmgBridgePort::new);
        NeoForge.EVENT_BUS.addListener(TfmgBridgePort::finishLevelTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server)) return;
        if (!isTfmgSection() || !assembled()) { disconnect(); return; }
        if (terminal().removeWiresNextTick) {
            terminal().removeConnection();terminal().removeWiresNextTick=false;
        }
        if (!joined) {
            joined = true;
            data.destroyed = false;
            onPlaced();
        }
        tickElectricity();
        TICKED.computeIfAbsent(server, world -> new ArrayList<>()).add(this);
    }

    private static void finishLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel world)) return;
        var ports = TICKED.remove(world);
        if (ports == null) return;
        var networks = new IdentityHashMap<ElectricalNetwork, List<TfmgBridgePort>>();
        for (TfmgBridgePort port : ports) {
            if (!port.isRemoved() && world.isLoaded(port.worldPosition)) {
                networks.computeIfAbsent(port.getOrCreateElectricNetwork(), network -> new ArrayList<>()).add(port);
            }
        }
        networks.forEach(TfmgBridgePort::settle);
    }

    private static void settle(ElectricalNetwork network, List<TfmgBridgePort> ports) {
        int nativeVoltage = 0;
        double nativeWatts = 0;
        double conductance = 0;
        for (IElectric member : network.getMembers()) {
            if (member instanceof TfmgBridgePort) continue;
            int sourceVoltage = member.voltageGeneration();
            nativeVoltage = Math.max(nativeVoltage, sourceVoltage);
            if (sourceVoltage > 0) nativeWatts += Math.max(0, member.powerGeneration());
            float r = member.resistance();
            if (Float.isFinite(r) && r > 0) conductance += 1.0 / r;
        }

        int target = nativeVoltage;
        for (TfmgBridgePort port : ports) {
            if (port.candidateWatts() > 0) target = Math.max(target, port.volts());
        }

        double nativeDemand = conductance * target * target;
        int importers = 0;
        double candidateCapacity = nativeWatts;
        for (TfmgBridgePort port : ports) {
            if (port.importing()) importers++;
            if (port.volts() == target) candidateCapacity += port.candidateWatts();
        }
        double spare = Math.max(0, nativeWatts - conductance * nativeVoltage * nativeVoltage);
        for (TfmgBridgePort port : ports) {
            port.advertisedWatts = 0;
            port.inputResistance = 0;
            port.inputRequest = 0;
            port.demandWatts = 0;
            if (port.candidateWatts() > 0 && port.volts() == target) {
                port.advertisedWatts = (int) port.availableWatts();
                port.demandWatts = candidateCapacity == 0 ? 0
                        : nativeDemand * port.candidateWatts() / candidateCapacity;
            } else if (port.importing() && nativeVoltage > 0) {
                port.inputRequest = Math.min(spare / Math.max(1, importers), port.requestedInput(port.eeDemandWatts()));
                port.inputRequest = Math.min(port.inputRequest, nativeVoltage * PowerBridge.MAX_AMPS.get());
                if (port.inputRequest >= 1) {
                    port.inputResistance = (float) (nativeVoltage * (double) nativeVoltage / port.inputRequest);
                }
            }
        }

        network.updateNetwork();
        double totalDemand = 0;
        for (IElectric member : network.getMembers()) {
            totalDemand += Math.max(0, member.getData().getVoltage() * (double) member.getCurrent());
        }
        for (TfmgBridgePort port : ports) {
            int voltage = port.data.getVoltage();
            if (port.exporting()) {
                int totalGeneration = port.data.networkPowerGeneration;
                double supplied = totalGeneration <= 0 ? 0
                        : Math.min(port.advertisedWatts, totalDemand * port.advertisedWatts / totalGeneration);
                port.recordOutput(voltage, voltage <= 0 ? 0 : supplied / voltage, supplied);
                port.observeShort(target > 0 && voltage <= 1 && nativeDemand > port.outputLimitWatts(target) * 2);
            } else if (port.importing()) {
                int drawn = port.data.notEnoughPower ? 0 : port.getPowerUsage();
                port.recordInput(voltage, voltage <= 0 ? 0 : drawn / (double) voltage, drawn);
            }
        }
    }

    /** TFMG normally raises a shortage flag but leaves voltage on passive loads. Limit bridge-fed buses before that voltage is applied. */
    public static int limitVoltage(ElectricalNetwork network, int voltage) {
        double conductance = 0;
        if (network.getMembers().stream().noneMatch(member -> member instanceof TfmgBridgePort port && port.powerGeneration() > 0)) {
            return voltage;
        }
        double capacity = networkCapacity(network);
        for (IElectric member : network.getMembers()) {
            float r = member.resistance();
            if (Float.isFinite(r) && r > 0) conductance += 1.0 / r;
        }
        if (conductance == 0) return voltage;
        double limit = Math.sqrt(capacity / conductance);
        for (IElectric member : network.getMembers()) {
            if (member instanceof TfmgBridgePort port && port.powerGeneration() > 0) {
                limit = Math.min(limit, PowerBridge.MAX_AMPS.get() * capacity / (conductance * port.powerGeneration()));
            }
        }
        return Math.min(voltage, (int) Math.floor(limit));
    }

    public static int limitCapacity(ElectricalNetwork network, int original) {
        return network.getMembers().stream().anyMatch(member -> member instanceof TfmgBridgePort)
                ? networkCapacity(network) : original;
    }

    private static int networkCapacity(ElectricalNetwork network) {
        long capacity = 0;
        for (IElectric member : network.getMembers()) {
            if (member.voltageGeneration() > 0) capacity += Math.max(0, member.powerGeneration());
        }
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    private boolean exporting() { return enabled() && !tripped() && !acDetected() && eeToTfmg(); }
    private boolean importing() { return enabled() && !tripped() && !acDetected() && !eeToTfmg(); }

    private double candidateWatts() {
        if (!exporting() || inputVoltage() <= 1) return 0;
        double inputLimit = Math.min(PowerBridge.MAX_WATTS.get(), inputVoltage() * PowerBridge.MAX_AMPS.get());
        return Math.min(outputLimitWatts(volts()), inputLimit * PowerBridge.EFFICIENCY.get());
    }

    @Override
    public double tfmgDemandWatts() {
        if (!isTfmgSection() && assembled()
                && level.getBlockEntity(DcBridgeBlock.partner(worldPosition, getBlockState())) instanceof TfmgBridgePort port)
            return port.demandWatts;
        return demandWatts;
    }
    @Override
    public ElectricBlockValues getData() { return data; }
    @Override
    public LevelAccessor getLevelAccessor() { return level; }
    @Override
    public long getPos() { return worldPosition.asLong(); }
    @Override
    public void sendStuff() { sendData(); }
    @Override
    public boolean hasElectricitySlot(Direction side) {
        return isTfmgSection() && side == Direction.UP;
    }

    // Only the small section joins TFMG; all energy/configuration belongs to the EE controller.
    @Override boolean enabled() { return isTfmgSection() ? controller() != this && controller().enabled() : super.enabled(); }
    @Override boolean tripped() { return controller() != this ? controller().tripped() : super.tripped(); }
    @Override boolean acDetected() { return controller() != this ? controller().acDetected() : super.acDetected(); }
    @Override public boolean eeToTfmg() { return controller() != this ? controller().eeToTfmg() : super.eeToTfmg(); }
    @Override public int volts() { return controller() != this ? controller().volts() : super.volts(); }
    @Override double inputVoltage() { return controller() != this ? controller().inputVoltage() : super.inputVoltage(); }
    @Override double availableWatts() { return controller() != this ? controller().availableWatts() : super.availableWatts(); }
    @Override double requestedInput(double watts) { return controller() != this ? controller().requestedInput(watts) : super.requestedInput(watts); }
    @Override double eeDemandWatts() { return controller() != this ? controller().eeDemandWatts() : super.eeDemandWatts(); }
    @Override void recordInput(double u, double i, double watts) {
        if (controller() != this) controller().recordInput(u, i, watts); else super.recordInput(u, i, watts);
    }
    @Override void recordOutput(double u, double i, double watts) {
        if (controller() != this) controller().recordOutput(u, i, watts); else super.recordOutput(u, i, watts);
    }
    @Override void observeShort(boolean shorted) {
        if (controller() != this) controller().observeShort(shorted); else super.observeShort(shorted);
    }
    @Override
    public int getMaxVoltage() { return 0; }
    @Override
    public float resistance() { return importing() ? inputResistance : 0; }
    @Override
    public float getCurrent() {
        int voltage = data.getVoltage();
        if (resistance() <= 0 || voltage <= 0) return 0;
        return (float) Math.min(voltage / (double) resistance(), Math.min(PowerBridge.MAX_AMPS.get(), inputRequest / voltage));
    }
    @Override
    public int voltageGeneration() { return powerGeneration() > 0 ? volts() : 0; }
    @Override
    public int powerGeneration() { return exporting() ? advertisedWatts : 0; }

    @Override
    public void remove() {
        if (isTfmgSection() && level!=null) terminal().notifyRemoval();
        disconnect();
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        disconnect();
        super.onChunkUnloaded();
    }

    private void disconnect() {
        if (joined && level instanceof ServerLevel) {
            joined = false;
            advertisedWatts = 0;
            onRemoved();
        }
    }
}
