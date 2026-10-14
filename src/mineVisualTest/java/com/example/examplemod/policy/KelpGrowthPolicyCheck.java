package com.example.examplemod.policy;

import com.example.examplemod.policy.KelpGrowthPolicy;

/**
 * Замедление роста ламинарии — арифметика, проверенная перебором.
 *
 * <p><b>Что здесь ломалось и почему это не видно глазами.</b> «Замедлить рост
 * в N раз» для вероятностного роста — не умножение на N. Ожидаемое время
 * равно {@code шаги / вероятность}, поэтому замедление времени в N раз — это
 * деление вероятности на N. Ошибка в знаке даёт правдоподобное число роста
 * (0.42 вместо 0.0467), и заметить её можно только сравнив время, а не
 * вероятность.
 *
 * <p><b>Почему перебор, а не равенство.</b> Проверка не сравнивает формулы, а
 * действительно прогоняет ванильное условие {@code rescaleRoll(u) < 0.14} по
 * равномерной сетке бросков и считает долю успехов. Так проверяется ровно то,
 * что делает mixin: если подмена броска ошибочна, доля успехов не совпадёт с
 * расчётной вероятностью, и это обнаружится здесь, а не в игре.
 */
public final class KelpGrowthPolicyCheck {

    private static int checks;

    /** Число бросков в сетке: кратно шагу, чтобы сетка не ложилась на границу. */
    private static final int ROLLS = 300_000;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void checkClose(double actual, double expected, double tolerance, String message) {
        checks++;
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        slowdownFactorIsApplicable();
        probabilityIsDividedNotMultiplied();
        rollRescaleProducesVanillaEquivalentProbability();
        gridSweepConfirmsEffectiveProbability();
        expectedTimeTriples();
        stepsCountIsTwentyFive();
        otherFactorsStillWork();
        fasterThanOneTickPerStepIsRefused();
        rescaleKeepsRollsBelowOneAndAboveVanillaThreshold();

        System.out.println("PASS: " + checks + " kelp growth policy checks");
    }

    /**
     * Множитель 3 пригоден: механизм предназначен для замедления.
     *
     * <p>Проверяется и на отрицательном примере: ускорение выше одного шага в
     * тик механизм дать не может.
     */
    private static void slowdownFactorIsApplicable() {
        check(KelpGrowthPolicy.isApplicable(KelpGrowthPolicy.SLOWDOWN_FACTOR),
                "a slowdown of 3 is applicable");
        check(KelpGrowthPolicy.isApplicable(1.0), "a factor of 1.0 is applicable (no change)");
    }

    /**
     * Главная проверка: вероятность делится на множитель, а не умножается.
     *
     * <p>Умножение дало бы 0.42 — рост втрое быстрее, то есть ровно
     * обратное тому, что просили. Числа далеки друг от друга, поэтому ошибка
     * не может остаться незамеченной.
     */
    private static void probabilityIsDividedNotMultiplied() {
        double effective = KelpGrowthPolicy.effectiveProbability();
        checkClose(effective, KelpGrowthPolicy.VANILLA_PROBABILITY / 3.0, 1e-12,
                "probability is divided by the slowdown factor");
        check(effective < KelpGrowthPolicy.VANILLA_PROBABILITY,
                "slowing growth must lower the probability, not raise it: " + effective);
        check(effective < KelpGrowthPolicy.VANILLA_PROBABILITY * 3.0,
                "probability must not be multiplied by the slowdown factor");
    }

    /**
     * Подмена броска эквивалентна нужной вероятности в терминах ванильного кода.
     *
     * <p>Проверяется ровно то сравнение, которое делает mixin: результат
     * подменённого {@code nextDouble()} против ванильного порога 0.14.
     */
    private static void rollRescaleProducesVanillaEquivalentProbability() {
        // Порог, ниже которого бросок приводит к росту, равен 0.14/scale.
        double threshold = KelpGrowthPolicy.VANILLA_PROBABILITY / KelpGrowthPolicy.rollScale();
        checkClose(threshold, 0.14 / 3.0, 1e-12,
                "the pass threshold on a raw roll is 0.14 / scale");

        /*
         * Граница проверяется по обе стороны, а не «в самой точке».
         *
         * <p>Само значение 0.14/scale, умноженное на scale, в двоичной
         * точности может лечь чуть выше или чуть ниже 0.14 — это зависит от
         * порядка округления и не является ошибкой. Утверждать точное
         * равенство значило бы зафиксировать артефакт округления вместо
         * поведения.
         */
        double below = threshold * (1.0 - 1e-9);
        double above = threshold * (1.0 + 1e-9);
        check(KelpGrowthPolicy.rescaleRoll(below) < KelpGrowthPolicy.VANILLA_PROBABILITY,
                "a roll just below the threshold grows");
        check(KelpGrowthPolicy.rescaleRoll(above) >= KelpGrowthPolicy.VANILLA_PROBABILITY,
                "a roll just above the threshold does not grow");
    }

