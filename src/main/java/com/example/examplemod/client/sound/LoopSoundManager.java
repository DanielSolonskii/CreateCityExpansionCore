package com.example.examplemod.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Менеджер зацикленных звуков.
 *
 * <p>Держит не более одного {@link PositionalLoopSound} на источник
 * и на тип. Это исключает дубликаты: если петля уже играет — новый
 * экземпляр не создаётся, а если источник исчез — петля удаляется.
 *
 * <p>Дополнительно ограничено число одновременных петель, чтобы
 * десяток City Pylon рядом не превращался в звуковую стену.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = com.example.examplemod.CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class LoopSoundManager {

    /**
     * Максимум одновременных ambient-петель.
     * При 10 рядом стоящих пилонах лишние просто не стартуют,
     * а не суммируют громкость.
     */
    private static final int MAX_PARALLEL_LOOPS = 4;

    /**
     * Ключ: позиция источника. Значение: петля.
     * Один источник — одна петля, независимо от события.
     */
    private static final Map<BlockPos, PositionalLoopSound> LOOPS = new HashMap<>();

    /**
     * Позиции, для которых петля уже отказывала из-за лимита.
     * Не даёт каждый кадр снова создавать и сразу отбрасывать экземпляр.
     */
    private static final Map<BlockPos, Long> REJECTED = new HashMap<>();

    private static final long REJECT_MEMORY_TICKS = 200L;

    private LoopSoundManager() {
    }

    /**
     * Запустить петлю у источника, если такой ещё нет.
     * Вызывается из мест, где состояние меняется, а НЕ каждый тик.
     */
    public static void startLoop(
            SoundEvent event,
            BlockPos pos,
            float gain,
            float pitch,
            BooleanSupplier stillAlive
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (LOOPS.containsKey(pos)) {
            return;
        }
        if (LOOPS.size() >= MAX_PARALLEL_LOOPS) {
            REJECTED.put(pos.immutable(), minecraft.level.getGameTime());
            return;
        }
        SoundManager soundManager = minecraft.getSoundManager();
        if (soundManager == null) {
            return;
        }
        PositionalLoopSound loop = new PositionalLoopSound(
                event, pos, gain, 0.0F, pitch, stillAlive
        );
        LOOPS.put(pos.immutable(), loop);
        soundManager.play(loop);
    }

    /**
     * Остановить петлю у источника, если она играет.
     */
    public static void stopLoop(BlockPos pos) {
        PositionalLoopSound loop = LOOPS.remove(pos);
        if (loop == null) {
            return;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (soundManager != null) {
            soundManager.stop(loop);
        }
    }

    /**
     * Есть ли активная петля у источника.
     */
    public static boolean isLooping(BlockPos pos) {
        return LOOPS.containsKey(pos);
    }

    /**
     * Остановить все петли. Вызывается при выходе из мира и реконнекте,
     * чтобы не осталось ghost sounds.
     */
    public static void stopAll() {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        for (PositionalLoopSound loop : LOOPS.values()) {
            if (soundManager != null) {
                soundManager.stop(loop);
            }
        }
        LOOPS.clear();
        REJECTED.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (LOOPS.isEmpty() && REJECTED.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;

        /*
         * Убираем петли, которые сами решили остановиться
         * (блок разрушен, чанк выгружен, игрок отошёл).
         */
        Iterator<Map.Entry<BlockPos, PositionalLoopSound>> it = LOOPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, PositionalLoopSound> entry = it.next();
            PositionalLoopSound loop = entry.getValue();
            if (loop.isStopped()) {
                it.remove();
                continue;
            }
            if (level == null) {
                if (minecraft.getSoundManager() != null) {
                    minecraft.getSoundManager().stop(loop);
                }
                it.remove();
            }
        }

        /*
         * Чистим память об отказанных петлях, иначе карта растёт.
         */
        if (!REJECTED.isEmpty() && level != null) {
            long now = level.getGameTime();
            REJECTED.entrySet()
                    .removeIf(e -> now - e.getValue() > REJECT_MEMORY_TICKS);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
    }
}
