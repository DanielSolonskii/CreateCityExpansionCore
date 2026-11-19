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
        final RenderType pass = TerrainRenderType.page(id);
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
        void prefer(){lastUse=++use;}
    }
    private record Quad(float left,float top,float right,float bottom,float u0,float v0,float u1,float v1,
                        boolean smooth,boolean coarse,float alpha) {}
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
        if(oldest!=null){
            if (oldest.owner instanceof TerrainTileTexture previous) previous.close();
            oldest.owner=owner;oldest.pin();
        }
        return oldest;
    }
    static void upload(Slot slot,int[] colors,Slot[] neighbours) {
        for(int z=0;z<32;z++) for(int x=0;x<32;x++)
            slot.page.image.setPixelRGBA(slot.x+1+x,slot.z+1+z,TerrainTileTexture.toNativeImageColor(colors[z*32+x]));
        writeGutters(slot,neighbours);
        uploadCell(slot);
    }

    /** Only the eight true world neighbours can supply edge colours, even on different GPU pages. */
    private static boolean writeGutters(Slot slot,Slot[] neighbours) {
        boolean changed=false;
        for(int z=0;z<34;z++) for(int x=0;x<34;x+=z==0 || z==33 ? 1 : 33) {
            int dx=x==0?-1:x==33?1:0, dz=z==0?-1:z==33?1:0;
            Slot from=neighbours[(dz+1)*3+dx+1];
            int sx=x,sz=z;
            if(from==null) {
                from=slot;
                sx=Math.clamp(x,1,32); sz=Math.clamp(z,1,32);
            } else {
                sx-=dx*32; sz-=dz*32;
            }
            int pixel=from.page.image.getPixelRGBA(from.x+sx,from.z+sz);
            if(slot.page.image.getPixelRGBA(slot.x+x,slot.z+z)!=pixel) {
                slot.page.image.setPixelRGBA(slot.x+x,slot.z+z,pixel);
                changed=true;
            }
        }
        return changed;
    }

    static void refreshGutters(Slot slot,Slot[] neighbours) {
        if(writeGutters(slot,neighbours)) uploadCell(slot);
    }

    private static void uploadCell(Slot slot) {
        int cell=AtlasMapScale.ATLAS_CELL;
        slot.page.texture.bind();
        slot.page.image.upload(0,slot.x,slot.z,slot.x,slot.z,cell,cell,false,true,false,false);
        slot.page.texture.setFilter(false,false);
    }
    static void queue(Slot slot,double left,double top,double right,double bottom,float u,float v,float size,
                      boolean smooth,boolean coarse,float alpha) {
        slot.pin();
        float scale=1f/AtlasMapScale.ATLAS_SIZE;
        slot.page.quads.add(new Quad((float)left,(float)top,(float)right,(float)bottom,(slot.x+1+u)*scale,(slot.z+1+v)*scale,
                (slot.x+1+u+size)*scale,(slot.z+1+v+size)*scale,smooth,coarse,Math.clamp(alpha,0,1)));
    }
    /**
     * Submit through Minecraft's managed renderer, one batch per occupied page.
     * A raw POSITION_TEX draw inside GuiGraphics' managed batch bypasses render-type setup and
     * shader-mod hooks, and used to leave depth/blend/shader state behind for city/player markers.
     * The terrain RenderType owns its shader, sampler, depth and blend state.
     */
    static void flush(GuiGraphics g) {
        g.flush();
        var matrix=g.pose().last().pose();
        for(Page page:PAGES) {
            if(page.quads.isEmpty())continue;
            var buffer=g.bufferSource().getBuffer(page.pass);
            for(Quad q:page.quads) {
                float smooth=q.smooth?1:0, coarse=q.coarse?1:0;
                buffer.addVertex(matrix,q.left,q.top,0).setUv(q.u0,q.v0).setColor(smooth,coarse,1,q.alpha);
                buffer.addVertex(matrix,q.left,q.bottom,0).setUv(q.u0,q.v1).setColor(smooth,coarse,1,q.alpha);
                buffer.addVertex(matrix,q.right,q.bottom,0).setUv(q.u1,q.v1).setColor(smooth,coarse,1,q.alpha);
                buffer.addVertex(matrix,q.right,q.top,0).setUv(q.u1,q.v0).setColor(smooth,coarse,1,q.alpha);
            }
            page.quads.clear();
        }
        // Finish the terrain before later gui/text buffers can cover or reorder its markers.
        g.flush();
    }
    static int pages() { return PAGES.size(); }
    static int occupied() {
        int count = 0;
        for (Page page : PAGES) for (Slot slot : page.slots) if (slot.owner != null) count++;
        return count;
    }
    static void reset() {
        for(Page page:PAGES) {
            for(Slot slot:page.slots) {
                if (slot.owner instanceof TerrainTileTexture texture) texture.close();
                slot.owner=null;
            }
            Minecraft.getInstance().getTextureManager().release(page.id);
        }
        PAGES.clear();
    }
}
