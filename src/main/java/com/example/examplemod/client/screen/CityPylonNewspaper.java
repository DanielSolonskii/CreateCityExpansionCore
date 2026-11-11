package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.uiedit.UiEditLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import static com.example.examplemod.client.screen.CityPylonTheme.*;

/** The newspaper slides out beside the city center, outside its content viewport. */
final class CityPylonNewspaper {
    private static final int RAIL = 30;
    private static final long SLIDE_MS = 220;
    private static final long ROOM_MS = 110;
    private static final int INK = 0xFF392D24;
    private static final int PAPER = 0xFFE7D2AC;
    private static final int RULE = 0xFF937754;
    private static final ResourceLocation LOCK = ResourceLocation.fromNamespaceAndPath(
            "createcityexpansioncore", "textures/gui/community/center_lock.png");

    private final SocialHubIcons.HoverMotion hover = new SocialHubIcons.HoverMotion();
    private boolean open;
    private float position;
    private float start;
    private long startedAt;

    private static MutableComponent tr(String key) {
        return Component.translatable("gui.createcityexpansioncore.workspace.center.newspaper." + key);
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
        float titleScale = Math.min(2.2f, (right-left-4f) / Math.max(1, titleWidth));
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
        int imageBottom = Math.min(contentTop+54,contentBottom);
        g.fill(left,contentTop,middle-3,imageBottom,0xFFBEA581);
        for (int row = contentTop, line = 0; row+2 < contentBottom; row += 8, line++) {
            if (line % 5 == 4) continue;
            g.fill(middle+3,row,right-(line%3)*2,row+2,RULE);
            if (row >= imageBottom) g.fill(left,row,middle-3,row+2,RULE);
        }
        g.fill(left,contentTop,right,contentBottom,0x90534536);
        Component status = tr("status");
        float statusScale = Math.min(1.65f, (right-left-10f) / Math.max(1, font.width(status)));
        int badgeHeight = 25 + (int) Math.ceil(font.lineHeight * statusScale);
        int badgeY = Math.max(contentTop,(contentTop+contentBottom-badgeHeight)/2);
        if (badgeY+badgeHeight > bottom-3) {
            centered(g,font,status,middle,contentTop,statusScale,INK);
        } else {
            rounded(g,left+2,badgeY,right-left-4,badgeHeight,4,DEEP);
            g.blit(LOCK,middle-8,badgeY+4,0,0,16,16,16,16);
            centered(g,font,status,middle,badgeY+22,statusScale,0xFFEFD19A);
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
