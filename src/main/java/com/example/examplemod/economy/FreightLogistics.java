package com.example.examplemod.economy;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.railway.CreateRailwayAdapter;
import com.example.examplemod.economy.block.EconomyThroughputBlockEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Server-side coordinator shared by P2P, exports and imports. Never loads chunks. */
@EventBusSubscriber(modid=CreateCityExpansionCore.MODID)
public final class FreightLogistics {
    private final EconomyManager economy;
    public final Map<UUID,FreightPoint> points=new LinkedHashMap<>();
    public final Map<UUID,FreightAssignment> assignments=new LinkedHashMap<>();
    public final Map<UUID,FreightSellOffer> sales=new LinkedHashMap<>();
    private final Map<UUID,UUID> tokenDestinations=new LinkedHashMap<>();
    public FreightLogistics(EconomyManager economy){this.economy=economy;}
    public FreightPoint point(UUID id){return points.get(id);}
    public FreightPoint defaultReceive(UUID city){return points.values().stream().filter(p->p.cityId.equals(city)&&p.mode.receives()&&p.enabled&&!p.removed).sorted(Comparator.comparing(p->!p.defaultReceive)).findFirst().orElse(null);}
    public FreightPoint defaultSend(UUID city){return points.values().stream().filter(p->p.cityId.equals(city)&&p.mode.sends()&&p.online()&&!busy(p.id,null)).sorted(Comparator.comparing(p->!p.defaultSend)).findFirst().orElse(null);}
    public boolean receiving(UUID id,UUID city){FreightPoint p=point(id);return p!=null&&p.cityId.equals(city)&&p.mode.receives()&&p.enabled&&!p.removed;}
    public boolean sending(UUID id,UUID city,UUID subject){FreightPoint p=point(id);return p!=null&&p.cityId.equals(city)&&p.mode.sends()&&p.enabled&&!p.removed&&!busy(id,subject);}
    private boolean terminal(UUID subject){IndustrialContract c=economy.contract(subject);if(c!=null)return c.state().terminal()||c.state()==EconomyTypes.ContractState.DELIVERED;MarketOffer o=economy.marketOffer(subject);return o==null||o.status().terminal();}
    public boolean busy(UUID point,UUID except){return assignments.values().stream().anyMatch(a->point.equals(a.source)&&!a.subject.equals(except)&&!terminal(a.subject));}
    public void bind(UUID subject,UUID source,FreightPoint destination){assignments.put(subject,new FreightAssignment(subject,source,destination));economy.setDirty();}
    public UUID destination(UUID subject){FreightAssignment a=assignments.get(subject);return a==null?null:a.destination;}
    public void tokenCreated(UUID token,UUID subject){FreightAssignment a=assignments.get(subject);if(a!=null){tokenDestinations.put(token,a.destination);economy.setDirty();}}
    public boolean tokenTargets(UUID token,UUID receiver){return Objects.equals(tokenDestinations.get(token),receiver);}
    public boolean isFreightToken(UUID token){return tokenDestinations.containsKey(token);}

