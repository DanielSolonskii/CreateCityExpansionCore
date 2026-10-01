package com.example.examplemod.city;

import com.example.examplemod.network.CityInviteResultPayload;
import com.example.examplemod.network.CityPlayerSearchPayload;
import com.example.examplemod.network.CityPlayerSearchResultPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who this server knows, and whether they can be invited.
 *
 * <p>The old invite screen listed {@code getConnection().getOnlinePlayers()} on the client, which is
 * why offline players could not be invited at all and why the server's own rules were invisible
 * until a button silently did nothing. Everything is resolved here instead, on the server, from data
 * that already exists: online players, plus every account this server has ever stored a membership
 * or an invite for.
 *
 * <p>The matching, ordering, capping and state classification are pure and static, so the parts that
 * decide what the player is allowed to do are verified headless rather than by playing the game.
 */
public final class CityInviteDirectory {
    private CityInviteDirectory() {
    }

    public static final int MAX_RESULTS = CityPlayerSearchResultPayload.MAX_RESULTS;

    /** One known account: stable identity plus the nickname used for display and search. */
    public record Candidate(UUID id, String name, boolean online) {
    }

    /** Case-insensitive substring match. An empty query matches everything, which is the "recent" list. */
    public static boolean matches(String query, String name) {
        if (name == null) {
            return false;
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || name.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Ranks candidates for a query: online first, then prefix matches before mere substrings, then
     * shorter names, then alphabetically. Capped, so a blank query lists recent players rather than
     * the whole history.
     */
    public static List<Candidate> rank(List<Candidate> known, String query, int cap) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Candidate> matches = new ArrayList<>();
        for (Candidate candidate : known) {
            if (matches(needle, candidate.name())) {
                matches.add(candidate);
            }
        }
        matches.sort(Comparator
                .comparing(Candidate::online).reversed()
                .thenComparingInt(candidate -> {
                    String name = candidate.name() == null ? "" : candidate.name().toLowerCase(Locale.ROOT);
                    return needle.isEmpty() || name.startsWith(needle) ? 0 : 1;
                })
                .thenComparingInt(candidate -> candidate.name() == null ? 0 : candidate.name().length())
                .thenComparing(candidate -> candidate.name() == null ? "" : candidate.name(),
                        String.CASE_INSENSITIVE_ORDER));
        return matches.size() > cap ? new ArrayList<>(matches.subList(0, cap)) : matches;
    }

    /**
     * The server's verdict for one candidate. Ordering matters: being yourself outranks everything,
     * and an existing invite is reported before "member of another city" because it is the actionable
     * state for this city.
     */
    public static CityPlayerSearchResultPayload.State stateOf(
            boolean self, boolean memberOfOwnCity, boolean alreadyInvited, boolean memberOfOtherCity) {
        if (self) {
            return CityPlayerSearchResultPayload.State.SELF;
        }
        if (memberOfOwnCity) {
            return CityPlayerSearchResultPayload.State.MEMBER_OF_OWN_CITY;
        }
        if (alreadyInvited) {
            return CityPlayerSearchResultPayload.State.INVITED;
        }
        if (memberOfOtherCity) {
            return CityPlayerSearchResultPayload.State.MEMBER_OF_OTHER_CITY;
        }
        return CityPlayerSearchResultPayload.State.AVAILABLE;
    }

    /** Translation key explaining a state, so the client never invents its own wording. */
    public static String reasonKey(CityPlayerSearchResultPayload.State state) {
        return "gui.createcityexpansioncore.city.invite.state." + state.name().toLowerCase(Locale.ROOT);
    }

    /** Refusal that is about the inviter rather than the candidate. */
    public static final String NO_PERMISSION_KEY =
            "gui.createcityexpansioncore.city.invite.state.no_permission";

    /** Maps the model's own outcome onto the state vocabulary the client already renders. */
    public static String reasonKey(City.InviteResult result) {
        return switch (result) {
            case SENT -> reasonKey(CityPlayerSearchResultPayload.State.AVAILABLE);
            case ALREADY_INVITED -> reasonKey(CityPlayerSearchResultPayload.State.INVITED);
            case ALREADY_MEMBER, IS_OWNER -> reasonKey(CityPlayerSearchResultPayload.State.MEMBER_OF_OWN_CITY);
        };
    }

    /**
     * Every account this server still knows.
     *
     * <p>Three sources, all local and all free of any network lookup:
     * <ol>
     *   <li><b>the {@code playerdata} directory</b> — one {@code <uuid>.dat} per player who has ever
     *       been on this server. This is what makes a player who played here once, but never joined a
     *       city and never received an invite, findable; deriving the roster from city data alone
     *       could never find them.</li>
     *   <li><b>the server's own city data</b> — owners, members and pending invitees, so a player who
     *       has since deleted every save file is still reachable.</li>
     *   <li><b>currently connected players</b>, who always win on name and online state.</li>
     * </ol>
     *
     * <p>Nicknames come from {@code GameProfileCache.get(UUID)}, which is a pure in-memory read of
     * {@code profilesByUUID}. {@code get(String)} is deliberately never called: it can fall through to
     * a repository lookup. When a UUID cannot be named locally the short id is used and no request
     * leaves the server — the limitation is stated rather than papered over.
     */
    public static List<Candidate> knownProfiles(MinecraftServer server, CityManager cities, ServerPlayer viewer) {
        Map<UUID, String> byId = new LinkedHashMap<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            byId.put(online.getUUID(), online.getGameProfile().getName());
        }
        for (Path file : playerDataFiles(server)) {
            UUID id = parsePlayerFile(file.getFileName().toString());
            if (id != null) {
                byId.putIfAbsent(id, null);
            }
        }
        for (City city : cities.getCities()) {
            if (city.getOwner() != null) {
                remember(byId, city.getOwner());
            }
            city.getMembers().forEach(member -> remember(byId, member.getPlayerId()));
            city.getInvites().forEach(invite -> remember(byId, invite.getPlayerId()));
        }
        UUID self = viewer.getUUID();
        List<Candidate> candidates = new ArrayList<>();
        server.getPlayerList().getPlayers().forEach(online ->
                candidates.add(new Candidate(online.getUUID(), online.getGameProfile().getName(), true)));
        for (Map.Entry<UUID, String> entry : byId.entrySet()) {
            if (entry.getKey().equals(self) || isOnline(server, entry.getKey())) {
                continue;
            }
            candidates.add(new Candidate(entry.getKey(), resolveName(server, entry.getValue(), entry.getKey()), false));
        }
        return candidates;
    }

