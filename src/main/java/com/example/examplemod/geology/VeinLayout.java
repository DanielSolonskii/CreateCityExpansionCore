package com.example.examplemod.geology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.SplittableRandom;

/** Partitions the global noise mask by jittered sites in warped world coordinates. */
final class VeinLayout {
    private static final int SIDE = VeinFootprint.SIDE;
    private static final int SAMPLES = SIDE * SIDE;
    private static final int STEP = VeinFootprint.STEP;
    private static final int SITE_SPACING = 640;
    private static final int SITE_JITTER = 96;
    private static final int WARP_AMPLITUDE = 120;
    private static final int HALF_WINDOW = SIDE / 2;
    // Nearest-site distance is at most sqrt(2) * (320 + 96) in warped space.
    // The window adds the maximum 120-block warp and room for the sample boundary.
    static final int STORAGE_REACH = (HALF_WINDOW + 1) * STEP;
    static final int MIN_AREA = (int) Math.ceil(Math.PI * DepositSizeClass.MIN_RADIUS
            * DepositSizeClass.MIN_RADIUS / (VeinFootprint.STEP * VeinFootprint.STEP));
    private static final int SPLIT_AREA = 1900;
    static final double PRESENCE_THRESHOLD = -0.052;

    record Patch(int component, VeinFootprint footprint) {}

    private record Growth(int sample, int owner, double cost) {}

    private record Site(int gridX, int gridZ, int x, int z) {}

    private VeinLayout() {}

    static List<Patch> generate(long seed, int cellX, int cellZ) {
        int cellWidth = GeologyGenerator.MACRO_CELL_SIZE;
        int cellStartX = cellX * cellWidth;
        int cellStartZ = cellZ * cellWidth;
        int minSiteX = Math.floorDiv(cellStartX - SITE_JITTER - SITE_SPACING / 2, SITE_SPACING);
        int minSiteZ = Math.floorDiv(cellStartZ - SITE_JITTER - SITE_SPACING / 2, SITE_SPACING);
        int maxSiteX = Math.floorDiv(cellStartX + cellWidth - 1 + SITE_JITTER - SITE_SPACING / 2,
                SITE_SPACING);
        int maxSiteZ = Math.floorDiv(cellStartZ + cellWidth - 1 + SITE_JITTER - SITE_SPACING / 2,
                SITE_SPACING);
        List<Patch> patches = new ArrayList<>();
        int siteOrdinal = 0;
        for (int z = minSiteZ; z <= maxSiteZ; z++) {
            for (int x = minSiteX; x <= maxSiteX; x++) {
                Site site = siteAt(seed, x, z);
                if (Math.floorDiv(site.x(), cellWidth) == cellX
                        && Math.floorDiv(site.z(), cellWidth) == cellZ) {
                    addSitePatches(seed, site, siteOrdinal * SAMPLES, patches);
                    siteOrdinal++;
                }
            }
        }
        return patches;
    }

    private static void addSitePatches(long seed, Site site, int componentOffset, List<Patch> patches) {
        boolean[] present = new boolean[SAMPLES];
        int originX = (Math.floorDiv(site.x(), STEP) - HALF_WINDOW) * STEP;
        int originZ = (Math.floorDiv(site.z(), STEP) - HALF_WINDOW) * STEP;
        int firstSiteX = site.gridX() - 3;
        int firstSiteZ = site.gridZ() - 3;
        Site[][] nearby = new Site[7][7];
        for (int z = 0; z < nearby.length; z++) {
            for (int x = 0; x < nearby[z].length; x++) {
                nearby[z][x] = siteAt(seed, firstSiteX + x, firstSiteZ + z);
            }
        }
        long warpXSeed = GeologyNoise.mix(seed, 0x3100L);
        long warpZSeed = GeologyNoise.mix(seed, 0x3110L);
        for (int sample = 0; sample < SAMPLES; sample++) {
            int x = originX + (sample % SIDE) * STEP + STEP / 2;
            int z = originZ + (sample / SIDE) * STEP + STEP / 2;
            if (presence(seed, x, z) >= PRESENCE_THRESHOLD) {
                int warpedX = x + warp(warpXSeed, x, z);
                int warpedZ = z + warp(warpZSeed, x, z);
                Site nearest = nearestSite(nearby, firstSiteX, firstSiteZ, warpedX, warpedZ);
                present[sample] = nearest.equals(site);
            }
        }

        for (int start = 0; start < SAMPLES; start++) {
            if (!present[start]) {
                continue;
            }
            List<Integer> component = new ArrayList<>();
            component.add(start);
            present[start] = false;
            for (int cursor = 0; cursor < component.size(); cursor++) {
                for (int neighbor : NEIGHBORS[component.get(cursor)]) {
                    if (present[neighbor]) {
                        present[neighbor] = false;
                        component.add(neighbor);
                    }
                }
            }
            if (component.size() >= MIN_AREA) {
                long splitSeed = GeologyNoise.mix(seed, site.gridX(), site.gridZ(), start);
                for (List<Integer> part : partition(component, splitSeed)) {
                    VeinFootprint footprint = VeinFootprint.fromSamples(originX, originZ, part);
                    if (footprint.coreClearance() >= DepositShape.MIN_VEIN_CORE_CLEARANCE) {
                        patches.add(new Patch(componentOffset + start, footprint));
                    }
                }
            }
        }
    }

