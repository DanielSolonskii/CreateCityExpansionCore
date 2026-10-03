package com.example.examplemod.network;

import com.example.examplemod.geology.DepositQualityClass;
import com.example.examplemod.geology.DepositSizeClass;
import com.example.examplemod.geology.DepositStage;
import com.example.examplemod.geology.GeologyDepositSnapshot;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

final class GeologySnapshotCodecs {

    private static final int MAX_DEPOSITS = 256;

    private GeologySnapshotCodecs() {
    }

    static void encodeRegion(
            RegistryFriendlyByteBuf buffer,
            GeologyRegionSnapshot region
    ) {
        buffer.writeResourceLocation(region.regionId());
        buffer.writeInt(region.regionX());
        buffer.writeInt(region.regionZ());
        buffer.writeVarInt(region.deposits().size());
        for (GeologyDepositSnapshot deposit : region.deposits()) {
            encodeDeposit(buffer, deposit);
        }
    }

    static GeologyRegionSnapshot decodeRegion(RegistryFriendlyByteBuf buffer) {
        var regionId = buffer.readResourceLocation();
        int regionX = buffer.readInt();
        int regionZ = buffer.readInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_DEPOSITS) {
            throw new IllegalArgumentException("Invalid geology deposit count: " + count);
        }

        List<GeologyDepositSnapshot> deposits = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            deposits.add(decodeDeposit(buffer));
        }
        return new GeologyRegionSnapshot(regionId, regionX, regionZ, deposits);
    }

    private static void encodeDeposit(
            RegistryFriendlyByteBuf buffer,
            GeologyDepositSnapshot deposit
    ) {
        buffer.writeResourceLocation(deposit.depositId());
        buffer.writeResourceLocation(deposit.resourceId());
        buffer.writeLong(deposit.initialReserve());
        buffer.writeLong(deposit.remainingReserve());
        buffer.writeEnum(deposit.stage());
        buffer.writeDouble(deposit.grade());
        buffer.writeInt(deposit.depth());
        buffer.writeDouble(deposit.hardness());
        buffer.writeDouble(deposit.sustainableExtractionRate());
        buffer.writeEnum(deposit.qualityClass());
        buffer.writeEnum(deposit.sizeClass());
        buffer.writeDouble(deposit.peakPotentialPerHour());
        buffer.writeDouble(deposit.localPotentialPerHour());
    }

    private static GeologyDepositSnapshot decodeDeposit(
            RegistryFriendlyByteBuf buffer
    ) {
        return new GeologyDepositSnapshot(
                buffer.readResourceLocation(),
                buffer.readResourceLocation(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readEnum(DepositStage.class),
                buffer.readDouble(),
                buffer.readInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readEnum(DepositQualityClass.class),
                buffer.readEnum(DepositSizeClass.class),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }
}
