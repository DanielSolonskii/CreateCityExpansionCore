package com.example.examplemod.client.map;

import com.example.examplemod.network.CityMapTilePayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * One GPU texture per atlas tile, built once from the tile's colour samples and then stretched over
 * the tile's screen rectangle.
 *
 * <p>The previous renderer issued one {@code fill()} per sample, so a screen full of terrain cost
 * thousands of individual GUI rectangles every frame. A tile is now a single blit, and the level of
 * detail is a property of the texture: when the map is zoomed out the texture is rebuilt once with
 * area-averaged samples, which removes the aliasing nearest-neighbour downsampling produces and
 * keeps far zoom readable instead of merely coarser.
 *
 * <p>The texture is derived data — it is rebuilt from the cached samples whenever the level of
 * detail changes — and {@link #close()} hands it back to the {@link net.minecraft.client.renderer.texture.TextureManager},
 * which both unregisters and closes it, so a tile that leaves the cache never leaks a GPU texture.
 *
 * <p>Samples reach the GPU already run through {@link MapSurveyTransform}, so the terrain arrives on
 * screen as drafting paper rather than as a photograph. The transform is applied per texel after
 * averaging rather than per sample before it: averaging first and transforming second keeps the level
 * of detail stable, because a transformed average is not the average of transformed samples.
 */
final class TerrainTileTexture {
    /** Texels per side; one texel per sample, so the texture always maps 1:1 onto the sample grid. */
    static final int SIZE = CityMapTilePayload.SAMPLE_COUNT;

    private final String name;
    private DynamicTexture texture;
    private NativeImage pixels;
    private ResourceLocation id;
    private int builtStride = -1;

    TerrainTileTexture(String name) { this.name = name; }

    /** The registered texture identifier, or null while it has not been built yet. */
    ResourceLocation id() { return this.id; }

    /** True when this texture already represents the tile at the wanted level of detail. */
    boolean matches(int stride) { return this.texture != null && this.builtStride == stride; }

    /** Marks the texture stale so the next frame rebuilds it, e.g. after new samples arrived. */
    void invalidate() { this.builtStride = -1; }

    /**
     * Rebuilds the texture for {@code stride}. Stride 1 copies the samples one-to-one; a larger
     * stride averages each {@code stride × stride} block into every texel of that block, which is an
     * area-average LOD rather than a point sample — no shimmer while panning, and no squares
     * appearing when a biome boundary lands between two samples.
     */
    void build(int[] colors, java.util.BitSet contours, int stride) {
        int step = Math.max(1, Math.min(stride, SIZE));
        if (this.texture == null) {
            this.pixels = new NativeImage(SIZE, SIZE, false);
            this.texture = new DynamicTexture(this.pixels);
            this.id = Minecraft.getInstance().getTextureManager().register(this.name, this.texture);
        }
        for (int z = 0; z < SIZE; z++) {
            int baseZ = (z / step) * step;
            for (int x = 0; x < SIZE; x++) {
                int baseX = (x / step) * step;
                this.pixels.setPixelRGBA(x, z, toNativeImageColor(
                        MapSurveyTransform.survey(
                                areaAverage(colors, baseX, baseZ, step)
                        )
                ));
            }
        }
        /*
         * Горизонтали ложатся поверх бумаги, а не смешиваются с ней.
         *
         * Смешивание убило бы ровно то, ради чего они нужны: на отдалённом зуме соседние
         * сэмплы усредняются в один тексель, и разбавленная линия перестаёт читаться как
         * линия. Поэтому линия рисуется последним проходом и в полную силу, а на сильном
         * укрупнении сглаживается по площади — иначе на одном блоке высоты она выглядела бы
         * как щетина.
         */
        drawContours(contours, step);
        this.texture.upload();
        this.builtStride = step;
    }

    /**
     * Рисует горизонтали как чернила поверх уже готовой подложки.
     *
     * <p>Покрытие считается по той же области текселя, что и усреднение цвета, поэтому линия
     * всегда попадает в те же блоки, что и рельеф, который она описывает. Тексель, где линия
     * занимает меньше половины, остаётся бумагой: половинка линии — это уже размытая полоса,
     * а не линия.
     */
    private void drawContours(java.util.BitSet contours, int step) {
        if (contours == null || contours.isEmpty()) {
            return;
        }
        for (int z = 0; z < SIZE; z++) {
            int baseZ = (z / step) * step;
            for (int x = 0; x < SIZE; x++) {
                int baseX = (x / step) * step;
                int marked = 0;
                int limitX = Math.min(SIZE, baseX + step);
                int limitZ = Math.min(SIZE, baseZ + step);
                int samples = (limitX - baseX) * (limitZ - baseZ);
                for (int z1 = baseZ; z1 < limitZ; z1++) {
                    int row = z1 * SIZE;
                    for (int x1 = baseX; x1 < limitX; x1++) {
                        if (contours.get(row + x1)) {
                            marked++;
                        }
                    }
                }
                if (marked * 2 < samples) {
                    continue;
                }
                int existing = this.pixels.getPixelRGBA(x, z);
                this.pixels.setPixelRGBA(x, z, toNativeImageColor(
                        MapSurveyTransform.surveyInk(fromNativeImageColor(existing))
                ));
            }
        }
    }

    /**
     * Averages one {@code step × step} block of samples, clamped at the tile edge. Unknown samples
     * take part in the average like any other value: mixing them in is what makes an explored border
     * fade instead of showing a hard staircase of half-known squares.
     */
    private static int areaAverage(int[] colors, int baseX, int baseZ, int step) {
        int limitX = Math.min(SIZE, baseX + step);
        int limitZ = Math.min(SIZE, baseZ + step);
        long red = 0, green = 0, blue = 0, alpha = 0;
        int samples = 0;
        for (int z = baseZ; z < limitZ; z++) {
            int row = z * SIZE;
            for (int x = baseX; x < limitX; x++) {
                int color = colors[row + x];
                alpha += color >>> 24 & 0xFF;
                red += color >> 16 & 0xFF;
                green += color >> 8 & 0xFF;
                blue += color & 0xFF;
                samples++;
            }
        }
        if (samples == 0) {
            return com.example.examplemod.city.CityMapSavedData.UNKNOWN_COLOR;
        }
        return (int) ((alpha / samples) << 24 | (red / samples) << 16 | (green / samples) << 8 | blue / samples);
    }

    /**
     * The single conversion from our 0xAARRGGBB terrain colour into the packed value
     * {@link NativeImage#setPixelRGBA} expects. Used by nothing else in the map.
     *
     * <p>The method name is misleading and that cost us a very visible bug. {@code setPixelRGBA}
     * does not interpret its argument as an RGBA-packed int; it is a raw
     * {@code MemoryUtil.memPutInt} of four little-endian bytes that OpenGL then reads as the RGBA
     * quad. The first byte in memory is the low byte of the int, and that byte is R, so red has to
     * live in the <em>low</em> byte and the int has to be packed {@code 0xAABBGGRR} — which is
     * exactly {@link net.minecraft.util.FastColor#ABGR32}'s layout ({@code ABGR32.red(x) == x & 0xFF}).
     *
     * <p>Packing it as {@code 0xRRGGBBAA} instead rotates every channel, which turned grass
     * {@code #689739} into {@code #FF3997} magenta and water {@code #373A97} into {@code #FF973A}
     * orange, while neutral greys looked fine and hid the defect.
     */
    static int toNativeImageColor(int argb) {
        int alpha = argb >>> 24;
        int red = argb >> 16 & 0xFF;
        int green = argb >> 8 & 0xFF;
        int blue = argb & 0xFF;
        return FastColor.ABGR32.color(alpha, blue, green, red);
    }

    /**
     * The exact inverse of {@link #toNativeImageColor}, needed because the contour pass reads a
     * texel back out of the image it just wrote. Round-tripping through the inverse instead of
     * mixing in the packed layout keeps the packing bug documented above from having a second,
     * silent version inside the overlay pass.
     */
    static int fromNativeImageColor(int packed) {
        return FastColor.ABGR32.alpha(packed) << 24
                | FastColor.ABGR32.red(packed) << 16
                | FastColor.ABGR32.green(packed) << 8
                | FastColor.ABGR32.blue(packed);
    }

    /** Hands the texture back to the texture manager, which unregisters and closes it. */
    void close() {
        if (this.texture != null && this.id != null) {
            Minecraft.getInstance().getTextureManager().release(this.id);
        }
        this.texture = null;
        this.pixels = null;
        this.id = null;
        this.builtStride = -1;
    }
}