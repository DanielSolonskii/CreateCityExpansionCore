package com.example.examplemod.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Проигрывание позиционных звуков из server-side gameplay-кода.
 *
 * <p>Класс общий (не client-only) специально: значимые игровые
 * события происходят на сервере, и {@code Level#playSound} сам
 * раздаёт звук всем nearby-клиентам. Так не требуется ни одного
 * сетевого пакета ради звука.
 *
 * <p>Класс-специфичные петли и UI-звуки живут отдельно, в
 * {@code com.example.examplemod.client.sound} (строго под Dist.CLIENT),
 * чтобы dedicated server никогда их не загружал.
 *
 * <p>Громкость не повышается: sounds.json уже содержит тихий микс,
 * здесь только множитель поверх него.
 */
public final class WorldSoundHelper {

    /**
     * Минимальный интервал между щелчками потока для одного источника.
     * 250 мс = максимум 4 щелчка в секунду на один Intake / Gateway.
     *
     * <p>Без этого ограничения поезд с тысячами предметов
     * превратил бы доставку в пулемёт: recordFlow вызывается
     * на каждый перемещённый стек.
     */
    private static final long FLOW_TICK_INTERVAL_MS = 250L;

    private static final java.util.Map<BlockPos, Long> LAST_FLOW_TICK =
            new java.util.concurrent.ConcurrentHashMap<>();

    private WorldSoundHelper() {
    }

    /**
     * Проиграть щелчок потока, если для источника прошло достаточно времени.
     *
     * <p>Ограничение действует на источник, а не глобально: два
     * независимых склада не мешают друг другу.
     *
     * @return true, если звук был проигран
     */
    public static boolean playFlowTick(
            Level level,
            BlockPos pos,
            SoundEvent event,
            float volume
    ) {
        if (level == null || pos == null || event == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_FLOW_TICK.get(pos);
        if (last != null && now - last < FLOW_TICK_INTERVAL_MS) {
            return false;
        }
        LAST_FLOW_TICK.put(pos.immutable(), now);

        // Не даём карте расти бесконечно.
        if (LAST_FLOW_TICK.size() > 256) {
            long cutoff = now - 30_000L;
            LAST_FLOW_TICK.entrySet().removeIf(e -> e.getValue() < cutoff);
        }

        playAt(level, pos, event, volume, 1.0F);
        return true;
    }

    /**
     * Сбросить ограничители. Вызывается при выгрузке уровня.
     */
    public static void clearFlowLimits() {
        LAST_FLOW_TICK.clear();
    }

    /**
     * Проиграть позиционный звук в указанной точке.
     *
     * @param level  уровень, из которого раздаётся звук
     * @param pos    позиция источника
     * @param event  звуковое событие
     * @param volume множитель поверх тихого микса
     * @param pitch  высота тона
     */
    public static void playAt(
            Level level,
            BlockPos pos,
            SoundEvent event,
            float volume,
            float pitch
    ) {
        if (level == null || event == null || pos == null) {
            return;
        }
        level.playSound(
                null,
                pos,
                event,
                SoundSource.BLOCKS,
                volume,
                pitch
        );
    }

    public static void playAt(Level level, BlockPos pos, SoundEvent event) {
        playAt(level, pos, event, 1.0F, 1.0F);
    }
}
