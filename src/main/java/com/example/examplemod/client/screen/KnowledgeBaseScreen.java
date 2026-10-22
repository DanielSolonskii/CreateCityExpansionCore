package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.knowledge.KnowledgeBase;
import com.example.examplemod.client.knowledge.KnowledgeMarkdown;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.example.examplemod.client.screen.WorldMapChrome.*;

/**
 * Локальный просмотр заметок .md из папки «knowledge base» игровой папки клиента.
 *
 * <p><b>Почему только чтение.</b> Файлы читаются исключительно с диска игрока:
 * ни один байт не уходит на сервер, состояние мира не меняется. Поэтому экран
 * открывается клавишей из любого места и не требует открытого меню пилона —
 * ровно как карта города, которая по клавише тоже только читает.
 *
 * <p><b>Оформление.</b> Экран собран на общих примитивах WorldMapChrome (панель,
 * закругления, цвета SURFACE/RAISED/EDGE и акцент ACCENT), поэтому выглядит как
 * остальные окна мода: непрозрачная подложка поверх мира, затемнение при открытии
 * и тихий звуковой отклик через CommunityFx.
 */
public final class KnowledgeBaseScreen extends Screen {
    /** Размер «дизайнерский»: экран масштабируется целиком, если окно меньше. */
    private static final int DESIGN_W = 720, DESIGN_H = 470;
    private static final int SIDEBAR = 178, LIST_TOP = 44, ROW = 20;
    private static final int PAD = 14, LINE_H = 11, SCROLL_STEP = 12;
    private static final int CONTENT_X = SIDEBAR + PAD;
    private static final int CONTENT_W = DESIGN_W - CONTENT_X - PAD;
    /** Свободное место справа под полосу прокрутки заметки. */
    private static final int TEXT_W = CONTENT_W - 10;
    private static final int VIEW_BOTTOM = DESIGN_H - 8;

    private final Screen parent;
    private Path folder;
    private List<KnowledgeBase.Note> notes = List.of();
    private List<RenderLine> lines = List.of();
    private boolean unreadable;
    private int selected = -1;
    private double listScroll, contentScroll, contentHeight;
    private float scale = 1f;
    private int originX, originY;
    private long opened;

    /** Одна визуальная строка заметки: перенос уже посчитан по ширине контента. */
    private record RenderLine(KnowledgeMarkdown.Kind kind, FormattedCharSequence text,
                              int indent, String marker, int top, int height) {}

    public KnowledgeBaseScreen(Screen parent) {
        super(Component.translatable("gui.createcityexpansioncore.knowledge.title"));
        this.parent = parent;
    }

    private static WorldMapUiLayout.Rect closeRect() {
        return new WorldMapUiLayout.Rect(DESIGN_W - 32, 11, 20, 18);
    }

    @Override
    protected void init() {
        scale = Math.max(0.1f, Math.min(1f, Math.min((width - 12f) / DESIGN_W, (height - 12f) / DESIGN_H)));
        originX = Math.round((width - DESIGN_W * scale) / 2f);
        originY = Math.round((height - DESIGN_H * scale) / 2f);
        CommunityFx.load();
        if (opened == 0) {
            opened = Util.getMillis();
            CommunityFx.sound("open");
        }
        folder = KnowledgeBase.folder(minecraft.gameDirectory.toPath());
        if (KnowledgeBase.ensureFolder(folder)) {
            boolean russian = false;
            try {
                russian = minecraft.getLanguageManager().getSelected().toLowerCase(java.util.Locale.ROOT).startsWith("ru");
            } catch (Exception ignored) {
                // Язык определить не удалось — образец будет на английском.
            }
            KnowledgeBase.writeSample(folder, russian);
        }
        String previous = selected >= 0 && selected < notes.size() ? notes.get(selected).name() : null;
        notes = KnowledgeBase.list(folder);
        selected = previous == null ? -1 : indexOf(previous);
        if (selected < 0 && !notes.isEmpty()) {
            selected = 0;
        }
        listScroll = Math.clamp(listScroll, 0, maxListScroll());
        loadContent(false);
    }

