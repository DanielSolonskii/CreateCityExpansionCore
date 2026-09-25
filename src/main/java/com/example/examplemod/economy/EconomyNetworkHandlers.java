package com.example.examplemod.economy;

import com.example.examplemod.Config;
import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.EconomyStatePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.example.examplemod.railway.RailwayManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class EconomyNetworkHandlers {
    private EconomyNetworkHandlers() {}

    public static void handle(ServerPlayer player, EconomyCommandPayload payload) {
        Context context=context(player,payload.pylonPos());if(context==null)return;
        if(payload.action()== EconomyCommandPayload.Action.REQUEST_STATE){sendState(context);return;}
        if(!context.unlocked){feedback(player,"locked");return;}
        boolean masterContract=payload.action()==EconomyCommandPayload.Action.CREATE_MASTER_SPECIAL;
        if(payload.action()==EconomyCommandPayload.Action.CREATE_CITY_CONTRACT||masterContract){
            String failure=contractCreationFailure(context,payload,player,masterContract);
            if(failure!=null){feedback(player,failure);sendState(context);return;}
        }
        boolean success=switch(payload.action()){
            case CREATE_CITY_CONTRACT -> validItem(payload.itemId())&&context.city.isAdmin(player.getUUID())&&context.manager.createCityContract(context.city.getId(),player.getUUID(),payload.title(),payload.description(),payload.itemId(),payload.amount(),payload.value(),payload.targetId(),payload.secondaryId(),payload.deadlineTicks(),player.serverLevel().getGameTime())!=null;
            case CREATE_MASTER_SPECIAL -> validItem(payload.itemId())&&context.master&&context.city.isAdmin(player.getUUID())&&context.manager.createMasterSpecial(player.getUUID(),payload.title(),payload.description(),payload.itemId(),payload.amount(),payload.value(),payload.targetId(),payload.secondaryId(),payload.deadlineTicks(),enumAt(EconomyTypes.ContractMode.values(),payload.typeOrdinal(),EconomyTypes.ContractMode.EXCLUSIVE),player.serverLevel().getGameTime())!=null;
            case ACCEPT_CONTRACT -> context.city.canBuild(player.getUUID())&&payload.targetId()!=null&&context.manager.acceptContract(payload.targetId(),context.city.getId(),player.getUUID(),player.serverLevel().getGameTime());
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
        feedback(player,success?"operation_complete":"operation_failed");sendState(context);
    }

    public static void sendState(Context context){
        EconomyManager manager=context.manager;CityManager cities=context.cities;RailwayManager railway=RailwayManager.get(context.player.serverLevel());List<EconomyStatePayload.ContractView> contracts=new ArrayList<>();List<EconomyStatePayload.ListingView> listings=new ArrayList<>();List<EconomyStatePayload.ShipmentView> shipments=new ArrayList<>();List<EconomyStatePayload.InfrastructureView> infrastructure=new ArrayList<>();List<EconomyStatePayload.StockView> warehouse=new ArrayList<>();
        if(context.unlocked){for(IndustrialContract c:manager.contracts()){City issuer=cities.getCity(c.issuerCityId());City contractor=c.contractorCityId()==null?null:cities.getCity(c.contractorCityId());String acceptedBy=c.acceptedByPlayerId()==null?"":context.player.getServer().getProfileCache().get(c.acceptedByPlayerId()).map(p->p.getName()).orElse(c.acceptedByPlayerId().toString().substring(0,8));contracts.add(new EconomyStatePayload.ContractView(c.id(),c.kind(),c.mode(),c.issuerCityId(),issuer==null?"?":issuer.getName(),acceptedBy,contractor==null?"":contractor.getName(),c.title(),c.description(),c.itemId(),c.amount(),c.deliveredAmount(),c.reward(),c.state(),c.contractorCityId(),c.deadlineAt(),c.destinationRegistrationId(),context.city.getId().equals(c.contractorCityId())||c.participants().contains(context.city.getId()),context.city.getId().equals(c.issuerCityId())));}contracts.sort(Comparator.comparingLong(EconomyStatePayload.ContractView::deadlineAt).thenComparing(EconomyStatePayload.ContractView::id));for(PrivateListing l:manager.listings()){String name=context.player.getServer().getProfileCache().get(l.creator()).map(p->p.getName()).orElse(l.creator().toString().substring(0,8));listings.add(new EconomyStatePayload.ListingView(l.id(),l.type(),l.creator(),name,l.counterparty(),l.itemId(),l.amount(),l.price(),l.meetingText(),l.state(),l.creator().equals(context.player.getUUID())||context.player.getUUID().equals(l.counterparty()),l.requestedItemId()));}for(IndustrialShipment value:manager.shipments()){var train=railway.train(value.fleetTrainId());shipments.add(new EconomyStatePayload.ShipmentView(value.shipmentId(),value.contractId(),value.fleetTrainId(),train==null?"?":train.lastKnownName(),value.declaredAmount(),value.verifiedDeliveredAmount(),value.status()));}for(var value:manager.infrastructure()){boolean cityOwned=context.city.getId().equals(value.cityId());boolean personal=value.cityId()==null&&value.registeredBy().equals(context.player.getUUID());if(cityOwned||personal)infrastructure.add(new EconomyStatePayload.InfrastructureView(value.registrationId(),value.type(),value.dimension(),value.pos(),value.available(),cityOwned,value.stationRegistrationId(),value.lastAttributionError()));}if(context.master)for(var profile:manager.smartProfiles()){long stock=manager.warehouseStock(profile.itemId());warehouse.add(new EconomyStatePayload.StockView(profile.itemId(),stock));}}
        PacketDistributor.sendToPlayer(context.player,new EconomyStatePayload(context.city.getPylonPos(),context.city.getId(),context.unlocked,context.master,manager.wallet(context.player.getUUID()),manager.treasury(context.city.getId()),manager.treasuryEscrow(context.city.getId()),manager.procurementAvailable(),manager.procurementReserved(),Config.ECONOMY_PROCUREMENT_CAP.get(),Config.ECONOMY_PRIVATE_FEE_BPS.get(),Config.ECONOMY_CITY_FEE_BPS.get(),contracts,listings,shipments,infrastructure,warehouse));
    }

    private static Context context(ServerPlayer player,net.minecraft.core.BlockPos pos){if(!(player.containerMenu instanceof CityPylonMenu menu)||!menu.getPylonPos().equals(pos)||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!(player.serverLevel().getBlockState(pos).getBlock() instanceof CityPylonBlock))return null;CityManager cities=CityManager.get(player.getServer().overworld());City city=cities.getCityByPylon(player.serverLevel().dimension().location(),pos);if(city==null||!city.hasMember(player.getUUID()))return null;EconomyManager manager=EconomyManager.get(player.serverLevel());boolean master=city.getId().equals(manager.masterCityId());return new Context(player,city,cities,manager,city.getLevel()>=2||master,master);}
    private static void feedback(ServerPlayer player,String key){player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy."+key),true);}
    private static String contractCreationFailure(Context context,EconomyCommandPayload payload,ServerPlayer player,boolean masterContract){
        if(!validItem(payload.itemId()))return "contract_invalid_item";
        if(!context.city.isAdmin(player.getUUID()))return "contract_permission_denied";
        if(masterContract&&!context.master)return "contract_master_only";
        if(payload.amount()<=0L||payload.amount()>Config.ECONOMY_MAX_CONTRACT_AMOUNT.get())return "contract_invalid_amount";
        if(payload.value()<=0L||payload.value()>Config.ECONOMY_MAX_TRANSACTION.get())return "contract_invalid_reward";
        long maxDeadline=Config.ECONOMY_MAX_DEADLINE_HOURS.get()*72_000L;
        if(payload.deadlineTicks()<0L||payload.deadlineTicks()>maxDeadline)return "contract_invalid_deadline";
        EconomyInfrastructureRegistration intake=payload.targetId()==null?null:context.manager.registration(payload.targetId());
        if(intake==null)return "contract_intake_missing";
        if(intake.type()!=EconomyTypes.InfrastructureType.CONTRACT_INTAKE)return "contract_wrong_infrastructure";
        if(!intake.available())return "contract_intake_unavailable";
        if(!context.city.getId().equals(intake.cityId()))return "contract_intake_wrong_city";
        long required=payload.value();
        if(!masterContract){
            try{required=Math.addExact(payload.value(),EconomyMath.applyBasisPoints(payload.value(),Config.ECONOMY_CITY_FEE_BPS.get()));}
            catch(ArithmeticException ex){return "contract_reward_overflow";}
        }
        if(context.manager.treasury(context.city.getId())<required)return "contract_insufficient_treasury";
        return null;
    }
    private static <T>T enumAt(T[] values,int index,T fallback){return index>=0&&index<values.length?values[index]:fallback;}
    private static boolean validItem(net.minecraft.resources.ResourceLocation id){return id!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)&&net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)!=net.minecraft.world.item.Items.AIR;}
    private static boolean validOptionalItem(net.minecraft.resources.ResourceLocation id){return id==null||validItem(id);}
    public record Context(ServerPlayer player,City city,CityManager cities,EconomyManager manager,boolean unlocked,boolean master){}
}
