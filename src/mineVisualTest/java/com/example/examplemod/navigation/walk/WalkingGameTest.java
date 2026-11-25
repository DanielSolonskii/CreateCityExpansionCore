package com.example.examplemod.navigation.walk;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class WalkingGameTest {
    @GameTest(template="auger_empty", timeoutTicks=320)
    public static void detourDoorAndUnknown(GameTestHelper h) {
        var level=h.getLevel();
        var chunk=h.absolutePos(new BlockPos(2,1,2));
        var origin=new BlockPos(chunk.getX() & ~15,level.getMaxBuildHeight()-12,chunk.getZ() & ~15);
        var from=origin.offset(2,0,5);
        var to=origin.offset(8,0,5);
        var actor=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"Walker"),
                ClientInformation.createDefault());
        actor.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5,0,0);
        for (int x=2;x<=10;x++) for (int z=4;z<=8;z++)
            level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        for (int z=4;z<8;z++) for (int dy=0;dy<2;dy++)
            level.setBlock(origin.offset(5,dy,z),Blocks.STONE.defaultBlockState(),2);
        var unknown=from.offset(1_000_000,0,0);
        int farX=unknown.getX()>>4,farZ=unknown.getZ()>>4;
        h.assertTrue(level.getChunkSource().getChunkNow(farX,farZ)==null,"Unknown QA area initially unloaded");
        var reply=new AtomicReference<List<WalkingRoutes.Result>>();
        boolean[] obsolete={false};
        var leg=new WalkingRoutes.Leg(from,to);
        WalkingRoutes.find(actor,List.of(leg),ignored -> obsolete[0]=true);
        WalkingRoutes.find(actor,List.of(leg,new WalkingRoutes.Leg(unknown,unknown.east()),
                new WalkingRoutes.Leg(from,from.east(513))),reply::set);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(reply.get()!=null,"Waiting for production walk queue"))
                .thenExecute(() -> {
                    var results=reply.getAndSet(null);
                    var path=results.getFirst();
                    h.assertTrue(path.status()==WalkingRoutes.Status.FOUND && path.seconds()>0
                            && path.path().getFirst().equals(from) && path.path().getLast().equals(to),"Real route to exact feet");
                    h.assertTrue(path.path().stream().anyMatch(p->p.getZ()==origin.getZ()+8),"Route detours round high wall");
                    for(int i=1;i<path.path().size();i++) h.assertTrue(WalkingRoutes.currentlyPassable(actor,
                            path.path().get(i-1),path.path().get(i)),"Every returned edge remains passable");
                    h.assertTrue(results.get(1).status()==WalkingRoutes.Status.NO_DATA
                            && level.getChunkSource().getChunkNow(farX,farZ)==null,"No chunk load for unknown area");
                    h.assertTrue(results.get(2).status()==WalkingRoutes.Status.LIMIT && !obsolete[0],"Bound and cancellation");
                    var door=Blocks.OAK_DOOR.defaultBlockState();
                    level.setBlock(to,door,2);
                    level.setBlock(to.above(),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
                    h.assertTrue(!WalkingRoutes.currentlyWalkable(actor,to),"Closing the entrance invalidates guidance");
                    WalkingRoutes.find(actor,List.of(leg),reply::set);
                }).thenWaitUntil(()->h.assertTrue(reply.get()!=null,"Waiting for closed entrance result"))
                .thenExecute(()->h.assertTrue(reply.get().getFirst().status()==WalkingRoutes.Status.UNREACHABLE,
                        "Closed entrance is distinct from missing collision data"))
                .thenSucceed();
    }
}
