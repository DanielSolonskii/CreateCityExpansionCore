package com.example.examplemod.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Перенос текста подсказки под ширину окна.
 *
 * <p>Существует потому, что «текст не вылезает за рамку» невозможно обеспечить
 * вручную: длина названия города, адреса freight-точки или названия станции
 * зависит от данных, которые игрок вводит, а окно — фиксированный размер.
 * Любая жёстко вписанная строка рано или поздно окажется длиннее окна.
 *
 * <p>Ширина строки передаётся извне функцией измерения. Это делает класс
 * чистым: в игре измерение даёт {@code Font.width}, а в headless-проверке —
 * синтетическая моноширинная метрика, где «не влезает» воспроизводится
 * точно. Иначе правило переноса проверялось бы только глазами.
 *
 * <p>Перенос по словам, а по одному символу — только для слова длиннее строки
 * (например, неразрывной идентификатор). Строка результата никогда не
 * превышает {@code maxWidth}, если только один символ сам по себе шире
 * окна: тогда он остаётся целиком, иначе окно стало бы пустым.
 */
public final class CityMapTextLayout {

    private static final String ELLIPSIS = "…";

    private CityMapTextLayout() {
    }

    /** Ширина самого длинного слова: перенос по словам не может её превысить. */
    private static int longestWord(String text, ToIntFunction<String> measure) {
        int widest = 0;
        for (String word : text.split(" ")) {
            widest = Math.max(widest, measure.applyAsInt(word));
        }
        return widest;
    }

    /**
     * Переносит одну строку на несколько.
     *
     * @param maxWidth ширина окна в пикселях; неположительное значение означает «не переносить»
     */
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> measure) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }
        // Окно уже, чем самое длинное слово: перенос по словам бесполезен,
        // режем по символам.
        if (maxWidth <= 0 || measure.applyAsInt(text) <= maxWidth
                || longestWord(text, measure) > maxWidth) {
            return wrapByCharacter(text, maxWidth, measure);
        }

        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (measure.applyAsInt(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                result.add(line.toString());
                line.setLength(0);
            }
            // Слово длиннее строки уже исключено проверкой выше, поэтому
            // оно помещается целиком.
            line.append(word);
        }
        if (!line.isEmpty()) {
            result.add(line.toString());
        }
        return result.isEmpty() ? List.of("") : result;
    }

    /** Аварийный перенос по символам для слов длиннее окна. */
    private static List<String> wrapByCharacter(String text, int maxWidth, ToIntFunction<String> measure) {
        if (maxWidth <= 0 || measure.applyAsInt(text) <= maxWidth) {
            return List.of(text);
        }
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            String candidate = line.toString() + symbol;
            if (!line.isEmpty() && measure.applyAsInt(candidate) > maxWidth) {
                result.add(line.toString());
                line.setLength(0);
            }
            line.append(symbol);
        }
        if (!line.isEmpty()) {
            result.add(line.toString());
        }
        return result;
    }

    /** Переносит все строки под одну ширину, сохраняя их порядок. */
    public static List<String> wrapAll(List<String> lines, int maxWidth, ToIntFunction<String> measure) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.addAll(wrap(line, maxWidth, measure));
        }
        return result;
    }

    /** Ширина самой длинной перенесённой строки — ширина содержимого окна. */
    public static int widest(List<String> lines, ToIntFunction<String> measure) {
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, measure.applyAsInt(line));
        }
        return widest;
    }

    /**
     * Подгоняет набор строк под окно: переносит по ширине и обрезает по
     * высоте.
     *
     * <p>Обрезка по высоте нужна потому, что число строк после переноса
     * непредсказуемо, а окно — фиксированной высоты. Обрезанные строки
     * помечаются многоточием, чтобы игрок понимал, что текст не закончился.
     *
     * @param maxLines максимум строк в окне, включая последнюю с многоточием
     * @return строки, которые гарантированно влезают в окно
     */
    public static List<String> fit(
            List<String> lines,
            int maxWidth,
            int maxLines,
            ToIntFunction<String> measure
    ) {
        List<String> wrapped = wrapAll(lines, maxWidth, measure);
        if (maxLines <= 0 || wrapped.size() <= maxLines) {
            return wrapped;
        }
        List<String> result = new ArrayList<>(wrapped.subList(0, maxLines - 1));
        /*
         * Последняя строка помечается многоточием принудительно, даже если
         * она и так влезает по ширине: обрезана она по высоте, а
         * truncateWithEllipsis по ширине вернул бы её без изменений и игрок
         * решил бы, что текст закончился сам собой.
         *
         * Если строка уже обрезана по ширине, многоточие дописывается вместо
         * последнего символа, чтобы ширина не выросла.
         */
        result.add(markTruncated(wrapped.get(maxLines - 1), maxWidth, measure));
        return result;
    }

    /**
     * Помечает строку как обрезанную по высоте.
     *
     * <p>Отличается от {@link #truncateWithEllipsis} именно тем, что многоточие
     * ставится всегда: вызывающий код знает, что следующие строки потеряны.
     */
    private static String markTruncated(String text, int maxWidth, ToIntFunction<String> measure) {
        if (measure.applyAsInt(text) <= maxWidth) {
            if (measure.applyAsInt(text + ELLIPSIS) <= maxWidth) {
                return text + ELLIPSIS;
            }
            return truncateWithEllipsis(text, maxWidth, measure);
        }
        return truncateWithEllipsis(text, maxWidth, measure);
    }

    /** Обрезает строку по ширине, дописывая многоточие вместо отрезанного хвоста. */
    public static String truncateWithEllipsis(String text, int maxWidth, ToIntFunction<String> measure) {
        String ellipsis = ELLIPSIS;
        if (measure.applyAsInt(text) <= maxWidth) {
            return text;
        }
        int budget = maxWidth - measure.applyAsInt(ellipsis);
        if (budget <= 0) {
            return ellipsis;
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            String candidate = result.toString() + text.charAt(index);
            if (measure.applyAsInt(candidate) > budget) {
                break;
            }
            result.append(text.charAt(index));
        }
        return result + ellipsis;
    }
}
