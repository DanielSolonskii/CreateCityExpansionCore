package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Territory rules for 50×50 claims, kept free of world state so they can be verified headless.
 *
 * <p>Everything here is a pure function of its arguments: no {@code City}, no {@code ServerLevel},
 * no NBT. {@link CityManager} owns the mutable state (which city owns which plot) and delegates
 * every decision here, so the rule set can be tested without a running server.
 */
public final class CityClaims {

    private CityClaims() {
    }

    /**
     * Old square territory around a pylon, exactly as {@code CityManager.getCityAt} used to compute
     * it: inclusive {@code pylon ± territorySize/2} on both axes.
     *
     * <p>Kept only to migrate worlds written before claims existed. Nothing in the ownership,
     * protection or map path may call this — a city owns plots, not a square.
     */
    public static int legacyMinBlock(int pylonCoordinate, int territorySize) {
        return pylonCoordinate - territorySize / 2;
    }

    public static int legacyMaxBlock(int pylonCoordinate, int territorySize) {
        return pylonCoordinate + territorySize / 2;
    }

    /**
     * Every plot that overlaps the old square territory of a legacy city.
     *
     * <p>A plot is included when it intersects the square at all, even by a single block, so the
     * migrated territory is a superset of the old one and no previously protected block becomes
     * free. Both corners are converted with floor division, which is what makes {@code -50..-1} and
     * {@code 0..49} two separate plots instead of one.
     *
     * <p>A legacy city with its pylon at origin and a 100-block territory spans {@code -50..50}, so
     * it migrates to the nine plots {@code -1..1} on both axes — 150×150 blocks. The extra margin is
     * the price of a grid-aligned claim and is the reason the plot count, not the old number, is
     * what the UI shows afterwards.
     */
    public static Set<CityPlot> legacyTerritory(ResourceLocation dimension, BlockPos pylonPos, int territorySize) {
        int minX = legacyMinBlock(pylonPos.getX(), territorySize);
        int maxX = legacyMaxBlock(pylonPos.getX(), territorySize);
        int minZ = legacyMinBlock(pylonPos.getZ(), territorySize);
        int maxZ = legacyMaxBlock(pylonPos.getZ(), territorySize);

        Set<CityPlot> plots = new LinkedHashSet<>();
        int firstX = CityPlot.plotXOf(minX);
        int lastX = CityPlot.plotXOf(maxX);
        int firstZ = CityPlot.plotZOf(minZ);
        int lastZ = CityPlot.plotZOf(maxZ);
        for (int plotZ = firstZ; plotZ <= lastZ; plotZ++) {
            for (int plotX = firstX; plotX <= lastX; plotX++) {
                plots.add(new CityPlot(dimension, plotX, plotZ));
            }
        }
        return plots;
    }

    /**
     * Plot that must always be inside a city's territory: the one holding its City Pylon.
     *
     * <p>Seed for a brand new city, and the anchor that makes the adjacency rule satisfiable on the
     * very first expansion.
     */
    public static CityPlot pylonPlot(ResourceLocation dimension, BlockPos pylonPos) {
        return CityPlot.at(dimension, pylonPos);
    }

    /**
     * Whether {@code candidate} shares a side with at least one already owned plot.
     *
     * <p>This is the whole "no remote enclaves" rule: expansion is always local, so a territory can
     * never grow a detached island, and one map click cannot teleport land across the map.
     */
    public static boolean touchesOwned(Collection<CityPlot> owned, CityPlot candidate) {
        if (owned == null || candidate == null) {
            return false;
        }
        for (CityPlot neighbour : candidate.neighbours()) {
            if (owned.contains(neighbour)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every claimed plot is reachable from the pylon plot by walking side to side.
     *
     * <p>Used to reject a migration or a future import that would leave an orphan plot. The search
     * is a flood fill over the plots themselves — no chunk is ever loaded.
     */
    public static boolean isConnected(CityPlot anchor, Collection<CityPlot> owned) {
        if (owned == null || owned.isEmpty()) {
            return false;
        }
        if (anchor == null || !owned.contains(anchor)) {
            return false;
        }
        Set<CityPlot> seen = new LinkedHashSet<>();
        List<CityPlot> pending = new ArrayList<>();
        pending.add(anchor);
        seen.add(anchor);
        while (!pending.isEmpty()) {
            for (CityPlot neighbour : pending.remove(pending.size() - 1).neighbours()) {
                if (owned.contains(neighbour) && seen.add(neighbour)) {
                    pending.add(neighbour);
                }
            }
        }
        return seen.size() == owned.size();
    }

    /**
     * Outer boundary of a claim set as unit segments on the 50-block grid.
     *
     * <p>An edge of a plot is returned only when the neighbouring plot is not claimed, so a 3×3 city
     * yields the outline of a 3×3 block rather than a 3×3 grid of nine squares. Internal borders
     * stay invisible, which is the difference between a territory and a chessboard.
     *
     * <p>Each segment is {@code [x1, z1, x2, z2]} in block coordinates along a single axis.
     */
    public static List<int[]> outline(Collection<CityPlot> owned) {
        List<int[]> edges = new ArrayList<>();
        if (owned == null || owned.isEmpty()) {
            return edges;
        }
        for (CityPlot plot : owned) {
            // North edge (towards lower Z) and south edge (towards higher Z).
            if (!owned.contains(plot.offset(0, -1))) {
                edges.add(new int[]{plot.minX(), plot.minZ(), plot.maxX() + 1, plot.minZ()});
            }
            if (!owned.contains(plot.offset(0, 1))) {
                edges.add(new int[]{plot.minX(), plot.maxZ() + 1, plot.maxX() + 1, plot.maxZ() + 1});
            }
            // West edge and east edge.
            if (!owned.contains(plot.offset(-1, 0))) {
                edges.add(new int[]{plot.minX(), plot.minZ(), plot.minX(), plot.maxZ() + 1});
            }
            if (!owned.contains(plot.offset(1, 0))) {
                edges.add(new int[]{plot.maxX() + 1, plot.minZ(), plot.maxX() + 1, plot.maxZ() + 1});
            }
        }
        return Collections.unmodifiableList(edges);
    }

    /** Total number of blocks a claim set covers, 2500 per plot. */
    public static long blockCount(Collection<CityPlot> owned) {
        return (long) owned.size() * CityPlot.PLOT_SIZE * CityPlot.PLOT_SIZE;
    }
}
