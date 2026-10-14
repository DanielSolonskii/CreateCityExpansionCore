package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Запрос снимка территорий для карты City Pylon.
 *
 * <p><b>Зачем нужен запрос, если сервер и так шлёт снимок при открытии пилона.</b>
 * Одноразовая отправка ненадёжна: меню и сетевой пакет идут разными каналами,
 * и пакет может прийти раньше, чем клиент создаст экран. Тогда снимок
 * приходится буферизовать и надеяться, что экран появится с тем же пилоном.
 * Запрос снимает гонку полностью: экран уже существует, отправляет запрос и
 * получает ответ в гарантированном порядке.
 *
 * <p>Ответ — обычный {@link CityMapPeersPayload}. Одноразовая отправка при
 * открытии остаётся как быстрый первый кадр, а запрос — как надёжная основа:
 * если снимок не пришёл, карта переспрашивает его сама.
 *
 * <p>Ничего авторитетного в запросе нет: сервер отвечает тем же фильтром и
 * лимитами, что и при открытии, и игрок не может выбрать, какие города увидеть.
 */
public record RequestCityTerritoriesPayload(
        BlockPos pylonPos, BlockPos center, int range
) implements CustomPacketPayload {
    public RequestCityTerritoriesPayload(BlockPos pylonPos){this(pylonPos,pylonPos,512);}
    public RequestCityTerritoriesPayload {range=Math.clamp(range,128,32768);}

    public static final Type<RequestCityTerritoriesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "request_city_territories"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCityTerritoriesPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());BlockPos.STREAM_CODEC.encode(buffer,payload.center());buffer.writeVarInt(payload.range());},
                    buffer -> new RequestCityTerritoriesPayload(BlockPos.STREAM_CODEC.decode(buffer),BlockPos.STREAM_CODEC.decode(buffer),buffer.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
