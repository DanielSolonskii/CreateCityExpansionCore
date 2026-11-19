package com.example.examplemod.client.screen;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.CommunityFx;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class KnowledgeHomeScreen extends Screen {
    enum Topic {
        FIRST_STEPS("РїРµСЂРІС‹Рµ С€Р°РіРё", "first steps"),
        CITY("РіРѕСЂРѕРґСЃРєРѕР№ С†РµРЅС‚СЂ", "city center", "РіРѕСЂРѕРґ", "city"),
        TERRITORIES("С‚РµСЂСЂРёС‚РѕСЂРёРё", "territories"),
        PROJECTS("РїСЂРѕРµРєС‚С‹", "projects"),
        COMMUNITY("СЃРѕРѕР±С‰РµСЃС‚РІРѕ", "community"),
        MAPS("РєР°СЂС‚С‹ Рё РјРµС‚РєРё", "maps and markers", "РєР°СЂС‚С‹", "maps");

        private final List<String> folders;

        Topic(String... folders) {
            this.folders = List.of(folders);
        }

        Component label() {
            return Component.translatable("gui.createcityexpansioncore.knowledge." + name().toLowerCase(Locale.ROOT));
        }

        static Topic forPath(String path) {
            for (String part : path.toLowerCase(Locale.ROOT).split("/")) {
                for (Topic topic : values()) {
                    if (topic.folders.contains(part)) return topic;
                }
            }
            return FIRST_STEPS;
        }
    }

    private final Screen parent;
    private final ItemStack cityPylon = new ItemStack(CreateCityExpansionCore.CITY_PYLON_ITEM.get());
    private final SocialHubIcons.HoverMotion motion = new SocialHubIcons.HoverMotion();
    private static final ResourceLocation FRAME = texture("frame");
    private static final ResourceLocation TITLE = texture("title");
    private static final ResourceLocation BOOK = texture("book");
    private static final ResourceLocation CLOSE = texture("close");
    private int left, top, panelWidth, panelHeight, headerHeight;

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "textures/gui/knowledge/" + name + ".png");
    }

    private static void image(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        g.blit(texture, x, y, w, h, 0f, 0f, 1, 1, 1, 1);
    }

    KnowledgeHomeScreen(Screen parent) {
        super(Component.translatable("gui.createcityexpansioncore.knowledge.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CommunityFx.load();
        int columns = width >= 400 ? 3 : width >= 210 ? 2 : 1;
        int rows = 6 / columns;
        // Keep the illustrated plates near their original 3:2 aspect even at large GUI scales.
        int maxHeight = height - 12;
        int maxWidth = Math.min(760, width - 16);
        headerHeight = Math.clamp(Math.min(maxWidth / 10, maxHeight / 5), 26, 76);
        int margin = Math.clamp(maxWidth / 32, 6, 22);
        int gap = Math.clamp(maxWidth / 100, 3, 7);
        int cardWidth = Math.min((maxWidth - 2 * margin - (columns - 1) * gap) / columns,
                (maxHeight - headerHeight - margin - (rows - 1) * gap) * 3 / (2 * rows));
        int cardHeight = cardWidth * 2 / 3;
        panelWidth = 2 * margin + columns * cardWidth + (columns - 1) * gap;
        panelHeight = headerHeight + rows * cardHeight + (rows - 1) * gap + margin;
        top = (height - panelHeight) / 2;
        left = (width - panelWidth) / 2;
        for (Topic topic : Topic.values()) {
            int col = topic.ordinal() % columns;
            int row = topic.ordinal() / columns;
            int x = left + margin + col * (cardWidth + gap);
            int y = top + headerHeight + row * (cardHeight + gap);
            int w = cardWidth;
            int h = cardHeight;
            addRenderableWidget(new Card(x, y, w, h, topic.label(), topic,
                    button -> minecraft.setScreen(new KnowledgeBaseScreen(this, null, topic))));
        }
        addRenderableWidget(new Card(left + panelWidth - margin - headerHeight / 2, top + headerHeight / 4, headerHeight / 2, headerHeight / 2,
                Component.translatable("gui.close"), null, button -> onClose()));
    }

    private final class Card extends Button {
        private final Topic topic;
        private final ResourceLocation idle, highlighted, lettering;

        Card(int x, int y, int w, int h, Component label, Topic topic, OnPress press) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION);
            this.topic = topic;
            String key = topic == null ? "close" : topic.name().toLowerCase(Locale.ROOT);
            idle = topic == null ? CLOSE : texture(key);
            highlighted = topic == null ? CLOSE : texture("lit_" + key);
            lettering = topic == null ? null : texture("label_" + key);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            var bounds = new WorldMapUiLayout.Rect(getX(), getY(), getWidth(), getHeight());
            boolean lit = isHoveredOrFocused();
            float hover = motion.amount(bounds, lit);
            g.pose().pushPose();
            SocialHubIcons.scaleButton(g, bounds, hover);
            image(g, lit ? highlighted : idle, getX(), getY(), getWidth(), getHeight());
            if (topic != null) {
                int labelHeight = Math.max(1, getHeight() * 17 / 100);
                int labelY = getY() + getHeight() - labelHeight - getHeight() / 24;
                if (topic == Topic.CITY) {
                    int padding = Math.max(2, getHeight() / 24);
                    int size = Math.min(getWidth() - 2 * padding, labelY - getY() - 2 * padding);
                    g.pose().pushPose();
                    g.pose().translate(getX() + (getWidth() - size) / 2f, getY() + padding, 0);
                    g.pose().scale(size / 16f, size / 16f, 1);
                    g.renderItem(cityPylon, 0, 0);
                    g.pose().popPose();
                }
                if (minecraft.getLanguageManager().getSelected().equals("ru_ru") && labelHeight >= 9) {
                    int inset = getWidth() / 32;
                    image(g, lettering, getX() + inset, labelY, getWidth() - 2 * inset, labelHeight);
                } else {
                    g.drawCenteredString(font, WorldMapChrome.fit(font, getMessage(), getWidth() - 6),
                            getX() + getWidth() / 2, labelY + (labelHeight - font.lineHeight) / 2, 0xFFEEDFBF);
                }
            }
            g.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        motion.beginFrame();
        g.fill(0, 0, width, height, 0x88090E10);
        image(g, FRAME, left, top, panelWidth, panelHeight);
        int titleHeight = headerHeight / 2;
        int titleX = left + panelWidth / 18;
        int titleY = top + headerHeight / 4;
        if (minecraft.getLanguageManager().getSelected().equals("ru_ru")) {
            image(g, TITLE, titleX, titleY, Math.min(titleHeight * 10, panelWidth * 2 / 3), titleHeight);
        } else {
            image(g, BOOK, titleX, titleY, titleHeight * 2, titleHeight);
            g.drawString(font, getTitle(), titleX + titleHeight * 2 + 4,
                    titleY + (titleHeight - font.lineHeight) / 2, 0xFFEEDFBF);
        }
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
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
