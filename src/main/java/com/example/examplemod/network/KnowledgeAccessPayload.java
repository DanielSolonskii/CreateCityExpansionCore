package com.example.examplemod.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Список знаний игрока, отправленный с сервера.
 *
 * <p>Источником списка является playerdata игрока (поле {@code ;}-раздельных имён
 * в PlayerPersisted-области). Клиентский ход по папке даёт список файлов, а этот
 * payload — кто их видит; интерфейс показывает пересечение. Строка — та же, что в
 * playerdata, поэтому сервер и клиент всегда согласованы по формату.
 */
public record KnowledgeAccessPayload(String files, boolean first, boolean last) implements CustomPacketPayload {
    public KnowledgeAccessPayload(String files) { this(files, true, true); }
    public static final Type<KnowledgeAccessPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "knowledge_access"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeAccessPayload> STREAM_CODEC = StreamCodec.of(
            (b, payload) -> { b.writeUtf(payload.files(), 8192); b.writeBoolean(payload.first()); b.writeBoolean(payload.last()); },
            b -> new KnowledgeAccessPayload(b.readUtf(8192), b.readBoolean(), b.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
