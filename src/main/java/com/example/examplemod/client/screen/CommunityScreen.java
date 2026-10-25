package com.example.examplemod.client.screen;
import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.city.CityMember;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;
/** Shared community workspace; existing profile, chat and map screens remain the same trusted views. */
public final class CommunityScreen extends Screen {
    private final Screen parent;private CommunityLayout layout;private CompoundTag state=new CompoundTag();private UUID pending,city,member,inviteTarget;private int section,scroll,page,memberPage,chosenRole=-1;private long requested,last,transition,opened;private String result="loading";private boolean rolePicker,inContent;private int contentHeight;private double lineLeft=Double.NaN,lineRight;private long motionAt;
    private final List<Hit> hits=new ArrayList<>();private record Hit(CommunityLayout.Rect r,Runnable run,boolean enabled,boolean content){}
    private Runnable membershipAction;private Component membershipQuestion;
    private CommunityRequestPayload.Action lastAction=CommunityRequestPayload.Action.VIEW;
    private CommunityScreen(Screen parent){super(tr("title"));this.parent=parent;}
    public static void open(Screen parent){CommunityDashboard.open(parent);}
    public static void openOwnCity(Screen parent){var v=new CommunityScreen(parent);v.section=3;v.ownCityOnReply=true;v.ownCityMode=true;SocialHubScreen.show(parent,v,SocialHubScreen.Section.CITY);}
    public static void openRelations(Screen parent){var v=new CommunityScreen(parent);v.section=3;v.ownCityOnReply=true;v.ownCityMode=true;v.relationsMode=true;SocialHubScreen.show(parent,v,SocialHubScreen.Section.CITY);}
    public static void openCity(Screen parent,UUID id){var v=new CommunityScreen(parent);v.section=3;v.city=id;SocialHubScreen.show(parent,v,SocialHubScreen.Section.CITY);}
    private boolean ownCityOnReply,ownCityMode,foundingHelp,relationsMode;private int relationsPage;
    public boolean homeView(){return section==0&&inviteTarget==null;}
    public boolean ownCityView(){return ownCityMode;}
    public static void openMember(Screen parent,UUID target){var v=new CommunityScreen(parent);v.section=3;v.member=target;SocialHubScreen.show(parent,v,v.section==3?SocialHubScreen.Section.CITY:SocialHubScreen.Section.HOME);}
    public static void openRoles(Screen parent,UUID target){var v=new CommunityScreen(parent);v.section=3;v.member=target;v.rolePicker=true;SocialHubScreen.show(parent,v,v.section==3?SocialHubScreen.Section.CITY:SocialHubScreen.Section.HOME);}
    public static void invite(Screen parent,UUID target){var v=new CommunityScreen(parent);v.inviteTarget=target;v.section=0;SocialHubScreen.show(parent,v,v.section==3?SocialHubScreen.Section.CITY:SocialHubScreen.Section.HOME);}
    public static void settings(Screen parent){var v=new CommunityScreen(parent);v.section=5;SocialHubScreen.show(parent,v,v.section==3?SocialHubScreen.Section.CITY:SocialHubScreen.Section.HOME);}
    public static void accept(CommunityStatePayload p){SocialHubScreen.acceptCommunity(p);var v=SocialHubScreen.find(CommunityScreen.class,s->s.pending!=null&&p.data().hasUUID("Request")&&s.pending.equals(p.data().getUUID("Request")));if(v==null)return;
        v.pending=null;v.last=net.minecraft.Util.getMillis();v.result=p.data().getString("Result");if(v.result.equals("busy"))return;v.state=p.data();v.page=v.state.getInt("Page");v.memberPage=v.state.getInt("MemberPage");v.relationsPage=v.state.getInt("RelationsPage");if(v.ownCityOnReply){v.ownCityOnReply=false;if(v.state.hasUUID("OwnCity"))v.city=v.state.getUUID("OwnCity");}
        if((v.rolePicker||v.section==3&&v.member!=null)&&v.state.hasUUID("City"))v.city=v.state.getUUID("City");
        if(v.ownCityMode&&!v.state.hasUUID("OwnCity")){v.city=null;v.member=null;v.rolePicker=false;}
        if(v.result.equals("ok")&&v.lastAction==CommunityRequestPayload.Action.KICK){v.rolePicker=false;v.member=null;v.scroll=0;}
        if(!v.lastActionView){CommunityFx.sound(v.result.equals("ok")?"success":"error");v.transition=v.last;}v.lastActionView=true;
    }
    private boolean lastActionView=true;
    private static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.community."+key,args);}
    @Override protected void init(){layout=CommunityLayout.calculate(width,height);var hub=SocialHubScreen.geometry(this);if(hub!=null)layout=new CommunityLayout(620,hub.pageHeight(),hub.scale(),hub.pageX(620),hub.pageY(),true);CommunityFx.load();if(opened==0){opened=transition=net.minecraft.Util.getMillis();CommunityFx.sound("open");}pending=null;request(CommunityRequestPayload.Action.VIEW,null,null,memberPage);}
    private void request(CommunityRequestPayload.Action a,UUID target,UUID token,int value){if(pending!=null)return;pending=UUID.randomUUID();requested=net.minecraft.Util.getMillis();lastAction=a;lastActionView=a==CommunityRequestPayload.Action.VIEW;PacketDistributor.sendToServer(new CommunityRequestPayload(pending,a,city,a==CommunityRequestPayload.Action.VIEW&&target==null?member:target,token,value,relationsMode?relationsPage:page,""));}
    @Override public void tick(){if(minecraft.player==null||minecraft.getConnection()==null){minecraft.setScreen(null);return;}long now=net.minecraft.Util.getMillis();if(pending!=null&&now-requested>8000){pending=null;result="timeout";}if(pending==null&&membershipAction==null&&now-last>5000)request(CommunityRequestPayload.Action.VIEW,null,null,memberPage);}
    private float offset(){return 7*(1-CommunityFx.ease((net.minecraft.Util.getMillis()-opened)/180.0));}
    private void section(int s){section=s;scroll=0;transition=net.minecraft.Util.getMillis();chosenRole=-1;rolePicker=false;CommunityFx.sound("click");}
    private void label(GuiGraphics g,Component text,int x,int y,int width,int color){if(inContent)contentHeight=Math.max(contentHeight,y+11+scroll-layout.body().y());g.drawString(font,fit(font,text,Math.max(1,width)),x,y,color,false);}
    private void button(GuiGraphics g,CommunityLayout.Rect r,Component text,Runnable run,boolean enabled,boolean primary,double mx,double my){control(g,new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height()),false,enabled&&r.contains(mx,my),enabled,primary);label(g,text,r.x()+7,r.y()+8,r.width()-14,enabled?TEXT:MUTED);if(inContent)contentHeight=Math.max(contentHeight,r.bottom()+scroll-layout.body().y());hits.add(new Hit(r,run,enabled,inContent));}
    private void row(GuiGraphics g,int y,Component title,Component subtitle,Runnable action,double mx,double my){var r=layout.row(y,42);rounded(g,r.x(),r.y(),r.width(),r.height(),4,r.contains(mx,my)?RAISED:INSET);label(g,title,r.x()+10,y+8,r.width()-20,TEXT);label(g,subtitle,r.x()+10,y+25,r.width()-20,MUTED);contentHeight=Math.max(contentHeight,r.bottom()+scroll-layout.body().y());if(action!=null)hits.add(new Hit(r,action,true,true));}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float pt){double mx=layout.localX(mouseX),my=layout.localY(mouseY)-offset();hits.clear();if(!SocialHubScreen.embedded(this))g.fill(0,0,width,height,0x88090E10);g.pose().pushPose();g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);g.pose().translate(0,offset(),0);panel(g,0,0,layout.width(),layout.height());
        g.fill(16,15,18,25,ACCENT);label(g,tr("title"),25,17,layout.width()-170,ACCENT);if(!SocialHubScreen.embedded(this)){button(g,layout.settings(),tr("settings"),()->section(5),true,false,mx,my);button(g,layout.close(),Component.literal("×"),this::onClose,true,false,mx,my);}
        double a=CommunityFx.ease((net.minecraft.Util.getMillis()-transition)/180.0);
        if(!SocialHubScreen.embedded(this)){
        String[] nav={"home","players","messages","cities","profile"};for(int i=0;i<5;i++){final int index=i;button(g,layout.nav(i),tr(nav[i]),()->{if(index==1)PlayerProfileScreen.openSearch(this);else if(index==2)SocialScreen.open(this,null);else if(index==4)PlayerProfileScreen.open(this,minecraft.player.getUUID());else section(index);},true,false,mx,my);}
        var selected=layout.nav(section==5?0:section);long motionNow=net.minecraft.Util.getMillis();double blend=CommunityFx.reduced?1:1-Math.exp(-Math.min(50,motionAt==0?16:motionNow-motionAt)/55.0);motionAt=motionNow;
        if(Double.isNaN(lineLeft)){lineLeft=selected.x()+6;lineRight=selected.right()-6;}lineLeft+=(selected.x()+6-lineLeft)*blend;lineRight+=(selected.right()-6-lineRight)*blend;
        g.fill((int)Math.round(lineLeft),selected.bottom()-2,(int)Math.round(lineRight),selected.bottom()-1,ACCENT);
        }
        var body=layout.body();rounded(g,body.x(),body.y(),body.width(),body.height(),5,SURFACE);var clip=layout.screen(body,offset());g.flush();g.enableScissor(clip.x(),clip.y(),clip.right(),clip.bottom());
        contentHeight=0;inContent=true;try{if(rolePicker)roles(g,mx,my);else if(section==5)settings(g,mx,my);else if(section==3)cities(g,mx,my);else home(g,mx,my);
            if(a<1)g.fill(body.x(),body.y(),body.right(),body.bottom(),((int)(75*(1-a))<<24)|(SURFACE&0xFFFFFF));
        }finally{inContent=false;g.flush();g.disableScissor();}
        scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-body.height()));
        label(g,tr("result."+result),54,layout.height()-25,layout.width()-108,MUTED);
        button(g,layout.footer(false),Component.literal("‹"),()->{if(rolePicker){rolePicker=false;member=null;}else if(section==3&&city!=null){city=null;state.remove("City");scroll=0;}else if(page>0){page--;request(CommunityRequestPayload.Action.VIEW,null,null,memberPage);}},pending==null,false,mx,my);
        if(section==3&&city==null)button(g,layout.footer(true),Component.literal("›"),()->{page++;scroll=0;request(CommunityRequestPayload.Action.VIEW,null,null,0);},pending==null&&(page+1)*12<state.getInt("Total"),false,mx,my);
        if(membershipAction!=null){hits.clear();g.fill(0,0,layout.width(),layout.height(),0xBD0B1012);var r=layout.membershipModal();panel(g,r.x(),r.y(),r.width(),r.height());label(g,tr("membership.confirm"),r.x()+12,r.y()+12,r.width()-24,ACCENT);g.drawWordWrap(font,membershipQuestion,r.x()+12,r.y()+34,r.width()-24,TEXT);button(g,layout.membershipConfirm(false),tr("membership.cancel"),()->membershipAction=null,true,false,mx,my);button(g,layout.membershipConfirm(true),tr("membership.proceed"),()->{var action=membershipAction;membershipAction=null;action.run();},pending==null,true,mx,my);}
        g.flush();g.pose().popPose();
    }
    private void home(GuiGraphics g,double mx,double my){int y=layout.body().y()+12-scroll;label(g,tr("greeting",state.getString("SelfName").isBlank()?minecraft.player.getGameProfile().getName():state.getString("SelfName")),layout.body().x()+12,y,layout.body().width()-24,TEXT);y+=23;
        row(g,y,tr("messages_count",state.getInt("Unread")),tr("open_messages"),()->SocialScreen.open(this,null),mx,my);y+=49;
        row(g,y,tr("requests_count",state.getInt("FriendRequests")),tr("open_friends"),()->SocialScreen.openRequests(this),mx,my);y+=49;
        row(g,y,AllianceScreen.tr("title"),AllianceScreen.tr("community_entry"),()->AllianceScreen.open(this,null),mx,my);y+=49;
        if(state.hasUUID("OwnCity")){row(g,y,tr("membership.my_city",state.getString("OwnName")),tr("membership.my_city_hint"),()->{city=state.getUUID("OwnCity");section(3);request(CommunityRequestPayload.Action.VIEW,null,null,0);},mx,my);y+=49;}
        if(state.getInt("CouncilVotes")>0&&state.hasUUID("AllianceId")){row(g,y,SocialHubScreen.tr("pending_votes",state.getInt("CouncilVotes")),Component.literal(state.getString("AllianceName")),()->AllianceScreen.openCouncil(this,state.getUUID("AllianceId")),mx,my);y+=49;}
        if(state.getInt("AllianceInvites")>0){row(g,y,SocialHubScreen.tr("alliance_invites",state.getInt("AllianceInvites")),SocialHubScreen.tr("alliance_invite_hint"),()->AllianceScreen.open(this,null),mx,my);y+=49;}
        if(inviteTarget!=null){button(g,layout.row(y,26),tr("invite_player"),()->request(CommunityRequestPayload.Action.INVITE,inviteTarget,null,0),pending==null&&state.hasUUID("OwnCity")&&state.getBoolean("Manage"),true,mx,my);y+=34;}
        for(Tag raw:state.getList("Invites",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var r=layout.row(y,76);rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);label(g,tr(t.getBoolean("Incoming")?"city_invite":"outgoing",t.getString("Name"),t.getString("Player")),r.x()+10,y+9,r.width()-20,ACCENT);label(g,Component.literal(t.getString("Inviter")),r.x()+10,y+26,r.width()-20,MUTED);
            UUID cityId=t.getUUID("City");int bx=r.x()+8,by=y+45;button(g,new CommunityLayout.Rect(bx,by,110,23),tr("open_city"),()->{city=cityId;section(3);request(CommunityRequestPayload.Action.VIEW,null,null,0);},pending==null,false,mx,my);
            if(t.getBoolean("Incoming")){button(g,new CommunityLayout.Rect(bx+118,by,106,23),tr("decline"),()->respond(t,false),pending==null,false,mx,my);button(g,new CommunityLayout.Rect(bx+232,by,106,23),tr("accept"),()->respond(t,true),pending==null,true,mx,my);}else button(g,new CommunityLayout.Rect(bx+118,by,106,23),tr("revoke"),()->{city=cityId;request(CommunityRequestPayload.Action.REVOKE,t.getUUID("Target"),t.getUUID("Token"),0);},pending==null,false,mx,my);y+=84;
        }
        for(Tag raw:state.getList("Projects",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;row(g,y,Component.literal(t.getString("Title")),Component.translatable("gui.createcityexpansioncore.project.status."+t.getString("Status").toLowerCase(Locale.ROOT)),this::openMap,mx,my);y+=49;}
        if(state.getList("Invites",10).isEmpty()&&state.getList("Projects",10).isEmpty())g.drawWordWrap(font,tr("calm"),layout.body().x()+12,y+10,layout.body().width()-24,MUTED);
    }
    private void openMap(){com.example.examplemod.client.CityWorldMapSession.INSTANCE.awaitAnchor();PacketDistributor.sendToServer(new RequestCityWorldMapPayload());minecraft.setScreen(SocialHubScreen.retainMap(new PolishedWorldMapScreen(com.example.examplemod.client.CityWorldMapSession.INSTANCE)));}
    private void respond(CompoundTag t,boolean accept){city=t.getUUID("City");request(accept?CommunityRequestPayload.Action.ACCEPT:CommunityRequestPayload.Action.DECLINE,null,t.getUUID("Token"),0);}
    private void cities(GuiGraphics g,double mx,double my){int y=layout.body().y()+12-scroll;
        if(city!=null&&!state.hasUUID("City")&&pending==null){label(g,SocialHubScreen.tr("unavailable"),layout.body().x()+12,y,layout.body().width()-24,MUTED);return;}
        if(city==null&&ownCityMode&&!state.hasUUID("OwnCity")){
            label(g,SocialHubScreen.tr("no_city_title"),layout.body().x()+12,y,layout.body().width()-24,ACCENT);y+=26;g.drawWordWrap(font,SocialHubScreen.tr(foundingHelp?"found_city":"no_city_body"),layout.body().x()+12,y,layout.body().width()-24,TEXT);y+=font.wordWrapHeight(SocialHubScreen.tr(foundingHelp?"found_city":"no_city_body"),layout.body().width()-24)+18;
            button(g,layout.row(y,26),SocialHubScreen.tr("browse_cities"),()->ownCityMode=false,pending==null,true,mx,my);y+=34;button(g,layout.row(y,26),SocialHubScreen.tr("founding_help"),()->foundingHelp=!foundingHelp,true,false,mx,my);y+=34;button(g,layout.row(y,26),SocialHubScreen.tr("invitations"),()->section(0),true,false,mx,my);return;
        }
        if(city==null){for(Tag raw:state.getList("Cities",10)){var t=(CompoundTag)raw;row(g,y,Component.literal(t.getString("Name")),tr("city_summary",t.getInt("Members"),t.getInt("Level")),()->{city=t.getUUID("Id");scroll=0;memberPage=0;request(CommunityRequestPayload.Action.VIEW,null,null,0);},mx,my);y+=49;}return;}
        label(g,Component.literal(state.getString("Name")),layout.body().x()+12,y,layout.body().width()-24,ACCENT);y+=24;
        button(g,layout.row(y,25),AllianceScreen.tr("city_entry"),()->AllianceScreen.open(this,city),pending==null,false,mx,my);y+=33;
        if(state.hasUUID("OwnCity")&&city.equals(state.getUUID("OwnCity"))){button(g,layout.row(y,25),SocialHubScreen.tr(relationsMode?"members_back":"city_relations"),()->{relationsMode=!relationsMode;relationsPage=0;scroll=0;request(CommunityRequestPayload.Action.RELATIONS,null,null,0);},pending==null,false,mx,my);y+=33;if(relationsMode){relations(g,y,mx,my);return;}}
        if(state.hasUUID("OwnCity")&&city.equals(state.getUUID("OwnCity"))){boolean owner=state.getString("OwnRole").equals("OWNER");button(g,layout.row(y,25),tr("membership.leave"),()->confirmMembership(tr("membership.leave_confirm",state.getString("Name")),()->request(CommunityRequestPayload.Action.LEAVE,null,null,0)),pending==null&&!owner,false,mx,my);y+=33;if(owner){g.drawWordWrap(font,tr("membership.owner_hint"),layout.body().x()+12,y,layout.body().width()-24,MUTED);y+=font.wordWrapHeight(tr("membership.owner_hint"),layout.body().width()-24)+9;}}
        if(!state.getString("Description").isBlank()){var lines=font.split(Component.literal(state.getString("Description")),layout.body().width()-24);for(int i=0;i<Math.min(3,lines.size());i++)g.drawString(font,lines.get(i),layout.body().x()+12,y+i*11,MUTED,false);y+=Math.min(3,lines.size())*11+10;}
        for(Tag raw:state.getList("Members",10)){var t=(CompoundTag)raw;UUID id=t.getUUID("Id");boolean selected=id.equals(member);row(g,y,Component.literal((t.getBoolean("Online")?"● ":"○ ")+t.getString("Name")),Component.translatable("role.createcityexpansioncore."+t.getString("Role").toLowerCase(Locale.ROOT)),()->{member=id;chosenRole=-1;CommunityFx.sound("click");SocialHubScreen.inspect(id);request(CommunityRequestPayload.Action.VIEW,id,null,memberPage);},mx,my);y+=49;
            if(selected){int x=layout.body().x()+12;button(g,new CommunityLayout.Rect(x,y,115,24),tr("open_profile"),()->{if(!SocialHubScreen.inspect(id))PlayerProfileScreen.open(this,id);},true,false,mx,my);button(g,new CommunityLayout.Rect(x+123,y,105,24),tr("write"),()->SocialScreen.open(this,id),!id.equals(minecraft.player.getUUID()),false,mx,my);if(t.getBoolean("Assignable"))button(g,new CommunityLayout.Rect(x+236,y,118,24),tr("change_role"),()->{rolePicker=true;scroll=0;},true,true,mx,my);y+=33;y=membershipActions(g,y,mx,my);}
        }
        if(state.getInt("MemberTotal")>16){button(g,layout.row(y,24),tr("next_members"),()->{memberPage=(memberPage+1)%((state.getInt("MemberTotal")+15)/16);scroll=0;request(CommunityRequestPayload.Action.VIEW,null,null,memberPage);},pending==null,false,mx,my);}
    }
    private void relations(GuiGraphics g,int y,double mx,double my){
        Component hint=SocialHubScreen.tr("relations_hint");g.drawWordWrap(font,hint,layout.body().x()+12,y,layout.body().width()-24,MUTED);y+=font.wordWrapHeight(hint,layout.body().width()-24)+12;
        boolean manage=state.getBoolean("Manage")&&state.getBoolean("PylonAccess");
        for(Tag raw:state.getList("Relations",10)){var t=(CompoundTag)raw;var r=layout.row(y,91);rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);label(g,Component.literal(t.getString("Name")),r.x()+10,y+9,r.width()-20,TEXT);label(g,Component.translatable("gui.createcityexpansioncore.relations.status."+t.getString("Status")),r.x()+10,y+26,r.width()-20,ACCENT);label(g,SocialHubScreen.tr(t.getBoolean("IncomingAccess")?"incoming_access":"no_incoming_access"),r.x()+10,y+43,r.width()-20,MUTED);
            int half=(r.width()-26)/2;UUID target=t.getUUID("Id");int trust=t.getBoolean("Trust")?0:1,access=t.getBoolean("Access")?0:1;
            button(g,new CommunityLayout.Rect(r.x()+8,y+61,half,23),Component.translatable("gui.createcityexpansioncore.relations."+(trust==1?"add":"remove")),()->request(CommunityRequestPayload.Action.SET_TRUST,target,null,trust),pending==null&&manage,false,mx,my);
            button(g,new CommunityLayout.Rect(r.x()+18+half,y+61,half,23),SocialHubScreen.tr(access==1?"allow_land":"deny_land"),()->confirmMembership(SocialHubScreen.tr(access==1?"allow_land_confirm":"deny_land_confirm",t.getString("Name")),()->request(CommunityRequestPayload.Action.SET_TERRITORY,target,null,access)),pending==null&&manage,t.getBoolean("Access"),mx,my);y+=99;
        }
        if(state.getInt("RelationsTotal")==0){label(g,SocialHubScreen.tr("no_other_cities"),layout.body().x()+12,y,layout.body().width()-24,MUTED);y+=25;}
        if(!manage){g.drawWordWrap(font,tr("membership.pylon_hint"),layout.body().x()+12,y,layout.body().width()-24,MUTED);y+=font.wordWrapHeight(tr("membership.pylon_hint"),layout.body().width()-24)+10;}
        if(state.getInt("RelationsTotal")>16){int half=(layout.body().width()-34)/2;int px=layout.body().x()+12;button(g,new CommunityLayout.Rect(px,y,half,24),Component.literal("‹"),()->{relationsPage--;scroll=0;request(CommunityRequestPayload.Action.RELATIONS,null,null,0);},pending==null&&relationsPage>0,false,mx,my);button(g,new CommunityLayout.Rect(px+half+10,y,half,24),Component.literal("›"),()->{relationsPage++;scroll=0;request(CommunityRequestPayload.Action.RELATIONS,null,null,0);},pending==null&&(relationsPage+1)*16<state.getInt("RelationsTotal"),false,mx,my);}
    }
    private void roles(GuiGraphics g,double mx,double my){if(!state.getBoolean("Manage")){label(g,tr("result.permission"),layout.body().x()+12,layout.body().y()+18,layout.body().width()-24,MUTED);return;}int y=layout.body().y()+12-scroll;label(g,tr("role_title"),layout.body().x()+12,y,layout.body().width()-24,ACCENT);y+=26;
        for(var role:CityMember.Role.values()){if(role==CityMember.Role.OWNER||role==CityMember.Role.ADMIN&&!state.getBoolean("Owner"))continue;var r=layout.row(y,54);boolean selected=chosenRole==role.ordinal();rounded(g,r.x(),r.y(),r.width(),r.height(),4,selected?RAISED:INSET);if(selected)g.fill(r.x()+1,r.y()+7,r.x()+3,r.bottom()-7,ACCENT);label(g,Component.translatable("role.createcityexpansioncore."+role.name().toLowerCase(Locale.ROOT)),r.x()+10,y+9,r.width()-20,TEXT);g.drawWordWrap(font,tr("rights."+role.name().toLowerCase(Locale.ROOT)),r.x()+10,y+25,r.width()-20,MUTED);hits.add(new Hit(r,()->{chosenRole=role.ordinal();transition=net.minecraft.Util.getMillis();CommunityFx.sound("click");},pending==null,true));y+=61;}
        button(g,layout.row(y,26),tr("assign_role"),()->{request(CommunityRequestPayload.Action.ROLE,member,null,chosenRole);rolePicker=false;scroll=0;},pending==null&&chosenRole>=0,true,mx,my);y+=34;membershipActions(g,y,mx,my);
    }
    private void confirmMembership(Component text,Runnable action){membershipQuestion=text;membershipAction=action;}
    private int membershipActions(GuiGraphics g,int y,double mx,double my){
        if(member==null||!state.hasUUID("Selected")||!member.equals(state.getUUID("Selected")))return y;
        if(state.getBoolean("CanKick")){UUID target=member;button(g,layout.row(y,25),tr("membership.kick"),()->confirmMembership(tr("membership.kick_confirm",state.getString("SelectedName"),state.getString("Name")),()->request(CommunityRequestPayload.Action.KICK,target,null,0)),pending==null,false,mx,my);y+=33;}
        if(state.getBoolean("CanTransfer")){UUID target=member;button(g,layout.row(y,25),tr("membership.transfer"),()->confirmMembership(tr("membership.transfer_confirm",state.getString("Name"),state.getString("SelectedName")),()->request(CommunityRequestPayload.Action.TRANSFER,target,null,0)),pending==null,false,mx,my);y+=33;}
        if(state.getBoolean("Manage")&&!state.getBoolean("PylonAccess")){g.drawWordWrap(font,tr("membership.pylon_hint"),layout.body().x()+12,y,layout.body().width()-24,MUTED);y+=font.wordWrapHeight(tr("membership.pylon_hint"),layout.body().width()-24)+8;}
        return y;
    }
    private void settings(GuiGraphics g,double mx,double my){int y=layout.body().y()+12-scroll;String[] ids={"telegram","preview","offline","quiet"};boolean[] values={state.getBoolean("Telegram"),state.getBoolean("Preview"),state.getBoolean("OfflineOnly"),state.getBoolean("Quiet")};for(int i=0;i<4;i++){final int v=i;button(g,layout.row(y,27),tr("setting."+ids[i],values[i]?"✓":"—"),()->request(CommunityRequestPayload.Action.PREFERENCE,null,null,v),pending==null,false,mx,my);y+=34;}
        button(g,layout.row(y,27),tr("setting.reduced",CommunityFx.reduced?"✓":"—"),()->{CommunityFx.reduced=!CommunityFx.reduced;CommunityFx.save();},true,false,mx,my);y+=34;
        button(g,layout.row(y,27),tr("setting.volume",Math.round(CommunityFx.volume*100)),()->{CommunityFx.volume=CommunityFx.volume>=1?0:Math.min(1,CommunityFx.volume+.25f);CommunityFx.save();CommunityFx.sound("click");},true,false,mx,my);y+=34;
        button(g,layout.row(y,27),tr("setting.messages",CommunityFx.messageSound?"✓":"—"),()->{CommunityFx.messageSound=!CommunityFx.messageSound;CommunityFx.save();},true,false,mx,my);y+=38;
        g.drawWordWrap(font,tr("privacy"),layout.body().x()+12,y,layout.body().width()-24,MUTED);contentHeight=Math.max(contentHeight,y+80+scroll-layout.body().y());
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;double mx=layout.localX(x),my=layout.localY(y)-offset();if(membershipAction!=null){if(layout.membershipConfirm(false).contains(mx,my)){membershipAction=null;return true;}if(layout.membershipConfirm(true).contains(mx,my)&&pending==null){var action=membershipAction;membershipAction=null;action.run();}return true;}for(int i=hits.size()-1;i>=0;i--){var h=hits.get(i);if(h.enabled&&layout.hit(h.r,h.content,mx,my)){CommunityFx.sound("click");h.run.run();return true;}}return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(layout.body().contains(layout.localX(x),layout.localY(y)-offset())){scroll=Math.clamp(scroll-(int)(dy*27),0,Math.max(0,contentHeight-layout.body().height()));return true;}return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(membershipAction!=null){if(key==256)membershipAction=null;return true;}return super.keyPressed(key,scan,mods);}
    @Override public void onClose(){if(!SocialHubScreen.back(this))minecraft.setScreen(parent);}
    @Override public void removed(){pending=null;super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
