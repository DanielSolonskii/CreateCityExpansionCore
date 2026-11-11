package com.example.examplemod.client.screen;

import com.example.examplemod.city.CityPlot;
import com.example.examplemod.menu.CityPylonMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

import static com.example.examplemod.client.screen.CityPylonTheme.*;

/** Landing page for the city pylon. The atlas and management screens keep their existing state. */
public final class CityPylonOverviewScreen extends CityPylonPageScreen {
    private static final ResourceLocation LOCK = ResourceLocation.fromNamespaceAndPath(
            "createcityexpansioncore", "textures/gui/community/center_lock.png");
    private static final int BLUE = 0xFF84C3CC;
    private static final int COPPER = 0xFFBC8E69;
    private static final String[] TASKS = {"resources", "build", "expand"};

    private final CityPylonScreen root;
    private final CityPylonMenu menu;
    private final SocialHubIcons.HoverMotion motion = new SocialHubIcons.HoverMotion();
    private Set<CityPlot> countedPlots = Set.of();
    private int claimedChunks;
    private boolean centered;

    public CityPylonOverviewScreen(CityPylonScreen root) {
        super(tr("title"));
        this.root = root;
        this.menu = root.getMenu();
    }

    private static Component tr(String key) {
        return Component.translatable("gui.createcityexpansioncore.workspace.center." + key);
    }

    private WorldMapUiLayout.Rect tile(String key, int column, boolean lower) {
        int usable = width - 2 * GAP;
        int columnWidth = lower ? usable * 36 / 100 : usable / 3;
        int topHeight = (height - GAP) * 43 / 100;
        int x = column * (columnWidth + GAP);
        int w = column < 2 ? columnWidth : width - x;
        return CityPylonWorkspace.pageControl(this, "center." + key, x, lower ? topHeight + GAP : 0,
                w, lower ? height - topHeight - GAP : topHeight, WorldMapUiLayout.Rect::new);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        motion.beginFrame();
        root.workspaceSyncMapData();
        var management = tile("management",0,false);
        var structures = tile("structures",1,false);
        var community = tile("community",2,false);
        drawShortcut(g,management,"management",SocialHubIcons.Icon.CITY,BRASS_TEXT,mx,my);
        drawShortcut(g,structures,"structures",SocialHubIcons.Icon.PROJECTS,COPPER,mx,my);
        drawShortcut(g,community,"community",SocialHubIcons.Icon.FRIENDS,GREEN,mx,my);
        quickButton(g,quick(management,"territory",1),tr("quick.territory"),SocialHubIcons.Icon.CITY,mx,my);
        quickButton(g,quick(management,"members",0),tr("quick.members"),SocialHubIcons.Icon.FRIENDS,mx,my);
        drawCapacity(g,structures);
        quickButton(g,quick(structures,"projects",0),tr("quick.projects"),SocialHubIcons.Icon.PROJECTS,mx,my);
        quickButton(g,quick(community,"chats",1),tr("quick.chats"),SocialHubIcons.Icon.MESSAGES,mx,my);
        quickButton(g,quick(community,"relations",0),tr("quick.relations"),SocialHubIcons.Icon.ALLIANCE,mx,my);
        drawStatistics(g, tile("statistics",0,true));
        drawDevelopment(g, tile("development",1,true));
        drawMinimap(g, tile("map",2,true), mx, my);
    }

    private WorldMapUiLayout.Rect shortcutHeader(WorldMapUiLayout.Rect rect) {
        return new WorldMapUiLayout.Rect(rect.x()+8,rect.y()+8,Math.max(1,rect.width()-16),
                Math.max(1,rect.height()-79));
    }

    private WorldMapUiLayout.Rect quick(WorldMapUiLayout.Rect tile, String key, int fromBottom) {
        return CityPylonWorkspace.pageControl(this,"center.quick."+key,tile.x()+9,
                tile.bottom()-31-fromBottom*27,Math.max(1,tile.width()-18),23,WorldMapUiLayout.Rect::new);
    }

