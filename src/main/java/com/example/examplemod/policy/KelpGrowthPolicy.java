package com.example.examplemod.policy;

/**
 * Скорость роста ламинарии: как замедлить её в заданное число раз.
 *
 * <p><b>Почему это отдельная чистая политика.</b> Механика ванильной
 * ламинарии спрятана в {@code GrowingPlantHeadBlock.randomTick}, куда нет
 * ни события, ни хука: вероятность роста — это {@code private final} поле,
 * задаваемое только из конструктора. Править приходится mixin'ом, и вот
 * здесь легко ошибиться в арифметике так, что результат не проверить.
 * Всё считается здесь, без Minecraft, а mixin только применяет число.
 *
 * <h2>Как устроен рост в ванилле</h2>
 * <p>Рост происходит в random tick, и условие буквально такое:
 * <pre>{@code if (age < 25 && random.nextDouble() < growPerTickProbability) { ... }}</pre>
 * <p>{@code growPerTickProbability} у ламинарии равно {@code 0.14}, а
 * {@code AGE} идёт от 0 до 25 — то есть растение набирает полные 26 блоков
 * за 25 шагов.
 *
 * <h2>Главная ошибка при «замедлить в N раз»</h2>
 * <p>Здесь нет шага «раз в тик»: есть <b>вероятность за тик</b>. Ожидаемое
 * время равно {@code шаги / вероятность}, поэтому замедление времени в N раз
 * — это <b>деление</b> вероятности на N, а не умножение.
 * <p>Умножение дало бы N раз <i>быстрее</i>. Это ошибка, которую не видно
 * на глаз: и 0.14, и 0.42 — правдоподобные числа роста.
 *
 * <h2>Почему нельзя просто задать вероятность напрямую</h2>
 * <p>Поле {@code private final}, и {@code KelpBlock.randomTick} не переопределён
 * (рост живёт в родителе). Остаётся одно безопасное место — подменить
 * значение, которое сравнивается с порогом.
 * <p>Схема: вернуть из {@code nextDouble()} число {@code u * scale} вместо
 * {@code u}. Vanilla по-прежнему сравнивает результат с 0.14, то есть проверяет
 * {@code u * scale < 0.14}, что равносильно {@code u < 0.14 / scale}.
 * Итоговая вероятность — ровно {@code 0.14 / scale}.
 * <p>Распределение при этом остаётся равномерным и не смещается: умножение на
 * положительную константу сохраняет равномерность, а сравнение с порогом
 * даёт в точности нужную долю успехов.
 *
 * <h2>Ограничение механизма</h2>
 * <p>Так можно только <b>замедлять</b>. Ускорить выше одного шага в тик
 * нельзя в принципе: {@code nextDouble()} меньше единицы, и при
 * {@code scale < 1} доля успехов упрётся в 1.0. Политика это проверяет и
 * отказывает, а не молча даёт неверный результат.
 */
public final class KelpGrowthPolicy {

    /**
     * Ванильная вероятность роста за random tick.
     *
     * <p>Взята из вызова конструктора {@code KelpBlock}:
     * {@code super(properties, Direction.UP, SHAPE, true, 0.14)}.
     * Поле приватное иfinal, поэтому число менять нельзя — только то, с чем
     * его сравнивают.
     */
    public static final double VANILLA_PROBABILITY = 0.14;

    /**
     * Шагов до полного роста: {@code AGE} 0 → 25.
     *
     * <p>Именно 25, а не 26: 26 блоков в высоту, но переходов между
     * состояниями 25.
     */
    public static final int KELP_STEPS = 25;

    /** Требуемое замедление: рост занимает во столько раз больше времени. */
    public static final double SLOWDOWN_FACTOR = 3.0;

    private KelpGrowthPolicy() {
    }

    /**
     * Вероятность роста, которой закончим.
     *
     * <p>{@code 0.14 / 3 ≈ 0.0467}. Ожидаемое время растёт при этом
     * примерно с 179 до 536 random ticks.
     */
    public static double effectiveProbability() {
        return VANILLA_PROBABILITY / SLOWDOWN_FACTOR;
    }

    /**
     * Множитель для значения, отдаваемого {@code nextDouble()}.
     *
     * <p>Ровно {@link #SLOWDOWN_FACTOR}: ванильный порог 0.14 остаётся, и
     * сравнение {@code u * scale < 0.14} даёт вероятность
     * {@code 0.14 / scale}.
     */
    public static double rollScale() {
        return SLOWDOWN_FACTOR;
    }

    /**
     * Подменяет значение броска перед ванильным сравнением.
     *
     * @param roll значение {@code RandomSource.nextDouble()}, в {@code [0,1)}
     */
    public static double rescaleRoll(double roll) {
        return roll * rollScale();
    }

    /** Ожидаемое число random ticks до конца роста при заданной вероятности. */
    public static double expectedRandomTicks(double probability, int steps) {
        if (probability <= 0.0 || steps <= 0) {
            throw new IllegalArgumentException(
                    "probability=" + probability + " steps=" + steps);
        }
        return steps / probability;
    }

    /** Ожидаемое время ванильного роста — база для сравнения. */
    public static double vanillaExpectedRandomTicks() {
        return expectedRandomTicks(VANILLA_PROBABILITY, KELP_STEPS);
    }

    /** Ожидаемое время после замедления. */
    public static double slowedExpectedRandomTicks() {
        return expectedRandomTicks(effectiveProbability(), KELP_STEPS);
    }

    /**
     * Пригоден ли множитель.
     *
     * <p>Меньше единицы означал бы попытку ускорить рост выше одного шага в
     * тик. Механизм не может этого дать: доля успехов упрётся в 1.0 и рост
     * станет «каждый тик» независимо от числа. Отказ честнее, чем молчаливое
     * игнорирование просьбы.
     */
    public static boolean isApplicable(double slowdownFactor) {
        return slowdownFactor >= 1.0 && !Double.isInfinite(slowdownFactor);
    }
}