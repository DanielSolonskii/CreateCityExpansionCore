package com.example.examplemod.client.screen;

import com.example.examplemod.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.screen.LogisticsStyle.*;

/** City sales retain explicit selection and confirmation; only presentation is client-owned. */
public final class FreightSellScreen extends CityPylonPageScreen {
    private final CityPylonScreen pylon;
    private UUID selected,destination;
    private int scroll,ticks,submittedAt;
    private boolean submitted;
    private Button confirm,point,create;
    public FreightSellScreen(CityPylonScreen pylon){super(FreightUi.tr("city_sales"));this.pylon=pylon;}
    private EconomyStatePayload state(){return pylon.getMenu().getEconomyState();}
    private ListTag sales(){return state()==null?new ListTag():state().logistics().getList("Sales",Tag.TAG_COMPOUND);}
    private CompoundTag selection(){
        for(Tag raw:sales())if(((CompoundTag)raw).getUUID("Id").equals(selected))return (CompoundTag)raw;
        return null;
    }
    private int rows(){return Math.max(1,(height-154)/64);}
    @Override protected void init(){
        clearWidgets();int bw=(width-48)/2;
        addRenderableWidget(new Action(20,height-28,bw,FreightUi.tr("back"),false,this::onClose));
        create=addRenderableWidget(new Action(28+bw,height-28,bw,FreightUi.tr("create_sale"),false,
                ()->minecraft.setScreen(new EconomyCreateScreen(this,EconomyCreateScreen.Mode.CITY_SELL,pylon.getMenu().getPylonPos(),state()))));
        point=addRenderableWidget(new Action(20,height-84,width-40,FreightUi.tr("choose_receive"),false,()->{
            var choices=FreightUi.choices(state(),true);
            if(choices.isEmpty())return;
            int index=-1;for(int i=0;i<choices.size();i++)if(choices.get(i).id.equals(destination))index=i;
            destination=choices.get((index+1)%choices.size()).id;
        }));
        confirm=addRenderableWidget(new Action(20,height-56,width-40,FreightUi.tr("confirm"),true,()->{
            if(selected==null||destination==null)return;
            submitted=true;submittedAt=ticks;
            PacketDistributor.sendToServer(new EconomyCommandPayload(pylon.getMenu().getPylonPos(),
                    EconomyCommandPayload.Action.ACCEPT_FREIGHT_SELL,selected,destination,null,null,0,0,0,0,"","",""));
        }));
        confirm.active=false;create.active=state()!=null;
    }
    @Override public void tick(){
        if(++ticks%20==0)PacketDistributor.sendToServer(EconomyCommandPayload.simple(pylon.getMenu().getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));
        if(submitted&&selected!=null&&state()!=null&&selection()==null){
            minecraft.setScreen(new LogisticsScreen(pylon));return;
        }
        if(submitted&&ticks-submittedAt>=100)submitted=false;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(new MarketScreen(pylon));}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        g.fill(0,0,width,height,BG);g.fill(0,0,width,34,0xFF192A32);
        g.drawCenteredString(font,title,width/2,12,GOLD);
        var list=sales();scroll=Math.clamp(scroll,0,Math.max(0,list.size()-rows()));
        CityPylonWorkspace.clip(g,20,40,width-20,height-104);
        if(state()==null)text(g,font,FreightUi.tr("loading"),26,48,width-52,MUTED);
        else if(list.isEmpty()){
            icon(g,"minecraft:chest",26,47);text(g,font,tr("sales_empty"),50,50,width-76,GOLD);
            g.drawWordWrap(font,tr("sales_empty_help"),26,72,width-52,MUTED);
        }
        for(int i=scroll;i<Math.min(list.size(),scroll+rows());i++){
            CompoundTag s=list.getCompound(i);int y=40+(i-scroll)*64;
            panel(g,20,y,width-40,58,s.getUUID("Id").equals(selected)?0xFF354438:PANEL);
            if(g!=null)g.renderItem(item(s.getString("Item")),28,y+8);
            text(g,font,FreightUi.tr("cargo",s.getLong("Amount"),item(s.getString("Item")).getHoverName()),52,y+9,width-80,TEXT);
            text(g,font,Component.literal(s.getString("CityName")).append(" · "+s.getLong("Reward")+" CR"),28,y+27,width-56,GOLD);
            text(g,font,FreightUi.tr("deadline",s.getLong("Deadline")==0?"∞":Long.toString(s.getLong("Deadline")/1200)),28,y+43,width-56,MUTED);
        }
        g.disableScissor();
        var choices=FreightUi.choices(state(),true);
        if(destination==null&&!choices.isEmpty())destination=choices.getFirst().id;
        var receiver=choices.stream().filter(p->p.id.equals(destination)).findFirst().orElse(null);
        point.setMessage(receiver==null?FreightUi.tr("no_receive"):FreightUi.tr("destination",receiver.name));
        point.active=!submitted&&!choices.isEmpty();create.active=state()!=null&&!submitted;
        CompoundTag s=selection();
        confirm.active=!submitted&&s!=null&&state()!=null&&!s.getUUID("City").equals(state().cityId())&&receiver!=null;
        confirm.setMessage(submitted?FreightUi.tr("loading"):s==null?tr("select_sale"):
                FreightUi.tr("confirm").copy().append(" · "+s.getLong("Reward")+" CR"));
        if(list.size()>rows())text(g,font,tr("list_count",scroll+1,Math.min(list.size(),scroll+rows()),list.size()),20,height-99,width-40,MUTED);
        super.render(g,mx,my,pt);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        if(button==0&&!submitted&&x>=20&&x<width-20&&y>=40&&y<height-104){
            int row=(int)(y-40)/64,i=scroll+row;
            if(row<rows()&&i<sales().size()&&(y-40)%64<58)selected=sales().getCompound(i).getUUID("Id");
            return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(y<40||y>=height-104||dy==0)return false;
        scroll=Math.clamp(scroll+(dy<0?1:-1),0,Math.max(0,sales().size()-rows()));return true;
    }
}
