package com.example.examplemod.journey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.journey.JourneyModel.Chain;
import com.example.examplemod.journey.JourneyModel.Kind;
import com.example.examplemod.journey.JourneyModel.Marker;
import com.example.examplemod.journey.JourneyModel.Objective;
import com.example.examplemod.journey.JourneyModel.ObjectiveKind;
import com.example.examplemod.journey.JourneyModel.Point;
import com.example.examplemod.journey.JourneyModel.Progress;
import com.example.examplemod.journey.JourneyModel.Quest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.example.examplemod.journey.JourneyRouteDraft.require;

/** Operator-only publication of one route and its generated marker and quest records. */
public final class JourneyRouteAuthoring {
    public static final String GROUP = "route";
    public static final String DRAFT_ID = "newcomer";
    public static final String CHAIN_ID = "ccec_first/newcomer";

    private record Candidate(Chain chain, List<Marker> markers, List<Quest> quests) {}
    record Result(String event, CompoundTag draft, String revision) {}

    public static boolean managed(String id) {
        return CHAIN_ID.equals(id) || id.startsWith(CHAIN_ID + "/");
    }

    public static boolean accessible(JourneyData data, Progress progress, String id) {
        return !managed(id) || data.chains.containsKey(CHAIN_ID)
                && (CHAIN_ID.equals(id) || CHAIN_ID.equals(progress.chain));
    }

    static void requireUnmanaged(String id) {
        if (managed(id)) throw new IllegalArgumentException("route_managed");
    }

    /** Called only after the common editor operator and rate checks, on the server thread. */
    static void request(ServerPlayer player, JourneyData data, JourneyEditorPayload request) {
        Result result;
        try {
            require(DRAFT_ID.equals(request.id()), "id", 0);
            CompoundTag value = "load".equals(request.action()) ? new CompoundTag() : TagParser.parseTag(request.value());
            result = apply(data, request, value, draft -> validateWorld(player.getServer(), draft));
        } catch (JourneyRouteDraft.Failure failure) {
            reply(player, result(data, "error"), data.chains.containsKey(CHAIN_ID), failure.code(), failure.step());
            return;
        } catch (Exception failure) {
            reply(player, result(data, "error"), data.chains.containsKey(CHAIN_ID), "invalid", 0);
            return;
        }

        reply(player, result, data.chains.containsKey(CHAIN_ID), "", 0);
        if ("published".equals(result.event())) {
            for (var online : player.getServer().getPlayerList().getPlayers()) {
                JourneyServer.send(online, false, "");
            }
        }
    }

    static Result apply(JourneyData data, JourneyEditorPayload request, CompoundTag value,
                        Consumer<JourneyRouteDraft> worldValidation) {
        data.requireAvailable();
        if ("load".equals(request.action())) return result(data, "loaded");
        require(revision(data).equals(request.base()), "conflict", 0);
        JourneyRouteDraft draft = JourneyRouteDraft.parse(value);
        CompoundTag normalized = draft.write();
        require(fitsEditorPacket(normalized.toString()), "limit", 0);
        switch (request.action()) {
            case "save" -> {
                data.routeDraft = normalized;
                data.setDirty();
                return result(data, "saved");
            }
            case "check", "publish" -> {
                draft.validate();
                worldValidation.accept(draft);
                Candidate candidate = candidate(data, draft);
                if ("check".equals(request.action())) return new Result("checked", normalized, revision(data));
                install(data, candidate, normalized);
                return result(data, "published");
            }
            default -> throw new JourneyRouteDraft.Failure("action", 0);
        }
    }

    private static boolean fitsEditorPacket(String text) {
        return text.length() <= 24000 && text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 30000;
    }

    private static String revision(JourneyData data) {
        return JourneyAuthoring.revision(data.routeDraft);
    }

    private static Result result(JourneyData data, String event) {
        return new Result(event, data.routeDraft.copy(), revision(data));
    }

    private static void reply(ServerPlayer player, Result result, boolean published, String error, int step) {
        var tag = new CompoundTag();
        tag.putString("Event", result.event());
        tag.putString("Group", GROUP);
        tag.putString("Id", DRAFT_ID);
        tag.put("Value", result.draft());
        tag.putString("Revision", result.revision());
        tag.putString("Error", error);
        tag.putInt("Step", step);
        tag.putBoolean("Published", published);
        PacketDistributor.sendToPlayer(player, new JourneyEditorStatePayload(tag));
    }

    private static Candidate candidate(JourneyData data, JourneyRouteDraft draft) {
        int stepCount = draft.steps().size();
        require(data.markers.keySet().stream().filter(id -> !managed(id)).count() + stepCount <= JourneyModel.MAX_MARKERS,
                "limit", 0);
        require(data.quests.keySet().stream().filter(id -> !managed(id)).count() + stepCount <= JourneyModel.MAX_QUESTS,
                "limit", 0);
        require(data.chains.keySet().stream().filter(id -> !managed(id)).count() < 16, "limit", 0);
        require(!hasLegacyReference(data), "referenced", 0);

        var chain = new Chain(CHAIN_ID);
        chain.title = draft.title();
        chain.enabled = true;
        var markers = new ArrayList<Marker>(stepCount);
        var quests = new ArrayList<Quest>(stepCount);
        String previous = "";
        for (int i = 0; i < stepCount; i++) {
            JourneyRouteDraft.Step step = draft.steps().get(i);
            String markerId = CHAIN_ID + "/m" + (i + 1);
            String questId = CHAIN_ID + "/q" + (i + 1);
            var marker = new Marker(markerId, step.point());
            marker.title = step.title();
            marker.description = step.description();
            marker.kind = step.type() == ObjectiveKind.VISIT ? Kind.GUIDE : Kind.QUEST;
            marker.radius = step.radius();
            marker.icon = step.icon();
            marker.afterQuest = previous;
            marker.route.addAll(step.route());

            var quest = new Quest(questId);
            quest.title = step.title();
            quest.description = step.description();
            quest.marker = markerId;
            quest.enabled = true;
            String target = step.type() == ObjectiveKind.ITEM ? ResourceLocation.parse(step.item()).toString() : markerId;
            quest.objectives.add(new Objective("complete", step.type(), target, step.count()));
            chain.steps.add(questId);
            markers.add(marker);
            quests.add(quest);
            previous = questId;
        }
        return new Candidate(chain, markers, quests);
    }

