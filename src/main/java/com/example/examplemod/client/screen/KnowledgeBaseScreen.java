package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.knowledge.KnowledgeAccess;
import com.example.examplemod.knowledge.KnowledgeBaseClient;
import com.example.examplemod.knowledge.KnowledgeFileMeta;
import com.example.examplemod.knowledge.KnowledgeImages;
import com.example.examplemod.knowledge.KnowledgeMarkdown;
import com.example.examplemod.knowledge.KnowledgeStorage;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.example.examplemod.client.screen.WorldMapChrome.ACCENT;
import static com.example.examplemod.client.screen.WorldMapChrome.INSET;
import static com.example.examplemod.client.screen.WorldMapChrome.MUTED;
import static com.example.examplemod.client.screen.WorldMapChrome.RAISED;
import static com.example.examplemod.client.screen.WorldMapChrome.SURFACE;
import static com.example.examplemod.client.screen.WorldMapChrome.TEXT;
import static com.example.examplemod.client.screen.WorldMapChrome.fit;
import static com.example.examplemod.client.screen.WorldMapChrome.panel;
import static com.example.examplemod.client.screen.WorldMapChrome.rounded;

/**
 * Экран базы знаний: список материалов слева, тело материала справа.
 *
 * <p>Файлы и содержимое читаются напрямую из локальной папки
 * {@code CCEC - knowledge base}; показываются только файлы, выданные игроку в
 * playerdata (пересечение каталога и синхронизированного списка доступа).
 * Папки показываются группами: по умолчанию свёрнуты, пустые для этого игрока
 * группы не рисуются вовсе. Ссылки: на {@code .md} открывают материал в том же
 * экране, {@code ponder:…} закрывают базу и открывают сцену как из Ponder;
 * картинка {@code ![](файл.png)} вставляется прямо в текст.
 *
 * <p>Экран read-only: правки возможны только через файлы на диске, интерфейс их
 * не редактирует.
 */
public final class KnowledgeBaseScreen extends Screen {
    private static final int ROW = 22;
    private static final int LINE = 11;
    private static final int PADDING = 10;

