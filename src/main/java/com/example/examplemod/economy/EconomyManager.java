package com.example.examplemod.economy;

import com.example.examplemod.Config;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.railway.CityFleetTrain;
import com.example.examplemod.railway.CityRailStation;
import com.example.examplemod.railway.CreateRailwayAdapter;
import com.example.examplemod.railway.RailwayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

public final class EconomyManager extends SavedData {
    public record TransportPayout(boolean cityAccount, UUID ownerId, long amount) {}
    private static final String DATA_NAME = "createcityexpansioncore_economy";
    private static final int FORMAT_VERSION = 7;
    private final FreightLogistics freight = new FreightLogistics(this);
    public FreightLogistics freight() { return freight; }
    public synchronized boolean createFreightSale(UUID city,UUID creator,ResourceLocation item,long amount,long price,long deadline,String title,String description){
        if(!validContract(item,amount,price,deadline)||freight.sales.values().stream().filter(s->s.city().equals(city)&&s.contract()==null).count()>=64)return false;
        FreightSellOffer offer=new FreightSellOffer(UUID.randomUUID(),city,creator,item,amount,price,deadline,title,description,null);
        freight.sales.put(offer.id(),offer);setDirty();return true;
    }
    public synchronized boolean acceptFreightSale(UUID offerId,UUID buyer,UUID player,UUID destination,long now){
        FreightSellOffer offer=freight.sales.get(offerId);
        if(offer==null||offer.contract()!=null||offer.city().equals(buyer)||!freight.receiving(destination,buyer))return false;
        IndustrialContract contract=createCityContract(buyer,player,offer.title(),offer.description(),offer.item(),offer.amount(),offer.reward(),destination,null,offer.deadline(),now);
        if(contract==null)return false;
        contract.accept(offer.city(),offer.creator(),now);issueManifest(contract,offer.city());
        freight.sales.put(offerId,offer.accepted(contract.id()));setDirty();return true;
    }
    public synchronized boolean setupFreight(UUID subject,UUID city,UUID destination){
        IndustrialContract c=contracts.get(subject);
        MarketOffer imported=marketOffers.get(subject);
        if(c==null&&imported!=null&&imported.direction()==EconomyTypes.MarketDirection.BUY_FROM_MASTER&&city.equals(imported.acceptedCity())&&!imported.status().terminal()&&!freight.assignments.containsKey(subject)&&freight.receiving(destination,city)){
            FreightPoint source=freight.defaultSend(masterCityId);if(source==null)return false;
            freight.bind(subject,source.id,freight.point(destination));freight.assignments.get(subject).running=true;return true;
        }
        if(c==null||!c.issuerCityId().equals(city)||c.state().terminal()||freight.assignments.containsKey(subject)||!freight.receiving(destination,city))return false;
        freight.bind(subject,null,freight.point(destination));return true;
    }

    private final Map<UUID, Long> wallets = new LinkedHashMap<>();
    private final Map<UUID, Long> treasuries = new LinkedHashMap<>();
    private final Map<UUID, Long> transportReserves = new LinkedHashMap<>();
    private final Set<UUID> settledTransportTrips = new LinkedHashSet<>();
    private final Map<UUID, IndustrialContract> contracts = new LinkedHashMap<>();
    private final Map<UUID, PrivateListing> listings = new LinkedHashMap<>();
    private final Map<UUID, IndustrialShipment> shipments = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeShipmentByTrain = new HashMap<>();
    private final Map<UUID, EconomyInfrastructureRegistration> infrastructure = new LinkedHashMap<>();
    private final Map<ResourceLocation, SmartOrderProfile> smartProfiles = new LinkedHashMap<>();
    private final Map<ResourceLocation, Long> warehouseStock = new LinkedHashMap<>();
    private final Map<ResourceLocation, ExternalMarketState> marketStates = new LinkedHashMap<>();
    private final Map<UUID, MarketOffer> marketOffers = new LinkedHashMap<>();
    private final Map<UUID, UUID> offerByContract = new LinkedHashMap<>();
    private final Map<UUID, ShippingManifest> manifests = new LinkedHashMap<>();
    private final Map<UUID, ShipmentToken> shipmentTokens = new LinkedHashMap<>();
    private final Set<UUID> reputationAppliedOffers = new LinkedHashSet<>();
    private UUID masterCityId;
    private long procurementAvailable;
    private long procurementReserved;
    private long lastProcurementTick;

