package com.example.examplemod.city;

import com.example.examplemod.network.CityPlayerSearchResultPayload;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Headless regression for city invite search and state classification.
 *
 * <p>The invite screen used to list online players on the client and had no search at all, so an
 * offline player could not be invited and every rule was invisible. These checks pin the parts that
 * decide what the player is allowed to do — matching, ordering, capping and the server's verdict —
 * so they are verified here rather than by playing the game.
 *
 * <p>Everything exercised is production code from {@link CityInviteDirectory} and
 * {@link City#addInvite}; the invite record itself is reflected into, because its fields are
 * private and the persistence of the inviter is part of what is being pinned.
 */
public final class CityInviteSearchCheck {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static CityInviteDirectory.Candidate candidate(String name, boolean online) {
        return new CityInviteDirectory.Candidate(UUID.nameUUIDFromBytes(name.getBytes()), name, online);
    }

    public static void main(String[] args) throws Exception {
        var names = new net.minecraft.nbt.CompoundTag();
        UUID namedId = UUID.randomUUID();
        names.putString(namedId.toString(), "moorgull");
        names.putString(UUID.randomUUID().toString(), "cs0123456789abcd");
        names.putString("bad-id", "drannch1k");
        var restoredNames = com.example.examplemod.auth.PlayerNames.load(names, net.minecraft.core.RegistryAccess.EMPTY);
        var savedNames = restoredNames.save(new net.minecraft.nbt.CompoundTag(), net.minecraft.core.RegistryAccess.EMPTY);
        require(savedNames.getString(namedId.toString()).equals("moorgull") && savedNames.getAllKeys().size() == 1,
                "offline display names survive persistence; technical aliases and corrupt entries are ignored");
        var roster = new com.example.examplemod.network.SyncCityMembersPayload(BlockPos.ZERO,
                List.of(new com.example.examplemod.network.SyncCityMembersPayload.Entry(namedId, CityMember.Role.RESIDENT.ordinal(), "moorgull")));
        var nameBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            com.example.examplemod.network.SyncCityMembersPayload.STREAM_CODEC.encode(nameBuffer, roster);
            require(com.example.examplemod.network.SyncCityMembersPayload.STREAM_CODEC.decode(nameBuffer).equals(roster),
                    "member snapshot codec preserves nickname and stable UUID independently");
        } finally { nameBuffer.release(); }
        // ------------------------------------------------------------------ case-insensitive matching
        require(CityInviteDirectory.matches("ste", "Steve"), "substring match, mixed case");
        require(CityInviteDirectory.matches("STE", "steve"), "case-insensitive both ways");
        require(CityInviteDirectory.matches("  ste  ", "Steve"), "query whitespace is trimmed");
        require(CityInviteDirectory.matches("", "Steve"), "an empty query lists everyone");
        require(CityInviteDirectory.matches("   ", "Steve"), "a blank query lists everyone");
        require(!CityInviteDirectory.matches("alex", "Steve"), "a non-matching name is rejected");
        require(!CityInviteDirectory.matches("ste", (String) null), "a nameless candidate never matches a query");

        // ------------------------------------------------------------ ordering: online, prefix, length
        CityInviteDirectory.Candidate onlineSteve = candidate("SteveOnline", true);
        CityInviteDirectory.Candidate offlineSteve = candidate("SteveOffline", false);
        CityInviteDirectory.Candidate otherSteve = candidate("AlexSteve", false);
        List<CityInviteDirectory.Candidate> ranked = CityInviteDirectory.rank(
                List.of(offlineSteve, otherSteve, onlineSteve), "steve", 30);
        require(ranked.getFirst() == onlineSteve, "online players rank first");
        require(ranked.get(1) == offlineSteve,
                "among offline players a prefix match outranks a mere substring");
        require(ranked.get(2) == otherSteve,
                "the substring-only match comes last, even though it is shorter");
        require(CityInviteDirectory.rank(offlineSteveFirst(), "", 30).size() == 1,
                "an empty query still returns the known list");

        // ------------------------------------------------------------------------- results are capped
        List<CityInviteDirectory.Candidate> many = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            many.add(candidate("Player" + index, false));
        }
        require(CityInviteDirectory.rank(many, "player", 30).size() == 30,
                "results are capped at the limit instead of dumping a profile list");
        require(CityInviteDirectory.rank(many, "player", 30).size() == CityInviteDirectory.MAX_RESULTS,
                "the cap is the advertised one");
        require(CityInviteDirectory.rank(many, "player", 5).size() == 5, "the cap is honoured exactly");
        require(CityInviteDirectory.rank(many, "Player199", 30).size() == 1,
                "a specific query still finds its match inside a large list");
        require(CityInviteDirectory.rank(many, "Player1", 1000).size() == 111,
                "a prefix query also matches Player1x and Player1xx, which is what substring ranking builds on");
        require(CityInviteDirectory.rank(many, "Player1", 30).size() == 30,
                "and that wide prefix result is still capped for the wire");

        // ------------------------------------------------- server-authoritative state classification
        require(CityInviteDirectory.stateOf(false, false, false, false) == CityPlayerSearchResultPayload.State.AVAILABLE,
                "a player with nothing in the way is AVAILABLE");
        require(CityInviteDirectory.stateOf(false, false, true, false) == CityPlayerSearchResultPayload.State.INVITED,
                "an existing invite is INVITED");
        require(CityInviteDirectory.stateOf(false, true, false, false) == CityPlayerSearchResultPayload.State.MEMBER_OF_OWN_CITY,
                "an existing member is MEMBER_OF_OWN_CITY");
        require(CityInviteDirectory.stateOf(false, false, false, true) == CityPlayerSearchResultPayload.State.MEMBER_OF_OTHER_CITY,
                "membership elsewhere is MEMBER_OF_OTHER_CITY");
        require(CityInviteDirectory.stateOf(true, false, false, false) == CityPlayerSearchResultPayload.State.SELF,
                "you are SELF");
        require(CityInviteDirectory.stateOf(true, true, true, true) == CityPlayerSearchResultPayload.State.SELF,
                "SELF outranks every other state");
        require(CityInviteDirectory.stateOf(false, true, true, false) == CityPlayerSearchResultPayload.State.MEMBER_OF_OWN_CITY,
                "membership outranks a stale invite for the same city");
        require(CityInviteDirectory.stateOf(false, false, true, true) == CityPlayerSearchResultPayload.State.MEMBER_OF_OTHER_CITY,
                "joining another city blocks resending an old invite");

        // --------------------------------------------------------------- the stale race the UI asked for
        // A row that said AVAILABLE, and a click that happens after the player joined another city,
        // have to agree: the server recomputes instead of trusting the row.
        require(CityInviteDirectory.stateOf(false, false, false, false) == CityPlayerSearchResultPayload.State.AVAILABLE,
                "search-time verdict is AVAILABLE");
        require(CityInviteDirectory.stateOf(false, false, false, true) == CityPlayerSearchResultPayload.State.MEMBER_OF_OTHER_CITY,
                "click-time verdict after joining another city is MEMBER_OF_OTHER_CITY, not AVAILABLE");

        // ------------------------------------------------------------------ reason keys are translatable
        for (CityPlayerSearchResultPayload.State state : CityPlayerSearchResultPayload.State.values()) {
            String key = CityInviteDirectory.reasonKey(state);
            require(key.startsWith("gui.createcityexpansioncore.city.invite.state."),
                    "reason key for " + state + " is translatable, not raw: " + key);
        }
        require(CityInviteDirectory.NO_PERMISSION_KEY.startsWith("gui.createcityexpansioncore."),
                "the no-permission reason is translatable too");
        require(CityInviteDirectory.reasonKey(City.InviteResult.SENT).endsWith("available"),
                "a sent invite reports the AVAILABLE wording, so success and 'can invite' agree");
        require(CityInviteDirectory.reasonKey(City.InviteResult.ALREADY_INVITED).endsWith("invited"),
                "a duplicate invite reports the INVITED wording");

        // ------------------------------------------- the invite model itself: duplicate and idempotency
        City city = new City(UUID.randomUUID(), "Test", UUID.randomUUID(), BlockPos.ZERO);
        UUID owner = city.getOwner();
        UUID candidate1 = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        city.addMember(admin, CityMember.Role.ADMIN);

        require(city.addInvite(candidate1, admin) == City.InviteResult.SENT, "a fresh invite is SENT");
        require(city.hasInvite(candidate1), "the invite exists");
        require(city.addInvite(candidate1, admin) == City.InviteResult.ALREADY_INVITED,
                "inviting twice reports ALREADY_INVITED");
        require(city.getInvites().size() == 1, "inviting twice never duplicates the record");
        require(city.addInvite(owner, admin) == City.InviteResult.IS_OWNER, "the owner cannot be invited");
        require(city.addInvite(admin, admin) == City.InviteResult.ALREADY_MEMBER,
                "an existing member cannot be invited");

        // ------------------------------------------- the inviter is part of the record and survives load
        CityInvite invite = city.getInvite(candidate1);
        require(invite != null, "the pending invite is retrievable before it is resolved");
        require(invite.getInvokerId().equals(admin), "the invite remembers who sent it");
        require(invite.getPlayerId().equals(candidate1), "the invite remembers who it was sent to");
        city.removeInvite(candidate1);
        require(city.addInvite(candidate1, owner) == City.InviteResult.SENT,
                "renewed invite can be sent again");
        require(city.getInvites().size() == 1, "resending preserves exactly one invite");
        require(city.getInvite(candidate1).getInvokerId().equals(owner),
                "renewed invite attributes the response to the latest sender");

        // ------------------------------------------------- accept is idempotent and clears the invite
        city.acceptInvite(candidate1);
        require(!city.hasInvite(candidate1), "accepting clears the invite");
        require(city.hasMember(candidate1), "accepting adds the member");
        int membersAfter = city.getMembers().size();
        city.acceptInvite(candidate1);
        require(city.getMembers().size() == membersAfter,
                "accepting twice never creates a second membership");
        require(!city.hasInvite(candidate1), "a repeated accept cannot revive the invite");

        // ------------------------------------------------------------- identity is the account, not the name
        UUID renamed = UUID.randomUUID();
        CityInvite byId = new CityInvite(renamed, admin);
        require(byId.getPlayerId().equals(renamed),
                "identity is the stable account id, so a nickname change cannot split an invite");
        Constructor<CityInvite> single = CityInvite.class.getConstructor(UUID.class);
        require(single.newInstance(renamed).getInvokerId() == null,
                "an invite written before the inviter existed still loads, with no inviter");

        // ---------------------------------------------------------------- localization completeness
        Set<String> required = new HashSet<>(List.of(
                "gui.createcityexpansioncore.city.invite.search",
                "gui.createcityexpansioncore.city.invite.search.hint",
                "gui.createcityexpansioncore.city.invite.search.empty",
                "gui.createcityexpansioncore.city.invite.search.pending",
                "gui.createcityexpansioncore.city.invite.search.online",
                "gui.createcityexpansioncore.city.invite.search.offline",
                "gui.createcityexpansioncore.city.invite.search.no_permission",
                "gui.createcityexpansioncore.city.invite.sent",
                "gui.createcityexpansioncore.city.invite.toast.sent",
                "gui.createcityexpansioncore.city.invite.outcome.accepted",
                "gui.createcityexpansioncore.city.invite.outcome.declined",
                "gui.createcityexpansioncore.city.invite.state.available",
                "gui.createcityexpansioncore.city.invite.state.invited",
                "gui.createcityexpansioncore.city.invite.state.member_of_own_city",
                "gui.createcityexpansioncore.city.invite.state.member_of_other_city",
                "gui.createcityexpansioncore.city.invite.state.member_of_other_city_plain",
                "gui.createcityexpansioncore.city.invite.state.self",
                "gui.createcityexpansioncore.city.invite.state.no_permission",
                "gui.createcityexpansioncore.city.invite.state.invite",
                "gui.createcityexpansioncore.city.invites.title"));
        for (String language : List.of("en_us", "ru_ru")) {
            String text = Files.readString(langFile(language));
            for (String key : required) {
                require(text.contains("\"" + key + "\""), language + " has " + key);
                require(!text.contains("\"" + key + "\":\"\""), language + " does not leave " + key + " blank");
            }
        }

        // The inviter field must actually be persisted, not only held in memory.
        Field inviterField = CityInvite.class.getDeclaredField("inviterId");
        inviterField.setAccessible(true);
        require(inviterField.get(invite).equals(admin), "the inviter is a real stored field");
        require(Files.readString(sourceFile()).contains("putUUID(\"Inviter\""),
                "the inviter is written to the saved data");

        // ------------------------------------ the offline roster really covers every past player
        // A player who played here once but never joined a city is found through the playerdata
        // directory, not through city data.
        UUID pastPlayer = UUID.randomUUID();
        require(CityInviteDirectory.parsePlayerFile(pastPlayer + ".dat").equals(pastPlayer),
                "a playerdata file name resolves back to the account id");
        require(CityInviteDirectory.parsePlayerFile(pastPlayer.toString()).equals(pastPlayer),
                "an undashed id still parses");
        require(CityInviteDirectory.parsePlayerFile("readme.txt") == null,
                "a file that is not a player save is ignored rather than guessed at");
        require(CityInviteDirectory.parsePlayerFile("not-a-uuid.dat") == null,
                "a malformed name never becomes a candidate");
        require(CityInviteDirectory.parsePlayerFile("") == null, "an empty name is not a candidate");

        // The roster must not be able to reach the network: only the UUID-keyed, in-memory profile
        // cache is allowed, never get(name), which can fall through to a repository lookup.
        String directory = Files.readString(sourceRoot()
                .resolve("src/main/java/com/example/examplemod/city/CityInviteDirectory.java"));
        require(directory.contains("LevelResource.PLAYER_DATA_DIR"),
                "the roster reads the playerdata directory, which covers players with no city history");
        require(directory.contains("PlayerNames.name(server, entry.getKey())"),
                "names come from the server-owned identity resolver");
        require(!directory.contains("getProfileCache().get(String"),
                "the network-capable profile lookup by name is never used");
        require(!directory.contains("lookupGameProfile"),
                "no repository fallback is reachable from the roster");
        require(directory.contains("PLAYER_DATA_DIR"),
                "the roster source is a real, local, persistent server directory");
        require(Files.readString(sourceRoot()
                                .resolve("src/main/java/com/example/examplemod/city/CityInviteDirectory.java"))
                        .contains("getWorldPath"),
                "the directory is reached through the public server API, not a guessed path");

        // ------------------------------------------------------ member snapshot channel and its events
        // Only ACCEPT needed a new channel: removal and role changes already pushed deltas, so a
        // snapshot was added for the one case that had none rather than duplicating working ones.
        String mod = Files.readString(sourceRoot()
                .resolve("src/main/java/com/example/examplemod/CreateCityExpansionCore.java"));
        require(mod.contains("SyncCityMembersPayload.TYPE"),
                "the member snapshot channel is registered");
        require(mod.contains("SYNC_CITY_MEMBERS_HANDLER"),
                "the snapshot is routed through a swappable handler like every other channel");
        String directory2 = Files.readString(sourceRoot()
                .resolve("src/main/java/com/example/examplemod/city/CityInviteDirectory.java"));
        require(directory2.contains("publishMembers"),
                "the server publishes the authoritative member list");
        require(directory2.contains("member.getRole().ordinal()"),
                "the snapshot reuses the very role representation the menu already stores");
        require(directory2.contains("for (CityMember member : city.getMembers())"),
                "the snapshot is the complete member list, not a delta");
        // A declined invite must not churn the member list.
        String handler = Files.readString(sourceRoot()
                .resolve("src/main/java/com/example/examplemod/CreateCityExpansionCoreClient.java"));
        require(handler.contains("applyMembersSnapshot"),
                "the client applies a snapshot to the open pylon");
        require(Files.readString(sourceRoot().resolve(
                        "src/main/java/com/example/examplemod/client/screen/CityPylonScreen.java"))
                        .contains("this.menu.getPylonPos().equals(payload.pylonPos())"),
                "a snapshot for another pylon is ignored instead of being applied blindly");
        require(Files.readString(sourceRoot().resolve(
                        "src/main/java/com/example/examplemod/menu/CityPylonMenu.java"))
                        .contains("public void replaceMembers("),
                "applying a snapshot replaces the list wholesale, so a replay cannot duplicate anybody");

        // -------------------------- restored invite handler: every refusal is reported, offline is allowed
        String mod2 = Files.readString(sourceRoot()
                .resolve("src/main/java/com/example/examplemod/CreateCityExpansionCore.java"));
        require(mod2.contains("CityInviteDirectory.reportResult("),
                "the invite handler answers the sender instead of failing silently");
        require(mod2.contains("CityInviteDirectory.stateOf("),
                "search and click share one authoritative verdict");
        require(mod2.contains("city.addInvite("),
                "the invite is created");
        require(mod2.contains("if (resend) {\n                            city.removeInvite(targetId);")
                        || mod2.contains("if (resend) {\r\n                            city.removeInvite(targetId);"),
                "resending renews the pending record before creation");
        int notificationStart = mod2.indexOf("new CityInviteNotificationPayload(", mod2.indexOf("InvitePlayerPayload.TYPE"));
        String inviteNotification = mod2.substring(notificationStart, mod2.indexOf("CityInviteDirectory.reportResult(", notificationStart));
        require(inviteNotification.contains("PlayerNames.name(serverPlayer)"),
                "incoming invitation uses the verified game nickname");
        require(!inviteNotification.replaceAll("\\s+", "").contains("serverPlayer.getName()"),
                "incoming invitation cannot use the technical profile name");
        require(!mod2.contains("target == null"),
                "the online target is only used for the immediate notification, never as a refusal");
        require(mod2.contains("if (target != null) {"),
                "an online recipient is pushed the notification straight away");
        // Offline must not be a refusal any more: the notification is guarded by a null check on the
        // resolved player rather than by rejecting the invite.
        require(!mod2.contains("Игрок должен быть онлайн"),
                "the old 'target must be online' refusal is gone");
        require(mod2.contains("CityInviteDirectory.reportOutcome("),
                "the inviter is told how the invite ended");
        require(mod2.contains("CityInviteDirectory.publishMembers("),
                "the authoritative member list is published on accept");
        require(mod2.contains("city.getInvite("),
                "the pending invite is read before it is consumed, so the inviter is known");
        require(mod2.contains("invite.getInvokerId()"),
                "the inviter id is taken from the invite itself");
        // Decline must not push a member snapshot: membership did not change.
        int decline = mod2.indexOf("CityInviteDirectory.reportOutcome(",
                mod2.indexOf("ОТКЛОНИТЬ") > 0 ? mod2.indexOf("ОТКЛОНИТЬ") : 0);
        int accept = mod2.indexOf("CityInviteDirectory.publishMembers(");
        require(decline > 0 && accept > decline,
                "the decline outcome is sent first and the snapshot only on the accept path");

        // inviterId is persisted, so an offline outcome still has a recipient once the sender logs back in.
        String manager = Files.readString(sourceRoot()
                .resolve("src/main/java/com/example/examplemod/city/CityManager.java"));
        require(manager.contains("putUUID(\"Inviter\""),
                "the inviter is written to the saved data");
        require(manager.contains("getUUID(\"Inviter\")"),
                "and read back, so a pending invite keeps its sender across a restart");

        // ---------------------------- the member snapshot reaches every viewer of that pylon, exactly once
        String directory3 = Files.readString(sourceRoot()
                .resolve("src/main/java/com/example/examplemod/city/CityInviteDirectory.java"));
        // Scope to publishMembers only: reportOutcome legitimately checks the inviter for a toast.
        int publishStart = directory3.indexOf("public static void publishMembers(");
        require(publishStart > 0, "publishMembers exists");
        String publish = directory3.substring(publishStart,
                directory3.indexOf("public static void reportOutcome(", publishStart));
        require(publish.contains("viewer.containerMenu instanceof com.example.examplemod.menu.CityPylonMenu menu"),
                "a viewer is recognised by having this pylon's menu open, like the removal delta");
        require(publish.contains("menu.getPylonPos().equals(city.getPylonPos())"),
                "only viewers of this very pylon are included");
        require(publish.contains("for (ServerPlayer viewer : server.getPlayerList().getPlayers())"),
                "every online player is considered, so a second admin is not left stale");
        require(publish.contains("PacketDistributor.sendToPlayer(viewer, snapshot)"),
                "the same snapshot instance goes to each viewer, so nobody receives it twice");
        require(!publish.contains("inviterId"),
                "the member sync no longer depends on the inviter at all, online or not");
        require(!publish.contains("if (viewer == null)"),
                "there is no single designated viewer that could silently drop the sync");
        require(publish.contains("for (CityMember member : city.getMembers())"),
                "the snapshot is the complete member list");
        require(directory3.contains("getPlayer(inviterId)"),
                "the inviter is still looked up for the toast, which is the only place it matters");

        System.out.println("PASS: " + checks + " city invite search, state and persistence checks");
    }

    private static List<CityInviteDirectory.Candidate> offlineSteveFirst() {
        return List.of(candidate("SteveOffline", false));
    }

    private static Path langFile(String language) {
        return sourceRoot().resolve("src/main/resources/assets/createcityexpansioncore/lang/" + language + ".json");
    }

    private static Path sourceFile() {
        return sourceRoot().resolve("src/main/java/com/example/examplemod/city/CityManager.java");
    }

    private static Path sourceRoot() {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("src/main/java"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new AssertionError("cannot locate source tree");
        }
        return root;
    }
}
