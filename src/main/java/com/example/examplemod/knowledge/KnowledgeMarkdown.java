package com.example.examplemod.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор подмножества Markdown, которое использует база знаний.
 *
 * <p>Поддержано ровно то, чем авторы файлов пишут материалы: заголовки {@code #},
 * обычные абзацы, списки {@code - }, картинки {@code ![alt](файл.png)} —
 * отдельными блоками, а также ссылки внутри текста:
 * {@code [текст](файл.md)} и {@code [текст](ponder:namespace:scene)}.
 * Рендер живёт в интерфейсе (клиент); этот класс — только данные, он одинаково
 * безопасен на выделенном сервере.
 */
public final class KnowledgeMarkdown {
    private static final Pattern INLINE_MARK = Pattern.compile("(!\\[[^]]*]\\([^)]+\\)|\\[[^]]*]\\([^)]+\\))");

    private KnowledgeMarkdown() {
    }

    /** Разобранный файл: метаданные передней части и блоки тела. */
    public record Document(KnowledgeFileMeta meta, List<Block> blocks) {
    }

    /** Один блок документа. */
    public sealed interface Block permits KnowledgeMarkdown.Heading, KnowledgeMarkdown.Paragraph, KnowledgeMarkdown.Image {
    }

    /** Заголовок. level — 1..3 ({@code #}, {@code ##}, {@code ###}). */
    public record Heading(int level, String text) implements Block {
    }

    /** Абзац или пункт списка; segment'ы содержат inline-ссылки. */
    public record Paragraph(List<Segment> segments, boolean bullet) implements Block {
    }

    /** Ссылка на файл или систему ponder. */
    public record Segment(String text, Link link) {
        public boolean isLink() {
            return link != null;
        }
    }

    /** Картинка: ссылка относительная (от папки с файлом), alt-текст и подпись. */
    public record Image(String alt, String file) implements Block {
    }

    /** Обёрнутая ссылка текущего сегмента. */
    public record Link(Type type, String target) {
        public static Link file(String target) {
            return new Link(Type.FILE, target);
        }

        public static Link ponder(String target) {
            return new Link(Type.PONDER, target);
        }

        public enum Type {
            FILE, PONDER
        }
    }

    /**
     * Разбирает исходный текст файла. Передняя часть ограничена строками
     * {@code ---} в начале файла; всё после закрытия — тело.
     */
    public static Document parse(String source) {
        List<String> front = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        collectFrontAndBody(source, front, body);
        KnowledgeFileMeta meta = KnowledgeFileMeta.parse("", front);
        return new Document(meta, parseBody(body.toString()));
    }

    /** То же, но meta уже известен (данные хода по каталогу). */
    public static Document parse(String source, KnowledgeFileMeta meta) {
        List<String> front = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        collectFrontAndBody(source, front, body);
        return new Document(meta, parseBody(body.toString()));
    }

    private static void collectFrontAndBody(String source, List<String> front, StringBuilder body) {
        if (source == null) return;
        List<String> lines = new ArrayList<>();
        for (String line : source.split("\n", -1)) lines.add(line.replace("\r", ""));
        int index = 0;
        if (!lines.isEmpty() && lines.getFirst().trim().equals("---")) {
            index = 1;
            while (index < lines.size()) {
                if (lines.get(index).trim().equals("---")) {
                    index++;
                    break;
                }
                front.add(lines.get(index));
                index++;
            }
        }
        for (; index < lines.size(); index++) {
            body.append(lines.get(index)).append('\n');
        }
    }

    private static List<Block> parseBody(String text) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String raw : text.split("\n")) {
            String line = raw.stripTrailing();
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                flushParagraph(blocks, paragraph, false);
                continue;
            }
            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                if (level <= trimmed.length() && (level == trimmed.length() || trimmed.charAt(level) == ' ')) {
                    flushParagraph(blocks, paragraph, false);
                    blocks.add(new Heading(level, stripInline(trimmed.substring(Math.min(level + 1, trimmed.length())))));
                    continue;
                }
                trimmed = stripInline(trimmed);
                paragraph.append(trimmed);
                continue;
            }
            if (trimmed.startsWith("- ")) {
                flushParagraph(blocks, paragraph, false);
                blocks.add(new Paragraph(parseInline(trimmed.substring(2)), true));
                continue;
            }
            String image = trimmed.startsWith("![") ? trimmed : null;
            if (image != null && image.matches("!\\[[^]]*]\\([^)]+\\)")) {
                flushParagraph(blocks, paragraph, false);
                Matcher matcher = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)").matcher(image);
                if (matcher.matches()) {
                    blocks.add(new Image(matcher.group(1), clean(matcher.group(2))));
                    continue;
                }
            }
            if (paragraph.length() > 0) paragraph.append(' ');
            paragraph.append(stripInline(trimmed));
        }
        flushParagraph(blocks, paragraph, false);
        return blocks;
    }

    /** Разные подряд идущие строки без пустой строки — один абзац. */
    private static void flushParagraph(List<Block> blocks, StringBuilder buffer, boolean bullet) {
        if (buffer.length() == 0) return;
        blocks.add(new Paragraph(parseInline(buffer.toString()), bullet));
        buffer.setLength(0);
    }

    /** Inline-сегменты: обычный текст, ссылка md, ссылка ponder. */
    public static List<Segment> parseInline(String text) {
        List<Segment> segments = new ArrayList<>();
        Matcher matcher = INLINE_MARK.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) segments.add(new Segment(stripInline(text.substring(last, matcher.start())), null));
            String plain = text.substring(matcher.start() + 1, matcher.end() - 1);
            String label = plain.substring(0, plain.lastIndexOf(']'));
            String target = clean(plain.substring(plain.lastIndexOf('(') + 1));
            if (label.isBlank()) label = target;
            if (target.startsWith("ponder:")) {
                segments.add(new Segment(label, Link.ponder(target.substring("ponder:".length()))));
            } else {
                segments.add(new Segment(label, Link.file(target)));
            }
            last = matcher.end();
        }
        if (last < text.length()) segments.add(new Segment(stripInline(text.substring(last)), null));
        return segments;
    }

    /** Убирает разметку выделения, которая не рисуется во вьюере. */
    public static String stripInline(String text) {
        if (text == null || text.isEmpty()) return "";
        String result = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        result = result.replaceAll("\\*(.+?)\\*", "$1");
        return result.replace("`", "").replace("__", "").replace("  ", " ");
    }

    /**
     * Чистит текст ссылки/пути: убирает пробелы по краям, чтобы пути в
     * {@link #parse} и в рендере совпадали.
     */
    static String clean(String path) {
        String result = path == null ? "" : path.strip();
        while (result.startsWith("(")) result = result.substring(1);
        while (result.endsWith(")")) result = result.substring(0, result.length() - 1);
        return result.strip();
    }

    /** Безопасный разбор: любая ошибка превращается в один абзац-заглушку. */
    public static Document safeParse(String source, KnowledgeFileMeta meta, Function<String, Paragraph> fallback) {
        try {
            return parse(source, meta);
        } catch (Exception e) {
            return new Document(meta, List.of(fallback.apply(meta.displayTitle())));
        }
    }
}