    public static EconomyManager get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EconomyManager::new, EconomyManager::load), DATA_NAME);
    }

    public long wallet(UUID playerId) { return wallets.getOrDefault(playerId, 0L); }
    public long treasury(UUID cityId) { return treasuries.getOrDefault(cityId, 0L); }
    public long treasuryEscrow(UUID cityId) {
        return contracts.values().stream().filter(c -> cityId.equals(c.issuerCityId()))
                .mapToLong(IndustrialContract::escrow).reduce(0L,
                        (left,right)->EconomyMath.saturatingAdd(left,right,Long.MAX_VALUE));
    }
    public long warehouseStock(ResourceLocation itemId) { return warehouseStock.getOrDefault(itemId, 0L); }
    public Map<ResourceLocation,Long> warehouseStocks() { return Map.copyOf(warehouseStock); }
    public UUID masterCityId() { return masterCityId; }
    public long procurementAvailable() { return procurementAvailable; }
    public long procurementReserved() { return procurementReserved; }
    public List<IndustrialContract> contracts() { return List.copyOf(contracts.values()); }
    public List<PrivateListing> listings() { return List.copyOf(listings.values()); }
    public List<IndustrialShipment> shipments(){return List.copyOf(shipments.values());}
    public List<IndustrialShipment> shipmentsForContract(UUID contractId){return shipments.values().stream().filter(value->value.contractId().equals(contractId)).toList();}
    public List<SmartOrderProfile> smartProfiles() { return List.copyOf(smartProfiles.values()); }
    public List<EconomyInfrastructureRegistration> infrastructure() { return List.copyOf(infrastructure.values()); }
    public List<MarketOffer> marketOffers() { return List.copyOf(marketOffers.values()); }
    public List<ShippingManifest> manifests() { return List.copyOf(manifests.values()); }
    public List<ShipmentToken> shipmentTokens() { return List.copyOf(shipmentTokens.values()); }
    @Nullable public MarketOffer marketOffer(UUID id) { return marketOffers.get(id); }
    @Nullable public IndustrialContract contract(UUID id) { return contracts.get(id); }
    @Nullable public PrivateListing listing(UUID id) { return listings.get(id); }
    @Nullable public IndustrialShipment shipment(UUID id){return shipments.get(id);}
    @Nullable public EconomyInfrastructureRegistration registration(UUID id) { return infrastructure.get(id); }
    @Nullable public ShippingManifest manifest(UUID id) { return manifests.get(id); }
    @Nullable public ShipmentToken shipmentToken(UUID id) { return shipmentTokens.get(id); }

    public boolean setMasterCity(@Nullable UUID cityId) {
        if (Objects.equals(masterCityId, cityId)) return false;
        masterCityId = cityId;
        setDirty();
        return true;
    }

    public boolean adminSetWallet(UUID playerId, long amount) {
        if (amount < 0L || amount > Config.ECONOMY_MAX_TRANSACTION.get()) return false;
        putBalance(wallets, playerId, amount); setDirty(); return true;
    }

    public boolean adminSetTreasury(UUID cityId, long amount) {
        if (amount < 0L || amount > Config.ECONOMY_MAX_TRANSACTION.get()) return false;
        putBalance(treasuries, cityId, amount); setDirty(); return true;
    }

    public synchronized boolean reserveTransport(UUID tripId, boolean cityAccount, UUID payerId, long amount) {
        if (amount < 0 || amount > Config.ECONOMY_MAX_TRANSACTION.get() || transportReserves.containsKey(tripId) || settledTransportTrips.contains(tripId)) return false;
        if (!debit(cityAccount ? treasuries : wallets, payerId, amount)) return false;
        transportReserves.put(tripId, amount);
        setDirty(); return true;
    }

    /** Settles an already debited reserve atomically; unused CR returns to the payer. */
    public synchronized boolean settleTransport(UUID tripId, boolean payerCity, UUID payerId, long reserved,
                                                List<TransportPayout> payouts) {
        if (reserved < 0 || settledTransportTrips.contains(tripId) || !Objects.equals(transportReserves.get(tripId), reserved)) return false;
        long paid = 0;
        Map<UUID,Long> walletCredits = new LinkedHashMap<>(), cityCredits = new LinkedHashMap<>();
        try {
            for (TransportPayout payout : payouts) {
                if (payout.amount() < 0) return false;
                paid = Math.addExact(paid, payout.amount());
                (payout.cityAccount() ? cityCredits : walletCredits).merge(payout.ownerId(), payout.amount(), Math::addExact);
            }
        } catch (ArithmeticException ex) { return false; }
        if (paid > reserved) return false;
        long refund = reserved - paid;
        (payerCity ? cityCredits : walletCredits).merge(payerId, refund, Math::addExact);
        for (var entry : walletCredits.entrySet()) if (!canCredit(wallets, entry.getKey(), entry.getValue())) return false;
        for (var entry : cityCredits.entrySet()) if (!canCredit(treasuries, entry.getKey(), entry.getValue())) return false;
        walletCredits.forEach((id, amount) -> credit(wallets, id, amount));
        cityCredits.forEach((id, amount) -> credit(treasuries, id, amount));
        transportReserves.remove(tripId); settledTransportTrips.add(tripId);
        setDirty(); return true;
    }

    public boolean pay(UUID sender, UUID receiver, long amount) {
        if (!validMoney(amount) || sender.equals(receiver) || !debit(wallets, sender, amount)) return false;
        try { credit(wallets, receiver, amount); }
        catch (ArithmeticException ex) { credit(wallets, sender, amount); return false; }
        setDirty(); return true;
    }

    public boolean depositToCity(UUID playerId, UUID cityId, long amount) {
        if (!validMoney(amount) || !debit(wallets, playerId, amount)) return false;
        try { credit(treasuries, cityId, amount); }
        catch (ArithmeticException ex) { credit(wallets, playerId, amount); return false; }
        setDirty(); return true;
    }

    public boolean withdrawFromCity(UUID cityId, UUID playerId, long amount) {
        if (!validMoney(amount) || !debit(treasuries, cityId, amount)) return false;
        try { credit(wallets, playerId, amount); }
        catch (ArithmeticException ex) { credit(treasuries, cityId, amount); return false; }
        setDirty(); return true;
    }

    @Nullable
    public PrivateListing createListing(EconomyTypes.ListingType type, UUID creator, ResourceLocation itemId,
                                        long amount, long price, String meetingText, long now) {
        return createListing(type, creator, itemId, amount, price, meetingText, now, null);
    }

    /**
     * Создание частного объявления.
     * <p>
     * Если {@code requestedItemId} не null — это barter (товар ↔ товар):
     * валюта не участвует, резерв не создаётся, комиссия не берётся.
     * Если null — работает прежняя логика «товар ↔ валюта» (BUY/SELL + escrow).
     */
    @Nullable
    public PrivateListing createListing(EconomyTypes.ListingType type, UUID creator, ResourceLocation itemId,
                                        long amount, long price, String meetingText, long now,
                                        @Nullable ResourceLocation requestedItemId) {
        if (itemId == null || !validItem(itemId)) return null;
        if (requestedItemId != null) {
            if (!validItem(requestedItemId) || !validAmount(amount) || !validAmount(price)) return null;
            PrivateListing barter = new PrivateListing(UUID.randomUUID(), EconomyTypes.ListingType.SELL, creator,
                    itemId, amount, price, meetingText, now, requestedItemId);
            listings.put(barter.id(), barter);
            setDirty();
            return barter;
        }
        if (!validAmount(amount) || !validMoney(price)) return null;
        long fee = EconomyMath.applyBasisPoints(price, Config.ECONOMY_PRIVATE_FEE_BPS.get());
        long required;
        try { required = Math.addExact(price, type == EconomyTypes.ListingType.BUY ? fee : 0L); }
        catch (ArithmeticException ex) { return null; }
        if (type == EconomyTypes.ListingType.BUY && !debit(wallets, creator, required)) return null;
        PrivateListing listing = new PrivateListing(UUID.randomUUID(), type, creator, itemId,
                amount, price, meetingText, now);
        if (type == EconomyTypes.ListingType.BUY && !listing.reserveEscrowOnly(price)) {
            credit(wallets, creator, required); return null;
        }
        listings.put(listing.id(), listing); setDirty(); return listing;
    }

    public boolean acceptListing(UUID listingId, UUID playerId) {
        PrivateListing listing = listings.get(listingId);
        if (listing == null || listing.state() != EconomyTypes.ListingState.OPEN || listing.creator().equals(playerId)) return false;
        if (listing.isBarter()) {
            if (!listing.reserve(playerId, 0L)) return false;
            setDirty(); return true;
        }
        long escrow = 0L;
        if (listing.type() == EconomyTypes.ListingType.SELL) {
            long fee = EconomyMath.applyBasisPoints(listing.price(), Config.ECONOMY_PRIVATE_FEE_BPS.get());
            long required;
            try { required = Math.addExact(listing.price(), fee); }
            catch (ArithmeticException ex) { return false; }
            if (!debit(wallets, playerId, required)) return false;
            escrow = listing.price();
        }
        if (!listing.reserve(playerId, escrow == 0L ? listing.price() : escrow)) {
            if (escrow > 0L) credit(wallets, playerId, escrow);
            return false;
        }
        setDirty(); return true;
    }

    public boolean markListingHanded(UUID listingId, UUID playerId) {
        PrivateListing listing = listings.get(listingId);
        if (listing == null || !listing.handOver(playerId)) return false;
        setDirty(); return true;
    }

    public boolean confirmListing(UUID listingId, UUID playerId) {
        PrivateListing listing = listings.get(listingId);
        if (listing == null || !canCredit(wallets,listing.supplier(),listing.escrow()) || !listing.complete(playerId)) return false;
        long escrow = listing.takeEscrow();
        credit(wallets, listing.supplier(), escrow);
        setDirty(); return true;
    }

    public boolean cancelListing(UUID listingId, UUID playerId) {
        PrivateListing listing = listings.get(listingId);
        if (listing == null || !canCredit(wallets,listing.buyer(),listing.escrow()) || !listing.cancel(playerId)) return false;
        long refund = listing.takeEscrow();
        UUID buyer = listing.buyer();
        if (refund > 0L && buyer != null) credit(wallets, buyer, refund);
        setDirty(); return true;
    }

    public boolean disputeListing(UUID listingId, UUID playerId) {
        PrivateListing listing = listings.get(listingId);
        if (listing == null || !listing.dispute(playerId)) return false;
        setDirty(); return true;
    }

    public boolean adminResolveListing(UUID listingId, boolean supplierWins) {
        PrivateListing listing=listings.get(listingId);
        UUID recipient=listing==null?null:supplierWins?listing.supplier():listing.buyer();
        if(listing==null||!canCredit(wallets,recipient,listing.escrow())||!listing.adminResolve(supplierWins))return false;
        long escrow=listing.takeEscrow();
        if(escrow>0L&&recipient!=null)credit(wallets,recipient,escrow);
        setDirty();return true;
    }

    @Nullable
    public IndustrialContract createCityContract(UUID issuerCityId, UUID creator, String title, String description,
                                                 ResourceLocation itemId, long amount, long reward,
                                                 UUID intakeRegistrationId, @Nullable UUID stationRegistrationId,
                                                 long deadlineTicks, long now) {
        if (!validContract(itemId, amount, reward, deadlineTicks)
                || !freight.receiving(intakeRegistrationId,issuerCityId)) return null;
        long fee = EconomyMath.applyBasisPoints(reward, Config.ECONOMY_CITY_FEE_BPS.get());
        long required;
        try { required = Math.addExact(reward, fee); } catch (ArithmeticException ex) { return null; }
        if (!debit(treasuries, issuerCityId, required)) return null;
        IndustrialContract contract = new IndustrialContract(UUID.randomUUID(), EconomyTypes.ContractKind.ORDINARY,
                EconomyTypes.ContractMode.EXCLUSIVE, issuerCityId, creator, title, description, itemId, amount,
                reward, intakeRegistrationId, stationRegistrationId, now, deadlineTicks);
        contracts.put(contract.id(), contract); freight.bind(contract.id(),null,freight.point(intakeRegistrationId)); setDirty(); return contract;
    }

    @Nullable
    public IndustrialContract createMasterSpecial(UUID creator, String title, String description,
                                                  ResourceLocation itemId, long amount, long reward,
                                                  UUID intakeRegistrationId, @Nullable UUID stationRegistrationId,
                                                  long deadlineTicks, EconomyTypes.ContractMode mode, long now) {
        if (masterCityId == null || !validContract(itemId, amount, reward, deadlineTicks)) return null;
        if (!freight.receiving(intakeRegistrationId,masterCityId)
                || !debit(treasuries, masterCityId, reward)) return null;
        IndustrialContract contract = new IndustrialContract(UUID.randomUUID(), EconomyTypes.ContractKind.MASTER_SPECIAL,
                mode, masterCityId, creator, title, description, itemId, amount, reward,
                intakeRegistrationId, stationRegistrationId, now, deadlineTicks);
        contracts.put(contract.id(), contract); freight.bind(contract.id(),null,freight.point(intakeRegistrationId)); setDirty(); return contract;
    }

    public boolean acceptContract(UUID contractId, UUID cityId, UUID playerId, long now) {
        return false; // All interactive acceptance must select a sending freight point.
    }

    public synchronized boolean acceptFreightContract(UUID contractId, UUID cityId, UUID playerId, long now, UUID source) {
        IndustrialContract contract = contracts.get(contractId);
        FreightAssignment assignment=freight.assignments.get(contractId);
        if(contract==null||assignment==null||!freight.sending(source,cityId,contractId)||!freight.receiving(assignment.destination,contract.issuerCityId()))return false;
        if(assignment.source!=null)return false;
        if (contract == null || !contract.accept(cityId, playerId, now)) return false;
        assignment.source=source;
        issueManifest(contract, cityId);
        setDirty(); return true;
    }

    @Nullable
    public synchronized ShippingManifest manifestForContract(UUID contractId, UUID contractorCityId) {
        return manifests.values().stream()
                .filter(value -> value.contractId().equals(contractId)
                        && value.contractorCityId().equals(contractorCityId))
                .findFirst().orElse(null);
    }

    @Nullable
    public synchronized ShippingManifest manifestForOffer(UUID offerId, UUID contractorCityId) {
        return offerByContract.entrySet().stream().filter(entry -> entry.getValue().equals(offerId))
                .map(entry -> manifestForContract(entry.getKey(), contractorCityId))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private ShippingManifest issueManifest(IndustrialContract contract, UUID contractorCityId) {
        ShippingManifest existing = manifestForContract(contract.id(), contractorCityId);
        if (existing != null) return existing;
        ShippingManifest created = new ShippingManifest(UUID.randomUUID(), contract.id(), contractorCityId,
                contract.remainingAmount(), contract.deadlineAt());
        manifests.put(created.manifestId(), created);
        return created;
    }

    public synchronized EconomyTypes.IntakeAttributionError authorizeExportPackage(
            UUID manifestId, ResourceLocation itemId, long amount, long now,
            java.util.function.Consumer<UUID> packageMarker) {
        ShippingManifest manifest = manifests.get(manifestId);
        if (manifest == null || manifest.state() != EconomyTypes.ManifestState.ACTIVE)
            return EconomyTypes.IntakeAttributionError.INVALID_MANIFEST;
        IndustrialContract contract = contracts.get(manifest.contractId());
        FreightAssignment dispatch=freight.assignments.get(manifest.contractId());
        if(dispatch!=null&&(!dispatch.running||freight.point(dispatch.source)==null||!freight.point(dispatch.source).online()))
            return EconomyTypes.IntakeAttributionError.INVALID_MANIFEST;
        EconomyTypes.IntakeAttributionError error = validateExportAuthorization(manifest, contract, itemId, amount, now);
        if (error != EconomyTypes.IntakeAttributionError.NONE) return error;
        long outstanding = shipmentTokens.values().stream()
                .filter(token -> token.kind() == EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT
                        && token.subjectId().equals(contract.id())
                        && token.state() == EconomyTypes.ShipmentTokenState.ACTIVE)
                .mapToLong(ShipmentToken::amount).reduce(0L, EconomyMath::addExactNonNegative);
        if (amount > Math.max(0L, contract.remainingAmount() - Math.min(contract.remainingAmount(), outstanding)))
            return EconomyTypes.IntakeAttributionError.EXCESS_CARGO;
        UUID tokenId = UUID.randomUUID();
        packageMarker.accept(tokenId);
        ShipmentToken token = new ShipmentToken(tokenId, manifest.manifestId(), contract.id(),
                EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT, manifest.contractorCityId(), contract.issuerCityId(),
                itemId, amount, now, manifest.expiresAt());
        shipmentTokens.put(tokenId, token);
        freight.tokenCreated(tokenId,contract.id());
        manifest.dispatched(amount);
        contract.markShipment(manifest.contractorCityId());
        setDirty();
        return EconomyTypes.IntakeAttributionError.NONE;
    }

    private EconomyTypes.IntakeAttributionError validateExportAuthorization(
            ShippingManifest manifest, @Nullable IndustrialContract contract,
            ResourceLocation itemId, long amount, long now) {
        if (contract == null || contract.state() == EconomyTypes.ContractState.CANCELLED
                || contract.state() == EconomyTypes.ContractState.FAILED
                || contract.state() == EconomyTypes.ContractState.DISPUTED)
            return EconomyTypes.IntakeAttributionError.CONTRACT_INVALID;
        if (contract.state() == EconomyTypes.ContractState.COMPLETED
                || contract.state() == EconomyTypes.ContractState.DELIVERED)
            return EconomyTypes.IntakeAttributionError.CONTRACT_COMPLETED;
        if (contract.deadlineAt() > 0L && now > contract.deadlineAt())
            return EconomyTypes.IntakeAttributionError.CONTRACT_EXPIRED;
        if (!contract.itemId().equals(itemId)) return EconomyTypes.IntakeAttributionError.WRONG_ITEM;
        boolean participant = contract.mode() == EconomyTypes.ContractMode.EXCLUSIVE
                ? manifest.contractorCityId().equals(contract.contractorCityId())
                : contract.participants().contains(manifest.contractorCityId());
        if (!participant) return EconomyTypes.IntakeAttributionError.WRONG_CONTRACTOR;
        if (amount <= 0L || amount > manifest.remainingToDispatch() || amount > contract.remainingAmount())
            return EconomyTypes.IntakeAttributionError.EXCESS_CARGO;
        return EconomyTypes.IntakeAttributionError.NONE;
    }

    @Nullable
    public IndustrialShipment createShipment(UUID contractId,UUID cityId,UUID playerId,UUID fleetTrainId,
                                               long declaredAmount,long now,RailwayManager railway){
        return null;
    }

    public boolean cancelShipment(UUID shipmentId,UUID cityId){IndustrialShipment shipment=shipments.get(shipmentId);if(shipment==null||!shipment.contractorCityId().equals(cityId)||!shipment.cancel())return false;activeShipmentByTrain.remove(shipment.fleetTrainId(),shipment.shipmentId());setDirty();return true;}

    public boolean confirmContract(UUID contractId, UUID issuerCityId) {
        IndustrialContract contract = contracts.get(contractId);
        if (contract == null || !contract.issuerCityId().equals(issuerCityId) || !completeContract(contract)) return false;
        setDirty(); return true;
    }

    public boolean cancelContract(UUID contractId, UUID issuerCityId) {
        IndustrialContract contract = contracts.get(contractId);
        if (contract == null || !contract.issuerCityId().equals(issuerCityId)
                || (contract.kind()!=EconomyTypes.ContractKind.MASTER_SMART
                && !canCredit(treasuries,contract.issuerCityId(),contract.escrow())) || !contract.cancelOpen()) return false;
        long refund = contract.takeEscrow();
        if (contract.kind() == EconomyTypes.ContractKind.MASTER_SMART) {
            procurementReserved = Math.max(0L, procurementReserved - refund);
            procurementAvailable = EconomyMath.saturatingAdd(procurementAvailable, refund, procurementCap());
        } else credit(treasuries, contract.issuerCityId(), refund);
        invalidateContractLogistics(contract.id(), EconomyTypes.ManifestState.INVALID);setDirty(); return true;
    }

    public boolean disputeContract(UUID contractId, UUID issuerCityId) {
        IndustrialContract contract = contracts.get(contractId);
        if (contract == null || !contract.issuerCityId().equals(issuerCityId) || !contract.dispute()) return false;
        setDirty(); return true;
    }

    /**
     * Cancel a freight dispatch that is still open — the repair counterpart to rebind.
     *
     * <p>Only legal while the trade can still move back without rolling back cargo the receiving
     * city already accepted. Escrow returns through the same path {@link #processTimeouts} uses and
     * {@link IndustrialContract#takeEscrow()} zeroes it, so a replay refunds nothing; unused
     * ShipmentTokens and the shipping manifest are invalidated, which is what makes a late or
     * replayed package after a revoke fail validation instead of settling twice.
     *
     * @return a translation-safe message key under {@code message.createcityexpansioncore.economy.}
     */
    public synchronized String revokeDispatch(UUID subject, UUID cityId) {
        if (subject == null || cityId == null) return "freight_revoke_unknown";
        IndustrialContract contract = contracts.get(subject);
        if (contract != null) return revokeContract(contract, cityId);
        MarketOffer offer = marketOffers.get(subject);
        if (offer != null) return revokeImport(offer, cityId);
        return "freight_revoke_unknown";
    }

    private String revokeContract(IndustrialContract contract, UUID cityId) {
        FreightAssignment assignment = freight.assignments.get(contract.id());
        FreightPoint source = assignment == null ? null : freight.point(assignment.source);
        FreightPoint destination = assignment == null ? null : freight.point(assignment.destination);
        boolean issuer = cityId.equals(contract.issuerCityId());
        boolean endpoint = source != null && cityId.equals(source.cityId) || destination != null && cityId.equals(destination.cityId);
        if (!issuer && !endpoint) return "freight_revoke_denied";
        // Terminal already: escrow was taken by a previous revoke/settlement, so this is a no-op.
        if (contract.state() == EconomyTypes.ContractState.DELIVERED
                || contract.state() == EconomyTypes.ContractState.DISPUTED) return "freight_revoke_state";
        if (contract.state().terminal()) return "freight_revoke_already";
        if (!contract.revoke()) return "freight_revoke_state";
        releaseContractEscrow(contract);
        failActiveShipments(contract.id());
        invalidateContractLogistics(contract.id(), EconomyTypes.ManifestState.INVALID);
        freight.stopDispatch(contract.id());
        freight.markChanged();
        setDirty();
        return "freight_revoked";
    }

    private String revokeImport(MarketOffer offer, UUID cityId) {
        if (offer.direction() != EconomyTypes.MarketDirection.BUY_FROM_MASTER
                || offer.acceptedCity() == null || !offer.acceptedCity().equals(cityId)) return "freight_revoke_denied";
        // Reserved offers have nothing dispatched yet; already-terminal ones refunded on their own path.
        if (offer.status().terminal()) return "freight_revoke_already";
        if (offer.status() == EconomyTypes.MarketOfferStatus.OPEN) return "freight_revoke_state";
        MarketDefinition definition = marketDefinition(offer.definitionId());
        ExternalMarketState state = marketStates.get(offer.definitionId());
        long cancelled = offer.remaining();
        if (definition != null && state != null && cancelled > 0L) state.release(offer.direction(), cancelled, definition);
        if (cancelled > 0L) {
            long refund;
            try { refund = EconomyMath.multiplyExactPositive(cancelled, offer.unitPrice()); }
            catch (RuntimeException ex) { refund = 0L; }
            refund = EconomyMath.saturatingAdd(refund, offer.bond(), Long.MAX_VALUE);
            credit(treasuries, offer.acceptedCity(), refund);
        }
        offer.restore(offer.remaining(), offer.acceptedCity(), offer.acceptedBy(),
                EconomyTypes.MarketOfferStatus.FAILED, offer.acceptedQuantity(), offer.dispatchedAmount());
        invalidateImportTokens(offer.id());
        freight.stopDispatch(offer.id());
        freight.markChanged();
        setDirty();
        return "freight_revoked";
    }

    /** Releases escrow exactly once, mirroring the existing contract-timeout refund branch. */
    private void releaseContractEscrow(IndustrialContract contract) {
        MarketOffer linked = offerByContract.get(contract.id()) == null ? null : marketOffers.get(offerByContract.get(contract.id()));
        long refund = contract.takeEscrow();
        if (linked != null) {
            procurementReserved = Math.max(0L, procurementReserved - refund);
            procurementAvailable = EconomyMath.saturatingAdd(procurementAvailable, refund, procurementCap());
            MarketDefinition definition = marketDefinition(linked.definitionId());
            ExternalMarketState state = marketStates.get(linked.definitionId());
            if (definition != null && state != null) state.release(linked.direction(), linked.remaining(), definition);
            linked.restore(linked.remaining(), linked.acceptedCity(), linked.acceptedBy(), EconomyTypes.MarketOfferStatus.FAILED);
        } else if (contract.kind() == EconomyTypes.ContractKind.MASTER_SMART) {
            procurementReserved = Math.max(0L, procurementReserved - refund);
            procurementAvailable = EconomyMath.saturatingAdd(procurementAvailable, refund, procurementCap());
        } else if (refund > 0L) {
            credit(treasuries, contract.issuerCityId(), refund);
        }
    }

    public long recordIntakeFlow(UUID registrationId, ResourceLocation itemId, long amount, ServerLevel level) {
        EconomyInfrastructureRegistration intake = infrastructure.get(registrationId);
        if (intake == null || !intake.available() || intake.type() != EconomyTypes.InfrastructureType.CONTRACT_INTAKE)
            return 0L;
        return attributionFailure(intake, EconomyTypes.IntakeAttributionError.INVALID_SHIPMENT, level.getGameTime());
    }

    private long attributionFailure(EconomyInfrastructureRegistration intake,EconomyTypes.IntakeAttributionError error,long now){if(intake.lastAttributionError()!=error||now-intake.lastAttributionErrorAt()>100L){intake.attributionError(error,now);setDirty();}return 0L;}

    public interface PhysicalTransaction {
        boolean prepare();
        boolean commit();
        void rollback();
    }

    public synchronized EconomyTypes.IntakeAttributionError acceptShipmentPackage(
            UUID registrationId, UUID tokenId, ResourceLocation itemId, long amount,
            long now, boolean simulate, PhysicalTransaction physical) {
        EconomyInfrastructureRegistration intake=infrastructure.get(registrationId);
        ShipmentToken token=shipmentTokens.get(tokenId);
        EconomyTypes.IntakeAttributionError error=validateShipmentPackage(registrationId,intake,token,itemId,amount,now);
        if(error!=EconomyTypes.IntakeAttributionError.NONE)return error;
        if(!physical.prepare())return EconomyTypes.IntakeAttributionError.EXCESS_CARGO;
        if(simulate)return EconomyTypes.IntakeAttributionError.NONE;
        if(!physical.commit()){physical.rollback();return EconomyTypes.IntakeAttributionError.EXCESS_CARGO;}
        boolean applied;
        if(token.kind()==EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT){
            IndustrialContract contract=contracts.get(token.subjectId());
            long accepted=contract.recordDelivery(token.senderCityId(),itemId,amount,now);
            applied=accepted==amount;
            if(applied&&token.manifestId()!=null){ShippingManifest manifest=manifests.get(token.manifestId());if(manifest!=null)manifest.accepted(amount);}
            if(applied){
                UUID linkedOfferId=offerByContract.get(contract.id());
                MarketOffer linkedOffer=linkedOfferId==null?null:marketOffers.get(linkedOfferId);
                if(linkedOffer!=null){
                    linkedOffer.applyPhysical(amount);
                    // Master warehouse is the accounting source for future physical imports.
                    // Credit only after the destination transaction committed; token replay is
                    // rejected before this point, so each physical stack is accounted once.
                    if(linkedOffer.direction()==EconomyTypes.MarketDirection.SELL_TO_MASTER)
                        putStock(warehouseStock,itemId,EconomyMath.saturatingAdd(warehouseStock(itemId),amount,Long.MAX_VALUE));
                }
                if(contract.state()==EconomyTypes.ContractState.DELIVERED)completeContract(contract);
            }
        }else{
            MarketOffer offer=marketOffers.get(token.subjectId());applied=offer!=null&&offer.receiveImport(amount);
            if(applied&&offer.status()==EconomyTypes.MarketOfferStatus.COMPLETED)finishMarketOffer(offer);
        }
        if(!applied){physical.rollback();return EconomyTypes.IntakeAttributionError.CONTRACT_INVALID;}
        token.consume(now);
        if(token.manifestId()!=null){ShippingManifest manifest=manifests.get(token.manifestId());if(manifest!=null&&manifest.acceptedAmount()>=manifest.authorizedAmount())manifest.complete();}
        if(intake!=null)intake.attributionError(EconomyTypes.IntakeAttributionError.NONE,now);freight.markChanged();setDirty();
        return EconomyTypes.IntakeAttributionError.NONE;
    }

    private EconomyTypes.IntakeAttributionError validateShipmentPackage(
            UUID receiverId,@Nullable EconomyInfrastructureRegistration intake,@Nullable ShipmentToken token,
            ResourceLocation itemId,long amount,long now){
        if(token==null||token.state()==EconomyTypes.ShipmentTokenState.INVALID)return EconomyTypes.IntakeAttributionError.INVALID_SHIPMENT;
        if(token.state()==EconomyTypes.ShipmentTokenState.CONSUMED)return EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED;
        FreightPoint receiver=freight.point(receiverId);
        UUID receiverCity;
        if(freight.isFreightToken(token.tokenId())){
            if(receiver==null||!receiver.online()||!receiver.mode.receives()||!freight.tokenTargets(token.tokenId(),receiverId))return EconomyTypes.IntakeAttributionError.WRONG_INTAKE;
            receiverCity=receiver.cityId;
        }else{
            if(intake==null||!intake.available()||intake.type()!=EconomyTypes.InfrastructureType.CONTRACT_INTAKE)return EconomyTypes.IntakeAttributionError.WRONG_INTAKE;
            receiverCity=intake.cityId();
        }
        if(!Objects.equals(receiverCity,token.receiverCityId()))return EconomyTypes.IntakeAttributionError.WRONG_RECEIVING_CITY;
        if(!token.itemId().equals(itemId))return EconomyTypes.IntakeAttributionError.WRONG_ITEM;
        if(amount<=0L||amount!=token.amount())return EconomyTypes.IntakeAttributionError.EXCESS_CARGO;
        if(token.expiresAt()>0L&&now>token.expiresAt())return token.kind()==EconomyTypes.ShipmentTokenKind.IMPORT_ORDER?EconomyTypes.IntakeAttributionError.IMPORT_ORDER_INVALID:EconomyTypes.IntakeAttributionError.CONTRACT_EXPIRED;
        if(token.kind()==EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT){
            IndustrialContract contract=contracts.get(token.subjectId());ShippingManifest manifest=token.manifestId()==null?null:manifests.get(token.manifestId());
            if(manifest==null||manifest.state()!=EconomyTypes.ManifestState.ACTIVE)return EconomyTypes.IntakeAttributionError.INVALID_MANIFEST;
            if(!manifest.contractId().equals(token.subjectId())||!manifest.contractorCityId().equals(token.senderCityId()))return EconomyTypes.IntakeAttributionError.INVALID_SHIPMENT;
            if(contract==null||contract.state()==EconomyTypes.ContractState.CANCELLED||contract.state()==EconomyTypes.ContractState.FAILED||contract.state()==EconomyTypes.ContractState.DISPUTED)return EconomyTypes.IntakeAttributionError.CONTRACT_INVALID;
            if(contract.state()==EconomyTypes.ContractState.COMPLETED||contract.state()==EconomyTypes.ContractState.DELIVERED)return EconomyTypes.IntakeAttributionError.CONTRACT_COMPLETED;
            if(contract.deadlineAt()>0L&&now>contract.deadlineAt())return EconomyTypes.IntakeAttributionError.CONTRACT_EXPIRED;
            if(!freight.isFreightToken(token.tokenId())&&!contract.destinationRegistrationId().equals(receiverId))return EconomyTypes.IntakeAttributionError.WRONG_INTAKE;
            boolean participant=contract.mode()==EconomyTypes.ContractMode.EXCLUSIVE?token.senderCityId().equals(contract.contractorCityId()):contract.participants().contains(token.senderCityId());
            if(!participant)return EconomyTypes.IntakeAttributionError.WRONG_CONTRACTOR;
            if(amount>contract.remainingAmount())return EconomyTypes.IntakeAttributionError.EXCESS_CARGO;
        }else{
            MarketOffer offer=marketOffers.get(token.subjectId());
            if(offer==null||offer.direction()!=EconomyTypes.MarketDirection.BUY_FROM_MASTER||offer.acceptedCity()==null||!offer.acceptedCity().equals(receiverCity)||(offer.status()!=EconomyTypes.MarketOfferStatus.DISPATCHED&&offer.status()!=EconomyTypes.MarketOfferStatus.IN_PROGRESS))return EconomyTypes.IntakeAttributionError.IMPORT_ORDER_INVALID;
            if(amount>offer.remaining())return EconomyTypes.IntakeAttributionError.EXCESS_CARGO;
        }
        return EconomyTypes.IntakeAttributionError.NONE;
    }

    public record ImportDispatch(UUID tokenId,ResourceLocation itemId,int amount){}

    @FunctionalInterface public interface ImportPackageSink { void place(UUID token,ResourceLocation item,int count); }
    public synchronized boolean dispatchFreightImport(UUID offerId,long now,ImportPackageSink sink){
        MarketOffer offer=marketOffers.get(offerId);FreightAssignment assignment=freight.assignments.get(offerId);
        if(offer==null||assignment==null||!assignment.running||offer.direction()!=EconomyTypes.MarketDirection.BUY_FROM_MASTER||offer.availableToDispatch()<=0||offer.status().terminal()||now>offer.expiresAt())return false;
        int maximum=BuiltInRegistries.ITEM.get(offer.itemId()).getDefaultMaxStackSize();
        int amount=(int)Math.min(maximum,Math.min(offer.availableToDispatch(),warehouseStock(offer.itemId())));if(amount<=0)return false;
        UUID tokenId=UUID.randomUUID();
        sink.place(tokenId,offer.itemId(),amount);
        if(!offer.markDispatched(amount))throw new IllegalStateException("Validated import dispatch changed on server thread");
        putStock(warehouseStock,offer.itemId(),warehouseStock(offer.itemId())-amount);
        shipmentTokens.put(tokenId,new ShipmentToken(tokenId,null,offer.id(),EconomyTypes.ShipmentTokenKind.IMPORT_ORDER,masterCityId,offer.acceptedCity(),offer.itemId(),amount,now,offer.expiresAt()));
        freight.tokenCreated(tokenId,offer.id());setDirty();return true;
    }

    @Nullable public synchronized ImportDispatch dispatchNextImport(UUID registrationId,int maximum,long now){
        // v5 gateway API retained for save/block compatibility; dispatch is owned by FreightLogistics.
        return null;
    }

    public long maximumGatewayOutput(UUID registrationId, ResourceLocation itemId, long requested) {
        return 0L;
    }

    public long recordGatewayFlow(UUID registrationId, EconomyTypes.GatewayMode mode,
                                  ResourceLocation itemId, long amount) {
        EconomyInfrastructureRegistration gateway = infrastructure.get(registrationId);
        if (gateway == null || !gateway.available() || gateway.type() != EconomyTypes.InfrastructureType.WAREHOUSE_GATEWAY
                || masterCityId == null || !masterCityId.equals(gateway.cityId()) || amount <= 0L) return 0L;
        long current = warehouseStock(itemId);
        long changed;
        if (mode == EconomyTypes.GatewayMode.INPUT) {
            changed = amount;
            warehouseStock.put(itemId, EconomyMath.saturatingAdd(current, amount, Long.MAX_VALUE));
            contracts.values().stream()
                    .filter(contract -> contract.kind() == EconomyTypes.ContractKind.MASTER_SMART
                            && contract.state() == EconomyTypes.ContractState.DELIVERED
                            && contract.itemId().equals(itemId))
                    .findFirst().ifPresent(this::completeContract);
        } else changed = 0L;
        if (changed > 0L) setDirty();
        return changed;
    }

    public EconomyInfrastructureRegistration registerInfrastructure(UUID physicalId, UUID playerId,
                                                                    EconomyTypes.InfrastructureType type,
                                                                    ResourceLocation dimension, BlockPos pos) {
        EconomyInfrastructureRegistration existing = infrastructure.values().stream()
                .filter(value -> value.physicalInstanceId().equals(physicalId)).findFirst().orElse(null);
        if (existing != null) {
            existing.locate(dimension, pos); setDirty(); return existing;
        }
        EconomyInfrastructureRegistration created = new EconomyInfrastructureRegistration(UUID.randomUUID(),
                physicalId, playerId, type, dimension, pos);
        infrastructure.put(created.registrationId(), created); setDirty(); return created;
    }

    public boolean bindInfrastructure(UUID registrationId, UUID playerId, UUID cityId) {
        EconomyInfrastructureRegistration registration = infrastructure.get(registrationId);
        if (registration == null || !registration.registeredBy().equals(playerId) || !registration.bind(cityId)) return false;
        setDirty(); return true;
    }

    public boolean bindIntakeStation(UUID registrationId,UUID cityId,UUID stationId,RailwayManager railway){
        EconomyInfrastructureRegistration registration=infrastructure.get(registrationId);CityRailStation station=railway.station(stationId);
        if(registration==null||registration.type()!=EconomyTypes.InfrastructureType.CONTRACT_INTAKE||!cityId.equals(registration.cityId())
                ||station==null||!cityId.equals(station.cityId())
                ||(station.type()!=com.example.examplemod.railway.RailwayTypes.StationType.FREIGHT
                &&station.type()!=com.example.examplemod.railway.RailwayTypes.StationType.INDUSTRIAL
                &&station.type()!=com.example.examplemod.railway.RailwayTypes.StationType.MIXED)
                ||CreateRailwayAdapter.station(station.createStationId())==null)return false;
        registration.bindStation(stationId);setDirty();return true;
    }

    public void markInfrastructureMissing(UUID physicalId) {
        infrastructure.values().stream().filter(value -> value.physicalInstanceId().equals(physicalId))
                .findFirst().ifPresent(value -> { value.setAvailable(false); setDirty(); });
    }

    public void reconcileInfrastructure(ServerLevel anyLevel) {
        for (EconomyInfrastructureRegistration registration : infrastructure.values()) {
            ServerLevel level = anyLevel.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, registration.dimension()));
            if (level == null || !level.hasChunkAt(registration.pos())) continue;
            var blockEntity = level.getBlockEntity(registration.pos());
            boolean available = blockEntity instanceof com.example.examplemod.economy.block.EconomyThroughputBlockEntity be
                    && be.physicalInstanceId().equals(registration.physicalInstanceId());
            if (registration.available() != available) { registration.setAvailable(available); setDirty(); }
        }
    }

    public boolean setSmartProfile(SmartOrderProfile profile) {
        smartProfiles.put(profile.itemId(), profile); setDirty(); return true;
    }
    public boolean removeSmartProfile(ResourceLocation itemId) {
        if (smartProfiles.remove(itemId) == null) return false; setDirty(); return true;
    }
    public boolean setProcurementAvailable(long amount) {
        if (amount < 0L || amount > procurementCap()) return false;
        procurementAvailable = amount; setDirty(); return true;
    }

    public synchronized boolean acceptMarketOffer(UUID offerId, UUID cityId, UUID playerId, long requested,
                                                  int reputation, long now) {
        return false;
    }

    public synchronized boolean acceptFreightMarketOffer(UUID offerId, UUID cityId, UUID playerId, long requested,
                                                  int reputation, long now,UUID selectedPoint) {
        MarketOffer offer=marketOffers.get(offerId);if(offer==null||offer.status()!=EconomyTypes.MarketOfferStatus.OPEN)return false;
        boolean exporting=offer.direction()==EconomyTypes.MarketDirection.SELL_TO_MASTER;
        FreightPoint destination=exporting?freight.defaultReceive(masterCityId):freight.point(selectedPoint);
        FreightPoint source=exporting?freight.point(selectedPoint):freight.defaultSend(masterCityId);
        if(destination==null||source==null||!destination.online()||!source.online()
                ||!FreightLogistics.route(source,destination).equals("connected")
                ||!freight.receiving(destination.id,exporting?masterCityId:cityId)
                ||!freight.sending(source.id,exporting?cityId:masterCityId,null))return false;
        MarketDefinition definition=marketDefinition(offer.definitionId());if(definition==null)return false;
        long reputationLimit=Math.min(definition.maximumOffer(),64L+Math.max(0,reputation)*4L);
        long quantity=Math.min(requested,reputationLimit);if(quantity<definition.minimumOffer()||quantity>offer.remaining())return false;
        long active=marketOffers.values().stream().filter(v->cityId.equals(v.acceptedCity())&&!v.status().terminal()).count();
        if(active>=1L+Math.max(0,reputation)/250)return false;
        ExternalMarketState state=marketStates.computeIfAbsent(definition.id(),id->new ExternalMarketState(definition.targetDemand(),definition.targetSupply(),now));
        long total,bond;try{total=EconomyMath.multiplyExactPositive(quantity,offer.unitPrice());bond=EconomyMath.applyBasisPoints(total,definition.bondBps());}catch(RuntimeException ex){return false;}
        if(!state.reserve(offer.direction(),quantity))return false;
        if(offer.direction()==EconomyTypes.MarketDirection.SELL_TO_MASTER){
            if(total>procurementAvailable||!debit(treasuries,cityId,bond)){state.release(offer.direction(),quantity,definition);return false;}
            IndustrialContract contract;
            try{contract=new IndustrialContract(UUID.randomUUID(),EconomyTypes.ContractKind.MASTER_SMART,EconomyTypes.ContractMode.EXCLUSIVE,masterCityId,masterCityId,"","",offer.itemId(),quantity,total,destination.id,null,now,definition.deadlineTicks());}
            catch(RuntimeException ex){credit(treasuries,cityId,bond);state.release(offer.direction(),quantity,definition);return false;}
            if(!contract.accept(cityId,playerId,now)||!offer.reserve(cityId,playerId,quantity,bond)){credit(treasuries,cityId,bond);state.release(offer.direction(),quantity,definition);return false;}
            procurementAvailable-=total;procurementReserved=EconomyMath.addExactNonNegative(procurementReserved,total);contracts.put(contract.id(),contract);offerByContract.put(contract.id(),offer.id());issueManifest(contract,cityId);
            freight.bind(contract.id(),source.id,destination);
        }else{
            long required;try{required=Math.addExact(total,bond);}catch(ArithmeticException ex){state.release(offer.direction(),quantity,definition);return false;}
            if(availableWarehouseStock(offer.itemId())<quantity||!debit(treasuries,cityId,required)||!offer.reserve(cityId,playerId,quantity,bond)){state.release(offer.direction(),quantity,definition);return false;}
            freight.bind(offer.id(),source.id,destination);freight.assignments.get(offer.id()).running=true;
        }
        setDirty();return true;
    }

    private MarketDefinition marketDefinition(ResourceLocation id){
        MarketDefinition loaded=currentDefinitions.get(id);if(loaded!=null)return loaded;
        SmartOrderProfile p=smartProfiles.get(id);return p==null?null:legacyDefinition(p);
    }

    /** Read-only projection used by the market UI; it does not mutate market state or pricing. */
    public MarketReadView marketReadView(MarketOffer offer,int reputation){
        MarketDefinition definition=marketDefinition(offer.definitionId());
        ExternalMarketState state=marketStates.get(offer.definitionId());
        if(definition==null)return new MarketReadView("legacy",offer.unitPrice(),offer.remaining(),offer.quantity(),offer.remaining(),0);
        long available;
        if(state==null)available=offer.remaining();
        else if(offer.direction()==EconomyTypes.MarketDirection.SELL_TO_MASTER){
            long affordable=offer.unitPrice()<=0?0:procurementAvailable/offer.unitPrice();
            available=Math.min(state.demand(),affordable);
        }else available=Math.min(state.supply(),availableWarehouseStock(offer.itemId()));
        long target=offer.direction()==EconomyTypes.MarketDirection.SELL_TO_MASTER?definition.targetDemand():definition.targetSupply();
        long acceptLimit=Math.min(available,Math.min(offer.remaining(),Math.min(definition.maximumOffer(),64L+Math.max(0,reputation)*4L)));
        return new MarketReadView(definition.category(),definition.referenceValue(),available,target,acceptLimit,definition.bondBps());
    }

    public record MarketReadView(String category,long referenceValue,long available,long target,long acceptLimit,int bondBps){}

    private transient Map<ResourceLocation,MarketDefinition> currentDefinitions=Map.of();
    private static MarketDefinition legacyDefinition(SmartOrderProfile p){return new MarketDefinition(p.itemId(),p.itemId().toString(),"legacy",true,true,p.baseUnitPrice(),p.targetStock(),p.targetStock(),Math.max(1,p.batchSize()),p.minimumMultiplierBasisPoints(),p.maximumMultiplierBasisPoints(),1500,1,p.batchSize(),Math.max(72_000,p.cooldownTicks()),500);}

    private void refreshMarket(ServerLevel level,long now){
        Map<ResourceLocation,MarketDefinition> definitions=new LinkedHashMap<>(MarketDefinitions.values(level));
        smartProfiles.values().forEach(p->definitions.putIfAbsent(p.itemId(),legacyDefinition(p)));currentDefinitions=Map.copyOf(definitions);
        for(MarketDefinition definition:definitions.values()){
            ExternalMarketState state=marketStates.computeIfAbsent(definition.id(),id->new ExternalMarketState(definition.targetDemand(),definition.targetSupply(),now));state.recover(definition,now);
            if(definition.sellToMaster())ensureOffer(definition,state,EconomyTypes.MarketDirection.SELL_TO_MASTER,now);
            if(definition.buyFromMaster())ensureOffer(definition,state,EconomyTypes.MarketDirection.BUY_FROM_MASTER,now);
        }
        for(MarketOffer offer:marketOffers.values())if(offer.expiresAt()>0&&now>offer.expiresAt()){
            if(offer.status()==EconomyTypes.MarketOfferStatus.OPEN)offer.restore(offer.remaining(),offer.acceptedCity(),offer.acceptedBy(),EconomyTypes.MarketOfferStatus.EXPIRED);
            else if(offer.direction()==EconomyTypes.MarketDirection.BUY_FROM_MASTER&&(offer.status()==EconomyTypes.MarketOfferStatus.RESERVED||offer.status()==EconomyTypes.MarketOfferStatus.DISPATCHED||offer.status()==EconomyTypes.MarketOfferStatus.IN_PROGRESS)){
                MarketDefinition definition=definitions.get(offer.definitionId());ExternalMarketState state=marketStates.get(offer.definitionId());
                long cancelled=offer.remaining();
                if(definition!=null&&state!=null&&cancelled>0L)state.release(offer.direction(),cancelled,definition);
                if(offer.acceptedCity()!=null&&cancelled>0){
                    long refund=EconomyMath.multiplyExactPositive(cancelled,offer.unitPrice());
                    refund=EconomyMath.saturatingAdd(refund,offer.bond(),Long.MAX_VALUE);
                    credit(treasuries,offer.acceptedCity(),refund);
                }
                offer.restore(offer.remaining(),offer.acceptedCity(),offer.acceptedBy(),EconomyTypes.MarketOfferStatus.FAILED,offer.acceptedQuantity(),offer.dispatchedAmount());
                invalidateImportTokens(offer.id());
            }
        }
        setDirty();
    }

    private void ensureOffer(MarketDefinition d,ExternalMarketState state,EconomyTypes.MarketDirection direction,long now){
        if(marketOffers.values().stream().anyMatch(o->o.definitionId().equals(d.id())&&o.direction()==direction&&!o.status().terminal()))return;
        if(masterCityId==null)return;
        FreightPoint endpoint=direction==EconomyTypes.MarketDirection.SELL_TO_MASTER
                ?freight.defaultReceive(masterCityId):freight.defaultSend(masterCityId);
        if(endpoint==null||!endpoint.online())return;
        ResourceLocation item=resolveItem(d.selector());if(item==null)return;
        long unitPrice=marketPrice(d,state,direction);
        long available=direction==EconomyTypes.MarketDirection.SELL_TO_MASTER
                ?Math.min(state.demand(),unitPrice<=0?0:procurementAvailable/unitPrice)
                :Math.min(state.supply(),availableWarehouseStock(item));
        long quantity=Math.min(d.maximumOffer(),available);if(quantity<d.minimumOffer())return;
        MarketOffer offer=new MarketOffer(UUID.randomUUID(),d.id(),item,direction,quantity,unitPrice,
                now,now+d.deadlineTicks(),EconomyMath.applyBasisPoints(
                        EconomyMath.multiplyExactPositive(quantity,unitPrice),d.bondBps()));
        marketOffers.put(offer.id(),offer);
    }

    /** Stock not already promised to accepted, not-yet-packaged Master imports. */
    public long availableWarehouseStock(ResourceLocation itemId){
        long reserved=marketOffers.values().stream()
                .filter(o->o.direction()==EconomyTypes.MarketDirection.BUY_FROM_MASTER
                        &&o.itemId().equals(itemId)&&!o.status().terminal())
                .mapToLong(MarketOffer::availableToDispatch)
                .reduce(0L,(a,b)->EconomyMath.saturatingAdd(a,b,Long.MAX_VALUE));
        return Math.max(0L,warehouseStock(itemId)-reserved);
    }

    private static ResourceLocation resolveItem(String selector){String raw=selector.startsWith("#")?selector.substring(1):selector;ResourceLocation id=ResourceLocation.tryParse(raw);if(id==null)return null;if(!selector.startsWith("#"))return validItem(id)?id:null;var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,id);return BuiltInRegistries.ITEM.getTag(tag).flatMap(set->set.stream().findFirst()).map(h->BuiltInRegistries.ITEM.getKey(h.value())).orElse(null);}
    private static long marketPrice(MarketDefinition d,ExternalMarketState s,EconomyTypes.MarketDirection direction){long bps;if(direction==EconomyTypes.MarketDirection.SELL_TO_MASTER){long ratio=d.targetDemand()==0?0:java.math.BigInteger.valueOf(s.demand()).multiply(java.math.BigInteger.valueOf(10_000-d.minimumPriceBps())).divide(java.math.BigInteger.valueOf(d.targetDemand())).longValue();bps=Math.min(10_000,d.minimumPriceBps()+ratio);}else{long scarcity=Math.max(0,d.targetSupply()-s.supply());long base=Math.min(d.maximumPriceBps(),10_000L+d.importSpreadBps());long span=Math.max(0,d.maximumPriceBps()-base);bps=d.targetSupply()==0?base:Math.min(d.maximumPriceBps(),base+java.math.BigInteger.valueOf(scarcity).multiply(java.math.BigInteger.valueOf(span)).divide(java.math.BigInteger.valueOf(d.targetSupply())).longValue());}return Math.max(1,EconomyMath.applyBasisPoints(d.referenceValue(),(int)bps));}

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        replenishProcurement(now);
        if (now % Math.max(200, Config.ECONOMY_SMART_ORDER_INTERVAL_TICKS.get()) == 0L) {
            reconcileInfrastructure(level);
            reconcileShipments(level);
            refreshMarket(level,now);
            processTimeouts(now);
            applyTradeReputation(level);
            pruneTerminalHistory();
        }
    }

    private void applyTradeReputation(ServerLevel level){CityManager cities=CityManager.get(level.getServer().overworld());boolean changed=false;for(MarketOffer offer:marketOffers.values()){if(offer.acceptedCity()==null||reputationAppliedOffers.contains(offer.id())||(!offer.status().terminal()))continue;var city=cities.getCity(offer.acceptedCity());if(city!=null){city.adjustTradeReputation(offer.status()==EconomyTypes.MarketOfferStatus.COMPLETED?25:-10);changed=true;}reputationAppliedOffers.add(offer.id());setDirty();}for(IndustrialContract contract:contracts.values()){
            if(contract.state()!=EconomyTypes.ContractState.COMPLETED||offerByContract.containsKey(contract.id())||reputationAppliedOffers.contains(contract.id()))continue;
            for(UUID supplier:contract.contributions().keySet()){var city=cities.getCity(supplier);if(city!=null){city.adjustTradeReputation(25);changed=true;}}
            reputationAppliedOffers.add(contract.id());setDirty();
        }if(changed)cities.setDirty();}

    private void replenishProcurement(long now) {
        if (lastProcurementTick <= 0L) { lastProcurementTick = now; setDirty(); return; }
        long elapsed = Math.max(0L, now - lastProcurementTick);
        long perHour = Config.ECONOMY_PROCUREMENT_REPLENISH_PER_HOUR.get();
        if (elapsed <= 0L || perHour <= 0L) return;
        long addition = BigInteger.valueOf(elapsed).multiply(BigInteger.valueOf(perHour))
                .divide(BigInteger.valueOf(72_000L)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        if (addition <= 0L) return;
        procurementAvailable = EconomyMath.saturatingAdd(procurementAvailable, addition, procurementCap());
        long consumedTicks = Math.max(1L, BigInteger.valueOf(addition).multiply(BigInteger.valueOf(72_000L))
                .divide(BigInteger.valueOf(perHour)).longValue());
        lastProcurementTick = Math.min(now, lastProcurementTick + consumedTicks);
        setDirty();
    }

    private void generateSmartOrders(long now) {
        if (masterCityId == null) return;
        FreightPoint intake = freight.defaultReceive(masterCityId);
        if (intake == null) return;
        for (SmartOrderProfile profile : List.copyOf(smartProfiles.values())) {
            long incoming = incomingMasterAmount(profile.itemId());
            long stock = warehouseStock(profile.itemId());
            long effective=EconomyMath.saturatingAdd(stock,incoming,Long.MAX_VALUE);
            if (effective >= profile.reorderPoint() || now - profile.lastGeneratedAt() < profile.cooldownTicks()) continue;
            boolean duplicate = contracts.values().stream().anyMatch(c -> c.kind() == EconomyTypes.ContractKind.MASTER_SMART
                    && c.itemId().equals(profile.itemId()) && !c.state().terminal());
            if (duplicate) continue;
            long quantity = Math.min(profile.batchSize(), Math.max(0L, profile.targetStock()-Math.min(profile.targetStock(),effective)));
            if (quantity <= 0L || !validAmount(quantity)) continue;
            long reward;
            try { reward = EconomyMath.multiplyExactPositive(quantity, profile.unitPrice(stock, incoming)); }
            catch (ArithmeticException | IllegalArgumentException ex) { continue; }
            if (reward > procurementAvailable || !validMoney(reward)) continue;
            IndustrialContract contract;
            try {
                contract = new IndustrialContract(UUID.randomUUID(), EconomyTypes.ContractKind.MASTER_SMART,
                        EconomyTypes.ContractMode.EXCLUSIVE, masterCityId, masterCityId,
                    "", "", profile.itemId(), quantity, reward,
                        intake.id, null, now, 0L);
            } catch (IllegalArgumentException ex) { continue; }
            procurementAvailable -= reward;
            procurementReserved = EconomyMath.addExactNonNegative(procurementReserved, reward);
            contracts.put(contract.id(), contract);
            freight.bind(contract.id(),null,intake);
            smartProfiles.put(profile.itemId(), profile.generatedAt(now));
            setDirty();
        }
    }

    private void processTimeouts(long now) {
        long deliveredTimeout = Config.ECONOMY_DELIVERED_TIMEOUT_MINUTES.get() * 1_200L;
        for (IndustrialContract contract : contracts.values()) {
            if (contract.state() == EconomyTypes.ContractState.DELIVERED
                    && contract.kind() != EconomyTypes.ContractKind.MASTER_SMART
                    && now - contract.deliveredAt() >= deliveredTimeout) {
                completeContract(contract); setDirty(); continue;
            }
            long deadline = contract.deadlineAt();
            UUID linkedOfferId=offerByContract.get(contract.id());MarketOffer linkedOffer=linkedOfferId==null?null:marketOffers.get(linkedOfferId);
            boolean canRefund=linkedOffer!=null||canCredit(treasuries,contract.issuerCityId(),contract.escrow());
            if (deadline > 0L && now > deadline && canRefund && contract.fail()) {
                failActiveShipments(contract.id());invalidateContractLogistics(contract.id(),EconomyTypes.ManifestState.EXPIRED);
                long refund = contract.takeEscrow();
                if(linkedOffer!=null){procurementReserved=Math.max(0,procurementReserved-refund);procurementAvailable=EconomyMath.saturatingAdd(procurementAvailable,refund,procurementCap());MarketDefinition definition=marketDefinition(linkedOffer.definitionId());ExternalMarketState state=marketStates.get(linkedOffer.definitionId());if(definition!=null&&state!=null)state.release(linkedOffer.direction(),linkedOffer.remaining(),definition);linkedOffer.restore(linkedOffer.remaining(),linkedOffer.acceptedCity(),linkedOffer.acceptedBy(),EconomyTypes.MarketOfferStatus.FAILED);}else credit(treasuries, contract.issuerCityId(), refund);
                setDirty();
            }
        }
    }

    private void pruneTerminalHistory(){
        int keep=Config.ECONOMY_HISTORY_LIMIT.get();
        List<UUID> oldContracts=contracts.values().stream().filter(value->value.state().terminal())
                .sorted(Comparator.comparingLong(IndustrialContract::createdAt).reversed()).skip(keep)
                .map(IndustrialContract::id).toList();
        List<UUID> oldListings=listings.values().stream().filter(value->value.state().terminal())
                .sorted(Comparator.comparingLong(PrivateListing::createdAt).reversed()).skip(keep)
                .map(PrivateListing::id).toList();
        if(oldContracts.isEmpty()&&oldListings.isEmpty())return;
        oldContracts.forEach(contracts::remove);oldListings.forEach(listings::remove);
        shipments.values().removeIf(value->{boolean remove=!contracts.containsKey(value.contractId());if(remove)activeShipmentByTrain.remove(value.fleetTrainId(),value.shipmentId());return remove;});setDirty();
        manifests.values().removeIf(value->!contracts.containsKey(value.contractId()));
        shipmentTokens.values().removeIf(value->value.kind()==EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT&&!contracts.containsKey(value.subjectId()));
    }

    private boolean failActiveShipments(UUID contractId){boolean changed=false;for(IndustrialShipment shipment:shipments.values())if(shipment.contractId().equals(contractId)&&shipment.fail()){activeShipmentByTrain.remove(shipment.fleetTrainId(),shipment.shipmentId());changed=true;}return changed;}

    private void reconcileShipments(ServerLevel level){boolean changed=false;for(IndustrialShipment shipment:shipments.values())if(shipment.active()){shipment.fail();activeShipmentByTrain.remove(shipment.fleetTrainId(),shipment.shipmentId());changed=true;}if(changed)setDirty();}

    private boolean completeContract(IndustrialContract contract) {
        long payout=contract.escrow();
        Map<UUID,Long> payouts=new LinkedHashMap<>();
        Map<UUID, Long> contributions = contract.contributions();
        if (contract.mode() == EconomyTypes.ContractMode.EXCLUSIVE) {
            if (contract.contractorCityId() != null) payouts.put(contract.contractorCityId(),payout);
        } else if (!contributions.isEmpty()) {
            List<Map.Entry<UUID, Long>> ordered = new ArrayList<>(contributions.entrySet());
            ordered.sort(Map.Entry.comparingByKey());
            long paid = 0L;
            for (int i = 0; i < ordered.size(); i++) {
                var entry = ordered.get(i);
                long share = i == ordered.size() - 1 ? payout - paid
                        : BigInteger.valueOf(payout).multiply(BigInteger.valueOf(entry.getValue()))
                        .divide(BigInteger.valueOf(contract.amount())).longValue();
                if(share>0L){payouts.put(entry.getKey(),share);paid+=share;}
            }
        }
        if(payouts.entrySet().stream().anyMatch(entry->!canCredit(treasuries,entry.getKey(),entry.getValue()))
                || !contract.complete()) return false;
        contract.takeEscrow();
        if (contract.kind() == EconomyTypes.ContractKind.MASTER_SMART) {
            procurementReserved = Math.max(0L, procurementReserved - payout);
        }
        payouts.forEach((city,share)->credit(treasuries,city,share));
        UUID offerId=offerByContract.get(contract.id());MarketOffer offer=offerId==null?null:marketOffers.get(offerId);
        if(offer!=null)finishMarketOffer(offer);
        invalidateContractLogistics(contract.id(),EconomyTypes.ManifestState.COMPLETED);
        return true;
    }

    private void invalidateContractLogistics(UUID contractId,EconomyTypes.ManifestState manifestState){
        manifests.values().stream().filter(value->value.contractId().equals(contractId)).forEach(value->{if(manifestState==EconomyTypes.ManifestState.COMPLETED)value.complete();else if(manifestState==EconomyTypes.ManifestState.EXPIRED)value.expire();else value.invalidate();});
        shipmentTokens.values().stream().filter(value->value.kind()==EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT&&value.subjectId().equals(contractId)).forEach(ShipmentToken::invalidate);
    }

    private void invalidateImportTokens(UUID offerId){shipmentTokens.values().stream().filter(value->value.kind()==EconomyTypes.ShipmentTokenKind.IMPORT_ORDER&&value.subjectId().equals(offerId)).forEach(ShipmentToken::invalidate);}

    private void finishMarketOffer(MarketOffer offer){if(offer.acceptedCity()!=null&&offer.bond()>0)credit(treasuries,offer.acceptedCity(),offer.bond());}

    private long incomingMasterAmount(ResourceLocation itemId) {
        return contracts.values().stream().filter(c -> c.kind() == EconomyTypes.ContractKind.MASTER_SMART
                        && c.itemId().equals(itemId) && !c.state().terminal())
                .mapToLong(IndustrialContract::remainingAmount).reduce(0L, EconomyMath::addExactNonNegative);
    }

    private boolean validContract(ResourceLocation itemId, long amount, long reward, long deadlineTicks) {
        return itemId != null && validAmount(amount) && validMoney(reward) && deadlineTicks >= 0L
                && deadlineTicks <= Config.ECONOMY_MAX_DEADLINE_HOURS.get() * 72_000L;
    }
    private boolean validAmount(long amount) { return amount > 0L && amount <= Config.ECONOMY_MAX_CONTRACT_AMOUNT.get(); }
    private boolean validMoney(long amount) { return amount > 0L && amount <= Config.ECONOMY_MAX_TRANSACTION.get(); }
    private static boolean validItem(ResourceLocation id) {
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR;
    }
    private long procurementCap() { return Config.ECONOMY_PROCUREMENT_CAP.get(); }
    private static void putBalance(Map<UUID, Long> map, UUID id, long amount) { if (amount == 0L) map.remove(id); else map.put(id, amount); }
    private static void putStock(Map<ResourceLocation, Long> map, ResourceLocation id, long amount) { if (amount <= 0L) map.remove(id); else map.put(id, amount); }
    private static boolean debit(Map<UUID, Long> map, UUID id, long amount) { long value=map.getOrDefault(id,0L);if(amount<0L||value<amount)return false;putBalance(map,id,value-amount);return true; }
    private static boolean canCredit(Map<UUID,Long> map,UUID id,long amount){return amount<=0L||id!=null&&map.getOrDefault(id,0L)<=Long.MAX_VALUE-amount;}
    private static void credit(Map<UUID, Long> map, UUID id, long amount) { if(amount<=0L)return;putBalance(map,id,EconomyMath.addExactNonNegative(map.getOrDefault(id,0L),amount)); }

    private static EconomyManager load(CompoundTag root, HolderLookup.Provider registries) {
        EconomyManager manager = new EconomyManager();
        int version = root.getInt("FormatVersion");
        if(version<=0)version=1;
        if(version>FORMAT_VERSION)throw new IllegalStateException("Economy save format "+version+" is newer than supported "+FORMAT_VERSION);
        manager.masterCityId = root.hasUUID("MasterCity") ? root.getUUID("MasterCity") : null;
        manager.procurementAvailable = Math.max(0L, root.getLong("ProcurementAvailable"));
        manager.procurementReserved = Math.max(0L, root.getLong("ProcurementReserved"));
        manager.lastProcurementTick = Math.max(0L, root.getLong("LastProcurementTick"));
        readUuidLongMap(root, "Wallets", "Player", manager.wallets);
        readUuidLongMap(root, "Treasuries", "City", manager.treasuries);
        if(version>=7){readUuidLongMap(root,"TransportReserves","Trip",manager.transportReserves);for(Tag raw:root.getList("SettledTransportTrips",Tag.TAG_COMPOUND)){CompoundTag tag=(CompoundTag)raw;if(tag.hasUUID("Trip"))manager.settledTransportTrips.add(tag.getUUID("Trip"));}}
        readResourceLongMap(root, "Warehouse", manager.warehouseStock);
        for (Tag raw : root.getList("Infrastructure", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag=(CompoundTag)raw; ResourceLocation dimension=ResourceLocation.tryParse(tag.getString("Dimension"));
                if(dimension==null)continue; EconomyInfrastructureRegistration value=new EconomyInfrastructureRegistration(
                        tag.getUUID("RegistrationId"),tag.getUUID("PhysicalId"),tag.getUUID("RegisteredBy"),
                        readEnum(tag,"Type",EconomyTypes.InfrastructureType.class,EconomyTypes.InfrastructureType.CONTRACT_INTAKE),
                        dimension,NbtUtils.readBlockPos(tag,"Pos").orElse(BlockPos.ZERO));
                value.restore(tag.hasUUID("CityId")?tag.getUUID("CityId"):null,tag.getBoolean("Available"),
                        tag.hasUUID("StationId")?tag.getUUID("StationId"):null,
                        readEnum(tag,"LastError",EconomyTypes.IntakeAttributionError.class,EconomyTypes.IntakeAttributionError.NONE),tag.getLong("LastErrorAt"));
                manager.infrastructure.put(value.registrationId(),value);
            } catch(RuntimeException ignored) {}
        }
        for (Tag raw : root.getList("Contracts", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag=(CompoundTag)raw; ResourceLocation item=ResourceLocation.tryParse(tag.getString("Item"));if(item==null)continue;
                IndustrialContract value=new IndustrialContract(tag.getUUID("Id"),readEnum(tag,"Kind",EconomyTypes.ContractKind.class,EconomyTypes.ContractKind.ORDINARY),
                        readEnum(tag,"Mode",EconomyTypes.ContractMode.class,EconomyTypes.ContractMode.EXCLUSIVE),tag.getUUID("IssuerCity"),tag.getUUID("CreatedBy"),
                        tag.getString("Title"),tag.getString("Description"),item,tag.getLong("Amount"),tag.getLong("Reward"),tag.getUUID("Destination"),
                        tag.hasUUID("Station")?tag.getUUID("Station"):null,tag.getLong("CreatedAt"),tag.getLong("DeadlineDuration"));
                Map<UUID,Long> contributions=new LinkedHashMap<>();readUuidLongMap(tag,"Contributions","City",contributions);
                java.util.List<UUID> participants=new java.util.ArrayList<>();for(Tag participantRaw:tag.getList("Participants",Tag.TAG_COMPOUND)){CompoundTag participantTag=(CompoundTag)participantRaw;if(participantTag.hasUUID("City"))participants.add(participantTag.getUUID("City"));}
                value.restore(readEnum(tag,"State",EconomyTypes.ContractState.class,EconomyTypes.ContractState.OPEN),tag.hasUUID("ContractorCity")?tag.getUUID("ContractorCity"):null,
                        tag.hasUUID("AcceptedBy")?tag.getUUID("AcceptedBy"):null,
                        tag.getLong("Delivered"),tag.getLong("AcceptedAt"),tag.getLong("DeliveredAt"),tag.getLong("Escrow"),contributions,participants);
                manager.contracts.put(value.id(),value);
            } catch(RuntimeException ignored) {}
        }
        for (Tag raw : root.getList("Listings",Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag=(CompoundTag)raw;ResourceLocation item=ResourceLocation.tryParse(tag.getString("Item"));if(item==null)continue;
                ResourceLocation requested=tag.contains("RequestedItem")?ResourceLocation.tryParse(tag.getString("RequestedItem")):null;
                PrivateListing value=new PrivateListing(tag.getUUID("Id"),readEnum(tag,"Type",EconomyTypes.ListingType.class,EconomyTypes.ListingType.BUY),
                        tag.getUUID("Creator"),item,tag.getLong("Amount"),tag.getLong("Price"),tag.getString("Meeting"),tag.getLong("CreatedAt"),requested);
                value.restore(readEnum(tag,"State",EconomyTypes.ListingState.class,EconomyTypes.ListingState.OPEN),tag.hasUUID("Counterparty")?tag.getUUID("Counterparty"):null,tag.getLong("Escrow"));
                manager.listings.put(value.id(),value);
            } catch(RuntimeException ignored) {}
        }
        if(version>=2)for(Tag raw:root.getList("Shipments",Tag.TAG_COMPOUND)){
            try{CompoundTag tag=(CompoundTag)raw;ResourceLocation item=ResourceLocation.tryParse(tag.getString("Item"));if(item==null)continue;
                IndustrialShipment shipment=new IndustrialShipment(tag.getUUID("Id"),tag.getUUID("Contract"),tag.getUUID("ContractorCity"),tag.getUUID("CreatedBy"),tag.getUUID("Train"),tag.getUUID("Destination"),item,tag.getLong("Declared"),tag.getLong("CreatedAt"));
                shipment.restore(readEnum(tag,"Status",EconomyTypes.ShipmentStatus.class,EconomyTypes.ShipmentStatus.CANCELLED),tag.getLong("Verified"),tag.getLong("DepartedAt"),tag.getLong("ArrivedAt"),tag.getLong("CompletedAt"));
                IndustrialContract shipmentContract=manager.contracts.get(shipment.contractId());if(shipmentContract!=null){manager.shipments.put(shipment.shipmentId(),shipment);if(shipment.active())shipment.fail();}
            }catch(RuntimeException ignored){}
        }
        for(Tag raw:root.getList("SmartProfiles",Tag.TAG_COMPOUND)){
            try{CompoundTag tag=(CompoundTag)raw;ResourceLocation item=ResourceLocation.tryParse(tag.getString("Item"));if(item==null)continue;
                SmartOrderProfile profile=new SmartOrderProfile(item,tag.getLong("Target"),tag.getLong("Reorder"),tag.getLong("Batch"),tag.getLong("BasePrice"),
                        tag.getInt("MinBps"),tag.getInt("MaxBps"),tag.getLong("Cooldown"),tag.getLong("LastGenerated"));manager.smartProfiles.put(item,profile);
            }catch(RuntimeException ignored){}
        }
        if(version>=4){
            for(Tag raw:root.getList("MarketStates",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;ResourceLocation id=ResourceLocation.tryParse(tag.getString("Definition"));if(id!=null)manager.marketStates.put(id,new ExternalMarketState(tag.getLong("Demand"),tag.getLong("Supply"),tag.getLong("LastRecovery")));}catch(RuntimeException ignored){}
            for(Tag raw:root.getList("MarketOffers",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;ResourceLocation definition=ResourceLocation.tryParse(tag.getString("Definition")),item=ResourceLocation.tryParse(tag.getString("Item"));if(definition==null||item==null)continue;MarketOffer offer=new MarketOffer(tag.getUUID("Id"),definition,item,readEnum(tag,"Direction",EconomyTypes.MarketDirection.class,EconomyTypes.MarketDirection.SELL_TO_MASTER),tag.getLong("Quantity"),tag.getLong("UnitPrice"),tag.getLong("CreatedAt"),tag.getLong("ExpiresAt"),tag.getLong("Bond"));EconomyTypes.MarketOfferStatus status=readEnum(tag,"Status",EconomyTypes.MarketOfferStatus.class,EconomyTypes.MarketOfferStatus.CANCELLED);offer.restore(tag.getLong("Remaining"),tag.hasUUID("AcceptedCity")?tag.getUUID("AcceptedCity"):null,tag.hasUUID("AcceptedBy")?tag.getUUID("AcceptedBy"):null,status,tag.contains("AcceptedQuantity")?tag.getLong("AcceptedQuantity"):(status==EconomyTypes.MarketOfferStatus.OPEN?0L:tag.getLong("Remaining")),tag.getLong("Dispatched"));manager.marketOffers.put(offer.id(),offer);}catch(RuntimeException ignored){}
            for(Tag raw:root.getList("OfferContracts",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;if(tag.hasUUID("Contract")&&tag.hasUUID("Offer")&&manager.contracts.containsKey(tag.getUUID("Contract"))&&manager.marketOffers.containsKey(tag.getUUID("Offer")))manager.offerByContract.put(tag.getUUID("Contract"),tag.getUUID("Offer"));}catch(RuntimeException ignored){}
            for(Tag raw:root.getList("ReputationAppliedOffers",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;if(tag.hasUUID("Offer"))manager.reputationAppliedOffers.add(tag.getUUID("Offer"));}catch(RuntimeException ignored){}
        }
        if(version>=5){
            for(Tag raw:root.getList("Manifests",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;ShippingManifest manifest=new ShippingManifest(tag.getUUID("Id"),tag.getUUID("Contract"),tag.getUUID("ContractorCity"),tag.getLong("Authorized"),tag.getLong("ExpiresAt"));manifest.restore(tag.getLong("Dispatched"),tag.getLong("Accepted"),readEnum(tag,"State",EconomyTypes.ManifestState.class,EconomyTypes.ManifestState.INVALID));if(manager.contracts.containsKey(manifest.contractId()))manager.manifests.put(manifest.manifestId(),manifest);}catch(RuntimeException ignored){}
            for(Tag raw:root.getList("ShipmentTokens",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;ResourceLocation item=ResourceLocation.tryParse(tag.getString("Item"));if(item==null)continue;ShipmentToken token=new ShipmentToken(tag.getUUID("Id"),tag.hasUUID("Manifest")?tag.getUUID("Manifest"):null,tag.getUUID("Subject"),readEnum(tag,"Kind",EconomyTypes.ShipmentTokenKind.class,EconomyTypes.ShipmentTokenKind.EXPORT_CONTRACT),tag.getUUID("SenderCity"),tag.getUUID("ReceiverCity"),item,tag.getLong("Amount"),tag.getLong("CreatedAt"),tag.getLong("ExpiresAt"));token.restore(readEnum(tag,"State",EconomyTypes.ShipmentTokenState.class,EconomyTypes.ShipmentTokenState.INVALID),tag.getLong("ConsumedAt"));manager.shipmentTokens.put(token.tokenId(),token);}catch(RuntimeException ignored){}
        }
        if(version>=6)manager.freight.load(root.getCompound("FreightLogistics"));
        manager.procurementAvailable=Math.min(manager.procurementAvailable,manager.procurementCap());
        manager.procurementReserved=manager.contracts.values().stream()
                .filter(value->value.kind()==EconomyTypes.ContractKind.MASTER_SMART&&!value.state().terminal())
                .mapToLong(IndustrialContract::escrow)
                .reduce(0L,(left,right)->EconomyMath.saturatingAdd(left,right,Long.MAX_VALUE));
        return manager;
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.put("FreightLogistics",freight.save());
        root.putInt("FormatVersion",FORMAT_VERSION);if(masterCityId!=null)root.putUUID("MasterCity",masterCityId);
        root.putLong("ProcurementAvailable",procurementAvailable);root.putLong("ProcurementReserved",procurementReserved);root.putLong("LastProcurementTick",lastProcurementTick);
        root.put("Wallets",writeUuidLongMap(wallets,"Player"));root.put("Treasuries",writeUuidLongMap(treasuries,"City"));root.put("TransportReserves",writeUuidLongMap(transportReserves,"Trip"));ListTag settledTrips=new ListTag();for(UUID id:settledTransportTrips){CompoundTag tag=new CompoundTag();tag.putUUID("Trip",id);settledTrips.add(tag);}root.put("SettledTransportTrips",settledTrips);root.put("Warehouse",writeResourceLongMap(warehouseStock));
        ListTag infrastructureTags=new ListTag();for(var value:infrastructure.values()){CompoundTag tag=new CompoundTag();tag.putUUID("RegistrationId",value.registrationId());tag.putUUID("PhysicalId",value.physicalInstanceId());tag.putUUID("RegisteredBy",value.registeredBy());tag.putString("Type",value.type().name());if(value.cityId()!=null)tag.putUUID("CityId",value.cityId());tag.putString("Dimension",value.dimension().toString());tag.put("Pos",NbtUtils.writeBlockPos(value.pos()));tag.putBoolean("Available",value.available());if(value.stationRegistrationId()!=null)tag.putUUID("StationId",value.stationRegistrationId());tag.putString("LastError",value.lastAttributionError().name());tag.putLong("LastErrorAt",value.lastAttributionErrorAt());infrastructureTags.add(tag);}root.put("Infrastructure",infrastructureTags);
        ListTag contractTags=new ListTag();for(var value:contracts.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.id());tag.putString("Kind",value.kind().name());tag.putString("Mode",value.mode().name());tag.putUUID("IssuerCity",value.issuerCityId());tag.putUUID("CreatedBy",value.createdBy());tag.putString("Title",value.title());tag.putString("Description",value.description());tag.putString("Item",value.itemId().toString());tag.putLong("Amount",value.amount());tag.putLong("Reward",value.reward());tag.putUUID("Destination",value.destinationRegistrationId());if(value.stationRegistrationId()!=null)tag.putUUID("Station",value.stationRegistrationId());tag.putLong("CreatedAt",value.createdAt());tag.putLong("DeadlineDuration",value.deadlineDurationTicks());tag.putString("State",value.state().name());if(value.contractorCityId()!=null)tag.putUUID("ContractorCity",value.contractorCityId());if(value.acceptedByPlayerId()!=null)tag.putUUID("AcceptedBy",value.acceptedByPlayerId());tag.putLong("Delivered",value.deliveredAmount());tag.putLong("AcceptedAt",value.acceptedAt());tag.putLong("DeliveredAt",value.deliveredAt());tag.putLong("Escrow",value.escrow());tag.put("Contributions",writeUuidLongMap(value.contributions(),"City"));ListTag participants=new ListTag();for(UUID city:value.participants()){CompoundTag participant=new CompoundTag();participant.putUUID("City",city);participants.add(participant);}tag.put("Participants",participants);contractTags.add(tag);}root.put("Contracts",contractTags);
        ListTag listingTags=new ListTag();for(var value:listings.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.id());tag.putString("Type",value.type().name());tag.putUUID("Creator",value.creator());if(value.counterparty()!=null)tag.putUUID("Counterparty",value.counterparty());tag.putString("Item",value.itemId().toString());tag.putLong("Amount",value.amount());tag.putLong("Price",value.price());tag.putString("Meeting",value.meetingText());tag.putLong("CreatedAt",value.createdAt());tag.putString("State",value.state().name());tag.putLong("Escrow",value.escrow());if(value.requestedItemId()!=null)tag.putString("RequestedItem",value.requestedItemId().toString());listingTags.add(tag);}root.put("Listings",listingTags);
        ListTag shipmentTags=new ListTag();for(var value:shipments.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.shipmentId());tag.putUUID("Contract",value.contractId());tag.putUUID("ContractorCity",value.contractorCityId());tag.putUUID("CreatedBy",value.createdByPlayerId());tag.putUUID("Train",value.fleetTrainId());tag.putUUID("Destination",value.destinationIntakeId());tag.putString("Item",value.expectedItem().toString());tag.putLong("Declared",value.declaredAmount());tag.putLong("Verified",value.verifiedDeliveredAmount());tag.putLong("CreatedAt",value.createdAt());tag.putLong("DepartedAt",value.departedAt());tag.putLong("ArrivedAt",value.arrivedAt());tag.putLong("CompletedAt",value.completedAt());tag.putString("Status",value.status().name());shipmentTags.add(tag);}root.put("Shipments",shipmentTags);
        ListTag profileTags=new ListTag();for(var value:smartProfiles.values()){CompoundTag tag=new CompoundTag();tag.putString("Item",value.itemId().toString());tag.putLong("Target",value.targetStock());tag.putLong("Reorder",value.reorderPoint());tag.putLong("Batch",value.batchSize());tag.putLong("BasePrice",value.baseUnitPrice());tag.putInt("MinBps",value.minimumMultiplierBasisPoints());tag.putInt("MaxBps",value.maximumMultiplierBasisPoints());tag.putLong("Cooldown",value.cooldownTicks());tag.putLong("LastGenerated",value.lastGeneratedAt());profileTags.add(tag);}root.put("SmartProfiles",profileTags);
        ListTag stateTags=new ListTag();marketStates.forEach((id,value)->{CompoundTag tag=new CompoundTag();tag.putString("Definition",id.toString());tag.putLong("Demand",value.demand());tag.putLong("Supply",value.supply());tag.putLong("LastRecovery",value.lastRecoveryTick());stateTags.add(tag);});root.put("MarketStates",stateTags);
        ListTag offerTags=new ListTag();for(MarketOffer value:marketOffers.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.id());tag.putString("Definition",value.definitionId().toString());tag.putString("Item",value.itemId().toString());tag.putString("Direction",value.direction().name());tag.putLong("Quantity",value.quantity());tag.putLong("Remaining",value.remaining());tag.putLong("AcceptedQuantity",value.acceptedQuantity());tag.putLong("Dispatched",value.dispatchedAmount());tag.putLong("UnitPrice",value.unitPrice());tag.putLong("CreatedAt",value.createdAt());tag.putLong("ExpiresAt",value.expiresAt());tag.putLong("Bond",value.bond());tag.putString("Status",value.status().name());if(value.acceptedCity()!=null)tag.putUUID("AcceptedCity",value.acceptedCity());if(value.acceptedBy()!=null)tag.putUUID("AcceptedBy",value.acceptedBy());offerTags.add(tag);}root.put("MarketOffers",offerTags);
        ListTag linkTags=new ListTag();offerByContract.forEach((contract,offer)->{CompoundTag tag=new CompoundTag();tag.putUUID("Contract",contract);tag.putUUID("Offer",offer);linkTags.add(tag);});root.put("OfferContracts",linkTags);
        ListTag reputationTags=new ListTag();for(UUID offer:reputationAppliedOffers){CompoundTag tag=new CompoundTag();tag.putUUID("Offer",offer);reputationTags.add(tag);}root.put("ReputationAppliedOffers",reputationTags);
        ListTag manifestTags=new ListTag();for(ShippingManifest value:manifests.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.manifestId());tag.putUUID("Contract",value.contractId());tag.putUUID("ContractorCity",value.contractorCityId());tag.putLong("Authorized",value.authorizedAmount());tag.putLong("Dispatched",value.dispatchedAmount());tag.putLong("Accepted",value.acceptedAmount());tag.putLong("ExpiresAt",value.expiresAt());tag.putString("State",value.state().name());manifestTags.add(tag);}root.put("Manifests",manifestTags);
        ListTag tokenTags=new ListTag();for(ShipmentToken value:shipmentTokens.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.tokenId());if(value.manifestId()!=null)tag.putUUID("Manifest",value.manifestId());tag.putUUID("Subject",value.subjectId());tag.putString("Kind",value.kind().name());tag.putUUID("SenderCity",value.senderCityId());tag.putUUID("ReceiverCity",value.receiverCityId());tag.putString("Item",value.itemId().toString());tag.putLong("Amount",value.amount());tag.putLong("CreatedAt",value.createdAt());tag.putLong("ExpiresAt",value.expiresAt());tag.putString("State",value.state().name());tag.putLong("ConsumedAt",value.consumedAt());tokenTags.add(tag);}root.put("ShipmentTokens",tokenTags);
        return root;
    }

    private static ListTag writeUuidLongMap(Map<UUID,Long> values,String idKey){ListTag list=new ListTag();values.forEach((id,amount)->{if(amount<=0L)return;CompoundTag tag=new CompoundTag();tag.putUUID(idKey,id);tag.putLong("Amount",amount);list.add(tag);});return list;}
    private static void readUuidLongMap(CompoundTag root,String listKey,String idKey,Map<UUID,Long> target){for(Tag raw:root.getList(listKey,Tag.TAG_COMPOUND)){try{CompoundTag tag=(CompoundTag)raw;long amount=tag.getLong("Amount");if(tag.hasUUID(idKey)&&amount>0L)target.put(tag.getUUID(idKey),amount);}catch(RuntimeException ignored){}}}
    private static ListTag writeResourceLongMap(Map<ResourceLocation,Long> values){ListTag list=new ListTag();values.forEach((id,amount)->{if(amount<=0L)return;CompoundTag tag=new CompoundTag();tag.putString("Item",id.toString());tag.putLong("Amount",amount);list.add(tag);});return list;}
    private static void readResourceLongMap(CompoundTag root,String key,Map<ResourceLocation,Long> target){for(Tag raw:root.getList(key,Tag.TAG_COMPOUND)){CompoundTag tag=(CompoundTag)raw;ResourceLocation id=ResourceLocation.tryParse(tag.getString("Item"));long amount=tag.getLong("Amount");if(id!=null&&amount>0L)target.put(id,amount);}}
    private static <E extends Enum<E>> E readEnum(CompoundTag tag,String key,Class<E> type,E fallback){try{return Enum.valueOf(type,tag.getString(key));}catch(IllegalArgumentException ex){return fallback;}}
}
