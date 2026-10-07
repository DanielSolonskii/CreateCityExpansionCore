package com.example.examplemod.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Перенос текста под ширину окна: подсказки и подтверждения участка.
 *
 * <p><b>Что здесь ломалось.</b> Подсказка на карте брала ширину как «самая
 * длинная строка» и рисовалась без переноса. Название города или станции
 * длиннее карты расширяло подсказку за её пределы, и кламп по левому краю
 * превращался в обрезку — игрок терял хвост названия. Теперь содержимое
 * переносится и ограничивается по высоте до отрисовки.
 *
 * <p><b>Почему метрика синтетическая.</b> Настоящий {@code Font} живёт в
 * клиенте и в headless-проверке недоступен, поэтому ширина задаётся функцией.
 * Здесь она моноширинная, 6 пикселей на символ: так «не влезает»
 * воспроизводится точно, и правило переноса проверяется правилом, а не глазами.
 */
public final class CityMapTextLayoutCheck {

    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    /** Моноширинная метрика: 6 пикселей на символ. */
    private static int width(String text) {
        return text.length() * 6;
    }

    private static final ToIntFunction<String> MEASURE = CityMapTextLayoutCheck::width;

    public static void main(String[] args) {
        shortTextUnchanged();
        longTextWrappedByWords();
        longWordBrokenByCharacter();
        neverExceedsWidth();
        fitKeepsMaxLines();
        fitNeverExceedsWidth();
        ellipsisShorterThanReplacedText();
        ellipsisOnAlreadyShortText();
        heightTruncationIsMarked();
        heightTruncationRespectsTightWidth();
        layoutInvariantsOverGeneratedContent();

        System.out.println("PASS: " + checks + " city map text layout checks");
    }

    /** Короткий текст не трогаем: перенос вносил бы правки без причины. */
    private static void shortTextUnchanged() {
        List<String> wrapped = CityMapTextLayout.wrap("Residents: 12", 200, MEASURE);
        check(wrapped.size() == 1, "short text stays on one line");
        check("Residents: 12".equals(wrapped.get(0)), "short text is not altered");
    }

    /**
     * Основной случай: длинное название города переносится по словам.
     *
     * <p>Раньше такая строка просто выходила за карту.
     */
    private static void longTextWrappedByWords() {
        String text = "Northern Junction Industrial Logistics Quarter";
        List<String> wrapped = CityMapTextLayout.wrap(text, 120, MEASURE);
        check(wrapped.size() > 1, "a long name is wrapped, not drawn past the edge");
        check(String.join(" ", wrapped).equals(text), "wrapping preserves every word and order");
    }

    /**
     * Слово длиннее строки режется по символам.
     *
     * <p>Название может оказаться неразрывным идентификатором: перенос по
     * словам для него невозможен, и без аварийного режима строка осталась бы
     * длиннее окна.
     */
    private static void longWordBrokenByCharacter() {
        String word = "A".repeat(50);
        List<String> wrapped = CityMapTextLayout.wrap(word, 60, MEASURE);
        check(wrapped.size() > 1, "a single oversized word is broken up");
        for (String line : wrapped) {
            check(width(line) <= 60, "every piece of an oversized word fits: " + line);
        }
        check(String.join("", wrapped).equals(word), "character breaking loses no characters");
    }

    /** Главный инвариант: ни одна строка не шире окна. */
    private static void neverExceedsWidth() {
        String[] samples = {
                "Residents: 128",
                "Claimed plots: 41",
                "Northern Junction Industrial Logistics Quarter of the Northern Provinces",
                "X: -1200  Z: 3400",
                "PLOT  -1 : 3",
                "BLOCKS  -50…-1  /  150…199",
                "W",
                "",
        };
        int[] widths = {40, 60, 90, 120, 200, 300, 12, 300};
        for (int index = 0; index < samples.length; index++) {
            List<String> wrapped = CityMapTextLayout.wrap(samples[index], widths[index], MEASURE);
            for (String line : wrapped) {
                check(width(line) <= widths[index],
                        "line \"" + line + "\" fits " + widths[index] + "px");
            }
        }
    }

    /** Ограничение по высоте: строк не больше заданного, лишние помечены многоточием. */
    private static void fitKeepsMaxLines() {
        List<String> source = List.of(
                "Northern Junction Industrial Logistics Quarter of the Northern Provinces",
                "Claimed plots: 412");
        List<String> fitted = CityMapTextLayout.fit(source, 120, 3, MEASURE);
        check(fitted.size() <= 3, "fit never returns more lines than allowed");
        check(fitted.get(fitted.size() - 1).endsWith("…"),
                "the last kept line is marked as truncated");
    }