    private final Screen parent;
    private final String initialPath;
    private KnowledgeBaseLayout layout;
    private String selected;
    private KnowledgeMarkdown.Document document;
    private final Set<String> expanded = new HashSet<>();
    private int listScroll;
    private int listContentHeight;
    private int contentScroll;
    private int contentHeight;
    private String status;
    private long statusAt;
    private long opened, transition;

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.createcityexpansioncore.knowledge." + key, args);
    }

    private final List<Hit> listHits = new ArrayList<>();
    private final List<LinkHit> contentHits = new ArrayList<>();

    private record Hit(KnowledgeBaseLayout.Rect rect, Runnable run) {
    }

    private record LinkHit(int x0, int x1, int yTop, int yBottom, KnowledgeMarkdown.Link link) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= yTop && y < yBottom;
        }
    }

    private KnowledgeBaseScreen(Screen parent, String initialPath) {
        super(Component.translatable("gui.createcityexpansioncore.knowledge.title"));
        this.parent = parent;
        this.initialPath = initialPath;
    }

    /** Открытие по клавише/тултипу: path — файл, который сразу открыть. */
    public static void open(Screen parent, String path) {
        Minecraft.getInstance().setScreen(new KnowledgeBaseScreen(parent, path));
    }

    public static void open(Screen parent) {
        open(parent, null);
    }

    @Override
    protected void init() {
        layout = KnowledgeBaseLayout.calculate(width, height);
        KnowledgeBaseClient.ensureFolder();
        CommunityFx.load();
        if (opened == 0) {
            opened = transition = Util.getMillis();
            CommunityFx.sound("open");
        }
        if (selected == null && initialPath != null) {
            select(findByAnyName(initialPath));
        }
    }

    /** Появление окна: 7px сверху, как у остальных театров мода. */
    private float offset() {
        return 7 * (1 - CommunityFx.ease((Util.getMillis() - opened) / 180.0));
    }

    /** Поиск файла по любому написанию (точный путь либо короткое имя). */
    private KnowledgeFileMeta findByAnyName(String name) {
        KnowledgeFileMeta exact = KnowledgeBaseClient.metaOf(name);
        if (exact != null) return exact;
        for (KnowledgeFileMeta meta : KnowledgeBaseClient.allEntries()) {
            if (KnowledgeAccess.matches(meta.path(), name)) return meta;
        }
        return null;
    }

    /** Выбор файла и разбор документа; false, когда файл недоступен. */
    private boolean select(KnowledgeFileMeta meta) {
        if (meta == null || !KnowledgeBaseClient.hasAccess(meta)) return false;
        selected = meta.path();
        document = KnowledgeBaseClient.readDocument(meta);
        contentScroll = 0;
        String group = parentGroup(meta.path());
        if (!group.isEmpty()) expanded.add(group);
        return true;
    }

    private String parentGroup(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "" : path.substring(0, slash);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        double mx = layout.localX(mouseX);
        double my = layout.localY(mouseY) - offset();
        g.fill(0, 0, width, height, 0x88090E10);
        // Контент рисуется в локальных координатах через pose-масштаб (как Community/Map).
        g.pose().pushPose();
        g.pose().translate(layout.x(), layout.y(), 0);
        g.pose().scale(layout.scale(), layout.scale(), 1);
        g.pose().translate(0, offset(), 0);
        panel(g, 0, 0, layout.width(), layout.height());
        listHits.clear();
        contentHits.clear();

        // Шапка
        g.fill(16, 15, 18, 25, ACCENT);
        g.drawString(font, fit(font, tr("title"), layout.width() - 170), 25, 17, ACCENT, false);
        drawCloseButton(g, mx, my);

        // Левый список
        var list = layout.list();
        rounded(g, list.x(), list.y(), list.width(), list.height(), 5, SURFACE);
        var listClip = layout.screen(list, offset());
        g.flush();
        g.enableScissor(listClip.x(), listClip.y(), listClip.right(), listClip.bottom());
        try {
            listContentHeight = 0;
            renderList(g, mx, my);
        } finally {
            g.flush();
            g.disableScissor();
        }
        listScroll = Math.clamp(listScroll, 0, Math.max(0, listContentHeight - list.height()));

        // Правый просмотр
        var viewer = layout.viewer();
        rounded(g, viewer.x(), viewer.y(), viewer.width(), viewer.height(), 5, SURFACE);
        var viewerClip = layout.screen(viewer, offset());
        g.flush();
        g.enableScissor(viewerClip.x(), viewerClip.y(), viewerClip.right(), viewerClip.bottom());
        try {
            contentHeight = 0;
            renderViewer(g, mx, my);
        } finally {
            g.flush();
            g.disableScissor();
        }
        contentScroll = Math.clamp(contentScroll, 0, Math.max(0, contentHeight - viewer.height()));

        renderFooter(g);
        g.flush();
        g.pose().popPose();
    }

    private void drawCloseButton(GuiGraphics g, double mx, double my) {
        var close = layout.close();
        boolean hover = close.contains(mx, my);
        WorldMapChrome.control(g, new WorldMapUiLayout.Rect(close.x(), close.y(), close.width(), close.height()),
                false, hover, true, false);
        g.drawString(font, "×", close.x() + close.width() / 2 - font.width("×") / 2,
                close.y() + 8, hover ? ACCENT : MUTED, false);
        listHits.add(new Hit(close, () -> {
            if (parent != null) minecraft.setScreen(parent);
            else minecraft.setScreen(null);
        }));
    }

    // ======= Левый список =======

    /** Список: файлы корня + свёрнутые группы верхнего уровня; вложенность — рекурсивно. */
    private void renderList(GuiGraphics g, double mx, double my) {
        List<KnowledgeFileMeta> entries = KnowledgeBaseClient.visibleEntries();
        if (entries.isEmpty()) {
            int y = layout.list().y() + PADDING;
            for (var line : font.split(Component.translatable("gui.createcityexpansioncore.knowledge.empty_list"),
                    layout.list().width() - 16)) {
                g.drawString(font, line, layout.list().x() + 8, y, MUTED, false);
                y += LINE;
            }
            listContentHeight = y - layout.list().y();
            return;
        }
        int y = layout.list().y() + PADDING - listScroll;
        for (KnowledgeFileMeta meta : entries) {
            if (parentGroup(meta.path()).isEmpty()) y = fileRow(g, meta, y, mx, my);
        }
        List<String> topDirs = new ArrayList<>();
        for (KnowledgeFileMeta meta : entries) {
            String rest = meta.path();
            int slash = rest.indexOf('/');
            if (slash <= 0) continue;
            String dir = rest.substring(0, slash);
            if (!topDirs.contains(dir)) topDirs.add(dir);
        }
        topDirs.sort(String.CASE_INSENSITIVE_ORDER);
        for (String dir : topDirs) {
            y = groupRow(g, dir, subtree(entries, dir), 0, y, mx, my);
        }
        listContentHeight = Math.max(0, y - layout.list().y() + PADDING);
    }

    /** Ряд файла; возвращает следующий y. */
    private int fileRow(GuiGraphics g, KnowledgeFileMeta meta, int y, double mx, double my) {
        var rect = row(y);
        boolean hover = rect.contains(mx, my);
        boolean active = meta.path().equals(selected);
        rounded(g, rect.x(), rect.y(), rect.width(), ROW, 3, hover ? RAISED : INSET);
        if (active) g.fill(rect.x() + 1, rect.y() + 4, rect.x() + 3, rect.bottom() - 4, ACCENT);
        int textX = rect.x() + 9;
        ItemStack icon = iconItem(meta);
        if (icon != null) {
            g.renderItem(icon, rect.x() + 5, rect.y() + 3);
            textX = rect.x() + 24;
        }
        g.drawString(font, fit(font, Component.literal(meta.displayTitle()), rect.right() - textX - 6),
                textX, rect.y() + (ROW - 9) / 2, active ? ACCENT : TEXT, false);
        listHits.add(new Hit(rect, () -> {
            if (select(meta)) {
                status = "opened";
                statusAt = Util.getMillis();
                CommunityFx.sound("click");
            } else {
                status = "not_available";
                statusAt = Util.getMillis();
                CommunityFx.sound("error");
            }
        }));
        return y + ROW + 2;
    }

    /** Ряд группы (папки): свёрнута по умолчанию, раскрытие — файлы и вложенные группы. */
    private int groupRow(GuiGraphics g, String folder, List<KnowledgeFileMeta> subtree,
                         int depth, int y, double mx, double my) {
        if (subtree.isEmpty()) return y; // пустых для игрока групп не видно
        var rect = row(y);
        boolean hover = rect.contains(mx, my);
        boolean open = expanded.contains(folder);
        rounded(g, rect.x(), rect.y(), rect.width(), ROW, 3, hover ? RAISED : INSET);
        int x = rect.x() + 9 + depth * 10;
        g.drawString(font, open ? "▾" : "▸", x, rect.y() + (ROW - 9) / 2, ACCENT, false);
        g.drawString(font, fit(font, Component.literal(folder.substring(folder.lastIndexOf('/') + 1)),
                rect.right() - (x + 14) - 6), x + 14, rect.y() + (ROW - 9) / 2, TEXT, false);
        listHits.add(new Hit(rect, () -> {
            if (open) expanded.remove(folder);
            else expanded.add(folder);
            CommunityFx.sound("click");
        }));
        if (!open) return y + ROW + 2;
        int cursor = y + ROW + 2;
        for (KnowledgeFileMeta meta : subtree) {
            if (parentGroup(meta.path()).equals(folder)) cursor = fileRow(g, meta, cursor, mx, my);
        }
        List<String> dirs = subDirectories(folder, subtree);
        for (String dir : dirs) cursor = groupRow(g, dir, subtree(subtree, dir), depth + 1, cursor, mx, my);
        return cursor + 2;
    }

    private List<KnowledgeFileMeta> subtree(List<KnowledgeFileMeta> source, String folder) {
        List<KnowledgeFileMeta> result = new ArrayList<>();
        String prefix = folder + "/";
        for (KnowledgeFileMeta meta : source) {
            if (meta.path().startsWith(prefix)) result.add(meta);
        }
        return result;
    }

    private List<String> subDirectories(String folder, List<KnowledgeFileMeta> subtree) {
        List<String> dirs = new ArrayList<>();
        String prefix = folder + "/";
        for (KnowledgeFileMeta meta : subtree) {
            String rest = meta.path().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash <= 0) continue;
            String dir = folder + "/" + rest.substring(0, slash);
            if (!dirs.contains(dir)) dirs.add(dir);
        }
        dirs.sort(String.CASE_INSENSITIVE_ORDER);
        return dirs;
    }

    private KnowledgeBaseLayout.Rect row(int y) {
        var list = layout.list();
        return new KnowledgeBaseLayout.Rect(list.x() + 4, y, list.width() - 8, ROW);
    }

    /** Иконка предмета из поля icon; поля нет или id неизвестен — иконки нет. */
    private ItemStack iconItem(KnowledgeFileMeta meta) {
        String icon = meta.icon();
        if (icon == null || icon.isBlank()) return null;
        int colon = icon.indexOf(':');
        ResourceLocation id = colon >= 0
                ? ResourceLocation.fromNamespaceAndPath(icon.substring(0, colon).trim(), icon.substring(colon + 1).trim())
                : ResourceLocation.withDefaultNamespace(icon.trim());
        return BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : null;
    }

    // ======= Правый просмотр =======

    /** Просмотр выбранного документа: заголовок, блоки, ссылки, картинки. */
    private void renderViewer(GuiGraphics g, double mx, double my) {
        var viewer = layout.viewer();
        if (selected == null || document == null) {
            int y = viewer.y() + PADDING + LINE;
            for (var line : font.split(Component.translatable("gui.createcityexpansioncore.knowledge.pick"),
                    viewer.width() - PADDING * 2)) {
                g.drawString(font, line, viewer.x() + PADDING, y, MUTED, false);
                y += LINE;
            }
            contentHeight = viewer.height();
            return;
        }
        int y = viewer.y() + PADDING - contentScroll;
        int width = viewer.width() - PADDING * 2;
        g.drawString(font, fit(font, Component.literal(document.meta().displayTitle()), width),
                viewer.x() + PADDING, y, ACCENT, false);
        g.hLine(viewer.x() + PADDING, viewer.right() - PADDING, y + 12, WorldMapChrome.EDGE);
        y += 20;
        for (KnowledgeMarkdown.Block block : document.blocks()) {
            y = renderBlock(g, block, viewer.x() + PADDING, y, width, mx, my);
        }
        contentHeight = Math.max(0, y + contentScroll - viewer.y());
    }

    private int renderBlock(GuiGraphics g, KnowledgeMarkdown.Block block, int x, int y,
                            int width, double mx, double my) {
        if (block instanceof KnowledgeMarkdown.Heading heading) {
            g.drawString(font, fit(font, Component.literal(heading.text()), width), x, y, TEXT, false);
            if (heading.level() == 1) {
                g.hLine(x, x + width, y + LINE + 3, WorldMapChrome.EDGE);
                return y + LINE + 9;
            }
            return y + LINE + 5;
        }
        if (block instanceof KnowledgeMarkdown.Image image) {
            return renderImage(g, image, x, y, width);
        }
        var paragraph = (KnowledgeMarkdown.Paragraph) block;
        if (paragraph.bullet()) {
            g.drawString(font, "•", x + 2, y, MUTED, false);
            x += 12;
            width -= 12;
        }
        return drawWrapped(g, paragraph.segments(), x, width, y, mx, my) + LINE;
    }

    private int renderImage(GuiGraphics g, KnowledgeMarkdown.Image image, int x, int y, int availableWidth) {
        int[] size = new int[2];
        ResourceLocation texture = KnowledgeImages.request(resolveLocalFile(image.file()), size);
        if (texture == null || size[0] <= 0 || size[1] <= 0) {
            g.drawString(font, "[🖼 " + KnowledgeImages.name(image.file()) + "]", x, y, MUTED, false);
            return y + LINE + 6;
        }
        int drawW = Math.min(320, availableWidth);
        int drawH = Math.max(1, (int) Math.round(drawW * size[1] / (float) size[0]));
        rounded(g, x - 2, y - 2, drawW + 4, drawH + 4, 3, WorldMapChrome.EDGE);
        g.blit(texture, x, y, drawW, drawH, 0, 0, size[0], size[1], size[0], size[1]);
        return y + drawH + 12;
    }

    /** Перенос абзаца по ширине; inline-ссылки рисуются цветом и кликаются. */
    private int drawWrapped(GuiGraphics g, List<KnowledgeMarkdown.Segment> segments, int x0, int width, int y,
                            double mx, double my) {
        int right = x0 + width;
        int space = font.width(" ");
        int cursorX = x0;
        int cursorY = y;
        for (Word word : words(segments)) {
            int wordWidth = font.width(word.text());
            if (cursorX > x0 && cursorX + wordWidth > right) {
                cursorX = x0;
                cursorY += LINE;
            }
            boolean ponder = word.link() != null && word.link().type() == KnowledgeMarkdown.Link.Type.PONDER;
            g.drawString(font, word.text(), cursorX, cursorY, ponder ? 0xFFC7772F : word.link() != null ? ACCENT : TEXT, false);
            if (word.link() != null) {
                contentHits.add(new LinkHit(cursorX, cursorX + wordWidth, cursorY, cursorY + LINE, word.link()));
            }
            cursorX += wordWidth + space;
        }
        return cursorY + LINE;
    }

    private record Word(String text, KnowledgeMarkdown.Link link) {
    }

    private List<Word> words(List<KnowledgeMarkdown.Segment> segments) {
        List<Word> result = new ArrayList<>();
        for (KnowledgeMarkdown.Segment segment : segments) {
            if (segment.text() == null || segment.text().isEmpty()) continue;
            for (String part : segment.text().split(" +")) {
                if (!part.isEmpty()) result.add(new Word(part, segment.link()));
            }
        }
        return result;
    }

    /**
     * Путь картинки относительно папки текущего .md; если там файла нет, а ссылка
     * короткая — пробуем от корня базы (частая авторская привычка).
     */
    private Path resolveLocalFile(String relative) {
        String target = relative.replace('\\', '/').trim();
        KnowledgeFileMeta meta = KnowledgeBaseClient.metaOf(selected);
        if (meta == null) return Path.of(target);
        Path direct = KnowledgeBaseClient.folderOf(meta).resolve(target);
        if (Files.isRegularFile(direct)) return direct;
        Path root = KnowledgeStorage.folder().resolve(target);
        return Files.isRegularFile(root) ? root : direct;
    }

    // ======= Взаимодействие =======

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        if (button != 0) return super.mouseClicked(screenX, screenY, button);
        double mx = layout.localX(screenX);
        double my = layout.localY(screenY);
        for (int i = contentHits.size() - 1; i >= 0; i--) {
            if (contentHits.get(i).contains(mx, my)) {
                openLink(contentHits.get(i).link());
                return true;
            }
        }
        for (int i = listHits.size() - 1; i >= 0; i--) {
            if (listHits.get(i).rect().contains(mx, my)) {
                listHits.get(i).run().run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double dx, double dy) {
        double mx = layout.localX(screenX);
        double my = layout.localY(screenY);
        var viewer = layout.viewer();
        if (viewer.contains(mx, my)) {
            contentScroll = Math.clamp(contentScroll - (int) (dy * LINE * 3), 0,
                    Math.max(0, contentHeight - viewer.height()));
            return true;
        }
        var list = layout.list();
        if (list.contains(mx, my)) {
            listScroll = Math.clamp(listScroll - (int) (dy * LINE * 3), 0,
                    Math.max(0, listContentHeight - list.height()));
            return true;
        }
        return super.mouseScrolled(screenX, screenY, dx, dy);
    }

    /** Открытие ссылки: файл — внутри базы, ponder — закрывает базу и открывает сцену. */
    private void openLink(KnowledgeMarkdown.Link link) {
        if (link == null) return;
        if (link.type() == KnowledgeMarkdown.Link.Type.PONDER) {
            try {
                PonderUI ponder = PonderUI.of(ResourceLocation.parse(link.target()));
                minecraft.setScreen(ponder);
            } catch (Exception e) {
                status = "ponder_missing";
                statusAt = Util.getMillis();
                CommunityFx.sound("error");
            }
            return;
        }
        String target = link.target();
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            status = "external_skipped";
            statusAt = Util.getMillis();
            CommunityFx.sound("error");
            return;
        }
        KnowledgeFileMeta resolved = resolveDocPath(target);
        if (resolved != null && select(resolved)) {
            CommunityFx.sound("click");
            return;
        }
        status = "not_available";
        statusAt = Util.getMillis();
        CommunityFx.sound("error");
    }

    /**
     * Разрешение ссылки на .md: сначала относительно папки текущего файла, затем
     * поиск по любому написанию имени по всей базе.
     */
    private KnowledgeFileMeta resolveDocPath(String target) {
        String cleaned = target.replace('\\', '/').trim();
        int hash = cleaned.indexOf('#');
        if (hash >= 0) cleaned = cleaned.substring(0, hash);
        if (cleaned.isEmpty()) return null;
        KnowledgeFileMeta meta = KnowledgeBaseClient.metaOf(selected);
        if (meta != null) {
            String folder = parentGroup(meta.path());
            String local = folder.isEmpty() ? cleaned : folder + "/" + cleaned;
            KnowledgeFileMeta direct = KnowledgeBaseClient.metaOf(local);
            if (direct != null && KnowledgeBaseClient.hasAccess(direct)) return direct;
        }
        for (KnowledgeFileMeta candidate : KnowledgeBaseClient.allEntries()) {
            if (KnowledgeAccess.matches(candidate.path(), cleaned)) return candidate;
        }
        return null;
    }

    private void renderFooter(GuiGraphics g) {
        boolean showStatus = status != null && Util.getMillis() - statusAt < 6000;
        Component text = showStatus
                ? tr(status)
                : tr("footer_hint", KnowledgeBaseClient.visibleEntries().size());
        g.drawString(font, fit(font, text, layout.width() - 170), 25, layout.height() - 25, MUTED, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
