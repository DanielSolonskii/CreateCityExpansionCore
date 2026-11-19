package com.example.examplemod.client.map;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.BitSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

final class TerrainExchangeCheck {
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    static void run() throws Exception {
        var view = UUID.randomUUID();
        var other = UUID.randomUUID();
        var key = new TerrainTileStore.Key(-1, 2, 3);
        var exchange = new TerrainExchange();
        var old = exchange.start(key, view, 92, 1);
        require(exchange.start(key, other, 92, 2) == null, "two consumers sent duplicate");
        require(exchange.invalidate(key, 12), "lost invalidation");
        var next = exchange.start(key, other, 92, 10);
        require(!exchange.accept(key,view,old.id(),8,false,false,92,true,11), "accepted old request");
        require(exchange.pending(key), "old response consumed new request");
        require(!exchange.accept(key,other,next.id(),11,false,false,92,true,12), "older version won");
        next=exchange.start(key,other,92,513);
        require(!exchange.accept(key,other,next.id(),12,false,true,92,false,514), "ACK without raw base");
        next=exchange.start(key,other,0,1015);
        require(exchange.accept(key,other,next.id(),12,false,false,-50,false,1016), "new version with smaller hash lost");
        require(!exchange.due(key,1017,60_000), "fresh tile queried again");
        require(exchange.due(key,62_000,60_000), "complete tile never retried");
        var wait=exchange.start(key,view,-50,62_000);
        exchange.forget(other);
        require(exchange.pending(key), "closing another consumer lost request");
        exchange.forget(view);
        require(!exchange.accept(key,view,wait.id(),13,false,false,2,false,62001), "closed view accepted");
        long now=70_000;
        for(int i=0;i<3;i++) {
            require(exchange.start(key,view,-50,now)!=null, "retry missing");
            exchange.expire(now+TerrainExchange.TIMEOUT);
            require(!exchange.due(key,now+TerrainExchange.TIMEOUT,0), "no retry delay");
            now+=10_000;
        }
        require(exchange.failed(key) && !exchange.due(key,now,0), "cooldown missing");
        require(exchange.due(key,now+30_000,0), "failed tile cannot recover");

        var building = new TerrainExchange();
        now = 100;
        for (long delay : new long[]{1000, 2000, 4000, 8000, 8000}) {
            var inProgress = building.start(key, view, 0, now);
            require(inProgress != null, "lost publication fallback");
            require(!building.accept(key, view, inProgress.id(), 0, true, false, 0, false, now), "unfinished tile was installed");
            require(building.publishing(key) && !building.failed(key), "server work treated as a network error");
            require(!building.due(key, now + delay - 1, 0), "busy tile polled too soon");
            now += delay;
        }
        require(building.invalidate(key, 0) && building.due(key, now - 1, 0), "first publication did not wake pending tile");
        var confirmed = building.start(key, other, 0, now);
        require(building.accept(key, other, confirmed.id(), 0, false, false, 7, false, now), "published tile was lost");
        require(!building.publishing(key), "finished publication left loading indicator");

        var world=UUID.randomUUID();
        var dimension=ResourceLocation.withDefaultNamespace("overworld");
        MapViewBindings.confirm(false,BlockPos.ZERO,dimension,view,world);
        MapViewBindings.confirm(true,BlockPos.ZERO,dimension,other,world);
        MapViewBindings.invalidate(false);
        require(MapViewBindings.find(view)==null && MapViewBindings.find(other)!=null,"independent view close");
        require(MapViewBindings.binding(true,BlockPos.ZERO,ResourceLocation.withDefaultNamespace("the_nether"))==null,"wrong dimension");
        MapViewBindings.reset();

        var root=Files.createTempDirectory("ccec-atlas-isolation-");
        try {
            var s=new TerrainTileStore(root,"endpoint/player/"+world+"/overworld");
            var data=new int[1024]; Arrays.fill(data,-1);
            s.put(-1,2,3,new TerrainTileStore.Tile(data,new BitSet(),true,3,new short[0],-50,world));
            s.flush().get();
            var different=new TerrainTileStore(root,"endpoint/player/"+UUID.randomUUID()+"/overworld");
            different.get(-1,2,3); TerrainTileStore.awaitIo();
            require(different.get(-1,2,3)==null,"cache crossed world identity");
            try(var files=Files.list(root)) { Files.write(files.findFirst().orElseThrow(),new byte[]{1,2}); }
            var reopened=new TerrainTileStore(root,"endpoint/player/"+world+"/overworld");
            reopened.get(-1,2,3); TerrainTileStore.awaitIo();
            require(reopened.get(-1,2,3)==null && reopened.ready(-1,2,3),"corrupt region blocks recovery");
        } finally { TerrainTileStore.prune(root,0); Files.delete(root); }
    }
}
