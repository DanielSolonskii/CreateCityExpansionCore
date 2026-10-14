package com.example.examplemod.mixin;

import com.example.examplemod.policy.KelpGrowthPolicy;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Замедление роста ламинарии в {@link KelpGrowthPolicy#SLOWDOWN_FACTOR} раз.
 *
 * <p><b>Почему mixin, а не хук.</b> Проверено на NeoForge 21.1.251: события
 * на random tick блока не существует. Есть {@code BlockGrowFeatureEvent},
 * но он про grow-features (грибы, деревья), а не про растения, и в
 * {@code GrowingPlantHeadBlock.randomTick} вообще не входит. Вероятность
 * роста — {@code private final} поле, задаваемое только из конструктора, а
 * {@code KelpBlock.randomTick} не переопределён: рост живёт в родителе
 * {@code GrowingPlantHeadBlock}. Заменить ванильный блок в реестре тоже нельзя
 * — это ломает миры и рецепты. Остаётся единственное безопасное место:
 * значение, с которым сравнивается бросок.
 *
 * <p><b>Что именно подменяется.</b> Ванильное условие:
 * <pre>{@code age < 25 && random.nextDouble() < growPerTickProbability}</pre>
 * Здесь подменяется только результат {@code nextDouble()} на {@code u * scale}.
 * Сравнение с порогом 0.14 остаётся нетронутым, то есть проверяется уже
 * {@code u < 0.14 / scale} — ровно нужная вероятность. Логика роста, условие
 * {@code age < 25}, проверка воды сверху и установка блока остаются
 * ванильными: дублировать их здесь было бы источником расхождений при
 * обновлении игры.
 *
 * <p><b>Почему сравнение именно такое.</b> Ванильный порог 0.14 неприступен:
 * он приватный и final. А вот число, с которым его сравнивают, — нет.
 *
 * <p><b>Область действия.</b> Только {@code Blocks.KELP}. Тело растения
 * ({@code KELP_PLANT}) — это {@code GrowingPlantBlock}, у которого нет
 * random tick вообще, и {@code tick} у него только удаляет неживучее
 * ({@code if (!state.canSurvive(...)) destroyBlock}). Поэтому верхушка
 * растения — единственное место, где вообще происходит рост, и править
 * больше нечего.
 */
@Mixin(GrowingPlantHeadBlock.class)
public class KelpGrowthMixin {

    @Redirect(
            method = "randomTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextDouble()D")
    )
    private double ccec$applyKelpSlowdown(RandomSource random) {
        /*
         * Ветка для всех остальных подтипов обязательна: класс открытый, и
         * мод-содержимое вполне может добавить своё растение с такой же
         * механикой роста. Молча замедлил бы чужое растение.
         *
         * Сравнение идёт через (Object): на этапе компиляции этот класс не
         * является подклассом цели — он лишь в неё вклеивается на runtime.
         * Прямое `this != Blocks.KELP` здесь даже не компилируется.
         */
        if (((Object) this) != Blocks.KELP) {
            return random.nextDouble();
        }
        return KelpGrowthPolicy.rescaleRoll(random.nextDouble());
    }
}