package com.example.examplemod.client.screen;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.uiedit.UiEditLayout;
import com.example.examplemod.client.uiedit.UiEditModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private final CityPylonNewspaper newspaper = new CityPylonNewspaper();
    private final Map<Screen,int[]> initializedPages=new WeakHashMap<>();
    private Screen page;

    private CityPylonWorkspaceLayout geometry;
    private int section;
    private boolean pointerCaptured;
    private static final String[] SECTIONS={"overview","management","map"};
    private static final Item[] ICONS={Items.COMPASS,Items.WRITABLE_BOOK,Items.FILLED_MAP};

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
            boolean fromMap=event.getCurrentScreen() instanceof PolishedWorldMapScreen;
            if(fresh)session.show(session.overview);
            else if(!fromMap||session.root.workspaceSection()==1&&session.section!=1)
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
                ||screen instanceof CityColorPaletteScreen||screen instanceof CityRemoveMemberConfirmScreen;
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
    static <R> R pageControl(Screen page, String name, int x, int y, int w, int h,
            UiEditLayout.BoxFactory<R> factory) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof CityPylonWorkspace workspace
                && workspace.page == page && workspace.geometry != null) {
            var layout = workspace.geometry;
            String key = "page." + page.getClass().getSimpleName() + "." + name;
            UiEditLayout.setElementViewport(key, new UiEditModel.Bounds(
                    layout.x(), layout.y(), layout.width(), layout.height()));
            return UiEditLayout.apply(key, x, y, w, h, layout.scale(), layout.originX(), layout.originY(), factory);
        }
        if (current == page && !(page instanceof CityPylonScreen)) {
            return UiEditLayout.apply(name, x, y, w, h, factory);
        }
        return factory.create(x, y, w, h);
    }

    private WorldMapUiLayout.Rect backControl() {
        return UiEditLayout.apply("back", geometry.frameX()+7, geometry.frameY()+geometry.frameHeight()-26,
                geometry.sidebarWidth()-14, 22, WorldMapUiLayout.Rect::new);
    }

    private WorldMapUiLayout.Rect centerBack() {
        return UiEditLayout.apply("centerBack",geometry.frameX()+7,geometry.frameY()+3,26,24,WorldMapUiLayout.Rect::new);
    }

    private WorldMapUiLayout.Rect collectControl() {
        return UiEditLayout.apply("collect", geometry.frameX()+7, geometry.frameY()+geometry.frameHeight()-52,
                geometry.sidebarWidth()-14, 20, WorldMapUiLayout.Rect::new);
    }

    private final java.util.Map<AbstractWidget, WorldMapUiLayout.Rect> pageWidgetBounds = new java.util.WeakHashMap<>();

    private void applyPageWidgetGeometry() {
        // The controller paints only its invite widget in the workspace; its other
        // controls are part of the manually drawn management body.
        if (page == root) return;
        var listeners = page.children();
        for (int index = 0; index < listeners.size(); index++) {
            if (!(listeners.get(index) instanceof AbstractWidget widget) || !widget.visible) continue;
            var original = pageWidgetBounds.computeIfAbsent(widget, w ->
                    new WorldMapUiLayout.Rect(w.getX(), w.getY(), w.getWidth(), w.getHeight()));
            var edited = pageControl(page, "widget[" + index + ":" + widget.getClass().getSimpleName() + "]",
                    original.x(), original.y(), original.width(), original.height(), WorldMapUiLayout.Rect::new);
            widget.setX(edited.x());
            widget.setY(edited.y());
            widget.setWidth(edited.width());
            widget.setHeight(edited.height());
        }
    }

    private void prepare(Screen target,int w,int h) {
        int[] old=initializedPages.get(target);
        if(old==null){target.init(minecraft,w,h);initializedPages.put(target,new int[]{w,h});}
        else if(old[0]!=w||old[1]!=h){target.resize(minecraft,w,h);initializedPages.put(target,new int[]{w,h});}
    }
    private void layout() {
        int inset = page == overview ? newspaper.rightInset(width) : 0;
        geometry=CityPylonWorkspaceLayout.calculate(width,height,page==root,page==overview||page==root,inset);
        if(page!=root)prepare(page,geometry.virtualWidth(),geometry.virtualHeight());
    }
    private void show(Screen next) {
        pointerCaptured=false;
        if(next!=overview)newspaper.reset();
        if(next==root)section=1;
        else if(next==overview)section=0;
        if(page!=next)page.setFocused(null);
        page=next;if(minecraft!=null)layout();
    }
    private void navigate(int index){
        if(index==2){minecraft.setScreen(new PolishedWorldMapScreen(root));return;}
        if(index==0){root.workspaceSelect(0);show(overview);}
        else if(index==1){root.workspaceSelect(2);show(root);}
    }
    @Override public void tick() {
        if(!valid()){minecraft.setScreen(null);return;}
        if(page==root||page==overview)root.tick();
        if(page!=root)page.tick();
    }
    private final SocialHubIcons.HoverMotion chromeMotion = new SocialHubIcons.HoverMotion();

    @Override public void render(GuiGraphics g,int mx,int my,float pt) {
        if (page == overview && newspaper.advance(width)) layout();
        int fx=geometry.frameX(),fy=geometry.frameY(),fw=geometry.frameWidth(),fh=geometry.frameHeight();
        int sidebar=geometry.sidebarWidth();
        // The world remains visible outside this bounded frame.
        g.fill(0,0,width,height,0x68000000);
        rounded(g,fx,fy,fw,fh,6,LINE);rounded(g,fx+1,fy+1,fw-2,fh-2,5,BG);
        rounded(g,fx+1,fy+1,fw-2,29,5,METAL);
        g.fill(fx+1,fy+25,fx+fw-1,fy+30,METAL);
        g.fill(fx+1,fy+29,fx+fw-1,fy+30,LINE);
        chromeMotion.beginFrame();
        UiEditLayout.clearElements("centerBack");
        var close = geometry.closeRect();
        SocialHubIcons.paintButton(g,font,close,Component.empty(),SocialHubIcons.Icon.CLOSE,chromeMotion,
                close.contains(mx,my),true,false,false);
        if (page == overview || page == root) {
            UiEditLayout.clearElements("nav[");
            UiEditLayout.clearElements("profileEntry");
            int cityWidth = Math.max(1, fw / 3 - 20);
            if (page == root) {
                var back = centerBack();
                SocialHubIcons.paintButton(g,font,back,Component.empty(),SocialHubIcons.Icon.CHAT_BACK,chromeMotion,
                        back.contains(mx,my),true,false,false);
            } else {
                SocialHubIcons.Icon.CITY.draw(g,fx+11,fy+7,false,0);
                g.drawString(font,fit(font,Component.literal(root.getMenu().getCityName()),cityWidth),
                        fx+31,fy+11,MUTED,false);
            }
            String key = page==overview ? "title" : root.workspaceProjects() ? "structures" : "management";
            var title = Component.translatable("gui.createcityexpansioncore.workspace.center."+key);
            g.drawCenteredString(font,fit(font,title,Math.max(1,fw/3)),fx+fw/2,fy+11,BRASS_TEXT);
        } else {
            g.drawString(font,Component.literal("CITY PYLON"),fx+13,fy+11,BRASS_TEXT,false);
            if(fw>=600)g.drawString(font,Component.translatable("gui.createcityexpansioncore.workspace.terminal"),fx+105,fy+11,MUTED,false);
            var profileEntry=PlayerProfileLayout.workspaceEntry(fx,fy,fw);
            button(g,font,profileEntry.x(),profileEntry.y(),profileEntry.width(),profileEntry.height(),
                    Component.translatable("gui.createcityexpansioncore.profile.my_profile"),false,profileEntry.contains(mx,my));
            g.fill(fx+1,fy+30,fx+sidebar,fy+fh-1,METAL);
            g.fill(fx+sidebar,fy+30,fx+sidebar+1,fy+fh-1,LINE);
            int cityY=fy+(fh<240?36:44);
            if(sidebar>=60)g.drawString(font,fit(font,Component.literal(root.getMenu().getCityName()),sidebar-26),fx+13,cityY,TEXT,false);
            if(fh>=240)g.drawString(font,Component.translatable("gui.createcityexpansioncore.workspace.level",root.getMenu().getCityLevel()),fx+13,cityY+17,MUTED,false);
            for(int i=0;i<SECTIONS.length;i++) {
                var nav=geometry.navRect(i);boolean selected=section==i;
                int x=nav.x(),y=nav.y(),w=nav.width(),h=nav.height();
                boolean hover=in(mx,my,x,y,w,h);
                rounded(g,x,y,w,h,4,selected?0xFF383123:hover?PANEL:METAL);
                if(selected)g.fill(x,y+4,x+2,y+h-4,BRASS);
                if(h>=18||sidebar<60)g.renderItem(new ItemStack(ICONS[i]),x+7,y+(h-16)/2);
                int tx=x+(h>=20?29:7);
                if(sidebar>=60)g.drawString(font,fit(font,navLabel(i),w-(tx-x)-5),tx,y+(h-8)/2,selected?TEXT:MUTED,false);
            }
        }
        UiEditLayout.clearElements("page.");
        UiEditLayout.clearElements("back");
        UiEditLayout.clearElements("collect");
        if (page != overview && page != root) {
            var back = backControl();
            if (sidebar >= 60) g.drawString(font,fit(font,Component.translatable("gui.createcityexpansioncore.common.back"),back.width()-6),
                    back.x()+6,back.y()+6,MUTED,false);
        }
        if(page!=overview&&page!=root)g.drawString(font,fit(font,navLabel(section),fw-sidebar-30),fx+sidebar+13,fy+fh-15,MUTED,false);
        g.enableScissor(geometry.x(),geometry.y(),geometry.x()+geometry.width(),geometry.y()+geometry.height());
        g.pose().pushPose();g.pose().translate(geometry.originX(),geometry.originY(),0);g.pose().scale(geometry.scale(),geometry.scale(),1);
        rendering=true;
        try {
            int px=geometry.contains(mx,my)?(int)geometry.localX(mx):-10000,py=geometry.contains(mx,my)?(int)geometry.localY(my):-10000;
            if(page==root)root.renderWorkspaceContent(g,px,py,pt);
            else {
                applyPageWidgetGeometry();
                page.render(g,px,py,pt);
            }
            g.flush();
        }finally{rendering=false;g.pose().popPose();g.disableScissor();}
        UiEditLayout.clearElements("newspaper.");
        if (page == overview) newspaper.render(g,font,geometry,width,mx,my);
        if (canCollect()) {
            var collect = collectControl();
            button(g,font,collect.x(),collect.y(),collect.width(),collect.height(),
                    Component.translatable("gui.createcityexpansioncore.pylon.collect"),false,
                    collect.contains(mx,my));
        }
    }
    private boolean canCollect(){return section==3&&minecraft.player!=null
            &&minecraft.player.getUUID().equals(root.getMenu().getOwner());}
    private Component navLabel(int index) {return Component.translatable("gui.createcityexpansioncore.city.tab."+SECTIONS[index]);}
    private static boolean in(double x,double y,int left,int top,int w,int h) {return x>=left&&x<left+w&&y>=top&&y<top+h;}
    @Override public boolean mouseClicked(double x,double y,int button) {
        int fx=geometry.frameX(),fy=geometry.frameY();
        if(button==0) {
            if(canCollect() && collectControl().contains(x,y)) {
                minecraft.gameMode.handleInventoryButtonClick(root.getMenu().containerId,
                        com.example.examplemod.menu.CityPylonMenu.RETRIEVE_PYLON);
                return true;
            }
            var close=geometry.closeRect();
            if(in(x,y,close.x(),close.y(),close.width(),close.height())){root.onClose();return true;}
            if(page==overview&&newspaper.mouseClicked(geometry,width,x,y))return true;
            if(page==root&&centerBack().contains(x,y)){onClose();return true;}
            if(page!=overview&&page!=root&&PlayerProfileLayout.workspaceEntry(fx,fy,geometry.frameWidth()).contains(x,y)) {
                PlayerProfileScreen.open(this,minecraft.player.getUUID());return true;
            }
            if(page!=overview&&page!=root)for(int i=0;i<SECTIONS.length;i++){var nav=geometry.navRect(i);if(in(x,y,nav.x(),nav.y(),nav.width(),nav.height())){navigate(i);return true;}}
            if(page!=overview&&page!=root&&backControl().contains(x,y)){onClose();return true;}
        }
        applyPageWidgetGeometry();
        pointerCaptured=geometry.contains(x,y)&&page.mouseClicked(geometry.localX(x),geometry.localY(y),button);return pointerCaptured;
    }
    @Override public boolean mouseReleased(double x,double y,int b) {boolean result=pointerCaptured&&page.mouseReleased(geometry.localX(x),geometry.localY(y),b);pointerCaptured=false;return result;}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy) {return pointerCaptured&&page.mouseDragged(geometry.localX(x),geometry.localY(y),b,dx/geometry.scale(),dy/geometry.scale());}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {return geometry.contains(x,y)&&page.mouseScrolled(geometry.localX(x),geometry.localY(y),dx,dy);}
    @Override public void mouseMoved(double x,double y) {page.mouseMoved(geometry.localX(x),geometry.localY(y));}
    @Override public boolean charTyped(char c,int modifiers) {return page.charTyped(c,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {if(key==256){onClose();return true;}return page.keyPressed(key,scan,modifiers);}
    @Override public boolean keyReleased(int key,int scan,int modifiers) {return page.keyReleased(key,scan,modifiers);}
    @Override public void onClose() {
        if(page==overview){if(!newspaper.close())overview.onClose();}
        else if(page==root){root.workspaceSelect(0);show(overview);}else page.onClose();
    }
    @Override public void removed() {if(!valid())root.removed();}
    @Override public boolean isPauseScreen() {return false;}
}
