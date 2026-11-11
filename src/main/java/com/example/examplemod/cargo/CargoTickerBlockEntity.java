package com.example.examplemod.cargo;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.example.examplemod.cargo.center.CargoWarehouseRegistry;
import com.example.examplemod.cargo.center.CargoShipmentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CargoTickerBlockEntity extends StockCheckingBlockEntity {
    public static final int MAX_REQUEST_COUNT = 4096;
    public enum RequestResult { DENIED, INVALID_REQUEST, NO_VALID_NEXT_POINT, INSUFFICIENT_STOCK, CREATE_REJECTED, DISPATCH_ATTEMPTED }
    public record StockEntry(ItemStack type, int available) {
        public StockEntry { type = type.copyWithCount(1); }
        @Override public ItemStack type() { return type.copy(); }
    }
    private CargoTickerSettings settings = new CargoTickerSettings(null, null, null);
    private UUID instanceId = UUID.randomUUID();
    public UUID instanceId() { return instanceId; }
    public UUID ownerId() { return settings.owner(); }

    public CargoTickerBlockEntity(BlockPos pos, BlockState state) { super(CargoBlocks.TICKER_ENTITY.get(), pos, state); }
    void placedBy(UUID owner) { settings = new CargoTickerSettings(owner, null, null); setChanged(); }

    private boolean mayUse(ServerPlayer player) {
        return level instanceof ServerLevel server && server.getServer().isSameThread()
                && player.getServer() == server.getServer() && player.level() == level
                && !isRemoved() && !isChunkUnloaded() && canPlayerUse(player) && behaviour.mayInteract(player);
    }

    /** Network administration and ownership of this Ticker are needed to choose its first cargo hop. */
    public boolean configureNextPoint(ServerPlayer player, UUID firstPoint) {
        if (!mayUse(player) || !player.getUUID().equals(settings.owner()) || !behaviour.mayAdministrate(player)
                || firstPoint == null || CargoPointRegistry.find(player.getServer(), firstPoint).isEmpty()) return false;
        settings = new CargoTickerSettings(settings.owner(), firstPoint, behaviour.freqId);
        setChanged();
        return true;
    }

    /** No shared mutable InventorySummary or ItemStack references escape Create's cache. */
    public Optional<List<StockEntry>> availableStock(ServerPlayer player) {
        if (!mayUse(player)) return Optional.empty();
        return Optional.of(getAccurateSummary().getStacks().stream()
                .filter(entry -> !entry.stack.isEmpty() && entry.count > 0)
                .map(entry -> new StockEntry(entry.stack, entry.count)).toList());
    }

    /** Registration delegates stock visibility to current city/company members while native owner authority stays valid. */
    public Optional<List<StockEntry>> warehouseStock(ServerPlayer player, UUID warehouseId) {
        var w = CargoWarehouseRegistry.get(player.getServer()).find(warehouseId).orElse(null);
        if (w == null || !CargoWarehouseRegistry.permits(player, w)
                || CargoWarehouseRegistry.ticker(player.getServer(), w) != this) return Optional.empty();
        var owner=CargoWarehouseRegistry.access(player.getServer(),w).owner();
        boolean delegated=(owner.kind()==com.example.examplemod.cargo.center.OwnerRef.Kind.CITY||owner.kind()==com.example.examplemod.cargo.center.OwnerRef.Kind.COMPANY)
            &&owner.operates(player.getServer(),player.getUUID())&&CargoWarehouseRegistry.nativeAuthorized(player.getServer(),w);
        if(!behaviour.mayInteract(player)&&!delegated)return Optional.empty();
        return Optional.of(getAccurateSummary().getStacks().stream().filter(e -> !e.stack.isEmpty() && e.count > 0)
                .map(e -> new StockEntry(e.stack, e.count)).toList());
    }

    /** Trusted fulfillment of an already persisted, funded shipment; still enforces live native network rights. */
    public RequestResult requestShipment(ServerPlayer operator, UUID warehouseId, UUID shipmentId) {
        if(warehouseStock(operator,warehouseId).isEmpty())return RequestResult.DENIED;
        return requestShipment(operator.getServer(),warehouseId,shipmentId);
    }

    public int shipmentStock(net.minecraft.server.MinecraftServer server,UUID warehouseId,UUID shipmentId){
        var shipment=CargoShipmentRegistry.get(server).find(shipmentId).orElse(null);
        var warehouse=CargoWarehouseRegistry.get(server).find(warehouseId).orElse(null);
        if(shipment==null||warehouse==null||!shipment.sourceWarehouse().equals(warehouseId)
                ||CargoWarehouseRegistry.ticker(server,warehouse)!=this||!CargoWarehouseRegistry.nativeAuthorized(server,warehouse))return 0;
        return getAccurateSummary().getCountOf(shipment.type());
    }

    /** Narrow persistent delegation for funded orders; all native lock/owner checks run again before broadcasting. */
    public RequestResult requestShipment(net.minecraft.server.MinecraftServer server,UUID warehouseId,UUID shipmentId){
        var shipment=CargoShipmentRegistry.get(server).find(shipmentId).orElse(null);
        if(shipment==null||shipmentStock(server,warehouseId,shipmentId)<shipment.quantity())return RequestResult.INSUFFICIENT_STOCK;
        var funded=com.example.examplemod.economy.EconomyManager.get(server.overworld()).cargo().order(shipmentId).orElse(null);
        if(funded==null||funded.state()!=com.example.examplemod.cargo.center.CargoCommerceBook.State.ATTEMPTED
                ||!CargoShipmentRegistry.get(server).claimPackaging(shipmentId))return RequestResult.DENIED;
        UUID next=shipment.route().isEmpty()?shipment.finalPoint():shipment.route().getFirst().next();
        var address=new CargoAddress(next,shipment.finalPoint(),shipmentId);
        var order=PackageOrderWithCrafts.simple(List.of(new BigItemStack(shipment.type(),shipment.quantity())));
        boolean attempted=LogisticsManager.broadcastPackageRequest(behaviour.freqId,RequestType.PLAYER,order,null,address.toString());
        return attempted?RequestResult.DISPATCH_ATTEMPTED:RequestResult.CREATE_REJECTED;
    }

    /** Delegates physical extraction/packaging to Create. The returned status is NEVER a delivery receipt. */
    public RequestResult requestItems(ServerPlayer player, ItemStack type, int count, UUID finalPoint) {
        if (!mayUse(player)) return RequestResult.DENIED;
        if (type == null || type.isEmpty() || count < 1 || count > MAX_REQUEST_COUNT || finalPoint == null)
            return RequestResult.INVALID_REQUEST;
        var registry = CargoPointRegistry.get(player.getServer());
        var address = settings.address(finalPoint, behaviour.freqId, id -> registry.point(id).isPresent());
        if (address.isEmpty()) return RequestResult.NO_VALID_NEXT_POINT;
        ItemStack requested = type.copyWithCount(1);
        if (getAccurateSummary().getCountOf(requested) < count) return RequestResult.INSUFFICIENT_STOCK;
        var order = PackageOrderWithCrafts.simple(List.of(new BigItemStack(requested, count)));
        boolean attempted = LogisticsManager.broadcastPackageRequest(behaviour.freqId, RequestType.PLAYER,
                order, null, address.get().toString());
        return attempted ? RequestResult.DISPATCH_ATTEMPTED : RequestResult.CREATE_REJECTED;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("CcecTickerInstance", instanceId);
        tag.put("CcecCargoTicker", settings.save());
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("CcecTickerInstance")) instanceId = tag.getUUID("CcecTickerInstance");
        if (tag.contains("CcecCargoTicker")) settings = CargoTickerSettings.load(tag.getCompound("CcecCargoTicker"));
    }
}
