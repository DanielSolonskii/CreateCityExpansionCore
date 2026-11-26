package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.example.examplemod.client.screen.KnowledgeChrome.image;
import static com.example.examplemod.client.screen.KnowledgeChrome.stepped;
import static com.example.examplemod.client.screen.KnowledgeChrome.texture;

final class KnowledgeHomeScreen extends Screen {
    enum Topic {
        FIRST_STEPS("первые шаги", "first steps"),
        CITY("города", "городской центр", "город", "cities", "city center", "city"),
        TERRITORIES("территории", "territories"),
        PROJECTS("проекты", "projects"),
        COMMUNITY("сообщество", "community"),
        MAPS("карты и метки", "карты", "maps and markers", "maps");

        private final List<String> folders;

        Topic(String... folders) {
            this.folders = List.of(folders);
        }

        Component label() {
            return Component.translatable("gui.createcityexpansioncore.knowledge." + name().toLowerCase(Locale.ROOT));
        }

        static Topic forPath(String path) {
            int fileStart = path.lastIndexOf('/');
            if (fileStart < 0) return FIRST_STEPS;
            for (String part : path.substring(0, fileStart).toLowerCase(Locale.ROOT).split("/")) {
                for (Topic topic : values()) {
                    if (topic.folders.contains(part)) return topic;
                }
            }
            return FIRST_STEPS;
        }
    }

    private static final int COLUMNS = 3;
    private static final int CAPTION_TEXTURE_WIDTH = 384;
    private static final int CAPTION_TEXTURE_HEIGHT = 44;
    private final Screen parent;
    private final SocialHubIcons.HoverMotion motion = new SocialHubIcons.HoverMotion();
    private static final ResourceLocation TITLE = texture("title_clean");
    private final List<Card> cards = new ArrayList<>();
    private Card close;
    private int left, top, panelWidth, panelHeight, headerHeight;
    private int gridTop, gridBottom, contentHeight, rowPitch;
    private double scroll;

