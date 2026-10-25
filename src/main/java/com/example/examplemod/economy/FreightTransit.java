package com.example.examplemod.economy;

import com.example.examplemod.city.*;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;

/** Physical, sealed-package cross-docking. Transit never invokes economic receipt. */
public final class FreightTransit {
    private static final String CURSOR="CCECTransitHop";
    private final FreightLogistics freight;
    private final EconomyManager economy;
    public final FreightRoutingBook routes=new FreightRoutingBook();
    private final Map<UUID,String> status=new HashMap<>();
    public FreightTransit(FreightLogistics freight,EconomyManager economy){this.freight=freight;this.economy=economy;}
    public boolean activeCargo(UUID subject){return economy.shipmentTokens().stream().anyMatch(t->t.subjectId().equals(subject)&&t.state()==EconomyTypes.ShipmentTokenState.ACTIVE);}
    public String configure(ServerLevel level,City city,UUID subject,List<UUID> hubs,boolean regional){
        FreightAssignment a=freight.assignments.get(subject);FreightPoint source=a==null?null:freight.point(a.source),dest=a==null?null:freight.point(a.destination);
        if(a==null||source==null||dest==null||!freight.carriers.controls(a,city.getId()))return "permission";
        if(activeCargo(subject)||a.running)return "route_locked";
        if(!regional){routes.direct(subject);freight.carriers.clearPlan(subject);a.train=null;a.routeSelection=null;freight.markChanged();return "ok";}
        if(hubs.size()>FreightRoutingBook.MAX_HUBS||new HashSet<>(hubs).size()!=hubs.size()||hubs.contains(source.id)||hubs.contains(dest.id))return "route_invalid";
        var previous=source;
        for(UUID id:hubs){
            FreightPoint hub=freight.point(id);
            if(hub==null||hub.removed||!hub.enabled||hub.kind!=FreightPoint.Kind.HUB||!hub.dimension.equals(source.dimension)
                    ||(!hub.cityId.equals(city.getId())&&!hub.publicTransit))return "hub_denied";
            if(!FreightLogistics.route(previous,hub).equals("connected"))return "route_invalid";
            previous=hub;
        }
        if(!dest.dimension.equals(source.dimension)||!FreightLogistics.route(previous,dest).equals("connected"))return "route_invalid";
        if(a.train!=null){var railway=com.example.examplemod.railway.RailwayManager.get(level);var selected=railway.selectedRoute(a.train);if(selected!=null&&selected.selectionId().equals(a.routeSelection))railway.consumeSelectedRoute(a.train);}
        routes.set(subject,hubs);freight.carriers.clearPlan(subject);a.train=null;a.routeSelection=null;a.dispatchError="";freight.markChanged();return "ok";
    }
    public String kind(ServerLevel level,FreightPoint point,boolean hub){
        if(!hub){
            boolean inUse=economy.shipmentTokens().stream().anyMatch(t->t.state()==EconomyTypes.ShipmentTokenState.ACTIVE&&routes.path(t.tokenId()).contains(point.id));
            if(inUse)return "hub_in_use";point.kind=FreightPoint.Kind.ENDPOINT;point.publicTransit=false;point.hubBuffer=null;freight.markChanged();return "ok";
        }
        if(point.mode!=FreightPoint.Mode.BOTH||!bufferReady(level,point))return "hub_buffer";
        point.hubBuffer=bufferPos(level,point);point.kind=FreightPoint.Kind.HUB;point.forwarding=true;freight.markChanged();return "ok";
    }
    public String address(FreightAssignment a){
        if(!routes.regional(a.subject)||routes.hubs(a.subject).isEmpty())return a.address;
        FreightPoint next=freight.point(routes.hubs(a.subject).getFirst());return next==null?"":next.address;
    }
    public boolean dispatchReady(FreightAssignment a,MinecraftServer server){
        var source=freight.point(a.source);if(source==null)return false;FreightPoint previous=source;
        for(UUID id:routes.hubs(a.subject)){
            var hub=freight.point(id);if(hub==null||hub.kind!=FreightPoint.Kind.HUB||!hub.enabled||hub.removed||(!hub.cityId.equals(source.cityId)&&!hub.publicTransit))return false;
            freight.validate(server,hub);if(!availableOrUnloaded(hub)||!FreightLogistics.route(previous,hub).equals("connected"))return false;previous=hub;
        }
        var dest=freight.point(a.destination);return availableOrUnloaded(dest)&&FreightLogistics.route(previous,dest).equals("connected");
    }
    static boolean availableOrUnloaded(FreightPoint point){return point!=null&&point.enabled&&!point.removed&&point.station!=null&&(point.online()||point.status.equals("unloaded"));}
    public static void initializeEnvelope(ItemStack box){setCursor(box,0);}
    static int cursor(ItemStack box){var data=box.get(DataComponents.CUSTOM_DATA);return data==null?-1:data.copyTag().contains(CURSOR,3)?data.copyTag().getInt(CURSOR):-1;}
    private static void setCursor(ItemStack box,int hop){var old=box.get(DataComponents.CUSTOM_DATA);var t=old==null?new CompoundTag():old.copyTag();t.putInt(CURSOR,hop);box.set(DataComponents.CUSTOM_DATA,CustomData.of(t));}
    public static boolean bufferReady(ServerLevel level,FreightPoint p){
        return FreightStorage.transit(level,p)!=null;
    }
    public static BlockPos bufferPos(ServerLevel level,FreightPoint p){return p.hubBuffer!=null?p.hubBuffer:FreightLogistics.receiverPos(level,p);}
    public void tick(MinecraftServer server){
        if(server.getTickCount()%10!=0)return;
        for(FreightPoint hub:freight.points.values())if(hub.kind==FreightPoint.Kind.HUB&&!hub.removed){
            ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,hub.dimension));
            var warehouse=level==null?null:FreightStorage.transit(level,hub);
            if(warehouse==null){observe(hub.id,"unavailable");continue;}
            if(!(level.getBlockEntity(hub.postbox) instanceof PostboxBlockEntity postbox)){observe(hub.id,"unavailable");continue;}
            var buffer=warehouse.inventory();String current="idle";
            if(!hub.enabled||hub.status.equals("territory_conflict")){observe(hub.id,"paused");continue;}
            // Release last tick's stored packages first; newly arrived cargo stays visible in the warehouse.
            for(int slot=0;hub.forwarding&&slot<buffer.getSlots();slot++){
                ItemStack box=buffer.getStackInSlot(slot);UUID token=eligible(box,hub,level.getGameTime());if(token==null)continue;
                UUID nextId=routes.next(token,hub.id,cursor(box));FreightPoint next=freight.point(nextId);
                if(next==null||next.removed||!next.enabled){current="next_unavailable";continue;}
                ItemStack forwarded=box.copy();setCursor(forwarded,cursor(box)+1);PackageItem.clearAddress(forwarded);PackageItem.addAddress(forwarded,next.address);
                if(move(buffer,slot,postbox.inventory,forwarded)){postbox.notifyUpdate();warehouse.changed();current="forwarded";}else current="outbox_full";
            }
            for(int slot=0;slot<postbox.inventory.getSlots();slot++){
                ItemStack box=postbox.inventory.getStackInSlot(slot);UUID token=eligible(box,hub,level.getGameTime());if(token==null)continue;
                if(move(postbox.inventory,slot,buffer,box.copy())){postbox.notifyUpdate();warehouse.changed();current="stored";
                    var assignment=freight.assignments.get(economy.shipmentToken(token).subjectId());if(assignment!=null&&!assignment.handedOff){assignment.handedOff=true;freight.markChanged();}
                }else current="buffer_full";
            }
            observe(hub.id,hub.forwarding?current:"paused");
        }
    }
    private void observe(UUID id,String next){if(!next.equals(status.put(id,next)))freight.markChanged();}
    /** No split, extraction, restamping, receipt, token consumption or money operation. */
    static boolean move(IItemHandlerModifiable from,int slot,IItemHandlerModifiable to,ItemStack stack){
        if(stack.isEmpty()||stack.getCount()!=1||from.getStackInSlot(slot).isEmpty())return false;
        for(int i=0;i<to.getSlots();i++)if(to.getStackInSlot(i).isEmpty()&&to.isItemValid(i,stack)&&to.getSlotLimit(i)>=1){to.setStackInSlot(i,stack);from.setStackInSlot(slot,ItemStack.EMPTY);return true;}return false;
    }
    private UUID eligible(ItemStack box,FreightPoint hub,long now){
        if(!PackageItem.isPackage(box)||box.getCount()!=1||!PackageItem.matchAddress(box,hub.address))return null;
        var ids=FreightLogistics.packageTokens(box);if(ids.size()!=1)return null;UUID id=ids.iterator().next();ShipmentToken token=economy.shipmentToken(id);
        if(token==null)return null;
        var content=PackageItem.getContents(box);long amount=0;
        for(int i=0;i<content.getSlots();i++){var item=content.getStackInSlot(i);if(item.isEmpty())continue;if(!BuiltInRegistries.ITEM.getKey(item.getItem()).equals(token.itemId()))return null;amount+=item.getCount();}
        var path=routes.path(id);UUID receiver=path.isEmpty()?null:path.getLast();
        return receiver!=null&&freight.tokenTargets(id,receiver)&&eligibleToken(token,receiver,hub.id,cursor(box),token.itemId(),amount,now)?id:null;
    }
    boolean eligibleToken(ShipmentToken token,UUID receiver,UUID hub,int hop,net.minecraft.resources.ResourceLocation item,long amount,long now){
        if(token==null||token.state()!=EconomyTypes.ShipmentTokenState.ACTIVE||token.expiresAt()>0&&now>token.expiresAt()
                ||!token.itemId().equals(item)||amount!=token.amount()||routes.next(token.tokenId(),hub,hop)==null)return false;
        var path=routes.path(token.tokenId());return !path.isEmpty()&&path.getLast().equals(receiver);
    }
    public CompoundTag snapshot(ServerLevel level,City viewer){
        var out=new CompoundTag();var hubs=new ListTag();
        for(var p:freight.points.values())if(p.kind==FreightPoint.Kind.HUB&&!p.removed&&(p.cityId.equals(viewer.getId())||p.publicTransit)){
            var t=new CompoundTag();t.putUUID("Id",p.id);t.putString("Status",status.getOrDefault(p.id,"unavailable"));
            if(p.cityId.equals(viewer.getId())&&bufferReady(level,p)){
                var buffer=FreightStorage.transit(level,p).inventory();int packages=0;
                for(int i=0;i<buffer.getSlots();i++)if(PackageItem.isPackage(buffer.getStackInSlot(i)))packages++;
                t.putInt("Packages",packages);t.putInt("Capacity",buffer.getSlots());
            }hubs.add(t);
        }out.put("Hubs",hubs);return out;
    }
}
