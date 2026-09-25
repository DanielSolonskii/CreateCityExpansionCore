package com.example.examplemod.economy;

import com.example.examplemod.Config;
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
    private static final String DATA_NAME = "createcityexpansioncore_economy";
    private static final int FORMAT_VERSION = 3;

    private final Map<UUID, Long> wallets = new LinkedHashMap<>();
    private final Map<UUID, Long> treasuries = new LinkedHashMap<>();
    private final Map<UUID, IndustrialContract> contracts = new LinkedHashMap<>();
    private final Map<UUID, PrivateListing> listings = new LinkedHashMap<>();
    private final Map<UUID, IndustrialShipment> shipments = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeShipmentByTrain = new HashMap<>();
    private final Map<UUID, EconomyInfrastructureRegistration> infrastructure = new LinkedHashMap<>();
    private final Map<ResourceLocation, SmartOrderProfile> smartProfiles = new LinkedHashMap<>();
    private final Map<ResourceLocation, Long> warehouseStock = new LinkedHashMap<>();
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
    public UUID masterCityId() { return masterCityId; }
    public long procurementAvailable() { return procurementAvailable; }
    public long procurementReserved() { return procurementReserved; }
    public List<IndustrialContract> contracts() { return List.copyOf(contracts.values()); }
    public List<PrivateListing> listings() { return List.copyOf(listings.values()); }
    public List<IndustrialShipment> shipments(){return List.copyOf(shipments.values());}
    public List<IndustrialShipment> shipmentsForContract(UUID contractId){return shipments.values().stream().filter(value->value.contractId().equals(contractId)).toList();}
    public List<SmartOrderProfile> smartProfiles() { return List.copyOf(smartProfiles.values()); }
    public List<EconomyInfrastructureRegistration> infrastructure() { return List.copyOf(infrastructure.values()); }
    @Nullable public IndustrialContract contract(UUID id) { return contracts.get(id); }
    @Nullable public PrivateListing listing(UUID id) { return listings.get(id); }
    @Nullable public IndustrialShipment shipment(UUID id){return shipments.get(id);}
    @Nullable public EconomyInfrastructureRegistration registration(UUID id) { return infrastructure.get(id); }

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
        EconomyInfrastructureRegistration intake = infrastructure.get(intakeRegistrationId);
        if (!validContract(itemId, amount, reward, deadlineTicks) || intake == null || !intake.available()
                || intake.type() != EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                || !issuerCityId.equals(intake.cityId())) return null;
        long fee = EconomyMath.applyBasisPoints(reward, Config.ECONOMY_CITY_FEE_BPS.get());
        long required;
        try { required = Math.addExact(reward, fee); } catch (ArithmeticException ex) { return null; }
        if (!debit(treasuries, issuerCityId, required)) return null;
        IndustrialContract contract = new IndustrialContract(UUID.randomUUID(), EconomyTypes.ContractKind.ORDINARY,
                EconomyTypes.ContractMode.EXCLUSIVE, issuerCityId, creator, title, description, itemId, amount,
                reward, intakeRegistrationId, stationRegistrationId, now, deadlineTicks);
        contracts.put(contract.id(), contract); setDirty(); return contract;
    }

    @Nullable
    public IndustrialContract createMasterSpecial(UUID creator, String title, String description,
                                                  ResourceLocation itemId, long amount, long reward,
                                                  UUID intakeRegistrationId, @Nullable UUID stationRegistrationId,
                                                  long deadlineTicks, EconomyTypes.ContractMode mode, long now) {
        if (masterCityId == null || !validContract(itemId, amount, reward, deadlineTicks)) return null;
        EconomyInfrastructureRegistration intake = infrastructure.get(intakeRegistrationId);
        if (intake == null || !masterCityId.equals(intake.cityId()) || !intake.available()
                || intake.type() != EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                || !debit(treasuries, masterCityId, reward)) return null;
        IndustrialContract contract = new IndustrialContract(UUID.randomUUID(), EconomyTypes.ContractKind.MASTER_SPECIAL,
                mode, masterCityId, creator, title, description, itemId, amount, reward,
                intakeRegistrationId, stationRegistrationId, now, deadlineTicks);
        contracts.put(contract.id(), contract); setDirty(); return contract;
    }

    public boolean acceptContract(UUID contractId, UUID cityId, UUID playerId, long now) {
        IndustrialContract contract = contracts.get(contractId);
        if (contract == null || !contract.accept(cityId, playerId, now)) return false;
        setDirty(); return true;
    }

    @Nullable
    public IndustrialShipment createShipment(UUID contractId,UUID cityId,UUID playerId,UUID fleetTrainId,
                                               long declaredAmount,long now,RailwayManager railway){
        IndustrialContract contract=contracts.get(contractId);CityFleetTrain train=railway.train(fleetTrainId);
        if(contract==null||train==null||!cityId.equals(train.cityId())||CreateRailwayAdapter.train(train.createTrainId())==null
                || activeShipmentByTrain.containsKey(fleetTrainId)||declaredAmount<=0L||declaredAmount>Config.ECONOMY_MAX_CONTRACT_AMOUNT.get())return null;
        boolean participant=contract.mode()==EconomyTypes.ContractMode.EXCLUSIVE?cityId.equals(contract.contractorCityId()):contract.participants().contains(cityId);
        if(!participant||(contract.state()!=EconomyTypes.ContractState.ACCEPTED&&contract.state()!=EconomyTypes.ContractState.IN_DELIVERY))return null;
        EconomyInfrastructureRegistration intake=infrastructure.get(contract.destinationRegistrationId());
        if(intake==null||!intake.available()||intake.type()!=EconomyTypes.InfrastructureType.CONTRACT_INTAKE||intake.stationRegistrationId()==null)return null;
        long reserved=shipments.values().stream().filter(value->value.contractId().equals(contractId)&&value.active())
                .mapToLong(IndustrialShipment::remainingAmount).reduce(0L,(left,right)->EconomyMath.saturatingAdd(left,right,Long.MAX_VALUE));
        long available=Math.max(0L,contract.remainingAmount()-Math.min(contract.remainingAmount(),reserved));
        if(declaredAmount>available)return null;
        IndustrialShipment shipment=new IndustrialShipment(UUID.randomUUID(),contractId,cityId,playerId,fleetTrainId,
                intake.registrationId(),contract.itemId(),declaredAmount,now);
        if(!shipment.depart(now)||!contract.markShipment(cityId))return null;
        shipments.put(shipment.shipmentId(),shipment);activeShipmentByTrain.put(fleetTrainId,shipment.shipmentId());setDirty();return shipment;
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
        setDirty(); return true;
    }

    public boolean disputeContract(UUID contractId, UUID issuerCityId) {
        IndustrialContract contract = contracts.get(contractId);
        if (contract == null || !contract.issuerCityId().equals(issuerCityId) || !contract.dispute()) return false;
        setDirty(); return true;
    }

    public long recordIntakeFlow(UUID registrationId, ResourceLocation itemId, long amount, ServerLevel level) {
        EconomyInfrastructureRegistration intake = infrastructure.get(registrationId);
        if (intake == null || !intake.available() || intake.type() != EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                || amount <= 0L) return 0L;
        long now=level.getGameTime();RailwayManager railway=RailwayManager.get(level);
        if(intake.stationRegistrationId()==null)return attributionFailure(intake,EconomyTypes.IntakeAttributionError.STATION_NOT_ASSIGNED,now);
        CityRailStation station=railway.station(intake.stationRegistrationId());
        var actualStation=station==null?null:CreateRailwayAdapter.station(station.createStationId());
        if(actualStation==null)return attributionFailure(intake,EconomyTypes.IntakeAttributionError.STATION_MISSING,now);
        var actualTrain=actualStation.getPresentTrain();
        if(actualTrain==null)return attributionFailure(intake,EconomyTypes.IntakeAttributionError.TRAIN_NOT_PRESENT,now);
        CityFleetTrain fleetTrain=railway.trainByCreateId(actualTrain.id);
        if(fleetTrain==null)return attributionFailure(intake,EconomyTypes.IntakeAttributionError.TRAIN_NOT_REGISTERED,now);
        UUID shipmentId=activeShipmentByTrain.get(fleetTrain.fleetTrainId());IndustrialShipment shipment=shipmentId==null?null:shipments.get(shipmentId);
        if(shipment==null||!shipment.active())return attributionFailure(intake,EconomyTypes.IntakeAttributionError.SHIPMENT_NOT_FOUND,now);
        if(!shipment.destinationIntakeId().equals(registrationId))return attributionFailure(intake,EconomyTypes.IntakeAttributionError.WRONG_INTAKE,now);
        if(!shipment.expectedItem().equals(itemId))return attributionFailure(intake,EconomyTypes.IntakeAttributionError.WRONG_ITEM,now);
        IndustrialContract contract=contracts.get(shipment.contractId());
        if(contract==null||contract.state().terminal()||contract.state()==EconomyTypes.ContractState.DISPUTED
                || !contract.destinationRegistrationId().equals(registrationId))return attributionFailure(intake,EconomyTypes.IntakeAttributionError.CONTRACT_INVALID,now);
        long limited=Math.min(amount,shipment.remainingAmount());long accepted=contract.recordDelivery(shipment.contractorCityId(),itemId,limited,now);
        if(accepted<=0L)return attributionFailure(intake,EconomyTypes.IntakeAttributionError.CONTRACT_INVALID,now);
        shipment.verify(itemId,accepted,now);intake.attributionError(EconomyTypes.IntakeAttributionError.NONE,now);
        if(!shipment.active())activeShipmentByTrain.remove(shipment.fleetTrainId(),shipment.shipmentId());setDirty();return accepted;
    }

    private long attributionFailure(EconomyInfrastructureRegistration intake,EconomyTypes.IntakeAttributionError error,long now){if(intake.lastAttributionError()!=error||now-intake.lastAttributionErrorAt()>100L){intake.attributionError(error,now);setDirty();}return 0L;}

    public long maximumGatewayOutput(UUID registrationId, ResourceLocation itemId, long requested) {
        EconomyInfrastructureRegistration gateway = infrastructure.get(registrationId);
        if (gateway == null || !gateway.available() || gateway.type() != EconomyTypes.InfrastructureType.WAREHOUSE_GATEWAY
                || masterCityId == null || !masterCityId.equals(gateway.cityId()) || requested <= 0L) return 0L;
        return Math.min(requested, warehouseStock(itemId));
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
        } else {
            changed = Math.min(current, amount);
            putStock(warehouseStock, itemId, current - changed);
        }
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

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        replenishProcurement(now);
        if (now % Math.max(200, Config.ECONOMY_SMART_ORDER_INTERVAL_TICKS.get()) == 0L) {
            reconcileInfrastructure(level);
            reconcileShipments(level);
            generateSmartOrders(now);
            processTimeouts(now);
            pruneTerminalHistory();
        }
    }

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
        EconomyInfrastructureRegistration intake = infrastructure.values().stream()
                .filter(value -> value.available() && value.type() == EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                        && masterCityId.equals(value.cityId())).findFirst().orElse(null);
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
                        intake.registrationId(), null, now, 0L);
            } catch (IllegalArgumentException ex) { continue; }
            procurementAvailable -= reward;
            procurementReserved = EconomyMath.addExactNonNegative(procurementReserved, reward);
            contracts.put(contract.id(), contract);
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
            if (deadline > 0L && now > deadline
                    && canCredit(treasuries,contract.issuerCityId(),contract.escrow()) && contract.fail()) {
                failActiveShipments(contract.id());
                long refund = contract.takeEscrow();
                credit(treasuries, contract.issuerCityId(), refund);
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
    }

    private boolean failActiveShipments(UUID contractId){boolean changed=false;for(IndustrialShipment shipment:shipments.values())if(shipment.contractId().equals(contractId)&&shipment.fail()){activeShipmentByTrain.remove(shipment.fleetTrainId(),shipment.shipmentId());changed=true;}return changed;}

    private void reconcileShipments(ServerLevel level){RailwayManager railway=RailwayManager.get(level);boolean changed=false;for(IndustrialShipment shipment:shipments.values())if(shipment.active()){IndustrialContract contract=contracts.get(shipment.contractId());CityFleetTrain train=railway.train(shipment.fleetTrainId());if(contract==null||contract.state().terminal()||contract.state()==EconomyTypes.ContractState.DELIVERED||contract.state()==EconomyTypes.ContractState.DISPUTED||train==null||CreateRailwayAdapter.train(train.createTrainId())==null){shipment.fail();activeShipmentByTrain.remove(shipment.fleetTrainId(),shipment.shipmentId());changed=true;}}if(changed)setDirty();}

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
        return true;
    }

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
        if (version <= 0 || version > FORMAT_VERSION) return manager;
        manager.masterCityId = root.hasUUID("MasterCity") ? root.getUUID("MasterCity") : null;
        manager.procurementAvailable = Math.max(0L, root.getLong("ProcurementAvailable"));
        manager.procurementReserved = Math.max(0L, root.getLong("ProcurementReserved"));
        manager.lastProcurementTick = Math.max(0L, root.getLong("LastProcurementTick"));
        readUuidLongMap(root, "Wallets", "Player", manager.wallets);
        readUuidLongMap(root, "Treasuries", "City", manager.treasuries);
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
                IndustrialContract shipmentContract=manager.contracts.get(shipment.contractId());if(shipmentContract!=null){manager.shipments.put(shipment.shipmentId(),shipment);if(shipment.active()&&(shipmentContract.state().terminal()||shipmentContract.state()==EconomyTypes.ContractState.DELIVERED||shipmentContract.state()==EconomyTypes.ContractState.DISPUTED))shipment.fail();if(shipment.active()){UUID conflict=manager.activeShipmentByTrain.putIfAbsent(shipment.fleetTrainId(),shipment.shipmentId());if(conflict!=null)shipment.fail();}}
            }catch(RuntimeException ignored){}
        }
        for(Tag raw:root.getList("SmartProfiles",Tag.TAG_COMPOUND)){
            try{CompoundTag tag=(CompoundTag)raw;ResourceLocation item=ResourceLocation.tryParse(tag.getString("Item"));if(item==null)continue;
                SmartOrderProfile profile=new SmartOrderProfile(item,tag.getLong("Target"),tag.getLong("Reorder"),tag.getLong("Batch"),tag.getLong("BasePrice"),
                        tag.getInt("MinBps"),tag.getInt("MaxBps"),tag.getLong("Cooldown"),tag.getLong("LastGenerated"));manager.smartProfiles.put(item,profile);
            }catch(RuntimeException ignored){}
        }
        manager.procurementAvailable=Math.min(manager.procurementAvailable,manager.procurementCap());
        manager.procurementReserved=manager.contracts.values().stream()
                .filter(value->value.kind()==EconomyTypes.ContractKind.MASTER_SMART&&!value.state().terminal())
                .mapToLong(IndustrialContract::escrow)
                .reduce(0L,(left,right)->EconomyMath.saturatingAdd(left,right,Long.MAX_VALUE));
        return manager;
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("FormatVersion",FORMAT_VERSION);if(masterCityId!=null)root.putUUID("MasterCity",masterCityId);
        root.putLong("ProcurementAvailable",procurementAvailable);root.putLong("ProcurementReserved",procurementReserved);root.putLong("LastProcurementTick",lastProcurementTick);
        root.put("Wallets",writeUuidLongMap(wallets,"Player"));root.put("Treasuries",writeUuidLongMap(treasuries,"City"));root.put("Warehouse",writeResourceLongMap(warehouseStock));
        ListTag infrastructureTags=new ListTag();for(var value:infrastructure.values()){CompoundTag tag=new CompoundTag();tag.putUUID("RegistrationId",value.registrationId());tag.putUUID("PhysicalId",value.physicalInstanceId());tag.putUUID("RegisteredBy",value.registeredBy());tag.putString("Type",value.type().name());if(value.cityId()!=null)tag.putUUID("CityId",value.cityId());tag.putString("Dimension",value.dimension().toString());tag.put("Pos",NbtUtils.writeBlockPos(value.pos()));tag.putBoolean("Available",value.available());if(value.stationRegistrationId()!=null)tag.putUUID("StationId",value.stationRegistrationId());tag.putString("LastError",value.lastAttributionError().name());tag.putLong("LastErrorAt",value.lastAttributionErrorAt());infrastructureTags.add(tag);}root.put("Infrastructure",infrastructureTags);
        ListTag contractTags=new ListTag();for(var value:contracts.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.id());tag.putString("Kind",value.kind().name());tag.putString("Mode",value.mode().name());tag.putUUID("IssuerCity",value.issuerCityId());tag.putUUID("CreatedBy",value.createdBy());tag.putString("Title",value.title());tag.putString("Description",value.description());tag.putString("Item",value.itemId().toString());tag.putLong("Amount",value.amount());tag.putLong("Reward",value.reward());tag.putUUID("Destination",value.destinationRegistrationId());if(value.stationRegistrationId()!=null)tag.putUUID("Station",value.stationRegistrationId());tag.putLong("CreatedAt",value.createdAt());tag.putLong("DeadlineDuration",value.deadlineDurationTicks());tag.putString("State",value.state().name());if(value.contractorCityId()!=null)tag.putUUID("ContractorCity",value.contractorCityId());if(value.acceptedByPlayerId()!=null)tag.putUUID("AcceptedBy",value.acceptedByPlayerId());tag.putLong("Delivered",value.deliveredAmount());tag.putLong("AcceptedAt",value.acceptedAt());tag.putLong("DeliveredAt",value.deliveredAt());tag.putLong("Escrow",value.escrow());tag.put("Contributions",writeUuidLongMap(value.contributions(),"City"));ListTag participants=new ListTag();for(UUID city:value.participants()){CompoundTag participant=new CompoundTag();participant.putUUID("City",city);participants.add(participant);}tag.put("Participants",participants);contractTags.add(tag);}root.put("Contracts",contractTags);
        ListTag listingTags=new ListTag();for(var value:listings.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.id());tag.putString("Type",value.type().name());tag.putUUID("Creator",value.creator());if(value.counterparty()!=null)tag.putUUID("Counterparty",value.counterparty());tag.putString("Item",value.itemId().toString());tag.putLong("Amount",value.amount());tag.putLong("Price",value.price());tag.putString("Meeting",value.meetingText());tag.putLong("CreatedAt",value.createdAt());tag.putString("State",value.state().name());tag.putLong("Escrow",value.escrow());if(value.requestedItemId()!=null)tag.putString("RequestedItem",value.requestedItemId().toString());listingTags.add(tag);}root.put("Listings",listingTags);
        ListTag shipmentTags=new ListTag();for(var value:shipments.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",value.shipmentId());tag.putUUID("Contract",value.contractId());tag.putUUID("ContractorCity",value.contractorCityId());tag.putUUID("CreatedBy",value.createdByPlayerId());tag.putUUID("Train",value.fleetTrainId());tag.putUUID("Destination",value.destinationIntakeId());tag.putString("Item",value.expectedItem().toString());tag.putLong("Declared",value.declaredAmount());tag.putLong("Verified",value.verifiedDeliveredAmount());tag.putLong("CreatedAt",value.createdAt());tag.putLong("DepartedAt",value.departedAt());tag.putLong("ArrivedAt",value.arrivedAt());tag.putLong("CompletedAt",value.completedAt());tag.putString("Status",value.status().name());shipmentTags.add(tag);}root.put("Shipments",shipmentTags);
        ListTag profileTags=new ListTag();for(var value:smartProfiles.values()){CompoundTag tag=new CompoundTag();tag.putString("Item",value.itemId().toString());tag.putLong("Target",value.targetStock());tag.putLong("Reorder",value.reorderPoint());tag.putLong("Batch",value.batchSize());tag.putLong("BasePrice",value.baseUnitPrice());tag.putInt("MinBps",value.minimumMultiplierBasisPoints());tag.putInt("MaxBps",value.maximumMultiplierBasisPoints());tag.putLong("Cooldown",value.cooldownTicks());tag.putLong("LastGenerated",value.lastGeneratedAt());profileTags.add(tag);}root.put("SmartProfiles",profileTags);
        return root;
    }

    private static ListTag writeUuidLongMap(Map<UUID,Long> values,String idKey){ListTag list=new ListTag();values.forEach((id,amount)->{if(amount<=0L)return;CompoundTag tag=new CompoundTag();tag.putUUID(idKey,id);tag.putLong("Amount",amount);list.add(tag);});return list;}
    private static void readUuidLongMap(CompoundTag root,String listKey,String idKey,Map<UUID,Long> target){for(Tag raw:root.getList(listKey,Tag.TAG_COMPOUND)){try{CompoundTag tag=(CompoundTag)raw;long amount=tag.getLong("Amount");if(tag.hasUUID(idKey)&&amount>0L)target.put(tag.getUUID(idKey),amount);}catch(RuntimeException ignored){}}}
    private static ListTag writeResourceLongMap(Map<ResourceLocation,Long> values){ListTag list=new ListTag();values.forEach((id,amount)->{if(amount<=0L)return;CompoundTag tag=new CompoundTag();tag.putString("Item",id.toString());tag.putLong("Amount",amount);list.add(tag);});return list;}
    private static void readResourceLongMap(CompoundTag root,String key,Map<ResourceLocation,Long> target){for(Tag raw:root.getList(key,Tag.TAG_COMPOUND)){CompoundTag tag=(CompoundTag)raw;ResourceLocation id=ResourceLocation.tryParse(tag.getString("Item"));long amount=tag.getLong("Amount");if(id!=null&&amount>0L)target.put(id,amount);}}
    private static <E extends Enum<E>> E readEnum(CompoundTag tag,String key,Class<E> type,E fallback){try{return Enum.valueOf(type,tag.getString(key));}catch(IllegalArgumentException ex){return fallback;}}
}