    private static Site siteAt(long seed, int gridX, int gridZ) {
        long hash = GeologyNoise.lattice(seed, gridX, gridZ);
        int x = gridX * SITE_SPACING + SITE_SPACING / 2
                + (int) Math.round(GeologyNoise.range(hash, -SITE_JITTER, SITE_JITTER));
        int z = gridZ * SITE_SPACING + SITE_SPACING / 2
                + (int) Math.round(GeologyNoise.range(GeologyNoise.mix(hash, 1), -SITE_JITTER, SITE_JITTER));
        return new Site(gridX, gridZ, x, z);
    }

    private static Site nearestSite(Site[][] nearby, int firstX, int firstZ, int x, int z) {
        int gridX = Math.floorDiv(x, SITE_SPACING) - firstX;
        int gridZ = Math.floorDiv(z, SITE_SPACING) - firstZ;
        Site best = null;
        int bestDistance = Integer.MAX_VALUE;
        // Sites outside the surrounding 3x3 are farther than the site in this square.
        for (int dz = gridZ - 1; dz <= gridZ + 1; dz++) {
            for (int dx = gridX - 1; dx <= gridX + 1; dx++) {
                Site candidate = nearby[dz][dx];
                int deltaX = x - candidate.x();
                int deltaZ = z - candidate.z();
                int distance = deltaX * deltaX + deltaZ * deltaZ;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static int warp(long seed, int x, int z) {
        double broad = GeologyNoise.value(seed, x / 340.0, z / 340.0);
        double detail = GeologyNoise.value(GeologyNoise.mix(seed, 1), x / 130.0, z / 130.0);
        return (int) Math.round((1.6 * broad + 0.4 * detail - 1.0) * WARP_AMPLITUDE);
    }

    static double presence(long seed, double x, double z) {
        // Rotate and stretch the low-frequency field to favor elongated bodies.
        double u = (0.8 * x + 0.6 * z) / 1280.0;
        double v = (-0.6 * x + 0.8 * z) / 800.0;
        double value = 0;
        double amplitude = 1;
        double totalAmplitude = 0;
        for (int octave = 0; octave < 4; octave++) {
            value += amplitude * perlin(GeologyNoise.mix(seed, octave), u, v);
            totalAmplitude += amplitude;
            u *= 2;
            v *= 2;
            amplitude *= 0.42;
        }
        return value / totalAmplitude;
    }

    private static double perlin(long seed, double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double dx = x - ix;
        double dz = z - iz;
        double top = lerp(fade(dx), gradient(seed, ix, iz, dx, dz),
                gradient(seed, ix + 1, iz, dx - 1, dz));
        double bottom = lerp(fade(dx), gradient(seed, ix, iz + 1, dx, dz - 1),
                gradient(seed, ix + 1, iz + 1, dx - 1, dz - 1));
        return lerp(fade(dz), top, bottom);
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double gradient(long seed, int x, int z, double dx, double dz) {
        return switch ((int) GeologyNoise.lattice(seed, x, z) & 7) {
            case 0 -> dx;
            case 1 -> -dx;
            case 2 -> dz;
            case 3 -> -dz;
            case 4 -> (dx + dz) * 0.7071067811865476;
            case 5 -> (dx - dz) * 0.7071067811865476;
            case 6 -> (-dx + dz) * 0.7071067811865476;
            default -> (-dx - dz) * 0.7071067811865476;
        };
    }

    private static List<List<Integer>> partition(List<Integer> component, long seed) {
        int target = Math.ceilDiv(component.size(), SPLIT_AREA);
        if (target == 1) {
            return List.of(component);
        }
        SplittableRandom random = new SplittableRandom(seed);
        List<Integer> centers = new ArrayList<>();
        centers.add(component.get(random.nextInt(component.size())));
        while (centers.size() < target) {
            int next = -1;
            double best = -1;
            for (int sample : component) {
                int distance = Integer.MAX_VALUE;
                for (int center : centers) {
                    int dx = sample % SIDE - center % SIDE;
                    int dz = sample / SIDE - center / SIDE;
                    distance = Math.min(distance, dx * dx + dz * dz);
                }
                double score = distance * (0.75 + random.nextDouble() * 0.25);
                if (distance >= 100 && score > best) {
                    next = sample;
                    best = score;
                }
            }
            if (next < 0) {
                break;
            }
            centers.add(next);
        }

        int[] owners = new int[SAMPLES];
        Arrays.fill(owners, -2);
        component.forEach(sample -> owners[sample] = -1);
        List<List<Integer>> parts = new ArrayList<>();
        double[][] movementCosts = new double[centers.size()][SAMPLES];
        PriorityQueue<Growth> pending = new PriorityQueue<>(Comparator
                .comparingDouble(Growth::cost).thenComparingInt(Growth::owner)
                .thenComparingInt(Growth::sample));
        for (int center : centers) {
            int owner = parts.size();
            pending.add(new Growth(center, owner, 0));
            parts.add(new ArrayList<>());
            // Each growing vein favors different lobes, even within the same component.
            long broadSeed = GeologyNoise.mix(seed, 0x3200L, owner);
            long detailSeed = GeologyNoise.mix(seed, 0x3210L, owner);
            for (int sample : component) {
                int x = sample % SIDE;
                int z = sample / SIDE;
                double broad = GeologyNoise.value(broadSeed, x / 9.0, z / 9.0);
                double detail = GeologyNoise.value(detailSeed, x / 4.0, z / 4.0);
                movementCosts[owner][sample] = 0.20 + 2.1 * broad * broad + 0.4 * detail;
            }
        }
        while (!pending.isEmpty()) {
            Growth growth = pending.remove();
            if (owners[growth.sample()] != -1) {
                continue;
            }
            owners[growth.sample()] = growth.owner();
            parts.get(growth.owner()).add(growth.sample());
            for (int neighbor : NEIGHBORS[growth.sample()]) {
                if (owners[neighbor] == -1) {
                    double[] costs = movementCosts[growth.owner()];
                    double cost = (costs[growth.sample()] + costs[neighbor]) * 0.5;
                    pending.add(new Growth(neighbor, growth.owner(), growth.cost() + cost));
                }
            }
        }
        for (int owner = 0; owner < parts.size(); owner++) {
            List<Integer> part = parts.get(owner);
            if (part.isEmpty() || part.size() >= MIN_AREA) {
                continue;
            }
            int[] sharedEdges = new int[parts.size()];
            for (int sample : part) {
                for (int neighbor : NEIGHBORS[sample]) {
                    int adjacent = owners[neighbor];
                    if (adjacent >= 0 && adjacent != owner) {
                        sharedEdges[adjacent]++;
                    }
                }
            }
            int best = 0;
            for (int adjacent = 1; adjacent < sharedEdges.length; adjacent++) {
                if (sharedEdges[adjacent] > sharedEdges[best]) {
                    best = adjacent;
                }
            }
            if (sharedEdges[best] > 0) {
                int destination = best;
                part.forEach(sample -> owners[sample] = destination);
                parts.get(destination).addAll(part);
                part.clear();
            }
        }
        return parts.stream().filter(part -> !part.isEmpty()).toList();
    }

    private static final int[][] NEIGHBORS = new int[SAMPLES][];

    static {
        for (int sample = 0; sample < SAMPLES; sample++) {
            int[] neighbors = new int[4];
            int count = 0;
            if (sample % SIDE > 0) neighbors[count++] = sample - 1;
            if (sample % SIDE < SIDE - 1) neighbors[count++] = sample + 1;
            if (sample / SIDE > 0) neighbors[count++] = sample - SIDE;
            if (sample / SIDE < SIDE - 1) neighbors[count++] = sample + SIDE;
            NEIGHBORS[sample] = Arrays.copyOf(neighbors, count);
        }
    }
}