    private void quickButton(GuiGraphics g, WorldMapUiLayout.Rect bounds, Component label, SocialHubIcons.Icon icon,
            int mx, int my) {
        SocialHubIcons.paintButton(g,font,bounds,label,icon,motion,bounds.contains(mx,my),true,false,false);
    }

    private void drawCapacity(GuiGraphics g, WorldMapUiLayout.Rect tile) {
        int count = menu.getProjects().size();
        int limit = menu.getProjectCapacity();
        Component capacity = Component.literal(count + " / " + limit);
        int left = tile.x()+12;
        int right = tile.right()-12;
        int y = tile.bottom()-59;
        int valueWidth = font.width(capacity);
        line(g,tr("project_capacity"),left,y,right-left-valueWidth-6,MUTED);
        g.drawString(font,capacity,right-valueWidth,y,COPPER,false);
        if (right > left) {
            g.fill(left,y+14,right,y+18,DEEP);
            int filled = (int) ((right-left) * Math.min(1.0,count/(double)Math.max(1,limit)));
            if (filled > 0) g.fill(left,y+14,left+filled,y+18,COPPER);
        }
    }

    private void drawShortcut(GuiGraphics g, WorldMapUiLayout.Rect rect, String key, SocialHubIcons.Icon icon,
            int accent, int mx, int my) {
        drawCard(g,rect,accent,false);
        var header = shortcutHeader(rect);
        boolean hovered = header.contains(mx,my);
        float hover = motion.amount(header,hovered);
        g.pose().pushPose();
        try {
            SocialHubIcons.scaleButton(g,header,hover);
            rounded(g,header.x(),header.y(),header.width(),header.height(),4,hovered?0xFF303D41:METAL);
            int y = header.y()+Math.max(6,(header.height()-48)/2);
            icon.draw(g,header.x()+8,y,hovered,hover);
            line(g,tr(key),header.x()+8,y+21,header.width()-16,TEXT);
            line(g,tr(key+".hint"),header.x()+8,y+34,header.width()-16,MUTED);
        } finally {
            g.pose().popPose();
        }
    }

    private void drawStatistics(GuiGraphics g, WorldMapUiLayout.Rect rect) {
        drawCard(g,rect,BLUE,false);
        heading(g,rect,"statistics",SocialHubIcons.Icon.STATISTICS);
        Set<CityPlot> plots = root.ownTerritoryPlots();
        if (!countedPlots.equals(plots)) {
            countedPlots = plots;
            claimedChunks = countChunks(plots);
        }
        Component chunks = plots.size() < menu.getPlotCount()
                ? Component.literal("…") : Component.translatable(
                        "gui.createcityexpansioncore.workspace.center.chunk_count",claimedChunks);
        statistic(g,rect,39,tr("players"),Component.literal(Integer.toString(menu.getMemberCount())));
        statistic(g,rect,64,tr("level"),Component.literal(Integer.toString(menu.getCityLevel())));
        statistic(g,rect,89,tr("territory"),chunks);
    }

    static int countChunks(Set<CityPlot> plots) {
        var chunks = new HashSet<Long>();
        for (var plot : plots) {
            for (int x = plot.minChunkX(); x <= plot.maxChunkX(); x++) {
                for (int z = plot.minChunkZ(); z <= plot.maxChunkZ(); z++) {
                    chunks.add(ChunkPos.asLong(x,z));
                }
            }
        }
        return chunks.size();
    }

    private void statistic(GuiGraphics g, WorldMapUiLayout.Rect rect, int offset, Component name, Component value) {
        int x = rect.x()+12;
        int y = rect.y()+offset;
        int right = rect.right()-10;
        int valueWidth = font.width(value);
        g.hLine(x,right,y-5,LINE);
        line(g,name,x,y,right-x-valueWidth-6,MUTED);
        g.drawString(font,value,right-valueWidth,y,TEXT,false);
    }