    public String register(ServerLevel level,City city,UUID player,BlockPos postboxPos,BlockPos packagerPos,String name,int mode){
        if(!city.isAdmin(player))return "permission";
        if(mode<0||mode>=FreightPoint.Mode.values().length||name.isBlank())return "invalid";
        if(!inside(city,level,postboxPos)||!inside(city,level,packagerPos))return "wrong_city";
        if(postboxPos.distSqr(packagerPos)>256)return "packager_missing";
        if(!level.hasChunkAt(postboxPos)||!level.hasChunkAt(packagerPos))return "unloaded";
        if(!(level.getBlockEntity(postboxPos) instanceof PostboxBlockEntity postbox))return "postbox_missing";
        if(!compatiblePackager(level,packagerPos))return "packager_missing";
        if(points.values().stream().anyMatch(p->p.dimension.equals(level.dimension().location())&&(p.postbox.equals(postboxPos)||p.packager.equals(packagerPos))))return "duplicate";
        if(points.values().stream().filter(p->p.cityId.equals(city.getId())).count()>=64)return "limit";
        FreightPoint p=new FreightPoint(UUID.randomUUID(),city.getId(),level.dimension().location(),postboxPos,packagerPos,name,FreightPoint.Mode.values()[mode]);
        if(station(level,postbox)==null)return "station_missing";
        if(p.mode.receives()&&(!inside(city,level,receiverPos(level,p))||!supportedReceiver(level,receiverPos(level,p))))return "receiver_missing";
        p.defaultReceive=p.mode.receives()&&defaultReceive(city.getId())==null;
        p.defaultSend=p.mode.sends()&&defaultSend(city.getId())==null;
        points.put(p.id,p);postbox.addressFilter=p.address;postbox.acceptsPackages=p.mode.receives();postbox.filterChanged();postbox.notifyUpdate();
        validate(level.getServer(),p);economy.setDirty();return "ok";
    }
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
        economy.setDirty();return true;
    }
    public void restoreAddress(MinecraftServer server,UUID id){
        FreightPoint p=point(id);if(p==null||!p.enabled||p.removed)return;
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,p.dimension));
        if(level!=null&&level.hasChunkAt(p.postbox)&&level.getBlockEntity(p.postbox) instanceof PostboxBlockEntity box){box.addressFilter=p.address;box.acceptsPackages=p.mode.receives();box.filterChanged();box.notifyUpdate();}
    }
    public boolean control(UUID city,UUID subject,UUID source,boolean running,MinecraftServer server){
        FreightAssignment a=assignments.get(subject);IndustrialContract c=economy.contract(subject);MarketOffer o=economy.marketOffer(subject);
        UUID sender=c!=null?(c.contractorCityId()!=null?c.contractorCityId():a!=null&&point(a.source)!=null?point(a.source).cityId:null):o!=null?economy.masterCityId():null;
        if(a==null||!city.equals(sender)||terminal(subject))return false;
        UUID selected=source==null?a.source:source;if(!sending(selected,city,subject))return false;
        FreightPoint p=point(selected);validate(server,p);FreightPoint dest=point(a.destination);if(dest!=null)validate(server,dest);
        if(running&&(!p.online()||dest==null||!dest.online()||!route(p,dest).equals("connected")))return false;
        a.source=selected;a.running=running;economy.setDirty();return true;
    }
    public static boolean inside(City city,ServerLevel level,BlockPos pos){
        int half=city.getTerritorySize()/2;return city.getPylonDimension().equals(level.dimension().location())&&Math.abs((long)pos.getX()-city.getPylonPos().getX())<=half&&Math.abs((long)pos.getZ()-city.getPylonPos().getZ())<=half;
    }
    private static GlobalStation station(ServerLevel level,PostboxBlockEntity box){
        if(!(box.target instanceof PackagePortTarget.TrainStationFrogportTarget))return null;
        return CreateRailwayAdapter.stationAt(level.dimension(),box.getBlockPos().offset(box.target.relativePos));
    }
    public static BlockPos receiverPos(ServerLevel level,FreightPoint p){
        var state=level.getBlockState(p.packager);return state.hasProperty(BlockStateProperties.FACING)?p.packager.relative(state.getValue(BlockStateProperties.FACING).getOpposite()):p.packager;
    }
    private static boolean supportedReceiver(ServerLevel level,BlockPos pos){return level.hasChunkAt(pos)&&(level.getBlockState(pos).getBlock() instanceof com.example.examplemod.economy.block.ContractIntakeBlock||level.getBlockState(pos).is(Blocks.CHEST)||level.getBlockState(pos).is(Blocks.BARREL));}
    private static boolean compatiblePackager(ServerLevel level,BlockPos pos){return level.getBlockEntity(pos) instanceof PackagerBlockEntity&&!(level.getBlockEntity(pos) instanceof com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity);}
    public FreightPoint receiver(ServerLevel level,BlockPos pos,Direction side){
        return points.values().stream().filter(p->p.dimension.equals(level.dimension().location())&&level.hasChunkAt(p.packager)&&p.packager.equals(pos.relative(side))&&receiverPos(level,p).equals(pos)).findFirst().orElse(null);
    }
    public void validate(MinecraftServer server,FreightPoint p){
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,p.dimension));String old=p.status;
        if(p.removed)p.status="removed";
        else if(!p.enabled)p.status="disabled";
        else if(level==null||!level.hasChunkAt(p.postbox)||!level.hasChunkAt(p.packager))p.status="unloaded";
        else {
            p.postboxPresent=level.getBlockEntity(p.postbox) instanceof PostboxBlockEntity;p.packagerPresent=compatiblePackager(level,p.packager);
            PostboxBlockEntity box=p.postboxPresent?(PostboxBlockEntity)level.getBlockEntity(p.postbox):null;
            GlobalStation station=box==null?null:station(level,box);p.stationPresent=station!=null;
            if(station!=null){p.station=station.id;p.stationName=station.name;}
            p.status=!p.postboxPresent?"postbox_missing":!p.packagerPresent?"packager_missing":!p.stationPresent?"station_missing":p.mode.receives()&&!supportedReceiver(level,receiverPos(level,p))?"receiver_missing":!p.address.equals(box.addressFilter)?"address_changed":"online";
        }
        if(!old.equals(p.status))economy.setDirty();
    }
    /** Connectivity only: no invented timetable, path or distance. */
    public static String route(FreightPoint source,FreightPoint dest){
        if(source==null||dest==null||source.station==null||dest.station==null)return "unknown";
        for(var graph:Create.RAILWAYS.trackNetworks.values())if(graph.getPoint(EdgePointType.STATION,source.station)!=null&&graph.getPoint(EdgePointType.STATION,dest.station)!=null)return "connected";
        return "disconnected";
    }
    public String state(FreightAssignment a){
        if(terminal(a.subject)){IndustrialContract c=economy.contract(a.subject);MarketOffer o=economy.marketOffer(a.subject);return c!=null&&c.state()==EconomyTypes.ContractState.COMPLETED||o!=null&&o.status()==EconomyTypes.MarketOfferStatus.COMPLETED?"complete":"expired";}
        FreightPoint dest=point(a.destination),source=point(a.source);
        if(dest==null||!dest.online())return "destination_unavailable";
        if(source==null)return "requires_setup";
        if(!source.online())return "source_unavailable";
        if(!route(source,dest).equals("connected"))return "route_error";
        return a.running?"running":"paused";
    }
    @SubscribeEvent public static void afterLevel(LevelTickEvent.Post event){
        if(event.getLevel() instanceof ServerLevel level&&level==level.getServer().overworld())EconomyManager.get(level).freight().tick(level.getServer());
    }
    public void tick(MinecraftServer server){
        for(FreightPoint p:points.values())validate(server,p);
        for(FreightAssignment a:assignments.values()){
            if(terminal(a.subject)||!a.running)continue;
            FreightPoint p=point(a.source),dest=point(a.destination);
            if(p==null||!p.online()){a.running=false;economy.setDirty();continue;}
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
        }
    }
    private void stamp(ServerLevel level,PackagerBlockEntity packager,ItemStack box,ShippingManifest manifest,FreightAssignment a){
        if(box.isEmpty()||!PackageItem.isPackage(box))return;
        var context=PackageItem.getOrderContext(box);if(context!=null&&context.stacks().stream().anyMatch(v->ShipmentTokenItemData.read(v.stack).isPresent()))return;
        var contents=PackageItem.getContents(box);net.minecraft.world.item.Item item=null;long count=0;
        for(int i=0;i<contents.getSlots();i++){ItemStack s=contents.getStackInSlot(i);if(s.isEmpty())continue;if(ShipmentTokenItemData.read(s).isPresent())return;if(item!=null&&item!=s.getItem())return;item=s.getItem();count+=s.getCount();}
        if(item==null)return;
        var result=economy.authorizeExportPackage(manifest.manifestId(),BuiltInRegistries.ITEM.getKey(item),count,level.getGameTime(),id->mark(box,id,a.address));
        if(result==EconomyTypes.IntakeAttributionError.NONE)packager.notifyUpdate();
    }
    public static void mark(ItemStack box,UUID token,String address){
        var current=PackageItem.getOrderContext(box);List<BigItemStack> stacks=new ArrayList<>(current==null?List.of():current.stacks());
        ItemStack seal=new ItemStack(CreateCityExpansionCore.SHIPMENT_SEAL.get());ShipmentTokenItemData.write(seal,token);stacks.add(new BigItemStack(seal,1));
        PackageItem.addOrderContext(box,new PackageOrderWithCrafts(new PackageOrder(stacks),current==null?List.of():current.orderedCrafts()));
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
        CompoundTag t=new CompoundTag();ListTag ps=new ListTag(),as=new ListTag(),candidates=new ListTag(),routes=new ListTag();
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
            IndustrialContract c=economy.contract(a.subject);MarketOffer o=economy.marketOffer(a.subject);
            if(c!=null){v.putString("Item",c.itemId().toString());v.putLong("Amount",c.amount());v.putLong("Delivered",c.deliveredAmount());}
            else if(o!=null){v.putString("Item",o.itemId().toString());v.putLong("Amount",o.acceptedQuantity());v.putLong("Delivered",o.acceptedQuantity()-o.remaining());}
            long flight=economy.shipmentTokens().stream().filter(s->s.subjectId().equals(a.subject)&&s.state()==EconomyTypes.ShipmentTokenState.ACTIVE).mapToLong(ShipmentToken::amount).sum();
            v.putLong("InFlight",flight);v.putLong("Available",Math.max(0,v.getLong("Amount")-v.getLong("Delivered")-flight));as.add(v);
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