    private int indexOf(String name) {
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private void loadContent(boolean resetScroll) {
        double previous = contentScroll;
        unreadable = false;
        if (selected < 0 || selected >= notes.size()) {
            lines = List.of();
            contentHeight = 0;
            contentScroll = 0;
            return;
        }
        List<KnowledgeMarkdown.Block> blocks;
        try {
            blocks = KnowledgeMarkdown.parse(KnowledgeBase.read(notes.get(selected).path()));
        } catch (Exception failure) {
            blocks = List.of();
            unreadable = true;
        }
        layout(blocks);
        contentScroll = resetScroll ? 0 : Math.clamp(previous, 0, maxContentScroll());
    }

    /** Переносим каждый блок по ширине текстовой колонки и считаем общую высоту. */
    private void layout(List<KnowledgeMarkdown.Block> blocks) {
        List<RenderLine> built = new ArrayList<>();
        int y = 0;
        for (KnowledgeMarkdown.Block block : blocks) {
            KnowledgeMarkdown.Kind kind = block.kind();
            if (kind == KnowledgeMarkdown.Kind.BLANK) {
                y += 5;
                continue;
            }
            if (kind == KnowledgeMarkdown.Kind.RULE) {
                built.add(new RenderLine(kind, null, 0, null, 0, 10));
                y += 10;
                continue;
            }
            int top = switch (kind) {
                case H1 -> 8;
                case H2 -> 6;
                case H3 -> 5;
                default -> 1;
            };
            int line = switch (kind) {
                case H1 -> 13;
                case H2, H3 -> 12;
                default -> LINE_H;
            };
            int indent = switch (kind) {
                case BULLET -> 10;
                case NUMBERED -> block.marker() == null ? 10 : font.width(block.marker()) + 5;
                case QUOTE -> 12;
                case CODE -> 8;
                default -> 0;
            };
            List<FormattedCharSequence> wrapped = font.split(block.text(), Math.max(40, TEXT_W - indent));
            if (wrapped.isEmpty()) {
                built.add(new RenderLine(kind, null, indent, block.marker(), top, line));
                y += line;
                continue;
            }
            boolean first = true;
            for (FormattedCharSequence seq : wrapped) {
                built.add(new RenderLine(kind, seq, indent, first ? block.marker() : null, first ? top : 0, line));
                y += line;
                first = false;
            }
        }
        lines = built;
        contentHeight = y;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        double mx = localX(mouseX), my = localY(mouseY);
        g.fill(0, 0, width, height, 0x99090E10);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        panel(g, 0, 0, DESIGN_W, DESIGN_H);
        g.fill(SIDEBAR, 9, SIDEBAR + 1, DESIGN_H - 9, EDGE);
        header(g, mx, my);
        sidebar(g, mx, my);
        content(g);
        float opening = 1 - CommunityFx.ease((Util.getMillis() - opened) / 180.0);
        if (opening > 0) {
            g.fill(1, 1, DESIGN_W - 1, DESIGN_H - 1, ((int) (100 * opening) << 24) | (SURFACE & 0xFFFFFF));
        }
        g.pose().popPose();
    }

    private void header(GuiGraphics g, double mx, double my) {
        g.drawString(font, fit(font, title, SIDEBAR - 26), 13, 15, ACCENT, false);
        g.drawString(font, fit(font, Component.translatable("gui.createcityexpansioncore.knowledge.count", notes.size()), SIDEBAR - 26),
                13, 27, MUTED, false);
        if (selected >= 0 && selected < notes.size()) {
            g.drawString(font, fit(font, Component.literal(notes.get(selected).name()), CONTENT_W - 44),
                    CONTENT_X, 15, TEXT, false);
        }
        WorldMapUiLayout.Rect close = closeRect();
        control(g, close, false, close.contains(mx, my), true, false);
        g.drawCenteredString(font, Component.literal("\u00d7"), close.x() + close.width() / 2, close.y() + 5,
                close.contains(mx, my) ? TEXT : MUTED);
    }

    private void sidebar(GuiGraphics g, double mx, double my) {
        if (notes.isEmpty()) {
            g.drawString(font, tr("empty"), 13, LIST_TOP + 2, MUTED, false);
            return;
        }
        int offset = (int) listScroll;
        for (int i = 0; i < notes.size(); i++) {
            int ry = LIST_TOP + i * ROW - offset;
            if (ry + ROW < LIST_TOP || ry > VIEW_BOTTOM) {
                continue;
            }
            boolean active = i == selected;
            boolean hover = mx >= 6 && mx < SIDEBAR - 8 && my >= ry && my < ry + ROW - 4;
            rounded(g, 6, ry, SIDEBAR - 12, ROW - 4, 4, active ? RAISED : hover ? SURFACE : INSET);
            if (active) {
                g.fill(6, ry + 4, 8, ry + ROW - 8, ACCENT);
            }
            g.drawString(font, fit(font, Component.literal(notes.get(i).name()), SIDEBAR - 34),
                    14, ry + 4, active ? ACCENT : hover ? TEXT : MUTED, false);
        }
        if (maxListScroll() > 0) {
            int trackTop = LIST_TOP;
            int trackH = VIEW_BOTTOM - LIST_TOP;
            int thumbH = Math.max(16, (int) (trackH * trackH / (double) (notes.size() * ROW)));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (listScroll / maxListScroll()));
            rounded(g, SIDEBAR - 5, trackTop, 3, trackH, 1, INSET);
            rounded(g, SIDEBAR - 5, thumbY, 3, thumbH, 1, EDGE);
        }
    }