    private void drawDevelopment(GuiGraphics g, WorldMapUiLayout.Rect rect) {
        drawCard(g,rect,BRASS_TEXT,false);
        heading(g,rect,"development",SocialHubIcons.Icon.CHECK);
        int x = rect.x()+11;
        int y = rect.y()+35;
        int width = Math.max(1,rect.width()-22);
        for (String task : TASKS) {
            rounded(g,x,y,width,20,3,DEEP);
            g.renderOutline(x+5,y+6,7,7,LINE);
            line(g,tr("task."+task),x+17,y+6,width-20,0xFF718083);
            y += 25;
        }
        g.fill(x,rect.y()+34,x+width,y-3,0x70151D21);
        Component status = tr("in_progress");
        int labelWidth = Math.min(font.width(status),Math.max(1,width-28));
        int start = x+(width-labelWidth-20)/2;
        int middle = rect.y()+60;
        g.blit(LOCK,start,middle,0,0,16,16,16,16);
        line(g,status,start+20,middle+4,labelWidth,BRASS_TEXT);
    }

    private void drawMinimap(GuiGraphics g, WorldMapUiLayout.Rect rect, int mx, int my) {
        boolean hovered = rect.contains(mx,my);
        float hover = motion.amount(rect,hovered);
        var map = root.getMapPanel();
        map.setDrawInternalControls(false);
        map.setGuiScale(1);
        if (!centered) {
            map.centerOn(menu.getPylonPos());
            centered = true;
        }
        g.pose().pushPose();
        try {
            SocialHubIcons.scaleButton(g,rect,hover);
            drawCard(g,rect,BLUE,hovered);
            map.render(g,rect.x()+4,rect.y()+4,Math.max(1,rect.width()-8),Math.max(1,rect.height()-8),-10000,-10000);
        } finally {
            g.pose().popPose();
        }
    }

    private void drawCard(GuiGraphics g, WorldMapUiLayout.Rect rect, int accent, boolean hovered) {
        rounded(g,rect.x(),rect.y(),rect.width(),rect.height(),5,hovered?accent:LINE);
        rounded(g,rect.x()+1,rect.y()+1,rect.width()-2,rect.height()-2,4,hovered?0xFF2B373B:PANEL);
        g.fill(rect.x()+1,rect.y()+8,rect.x()+3,rect.bottom()-8,accent);
    }

    private void heading(GuiGraphics g, WorldMapUiLayout.Rect rect, String key, SocialHubIcons.Icon icon) {
        icon.draw(g,rect.x()+11,rect.y()+8,false,0);
        line(g,tr(key),rect.x()+34,rect.y()+12,rect.width()-41,TEXT);
    }

    private void line(GuiGraphics g, Component text, int x, int y, int w, int color) {
        g.drawString(font,fit(font,text,Math.max(1,w)),x,y,color,false);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        var management = tile("management",0,false);
        var structures = tile("structures",1,false);
        var community = tile("community",2,false);
        if (tile("map",2,true).contains(x,y)) {
            minecraft.setScreen(new PolishedWorldMapScreen(root));
        } else if (quick(management,"members",0).contains(x,y)) {
            openManagement(2);
        } else if (quick(management,"territory",1).contains(x,y) || shortcutHeader(management).contains(x,y)) {
            openManagement(1);
        } else if (quick(structures,"projects",0).contains(x,y) || shortcutHeader(structures).contains(x,y)) {
            openManagement(3);
        } else if (quick(community,"relations",0).contains(x,y)) {
            CommunityScreen.openRelations(minecraft.screen);
        } else if (quick(community,"chats",1).contains(x,y) || shortcutHeader(community).contains(x,y)) {
            SocialScreen.openDialogs(minecraft.screen);
        } else {
            return false;
        }
        return true;
    }

    private void openManagement(int tab) {
        root.workspaceSelect(tab);
        minecraft.setScreen(root);
    }

    @Override
    public void onClose() {
        root.onClose();
    }
}
