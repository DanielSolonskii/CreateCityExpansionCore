package com.example.examplemod.knowledge;

import com.example.examplemod.network.KnowledgeAccessPayload;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the installed server revision without network requests. Singleplayer
 * merges user articles and downloaded libraries and exposes all local articles.
 *
 * <p>Скан переоценивается не чаще раза в секунду — трогать диск каждый кадр
 * недопустимо, а реакция на новые файлы в течение секунды достаточно быстрая для
 * UX.
 */
public final class KnowledgeBaseClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SCAN_INTERVAL_MILLIS = 1000;

    private static volatile List<String> allowed = List.of();
    private static final StringBuilder pendingAccess = new StringBuilder();
    private static volatile Map<String, KnowledgeFileMeta> metas = Map.of();
    private static volatile Map<String, KnowledgeFileMeta> byParentItem = Map.of();
    private static Map<String, Path> files = Map.of(), roots = Map.of();
    private static long scannedAt;
    private static long rescanAfter; // 0: первый вызов обязан отсканировать каталог

    private KnowledgeBaseClient() {
    }

    /** Payload с сервера: полный список имён из playerdata. */
    public static void acceptAccess(KnowledgeAccessPayload payload) {
        if (payload.first()) pendingAccess.setLength(0);
        if (pendingAccess.length()+payload.files().length()>2*1024*1024) { pendingAccess.setLength(0); return; }
        pendingAccess.append(payload.files());
        if (!payload.last()) return;
        allowed = KnowledgeAccess.parse(pendingAccess.toString()); pendingAccess.setLength(0);
        LOGGER.info("База знаний: сервер выдал {} записей доступа", allowed.size());
    }

    /** Папка базы: создаётся при первом касании интерфейса. */
    public static void ensureFolder() {
        KnowledgeStorage.ensureFolder();
    }

    public static void invalidate(boolean clearAccess) {
        rescanAfter = 0; metas = Map.of(); byParentItem = Map.of(); files = Map.of(); roots = Map.of();
        if (clearAccess) { allowed = List.of(); pendingAccess.setLength(0); }
    }

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now < rescanAfter) return;
        boolean local = KnowledgeSyncClient.local();
        rescanAfter = local ? now + SCAN_INTERVAL_MILLIS : Long.MAX_VALUE;
        Map<String, KnowledgeFileMeta> scanned = new LinkedHashMap<>();
        Map<String, KnowledgeFileMeta> parents = new HashMap<>();
        Map<String,Path> locations = new HashMap<>(), bases = new HashMap<>();
        Map<String,Path> catalogs = new LinkedHashMap<>();
        if (local) {
            catalogs.put("", KnowledgeStorage.folder());
            Path cache = KnowledgeSyncClient.cacheRoot();
            if (Files.isDirectory(cache)) try (var dirs = Files.list(cache)) {
                for (Path dir : dirs.sorted().toList()) {
                    if (!KnowledgeBundle.validHash(dir.getFileName().toString())) continue;
                    try {
                        Path root = new KnowledgeBundle.Cache(dir).active();
                        if (root != null) catalogs.put("Сохранённые базы/" + dir.getFileName() + "/", root);
                    } catch (java.io.IOException damaged) { LOGGER.warn("Skipping damaged offline library {}",dir); }
                }
            } catch (java.io.IOException e) { LOGGER.warn("Cannot read offline knowledge cache", e); }
        } else if (KnowledgeSyncClient.ready() && KnowledgeSyncClient.activeRoot() != null) {
            catalogs.put("", KnowledgeSyncClient.activeRoot());
        }
        catalogs.forEach((prefix, root) -> KnowledgeStorage.forEachMarkdown(root, path -> {
            String key = prefix + path.rel();
            KnowledgeFileMeta meta = KnowledgeFileMeta.parse(key, KnowledgeStorage.firstLines(path.file(), 32));
            scanned.put(key, meta); locations.put(key, path.file()); bases.put(key, root);
            if (meta.parentItem() != null && !meta.parentItem().isBlank()) {
                parents.putIfAbsent(meta.parentItem(), meta);
            }
        }));
        files = locations; roots = bases;
        metas = scanned;
        byParentItem = parents;
        scannedAt = now;
    }

    /** Файл, привязанный к предмету через {@code parent_item}. */
    public static KnowledgeFileMeta byParentItem(String itemId) {
        refresh();
        return byParentItem.get(itemId);
    }

    /** Есть ли у игрока доступ к файлу. */
    public static boolean hasAccess(KnowledgeFileMeta meta) {
        return KnowledgeSyncClient.local() || KnowledgeAccess.hasAccess(meta, allowed);
    }

    /** Число записей доступа — для интерфейса (пусто ⇒ «сервер не выдал ничего»). */
    public static int allowedCount() {
        return allowed.size();
    }

    /**
     * Разобранный документ файла. Рендер сам собирает body, заголовочные поля
     * уже в meta.
     */
    public static KnowledgeMarkdown.Document readDocument(KnowledgeFileMeta meta) {
        Path file = fileOf(meta);
        try {
            String source = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            return KnowledgeMarkdown.parse(source, meta);
        } catch (Exception e) {
            LOGGER.warn("Не удалось прочитать {}", file, e);
            return new KnowledgeMarkdown.Document(meta, List.of(
                    new KnowledgeMarkdown.Paragraph(List.of(new KnowledgeMarkdown.Segment(
                            "[Не удалось прочитать файл]", null)), false)));
        }
    }

    /** Настоящий путь файла по мета. */
    public static Path fileOf(KnowledgeFileMeta meta) {
        return files.get(meta.path());
    }

    public static Path resolveLocalFile(KnowledgeFileMeta meta, String target) {
        if (meta == null || !files.containsKey(meta.path())) return null;
        Path root = roots.get(meta.path()).toAbsolutePath().normalize();
        try {
            Path direct = fileOf(meta).getParent().resolve(target.replace('\\','/')).toAbsolutePath().normalize();
            if (direct.startsWith(root)) {
                Path safe = KnowledgeBundle.resolve(root, root.relativize(direct).toString().replace('\\','/'));
                if (Files.isRegularFile(safe)) return safe;
            }
            Path fallback = KnowledgeBundle.resolve(root, target.replace('\\','/'));
            return Files.isRegularFile(fallback) ? fallback : null;
        } catch (Exception e) { return null; }
    }

    /**
     * Папка, в которой лежит файл: относительно неё берутся пути картинок и
     * md-ссылок из содержимого.
     */
    public static Path folderOf(KnowledgeFileMeta meta) {
        Path file = fileOf(meta);
        return file.getParent() != null ? file.getParent() : file;
    }

    /** Разобранный документ по relative path. */
    public static KnowledgeMarkdown.Document readDocument(String path) {
        KnowledgeFileMeta meta = metaOf(path);
        if (meta == null) return null;
        return readDocument(meta);
    }

    public static KnowledgeFileMeta metaOf(String path) {
        refresh();
        return metas.get(Path.of(path.replace('\\', '/')).normalize().toString().replace('\\','/'));
    }

    /** Список файлов с доступом: корень в порядке следования каталога. */
    public static List<KnowledgeFileMeta> visibleEntries() {
        refresh();
        List<KnowledgeFileMeta> result = new ArrayList<>();
        for (KnowledgeFileMeta meta : metas.values()) {
            if (hasAccess(meta)) result.add(meta);
        }
        return result;
    }

    /** Метаданные всех файлов (для rebuild интерфейса). */
    public static List<KnowledgeFileMeta> allEntries() {
        refresh();
        return List.copyOf(metas.values());
    }

    /** Оценка нагрузки скана (для отладки и проверок). */
    public static long lastScan() {
        return scannedAt;
    }
}
