package com.example.examplemod.geology;

/**
 * Пространственная геометрия месторождения.
 *
 * Намеренно простая и поддерживаемая модель без полигонов и GIS:
 * эллипс с полуосями radiusX / radiusZ, поворотом, гладким
 * детерминированным искажением границы и отдельным
 * богатым ядром.
 *
 * Геометрия полностью выводится из shapeSeed и координат,
 * поэтому она идентична при любом пересчёте, в любом
 * диапазоне мира и после рестарта сервера.
 */
public record DepositShape(
        int centerX,
        int centerZ,
        int radiusX,
        int radiusZ,
        double rotationRadians,
        double coreRadius,
        long shapeSeed
) {

    /** Диаметр богатого ядра: 80-250 блоков. */
    public static final double MIN_CORE_DIAMETER = 80.0;
    public static final double MAX_CORE_DIAMETER = 250.0;

    /**
     * Масштаб решётки искажения границы, в блоках.
     *
     * Чем крупнее значение, тем положе граница. Для залежей
     * диаметром в сотни блоков мелкая решётка давала бы
     * заметно разные показания у соседних блоков, поэтому
     * масштаб намеренно велик.
     */
    public static final double EDGE_NOISE_SCALE = 160.0;

    /** Амплитуда искажения границы: +-18% от номинального радиуса. */
    public static final double EDGE_NOISE_AMPLITUDE = 0.18;

    /** Нижняя и верхняя границы доли радиуса, занятой ядром. */
    public static final double MIN_CORE_FRACTION = 0.15;
    public static final double MAX_CORE_FRACTION = 0.60;

    public DepositShape {
        if (radiusX <= 0 || radiusZ <= 0) {
            throw new IllegalArgumentException("deposit radii must be positive");
        }
        if (!Double.isFinite(rotationRadians)) {
            throw new IllegalArgumentException("rotationRadians must be finite");
        }
        if (!Double.isFinite(coreRadius)) {
            throw new IllegalArgumentException("coreRadius must be finite");
        }
        if (coreRadius < MIN_CORE_FRACTION || coreRadius > MAX_CORE_FRACTION) {
            throw new IllegalArgumentException("coreRadius is out of range");
        }
    }

    /**
     * Нормализованный радиус точки относительно залежи:
     * 0 в центре, 1 на границе, больше 1 снаружи.
     *
     * Значение непрерывно по обеим производным, поэтому
     * потенциал по соседним блокам отличается плавно.
     */
    public double normalizedRadius(double worldX, double worldZ) {
        double dx = worldX - centerX;
        double dz = worldZ - centerZ;

        double cos = Math.cos(rotationRadians);
        double sin = Math.sin(rotationRadians);
        double localX = dx * cos + dz * sin;
        double localZ = -dx * sin + dz * cos;

        double ellipse = Math.hypot(localX / radiusX, localZ / radiusZ);
        double warp = GeologyNoise.value(
                shapeSeed,
                localX / EDGE_NOISE_SCALE,
                localZ / EDGE_NOISE_SCALE
        ) - 0.5;
        double edge = 1.0 + EDGE_NOISE_AMPLITUDE * warp;
        return ellipse / edge;
    }

    public boolean contains(double worldX, double worldZ) {
        return normalizedRadius(worldX, worldZ) < 1.0;
    }

    /**
     * Диаметр богатого ядра, вычисленный из геометрии.
     */
    public double coreDiameterBlocks() {
        return coreRadius * (radiusX + radiusZ);
    }
}