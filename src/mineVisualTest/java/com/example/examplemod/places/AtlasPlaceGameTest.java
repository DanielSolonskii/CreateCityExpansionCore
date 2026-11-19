package com.example.examplemod.places;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class AtlasPlaceGameTest {
    private static AtlasPlace draft(ServerPlayer player,UUID id,long version,BlockPos icon,BlockPos entry,boolean published){
        return new AtlasPlace(id,player.getUUID(),version,"Foundry",AtlasPlace.Category.FACTORY,"South gate",
                player.level().dimension().location(),icon,List.of(entry),published,null);
    }
    @GameTest(template="auger_empty",timeoutTicks=160)
    public static void accessibleEntrancesAndOwnership(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();
        var owner=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"PlaceOwner"),ClientInformation.createDefault());
        var visitor=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"PlaceVisitor"),ClientInformation.createDefault());
        var entry=h.absolutePos(new BlockPos(1,2,1));var icon=entry.offset(6,4,0);
        level.setBlock(entry.below(),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(entry,Blocks.AIR.defaultBlockState(),2);level.setBlock(entry.above(),Blocks.AIR.defaultBlockState(),2);
        for(var p:List.of(owner,visitor)){p.setPos(entry.getX()+.5,entry.getY(),entry.getZ()+.5);p.setOnGround(true);}
        var id=UUID.randomUUID();
        h.assertTrue(PlaceService.save(owner,draft(owner,id,0,icon,icon,false)).equals("entrance"),"An unseen roof is not a surveyed entrance");
        h.assertTrue(PlaceService.save(owner,draft(owner,id,0,icon,entry,false)).equals("saved"),"Capture occupied walkable entrance");
        h.assertTrue(PlaceService.resolve(visitor,id)==null && PlaceService.delete(visitor,id,1).equals("unavailable"),"Private place is invisible");
        var visible=draft(owner,id,1,icon,entry,true);
        h.assertTrue(PlaceService.save(owner,visible).equals("saved"),"Explicitly publish existing entrance");
        var result=PlaceService.resolve(visitor,id);
        h.assertTrue(result!=null && result.version()==2 && !result.icon().equals(entry) && result.entrances().equals(List.of(entry)),"Icon and approach retain distinct coordinates");
        h.assertTrue(PlaceService.save(owner,visible).equals("conflict"),"Stale editor cannot overwrite current publication");
        var other=UUID.randomUUID();h.assertTrue(PlaceService.save(visitor,draft(visitor,other,0,icon,entry,false)).equals("saved"),"Equal names retain separate IDs");
        var cities=com.example.examplemod.city.CityManager.get(server.overworld());
        var city=cities.createCity("Place rights",owner.getUUID(),level.dimension().location(),icon.offset(1024,0,1024));
        h.assertTrue(city!=null,"Create real private management context");
        var unauthorized=new AtlasPlace(UUID.randomUUID(),visitor.getUUID(),0,"Foundry",AtlasPlace.Category.PUBLIC,"",level.dimension().location(),icon,List.of(entry),
                false,new AtlasPlace.Source(AtlasPlace.SourceKind.CITY,city.getId()));
        h.assertTrue(PlaceService.save(visitor,unauthorized).equals("permission"),"Cannot link a third-party city");
        h.assertTrue(PlaceService.save(owner,draft(owner,id,2,icon,entry,false)).equals("saved") && PlaceService.resolve(visitor,id)==null,"Withdrawal removes target");
        var db=PlaceBook.get(server);var restored=PlaceBook.load(db.save(new CompoundTag(),server.registryAccess()),server.registryAccess());
        h.assertTrue(restored.entries.get(id).version()==3 && restored.entries.get(other).owner().equals(visitor.getUUID()),"Reload preserves identities and versions");
        var renamed=new AtlasPlace(id,owner.getUUID(),3,"Foundry west",AtlasPlace.Category.FACTORY,"South gate",
                level.dimension().location(),icon,List.of(entry),true,null);
        h.assertTrue(PlaceService.save(owner,renamed).equals("saved") && PlaceService.resolve(visitor,id).name().equals("Foundry west"),
                "Renaming preserves the published destination ID");
        var territory=cities.createCity("Receiver",visitor.getUUID(),level.dimension().location(),icon);
        h.assertTrue(territory!=null && PlaceService.resolve(visitor,id)==null && !PlaceService.resolve(owner,id).published(),
                "Changed territorial publication authority withdraws the card even for a previous reader");
        h.assertTrue(PlaceService.delete(owner,id,4).equals("saved") && PlaceService.resolve(owner,id)==null,"Deleted destination is gone");
        var nonce=UUID.randomUUID();
        var save=new PlaceNetwork.Request(nonce,UUID.randomUUID(),UUID.randomUUID(),level.dimension().location(),PlaceNetwork.Action.SAVE,
                id,0,draft(owner,id,0,icon,entry,false),null);
        h.assertTrue(PlaceService.mutateOnce(owner,save).equals("saved") && PlaceService.delete(owner,id,1).equals("saved"),
                "Create and delete through the current book");
        h.assertTrue(PlaceService.mutateOnce(owner,save).equals("saved") && PlaceService.resolve(owner,id)==null,
                "A delayed repeat of the same creation cannot resurrect a deleted place");
        h.succeed();
    }
    private AtlasPlaceGameTest(){}
}
