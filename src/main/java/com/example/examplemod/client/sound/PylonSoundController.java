package com.example.examplemod.client.sound;

import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Звук City Pylon.
 *
 * <p>Решение по петлям, принятое в пользу тишины:
 *
 * <ul>
 *   <li><b>idle_hum</b> — единственная постоянная петля, и только
 *       у активного пилона. Громкость минимальная (0.21 в sounds.json),
 *       петля сама гаснет за пределами ~5 блоков.</li>
 *   <li><b>mechanism</b> — постоянная петля НЕ используется.
 *       Вращение кольца и так читается визуально, а hum + mechanism
 *       вместе дают лишний шум у основания. Механизм проявляется
 *       только через редкий щелчок реле и звук активности.</li>
 *   <li><b>relay_tick</b> — редкий событийный щелчок, не чаще
 *       одного раза в ~2.4 с на пилон.</li>
 * </ul>
 *
 * <p>Никаких сетевых пакетов: всё выводится из уже существующего
 * состояния BlockEntity.
 */
@OnlyIn(Dist.CLIENT)
public final class PylonSoundController {

    /** Громкость петли гулкого idle поверх тихого микса. */
    private static final float HUM_GAIN = 0.55F;

    /** Тон гулкой петли: чуть ниже середины, чтобы не «пищала». */
    private static final float HUM_PITCH = 0.85F;

    /**
     * Период щелчка реле в тиках. 2.4 с — редко и заметно,
     * но не превращается в поток.
     */
    private static final long RELAY_PERIOD_TICKS = 48L;

    /**
     * Множитель громкости щелчка реле.
     */
    private static final float RELAY_GAIN = 0.7F;

    /**
     * Время последнего щелчка по позиции.
     * Нужен, чтобы щелчок шёл раз в период, а не каждый кадр.
     */
    private static long lastRelayTick = Long.MIN_VALUE;

    private PylonSoundController() {
    }

    /**
     * Обновляет звуковое состояние пилона.
     *
     * <p>Вызывается из BlockEntityRenderer, то есть только когда
     * пилон реально отрисовывается (в пределах дистанции рендера).
     * Специального тика для звука не требуется.
     *
     * @param pos    позиция пилона
     * @param active активен ли пилон (существующий флаг BlockEntity)
     * @param time   игровое время с дробной частью
     */
    public static void update(BlockPos pos, boolean active, double time) {

        if (!active) {
            // Пилон не активен: петля не нужна.
            LoopSoundManager.stopLoop(pos);
            return;
        }

        /*
         * Постоянный тихий гул. Менеджер гарантирует одну петлю
         * на позицию, поэтому повторный вызов каждый кадр безопасен.
         */
        if (!LoopSoundManager.isLooping(pos)) {
            LoopSoundManager.startLoop(
                    ModSounds.PYLON_IDLE_HUM.get(),
                    pos,
                    HUM_GAIN,
                    HUM_PITCH,
                    () -> isPylonStillActive(pos)
            );
        }

        /*
         * Редкий щелчок реле. Период считается от gameTime,
         * поэтому у соседних пилонов щелчки не синхронны.
         */
        long tick = (long) time;
        long since = tick - lastRelayTick;
        if (lastRelayTick != Long.MIN_VALUE && since < RELAY_PERIOD_TICKS) {
            return;
        }
        if (lastRelayTick == Long.MIN_VALUE || since < 0) {
            lastRelayTick = tick;
            return;
        }
        lastRelayTick = tick;
        SoundHelper.playAt(
                ModSounds.PYLON_RELAY_TICK.get(),
                pos,
                RELAY_GAIN,
                1.0F
        );
    }

    /**
     * Проверка «пилон всё ещё на месте и активен».
     * Петля сама себя гасит, когда это перестаёт быть правдой.
     */
    private static boolean isPylonStillActive(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(CityPylonBlockHolder.BLOCK)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof com.example.examplemod.block.CityPylonBlockEntity be) {
            return be.isActive();
        }
        return false;
    }

    /**
     * Ленивый доступ к блоку, чтобы не тянуть тяжёлый импорт
     * в горячий путь отрисовки.
     */
    static final class CityPylonBlockHolder {
        static final net.minecraft.world.level.block.Block BLOCK =
                com.example.examplemod.CreateCityExpansionCore.CITY_PYLON.get();

        private CityPylonBlockHolder() {
        }
    }
}
