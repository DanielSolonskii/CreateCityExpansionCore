package com.example.examplemod.daily;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.network.DailyRewardsRequest;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class DailyRewardsGameTest {
    @GameTest(template = "auger_empty")
    public static void claimPersistsOnePlayerAndRecoversPendingReceipt(GameTestHelper helper) throws IOException {
        var level = helper.getLevel();
        var server = level.getServer();
        var id = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "DailyClaimTest"), false);
        var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, cookie);

        long today = DailyRewardsService.config(server).today();
        var request = new DailyRewardsRequest(UUID.randomUUID(), true, today, 0);
        var pending = new DailyRewardsData.Pending(UUID.randomUUID(), new ItemStack(Items.DIAMOND, 3), 2);
        var reserved = new DailyRewardsData.Entry("DailyClaimTest", DailyRewardProgress.fresh().reserve(today), pending);
        var data = DailyRewardsData.get(server);
        data.put(id, reserved);
        data.flush(server);
        var savedPlayers = new ArrayList<UUID>();
        Consumer<PlayerEvent.SaveToFile> observer = event -> savedPlayers.add(event.getEntity().getUUID());
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.SaveToFile.class, observer);
        try {
            helper.assertTrue(DailyRewardsService.claim(player, request).equals("received"), "The reserved reward is delivered");
            helper.assertTrue(data.entry(id).delivered() && savedPlayers.equals(List.of(id)), "Only the claimant is saved");

            // Simulate recovery after the player file reached disk but the journal still showed pending.
            data.put(id, reserved);
            data.flush(server);
            helper.assertTrue(DailyRewardsService.claim(player, request).equals("received"), "The pending receipt settles on retry");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3 && player.experienceLevel == 2,
                    "Retry must not duplicate items or experience");
            helper.assertTrue(DailyRewardsService.claim(player, request).equals("already") && savedPlayers.equals(List.of(id, id)),
                    "A settled claim requires no further player save");
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
        }
        helper.succeed();
    }
}
