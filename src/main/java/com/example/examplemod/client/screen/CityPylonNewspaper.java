package com.example.examplemod.client.screen;

import com.example.examplemod.city.CityGazette;
import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.uiedit.UiEditLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.example.examplemod.client.screen.CityPylonTheme.*;

/** The newspaper slides out beside the city center, outside its content viewport. */
final class CityPylonNewspaper {
    private static final int RAIL = 30;
    private static final long SLIDE_MS = 220;
    private static final long ROOM_MS = 110;
    private static final int INK = 0xFF392D24;
    private static final int PAPER = 0xFFE7D2AC;
    private static final int RULE = 0xFF937754;
    private final SocialHubIcons.HoverMotion hover = new SocialHubIcons.HoverMotion();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());
    private List<CityGazette.Story> stories = List.of();
    private int offset, maxOffset;

    void update(List<CityGazette.Story> next) {
        if (!stories.equals(next)) offset = 0;
        stories = next;
    }

    boolean scroll(CityPylonWorkspaceLayout city, int width, double x, double y, double dy) {
        var paper = page(city,width);
        if (!open || !paper.contains(x,y) || x >= paper.x()+visibleWidth(paper,width)) return false;
        offset = Math.clamp(offset-(int)Math.signum(dy)*24,0,maxOffset);
        return true;
    }
    private boolean open;
    private float position;
    private float start;
    private long startedAt;

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.createcityexpansioncore.workspace.center.newspaper." + key,args);
    }

    private static int paperWidth(int screenWidth) {
        return Math.min(Math.clamp(screenWidth * 3 / 10, 128, 248), Math.max(1, screenWidth / 2 - 16));
    }

    int rightInset(int screenWidth) {
        float room = needsRoom(screenWidth) ? (position == 0 ? 0 : CommunityFx.ease(position * 3)) : 1;
        return RAIL + Math.round(paperWidth(screenWidth) * room);
    }

    private boolean needsRoom(int screenWidth) {
        return screenWidth < 720 + 2 * (12 + RAIL + paperWidth(screenWidth));
    }

    boolean advance(int screenWidth) {
        int previous = rightInset(screenWidth);
        long duration = SLIDE_MS + (needsRoom(screenWidth) ? ROOM_MS : 0);
        float distance = (Util.getMillis() - startedAt) / (float) duration;
        position = CommunityFx.reduced ? (open ? 1 : 0) : Math.clamp(start + (open ? distance : -distance),0,1);
        return previous != rightInset(screenWidth);
    }

    private void toggle() {
        open = !open;
        if (open) offset = 0;
        start = position;
        startedAt = Util.getMillis();
    }

    boolean close() {
        if (!open) return false;
        toggle();
        return true;
    }

    void reset() {
        open = false;
        position = start = 0;
    }

    static WorldMapUiLayout.Rect trigger(CityPylonWorkspaceLayout city) {
        int height = Math.min(54, Math.max(1, city.frameHeight() - 8));
        int center = Math.round(city.originY() + city.virtualHeight() * city.scale() * 0.21f);
        int y = Math.clamp(center - height / 2, city.frameY(), city.frameY() + city.frameHeight() - height);
        return UiEditLayout.apply("newspaper.trigger", city.frameX() + city.frameWidth() + 4, y, 22, height,
                WorldMapUiLayout.Rect::new);
    }

    static WorldMapUiLayout.Rect page(CityPylonWorkspaceLayout city, int screenWidth) {
        return UiEditLayout.apply("newspaper.page", city.frameX() + city.frameWidth() + RAIL, city.frameY(),
                paperWidth(screenWidth), city.frameHeight(), WorldMapUiLayout.Rect::new);
    }

    private WorldMapUiLayout.Rect closeButton(WorldMapUiLayout.Rect page) {
        return UiEditLayout.apply("newspaper.close", page.right() - 24, page.y() + 5, 19, 18,
                WorldMapUiLayout.Rect::new);
    }

    boolean mouseClicked(CityPylonWorkspaceLayout city, int screenWidth, double x, double y) {
        if (trigger(city).contains(x,y)) {
            toggle();
            return true;
        }
        if (position <= 0) return false;
        var page = page(city,screenWidth);
        int visible = visibleWidth(page,screenWidth);
        if (visible == 0) return false;
        int hidden = page.width() - visible;
        if (x < page.x() || !page.contains(x+hidden,y)) return false;
        if (closeButton(page).contains(x+hidden,y)) close();
        return true;
    }

    private int visibleWidth(WorldMapUiLayout.Rect page, int screenWidth) {
        float slide = needsRoom(screenWidth) ? (position * 3 - 1) / 2 : position;
        return slide <= 0 ? 0 : Math.round(page.width() * CommunityFx.ease(slide));
    }

    void render(GuiGraphics g, Font font, CityPylonWorkspaceLayout city, int screenWidth, int mx, int my) {
        hover.beginFrame();
        var trigger = trigger(city);
        boolean hovered = trigger.contains(mx,my);
        g.pose().pushPose();
        SocialHubIcons.scaleButton(g,trigger,hover.amount(trigger,hovered));
        WorldMapChrome.control(g,trigger,open,hovered,true,false);
        int x = trigger.x() + (trigger.width()-13)/2;
        int y = trigger.y() + (trigger.height()-13)/2;
        g.fill(x+2,y+2,x+13,y+13,INK);
        g.fill(x,y,x+11,y+11,hovered || open ? PAPER : BRASS_TEXT);
        g.fill(x+2,y+2,x+9,y+4,INK);
        g.fill(x+2,y+5,x+5,y+9,RULE);
        g.fill(x+6,y+5,x+9,y+6,INK);
        g.fill(x+6,y+7,x+9,y+8,INK);
        g.pose().popPose();

        if (position > 0) {
            var page = page(city,screenWidth);
            int visible = visibleWidth(page,screenWidth);
            if (visible > 0) {
                int hidden = page.width() - visible;
                g.enableScissor(page.x(),page.y(),page.x()+visible,page.bottom());
                g.pose().pushPose();
                try {
                    g.pose().translate(-hidden,0,0);
                    drawPaper(g,font,page);
                    var close = closeButton(page);
                    SocialHubIcons.paintButton(g,font,close,Component.empty(),SocialHubIcons.Icon.CLOSE,hover,
                            mx >= page.x() && close.contains(mx+hidden,my),true,false,false);
                    g.flush();
                } finally {
                    g.pose().popPose();
                    g.disableScissor();
                }
            }
        }
        if (hovered) g.renderTooltip(font,tr("button"),mx,my);
    }

    private void drawPaper(GuiGraphics g, Font font, WorldMapUiLayout.Rect page) {
        int left = page.x()+8, right = page.right()-8, top = page.y(), bottom = page.bottom();
        rounded(g,page.x(),top,page.width(),page.height(),4,RULE);
        rounded(g,page.x()+1,top+1,page.width()-2,page.height()-2,3,PAPER);
        if (right-left < 20 || page.height() < 108) return;
        int middle = (left+right)/2;
        Component titleTop = tr("title_top").withStyle(ChatFormatting.BOLD);
        Component titleBottom = tr("title_bottom").withStyle(ChatFormatting.BOLD);
        int titleWidth = Math.max(font.width(titleTop), font.width(titleBottom));
        float titleScale = Math.min(page.height() < 180 ? 1f : 2.2f, (right-left-4f) / Math.max(1, titleWidth));
        int lineHeight = (int) Math.ceil(font.lineHeight * titleScale);
        g.hLine(left,right,top+26,INK);
        g.hLine(left,right,top+28,RULE);
        centered(g,font,titleTop,middle,top+33,titleScale,INK);
        centered(g,font,titleBottom,middle,top+35+lineHeight,titleScale,INK);
        int mastheadBottom = top+39+2*lineHeight;
        g.hLine(left,right,mastheadBottom,INK);
        g.hLine(left,right,mastheadBottom+2,RULE);

        int contentTop = mastheadBottom+9;
        int contentBottom = bottom-10;
        if (contentBottom <= contentTop) return;
        int column = right-left-4;
        int y = contentTop-offset;
        g.enableScissor(left,contentTop,right,contentBottom);
        try {
            if (stories.isEmpty()) {
                g.drawWordWrap(font,tr("empty"),left,y,column,INK);
            }
            for (var story : stories) {
                String kind = story.kind().name().toLowerCase(Locale.ROOT);
                g.drawString(font,story.kind()==CityGazette.Kind.CITY_REGISTER ? tr("register_label").getString() : DATE.format(Instant.ofEpochMilli(story.time())),left,y,RULE,false);
                y += font.lineHeight+3;
                for (var line : font.split(tr(kind+".title").withStyle(ChatFormatting.BOLD),column)) {
                    g.drawString(font,line,left,y,INK,false);
                    y += font.lineHeight+1;
                }
                y += 3;
                for (var line : font.split(tr(kind+".body",story.subject(),story.place(),story.detail()),column)) {
                    g.drawString(font,line,left,y,INK,false);
                    y += font.lineHeight+1;
                }
                g.hLine(left,right-4,y+4,RULE);
                y += 14;
            }
        } finally { g.disableScissor(); }
        maxOffset = Math.max(0,y+offset-contentBottom);
        offset = Math.min(offset,maxOffset);
        if (maxOffset > 0) {
            int h = contentBottom-contentTop;
            int thumb = Math.max(4,h*h/(h+maxOffset));
            int topY = contentTop+(h-thumb)*offset/maxOffset;
            g.fill(right-1,topY,right,topY+thumb,RULE);
        }
    }

    private static void centered(GuiGraphics g, Font font, Component label, int x, int y, float scale, int color) {
        g.pose().pushPose();
        try {
            g.pose().translate(x-font.width(label)*scale/2,y,0);
            g.pose().scale(scale,scale,1);
            g.drawString(font,label,0,0,color,false);
        } finally {
            g.pose().popPose();
        }
    }
}
