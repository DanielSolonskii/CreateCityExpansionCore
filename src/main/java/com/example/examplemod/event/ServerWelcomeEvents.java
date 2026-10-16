package com.example.examplemod.event;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Greets a player on every login: a full welcome the very first time, a welcome back afterwards,
 * followed both times by the standing alpha warning. Neither line is broadcast — only the player who
 * just logged in sees them.
 *
 * <p>"First time" is a flag in the player's own saved NBT. {@code Entity#getPersistentData()} is
 * written by the server under the {@code NeoForgeData} key inside {@code saveWithoutId} and read back
 * in {@code load}, so the flag survives a relog and cannot be fooled by a reconnect within one
 * session. Nothing else is stored: wiping the player data file only makes the greeting read as a
 * first visit again, which is the safe direction to fail.
 *
 * <p>Both lines are translatable, so the wording follows the client's own language. The player name
 * and the command are passed in as components and keep their own colour against the line colour.
 */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class ServerWelcomeEvents {
    /** Player NBT flag: set once the player has been greeted, so every later login is a welcome back. */
    private static final String GREETED = "ccecGreeted";

    /**
     * Command the alpha warning sends players to. Held here so the wording of the message and the
     * command it advertises cannot drift apart.
     */
    private static final String REPORT_COMMAND = "/report";

    private ServerWelcomeEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Fake players (Create contraptions, for one) are ServerPlayers too and must not be greeted.
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }

        var data = player.getPersistentData();
        boolean firstJoin = !data.getBoolean(GREETED);
        data.putBoolean(GREETED, true);

        String name = com.example.examplemod.auth.PlayerNames.name(player);

        var greeting = Component.translatable(
                firstJoin ? "message.createcityexpansioncore.server.welcome"
                        : "message.createcityexpansioncore.server.welcome_back",
                Component.literal(name).withStyle(ChatFormatting.WHITE)
        ).withStyle(style -> style.withColor(0xADB7BA).withBold(false));

        player.sendSystemMessage(Component.empty()
                .append(Component.literal("RE:CREATED").withStyle(style -> style.withColor(0xC5A264)))
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(greeting));

        var report = Component.translatable("message.createcityexpansioncore.server.report_action")
                .withStyle(style -> style.withColor(0x78BFC4).withBold(false)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, REPORT_COMMAND + " "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.createcityexpansioncore.server.report_hint"))));

        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.server.alpha_notice",
                report
        ).withStyle(style -> style.withColor(0xADB7BA).withBold(false)));
    }
}
