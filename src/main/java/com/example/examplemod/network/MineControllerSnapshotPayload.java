package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import com.example.examplemod.mine.IndustrialMineState;
import com.example.examplemod.mine.MineControllerSnapshot;
import com.example.examplemod.mine.MineElectricalState;
import com.example.examplemod.mine.MineOutputSnapshot;
import com.example.examplemod.mine.MinePowerSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record MineControllerSnapshotPayload(
        MineControllerSnapshot snapshot,
        Optional<String> railwayStationName,
        boolean railwayStationAvailable,
        boolean openScreen
) implements CustomPacketPayload {

    public static final Type<MineControllerSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "mine_controller_snapshot"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MineControllerSnapshotPayload>
            STREAM_CODEC = StreamCodec.of(
                    MineControllerSnapshotPayload::encode,
                    MineControllerSnapshotPayload::decode
            );

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            MineControllerSnapshotPayload payload
    ) {
        MineControllerSnapshot snapshot = payload.snapshot();
        BlockPos.STREAM_CODEC.encode(buffer, snapshot.controllerPos());
        buffer.writeResourceLocation(snapshot.dimensionId());
        buffer.writeEnum(snapshot.state());
        buffer.writeEnum(snapshot.electricalState());
        buffer.writeDouble(snapshot.machineProgress());
        buffer.writeDouble(snapshot.miningProgress());
        buffer.writeDouble(snapshot.richness());
        buffer.writeDouble(snapshot.productionPerMinute());
        buffer.writeVarLong(snapshot.outputPerCycle());
        buffer.writeVarInt(snapshot.cycleDurationTicks());
        buffer.writeBoolean(snapshot.structureValid());
        buffer.writeUtf(snapshot.structureMessage(), 512);
        buffer.writeBoolean(snapshot.geologyAvailable());
        buffer.writeBoolean(snapshot.power().connected());
        buffer.writeBoolean(snapshot.power().sufficient());
        buffer.writeDouble(snapshot.power().voltage());
        buffer.writeDouble(snapshot.power().current());
        buffer.writeDouble(snapshot.power().power());
        buffer.writeDouble(snapshot.power().requiredPower());
        buffer.writeBoolean(snapshot.output().available());
        buffer.writeVarInt(snapshot.output().itemCount());
        buffer.writeVarInt(snapshot.output().totalCapacity());
        buffer.writeBoolean(snapshot.region().isPresent());
        snapshot.region().ifPresent(region ->
                GeologySnapshotCodecs.encodeRegion(buffer, region)
        );
        buffer.writeBoolean(snapshot.selectedDepositId().isPresent());
        snapshot.selectedDepositId().ifPresent(buffer::writeResourceLocation);
        buffer.writeBoolean(snapshot.creatorId().isPresent());
        snapshot.creatorId().ifPresent(buffer::writeUUID);
        buffer.writeBoolean(snapshot.specialized());
        buffer.writeBoolean(snapshot.commissioned());
        buffer.writeBoolean(snapshot.cityId().isPresent());
        snapshot.cityId().ifPresent(buffer::writeUUID);
        buffer.writeBoolean(snapshot.projectId().isPresent());
        snapshot.projectId().ifPresent(buffer::writeUUID);
        buffer.writeLong(snapshot.requestedExtraction());
        buffer.writeLong(snapshot.actualExtraction());
        buffer.writeBoolean(snapshot.energyIntegrationPending());
        buffer.writeUtf(snapshot.actionMessage(), 1024);
        buffer.writeBoolean(payload.railwayStationName().isPresent());
        payload.railwayStationName().ifPresent(name -> buffer.writeUtf(name, 128));
        buffer.writeBoolean(payload.railwayStationAvailable());
        buffer.writeBoolean(payload.openScreen());
    }

    private static MineControllerSnapshotPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        BlockPos controllerPos = BlockPos.STREAM_CODEC.decode(buffer);
        ResourceLocation dimensionId = buffer.readResourceLocation();
        IndustrialMineState state = buffer.readEnum(IndustrialMineState.class);
        MineElectricalState electricalState = buffer.readEnum(MineElectricalState.class);
        double machineProgress = buffer.readDouble();
        double miningProgress = buffer.readDouble();
        double richness = buffer.readDouble();
        double productionPerMinute = buffer.readDouble();
        long outputPerCycle = buffer.readVarLong();
        int cycleDurationTicks = buffer.readVarInt();
        boolean structureValid = buffer.readBoolean();
        String structureMessage = buffer.readUtf(512);
        boolean geologyAvailable = buffer.readBoolean();
        MinePowerSnapshot power = new MinePowerSnapshot(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        MineOutputSnapshot output = new MineOutputSnapshot(
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
        Optional<GeologyRegionSnapshot> region = buffer.readBoolean()
                ? Optional.of(GeologySnapshotCodecs.decodeRegion(buffer))
                : Optional.empty();
        Optional<ResourceLocation> selectedDepositId = buffer.readBoolean()
                ? Optional.of(buffer.readResourceLocation())
                : Optional.empty();
        Optional<java.util.UUID> creatorId = buffer.readBoolean()
                ? Optional.of(buffer.readUUID()) : Optional.empty();
        boolean specialized = buffer.readBoolean();
        boolean commissioned = buffer.readBoolean();
        Optional<java.util.UUID> cityId = buffer.readBoolean()
                ? Optional.of(buffer.readUUID()) : Optional.empty();
        Optional<java.util.UUID> projectId = buffer.readBoolean()
                ? Optional.of(buffer.readUUID()) : Optional.empty();
        long requestedExtraction = buffer.readLong();
        long actualExtraction = buffer.readLong();
        boolean energyIntegrationPending = buffer.readBoolean();
        String actionMessage = buffer.readUtf(1024);
        Optional<String> railwayStationName = buffer.readBoolean()
                ? Optional.of(buffer.readUtf(128)) : Optional.empty();
        boolean railwayStationAvailable = buffer.readBoolean();

        MineControllerSnapshot snapshot =
                new MineControllerSnapshot(
                        controllerPos,
                        dimensionId,
                        state,
                        electricalState,
                        machineProgress,
                        miningProgress,
                        richness,
                        productionPerMinute,
                        outputPerCycle,
                        cycleDurationTicks,
                        structureValid,
                        structureMessage,
                        geologyAvailable,
                        power,
                        output,
                        region,
                        selectedDepositId,
                        creatorId,
                        specialized,
                        commissioned,
                        cityId,
                        projectId,
                        requestedExtraction,
                        actualExtraction,
                        energyIntegrationPending,
                        actionMessage
                );
        return new MineControllerSnapshotPayload(
                snapshot, railwayStationName, railwayStationAvailable, buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