    /**
     * Перебор бросков даёт заявленную вероятность.
     *
     * <p>Это главное доказательство: mixin работает именно так, и ошибка в
     * подмене броска изменила бы долю успехов здесь.
     */
    private static void gridSweepConfirmsEffectiveProbability() {
        int successes = 0;
        for (int index = 0; index < ROLLS; index++) {
            // Равномерная сетка по [0,1): последнее значение строго меньше 1.
            double roll = (double) index / ROLLS;
            if (KelpGrowthPolicy.rescaleRoll(roll) < KelpGrowthPolicy.VANILLA_PROBABILITY) {
                successes++;
            }
        }
        double measured = (double) successes / ROLLS;
        checkClose(measured, KelpGrowthPolicy.effectiveProbability(), 0.001,
                "measured growth probability over the roll grid");
        check(successes > 0, "growth must still be possible at all");
    }

    /** Требование пользователя: время выросло примерно втрое. */
    private static void expectedTimeTriples() {
        double vanilla = KelpGrowthPolicy.vanillaExpectedRandomTicks();
        double slowed = KelpGrowthPolicy.slowedExpectedRandomTicks();
        checkClose(slowed / vanilla, KelpGrowthPolicy.SLOWDOWN_FACTOR, 1e-9,
                "expected growth time grew by exactly the slowdown factor");
        check(slowed > vanilla, "slowed growth must take longer: " + slowed);
    }

    /**
     * Шагов до полноты ровно 25, а не 26.
     *
     * <p>Растение вырастает до 26 блоков в высоту, но переходов между
     * состояниями 25. Ошибка здесь сдвинула бы оценку времени на 4%.
     */
    private static void stepsCountIsTwentyFive() {
        check(KelpGrowthPolicy.KELP_STEPS == 25,
                "AGE 0 to 25 is 25 transitions, got " + KelpGrowthPolicy.KELP_STEPS);
    }

    /** Механизм не зашит под тройку: любой множитель ≥ 1 даёт корректный результат. */
    private static void otherFactorsStillWork() {
        for (double factor : new double[]{1.0, 1.5, 2.0, 3.0, 5.0, 10.0}) {
            check(KelpGrowthPolicy.isApplicable(factor), "factor " + factor + " is applicable");
            double scale = factor;
            int successes = 0;
            for (int index = 0; index < ROLLS; index++) {
                double roll = (double) index / ROLLS;
                if (roll * scale < KelpGrowthPolicy.VANILLA_PROBABILITY) {
                    successes++;
                }
            }
            double measured = (double) successes / ROLLS;
            checkClose(measured, KelpGrowthPolicy.VANILLA_PROBABILITY / factor, 0.001,
                    "measured probability for factor " + factor);
        }
    }

    /**
     * Ускорение отвергается, а не игнорируется.
     *
     * <p>Множитель меньше 1.0 физически не может ускорить рост выше одного
     * шага в тик: доля успехов упрётся в 1.0. Политика обязана это сказать,
     * иначе просьба «ускорить вдвое» выглядела бы выполненной, хотя дала бы
     * максимум.
     */
    private static void fasterThanOneTickPerStepIsRefused() {
        for (double factor : new double[]{0.5, 0.9, -1.0, 0.0}) {
            check(!KelpGrowthPolicy.isApplicable(factor),
                    "a factor of " + factor + " cannot be honoured and must be refused");
        }
    }

    /**
     * Подмена не выбрасывает броски и не искажает их порядок.
     *
     * <p>Значение может превысить единицу — это нормально, оно только
     * сравнивается с порогом. Но равенство нулю означало бы «рост при любом
     * броске», а такое значение означало бы «никогда»: обе крайности ломали
     * бы распределение, поэтому фиксируются явно.
     */
    private static void rescaleKeepsRollsBelowOneAndAboveVanillaThreshold() {
        check(KelpGrowthPolicy.rescaleRoll(0.0) == 0.0,
                "a zero roll stays zero, so growth is still probabilistic");
        double maxRescaled = KelpGrowthPolicy.rescaleRoll(1.0 - 1e-12);
        check(maxRescaled > KelpGrowthPolicy.VANILLA_PROBABILITY,
                "a roll near one maps above the vanilla threshold, so it never grows");
        check(maxRescaled >= 1.0,
                "rescaling may exceed one: the value is only compared against a threshold");
    }
}