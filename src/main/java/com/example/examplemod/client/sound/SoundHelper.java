package com.example.examplemod.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Одноразовые звуки мода: позиционные world-звуки и локальный UI.
 *
 * <p>Также содержит rate limit. Без него поезд с тысячами предметов
 * превратил бы доставку в пулемёт: каждый предмет дёргал бы звук.
 * Частота ограничивается на источник, а не глобально, чтобы два
 * независимых склада не мешали друг другу.
 */
@OnlyIn(Dist.CLIENT)
public final class SoundHelper {

    /**
     * Минимальный интервал между щелчками доставки для одного источника.
     * 250 мс — примерно 4 щелчка в секунду максимум.
     */

    /**
     * Минимальный интервал между щелчками склада для одного источника.
     */

    /** Минимальный интервал между relay-щелчками пилона. */
    private static final long PYLON_RELAY_INTERVAL_MS = 2400L;

    private static final Map<BlockPos, Long> PYLON_RELAY_LAST = new HashMap<>();

    private SoundHelper() {
    }

    /*
     * =========================================
     * ПОЗИЦИОННЫЕ ЗВУКИ
     * =========================================
     */

    /**
     * Проиграть позиционный звук в мире.
     *
     * @param volume множитель поверх тихого микса из sounds.json
     */
    public static void playAt(SoundEvent event, BlockPos pos, float volume, float pitch) {
        Level level = Minecraft.getInstance().level;
        if (level == null || event == null) {
            return;
        }
        if (!level.isLoaded(pos)) {
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

    public static void playAt(SoundEvent event, BlockPos pos) {
        playAt(event, pos, 1.0F, 1.0F);
    }

    /**
     * Проиграть локальный, не позиционный звук.
     * Используется для GUI и для редких системных событий.
     */
    public static void playLocal(SoundEvent event, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event == null || minecraft.getSoundManager() == null) {
            return;
        }
        minecraft.getSoundManager().play(
                new SimpleSoundInstance(
                        event.getLocation(),
                        SoundSource.MASTER,
                        volume,
                        pitch,
                        net.minecraft.util.RandomSource.create(0L),
                        false,
                        0,
                        net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                        0.0D,
                        0.0D,
                        0.0D,
                        false
                )
        );
    }

    public static void playUi(SoundEvent event) {
        if (event == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null) {
            return;
        }
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(event, 1.0F)
        );
    }

    /*
     * =========================================
     * RATE LIMIT
     * =========================================
     */

    /**
     * Проиграть щелчок доставки, если для этого источника
     * прошло достаточно времени.
     *
     * @return true, если звук был проигран
     */

    /**
     * Аналогично для склада.
     *
     * @return true, если звук был проигран
     */

    /**
     * Редкий щелчок реле пилона.
     *
     * @return true, если звук был проигран
     */
    public static boolean playPylonRelay(SoundEvent event, BlockPos pos) {
        if (!acquire(PYLON_RELAY_LAST, pos, PYLON_RELAY_INTERVAL_MS)) {
            return false;
        }
        playAt(event, pos, 1.0F, 1.0F);
        return true;
    }

    private static boolean acquire(Map<BlockPos, Long> store, BlockPos pos, long intervalMs) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = store.get(pos);
        if (last != null && now - last < intervalMs) {
            return false;
        }
        store.put(pos.immutable(), now);

        /*
         * Не даём картам расти бесконечно: чистим всё, что давно не звучало.
         */
        if (store.size() > 64) {
            long cutoff = now - 30_000L;
            store.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        return true;
    }

    /**
     * Сбросить rate limit. Вызывается при выходе из мира.
     */
    public static void clearRateLimits() {

        PYLON_RELAY_LAST.clear();
    }
}
