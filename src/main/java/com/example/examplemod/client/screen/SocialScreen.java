package com.example.examplemod.client.screen;

import com.example.examplemod.network.*;
import com.example.examplemod.social.SocialBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Private social workspace. Only the server supplies relationships, history and online state. */
public final class SocialScreen extends Screen {
    private SocialRequestPayload sendAttempt;
    private UUID readPending;private long readCursor,readRequestedAt;
    private final Screen parent;private UUID target,pending;private SocialLayout layout;private SocialStatePayload state;
    private EditBox input,filterBox;private int filter,page,listScroll,messageScroll,messageHeight;private long before,lastReply,requestedAt,lastRead,filterChangedAt;
    private String result="loading",filterText="";private SocialBook.Action pendingAction,confirmation;private boolean scrollToEnd=true;
    private final List<SocialStatePayload.Contact> visible=new ArrayList<>();
    private record Button(SocialLayout.Rect r,Component text,Runnable run,boolean enabled,boolean primary){}
    private final List<Button> buttons=new ArrayList<>();
    public SocialScreen(Screen parent,UUID target){super(tr("title"));this.parent=parent;this.target=target;}
    public static void open(Screen parent,UUID target){Minecraft.getInstance().setScreen(new SocialScreen(parent,target));}
    public static void accept(SocialStatePayload p){
        if(!(Minecraft.getInstance().screen instanceof SocialScreen s))return;
        if(s.readPending!=null&&s.readPending.equals(p.request())){if(p.result().equals("ok"))s.lastRead=Math.max(s.lastRead,s.readCursor);s.readPending=null;return;}
        if(s.pending==null||!s.pending.equals(p.request()))return;
        s.pending=null;s.result=p.result().equals("duplicate")?"ok":p.result();s.lastReply=net.minecraft.Util.getMillis();
        if(p.result().equals("busy")){s.updateInputs();return;}
        if(s.pendingAction==SocialBook.Action.SEND){
            if(p.result().equals("ok")||p.result().equals("duplicate"))s.input.setValue("");
            s.sendAttempt=null;
        }
        boolean atEnd=s.before==0&&s.messageScroll>=Math.max(0,s.messageHeight-s.layout.messages().height());
        s.state=p;s.page=p.page();if(s.scrollToEnd||atEnd){s.messageScroll=Integer.MAX_VALUE;s.scrollToEnd=false;}
        s.updateInputs();
    }
    private static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.social."+key,args);}
    @Override protected void init(){layout=SocialLayout.calculate(width,height);String draft=input==null?"":input.getValue();
        var r=layout.input();input=new EditBox(font,r.x()+6,r.y()+8,r.width()-12,12,tr("message_hint"));input.setMaxLength(500);input.setBordered(false);input.setTextColor(TEXT);input.setHint(tr("message_hint"));input.setValue(draft);addWidget(input);
        var l=layout.list();filterBox=new EditBox(font,l.x()+6,l.y()+6,l.width()-12,12,tr("filter_hint"));filterBox.setMaxLength(16);filterBox.setBordered(false);filterBox.setTextColor(TEXT);filterBox.setHint(tr("filter_hint"));filterBox.setValue(filterText);filterBox.setResponder(v->{filterText=v;listScroll=0;page=0;filterChangedAt=net.minecraft.Util.getMillis();});addWidget(filterBox);
        updateInputs();if(pending==null)request(SocialBook.Action.LIST,"");
    }
    private boolean detailVisible(){return target!=null;}
    private boolean listVisible(){return layout.wide()||target==null;}
    private void updateInputs(){boolean send=state!=null&&detailVisible()&&state.relation()==SocialBook.Relation.FRIEND&&before==0;input.setVisible(send);input.setEditable(pending==null&&sendAttempt==null);filterBox.setVisible(listVisible());}
    private void request(SocialBook.Action action,String text){if(pending!=null)return;pending=UUID.randomUUID();pendingAction=action;requestedAt=net.minecraft.Util.getMillis();
        var packet=new SocialRequestPayload(pending,target,action,state==null?0:state.revision(),text,filterText,filter,page,before);
        if(action==SocialBook.Action.SEND)sendAttempt=packet;
        PacketDistributor.sendToServer(packet);updateInputs();}
    private void pick(UUID id){sendAttempt=null;readPending=null;target=id;before=0;lastRead=0;confirmation=null;state=null;input.setValue("");scrollToEnd=true;messageScroll=0;request(SocialBook.Action.LIST,"");}
    @Override public void tick(){if(minecraft.player==null||minecraft.getConnection()==null){minecraft.setScreen(null);return;}long now=net.minecraft.Util.getMillis();
        if(pending!=null&&now-requestedAt>8000){pending=null;result="timeout";updateInputs();}
        if(pending==null&&filterChangedAt!=0&&now-filterChangedAt>350){filterChangedAt=0;request(SocialBook.Action.LIST,"");}
        if(pending==null&&now-lastReply>3000)request(SocialBook.Action.LIST,"");
        if(confirmation==null&&state!=null&&target!=null&&before==0&&!state.messages().isEmpty()&&messageScroll>=Math.max(0,messageHeight-layout.messages().height())){
            long latest=state.messages().getLast().id();if(readPending!=null&&now-readRequestedAt>8000)readPending=null;
            if(latest>lastRead&&readPending==null){readPending=UUID.randomUUID();readCursor=latest;readRequestedAt=now;PacketDistributor.sendToServer(new SocialRequestPayload(readPending,target,SocialBook.Action.READ,state.revision(),"",filterText,filter,page,latest));}
        }
    }
    private void action(SocialBook.Action action){if(action==SocialBook.Action.BLOCK||action==SocialBook.Action.REMOVE){confirmation=action;return;}request(action,"");}
    private void send(){if(pending==null&&sendAttempt!=null){pending=sendAttempt.request();pendingAction=SocialBook.Action.SEND;requestedAt=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(sendAttempt);updateInputs();return;}if(pending==null&&state!=null&&state.relation()==SocialBook.Relation.FRIEND&&before==0&&!input.getValue().isBlank()){scrollToEnd=true;request(SocialBook.Action.SEND,input.getValue());}}
    private void button(GuiGraphics g,SocialLayout.Rect r,Component label,Runnable run,boolean enabled,boolean primary,double mx,double my){buttons.add(new Button(r,label,run,enabled,primary));control(g,new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height()),false,enabled&&r.contains(mx,my),enabled,primary);g.drawString(font,fit(font,label,r.width()-12),r.x()+6,r.y()+8,enabled?TEXT:MUTED,false);}
    private void text(GuiGraphics g,Component value,int x,int y,int width,int color){g.drawString(font,fit(font,value,width),x,y,color,false);}
    private void clip(GuiGraphics g,SocialLayout.Rect r){var s=layout.screen(r);g.flush();g.enableScissor(s.x(),s.y(),s.right(),s.bottom());}
    private void unclip(GuiGraphics g){g.flush();g.disableScissor();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float pt){double mx=layout.localX(mouseX),my=layout.localY(mouseY);buttons.clear();
        g.fill(0,0,width,height,0x90090D10);g.pose().pushPose();g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);panel(g,0,0,layout.width(),layout.height());
        text(g,tr("title"),52,17,layout.width()-210,ACCENT);button(g,layout.back(),Component.literal("‹"),()->{if(target!=null&&!layout.wide()){target=null;before=0;confirmation=null;state=null;request(SocialBook.Action.LIST,"");}else onClose();},true,false,mx,my);
        button(g,layout.search(),tr("find"),()->PlayerProfileScreen.openSearch(this),true,false,mx,my);button(g,layout.close(),Component.literal("×"),this::onClose,true,false,mx,my);
        String[] tabs={"friends","requests","dialogs","blocked"};for(int i=0;i<4;i++){final int f=i;Component title=tr(tabs[i]);if(state!=null&&i==1)title=title.copy().append(" "+state.requests());if(state!=null&&i==2&&state.unread()>0)title=title.copy().append(" "+state.unread());
            var r=layout.tab(i);button(g,r,title,()->{filter=f;page=0;listScroll=0;target=null;before=0;state=null;confirmation=null;request(SocialBook.Action.LIST,"");},pending==null,false,mx,my);if(filter==i)g.fill(r.x()+5,r.bottom()-2,r.right()-5,r.bottom()-1,ACCENT);}
        if(listVisible())renderList(g,mx,my,pt);if(detailVisible())renderDetail(g,mx,my,pt);
        else if(layout.wide()){var r=layout.detail();rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);text(g,tr("choose"),r.x()+16,r.y()+20,r.width()-32,ACCENT);g.drawWordWrap(font,tr("privacy_note"),r.x()+16,r.y()+44,r.width()-32,MUTED);}
        if(!input.isVisible()&&before==0)text(g,tr("result."+result),layout.wide()&&target!=null?202:86,layout.height()-25,layout.width()-(layout.wide()&&target!=null?216:100),MUTED);
        if(confirmation!=null){input.setFocused(false);var r=layout.detail();g.fill(r.x(),r.y(),r.right(),r.bottom(),0xF01B2327);g.drawWordWrap(font,tr("confirm."+confirmation.name().toLowerCase(Locale.ROOT)),r.x()+12,r.y()+20,r.width()-24,ACCENT);
            button(g,new SocialLayout.Rect(r.x()+12,r.y()+56,110,24),tr("confirm"),()->{var a=confirmation;confirmation=null;request(a,"");},pending==null,true,mx,my);
            button(g,new SocialLayout.Rect(r.x()+128,r.y()+56,110,24),tr("cancel"),()->confirmation=null,true,false,mx,my);}
        g.flush();g.pose().popPose();
    }
    private void renderList(GuiGraphics g,double mx,double my,float pt){var r=layout.list();rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);filterBox.render(g,(int)mx,(int)my,pt);visible.clear();if(state!=null)state.contacts().stream().filter(c->c.name().toLowerCase(Locale.ROOT).contains(filterText.toLowerCase(Locale.ROOT))).forEach(visible::add);
        int max=Math.max(0,visible.size()*42-(r.height()-29));listScroll=Math.clamp(listScroll,0,max);var body=new SocialLayout.Rect(r.x(),r.y()+28,r.width(),r.height()-28);clip(g,body);
        if(visible.isEmpty())text(g,tr(pending!=null?"result.loading":"empty."+filter),r.x()+10,r.y()+45,r.width()-20,MUTED);
        for(int i=0;i<visible.size();i++){var c=visible.get(i);var row=layout.row(i,listScroll-28);boolean over=body.contains(mx,my)&&row.contains(mx,my);rounded(g,row.x(),row.y(),row.width(),row.height(),3,Objects.equals(c.id(),target)?RAISED:over?SURFACE:INSET);
            g.fill(row.x()+7,row.y()+10,row.x()+10,row.y()+13,c.online()?0xFF8EC7AA:0xFF68777B);text(g,Component.literal(c.name()),row.x()+16,row.y()+7,row.width()-22,TEXT);
            text(g,tr("relation."+c.relation().name().toLowerCase(Locale.ROOT)),row.x()+16,row.y()+22,row.width()-45,MUTED);if(c.unread()>0)text(g,Component.literal("+"+c.unread()),row.right()-30,row.y()+22,27,ACCENT);
        }unclip(g);
        button(g,layout.previous(),Component.literal("‹"),()->{page--;request(SocialBook.Action.LIST,"");},pending==null&&page>0,false,mx,my);button(g,layout.next(),Component.literal("›"),()->{page++;request(SocialBook.Action.LIST,"");},pending==null&&state!=null&&(page+1)*16<state.total(),false,mx,my);
    }
    private List<SocialBook.Action> actions(){if(state==null||state.target()==null)return List.of();return switch(state.relation()){
        case NONE->List.of(SocialBook.Action.REQUEST,SocialBook.Action.BLOCK);case INCOMING->List.of(SocialBook.Action.ACCEPT,SocialBook.Action.DECLINE,SocialBook.Action.BLOCK);
        case OUTGOING->List.of(SocialBook.Action.CANCEL,SocialBook.Action.BLOCK);case FRIEND->List.of(SocialBook.Action.REMOVE,SocialBook.Action.BLOCK);
        case BLOCKED->List.of(SocialBook.Action.UNBLOCK);case UNAVAILABLE->List.of();};}
    private void renderDetail(GuiGraphics g,double mx,double my,float pt){var r=layout.detail();rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);text(g,state==null?tr("result.loading"):Component.literal(state.name()),r.x()+8,r.y()+8,r.width()-114,ACCENT);
        if(state==null)return;button(g,layout.older(),tr("profile"),()->PlayerProfileScreen.open(this,target),pending==null,false,mx,my);
        var actions=actions();for(int i=0;i<actions.size();i++){var action=actions.get(i);button(g,layout.action(i,actions.size()),tr("action."+action.name().toLowerCase(Locale.ROOT)),()->action(action),pending==null,action==SocialBook.Action.REQUEST||action==SocialBook.Action.ACCEPT,mx,my);}
        if(!result.equals("ok")&&!result.equals("loading"))text(g,tr("result."+result),r.x()+6,r.y()+67,r.width()-116,ACCENT);
        var body=layout.messages();int y=body.y()+4-messageScroll;int content=4;
        for(var message:state.messages()){var lines=font.split(Component.literal(message.text()),body.width()-42);content+=25+lines.size()*11+8;}messageHeight=content;int maximum=Math.max(0,messageHeight-body.height());messageScroll=Math.clamp(messageScroll,0,maximum);y=body.y()+4-messageScroll;
        clip(g,body);
        if(state.messages().isEmpty()){text(g,tr("relation."+state.relation().name().toLowerCase(Locale.ROOT)),body.x()+12,body.y()+14,body.width()-24,TEXT);g.drawWordWrap(font,tr(state.relation()==SocialBook.Relation.FRIEND?"first_message":"friends_only"),body.x()+12,body.y()+37,body.width()-24,MUTED);}
        for(var m:state.messages()){boolean own=m.sender().equals(minecraft.player.getUUID());var lines=font.split(Component.literal(m.text()),body.width()-42);int h=25+lines.size()*11;int x=body.x()+(own?16:4);int w=body.width()-20;
            rounded(g,x,y,w,h,4,own?0xFF303630:SURFACE);String when=DateTimeFormatter.ofPattern("dd.MM HH:mm").format(Instant.ofEpochMilli(m.time()).atZone(ZoneId.systemDefault()));text(g,Component.literal((own?tr("you").getString():state.name())+" · "+when),x+8,y+7,w-16,own?ACCENT:MUTED);
            for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),x+8,y+21+i*11,TEXT,false);y+=h+8;
        }unclip(g);
        if(input.isVisible()){var field=layout.input();rounded(g,field.x(),field.y(),field.width(),field.height(),3,input.isFocused()?RAISED:INSET);input.render(g,(int)mx,(int)my,pt);button(g,layout.send(),tr("send"),this::send,pending==null&&!input.getValue().isBlank(),true,mx,my);}
        else if(before!=0)button(g,layout.input(),tr("latest"),()->{before=0;scrollToEnd=true;request(SocialBook.Action.LIST,"");},pending==null,false,mx,my);
        // History navigation is explicit, never an implicit read-all operation.
        if(state.older())button(g,new SocialLayout.Rect(body.x()+body.width()-104,body.y()-24,100,20),tr("older"),()->{before=state.messages().getFirst().id();messageScroll=0;request(SocialBook.Action.LIST,"");},pending==null,false,mx,my);
    }
    @Override public boolean mouseClicked(double x,double y,int button){double mx=layout.localX(x),my=layout.localY(y);if(button!=0)return false;
        if(confirmation!=null){for(int i=buttons.size()-1;i>=0;i--){var b=buttons.get(i);if(b.r.y()==layout.detail().y()+56&&b.r.contains(mx,my)){if(b.enabled)b.run.run();return true;}}return true;}
        for(int i=buttons.size()-1;i>=0;i--){var b=buttons.get(i);if(b.r.contains(mx,my)){if(b.enabled)b.run.run();return true;}}
        if(pending==null&&listVisible()&&layout.list().contains(mx,my)&&my>=layout.list().y()+28)for(int i=0;i<visible.size();i++)if(layout.row(i,listScroll-28).contains(mx,my)){pick(visible.get(i).id());return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){double mx=layout.localX(x),my=layout.localY(y);if(listVisible()&&layout.list().contains(mx,my)){listScroll=Math.max(0,listScroll-(int)(dy*24));return true;}if(target!=null&&layout.messages().contains(mx,my)){messageScroll=Math.clamp(messageScroll-(int)(dy*24),0,Math.max(0,messageHeight-layout.messages().height()));return true;}return false;}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return super.mouseDragged(layout.localX(x),layout.localY(y),b,dx/layout.scale(),dy/layout.scale());}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(confirmation!=null&&key==256){confirmation=null;return true;}if(confirmation==null&&(key==257||key==335)&&input.isFocused()){send();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void removed(){pending=null;readPending=null;super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
