package com.example.examplemod.client.screen;

import com.example.examplemod.alliance.AllianceBook;
import com.example.examplemod.client.*;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;
import static com.example.examplemod.alliance.AllianceBook.Action;

/** One compact workspace; rows select, labelled buttons perform mutations. */
public final class AllianceScreen extends Screen {
    private final Screen parent;private UUID initialCity,selected,candidate;private AllianceLayout layout;
    private CompoundTag state=new CompoundTag();private AllianceRequestPayload pending,lastRequest;
    private long requested,received,opened,searchAt;private String result="loading",form="";private int tab,scroll,sideScroll,contentHeight,sideHeight,page,chatPage,color=0xD3A363,emblem;
    private final Map<UUID,Double> voteProgress=new HashMap<>();private UUID seenMessage,readRequest,readCursor;private long readAt;
    private EditBox name,description,search,message;private boolean searchDirty,stickBottom;
    private Runnable confirm;private Component confirmation;private final List<Hit> hits=new ArrayList<>();
    private record Hit(AllianceLayout.Rect rect,Runnable run,boolean enabled,int clip){}
    private static final int[] COLORS={0xD3A363,0x6DBCA7,0x70AED1,0xBA92CC,0xD58C73,0xB7C67A};
    private AllianceScreen(Screen parent,UUID city){super(tr("title"));this.parent=parent;initialCity=city;}
    public static void open(Screen parent,UUID city){SocialHubScreen.show(parent,new AllianceScreen(parent,city),SocialHubScreen.Section.ALLIANCE);}
    public static void openAlliance(Screen parent,UUID alliance){var s=new AllianceScreen(parent,null);s.selected=alliance;SocialHubScreen.show(parent,s,SocialHubScreen.Section.ALLIANCE);}
    public static void openCouncil(Screen parent,UUID alliance){var s=new AllianceScreen(parent,null);s.selected=alliance;s.tab=1;SocialHubScreen.show(parent,s,SocialHubScreen.Section.ALLIANCE);}
    public static void openChat(Screen parent,UUID alliance){var s=new AllianceScreen(parent,null);s.selected=alliance;s.tab=2;s.stickBottom=true;SocialHubScreen.show(parent,s,SocialHubScreen.Section.MESSAGES);}
    public static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.alliance."+key,args);}
    public static void accept(AllianceStatePayload payload){var s=SocialHubScreen.find(AllianceScreen.class,v->v.pending!=null&&payload.data().hasUUID("Request")&&v.pending.command().request().equals(payload.data().getUUID("Request")));if(s==null)return;
        var action=s.pending.command().action();s.pending=null;s.received=net.minecraft.Util.getMillis();s.result=payload.data().getString("Result");if(s.result.equals("busy"))return;
        if(s.tab==2&&s.chatPage==0&&s.scroll>=Math.max(0,s.contentHeight-s.layout.chat().height()-12)&&payload.data().getInt("ChatTotal")>s.state.getInt("ChatTotal"))s.stickBottom=true;
        s.state=payload.data();s.chatPage=s.state.getInt("ChatPage");s.page=s.state.getInt("DirectoryPage");s.initialCity=null;if(s.state.contains("Alliance",10))s.selected=s.alliance().getUUID("Id");else s.selected=null;
        if(action!=Action.VIEW){boolean ok=Set.of("ok","created","proposed","voted","sent","left","duplicate").contains(s.result);CommunityFx.sound(ok?"success":"error");if(ok){if(action==Action.CHAT){if(s.lastRequest.command().text().equals(s.message.getValue()))s.message.setValue("");s.chatPage=0;s.stickBottom=true;}else{s.form="";s.scroll=0;s.candidate=null;}}}if(!s.member()&&s.tab>0)s.tab=0;s.updateFields();
    }
    public static void acceptRead(CommunityStatePayload payload){var page=SocialHubScreen.find(AllianceScreen.class,v->v.readRequest!=null&&payload.data().hasUUID("Request")&&v.readRequest.equals(payload.data().getUUID("Request")));if(page!=null){if(payload.data().getString("Result").equals("ok"))page.seenMessage=page.readCursor;page.readRequest=null;}}
    private CompoundTag alliance(){return state.getCompound("Alliance");}
    private boolean member(){return state.getBoolean("Member");}private boolean council(){return state.getBoolean("Council");}private boolean owner(){return state.getBoolean("Owner");}
    private String draft(EditBox field){return field==null?"":field.getValue();}
    @Override protected void init(){String n=draft(name),d=draft(description),q=draft(search),m=draft(message);layout=AllianceLayout.calculate(width,height);var hub=SocialHubScreen.geometry(this);if(hub!=null)layout=new AllianceLayout(560,hub.pageHeight(),hub.scale(),hub.pageX(560),hub.pageY());name=field(layout.field(0),48,"name",n);description=field(layout.field(1),180,"description",d);search=field(layout.field(0),48,"search",q);message=field(layout.compose(),256,"message",m);search.setResponder(v->{searchDirty=true;searchAt=net.minecraft.Util.getMillis();});updateFields();if(opened==0){opened=net.minecraft.Util.getMillis();CommunityFx.sound("open");request(Action.VIEW,null,"");}}
    private EditBox field(AllianceLayout.Rect r,int max,String hint,String value){var f=new EditBox(font,r.x()+7,r.y()+7,r.width()-14,r.height()-8,tr(hint));f.setMaxLength(max);f.setBordered(false);f.setTextColor(TEXT);f.setHint(tr(hint));f.setValue(value);addWidget(f);return f;}
    private void updateFields(){if(name==null)return;name.setVisible(form.equals("create")||form.equals("rename"));description.setVisible(form.equals("create"));search.setVisible(form.equals("invite")||form.equals("exclude")||form.equals("representative"));message.setVisible(form.isEmpty()&&tab==2&&member());if(!name.isVisible()&&!search.isVisible()&&!message.isVisible())setFocused(null);}
    private void request(Action action,UUID target,String text){if(pending!=null)return;var command=new AllianceBook.Command(UUID.randomUUID(),action,selected,initialCity!=null&&action==Action.VIEW?initialCity:target,alliance().getLong("Revision"),text,description==null?"":description.getValue(),color,emblem);pending=lastRequest=new AllianceRequestPayload(command,page,chatPage);requested=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(pending);}
    private void view(UUID id){selected=id;initialCity=null;form="";tab=scroll=chatPage=0;state.remove("Alliance");state.putBoolean("Member",false);updateFields();request(Action.VIEW,null,"");}
    @Override public void tick(){if(minecraft.player==null||minecraft.getConnection()==null){minecraft.setScreen(null);return;}long now=net.minecraft.Util.getMillis();AllianceClient.ensure();if(pending!=null&&now-requested>8000){pending=null;result="timeout";}
        if(pending==null&&confirm==null){if(searchDirty&&form.equals("invite")&&now-searchAt>400){searchDirty=false;request(Action.VIEW,null,search.getValue());}else if(form.isEmpty()&&!result.equals("timeout")&&now-received>3000)request(Action.VIEW,null,"");}}
    private float offset(){return 6*(1-CommunityFx.ease((net.minecraft.Util.getMillis()-opened)/180.0));}
    private void text(GuiGraphics g,Component c,int x,int y,int width,int color){g.drawString(font,fit(font,c,Math.max(1,width)),x,y,color,false);}
    private void button(GuiGraphics g,AllianceLayout.Rect r,Component label,Runnable action,boolean enabled,boolean primary,int clip,double mx,double my){control(g,new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height()),false,enabled&&r.contains(mx,my),enabled,primary);text(g,label,r.x()+7,r.y()+8,r.width()-14,enabled?TEXT:MUTED);hits.add(new Hit(r,action,enabled,clip));}
    private void panelRow(GuiGraphics g,AllianceLayout.Rect r,Component title,Component sub,boolean selected,Runnable select,int clip,double mx,double my){rounded(g,r.x(),r.y(),r.width(),r.height(),4,selected||r.contains(mx,my)?RAISED:INSET);if(selected)g.fill(r.x(),r.y()+6,r.x()+2,r.bottom()-6,ACCENT);text(g,title,r.x()+8,r.y()+7,r.width()-16,TEXT);text(g,sub,r.x()+8,r.y()+23,r.width()-16,MUTED);if(select!=null)hits.add(new Hit(r,select,pending==null,clip));}
    private void clip(GuiGraphics g,AllianceLayout.Rect r){g.flush();var s=layout.screen(r,offset());g.enableScissor(s.x(),s.y(),s.right(),s.bottom());}
    private void unclip(GuiGraphics g){g.flush();g.disableScissor();}
    private void drawField(GuiGraphics g,EditBox f,AllianceLayout.Rect r,int mx,int my,float pt){rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);g.renderOutline(r.x(),r.y(),r.width(),r.height(),f.isFocused()?ACCENT:EDGE);f.render(g,mx,my,pt);}
    @Override public void render(GuiGraphics g,int rawX,int rawY,float pt){double mx=layout.localX(rawX),my=layout.localY(rawY)-offset();hits.clear();if(!SocialHubScreen.embedded(this))g.fill(0,0,width,height,0x99090E10);g.pose().pushPose();g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);g.pose().translate(0,offset(),0);panel(g,0,0,layout.width(),layout.height());
        drawEmblem(g,20,14,28,alliance().contains("Color")?alliance().getInt("Color"):color,alliance().getInt("Emblem"));text(g,selected==null?tr("title"):Component.literal(alliance().getString("Name")),58,14,layout.width()-112,TEXT);text(g,tr(Set.of("ok","loading","created","sent","left","voted","proposed","duplicate").contains(result)?"subtitle":"result."+result),58,30,layout.width()-112,Set.of("ok","loading","created","sent","left","voted","proposed","duplicate").contains(result)?MUTED:0xFFE4A297);if(!SocialHubScreen.embedded(this))button(g,layout.close(),Component.literal("×"),this::onClose,true,false,0,mx,my);
        String[] tabs={"overview","council","chat"};for(int i=0;i<3;i++){final int t=i;var r=layout.tab(i);button(g,r,tr(tabs[i]),()->{tab=t;scroll=0;form="";stickBottom=t==2;updateFields();request(Action.VIEW,null,"");},pending==null&&(i==0||member()),false,0,mx,my);if(form.isEmpty()&&tab==i)g.fill(r.x()+7,r.bottom()-2,r.right()-7,r.bottom()-1,ACCENT);}
        var side=layout.sidebar();rounded(g,side.x(),side.y(),side.width(),side.height(),5,SURFACE);clip(g,side);sidebar(g,mx,my);unclip(g);
        var body=layout.body();rounded(g,body.x(),body.y(),body.width(),body.height(),5,SURFACE);contentHeight=0;clip(g,form.isEmpty()&&tab==2?layout.chat():body);
        if(form.equals("create")||form.equals("rename"))creation(g,(int)mx,(int)my,pt);else if(!form.isEmpty())candidates(g,(int)mx,(int)my,pt);else if(selected==null)empty(g,mx,my);else if(tab==0)overview(g,mx,my);else if(tab==1)council(g,mx,my);else chat(g);
        unclip(g);if(form.isEmpty()&&tab==2&&member()){drawField(g,message,layout.compose(),(int)mx,(int)my,pt);button(g,layout.send(),tr("send"),()->request(Action.CHAT,null,message.getValue()),pending==null&&!message.getValue().isBlank(),true,0,mx,my);}
        if(tab==2&&member()&&chatPage==0&&SocialHubScreen.unobstructed(this)&&scroll>=Math.max(0,contentHeight-layout.chat().height())-8&&!state.getList("Chat",10).isEmpty()){
            var last=(CompoundTag)state.getList("Chat",10).getLast();if(readRequest!=null&&net.minecraft.Util.getMillis()-readAt>5000)readRequest=null;if(readRequest==null&&!last.getUUID("Id").equals(seenMessage)&&net.minecraft.Util.getMillis()-readAt>1000){readRequest=UUID.randomUUID();readCursor=last.getUUID("Id");readAt=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(new CommunityRequestPayload(readRequest,CommunityRequestPayload.Action.READ_ALLIANCE,null,selected,readCursor,0,0,""));}
        }
        int visible=form.isEmpty()&&tab==2?layout.chat().height():body.height();if(stickBottom){scroll=Math.max(0,contentHeight-visible);stickBottom=false;}scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-visible));
        if(sideHeight>layout.sidebar().height())scrollbar(g,true,false,sideScroll,sideHeight);
        if(contentHeight>visible&&form.isEmpty())scrollbar(g,false,tab==2,scroll,contentHeight);
        button(g,layout.sideFooter(0),tr("new"),()->{form="create";scroll=0;name.setValue("");description.setValue("");updateFields();setFocused(name);},pending==null&&owner()&&!state.hasUUID("OwnAlliance"),true,0,mx,my);
        button(g,layout.sideFooter(1),tr("refresh"),()->request(Action.VIEW,null,""),pending==null,false,0,mx,my);
        if(!form.isEmpty())button(g,layout.footer(),tr("back"),()->{form="";scroll=0;candidate=null;updateFields();},pending==null,false,0,mx,my);
        else if(result.equals("timeout"))button(g,layout.footer(),tr("retry"),()->{pending=lastRequest;requested=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(pending);},pending==null&&lastRequest!=null,false,0,mx,my);
        else text(g,tr("result."+(pending!=null?"loading":result)),layout.footer().x()+4,layout.footer().y()+7,layout.footer().width()-8,MUTED);
        if(confirm!=null){g.fill(0,0,layout.width(),layout.height(),0xB00A1012);var r=layout.modal();panel(g,r.x(),r.y(),r.width(),r.height());text(g,tr("confirm"),r.x()+12,r.y()+13,r.width()-24,ACCENT);g.drawWordWrap(font,confirmation,r.x()+12,r.y()+35,r.width()-24,TEXT);button(g,layout.modalButton(0),tr("cancel"),()->confirm=null,true,false,3,mx,my);button(g,layout.modalButton(1),tr("confirm_action"),()->{var action=confirm;confirm=null;action.run();},pending==null,true,3,mx,my);}
        g.flush();g.pose().popPose();
    }
    public static void drawEmblem(GuiGraphics g,int x,int y,int size,int color,int variant){String pixels=com.example.examplemod.city.CityIdentity.template(variant);for(int py=0;py<32;py++)for(int px=0;px<32;px++){int index=Character.digit(pixels.charAt(py*32+px),16);if(index!=0)g.fill(x+px*size/32,y+py*size/32,x+(px+1)*size/32,y+(py+1)*size/32,com.example.examplemod.city.CityIdentity.colour(index,color));}}
    private void scrollbar(GuiGraphics g,boolean side,boolean chat,int value,int height){var track=layout.scrollTrack(side,chat);var thumb=layout.scrollThumb(side,chat,value,height);g.fill(track.x(),track.y(),track.right(),track.bottom(),INSET);g.fill(thumb.x(),thumb.y(),thumb.right(),thumb.bottom(),ACCENT);}
    private void sidebar(GuiGraphics g,double mx,double my){int i=0;var seen=new HashSet<UUID>();if(state.hasUUID("OwnAlliance")){UUID id=state.getUUID("OwnAlliance");var summary=state.getCompound("OwnSummary");sideRow(g,i++,summary,tr("your_alliance"),mx,my);seen.add(id);}
        for(Tag raw:state.getList("Incoming",10)){var a=(CompoundTag)raw;if(seen.add(a.getUUID("Id")))sideRow(g,i++,a,tr("invitation"),mx,my);}
        for(Tag raw:state.getList("Directory",10)){var a=(CompoundTag)raw;if(seen.add(a.getUUID("Id")))sideRow(g,i++,a,tr("city_count",a.getList("Cities",10).size()),mx,my);}
        if(i==0){var r=layout.sideRow(i++,sideScroll);text(g,tr("none"),r.x()+8,r.y()+12,r.width()-16,MUTED);}
        if(state.getInt("DirectoryTotal")>32){var r=layout.sideRow(i++,sideScroll);button(g,new AllianceLayout.Rect(r.x(),r.y(),60,25),Component.literal("‹"),()->{page=Math.max(0,page-1);request(Action.VIEW,null,"");},pending==null&&page>0,false,1,mx,my);button(g,new AllianceLayout.Rect(r.right()-60,r.y(),60,25),Component.literal("›"),()->{page++;request(Action.VIEW,null,"");},pending==null&&(page+1)*32<state.getInt("DirectoryTotal"),false,1,mx,my);}
        sideHeight=i*43+14;sideScroll=Math.clamp(sideScroll,0,Math.max(0,sideHeight-layout.sidebar().height()));
    }
    private void sideRow(GuiGraphics g,int index,CompoundTag a,Component sub,double mx,double my){var r=layout.sideRow(index,sideScroll);panelRow(g,r,Component.literal(a.getString("Name")),sub,a.getUUID("Id").equals(selected),()->view(a.getUUID("Id")),1,mx,my);}
    private void empty(GuiGraphics g,double mx,double my){var b=layout.body();text(g,tr("start_title"),b.x()+14,b.y()+17,b.width()-28,ACCENT);g.drawWordWrap(font,tr("start_body"),b.x()+14,b.y()+44,b.width()-28,TEXT);g.drawWordWrap(font,tr(owner()?"create_hint":state.hasUUID("OwnCity")?"owner_hint":"city_hint"),b.x()+14,b.y()+112,b.width()-28,MUTED);}
    private int wrapped(GuiGraphics g,Component text,int y,int color,int maxLines){var r=layout.row(y,1);var lines=font.split(text,r.width()-14);int count=Math.min(maxLines,lines.size());for(int i=0;i<count;i++)g.drawString(font,lines.get(i),r.x()+7,y+i*11,color,false);return Math.max(11,count*11);}
    private void overview(GuiGraphics g,double mx,double my){var b=layout.body();int y=b.y()+12-scroll;var a=alliance();y+=wrapped(g,Component.literal(a.getString("Description").isBlank()?tr("description_empty").getString():a.getString("Description")),y,MUTED,3)+13;
        text(g,tr("city_count",a.getList("Cities",10).size()),b.x()+17,y,b.width()-34,TEXT);y+=20;
        button(g,layout.row(y,25),tr("on_map"),this::openMap,pending==null,true,2,mx,my);y+=35;
        if(state.getBoolean("Invited")&&!member()){y+=wrapped(g,tr("invite_explain"),y,ACCENT,3)+8;button(g,layout.split(y,0,2),tr("decline"),()->ask(tr("decline_confirm"),()->request(Action.DECLINE,null,"")),owner()&&pending==null,false,2,mx,my);button(g,layout.split(y,1,2),tr("join"),()->ask(tr("join_confirm"),()->request(Action.ACCEPT,null,"")),owner()&&pending==null,true,2,mx,my);y+=35;}
        for(Tag raw:state.getList("Cities",10)){var c=(CompoundTag)raw;var r=layout.row(y,39);panelRow(g,r,Component.literal(c.getString("Name")),tr("representative_name",c.getString("RepresentativeName")),c.getUUID("Id").equals(candidate),()->{candidate=c.getUUID("Id");},2,mx,my);y+=45;}
        y+=wrapped(g,tr("independent"),y,MUTED,4)+12;
        if(member()){button(g,layout.split(y,0,2),tr("invite_city"),()->showForm("invite"),council()&&pending==null,false,2,mx,my);button(g,layout.split(y,1,2),tr("rename"),()->{name.setValue(a.getString("Name"));showForm("rename");},council()&&pending==null,false,2,mx,my);y+=32;
            button(g,layout.split(y,0,2),tr("representative"),()->showForm("representative"),owner()&&pending==null,false,2,mx,my);button(g,layout.split(y,1,2),tr("exclude"),()->showForm("exclude"),council()&&a.getList("Cities",10).size()>1&&pending==null,false,2,mx,my);y+=32;
            button(g,layout.row(y,25),tr(a.getList("Cities",10).size()==1?"dissolve":"leave"),()->ask(tr(a.getList("Cities",10).size()==1?"dissolve_confirm":"leave_confirm"),()->request(Action.LEAVE,null,"")),owner()&&pending==null,false,2,mx,my);y+=35;}
        contentHeight=y+scroll-b.y();
    }
    private void council(GuiGraphics g,double mx,double my){var b=layout.body();int y=b.y()+12-scroll;y+=wrapped(g,tr("council_rules"),y,MUTED,4)+14;
        if(state.getList("Proposals",10).isEmpty()){text(g,tr("council_empty"),b.x()+17,y,b.width()-34,TEXT);y+=26;}
        for(Tag raw:state.getList("Proposals",10)){var p=(CompoundTag)raw;var r=layout.row(y,94);rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);String kind=p.getString("Kind").toLowerCase(Locale.ROOT);text(g,tr("proposal."+kind),r.x()+9,y+9,r.width()-18,ACCENT);text(g,Component.literal(p.getString(kind.equals("rename")?"Text":"TargetName")),r.x()+9,y+26,r.width()-18,TEXT);text(g,tr("votes",p.getInt("Yes"),p.getInt("No"),p.getInt("Needed")),r.x()+9,y+43,r.width()-18,MUTED);
            double target=Math.min(1,p.getInt("Yes")/(double)Math.max(1,p.getInt("Needed")));double progress=voteProgress.getOrDefault(p.getUUID("Id"),0d);progress=CommunityFx.reduced?target:progress+(target-progress)*.22;voteProgress.put(p.getUUID("Id"),progress);g.fill(r.x()+9,y+56,r.right()-9,y+58,EDGE);g.fill(r.x()+9,y+56,r.x()+9+(int)((r.width()-18)*progress),y+58,ACCENT);
            text(g,tr("days",Math.max(1,(p.getLong("Expires")-System.currentTimeMillis()+86399999)/86400000)),r.right()-91,y+9,80,MUTED);
            button(g,new AllianceLayout.Rect(r.x()+8,y+62,145,24),tr("vote_no"),()->request(Action.NO,p.getUUID("Id"),""),pending==null&&p.getBoolean("CanVote"),false,2,mx,my);button(g,new AllianceLayout.Rect(r.x()+161,y+62,r.width()-169,24),tr("vote_yes"),()->request(Action.YES,p.getUUID("Id"),""),pending==null&&p.getBoolean("CanVote"),true,2,mx,my);y+=103;}
        for(Tag raw:state.getList("Outgoing",10)){var i=(CompoundTag)raw;panelRow(g,layout.row(y,39),Component.literal(i.getString("Name")),tr("waiting_owner"),false,null,2,mx,my);y+=46;}
        button(g,layout.row(y,25),tr("invite_city"),()->showForm("invite"),council()&&pending==null,true,2,mx,my);y+=34;contentHeight=y+scroll-b.y();
    }
    private void chat(GuiGraphics g){var b=layout.chat();int y=b.y()+10-scroll;
        if(state.getList("Chat",10).isEmpty()){wrapped(g,tr("chat_empty"),y,MUTED,5);contentHeight=70;return;}
        for(Tag raw:state.getList("Chat",10)){var m=(CompoundTag)raw;boolean own=m.getUUID("Author").equals(minecraft.player.getUUID());var lines=font.split(Component.literal(m.getString("Text")),b.width()-52);int h=33+lines.size()*11;int x=b.x()+(own?26:10),w=b.width()-36;rounded(g,x,y,w,h,5,own?0xFF28382F:INSET);text(g,Component.literal(m.getString("Name")+" · "+m.getString("CityName")),x+9,y+8,w-68,own?0xFF96C8AE:ACCENT);String time=java.time.Instant.ofEpochMilli(m.getLong("Time")).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));text(g,Component.literal(time),x+w-43,y+8,36,MUTED);for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),x+9,y+25+i*11,TEXT,false);y+=h+8;}
        if(state.getInt("ChatTotal")>40){button(g,layout.split(y,0,2),tr("newer"),()->{chatPage=Math.max(0,chatPage-1);scroll=0;request(Action.VIEW,null,"");},pending==null&&chatPage>0,false,2,-1,-1);button(g,layout.split(y,1,2),tr("older"),()->{chatPage++;scroll=0;request(Action.VIEW,null,"");},pending==null&&(chatPage+1)*40<state.getInt("ChatTotal"),false,2,-1,-1);y+=33;}
        contentHeight=y+scroll-b.y();
    }
    private void showForm(String next){form=next;scroll=0;candidate=null;search.setValue("");updateFields();if(search.isVisible()){setFocused(search);request(Action.VIEW,null,"");}else if(name.isVisible())setFocused(name);}
    private void creation(GuiGraphics g,int mx,int my,float pt){var b=layout.body();text(g,tr(form.equals("create")?"create_title":"rename"),b.x()+17,b.y()+10,b.width()-34,ACCENT);drawField(g,name,layout.field(0),mx,my,pt);
        if(form.equals("rename")){g.drawWordWrap(font,tr("rename_hint"),b.x()+17,b.y()+66,b.width()-34,MUTED);button(g,layout.row(b.y()+113,26),tr("propose"),()->ask(tr("rename_confirm",name.getValue()),()->request(Action.RENAME,null,name.getValue())),pending==null&&!name.getValue().isBlank(),true,2,mx,my);return;}
        text(g,tr("description_label"),b.x()+17,layout.field(1).y()-12,b.width()-34,MUTED);drawField(g,description,layout.field(1),mx,my,pt);int y=layout.field(1).bottom()+17;text(g,tr("identity"),b.x()+17,y,b.width()-34,MUTED);y+=18;
        for(int i=0;i<COLORS.length;i++){final int ink=COLORS[i];var r=new AllianceLayout.Rect(b.x()+15+i*29,y,22,22);rounded(g,r.x(),r.y(),r.width(),r.height(),4,0xFF000000|ink);if(color==ink)g.renderOutline(r.x()-2,r.y()-2,26,26,TEXT);hits.add(new Hit(r,()->color=ink,pending==null,2));}
        for(int i=0;i<3;i++){final int v=i;var r=new AllianceLayout.Rect(b.right()-130+i*39,y-3,32,32);drawEmblem(g,r.x(),r.y(),32,color,i);if(emblem==i)g.renderOutline(r.x(),r.y(),32,32,ACCENT);hits.add(new Hit(r,()->emblem=v,pending==null,2));}
        y+=43;g.drawWordWrap(font,tr("create_rules"),b.x()+17,y,b.width()-34,MUTED);button(g,layout.row(b.bottom()-35,26),tr("create"),()->request(Action.CREATE,null,name.getValue()),pending==null&&owner()&&!name.getValue().isBlank(),true,2,mx,my);
    }
    private void candidates(GuiGraphics g,int mx,int my,float pt){var b=layout.body();text(g,tr(form),b.x()+17,b.y()+10,b.width()-34,ACCENT);drawField(g,search,layout.field(0),mx,my,pt);int y=b.y()+61-scroll;String key=form.equals("invite")?"Candidates":form.equals("exclude")?"Cities":"Representatives";
        var viewport=new AllianceLayout.Rect(b.x(),b.y()+57,b.width(),b.height()-96);unclip(g);clip(g,viewport);int count=0;
        for(Tag raw:state.getList(key,10)){var c=(CompoundTag)raw;if(!c.getString("Name").toLowerCase(Locale.ROOT).contains(search.getValue().toLowerCase(Locale.ROOT)))continue;if(c.getUUID("Id").equals(state.hasUUID("OwnCity")?state.getUUID("OwnCity"):null)&&form.equals("exclude"))continue;
            panelRow(g,layout.row(y,39),Component.literal(c.getString("Name")),tr(form.equals("representative")?"representative_hint":"select_city"),c.getUUID("Id").equals(candidate),()->candidate=c.getUUID("Id"),4,mx,my);y+=45;count++;}
        if(count==0){wrapped(g,tr("search_empty"),y,MUTED,3);y+=44;}contentHeight=y+scroll-b.y()+40;unclip(g);clip(g,b);
        Action action=form.equals("invite")?Action.INVITE:form.equals("exclude")?Action.EXCLUDE:Action.REPRESENTATIVE;
        button(g,layout.row(b.bottom()-31,25),tr(form.equals("representative")?"assign":"propose"),()->ask(tr("confirm_proposal"),()->request(action,candidate,"")),pending==null&&candidate!=null,true,2,mx,my);
    }
    private void ask(Component question,Runnable action){confirmation=question;confirm=action;setFocused(null);}
    private void openMap(){AllianceClient.focus(alliance());CityWorldMapSession.INSTANCE.awaitAnchor();PacketDistributor.sendToServer(new RequestCityWorldMapPayload());minecraft.setScreen(SocialHubScreen.retainMap(new PolishedWorldMapScreen(CityWorldMapSession.INSTANCE)));}
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;double mx=layout.localX(x),my=layout.localY(y)-offset();if(confirm==null){for(var f:List.of(name,description,search,message)){var r=f==message?layout.compose():f==description?layout.field(1):layout.field(0);if(f.isVisible()&&r.contains(mx,my)){setFocused(f);f.setFocused(true);f.mouseClicked(Math.clamp(mx,f.getX(),f.getX()+f.getWidth()-1),f.getY()+5,button);return true;}}}
        for(int i=hits.size()-1;i>=0;i--){var h=hits.get(i);if(confirm!=null&&h.clip!=3)continue;var clip=h.clip==1?layout.sidebar():h.clip==4?new AllianceLayout.Rect(layout.body().x(),layout.body().y()+57,layout.body().width(),layout.body().height()-96):form.isEmpty()&&tab==2?layout.chat():layout.body();if(h.enabled&&h.rect.contains(mx,my)&&(h.clip==0||h.clip==3||clip.contains(mx,my))){CommunityFx.sound("click");h.run.run();return true;}}setFocused(null);return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(confirm!=null)return true;double mx=layout.localX(x),my=layout.localY(y)-offset();if(layout.sidebar().contains(mx,my))sideScroll=Math.clamp(sideScroll-(int)(dy*26),0,Math.max(0,sideHeight-layout.sidebar().height()));else if(layout.body().contains(mx,my)&&!form.equals("create")&&!form.equals("rename"))scroll=Math.max(0,scroll-(int)(dy*28));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==256){if(confirm!=null){confirm=null;return true;}if(!form.isEmpty()){form="";updateFields();return true;}}
        if(confirm!=null)return true;if((key==257||key==335)&&message.isVisible()&&message.isFocused()){if(pending==null&&!message.getValue().isBlank())request(Action.CHAT,null,message.getValue());return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean charTyped(char c,int mods){return confirm!=null||super.charTyped(c,mods);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(confirm!=null)return true;if(getFocused() instanceof EditBox field&&field.isVisible())return field.mouseDragged(layout.localX(x),layout.localY(y)-offset(),button,dx/layout.scale(),dy/layout.scale());return false;}
    @Override public void onClose(){if(!SocialHubScreen.back(this))minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
