package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Запрос карты города по клавише — без пилона.
 *
 * <p><b>Почему в запросе нет координат.</b> Все прочие запросы несут
 * {@code pylonPos}, потому что сервер сверяет его с открытым контейнером и с
 * блоком пилона. Здесь игрок может быть где угодно, и координаты в запросе
 * были бы не проверяемым вводом: он указал бы чужой город, и сервер обязан был
 * бы либо поверить (утечка), либо отказать (запрос бесполезен).
 * Поэтому город определяется на сервере — по тому, участником какого города
 * является игрок.
 *
 * <p><b>Что именно разрешается.</b> Только чтение. Ни один параметр запроса не
 * меняет состояние, и ответ не содержит ничего, что позволило бы что-то
 * изменить: право на изменение проверяется отдельно, в обработчиках команд, и
 * там по-прежнему требуется открытое меню пилона.
 *
 * <p><b>Пустой payload.</b> Это осознанно: любое поле здесь было бы либо
 * лишним, либо небезопасным. Всё, что нужно серверу, он знает сам.
 */
public record RequestCityWorldMapPayload() implements CustomPacketPayload {

    public static final Type<RequestCityWorldMapPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "request_city_world_map"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCityWorldMapPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        // Полей нет: пустой запрос не пишет ничего.
                    },
                    buffer -> new RequestCityWorldMapPayload()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}