    KnowledgeHomeScreen(Screen parent) {
        super(Component.translatable("gui.createcityexpansioncore.knowledge.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CommunityFx.load();
        double previousRows = rowPitch == 0 ? 0 : scroll / rowPitch;
        boolean wasAtBottom = maxScroll() > 0 && scroll >= maxScroll();
        cards.clear();
        Topic[] topics = Topic.values();
        int rows = (topics.length + COLUMNS - 1) / COLUMNS;
        // Keep the illustrated plates near their original 3:2 aspect even at large GUI scales.
        int maxHeight = Math.max(1, height - 12);
        int maxWidth = Math.min(760, Math.max(1, width - 16));
        headerHeight = Math.clamp(Math.min(maxWidth / 10, maxHeight / 6), 28, 76);
        int margin = Math.clamp(Math.min(maxWidth / 32, maxHeight / 24), 6, 22);
        int gap = Math.clamp(maxWidth / 100, 3, 7);
        int availableHeight = Math.max(1, maxHeight - headerHeight - margin);
        int widthPerColumn = (maxWidth - 2 * margin - (COLUMNS - 1) * gap) / COLUMNS;
        int widthForHeight = Math.min(availableHeight * 3 / 2,
                Math.max(72, (availableHeight - gap) * 3 / 4));
        int cardWidth = Math.max(1, Math.min(widthPerColumn, widthForHeight));
        int cardHeight = Math.max(1, cardWidth * 2 / 3);
        rowPitch = cardHeight + gap;
        contentHeight = rows * rowPitch - gap;
        panelWidth = 2 * margin + COLUMNS * cardWidth + (COLUMNS - 1) * gap;
        // Keep part of the next row below the viewport so scrolling is discoverable.
        int previewHeight = 2 * rowPitch - gap - Math.max(10, cardHeight / 7);
        panelHeight = headerHeight + Math.min(availableHeight, Math.min(contentHeight, previewHeight)) + margin;
        top = (height - panelHeight) / 2;
        left = (width - panelWidth) / 2;
        gridTop = top + headerHeight;
        gridBottom = top + panelHeight - margin;
        for (Topic topic : topics) {
            int col = topic.ordinal() % COLUMNS;
            int x = left + margin + col * (cardWidth + gap);
            cards.add(addRenderableWidget(new Card(x, gridTop, cardWidth, cardHeight, topic.label(), topic,
                    button -> minecraft.setScreen(new KnowledgeBaseScreen(this, null, topic)))));
        }
        int closeSize = Math.clamp(headerHeight / 2, 18, 38);
        close = addRenderableWidget(new Card(left + panelWidth - margin - closeSize,
                top + (headerHeight - closeSize) / 2, closeSize, closeSize,
                Component.translatable("gui.close"), null, button -> onClose()));
        updateScroll(wasAtBottom ? maxScroll() : previousRows * rowPitch);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (gridBottom - gridTop));
    }

    private void updateScroll(double value) {
        scroll = Math.clamp(value, 0, maxScroll());
        int offset = (int) Math.round(scroll);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setY(gridTop + (i / COLUMNS) * rowPitch - offset);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && maxScroll() > 0 && x >= left && x < left + panelWidth && inGrid(y)) {
            updateScroll(scroll - vertical * rowPitch / 2.0);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    protected void changeFocus(ComponentPath path) {
        super.changeFocus(path);
        if (getFocused() instanceof Card card && card.topic != null) {
            int bottom = card.getY() + card.getHeight();
            if (card.getY() < gridTop) updateScroll(scroll + card.getY() - gridTop);
            else if (bottom > gridBottom) updateScroll(scroll + bottom - gridBottom);
        }
    }

    private boolean inGrid(double y) {
        return y >= gridTop && y < gridBottom;
    }

    private final class Card extends Button {
        private final Topic topic;
        private final ResourceLocation artwork, lettering;

        Card(int x, int y, int w, int h, Component label, Topic topic, OnPress press) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION);
            this.topic = topic;
            String key = topic == null ? "close" : topic.name().toLowerCase(Locale.ROOT);
            artwork = topic == null ? KnowledgeChrome.CLOSE : texture("art_" + key);
            lettering = topic == null ? null : texture("caption_" + key);
        }

        @Override
        protected boolean clicked(double x, double y) {
            return (topic == null || inGrid(y)) && super.clicked(x, y);
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            return (topic == null || inGrid(y)) && super.isMouseOver(x, y);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            var bounds = new WorldMapUiLayout.Rect(getX(), getY(), getWidth(), getHeight());
            boolean lit = isHoveredOrFocused();
            float hover = motion.amount(bounds, lit);
            g.pose().pushPose();
            try {
                SocialHubIcons.scaleButton(g, bounds, hover);
                if (topic == null) {
                    image(g, artwork, getX(), getY(), getWidth(), getHeight());
                    return;
                }
                drawTile(g, bounds, hover, lit);
                boolean illustratedCaption = minecraft.getLanguageManager().getSelected().equals("ru_ru");
                int textWidth = Math.max(1, getWidth() - 10);
                int captionWidth = Math.min(textWidth,
                        Math.max(1, getHeight() * 17 / 100) * CAPTION_TEXTURE_WIDTH / CAPTION_TEXTURE_HEIGHT);
                int captionHeight = Math.max(1, (captionWidth * CAPTION_TEXTURE_HEIGHT
                        + CAPTION_TEXTURE_WIDTH - 1) / CAPTION_TEXTURE_WIDTH);
                var lines = illustratedCaption ? List.<net.minecraft.util.FormattedCharSequence>of()
                        : font.split(getMessage(), textWidth);
                int captionSpace = illustratedCaption ? captionHeight : Math.min(2, lines.size()) * font.lineHeight;
                int captionY = bounds.bottom() - captionSpace - Math.max(4, getHeight() / 14);
                int artY = getY() + Math.max(3, getHeight() / 20);
                int artHeight = Math.max(1, captionY - artY - Math.max(3, getHeight() / 24));
                int textureWidth = topic == Topic.CITY ? 200 : 384;
                int artWidth = Math.max(1, Math.min(getWidth() * 88 / 100, artHeight * textureWidth / 200));
                int drawHeight = Math.max(1, artWidth * 200 / textureWidth);
                image(g, artwork, getX() + (getWidth() - artWidth) / 2,
                        artY + artHeight - drawHeight, artWidth, drawHeight);
                if (illustratedCaption) {
                    image(g, lettering, getX() + (getWidth() - captionWidth) / 2,
                            captionY, captionWidth, captionHeight);
                } else {
                    for (int i = 0; i < Math.min(2, lines.size()); i++) {
                        g.drawCenteredString(font, lines.get(i), getX() + getWidth() / 2,
                                captionY + i * font.lineHeight, 0xFFEEDFBF);
                    }
                }
            } finally {
                g.pose().popPose();
            }
        }
    }

