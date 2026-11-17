package com.example.examplemod.devtools;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.multiblock.MineBlueprints;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class BlueprintEditorAccess {
    private static final String OWNER = "BlueprintEditorOwner";
    public static final String MESSAGE = "gui.createcityexpansioncore.blueprint_editor.";

    private BlueprintEditorAccess() {}

    public static boolean isLocalOperator(ServerPlayer player) {
        return player != null && !player.serverLevel().getServer().isDedicatedServer()
                && player.hasPermissions(2) && player.serverLevel().getServer().isSingleplayerOwner(player.getGameProfile());
    }

    private static boolean owns(ServerPlayer player, ItemStack stack) {
        if (!stack.is(CreateCityExpansionCore.BLUEPRINT_EDITOR.get())) {
            return false;
        }
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.hasUUID(OWNER) && player.getUUID().equals(data.getUUID(OWNER));
    }

    public static boolean canUse(ServerPlayer player) {
        return isLocalOperator(player) && (owns(player, player.getMainHandItem()) || owns(player, player.getOffhandItem()));
    }

    public static int issue(CommandSourceStack source, ServerPlayer player) {
        if (!isLocalOperator(player)) {
            return 0;
        }
        ItemStack terminal = CreateCityExpansionCore.BLUEPRINT_EDITOR.get().getDefaultInstance();
        CompoundTag data = new CompoundTag();
        data.putUUID(OWNER, player.getUUID());
        terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        terminal.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (!player.getInventory().add(terminal)) {
            source.sendFailure(Component.translatable(MESSAGE + "inventory_full"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(MESSAGE + "issued"), false);
        return 1;
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            MineBlueprints.clearLocalOverride();
        }
    }
}
