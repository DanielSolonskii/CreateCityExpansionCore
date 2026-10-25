package com.example.examplemod.client.screen;

import com.example.examplemod.economy.EconomyTypes;
import com.example.examplemod.menu.EconomyInfrastructureMenu;
import com.example.examplemod.network.InfrastructureCommandPayload;
import com.example.examplemod.network.InfrastructureStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EconomyInfrastructureScreen extends AbstractContainerScreen<EconomyInfrastructureMenu> {
    /**
     * Smoothed hover for the one button this window has. Container screens are not page screens, so
     * this window owns its own motion rather than borrowing the shared one.
     */
    private final UiMotion motion = new UiMotion();
    /** Sampled once in {@link #renderBg}: a second read in the same frame would return zero. */
    private float frameStep = 1f;
    private int refresh;
    public EconomyInfrastructureScreen(EconomyInfrastructureMenu menu,Inventory inventory,Component title){super(menu,inventory,title);this.imageWidth=340;this.imageHeight=220;}
    @Override protected void init(){super.init();request();}
    private void request(){PacketDistributor.sendToServer(new InfrastructureCommandPayload(menu.pos(),InfrastructureCommandPayload.Action.REQUEST));}
    @Override public void containerTick(){super.containerTick();if(++refresh>=40){refresh=0;request();}}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        frameStep = motion.delta(net.minecraft.Util.getMillis());
        int l=leftPos,t=topPos;g.fill(l,t,l+imageWidth,t+imageHeight,0xEE151B1E);g.fill(l,t,l+imageWidth,t+3,0xFFD09A3A);
        InfrastructureStatePayload state=menu.state();
        if(state==null){g.drawCenteredString(font,Component.translatable("gui.createcityexpansioncore.economy.loading"),l+170,t+102,0xFFAEB8BB);return;}
        g.drawString(font,Component.translatable(state.infrastructureType()==EconomyTypes.InfrastructureType.CONTRACT_INTAKE?"block.createcityexpansioncore.contract_intake":"block.createcityexpansioncore.warehouse_gateway"),l+16,t+15,0xFFFFFFFF,false);
        g.drawString(font,Component.translatable("gui.createcityexpansioncore.economy.infrastructure.city_name",state.cityName().isBlank()?"—":state.cityName()),l+16,t+32,0xFF8F9A9E,false);
        if(state.infrastructureType()==EconomyTypes.InfrastructureType.WAREHOUSE_GATEWAY){g.drawString(font,Component.translatable("message.createcityexpansioncore.economy.gateway_mode",Component.translatable("mode.createcityexpansioncore.economy."+state.mode().name().toLowerCase())),l+16,t+62,0xFFB7C0C3,false);button(g,l+16,t+90,308,Component.translatable("gui.createcityexpansioncore.economy.toggle_gateway"),mouseX,mouseY);g.drawWordWrap(font,Component.translatable("tooltip.createcityexpansioncore.economy.warehouse_gateway"),l+16,t+126,308,0xFF8F9A9E);return;}
        g.drawString(font,Component.translatable("gui.createcityexpansioncore.economy.infrastructure.station",state.stationName().isBlank()?Component.translatable("gui.createcityexpansioncore.economy.infrastructure.station_missing"):Component.literal(state.stationName())),l+16,t+58,state.stationName().isBlank()?0xFFD09A3A:0xFF77B992,false);
        g.drawString(font,Component.translatable("gui.createcityexpansioncore.economy.infrastructure.attribution"),l+16,t+82,0xFFB7C0C3,false);
        g.drawString(font,Component.translatable("status.createcityexpansioncore.economy.intake_error."+state.lastError().name().toLowerCase()),l+16,t+98,state.lastError()==EconomyTypes.IntakeAttributionError.NONE?0xFF77B992:0xFFD07872,false);
        g.drawWordWrap(font,Component.translatable("tooltip.createcityexpansioncore.economy.contract_intake"),l+16,t+124,308,0xFF8F9A9E);
    }
    private void button(GuiGraphics g,int x,int y,int w,Component text,int mx,int my){
        // Both endpoints are the colours this control used before it lerped, so a settled button is
        // unchanged and only the travel is new.
        float lit=motion.hover(x*7919+y,inside(mx,my,x,y,w,22),frameStep);
        g.fill(x,y,x+w,y+22,UiMotion.mix(0xFF493A29,0xFF76562E,lit));
        g.drawCenteredString(font,text,x+w/2,y+7,0xFFFFD59A);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){InfrastructureStatePayload state=menu.state();if(state!=null&&button==0&&state.infrastructureType()==EconomyTypes.InfrastructureType.WAREHOUSE_GATEWAY&&inside(mouseX,mouseY,leftPos+16,topPos+90,308,22)){PacketDistributor.sendToServer(new InfrastructureCommandPayload(menu.pos(),InfrastructureCommandPayload.Action.TOGGLE_GATEWAY));return true;}return super.mouseClicked(mouseX,mouseY,button);}
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){}
}