    private static void drawTile(GuiGraphics g, WorldMapUiLayout.Rect bounds, float motion, boolean lit) {
        float warmth = CommunityFx.reduced ? (lit ? 1 : 0) : motion;
        int edge = net.minecraft.util.FastColor.ARGB32.lerp(warmth, KnowledgeChrome.EDGE, KnowledgeChrome.ACCENT);
        int background = net.minecraft.util.FastColor.ARGB32.lerp(warmth, KnowledgeChrome.SURFACE, KnowledgeChrome.HIGHLIGHT);
        stepped(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4, 0xFF101117);
        stepped(g, bounds.x()+1, bounds.y()+1, bounds.width()-2, bounds.height()-2, 3, edge);
        stepped(g, bounds.x()+3, bounds.y()+3, bounds.width()-6, bounds.height()-6, 2, background);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        motion.beginFrame();
        g.fill(0, 0, width, height, KnowledgeChrome.BACKDROP);
        KnowledgeChrome.frame(g, left, top, panelWidth, panelHeight);
        int titleHeight = headerHeight / 2;
        int titleX = left + panelWidth / 18;
        int titleY = top + headerHeight / 4;
        int textX = KnowledgeChrome.headingMark(g, titleX, titleY, titleHeight);
        int textWidth = Math.max(1, close.getX() - textX - 6);
        if (minecraft.getLanguageManager().getSelected().equals("ru_ru")) {
            int titleWidth = Math.min(titleHeight * 7, textWidth);
            int textHeight = Math.max(1, titleWidth / 7);
            image(g, TITLE, textX, titleY + (titleHeight-textHeight)/2, titleWidth, textHeight);
        } else {
            g.drawString(font, WorldMapChrome.fit(font, getTitle(), textWidth), textX,
                    titleY + (titleHeight - font.lineHeight) / 2, KnowledgeChrome.TEXT);
        }
        g.enableScissor(left, gridTop, left + panelWidth, gridBottom);
        try {
            for (Card card : cards) {
                if (card.getY() < gridBottom && card.getY() + card.getHeight() > gridTop) {
                    card.render(g, mouseX, mouseY, partialTick);
                }
            }
        } finally {
            g.disableScissor();
        }
        if (maxScroll() > 0) {
            int track = gridBottom - gridTop;
            int thumb = Math.min(track, Math.max(8, track * track / contentHeight));
            int thumbY = gridTop + (int) Math.round((track - thumb) * scroll / maxScroll());
            int x = left + panelWidth - Math.max(5, (cards.getFirst().getX() - left) / 2 + 1);
            g.fill(x, gridTop, x + 2, gridBottom, WorldMapChrome.INSET);
            g.fill(x, thumbY, x + 2, thumbY + thumb, WorldMapChrome.ACCENT);
        }
        close.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
