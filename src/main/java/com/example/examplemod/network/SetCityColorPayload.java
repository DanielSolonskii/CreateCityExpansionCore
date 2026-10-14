package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Запрос сменить собственный цвет города, отправленный из палитры City Pylon.
 *
 * <p>Клиент присылает только позицию пилона и {@code 0xRRGGBB}.
 * Сервер находит город по пилону, проверяет OWNER/ADMIN и то, что
 * цвет входит в палитру {@link com.example.examplemod.city.CityColors}:
 * произвольный цвет сохранять нельзя, иначе город мог бы получить
 * нечитаемую территорию, и палитра перестала бы быть источником
 * истины для цвета.
 */
public record SetCityColorPayload(
        BlockPos pylonPos,
        int colorRgb
) implements CustomPacketPayload {

    public static final Type<SetCityColorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "set_city_color"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCityColorPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
                        buffer.writeVarInt(payload.colorRgb());
                    },
                    buffer -> new SetCityColorPayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            buffer.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
