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
 * Server-authoritative answer to a {@link CityPlayerSearchPayload}: a capped, permission-resolved
 * list of candidates.
 *
 * <p>Every row carries the stable {@link UUID} identity plus everything the UI needs to explain
 * itself: online state, the membership verdict, and — when the row cannot be invited — the server's
 * actual reason. The nickname is display and search only; identity never depends on it, so a rename
 * cannot split or duplicate an invite.
 */
public record CityPlayerSearchResultPayload(
        BlockPos pylonPos,
        List<Entry> entries
) implements CustomPacketPayload {

    /** Maximum rows the server will ever send, so opening the screen cannot dump a profile list. */
    public static final int MAX_RESULTS = 30;

    /** Server-side verdict for one candidate. */
    public enum State {
        AVAILABLE,
        INVITED,
        MEMBER_OF_OWN_CITY,
        MEMBER_OF_OTHER_CITY,
        SELF
    }

    public record Entry(
            UUID playerId,
            String name,
            boolean online,
            State state,
            String reasonKey,
            String cityName
    ) {}

    public static final Type<CityPlayerSearchResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID, "city_player_search_result"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC =
            StreamCodec.of(
                    (buffer, entry) -> {
                        buffer.writeUUID(entry.playerId());
                        ByteBufCodecs.STRING_UTF8.encode(buffer, entry.name());
                        buffer.writeBoolean(entry.online());
                        buffer.writeEnum(entry.state());
                        ByteBufCodecs.STRING_UTF8.encode(buffer, entry.reasonKey());
                        ByteBufCodecs.STRING_UTF8.encode(buffer, entry.cityName());
                    },
                    buffer -> new Entry(
                            buffer.readUUID(),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            buffer.readBoolean(),
                            buffer.readEnum(State.class),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer)));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityPlayerSearchResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CityPlayerSearchResultPayload::pylonPos,
                    ENTRY_CODEC.apply(ByteBufCodecs.list(MAX_RESULTS)), CityPlayerSearchResultPayload::entries,
                    CityPlayerSearchResultPayload::new);

    public CityPlayerSearchResultPayload {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}