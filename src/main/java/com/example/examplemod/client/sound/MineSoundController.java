package com.example.examplemod.client.sound;

import com.example.examplemod.mine.IndustrialMineState;
import com.example.examplemod.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Звук Industrial Mine.
 *
 * <p>Главное решение: <b>одна агрегированная петля на контроллер</b>,
 * а не петля на каждый Drill Unit. Иначе 16 буровых блоков дали бы
 * 16 одинаковых источников и звуковую стену.
 *
 * <p>Create Mechanical Drill уже издаёт собственный физический звук
 * вращения, поэтому модуль не дублирует его: наш drill_loop отражает
 * состояние промышленной машины целиком (контроллер + питание),
 * а не вращение каждого бура.
 *
 * <p>Переходы состояния обрабатываются по факту смены состояния,
 * а не каждый кадр, поэтому one-shot события не повторяются.
 */
@OnlyIn(Dist.CLIENT)
public final class MineSoundController {

    /** Громкость агрегированной петли бурения. */
    private static final float DRILL_LOOP_GAIN = 0.5F;

    /** Громкость петли нестабильного питания. */
    private static final float UNSTABLE_LOOP_GAIN = 0.45F;

    /** Предыдущее состояние по позиции контроллера. */
    private static final Map<BlockPos, IndustrialMineState> LAST_STATE = new HashMap<>();

    private MineSoundController() {
    }

    /**
     * Обработать новое состояние шахты.
     *
     * <p>Вызывается из GUI/снимка состояния, а не каждый тик.
     *
     * @param controllerPos позиция контроллера
     * @param state         реальное состояние IndustrialMineState
     */
    public static void onState(BlockPos controllerPos, IndustrialMineState state) {
        IndustrialMineState previous = LAST_STATE.put(controllerPos.immutable(), state);

        // Первый снимок не считаем переходом: нечего озвучивать.
        if (previous == null) {
            if (isRunning(state)) {
                startDrillLoop(controllerPos);
            }
            return;
        }
        if (previous == state) {
            return;
        }

        /*
         * Переходы питания. Одноразовые, чтобы не спамить.
         */
        if (!lowPower(previous) && lowPower(state)) {
            SoundHelper.playAt(ModSounds.MINE_POWER_UNSTABLE.get(), controllerPos, 0.8F, 1.0F);
        } else if (lowPower(previous) && !lowPower(state)) {
            SoundHelper.playAt(ModSounds.MINE_POWER_CONNECT.get(), controllerPos, 0.8F, 1.0F);
        }

        /*
         * Пуск и остановка.
         */
        if (!isRunning(previous) && isRunning(state)) {
            SoundHelper.playAt(ModSounds.MINE_DRILL_START.get(), controllerPos, 0.9F, 1.0F);
            startDrillLoop(controllerPos);
        } else if (isRunning(previous) && !isRunning(state)) {
            stopDrillLoop(controllerPos);
            SoundHelper.playAt(ModSounds.MINE_DRILL_STOP.get(), controllerPos, 0.9F, 1.0F);
        }

        /*
         * Аварийная остановка и нестабильное питание.
         */
        if (state == IndustrialMineState.OVERLOAD && previous != IndustrialMineState.OVERLOAD) {
            SoundHelper.playAt(ModSounds.MINE_EMERGENCY_STOP.get(), controllerPos, 1.0F, 1.0F);
            stopDrillLoop(controllerPos);
        }
        if (lowPower(state)) {
            startUnstableLoop(controllerPos);
        } else {
            stopUnstableLoop(controllerPos);
        }
    }

    /**
     * Одноразовый звук пуска контроллера. Вызывается при открытии GUI
     * шахты, чтобы было понятно, что интерфейс ожил.
     */
    public static void playControllerBoot(BlockPos controllerPos) {
        SoundHelper.playAt(ModSounds.MINE_CONTROLLER_BOOT.get(), controllerPos, 0.7F, 1.0F);
    }

    /**
     * Остановить все петли шахты. Вызывается при закрытии интерфейса
     * и при выходе из мира.
     */
    public static void stopAll(BlockPos controllerPos) {
        stopDrillLoop(controllerPos);
        stopUnstableLoop(controllerPos);
        LAST_STATE.remove(controllerPos);
    }

    /**
     * Остановить все петли шахт разом.
     */
    public static void stopAllMines() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        for (var key : LAST_STATE.keySet().toArray(new BlockPos[0])) {
            stopAll(key);
        }
    }

    private static boolean isRunning(IndustrialMineState state) {
        return state == IndustrialMineState.RUNNING
                || state == IndustrialMineState.LOW_POWER
                || state == IndustrialMineState.STARTING;
    }

    private static boolean lowPower(IndustrialMineState state) {
        return state == IndustrialMineState.LOW_POWER
                || state == IndustrialMineState.NO_POWER;
    }

    private static void startDrillLoop(BlockPos pos) {
        if (LoopSoundManager.isLooping(pos)) {
            return;
        }
        LoopSoundManager.startLoop(
                ModSounds.MINE_DRILL_LOOP.get(),
                pos,
                DRILL_LOOP_GAIN,
                0.8F,
                () -> stillController(pos)
        );
    }

    private static void stopDrillLoop(BlockPos pos) {
        LoopSoundManager.stopLoop(pos);
    }

    private static void startUnstableLoop(BlockPos pos) {
        // Одна петля на источник: если уже играет — не дублируем.
        if (LoopSoundManager.isLooping(pos)) {
            return;
        }
        LoopSoundManager.startLoop(
                ModSounds.MINE_POWER_UNSTABLE.get(),
                pos,
                UNSTABLE_LOOP_GAIN,
                1.0F,
                () -> stillController(pos)
        );
    }

    private static void stopUnstableLoop(BlockPos pos) {
        LoopSoundManager.stopLoop(pos);
    }

    private static boolean stillController(BlockPos pos) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(com.example.examplemod.CreateCityExpansionCore.MINE_CONTROLLER.get());
    }
}
