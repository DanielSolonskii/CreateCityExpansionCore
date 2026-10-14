package com.example.examplemod.client.map;

import com.example.examplemod.city.*;
import com.example.examplemod.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.*;
import java.nio.file.*;

/** Production pyramid/raster/protocol/cache checks, no live world or GPU required. */
public final class TerrainPipelineCheck {
    private static int checks;
    private static void require(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void run()throws Exception {
        int[] white=new int[1024];Arrays.fill(white,0xFFFFFFFF);short[] height=new short[1024];Arrays.fill(height,(short)80);
        var pyramid=new TerrainPyramid();
        for(int z=-2;z<2;z++)for(int x=-2;x<2;x++)pyramid.put(x,z,white,height);
        for(int i=0;i<20;i++)pyramid.advance(100,100_000_000L);
        require(pyramid.get(new TerrainPyramid.Key(1,-1,-1)).colors()[0]==0xFFFFFFFF,"white snow survives overview reduction at negative coordinates");
        require(pyramid.get(new TerrainPyramid.Key(5,-1,-1))!=null,"all six levels built from existing data");
        var partial=TerrainPyramid.reduce(new TerrainPyramid.Data[]{new TerrainPyramid.Data(white,height),null,null,null});
        require(partial.colors()[0]==0xFFFFFFFF&&partial.colors()[31]==0,"unexplored samples cannot darken known snow or invent land");
        int[] sparse=new int[1024];sparse[0]=0xFFFFFFFF;
        partial=TerrainPyramid.reduce(new TerrainPyramid.Data[]{new TerrainPyramid.Data(sparse,height),null,null,null});
        require((partial.colors()[0]&0xFFFFFF)==0xFFFFFF&&(partial.colors()[0]>>>24)<255,"partial coverage retains white RGB with honest coverage");
        short[] halo=new short[1156];Arrays.fill(halo,(short)80);
        int[] snow=TerrainRelief.raster(white,halo,0);
        require(Arrays.equals(snow,white),"flat snow stays white, no luminance ceiling");
        int[] grass=new int[1024];Arrays.fill(grass,0xFF7FB238);
        for(int z=0;z<34;z++)for(int x=0;x<34;x++)halo[z*34+x]=(short)(80+x);
        int[] light=TerrainRelief.raster(grass,halo,0);
        for(int c:light)require((c>>8&255)>(c>>16&255)&&(c>>8&255)>(c&255),"hillshade preserves green");
        Arrays.fill(halo,TerrainPyramid.NO_HEIGHT);require(Arrays.equals(TerrainRelief.raster(white,halo,0),white),"legacy missing heights introduce no cliffs");
        // Same planar slope across adjacent tiles: both sides of seam have identical lighting.
        for(int tz=0;tz<2;tz++)for(int tx=0;tx<2;tx++){
            short[] plane=new short[1024];for(int z=0;z<32;z++)for(int x=0;x<32;x++)plane[z*32+x]=(short)(tx*32+x+80);
            pyramid.put(tx,tz,grass,plane);
        }
        var left=TerrainRelief.raster(grass,pyramid.halo(new TerrainPyramid.Key(0,0,0)),0);
        var right=TerrainRelief.raster(grass,pyramid.halo(new TerrainPyramid.Key(0,1,0)),0);
        require(left[16*32+31]==right[16*32],"tile halo eliminates planar slope seams");
        long revision=TerrainPyramid.revision(grass,halo);halo[55]=99;
        require(revision!=TerrainPyramid.revision(grass,halo),"height-only edits invalidate revision");
        for(double zoom:new double[]{.0625,.125,.25,.5,1,2,4}){
            int lod=AtlasMapScale.terrainLevel(zoom),span=AtlasMapScale.span(lod);
            long visible=((long)Math.ceil(1024/(zoom*span))+2)*((long)Math.ceil(640/(zoom*span))+2);
            require(visible<=AtlasMapScale.ACTIVE_TILES,"viewport tile count stays within the atlas budget");
        }
        for(int width:new int[]{426,1024,1920,3840})for(int viewHeight:new int[]{320,640,1080,2160})for(double zoom:new double[]{.0625,.25,1,4}) {
            int lod=AtlasMapScale.terrainLevel(zoom,width,viewHeight);long count=((long)Math.ceil(width/(zoom*AtlasMapScale.span(lod)))+4)*((long)Math.ceil(viewHeight/(zoom*AtlasMapScale.span(lod)))+4);
            require(count<=AtlasMapScale.ACTIVE_TILES,"responsive LOD prevents GPU/cache thrashing up to 4K");
        }
        for(int physicalWidth:new int[]{1920,3840})for(int guiScale:new int[]{1,2,3,4})for(double zoom:new double[]{.0625,.1,.25,.5,.9,1,2,4}) {
            int w=physicalWidth/guiScale,h=physicalWidth*9/16/guiScale;
            int lod=AtlasMapScale.terrainLevel(zoom,w,h,guiScale);
            double physical=(1<<lod)*zoom*guiScale;
            require(physical<=Math.max(physicalWidth==1920?2:4,zoom*guiScale),"physical detail stays bounded at 1080p/4K without expanding the 16 MiB atlas");
            if(lod>0) {
                long finer=(long)(Math.ceil(w/(zoom*AtlasMapScale.span(lod-1)))+4)*(long)(Math.ceil(h/(zoom*AtlasMapScale.span(lod-1)))+4);
                require((1<<(lod-1))*zoom*guiScale<.8||finer>AtlasMapScale.ACTIVE_TILES,"finest affordable LOD is chosen; no unnecessary coarsening");
            }
        }
        require(AtlasMapScale.terrainLevel(.5,960,540,2)==0,"GUI Scale 2 preserves one physical pixel per world block");
        require((long)AtlasMapScale.ATLAS_SIZE*AtlasMapScale.ATLAS_SIZE*4*AtlasMapScale.ATLAS_PAGES==16L*1024*1024,"GPU atlas has a fixed 16 MiB upper bound");
        int slots=AtlasMapScale.ATLAS_SIZE/AtlasMapScale.ATLAS_CELL;
        require(slots*slots*AtlasMapScale.ATLAS_PAGES>AtlasMapScale.ACTIVE_TILES*4/3+128,"atlas fits selected detail, cropped parents and transition fallback");
        int[] path=white.clone();path[528]=0xFFE8E8E8;Arrays.fill(halo,(short)80);
        require(Arrays.equals(TerrainRelief.raster(path,halo,0),path),"native detail does not smooth away a one-block path");
        UUID world=UUID.randomUUID();var dimension=ResourceLocation.parse("minecraft:overworld");
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.EMPTY);
        try{
            var request=new RequestCityMapTilePayload(BlockPos.ZERO,dimension,-9,11,5,revision,world);
            RequestCityMapTilePayload.STREAM_CODEC.encode(buffer,request);require(request.equals(RequestCityMapTilePayload.STREAM_CODEC.decode(buffer)),"LOD/revision/world request round trip");buffer.clear();
            var full=new CityMapTilePayload(BlockPos.ZERO,dimension,-9,11,true,grass,new BitSet(),5,halo,world,revision,false);
            CityMapTilePayload.STREAM_CODEC.encode(buffer,full);int fullBytes=buffer.readableBytes();
            var decoded=CityMapTilePayload.STREAM_CODEC.decode(buffer);
            require(decoded.level()==5&&decoded.worldId().equals(world)&&Arrays.equals(decoded.heights(),halo)&&Arrays.equals(decoded.colors(),grass),"full tile codec retains height/colour/world");buffer.clear();
            var ack=new CityMapTilePayload(BlockPos.ZERO,dimension,-9,11,true,new int[0],new BitSet(),5,new short[0],world,revision,true);
            CityMapTilePayload.STREAM_CODEC.encode(buffer,ack);require(buffer.readableBytes()<fullBytes/20,"unchanged acknowledgement avoids terrain retransmission");require(CityMapTilePayload.STREAM_CODEC.decode(buffer).unchanged(),"ack codec");
        }finally{buffer.release();}
        var legacy=new net.minecraft.nbt.CompoundTag();var entries=new net.minecraft.nbt.ListTag();var entry=new net.minecraft.nbt.CompoundTag();
        entry.putInt("X",-1);entry.putInt("Z",2);entry.putIntArray("Colors",white);var known=new BitSet(1024);known.set(0,1024);entry.putLongArray("Known",known.toLongArray());entries.add(entry);legacy.put("Tiles",entries);
        var load=CityMapSavedData.class.getDeclaredMethod("load",net.minecraft.nbt.CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);load.setAccessible(true);
        var migrated=(CityMapSavedData)load.invoke(null,legacy,RegistryAccess.EMPTY);
        require(migrated.terrain(new TerrainPyramid.Key(0,-1,2)).colors()[0]==0xFFFFFFFF,"old saved map remains available as fallback");
        require(migrated.terrain(new TerrainPyramid.Key(0,-1,2)).heights()[0]==TerrainPyramid.NO_HEIGHT,"old map does not invent height data");
        var savedMap=migrated.save(new net.minecraft.nbt.CompoundTag(),RegistryAccess.EMPTY);
        var reload=(CityMapSavedData)load.invoke(null,savedMap,RegistryAccess.EMPTY);
        require(reload.worldId().equals(migrated.worldId())&&savedMap.getInt("TerrainVersion")==3,"map world identity and additive format survive restart");
        int[] savedHeights=new int[1024];Arrays.fill(savedHeights,143);savedMap.getList("Tiles",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putIntArray("Heights",savedHeights);
        reload=(CityMapSavedData)load.invoke(null,savedMap,RegistryAccess.EMPTY);
        require(reload.terrain(new TerrainPyramid.Key(0,-1,2)).heights()[0]==143,"actual saved elevations survive reload");
        Path dir=Files.createTempDirectory("ccec-terrain-v3-");
        var store=new TerrainTileStore(dir,"test");var tile=new TerrainTileStore.Tile(grass,new BitSet(),true,10,halo,revision,world);
        store.put(-9,11,5,tile);store.flush().get();var reopened=new TerrainTileStore(dir,"test");reopened.get(-9,11,5);TerrainTileStore.awaitIo();
        var saved=reopened.get(-9,11,5);require(saved!=null&&Arrays.equals(saved.heights(),halo)&&saved.worldId().equals(world)&&saved.revision()==revision,"region cache retains LOD/height/revision/world after restart");
        require(reopened.get(-9,11,0)==null,"cache levels do not collide");TerrainTileStore.awaitIo();
        // A second checkpoint merges the region instead of dropping its other cached tiles.
        store.put(-10,11,5,tile);store.flush().get();var merged=new TerrainTileStore(dir,"test");merged.get(-9,11,5);TerrainTileStore.awaitIo();
        require(merged.get(-9,11,5)!=null&&merged.get(-10,11,5)!=null,"incremental checkpoint preserves other tiles in region");
        TerrainTileStore.prune(dir,0);Files.delete(dir);
        System.out.println("PASS: "+checks+" terrain pyramid, relief, revision, protocol and regional cache checks");
    }
}
