package com.example.examplemod.economy;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Redeems a physically held cancelled parcel. Never searches inventories or creates lost cargo. */
public final class FreightParcelRecovery {
    private static final int MAIN_SLOTS = 36;
    private FreightParcelRecovery() {}

    /**
     * Leaves already extracted but unsealed goods with the sender when cancellation closes new
     * dispatch. Only the known, loaded source packager is inspected; stamped cargo is untouched.
     */
    public static int neutralizeUnsealed(MinecraftServer server, UUID subject) {
        var freight = EconomyManager.get(server.overworld()).freight();
        FreightAssignment assignment = freight.assignments.get(subject);
        FreightPoint source = assignment == null ? null : freight.point(assignment.source);
        if (source == null) return 0;
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, source.dimension));
        if (level == null || !level.hasChunkAt(source.packager)
                || !(level.getBlockEntity(source.packager) instanceof PackagerBlockEntity packager)
                || packager.animationInward) return 0;
        String address = freight.transit.address(assignment);
        if (address == null || address.isBlank()) return 0;
        int changed = neutralize(packager.heldBox, address) ? 1 : 0;
        for (var queued : packager.queuedExitingPackages) if (neutralize(queued.stack, address)) changed++;
        if (changed > 0) packager.notifyUpdate();
        return changed;
    }

    private static boolean neutralize(ItemStack parcel, String address) {
        if (!PackageItem.isPackage(parcel) || !PackageItem.matchAddress(parcel, address)
                || !FreightLogistics.packageTokens(parcel).isEmpty()) return false;
        var contents = PackageItem.getContents(parcel);
        for (int i = 0; i < contents.getSlots(); i++) if (ShipmentTokenItemData.read(contents.getStackInSlot(i)).isPresent()) return false;
        PackageItem.clearAddress(parcel);
        return true;
    }

    /** Returns a key under message.createcityexpansioncore.economy. */
    public static String recoverHeld(ServerPlayer player, UUID expectedSubject, boolean admin) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer
                || admin && !player.createCommandSourceStack().hasPermission(2)) return "freight_revoke_denied";
        ItemStack parcel = player.getMainHandItem();
        if (!PackageItem.isPackage(parcel) || parcel.getCount() != 1) return "freight_recover_hold";
        Set<UUID> ids = new LinkedHashSet<>(FreightLogistics.packageTokens(parcel));
        var contents = PackageItem.getContents(parcel);
        for (int i = 0; i < contents.getSlots(); i++) ShipmentTokenItemData.read(contents.getStackInSlot(i)).ifPresent(ids::add);
        if (ids.size() != 1) return "freight_recover_invalid";
        UUID tokenId = ids.iterator().next();
        EconomyManager manager = EconomyManager.get(player.getServer().overworld());
        ShipmentToken token = manager.shipmentToken(tokenId);
        if (token == null || expectedSubject != null && !expectedSubject.equals(token.subjectId())) return "freight_recover_invalid";
        FreightAssignment assignment = manager.freight().assignments.get(token.subjectId());
        FreightPoint source = assignment == null ? null : manager.freight().point(assignment.source);
        if (source == null || !source.cityId.equals(token.senderCityId())) return "freight_recover_source";
        if (!admin) {
            City city = CityManager.get(player.getServer().overworld()).getCityByMember(player.getUUID());
            if (city == null || !city.canBuild(player.getUUID()) || !city.getId().equals(token.senderCityId())) return "freight_revoke_denied";
        }
        net.minecraft.world.item.Item item = null;
        long amount = 0L;
        List<ItemStack> goods = new ArrayList<>();
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack stack = contents.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (item != null && item != stack.getItem()) return "freight_recover_invalid";
            item = stack.getItem();
            amount += stack.getCount();
            goods.add(ShipmentTokenItemData.withoutToken(stack));
        }
        if (item == null || amount != token.amount() || !BuiltInRegistries.ITEM.getKey(item).equals(token.itemId())) return "freight_recover_invalid";
        var result = manager.reclaimCancelledPackage(source.id, tokenId, BuiltInRegistries.ITEM.getKey(item), amount,
                player.serverLevel().getGameTime(), false, new HeldTransaction(player, parcel, goods));
        return switch (result) {
            case NONE -> "freight_recovered";
            case SHIPMENT_ALREADY_USED -> "freight_recover_used";
            case CONTRACT_INVALID -> "freight_recover_not_cancelled";
            case EXCESS_CARGO -> "freight_recover_full";
            case WRONG_INTAKE -> "freight_recover_source";
            default -> "freight_recover_invalid";
        };
    }

    /** One atomic replacement of one held parcel with its own goods in the main inventory. */
    private static final class HeldTransaction implements EconomyManager.PhysicalTransaction {
        private final ServerPlayer player;
        private final Inventory inventory;
        private final int handSlot;
        private final ItemStack parcel;
        private final List<ItemStack> goods;
        private final ItemStack[] before = new ItemStack[MAIN_SLOTS];
        private final ItemStack[] after = new ItemStack[MAIN_SLOTS];
        private boolean prepared, changed;

        HeldTransaction(ServerPlayer player, ItemStack parcel, List<ItemStack> goods) {
            this.player = player;
            this.inventory = player.getInventory();
            this.handSlot = inventory.selected;
            this.parcel = parcel.copy();
            this.goods = goods.stream().map(ItemStack::copy).toList();
        }

        @Override public boolean prepare() {
            if (handSlot < 0 || handSlot >= 9 || inventory.selected != handSlot || !ItemStack.matches(inventory.getItem(handSlot), parcel)) return false;
            // The adapter uses the real player's slot acceptance and limit. Armor, offhand and
            // carried/menu slots are deliberately outside this transaction.
            ItemStackHandler plan = new ItemStackHandler(MAIN_SLOTS) {
                @Override public boolean isItemValid(int slot, ItemStack stack) { return inventory.canPlaceItem(slot, stack); }
                @Override public int getSlotLimit(int slot) { return inventory.getMaxStackSize(); }
            };
            for (int i = 0; i < MAIN_SLOTS; i++) {
                before[i] = inventory.getItem(i).copy();
                plan.setStackInSlot(i, i == handSlot ? ItemStack.EMPTY : before[i].copy());
            }
            for (ItemStack stack : goods) if (!ItemHandlerHelper.insertItemStacked(plan, stack.copy(), false).isEmpty()) return false;
            for (int i = 0; i < MAIN_SLOTS; i++) after[i] = plan.getStackInSlot(i).copy();
            prepared = true;
            return true;
        }

        @Override public boolean commit() {
            if (!prepared || inventory.selected != handSlot) return false;
            for (int i = 0; i < MAIN_SLOTS; i++) if (!ItemStack.matches(inventory.getItem(i), before[i])) return false;
            changed = true;
            try {
                inventory.setItem(handSlot, ItemStack.EMPTY);
                for (int i = 0; i < MAIN_SLOTS; i++) inventory.setItem(i, after[i].copy());
                sync();
                return true;
            } catch (RuntimeException ex) {
                rollback();
                return false;
            }
        }

        @Override public void rollback() {
            if (!changed) return;
            for (int i = 0; i < MAIN_SLOTS; i++) inventory.setItem(i, before[i].copy());
            changed = false;
            sync();
        }

        private void sync() {
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
    }
}
