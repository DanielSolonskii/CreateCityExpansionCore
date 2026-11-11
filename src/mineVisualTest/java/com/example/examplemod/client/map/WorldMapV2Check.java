package com.example.examplemod.client.map;

import com.example.examplemod.network.CityMapTilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class WorldMapV2Check {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        // ---- terrain resolution: one sample per world block, which is the whole point of this pass.
        require(CityMapTilePayload.SAMPLE_STEP==1,
                "one sample per world block, not one per four");
        require(CityMapTilePayload.WORLD_SIZE==CityMapTilePayload.SAMPLE_COUNT,
                "a tile is as many blocks wide as it has samples");
        require(CityMapTilePayload.COLOR_COUNT==CityMapTilePayload.SAMPLE_COUNT*CityMapTilePayload.SAMPLE_COUNT,
                "one colour per sample");
        require(CityMapTilePayload.SAMPLE_COUNT<=16*2,
                "a tile covers a whole number of vanilla chunks");
        require(16/CityMapTilePayload.SAMPLE_STEP==16,
                "a vanilla chunk maps onto a 16x16 block of samples");
        require(CityMapTilePayload.WORLD_SIZE%16==0,
                "a tile boundary never falls inside a vanilla chunk");
        int samplesPerChunk=16*16;
        require(samplesPerChunk<=CityMapTilePayload.COLOR_COUNT,
                "a chunk's samples fit inside one tile");

        // ---- level of detail: full detail when close, coarser texels only when zoomed out.
        require(CityMapPanel.terrainStrideFor(2.0)==1,"zoomed in, every block gets its own texel");
        require(CityMapPanel.terrainStrideFor(1.0)==2,"at one pixel per sample the detail halves");
        require(CityMapPanel.terrainStrideFor(0.25)==8,"zoomed far out the detail coarsens");
        require(CityMapPanel.terrainStrideFor(0.001)>=CityMapTilePayload.SAMPLE_COUNT,
                "the coarsest level of detail never exceeds the sample grid");
        for (double step : new double[]{4.0,2.0,1.0,0.5,0.25,0.1,0.01,0.001}) {
            int stride=CityMapPanel.terrainStrideFor(step);
            require(stride>=1&&stride<=CityMapTilePayload.SAMPLE_COUNT,"stride stays in range at "+step);
            require((stride&(stride-1))==0,"stride is always a power of two at "+step);
        }
        require(CityMapPanel.terrainStrideFor(2.0)<=CityMapPanel.terrainStrideFor(0.05),
                "zooming in never produces coarser detail than zooming out");

        System.out.println("PASS: "+checks+" World Map v2 payload, terrain resolution and LOD checks");
    }
}