    private void content(GuiGraphics g) {
        if (notes.isEmpty()) {
            int y = LIST_TOP;
            for (FormattedCharSequence line : font.split(tr("empty_hint"), TEXT_W)) {
                g.drawString(font, line, CONTENT_X, y, MUTED, false);
                y += LINE_H;
            }
            g.drawString(font, fit(font, Component.literal(folder.toString()), TEXT_W), CONTENT_X, y + 4, ACCENT, false);
            return;
        }
        if (selected < 0) {
            g.drawString(font, tr("no_selection"), CONTENT_X, LIST_TOP, MUTED, false);
            return;
        }
        if (unreadable) {
            g.drawString(font, tr("unreadable"), CONTENT_X, LIST_TOP, MUTED, false);
            return;
        }
        // Обрезка — в экранных координатах: поза экрана уже масштабирована.
        g.flush();
        g.enableScissor(sx(CONTENT_X - 6), sy(LIST_TOP), sx(DESIGN_W - 6), sy(VIEW_BOTTOM));
        int y = LIST_TOP - (int) contentScroll;
        for (RenderLine line : lines) {
            int ly = y + line.top();
            y += line.top() + line.height();
            if (ly > VIEW_BOTTOM || ly + line.height() < LIST_TOP) {
                continue;
            }
            drawLine(g, line, ly);
        }
        g.flush();
        g.disableScissor();
        if (maxContentScroll() > 0) {
            int trackTop = LIST_TOP, trackH = VIEW_BOTTOM - LIST_TOP;
            int thumbH = Math.max(16, (int) (trackH * trackH / contentHeight));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (contentScroll / maxContentScroll()));
            rounded(g, DESIGN_W - 10, trackTop, 3, trackH, 1, INSET);
            rounded(g, DESIGN_W - 10, thumbY, 3, thumbH, 1, EDGE);
        }
    }

    private void drawLine(GuiGraphics g, RenderLine line, int ly) {
        switch (line.kind()) {
            case RULE -> g.hLine(CONTENT_X, CONTENT_X + TEXT_W - 1, ly + 4, EDGE);
            case CODE -> {
                rounded(g, CONTENT_X - 5, ly - 1, TEXT_W + 6, line.height() + 1, 3, INSET);
                if (line.text() != null) {
                    g.drawString(font, line.text(), CONTENT_X + line.indent(), ly + 2, MUTED, false);
                }
            }
            case QUOTE -> {
                g.fill(CONTENT_X, ly + 1, CONTENT_X + 2, ly + line.height() - 3, ACCENT);
                if (line.text() != null) {
                    g.drawString(font, line.text(), CONTENT_X + line.indent(), ly + 1, MUTED, false);
                }
            }
            case H1 -> {
                if (line.text() != null) {
                    g.drawString(font, line.text(), CONTENT_X, ly + 2, ACCENT, false);
                }
            }
            case H2 -> {
                if (line.text() != null) {
                    g.drawString(font, line.text(), CONTENT_X, ly + 1, TEXT, false);
                }
            }
            case H3 -> {
                if (line.text() != null) {
                    g.drawString(font, line.text(), CONTENT_X, ly + 1, MUTED, false);
                }
            }
            default -> {
                if (line.marker() != null) {
                    g.drawString(font, line.marker(), CONTENT_X, ly + 1, ACCENT, false);
                }
                if (line.text() != null) {
                    g.drawString(font, line.text(), CONTENT_X + line.indent(), ly + 1, TEXT, false);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) {
            return super.mouseClicked(x, y, button);
        }
        double mx = localX(x), my = localY(y);
        if (closeRect().contains(mx, my)) {
            CommunityFx.sound("click");
            onClose();
            return true;
        }
        if (mx >= 6 && mx < SIDEBAR - 8) {
            int offset = (int) listScroll;
            for (int i = 0; i < notes.size(); i++) {
                int ry = LIST_TOP + i * ROW - offset;
                if (my >= ry && my < ry + ROW - 4) {
                    select(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (localX(x) < SIDEBAR) {
            listScroll = Math.clamp(listScroll - dy * SCROLL_STEP, 0, maxListScroll());
        } else {
            contentScroll = Math.clamp(contentScroll - dy * SCROLL_STEP, 0, maxContentScroll());
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN && !notes.isEmpty()) {
            int next = selected < 0 ? 0 : selected + 1;
            if (next < notes.size()) {
                select(next);
                revealSelected();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP && selected > 0) {
            select(selected - 1);
            revealSelected();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void select(int index) {
        if (index == selected || index < 0 || index >= notes.size()) {
            return;
        }
        selected = index;
        loadContent(true);
        CommunityFx.sound("click");
    }

    private void revealSelected() {
        double view = DESIGN_H - LIST_TOP - 6;
        double top = (double) selected * ROW;
        if (top < listScroll) {
            listScroll = top;
        } else if (top + ROW > listScroll + view) {
            listScroll = top + ROW - view;
        }
        listScroll = Math.clamp(listScroll, 0, maxListScroll());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double localX(double x) { return (x - originX) / scale; }
    private double localY(double y) { return (y - originY) / scale; }
    private int sx(double lx) { return (int) Math.round(originX + lx * scale); }
    private int sy(double ly) { return (int) Math.round(originY + ly * scale); }
    private double maxListScroll() { return Math.max(0, notes.size() * ROW - (DESIGN_H - LIST_TOP - 6)); }
    private double maxContentScroll() { return Math.max(0, contentHeight - (DESIGN_H - LIST_TOP - 6)); }
    private static Component tr(String key) {
        return Component.translatable("gui.createcityexpansioncore.knowledge." + key);
    }
}
