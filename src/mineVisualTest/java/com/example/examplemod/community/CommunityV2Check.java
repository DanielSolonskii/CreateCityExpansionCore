package com.example.examplemod.community;
import com.example.examplemod.projects.*;
import com.example.examplemod.client.screen.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.example.examplemod.projects.Project.*;

/** Regression checks for cursor races, real authority, additive metadata and scaled geometry. */
public final class CommunityV2Check {
    private static void watchAndFeedback(UUID owner, UUID actor, Project project) {
        var workspace = new ProjectWorkspaceData();
        UUID id = project.id();
        require(workspace.watches.set(owner, id, 3, true), "subscribe");
        require(workspace.watches.set(owner, id, 6, true), "idempotent subscribe");
        require(workspace.watches.recipients(id, 3).isEmpty()
                && workspace.watches.recipients(id, 4).contains(owner), "watch starts at the acknowledged revision");
        workspace = ProjectWorkspaceData.load(workspace.save(new CompoundTag(), null), null);
        require(workspace.watches.recipients(id, 4).contains(owner), "watch survives reconnect");
        for (int i = 1; i < 32; i++) require(workspace.watches.set(owner, UUID.randomUUID(), 0, true), "within watch limit");
        require(!workspace.watches.set(owner, UUID.randomUUID(), 0, true), "watch list remains bounded");
        workspace.watches.finish(id);
        require(workspace.watches.recipients(id, 5).isEmpty(), "terminal project releases watches");

        var inbox = new CommunityInbox();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        require(inbox.addProject(owner, first, id, actor, "Town", "project_submit", 100), "first event");
        require(!inbox.addProject(owner, first, id, actor, "Town", "project_submit", 100), "duplicate cause");
        require(inbox.addProject(owner, second, id, actor, "Town", "project_return", 110), "new cause on same object");
        inbox = CommunityInbox.load(inbox.save(new CompoundTag(), null), null);
        require(!inbox.addProject(owner, first, id, actor, "Town", "project_submit", 100), "redelivery after restart");
        var page = new CompoundTag();
        var journal = new ProjectJournal();
        var worker = Applicant.player(actor);
        java.util.function.Function<Status,Project> assignment = status -> new Project(id, project.creationRequest(),
                project.scope(), project.city(), owner, project.title(), "", project.category(), project.zone(),
                project.stages(), project.funding(), 0, status, List.of(worker), worker, 0, false, 7, "");
        var rework = assignment.apply(Status.REWORK);
        journal.record(id, second, actor, "RETURN", 4, 110);
        inbox.projectPage(owner, page, true, 0, key -> rework, journal);
        require(page.getInt("InboxUnread") == 2 && page.getList("Inbox", 10).size() == 2
                && page.getList("Inbox", 10).getCompound(0).getLong("Time") == 110, "independent events keep recorded time");
        require(!page.getList("Inbox",10).getCompound(0).getBoolean("Resolved"), "current return needs work");
        var legacy = journal.save();
        legacy.getList("Traces",10).getCompound(0).remove("ActiveReturn");
        journal.load(legacy);
        require(second.equals(journal.activeReturn(id)), "older journal reconstructs current return from existing events");
        journal.record(id, UUID.randomUUID(), actor, "SUBMIT", 5, 120);
        var review = assignment.apply(Status.REVIEW);
        inbox.projectPage(owner, page, true, 0, key -> review, journal);
        require(page.getList("Inbox",10).getCompound(0).getBoolean("Resolved"), "a confirmed resubmission resolves that return");
        UUID latest = UUID.randomUUID();
        journal.record(id, latest, actor, "RETURN", 6, 130);
        inbox.addProject(owner,latest,id,actor,"Town","project_return",130);
        for(int i=0;i<65;i++) journal.record(id,UUID.randomUUID(),actor,"STAGE",7+i,140+i);
        journal.load(journal.save());
        inbox.projectPage(owner, page, true, 0, key -> rework, journal);
        require(!page.getList("Inbox",10).getCompound(0).getBoolean("Resolved")
                && page.getList("Inbox",10).getCompound(1).getBoolean("Resolved"),
                "repeated returns keep separate status after detailed journal eviction and restart");
        var terminal = new Project(id, project.creationRequest(), project.scope(), project.city(), owner, project.title(), "",
                project.category(), project.zone(), project.stages(), project.funding(), 0, Status.CANCELLED,
                List.of(), null, 0, false, 8, "");
        inbox.projectPage(owner, page, true, 0, key -> terminal, journal);
        require(page.getList("Inbox", 10).getCompound(0).getBoolean("Resolved"), "terminal state comes from the current project");
        inbox.projectPage(owner, page, true, 0, key -> null, journal);
        require(page.getInt("InboxUnread") == 0 && page.getInt("InboxTotal") == 0 && page.getList("Inbox", 10).isEmpty(),
                "revoked access hides metadata and counts");

        var change = new ProjectJournal.Event(first, actor, "RETURN", 5, 200);
        require(ProjectWorkspaceService.liveNotice(change, 5, 150, 250), "live confirmed revision");
        require(!ProjectWorkspaceService.liveNotice(change, 5, 230, 250)
                && !ProjectWorkspaceService.liveNotice(change, 6, 150, 250)
                && !ProjectWorkspaceService.liveNotice(change, 5, 150, 30_000),
                "reconnect, superseded revision and backlog cannot replay an alert");

        var fx = new com.example.examplemod.client.CommunityFeedbackSettings();
        long now = 10_000;
        require(fx.projectNotice(first, now) && !fx.projectNotice(first, now + 1), "duplicate live event stays silent");
        require(fx.sound("map_select", now, now) && !fx.sound("map_select", now + 10, now)
                && fx.sound("map_ok", now + 10, now), "rapid input is limited without masking confirmed results");
        fx.toggle(com.example.examplemod.client.CommunityFeedbackSettings.Option.NAVIGATION);
        require(!fx.sound("map_navigation", now, now), "category mute");
        fx.toggleSnooze(now);
        require(!fx.projectNotice(second, now) && !fx.sound("map_warning", now, now), "snooze suppresses banner and sound");
        var props = new Properties();
        fx.save(props);
        fx.load(props);
        require(!fx.banners(true, now + 5) && fx.banners(true, now + 900_001), "snooze expiry survives restart");
        fx.toggle(com.example.examplemod.client.CommunityFeedbackSettings.Option.DO_NOT_DISTURB);
        require(!fx.banners(false, now + 900_001) && !fx.sound("map_save", now, now + 900_001), "DND covers both channels");
    }
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
        watchAndFeedback(owner, peer, project);
        var data=new ProjectWorkspaceData();var room=data.room(projectId);room.chat.add(new ProjectWorkspaceData.Message(first,owner,"Owner","Bring stone",100));room.marks.put(second,new ProjectWorkspaceData.Mark(second,owner,"Station",10,20,30,40,true));require(data.receipt(room,third)&&!data.receipt(room,third)&&room.revision==1,"duplicate room mutation cannot advance revision twice");data.recruiting.put(owner,4);var reloaded=ProjectWorkspaceData.load(data.save(new CompoundTag(),null),null);require(reloaded.room(projectId).chat.equals(room.chat)&&reloaded.room(projectId).marks.equals(room.marks)&&reloaded.room(projectId).revision==1&&reloaded.recruiting.get(owner)==4,"discussion, agreed sketches and voluntary availability survive restart");
        require(ProjectWorkspaceData.load(new CompoundTag(),null).rooms.isEmpty(),"old worlds load without workspace migrations");
        for(int[] size:new int[][]{{320,180},{426,240},{640,360},{960,540},{1920,1080}}){var hub=SocialHubLayout.calculate(size[0],size[1]);var page=CommunityV2Layout.calculate(size[0],size[1],hub);require(page.x()>=0&&page.y()>=0&&page.x()+620*page.scale()<=size[0]+1&&page.y()+page.height()*page.scale()<=size[1]+1,"workspace fits viewport");require(page.body().bottom()<page.footer(0,3).y(),"content never overlaps footer");for(int i=0;i<4;i++){var r=page.tab(i,4);var screen=page.screen(r);require(r.contains(page.localX(screen.x()+screen.width()/2d),page.localY(screen.y()+screen.height()/2d)),"tab draw and hitbox share one scaled geometry");}}
        System.out.println("PASS: "+checks+" community v2 cursor, authority, persistence and layout checks");
    }
}
