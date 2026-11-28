package com.example.examplemod.navigation;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityMapSavedData;
import com.example.examplemod.city.TerrainPyramid;
import com.example.examplemod.navigation.walk.WalkingRoutes;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.journey.JourneyObjectLookup;
import com.example.examplemod.journey.NavigationLookup;
import com.example.examplemod.projects.*;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class ProjectRouteGameTest {
    @GameTest(template="auger_empty", timeoutTicks=700)
    public static void cityProjectFromPlayer(GameTestHelper test) {
        var level = test.getLevel();
        var dim = level.dimension().location();
        var owner = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "ProjectOwner"), ClientInformation.createDefault());
        var stranger = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "OtherCity"), ClientInformation.createDefault());
        var base = test.absolutePos(new BlockPos(1, 1, 1));
        var start = new BlockPos((base.getX() & ~15) + 4, level.getMaxBuildHeight() - 8, (base.getZ() & ~15) + 5);
        int distance = 130;
        var end = start.east(distance);
        for (int x=start.getX() >> 4; x<=(end.getX() >> 4); x++)
            level.setChunkForced(x, start.getZ() >> 4, true);
        for (int dx=0; dx<=distance; dx++) level.setBlockAndUpdate(start.east(dx).below(), Blocks.STONE.defaultBlockState());
        owner.setPos(start.getX()+.5, start.getY(), start.getZ()+.5);
        stranger.setPos(owner.position());
        var cities = CityManager.get(level.getServer().overworld());
        var city = cities.createCity("Factory district", owner.getUUID(), dim, end);
        test.assertTrue(city != null, "Private project city created in the isolated world");
        var manager = EconomyManager.get(level.getServer().overworld()).projects();
        var zone = new ProjectZone(dim, ProjectZone.Shape.POINT, end.getX(), end.getZ(), end.getX(), end.getZ());
        var spec = new ProjectManager.Draft(Project.Scope.CITY, "Factory", "", Project.Category.RAILWAY, zone,
                List.of(new Project.Stage("Walk", false)), Project.Funding.FREE, 0);
        var empty = new UUID(0,0);
        var create = new ProjectManager.Command(UUID.randomUUID(), empty, 0, ProjectManager.Action.CREATE, empty, 0, false, "", spec);
        var author = ProjectNetworkHandlers.actor(owner, cities, city);
        test.assertTrue("ok".equals(manager.execute(author,create)), "Owner created city project");
        var project = manager.all().stream().filter(p -> create.request().equals(p.creationRequest())).findFirst().orElseThrow();
        var goal = new TripRef(TripRef.Kind.PROJECT, project.id());
        var plan = new TripNetwork.Query(TripNetwork.Action.PLAN, UUID.randomUUID(), CityMapSavedData.get(level).worldId(),
                TripRef.PLAYER, goal, TripPlanner.Mode.WALK, 0);
        var observed = JourneyObjectLookup.fixedLandmark(owner, NavigationLookup.Kind.PROJECT, project.id());
        test.assertTrue(observed != null && observed.position().getY()==0, "Project map position has no elevation");
        test.assertTrue(JourneyObjectLookup.fixedLandmark(stranger, NavigationLookup.Kind.PROJECT, project.id())==null,
                "Private project also hidden from direct bearing");
        var denied = new AtomicReference<CompoundTag>();
        TripServer.handle(stranger,plan,(p,r)->denied.set(r.data()));
        test.assertTrue(denied.get()!=null && "entrance".equals(denied.get().getString("State"))
                && !denied.get().contains("Options") && !denied.get().contains("Goal"), "No private route for stranger");
        var reply = new AtomicReference<CompoundTag>();
        var terrain = CityMapSavedData.get(level);
        var view = UUID.randomUUID();
        for (int x=start.getX() >> 5; x<=(end.getX() >> 5); x++) {
            var tile = new TerrainPyramid.Key(0,x,start.getZ() >> 5);
            terrain.watch(owner.getUUID(),view,tile,level.getGameTime(),0);
            terrain.prioritize(tile,end);
        }
        test.startSequence().thenWaitUntil(()->{
            for (int dx=0; dx<=distance; dx++)
                test.assertTrue(WalkingRoutes.currentlyWalkable(owner,start.east(dx)), "Awaiting surveyed walkway");
        }).thenExecute(()->TripServer.handle(owner,plan,(p,r)->reply.set(r.data()))).thenWaitUntil(()->{
            var data=reply.get();
            test.assertTrue(data!=null && "options".equals(data.getString("State")), "Owner has a verified walking option: "+data);
            var steps=data.getList("Options",Tag.TAG_COMPOUND).getCompound(0).getList("Steps",Tag.TAG_COMPOUND);
            test.assertTrue(!steps.isEmpty() && BlockPos.of(steps.getCompound(0).getLong("To")).equals(end),
                    "Route ends on the actual elevated floor 130 blocks from the player");
        }).thenExecute(()->TripServer.handle(owner,plan.action(TripNetwork.Action.BEGIN,0),(p,r)->reply.set(r.data())))
          .thenWaitUntil(()->test.assertTrue("guide".equals(reply.get().getString("State"))
                  && reply.get().getLongArray("Ahead").length>=5 && end.equals(BlockPos.of(reply.get().getLong("GoalPos")))
                  && !end.equals(BlockPos.of(reply.get().getLong("Pos"))), "Guide distinguishes next step and final destination"))
          .thenExecute(()->{
              TripServer.handle(owner,plan.action(TripNetwork.Action.CANCEL,0),(p,r)->{});
              terrain.unwatch(owner.getUUID(),view);
              for (int x=start.getX() >> 4; x<=(end.getX() >> 4); x++)
                  level.setChunkForced(x,start.getZ() >> 4,false);
          }).thenSucceed();
    }
}
