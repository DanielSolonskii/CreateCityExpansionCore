package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityWorldMapSession;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server membership discovery, without opening or fabricating a pylon container. */
public final class RemoteMarketEntry extends Screen {
    private final Screen parent;
    private int ticks;
    private java.util.UUID project;private String projectName;
    public static void supplies(Screen parent,java.util.UUID id,String title){CityWorldMapSession.INSTANCE.awaitAnchor();var entry=new RemoteMarketEntry(parent);entry.project=id;entry.projectName=title;Minecraft.getInstance().setScreen(entry);}
    private RemoteMarketEntry(Screen parent){super(Component.translatable("gui.createcityexpansioncore.trade.inventory"));this.parent=parent;}
    public static void open(Screen parent){CityWorldMapSession.INSTANCE.awaitAnchor();Minecraft.getInstance().setScreen(new RemoteMarketEntry(parent));}
    @Override protected void init(){request();}
    private void request(){PacketDistributor.sendToServer(new RequestCityWorldMapPayload());PacketDistributor.sendToServer(EconomyCommandPayload.simple(BlockPos.ZERO,EconomyCommandPayload.Action.REQUEST_STATE,null));}
    @Override public void tick(){
        if(++ticks%60==0)request();
        if(CityWorldMapSession.INSTANCE.getEconomyState()!=null) minecraft.setScreen(project==null?new MarketScreen(parent,CityWorldMapSession.INSTANCE):new ProjectSuppliesScreen(parent,CityWorldMapSession.INSTANCE,project,projectName));
    }
    @Override public void render(GuiGraphics g,int x,int y,float dt){
        renderBackground(g,x,y,dt);
        WorldMapChrome.rounded(g,width/2-130,height/2-30,260,60,4,WorldMapChrome.SURFACE);
        g.drawCenteredString(font,Component.translatable(ticks<160?"gui.createcityexpansioncore.economy.loading":"gui.createcityexpansioncore.trade.unavailable"),width/2,height/2-4,WorldMapChrome.TEXT);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
