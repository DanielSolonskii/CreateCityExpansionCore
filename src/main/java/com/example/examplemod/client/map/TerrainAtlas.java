package com.example.examplemod.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Render-thread atlas. Four 1024² pages maximum: 16 MiB GPU, independent of explored world size. */
final class TerrainAtlas {
    private TerrainAtlas() {}
    private static final List<Page> PAGES=new ArrayList<>();
    private static long frame, use;
    private static final class Page {
        final NativeImage image=new NativeImage(AtlasMapScale.ATLAS_SIZE,AtlasMapScale.ATLAS_SIZE,false);
        final DynamicTexture texture=new DynamicTexture(image);
        final ResourceLocation id=Minecraft.getInstance().getTextureManager().register("ccec_terrain_atlas",texture);
        final List<Slot> slots=new ArrayList<>();
        final List<Quad> quads=new ArrayList<>();
        Page() {
            texture.setFilter(false,false);
            int side=AtlasMapScale.ATLAS_SIZE/AtlasMapScale.ATLAS_CELL;
            for(int z=0;z<side;z++)for(int x=0;x<side;x++)slots.add(new Slot(this,x*AtlasMapScale.ATLAS_CELL,z*AtlasMapScale.ATLAS_CELL));
        }
    }
    static final class Slot {
        final Page page; final int x,z;
        Object owner;
        long usedFrame, lastUse;
        Slot(Page page,int x,int z){this.page=page;this.x=x;this.z=z;}
        boolean belongs(Object value){return owner==value;}
        ResourceLocation id(){return page.id;}
        void pin(){usedFrame=frame;lastUse=++use;}
    }
    private record Quad(int left,int top,int right,int bottom,float u0,float v0,float u1,float v1,float alpha) {}
    static void begin() { frame++; PAGES.forEach(page->page.quads.clear()); }
    static Slot acquire(Object owner) {
        Slot oldest=null;
        for(Page page:PAGES)for(Slot slot:page.slots) {
            if(slot.owner==null){slot.owner=owner;slot.pin();return slot;}
            if(slot.usedFrame!=frame&&(oldest==null||slot.lastUse<oldest.lastUse))oldest=slot;
        }
        if(PAGES.size()<AtlasMapScale.ATLAS_PAGES) {
            Page page=new Page();PAGES.add(page);Slot slot=page.slots.getFirst();slot.owner=owner;slot.pin();return slot;
        }
        if(oldest!=null){oldest.owner=owner;oldest.pin();}
        return oldest;
    }
    static void upload(Slot slot,int[] colors) {
        // One replicated-texel gutter keeps adjacent tiles from bleeding into each other.
        int cell=AtlasMapScale.ATLAS_CELL;
        for(int z=0;z<cell;z++)for(int x=0;x<cell;x++) {
            int sx=Math.max(0,Math.min(31,x-1)),sz=Math.max(0,Math.min(31,z-1));
            slot.page.image.setPixelRGBA(slot.x+x,slot.z+z,TerrainTileTexture.toNativeImageColor(colors[sz*32+sx]));
        }
        slot.page.texture.bind();
        slot.page.image.upload(0,slot.x,slot.z,slot.x,slot.z,cell,cell,false,true,false,false);
        slot.page.texture.setFilter(false,false);
    }
    static void queue(Slot slot,int left,int top,int right,int bottom,float u,float v,float size) {
        queue(slot,left,top,right,bottom,u,v,size,1);
    }
    static void queue(Slot slot,int left,int top,int right,int bottom,float u,float v,float size,float alpha) {
        slot.pin();
        float scale=1f/AtlasMapScale.ATLAS_SIZE;
        slot.page.quads.add(new Quad(left,top,right,bottom,(slot.x+1+u)*scale,(slot.z+1+v)*scale,
                (slot.x+1+u+size)*scale,(slot.z+1+v+size)*scale,Math.clamp(alpha,0,1)));
    }
    /**
     * Submit through Minecraft's managed renderer, one batch per occupied page.
     * A raw POSITION_TEX draw inside GuiGraphics' managed batch bypasses render-type setup and
     * shader-mod hooks, and used to leave depth/blend/shader state behind for city/player markers.
     * The built-in full-bright, see-through textured type owns and restores that state; it is not
     * an actual text draw (there are no glyphs), only the matching POSITION_COLOR_TEX_LIGHTMAP type.
     */
    static void flush(GuiGraphics g) {
        g.flush();
        var matrix=g.pose().last().pose();
        for(Page page:PAGES) {
            if(page.quads.isEmpty())continue;
            var buffer=g.bufferSource().getBuffer(RenderType.textSeeThrough(page.id));
            for(Quad q:page.quads) {
                buffer.addVertex(matrix,q.left,q.top,0).setColor(1f,1f,1f,q.alpha).setUv(q.u0,q.v0).setLight(0xF000F0);
                buffer.addVertex(matrix,q.left,q.bottom,0).setColor(1f,1f,1f,q.alpha).setUv(q.u0,q.v1).setLight(0xF000F0);
                buffer.addVertex(matrix,q.right,q.bottom,0).setColor(1f,1f,1f,q.alpha).setUv(q.u1,q.v1).setLight(0xF000F0);
                buffer.addVertex(matrix,q.right,q.top,0).setColor(1f,1f,1f,q.alpha).setUv(q.u1,q.v0).setLight(0xF000F0);
            }
            page.quads.clear();
        }
        // Finish the terrain before later gui/text buffers can cover or reorder its markers.
        g.flush();
    }
    static void reset() {
        for(Page page:PAGES) {
            for(Slot slot:page.slots)slot.owner=null;
            Minecraft.getInstance().getTextureManager().release(page.id);
        }
        PAGES.clear();
    }
}
