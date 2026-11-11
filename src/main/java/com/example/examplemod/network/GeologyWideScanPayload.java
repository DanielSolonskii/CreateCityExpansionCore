package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.VeinFootprint;

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

    public record Contour(
            int centerX,
            int centerZ,
            int radiusX,
            int radiusZ,
            float rotationRadians,
            ResourceLocation resourceId,
            VeinFootprint footprint
    ) {
        public Contour(int centerX, int centerZ, int radiusX, int radiusZ,
                       float rotationRadians, ResourceLocation resourceId) {
            this(centerX, centerZ, radiusX, radiusZ, rotationRadians, resourceId, null);
        }
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
            if (contours.size() >= Math.min(MAX_CONTOURS, maxContours)) {
                break;
            }
            var shape = deposit.getShape();
            contours.add(new Contour(
                    shape.centerX(),
                    shape.centerZ(),
                    shape.radiusX(),
                    shape.radiusZ(),
                    (float) shape.rotationRadians(),
                    deposit.getResourceId(),
                    shape.footprint()
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
            buffer.writeBoolean(contour.footprint() != null);
            if (contour.footprint() != null) {
                buffer.writeVarInt(contour.footprint().originX());
                buffer.writeVarInt(contour.footprint().originZ());
                for (long word : contour.footprint().sampleWords()) {
                    buffer.writeLong(word);
                }
            }
        }
    }

    private static GeologyWideScanPayload read(FriendlyByteBuf buffer) {
        ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buffer);
        BlockPos origin = BlockPos.STREAM_CODEC.decode(buffer);
        int foundTotal = buffer.readVarInt();
        boolean truncated = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_CONTOURS) {
            throw new IllegalArgumentException("Invalid geology contour count: " + count);
        }
        List<Contour> contours = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int centerX = buffer.readVarInt();
            int centerZ = buffer.readVarInt();
            int radiusX = buffer.readVarInt();
            int radiusZ = buffer.readVarInt();
            float rotation = buffer.readFloat();
            ResourceLocation resourceId = ResourceLocation.STREAM_CODEC.decode(buffer);
            VeinFootprint footprint = null;
            if (buffer.readBoolean()) {
                int originX = buffer.readVarInt();
                int originZ = buffer.readVarInt();
                long[] samples = new long[VeinFootprint.WORD_COUNT];
                for (int word = 0; word < samples.length; word++) {
                    samples[word] = buffer.readLong();
                }
                footprint = new VeinFootprint(originX, originZ, samples);
            }
            contours.add(new Contour(
                    centerX,
                    centerZ,
                    radiusX,
                    radiusZ,
                    rotation,
                    resourceId,
                    footprint
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
