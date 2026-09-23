package com.example.examplemod.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

public class GeologyManager {

    /*
     * Масштабы геологического поля.
     *
     * Большой масштаб определяет крупные залежи.
     * Средний добавляет локальные изменения.
     * Малый создаёт небольшие переходы между зонами.
     */
    private static final double LARGE_SCALE = 100.0;
    private static final double MEDIUM_SCALE = 45.0;
    private static final double SMALL_SCALE = 20.0;

    private static final String MOD_ID = "createcityexpansioncore";

    public static GeologicalRegion getRegion(ServerLevel level, BlockPos pos) {

        long seed = level.getSeed();

        Map<ResourceLocation, Integer> resources = new HashMap<>();

        resources.put(
                resource("copper"),
                calculateResource(seed, pos, 17L, 0.0)
        );

        resources.put(
                resource("iron"),
                calculateResource(seed, pos, 31L, 1.7)
        );

        resources.put(
                resource("coal"),
                calculateResource(seed, pos, 47L, 3.4)
        );

        resources.put(
                resource("zinc"),
                calculateResource(seed, pos, 73L, 5.1)
        );

        /*
         * Поле идентифицируется координатами,
         * чтобы соседние точки относились к одной
         * крупной геологической области.
         */
        int fieldX = Math.floorDiv(pos.getX(), 100);
        int fieldZ = Math.floorDiv(pos.getZ(), 100);

        ResourceLocation fieldId =
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID,
                        "field_" + fieldX + "_" + fieldZ
                );

        return new GeologicalRegion(
                fieldId,
                resources
        );
    }

    /**
     * Рассчитывает концентрацию конкретного ресурса
     * в указанной точке мира.
     *
     * Значение находится в диапазоне 0-100.
     */
    private static int calculateResource(
            long worldSeed,
            BlockPos pos,
            long resourceSeed,
            double phase
    ) {

        long hash = mixSeed(worldSeed, resourceSeed);

        /*
         * Для каждого ресурса создаётся собственное
         * смещение поля.
         *
         * Благодаря этому медь, железо, уголь и цинк
         * имеют разные геологические распределения.
         */
        double offsetX =
                ((hash & 0xFFFFL) / 65535.0) * 1000.0;

        double offsetZ =
                (((hash >>> 16) & 0xFFFFL) / 65535.0) * 1000.0;

        double x = pos.getX() + offsetX;
        double z = pos.getZ() + offsetZ;

        /*
         * Крупная геологическая структура.
         */
        double large =
                Math.sin(
                        x / LARGE_SCALE + phase
                )
                        *
                        Math.cos(
                                z / LARGE_SCALE + phase * 0.7
                        );

        /*
         * Средние изменения.
         */
        double medium =
                Math.sin(
                        (x + z * 0.7)
                                / MEDIUM_SCALE
                                + phase * 1.3
                );

        /*
         * Небольшие локальные изменения.
         */
        double small =
                Math.sin(
                        (x - z * 0.45)
                                / SMALL_SCALE
                                + phase * 2.1
                );

        /*
         * Общий геологический потенциал.
         *
         * Большие структуры имеют наибольшее влияние,
         * средние — вторичное,
         * маленькие — небольшое.
         */
        double geologicalPotential =
                large * 0.65
                        + medium * 0.25
                        + small * 0.10;

        /*
         * Переводим диапазон примерно [-1; 1]
         * в [0; 1].
         */
        double normalized =
                (geologicalPotential + 1.0) / 2.0;

        /*
         * Немного усиливаем разницу между
         * бедными и богатыми участками.
         */
        double shaped =
                Math.pow(
                        normalized,
                        1.35
                );

        /*
         * Получаем значение 0-100.
         */
        double value =
                shaped * 100.0;

        /*
         * БЕСПЛОДНАЯ ЗОНА
         *
         * Если геологический потенциал очень низкий,
         * эта территория практически лишена
         * полезных ресурсов.
         */
        if (geologicalPotential < -0.55) {
            value *= 0.05;
        }

        /*
         * Защита от выхода за пределы диапазона.
         */
        value = Math.max(
                0.0,
                Math.min(
                        100.0,
                        value
                )
        );

        return (int) Math.round(value);
    }

    /**
     * Создаёт стабильное число из seed мира
     * и идентификатора ресурса.
     *
     * Одинаковый мир + одинаковый ресурс =
     * одинаковое геологическое поле.
     */
    private static long mixSeed(
            long worldSeed,
            long resourceSeed
    ) {

        long value =
                worldSeed
                        ^ (
                        resourceSeed
                                * 0x9E3779B97F4A7C15L
                );

        value =
                (
                        value
                                ^ (value >>> 30)
                )
                        * 0xBF58476D1CE4E5B9L;

        value =
                (
                        value
                                ^ (value >>> 27)
                )
                        * 0x94D049BB133111EBL;

        return value ^ (value >>> 31);
    }

    /**
     * Создаёт ResourceLocation нашего ресурса.
     */
    private static ResourceLocation resource(
            String name
    ) {

        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                name
        );
    }
}