package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Запрос занять участок 50×50, отправленный с карты City Pylon.
 *
 * <p><b>Это запрос, а не команда.</b> Клиент не присылает город и не
 * присылает результат: только позицию пилона, у которой он стоит,
 * измерение и координаты участка. Сервер сам находит город по пилону,
 * проверяет права, занятость и смежность, и отвечает результатом.
 * Модифицированный клиент не может ни занять чужой участок, ни
 * расширить территорию не через тот город, рядом с которым стоит.
 *
 * <p>Измерение передаётся явно, потому что карта показывает города
 * своего измерения, а пилон мог быть перенесён в другое измерение
 * раньше, чем игрок открыл это меню.
 */
public record ClaimCityPlotPayload(
        BlockPos pylonPos,
        ResourceLocation dimension,
        int plotX,
        int plotZ
) implements CustomPacketPayload {

    public static final Type<ClaimCityPlotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "claim_city_plot"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimCityPlotPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
                        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimension());
                        buffer.writeVarInt(payload.plotX());
                        buffer.writeVarInt(payload.plotZ());
                    },
                    buffer -> new ClaimCityPlotPayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            ResourceLocation.STREAM_CODEC.decode(buffer),
                            buffer.readVarInt(),
                            buffer.readVarInt()
                    )
            );

    /**
     * Участок из запроса.
     *
     * <p>Создаётся здесь, на сервере, из пришедших чисел, и перед
     * любым использованием проходит {@link CityPlot#isValid()} —
     * координаты приходят от клиента и им нельзя доверять.
     */
    public CityPlot plot() {
        return new CityPlot(dimension, plotX, plotZ);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
