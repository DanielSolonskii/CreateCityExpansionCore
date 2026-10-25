package com.example.examplemod.client.screen;

import com.example.examplemod.economy.*;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.math.BigInteger;
import java.util.*;
import static com.example.examplemod.client.screen.LogisticsStyle.*;

/** Selection and explicit confirmation share one screen for all three freight trades. */
public final class FreightConfirmScreen extends CityPylonPageScreen {
    private final Screen parent;

    private final com.example.examplemod.client.WorldMapDataSource menu;
    private final UUID subject;
    private final boolean market;
    private final long quantity;
    private UUID selected;
    private int ticks,scroll,submittedAt,contentHeight;
    private boolean submitted,diagnostics;
    private Button choice,confirm;

    public FreightConfirmScreen(Screen parent,CityPylonScreen pylon,UUID subject,boolean market,long quantity){
        this(parent,pylon.getMenu(),subject,market,quantity);
    }
    public FreightConfirmScreen(Screen parent,com.example.examplemod.client.WorldMapDataSource source,UUID subject,boolean market,long quantity){
        super(FreightUi.tr("review"));this.parent=parent;
        this.menu=source;this.subject=subject;this.market=market;this.quantity=quantity;
    }
    private EconomyStatePayload.OfferView offer(){
        var s=menu.getEconomyState();
        return s==null?null:s.offers().stream().filter(o->o.id().equals(subject)).findFirst().orElse(null);
    }
    private EconomyStatePayload.ContractView contract(){
        var s=menu.getEconomyState();
        return s==null?null:s.contracts().stream().filter(c->c.id().equals(subject)).findFirst().orElse(null);
    }
    private boolean importing(){return market&&offer()!=null&&offer().direction()==EconomyTypes.MarketDirection.BUY_FROM_MASTER;}
    @Override protected void init(){
        clearWidgets();
        choice=addRenderableWidget(new Action(20,height-84,width-40,FreightUi.tr("choose"),false,()->{
            var options=FreightUi.choices(menu.getEconomyState(),importing());
            if(!options.isEmpty()){
                int index=-1;for(int i=0;i<options.size();i++)if(options.get(i).id.equals(selected))index=i;
                selected=options.get((index+1)%options.size()).id;
            }
        }));
        addRenderableWidget(new Action(20,height-57,width-40,tr("diagnostics"),false,()->diagnostics=!diagnostics));
        int bw=(width-48)/3;
        addRenderableWidget(new Action(20,height-28,bw,FreightUi.tr("cancel"),false,this::onClose));
        addRenderableWidget(new Action(24+bw,height-28,bw,FreightUi.tr("setup"),false,
                ()->minecraft.setScreen(new LogisticsScreen(parent,menu,subject))));
        confirm=addRenderableWidget(new Action(28+2*bw,height-28,bw,FreightUi.tr("confirm"),true,()->{
            submitted=true;submittedAt=ticks;
            PacketDistributor.sendToServer(new EconomyCommandPayload(menu.getPylonPos(),
                    market?EconomyCommandPayload.Action.ACCEPT_MARKET_OFFER:EconomyCommandPayload.Action.ACCEPT_CONTRACT,
                    subject,selected,null,null,quantity,0,0,0,"","",""));
        }));
        confirm.active=false;
        var options=FreightUi.choices(menu.getEconomyState(),importing());
        if(selected==null&&!options.isEmpty())selected=options.getFirst().id;
    }
    @Override public void tick(){
        if(++ticks%20==0)PacketDistributor.sendToServer(EconomyCommandPayload.simple(menu.getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));
        if(submitted&&((market&&offer()!=null&&offer().mine())||(!market&&contract()!=null&&contract().mine())))
            minecraft.setScreen(new LogisticsScreen(parent,menu,subject));
        if(submitted&&ticks-submittedAt>=100)submitted=false;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    private int maxScroll(){return Math.max(0,contentHeight-Math.max(1,height-136));}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(y<40||y>=height-94||dy==0)return false;
        scroll=Math.max(0,Math.min(maxScroll(),scroll+(dy<0?24:-24)));return true;
    }
    private int row(GuiGraphics g,Component value,int y,int color) {
        var lines=font.split(value,Math.max(40,width-64));
        int h=lines.size()*12+12;panel(g,20,y,width-40,h,PANEL);
        for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),32,y+7+i*12,color,false);
        return y+h+5;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        g.fill(0,0,width,height,BG);g.fill(0,0,width,34,0xFF192A32);
        g.drawCenteredString(font,title,width/2,12,GOLD);
        var s=menu.getEconomyState();var o=offer();var c=contract();
        FreightPoint own=FreightUi.point(s,selected),destination=null,source=null;
        confirm.active=false;
        if(s!=null){
            UUID destId=market?(importing()?selected:s.logistics().hasUUID("MasterReceive")?s.logistics().getUUID("MasterReceive"):null)
                    :c==null?null:FreightUi.destination(s,subject,c.destinationId());
            destination=FreightUi.point(s,destId);
            source=importing()?FreightUi.point(s,s.logistics().hasUUID("MasterSend")?s.logistics().getUUID("MasterSend"):null):own;
            int start=42-scroll,y=start;
            CityPylonWorkspace.clip(g,18,40,width-18,height-94);
            if(o!=null||c!=null){
                var item=o!=null?o.itemId():c.itemId();
                long amount=o!=null?quantity:c.amount();
                BigInteger reward=o!=null?BigInteger.valueOf(quantity).multiply(BigInteger.valueOf(o.unitPrice())):BigInteger.valueOf(c.reward());
                g.renderItem(new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(item)),22,y);
                text(g,font,FreightUi.tr("cargo",amount,BuiltInRegistries.ITEM.get(item).getDescription()),46,y+4,width-70,GOLD);y+=27;
                y=row(g,FreightUi.tr("reward",reward.toString()),y,TEXT);
                long deadline=o!=null?o.expiresAt():c.deadlineAt(),now=minecraft.level==null?0:minecraft.level.getGameTime();
                y=row(g,FreightUi.tr("deadline",deadline==0?"∞":Long.toString(Math.max(0,(deadline-now)/1200))),y,TEXT);
                if(o!=null)y=row(g,FreightUi.tr("bond",reward.multiply(BigInteger.valueOf(o.bondBasisPoints())).divide(BigInteger.valueOf(10000)).toString()),y,TEXT);
                if(c!=null)y=row(g,Component.literal(c.issuerName()),y,TEXT);
            }
            y=row(g,FreightUi.tr("source",source==null?tr("unassigned"):source.name),y,TEXT);
            y=row(g,FreightUi.tr("destination",destination==null?tr("unassigned"):destination.name),y,TEXT);
            if(own!=null&&!own.online())y=row(g,FreightUi.tr(own.status),y,RED);
            String route=source==null||destination==null?"unknown":FreightUi.route(s,source.id,destination.id);
            y=row(g,FreightUi.tr("route."+route),y,route.equals("connected")?GREEN:GOLD);
            if(source!=null&&destination!=null)y=row(g,Component.literal(source.stationName+" → "+destination.stationName),y,MUTED);
            y=row(g,FreightUi.tr("tariffs"),y,MUTED);
            if(diagnostics&&destination!=null)y=row(g,Component.literal(destination.address),y,MUTED);
            contentHeight=y-start;
            g.disableScissor();
            choice.setMessage(FreightUi.tr(importing()?"destination":"source",own==null?FreightUi.tr("choose"):own.name));
            choice.active=!submitted&&!FreightUi.choices(s,importing()).isEmpty();
            confirm.active=!submitted&&(o!=null||c!=null)&&own!=null&&destination!=null&&source!=null&&own.enabled&&!own.removed;
        }else{
            text(g,font,FreightUi.tr("loading"),20,46,width-40,MUTED);choice.active=false;
        }
        if(contentHeight>height-136){
            int track=Math.max(1,height-136),thumb=Math.max(12,track*track/Math.max(1,contentHeight));
            int y=40+(track-thumb)*Math.min(scroll,maxScroll())/Math.max(1,maxScroll());
            g.fill(width-12,y,width-10,y+thumb,GOLD);
        }
        if(scroll>maxScroll())scroll=maxScroll();
        super.render(g,mx,my,pt);
    }
}
