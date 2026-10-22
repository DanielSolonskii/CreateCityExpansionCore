package com.example.examplemod.client.screen;

import com.example.examplemod.client.*;
import com.example.examplemod.daily.DailyRewardsConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.time.LocalDate;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Compact seven-day window. Item components, dates, availability and rolls are supplied by the server. */
public final class DailyRewardsScreen extends Screen {
    private final Screen parent;private DailyRewardsLayout layout;private int wood;private long opened;
    private static final ResourceLocation GIFT=ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","textures/gui/community/gift.png");
    public DailyRewardsScreen(Screen parent){super(tr("title"));this.parent=parent;}
    public static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.daily."+key,args);}
    @Override protected void init(){layout=DailyRewardsLayout.of(width,height);if(opened==0){opened=net.minecraft.Util.getMillis();CommunityFx.load();CommunityFx.sound("open");}}
    public static void gift(GuiGraphics g,int x,int y){g.blit(GIFT,x,y,0,0,16,16,16,16);}
    private void text(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,fit(font,text,width),x,y,color,false);}
    private void control(GuiGraphics g,SocialHubLayout.Rect r,Component label,double mx,double my,boolean enabled,boolean primary){WorldMapChrome.control(g,new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height()),false,enabled&&r.contains(mx,my),enabled,primary);g.drawCenteredString(font,fit(font,label,r.width()-12),r.x()+r.width()/2,r.y()+10,enabled?TEXT:MUTED);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        var t=DailyRewardsClient.state();double mx=layout.localX(mouseX),my=layout.localY(mouseY);boolean pending=t.getBoolean("Pending"),available=DailyRewardsClient.available();int day=t.getInt("Day");var cards=t.getList("Cards",10);ItemStack tooltip=ItemStack.EMPTY;Component extra=null;
        g.fill(0,0,width,height,0x85090E10);g.pose().pushPose();g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);
        panel(g,0,0,DailyRewardsLayout.WIDTH,DailyRewardsLayout.HEIGHT);gift(g,20,18);text(g,title,45,20,DailyRewardsLayout.WIDTH-90,ACCENT);control(g,layout.close(),Component.literal("×"),mx,my,true,false);
        text(g,tr(t.getBoolean("Starter")?"starter":"regular"),20,46,300,TEXT);text(g,tr("day",Math.max(1,day)),360,46,132,ACCENT);
        if(t.contains("Today"))text(g,Component.literal(LocalDate.ofEpochDay(t.getLong("Today"))+" · "+t.getString("Zone")),20,65,472,MUTED);
        else text(g,tr(t.getString("Result").equals("error")?"result.error":"loading"),20,65,472,MUTED);
        for(int i=0;i<7;i++){
            var r=layout.card(i);int number=i+1;boolean current=number==day,done=number<day||current&&t.getBoolean("Claimed")&&!pending;
            int color=done?0xFF82B99C:current?ACCENT:EDGE;rounded(g,r.x(),r.y(),r.width(),r.height(),5,color);rounded(g,r.x()+1,r.y()+1,r.width()-2,r.height()-2,4,current?RAISED:INSET);
            g.drawCenteredString(font,tr("day",number),r.x()+r.width()/2,r.y()+9,current?ACCENT:MUTED);
            if(i<cards.size()){var card=cards.getCompound(i);ItemStack item=ItemStack.parseOptional(minecraft.level.registryAccess(),card.getCompound("Icon"));
                if(current&&t.getBoolean("NeedsWood"))item=new ItemStack(DailyRewardsConfig.WOODS.get(wood),item.getCount());
                g.pose().pushPose();g.pose().translate(r.x()+16,r.y()+29,0);g.pose().scale(2,2,1);g.renderItem(item,0,0);g.pose().popPose();
                Component amount=card.getInt("Levels")>0?tr("levels",card.getInt("Levels")):Component.literal("×"+item.getCount());g.drawCenteredString(font,amount,r.x()+32,r.y()+70,TEXT);
                Component status=tr(done?"taken":current?(pending?"pending_short":available?"today":"taken"):"future");g.drawCenteredString(font,fit(font,status,58),r.x()+32,r.y()+89,current?ACCENT:MUTED);
                if(card.getBoolean("StarterUsed"))g.fill(r.x()+r.width()-9,r.y()+5,r.x()+r.width()-5,r.y()+9,0xFF82B99C);
                if(r.contains(mx,my)){tooltip=item;extra=card.getInt("Levels")>0?tr("levels_hint",card.getInt("Levels")):card.getString("Type").equals("trim")?tr("trim_hint"):card.getString("Type").equals("chance")?tr("chance_hint",String.format(Locale.ROOT,"%.0f",card.getDouble("Chance")*100),ItemStack.parseOptional(minecraft.level.registryAccess(),card.getCompound("Alternative")).getHoverName()):card.getBoolean("StarterUsed")?tr("unique_taken"):null;}
            }
        }
        if(t.getBoolean("NeedsWood")){
            text(g,tr("wood"),20,225,472,MUTED);
            for(int i=0;i<DailyRewardsConfig.WOODS.size();i++){var r=layout.wood(i);WorldMapChrome.control(g,new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height()),i==wood,r.contains(mx,my),!DailyRewardsClient.waiting(),false);var stack=new ItemStack(DailyRewardsConfig.WOODS.get(i));g.renderItem(stack,r.x()+5,r.y()+6);if(r.contains(mx,my)){tooltip=stack;extra=null;}}
            text(g,new ItemStack(DailyRewardsConfig.WOODS.get(wood)).getHoverName(),272,254,220,TEXT);
        }else{
            var lines=font.split(tr(pending?"pending_hint":t.getBoolean("Starter")?"starter_hint":"regular_hint"),472);for(int i=0;i<Math.min(3,lines.size());i++)g.drawString(font,lines.get(i),20,225+i*12,MUTED,false);
        }
        control(g,layout.later(),tr("later"),mx,my,true,false);control(g,layout.claim(),tr(DailyRewardsClient.waiting()?"loading":pending?"collect_pending":available?"claim":"taken_today"),mx,my,available&&!DailyRewardsClient.waiting(),true);
        text(g,tr("result."+(t.getString("Result").isBlank()?"ready":t.getString("Result"))),20,324,472,t.getString("Result").equals("received")?0xFF82B99C:MUTED);
        float fade=1-CommunityFx.ease((net.minecraft.Util.getMillis()-opened)/180d);if(fade>0)g.fill(1,1,511,343,((int)(90*fade)<<24)|(SURFACE&0xFFFFFF));g.flush();g.pose().popPose();
        if(!tooltip.isEmpty()){var lines=new ArrayList<Component>(Screen.getTooltipFromItem(minecraft,tooltip));if(extra!=null)lines.add(extra.copy().withStyle(net.minecraft.ChatFormatting.GRAY));g.renderComponentTooltip(font,lines,mouseX,mouseY);}
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;double mx=layout.localX(x),my=layout.localY(y);if(layout.close().contains(mx,my)||layout.later().contains(mx,my)){onClose();return true;}if(layout.claim().contains(mx,my)){DailyRewardsClient.claim(wood);return true;}if(DailyRewardsClient.state().getBoolean("NeedsWood")&&!DailyRewardsClient.waiting())for(int i=0;i<DailyRewardsConfig.WOODS.size();i++)if(layout.wood(i).contains(mx,my)){wood=i;CommunityFx.sound("click");return true;}return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
