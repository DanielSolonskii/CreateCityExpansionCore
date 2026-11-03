package com.example.examplemod.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

/** Cancellation accounting is exercised through the production ledger and real persistence. */
final class FreightCancellationCheck {
    private static int checks;
    private static final ResourceLocation ITEM = ResourceLocation.parse("minecraft:iron_ingot");
    private static final ResourceLocation WORLD = ResourceLocation.parse("minecraft:overworld");

    private static void require(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static final class Physical implements EconomyManager.PhysicalTransaction {
        boolean fits = true, works = true;
        int commits, rollbacks;
        public boolean prepare() { return fits; }
        public boolean commit() { commits++; return works; }
        public void rollback() { rollbacks++; }
    }

    private static final class Fixture {
        EconomyManager manager = new EconomyManager();
        final UUID seller = new UUID(0, 1), buyer = new UUID(0, 2), player = UUID.randomUUID();
        final FreightPoint source = new FreightPoint(UUID.randomUUID(), seller, WORLD, BlockPos.ZERO,
                new BlockPos(2, 0, 0), "Source", FreightPoint.Mode.BOTH);
        final FreightPoint destination = new FreightPoint(UUID.randomUUID(), buyer, WORLD, new BlockPos(100, 0, 0),
                new BlockPos(102, 0, 0), "Destination", FreightPoint.Mode.BOTH);
        final IndustrialContract contract;
        final ShippingManifest manifest;
        Fixture(long amount, long reward, boolean community) throws Exception {
            source.status = destination.status = "online";
            manager.freight().points.put(source.id, source);
            manager.freight().points.put(destination.id, destination);
            contract = new IndustrialContract(UUID.randomUUID(), EconomyTypes.ContractKind.ORDINARY,
                    community ? EconomyTypes.ContractMode.COMMUNITY : EconomyTypes.ContractMode.EXCLUSIVE,
                    buyer, player, "Order", "", ITEM, amount, reward, destination.id, null, 0, 12000);
            contract.accept(seller, player, 1);
            put(manager, "contracts", contract.id(), contract);
            manifest = new ShippingManifest(UUID.randomUUID(), contract.id(), seller, amount, 12000);
            put(manager, "manifests", manifest.manifestId(), manifest);
            manager.freight().bind(contract.id(), source.id, destination);
            manager.freight().assignments.get(contract.id()).running = true;
        }
        UUID token(long amount) { return token(manifest, amount); }
        UUID token(ShippingManifest seal, long amount) {
            UUID[] token = {null};
            require(manager.authorizeExportPackage(seal.manifestId(), ITEM, amount, 2, id -> token[0] = id)
                    == EconomyTypes.IntakeAttributionError.NONE, "physical package authorized before cancellation");
            return token[0];
        }
        EconomyTypes.IntakeAttributionError accept(UUID token, long amount) {
            manager.freight().point(destination.id).status = "online";
            return manager.acceptShipmentPackage(destination.id, token, ITEM, amount, 1_000_000, false, new Physical());
        }
        EconomyTypes.IntakeAttributionError reclaim(UUID token, long amount, boolean simulate, Physical physical) {
            return manager.reclaimCancelledPackage(source.id, token, ITEM, amount, 1_000_000, simulate, physical);
        }
        void reload() throws Exception { manager = reloadManager(manager.save(new CompoundTag(), null)); }
    }

    static void run() throws Exception {
        Fixture mixed = new Fixture(128, 18000, false);
        UUID returned = mixed.token(32), delivered = mixed.token(32);
        require(mixed.manager.revokeDispatch(mixed.contract.id(), UUID.randomUUID()).equals("freight_revoke_denied"),
                "a foreign city cannot request cancellation");
        require(mixed.manager.revokeDispatchAsAdmin(mixed.contract.id()).equals("freight_cancel_pending"),
                "operator API requests the same physical closing semantics");
        require(mixed.manager.treasury(mixed.buyer) == 9000 && mixed.contract.escrow() == 9000,
                "only the unsealed half is refunded; sealed cargo keeps its reserve");
        require(mixed.manager.shipmentToken(returned).state() == EconomyTypes.ShipmentTokenState.ACTIVE,
                "cancellation never destroys an unresolved physical seal");
        require(mixed.manager.authorizeExportPackage(mixed.manifest.manifestId(), ITEM, 1, 3,
                id -> { throw new AssertionError("new seal after cancellation"); }) == EconomyTypes.IntakeAttributionError.INVALID_MANIFEST,
                "no additional physical package can be issued");
        require(mixed.manager.revokeDispatch(mixed.contract.id(), mixed.buyer).equals("freight_cancel_pending")
                && mixed.manager.treasury(mixed.buyer) == 9000, "repeat cancellation does not repeat its refund");
        Physical simulated = new Physical();
        require(mixed.reclaim(returned, 32, true, simulated) == EconomyTypes.IntakeAttributionError.NONE
                && simulated.commits == 0 && mixed.manager.cancellationOutstanding(mixed.contract.id()) == 64,
                "reclaim simulation preserves both proof and reserve");
        Physical blocked = new Physical(); blocked.fits = false;
        require(mixed.reclaim(returned, 32, false, blocked) == EconomyTypes.IntakeAttributionError.EXCESS_CARGO
                && blocked.commits == 0, "a full receiver changes nothing");
        Physical failed = new Physical(); failed.works = false;
        require(mixed.reclaim(returned, 32, false, failed) == EconomyTypes.IntakeAttributionError.EXCESS_CARGO
                && failed.rollbacks == 1 && mixed.manager.cancellationOutstanding(mixed.contract.id()) == 64,
                "failed physical reclaim retains the seal and escrow");
        require(mixed.manager.reclaimCancelledPackage(mixed.destination.id, returned, ITEM, 32, 4, false, new Physical())
                == EconomyTypes.IntakeAttributionError.WRONG_INTAKE, "only the original source may reclaim");
        require(mixed.reclaim(returned, 31, false, new Physical()) == EconomyTypes.IntakeAttributionError.EXCESS_CARGO,
                "a partial or forged box cannot redeem a whole token");
        var timeout = EconomyManager.class.getDeclaredMethod("processTimeouts", long.class); timeout.setAccessible(true);
        timeout.invoke(mixed.manager, 1_000_000L);
        require(mixed.manager.cancellationPending(mixed.contract.id()) && mixed.contract.escrow() == 9000,
                "ordinary timeout cannot refund cargo still in flight");
        mixed.reload();
        require(mixed.manager.cancellationPending(mixed.contract.id()) && mixed.manager.cancellationRefunded(mixed.contract.id()) == 9000,
                "closing intent and incremental refund survive restart");
        require(mixed.reclaim(returned, 32, false, new Physical()) == EconomyTypes.IntakeAttributionError.NONE,
                "real source reclaim resolves one physical package even after its deadline");
        require(mixed.manager.treasury(mixed.buyer) == 13500 && mixed.manager.contract(mixed.contract.id()).escrow() == 4500,
                "only actually reclaimed cargo releases its own reserve");
        require(mixed.accept(delivered, 32) == EconomyTypes.IntakeAttributionError.NONE,
                "an already sealed package can arrive after cancellation and deadline");
        require(mixed.manager.contract(mixed.contract.id()).state() == EconomyTypes.ContractState.CANCELLED
                && mixed.manager.treasury(mixed.seller) == 4500 && mixed.manager.contract(mixed.contract.id()).escrow() == 0,
                "accepted items are paid and the remaining order closes without inventing goods");
        require(mixed.reclaim(returned, 32, false, new Physical()) == EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED
                && mixed.accept(delivered, 32) == EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED,
                "neither returned nor delivered proof can be spent twice");
        mixed.reload();
        require(mixed.manager.revokeDispatchAsAdmin(mixed.contract.id()).equals("freight_revoke_already")
                && mixed.manager.treasury(mixed.buyer) + mixed.manager.treasury(mixed.seller) == 18000,
                "restart and admin replay conserve the exact original escrow");

        Fixture full = new Fixture(64, 101, false);
        UUID last = full.token(64);
        require(full.manager.revokeDispatch(full.contract.id(), full.buyer).equals("freight_cancel_pending"), "full sealed order waits");
        require(full.accept(last, 64) == EconomyTypes.IntakeAttributionError.NONE
                && full.contract.state() == EconomyTypes.ContractState.COMPLETED && full.manager.treasury(full.seller) == 101,
                "when all sealed goods really arrive the order completes and pays once");

        Fixture delayed = new Fixture(3, 5, false);
        UUID early = delayed.token(1), late = delayed.token(2);
        require(delayed.manager.revokeDispatch(delayed.contract.id(), delayed.buyer).equals("freight_cancel_pending"), "fully sealed order starts closing");
        require(delayed.accept(early, 1) == EconomyTypes.IntakeAttributionError.NONE && delayed.manager.treasury(delayed.seller) == 1,
                "first real receipt pays only its own share");
        put(delayed.manager,"treasuries",delayed.seller,Long.MAX_VALUE);
        require(delayed.accept(late, 2) == EconomyTypes.IntakeAttributionError.NONE && delayed.manager.cancellationPending(delayed.contract.id()),
                "physical final receipt survives a temporarily blocked money payout");
        require(!delayed.manager.disputeContract(delayed.contract.id(),delayed.buyer), "dispute cannot bypass a pending cancellation settlement");
        put(delayed.manager,"treasuries",delayed.seller,0L);
        require(delayed.manager.confirmContract(delayed.contract.id(),delayed.buyer)
                && !delayed.manager.cancellationPending(delayed.contract.id())
                && delayed.manager.cancellationPaid(delayed.contract.id()) == 5
                && delayed.manager.treasury(delayed.seller) == 4,
                "manual confirmation uses the same incremental receipt and closes without paying the first item twice");

        Fixture previous = new Fixture(64, 101, false);
        UUID previousToken = previous.token(32);
        CompoundTag oldSave=previous.manager.save(new CompoundTag(),null);oldSave.putInt("FormatVersion",12);oldSave.remove("FreightCancellations");
        EconomyManager migrated=reloadManager(oldSave);
        require(!migrated.cancellationRequested(previous.contract.id())&&migrated.contract(previous.contract.id()).escrow()==101
                && migrated.shipmentToken(previousToken).state()==EconomyTypes.ShipmentTokenState.ACTIVE
                && migrated.freight().tokenTargets(previousToken,previous.destination.id), "v12 migration preserves existing live cargo, destination and escrow");
        CompoundTag future=oldSave.copy();future.putInt("FormatVersion",14);
        try{reloadManager(future);throw new AssertionError("newer financial schema accepted");}
        catch(java.lang.reflect.InvocationTargetException expected){require(expected.getCause() instanceof IllegalStateException,"future financial format is rejected");}

        Fixture community = new Fixture(3, 5, true);
        UUID secondSeller = new UUID(0, 3);
        community.contract.accept(secondSeller, community.player, 1);
        ShippingManifest secondManifest = new ShippingManifest(UUID.randomUUID(), community.contract.id(), secondSeller, 3, 12000);
        put(community.manager, "manifests", secondManifest.manifestId(), secondManifest);
        UUID firstPart = community.token(1), secondPart = community.token(secondManifest, 1);
        require(community.manager.revokeDispatch(community.contract.id(), community.buyer).equals("freight_cancel_pending"), "community order closing");
        require(community.accept(firstPart, 1) == EconomyTypes.IntakeAttributionError.NONE
                && community.manager.treasury(community.seller) == 1, "first contributor receives a monotonic integer share");
        community.reload();
        require(community.accept(secondPart, 1) == EconomyTypes.IntakeAttributionError.NONE
                && community.manager.treasury(community.seller) == 1 && community.manager.treasury(secondSeller) == 2
                && community.manager.treasury(community.buyer) == 2, "final rounding is conserved without clawing back earlier payments");

        checkImport(false);
        checkImport(true);
        System.out.println("PASS: " + checks + " physical cancellation, held escrow, replay, rounding and restart checks");
    }

    private static void checkImport(boolean returnCargo) throws Exception {
        Fixture f = new Fixture(128, 18000, false);
        MarketOffer offer = new MarketOffer(UUID.randomUUID(), ITEM, ITEM, EconomyTypes.MarketDirection.BUY_FROM_MASTER,
                128, 10, 0, 12000, 7);
        require(offer.reserve(f.buyer, f.player, 128, 7) && offer.markDispatched(64), "import has real prepaid and dispatched quantities");
        put(f.manager, "marketOffers", offer.id(), offer);
        f.manager.freight().bind(offer.id(), f.source.id, f.destination);
        f.manager.freight().assignments.get(offer.id()).running = true;
        ShipmentToken token = new ShipmentToken(UUID.randomUUID(), null, offer.id(), EconomyTypes.ShipmentTokenKind.IMPORT_ORDER,
                f.seller, f.buyer, ITEM, 64, 1, 12000);
        put(f.manager, "shipmentTokens", token.tokenId(), token);
        f.manager.freight().tokenCreated(token.tokenId(), offer.id());
        put(f.manager, "treasuries", f.buyer, Long.MAX_VALUE);
        require(f.manager.revokeDispatch(offer.id(), f.buyer).equals("freight_revoke_state")
                && !f.manager.cancellationRequested(offer.id()) && offer.status() == EconomyTypes.MarketOfferStatus.DISPATCHED
                && token.state() == EconomyTypes.ShipmentTokenState.ACTIVE,
                "refund overflow is rejected before changing offer, intent, token or money");
        put(f.manager, "treasuries", f.buyer, 0L);
        require(f.manager.revokeDispatch(offer.id(), f.buyer).equals("freight_cancel_pending") && f.manager.treasury(f.buyer) == 640,
                "import refunds only goods that were never dispatched");
        require(!f.manager.dispatchFreightImport(offer.id(), 3, (id, item, count) -> { throw new AssertionError("new import box"); }),
                "closing import cannot mint new goods");
        f.reload();
        if (returnCargo) {
            require(f.manager.reclaimCancelledPackage(f.source.id, token.tokenId(), ITEM, 64, 1_000_000, false, new Physical())
                    == EconomyTypes.IntakeAttributionError.NONE && f.manager.treasury(f.buyer) == 1287,
                    "real return refunds remaining goods plus bond once");
            require(f.manager.warehouseStock(ITEM) == 0L, "physical returned goods are not also minted as virtual stock");
        } else {
            f.manager.freight().point(f.destination.id).status = "online";
            require(f.manager.acceptShipmentPackage(f.destination.id, token.tokenId(), ITEM, 64, 1_000_000, false, new Physical())
                    == EconomyTypes.IntakeAttributionError.NONE && f.manager.treasury(f.buyer) == 647,
                    "real arrival keeps its paid item price and returns only the final bond");
        }
        require(!f.manager.cancellationPending(offer.id()) && f.manager.marketOffer(offer.id()).status() == EconomyTypes.MarketOfferStatus.FAILED,
                "an import with a cancelled remainder closes only after physical resolution");
        f.reload();
        long balance = f.manager.treasury(f.buyer);
        require(f.manager.revokeDispatch(offer.id(), f.buyer).equals("freight_revoke_already") && f.manager.treasury(f.buyer) == balance,
                "import cancellation refund cannot repeat across restart");
    }

    @SuppressWarnings("unchecked")
    private static void put(EconomyManager manager, String name, Object key, Object value) throws Exception {
        var field = EconomyManager.class.getDeclaredField(name); field.setAccessible(true);
        ((Map<Object, Object>) field.get(manager)).put(key, value);
    }

    private static EconomyManager reloadManager(CompoundTag tag) throws Exception {
        var load = EconomyManager.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        return (EconomyManager) load.invoke(null, tag, null);
    }
}
