package com.example.examplemod.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GeologyManager extends SavedData {

    static final int CURRENT_FORMAT_VERSION =
            GeologySavedDataCodec.CURRENT_FORMAT_VERSION;

    private static final String DATA_NAME =
            "createcityexpansioncore_geology";

    private final ResourceLocation dimensionId;
    private final Map<ResourceLocation, GeologicalRegion> regions =
            new LinkedHashMap<>();
    private final boolean storageAvailable;
    private final CompoundTag preservedFallbackData;

    GeologyManager(ResourceLocation dimensionId) {
        this(dimensionId, true, null);
    }

    private GeologyManager(
            ResourceLocation dimensionId,
            boolean storageAvailable,
            CompoundTag preservedFallbackData
    ) {
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.storageAvailable = storageAvailable;
        this.preservedFallbackData = preservedFallbackData;
    }

    static GeologyManager load(
            CompoundTag tag,
            HolderLookup.Provider registries,
            ResourceLocation dimensionId
    ) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(dimensionId, "dimensionId");

        GeologySavedDataCodec.LoadResult result =
                GeologySavedDataCodec.load(tag, dimensionId);
        GeologyManager manager = new GeologyManager(
                dimensionId,
                result.storageAvailable(),
                result.preservedFallbackData()
        );
        manager.regions.putAll(result.regions());

        if (result.migrated()) {
            manager.setDirty();
        }

        return manager;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        if (!storageAvailable) {
            return preservedFallbackData.copy();
        }

        return GeologySavedDataCodec.save(
                tag,
                dimensionId,
                regions.values()
        );
    }

    public static GeologyManager get(ServerLevel level) {
        ResourceLocation dimensionId = level.dimension().location();
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> new GeologyManager(dimensionId),
                        (tag, registries) -> load(
                                tag,
                                registries,
                                dimensionId
                        ),
                        null
                ),
                DATA_NAME
        );
    }

    public static GeologicalRegion getRegion(
            ServerLevel level,
            BlockPos pos
    ) {
        return get(level).getOrCreateRegion(level.getSeed(), pos);
    }

    public static Collection<GeologicalDeposit> getDeposits(
            ServerLevel level,
            BlockPos pos
    ) {
        return getRegion(level, pos).getDeposits();
    }

    public static Optional<GeologicalDeposit> getDeposit(
            ServerLevel level,
            ResourceLocation depositId
    ) {
        return get(level).findDeposit(depositId);
    }

    public static DepositExtractionResult extractDeposit(
            ServerLevel level,
            ResourceLocation depositId,
            long requestedAmount
    ) {
        return get(level).extract(depositId, requestedAmount);
    }

    GeologicalRegion getOrCreateRegion(long worldSeed, BlockPos pos) {
        int fieldX = Math.floorDiv(
                pos.getX(),
                GeologyGenerator.REGION_SIZE
        );
        int fieldZ = Math.floorDiv(
                pos.getZ(),
                GeologyGenerator.REGION_SIZE
        );
        ResourceLocation regionId = GeologyGenerator.regionId(
                dimensionId,
                fieldX,
                fieldZ
        );

        GeologicalRegion existing = regions.get(regionId);
        if (existing != null) {
            return existing;
        }

        if (!storageAvailable) {
            GeologicalRegion unavailableRegion = new GeologicalRegion(
                    regionId,
                    fieldX,
                    fieldZ,
                    List.of()
            );
            regions.put(regionId, unavailableRegion);
            return unavailableRegion;
        }

        GeologicalRegion created = GeologyGenerator.createRegion(
                dimensionId,
                worldSeed,
                fieldX,
                fieldZ
        );
        regions.put(regionId, created);
        setDirty();
        return created;
    }

    Optional<GeologicalDeposit> findDeposit(ResourceLocation depositId) {
        Objects.requireNonNull(depositId, "depositId");

        for (GeologicalRegion region : regions.values()) {
            Optional<GeologicalDeposit> deposit = region.getDeposit(depositId);
            if (deposit.isPresent()) {
                return deposit;
            }
        }

        return Optional.empty();
    }

    DepositExtractionResult extract(
            ResourceLocation depositId,
            long requestedAmount
    ) {
        Objects.requireNonNull(depositId, "depositId");

        if (!storageAvailable) {
            return DepositExtractionResult.storageUnavailable();
        }

        Optional<GeologicalDeposit> depositResult = findDeposit(depositId);
        if (depositResult.isEmpty()) {
            return DepositExtractionResult.notFound();
        }

        GeologicalDeposit deposit = depositResult.get();
        if (requestedAmount < 0) {
            return DepositExtractionResult.forDeposit(
                    DepositExtractionResult.Status.INVALID_REQUEST,
                    0L,
                    deposit
            );
        }
        if (requestedAmount == 0) {
            return DepositExtractionResult.forDeposit(
                    DepositExtractionResult.Status.ZERO_REQUEST,
                    0L,
                    deposit
            );
        }
        if (deposit.isDepleted()) {
            return DepositExtractionResult.forDeposit(
                    DepositExtractionResult.Status.DEPLETED,
                    0L,
                    deposit
            );
        }

        long extracted = deposit.extract(requestedAmount);
        setDirty();
        return DepositExtractionResult.forDeposit(
                DepositExtractionResult.Status.EXTRACTED,
                extracted,
                deposit
        );
    }

    public boolean isStorageAvailable() {
        return storageAvailable;
    }

    static ResourceLocation depositId(
            ResourceLocation dimensionId,
            int fieldX,
            int fieldZ,
            ResourceLocation resourceId,
            int depositIndex
    ) {
        return GeologyGenerator.depositId(
                dimensionId,
                fieldX,
                fieldZ,
                resourceId,
                depositIndex
        );
    }
}
