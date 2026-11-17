package com.example.examplemod.geology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** Polygon reconstructed from exclusive grid samples, including holes. */
public final class VeinFootprint {
    public static final int STEP = 16;
    public static final int SIDE = 91;
    public static final int WORD_COUNT = Math.ceilDiv(SIDE * SIDE, Long.SIZE);

    private final int originX;
    private final int originZ;
    private final BitSet samples;
    private final List<Edge> edges;
    private final int area;
    private final int coreSample;
    private final double coreClearance;

    public record Edge(int x0, int z0, int x1, int z1) {
        double distanceSquared(double x, double z) {
            double vx = x1 - x0;
            double vz = z1 - z0;
            double dx = x - x0;
            double dz = z - z0;
            double t = Math.clamp((dx * vx + dz * vz) / (vx * vx + vz * vz), 0.0, 1.0);
            dx -= t * vx;
            dz -= t * vz;
            return dx * dx + dz * dz;
        }
    }

    public VeinFootprint(int originX, int originZ, long[] samples) {
        if (samples.length != WORD_COUNT || originX % STEP != 0 || originZ % STEP != 0) {
            throw new IllegalArgumentException("Invalid vein sample grid");
        }
        this.originX = originX;
        this.originZ = originZ;
        this.samples = BitSet.valueOf(samples);
        if (this.samples.isEmpty() || this.samples.length() > SIDE * SIDE) {
            throw new IllegalArgumentException("Invalid vein sample bounds");
        }
        area = this.samples.cardinality();
        edges = buildEdges();
        coreSample = findCoreSample();
        coreClearance = sampleClearance(coreSample);
    }

    static VeinFootprint fromSamples(int originX, int originZ, List<Integer> samples) {
        BitSet mask = new BitSet(SIDE * SIDE);
        for (int sample : samples) {
            mask.set(sample);
        }
        return new VeinFootprint(originX, originZ, Arrays.copyOf(mask.toLongArray(), WORD_COUNT));
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public long[] sampleWords() {
        return Arrays.copyOf(samples.toLongArray(), WORD_COUNT);
    }

    int area() {
        return area;
    }

    public boolean contains(double x, double z) {
        double gridX = (x - originX()) / STEP - 0.5;
        double gridZ = (z - originZ()) / STEP - 0.5;
        int col = (int) Math.floor(gridX);
        int row = (int) Math.floor(gridZ);
        double u = gridX - col;
        double v = gridZ - row;
        double sum = u + v;
        double difference = u - v;
        double side = switch (corners(col, row)) {
            case 1 -> 0.5 - sum;
            case 2 -> difference - 0.5;
            case 3 -> 0.5 - v;
            case 4 -> sum - 1.5;
            case 5 -> Math.max(0.5 - sum, sum - 1.5);
            case 6 -> u - 0.5;
            case 7 -> 0.5 + difference;
            case 8 -> -0.5 - difference;
            case 9 -> 0.5 - u;
            case 10 -> Math.abs(difference) - 0.5;
            case 11 -> 1.5 - sum;
            case 12 -> v - 0.5;
            case 13 -> 0.5 - difference;
            case 14 -> sum - 0.5;
            case 15 -> 1;
            default -> -1;
        };
        // The original half-open sample owns points lying exactly on a shared edge.
        return side > 0 || (side == 0 && occupied(col + (u >= 0.5 ? 1 : 0),
                row + (v >= 0.5 ? 1 : 0)));
    }

    private boolean occupied(int x, int z) {
        return x >= 0 && x < SIDE && z >= 0 && z < SIDE && samples.get(z * SIDE + x);
    }

    private int corners(int x, int z) {
        return (occupied(x, z) ? 1 : 0)
                | (occupied(x + 1, z) ? 2 : 0)
                | (occupied(x + 1, z + 1) ? 4 : 0)
                | (occupied(x, z + 1) ? 8 : 0);
    }

    /**
     * Marching squares over sample centers. Diagonal pairs stay separate,
     * so two owners can share an edge without claiming the same interior.
     */
    private List<Edge> buildEdges() {
        List<Edge> boundary = new ArrayList<>();
        int half = STEP / 2;
        for (int row = -1; row < SIDE; row++) {
            for (int col = -1; col < SIDE; col++) {
                int x = (col + 1) * STEP;
                int z = (row + 1) * STEP;
                int mask = corners(col, row);
                switch (mask) {
                    case 1, 5, 14 -> boundary.add(new Edge(x - half, z, x, z - half));
                    case 2, 10, 13 -> boundary.add(new Edge(x, z - half, x + half, z));
                    case 3, 12 -> boundary.add(new Edge(x - half, z, x + half, z));
                    case 4, 11 -> boundary.add(new Edge(x + half, z, x, z + half));
                    case 6, 9 -> boundary.add(new Edge(x, z - half, x, z + half));
                    case 7, 8 -> boundary.add(new Edge(x, z + half, x - half, z));
                    default -> { }
                }
                if (mask == 5) {
                    boundary.add(new Edge(x + half, z, x, z + half));
                }
                if (mask == 10) {
                    boundary.add(new Edge(x, z + half, x - half, z));
                }
            }
        }
        return List.copyOf(boundary);
    }

    /** Boundary segments in block coordinates relative to the footprint origin. */
    public List<Edge> edges() {
        return edges;
    }

    public double distanceToBoundary(double x, double z) {
        return Math.sqrt(nearestEdgeDistanceSquared(x - originX(), z - originZ(), -1.0));
    }

    private double nearestEdgeDistanceSquared(double x, double z, double stopAt) {
        double distance = Double.POSITIVE_INFINITY;
        for (Edge edge : edges) {
            distance = Math.min(distance, edge.distanceSquared(x, z));
            if (distance <= stopAt) {
                break;
            }
        }
        return distance;
    }

    int coreSample() {
        return coreSample;
    }

    double coreClearance() {
        return coreClearance;
    }

    /** Choose the sample with the most room for a rich core. */
    private int findCoreSample() {
        double bestDistanceSquared = -1;
        int bestSample = -1;
        for (int sample = samples.nextSetBit(0); sample >= 0; sample = samples.nextSetBit(sample + 1)) {
            // A single nearby edge can rule out this sample as the core.
            double distanceSquared = nearestEdgeDistanceSquared(
                    (sample % SIDE + 0.5) * STEP + 0.5,
                    (sample / SIDE + 0.5) * STEP + 0.5, bestDistanceSquared);
            if (distanceSquared > bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestSample = sample;
            }
        }
        return bestSample;
    }

    private double sampleClearance(int sample) {
        return distanceToBoundary(originX() + (sample % SIDE + 0.5) * STEP + 0.5,
                originZ() + (sample / SIDE + 0.5) * STEP + 0.5);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VeinFootprint footprint && originX == footprint.originX
                && originZ == footprint.originZ && samples.equals(footprint.samples);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * originX + originZ) + samples.hashCode();
    }
}
