package com.example.examplemod.geology;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GeologicalRegion {

    private final ResourceLocation id;
    private final int regionX;
    private final int regionZ;
    private final Map<ResourceLocation, GeologicalDeposit> deposits;

    public GeologicalRegion(
            ResourceLocation id,
            int regionX,
            int regionZ,
            Collection<GeologicalDeposit> deposits
    ) {
        this.id = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(deposits, "deposits");
        this.regionX = regionX;
        this.regionZ = regionZ;

        Map<ResourceLocation, GeologicalDeposit> depositsById =
                new LinkedHashMap<>();

        for (GeologicalDeposit deposit : deposits) {
            Objects.requireNonNull(deposit, "deposit");
            GeologicalDeposit previous = depositsById.put(
                    deposit.getId(),
                    deposit
            );

            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate geological deposit id: " + deposit.getId()
                );
            }
        }

        this.deposits = depositsById;
    }

    public ResourceLocation getId() {
        return id;
    }

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public Collection<GeologicalDeposit> getDeposits() {
        return Collections.unmodifiableCollection(deposits.values());
    }

    public Optional<GeologicalDeposit> getDeposit(ResourceLocation depositId) {
        return Optional.ofNullable(deposits.get(depositId));
    }

    /**
     * Compatibility-представление текущего извлекаемого потенциала 0-100.
     * Для каждого ресурса используется максимальное значение среди его залежей:
     * grade * remainingRatio. Полностью истощённые залежи дают 0.
     * Значение всегда вычисляется и не является отдельным состоянием.
     */
    public Map<ResourceLocation, Integer> getResources() {
        Map<ResourceLocation, Integer> resources = new LinkedHashMap<>();

        for (GeologicalDeposit deposit : deposits.values()) {
            int richness = getLegacyRichness(deposit);
            resources.merge(deposit.getResourceId(), richness, Math::max);
        }

        return Collections.unmodifiableMap(resources);
    }

    /**
     * Извлекаемый потенциал залежи в шкале 0-100.
     *
     * Считается от пикового потенциала, а не от усреднённого
     * grade: усреднение по всей залежи ничего не говорит о том,
     * насколько богато то место, где фактически стоит шахта.
     *
     * 0  = ресурса практически нет
     * 100 = очень богатое месторождение
     */
    public int getRichness(ResourceLocation resource) {
        Objects.requireNonNull(resource, "resource");
        int highest = 0;

        for (GeologicalDeposit deposit : deposits.values()) {
            if (deposit.getResourceId().equals(resource)) {
                highest = Math.max(
                        highest,
                        getLegacyRichness(deposit)
                );
            }
        }

        return highest;
    }

    private static int getLegacyRichness(GeologicalDeposit deposit) {
        double ceiling = DepositQualityClass.PEAK_POTENTIAL_CEILING;
        return (int) Math.round(
                deposit.getPeakPotentialPerHour()
                        / ceiling
                        * deposit.getRemainingRatio()
                        * 100.0
        );
    }

    /**
     * Возвращает ресурс с наибольшим содержанием.
     */
    public ResourceLocation getPrimaryResource() {
        ResourceLocation primary = null;
        int highest = 0;

        for (Map.Entry<ResourceLocation, Integer> entry : getResources().entrySet()) {
            if (entry.getValue() > highest) {
                highest = entry.getValue();
                primary = entry.getKey();
            }
        }

        return primary;
    }
}
