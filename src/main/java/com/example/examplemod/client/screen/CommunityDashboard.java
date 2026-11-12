package com.example.examplemod.client.screen;
import com.example.examplemod.client.*;
import com.example.examplemod.network.*;
import com.example.examplemod.projects.Project;
import net.minecraft.nbt.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

public final class CommunityDashboard extends CommunityPage {
    private CompoundTag summary=new CompoundTag(),listing=new CompoundTag();private UUID summaryRequest,listRequest;private long summaryAt,listAt,summarySent,listSent;private int tab,page,category=6;private boolean history;private String result="loading";private Runnable confirmation;private boolean declining;
    private CommunityDashboard(Screen parent){super(parent,"center");}
    public static void open(Screen parent){SocialHubScreen.show(parent,new CommunityDashboard(parent),SocialHubScreen.Section.HOME);}
    public boolean home(){return tab==0;}
    public static void acceptCommunity(CommunityStatePayload payload){var page=SocialHubScreen.find(CommunityDashboard.class,p->p.summaryRequest!=null&&payload.data().hasUUID("Request")&&p.summaryRequest.equals(payload.data().getUUID("Request")));if(page==null)return;page.summaryRequest=null;page.summaryAt=net.minecraft.Util.getMillis()-(payload.data().getString("Result").equals("ok")&&!payload.data().contains("Tasks")?4500:0);if(payload.data().getString("Result").equals("ok")&&payload.data().contains("Tasks"))page.summary=payload.data();page.result=payload.data().getString("Result");}
    public static void acceptWorkspace(WorkspaceState payload){var page=SocialHubScreen.find(CommunityDashboard.class,p->p.listRequest!=null&&payload.data().hasUUID("Request")&&p.listRequest.equals(payload.data().getUUID("Request")));if(page==null)return;page.listRequest=null;page.listAt=net.minecraft.Util.getMillis();page.listing=payload.data();page.page=page.listing.getInt("Page");page.result=page.listing.getString("Result");}
    @Override protected void init(){super.init();summaryRequest=null;listRequest=null;refresh();}
    private void refresh(){if(summaryRequest==null){summaryRequest=UUID.randomUUID();summarySent=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(new CommunityRequestPayload(summaryRequest,CommunityRequestPayload.Action.SUMMARY,null,null,null,0,0,""));}if(tab>0&&listRequest==null)request(WorkspaceRequest.Action.DIRECTORY,0);}
    private void request(WorkspaceRequest.Action action,int value){if(listRequest!=null)return;listRequest=UUID.randomUUID();listSent=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(new WorkspaceRequest(listRequest,action,null,null,0,0,page,category,value,history,"",0,0,0,0));}
    @Override public void tick(){long now=net.minecraft.Util.getMillis();if(summaryRequest!=null&&now-summarySent>8000){summaryRequest=null;summaryAt=now;result="timeout";}if(listRequest!=null&&now-listSent>8000){listRequest=null;listAt=now;result="timeout";}if(now-summaryAt>5000||tab>0&&now-listAt>5000)refresh();}
    private void select(int next){listRequest=null;listAt=0;tab=next;page=0;listing=new CompoundTag();transition();refresh();}
    @Override protected void draw(GuiGraphics g,float partial){
        rounded(g,12,12,596,60,6,RAISED);int color=summary.hasUUID("OwnCity")?0xff000000|summary.getInt("OwnColor"):ACCENT;
        if(summary.hasUUID("OwnCity"))AllianceScreen.drawEmblem(g,24,22,36,color&0xffffff,0);else {var info=minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());PlayerFaceRenderer.draw(g,info==null?DefaultPlayerSkin.get(minecraft.player.getUUID()):info.getSkin(),24,22,36);}
        text(g,tr("center"),72,23,360,TEXT);text(g,summary.hasUUID("OwnCity")?Component.literal(summary.getString("OwnName")).append(" · ").append(Component.translatable("role.createcityexpansioncore."+summary.getString("OwnRole").toLowerCase(Locale.ROOT))):tr("welcome"),72,43,510,MUTED);
        tabButton(g,0,2,tr("tasks"),SocialHubScreen.Section.HOME,()->select(0),confirmation==null,tab==0);
        tabButton(g,1,2,tr("projects"),SocialHubScreen.Section.CITY,()->select(1),confirmation==null,tab==1);
        clip(g);int y=0;
        if(tab==0){
            int[] values={summary.getInt("Unread")+summary.getInt("AllianceUnread"),summary.getInt("TaskCount"),summary.getInt("ProjectCount")};String[] labels={"dialogs","decisions","projects"};
            var icons=new SocialHubIcons.Icon[]{SocialHubIcons.Icon.MESSAGES,SocialHubIcons.Icon.NOTIFICATIONS,SocialHubIcons.Icon.PROJECTS};
            for(int i=0;i<3;i++){int index=i;var b=layout.body();var r=new SocialHubLayout.Rect(b.x()+4+i*196,b.y()-scroll,188,46);card(g,r,i==0?0xff77bdd2:i==1?ACCENT:0xff8cc7aa);text(g,Component.literal(Integer.toString(values[i])),r.x()+12,r.y()+7,45,TEXT);text(g,tr(labels[i]),r.x()+12,r.y()+30,130,MUTED);hint(r,tr(labels[i]));button(g,new SocialHubLayout.Rect(r.x()+150,r.y()+8,27,28),Component.empty(),icons[i],()->{if(index==0)SocialScreen.openDialogs(this);else if(index==2)select(1);else scroll=58;},true,false);}y=58;
            if(DailyRewardsClient.available()){task(g,y,tr("daily"),tr("daily_hint"),tr("collect"),SocialHubScreen::openDailyRewards,0xffd3a363);y+=68;}
            if(summary.getInt("FriendRequests")>0){task(g,y,tr("friend_requests",summary.getInt("FriendRequests")),tr("friend_requests_hint"),tr("view"),()->SocialScreen.openRequests(this),0xff8cc7aa);y+=68;}
            for(Tag raw:summary.getList("Tasks",10)){var t=(CompoundTag)raw;String kind=t.getString("Kind");UUID id=t.getUUID("Id");
                if(kind.equals("invite")){var r=row(y,84);card(g,r,ACCENT);text(g,Component.literal(t.getString("Name")),r.x()+13,r.y()+10,r.width()-26,TEXT);text(g,tr("task.invite"),r.x()+13,r.y()+28,r.width()-26,MUTED);button(g,new SocialHubLayout.Rect(r.x()+13,r.y()+47,266,26),tr("decline"),()->invite(t,false),summaryRequest==null,false);var join=new SocialHubLayout.Rect(r.x()+291,r.y()+47,280,26);button(g,join,tr("join"),()->invite(t,true),t.getBoolean("Allowed")&&summaryRequest==null,true);if(!t.getBoolean("Allowed"))hint(join,tr("invite_unavailable"));y+=92;continue;}
                Runnable action=()->{if(kind.equals("project"))ProjectWorkspaceScreen.open(this,id);else AllianceScreen.openCouncil(this,id);};task(g,y,Component.literal(t.getString("Name")),tr("task."+t.getString("Action")),tr("view"),t.getBoolean("Allowed")?action:null,kind.equals("project")?0xff77bdd2:ACCENT);y+=68;}
            if(y==58){var r=row(y,100);card(g,r,0xff8cc7aa);text(g,tr("all_clear"),r.x()+16,r.y()+18,r.width()-32,TEXT);text(g,tr("all_clear_hint"),r.x()+16,r.y()+40,r.width()-32,MUTED);button(g,new SocialHubLayout.Rect(r.x()+16,r.y()+62,170,25),tr("find_project"),()->select(1),true,false);}
        }else{
            for(Tag raw:listing.getList("Rows",10)){var t=(CompoundTag)raw;var r=row(y,72);card(g,r,ProjectMapStyle.color(Project.Scope.valueOf(t.getString("Scope")),Project.Status.valueOf(t.getString("Status"))));
                text(g,Component.literal(t.getString("Title")),r.x()+13,r.y()+10,r.width()-140,TEXT);text(g,Component.translatable("gui.createcityexpansioncore.projects.status."+t.getString("Status").toLowerCase(Locale.ROOT)),r.x()+13,r.y()+29,350,MUTED);int total=Math.max(1,t.getInt("Stages"));g.fill(r.x()+13,r.y()+53,r.x()+200,r.y()+56,EDGE);g.fill(r.x()+13,r.y()+53,r.x()+13+187*t.getInt("Done")/total,r.y()+56,0xff86cfa0);button(g,new SocialHubLayout.Rect(r.right()-124,r.y()+21,112,27),tr("workspace"),()->ProjectWorkspaceScreen.open(this,t.getUUID("Id")),true,false);y+=80;
            }
            if(listing.getList("Rows",10).isEmpty()){var r=row(y,60);text(g,tr(listRequest!=null?"loading":"empty"),r.x()+12,r.y()+20,r.width()-24,MUTED);}
        }
        unclip(g);
        if(tab==0){button(g,layout.footer(0,2),tr("refresh"),this::refresh,summaryRequest==null,false);hint(layout.footer(0,2),tr(result));button(g,layout.footer(1,2),tr("my_city"),()->CommunityScreen.openOwnCity(this),true,false);}
        else {button(g,layout.footer(0,4),tr(category==6?"all_categories":"category",category==6?"":Component.translatable("gui.createcityexpansioncore.projects.category."+Project.Category.values()[category].name().toLowerCase(Locale.ROOT))),()->{category=(category+1)%7;page=0;transition();request(WorkspaceRequest.Action.DIRECTORY,0);},listRequest==null,false);button(g,layout.footer(1,4),tr(history?"active":"history"),()->{history=!history;page=0;transition();request(WorkspaceRequest.Action.DIRECTORY,0);},listRequest==null,history);button(g,layout.footer(2,4),Component.literal("‹"),()->{page--;scroll=0;request(WorkspaceRequest.Action.DIRECTORY,0);},page>0&&listRequest==null,false);button(g,layout.footer(3,4),Component.literal("›"),()->{page++;scroll=0;request(WorkspaceRequest.Action.DIRECTORY,0);},(page+1)*16<listing.getInt("Total")&&listRequest==null,false);}
        if(confirmation!=null){clearHits();g.fill(0,0,620,layout.height(),0xB00A1012);hitsForModal(g);}
    }
    private void task(GuiGraphics g,int y,Component title,Component detail,Component action,Runnable run,int color){var r=row(y,60);card(g,r,color);text(g,title,r.x()+13,r.y()+10,r.width()-155,TEXT);text(g,detail,r.x()+13,r.y()+31,r.width()-155,MUTED);button(g,new SocialHubLayout.Rect(r.right()-140,r.y()+16,128,28),action,run,run!=null&&summaryRequest==null,false);}
    private void invite(CompoundTag t,boolean accept){declining=!accept;confirmation=()->{if(summaryRequest!=null)return;summaryRequest=UUID.randomUUID();summarySent=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(new CommunityRequestPayload(summaryRequest,accept?CommunityRequestPayload.Action.ACCEPT:CommunityRequestPayload.Action.DECLINE,t.getUUID("Id"),null,t.getUUID("Token"),0,0,""));};}
    private void hitsForModal(GuiGraphics g){int y=layout.height()/2-55;panel(g,100,y,420,116);g.drawWordWrap(font,tr(declining?"confirm_decline":"confirm_join"),116,y+15,388,TEXT);button(g,new SocialHubLayout.Rect(116,y+67,180,28),tr("cancel"),()->confirmation=null,true,false);button(g,new SocialHubLayout.Rect(312,y+67,192,28),tr(declining?"decline":"join"),()->{var run=confirmation;confirmation=null;run.run();},summaryRequest==null,true);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return confirmation!=null||super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(confirmation!=null){if(key==256)confirmation=null;return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean mouseClicked(double x,double y,int button){if(confirmation!=null){double lx=layout.localX(x),ly=layout.localY(y);int top=layout.height()/2-55;if(!new SocialHubLayout.Rect(100,top,420,116).contains(lx,ly))return true;}return super.mouseClicked(x,y,button);}
}
