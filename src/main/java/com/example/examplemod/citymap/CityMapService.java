package com.example.examplemod.citymap;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.city.CityPublicAtlas;
import com.example.examplemod.passenger.PassengerService;
import com.example.examplemod.passenger.PassengerRailMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public final class CityMapService {
    private record Budget(long second, int requests, int writes) {}
    private record Receipt(CityMapNetwork.Request request, String status) {}
    private static final Map<ServerPlayer, Budget> budgets = new WeakHashMap<>();
    private static final Map<ServerPlayer, LinkedHashMap<UUID, Receipt>> receipts = new WeakHashMap<>();

    private CityMapService() {}

    static BiPredicate<UUID, CityPlot> ownership(CityManager cities) {
        return (city, plot) -> {
            var owner = cities.getClaimOwner(plot);
            if (CityMapNetwork.OUTSIDE.equals(city)) return owner == null;
            return owner != null && owner.getId().equals(city);
        };
    }

    public static CityMapFeature resolve(ServerPlayer actor, UUID id) {
        var server = actor.serverLevel().getServer();
        return CityMapBook.get(server).resolve(id, actor.serverLevel().dimension().location(),
                ownership(CityManager.get(server.overworld())));
    }

    public static List<CityMapFeature> publicFeatures(ServerPlayer actor) {
        var server = actor.serverLevel().getServer();
        return CityMapBook.get(server).visible(actor.serverLevel().dimension().location(), null,
                ownership(CityManager.get(server.overworld())));
    }

    public static CityMapFeature stationForBinding(ServerPlayer actor, UUID id, long revision, BlockPos stationBlock, BlockPos boarding) {
        if (!actor.hasPermissions(2) || actor instanceof FakePlayer) throw new IllegalArgumentException("op_required");
        var station = resolve(actor, id);
        if (station == null || station.revision() != revision) throw new IllegalArgumentException("stale");
        if (station.kind() != CityMapFeature.Kind.STATION) throw new IllegalArgumentException("invalid");
        var level = actor.serverLevel();
        var server = level.getServer();
        if (!CityMapBook.get(server).canRevise()) throw new IllegalArgumentException("limit");
        var owns = ownership(CityManager.get(server.overworld()));
        for (var pos : List.of(stationBlock, boarding)) {
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) ||
                    !owns.test(station.city(), CityPlot.at(station.dimension(), pos))) throw new IllegalArgumentException("territory");
        }
        return station;
    }

    public static void platformsChanged(ServerPlayer actor, UUID id) {
        CityMapBook.get(actor.serverLevel().getServer()).platformsChanged(id);
    }

    static boolean mayEdit(City city, UUID actor, boolean operator, ResourceLocation playerDimension) {
        return city != null && city.getPylonDimension().equals(playerDimension) && (operator || city.hasMember(actor));
    }

    static boolean mayEditScope(UUID scope, ResourceLocation dimension, City city, UUID actor, boolean operator, ResourceLocation playerDimension) {
        if (CityMapNetwork.OUTSIDE.equals(scope)) return operator && dimension.equals(playerDimension);
        return mayEdit(city, actor, operator, playerDimension);
    }

    public static CityMapNetwork.Reply handle(ServerPlayer actor, CityMapNetwork.Request request) {
        return handle(actor, request, true);
    }

    static CityMapNetwork.Reply handle(ServerPlayer actor, CityMapNetwork.Request request, boolean modern) {
        return handle(actor, request, modern, false);
    }

    static CityMapNetwork.Reply handle(ServerPlayer actor, CityMapNetwork.Request request, boolean modern, boolean supportsIcons) {
        var view = CityPublicAtlas.view(actor, request.view());
        if (actor instanceof FakePlayer || view == null || !view.worldId().equals(request.world())
                || !view.dimension().equals(request.dimension())) return empty(request, "unavailable");
        boolean write = request.action() != CityMapNetwork.Action.LIST;
        long second = Util.getMillis() / 1000;
        var budget = budgets.get(actor);
        if (budget == null || budget.second != second) budget = new Budget(second, 0, 0);
        if (budget.requests >= 8 || write && budget.writes >= 4) return empty(request, "busy");
        budgets.put(actor, new Budget(second, budget.requests + 1, budget.writes + (write ? 1 : 0)));

        var server = actor.serverLevel().getServer();
        var book = CityMapBook.get(server);
        if (!book.available()) return empty(request, "unavailable");
        var cities = CityManager.get(server.overworld());
        boolean all = request.city().equals(CityMapNetwork.ALL);
        var city = all ? null : cities.getCity(request.city());
        boolean outside = CityMapNetwork.OUTSIDE.equals(request.city());
        if (!all && !outside && (city == null || !city.getPylonDimension().equals(request.dimension()))) return empty(request, "unavailable");
        boolean edit = mayEditScope(request.city(), request.dimension(), city, actor.getUUID(), actor.hasPermissions(2), actor.serverLevel().dimension().location());
        if (write && !edit) return empty(request, "permission");
        var owns = ownership(cities);
        if (write && !modern && book.hasLinks(request.id())) return empty(request, "unavailable");
        if (!supportsIcons && request.action() == CityMapNetwork.Action.SAVE) {
            var previous = book.resolve(request.id(), request.dimension(), owns);
            if (previous != null && previous.icon() != CityMapFeature.Icon.HOME) return empty(request, "unavailable");
        }
        String status = write ? mutateOnce(actor, request, book, owns) : "ok";
        var rows = "ok".equals(status) || "stale".equals(status)
                ? book.visible(request.dimension(), all ? null : request.city(), owns) : List.<CityMapFeature>of();
        var platforms = modern && ("ok".equals(status) || "stale".equals(status)) ? PassengerService.stops(actor).stream()
                .filter(stop -> stop.dimension().equals(request.dimension()))
                .filter(stop -> all || owns.test(request.city(), CityPlot.at(request.dimension(), stop.stationPos()))
                        && owns.test(request.city(), CityPlot.at(request.dimension(), stop.entrance())))
                .filter(stop -> {
                    if (stop.feature() == null) return true;
                    var station = book.resolve(stop.feature(), stop.dimension(), owns);
                    return station != null && owns.test(station.city(), CityPlot.at(stop.dimension(), stop.stationPos()))
                            && owns.test(station.city(), CityPlot.at(stop.dimension(), stop.entrance()));
                })
                .toList() : List.<PassengerService.Stop>of();
        var railMap = modern && all && "ok".equals(status) ? PassengerRailMap.forAtlas(actor, request.dimension()) : PassengerRailMap.EMPTY;
        return new CityMapNetwork.Reply(request.nonce(), request.city(), status, edit, rows, platforms, railMap);
    }

    private static CityMapNetwork.Reply empty(CityMapNetwork.Request r, String status) {
        return new CityMapNetwork.Reply(r.nonce(), r.city(), status, false, List.of());
    }

    private static String mutateOnce(ServerPlayer actor, CityMapNetwork.Request r, CityMapBook book,
                                     BiPredicate<UUID, CityPlot> owns) {
        var recent = receipts.computeIfAbsent(actor, key -> new LinkedHashMap<>());
        var receipt = recent.get(r.nonce());
        if (receipt != null) return r.equals(receipt.request) ? receipt.status : "invalid";
        String status;
        if (r.action() == CityMapNetwork.Action.REGISTER_STATION || r.platform() != null) {
            var feature = book.resolve(r.id(), r.dimension(), owns);
            if (feature == null || !feature.city().equals(r.city())) return "unavailable";
            if (feature.revision() != r.expected()) return "stale";
            try {
                switch (r.action()) {
                    case REGISTER_STATION -> PassengerService.registerCityStation(actor, feature);
                    case BIND_PLATFORM -> PassengerService.bindCityPlatform(actor, r.id(), r.expected(), r.platform());
                    case UNBIND_PLATFORM -> PassengerService.unbindCityPlatform(actor, r.id(), r.expected(), r.platform());
                    default -> throw new IllegalStateException("Unexpected platform action");
                }
                status = "ok";
            } catch (IllegalArgumentException rejected) {
                status = rejected.getMessage();
            }
        } else {
            var candidate = r.value();
            if ((candidate == null || candidate.kind() != CityMapFeature.Kind.STATION)
                    && PassengerService.hasCityPlatforms(actor.serverLevel().getServer(), r.id())) return "in_use";
            if (candidate != null && Stream.concat(candidate.vertices().stream(), candidate.entrances().stream()).anyMatch(
                    pos -> actor.serverLevel().isOutsideBuildHeight(pos) || !actor.serverLevel().getWorldBorder().isWithinBounds(pos)))
                return "territory";
            status = book.change(r.city(), r.dimension(), r.id(), r.expected(), candidate, owns);
        }
        recent.put(r.nonce(), new Receipt(r, status));
        if (recent.size() > 32) recent.pollFirstEntry();
        return status;
    }
}
