package com.example.examplemod.client.map;

import com.example.examplemod.network.CityMapTilePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/** One texture per LOD tile. Raster work is bounded and asynchronous; uploads stay on the render thread.
 * Raw colours and elevations remain in the disk cache independently of GPU lifetime. */
final class TerrainTileTexture {
    /** Texels per side; one texel per sample, so the texture always maps 1:1 onto the sample grid. */
    static final int SIZE = CityMapTilePayload.SAMPLE_COUNT;

    private final String name;
    private TerrainAtlas.Slot slot;
    private boolean uploaded;
    private int builtStride = -1;

    TerrainTileTexture(String name) { this.name = name; }

    /** The registered texture identifier, or null while it has not been built yet. */
    ResourceLocation id() { return valid() && uploaded ? slot.id() : null; }
    private boolean valid() { return slot!=null&&slot.belongs(this); }
    void pin() { if(valid())slot.pin(); }
    void draw(int left,int top,int right,int bottom,float u,float v,float size) {
        if(valid())TerrainAtlas.queue(slot,left,top,right,bottom,u,v,size);
    }

    /** True when this texture already represents the tile at the wanted level of detail. */
    boolean matches(int stride) { return valid() && uploaded && this.builtStride == stride; }

    /** Marks the texture stale so the next frame rebuilds it, e.g. after new samples arrived. */
    void invalidate() { this.builtStride = -1;if(pending!=null){pending.cancel(false);pending=null;} }

    private static final java.util.concurrent.ThreadPoolExecutor WORK=new java.util.concurrent.ThreadPoolExecutor(1,2,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"CCEC terrain raster");t.setDaemon(true);return t;});
    private java.util.concurrent.Future<int[]> pending;
    private int pendingStride;
    void build(int[] colors,java.util.BitSet contours,int stride){build(colors,contours,stride,new short[0],0);}
    void build(int[] colors,java.util.BitSet contours,int stride,short[] heights,int level) {
        if(pending==null){
            int[] copy=colors.clone();short[] h=heights.clone();pendingStride=stride;
            try{pending=WORK.submit(()->h.length==1156?TerrainRelief.raster(copy,h,level):raster(copy,contours,stride));}catch(java.util.concurrent.RejectedExecutionException busy){return;}
        }
        if(!pending.isDone())return;
        if(!valid()){slot=TerrainAtlas.acquire(this);uploaded=false;if(slot==null)return;}
        int[] raster;
        try{raster=pending.get();}catch(Exception failed){pending=null;return;}pending=null;
        TerrainAtlas.upload(slot,raster);uploaded=true;this.builtStride=pendingStride;
    }

    /**
     * Рисует горизонтали как чернила поверх уже готовой подложки.
     *
     * <p>Покрытие считается по той же области текселя, что и усреднение цвета, поэтому линия
     * всегда попадает в те же блоки, что и рельеф, который она описывает. Тексель, где линия
     * занимает меньше половины, остаётся бумагой: половинка линии — это уже размытая полоса,
     * а не линия.
     */
    static int[] raster(int[] colors, java.util.BitSet contours, int stride) {
        int step = Math.max(1, Math.min(stride, SIZE));
        int[] result = new int[SIZE * SIZE];
        colors = TerrainSurfaceFilter.smooth(colors,SIZE);
        // Average and count contour coverage once per LOD cell, rather than once per texel.
        for (int baseZ = 0; baseZ < SIZE; baseZ += step) for (int baseX = 0; baseX < SIZE; baseX += step) {
            int limitX = Math.min(SIZE, baseX + step), limitZ = Math.min(SIZE, baseZ + step);
            int color = MapSurveyTransform.survey(areaAverage(colors, baseX, baseZ, step));
            if (step < 8 && contours != null && !contours.isEmpty()) {
                int marked = 0;
                for (int z = baseZ; z < limitZ; z++) for (int x = baseX; x < limitX; x++)
                    if (contours.get(z * SIZE + x)) marked++;
                if (marked * 2 >= (limitX - baseX) * (limitZ - baseZ)) color = MapSurveyTransform.surveyInk(color);
            }
            for (int z = baseZ; z < limitZ; z++) java.util.Arrays.fill(result, z * SIZE + baseX, z * SIZE + limitX, color);
        }
        return result;
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
        if(pending!=null){pending.cancel(false);pending=null;}
        if(valid())slot.owner=null;
        this.slot = null;
        this.uploaded = false;
        this.builtStride = -1;
    }
}
