package com.example.examplemod.profile;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.auth.PlayerNames;
import com.example.examplemod.city.CityInviteDirectory;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.network.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Reuses the existing local identity directory. No name-based remote repository lookup. */
@EventBusSubscriber(modid=CreateCityExpansionCore.MODID)
public final class ProfileAccess {
    private ProfileAccess() {}
    private static final class Cache {
        List<CityInviteDirectory.Candidate> known=List.of();
        long until;
        final Map<UUID,long[]> requests=new LinkedHashMap<>(32,.75f,true);
    }
    private static final Map<MinecraftServer,Cache> CACHES=new HashMap<>();
    private static Cache cache(MinecraftServer server) { return CACHES.computeIfAbsent(server,ignored->new Cache()); }
    private static List<CityInviteDirectory.Candidate> known(ServerPlayer viewer) {
        var server=viewer.getServer(); var cache=cache(server); long now=Util.getMillis();
        if(cache.until<=now) {
            cache.known=List.copyOf(CityInviteDirectory.knownProfiles(server,CityManager.get(server.overworld()),viewer).stream()
                    .filter(c->PlayerNames.usable(c.name())).toList());
            cache.until=now+60_000;
        }
        return cache.known;
    }
    private static boolean allow(ServerPlayer viewer) {
        var requests=cache(viewer.getServer()).requests;
        long[] budget=requests.computeIfAbsent(viewer.getUUID(),ignored->new long[2]);
        while(requests.size()>256) requests.remove(requests.keySet().iterator().next());
        return ProfileService.consume(budget,Util.getMillis());
    }
    public static void search(ServerPlayer viewer,ProfileSearchPayload request) {
        if(viewer instanceof FakePlayer) return;
        if(!allow(viewer)) {
            PacketDistributor.sendToPlayer(viewer,new ProfileDirectoryPayload(request.request(),request.query(),"busy",0,0,List.of()));
            return;
        }
        var ranked=CityInviteDirectory.rank(known(viewer),request.query(),Integer.MAX_VALUE);
        int page=Math.min(request.page(),Math.max(0,(ranked.size()-1)/ProfileDirectoryPayload.PAGE_SIZE));
        int first=page*ProfileDirectoryPayload.PAGE_SIZE;
        var cities=CityManager.get(viewer.getServer().overworld());
        List<ProfileDirectoryPayload.Entry> entries=new ArrayList<>();
        for(var candidate:ranked.subList(first,Math.min(ranked.size(),first+ProfileDirectoryPayload.PAGE_SIZE))) {
            var city=cities.getMemberCity(candidate.id());
            entries.add(new ProfileDirectoryPayload.Entry(candidate.id(),candidate.name(),viewer.getServer().getPlayerList().getPlayer(candidate.id())!=null,city==null?"":city.getName()));
        }
        PacketDistributor.sendToPlayer(viewer,new ProfileDirectoryPayload(request.request(),request.query(),"ok",page,ranked.size(),entries));
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ccec").then(Commands.literal("profile")
                .requires(source->source.getEntity() instanceof ServerPlayer && !(source.getEntity() instanceof FakePlayer))
                .executes(ctx->open(ctx.getSource(),null))
                .then(Commands.argument("player",StringArgumentType.word()).suggests((ctx,builder)-> {
                    if(!(ctx.getSource().getEntity() instanceof ServerPlayer viewer)) return builder.buildFuture();
                    String prefix=builder.getRemaining().toLowerCase(Locale.ROOT);
                    return SharedSuggestionProvider.suggest(known(viewer).stream().map(CityInviteDirectory.Candidate::name)
                            .filter(name->name.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct()
                            .sorted(String.CASE_INSENSITIVE_ORDER).limit(50).toList(),builder);
                }).executes(ctx->open(ctx.getSource(),StringArgumentType.getString(ctx,"player"))))));
    }
    private static int open(CommandSourceStack source,String name) {
        if(!(source.getEntity() instanceof ServerPlayer viewer) || viewer instanceof FakePlayer) return 0;
        if(!allow(viewer)) { viewer.sendSystemMessage(message("result.busy")); return 0; }
        UUID id=viewer.getUUID();
        if(name!=null) {
            List<CityInviteDirectory.Candidate> matches;
            try {
                UUID requested=UUID.fromString(name);
                matches=known(viewer).stream().filter(c->c.id().equals(requested)).toList();
            } catch(IllegalArgumentException notUuid) {
                if(!PlayerNames.usable(name)) { viewer.sendSystemMessage(message("result.unknown")); return 0; }
                matches=known(viewer).stream().filter(c->c.name().equalsIgnoreCase(name)).toList();
            }
            if(matches.size()!=1) {
                viewer.sendSystemMessage(message(matches.isEmpty()?"result.unknown":"search_ambiguous")); return 0;
            }
            id=matches.getFirst().id();
        }
        PacketDistributor.sendToPlayer(viewer,new OpenProfilePayload(id));
        return 1;
    }
    private static Component message(String key) { return Component.translatable("gui.createcityexpansioncore.profile."+key); }
    private static void invalidate(ServerPlayer player) { var cache=CACHES.get(player.getServer()); if(cache!=null) cache.until=0; }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { if(event.getEntity() instanceof ServerPlayer p) invalidate(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) { invalidate(p); var c=CACHES.get(p.getServer()); if(c!=null) c.requests.remove(p.getUUID()); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CACHES.remove(event.getServer()); }
}
