package com.example.examplemod.economy;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.railway.*;
import com.example.examplemod.economy.block.EconomyThroughputBlockEntity;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import java.util.*;
import com.example.examplemod.status.CcecStatus;

/** Server-side coordinator shared by P2P, exports and imports. Never loads chunks. */
@EventBusSubscriber(modid=CreateCityExpansionCore.MODID)
public final class FreightLogistics {
    public record RoutePlan(String source,String destination,List<RailwayRouteOption> options,String error){}
    private final EconomyManager economy;
    public final Map<UUID,FreightPoint> points=new LinkedHashMap<>();
    public final Map<UUID,FreightAssignment> assignments=new LinkedHashMap<>();
    public final Map<UUID,FreightSellOffer> sales=new LinkedHashMap<>();
    private final Map<UUID,UUID> tokenDestinations=new LinkedHashMap<>();
    /** Deferred server-authoritative push: set by any meaningful transition, flushed on a throttle. */
    private boolean pendingPush;
    private long lastPushTick=Long.MIN_VALUE/2;
    public FreightLogistics(EconomyManager economy){this.economy=economy;}
    public FreightPoint point(UUID id){return points.get(id);}
    public FreightPoint defaultReceive(UUID city){return points.values().stream().filter(p->p.cityId.equals(city)&&p.mode.receives()&&p.enabled&&!p.removed).sorted(Comparator.comparing(p->!p.defaultReceive)).findFirst().orElse(null);}
    public FreightPoint defaultSend(UUID city){return points.values().stream().filter(p->p.cityId.equals(city)&&p.mode.sends()&&p.online()&&!busy(p.id,null)).sorted(Comparator.comparing(p->!p.defaultSend)).findFirst().orElse(null);}
    public boolean receiving(UUID id,UUID city){FreightPoint p=point(id);return p!=null&&p.cityId.equals(city)&&p.mode.receives()&&p.enabled&&!p.removed;}
    public boolean sending(UUID id,UUID city,UUID subject){FreightPoint p=point(id);return p!=null&&p.cityId.equals(city)&&p.mode.sends()&&p.enabled&&!p.removed&&!busy(id,subject);}
    private boolean terminal(UUID subject){IndustrialContract c=economy.contract(subject);if(c!=null)return c.state().terminal()||c.state()==EconomyTypes.ContractState.DELIVERED;MarketOffer o=economy.marketOffer(subject);return o==null||o.status().terminal();}
    public boolean busy(UUID point,UUID except){return assignments.values().stream().anyMatch(a->point.equals(a.source)&&!a.subject.equals(except)&&!terminal(a.subject));}
    public void bind(UUID subject,UUID source,FreightPoint destination){assignments.put(subject,new FreightAssignment(subject,source,destination));markChanged();}
    public UUID destination(UUID subject){FreightAssignment a=assignments.get(subject);return a==null?null:a.destination;}
    public void tokenCreated(UUID token,UUID subject){FreightAssignment a=assignments.get(subject);if(a!=null){tokenDestinations.put(token,a.destination);markChanged();}}
    public boolean tokenTargets(UUID token,UUID receiver){return Objects.equals(tokenDestinations.get(token),receiver);}
    public boolean isFreightToken(UUID token){return tokenDestinations.containsKey(token);}
    /**
     * Stops dispatching for a revoked/closed subject without touching the assignment record itself,
     * so the point pair, address and destination identity stay intact for the UI. The selected rail
     * route is released by {@link #tick} on the next pass, which already closes terminal subjects.
     */
    public void stopDispatch(UUID subject){
        FreightAssignment a=assignments.get(subject);
        if(a==null)return;
        if(a.running||!a.dispatchError.isEmpty()){a.running=false;a.dispatchError="";markChanged();}
    }

    public String register(ServerLevel level,City city,UUID player,BlockPos postboxPos,BlockPos packagerPos,String name,int mode){
        if(!city.isAdmin(player))return "permission";
        if(mode<0||mode>=FreightPoint.Mode.values().length||name.isBlank())return "invalid";
        String failure=pairFailure(level,city,null,postboxPos,packagerPos);
        if(!failure.isEmpty())return failure;
        FreightPoint.Mode selected=FreightPoint.Mode.values()[mode];
        failure=receiverFailure(level,city,selected,packagerPos);
        if(!failure.isEmpty())return failure;
        FreightPoint p=new FreightPoint(UUID.randomUUID(),city.getId(),level.dimension().location(),postboxPos,packagerPos,name,selected);
        p.defaultReceive=selected.receives()&&defaultReceive(city.getId())==null;
        p.defaultSend=selected.sends()&&defaultSend(city.getId())==null;
        points.put(p.id,p);
        applyAddress(p,(PostboxBlockEntity)level.getBlockEntity(postboxPos));
        validate(level.getServer(),p);markChanged();return "ok";
    }

    /**
     * Repoint an existing Freight Point at a new Postbox/Packager pair. Everything that identifies
     * the trade — id, address, name, mode, city, assignments and in-flight token destinations — is
     * preserved, so a broken rebuild never orphans shipments. Returns a translation-safe reason key
     * when the replacement pair is not compatible.
     */
    public String rebind(ServerLevel level,City city,UUID player,UUID id,BlockPos postboxPos,BlockPos packagerPos){
        if(!city.isAdmin(player))return "permission";
        FreightPoint p=point(id);
        if(p==null||!p.cityId.equals(city.getId())||p.removed)return "invalid";
        if(!p.dimension.equals(level.dimension().location()))return "wrong_dimension";
        String failure=pairFailure(level,city,p,postboxPos,packagerPos);
        if(!failure.isEmpty())return failure;
        failure=receiverFailure(level,city,p.mode,packagerPos);
        if(!failure.isEmpty())return failure;
        PostboxBlockEntity box=(PostboxBlockEntity)level.getBlockEntity(postboxPos);
        p.postbox=postboxPos.immutable();p.packager=packagerPos.immutable();
        p.stationPos=null;p.stationMatched=true;
        applyAddress(p,box);
        GlobalStation detected=station(level,box);
        if(detected!=null){p.stationPos=stationPosOf(box);p.station=detected.id;p.stationName=detected.name;}
        p.postboxPresent=true;p.packagerPresent=true;p.stationPresent=detected!=null;
        assignments.values().stream().filter(a->id.equals(a.source)||id.equals(a.destination)).forEach(a->a.dispatchError="");
        validate(level.getServer(),p);markChanged();return "ok";
    }

