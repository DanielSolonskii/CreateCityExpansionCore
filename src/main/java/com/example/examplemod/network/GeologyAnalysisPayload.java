package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.geology.DepositQualityClass;
import com.example.examplemod.geology.DepositSizeClass;
import com.example.examplemod.geology.GeologySurvey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Результат замера магнитного анализатора.
 *
 * Координаты месторождения намеренно не передаются: в режиме
 * OUTSIDE прибор сообщает только ресурс, направление и
 * грубую дистанцию, в режиме INSIDE — локальный потенциал,
 * остаток запаса и класс размера. Игрок находит богатый
 * участок перемещением и сравнением показаний, а не по стрелке
 * к координатам ядра.
 */
public record GeologyAnalysisPayload(
        GeologySurvey.Mode mode,
        BlockPos origin,
        ResourceLocation resourceId,
        int direction,
        int distanceBlocks,
        int boundaryDistanceBlocks,
        int localPotentialPerHour,
        int peakPotentialPerHour,
        long remainingReserve,
        long initialReserve,
        DepositSizeClass sizeClass,
        DepositQualityClass qualityClass,
        int estimatedLifetimeHours,
        int remainingUses
) implements CustomPacketPayload {

    public static final Type<GeologyAnalysisPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "geology_analysis"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GeologyAnalysisPayload>
            STREAM_CODEC = StreamCodec.of(
                    GeologyAnalysisPayload::encode,
                    GeologyAnalysisPayload::decode
            );

    public GeologyAnalysisPayload {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sizeClass, "sizeClass");
        Objects.requireNonNull(qualityClass, "qualityClass");
        if (remainingUses < 0) {
            throw new IllegalArgumentException("remainingUses cannot be negative");
        }
    }

    public static GeologyAnalysisPayload of(GeologySurvey survey) {
        Objects.requireNonNull(survey, "survey");
        return new GeologyAnalysisPayload(
                survey.mode(),
                survey.origin(),
                survey.resourceId(),
                survey.direction(),
                survey.distanceBlocks(),
                survey.boundaryDistanceBlocks(),
                survey.localPotentialPerHour(),
                survey.peakPotentialPerHour(),
                survey.remainingReserve(),
                survey.initialReserve(),
                survey.sizeClass(),
                survey.qualityClass(),
                survey.estimatedLifetimeHours(),
                survey.remainingUses()
        );
    }

    public boolean hasResource() {
        return resourceId != null;
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            GeologyAnalysisPayload payload
    ) {
        buffer.writeEnum(payload.mode());
        BlockPos.STREAM_CODEC.encode(buffer, payload.origin());
        if (payload.hasResource()) {
            buffer.writeBoolean(true);
            buffer.writeResourceLocation(payload.resourceId());
        } else {
            buffer.writeBoolean(false);
        }
        buffer.writeVarInt(payload.direction());
        buffer.writeVarInt(payload.distanceBlocks());
        buffer.writeVarInt(payload.boundaryDistanceBlocks());
        buffer.writeVarInt(payload.localPotentialPerHour());
        buffer.writeVarInt(payload.peakPotentialPerHour());
        buffer.writeVarLong(payload.remainingReserve());
        buffer.writeVarLong(payload.initialReserve());
        buffer.writeEnum(payload.sizeClass());
        buffer.writeEnum(payload.qualityClass());
        buffer.writeVarInt(payload.estimatedLifetimeHours());
        buffer.writeVarInt(payload.remainingUses());
    }

    private static GeologyAnalysisPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        var mode = buffer.readEnum(GeologySurvey.Mode.class);
        BlockPos origin = BlockPos.STREAM_CODEC.decode(buffer);
        ResourceLocation resourceId = buffer.readBoolean()
                ? buffer.readResourceLocation()
                : null;
        return new GeologyAnalysisPayload(
                mode,
                origin,
                resourceId,
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readEnum(DepositSizeClass.class),
                buffer.readEnum(DepositQualityClass.class),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}