package com.example.examplemod.economy;

import com.example.examplemod.Config;
import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.EconomyStatePayload;
import com.example.examplemod.network.RailwayRouteOptionsPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.example.examplemod.railway.RailwayManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EconomyNetworkHandlers {
    private EconomyNetworkHandlers() {}

    public static void handle(ServerPlayer player, EconomyCommandPayload payload) {
        Context context=context(player,payload.pylonPos());if(context==null)return;
        if(payload.action()== EconomyCommandPayload.Action.REQUEST_STATE){sendState(context);return;}
        if(!context.unlocked&&payload.action()!=EconomyCommandPayload.Action.REGISTER_FREIGHT&&payload.action()!=EconomyCommandPayload.Action.MANAGE_FREIGHT){feedback(player,"locked");return;}
        if(payload.action()==EconomyCommandPayload.Action.REQUEST_FREIGHT_ROUTES){
            if(context.city.canBuild(player.getUUID())&&payload.targetId()!=null&&payload.secondaryId()!=null){var plan=context.manager.freight().routeOptions(player.serverLevel(),context.city,payload.targetId(),payload.secondaryId());PacketDistributor.sendToPlayer(player,new RailwayRouteOptionsPayload(payload.pylonPos(),payload.secondaryId(),plan.source(),plan.destination(),plan.options(),plan.error()));}sendState(context);return;
        }
        if(payload.action()==EconomyCommandPayload.Action.REVOKE_FREIGHT){
            String reason=!context.city.canBuild(player.getUUID())?"freight_revoke_denied"
                    :payload.targetId()==null?"freight_revoke_unknown"
                    :context.manager.revokeDispatch(payload.targetId(),context.city.getId());
            feedback(player,reason);sendState(context);return;
        }
        boolean specificFeedback=payload.action()==EconomyCommandPayload.Action.MANAGE_FREIGHT&&"rebind".equals(payload.note());
        boolean masterContract=payload.action()==EconomyCommandPayload.Action.CREATE_MASTER_SPECIAL;
        if(payload.action()==EconomyCommandPayload.Action.CREATE_CITY_CONTRACT||masterContract){
            String failure=contractCreationFailure(context,payload,player,masterContract);
            if(failure!=null){feedback(player,failure);sendState(context);return;}
        }
        boolean success=switch(payload.action()){
            case CREATE_FREIGHT_SELL -> context.city.isAdmin(player.getUUID())&&validItem(payload.itemId())&&context.manager.createFreightSale(context.city.getId(),player.getUUID(),payload.itemId(),payload.amount(),payload.value(),payload.deadlineTicks(),payload.title(),payload.description());
            case ACCEPT_FREIGHT_SELL -> context.city.isAdmin(player.getUUID())&&context.manager.acceptFreightSale(payload.targetId(),context.city.getId(),player.getUUID(),payload.secondaryId(),player.serverLevel().getGameTime());
            case REGISTER_FREIGHT -> {
                String result=context.manager.freight().register(player.serverLevel(),context.city,player.getUUID(),net.minecraft.core.BlockPos.of(payload.amount()),net.minecraft.core.BlockPos.of(payload.value()),payload.title(),payload.typeOrdinal());
                player.displayClientMessage(Component.translatable("gui.createcityexpansioncore.freight."+result),true);yield result.equals("ok");
            }
            case MANAGE_FREIGHT -> {
                boolean changed;
                if(specificFeedback){
                    String result=context.manager.freight().rebind(player.serverLevel(),context.city,player.getUUID(),
                            payload.targetId(),net.minecraft.core.BlockPos.of(payload.amount()),net.minecraft.core.BlockPos.of(payload.value()));
                    player.displayClientMessage(Component.translatable("gui.createcityexpansioncore.freight."+result),true);
                    changed=result.equals("ok");
                }else{
                    changed=context.city.isAdmin(player.getUUID())&&context.manager.freight().manage(context.city.getId(),payload.targetId(),payload.note(),payload.title());
                    if(changed&&payload.note().equals("toggle"))context.manager.freight().restoreAddress(player.getServer(),payload.targetId());
                }
                yield changed;
            }
            case CONTROL_DISPATCH -> context.city.canBuild(player.getUUID())&&context.manager.freight().control(context.city.getId(),payload.targetId(),payload.secondaryId(),payload.typeOrdinal()==1,player.getServer());
            case SELECT_FREIGHT_ROUTE -> context.city.canBuild(player.getUUID())&&payload.targetId()!=null&&payload.secondaryId()!=null&&parseUuid(payload.note())!=null&&context.manager.freight().selectDispatch(player.serverLevel(),context.city,payload.targetId(),payload.secondaryId(),parseUuid(payload.note()));
            case SETUP_FREIGHT -> context.city.isAdmin(player.getUUID())&&context.manager.setupFreight(payload.targetId(),context.city.getId(),payload.secondaryId());

            case ACCEPT_MARKET_OFFER -> context.city.isAdmin(player.getUUID())&&payload.targetId()!=null
                    &&context.manager.acceptFreightMarketOffer(payload.targetId(),context.city.getId(),player.getUUID(),payload.amount(),context.city.getTradeReputation(),player.serverLevel().getGameTime(),payload.secondaryId());
            case CREATE_CITY_CONTRACT -> validItem(payload.itemId())&&context.city.isAdmin(player.getUUID())&&context.manager.createCityContract(context.city.getId(),player.getUUID(),payload.title(),payload.description(),payload.itemId(),payload.amount(),payload.value(),payload.targetId(),payload.secondaryId(),payload.deadlineTicks(),player.serverLevel().getGameTime())!=null;
            case CREATE_MASTER_SPECIAL -> validItem(payload.itemId())&&context.master&&context.city.isAdmin(player.getUUID())&&context.manager.createMasterSpecial(player.getUUID(),payload.title(),payload.description(),payload.itemId(),payload.amount(),payload.value(),payload.targetId(),payload.secondaryId(),payload.deadlineTicks(),enumAt(EconomyTypes.ContractMode.values(),payload.typeOrdinal(),EconomyTypes.ContractMode.EXCLUSIVE),player.serverLevel().getGameTime())!=null;
            case ACCEPT_CONTRACT -> context.city.canBuild(player.getUUID())&&payload.targetId()!=null&&context.manager.acceptFreightContract(payload.targetId(),context.city.getId(),player.getUUID(),player.serverLevel().getGameTime(),payload.secondaryId());
            case CREATE_SHIPMENT -> context.city.canBuild(player.getUUID())&&payload.targetId()!=null&&payload.secondaryId()!=null&&context.manager.createShipment(payload.targetId(),context.city.getId(),player.getUUID(),payload.secondaryId(),payload.amount(),player.serverLevel().getGameTime(),RailwayManager.get(player.serverLevel()))!=null;
            case CANCEL_SHIPMENT -> context.city.canBuild(player.getUUID())&&payload.targetId()!=null&&context.manager.cancelShipment(payload.targetId(),context.city.getId());
            case CONFIRM_CONTRACT -> context.city.isAdmin(player.getUUID())&&payload.targetId()!=null&&context.manager.confirmContract(payload.targetId(),context.city.getId());
            case CANCEL_CONTRACT -> context.city.isAdmin(player.getUUID())&&payload.targetId()!=null&&context.manager.cancelContract(payload.targetId(),context.city.getId());
            case DISPUTE_CONTRACT -> context.city.isAdmin(player.getUUID())&&payload.targetId()!=null&&context.manager.disputeContract(payload.targetId(),context.city.getId());
            case CREATE_LISTING -> validItem(payload.itemId())&&validOptionalItem(payload.requestedItemId())&&context.manager.createListing(enumAt(EconomyTypes.ListingType.values(),payload.typeOrdinal(),EconomyTypes.ListingType.BUY),player.getUUID(),payload.itemId(),payload.amount(),payload.value(),payload.note(),player.serverLevel().getGameTime(),payload.requestedItemId())!=null;
            case ACCEPT_LISTING -> payload.targetId()!=null&&context.manager.acceptListing(payload.targetId(),player.getUUID());
            case MARK_HANDED -> payload.targetId()!=null&&context.manager.markListingHanded(payload.targetId(),player.getUUID());
            case CONFIRM_LISTING -> payload.targetId()!=null&&context.manager.confirmListing(payload.targetId(),player.getUUID());
            case CANCEL_LISTING -> payload.targetId()!=null&&context.manager.cancelListing(payload.targetId(),player.getUUID());
            case DISPUTE_LISTING -> payload.targetId()!=null&&context.manager.disputeListing(payload.targetId(),player.getUUID());
            case BIND_INFRASTRUCTURE -> payload.targetId()!=null&&context.city.isAdmin(player.getUUID())
                    &&context.manager.bindInfrastructure(payload.targetId(),player.getUUID(),context.city.getId());
            case BIND_INTAKE_STATION -> payload.targetId()!=null&&payload.secondaryId()!=null&&context.city.isAdmin(player.getUUID())
                    &&context.manager.bindIntakeStation(payload.targetId(),context.city.getId(),payload.secondaryId(),RailwayManager.get(player.serverLevel()));
            default -> false;
        };

        if(success)com.example.examplemod.auth.ServerTelemetryBridge.emit("ECONOMY_ACTION",player.getUUID(),context.city.getId(),payload.action().name());
        if(!success)com.example.examplemod.auth.ServerTelemetryBridge.emit("UI_ACTION",player.getUUID(),null,"market."+payload.action().name()+".REFUSED");
        if(payload.action()!=EconomyCommandPayload.Action.REGISTER_FREIGHT&&!specificFeedback)feedback(player,success?"operation_complete":"operation_failed");sendState(context);
    }

    /**
     * Server-authoritative push for every player currently looking at a pylon menu. Called on the
     * FreightLogistics throttle so breaking, restoring, rebinding or revoking updates the open
     * screen immediately instead of waiting for the client's poll.
     */
    public static void pushOpenMenus(MinecraftServer server){
        if(server==null)return;
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            if(!(player.containerMenu instanceof CityPylonMenu menu))continue;
            Context context=context(player,menu.getPylonPos(),false);
            if(context!=null)sendState(context);
        }
    }

    private static void giveManifest(ServerPlayer player,ShippingManifest manifest){
        if(manifest==null)return;
        var stack=ShippingManifestItemData.create(com.example.examplemod.CreateCityExpansionCore.SHIPPING_MANIFEST.get(),manifest.manifestId());
        if(!player.addItem(stack))player.drop(stack,false);
    }

    public static void sendState(Context context){
        EconomyManager manager=context.manager;CityManager cities=context.cities;RailwayManager railway=RailwayManager.get(context.player.serverLevel());List<EconomyStatePayload.OfferView> offers=new ArrayList<>();List<EconomyStatePayload.ContractView> contracts=new ArrayList<>();List<EconomyStatePayload.ListingView> listings=new ArrayList<>();List<EconomyStatePayload.ShipmentView> shipments=new ArrayList<>();List<EconomyStatePayload.InfrastructureView> infrastructure=new ArrayList<>();List<EconomyStatePayload.StockView> warehouse=new ArrayList<>();
        if(context.unlocked){for(IndustrialContract c:manager.contracts()){City issuer=cities.getCity(c.issuerCityId());City contractor=c.contractorCityId()==null?null:cities.getCity(c.contractorCityId());String acceptedBy=c.acceptedByPlayerId()==null?"":com.example.examplemod.auth.PlayerNames.name(context.player.getServer(), c.acceptedByPlayerId());contracts.add(new EconomyStatePayload.ContractView(c.id(),c.kind(),c.mode(),c.issuerCityId(),issuer==null?"?":issuer.getName(),acceptedBy,contractor==null?"":contractor.getName(),c.title(),c.description(),c.itemId(),c.amount(),c.deliveredAmount(),c.reward(),c.state(),c.contractorCityId(),c.deadlineAt(),manager.freight().destination(c.id())==null?c.destinationRegistrationId():manager.freight().destination(c.id()),context.city.getId().equals(c.contractorCityId())||c.participants().contains(context.city.getId()),context.city.getId().equals(c.issuerCityId())));}contracts.sort(Comparator.comparingLong(EconomyStatePayload.ContractView::deadlineAt).thenComparing(EconomyStatePayload.ContractView::id));for(PrivateListing l:manager.listings()){String name=com.example.examplemod.auth.PlayerNames.name(context.player.getServer(), l.creator());listings.add(new EconomyStatePayload.ListingView(l.id(),l.type(),l.creator(),name,l.counterparty(),l.itemId(),l.amount(),l.price(),l.meetingText(),l.state(),l.creator().equals(context.player.getUUID())||context.player.getUUID().equals(l.counterparty()),l.requestedItemId()));}for(IndustrialShipment value:manager.shipments()){var train=railway.train(value.fleetTrainId());shipments.add(new EconomyStatePayload.ShipmentView(value.shipmentId(),value.contractId(),value.fleetTrainId(),train==null?"?":train.lastKnownName(),value.declaredAmount(),value.verifiedDeliveredAmount(),value.status()));}for(var value:manager.infrastructure()){boolean cityOwned=context.city.getId().equals(value.cityId());boolean personal=value.cityId()==null&&value.registeredBy().equals(context.player.getUUID());if(cityOwned||personal)infrastructure.add(new EconomyStatePayload.InfrastructureView(value.registrationId(),value.type(),value.dimension(),value.pos(),value.available(),cityOwned,value.stationRegistrationId(),value.lastAttributionError()));}if(context.master)manager.warehouseStocks().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->warehouse.add(new EconomyStatePayload.StockView(entry.getKey(),entry.getValue())));}
        for(MarketOffer offer:manager.marketOffers()){var view=manager.marketReadView(offer,context.city.getTradeReputation());offers.add(new EconomyStatePayload.OfferView(offer.id(),offer.itemId(),offer.direction(),offer.quantity(),offer.remaining(),offer.unitPrice(),offer.bond(),offer.expiresAt(),offer.status(),context.city.getId().equals(offer.acceptedCity()),view.category(),view.referenceValue(),view.available(),view.target(),view.acceptLimit(),view.bondBps()));}
        int obligations=(int)offers.stream().filter(v->v.mine()&&!v.status().terminal()).count();
        PacketDistributor.sendToPlayer(context.player,new EconomyStatePayload(context.city.getPylonPos(),context.city.getId(),context.unlocked,context.master,manager.wallet(context.player.getUUID()),manager.treasury(context.city.getId()),manager.treasuryEscrow(context.city.getId()),manager.procurementAvailable(),manager.procurementReserved(),Config.ECONOMY_PROCUREMENT_CAP.get(),Config.ECONOMY_PRIVATE_FEE_BPS.get(),Config.ECONOMY_CITY_FEE_BPS.get(),context.city.getTradeReputation(),obligations,offers,contracts,listings,shipments,infrastructure,warehouse,manager.freight().snapshot(context.player.serverLevel(),context.city)));
    }

    private static Context context(ServerPlayer player,net.minecraft.core.BlockPos pos){return context(player,pos,true);}
    /** {@code strict} additionally enforces reach and block checks; a push only needs the open menu. */
    private static Context context(ServerPlayer player,net.minecraft.core.BlockPos pos,boolean strict){
        if(!(player.containerMenu instanceof CityPylonMenu menu)||!menu.getPylonPos().equals(pos))return null;
        if(strict&&(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!(player.serverLevel().getBlockState(pos).getBlock() instanceof CityPylonBlock)))return null;
        CityManager cities=CityManager.get(player.getServer().overworld());City city=cities.getCityByPylon(player.serverLevel().dimension().location(),pos);
        if(city==null||!city.hasMember(player.getUUID()))return null;
        EconomyManager manager=EconomyManager.get(player.serverLevel());boolean master=city.getId().equals(manager.masterCityId());
        return new Context(player,city,cities,manager,city.getLevel()>=2||master,master);
    }

    /**
     * Sends the economy snapshot for the player's own city, with no pylon in reach.
     *
     * <p>Same reasoning as the railway counterpart: the pylon-bound context needs
     * an open container and a distance limit, neither of which can hold for a map
     * opened from a key binding, and neither of which is what grants access.
     * Membership is. The city pylon stays only as the anchor the client matches
     * snapshots against.
     *
     * <p>Only {@link EconomyNetworkHandlers#sendState} is reachable from here, so
     * this path cannot authorise a transaction — contract, listing or shipment
     * actions stay behind the container check in {@link #context}.
     */
    public static boolean sendMemberView(ServerPlayer player) {
        CityManager cities = CityManager.get(player.getServer().overworld());
        City city = cities.getMemberCity(player.getUUID());
        if (city == null) {
            return false;
        }
        EconomyManager manager = EconomyManager.get(player.serverLevel());
        boolean master = city.getId().equals(manager.masterCityId());
        Context c = new Context(player, city, cities, manager,
                city.getLevel() >= 2 || master, master);
        sendState(c);
        return true;
    }

    private static void feedback(ServerPlayer player,String key){player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy."+key),true);}
    private static String contractCreationFailure(Context context,EconomyCommandPayload payload,ServerPlayer player,boolean masterContract){
        if(!validItem(payload.itemId()))return "contract_invalid_item";
        if(!context.city.isAdmin(player.getUUID()))return "contract_permission_denied";
        if(masterContract&&!context.master)return "contract_master_only";
        if(payload.amount()<=0L||payload.amount()>Config.ECONOMY_MAX_CONTRACT_AMOUNT.get())return "contract_invalid_amount";
        if(payload.value()<=0L||payload.value()>Config.ECONOMY_MAX_TRANSACTION.get())return "contract_invalid_reward";
        long maxDeadline=Config.ECONOMY_MAX_DEADLINE_HOURS.get()*72_000L;
        if(payload.deadlineTicks()<0L||payload.deadlineTicks()>maxDeadline)return "contract_invalid_deadline";
        if(!context.manager.freight().receiving(payload.targetId(),context.city.getId()))return "contract_intake_missing";
        long required=payload.value();
        if(!masterContract){
            try{required=Math.addExact(payload.value(),EconomyMath.applyBasisPoints(payload.value(),Config.ECONOMY_CITY_FEE_BPS.get()));}
            catch(ArithmeticException ex){return "contract_reward_overflow";}
        }
        if(context.manager.treasury(context.city.getId())<required)return "contract_insufficient_treasury";
        return null;
    }
    private static <T>T enumAt(T[] values,int index,T fallback){return index>=0&&index<values.length?values[index]:fallback;}
    private static UUID parseUuid(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException ex){return null;}}
    private static boolean validItem(net.minecraft.resources.ResourceLocation id){return id!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)&&net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)!=net.minecraft.world.item.Items.AIR;}
    private static boolean validOptionalItem(net.minecraft.resources.ResourceLocation id){return id==null||validItem(id);}
    public record Context(ServerPlayer player,City city,CityManager cities,EconomyManager manager,boolean unlocked,boolean master){}
}
