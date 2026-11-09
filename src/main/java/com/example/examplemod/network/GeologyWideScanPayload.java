package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.geology.GeologicalDeposit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Результат широкого спектра магнитного анализатора.
 *
 * <p>В отличие от {@link GeologyAnalysisPayload} (прицельный
 * замер, координаты намеренно не раскрываются) широкий спектр —
 * это именно обзор: сервер отправляет контуры всех залежей в
 * радиусе сканирования, и клиент рисует их на глобальной карте.
 *
 * <p>Контур — вытянутый эллипс залежи ({@code DepositShape})
 * без краевого шума: на масштабе карты шум нечитаем, а радиусы
 * и поворот уже дают игроку адекватный абрис.
 *
 * <p>{@code foundTotal} и {@code truncated} описывают полный
 * список находок: если число залежей превысило предел пейлоада,
 * клиент покажет «N из M», а не молча потеряет данные.
 */
public record GeologyWideScanPayload(
        ResourceLocation dimension,
        BlockPos origin,
        int foundTotal,
        boolean truncated,
        List<Contour> contours
) implements CustomPacketPayload {

    /**
     * Один контур на карте. Центр, радиусы и поворот — прямо
     * из DepositShape залежи; resourceId нужен для подписей
     * в будущих итерациях интерфейса карты.
     */
    public record Contour(
            int centerX,
            int centerZ,
            int radiusX,
            int radiusZ,
            float rotationRadians,
            ResourceLocation resourceId
    ) {
    }

    public static final Type<GeologyWideScanPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "geology_wide_scan"
            )
    );

    private static final int MAX_CONTOURS = 256;

    public GeologyWideScanPayload {
        contours = List.copyOf(contours);
    }

    /**
     * Сборка пейлоада из находок {@code queryNearby}.
     *
     * @param level      серверное измерение сканирования
     * @param origin     позиция игрока (центр скана)
     * @param deposits   все залежи широкого спектра, без обрезки
     * @param maxContours предел контуров в пакете (контрактный тест
     *                   сверяет обрезку с тем же значением, поэтому
     *                   оно приходит снаружи)
     */
    public static GeologyWideScanPayload of(
            ServerLevel level,
            BlockPos origin,
            List<GeologicalDeposit> deposits,
            int maxContours
    ) {
        List<Contour> contours = new ArrayList<>();
        for (GeologicalDeposit deposit : deposits) {
            if (contours.size() >= maxContours) {
                break;
            }
            var shape = deposit.getShape();
            contours.add(new Contour(
                    shape.centerX(),
                    shape.centerZ(),
                    shape.radiusX(),
                    shape.radiusZ(),
                    (float) shape.rotationRadians(),
                    deposit.getResourceId()
            ));
        }
        return new GeologyWideScanPayload(
                level.dimension().location(),
                origin,
                deposits.size(),
                deposits.size() > contours.size(),
                contours
        );
    }

    public static final StreamCodec<FriendlyByteBuf, GeologyWideScanPayload> STREAM_CODEC =
            StreamCodec.of(
                    GeologyWideScanPayload::write,
                    GeologyWideScanPayload::read
            );

    private static void write(
            FriendlyByteBuf buffer,
            GeologyWideScanPayload payload
    ) {
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimension());
        BlockPos.STREAM_CODEC.encode(buffer, payload.origin());
        buffer.writeVarInt(payload.foundTotal());
        buffer.writeBoolean(payload.truncated());
        buffer.writeVarInt(payload.contours().size());
        for (Contour contour : payload.contours()) {
            buffer.writeVarInt(contour.centerX());
            buffer.writeVarInt(contour.centerZ());
            buffer.writeVarInt(contour.radiusX());
            buffer.writeVarInt(contour.radiusZ());
            buffer.writeFloat(contour.rotationRadians());
            ResourceLocation.STREAM_CODEC.encode(buffer, contour.resourceId());
        }
    }

    private static GeologyWideScanPayload read(FriendlyByteBuf buffer) {
        ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buffer);
        BlockPos origin = BlockPos.STREAM_CODEC.decode(buffer);
        int foundTotal = buffer.readVarInt();
        boolean truncated = buffer.readBoolean();
        int count = buffer.readVarInt();
        List<Contour> contours = new ArrayList<>(Math.min(count, 4096));
        for (int index = 0; index < count; index++) {
            int centerX = buffer.readVarInt();
            int centerZ = buffer.readVarInt();
            int radiusX = buffer.readVarInt();
            int radiusZ = buffer.readVarInt();
            float rotation = buffer.readFloat();
            ResourceLocation resourceId = ResourceLocation.STREAM_CODEC.decode(buffer);
            contours.add(new Contour(
                    centerX,
                    centerZ,
                    radiusX,
                    radiusZ,
                    rotation,
                    resourceId
            ));
        }
        Objects.requireNonNull(dimension, "dimension");
        return new GeologyWideScanPayload(
                dimension,
                origin,
                foundTotal,
                truncated,
                contours
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
