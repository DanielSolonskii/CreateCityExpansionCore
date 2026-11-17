package com.example.examplemod.item;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Спектр замера магнитного анализатора.
 *
 * Два режима одним предметом:
 *
 *  NARROW — «узкий спектр». Историческое поведение прибора:
 *  прицельный замер по позиции оператора, сообщающий ресурс,
 *  направление и грубую дистанцию до ближайшей аномалии.
 *
 *  WIDE — «широкий спектр». Обзорный замер: прибор
 *  вычленяет контуры всех залежей в радиусе 1000 блоков
 *  и рисует их на глобальной карте игрока. Прицельная
 *  логика не меняется — это отдельный режим работы.
 *
 * Режим хранится в предмете как data component: переживает
 * выкладывание, перекладывание и перезапуск игры, а его codec
 * синхронизирован в сеть, поэтому тултип показывает спектр
 * без дополнительного пейлоада.
 */
public enum MagneticAnalyzerMode implements StringRepresentable {
    NARROW,
    WIDE;

    public static final MagneticAnalyzerMode[] VALUES = values();

    /** Диапазон переключения Shift+ПКМ: от WIDE назад в NARROW. */
    public MagneticAnalyzerMode next() {
        return this == NARROW ? WIDE : NARROW;
    }

    /** Человекочитаемое название спектра для тултипа и action bar. */
    public Component label() {
        return Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer.spectrum."
                        + getSerializedName()
        );
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Сохранение в предмет: сериализация в NBT по серийному имени. */
    public static final Codec<MagneticAnalyzerMode> CODEC =
            StringRepresentable.fromEnum(MagneticAnalyzerMode::values);

    /** Синхронизация в сеть: компактный индекс вместо строки. */
    public static final StreamCodec<ByteBuf, MagneticAnalyzerMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(
                    index -> VALUES[index],
                    MagneticAnalyzerMode::ordinal
            );
}