    private static boolean hasLegacyReference(JourneyData data) {
        return data.chains.values().stream().anyMatch(chain -> !managed(chain.id) && chain.steps.stream().anyMatch(JourneyRouteAuthoring::managed))
                || data.markers.values().stream().anyMatch(marker -> !managed(marker.id) && managed(marker.afterQuest))
                || data.quests.values().stream().anyMatch(quest -> !managed(quest.id) && (managed(quest.marker)
                || quest.objectives.stream().anyMatch(objective -> spatial(objective.kind()) && managed(objective.target()))));
    }

    /** All parsing, references, capacity and world validation precede the first mutation. */
    private static void install(JourneyData data, Candidate candidate, CompoundTag draft) {
        Set<String> outdated = outdatedConditions(data, candidate);
        data.markers.keySet().removeIf(JourneyRouteAuthoring::managed);
        data.quests.keySet().removeIf(JourneyRouteAuthoring::managed);
        data.chains.put(CHAIN_ID, candidate.chain());
        candidate.markers().forEach(marker -> data.markers.put(marker.id, marker));
        candidate.quests().forEach(quest -> data.quests.put(quest.id, quest));
        for (Progress progress : data.players.values()) {
            for (String id : outdated) {
                progress.completed.remove(id);
                progress.met.remove(id + "/complete");
            }
            progress.accepted.removeIf(id -> managed(id) && !data.quests.containsKey(id));
            if (managed(progress.activeQuest) && !data.quests.containsKey(progress.activeQuest)) progress.activeQuest = "";
            if (managed(progress.activeMarker) && !data.markers.containsKey(progress.activeMarker)) progress.activeMarker = "";
            if (CHAIN_ID.equals(progress.chain)) {
                String next = JourneyModel.next(candidate.chain(), progress);
                progress.accepted.removeIf(id -> managed(id) && !next.equals(id));
                if (managed(progress.activeQuest) && !next.equals(progress.activeQuest)) progress.activeQuest = "";
            }
        }
        data.routeDraft = draft;
        data.setDirty();
    }

    private static Set<String> outdatedConditions(JourneyData data, Candidate candidate) {
        var outdated = new HashSet<String>();
        data.quests.keySet().stream().filter(JourneyRouteAuthoring::managed).forEach(outdated::add);
        for (int i = 0; i < candidate.quests().size(); i++) {
            Quest quest = candidate.quests().get(i);
            Quest oldQuest = data.quests.get(quest.id);
            Marker marker = candidate.markers().get(i);
            Marker oldMarker = data.markers.get(marker.id);
            if (oldQuest != null && oldMarker != null && oldQuest.objectives.equals(quest.objectives)
                    && oldMarker.point.equals(marker.point) && oldMarker.radius == marker.radius) {
                outdated.remove(quest.id);
            }
        }
        return outdated;
    }

    private static void validateWorld(MinecraftServer server, JourneyRouteDraft draft) {
        if (draft.start() != null) validatePoint(server, draft.start(), 0);
        for (int i = 0; i < draft.steps().size(); i++) {
            JourneyRouteDraft.Step step = draft.steps().get(i);
            int number = i + 1;
            ServerLevel level = validatePoint(server, step.point(), number);
            for (Point waypoint : step.route()) validatePoint(server, waypoint, number);
            if (step.type() == ObjectiveKind.ITEM) {
                var item = ResourceLocation.parse(step.item());
                require(BuiltInRegistries.ITEM.containsKey(item) && !item.equals(ResourceLocation.withDefaultNamespace("air")),
                        "item", number);
            }
            if (step.type() == ObjectiveKind.INTERACT || step.type() == ObjectiveKind.OPEN_PYLON) {
                var pos = new BlockPos(step.point().x(), step.point().y(), step.point().z());
                if (!level.hasChunkAt(pos)) continue;
                var state = level.getBlockState(pos);
                boolean valid = step.type() == ObjectiveKind.OPEN_PYLON ? state.getBlock() instanceof CityPylonBlock : !state.isAir();
                require(valid, "location", number);
            }
        }
    }

    private static ServerLevel validatePoint(MinecraftServer server, Point point, int step) {
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(point.dimension()));
        ServerLevel level = server.getLevel(dimension);
        require(level != null, "dimension", step);
        require(!level.isOutsideBuildHeight(new BlockPos(point.x(), point.y(), point.z())), "location", step);
        return level;
    }

    static boolean spatial(ObjectiveKind kind) {
        return kind == ObjectiveKind.VISIT || kind == ObjectiveKind.INTERACT || kind == ObjectiveKind.OPEN_PYLON;
    }

    private JourneyRouteAuthoring() {}
}
