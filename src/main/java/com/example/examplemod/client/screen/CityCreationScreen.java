package com.example.examplemod.client.screen;

import com.example.examplemod.city.CityPlot;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.network.CreateCityPayload;
import com.example.examplemod.network.CityCreationResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A local draft: founding is only performed by the server after the labelled action. */
public class CityCreationScreen extends Screen {
    private final CityPylonScreen parent;
    private final BlockPos pylonPos;
    private final CityMapPanel map;
    private final CityPlot plot;
    private CityFoundingLayout layout;
    private EditBox cityName;
    private Button submit;
    private String draft = "", status = "ready";
    private boolean waiting, created;
    private int waitTicks;

    public CityCreationScreen(CityPylonScreen parent) {
        super(text("heading"));
        this.parent = parent;
        this.pylonPos = parent.getMenu().getPylonPos();
        this.map = parent.getMapPanel();
        this.plot = CityPlot.at(parent.getMenu().getDimensionId(), pylonPos);
        map.setDrawInternalControls(false);
        map.setGuiScale(1);
        map.setPlotClaimRequest(null);
        map.restoreView(pylonPos.getX()+.5,pylonPos.getZ()+.5,1);
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("gui.createcityexpansioncore.city.founding."+key,args);
    }
    @Override protected void init() {
        layout=CityFoundingLayout.compute(width,height);
        var field=layout.name();
        cityName=new EditBox(font,field.x(),field.y(),field.width(),field.height(),text("name"));
        cityName.setMaxLength(32); cityName.setHint(text("name_hint")); cityName.setValue(draft);
        cityName.setResponder(value -> {draft=value;if(!waiting&&!created)status="ready";});
        addRenderableWidget(cityName);
        var back=layout.back(); var action=layout.submit();
        addRenderableWidget(new FoundingButton(back,text("back"),false,b->onClose()));
        submit=addRenderableWidget(new FoundingButton(action,text("create"),true,b->submit()));
        cityName.setEditable(!waiting&&!created);
        setInitialFocus(cityName);
    }
    private void submit() {
        if(waiting||created||draft.trim().isEmpty())return;
        waiting=true;waitTicks=200;status="sending";cityName.setEditable(false);
        PacketDistributor.sendToServer(new CreateCityPayload(draft.trim(),pylonPos));
    }
    public static void accept(CityCreationResultPayload result) {
        if(Minecraft.getInstance().screen instanceof CityCreationScreen screen
                && screen.pylonPos.equals(result.pylon())&&screen.waiting) {
            screen.waiting=false;screen.status=result.result();screen.created=result.result().equals("created");
            screen.cityName.setEditable(!screen.created);
        }
    }
    @Override public void tick() {
        com.example.examplemod.client.CityTerritorySnapshot.applyPending(parent);
        parent.workspaceSyncMapData();
        if(waiting&&--waitTicks<=0){waiting=false;status="timeout";cityName.setEditable(true);}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){
        map.releaseTerrainTextures();map.setDrawInternalControls(true);
        if(minecraft.player!=null)minecraft.player.closeContainer();
        minecraft.setScreen(null);
    }
    @Override public void removed(){map.releaseTerrainTextures();}
    @Override public void render(GuiGraphics g,int mx,int my,float pt) {
        renderBackground(g,mx,my,pt);
        var p=layout.panel();var m=layout.map();
        CityPylonTheme.panel(g,p.x(),p.y(),p.width(),p.height());
        g.drawString(font,text("heading"),p.x()+14,p.y()+12,CityPylonTheme.TEXT,false);
        g.drawString(font,CityPylonTheme.fit(font,text("subtitle"),p.width()-28),p.x()+14,p.y()+29,CityPylonTheme.MUTED,false);
        g.fill(p.x()+14,p.y()+44,p.right()-14,p.y()+45,CityPylonTheme.LINE);
        map.render(g,m.x(),m.y(),m.width(),m.height(),mx,my);
        g.enableScissor(m.x(),m.y(),m.right(),m.bottom());
        int x1=map.worldToScreenX(plot.minX()),z1=map.worldToScreenY(plot.minZ());
        int x2=map.worldToScreenX(plot.maxX()+1),z2=map.worldToScreenY(plot.maxZ()+1);
        g.fill(x1,z1,x2,z2,0x287DAF9B);g.renderOutline(x1,z1,x2-x1,z2-z1,0xFF8BC5AE);
        g.disableScissor();g.renderOutline(m.x(),m.y(),m.width(),m.height(),CityPylonTheme.LINE);
        g.drawString(font,CityPylonTheme.fit(font,text("preview"),m.width()),m.x(),m.bottom()+6,CityPylonTheme.MUTED,false);
        int x=layout.formX(),y=layout.name().y(),w=layout.formWidth();
        g.drawString(font,text("name"),x,y-12,CityPylonTheme.BRASS_TEXT,false);
        line(g,text("territory",CityPlot.PLOT_SIZE,CityPlot.PLOT_SIZE),x,y+30,w);
        line(g,text("center",pylonPos.getX(),pylonPos.getZ()),x,y+44,w);
        if(m.height()>110) {
            line(g,text("founder",minecraft.player==null?"":minecraft.player.getName().getString()),x,y+60,w);
            g.drawWordWrap(font,text("colour_later"),x,y+80,w,CityPylonTheme.MUTED);
        }
        g.fill(p.x()+14,p.bottom()-51,p.right()-14,p.bottom()-50,CityPylonTheme.LINE);
        g.drawString(font,CityPylonTheme.fit(font,text(status),p.width()-28),p.x()+14,p.bottom()-46,
                created?CityPylonTheme.GREEN:status.equals("ready")||waiting?CityPylonTheme.MUTED:0xFFDB9A87,false);
        submit.active=!waiting&&!created&&!draft.trim().isEmpty();
        submit.setMessage(text(waiting?"sending":"create"));
        super.render(g,mx,my,pt);
    }
    private void line(GuiGraphics g,Component text,int x,int y,int w){g.drawString(font,CityPylonTheme.fit(font,text,w),x,y,CityPylonTheme.TEXT,false);}
    @Override public boolean mouseClicked(double x,double y,int button){
        return layout.map().contains(x,y)?button==0&&map.mouseClicked(x,y,button):super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return map.mouseDragged(x,y,button,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){return map.mouseReleased(x,y,button)||super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){return layout.map().contains(x,y)&&map.mouseScrolled(x,y,sy)||super.mouseScrolled(x,y,sx,sy);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER&&!waiting&&!created){submit();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    private static final class FoundingButton extends Button {
        private final boolean primary;
        /** Smoothed hover. Keyed by position: a fixed control never moves, so position is stable. */
        private final UiMotion motion = new UiMotion();
        FoundingButton(WorldMapUiLayout.Rect r,Component label,boolean primary,OnPress press){super(r.x(),r.y(),r.width(),r.height(),label,press,DEFAULT_NARRATION);this.primary=primary;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float pt){
            float lit=motion.hover(getX()*7919+getY(),isHoveredOrFocused(),motion.delta(net.minecraft.Util.getMillis()));
            CityPylonTheme.button(g,Minecraft.getInstance().font,getX(),getY(),getWidth(),getHeight(),getMessage(),primary&&active,lit);
            if(!active)g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0x55222C30);
        }
    }
}
