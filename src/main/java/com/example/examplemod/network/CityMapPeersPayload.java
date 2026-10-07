package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityColors;
import com.example.examplemod.city.CityPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Снимок территорий городов для карты City Pylon.
 *
 * <p>Носит ТОЛЬКО визуальные сведения: идентичность, имя, цвет,
 * позицию пилона и реальные заявленные участки 50×50. Никаких прав,
 * участников, экономики или проектов. Поэтому отображать чужой город
 * на карте безопасно — это чистая видимость, а не доступ к
 * управлению.
 *
 * <p>Снимок формирует сервер из {@code CityManager}, клиент кеширует
 * его целиком и рисует по кешу каждый кадр: карта не обращается к
 * миру и не обходит города в цикле отрисовки. Пакет пересылается при
 * открытии пилона и после каждого изменения — claim участка или смены
 * цвета, — поэтому карта обновляется без закрытия и повторного
 * открытия пилона.
 *
 * <p><b>Почему участки, а не размер.</b> Раньше здесь был
 * {@code territorySize}, и карта рисовала квадрат вокруг пилона. Так
 * территория на карте расходилась с реальной землёй города, а цвет
 * города нельзя было показать вообще. Теперь рисуется ровно то, что
 * сервер считает территорией.
 */
public record CityMapPeersPayload(
        BlockPos pylonPos,
        ResourceLocation dimensionId,
        List<Peer> peers
) implements CustomPacketPayload {

    /**
     * Один город в виде, достаточном для карты.
     *
     * @param name     отображаемое название города
     * @param pylonX   X пилона города
     * @param pylonZ   Z пилона города
     * @param colorRgb собственный цвет города, {@code 0xRRGGBB}
     * @param plots    реально заявленные участки 50×50 этого города
     * @param residents число участников города; нужно для подсказки при наведении
     * @param own      true, если это город самого игрока
     */
    public record Peer(
            UUID cityId,
            String name,
            int pylonX,
            int pylonZ,
            int colorRgb,
            Set<CityPlot> plots,
            int residents,
            boolean own,
            boolean master
    ) {
        public Peer {
            plots = Collections.unmodifiableSet(new LinkedHashSet<>(plots));
            // Отрицательное значение невозможно, но из сети приходит любое
            // число, а отрицательный счётчик в подсказке выглядел бы как
            // ошибка данных.
            residents = Math.max(0, residents);
        }
    }

    /** Жёсткий лимит: на карте физически не может быть видно больше. */
    public static final int MAX_PEERS = 16;

    /**
     * Ограничение на участки одного города в пакете.
     *
     * <p>Страховка на случай испорченного или злонамеренного состояния
     * города: без неё один город мог бы прислать мегабайты координат.
     * 4096 участков — это 200×200 участков, то есть 10 000×10 000
     * блоков, чего с большим запасом хватает любой реальной территории.
     */
    public static final int MAX_PLOTS_PER_PEER = 4096;

    public static final Type<CityMapPeersPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "city_map_peers"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CityMapPeersPayload>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
                        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId());
                        buffer.writeVarInt(payload.peers().size());
                        for (Peer peer : payload.peers()) {
                            buffer.writeUUID(peer.cityId());
                            buffer.writeUtf(peer.name(), 48);
                            buffer.writeVarInt(peer.pylonX());
                            buffer.writeVarInt(peer.pylonZ());
                            buffer.writeVarInt(peer.colorRgb());
                            buffer.writeVarInt(peer.residents());
                            buffer.writeVarInt(peer.plots().size());
                            for (CityPlot plot : peer.plots()) {
                                /*
                                 * Измерение у всех участков одно — это
                                 * измерение пакета, — поэтому в провод
                                 * оно не пишется: только две координаты.
                                 */
                                buffer.writeVarInt(plot.plotX());
                                buffer.writeVarInt(plot.plotZ());
                            }
                            buffer.writeBoolean(peer.own());
                            buffer.writeBoolean(peer.master());
                        }
                    },
                    buffer -> {
                        BlockPos pylonPos = BlockPos.STREAM_CODEC.decode(buffer);
                        ResourceLocation dimensionId =
                                ResourceLocation.STREAM_CODEC.decode(buffer);
                        int size = Math.min(MAX_PEERS, Math.max(0, buffer.readVarInt()));
                        List<Peer> peers = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            UUID cityId = buffer.readUUID();
                            String name = buffer.readUtf(48);
                            int pylonX = buffer.readVarInt();
                            int pylonZ = buffer.readVarInt();
                            int colorRgb = buffer.readVarInt();
                            int residents = buffer.readVarInt();
                            int plotCount = Math.min(
                                    MAX_PLOTS_PER_PEER, Math.max(0, buffer.readVarInt()));
                            Set<CityPlot> plots = new LinkedHashSet<>(Math.min(plotCount, 64));
                            for (int p = 0; p < plotCount; p++) {
                                int plotX = buffer.readVarInt();
                                int plotZ = buffer.readVarInt();
                                CityPlot plot = new CityPlot(dimensionId, plotX, plotZ);
                                if (plot.isValid()) {
                                    plots.add(plot);
                                }
                            }
                            peers.add(new Peer(
                                    cityId, name, pylonX, pylonZ,
                                    CityColors.normalize(colorRgb), plots, residents,
                                    buffer.readBoolean(), buffer.readBoolean()
                            ));
                        }
                        return new CityMapPeersPayload(
                                pylonPos, dimensionId, peers
                        );
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
