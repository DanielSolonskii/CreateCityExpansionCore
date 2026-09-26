package com.example.examplemod.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.BooleanSupplier;

/**
 * Тихий зацикленный позиционный звук.
 *
 * <p>Используется только для ambient-петель City Pylon и Industrial Mine.
 * Каждый кадр экземпляр только проверяет условия и крутит фазу —
 * новых объектов и аллокаций в цикле не создаётся.
 *
 * <p>Экземпляр сам останавливается, если:
 * <ul>
 *   <li>пропал уровень или блок-владелец;</li>
 *   <li>игрок вышел за пределы слышимости;</li>
 *   <li>источник отключился (условие существования).</li>
 * </ul>
 * Это исключает «ghost sounds» после разрушения блока, выгрузки
 * чанка, смены измерения или выхода из мира.
 */
@OnlyIn(Dist.CLIENT)
public class PositionalLoopSound extends AbstractTickableSoundInstance {

    /** Комфортная слышимость. Дальше звук практически не слышен. */
    private static final double COMFORTABLE_RADIUS = 5.0D;

    private final BlockPos pos;

    /**
     * Проверка «источник всё ещё существует». Вызывается каждый тик.
     */
    private final BooleanSupplier stillAlive;

    /** Множитель громкости поверх тихого микса из sounds.json. */
    private final float gain;

    /** Скорость изменения громкости, чтобы не было щелчков на границе. */
    private float currentGain;

    public PositionalLoopSound(
            SoundEvent event,
            BlockPos pos,
            float gain,
            float volume,
            float pitch,
            BooleanSupplier stillAlive
    ) {
        super(event, SoundSource.BLOCKS, RandomSource.create(0L));
        this.pos = pos.immutable();
        this.gain = gain;
        this.currentGain = 0.0F;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.pitch = pitch;
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
        this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR;
        this.stillAlive = stillAlive;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stop();
            return;
        }
        if (!stillAlive.getAsBoolean()) {
            stop();
            return;
        }
        if (!minecraft.level.isLoaded(pos)) {
            stop();
            return;
        }

        /*
         * Плавное затухание вместо резкого обрыва на границе слышимости.
         * Иначе игрок слышит щелчок, отходя от механизма.
         */
        double dx = x - minecraft.player.getX();
        double dy = y - minecraft.player.getY();
        double dz = z - minecraft.player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance > COMFORTABLE_RADIUS) {
            currentGain = Math.max(0.0F, currentGain - 0.08F);
        } else {
            currentGain = Math.min(1.0F, currentGain + 0.05F);
        }

        this.volume = Mth.clamp(gain * currentGain, 0.0F, 1.0F);

        if (currentGain <= 0.0F && distance > COMFORTABLE_RADIUS) {
            stop();
        }
    }

    /**
     * @return true, если петля ещё активна
     */
    public boolean isRunning() {
        return !isStopped();
    }

    public BlockPos pos() {
        return pos;
    }
}
