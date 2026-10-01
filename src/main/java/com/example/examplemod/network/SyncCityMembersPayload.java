package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The authoritative member list of one city, pushed when it actually changed.
 *
 * <p>The member list used to be a one-shot snapshot baked into the pylon menu when it opened, so
 * somebody who accepted an invitation only appeared after the player reopened the pylon. There was
 * no channel to refresh it; this is that channel, and it is deliberately the smallest one that works:
 * a full snapshot rather than a delta, because membership changes rarely and a snapshot cannot be
 * applied twice or out of order and end up wrong.
 *
 * <p>Entries reuse the very representation the menu already stores — account id plus
 * {@code CityMember.Role} ordinal — so no second membership model exists. It is applied only when the
 * pylon matches, and membership stays entirely server-authoritative.
 */
public record SyncCityMembersPayload(
        BlockPos pylonPos,
        List<Entry> members
) implements CustomPacketPayload {

    /** One member: the stable account id and the role the server assigned. */
    public record Entry(UUID playerId, int roleId) {}

    /** Bounded so a malformed or hostile packet cannot allocate an unbounded list. */
    public static final int MAX_MEMBERS = 256;

    public static final Type<SyncCityMembersPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID, "sync_city_members"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC =
            StreamCodec.of(
                    (buffer, entry) -> {
                        buffer.writeUUID(entry.playerId());
                        ByteBufCodecs.VAR_INT.encode(buffer, entry.roleId());
                    },
                    buffer -> new Entry(
                            buffer.readUUID(),
                            ByteBufCodecs.VAR_INT.decode(buffer)));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCityMembersPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SyncCityMembersPayload::pylonPos,
                    ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_MEMBERS)), SyncCityMembersPayload::members,
                    SyncCityMembersPayload::new);

    public SyncCityMembersPayload {
        members = List.copyOf(members);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}