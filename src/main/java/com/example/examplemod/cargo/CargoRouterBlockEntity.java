package com.example.examplemod.cargo;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.example.examplemod.cargo.center.CargoOwners;
import com.example.examplemod.cargo.center.OwnerRef;
import com.example.examplemod.cargo.center.CargoNavigation;
import com.example.examplemod.cargo.center.CargoShipmentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

/** Physical custody stays in one inventory; only manual assignment or a valid configured rule enables sending. */
public final class CargoRouterBlockEntity extends BlockEntity {
    public static final int SLOTS = CargoRouterData.SLOTS;
    private UUID owner;
    private UUID instanceId = UUID.randomUUID();
    private boolean automaticRouting;
    public boolean automaticRouting() { return automaticRouting; }
    public boolean configureAutomaticRouting(ServerPlayer actor, boolean enabled) {
        if (!ownerMayConfigure(actor)) return false;
        automaticRouting=enabled;invalidateAutomaticPreparations();setChanged();return true;
    }
    private final boolean[] ready = new boolean[SLOTS];
    private CargoRouterRules rules = new CargoRouterRules();
    private final ItemStackHandler buffer = new ItemStackHandler(SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return PackageItem.isPackage(stack); }
        @Override protected void onContentsChanged(int slot) {
            ready[slot] = false; rules.clearSlot(slot);
            try { CargoParcelIdentity.stamp(getStackInSlot(slot)); }
            catch (IllegalArgumentException invalidIdentity) { /* Keep malformed parcel physically buffered, fail closed on processing. */ }
            setChanged();
        }
    };
    private final IItemHandler input = new CargoRouterInputHandler(buffer, this::canReceiveAutomation, this::mayExtractArrived);

    /** All sides accept physical packages and expose only final ARRIVED parcels for extraction. */
    public IItemHandler inputHandler() { return input; }

    private boolean mayExtractArrived(int slot) {
        if (!(level instanceof ServerLevel server)) return false;
        ItemStack stack = buffer.getStackInSlot(slot);
        if (stack.getCount() != 1 || !PackageItem.isPackage(stack)) return false;
        try {
            var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            var parcel = CargoParcelIdentity.read(custom);
            if (parcel.isEmpty() || CargoDeliveryReceipts.find(server.getServer(), parcel.get()).isPresent()) return false;
            return CargoTransshipment.mayExtract(CargoAddress.parse(PackageItem.getAddress(stack)).orElse(null),
                    ownedPoint(server), CargoTransshipment.read(custom), ready[slot]);
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private boolean canReceiveAutomation() {
        return level instanceof ServerLevel server && server.getServer().isSameThread()
                && owner != null && !isRemoved() && server.hasChunkAt(worldPosition)
                && server.getBlockEntity(worldPosition) == this;
    }

    public CargoRouterBlockEntity(BlockPos pos, BlockState state) { super(CargoBlocks.ROUTER_ENTITY.get(), pos, state); }

    void claim(UUID player) { if (owner == null) { owner = player; setChanged(); } }

    public UUID ownerId() { return owner; }
    public OwnerRef logisticsOwner() {
        if (!(level instanceof ServerLevel server) || owner == null) throw new IllegalStateException("Unclaimed Router");
        return CargoOwners.router(server.getServer(), reference(), owner);
    }
    public CargoPointRegistry.RouterRef reference() {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread())
            throw new IllegalStateException("Router reference requires server thread and level");
        return new CargoPointRegistry.RouterRef(server.dimension().location(), worldPosition, instanceId);
    }
    public java.util.Optional<UUID> pointId() {
        if (!(level instanceof ServerLevel server)) return java.util.Optional.empty();
        return CargoPointRegistry.get(server.getServer()).pointForRouter(reference()).map(CargoPointRegistry.Point::id);
    }

    /** Returns a copy, never a mutable handle into the physical buffer. */
    public ItemStack packageInSlot(int slot) { return buffer.getStackInSlot(slot).copy(); }

    private boolean ownerMayConfigure(ServerPlayer actor) {
        return level instanceof ServerLevel server && server.getServer().isSameThread() && !isRemoved()
                && actor.getServer() == server.getServer() && owner != null && logisticsOwner().manages(server.getServer(), actor.getUUID())
                && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
    }

    public java.util.Map<UUID, UUID> dispatchRules() { return rules.snapshot(); }

    public java.util.Map<UUID, UUID> transferRules() { return rules.transferSnapshot(); }

    public boolean setTransferRule(ServerPlayer actor, UUID finalPoint, UUID nextPoint) {
        if (!ownerMayConfigure(actor)) return false;
        var points = CargoPointRegistry.get(actor.getServer());
        UUID current = ownedPoint((ServerLevel) level);
        if (!rules.putTransfer(owner, owner, current, finalPoint, nextPoint, id -> points.point(id).isPresent())) return false;
        setChanged(); return true;
    }

    public boolean removeTransferRule(ServerPlayer actor, UUID finalPoint) {
        if (!ownerMayConfigure(actor) || !rules.removeTransfer(owner, owner, finalPoint)) return false;
        setChanged(); return true;
    }

    private UUID ownedPoint(ServerLevel level) {
        return CargoPointRegistry.get(level.getServer()).pointForRouter(reference())
                .filter(point -> CargoOwners.point(level.getServer(), point).equals(logisticsOwner())).map(CargoPointRegistry.Point::id).orElse(null);
    }

    private CargoTransshipment.Outcome transferOutcome(ServerLevel level, ItemStack stack) {
        String raw = PackageItem.getAddress(stack);
        var address = CargoAddress.parse(raw);
        if (address.isEmpty()) return new CargoTransshipment.Outcome(null, null,
                CargoAddress.reserved(raw) ? CargoTransshipment.Status.BLOCKED : CargoTransshipment.Status.ORDINARY);
        try {
            var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            var points = CargoPointRegistry.get(level.getServer());
            var journey = CargoTransshipment.read(custom);
            var parcel = CargoParcelIdentity.read(custom);
            if (parcel.isPresent() && journey != null && journey.arrived()
                    && CargoDeliveryReceipts.find(level.getServer(), parcel.get())
                    .filter(receipt -> receipt.finalPoint().equals(address.get().finalPoint())).isPresent())
                return new CargoTransshipment.Outcome(address.get(), journey, CargoTransshipment.Status.DELIVERED);
            UUID next=rules.nextFor(address.get().finalPoint());
            if (next==null && automaticRouting) { var dispatch=CargoNavigation.dispatch(level.getServer(),this,address.get());if(dispatch!=null)next=dispatch.next(); }
            return CargoTransshipment.evaluate(address.get(), ownedPoint(level), journey,
                    next, id -> points.point(id).isPresent());
        } catch (IllegalArgumentException invalid) {
            return new CargoTransshipment.Outcome(address.get(), null, CargoTransshipment.Status.BLOCKED);
        }
    }

    public CargoTransshipment.Status parcelStatus(int slot) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread())
            throw new IllegalStateException("Parcel status requires server thread");
        var status = transferOutcome(server, buffer.getStackInSlot(slot)).status();
        return status == CargoTransshipment.Status.WAITING_TRAIN && ready[slot] ? CargoTransshipment.Status.READY : status;
    }

    private boolean processTransshipment(ServerLevel level, int slot) {
        ItemStack stack = buffer.getStackInSlot(slot);
        var outcome = transferOutcome(level, stack);
        if (outcome.status() == CargoTransshipment.Status.ORDINARY) return true;
        boolean changedAddress = outcome.address() != null && !PackageItem.getAddress(stack).equals(outcome.address().toString());
        if (outcome.journey() != null) {
            var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            var updated = custom.copy(); CargoParcelIdentity.ensure(updated); CargoTransshipment.write(updated, outcome.journey());
            if (changedAddress || !updated.equals(custom)) {
                boolean wasReady = ready[slot], wasAutomatic = rules.isAutomatic(slot);
                ItemStack proposed = stack.copy();
                proposed.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
                if (changedAddress) PackageItem.addAddress(proposed, outcome.address().toString());
                buffer.setStackInSlot(slot, proposed);
                if (!changedAddress && wasReady) { ready[slot] = true; if (wasAutomatic) rules.markAutomatic(slot); }
                setChanged();
            }
        }
        if (outcome.status() == CargoTransshipment.Status.WAITING_TRAIN) return true;
        if (ready[slot]) { ready[slot] = false; setChanged(); }
        return false;
    }

    /** One explicit next-point -> owned registered train mapping; replaces an existing mapping for that point. */
    public boolean setDispatchRule(ServerPlayer actor, UUID nextPoint, UUID trainId) {
        if (!ownerMayConfigure(actor) || nextPoint == null || trainId == null) return false;
        boolean pointExists = nextPoint != null && CargoPointRegistry.find(actor.getServer(), nextPoint).isPresent();
        boolean trainAllowed = CargoPackageAssignment.mayAssignTrain(actor.getServer(), logisticsOwner(), trainId);
        boolean changed = !java.util.Objects.equals(rules.snapshot().get(nextPoint), trainId);
        if (!rules.put(owner, owner, nextPoint, trainId, pointExists, trainAllowed)) return false;
        if (changed) { invalidateAutomaticPreparations(); setChanged(); }
        return true;
    }

    public boolean removeDispatchRule(ServerPlayer actor, UUID nextPoint) {
        if (!ownerMayConfigure(actor) || !rules.remove(owner, owner, nextPoint)) return false;
        invalidateAutomaticPreparations(); setChanged(); return true;
    }

    private void invalidateAutomaticPreparations() {
        for (int slot = 0; slot < SLOTS; slot++) if (rules.isAutomatic(slot)) ready[slot] = false;
    }

    private void prepareAutomatically(ServerLevel level) {
        var points = CargoPointRegistry.get(level.getServer());
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack original = buffer.getStackInSlot(slot);
            if (original.getCount() != 1 || !PackageItem.isPackage(original)) continue;
            boolean mayDispatch=processTransshipment(level, slot);
            try { CargoShipmentRegistry.observe(level.getServer(),pointId().orElse(null),new com.example.examplemod.cargo.center.CargoInfrastructure.Location(level.dimension().location(),worldPosition),buffer.getStackInSlot(slot),parcelStatus(slot)); }
            catch (IllegalArgumentException invalid) { /* Physical box stays buffered; malformed metadata cannot create progress. */ }
            if (!mayDispatch) continue;
            if (ready[slot] && !rules.isAutomatic(slot)) continue; // manual preparation always wins
            ItemStack stack = buffer.getStackInSlot(slot);
            UUID train = stack.getCount() == 1 && PackageItem.isPackage(stack)
                    ? rules.match(PackageItem.getAddress(stack), id -> points.point(id).isPresent(),
                            id -> CargoPackageAssignment.mayAssignTrain(level.getServer(), logisticsOwner(), id)).orElse(null)
                    : null;
            CargoNavigation.Dispatch service=null;
            if(train==null&&automaticRouting){service=CargoNavigation.dispatch(level.getServer(),this,CargoAddress.parse(PackageItem.getAddress(stack)).orElse(null));
                var addressed=CargoAddress.parse(PackageItem.getAddress(stack)).orElse(null);
                if(service!=null&&addressed!=null&&addressed.nextPoint().equals(service.next()))train=service.train();else service=null;}
            if (train == null) {
                if (ready[slot] && rules.isAutomatic(slot)) { ready[slot] = false; setChanged(); }
                continue;
            }
            if (ready[slot] && train.equals(CargoPackageAssignment.assignedTrain(stack))
                    && CargoPackageAssignment.readyForDispatch(level.getServer(),stack)) continue;
            ItemStack proposed = stack.copy();
            if (!(service==null?CargoPackageAssignment.assignBufferedPackage(level.getServer(), logisticsOwner(), proposed, train)
                    :CargoPackageAssignment.assignService(level.getServer(),proposed,service))) continue;
            // Do not rewrite the address: both next and final Cargo Point remain exactly as received.
            buffer.setStackInSlot(slot, proposed);
            ready[slot] = true; rules.markAutomatic(slot); setChanged();
        }
    }

    /** Explicit server action; the actor must own this Router and the registered next train. */
    public boolean assignNextHop(ServerPlayer actor, int slot, String nextAddress, UUID trainId) {
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread() || isRemoved()
                || actor.getServer() != serverLevel.getServer() || !ownerMayConfigure(actor)
                || slot < 0 || slot >= SLOTS || nextAddress == null || nextAddress.isBlank()
                || nextAddress.length() > 256 || nextAddress.indexOf('*') >= 0) return false;
        ItemStack proposed = buffer.getStackInSlot(slot).copy();
        if (!CargoAddress.mayForward(PackageItem.getAddress(proposed), nextAddress)
                || !registeredAddress(serverLevel, nextAddress)) return false;
        var requested = CargoAddress.parse(nextAddress);
        if (requested.isPresent()) {
            try {
                var custom = proposed.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                var journey = CargoTransshipment.manualPlan(CargoAddress.parse(PackageItem.getAddress(proposed)).orElse(null),
                        requested.get(), ownedPoint(serverLevel), CargoTransshipment.read(custom));
                CargoTransshipment.write(custom, journey);
                proposed.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            } catch (IllegalArgumentException invalid) { return false; }
        }
        if (!CargoPackageAssignment.assignBufferedPackage(serverLevel.getServer(), logisticsOwner(), proposed, trainId)) return false;
        PackageItem.addAddress(proposed, nextAddress);
        buffer.setStackInSlot(slot, proposed);
        ready[slot] = true;
        setChanged();
        return true;
    }

    /** Explicit initial/further CCEC leg. A supplied final point must agree with an existing CCEC address. */
    public boolean assignNextHop(ServerPlayer actor, int slot, UUID nextPoint, UUID finalPoint, UUID trainId) {
        if (nextPoint == null || finalPoint == null) return false;
        if(slot<0||slot>=SLOTS)return false;
        UUID shipment=CargoAddress.parse(PackageItem.getAddress(buffer.getStackInSlot(slot))).map(CargoAddress::shipment).orElse(null);
        return assignNextHop(actor, slot, new CargoAddress(nextPoint, finalPoint,shipment).toString(), trainId);
    }

    /** Preserve the existing final point, or use nextPoint as the final point on first assignment. */
    public boolean assignNextHop(ServerPlayer actor, int slot, UUID nextPoint, UUID trainId) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()
                || nextPoint == null || slot < 0 || slot >= SLOTS) return false;
        UUID finalPoint = CargoAddress.parse(PackageItem.getAddress(buffer.getStackInSlot(slot)))
                .map(CargoAddress::finalPoint).orElse(nextPoint);
        return assignNextHop(actor, slot, nextPoint, finalPoint, trainId);
    }

    private static boolean registeredAddress(ServerLevel level, String address) {
        if (!CargoAddress.reserved(address)) return true;
        return CargoAddress.parse(address).map(route -> {
            var registry = CargoPointRegistry.get(level.getServer());
            return registry.point(route.nextPoint()).isPresent() && registry.point(route.finalPoint()).isPresent();
        }).orElse(false);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CargoRouterBlockEntity router) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 10 != 0
                || router.isRemoved() || router.owner == null) return;
        router.prepareAutomatically(serverLevel);
        BlockPos above = pos.above();
        if (!level.hasChunkAt(above) || !(level.getBlockEntity(above) instanceof PostboxBlockEntity postbox)) return;
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return;
        String localAddress = postbox.getFilterString();
        if (localAddress == null) return;
        router.send(serverLevel, handler, localAddress);
        router.receive(handler);
    }

    private void send(ServerLevel level, IItemHandler postbox, String localAddress) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (!ready[slot] || stack.getCount() != 1 || !PackageItem.isPackage(stack)
                    || !registeredAddress(level, PackageItem.getAddress(stack))
                    || PackageItem.matchAddress(stack, localAddress)
                    || !CargoPackageAssignment.readyForDispatch(level.getServer(), stack)) continue;
            if (!ItemHandlerHelper.insertItemStacked(postbox, stack.copy(), true).isEmpty()) continue;
            // Single package: native IItemHandler can accept the whole unit or leave it here.
            if (ItemHandlerHelper.insertItemStacked(postbox, stack.copy(), false).isEmpty())
                buffer.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private void receive(IItemHandler postbox) {
        for (int source = 0; source < postbox.getSlots(); source++) {
            int destination = -1;
            for (int slot = 0; slot < SLOTS; slot++) if (buffer.getStackInSlot(slot).isEmpty()) { destination = slot; break; }
            if (destination < 0) return;
            // Create's wrapper exposes only incoming parcels and may ignore amount during simulation.
            ItemStack preview = postbox.extractItem(source, 1, true);
            if (preview.getCount() != 1 || !PackageItem.isPackage(preview)) continue;
            ItemStack extracted = postbox.extractItem(source, 1, false);
            if (!extracted.isEmpty()) buffer.setStackInSlot(destination, extracted);
        }
    }

    void dropBuffer() {
        if (level instanceof ServerLevel server) CargoPointRegistry.routerRemoved(server.getServer(), reference());
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            buffer.setStackInSlot(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5,
                    worldPosition.getY() + .5, worldPosition.getZ() + .5, stack);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("CargoRouterId", instanceId);
        tag.putBoolean("CargoAutomaticRouting",automaticRouting);
        tag.put("CargoDispatchRules", rules.save());
        tag.put("CargoRouter", CargoRouterData.save(owner, buffer.serializeNBT(registries), ready));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("CargoRouterId")) instanceId = tag.getUUID("CargoRouterId");
        automaticRouting=tag.getBoolean("CargoAutomaticRouting");
        if (!tag.contains("CargoRouter")) return; // fresh /setblock has no inventory payload
        var loaded = CargoRouterData.load(tag.getCompound("CargoRouter"));
        owner = loaded.owner();
        buffer.deserializeNBT(registries, loaded.packages());
        System.arraycopy(loaded.ready(), 0, ready, 0, SLOTS);
        rules = tag.contains("CargoDispatchRules") ? CargoRouterRules.load(tag.getCompound("CargoDispatchRules")) : new CargoRouterRules();
    }
}
