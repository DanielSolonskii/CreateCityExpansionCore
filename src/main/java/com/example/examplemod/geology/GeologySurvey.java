package com.example.examplemod.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * Результат одного замера магнитного анализатора.
 *
 * Два принципиально разных режима одним предметом:
 *
 *  OUTSIDE — игрок вне месторождения. Прибор находит ближайшую
 *  промышленную аномалию в пределах 150-200 блоков от границы
 *  залежи и сообщает ресурс, направление и грубую дистанцию.
 *  Координаты ядра намеренно не раскрываются: цель игры —
 *  искать аномалию, а не получать готовые координаты.
 *
 *  INSIDE — игрок внутри месторождения. Прибор показывает
 *  ресурс, локальный production potential, оценочный остаток
 *  запаса и класс размера. Максимум находится перемещением
 *  и сравнением local potential, а не по стрелке к ядру.
 *
 * Координаты не передаются в сеть ни в одном из режимов.
 */
public record GeologySurvey(
        Mode mode,
        BlockPos origin,
        BlockPos anomalyCenter,
        ResourceLocation resourceId,
        int direction,
        int distanceBlocks,
        int boundaryDistanceBlocks,
        int localPotentialPerHour,
        int peakPotentialPerHour,
        long remainingReserve,
        long initialReserve,
        DepositSizeClass sizeClass,
        DepositQualityClass qualityClass,
        int estimatedLifetimeHours,
        int remainingUses
) {

    public enum Mode {
        OUTSIDE,
        INSIDE,
        NO_ANOMALY
    }

    public static final int SCAN_RADIUS_BLOCKS = 200;

    public static final int[] DIRECTION_VECTORS = {
            1, 0,
            1, 1,
            0, 1,
            -1, 1,
            -1, 0,
            -1, -1,
            0, -1,
            1, -1
    };

    public static final String[] DIRECTION_KEYS = {
            "east",
            "east_northeast",
            "north",
            "north_northwest",
            "west",
            "west_southwest",
            "south",
            "south_southeast"
    };

    public GeologySurvey {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sizeClass, "sizeClass");
        Objects.requireNonNull(qualityClass, "qualityClass");
        anomalyCenter = anomalyCenter == null ? null : anomalyCenter.immutable();
        if (distanceBlocks < 0 || boundaryDistanceBlocks < 0) {
            throw new IllegalArgumentException("distances cannot be negative");
        }
        if (localPotentialPerHour < 0 || peakPotentialPerHour < 0) {
            throw new IllegalArgumentException("potentials cannot be negative");
        }
        if (estimatedLifetimeHours < 0 || remainingUses < 0) {
            throw new IllegalArgumentException("counters cannot be negative");
        }
        if (mode == Mode.NO_ANOMALY && resourceId != null) {
            throw new IllegalArgumentException(
                    "a survey without an anomaly must not carry a resource"
            );
        }
        if (mode != Mode.NO_ANOMALY && resourceId == null) {
            throw new IllegalArgumentException("a survey must carry a resource");
        }
    }

    public Optional<ResourceLocation> resourceIdOptional() {
        return Optional.ofNullable(resourceId);
    }

    public Optional<BlockPos> anomalyCenterOptional() {
        return Optional.ofNullable(anomalyCenter);
    }

    /**
     * Строит замер, разбивая поиск по режимам.
     *
     * Сначала проверяется сама позиция наблюдателя, поэтому
     * игрок на краю залежи всегда получает данные о том месте,
     * где он действительно стоит.
     */
    public static GeologySurvey of(
            BlockPos origin,
            java.util.List<GeologicalDeposit> candidates,
            int remainingUses
    ) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(candidates, "candidates");

        GeologicalDeposit inside = null;
        for (GeologicalDeposit deposit : candidates) {
            if (deposit.contains(origin.getX(), origin.getZ())) {
                if (inside == null
                        || deposit.localPotentialPerHour(origin.getX(), origin.getZ())
                        > inside.localPotentialPerHour(origin.getX(), origin.getZ())) {
                    inside = deposit;
                }
            }
        }

        if (inside != null) {
            return insideOrigin(origin, inside, remainingUses);
        }

        Candidate nearest = nearest(origin, candidates);
        if (nearest == null) {
            /*
             * Дистанции здесь нулевые, а не отрицательные:
             * запись валидирует их как неотрицательные, и
             * sentinel -1 приводил к IllegalArgumentException
             * прямо на серверном треде. Именно этот случай
             * «аномалий рядом нет» является самым частым, то есть
             * прибор падал чаще всего.
             */
            return new GeologySurvey(
                    Mode.NO_ANOMALY,
                    origin,
                    null,
                    null,
                    -1,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    DepositSizeClass.NORMAL,
                    DepositQualityClass.MODEST,
                    0,
                    remainingUses
            );
        }

        int dx = nearest.deposit().getCenterX() - origin.getX();
        int dz = nearest.deposit().getCenterZ() - origin.getZ();
        int direction = directionOf(dx, dz);

        return new GeologySurvey(
                Mode.OUTSIDE,
                origin,
                BlockPos.containing(
                        nearest.deposit().getCenterX(),
                        origin.getY(),
                        nearest.deposit().getCenterZ()
                ),
                nearest.deposit().getResourceId(),
                direction,
                nearest.centerDistance(),
                nearest.boundaryDistance(),
                0,
                (int) Math.round(nearest.deposit().getPeakPotentialPerHour()),
                nearest.deposit().getRemainingReserve(),
                nearest.deposit().getInitialReserve(),
                nearest.deposit().getSizeClass(),
                nearest.deposit().getQualityClass(),
                0,
                remainingUses
        );
    }

    private static GeologySurvey insideOrigin(
            BlockPos origin,
            GeologicalDeposit deposit,
            int remainingUses
    ) {
        double local = deposit.localPotentialPerHour(origin.getX(), origin.getZ());
        int roundedLocal = (int) (Math.round(local / 100.0) * 100.0);
        int roundedReserve = (int) (Math.round(
                (double) deposit.getRemainingReserve() / 1_000.0
        ) * 1_000L);
        int lifetime = (int) Math.round(deposit.estimatedLifetimeHours(local));

        return new GeologySurvey(
                Mode.INSIDE,
                origin,
                BlockPos.containing(deposit.getCenterX(), origin.getY(), deposit.getCenterZ()),
                deposit.getResourceId(),
                -1,
                0,
                0,
                Math.max(0, roundedLocal),
                (int) Math.round(deposit.getPeakPotentialPerHour()),
                Math.max(0L, roundedReserve),
                deposit.getInitialReserve(),
                deposit.getSizeClass(),
                deposit.getQualityClass(),
                Math.max(0, lifetime),
                remainingUses
        );
    }

    /**
     * Ближайшая аномалия в пределах SCAN_RADIUS_BLOCKS
     * от ГРАНИЦЫ залежи. Дистанция до центра не раскрывается
     * игроку: она используется только для выбора направления.
     */
    private static Candidate nearest(
            BlockPos origin,
            java.util.List<GeologicalDeposit> candidates
    ) {
        Candidate best = null;

        for (GeologicalDeposit deposit : candidates) {
            int boundary = boundaryDistance(origin, deposit);
            if (boundary < 0 || boundary > SCAN_RADIUS_BLOCKS) {
                continue;
            }
            int centerDistance = (int) Math.hypot(
                    deposit.getCenterX() - origin.getX(),
                    deposit.getCenterZ() - origin.getZ()
            );
            Candidate candidate = new Candidate(
                    deposit,
                    boundary,
                    centerDistance
            );
            if (best == null
                    || candidate.boundaryDistance() < best.boundaryDistance()
                    || (candidate.boundaryDistance() == best.boundaryDistance()
                    && candidate.centerDistance() < best.centerDistance())) {
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Расстояние от точки до границы залежи в горизонтальной
     * плоскости. Отрицательное значение означает, что точка
     * находится внутри залежи.
     */
    static int boundaryDistance(BlockPos origin, GeologicalDeposit deposit) {
        return boundaryDistance(origin.getX(), origin.getZ(), deposit);
    }

    /**
     * Расстояние от точки до границы залежи в горизонтальной
     * плоскости, в блоках.
     *
     * 0    = точка на границе
     * >0   = точка снаружи, на таком расстоянии до границы
     * <0   = точка внутри (в частности -1 для ближайшей залежи)
     *
     * ВАЖНО: метод не фильтрует по contains(). Он нужен в том
     * числе для поиска ближайшей аномалии СНАРУЖИ залежи, где
     * contains() возвращает false. Собственно поэтому он и
     * принимает координаты явно, а не только BlockPos.
     */
    static int boundaryDistance(int x, int z, GeologicalDeposit deposit) {
        double radius = deposit.getShape().normalizedRadius(x, z);
        int maxRadius = Math.max(
                deposit.getShape().radiusX(),
                deposit.getShape().radiusZ()
        );
        int raw = (int) Math.round((radius - 1.0) * maxRadius);
        return raw > 0 ? raw : (raw < 0 ? -1 : 0);
    }

    static int directionOf(int dx, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);

        if (ax >= 2 * az) {
            return dx >= 0 ? 0 : 4;
        }
        if (az >= 2 * ax) {
            return dz >= 0 ? 2 : 6;
        }
        if (dx >= 0 && dz >= 0) {
            return 1;
        }
        if (dx < 0 && dz >= 0) {
            return 3;
        }
        if (dx < 0) {
            return 5;
        }
        return 7;
    }

    /**
     * Заглушка ресурса для режима «аномалий не найдено».
     * Реального ресурса за ней нет: прибор её не показывает.
     */
    private record Candidate(
            GeologicalDeposit deposit,
            int boundaryDistance,
            int centerDistance
    ) {
    }
}