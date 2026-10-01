package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.railway.RailLineStop;
import com.example.examplemod.railway.RailwayTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RailwayCommandPayload(BlockPos pylonPos, Action action, UUID primaryId, UUID secondaryId,
                                    String name, int typeOrdinal, boolean cyclic, List<RailLineStop> stops)
        implements CustomPacketPayload {
    public enum Action { REQUEST_STATE, REQUEST_MAP, ATTACH_STATION, ATTACH_TRAIN, CREATE_LINE, UPDATE_LINE,
        REMOVE_LINE, ASSIGN_LINE, UNASSIGN_LINE, RESTORE_SCHEDULE, REMOVE_TRAIN, REMOVE_STATION,
        SET_STATION_TYPE, SET_TRAIN_TYPE, REBIND_STATION, REBIND_TRAIN, LINK_PROJECT,
        SET_NETWORK_ACCESS, SET_NETWORK_TARIFF, REQUEST_ROUTES, SELECT_ROUTE,
        /** Register a Create train (payload.primaryId = Create train id) into CCEC. */
        REGISTER_TRAIN }
    public static final Type<RailwayCommandPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CreateCityExpansionCore.MODID, "railway_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RailwayCommandPayload> STREAM_CODEC =
            StreamCodec.of(RailwayCommandPayload::encode, RailwayCommandPayload::decode);
    public RailwayCommandPayload { name = name == null ? "" : name; stops = List.copyOf(stops); }
    public static RailwayCommandPayload simple(BlockPos pos, Action action, UUID primary, UUID secondary) {
        return new RailwayCommandPayload(pos, action, primary, secondary, "", 0, true, List.of());
    }
    private static void encode(RegistryFriendlyByteBuf b, RailwayCommandPayload p) {
        BlockPos.STREAM_CODEC.encode(b, p.pylonPos); b.writeEnum(p.action);
        b.writeBoolean(p.primaryId != null); if (p.primaryId != null) b.writeUUID(p.primaryId);
        b.writeBoolean(p.secondaryId != null); if (p.secondaryId != null) b.writeUUID(p.secondaryId);
        b.writeUtf(p.name, 64); b.writeVarInt(p.typeOrdinal); b.writeBoolean(p.cyclic);
        b.writeVarInt(Math.min(64, p.stops.size()));
        for (int i = 0; i < Math.min(64, p.stops.size()); i++) { RailLineStop s = p.stops.get(i);
            b.writeUUID(s.stationRegistrationId()); b.writeEnum(s.mode()); b.writeVarInt(s.waitSeconds()); }
    }
    private static RailwayCommandPayload decode(RegistryFriendlyByteBuf b) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(b); Action action = b.readEnum(Action.class);
        UUID primary = b.readBoolean() ? b.readUUID() : null; UUID secondary = b.readBoolean() ? b.readUUID() : null;
        String name = b.readUtf(64); int type = b.readVarInt(); boolean cyclic = b.readBoolean();
        int count = Math.min(64, Math.max(0, b.readVarInt())); List<RailLineStop> stops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) stops.add(new RailLineStop(b.readUUID(), b.readEnum(RailwayTypes.StopMode.class), b.readVarInt()));
        return new RailwayCommandPayload(pos, action, primary, secondary, name, type, cyclic, stops);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