    /** Pre-flight shared by {@link #register} and {@link #rebind}; never loads a chunk. */
    private String pairFailure(ServerLevel level,City city,FreightPoint self,BlockPos postboxPos,BlockPos packagerPos){
        boolean loaded=level.hasChunkAt(postboxPos)&&level.hasChunkAt(packagerPos);
        PostboxBlockEntity box=loaded&&level.getBlockEntity(postboxPos) instanceof PostboxBlockEntity b?b:null;
        return FreightPointRules.pairFailure(inside(city,level,postboxPos),inside(city,level,packagerPos),
                postboxPos.distSqr(packagerPos)>256,loaded,
                probePostbox(level,postboxPos),probePackager(level,packagerPos),
                points.values().stream().anyMatch(p->p!=self&&p.dimension.equals(level.dimension().location())
                        &&(p.postbox.equals(postboxPos)||p.packager.equals(packagerPos))),
                self==null&&points.values().stream().filter(p->p.cityId.equals(city.getId())).count()>=64,
                box!=null&&station(level,box)!=null);
    }
    private static String receiverFailure(ServerLevel level,City city,FreightPoint.Mode mode,BlockPos packagerPos){
        if(!mode.receives())return"";
        BlockPos receiver=receiverPos(level,packagerPos);
        return !inside(city,level,receiver)||!supportedReceiver(level,receiver)?"receiver_missing":"";
    }
    private static void applyAddress(FreightPoint p,PostboxBlockEntity box){
        box.addressFilter=p.address;box.acceptsPackages=p.mode.receives();box.filterChanged();box.notifyUpdate();
    }
    private static FreightPointRules.Probe probePostbox(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return FreightPointRules.Probe.MISSING;
        if(level.getBlockEntity(pos) instanceof PostboxBlockEntity)return FreightPointRules.Probe.PRESENT;
        return level.getBlockState(pos).isAir()?FreightPointRules.Probe.MISSING:FreightPointRules.Probe.INCOMPATIBLE;
    }
    private static FreightPointRules.Probe probePackager(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return FreightPointRules.Probe.MISSING;
        if(level.getBlockEntity(pos) instanceof PackagerBlockEntity pe&&!(pe instanceof com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity))
            return FreightPointRules.Probe.PRESENT;
        return level.getBlockState(pos).isAir()?FreightPointRules.Probe.MISSING:FreightPointRules.Probe.INCOMPATIBLE;
    }
    private static Long stationPosOf(PostboxBlockEntity box){
        if(!(box.target instanceof PackagePortTarget.TrainStationFrogportTarget))return null;
        return box.getBlockPos().offset(box.target.relativePos).asLong();
    }
    /** Records a meaningful state transition so open Logistics/overview screens get a fresh copy. */
    public void markChanged(){pendingPush=true;economy.setDirty();}
    public boolean manage(UUID city,UUID id,String action,String name){
        FreightPoint p=point(id);if(p==null||!p.cityId.equals(city))return false;
        switch(action){
            case "rename" -> {if(name.isBlank())return false;p.name=name.substring(0,Math.min(64,name.length()));}
            case "toggle" -> {p.enabled=!p.enabled;if(p.enabled)p.removed=false;}
            case "remove" -> {p.removed=true;p.enabled=false;}
            case "default" -> {if(!p.mode.receives()||!p.enabled||p.removed)return false;points.values().stream().filter(v->v.cityId.equals(city)).forEach(v->v.defaultReceive=false);p.defaultReceive=true;}
            default -> {return false;}
        }
        if(!p.enabled)assignments.values().stream().filter(a->id.equals(a.source)).forEach(a->a.running=false);
        markChanged();return true;
    }
    public void restoreAddress(MinecraftServer server,UUID id){
        FreightPoint p=point(id);if(p==null||!p.enabled||p.removed)return;
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,p.dimension));
        if(level!=null&&level.hasChunkAt(p.postbox)&&level.getBlockEntity(p.postbox) instanceof PostboxBlockEntity box)applyAddress(p,box);
        markChanged();
    }
    public boolean control(UUID city,UUID subject,UUID source,boolean running,MinecraftServer server){
        FreightAssignment a=assignments.get(subject);IndustrialContract c=economy.contract(subject);MarketOffer o=economy.marketOffer(subject);
        UUID sender=c!=null?(c.contractorCityId()!=null?c.contractorCityId():a!=null&&point(a.source)!=null?point(a.source).cityId:null):o!=null?economy.masterCityId():null;
        if(a==null||!city.equals(sender)||terminal(subject))return false;
        UUID selected=source==null?a.source:source;if(!sending(selected,city,subject))return false;
        FreightPoint p=point(selected);validate(server,p);FreightPoint dest=point(a.destination);if(dest!=null)validate(server,dest);
        if(running&&(!p.online()||dest==null||!dest.online()||!route(p,dest).equals("connected")))return false;
        if(!Objects.equals(a.source,selected)){clearDispatch(a,RailwayManager.get(server.overworld()));a.source=selected;}
        if(running){RailwayManager railway=RailwayManager.get(server.overworld());SelectedRailwayRoute route=a.train==null?null:railway.selectedRoute(a.train);var readiness=FreightDispatchPolicy.readiness(a.train!=null,route!=null&&a.routeSelection!=null&&a.routeSelection.equals(route.selectionId()),economy.treasury(city),route==null?0:route.option().tariffCost(),0,1);if(readiness==FreightDispatchPolicy.Readiness.ROUTE_NOT_SELECTED||readiness==FreightDispatchPolicy.Readiness.TRAIN_NOT_ASSIGNED){a.dispatchError="route_not_selected";markChanged();return false;}if(readiness==FreightDispatchPolicy.Readiness.INSUFFICIENT_FUNDS){a.dispatchError="insufficient_funds";markChanged();return false;}a.dispatchError="";}
        a.running=running;markChanged();return true;
    }
    public RoutePlan routeOptions(ServerLevel level,City city,UUID subject,UUID fleetId){
        FreightAssignment a=assignments.get(subject);FreightPoint source=a==null?null:point(a.source),destination=a==null?null:point(a.destination);RailwayManager railway=RailwayManager.get(level);
        if(a==null)return new RoutePlan("","",List.of(),"assignment_missing");
        if(source==null)return routeFailure(a,"","","source_missing");
        if(destination==null)return routeFailure(a,source.stationName,"","destination_missing");
        if(!city.getId().equals(source.cityId))return routeFailure(a,source.stationName,destination.stationName,"train_not_owned");
        validate(level.getServer(),source);validate(level.getServer(),destination);
        if(!source.online())return routeFailure(a,source.stationName,destination.stationName,"source_offline");
        if(!destination.online())return routeFailure(a,source.stationName,destination.stationName,"destination_offline");
        if(source.station==null)return routeFailure(a,source.stationName,destination.stationName,"source_station_missing");
        if(destination.station==null)return routeFailure(a,source.stationName,destination.stationName,"destination_station_missing");
        CityFleetTrain fleet=railway.train(fleetId);Train train=fleet==null?null:CreateRailwayAdapter.train(fleet.createTrainId());GlobalStation target=CreateRailwayAdapter.station(destination.station);
        if(fleet==null||train==null)return routeFailure(a,source.stationName,destination.stationName,"train_missing");
        if(!city.getId().equals(fleet.cityId()))return routeFailure(a,source.stationName,destination.stationName,"train_not_owned");
        if(fleet.activeLineId()!=null)return routeFailure(a,source.stationName,destination.stationName,"train_has_schedule");
        if(fleet.type()!=RailwayTypes.TrainType.FREIGHT&&fleet.type()!=RailwayTypes.TrainType.MIXED)return routeFailure(a,source.stationName,destination.stationName,"train_wrong_type");
        if(target==null)return routeFailure(a,source.stationName,destination.stationName,"destination_station_missing");
        if(train.getCurrentStation()==null||!source.station.equals(train.getCurrentStation().id))return routeFailure(a,source.stationName,destination.stationName,"train_not_at_source");
        if(railway.activeTrip(fleet.createTrainId())!=null)return routeFailure(a,source.stationName,destination.stationName,"trip_active");
        if(trainBusy(fleetId,subject))return routeFailure(a,source.stationName,destination.stationName,"train_busy");
        List<RailwayRouteOption> routes=RailwayRoutePlanner.plan(train,target,railway,RailwayOwnershipTypes.OwnerType.CITY,city.getId(),CityManager.get(level));
        String error="";if(routes.isEmpty())error=train.navigation.findPathTo(target,Double.MAX_VALUE)!=null?"private_access_denied":"no_route";
        updateDispatchError(a,error);
        railway.proposeRoutes(fleetId,routes);return new RoutePlan(source.stationName,destination.stationName,routes,error);
    }
    private RoutePlan routeFailure(FreightAssignment a,String source,String destination,String error){updateDispatchError(a,error);return new RoutePlan(source,destination,List.of(),error);}
    private void updateDispatchError(FreightAssignment a,String error){if(!Objects.equals(a.dispatchError,error)){a.dispatchError=error;markChanged();}}
    /** Reasons {@link #routeOptions} records from a physical endpoint check; all re-validated every tick. */
    private static final Set<String> ENDPOINT_ERRORS=Set.of("source_missing","destination_missing",
            "source_offline","destination_offline","source_station_missing","destination_station_missing");
    /**
     * A recorded endpoint failure is re-derived from the world on every pass, so it must not
     * survive the validation that clears it: as soon as both endpoints check out again the stale
     * reason is dropped instead of lingering until the player happens to replan a route.
     */
    void clearResolvedDispatchError(FreightAssignment a){
        if(!ENDPOINT_ERRORS.contains(a.dispatchError))return;
        FreightPoint source=point(a.source),dest=point(a.destination);
        if(source==null||dest==null||!source.online()||!dest.online())return;
        boolean resolved=switch(a.dispatchError){
            case "source_station_missing" -> source.station!=null;
            case "destination_station_missing" -> dest.station!=null;
            default -> true;
        };
        if(resolved)updateDispatchError(a,"");
    }
    /**
     * Pure handoff rule: only positive, server-authoritative evidence may hand a shipment off.
     *
     * <p>{@code carried} is the ACTIVE shipment token sitting inside the selected train's carriage
     * storage, and {@code travelling} is a started trip — {@link #tryStartTrain} refuses to start
     * navigation until the whole outstanding cargo is loaded, so an active trip already implies the
     * cargo was verified on that train. {@code handedOff} then sticks while the shipment is still in
     * the air and resets as soon as nothing of it is, so the next one is gated by the source again.
     *
     * <p>Source inventory is deliberately not an input. A source we cannot see (unloaded chunk), or
     * a source that reads empty for reasons we cannot account for, is UNKNOWN — never ABSENT — and
     * either of those read as proof of handoff would hand the shipment off while its cargo is still
     * sitting at the Postbox/Packager. The freight side never inspects that inventory at all.
     */
    static boolean handoff(boolean handedOff,boolean carried,boolean travelling,boolean airborne){
        if(carried||travelling)return true;
        if(!airborne)return false;
        return handedOff;
    }
    /** Recomputes {@link FreightAssignment#handedOff} from train/trip evidence alone. */
    private void refreshHandoff(MinecraftServer server,FreightAssignment a){
        boolean carried=false,travelling=false;
        if(a.train!=null){
            RailwayManager railway=RailwayManager.get(server.overworld());
            CityFleetTrain fleet=railway.train(a.train);
            Train train=fleet==null?null:CreateRailwayAdapter.train(fleet.createTrainId());
            carried=train!=null&&loadedAmount(train,a.subject)>0;
            travelling=fleet!=null&&railway.activeTrip(fleet.createTrainId())!=null;
        }
        boolean next=handoff(a.handedOff,carried,travelling,airborne(a.subject));
        if(a.handedOff!=next){a.handedOff=next;markChanged();}
    }
    private boolean airborne(UUID subject){return economy.shipmentTokens().stream().anyMatch(t->t.subjectId().equals(subject)&&t.state()==EconomyTypes.ShipmentTokenState.ACTIVE);}
    public boolean selectDispatch(ServerLevel level,City city,UUID subject,UUID fleetId,UUID optionId){
        FreightAssignment a=assignments.get(subject);FreightPoint source=a==null?null:point(a.source),destination=a==null?null:point(a.destination);RailwayManager railway=RailwayManager.get(level);CityFleetTrain fleet=railway.train(fleetId);Train train=fleet==null?null:CreateRailwayAdapter.train(fleet.createTrainId());RailwayRouteOption option=railway.proposedRoutes(fleetId).stream().filter(o->o.id().equals(optionId)).findFirst().orElse(null);
        if(a==null||source==null||destination==null||source.station==null||destination.station==null||fleet==null||train==null||fleet.activeLineId()!=null||!city.getId().equals(source.cityId)||!city.getId().equals(fleet.cityId())||train.getCurrentStation()==null||!source.station.equals(train.getCurrentStation().id)||option==null||!destination.station.equals(option.destinationStationId())||RailwayTripAccountingService.selectedPath(train,CreateRailwayAdapter.station(destination.station),option)==null)return false;
        if(a.train!=null&&!a.train.equals(fleetId))railway.consumeSelectedRoute(a.train);if(!railway.selectRoute(fleetId,optionId,level.getGameTime()))return false;
        SelectedRailwayRoute selected=railway.selectedRoute(fleetId);a.train=fleetId;a.routeSelection=selected.selectionId();a.running=false;a.dispatchError="";markChanged();return true;
    }
    private boolean trainBusy(UUID train,UUID except){return assignments.values().stream().anyMatch(a->train.equals(a.train)&&!a.subject.equals(except)&&!terminal(a.subject));}
    private static void clearDispatch(FreightAssignment a,RailwayManager railway){if(a.train!=null)railway.consumeSelectedRoute(a.train);a.train=null;a.routeSelection=null;a.running=false;a.dispatchError="";a.handedOff=false;}
    public static boolean inside(City city,ServerLevel level,BlockPos pos){
        int half=city.getTerritorySize()/2;return city.getPylonDimension().equals(level.dimension().location())&&Math.abs((long)pos.getX()-city.getPylonPos().getX())<=half&&Math.abs((long)pos.getZ()-city.getPylonPos().getZ())<=half;
    }
    private static GlobalStation station(ServerLevel level,PostboxBlockEntity box){
        if(!(box.target instanceof PackagePortTarget.TrainStationFrogportTarget))return null;
        return CreateRailwayAdapter.stationAt(level.dimension(),box.getBlockPos().offset(box.target.relativePos));
    }
    public static BlockPos receiverPos(ServerLevel level,BlockPos packager){
        var state=level.getBlockState(packager);return state.hasProperty(BlockStateProperties.FACING)?packager.relative(state.getValue(BlockStateProperties.FACING).getOpposite()):packager;
    }
    public static BlockPos receiverPos(ServerLevel level,FreightPoint p){return receiverPos(level,p.packager);}
    private static boolean supportedReceiver(ServerLevel level,BlockPos pos){return level.hasChunkAt(pos)&&(level.getBlockState(pos).getBlock() instanceof com.example.examplemod.economy.block.ContractIntakeBlock||level.getBlockState(pos).is(Blocks.CHEST)||level.getBlockState(pos).is(Blocks.BARREL));}
    private static boolean compatiblePackager(ServerLevel level,BlockPos pos){return level.getBlockEntity(pos) instanceof PackagerBlockEntity&&!(level.getBlockEntity(pos) instanceof com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity);}
    public FreightPoint receiver(ServerLevel level,BlockPos pos,Direction side){
        return points.values().stream().filter(p->p.dimension.equals(level.dimension().location())&&level.hasChunkAt(p.packager)&&p.packager.equals(pos.relative(side))&&receiverPos(level,p).equals(pos)).findFirst().orElse(null);
    }

    /**
     * Live reconciliation. Re-reads the physical world every tick and heals whatever a temporary
     * breakage lost — most importantly a replaced Postbox's address filter, which previously left
     * the point stuck in {@code address_changed} until a manual toggle. Never loads a chunk.
     *
     * @return {@code true} when the visible status changed, so callers can request a state push.
     */
    public boolean validate(MinecraftServer server,FreightPoint p){
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,p.dimension));
        if(level!=null&&level.hasChunkAt(p.postbox)&&level.getBlockEntity(p.postbox) instanceof PostboxBlockEntity box
                &&FreightPointRules.healAddress(p.address,box.addressFilter))applyAddress(p,box);
        String old=p.status;
        p.status=FreightPointRules.status(observe(level,p));
        if(!old.equals(p.status)){markChanged();return true;}
        return false;
    }

    /** World observation only; the decision itself lives in {@link FreightPointRules}. */
    private FreightPointRules.Observation observe(ServerLevel level,FreightPoint p){
        if(level==null||!level.hasChunkAt(p.postbox)||!level.hasChunkAt(p.packager))
            return new FreightPointRules.Observation(p.removed,p.enabled,false,FreightPointRules.Probe.MISSING,
                    FreightPointRules.Probe.MISSING,false,true,true,true);
        FreightPointRules.Probe postbox=probePostbox(level,p.postbox);
        FreightPointRules.Probe packager=probePackager(level,p.packager);
        p.postboxPresent=postbox==FreightPointRules.Probe.PRESENT;
        p.packagerPresent=packager==FreightPointRules.Probe.PRESENT;
        boolean stationPresent=false,stationMatched=true,receiverOk=true,addressMatches=true;
        if(postbox==FreightPointRules.Probe.PRESENT){
            PostboxBlockEntity box=(PostboxBlockEntity)level.getBlockEntity(p.postbox);
            addressMatches=p.address.equals(box.addressFilter);
            GlobalStation detected=station(level,box);
            stationPresent=detected!=null;p.stationPresent=stationPresent;
            if(detected!=null){
                Long detectedPos=stationPosOf(box);
                if(p.stationPos==null&&detectedPos!=null)p.stationPos=detectedPos;
                stationMatched=detectedPos==null||p.stationPos==null||p.stationPos.longValue()==detectedPos.longValue();
                p.stationMatched=stationMatched;
                if(stationMatched){p.station=detected.id;p.stationName=detected.name;}
            }
        }else p.stationPresent=false;
        receiverOk=!p.mode.receives()||supportedReceiver(level,receiverPos(level,p.packager));
        return new FreightPointRules.Observation(p.removed,p.enabled,true,postbox,packager,stationPresent,stationMatched,receiverOk,addressMatches);
    }
    /** Connectivity only: no invented timetable, path or distance. */
    public static String route(FreightPoint source,FreightPoint dest){
        if(source==null||dest==null||source.station==null||dest.station==null)return "unknown";
        for(var graph:Create.RAILWAYS.trackNetworks.values())if(graph.getPoint(EdgePointType.STATION,source.station)!=null&&graph.getPoint(EdgePointType.STATION,dest.station)!=null)return "connected";
        return "disconnected";
    }
    /**
     * Whether {@code cityId} may cancel this dispatch right now, and a translation-safe reason key
     * when it may not. Mirrors {@link EconomyManager#revokeDispatch} so the client never offers an
     * action the server would refuse.
     */
    public String revocable(UUID subject,UUID cityId){
        IndustrialContract contract=economy.contract(subject);
        if(contract!=null){
            FreightAssignment assignment=assignments.get(subject);
            FreightPoint source=assignment==null?null:point(assignment.source);
            FreightPoint destination=assignment==null?null:point(assignment.destination);
            boolean issuer=cityId.equals(contract.issuerCityId());
            boolean endpoint=source!=null&&cityId.equals(source.cityId)||destination!=null&&cityId.equals(destination.cityId);
            if(!issuer&&!endpoint)return "freight_revoke_denied";
            if(contract.state()==EconomyTypes.ContractState.DELIVERED||contract.state()==EconomyTypes.ContractState.DISPUTED)return "freight_revoke_state";
            if(contract.state().terminal())return "freight_revoke_already";
            return "ok";
        }
        MarketOffer offer=economy.marketOffer(subject);
        if(offer!=null){
            if(offer.direction()!=EconomyTypes.MarketDirection.BUY_FROM_MASTER||offer.acceptedCity()==null||!offer.acceptedCity().equals(cityId))return "freight_revoke_denied";
            if(offer.status().terminal())return "freight_revoke_already";
            if(offer.status()==EconomyTypes.MarketOfferStatus.OPEN)return "freight_revoke_state";
            return "ok";
        }
        return "freight_revoke_unknown";
    }
    public String state(FreightAssignment a){
        if(terminal(a.subject)){IndustrialContract c=economy.contract(a.subject);MarketOffer o=economy.marketOffer(a.subject);return c!=null&&c.state()==EconomyTypes.ContractState.COMPLETED||o!=null&&o.status()==EconomyTypes.MarketOfferStatus.COMPLETED?"complete":"expired";}
        FreightPoint dest=point(a.destination),source=point(a.source);
        if(dest==null||!dest.online())return "destination_unavailable";
        if(source==null)return "requires_setup";
        // A source outage only ever blocks cargo that has not left the endpoint yet. Once the
        // shipment has been handed off, the source gates the *next* packages (see tick) and owns
        // its own Freight Point card; reporting it against the shipment that is already on its way
        // is what produced the false "Источник недоступен" after a normal cargo handoff.
        if(!source.online())return a.handedOff?(a.running?"running":"paused"):"source_unavailable";
        if(!route(source,dest).equals("connected"))return a.running?"running":"paused";
        return a.running?"running":"paused";
    }
    CcecStatus uiStatus(FreightAssignment a,String state,long delivered,long amount,long inFlight,boolean outgoing,RailwayManager railway){
        CcecStatus endpoint=switch(state){
            case "complete" -> CcecStatus.of(CcecStatus.Category.SUCCESS,CcecStatus.Code.DELIVERED,CcecStatus.Action.NONE);
            case "expired" -> CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.SHIPMENT_REVOKED,CcecStatus.Action.FIX_PROBLEM);
            case "destination_unavailable" -> CcecStatus.of(CcecStatus.Category.WARNING,CcecStatus.Code.DESTINATION_OFFLINE,CcecStatus.Action.FIX_PROBLEM);
            case "source_unavailable" -> CcecStatus.of(CcecStatus.Category.WARNING,CcecStatus.Code.SOURCE_OFFLINE,CcecStatus.Action.FIX_PROBLEM);
            case "route_error" -> CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.RAIL_ROUTE_UNAVAILABLE,CcecStatus.Action.FIX_PROBLEM);
            default -> null;
        };
        if(endpoint!=null)return endpoint;if(!outgoing)return inFlight>0?CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.IN_TRANSIT,CcecStatus.Action.OPEN_FREIGHT_POINT):CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.WAITING_FOR_CARGO,CcecStatus.Action.NONE);
        if("insufficient_funds".equals(a.dispatchError))return CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.INSUFFICIENT_FUNDS,CcecStatus.Action.FIX_PROBLEM);
        if("private_access_denied".equals(a.dispatchError))return CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.PRIVATE_ACCESS_DENIED,CcecStatus.Action.SELECT_ROUTE);
        if("route_invalidated".equals(a.dispatchError))return CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.ROUTE_INVALIDATED,CcecStatus.Action.SELECT_ROUTE);
        if("no_route".equals(a.dispatchError))return CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.RAIL_ROUTE_UNAVAILABLE,CcecStatus.Action.SELECT_ROUTE);
        if(a.train==null)return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.TRAIN_NOT_ASSIGNED,CcecStatus.Action.ASSIGN_TRAIN);
        CityFleetTrain fleet=railway.train(a.train);if(fleet!=null&&railway.activeTrip(fleet.createTrainId())!=null)return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.IN_TRANSIT,CcecStatus.Action.OPEN_FREIGHT_POINT);
        RailwayTripLedger latest=railway.tripLedgers().stream().filter(t->t.fleetTrainId().equals(a.train)).max(Comparator.comparingLong(t->Math.max(t.startedAt(),t.settledAt()))).orElse(null);if(latest!=null&&latest.status()==RailwayTripLedger.Status.ROUTE_INVALIDATED)return CcecStatus.of(CcecStatus.Category.BLOCKED,CcecStatus.Code.ROUTE_INVALIDATED,CcecStatus.Action.SELECT_ROUTE);
        if(inFlight>0&&latest!=null&&latest.status()==RailwayTripLedger.Status.COMPLETED)return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.IN_TRANSIT,CcecStatus.Action.OPEN_FREIGHT_POINT);
        SelectedRailwayRoute selected=railway.selectedRoute(a.train);if(selected==null||a.routeSelection==null||!a.routeSelection.equals(selected.selectionId()))return CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.ROUTE_NOT_SELECTED,CcecStatus.Action.SELECT_ROUTE);
        // A dispatch that is set up but stuck used to say nothing beyond "waiting for cargo". The station
        // gate is checked first: without the train at the source station the mail transfer never
        // loads anything, so claiming "the cargo is not counted" there would be untrue.
        if(a.running)return switch(a.waitReason){
            case "train_not_at_source" -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.TRAIN_NOT_AT_SOURCE,CcecStatus.Action.OPEN_FREIGHT_POINT);
            case "cargo_not_counted" -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.CARGO_NOT_COUNTED,CcecStatus.Action.OPEN_FREIGHT_POINT);
            default -> CcecStatus.of(CcecStatus.Category.ACTION_REQUIRED,CcecStatus.Code.WAITING_FOR_CARGO,CcecStatus.Action.OPEN_FREIGHT_POINT);
        };
        return CcecStatus.of(CcecStatus.Category.SUCCESS,CcecStatus.Code.READY_TO_DISPATCH,CcecStatus.Action.START_DISPATCH);
    }
    @SubscribeEvent public static void afterLevel(LevelTickEvent.Post event){
        if(event.getLevel() instanceof ServerLevel level&&level==level.getServer().overworld())EconomyManager.get(level).freight().tick(level.getServer());
    }
    public void tick(MinecraftServer server){
        for(FreightPoint p:points.values())validate(server,p);
        for(FreightAssignment a:assignments.values()){
            // Runs before any guard: a source outage must not stop us from noticing that the
            // shipment has already left, and a recorded endpoint failure must not outlive the
            // validation that produced it.
            refreshHandoff(server,a);
            clearResolvedDispatchError(a);
            if(terminal(a.subject)){if(a.running){a.running=false;markChanged();}await(a,"");if(a.train!=null&&a.routeSelection!=null){RailwayManager railway=RailwayManager.get(server.overworld());SelectedRailwayRoute selected=railway.selectedRoute(a.train);if(selected!=null&&a.routeSelection.equals(selected.selectionId()))railway.consumeSelectedRoute(a.train);}continue;}if(!a.running){await(a,"");continue;}
            FreightPoint p=point(a.source),dest=point(a.destination);
            // A deleted source is terminal for the dispatch; a merely broken one only pauses it so
            // restoring the point resumes the same assignment instead of losing the player's intent.
            if(p==null){a.running=false;markChanged();continue;}
            if(!p.online())continue;
            if(dest==null||!dest.online()||!route(p,dest).equals("connected"))continue;
            ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,p.dimension));
            if(level==null||!(level.getBlockEntity(p.packager) instanceof PackagerBlockEntity packager))continue;
            IndustrialContract contract=economy.contract(a.subject);
            if(contract!=null){
                if(contract.deadlineAt()>0&&level.getGameTime()>contract.deadlineAt())continue;
                ShippingManifest manifest=economy.manifestForContract(contract.id(),p.cityId);if(manifest==null)continue;
                if(packager.heldBox.isEmpty()&&packager.queuedExitingPackages.isEmpty()&&packager.animationTicks==0){
                    long inFlight=economy.shipmentTokens().stream().filter(t->t.subjectId().equals(contract.id())&&t.state()==EconomyTypes.ShipmentTokenState.ACTIVE).mapToLong(ShipmentToken::amount).sum();
                    int count=(int)Math.min(64,Math.min(manifest.remainingToDispatch(),Math.max(0,contract.remainingAmount()-inFlight)));
                    if(count>0){
                        ItemStack item=new ItemStack(BuiltInRegistries.ITEM.get(contract.itemId()));
                        var context=new PackageOrderWithCrafts(new PackageOrder(List.of(new BigItemStack(item,count))),List.of());
                        var request=com.simibubi.create.content.logistics.packager.PackagingRequest.create(item,count,a.address,0,new org.apache.commons.lang3.mutable.MutableBoolean(true),0,UUID.randomUUID().hashCode(),context);
                        packager.attemptToSend(new ArrayList<>(List.of(request)));
                    }
                }
                stamp(level,packager,packager.heldBox,manifest,a);
                for(BigItemStack queued:packager.queuedExitingPackages)stamp(level,packager,queued.stack,manifest,a);
            }else if(packager.heldBox.isEmpty()&&packager.queuedExitingPackages.isEmpty()&&packager.animationTicks==0){
                economy.dispatchFreightImport(a.subject,level.getGameTime(),(token,item,count)->{
                    ItemStack box=PackageItem.containing(List.of(new ItemStack(BuiltInRegistries.ITEM.get(item),count)));
                    mark(box,token,a.address);packager.heldBox=box;packager.animationInward=false;packager.animationTicks=20;packager.notifyUpdate();
                });
            }
            tryStartTrain(a,server);
        }
        // Arrival intake is its own pass on purpose: the cargo has already left the source, so a
        // source outage must never be able to stop it from being accepted at the destination.
        for(FreightAssignment a:assignments.values())acceptArrivals(server,a);
        if(server.getTickCount()%20L==0L)observeAssignmentStates(server);
        flushPush(server);
    }
    /** The goods a package carries, with every dispatch seal stripped off. */
    private record Goods(List<ItemStack> stacks,long amount){}
    /**
     * Destination intake. A shipment package that has physically arrived in the destination Postbox
     * is accepted exactly the way a manual unpack is: the seal is dropped, the goods land in the
     * receiver behind the point's Packager, and the ledger call is the very same one the unpacking
     * handler already uses — so completion, escrow settlement, manifest closure and replay rejection
     * all apply unchanged and cannot drift apart from it.
     *
     * <p>This is the link that was missing. Nothing else ever hands a resting package to the ledger,
     * so a trip that had physically completed left the token ACTIVE, {@code deliveredAmount} at zero
     * and the dispatch reporting IN_TRANSIT forever. Presence in the destination Postbox is the
     * server-authoritative arrival signal, and this never loads a chunk: an unreadable destination is
     * simply not inspected, and the packages wait there until it can be.
     */
    private void acceptArrivals(MinecraftServer server,FreightAssignment a){
        if(terminal(a.subject))return;
        FreightPoint dest=point(a.destination);if(dest==null)return;
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,dest.dimension));
        if(level==null||!dest.online()||!level.hasChunkAt(dest.postbox))return;
        if(!(level.getBlockEntity(dest.postbox) instanceof PostboxBlockEntity box))return;
        IItemHandlerModifiable receiver=intakeOf(level,dest);if(receiver==null)return;
        for(int slot=0;slot<box.inventory.getSlots();slot++){
            ItemStack candidate=box.inventory.getStackInSlot(slot);
            if(!PackageItem.isPackage(candidate))continue;
            Set<UUID> ids=arrivalTokens(candidate);
            // Ambiguous packages are left strictly alone: crediting the wrong shipment is far worse
            // than leaving a box for the player to look at.
            if(ids.size()!=1)continue;
            UUID tokenId=ids.iterator().next();
            if(!isFreightToken(tokenId)||!tokenTargets(tokenId,dest.id))continue;
            ShipmentToken token=economy.shipmentToken(tokenId);
            if(token==null||token.state()!=EconomyTypes.ShipmentTokenState.ACTIVE)continue;
            Goods goods=strippedGoods(candidate);if(goods==null||goods.amount()!=token.amount())continue;
            economy.acceptShipmentPackage(dest.id,tokenId,token.itemId(),goods.amount(),level.getGameTime(),false,
                    new ArrivalTransaction(receiver,box.inventory,slot,goods.stacks()));
        }
    }
    /** Every seal a package carries, from its order and its contents, exactly like the unpacking handler. */
    private static Set<UUID> arrivalTokens(ItemStack box){
        Set<UUID> ids=new LinkedHashSet<>(packageTokens(box));
        ItemStackHandler contents=PackageItem.getContents(box);
        for(int slot=0;slot<contents.getSlots();slot++)ShipmentTokenItemData.read(contents.getStackInSlot(slot)).ifPresent(ids::add);
        return ids;
    }
    /** The package's contents as plain goods, or null when it is not a single well-formed cargo. */
    private static Goods strippedGoods(ItemStack box){
        ItemStackHandler contents=PackageItem.getContents(box);
        net.minecraft.world.item.Item item=null;long amount=0L;List<ItemStack> cargo=new ArrayList<>();
        for(int slot=0;slot<contents.getSlots();slot++){
            ItemStack raw=contents.getStackInSlot(slot);if(raw.isEmpty())continue;
            if(item!=null&&item!=raw.getItem())return null;
            item=raw.getItem();amount+=raw.getCount();cargo.add(ShipmentTokenItemData.withoutToken(raw));
        }
        return item==null||amount<=0L?null:new Goods(cargo,amount);
    }
    /** The physical container behind a point's Packager; null when there is nothing to unload into. */
    private static IItemHandlerModifiable intakeOf(ServerLevel level,FreightPoint dest){
        BlockPos pos=receiverPos(level,dest.packager);
        if(!level.hasChunkAt(pos))return null;
        if(level.getBlockEntity(pos) instanceof EconomyThroughputBlockEntity throughput)return throughput.buffer();
        if(level.getBlockEntity(pos) instanceof Container container)return new InvWrapper(container);
        return null;
    }
    /**
     * Moves one arrived package into the receiver: goods in, exactly one package slot consumed. The
     * receiver is snapshotted so a partially inserted stack is rolled back rather than half-applied,
     * and the package slot is only touched after every stack has landed.
     */
    private static final class ArrivalTransaction implements EconomyManager.PhysicalTransaction {
        private final IItemHandlerModifiable receiver,source;
        private final int slot;
        private final List<ItemStack> cargo;
        private final ItemStack[] snapshot;
        ArrivalTransaction(IItemHandlerModifiable receiver,IItemHandlerModifiable source,int slot,List<ItemStack> cargo){
            this.receiver=receiver;this.source=source;this.slot=slot;this.cargo=cargo;
            this.snapshot=new ItemStack[receiver.getSlots()];
            for(int i=0;i<receiver.getSlots();i++)snapshot[i]=receiver.getStackInSlot(i).copy();
        }
        public boolean prepare(){
            if(!fittable(receiver,cargo))return false;
            ItemStack current=source.getStackInSlot(slot);
            return !current.isEmpty()&&PackageItem.isPackage(current);
        }
        public boolean commit(){
            ItemStack current=source.getStackInSlot(slot);
            if(current.isEmpty()||!PackageItem.isPackage(current)){rollback();return false;}
            if(!fittable(receiver,cargo)){rollback();return false;}
            for(ItemStack stack:cargo)if(!ItemHandlerHelper.insertItemStacked(receiver,stack.copy(),false).isEmpty()){rollback();return false;}
            if(current.getCount()<=1)source.setStackInSlot(slot,ItemStack.EMPTY);
            else{current.shrink(1);source.setStackInSlot(slot,current);}
            return true;
        }
        public void rollback(){for(int i=0;i<snapshot.length&&i<receiver.getSlots();i++)receiver.setStackInSlot(i,snapshot[i].copy());}
        private static boolean fittable(IItemHandlerModifiable target,List<ItemStack> cargo){
            ItemStackHandler copy=new ItemStackHandler(target.getSlots());
            for(int i=0;i<copy.getSlots()&&i<target.getSlots();i++)copy.setStackInSlot(i,target.getStackInSlot(i).copy());
            for(ItemStack stack:cargo)if(!ItemHandlerHelper.insertItemStacked(copy,stack.copy(),false).isEmpty())return false;
            return true;
        }
    }
    /**
     * Ledger-level transitions (route invalidation, arrival, an invalidated trip) never mutate an
     * assignment, so nothing else would notice them. Compare the rendered state once a second and
     * request a push when it moves; the first pass simply seeds the baseline.
     */
    private void observeAssignmentStates(MinecraftServer server){
        RailwayManager railway=RailwayManager.get(server.overworld());
        StringBuilder fingerprint=new StringBuilder(assignments.size()*64);
        for(FreightAssignment a:assignments.values()){
            fingerprint.append(a.subject).append('=').append(state(a)).append('|').append(a.running)
                    .append('|').append(a.dispatchError).append('|').append(a.routeSelection)
                    .append('|').append(a.waitReason)
                    .append('|').append(ledgerStatus(railway,a)).append(';');
        }
        String current=fingerprint.toString();
        if(!current.equals(lastStateFingerprint)){lastStateFingerprint=current;markChanged();}
    }
    private static String ledgerStatus(RailwayManager railway,FreightAssignment a){
        if(a.train==null)return "";
        RailwayTripLedger latest=railway.tripLedgers().stream().filter(t->t.fleetTrainId().equals(a.train))
                .max(Comparator.comparingLong(t->Math.max(t.startedAt(),t.settledAt()))).orElse(null);
        return latest==null?"":latest.status().name();
    }
    private String lastStateFingerprint="";
    private void tryStartTrain(FreightAssignment a,MinecraftServer server){
        if(!a.running||a.train==null||a.routeSelection==null)return;RailwayManager railway=RailwayManager.get(server.overworld());CityFleetTrain fleet=railway.train(a.train);SelectedRailwayRoute selected=railway.selectedRoute(a.train);Train train=fleet==null?null:CreateRailwayAdapter.train(fleet.createTrainId());FreightPoint source=point(a.source),destination=point(a.destination);
        if(fleet==null||selected==null||!a.routeSelection.equals(selected.selectionId())||train==null||source==null||destination==null||source.station==null||destination.station==null){await(a,"");return;}
        if(railway.activeTrip(fleet.createTrainId())!=null||train.navigation.destination!=null){await(a,"");return;}
        // The train has to stand at the source station: that is where the station's mail transfer
        // loads the carriage storage, so nothing else can put this dispatch's cargo aboard.
        if(train.getCurrentStation()==null||!source.station.equals(train.getCurrentStation().id)){await(a,"train_not_at_source");return;}
        long remaining=remaining(a.subject);TrainCargo cargo=trainCargo(train,a.subject);
        // Boxes in the train that this dispatch will never accept are reported instead of a bare
        // "waiting for cargo"; an empty train stays an ordinary wait, not a false alarm.
        await(a,cargo.counted()==0&&cargo.stray()>0&&remaining>0?"cargo_not_counted":"");
        var readiness=FreightDispatchPolicy.readiness(true,true,economy.treasury(source.cityId),selected.option().tariffCost(),cargo.counted(),remaining);if(readiness==FreightDispatchPolicy.Readiness.WAITING_FOR_CARGO)return;if(readiness==FreightDispatchPolicy.Readiness.INSUFFICIENT_FUNDS){a.dispatchError="insufficient_funds";a.running=false;markChanged();return;}
        GlobalStation target=CreateRailwayAdapter.station(destination.station);var path=target==null?null:RailwayTripAccountingService.selectedPath(train,target,selected.option());
        if(path==null||train.navigation.startNavigation(path)<0){a.dispatchError="route_invalidated";a.running=false;markChanged();}
    }
    /** Records why the train has not left yet; pure presentation state, re-derived every tick. */
    private void await(FreightAssignment a,String reason){
        if(a.waitReason.equals(reason))return;
        a.waitReason=reason;markChanged();
    }
    /** Throttled server-authoritative push: at most one payload per 10 ticks, never dropped. */
    private void flushPush(MinecraftServer server){
        if(!pendingPush)return;
        long tick=server.getTickCount();
        if(tick-lastPushTick<10L)return;
        pendingPush=false;lastPushTick=tick;
        EconomyNetworkHandlers.pushOpenMenus(server);
    }
    /** True while an open Logistics/overview screen still needs a fresh copy of this state. */
    public boolean pushPending(){return pendingPush;}
    private long remaining(UUID subject){IndustrialContract contract=economy.contract(subject);if(contract!=null)return Math.max(0,contract.amount()-contract.deliveredAmount());MarketOffer offer=economy.marketOffer(subject);return offer==null?0:Math.max(0,offer.remaining());}
    /** What the selected train physically carries: cargo this dispatch may count, and packages it cannot. */
    private record TrainCargo(long counted,long stray){}
    /** How one single package counts for one dispatch. */
    record PackageVerdict(long counted,boolean stray){}
    /**
     * Token ids a package carries.
     *
     * <p>This must always go through {@code PackageItem.getOrderContext}. Create 6 stores the order
     * of any box produced by a packager request in {@code PACKAGE_ORDER_DATA} and resolves that
     * component in preference to the legacy {@code PACKAGE_ORDER_CONTEXT}; a stamp written only to
     * the legacy component is invisible here, and then also to Create's own unpacking call, which
     * reads the same accessor.
     */
    static Set<UUID> packageTokens(ItemStack box){
        Set<UUID> ids=new LinkedHashSet<>();
        PackageOrderWithCrafts context=PackageItem.getOrderContext(box);
        if(context!=null)context.stacks().forEach(value->ShipmentTokenItemData.read(value.stack).ifPresent(ids::add));
        return ids;
    }
    static PackageVerdict packageVerdict(ItemStack box,UUID subject,EconomyManager economy,Set<UUID> seen){
        return packageVerdict(packageTokens(box),subject,economy,seen);
    }
    /**
     * Classifies one package's token ids against a dispatch, which is exactly the predicate that
     * used to report a perfectly good box as unusable. Split out of {@link #trainCargo} so the
     * headless regression can drive it without a live Create train or a bootstrapped item registry.
     *
     * <p>A box counts only when it carries an ACTIVE token of this subject. A box that carries no
     * stamp at all, or carries this subject's stamp in a spent state, is {@code stray}: proof that
     * something is in the train that this dispatch will never accept. A box carrying a live token of
     * a different dispatch is neither — it is somebody else's cargo.
     *
     * @param seen token ids already attributed in this scan; guarantees a token is never counted twice.
     */
    static PackageVerdict packageVerdict(Set<UUID> ids,UUID subject,EconomyManager economy,Set<UUID> seen){
        boolean stamped=false,stale=false;long counted=0;
        for(UUID id:ids){
            if(!seen.add(id))continue;
            stamped=true;
            ShipmentToken token=economy.shipmentToken(id);
            if(token==null||!token.subjectId().equals(subject))continue;
            if(token.state()==EconomyTypes.ShipmentTokenState.ACTIVE){counted=counted>Long.MAX_VALUE-token.amount()?Long.MAX_VALUE:counted+token.amount();}
            else stale=true;
        }
        return new PackageVerdict(counted,!stamped||stale);
    }
    /**
     * Reads the train's carriage storage once. {@code counted} is the ACTIVE shipment-token amount
     * for this subject — the only figure readiness may rely on. {@code stray} counts packages the
     * dispatch provably cannot use: a box with no shipment stamp at all, or one stamped with this
     * subject's token that is no longer ACTIVE. Both mean "there is a box in the train that this
     * dispatch will never accept", which used to surface as a bare "waiting for cargo".
     *
     * <p>A box carrying a live token of a *different* dispatch is deliberately not stray: it is
     * somebody else's cargo and says nothing about this one.
     */
    private TrainCargo trainCargo(Train train,UUID subject){
        if(train==null)return new TrainCargo(0,0);
        Set<UUID> seen=new HashSet<>();long total=0,stray=0;
        for(var carriage:train.carriages){
            var inventory=carriage.storage.getAllItems();if(inventory==null)continue;
            for(int slot=0;slot<inventory.getSlots();slot++){
                ItemStack box=inventory.getStackInSlot(slot);if(!PackageItem.isPackage(box))continue;
                PackageVerdict verdict=packageVerdict(box,subject,economy,seen);
                total=total>Long.MAX_VALUE-verdict.counted()?Long.MAX_VALUE:total+verdict.counted();
                if(verdict.stray())stray++;
            }
        }
        return new TrainCargo(total,stray);
    }
    private long loadedAmount(Train train,UUID subject){return trainCargo(train,subject).counted();}
    private void stamp(ServerLevel level,PackagerBlockEntity packager,ItemStack box,ShippingManifest manifest,FreightAssignment a){
        if(box.isEmpty()||!PackageItem.isPackage(box))return;
        // Read the stamp exactly the way trainCargo() does, or a box that is already stamped looks
        // unstamped and every pass would mint another token instead of stopping.
        if(!packageTokens(box).isEmpty())return;
        var contents=PackageItem.getContents(box);net.minecraft.world.item.Item item=null;long count=0;
        for(int i=0;i<contents.getSlots();i++){ItemStack s=contents.getStackInSlot(i);if(s.isEmpty())continue;if(ShipmentTokenItemData.read(s).isPresent())return;if(item!=null&&item!=s.getItem())return;item=s.getItem();count+=s.getCount();}
        if(item==null)return;
        var result=economy.authorizeExportPackage(manifest.manifestId(),BuiltInRegistries.ITEM.getKey(item),count,level.getGameTime(),id->mark(box,id,a.address));
        if(result==EconomyTypes.IntakeAttributionError.NONE)packager.notifyUpdate();
    }
    public static void mark(ItemStack box,UUID token,String address){
        var current=PackageItem.getOrderContext(box);List<BigItemStack> stacks=new ArrayList<>(current==null?List.of():current.stacks());
        ItemStack seal=new ItemStack(CreateCityExpansionCore.SHIPMENT_SEAL.get());ShipmentTokenItemData.write(seal,token);stacks.add(new BigItemStack(seal,1));
        PackageOrderWithCrafts stamped=new PackageOrderWithCrafts(new PackageOrder(stacks),current==null?List.of():current.orderedCrafts());
        // Create 6 keeps the order in PACKAGE_ORDER_DATA whenever a box comes out of a packager
        // request, and getOrderContext() returns that component in preference to the legacy
        // PACKAGE_ORDER_CONTEXT. Writing only the legacy component therefore produced a package
        // whose stamp nobody could ever read again: not our own stamp guard, not trainCargo(), and
        // not Create's own unpacking call, which all go through getOrderContext(). The stamp was
        // minted, stored in a dead component, and the box counted as an unrecognised package.
        // setOrder() is the sanctioned writer and it takes the linkage metadata explicitly, so all
        // of Create's own order bookkeeping is carried over untouched.
        if(box.has(AllDataComponents.PACKAGE_ORDER_DATA)){
            PackageItem.PackageOrderData order=box.get(AllDataComponents.PACKAGE_ORDER_DATA);
            PackageItem.setOrder(box,order.orderId(),order.linkIndex(),order.isFinalLink(),order.fragmentIndex(),order.isFinal(),stamped);
        }else PackageItem.addOrderContext(box,stamped);
        PackageItem.clearAddress(box);PackageItem.addAddress(box,address);
    }
    public CompoundTag save(){
        CompoundTag t=new CompoundTag();ListTag ps=new ListTag(),as=new ListTag(),ts=new ListTag();points.values().forEach(p->ps.add(p.save()));assignments.values().forEach(a->as.add(a.save()));
        ListTag ss=new ListTag();sales.values().forEach(s->ss.add(s.save()));t.put("Sales",ss);
        tokenDestinations.forEach((token,point)->{CompoundTag v=new CompoundTag();v.putUUID("Token",token);v.putUUID("Point",point);ts.add(v);});t.put("Points",ps);t.put("Assignments",as);t.put("TokenDestinations",ts);return t;
    }
    public void load(CompoundTag t){
        for(Tag raw:t.getList("Sales",Tag.TAG_COMPOUND)){FreightSellOffer s=FreightSellOffer.load((CompoundTag)raw);sales.put(s.id(),s);}
        for(Tag raw:t.getList("Points",Tag.TAG_COMPOUND)){FreightPoint p=FreightPoint.load((CompoundTag)raw);p.status="unloaded";points.put(p.id,p);}
        for(Tag raw:t.getList("Assignments",Tag.TAG_COMPOUND)){FreightAssignment a=FreightAssignment.load((CompoundTag)raw);assignments.put(a.subject,a);}
        for(Tag raw:t.getList("TokenDestinations",Tag.TAG_COMPOUND)){CompoundTag v=(CompoundTag)raw;tokenDestinations.put(v.getUUID("Token"),v.getUUID("Point"));}
    }
    public CompoundTag snapshot(ServerLevel level,City city){
        CompoundTag t=new CompoundTag();ListTag ps=new ListTag(),as=new ListTag(),candidates=new ListTag(),routes=new ListTag();RailwayManager railway=RailwayManager.get(level);
        ListTag ss=new ListTag();for(FreightSellOffer s:sales.values())if(s.contract()==null){CompoundTag v=s.save();City seller=CityManager.get(level.getServer().overworld()).getCity(s.city());v.putString("CityName",seller==null?"?":seller.getName());ss.add(v);}t.put("Sales",ss);
        ListTag legacy=new ListTag();
        for(IndustrialContract c:economy.contracts())if(c.issuerCityId().equals(city.getId())&&!c.state().terminal()&&!assignments.containsKey(c.id())){CompoundTag v=new CompoundTag();v.putUUID("Subject",c.id());legacy.add(v);}
        for(MarketOffer o:economy.marketOffers())if(city.getId().equals(o.acceptedCity())&&o.direction()==EconomyTypes.MarketDirection.BUY_FROM_MASTER&&!o.status().terminal()&&!assignments.containsKey(o.id())){CompoundTag v=new CompoundTag();v.putUUID("Subject",o.id());legacy.add(v);}
        t.put("Legacy",legacy);
        for(FreightPoint p:points.values()){validate(level.getServer(),p);ps.add(p.save());}
        for(FreightAssignment a:assignments.values()){
            FreightPoint source=point(a.source),dest=point(a.destination);
            IndustrialContract ownedContract=economy.contract(a.subject);
            boolean outgoing=source!=null&&source.cityId.equals(city.getId())||ownedContract!=null&&city.getId().equals(ownedContract.contractorCityId());
            if(!outgoing&&(dest==null||!dest.cityId.equals(city.getId())))continue;
            CompoundTag v=a.save();v.putString("State",state(a));v.putBoolean("Outgoing",outgoing);
            String revoke=revocable(a.subject,city.getId());
            v.putBoolean("Revocable","ok".equals(revoke));
            if(!"ok".equals(revoke))v.putString("RevokeReason",revoke);
            IndustrialContract c=economy.contract(a.subject);MarketOffer o=economy.marketOffer(a.subject);
            if(c!=null){v.putString("Item",c.itemId().toString());v.putLong("Amount",c.amount());v.putLong("Delivered",c.deliveredAmount());}
            else if(o!=null){v.putString("Item",o.itemId().toString());v.putLong("Amount",o.acceptedQuantity());v.putLong("Delivered",o.acceptedQuantity()-o.remaining());}
            long flight=economy.shipmentTokens().stream().filter(s->s.subjectId().equals(a.subject)&&s.state()==EconomyTypes.ShipmentTokenState.ACTIVE).mapToLong(ShipmentToken::amount).sum();
            v.putLong("InFlight",flight);v.putLong("Available",Math.max(0,v.getLong("Amount")-v.getLong("Delivered")-flight));
            if(a.train!=null){CityFleetTrain fleet=railway.train(a.train);v.putString("TrainName",fleet==null?"?":fleet.lastKnownName());SelectedRailwayRoute selected=railway.selectedRoute(a.train);if(selected!=null&&a.routeSelection!=null&&a.routeSelection.equals(selected.selectionId())){v.putDouble("RouteDistance",selected.option().distance());v.putLong("RouteCost",selected.option().tariffCost());v.putLong("RouteEta",selected.option().estimatedTicks());v.putString("RouteStrategy",selected.option().strategy().name());}}
            ListTag trainTags=new ListTag();if(outgoing&&source!=null&&source.station!=null)for(CityFleetTrain fleet:railway.trainsForCity(city.getId())){if(fleet.type()!=RailwayTypes.TrainType.FREIGHT&&fleet.type()!=RailwayTypes.TrainType.MIXED)continue;Train train=CreateRailwayAdapter.train(fleet.createTrainId());boolean atSource=train!=null&&train.getCurrentStation()!=null&&source.station.equals(train.getCurrentStation().id);boolean available=fleet.activeLineId()==null&&FreightDispatchPolicy.eligibleTrain(fleet.type(),city.getId().equals(fleet.cityId()),atSource,railway.activeTrip(fleet.createTrainId())!=null,trainBusy(fleet.fleetTrainId(),a.subject));CompoundTag candidate=new CompoundTag();candidate.putUUID("Id",fleet.fleetTrainId());candidate.putString("Name",fleet.lastKnownName());candidate.putString("Type",fleet.type().name());candidate.putBoolean("Available",available);candidate.putBoolean("AtSource",atSource);trainTags.add(candidate);}v.put("TrainCandidates",trainTags);
            v.put("UiStatus",uiStatus(a,v.getString("State"),v.getLong("Delivered"),v.getLong("Amount"),flight,outgoing,railway).save());as.add(v);
        }
        for(FreightPoint p:points.values())if(p.cityId.equals(city.getId()))for(FreightPoint d:points.values()){
            CompoundTag r=new CompoundTag();r.putUUID("Source",p.id);r.putUUID("Destination",d.id);r.putString("State",route(p,d));routes.add(r);
            CompoundTag reverse=new CompoundTag();reverse.putUUID("Source",d.id);reverse.putUUID("Destination",p.id);reverse.putString("State",route(d,p));routes.add(reverse);
        }
        List<PostboxBlockEntity> boxes=new ArrayList<>();List<PackagerBlockEntity> packagers=new ArrayList<>();int half=city.getTerritorySize()/2;
        for(int x=(city.getPylonPos().getX()-half)>>4;x<=(city.getPylonPos().getX()+half)>>4;x++)for(int z=(city.getPylonPos().getZ()-half)>>4;z<=(city.getPylonPos().getZ()+half)>>4;z++){
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            for(var be:chunk.getBlockEntities().values())if(inside(city,level,be.getBlockPos())){if(be instanceof PostboxBlockEntity b)boxes.add(b);if(be instanceof PackagerBlockEntity p&&compatiblePackager(level,p.getBlockPos()))packagers.add(p);}
        }
        for(var box:boxes)for(var packager:packagers)if(box.getBlockPos().distSqr(packager.getBlockPos())<=256&&candidates.size()<128){
            if(points.values().stream().anyMatch(p->p.dimension.equals(level.dimension().location())&&(p.postbox.equals(box.getBlockPos())||p.packager.equals(packager.getBlockPos()))))continue;
            CompoundTag c=new CompoundTag();c.putLong("Postbox",box.getBlockPos().asLong());c.putLong("Packager",packager.getBlockPos().asLong());c.putString("Address",box.addressFilter==null?"":box.addressFilter);GlobalStation station=station(level,box);c.putString("Station",station==null?"":station.name);candidates.add(c);
        }
        FreightPoint master=defaultReceive(economy.masterCityId());if(master!=null)t.putUUID("MasterReceive",master.id);
        FreightPoint sender=defaultSend(economy.masterCityId());if(sender!=null)t.putUUID("MasterSend",sender.id);
        t.put("Points",ps);t.put("Assignments",as);t.put("Candidates",candidates);t.put("Routes",routes);return t;
    }
}
