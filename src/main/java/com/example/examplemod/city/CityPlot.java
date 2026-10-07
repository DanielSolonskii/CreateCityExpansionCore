package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * One square claim of {@link #PLOT_SIZE}×{@link #PLOT_SIZE} blocks.
 *
 * <p><b>Plots live on the world grid, not on the pylon.</b> The grid is anchored at world
 * coordinate 0 and runs in both directions, so plot {@code (0,0)} always covers blocks
 * {@code 0..49} and plot {@code (-1,-1)} always covers {@code -50..-1}. A pylon dropped at
 * {@code -1} therefore sits in plot {@code (-1,-1)} and not in a pylon-relative square, and the
 * same block keeps the same plot whatever city is built next to it. That is what makes two cities
 * able to detect an overlap and what makes a claim reproducible across restarts.
 *
 * <p><b>Division is floor division, not truncation.</b> {@code blockX / 50} in Java truncates
 * towards zero, so it maps {@code -1} to {@code 0} — which would hand the block {@code -1..49} to
 * one plot and make {@code -50} and {@code -1} neighbours. {@link Math#floorDiv(int, int)} is used
 * everywhere instead, so every negative coordinate lands in the plot below/left of zero exactly
 * like the vanilla chunk grid does.
 *
 * <p>Immutable and safe to share between server and client. Plot coordinates are validated against
 * {@link #MAX_PLOT_COORDINATE} so a hostile client cannot overflow {@code plotX * PLOT_SIZE}.
 */
public record CityPlot(
        ResourceLocation dimension,
        int plotX,
        int plotZ
) implements Comparable<CityPlot> {

    /**
     * Edge length of one claim, in blocks.
     *
     * <p>Deliberately unrelated to the vanilla 16×16 chunk: a claim is a gameplay boundary, not a
     * storage unit, and tying the two together would make every chunk boundary a claim edge.
     */
    public static final int PLOT_SIZE = 50;

    /** Last block coordinate inside a plot. {@code min + PLOT_SIZE - 1}, so 50 blocks per plot. */
    public static final int PLOT_MAX_OFFSET = PLOT_SIZE - 1;

    /**
     * Largest plot coordinate accepted from anywhere, including the network.
     *
     * <p>The vanilla world border stops at 29,999,984 blocks, which is plot 599,999. The bound is
     * set an order of magnitude above that purely so {@code plotX * PLOT_SIZE} can never overflow
     * an int while converting a coordinate back to blocks.
     */
    public static final int MAX_PLOT_COORDINATE = 1_000_000;

    public CityPlot {
        Objects.requireNonNull(dimension, "dimension");
    }

    /** Plot containing a block X coordinate. Floor division, so it is correct below zero. */
    public static int plotXOf(int blockX) {
        return Math.floorDiv(blockX, PLOT_SIZE);
    }

    /** Plot containing a block Z coordinate. Floor division, so it is correct below zero. */
    public static int plotZOf(int blockZ) {
        return Math.floorDiv(blockZ, PLOT_SIZE);
    }

    /** Plot containing a block position. Y is irrelevant: a claim is a column of the world. */
    public static CityPlot at(ResourceLocation dimension, int blockX, int blockZ) {
        return new CityPlot(dimension, plotXOf(blockX), plotZOf(blockZ));
    }

    /** Plot containing a block position, ignoring Y. */
    public static CityPlot at(ResourceLocation dimension, BlockPos pos) {
        return at(dimension, pos.getX(), pos.getZ());
    }

    /** First block X of this plot. {@code plotX * 50}. */
    public int minX() {
        return this.plotX * PLOT_SIZE;
    }

    /** Last block X of this plot, inclusive. Exactly {@link #PLOT_SIZE} blocks wide. */
    public int maxX() {
        return minX() + PLOT_MAX_OFFSET;
    }

    /** First block Z of this plot. */
    public int minZ() {
        return this.plotZ * PLOT_SIZE;
    }

    /** Last block Z of this plot, inclusive. */
    public int maxZ() {
        return minZ() + PLOT_MAX_OFFSET;
    }

    /** Centre of the plot in block coordinates, used for marker placement on the map. */
    public int centerX() {
        return minX() + PLOT_SIZE / 2;
    }

    public int centerZ() {
        return minZ() + PLOT_SIZE / 2;
    }

    /** First chunk X this plot touches. */
    public int minChunkX() {
        return Math.floorDiv(minX(), 16);
    }

    /** Last chunk X this plot touches. */
    public int maxChunkX() {
        return Math.floorDiv(maxX(), 16);
    }

    public int minChunkZ() {
        return Math.floorDiv(minZ(), 16);
    }

    public int maxChunkZ() {
        return Math.floorDiv(maxZ(), 16);
    }

    public boolean contains(int blockX, int blockZ) {
        return blockX >= minX() && blockX <= maxX() && blockZ >= minZ() && blockZ <= maxZ();
    }

    /**
     * True when this plot shares a full side with {@code other}.
     *
     * <p>Diagonal contact is deliberately excluded: a corner-touching plot would let a city
     * sprinkle single plots across a map, and a plot touching only at a corner is not a defensible
     * extension of a contiguous territory.
     */
    public boolean isAdjacentTo(CityPlot other) {
        if (other == null || !this.dimension.equals(other.dimension)) {
            return false;
        }
        int dx = Math.abs(this.plotX - other.plotX);
        int dz = Math.abs(this.plotZ - other.plotZ);
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    /** The four side neighbours, in a fixed order, for adjacency checks and outline rendering. */
    public java.util.List<CityPlot> neighbours() {
        return java.util.List.of(
                new CityPlot(dimension, plotX + 1, plotZ),
                new CityPlot(dimension, plotX - 1, plotZ),
                new CityPlot(dimension, plotX, plotZ + 1),
                new CityPlot(dimension, plotX, plotZ - 1)
        );
    }

    public CityPlot offset(int deltaX, int deltaZ) {
        return new CityPlot(dimension, this.plotX + deltaX, this.plotZ + deltaZ);
    }

    /**
     * Whether the plot coordinates can be turned into block coordinates without overflow and stay
     * inside the world. Checked before any claim reaches the ownership index.
     */
    public boolean isValid() {
        return Math.abs((long) this.plotX) <= MAX_PLOT_COORDINATE
                && Math.abs((long) this.plotZ) <= MAX_PLOT_COORDINATE;
    }

    /**
     * Two plots packed into one long, dimension excluded.
     *
     * <p>Used by the client cache and by anything that wants a cheap map key; callers that mix
     * dimensions must keep the dimension in the key themselves.
     */
    public long packedXZ() {
        return ((long) this.plotX << 32) | (this.plotZ & 0xFFFFFFFFL);
    }

    /** Smallest grid index first, so a saved city serialises its claims in a stable order. */
    @Override
    public int compareTo(CityPlot other) {
        int byZ = Integer.compare(this.plotZ, other.plotZ);
        return byZ != 0 ? byZ : Integer.compare(this.plotX, other.plotX);
    }

    @Override
    public String toString() {
        return this.plotX + "," + this.plotZ + "@" + this.dimension.getNamespace()
                + ":" + this.dimension.getPath();
    }
}
