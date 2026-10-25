package com.example.examplemod.community;
import com.example.examplemod.projects.*;
import com.example.examplemod.client.screen.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.example.examplemod.projects.Project.*;

/** Regression checks for cursor races, real authority, additive metadata and scaled geometry. */
public final class CommunityV2Check {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        UUID owner=UUID.randomUUID(),peer=UUID.randomUUID(),other=UUID.randomUUID(),alliance=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID(),third=UUID.randomUUID();
        var inbox=new CommunityInbox();inbox.add(owner,first,"alliance_message",peer,null,alliance,"A",10);inbox.readAllianceThrough(owner,alliance,first,List.of(first));
        inbox.add(owner,second,"alliance_message",peer,null,alliance,"A",20);inbox.readAllianceThrough(owner,alliance,first,List.of(first,second));require(inbox.unread(owner)==1,"late read acknowledgement must not erase a newer message");
        inbox.readAllianceThrough(owner,alliance,UUID.randomUUID(),List.of(first,second));require(inbox.unread(owner)==1,"unknown cursor cannot read anything");
        inbox.readAllianceThrough(other,alliance,second,List.of(first,second));require(inbox.unread(owner)==1,"owner cursors are isolated");
        inbox.readAllianceThrough(owner,alliance,second,List.of(first,second));inbox.readAllianceThrough(owner,alliance,first,List.of(first,second));require(inbox.allianceRead(owner,alliance).equals(second)&&inbox.unread(owner)==0,"acknowledgements are monotonic");
        inbox=CommunityInbox.load(inbox.save(new CompoundTag(),null),null);inbox.add(owner,third,"alliance_message",peer,null,alliance,"A",30);require(inbox.allianceRead(owner,alliance).equals(second)&&inbox.unread(owner)==1,"read cursor survives restart and new messages remain unread");
        UUID inviteId=UUID.randomUUID();inbox.add(owner,inviteId,"city_invite",peer,null,null,"C",40);inbox.resolve(owner,inviteId,"city_invite_revoked");require(inbox.history(owner).stream().anyMatch(e->e.id().equals(inviteId)&&e.read()&&e.kind().equals("city_invite_revoked")),"revoked invitation is resolved rather than actionable");
        UUID city=UUID.randomUUID(),projectId=UUID.randomUUID();var zone=new ProjectZone(ResourceLocation.parse("minecraft:overworld"),ProjectZone.Shape.RECTANGLE,10,20,30,40);
        var project=new Project(projectId,UUID.randomUUID(),Scope.CITY,city,owner,"Town hall","",Category.BUILDING,zone,List.of(new Stage("Build",false)),Funding.FREE,0,Status.OPEN,List.of(),null,0,false,0,"");
        var member=new ProjectManager.Actor(peer,city,false,id->true,z->true,false,(c,p)->false,z->false,false,false);
        var visitor=new ProjectManager.Actor(other,UUID.randomUUID(),true,id->true,z->true,true,(c,p)->true,z->false,true,true);
        require(ProjectWorkspaceService.participant(project,member)&&CommunityActions.visible(project,member),"current city members can use the CITY workspace");
        require(!ProjectWorkspaceService.participant(project,visitor)&&!CommunityActions.visible(project,visitor),"OP or admin in another city gets no implicit private CITY discussion access");
        require(!ProjectWorkspaceService.manages(project,member),"ordinary participant is not a reviewer");
        require(ProjectWorkspaceService.inside(zone,-54,104)&&!ProjectWorkspaceService.inside(zone,-55,104),"planning margin is bounded");
        var server=new Project(UUID.randomUUID(),UUID.randomUUID(),Scope.SERVER,null,owner,"Public station","",Category.RAILWAY,zone,List.of(new Stage("Build",false)),Funding.SERVER_ISSUANCE,0,Status.OPEN,List.of(Applicant.player(peer)),null,0,false,0,"");
        require(ProjectWorkspaceService.participant(server,member)&&!ProjectWorkspaceService.manages(server,member),"server applicant can discuss but cannot manage");
        require(ProjectWorkspaceService.manages(server,visitor),"server review uses existing OP authority");
        var departed=new ProjectManager.Actor(peer,null,false,id->false,z->false,false,(c,p)->false,z->false,false,false);require(!CommunityActions.visible(project,departed),"leaving a city removes private project visibility immediately");
        var journal=new ProjectJournal();require(journal.trace(projectId)==null,"legacy projects have no fabricated history");journal.record(projectId,first,owner,"CREATE",0,100);journal.record(projectId,first,owner,"CREATE",0,200);journal.record(projectId,second,peer,"ACCEPT",8,300);journal.trace(projectId).announced=8;var restored=new ProjectJournal();restored.load(journal.save());var trace=restored.trace(projectId);require(trace.created==100&&trace.completed==300&&trace.acceptedBy.equals(peer)&&trace.events.size()==2&&trace.announced==8,"passport metadata, receipt deduplication and dispatch watermark survive restart");
        inbox.addProject(owner,UUID.randomUUID(),projectId,peer,"Town hall");inbox=CommunityInbox.load(inbox.save(new CompoundTag(),null),null);require(inbox.history(owner).stream().anyMatch(e->projectId.equals(e.project())),"inbox links preserve project identity across restart");
        var data=new ProjectWorkspaceData();var room=data.room(projectId);room.chat.add(new ProjectWorkspaceData.Message(first,owner,"Owner","Bring stone",100));room.marks.put(second,new ProjectWorkspaceData.Mark(second,owner,"Station",10,20,30,40,true));require(data.receipt(room,third)&&!data.receipt(room,third)&&room.revision==1,"duplicate room mutation cannot advance revision twice");data.recruiting.put(owner,4);var reloaded=ProjectWorkspaceData.load(data.save(new CompoundTag(),null),null);require(reloaded.room(projectId).chat.equals(room.chat)&&reloaded.room(projectId).marks.equals(room.marks)&&reloaded.room(projectId).revision==1&&reloaded.recruiting.get(owner)==4,"discussion, agreed sketches and voluntary availability survive restart");
        require(ProjectWorkspaceData.load(new CompoundTag(),null).rooms.isEmpty(),"old worlds load without workspace migrations");
        for(int[] size:new int[][]{{320,180},{426,240},{640,360},{960,540},{1920,1080}}){var hub=SocialHubLayout.calculate(size[0],size[1]);var page=CommunityV2Layout.calculate(size[0],size[1],hub);require(page.x()>=0&&page.y()>=0&&page.x()+620*page.scale()<=size[0]+1&&page.y()+page.height()*page.scale()<=size[1]+1,"workspace fits viewport");require(page.body().bottom()<page.footer(0,3).y(),"content never overlaps footer");for(int i=0;i<4;i++){var r=page.tab(i,4);var screen=page.screen(r);require(r.contains(page.localX(screen.x()+screen.width()/2d),page.localY(screen.y()+screen.height()/2d)),"tab draw and hitbox share one scaled geometry");}}
        System.out.println("PASS: "+checks+" community v2 cursor, authority, persistence and layout checks");
    }
}
