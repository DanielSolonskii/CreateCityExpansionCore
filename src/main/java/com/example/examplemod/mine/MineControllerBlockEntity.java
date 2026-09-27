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

public final class MineControllerBlockEntity extends BlockEntity {

    public static final int STARTUP_DURATION_TICKS = 60;
    public static final int STOP_DURATION_TICKS = 40;
    public static final int MAX_PERSISTED_MINING_TICKS = 1200;

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
    private int miningTicks;
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

    public void restoreFromItem(MineControllerItemData.Data data) {
        creatorId = data.creatorId();
        creatorName = data.creatorName();
        activeDepositId = data.depositId();
        specializedResourceId = data.resourceId();
        cityId = data.commissioned() ? data.cityId() : null;
        projectId = data.commissioned() ? data.projectId() : null;
        commissioned = data.commissioned();
        previewDepositId = activeDepositId;
        runtimeState = IndustrialMineState.STOPPED;
        electricalState = MineElectricalState.NO_POWER;
        userRequestedStart = false;
        startupTicks = 0;
        shutdownTicks = 0;
        miningTicks = 0;
        lastResult = null;
        statusMessage = "Контроллер восстановлен из переносимого предмета.";
        setChanged();
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
        userRequestedStart = requested;
    }

    int getStartupTicks() { return startupTicks; }
    void setStartupTicks(int ticks) {
        startupTicks = Math.max(0, Math.min(STARTUP_DURATION_TICKS, ticks));
    }

    int getShutdownTicks() { return shutdownTicks; }
    void setShutdownTicks(int ticks) {
        shutdownTicks = Math.max(0, Math.min(STOP_DURATION_TICKS, ticks));
    }

    int getMiningTicks() { return miningTicks; }
    void setMiningTicks(int ticks) {
        int clamped = Math.max(0, Math.min(MAX_PERSISTED_MINING_TICKS, ticks));
        if (miningTicks != clamped) {
            miningTicks = clamped;
            setChanged();
        }
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

    double miningProgress(int cycleDurationTicks) {
        if (cycleDurationTicks <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) miningTicks / cycleDurationTicks);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
        tag.putInt("MiningProgressTicks", miningTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        miningTicks = Math.max(0, Math.min(
                MAX_PERSISTED_MINING_TICKS,
                tag.getInt("MiningProgressTicks")
        ));
        runtimeState = IndustrialMineState.STOPPED;
        electricalState = MineElectricalState.NO_POWER;
        userRequestedStart = false;
        startupTicks = 0;
        shutdownTicks = 0;
        lastResult = null;
        statusMessage = "Шахта безопасно остановлена после загрузки мира.";
        visualAssembled = false;
        visualPowerPort = null;
        visualGateway = null;
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
        tag.putString("VisualRuntimeState", runtimeState.name());
        tag.putString("VisualElectricalState", electricalState.name());
        tag.putBoolean("VisualAssembled", visualAssembled);
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
