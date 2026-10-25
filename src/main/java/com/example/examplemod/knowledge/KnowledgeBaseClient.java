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
 * Клиентский кэш базы знаний: разбор файлов каталога и синхронизированный
 * доступ. Клиент читает файлы напрямую (они в его папке), доступ приходит
 * payload'ом с сервера; интерфейс показывает пересечение двух списков.
 *
 * <p>Скан переоценивается не чаще раза в секунду — трогать диск каждый кадр
 * недопустимо, а реакция на новые файлы в течение секунды достаточно быстрая для
 * UX.
 */
public final class KnowledgeBaseClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SCAN_INTERVAL_MILLIS = 1000;

    private static volatile List<String> allowed = List.of();
    private static volatile Map<String, KnowledgeFileMeta> metas = Map.of();
    private static volatile Map<String, KnowledgeFileMeta> byParentItem = Map.of();
    private static long scannedAt;
    private static long rescanAfter; // 0: первый вызов обязан отсканировать каталог

    private KnowledgeBaseClient() {
    }

    /** Payload с сервера: полный список имён из playerdata. */
    public static void acceptAccess(KnowledgeAccessPayload payload) {
        allowed = KnowledgeAccess.parse(payload.files());
        LOGGER.info("База знаний: сервер выдал {} записей доступа", allowed.size());
    }

    /** Папка базы: создаётся при первом касании интерфейса. */
    public static void ensureFolder() {
        KnowledgeStorage.ensureFolder();
    }

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now < rescanAfter) return;
        rescanAfter = now + SCAN_INTERVAL_MILLIS;
        Map<String, KnowledgeFileMeta> scanned = new LinkedHashMap<>();
        Map<String, KnowledgeFileMeta> parents = new HashMap<>();
        KnowledgeStorage.forEachMarkdown(path -> {
            KnowledgeFileMeta meta = KnowledgeFileMeta.parse(
                    path.rel(), KnowledgeStorage.firstLines(path.file(), 8));
            scanned.put(path.rel(), meta);
            if (meta.parentItem() != null && !meta.parentItem().isBlank()) {
                parents.putIfAbsent(meta.parentItem(), meta);
            }
        });
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
        return KnowledgeAccess.hasAccess(meta, allowed);
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
        return KnowledgeStorage.folder().resolve(meta.path());
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
        return metas.get(path.replace('\\', '/'));
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
