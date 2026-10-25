package com.example.examplemod.client.screen;

import com.example.examplemod.client.*;
import com.example.examplemod.network.*;
import com.example.examplemod.projects.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** A task workspace over the existing project lifecycle. Rows select, labelled buttons mutate. */
public final class ProjectWorkspaceScreen extends CommunityPage {
    private final UUID id; private CompoundTag state=new CompoundTag(); private Project project;
    private UUID pending; private WorkspaceRequest.Action pendingAction; private long sent,received;
    private int tab; private String result="loading",draft=""; private EditBox input;
    private final String[] drafts={"","","",""};
    private Runnable confirmation; private Component confirmText;
    private ProjectWorkspaceScreen(Screen parent,UUID id){super(parent,"workspace");this.id=id;}
    public static void open(Screen parent,UUID id){SocialHubScreen.show(parent,new ProjectWorkspaceScreen(parent,id),SocialHubScreen.Section.HOME);}
    @Override protected void init(){super.init();if(input!=null)draft=input.getValue();input=new EditBox(font,16,layout.height()-64,430,23,tr("input"));input.setMaxLength(200);input.setValue(draft);input.setResponder(value->draft=value);addWidget(input);pending=null;view();}
    public static void accept(WorkspaceState response){var page=SocialHubScreen.find(ProjectWorkspaceScreen.class,p->p.pending!=null&&response.data().hasUUID("Request")&&p.pending.equals(response.data().getUUID("Request")));if(page==null)return;var data=response.data();var action=page.pendingAction;page.pending=null;page.received=net.minecraft.Util.getMillis();page.result=data.getString("Result");
        if(data.hasUUID("ProjectId")&&data.getUUID("ProjectId").equals(page.id)){if(data.getBoolean("RoomUnchanged")){if(page.state.contains("Chat"))data.put("Chat",page.state.getList("Chat",10).copy());if(page.state.contains("Marks"))data.put("Marks",page.state.getList("Marks",10).copy());}page.state=data;page.project=ProjectCodec.read(data.getCompound("Project"));if(action==WorkspaceRequest.Action.CHAT&&page.result.equals("ok")){page.input.setValue("");page.scroll=Integer.MAX_VALUE;}if(action!=WorkspaceRequest.Action.VIEW)CommunityFx.sound(page.result.equals("ok")?"success":"error");}
        else if(page.result.equals("permission")){page.state=new CompoundTag();page.project=null;}
    }
    @Override protected int reservedBottom(){return 34;}
    private void select(int next){drafts[tab]=draft;tab=next;input.setMaxLength(tab==2?96:200);input.setValue(drafts[tab]);input.setFocused(false);transition();}
    private void view(){send(WorkspaceRequest.Action.VIEW,null,0,false,"");}
    private void send(WorkspaceRequest.Action action,UUID target,int value,boolean flag,String text){if(pending!=null)return;pending=UUID.randomUUID();pendingAction=action;sent=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(new WorkspaceRequest(pending,action,id,target,project==null?0:project.revision(),state.getLong("RoomRevision"),0,6,value,flag,text,0,0,0,0));}
    @Override public void tick(){if(!SocialHubScreen.unobstructed(this))return;long now=net.minecraft.Util.getMillis();if(pending!=null&&now-sent>8000){pending=null;result="timeout";received=now;}if(pending==null&&confirmation==null&&now-received>2000)view();}
    private Component name(UUID who){return Component.literal(state.getCompound("Names").getString(who.toString()));}
    private Component status(){return Component.translatable("gui.createcityexpansioncore.projects.status."+project.status().name().toLowerCase(Locale.ROOT));}
    private void confirm(Component title,Runnable run){confirmation=run;confirmText=title;input.setFocused(false);CommunityFx.sound("open");}
    private void command(ProjectManager.Action action,UUID target,int stage,boolean flag){send(WorkspaceRequest.Action.PROJECT,target,stage,flag,action.name()+"\n"+(action==ProjectManager.Action.RETURN?draft:""));}
    private boolean executor(){return state.getBoolean("Executor");}
    @Override protected void draw(GuiGraphics g,float partial){
        rounded(g,12,12,596,60,6,RAISED);
        text(g,project==null?tr("workspace"):Component.literal(project.title()),25,23,490,TEXT);
        text(g,project==null?Component.translatable("gui.createcityexpansioncore.projects.result."+result):status().copy().append(" · ").append(Component.translatable("gui.createcityexpansioncore.projects.category."+project.category().name().toLowerCase(Locale.ROOT))),25,43,550,project!=null&&project.scope()==Project.Scope.SERVER?ACCENT:MUTED);
        String[] tabs={"overview","discussion","plan","passport"};for(int i=0;i<4;i++){int index=i;tabButton(g,i,4,tr(tabs[i]),new SocialHubScreen.Section[]{SocialHubScreen.Section.HOME,SocialHubScreen.Section.MESSAGES,SocialHubScreen.Section.CITY,SocialHubScreen.Section.PROFILE}[i],()->select(index),confirmation==null,tab==i);}
        boolean write=project!=null&&state.getBoolean("CanDiscuss")&&!project.status().terminal();boolean editor=tab==1&&write||tab==2&&write||tab==0&&state.getBoolean("CanReview");input.setVisible(editor);input.setEditable(pending==null&&confirmation==null);
        clip(g);int y=0;
        if(project==null){var r=row(0,80);text(g,Component.translatable("gui.createcityexpansioncore.projects.result."+result),r.x()+14,r.y()+24,r.width()-28,MUTED);}
        else if(tab==0){
            var supply=row(y,34);button(g,supply,Component.translatable("gui.createcityexpansioncore.trade.supplies"),()->RemoteMarketEntry.supplies(this,project.id(),project.title()),project.scope()==Project.Scope.CITY&&confirmation==null,false);y+=42;
            var r=row(y,66);card(g,r,ACCENT);person(g,project.author(),tr("author"),r.x()+12,r.y()+10,240);if(project.executor()!=null)person(g,project.executor().representative(),tr(project.executor().kind()==Project.ApplicantKind.CITY?"city_representative":"executor"),r.x()+295,r.y()+10,260);else text(g,tr("unassigned"),r.x()+300,r.y()+22,260,MUTED);y+=76;
            for(var line:font.split(Component.literal(project.description()),550)){r=row(y,14);g.drawString(font,line,r.x()+12,r.y(),TEXT,false);y+=14;}y+=10;
            if(!project.reviewNote().isBlank()){r=row(y,50);card(g,r,0xffd19478);text(g,tr("review_note"),r.x()+12,r.y()+8,550,ACCENT);text(g,Component.literal(project.reviewNote()),r.x()+12,r.y()+28,550,TEXT);hint(r,Component.literal(project.reviewNote()));y+=60;}
            boolean working=executor()&&(project.status()==Project.Status.BUILDING||project.status()==Project.Status.REWORK);for(int i=0;i<project.stages().size();i++){var stage=project.stages().get(i);int index=i;r=row(y,35);card(g,r,stage.done()?0xff82c6a7:EDGE);text(g,Component.literal((stage.done()?"✓ ":"○ ")+stage.title()),r.x()+13,r.y()+12,430,TEXT);if(working)button(g,new SocialHubLayout.Rect(r.right()-126,r.y()+4,114,26),tr(stage.done()?"undo":"mark_done"),()->command(ProjectManager.Action.STAGE,null,index,!stage.done()),pending==null,false);y+=42;}
            if(working){r=row(y,34);button(g,r,tr("submit"),()->confirm(tr("confirm_submit"),()->command(ProjectManager.Action.SUBMIT,null,0,false)),pending==null&&project.stages().stream().allMatch(Project.Stage::done),true);y+=43;}
            if(state.getBoolean("CanFinance")){r=row(y,34);button(g,r,tr("fund",project.reward()),()->confirm(tr("confirm_fund",project.reward()),()->command(ProjectManager.Action.APPROVE,null,0,false)),pending==null,true);y+=43;}
            if(state.getBoolean("CanReview")){r=row(y,34);button(g,new SocialHubLayout.Rect(r.x(),r.y(),r.width()/2-4,31),tr("return"),()->confirm(tr("confirm_return"),()->command(ProjectManager.Action.RETURN,null,0,false)),pending==null&&!draft.isBlank(),false);button(g,new SocialHubLayout.Rect(r.x()+r.width()/2+4,r.y(),r.width()/2-4,31),tr("accept",project.reward()),()->confirm(tr("confirm_accept",project.reward()).copy().append(state.getList("Conflicts",8).isEmpty()?Component.empty():tr("confirm_conflicts",String.join(", ",state.getList("Conflicts",8).stream().map(Tag::getAsString).toList()))),()->command(ProjectManager.Action.ACCEPT,null,0,true)),pending==null,true);y+=43;}
            if(project.status()==Project.Status.OPEN){r=row(y,34);button(g,new SocialHubLayout.Rect(r.x(),r.y(),r.width()/2-4,31),tr("apply"),()->confirm(tr("confirm_apply"),()->command(ProjectManager.Action.APPLY,null,0,false)),pending==null&&!project.applicants().contains(Project.Applicant.player(minecraft.player.getUUID())),true);if(project.scope()==Project.Scope.SERVER)button(g,new SocialHubLayout.Rect(r.x()+r.width()/2+4,r.y(),r.width()/2-4,31),tr("apply_city"),()->confirm(tr("confirm_apply_city"),()->command(ProjectManager.Action.APPLY_CITY,null,0,false)),pending==null&&state.getBoolean("CanApplyCity"),false);y+=43;}
            if(state.getBoolean("CanManage")&&project.status()==Project.Status.OPEN)for(var applicant:project.applicants()){r=row(y,49);person(g,applicant.representative(),tr(applicant.kind()==Project.ApplicantKind.CITY?"city_representative":"applicant"),r.x()+10,r.y()+6,380);button(g,new SocialHubLayout.Rect(r.right()-140,r.y()+9,128,28),tr("assign"),()->confirm(tr("confirm_assign",name(applicant.representative())),()->command(applicant.kind()==Project.ApplicantKind.CITY?ProjectManager.Action.ASSIGN_CITY:ProjectManager.Action.ASSIGN,applicant.id(),0,false)),pending==null,false);y+=57;}
        }else if(tab==1){
            if(!state.getBoolean("CanDiscuss")){var r=row(0,70);text(g,tr("participants_only"),r.x()+12,r.y()+20,550,MUTED);}
            for(Tag raw:state.getList("Chat",10)){var t=(CompoundTag)raw;var lines=font.split(Component.literal(t.getString("Text")),510);var r=row(y,36+lines.size()*12);card(g,r,t.getUUID("Author").equals(minecraft.player.getUUID())?ACCENT:0xff77bdd2);text(g,Component.literal(t.getString("Name")),r.x()+13,r.y()+9,350,ACCENT);text(g,date(t.getLong("Time")),r.right()-155,r.y()+9,140,MUTED);int ly=r.y()+28;for(var line:lines){g.drawString(font,line,r.x()+13,ly,TEXT,false);ly+=12;}y+=r.height()+8;}
            if(y==0&&state.getBoolean("CanDiscuss")){var r=row(0,60);text(g,tr("discussion_empty"),r.x()+12,r.y()+20,550,MUTED);}
        }else if(tab==2){
            var r=row(y,58);text(g,tr("plan_hint"),r.x()+12,r.y()+8,550,TEXT);text(g,tr("plan_proposals"),r.x()+12,r.y()+29,550,MUTED);y+=65;
            for(Tag raw:state.getList("Marks",10)){var t=(CompoundTag)raw;r=row(y,63);card(g,r,t.getBoolean("Approved")?0xff82c6a7:ACCENT);text(g,Component.literal(t.getString("Text")),r.x()+12,r.y()+8,310,TEXT);text(g,tr(t.getBoolean("Approved")?"approved":"proposed"),r.x()+12,r.y()+27,300,MUTED);if(write&&state.getBoolean("CanManage"))button(g,new SocialHubLayout.Rect(r.right()-242,r.y()+17,110,27),tr(t.getBoolean("Approved")?"unapprove":"approve"),()->send(WorkspaceRequest.Action.APPROVE_MARK,t.getUUID("Id"),0,!t.getBoolean("Approved"),""),pending==null,false);if(write&&(state.getBoolean("CanManage")||t.getUUID("Author").equals(minecraft.player.getUUID())))button(g,new SocialHubLayout.Rect(r.right()-122,r.y()+17,110,27),tr("remove"),()->confirm(tr("confirm_remove"),()->send(WorkspaceRequest.Action.REMOVE_MARK,t.getUUID("Id"),0,false,"")),pending==null,false);y+=71;}
        }else{
            var r=row(y,80);card(g,r,0xff82c6a7);text(g,tr("verified_record"),r.x()+12,r.y()+10,550,ACCENT);text(g,tr("created",date(state.getLong("Created"))),r.x()+12,r.y()+30,550,TEXT);text(g,tr("completed",date(state.getLong("Completed"))),r.x()+12,r.y()+49,550,TEXT);y+=90;
            if(state.hasUUID("AcceptedBy")){r=row(y,50);person(g,state.getUUID("AcceptedBy"),tr("accepted_by"),r.x()+12,r.y()+8,550);y+=60;}else{r=row(y,36);text(g,tr("legacy_record"),r.x()+12,r.y()+10,550,MUTED);y+=46;}
            var events=state.getList("Events",10);for(int i=events.size()-1;i>=0;i--){var t=events.getCompound(i);r=row(y,38);text(g,tr("event."+t.getString("Action").toLowerCase(Locale.ROOT)),r.x()+12,r.y()+4,310,TEXT);text(g,Component.literal(t.getString("Actor")).append(" · ").append(date(t.getLong("Time"))),r.x()+12,r.y()+21,550,MUTED);y+=45;}
        }
        unclip(g);
        if(editor){input.setX(16);input.setY(layout.height()-64);input.render(g,(int)mx,(int)my,partial);hint(new SocialHubLayout.Rect(16,layout.height()-64,430,23),tr(tab==1?"message_hint":tab==2?"plan_label":"return_reason"));button(g,new SocialHubLayout.Rect(456,layout.height()-64,148,23),tr(tab==1?"send":tab==2?"draw_on_map":"return_reason"),()->{if(tab==1)send(WorkspaceRequest.Action.CHAT,null,0,false,draft);else if(tab==2)ProjectWorkspaceClient.openMap(this,project,state,draft);},pending==null&&!draft.isBlank()&&tab!=0,false);}
        button(g,layout.footer(0,3),tr("back"),this::onClose,confirmation==null,false);button(g,layout.footer(1,3),tr("find_team"),()->CommunityDashboard.team(this,project.category()),project!=null&&confirmation==null,false);button(g,layout.footer(2,3),result.equals("ok")?tr("present",state.getList("Presence",10).size()):Component.translatable("gui.createcityexpansioncore.projects.result."+result),this::view,pending==null&&confirmation==null,false);
        if(confirmation!=null){clearHits();g.fill(0,0,620,layout.height(),0xBB090F11);int top=layout.height()/2-70;panel(g,80,top,460,140);g.drawWordWrap(font,confirmText,96,top+16,428,TEXT);button(g,new SocialHubLayout.Rect(96,top+92,196,28),tr("cancel"),()->confirmation=null,true,false);button(g,new SocialHubLayout.Rect(308,top+92,216,28),tr("confirm"),()->{var run=confirmation;confirmation=null;run.run();},pending==null,true);}
    }
    private void person(GuiGraphics g,UUID id,Component label,int x,int y,int width){var info=minecraft.getConnection().getPlayerInfo(id);PlayerFaceRenderer.draw(g,info==null?DefaultPlayerSkin.get(id):info.getSkin(),x,y,28);text(g,label,x+37,y,width-45,MUTED);text(g,name(id),x+37,y+16,width-45,TEXT);}
    private Component date(long time){return time<=0?tr("unknown"):Component.literal(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(time)));}
    @Override public boolean mouseClicked(double x,double y,int b){if(confirmation!=null){super.mouseClicked(x,y,b);return true;}if(input.isVisible()){boolean hit=input.mouseClicked(layout.localX(x),layout.localY(y),b);setFocused(hit?input:null);input.setFocused(hit);if(hit)return true;}return super.mouseClicked(x,y,b);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return confirmation!=null||super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(confirmation!=null){if(key==256)confirmation=null;return true;}if(input.isVisible()&&input.isFocused()){if(key==257&&tab==1&&!draft.isBlank()){send(WorkspaceRequest.Action.CHAT,null,0,false,draft);return true;}if(key!=256&&input.keyPressed(key,scan,mods))return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean charTyped(char c,int mods){return confirmation==null&&input.isVisible()&&input.isFocused()&&input.charTyped(c,mods);}
}
