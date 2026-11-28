package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.CargoTrainRegistry;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.economy.EconomyManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A read-only projection of published Cargo terms. No inventory or order books leave the server. */
public final class PublicTradeMap {
    static final int PER_KIND = 32;
    public static final int MAX_ROWS = 3 * PER_KIND;
    private static final int SCAN_LIMIT = 512;
    private PublicTradeMap() {}

    public static boolean isKind(String kind) {
        return "offer".equals(kind) || "trade_contract".equals(kind) || "intake".equals(kind);
    }

    static boolean published(CargoCommerceBook.Offer offer, CargoOwners.Access access) {
        return offer.active() && offer.remaining() > 0 && access.mode() == CargoTrainRegistry.AccessMode.PUBLIC;
    }

    static boolean openContract(MasterCityTradeBook.Contract contract, boolean enabled, long now, int remaining) {
        return enabled && contract.state() == MasterCityTradeBook.ContractState.OPEN
                && remaining > 0 && contract.expires() > now;
    }

    public static CompoundTag snapshot(ServerPlayer actor, CompoundTag request) {
        var out=new CompoundTag();
        out.putInt("TradeVersion",1);
        var items=request.getList("Items",net.minecraft.nbt.Tag.TAG_STRING);
        String dim=request.getString("Dimension"), query=request.getString("Search");
        if(dim.length()>160 || query.length()>64 || items.size()>32 || net.minecraft.resources.ResourceLocation.tryParse(dim)==null)return out;
        var ids=new java.util.HashSet<String>();
        for(int i=0;i<items.size();i++)if(items.getString(i).length()<=160)ids.add(items.getString(i));
        out.put("Offers",directory(actor,dim,query,ids,"",null));
        String kind=request.getString("SelectedKind");
        if(isKind(kind) && request.hasUUID("SelectedObject")){
            var selected=lookup(actor,kind,request.getUUID("SelectedObject"));
            if(selected!=null && dim.equals(selected.getCompound("Location").getString("Dimension")))
                out.put("SelectedTrade",selected);
        }
        return out;
    }

    public static CompoundTag lookup(ServerPlayer actor,String kind,UUID source) {
        var result=directory(actor,"","",java.util.Set.of(),kind,source);
        return result.isEmpty()?null:result.getCompound(0);
    }

