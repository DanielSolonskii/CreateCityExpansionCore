package com.example.examplemod.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Сопоставление файла и записи доступа.
 *
 * <p>В playerdata хранится строка имён через {@code ;}. Админ вводит имя так, как
 * его видит — {@code test}, {@code test.md} или {@code Папка/файл.md}, поэтому
 * совпадение разрешения ведётся по всему относительному пути, по имени файла и по
 * имени без расширения; это покрывает все варианты записи без отдельной формы
 * «выбрать файл».
 */
public final class KnowledgeAccess {
    public static final String SEPARATOR = ";";

    private KnowledgeAccess() {
    }

    /** Разбирает строку из playerdata в чистый список имён. */
    public static List<String> parse(String joined) {
        if (joined == null || joined.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String raw : joined.split(";")) {
            String name = raw.replace('\\', '/').trim();
            if (!name.isEmpty() && !result.contains(name)) result.add(name);
        }
        return result;
    }

    /** Склеивает список обратно в строку playerdata. */
    public static String join(List<String> names) {
        StringBuilder joined = new StringBuilder();
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            if (joined.length() > 0) joined.append(SEPARATOR);
            joined.append(name.replace(';', '-'));
        }
        return joined.toString();
    }

    /**
     * Есть ли у файла доступ по одной записи. Любой из четырёх вариантов написания
     * распознаётся: путь, путь без .md, имя файла, имя без .md.
     */
    public static boolean matches(String path, String granted) {
        String p = path.replace('\\', '/');
        String g = granted.replace('\\', '/').trim();
        if (g.isEmpty()) return false;
        if (p.equals(g) || p.equals(g + ".md")) return true;
        String base = baseName(p);
        String gBase = baseName(g);
        if (gBase.isEmpty()) return false;
        return base.equals(g) // granted = короткое имя
                || base.equals(gBase) // granted = короткое имя без расширения
                || withoutExt(base).equals(g) // granted = имя без .md
                || withoutExt(base).equals(gBase);
    }

    /** Доступен ли файл по полному списку разрешений. */
    public static boolean hasAccess(String path, List<String> granted) {
        return hasAccess(new KnowledgeFileMeta(path, null, null, null, null), granted);
    }

    /** То же для разобранных метаданных (клиентский ход). */
    public static boolean hasAccess(KnowledgeFileMeta meta, List<String> granted) {
        for (String name : granted) {
            if (matches(meta.path(), name)) return true;
        }
        return false;
    }

    public static boolean hasAccess(List<String> granted, String path) {
        return hasAccess(path, granted);
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String withoutExt(String name) {
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }
}
