package com.example.examplemod.client.map;

import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.common.*;

/** Reads existing local diagnostics. Packet traffic is measured separately at the terrain payload layer. */
final class NavigationFrameSample {
    private static final AtomicLong inbound = new AtomicLong(), outbound = new AtomicLong();
    private static final AtomicLong replies = new AtomicLong(), requests = new AtomicLong();
    private static final long WINDOW = 8_000_000_000L;
    private final List<Long> intervals = new ArrayList<>();
    private final String name;
    private final long start = System.nanoTime(), startHeap = heap(), startIn = inbound.get(), startOut = outbound.get();
    private final long startReplies = replies.get(), startRequests = requests.get();
    private final long startRaster = rasterCpu(), startRequestsAll, startRetry, startHits, startCpu, startFirst, startFirstCount;
    private int inFlight, raster, io, maxSlots;
    private long lastFrame, peakHeap = startHeap;

    NavigationFrameSample(String name) throws Exception {
        this.name=name;
        startCpu=counter("cpuNanos");startRequestsAll=counter("requests");startRetry=counter("retries");
        startHits=counter("hits");startFirst=counter("sumFirstNanos");startFirstCount=counter("firstDrawCount");
    }
    static void installTrafficMeter(Minecraft client) {
        var registries=client.level.registryAccess();
        client.getConnection().getConnection().channel().pipeline().addBefore("packet_handler","ccec_qa_terrain",new ChannelDuplexHandler() {
            private void measure(Object packet) {
                if(packet instanceof BundlePacket<?> bundle){bundle.subPackets().forEach(this::measure);return;}
                if(packet instanceof ClientboundCustomPayloadPacket in && in.payload() instanceof CityMapTilePayload tile){
                    var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
                    try{CityMapTilePayload.STREAM_CODEC.encode(buffer,tile);inbound.addAndGet(buffer.readableBytes());replies.incrementAndGet();}
                    finally{buffer.release();}
                }else if(packet instanceof ServerboundCustomPayloadPacket out && out.payload() instanceof RequestCityMapTilePayload request){
                    var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),registries);
                    try{RequestCityMapTilePayload.STREAM_CODEC.encode(buffer,request);outbound.addAndGet(buffer.readableBytes());requests.incrementAndGet();}
                    finally{buffer.release();}
                }
            }
            @Override public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception{measure(msg);super.channelRead(ctx,msg);}
            @Override public void write(ChannelHandlerContext ctx,Object msg,ChannelPromise promise)throws Exception{measure(msg);super.write(ctx,msg,promise);}
        });
    }
    void frame(){
        long now=System.nanoTime();
        if(lastFrame!=0)intervals.add(now-lastFrame);
        lastFrame=now;
    }
    void sampleQueues(){
        peakHeap=Math.max(peakHeap,heap());
        inFlight=Math.max(inFlight,TerrainCache.inflight());raster=Math.max(raster,TerrainTileTexture.queueSize());
        io=Math.max(io,TerrainTileStore.ioQueueSize());maxSlots=Math.max(maxSlots,TerrainAtlas.occupied());
    }
    boolean ready(){return System.nanoTime()-start>=WINDOW;}
    String finish(Path directory)throws Exception{
        if(intervals.isEmpty())throw new AssertionError("no frames");
        var raw=new StringBuilder("frame,interval_ns\n");
        for(int i=0;i<intervals.size();i++)raw.append(i+1).append(',').append(intervals.get(i)).append('\n');
        Files.writeString(directory.resolve(name+"-frames.csv"),raw);
        var sorted=intervals.stream().mapToLong(Long::longValue).sorted().toArray();
        long firstCount=counter("firstDrawCount")-startFirstCount;
        return String.format(Locale.ROOT,"%s duration_ms=%.1f frames=%d interval_ms[p50=%.2f,p95=%.2f,p99=%.2f,max=%.2f] "+
                "map_draw_cpu_ms=%.2f raster_worker_cpu_ms=%.2f heap_MiB[start=%.1f,peak=%.1f,end=%.1f] "+
                "peak_queues[inflight=%d,raster=%d,io=%d] slots_peak=%d raw_end=%d pages_end=%d pooled_end=%d "+
                "diag_requests=%d retries=%d hits=%d first_draw_ms=%.1f first_draws=%d terrain_codec[out_packets=%d,out_bytes=%d,in_packets=%d,in_bytes=%d]",
                name,(System.nanoTime()-start)/1e6,sorted.length,p(sorted,.50),p(sorted,.95),p(sorted,.99),p(sorted,1),
                (counter("cpuNanos")-startCpu)/1e6,(rasterCpu()-startRaster)/1e6,startHeap/1048576d,peakHeap/1048576d,heap()/1048576d,
                inFlight,raster,io,maxSlots,TerrainCache.cachedTiles(),TerrainAtlas.pages(),TerrainCache.pooledTextures(),
                counter("requests")-startRequestsAll,counter("retries")-startRetry,counter("hits")-startHits,
                firstCount==0?-1:(counter("sumFirstNanos")-startFirst)/firstCount/1e6,firstCount,requests.get()-startRequests,outbound.get()-startOut,
                replies.get()-startReplies,inbound.get()-startIn);
    }
    private static double p(long[] data,double q){return data[(int)Math.ceil(data.length*q)-1]/1e6;}
    private static long heap(){var rt=Runtime.getRuntime();return rt.totalMemory()-rt.freeMemory();}
    private static long counter(String name)throws Exception{
        Field field=MapDiagnostics.class.getDeclaredField(name);field.setAccessible(true);return field.getLong(null);
    }
    private static long rasterCpu(){
        var bean=ManagementFactory.getThreadMXBean();long total=0;
        for(long id:bean.getAllThreadIds()){
            var info=bean.getThreadInfo(id);
            if(info!=null && info.getThreadName().equals("CCEC terrain raster"))total+=Math.max(0,bean.getThreadCpuTime(id));
        }
        return total;
    }
}
