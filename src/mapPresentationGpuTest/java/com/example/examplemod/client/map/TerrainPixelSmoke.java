package com.example.examplemod.client.map;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

/** Invoked from the isolated QA client's title screen render, after shader registration. */
public final class TerrainPixelSmoke {
    private static final int LEFT=16, TOP=15, LENGTH=128;
    private static final int RED=0xffff0000, BLUE=0xff0000ff, GREEN=0xff00ff00;
    private static void require(boolean ok,String reason) { if (!ok) throw new AssertionError(reason); }

    private static int pixel(double x,double y) {
        var window=Minecraft.getInstance().getWindow();
        int sx=(int)Math.floor(x*window.getGuiScale());
        int sy=window.getHeight()-1-(int)Math.floor(y*window.getGuiScale());
        try (var stack=MemoryStack.stackPush()) {
            var p=stack.malloc(4);
            GL11.glReadPixels(sx,sy,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);
            return (p.get(0)&255)<<16 | (p.get(1)&255)<<8 | (p.get(2)&255);
        }
    }
    private static int sample(double x) { return pixel(LEFT+x, TOP+64.5); }
    private static void paint(GuiGraphics g,TerrainAtlas.Slot slot,boolean smooth,boolean coarse) {
        g.fill(LEFT-2,TOP-2,LEFT+LENGTH+2,TOP+LENGTH+2,0xff15273b); g.flush();
        TerrainAtlas.begin();
        TerrainAtlas.queue(slot,LEFT,TOP,LEFT+LENGTH,TOP+LENGTH,0,0,32,smooth,coarse,1);
        TerrainAtlas.flush(g);
    }
    public static void run(GuiGraphics g) {
        int[] terrain=new int[1024], adjacent=new int[1024]; Arrays.fill(adjacent,GREEN);
        for (int i=0;i<terrain.length;i++) {
            int x=i%32;
            terrain[i]=x<8?RED:x<16?BLUE:x<24?0:x==26?0x80ff0000:RED;
        }
        TerrainAtlas.begin();
        var slot=TerrainAtlas.acquire(new Object()); var neighbour=TerrainAtlas.acquire(new Object());
        var a=new TerrainAtlas.Slot[9]; var b=new TerrainAtlas.Slot[9];
        TerrainAtlas.upload(slot,terrain,a); TerrainAtlas.upload(neighbour,adjacent,b);
        try {
            paint(g,slot,false,false);
            int sharp=sample(31.25), background=sample(80.25);
            require((sharp>>16)>240 && (sharp&255)<6,"nearest mode pixel="+Integer.toHexString(sharp));
            require(background!=sharp,"unknown area painted as known");
            paint(g,slot,true,false);
            int smooth=sample(31.25), edge=sample(127.25);
            require((smooth>>16)>40 && (smooth&255)>30 && smooth!=sharp,"smooth mode not interpolated: "+Integer.toHexString(smooth));
            require(sample(64.25)==background && sample(95.75)==background,"smoothing disclosed an unknown texel");
            require((edge>>16)>240 && (edge&65535)<1030,"adjacent atlas cell leaked: "+Integer.toHexString(edge));
            require(sample(106.25)!=background,"measured partial terrain not drawn");
            paint(g,slot,true,true);
            require(sample(106.25)==background,"overview fallback exposed uncertain fine cell");
            require((sample(20.25)>>16)>240,"fully covered overview fallback lost");
            g.fill(LEFT,TOP,LEFT+LENGTH,TOP+LENGTH,0xff15273b); g.flush();
            TerrainAtlas.begin();
            double seam=LEFT+63.375;
            TerrainAtlas.queue(slot,LEFT,TOP,seam,TOP+LENGTH,0,0,4,true,false,1);
            TerrainAtlas.queue(slot,seam,TOP,LEFT+LENGTH,TOP+LENGTH,0,0,4,true,false,1);
            TerrainAtlas.flush(g);
            for (double x=seam-2;x<=seam+2;x+=.22) require((pixel(x,TOP+40.25)>>16)>240,"hole at fractional tile boundary");
            // Now the same slots represent actual map neighbours. A colour ramp crosses the
            // boundary continuously; an unrelated adjacent atlas slot above had no influence.
            a[5]=neighbour; b[3]=slot;
            TerrainAtlas.refreshGutters(slot,a); TerrainAtlas.refreshGutters(neighbour,b);
            g.fill(LEFT,TOP,LEFT+LENGTH,TOP+LENGTH,0xff15273b); g.flush();
            TerrainAtlas.begin();
            TerrainAtlas.queue(slot,LEFT,TOP,LEFT+64,TOP+LENGTH,16,0,16,true,false,1);
            TerrainAtlas.queue(neighbour,LEFT+64,TOP,LEFT+128,TOP+LENGTH,0,0,16,true,false,1);
            TerrainAtlas.flush(g);
            int mixedA=sample(63.5),mixedB=sample(64.5);
            require((mixedA>>16)>40 && (mixedA>>8&255)>30 && (mixedB>>16)>30 && (mixedB>>8&255)>40,
                    "world-border filter discontinuity "+Integer.toHexString(mixedA)+" / "+Integer.toHexString(mixedB));
            System.out.printf("TERRAIN_GL_PASS sharp=%06x smooth=%06x unknown=%06x edge=%06x%n",sharp,smooth,background,edge);
        } finally { slot.owner=null; neighbour.owner=null; }
    }
}
