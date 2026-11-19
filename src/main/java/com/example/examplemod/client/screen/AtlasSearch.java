package com.example.examplemod.client.screen;

import com.example.examplemod.client.logistics.AtlasTradeDirectory;

import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.ProjectClientState;
import com.example.examplemod.client.journey.JourneyClient;
import com.example.examplemod.client.logistics.LogisticsMapOverlay;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.projects.Project;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.regex.Pattern;

public final class AtlasSearch {
    public enum Type { ALL, CITY, STATION, INDUSTRY, PROJECT, WAYPOINT, LOGISTICS, JOURNEY, COORDINATES, TRADE, PLACE }
    public enum Tab { ALL, FAVORITES, RECENT, VIEWS }
    enum Arrival { READY, WAIT, EXPIRED }
    static Arrival arrival(UUID requestedView,UUID activeView,ResourceLocation requestedDimension,ResourceLocation activeDimension,
                           String key,boolean projectsReady,long now,long deadline) {
        if(!requestedView.equals(activeView)||!requestedDimension.equals(activeDimension))return Arrival.EXPIRED;
        if(key!=null&&key.startsWith("PROJECT/")&&!projectsReady)return now<deadline?Arrival.WAIT:Arrival.EXPIRED;
        return Arrival.READY;
    }
    public record Entry(Type type,String id,String name,String city,ResourceLocation dimension,BlockPos pos,
                        boolean own,boolean warning,CityMapPanel.Selection selection) {
        String key() {return type.name()+"/"+dimension+"/"+id;}
        String context() {
            String label=tr("type."+type.name().toLowerCase(Locale.ROOT)).getString();
            if(pos==null) {
                var row=type==Type.TRADE?AtlasTradeDirectory.row(UUID.fromString(id),dimension):null;
                String terms=row!=null && !row.details().isEmpty()?row.details().getFirst().getString():label;
                return terms+" · "+Component.translatable("gui.createcityexpansioncore.atlas.trade.unlocated").getString();
            }
            return label+" · "+(city.isBlank()?"":city+" · ")+dimension+" · "+pos.getX()+", "+pos.getZ();
        }
    }
    private static final Pattern COORDINATES=Pattern.compile("^\\s*([+-]?\\d{1,8})(?:\\s*[,;]\\s*|\\s+)([+-]?\\d{1,8})\\s*$");
    private AtlasSearch() {}
    static Component tr(String key,Object... args) {
        return Component.translatable("gui.createcityexpansioncore.atlas_search."+key,args);
    }
    public static BlockPos coordinates(String query) {
        var match=COORDINATES.matcher(query);
        if (!match.matches()) return null;
        int x=Integer.parseInt(match.group(1)),z=Integer.parseInt(match.group(2));
        return Math.abs(x)>30_000_000 || Math.abs(z)>30_000_000 ? null : new BlockPos(x,0,z);
    }
    private static CityMapPanel.Selection projectSelection(ProjectClientState.Marker marker) {
        var zone=marker.zone();
        var pos=new BlockPos((int)Math.floorDiv((long)zone.minX()+zone.maxX(),2),0,
                (int)Math.floorDiv((long)zone.minZ()+zone.maxZ(),2));
        return new CityMapPanel.Selection(CityMapPanel.SelectionKind.PROJECT,marker.id(),Component.literal(marker.title()),
                pos,marker.city()!=null&&marker.city().equals(ProjectClientState.city),
                List.of(Component.translatable("gui.createcityexpansioncore.projects.status."+marker.status().name().toLowerCase(Locale.ROOT)),
                        Component.translatable("gui.createcityexpansioncore.projects.scope."+marker.scope().name().toLowerCase(Locale.ROOT))),
                ItemStack.EMPTY, marker.status()==Project.Status.REWORK);
    }
    public static CityMapPanel.Selection projectSelection(UUID id, ResourceLocation dimension) {
        for (var marker:ProjectClientState.mapMarkers())
            if (marker.id().equals(id) && dimension.equals(marker.zone().dimension())) return projectSelection(marker);
        return null;
    }
    private static Entry entry(Type type, CityMapPanel.Selection selection, String city, ResourceLocation dim) {
        String id=selection.id()==null?"pos:"+selection.pos().asLong():selection.id().toString();
        return new Entry(type,id,selection.title().getString(),city,dim,selection.pos(),
                selection.own(),selection.warning(),selection);
    }
    static List<Entry> catalog(CityMapPanel panel,ResourceLocation dim,String localCity) {
        var rows=new ArrayList<Entry>();
        for(var value:panel.search("")) {
            Type type=switch(value.kind()) {case CITY->Type.CITY;case WAYPOINT->Type.WAYPOINT;case INDUSTRY->Type.INDUSTRY;default->null;};
            if(type!=null) rows.add(entry(type,value,type==Type.INDUSTRY?Objects.toString(localCity,""):"",dim));
        }
        for(var marker:ProjectClientState.mapMarkers()) {
            var profile=CityPublicDirectory.profile(marker.city());
            rows.add(entry(Type.PROJECT,projectSelection(marker),profile==null?"":profile.name(),marker.zone().dimension()));
        }
        for(var selection:LogisticsMapOverlay.visibleMapSelections(dim)) {
            String kind=LogisticsMapOverlay.markerKind(selection.id(),dim);
            String owner=selection.details().isEmpty()?"":selection.details().getFirst().getString();
            rows.add(entry(kind.equals("station")?Type.STATION:Type.LOGISTICS,selection,owner,dim));
        }
        for(var row:AtlasTradeDirectory.rows(dim)) {
            rows.add(new Entry(Type.TRADE,row.id().toString(),row.title().getString(),row.place(),dim,row.pos(),false,false,row.selection()));
        }
        for(var place:AtlasPlaces.selections(dim))rows.add(entry(Type.PLACE,place,place.details().getFirst().getString(),dim));
        for(var marker:JourneyClient.markers.values()) {
            if(!marker.onMap||!marker.enabled) continue;
            var point=marker.point;
            rows.add(new Entry(Type.JOURNEY,marker.id,marker.title,"",ResourceLocation.parse(point.dimension()),
                    new BlockPos(point.x(),point.y(),point.z()),false,false,null));
        }
        return rows;
    }
    static List<Entry> find(List<Entry> catalog, String query, Type type, int owner, boolean problems,
                            Tab tab, AtlasWorkspace library,ResourceLocation dimension) {
        var matches=new ArrayList<Entry>();
        String needle=query.strip().toLowerCase(Locale.ROOT);
        var coords=coordinates(needle);
        if(coords!=null && (type==Type.ALL || type==Type.COORDINATES) && tab==Tab.ALL && !problems && owner==0)
            matches.add(coordinateEntry(coords,dimension));
        if ((tab==Tab.FAVORITES || tab==Tab.RECENT) && library!=null && (type==Type.ALL||type==Type.COORDINATES)&&!problems&&owner==0) {
            String prefix=Type.COORDINATES.name()+"/"+dimension+"/";
            for(String key:library.resultKeys(tab==Tab.FAVORITES)) if(key.startsWith(prefix)) {
                var pos=coordinates(key.substring(prefix.length()));
                if(pos!=null && (needle.isEmpty() || key.endsWith(needle)||pos.equals(coords)))matches.add(coordinateEntry(pos,dimension));
            }
        }
        for(var item:catalog) {
            if(type!=Type.ALL&&item.type!=type || problems&&!item.warning || owner!=0&&item.own!=(owner==1)) continue;
            if(tab==Tab.FAVORITES && (library==null||!library.favorite(item.key()))
                    ||tab==Tab.RECENT && (library==null||library.recency(item.key())<0)) continue;
            if(!needle.isEmpty()&&!item.name.toLowerCase(Locale.ROOT).contains(needle)
                    &&!item.city.toLowerCase(Locale.ROOT).contains(needle)&&!item.dimension.toString().contains(needle)
                    && !(item.type==Type.PLACE && ("place:"+item.id).equals(needle))
                    && !(item.type==Type.TRADE && AtlasTradeDirectory.matches(item.id,dimension,needle))) continue;
            matches.add(item);
        }
        Comparator<Entry> order=Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::key);
        if(tab==Tab.RECENT&&library!=null) order=Comparator.comparingInt(e->library.recency(e.key()));
        matches.sort(order);
        return List.copyOf(matches);
    }
    static Entry coordinateEntry(BlockPos pos, ResourceLocation dimension) {
        String label=pos.getX()+", "+pos.getZ();
        return new Entry(Type.COORDINATES,pos.getX()+","+pos.getZ(),label,"",dimension,pos,false,false,null);
    }
}
