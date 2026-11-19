package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.logistics.LogisticsMapOverlay;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.places.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Short-lived source for the existing atlas catalog and renderer. No place labels are persisted by the client. */
public final class AtlasPlaces {
    private static final UUID NONE=new UUID(0,0);
    private static UUID view,world,lastReply,catalogReceipt;
    private static long epoch,deadline,next,validUntil;
    private static ResourceLocation dimension;
    private static PlaceNetwork.Request pending;
    private static AtlasPlace.Source link;
    private static String status="";
    private static Map<UUID,AtlasPlace> rows=Map.of();
    private static Map<UUID,CityMapPanel.Selection> indexed=Map.of();
    private static List<CityMapPanel.Selection> selections=List.of();
    private AtlasPlaces(){}
    public static Component text(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.places."+key,args);}
    static String key(Enum<?> value){return value.name().toLowerCase(Locale.ROOT);}
    static boolean supported(){
        var c=Minecraft.getInstance().getConnection();return c!=null && c.hasChannel(PlaceNetwork.Request.TYPE);
    }
    private static boolean bound(ResourceLocation dim){
        return dim!=null && dim.equals(dimension) && view!=null && view.equals(CityPublicDirectory.viewId()) &&
                world!=null && world.equals(CityPublicDirectory.worldId(dim)) && epoch==CityPublicDirectory.accessEpoch();
    }
    public static boolean ready(ResourceLocation dim){return bound(dim)&&Util.getMillis()<validUntil;}
    static void clear(){
        view=world=lastReply=catalogReceipt=null;dimension=null;pending=null;link=null;validUntil=next=deadline=0;status="";
        rows=Map.of();indexed=Map.of();selections=List.of();
    }
    public static List<CityMapPanel.Selection> selections(ResourceLocation dim){return ready(dim)?selections:List.of();}
    public static CityMapPanel.Selection selection(UUID id,ResourceLocation dim){return id!=null && ready(dim)?indexed.get(id):null;}
    public static AtlasPlace get(UUID id,ResourceLocation dim){return id!=null && ready(dim)?rows.get(id):null;}
    static AtlasPlace.Source source(CityMapPanel.Selection target,ResourceLocation dim){
        if(target==null || target.id()==null)return null;
        AtlasPlace.SourceKind kind=switch(target.kind()){
            case CITY -> AtlasPlace.SourceKind.CITY;
            case PROJECT -> AtlasPlace.SourceKind.PROJECT;
            case LOGISTICS -> "warehouse".equals(LogisticsMapOverlay.markerKind(target.id(),dim))?AtlasPlace.SourceKind.WAREHOUSE:null;
            default -> null;
        };
        return kind==null?null:new AtlasPlace.Source(kind,target.id());
    }
    static boolean canLink(AtlasPlace.Source candidate){return candidate!=null && candidate.equals(link) && ready(dimension);}
    private static void send(PlaceNetwork.Request request) {
        pending=request;deadline=Util.getMillis()+5500;
        PacketDistributor.sendToServer(request);
    }
    static void tick(ResourceLocation dim,CityMapPanel.Selection target){
        var active=CityPublicDirectory.viewId();var scope=CityPublicDirectory.worldId(dim);
        if(active==null || scope==null || !supported()){if(view!=null)clear();return;}
        if(!bound(dim)){clear();dimension=dim;view=active;world=scope;epoch=CityPublicDirectory.accessEpoch();}
        long now=Util.getMillis();var candidate=source(target,dim);
        if(pending!=null){
            if(now<deadline)return;
            lastReply=pending.nonce();status="timeout";pending=null;next=now+1200;
        }
        if(now<next)return;
        send(new PlaceNetwork.Request(UUID.randomUUID(),view,world,dimension,PlaceNetwork.Action.LIST,
                catalogReceipt==null?NONE:catalogReceipt,0,null,candidate));
        next=now+3600;
    }
    static UUID mutate(ResourceLocation dim,PlaceNetwork.Action action,UUID id,long ver,AtlasPlace value){
        if(!bound(dim)||!supported() || pending!=null && pending.action()!=PlaceNetwork.Action.LIST)return null;
        var request=new PlaceNetwork.Request(UUID.randomUUID(),view,world,dim,action,id,ver,value,null);
        send(request);return request.nonce();
    }
    static String receipt(UUID id){return id!=null && id.equals(lastReply)?status:null;}
    public static void receive(PlaceNetwork.Reply reply){
        if(!bound(dimension)||pending==null||!pending.nonce().equals(reply.nonce())||Util.getMillis()>=deadline)return;
        var sent=pending;pending=null;
        if(sent.action()!=PlaceNetwork.Action.LIST){lastReply=reply.nonce();status=reply.status();}
        if("unchanged".equals(reply.status()) && sent.action()==PlaceNetwork.Action.LIST && sent.id().equals(catalogReceipt)){
            renew(sent,reply);
            return;
        }
        if(!Set.of("ok","saved","conflict").contains(reply.status())){
            if("unavailable".equals(reply.status())||"incompatible".equals(reply.status())){validUntil=0;catalogReceipt=null;link=null;}
            next=Util.getMillis()+1500;return;
        }
        var player=Minecraft.getInstance().player;if(player==null){clear();return;}
        var values=new LinkedHashMap<UUID,AtlasPlace>();var markers=new LinkedHashMap<UUID,CityMapPanel.Selection>();
        for(var p:reply.rows()){
            if(!dimension.equals(p.dimension()))continue;
            values.put(p.id(),p);
            var details=new ArrayList<Component>();
            details.add(text("category."+key(p.category())));
            if(!p.description().isBlank())details.add(Component.literal(p.description()));
            details.add(text("entrances",p.entrances().size()));
            if(p.source()!=null)details.add(text("source."+key(p.source().kind())));
            markers.put(p.id(),new CityMapPanel.Selection(CityMapPanel.SelectionKind.PLACE,p.id(),Component.literal(p.name()),p.icon(),
                    p.owner().equals(player.getUUID()),details,Items.COMPASS.getDefaultInstance(),false));
        }
        rows=Map.copyOf(values);indexed=Map.copyOf(markers);selections=List.copyOf(markers.values());
        renew(sent,reply);
    }
    private static void renew(PlaceNetwork.Request sent, PlaceNetwork.Reply reply) {
        catalogReceipt=reply.nonce();link=reply.canLink()?sent.probe():null;
        validUntil=deadline+2000;next=Util.getMillis()+3500;
    }
}
