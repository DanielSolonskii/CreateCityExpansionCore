package com.example.examplemod.social;
import com.example.examplemod.client.ProjectWorldGeometry;
import com.example.examplemod.client.map.MinimapLayout;
import com.example.examplemod.network.*;
import com.example.examplemod.projects.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Real save/codec/visibility and bounded geometry checks, without a running Minecraft client. */
public final class NearbySceneCheck {
    private static int checks;
    private static final ResourceLocation DIM=ResourceLocation.parse("minecraft:overworld");
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}require(rejected,message);}
    public static void main(String[] args){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),outsider=UUID.randomUUID();
        var data=new SocialSavedData();var pair=SocialBook.Pair.of(a,b);data.book.friends.add(pair);
        data.book.dialogs.put(pair,new ArrayList<>(List.of(new SocialBook.Message(1,UUID.randomUUID(),a,1234,"Private test message"))));
        require(data.sharesNearby(a),"default preference shares with mutual friends");data.shareNearby(a,true);
        var saved=data.save(new CompoundTag(),null);var loaded=SocialSavedData.load(saved,null);
        require(loaded.sharesNearby(a)&&loaded.sharesNearby(b),"owner preference survives save/reload");
        require(loaded.book.relation(a,b)==SocialBook.Relation.FRIEND&&loaded.book.messages(b,a,0).size()==1,"friends and messages survive additive save");
        loaded.shareNearby(a,false);require(!SocialSavedData.load(loaded.save(new CompoundTag(),null),null).sharesNearby(a),"opt out survives reload");
        saved.remove("NearbySharing");require(SocialSavedData.load(saved,null).sharesNearby(a),"legacy unset preference defaults to sharing");
        require(NearbyScenePolicy.friend(true,true,true,false,256*256),"friend range includes 256 blocks");
        require(NearbyScenePolicy.friend(true,true,true,false,30000000d*30000000d),"friends visible globally in dimension");
        require(!NearbyScenePolicy.friend(false,true,true,false,0),"strangers/removed friends excluded");
        require(!NearbyScenePolicy.friend(true,false,true,false,0),"opt out excluded");
        require(!NearbyScenePolicy.friend(true,true,false,false,0),"cross dimension excluded");
        require(!NearbyScenePolicy.friend(true,true,true,true,0),"spectator position excluded");
        require(!NearbyScenePolicy.preferenceReady(1000,1100)&&NearbyScenePolicy.preferenceReady(1000,1400),"preference burst throttled and then recovers");
        require(NearbyScenePolicy.preferenceReady(10000,500),"saved rate limit cannot lock settings after host reboot");
        data.book.blocks.add(new SocialBook.Edge(a,b));require(data.book.relation(a,b)!=SocialBook.Relation.FRIEND,"blocking immediately overrides friendship");
        for(var shape:ProjectZone.Shape.values()){
            var zone=new ProjectZone(DIM,shape,-32,-24,32,24);var geometry=ProjectWorldGeometry.nearby(zone,0,0);
            require(!geometry.isEmpty()&&geometry.size()<=ProjectWorldGeometry.MAX_SEGMENTS,"all footprint shapes produce bounded marks");
            require(geometry.stream().allMatch(s->s.distance(0,0)<=64),"marks clipped to physical neighbourhood");
            require(geometry.stream().anyMatch(ProjectWorldGeometry.Segment::post),"point/end/corner beacons");
        }
        var huge=new ProjectZone(DIM,ProjectZone.Shape.RECTANGLE,0,0,4096,4096);
        require(ProjectWorldGeometry.nearby(huge,2048,2048).isEmpty(),"inside large zone does not invent an interior border");
        var nearby=ProjectWorldGeometry.nearby(huge,2048,1);require(!nearby.isEmpty()&&nearby.size()<40,"long edge clips before sampling");
        require(ProjectWorldGeometry.nearby(new ProjectZone(DIM,ProjectZone.Shape.LINE,-4096,-4096,0,0),-2048,-2048).size()<70,"diagonal line budget");
        var degenerate=ProjectWorldGeometry.nearby(new ProjectZone(DIM,ProjectZone.Shape.LINE,-1,-1,-1,-1),-1,-1);require(!degenerate.isEmpty(),"zero length line still has a marker");
        for(int[] size:new int[][]{{320,180},{427,240},{640,360},{960,540}}){
            var hud=MinimapLayout.view(size[0],size[1],160,0);require(hud.frameY()+hud.frameHeight()<=size[1]&&hud.footerY()+20<hud.frameY()+hud.frameHeight(),"coordinate rows remain inside HUD frame");
            var settings=MinimapLayout.settings(size[0],size[1]);require(settings.y()>=0&&settings.y()+settings.height()<=size[1],"eight settings adapt to GUI scale");
            require(settings.buttonY(7)+20<settings.y()+settings.height()-28,"privacy button does not overlap Done");
        }
        var zone=new ProjectZone(DIM,ProjectZone.Shape.POINT,-1,-1,0,0);
        var site=new NearbyScenePayload.Site(UUID.randomUUID(),"Station",Project.Scope.SERVER,Project.Status.OPEN,zone,a,"Author",b,"Builder",1,2,true);
        var packet=new NearbyScenePayload(DIM,true,List.of(new NearbyScenePayload.Friend(b,-.5,5)),true,List.of(site));
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try{NearbyScenePayload.STREAM_CODEC.encode(buffer,packet);require(NearbyScenePayload.STREAM_CODEC.decode(buffer).equals(packet),"bounded scene codec round trip");buffer.clear();
            NearbySharingPayload.STREAM_CODEC.encode(buffer,new NearbySharingPayload(false));require(!NearbySharingPayload.STREAM_CODEC.decode(buffer).enabled(),"preference packet only contains own boolean");
        }finally{buffer.release();}
        rejects(()->new NearbyScenePayload.Friend(a,Double.NaN,0),"non-finite locations rejected");
        rejects(()->new NearbyScenePayload(DIM,false,Collections.nCopies(129,new NearbyScenePayload.Friend(a,0,0)),true,List.of()),"friend packet budget enforced");
        rejects(()->new NearbyScenePayload(DIM,false,List.of(),true,Collections.nCopies(129,site)),"project packet budget enforced");
        require(packet.projects().getFirst().title().equals("Station"),"scene projects carry only public visual summary");
        visibility(a,outsider,zone);
        System.out.println("Nearby scene: "+checks+" checks passed");
    }
    private static void visibility(UUID author,UUID other,ProjectZone zone){
        UUID city=UUID.randomUUID();var manager=new ProjectManager(new ProjectManager.Accounts(){public long balance(boolean c,UUID id){return 0;}public boolean debit(boolean c,UUID id,long n){return false;}public boolean canCredit(boolean c,UUID id,long n){return true;}public void credit(boolean c,UUID id,long n){}public long maximum(){return 1000;}public void dirty(){}});
        var cityActor=new ProjectManager.Actor(author,city,true,id->true,z->true);
        var outsider=new ProjectManager.Actor(other,null,false,id->false,z->false);
        var op=new ProjectManager.Actor(author,null,false,id->false,z->false,true,(a,b)->false,z->false);
        for(var scope:Project.Scope.values()){
            var draft=new ProjectManager.Draft(scope,"Private draft","",Project.Category.BUILDING,zone,List.of(new Project.Stage("Build",false)),scope==Project.Scope.CITY?Project.Funding.FREE:Project.Funding.SERVER_ISSUANCE,0);
            require(manager.execute(scope==Project.Scope.CITY?cityActor:op,new ProjectManager.Command(UUID.randomUUID(),new UUID(0,0),0,ProjectManager.Action.CREATE,new UUID(0,0),0,false,"",draft)).equals("ok"),"create fixture through production manager");
        }
        var social=new SocialSavedData();social.book.friends.add(SocialBook.Pair.of(author,other));
        var cityProject=manager.all().stream().filter(p->p.scope()==Project.Scope.CITY).findFirst().orElseThrow();
        require(!NearbySceneServer.friendProject(social,other,cityProject),"friendship does not expose private drafts");
        require(manager.execute(cityActor,new ProjectManager.Command(UUID.randomUUID(),cityProject.id(),cityProject.revision(),ProjectManager.Action.PUBLISH,new UUID(0,0),0,true,"",null)).equals("ok"),"publish city project for public friend projection");
        cityProject=manager.all().stream().filter(p->p.scope()==Project.Scope.CITY).findFirst().orElseThrow();
        require(NearbySceneServer.friendProject(social,other,cityProject),"confirmed author friendship exposes published passport");
        require(!NearbySceneServer.friendProject(social,UUID.randomUUID(),cityProject),"stranger gets no city passport by guessing project");
        require(manager.visible(outsider,0).isEmpty(),"actual visibility hides city projects and server drafts from outsiders");
        var server=manager.all().stream().filter(p->p.scope()==Project.Scope.SERVER).findFirst().orElseThrow();
        require(manager.execute(op,new ProjectManager.Command(UUID.randomUUID(),server.id(),server.revision(),ProjectManager.Action.PUBLISH,new UUID(0,0),0,true,"",null)).equals("ok"),"publish server project");
        require(manager.visible(outsider,0).size()==1&&manager.visible(outsider,0).getFirst().scope()==Project.Scope.SERVER,"published server project visible without city membership");
    }
}