    private static ListTag directory(ServerPlayer actor, String dimension, String search, java.util.Set<String> ids,
                                     String kind,UUID source) {
        var rows = new ListTag();
        var server = actor.getServer();
        var viewer = OwnerRef.player(actor.getUUID());
        var warehouses = CargoWarehouseRegistry.get(server);
        String query = search.strip().toLowerCase(java.util.Locale.ROOT);
        int scanned = 0, added = 0;
        var book=EconomyManager.get(server.overworld()).cargo();
        var offers=source==null?book.mapCandidates(SCAN_LIMIT):"offer".equals(kind)?book.offer(source).stream().toList():java.util.List.<CargoCommerceBook.Offer>of();
        for (var offer : offers) {
            if (++scanned > SCAN_LIMIT || added >= PER_KIND) break;
            var access = CenterOperations.access(actor, "offer", offer.id());
            if (!published(offer, access)) {
                // Restricted sales stay in Cargo. Later pages can then reach public offers behind them.
                if (source == null) book.deferUnpublishedOffer(offer.id());
                continue;
            }
            if (!CargoOwners.permits(server, viewer, access)) continue;
            var warehouse = warehouses.find(offer.warehouse()).orElse(null);
            if (warehouse == null) continue;
            var stack = ItemStack.parseOptional(server.registryAccess(), offer.item());
            if (stack.isEmpty()) continue;
            var row = terms("offer", offer.id(), offer.itemName(), stack, offer.remaining(), offer.unitPrice());
            if (CargoWarehouseRegistry.permits(actor, warehouse)) place(row, warehouse.name(), warehouse.ticker());
            if (include(rows, row, dimension, query,ids)) added++;
        }
        var trade = MasterCityTradeService.book(server);
        var binding = trade.binding();
        if (binding != null && trade.enabled()) {
            var city = CityManager.get(server.overworld()).getCity(binding.city());
            var destination = warehouses.find(binding.warehouse()).orElse(null);
            var now = Math.max(trade.now(), System.currentTimeMillis());
            scanned = 0; added = 0;
            var contracts=source==null?trade.mapCandidates(SCAN_LIMIT):"trade_contract".equals(kind)?trade.contract(source).stream().toList():java.util.List.<MasterCityTradeBook.Contract>of();
            var committed=contracts.isEmpty()?java.util.Map.<UUID,Integer>of():trade.mapCommitted(contracts);
            for (var contract : contracts) {
                if (++scanned > SCAN_LIMIT || added >= PER_KIND) break;
                if (contract.state() != MasterCityTradeBook.ContractState.OPEN || contract.expires() <= now) continue;
                int remaining = Math.max(0,contract.quantity()-committed.getOrDefault(contract.id(),0));
                if (!openContract(contract, trade.enabled(), now, remaining)) continue;
                var policy = trade.policy(contract.policy()).orElse(null);
                if (policy == null || city == null || destination == null || !destination.point().equals(binding.point()))
                    continue;
                var owner = CargoWarehouseRegistry.access(server, destination).owner();
                if (owner.kind() != OwnerRef.Kind.CITY || !owner.id().equals(city.getId())) continue;
                var stack = ItemStack.parseOptional(server.registryAccess(), policy.item());
                if (stack.isEmpty()) continue;
                var row = terms("trade_contract", contract.id(), contract.name(), stack, remaining, contract.price());
                row.putLong("RemainingMs", contract.expires() - now);
                if (CargoWarehouseRegistry.permits(actor, destination) && destination.receiver() != null)
                    place(row, destination.name(), destination.receiver());
                else {
                    place(row, city.getName(), new CargoInfrastructure.Location(city.getPylonDimension(), city.getPylonPos()));
                    row.putBoolean("CityLandmark", true);
                }
                if (include(rows, row, dimension, query,ids)) added++;
            }
        }
        scanned = 0; added = 0;
        var receivers=source==null?warehouses.mapCandidates(SCAN_LIMIT):"intake".equals(kind)?warehouses.find(source).stream().toList():java.util.List.<CargoWarehouseRegistry.Warehouse>of();
        for (var warehouse : receivers) {
            if (++scanned > SCAN_LIMIT || added >= PER_KIND) break;
            if (warehouse.receiver() == null || !CargoWarehouseRegistry.permits(actor, warehouse)) continue;
            if (!dimension.isEmpty() && !dimension.equals(warehouse.receiver().dimension().toString()) ||
                    CargoWarehouseRegistry.receivingInventory(server, warehouse) == null) continue;
            var row = terms("intake", warehouse.id(), warehouse.name(), ItemStack.EMPTY, 0, 0);
            place(row, warehouse.name(), warehouse.receiver());
            if (include(rows, row, dimension, query,ids)) added++;
        }
        return rows;
    }

    static CompoundTag terms(String kind, UUID source, String name, ItemStack item, int quantity, long price) {
        var row = new CompoundTag();
        row.putString("Kind", kind);
        row.putUUID("OpenId", source);
        row.putUUID("Id", UUID.nameUUIDFromBytes(("ccec/atlas/" + kind + "/" + source).getBytes(StandardCharsets.UTF_8)));
        row.putString("Name", name);
        if (!item.isEmpty()) {
            row.putString("ItemId", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
            row.putString("ItemKey", item.getDescriptionId());
            row.putString("ItemName", item.getHoverName().getString());
            row.putInt("Quantity", quantity);
            row.putLong("UnitPrice", price);
        }
        return row;
    }

    private static void place(CompoundTag row, String name, CargoInfrastructure.Location location) {
        row.put("Location", location.save());
        row.putString("Place", name);
    }

    static boolean include(ListTag rows, CompoundTag row, String dimension, String query, java.util.Set<String> ids) {
        if (!dimension.isEmpty() && row.contains("Location") && !dimension.equals(row.getCompound("Location").getString("Dimension"))) return false;
        if (!query.isEmpty() && !ids.contains(row.getString("ItemId"))) {
            String fields = row.getString("Name")+" "+row.getString("ItemName")+" "+row.getString("ItemId")+" "+row.getString("Place");
            if (!fields.toLowerCase(java.util.Locale.ROOT).contains(query)) return false;
        }
        rows.add(row);
        return true;
    }
}
