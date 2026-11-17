package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MineControllerBlockEntity extends BlockEntity implements com.example.examplemod.machine.MachineDisplaySource {

    private com.example.examplemod.machine.MachineTelemetry telemetry = com.example.examplemod.machine.MachineTelemetry.EMPTY;
    private UUID placementCity;
    private boolean placementCityAssigned;
    private boolean redstoneInitialized;
    private boolean redstoneHigh;

    @Override public com.example.examplemod.machine.MachineTelemetry telemetry() { return telemetry; }
    @Override public Role displayRole() { return Role.CONTROLLER; }
    public void publishTelemetry(com.example.examplemod.machine.MachineTelemetry next) {
        if (next.equals(telemetry)) return;
        telemetry = next;
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    public void assignPlacementCity(UUID id) { placementCity = id; placementCityAssigned = true; setChanged(); }
    public boolean hasPlacementCity() { return placementCityAssigned; }
    public UUID placementCity() { return placementCity; }
    public void initializeRedstone(boolean high) { redstoneHigh = high; redstoneInitialized = true; }
    boolean consumeRedstoneEdge(boolean high) {
        if (!redstoneInitialized) { initializeRedstone(high); return false; }
        boolean changed = high != redstoneHigh; redstoneHigh = high; return changed;
    }

    public static final int STARTUP_DURATION_TICKS = 60;
    public static final int STOP_DURATION_TICKS = 40;

    private ResourceLocation activeDepositId;
    private ResourceLocation specializedResourceId;
    private ResourceLocation previewDepositId;
    private UUID creatorId;
    private String creatorName = "";
    private UUID cityId;
    private UUID projectId;
    private boolean commissioned;
    private IndustrialMineState runtimeState = IndustrialMineState.STOPPED;
    private MineElectricalState electricalState = MineElectricalState.NO_POWER;
    private boolean userRequestedStart;
    private int startupTicks;
    private int shutdownTicks;
    private double miningTicks;
    private MineOperationResult lastResult;
    private String statusMessage = "Шахта остановлена.";
    private boolean visualAssembled;
    private BlockPos visualPowerPort;
    private BlockPos visualGateway;
    private boolean visualClientLoaded;
    /** Client installs a listener; the dedicated server never links client classes. */
    public static java.util.function.Consumer<MineControllerBlockEntity> CLIENT_VISUAL_UPDATE = ignored -> {};
    private IndustrialMineState lastSyncedVisualState;
    private MineElectricalState lastSyncedElectricalState;
    private boolean lastSyncedAssembled;
    private long clientLastVisualParticleTick = Long.MIN_VALUE;
    private java.util.List<BlockPos> effectDrills = java.util.List.of();
    private java.util.List<BlockPos> effectVents = java.util.List.of();

    public java.util.List<BlockPos> effectDrills() { return effectDrills; }
    public java.util.List<BlockPos> effectVents() { return effectVents; }

    public void publishEffectParts(java.util.List<BlockPos> drills, java.util.List<BlockPos> vents) {
        if (effectDrills.equals(drills) && effectVents.equals(vents)) return;
        effectDrills = java.util.List.copyOf(drills);
        effectVents = java.util.List.copyOf(vents);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public MineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.MINE_CONTROLLER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  MineControllerBlockEntity controller) {
        if (controller.commissioned && controller.projectId != null) {
            com.example.examplemod.city.CityProject project =
                    com.example.examplemod.city.CityManager.get(level.getServer().overworld())
                            .getProject(controller.projectId);
            if (project == null
                    || !project.dimensionId().equals(level.dimension().location())
                    || !project.controllerPos().equals(pos)) {
                controller.decommission(controller.projectId);
            }
        }
        IndustrialMineService.serverTick(level, pos, controller);
    }

    public Optional<ResourceLocation> getActiveDepositId() {
        return Optional.ofNullable(activeDepositId);
    }

    public Optional<ResourceLocation> getDisplayDepositId() {
        return Optional.ofNullable(commissioned ? activeDepositId : previewDepositId);
    }

    public Optional<UUID> getCreatorId() {
        return Optional.ofNullable(creatorId);
    }

    public String getCreatorName() {
        return creatorName;
    }

    public Optional<ResourceLocation> getSpecializedResourceId() {
        return Optional.ofNullable(specializedResourceId);
    }

    public Optional<UUID> getCityId() {
        return Optional.ofNullable(cityId);
    }

    public Optional<UUID> getProjectId() {
        return Optional.ofNullable(projectId);
    }

    public boolean isCommissioned() {
        return commissioned;
    }

    public void setCreatorId(UUID creatorId) {
        if (this.creatorId == null && creatorId != null) {
            this.creatorId = creatorId;
            setChanged();
        }
    }

    public void setCreator(UUID creatorId, String creatorName) {
        if (this.creatorId == null && creatorId != null) {
            this.creatorId = creatorId;
            this.creatorName = creatorName == null ? "" : creatorName;
            setChanged();
        } else if (Objects.equals(this.creatorId, creatorId)
                && this.creatorName.isBlank()
                && creatorName != null && !creatorName.isBlank()) {
            this.creatorName = creatorName;
            setChanged();
        }
    }

    void setPreviewDepositId(ResourceLocation depositId) {
        if (!commissioned) {
            previewDepositId = depositId;
        }
    }

    boolean commission(
            UUID cityId,
            UUID projectId,
            ResourceLocation depositId,
            ResourceLocation resourceId
    ) {
        if (commissioned || cityId == null || projectId == null || depositId == null
                || resourceId == null
                || (activeDepositId != null && !activeDepositId.equals(depositId))) {
            return false;
        }
        this.cityId = cityId;
        this.projectId = projectId;
        this.activeDepositId = depositId;
        this.specializedResourceId = resourceId;
        this.previewDepositId = null;
        this.commissioned = true;
        this.miningTicks = 0;
        setChanged();
        return true;
    }

    public void decommission(UUID expectedProjectId) {
        if (commissioned && Objects.equals(projectId, expectedProjectId)) {
            commissioned = false;
            cityId = null;
            projectId = null;
            previewDepositId = activeDepositId;
            userRequestedStart = false;
            runtimeState = IndustrialMineState.STOPPED;
            statusMessage = "Проект шахты снят с эксплуатации; специализация сохранена.";
            setChanged();
        }
    }

    void setActiveDepositId(ResourceLocation depositId) {
        if (!Objects.equals(activeDepositId, depositId)) {
            activeDepositId = depositId;
            setChanged();
        }
    }

    public IndustrialMineState getRuntimeState() {
        return runtimeState;
    }

    public MineElectricalState getVisualElectricalState() {
        return electricalState;
    }

    public boolean isVisualAssembled() {
        return visualAssembled;
    }

    public boolean claimVisualParticleTick(long gameTime) {
        if (clientLastVisualParticleTick == gameTime) {
            return false;
        }
        clientLastVisualParticleTick = gameTime;
        return true;
    }

    public BlockPos getVisualPowerPort() { return visualPowerPort; }
    public BlockPos getVisualGateway() { return visualGateway; }
    public boolean isVisualClientLoaded() { return visualClientLoaded; }

    @Override
    public void onLoad() {
        super.onLoad();
        visualClientLoaded = true;
        notifyClientVisual();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        notifyClientVisual();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        visualClientLoaded = false;
        visualAssembled = false;
        notifyClientVisual();
    }

    private void notifyClientVisual() {
        if (level != null && level.isClientSide()) CLIENT_VISUAL_UPDATE.accept(this);
    }

    void syncVisualState(com.example.examplemod.multiblock.MineMultiblock.ValidationResult validation) {
        boolean assembled = validation.assembled();
        BlockPos power = assembled ? validation.powerPort() : null;
        BlockPos gateway = assembled ? validation.gateway() : null;
        boolean interfacesChanged = !Objects.equals(power, visualPowerPort)
                || !Objects.equals(gateway, visualGateway);
        visualPowerPort = power;
        visualGateway = gateway;
        visualAssembled = assembled;
        if (lastSyncedVisualState == runtimeState
                && lastSyncedElectricalState == electricalState
                && lastSyncedAssembled == assembled && !interfacesChanged) {
            return;
        }
        lastSyncedVisualState = runtimeState;
        lastSyncedElectricalState = electricalState;
        lastSyncedAssembled = assembled;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    void setRuntimeState(IndustrialMineState state, String message) {
        runtimeState = Objects.requireNonNull(state, "state");
        statusMessage = Objects.requireNonNull(message, "message");
    }

    MineElectricalState getElectricalState() {
        return electricalState;
    }

    void setElectricalState(MineElectricalState state) {
        electricalState = Objects.requireNonNull(state, "state");
    }

    boolean isUserRequestedStart() {
        return userRequestedStart;
    }

    void setUserRequestedStart(boolean requested) {
        if (userRequestedStart != requested) {
            userRequestedStart = requested;
            setChanged();
        }
    }

    int getStartupTicks() { return startupTicks; }
    void setStartupTicks(int ticks) {
        startupTicks = Math.max(0, Math.min(STARTUP_DURATION_TICKS, ticks));
    }

    int getShutdownTicks() { return shutdownTicks; }
    void setShutdownTicks(int ticks) {
        shutdownTicks = Math.max(0, Math.min(STOP_DURATION_TICKS, ticks));
    }

    double getMiningTicks() { return miningTicks; }
    void setMiningTicks(double ticks) {
        double clamped = validMiningTicks(ticks);
        if (miningTicks != clamped) {
            miningTicks = clamped;
            setChanged();
        }
    }

    private static double validMiningTicks(double ticks) {
        return Double.isFinite(ticks) ? Math.max(0.0, ticks) : 0.0;
    }

    void setLastResult(MineOperationResult result) {
        lastResult = result;
        statusMessage = result.message();
        if (result.activeDepositChanged()) {
            result.depositId().ifPresent(this::setActiveDepositId);
        }
    }

    public Optional<MineOperationResult> getLastResult() {
        return Optional.ofNullable(lastResult);
    }

    String getStatusMessage() { return statusMessage; }

    double machineProgress() {
        return switch (runtimeState) {
            case STARTING -> (double) startupTicks / STARTUP_DURATION_TICKS;
            case STOPPING -> (double) shutdownTicks / STOP_DURATION_TICKS;
            case RUNNING -> 1.0;
            default -> 0.0;
        };
    }

    double miningProgress(double cycleDurationTicks) {
        if (cycleDurationTicks <= 0) {
            return 0.0;
        }
        return Math.min(1.0, miningTicks / cycleDurationTicks);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("PlacementCityAssigned", placementCityAssigned);
        if (placementCity != null) tag.putUUID("PlacementCity", placementCity);
        if (activeDepositId != null) {
            tag.putString("ActiveDepositId", activeDepositId.toString());
        }
        if (creatorId != null) {
            tag.putUUID("Creator", creatorId);
        }
        if (!creatorName.isBlank()) {
            tag.putString("CreatorName", creatorName);
        }
        if (specializedResourceId != null) {
            tag.putString("SpecializedResource", specializedResourceId.toString());
        }
        if (cityId != null) {
            tag.putUUID("CityId", cityId);
        }
        if (projectId != null) {
            tag.putUUID("ProjectId", projectId);
        }
        tag.putBoolean("Commissioned", commissioned);
        tag.putBoolean("UserRequestedStart", userRequestedStart);
        tag.putDouble("MiningProgressTicks", miningTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        placementCityAssigned = tag.getBoolean("PlacementCityAssigned");
        placementCity = tag.hasUUID("PlacementCity") ? tag.getUUID("PlacementCity") : null;
        redstoneInitialized = false;
        telemetry = com.example.examplemod.machine.MachineTelemetry.read(tag.getCompound("Instruments"));
        activeDepositId = tag.contains("ActiveDepositId")
                ? ResourceLocation.tryParse(tag.getString("ActiveDepositId")) : null;
        previewDepositId = activeDepositId;
        creatorId = tag.hasUUID("Creator") ? tag.getUUID("Creator") : null;
        creatorName = tag.getString("CreatorName");
        specializedResourceId = tag.contains("SpecializedResource")
                ? ResourceLocation.tryParse(tag.getString("SpecializedResource")) : null;
        cityId = tag.hasUUID("CityId") ? tag.getUUID("CityId") : null;
        projectId = tag.hasUUID("ProjectId") ? tag.getUUID("ProjectId") : null;
        commissioned = tag.getBoolean("Commissioned")
                && cityId != null && projectId != null && activeDepositId != null;
        miningTicks = validMiningTicks(tag.getDouble("MiningProgressTicks"));
        runtimeState = IndustrialMineState.STOPPED;
        electricalState = MineElectricalState.NO_POWER;
        // Restore intent only. Structure, bindings and live power must pass a fresh startup.
        userRequestedStart = commissioned && tag.getBoolean("UserRequestedStart");
        startupTicks = 0;
        shutdownTicks = 0;
        lastResult = null;
        statusMessage = userRequestedStart
                ? "Ожидание безопасного запуска после загрузки мира."
                : "Шахта безопасно остановлена после загрузки мира.";
        visualAssembled = false;
        visualPowerPort = null;
        visualGateway = null;
        effectDrills = java.util.Arrays.stream(tag.getLongArray("EffectDrills")).mapToObj(BlockPos::of).toList();
        effectVents = java.util.Arrays.stream(tag.getLongArray("EffectVents")).mapToObj(BlockPos::of).toList();
        if (tag.contains("VisualRuntimeState")) {
            runtimeState = readEnum(tag, "VisualRuntimeState", IndustrialMineState.class,
                    IndustrialMineState.STOPPED);
            electricalState = readEnum(tag, "VisualElectricalState", MineElectricalState.class,
                    MineElectricalState.NO_POWER);
            visualAssembled = tag.getBoolean("VisualAssembled");
            visualPowerPort = tag.contains("VisualPowerPort") ? BlockPos.of(tag.getLong("VisualPowerPort")) : null;
            visualGateway = tag.contains("VisualGateway") ? BlockPos.of(tag.getLong("VisualGateway")) : null;
        }
        notifyClientVisual();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Instruments", telemetry.save());
        tag.putString("VisualRuntimeState", runtimeState.name());
        tag.putString("VisualElectricalState", electricalState.name());
        tag.putBoolean("VisualAssembled", visualAssembled);
        tag.putLongArray("EffectDrills", effectDrills.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("EffectVents", effectVents.stream().mapToLong(BlockPos::asLong).toArray());
        if (visualPowerPort != null) tag.putLong("VisualPowerPort", visualPowerPort.asLong());
        if (visualGateway != null) tag.putLong("VisualGateway", visualGateway.asLong());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static <E extends Enum<E>> E readEnum(
            CompoundTag tag, String key, Class<E> type, E fallback
    ) {
        try {
            return Enum.valueOf(type, tag.getString(key));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
