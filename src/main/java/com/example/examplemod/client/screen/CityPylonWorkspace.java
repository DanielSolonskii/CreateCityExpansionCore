package com.example.examplemod.client.screen;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;
import static com.example.examplemod.client.screen.CityPylonTheme.*;

/** Persistent navigation around independent content pages, never a cropped container screen. */
@EventBusSubscriber(modid=CreateCityExpansionCore.MODID,value=Dist.CLIENT)
public final class CityPylonWorkspace extends Screen {
    private static CityPylonWorkspace session;
    private static boolean rendering;
    private final CityPylonScreen root;
    private final CityPylonOverviewScreen overview;
    private final Map<Screen,int[]> initializedPages=new WeakHashMap<>();
    private Screen page;
    private MarketScreen market;
    private LogisticsScreen logistics;
    private CityPylonWorkspaceLayout geometry;
    private int section;
    private boolean pointerCaptured;
    private static final String[] SECTIONS={"overview","marketplace","logistics","management","map"};
    private static final Item[] ICONS={Items.COMPASS,Items.EMERALD,Items.MINECART,Items.WRITABLE_BOOK,Items.FILLED_MAP};

    private CityPylonWorkspace(CityPylonScreen root) {
        super(Component.translatable("gui.createcityexpansioncore.city.pylon"));
        this.root=root;this.overview=new CityPylonOverviewScreen(root);this.page=overview;
    }
    private boolean valid() {var p=Minecraft.getInstance().player;return p!=null&&p.containerMenu==root.getMenu();}

    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        Screen next=event.getNewScreen();
        if(next instanceof CityPylonWorkspace)return;
        if(session!=null&&!session.valid())session=null;
        if(next instanceof CityPylonScreen pylon&&pylon.getMenu().hasCity()) {
            boolean fresh=session==null||session.root.getMenu()!=pylon.getMenu();
            if(fresh)session=new CityPylonWorkspace(pylon);
            boolean fromMap=event.getCurrentScreen() instanceof PolishedWorldMapScreen||event.getCurrentScreen() instanceof WorldMapScreen;
            if(fresh)session.show(session.overview);
            else if(!fromMap||session.root.workspaceSection()==3&&session.section!=3)
                session.show(session.root.workspaceSection()==0?session.overview:session.root);
            if(event.getCurrentScreen()==session)event.setCanceled(true);else event.setNewScreen(session);
        }else if(session!=null&&session.valid()&&isPage(next)
                &&(event.getCurrentScreen()==session||event.getCurrentScreen() instanceof PolishedWorldMapScreen)) {
            session.show(next);
            if(event.getCurrentScreen()==session)event.setCanceled(true);else event.setNewScreen(session);
        }else if(next==null)session=null;
    }
    private static boolean isPage(Screen screen) {
        return screen instanceof CityPylonPageScreen||screen instanceof CityRoleScreen
                ||screen instanceof CityColorPaletteScreen||screen instanceof CityRemoveMemberConfirmScreen
                ||screen instanceof RailwayLineEditorScreen;
    }
    public static boolean isEmbedded(Screen screen) {
        return session!=null&&session.valid()&&(session.page==screen||session.root==screen)&&Minecraft.getInstance().screen==session;
    }
    public static CityPylonScreen controller() {
        Screen screen=Minecraft.getInstance().screen;
        if(screen instanceof CityPylonWorkspace workspace&&workspace.valid())return workspace.root;
        return screen instanceof CityPylonScreen pylon?pylon:null;
    }
    public static void clip(GuiGraphics g,int x1,int y1,int x2,int y2) {
        if(rendering) {
            var matrix=g.pose().last().pose();
            Vector3f a=matrix.transformPosition(new Vector3f(x1,y1,0)),b=matrix.transformPosition(new Vector3f(x2,y2,0));
            g.enableScissor((int)Math.floor(a.x),(int)Math.floor(a.y),(int)Math.ceil(b.x),(int)Math.ceil(b.y));
        }else g.enableScissor(x1,y1,x2,y2);
    }
    @Override protected void init() {prepare(root,632,357);root.prepareWorkspaceContent();layout();}
    private void prepare(Screen target,int w,int h) {
        int[] old=initializedPages.get(target);
        if(old==null){target.init(minecraft,w,h);initializedPages.put(target,new int[]{w,h});}
        else if(old[0]!=w||old[1]!=h){target.resize(minecraft,w,h);initializedPages.put(target,new int[]{w,h});}
    }
    private void layout() {
        geometry=CityPylonWorkspaceLayout.calculate(width,height,page==root);
        if(page!=root)prepare(page,geometry.virtualWidth(),geometry.virtualHeight());
    }
    private void show(Screen next) {
        pointerCaptured=false;
        if(next instanceof MarketScreen){if(market==null)market=(MarketScreen)next;next=market;section=1;}
        else if(next instanceof LogisticsScreen){logistics=(LogisticsScreen)next;section=2;}
        else if(next==root)section=3;
        else if(next==overview)section=0;
        if(page!=next)page.setFocused(null);
        page=next;if(minecraft!=null)layout();
        // The tabs are cached, so a page object outlives its visit. Without an explicit restart the
        // entrance would play once per session and every later visit would show it already settled.
        if(next instanceof CityPylonPageScreen form)form.replayEntrance();
    }
    private void navigate(int index) {
        if(index==4){minecraft.setScreen(new PolishedWorldMapScreen(root));return;}
        switch(index) {
            case 0->{root.workspaceSelect(0);show(overview);}
            case 1->show(market==null?new MarketScreen(root):market);
            case 2->show(logistics==null?new LogisticsScreen(root):logistics);
            case 3->{root.workspaceSelect(2);show(root);}
            default->{}
        }
    }
    @Override public void tick() {
        if(!valid()){minecraft.setScreen(null);return;}
        if(page==root||page==overview)root.tick();
        if(page!=root)page.tick();
    }
    /** Smoothed hover for the nav rail, the profile entry and the close control. */
    private final UiMotion motion=new UiMotion();
    /** One lerp step per frame, measured once in render -- never per control. */
    private float frameDelta;
    @Override public void render(GuiGraphics g,int mx,int my,float pt) {
        this.frameDelta=motion.delta(net.minecraft.Util.getMillis());
        int fx=geometry.frameX(),fy=geometry.frameY(),fw=geometry.frameWidth(),fh=geometry.frameHeight();
        int sidebar=geometry.sidebarWidth();
        // The world remains visible outside this bounded frame.
        g.fill(0,0,width,height,0x68000000);
        rounded(g,fx,fy,fw,fh,6,LINE);rounded(g,fx+1,fy+1,fw-2,fh-2,5,BG);
        rounded(g,fx+1,fy+1,fw-2,29,5,METAL);
        g.fill(fx+1,fy+25,fx+fw-1,fy+30,METAL);
        g.fill(fx+1,fy+29,fx+fw-1,fy+30,LINE);
        g.drawString(font,Component.literal("CITY PYLON"),fx+13,fy+11,BRASS_TEXT,false);
        if(fw>=600)g.drawString(font,Component.translatable("gui.createcityexpansioncore.workspace.terminal"),fx+105,fy+11,MUTED,false);
        var profileEntry=PlayerProfileLayout.workspaceEntry(fx,fy,fw);
        // Keyed by position: both controls are fixed, so their coordinates are a stable identity.
        button(g,font,profileEntry.x(),profileEntry.y(),profileEntry.width(),profileEntry.height(),
                Component.translatable("gui.createcityexpansioncore.profile.my_profile"),false,
                motion.hover(profileEntry.x()*7919+profileEntry.y(),profileEntry.contains(mx,my),frameDelta));
        // The close control had no hover feedback at all -- a bare glyph with a hit test behind it.
        float closeLit=motion.hover((fx+fw-29)*7919+fy+3,in(mx,my,fx+fw-29,fy+3,26,24),frameDelta);
        if(closeLit>0.02f)rounded(g,fx+fw-25,fy+6,18,18,4,UiMotion.mix(METAL,0xFF4A342E,closeLit));
        g.drawString(font,Component.literal("×"),fx+fw-19,fy+10,TEXT,false);
        g.fill(fx+1,fy+30,fx+sidebar,fy+fh-1,METAL);
        g.fill(fx+sidebar,fy+30,fx+sidebar+1,fy+fh-1,LINE);
        int cityY=fy+(fh<240?36:44);
        g.drawString(font,fit(font,Component.literal(root.getMenu().getCityName()),sidebar-26),fx+13,cityY,TEXT,false);
        if(fh>=240)g.drawString(font,Component.translatable("gui.createcityexpansioncore.workspace.level",root.getMenu().getCityLevel()),fx+13,cityY+17,MUTED,false);
        for(int i=0;i<5;i++) {
            int y=geometry.navY(i),h=geometry.navHeight();boolean selected=section==i;
            // Keyed by the rail index, which never moves: the highlight follows the cursor across
            // the rail instead of snapping from one entry to the next.
            float lit=motion.hover(i,in(mx,my,fx+7,y,sidebar-14,h),frameDelta);
            rounded(g,fx+7,y,sidebar-14,h,4,selected?0xFF383123:UiMotion.mix(METAL,PANEL,lit));
            if(selected)g.fill(fx+7,y+4,fx+9,y+h-4,BRASS);
            if(h>=20)g.renderItem(new ItemStack(ICONS[i]),fx+14,y+(h-16)/2);
            int tx=fx+(h>=20?36:14);
            g.drawString(font,fit(font,navLabel(i),sidebar-(tx-fx)-12),tx,y+(h-8)/2,selected?TEXT:MUTED,false);
        }
        if(page!=overview)g.drawString(font,Component.translatable("gui.createcityexpansioncore.common.back"),fx+13,fy+fh-20,MUTED,false);
        g.drawString(font,fit(font,navLabel(section),fw-sidebar-30),fx+sidebar+13,fy+fh-15,MUTED,false);
        g.enableScissor(geometry.x(),geometry.y(),geometry.x()+geometry.width(),geometry.y()+geometry.height());
        g.pose().pushPose();g.pose().translate(geometry.originX(),geometry.originY(),0);g.pose().scale(geometry.scale(),geometry.scale(),1);
        rendering=true;
        try {
            int px=geometry.contains(mx,my)?(int)geometry.localX(mx):-10000,py=geometry.contains(mx,my)?(int)geometry.localY(my):-10000;
            if(page==root)root.renderWorkspaceContent(g,px,py,pt);
            else page.render(g,px,py,pt);
            g.flush();
        }finally{rendering=false;g.pose().popPose();g.disableScissor();}
    }
    private Component navLabel(int index) {return Component.translatable(index==2?"gui.createcityexpansioncore.freight.title":"gui.createcityexpansioncore.city.tab."+SECTIONS[index]);}
    private static boolean in(double x,double y,int left,int top,int w,int h) {return x>=left&&x<left+w&&y>=top&&y<top+h;}
    @Override public boolean mouseClicked(double x,double y,int button) {
        int fx=geometry.frameX(),fy=geometry.frameY();
        if(button==0) {
            if(in(x,y,fx+geometry.frameWidth()-29,fy+3,26,24)){root.onClose();return true;}
            if(PlayerProfileLayout.workspaceEntry(fx,fy,geometry.frameWidth()).contains(x,y)) {
                PlayerProfileScreen.open(this,minecraft.player.getUUID());return true;
            }
            if(x>=fx+7&&x<fx+geometry.sidebarWidth()-7) {
                for(int i=0;i<5;i++)if(in(x,y,fx+7,geometry.navY(i),geometry.sidebarWidth()-14,geometry.navHeight())){navigate(i);return true;}
                if(y>=fy+geometry.frameHeight()-26&&y<fy+geometry.frameHeight()-4&&page!=overview){onClose();return true;}
            }
        }
        pointerCaptured=geometry.contains(x,y)&&page.mouseClicked(geometry.localX(x),geometry.localY(y),button);return pointerCaptured;
    }
    @Override public boolean mouseReleased(double x,double y,int b) {boolean result=pointerCaptured&&page.mouseReleased(geometry.localX(x),geometry.localY(y),b);pointerCaptured=false;return result;}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy) {return pointerCaptured&&page.mouseDragged(geometry.localX(x),geometry.localY(y),b,dx/geometry.scale(),dy/geometry.scale());}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {return geometry.contains(x,y)&&page.mouseScrolled(geometry.localX(x),geometry.localY(y),dx,dy);}
    @Override public void mouseMoved(double x,double y) {page.mouseMoved(geometry.localX(x),geometry.localY(y));}
    @Override public boolean charTyped(char c,int modifiers) {return page.charTyped(c,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {if(key==256){onClose();return true;}return page.keyPressed(key,scan,modifiers);}
    @Override public boolean keyReleased(int key,int scan,int modifiers) {return page.keyReleased(key,scan,modifiers);}
    @Override public void onClose() {if(page==overview)overview.onClose();else if(page==root){root.workspaceSelect(0);show(overview);}else page.onClose();}
    @Override public void removed() {if(!valid())root.removed();}
    @Override public boolean isPauseScreen() {return false;}
}
