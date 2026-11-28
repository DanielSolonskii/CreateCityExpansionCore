package com.example.examplemod.navigation;

import com.example.examplemod.city.CityMapSavedData;
import com.example.examplemod.places.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class TripReconnectGameTest {
    private final GameTestHelper h;
    private final ServerLevel level;
    private final GameProfile identity=new GameProfile(UUID.randomUUID(),"TripReconnect");
    private final UUID id=UUID.randomUUID();
    private final BlockPos origin,goal;
    private ServerPlayer actor;
    private TripNetwork.Query oldQuery,freshQuery,afterDeath;
    private ServerPlayer beforeDeath;
    private CompoundTag latest;
    private BlockPos firstRespawnWaypoint;

    private TripReconnectGameTest(GameTestHelper h){
        this.h=h;level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));
        origin=new BlockPos((pos.getX() & ~15)+3,level.getMaxBuildHeight()-20,(pos.getZ() & ~15)+4);goal=origin.east(6);
    }
    private ServerPlayer connection(){
        var p=new ServerPlayer(level.getServer(),level,identity,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);
        // Vanilla respawn queries negotiated channels. A real in-memory Netty channel has no remote mod handshake.
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,p,
                CommonListenerCookie.createInitial(identity,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { /* This fixture has no remote client. */ }
        };
        return p;
    }
    private void stand(BlockPos pos){actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);actor.setOnGround(true);}
    private void send(TripNetwork.Query q){TripServer.handle(actor,q,(p,a)->{
        h.assertTrue(p==actor,"reply addressed to current instance");latest=a.data();
        if(afterDeath!=null && firstRespawnWaypoint==null && afterDeath.request().equals(latest.getUUID("Request")) &&
                "guide".equals(latest.getString("State")))firstRespawnWaypoint=BlockPos.of(latest.getLong("Pos"));
    });}
    private TripNetwork.Query query(){return new TripNetwork.Query(TripNetwork.Action.PLAN,UUID.randomUUID(),CityMapSavedData.get(level).worldId(),
            TripRef.PLAYER,new TripRef(TripRef.Kind.PLACE,id),TripPlanner.Mode.WALK,0);}
    private void state(String name,TripNetwork.Query q){
        h.assertTrue(q!=null && latest!=null && name.equals(latest.getString("State")) && q.request().equals(latest.getUUID("Request")),"expected "+name+" got "+latest);
    }
    @GameTest(template="auger_empty",timeoutTicks=350)
    public static void reconnectRejectsPriorItinerary(GameTestHelper h){new TripReconnectGameTest(h).run();}
    private void run(){
        for(int x=-2;x<10;x++)for(int z=-2;z<=2;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        actor=connection();stand(goal);level.addNewPlayer(actor);
        h.assertTrue(PlaceService.save(actor,new AtlasPlace(id,identity.getId(),0,"Arrival",AtlasPlace.Category.HOME,"",
                level.dimension().location(),goal.above(3),List.of(goal),false,null)).equals("saved"),"register private entrance");
        stand(origin);oldQuery=query();send(oldQuery);
        h.startSequence().thenWaitUntil(()->state("options",oldQuery)).thenExecute(()->send(oldQuery.action(TripNetwork.Action.BEGIN,0)))
          .thenWaitUntil(()->state("guide",oldQuery)).thenExecute(()->{
            var old=actor;
            level.getServer().getPlayerList().remove(old);
            old.connection.getConnection().channel().close();
            h.assertTrue(old.isRemoved(),"The actual player list detached the old entity");
            actor=connection();
            level.getServer().getPlayerList().load(actor);
            h.assertTrue(actor.blockPosition().equals(origin),"Vanilla loaded the saved position for the reconnecting profile");
            level.addNewPlayer(actor);
            stand(origin);
            h.assertTrue(old!=actor && old.connection!=actor.connection && old.getUUID().equals(actor.getUUID()),"new listener, same profile");
            h.assertTrue(PlaceService.resolve(actor,id)!=null,"new instance still owns its private place");
            latest=null;send(oldQuery.action(TripNetwork.Action.POLL,0));state("ended",oldQuery);
            send(oldQuery.action(TripNetwork.Action.BEGIN,0));state("expired",oldQuery);
            freshQuery=query();latest=null;send(freshQuery);
        }).thenWaitUntil(()->state("options",freshQuery)).thenIdle(6).thenExecute(()->send(freshQuery.action(TripNetwork.Action.BEGIN,0)))
          .thenWaitUntil(()->state("guide",freshQuery)).thenExecute(()->{
            send(oldQuery.action(TripNetwork.Action.CANCEL,0));
        }).thenIdle(12).thenExecute(()->{
            send(oldQuery.action(TripNetwork.Action.POLL,0));state("ended",oldQuery);
            stand(origin.east());latest=null;
        }).thenWaitUntil(()->{TripServer.advance(actor);state("guide",freshQuery);})
          .thenExecute(()->{
              System.out.println("QA reconnect: removed previous server player; loaded the saved profile; old requests rejected; new guidance survived old cancellation");
              beforeDeath=actor;
              var spawn=origin.east(3);
              actor.setRespawnPosition(level.dimension(),spawn,0,true,false);
              h.assertTrue(actor.hurt(actor.damageSources().genericKill(),Float.MAX_VALUE) && !actor.isAlive(),"Vanilla fatal damage");
              latest=null;send(freshQuery.action(TripNetwork.Action.POLL,0));
              h.assertTrue(latest==null,"Dead player receives no route updates");
              actor=level.getServer().getPlayerList().respawn(beforeDeath,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);
              h.assertTrue(actor!=beforeDeath && actor.isAlive() && actor.getUUID().equals(identity.getId()) && actor.getVehicle()==null,
                      "Vanilla replaced the dead entity with a living dismounted player");
              h.assertTrue(actor.blockPosition().equals(spawn),"Actual respawn feet should be "+spawn+" got "+actor.blockPosition());
              latest=null;send(freshQuery.action(TripNetwork.Action.POLL,0));state("ended",freshQuery);
              send(freshQuery.action(TripNetwork.Action.BEGIN,0));state("expired",freshQuery);
              afterDeath=query();latest=null;send(afterDeath);
          }).thenWaitUntil(()->state("options",afterDeath)).thenIdle(6).thenExecute(()->{
              var option=latest.getList("Options",Tag.TAG_COMPOUND).getCompound(0);
              h.assertTrue(option.getInt("Rides")==0,"Respawn created a fresh walking option");
              send(afterDeath.action(TripNetwork.Action.BEGIN,0));
          }).thenWaitUntil(()->state("guide",afterDeath)).thenExecute(()->{
              h.assertTrue(latest.getString("Phase").equals("walk") && actor.blockPosition().equals(firstRespawnWaypoint),
                      "New guidance starts at actual respawn feet");
              System.out.println("QA death: lethal damage, no response for dead player, native respawn at "+actor.blockPosition()+
                      "; old itinerary cannot resume, fresh walk confirmed on the ground");
              level.getServer().getPlayerList().remove(actor);
              actor.connection.getConnection().channel().close();
          }).thenSucceed();
    }
}
