package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Список соседних городов для отрисовки на карте пилона.
 *
 * <p>Носит ТОЛЬКО визуальные сведения: имя, позицию пилона и
 * размер территории. Никаких прав, участников, экономики или
 * проектов. Поэтому отображать чужой город на карте безопасно —
 * это чистая видимость, а не доступ к управлению.
 *
 * <p>Передаётся один раз при открытии пилона и обновляется редко,
 * а не каждый кадр: панель карты просто хранит список.
 */
public record CityMapPeersPayload(
        BlockPos pylonPos,
        ResourceLocation dimensionId,
        List<Peer> peers
) implements CustomPacketPayload {

    /**
     * Один соседний город в виде, достаточном для карты.
     *
     * @param name           отображаемое название города
     * @param pylonX         X пилона соседа
     * @param pylonZ         Z пилона соседа
     * @param territorySize  половина территории (как у пилона владельца)
     * @param own            true, если это город самого игрока
     */
    public record Peer(
            UUID cityId,
            String name,
            int pylonX,
            int pylonZ,
            int territorySize,
            boolean own,
            boolean master
    ) {
    }

    /** Жёсткий лимит: на карте физически не может быть видно больше. */
    public static final int MAX_PEERS = 16;

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
                            buffer.writeVarInt(peer.territorySize());
                            buffer.writeBoolean(peer.own());
                            buffer.writeBoolean(peer.master());
                        }
                    },
                    buffer -> {
                        BlockPos pylonPos = BlockPos.STREAM_CODEC.decode(buffer);
                        ResourceLocation dimensionId =
                                ResourceLocation.STREAM_CODEC.decode(buffer);
                        int size = buffer.readVarInt();
                        List<Peer> peers = new ArrayList<>(Math.min(size, MAX_PEERS));
                        for (int i = 0; i < size; i++) {
                            peers.add(new Peer(
                                    buffer.readUUID(),
                                    buffer.readUtf(48),
                                    buffer.readVarInt(),
                                    buffer.readVarInt(),
                                    buffer.readVarInt(),
                                    buffer.readBoolean(),
                                    buffer.readBoolean()
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