    /** Перенос и обрезка вместе всё равно дают строки в пределах окна. */
    private static void fitNeverExceedsWidth() {
        List<String> source = List.of(
                "Northern Junction Industrial Logistics Quarter of the Northern Provinces",
                "BLOCKS  -1250…-1201  /  3400…3449",
                "Claimed plots: 412");
        for (int maxWidth : new int[]{60, 90, 140, 200, 280}) {
            List<String> fitted = CityMapTextLayout.fit(source, maxWidth, 5, MEASURE);
            for (String line : fitted) {
                check(width(line) <= maxWidth,
                        "fitted line \"" + line + "\" fits " + maxWidth + "px");
            }
        }
    }

    /**
     * Многоточие короче текста, который заменило.
     *
     * <p>Иначе строка с многоточием могла бы оказаться длиннее обычной строки
     * того же окна, то есть обрезка увеличивала бы ширину.
     */
    private static void ellipsisShorterThanReplacedText() {
        String long_ = "W".repeat(30);
        String truncated = CityMapTextLayout.truncateWithEllipsis(long_, 60, MEASURE);
        check(width(truncated) <= 60, "the truncated line fits the window");
        check(long_.startsWith(truncated.substring(0, truncated.length() - 1)),
                "truncation keeps the beginning of the text");
    }

    /** Короткий текст не получает многоточие: ничего не обрезано. */
    private static void ellipsisOnAlreadyShortText() {
        String text = "Residents: 3";
        check(text.equals(CityMapTextLayout.truncateWithEllipsis(text, 200, MEASURE)),
                "a fitting line is returned unchanged");
    }

    /**
     * Обрезка по высоте помечается всегда, даже когда строка помещается.
     *
     * <p>Главный случай: обрезанную по высоте строку нельзя отличить от
     * закончившейся самой собой — игрок решил бы, что название города
     * действительно такое короткое. Раньше этот случай не помечался вовсе.
     */
    private static void heightTruncationIsMarked() {
        // Текст заведомо переносится на две строки, а бюджет — одна.
        List<String> fitted = CityMapTextLayout.fit(
                List.of("AAAA BBBB CCCC DDDD EEEE FFFF"), 120, 1, MEASURE);
        check(fitted.size() == 1, "a single line budget keeps one line");
        check(fitted.get(0).endsWith("…"),
                "a line dropped for height is still marked: " + fitted.get(0));
        check(width(fitted.get(0)) <= 120, "the marked line still fits the width");
    }

    /**
     * Пометка не выходит за ширину, когда дописывать некуда.
     *
     * <p>Если к строке нельзя приписать многоточие, она обрезается по
     * ширине — иначе окно снова стало бы шире заданного.
     */
    private static void heightTruncationRespectsTightWidth() {
        for (int maxWidth : new int[]{20, 24, 36, 48, 60}) {
            List<String> fitted = CityMapTextLayout.fit(
                    List.of("AAAA BBBB CCCC DDDD"), maxWidth, 1, MEASURE);
            for (String line : fitted) {
                check(width(line) <= maxWidth,
                        "marked line \"" + line + "\" still fits " + maxWidth + "px");
            }
        }
    }

    /**
     * Инвариант на случайных данных.
     *
     * <p>Названия городов, адреса и координаты приходят от игроков, поэтому
     * перебор длин и текстов ищет нарушение правила там, где точечные примеры
     * его не заметили бы.
     */
    private static void layoutInvariantsOverGeneratedContent() {
        String[] words = {
                "Northern", "Junction", "aaa", "WWWWWWWWWWWWWWWWWWWW", "1", "…",
                "verylongunbrokencitynamewithoutanyspacesatall", "PLOT", "-1200",
        };
        int[] maxWidths = {24, 40, 64, 96, 160, 240};
        int[] maxLines = {1, 2, 3, 6};

        for (int maxWidth : maxWidths) {
            for (int maxLinesValue : maxLines) {
                for (String text : generateTexts(words, 200)) {
                    List<String> fitted = CityMapTextLayout.fit(
                            List.of(text), maxWidth, maxLinesValue, MEASURE);
                    check(fitted.size() <= maxLinesValue,
                            "line budget honoured for \"" + text + "\" at " + maxWidth);
                    for (String line : fitted) {
                        check(width(line) <= maxWidth,
                                "\"" + line + "\" fits " + maxWidth + "px, source \"" + text + "\"");
                    }
                }
            }
        }
    }

    /** Детерминированный набор текстов разной длины и состава. */
    private static List<String> generateTexts(String[] words, int count) {
        List<String> result = new ArrayList<>();
        int state = 12345;
        for (int index = 0; index < count; index++) {
            StringBuilder builder = new StringBuilder();
            int wordsInText = 1 + state % 6;
            for (int word = 0; word < wordsInText; word++) {
                state = state * 1103515245 + 12345;
                if (word > 0) {
                    builder.append(' ');
                }
                builder.append(words[Math.floorMod(state >> 8, words.length)]);
            }
            result.add(builder.toString());
        }
        result.add("");
        return result;
    }
}