    /**
     * The {@code playerdata} folder itself. Reading the directory is all that is needed: the files
     * are never opened, so nothing is parsed and nothing is generated.
     */
    private static List<Path> playerDataFiles(MinecraftServer server) {
        try (java.util.stream.Stream<Path> files = Files.list(server.getWorldPath(LevelResource.PLAYER_DATA_DIR))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".dat")).toList();
        } catch (Exception ex) {
            // A world without player data yet is normal; the other two sources still work.
            return List.of();
        }
    }

    /** A {@code .dat} file name is the account id in its dashed UUID form. */
    static UUID parsePlayerFile(String fileName) {
        String name = fileName.endsWith(".dat") ? fileName.substring(0, fileName.length() - 4) : fileName;
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isOnline(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id) != null;
    }

    private static void remember(Map<UUID, String> byId, UUID id) {
        if (id != null) {
            byId.putIfAbsent(id, null);
        }
    }

    /** The remembered name when we have one, otherwise the server's own profile cache, otherwise a short id. */
    private static String resolveName(MinecraftServer server, String remembered, UUID id) {
        if (remembered != null && !remembered.isBlank()) {
            return remembered;
        }
        Optional<com.mojang.authlib.GameProfile> cached = server.getProfileCache().get(id);
        return cached.map(com.mojang.authlib.GameProfile::getName).orElseGet(() -> shortId(id));
    }

    public static String shortId(UUID id) {
        String hex = id.toString().replace("-", "");
        return hex.substring(0, 8);
    }

    /** Answers a search request. Permission and membership are resolved here, never on the client. */
    public static void handleSearch(CityPlayerSearchPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer viewer)) {
            return;
        }
        ServerLevel level = viewer.serverLevel();
        CityManager cities = CityManager.get(level);
        City city = cities.getCityByPylon(viewer.serverLevel().dimension().location(), payload.pylonPos());
        if (city == null || !city.isAdmin(viewer.getUUID())) {
            return;
        }
        List<Candidate> ranked = rank(knownProfiles(viewer.server, cities, viewer), payload.query(), MAX_RESULTS);
        List<CityPlayerSearchResultPayload.Entry> entries = new ArrayList<>(ranked.size());
        for (Candidate candidate : ranked) {
            City other = cities.getCityByMember(candidate.id());
            CityPlayerSearchResultPayload.State state = stateOf(
                    candidate.id().equals(viewer.getUUID()),
                    city.hasMember(candidate.id()),
                    city.hasInvite(candidate.id()),
                    other != null);
            entries.add(new CityPlayerSearchResultPayload.Entry(
                    candidate.id(),
                    candidate.name(),
                    candidate.online(),
                    state,
                    reasonKey(state),
                    other == null ? "" : other.getName()));
        }
        PacketDistributor.sendToPlayer(viewer,
                new CityPlayerSearchResultPayload(payload.pylonPos(), entries));
    }

    /**
     * Tells the inviter how their invite ended, if they are connected. A success is an event, not a
     * problem, so it never reaches the Problem Center.
     */
    /**
 * Pushes the authoritative member list to one viewer.
 *
 * <p>Sent only where membership actually changed: accepting an invite, a role change, a removal. A
 * declined invite deliberately does not go through here, because it leaves the member list
 * untouched — invite and search state belong to the invite system that already owns them.
 */
    public static void publishMembers(MinecraftServer server, City city, ServerPlayer viewer) {
        if (viewer == null) {
            return;
        }
        List<com.example.examplemod.network.SyncCityMembersPayload.Entry> entries = new ArrayList<>();
        for (CityMember member : city.getMembers()) {
            entries.add(new com.example.examplemod.network.SyncCityMembersPayload.Entry(
                    member.getPlayerId(), member.getRole().ordinal()));
        }
        PacketDistributor.sendToPlayer(viewer,
                new com.example.examplemod.network.SyncCityMembersPayload(city.getPylonPos(), entries));
    }

    /**
     * Tells the inviter how their invite ended, if they are connected. A success is never a problem, so never the Problem Center.
     */
    public static void reportOutcome(MinecraftServer server, UUID inviterId, String invitedName,
                                      String cityName, boolean accepted) {
        if (inviterId == null) {
            return;
        }
        ServerPlayer inviter = server.getPlayerList().getPlayer(inviterId);
        if (inviter == null) {
            return;
        }
        PacketDistributor.sendToPlayer(inviter,
                new com.example.examplemod.network.CityInviteOutcomePayload(
                        inviterId, invitedName, cityName, accepted));
    }

    /**
 * Display name for an account, from the server's in-memory profile cache only. Never performs a
 * network lookup, and falls back to a short id rather than guessing a nickname.
 */
public static String displayName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return resolveName(server, null, id);
    }

    /** Tells the inviter exactly what happened to the invite they just sent. */
    public static void reportResult(ServerPlayer inviter, net.minecraft.core.BlockPos pylonPos,
                                    boolean sent, String reasonKey, String targetName) {
        PacketDistributor.sendToPlayer(inviter,
                new CityInviteResultPayload(pylonPos, sent, reasonKey, targetName));
    }
}