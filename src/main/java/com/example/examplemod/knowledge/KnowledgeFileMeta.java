package com.example.examplemod.knowledge;

import java.util.List;

/**
 * Метаданные одного файла базы знаний.
 *
 * <p>Передняя часть {@code .md} файла ограничена строками {@code ---} и может
 * задавать {@code title}, {@code icon}, {@code default} и {@code parent_item}.
 * Оба конца (сканирующий сервер и клиентский интерфейс) разбирают одинаковый
 * формат, поэтому парсер вынесен в общий код. Содержимое файла читается только
 * на клиенте — серверу нужны заголовочные поля: доступ в playerdata и
 * выборка {@code default: visible}.
 */
public record KnowledgeFileMeta(String path, String title, String icon, String visibility, String parentItem) {

    /**
     * Имя папки базы знаний в каталоге игры.
     *
     * <p>Двоеточие в имени папки запрещено Windows, поэтому вместо
     * {@code CCEC:knowledge base} папка называется {@code CCEC - knowledge base}.
     * Она создаётся автоматически рядом с профилем (game dir), владельцу папку
     * трогать руками не нужно.
     */
    public static final String FOLDER_NAME = "CCEC - knowledge base";

    public KnowledgeFileMeta {
        path = normalize(path);
    }

    /** Имя файла без каталогов и расширения, для отображения и сравнения доступа. */
    public static String displayName(String path) {
        String name = normalize(path);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.endsWith(".md")) name = name.substring(0, name.length() - 3);
        return name;
    }

    /** Заголовок интерфейса: поле {@code title} либо имя файла. */
    public String displayTitle() {
        return title == null || title.isBlank() ? displayName(path) : title;
    }

    /** Поле {@code default: visible} — файл выдаётся каждому входящему игроку. */
    public boolean visibleByDefault() {
        return visibility != null && "visible".equalsIgnoreCase(visibility.trim());
    }

    private static String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/').trim();
    }

    /**
     * Разбор передней части. Неизвестные игнорируются, значения без обфускации.
     * {@code path} задаётся внешним ходом каталога, поэтому приходит отдельным
     * аргументом.
     */
    public static KnowledgeFileMeta parse(String path, List<String> frontmatter) {
        String title = null;
        String icon = null;
        String def = null;
        String parent = null;
        for (String line : frontmatter) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "title" -> title = value;
                case "icon" -> icon = value;
                case "default" -> def = value;
                case "parent_item" -> parent = value;
                default -> {
                }
            }
        }
        return new KnowledgeFileMeta(path, title, icon, def, parent);
    }
}
