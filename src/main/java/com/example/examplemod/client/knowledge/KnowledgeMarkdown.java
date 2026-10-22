package com.example.examplemod.client.knowledge;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Лёгкий разбор Markdown без сторонних библиотек.
 *
 * <p>Мод не тянет markdown-движок: формат читается по-своему и намеренно
 * ограничен тем, что реально нужно справочнику — заголовки, списки, цитаты,
 * код и базовое выделение. Цвет и насыщенность линии решает рендерер экрана,
 * здесь остаётся только структура и inline-стили.
 */
public final class KnowledgeMarkdown {

    public enum Kind { H1, H2, H3, TEXT, BULLET, NUMBERED, CODE, QUOTE, RULE, BLANK }

    /**
     * marker — то, что рисуется слева от текста и подсвечивается акцентом
     * ("•" у маркированного списка, "3." у нумерованного). У последующих
     * строк переноса marker = null: они продолжают блок с отступом.
     */
    public record Block(Kind kind, Component text, String marker) {}

    private static final int ACCENT = 0xD3A363;

    private KnowledgeMarkdown() {}

    public static List<Block> parse(String source) {
        List<Block> blocks = new ArrayList<>();
        boolean code = false;
        for (String raw : source.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = raw.replace('\u00A7', '&');
            if (line.strip().startsWith("```")) {
                code = !code;
                continue;
            }
            if (code) {
                blocks.add(new Block(Kind.CODE, Component.literal(line), null));
                continue;
            }

            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                // Пустые строки — только разделитель: подряд идущие не складываются.
                if (!blocks.isEmpty() && blocks.get(blocks.size() - 1).kind() != Kind.BLANK) {
                    blocks.add(new Block(Kind.BLANK, Component.empty(), null));
                }
                continue;
            }
            if (trimmed.matches("-{3,}|\\*{3,}|_{3,}")) {
                blocks.add(new Block(Kind.RULE, Component.empty(), null));
                continue;
            }

            int level = headingLevel(trimmed);
            if (level > 0) {
                Component body = inline(trimmed.substring(level).stripLeading());
                Kind kind = level == 1 ? Kind.H1 : level == 2 ? Kind.H2 : Kind.H3;
                MutableComponent text = Component.empty();
                text.append(body);
                text.withStyle(style -> style.withBold(true));
                blocks.add(new Block(kind, text, null));
                continue;
            }

            int bullet = bulletMarker(trimmed);
            if (bullet > 0) {
                blocks.add(new Block(Kind.BULLET, inline(trimmed.substring(bullet)), "\u2022"));
                continue;
            }

            int ordered = orderedMarker(trimmed);
            if (ordered > 0) {
                blocks.add(new Block(Kind.NUMBERED, inline(trimmed.substring(ordered + 1)),
                        trimmed.substring(0, ordered)));
                continue;
            }

            if (trimmed.startsWith(">")) {
                MutableComponent text = inline(trimmed.substring(1).stripLeading());
                text.withStyle(style -> style.withItalic(true));
                blocks.add(new Block(Kind.QUOTE, text, null));
                continue;
            }

            blocks.add(new Block(Kind.TEXT, inline(line), null));
        }
        // Заметка не должна начинаться и заканчиваться пустотой.
        while (!blocks.isEmpty() && blocks.get(blocks.size() - 1).kind() == Kind.BLANK) {
            blocks.remove(blocks.size() - 1);
        }
        return blocks;
    }

    private static int headingLevel(String trimmed) {
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6 || level == trimmed.length()) {
            return 0;
        }
        return Character.isWhitespace(trimmed.charAt(level)) ? level : 0;
    }

    private static int bulletMarker(String trimmed) {
        if (trimmed.length() < 2) {
            return 0;
        }
        char first = trimmed.charAt(0);
        return (first == '-' || first == '*' || first == '+') && Character.isWhitespace(trimmed.charAt(1)) ? 2 : 0;
    }

    /** Возвращает длину маркера вида "12." — сколько символов съедает номер. */
    private static int orderedMarker(String trimmed) {
        int digits = 0;
        while (digits < trimmed.length() && Character.isDigit(trimmed.charAt(digits))) {
            digits++;
        }
        if (digits == 0 || digits + 1 >= trimmed.length()) {
            return 0;
        }
        char dot = trimmed.charAt(digits);
        if ((dot != '.' && dot != ')') || !Character.isWhitespace(trimmed.charAt(digits + 1))) {
            return 0;
        }
        return digits + 1;
    }

    /**
     * **жирь**, *курсив* и `код` превращаются в стили Component. Остальной
     * текст остаётся обычным и наследует цвет строки от рендерера.
     */
    private static MutableComponent inline(String source) {
        MutableComponent out = Component.empty();
        StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("**", index)) {
                int end = source.indexOf("**", index + 2);
                if (end >= 0) {
                    flush(plain, out);
                    out.append(Component.literal(source.substring(index + 2, end))
                            .withStyle(style -> style.withBold(true)));
                    index = end + 2;
                    continue;
                }
            }
            char current = source.charAt(index);
            if (current == '`') {
                int end = source.indexOf('`', index + 1);
                if (end >= 0) {
                    flush(plain, out);
                    out.append(Component.literal(source.substring(index + 1, end))
                            .withStyle(style -> style.withColor(ACCENT)));
                    index = end + 1;
                    continue;
                }
            }
            if (current == '*') {
                int end = source.indexOf('*', index + 1);
                if (end > index + 1) {
                    flush(plain, out);
                    out.append(Component.literal(source.substring(index + 1, end))
                            .withStyle(style -> style.withItalic(true)));
                    index = end + 1;
                    continue;
                }
            }
            plain.append(current);
            index++;
        }
        flush(plain, out);
        return out;
    }

    private static void flush(StringBuilder plain, MutableComponent out) {
        if (plain.length() > 0) {
            out.append(Component.literal(plain.toString()));
            plain.setLength(0);
        }
    }
}
