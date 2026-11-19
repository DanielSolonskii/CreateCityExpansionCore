package com.example.examplemod.client.logistics;

import com.example.examplemod.cargo.center.CenterRequest;
import com.example.examplemod.cargo.center.PublicTradeMap;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.map.CityMapPanel;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Scoped to an open atlas. Reads use the existing Cargo channel and never request stock or rail geometry. */
public final class AtlasTradeDirectory {
    public record Row(UUID id, UUID object, String kind, Component title, String place, ResourceLocation dim,
                      BlockPos pos, List<Component> details, long expires, String itemId) {
        public CityMapPanel.Selection selection() {
            if (pos == null) return null;
            return new CityMapPanel.Selection(CityMapPanel.SelectionKind.LOGISTICS,id,title,pos,false,details,
                    Items.EMERALD.getDefaultInstance(),false);
        }
        CompoundTag marker() {
            var result=new CompoundTag();
            result.putUUID("Id",id); result.putUUID("OpenId",object);
            result.putString("Name",title.getString()); result.putString("Kind",kind);
            var loc=new CompoundTag(); loc.putString("Dimension",dim.toString()); loc.putLong("Pos",pos.asLong());
            result.put("Location",loc);return result;
        }
    }
    /** Only public identifiers cross the management screen. Names, terms and coordinates require a fresh answer. */
    record Reference(UUID marker,UUID object,String kind,ResourceLocation dimension,UUID world) {
        boolean matches(Row row){return row!=null && marker.equals(row.id()) && object.equals(row.object()) && kind.equals(row.kind());}
    }
    private static final Map<UUID,Row> rows=new LinkedHashMap<>();
    private static Reference returning,pendingReference;
    private static Row selectedRow;
    private static long returnDeadline;
    private static ResourceLocation dimension;
    private static UUID world, pending, pendingRestore;
    private static long epoch, sent, next, validUntil, restoreUntil;
    private static int misses;
    private static UUID restoring;
    private static String itemQuery;
    private static ListTag itemIds=new ListTag();
    private static Iterator<net.minecraft.world.item.Item> items;
    private static int itemsChecked;
    private static String query="";
    private AtlasTradeDirectory() {}
    private static Component text(String key,Object...args) {return Component.translatable("gui.createcityexpansioncore.atlas.trade."+key,args);}
    public static void clear() {
        returning=null;returnDeadline=0;
        clearData();
    }
    private static void clearData() {
        selectedRow=null;pendingReference=null;
        rows.clear(); dimension=null; world=null; pending=restoring=pendingRestore=null; query=""; sent=next=validUntil=restoreUntil=0;
        itemQuery=null; items=null; itemIds=new ListTag();misses=0;
    }
    static Reference reference(Row row){return new Reference(row.id(),row.object(),row.kind(),row.dim(),world);}
    static void restore(Reference reference) {
        if(reference==null)return;
        returning=reference;returnDeadline=Util.getMillis()+15000;
    }
    private static boolean bound(ResourceLocation dim) {
        return dim!=null && dim.equals(dimension) && world!=null && world.equals(CityPublicDirectory.worldId(dim))
                && epoch==CityPublicDirectory.accessEpoch();
    }
    public static Row row(UUID id,ResourceLocation dim) {
        long now=Util.getMillis();
        if(!bound(dim) || now>=validUntil)return null;
        var row=selectedRow!=null && selectedRow.id().equals(id)?selectedRow:rows.get(id);
        return row!=null && now<row.expires()?row:null;
    }
    public static List<Row> rows(ResourceLocation dim) {
        long now=Util.getMillis();
        if (!bound(dim) || now>=validUntil) return List.of();
        return rows.values().stream().filter(r->now<r.expires()).toList();
    }
    public static long ageSeconds(){return Math.max(0,Util.getMillis()-(validUntil-15000))/1000;}
    public static boolean loading(ResourceLocation dim){return bound(dim) && validUntil<=Util.getMillis();}
    public static boolean matches(String id,ResourceLocation dim,String needle) {
        Row row=row(UUID.fromString(id),dim);
        return row!=null && (row.itemId().contains(needle) || row.details().stream()
                .anyMatch(line->line.getString().toLowerCase(Locale.ROOT).contains(needle)));
    }
    public static boolean finishedRestore(UUID id) {
        return id!=null && id.equals(restoring) && Util.getMillis()>=restoreUntil;
    }
    public static void tick(ResourceLocation dim, boolean searching, String needle, UUID selected, UUID restore) {
        long now=Util.getMillis();
        UUID wanted=restore!=null?restore:selected;
        var confirmed=CityPublicDirectory.worldId(dim);
        if(returning!=null && (now>=returnDeadline || !returning.dimension().equals(dim) ||
                confirmed!=null && !returning.world().equals(confirmed) || wanted!=null && !returning.marker().equals(wanted)))returning=null;
        if (!searching && restore==null && (wanted==null || returning==null && !rows.containsKey(wanted)
                && (selectedRow==null || !selectedRow.id().equals(wanted)))) {
            if(dimension!=null)clearData();
            return;
        }
        if(confirmed==null || !LogisticsMapOverlay.supportsTrade(dim)) {if(dimension!=null)clearData();return;}
        if(!bound(dim)){clearData();dimension=dim;world=confirmed;epoch=CityPublicDirectory.accessEpoch();}
        if(selectedRow==null || !selectedRow.id().equals(wanted))selectedRow=rows.get(wanted);
        if(!searching && restore==null && selectedRow==null && returning==null){clear();return;}
        Reference target=returning!=null && returning.marker().equals(wanted)?returning:selectedRow!=null?reference(selectedRow):null;
        if(restore!=null && !restore.equals(restoring)){restoring=restore;restoreUntil=now+15000;}
        if(restore==null){restoring=null;restoreUntil=0;}
        // Let the atlas resolve the requested marker against this reply before releasing its rows.
        if(!searching && finishedRestore(restore))return;
        String desired=searching?needle.strip().toLowerCase(Locale.ROOT):query;
        if(desired.length()>64)desired=desired.substring(0,64);
        if(!query.equals(desired)){query=desired;pending=null;misses=0;next=Math.max(now+400,sent+1000);rows.clear();validUntil=0;}
        if(pending!=null && !Objects.equals(target,pendingReference)){pending=null;next=Math.max(now,sent+1000);}
        if(pending!=null){
            if(now-sent<15000)return;
            pending=null;misses=Math.min(3,misses+1);next=now+(1000L<<misses);
        }
        if(now<next)return;
        boolean itemsReady=query.isBlank() || translatedItemsReady();
        if(!itemsReady && returning==null)return;
        var req=new CompoundTag();
        pending=UUID.randomUUID(); pendingRestore=restoring;pendingReference=target; sent=now; next=now+5000;
        req.putUUID("Request",pending);req.putString("Action","VIEW");req.putString("Section","atlas_trade");
        req.putString("Dimension",dim.toString());req.putString("Search",query);
        if(!query.isBlank() && itemsReady)req.put("Items",itemIds);
        if(target!=null){req.putString("SelectedKind",target.kind());req.putUUID("SelectedObject",target.object());}
        PacketDistributor.sendToServer(new CenterRequest(req));
    }
    /** Translate a bounded slice on a client tick so item names can be searched across client/server languages. */
    private static boolean translatedItemsReady() {
        if(!query.equals(itemQuery)){
            itemQuery=query;items=BuiltInRegistries.ITEM.iterator();
            itemIds=new ListTag();itemsChecked=0;
        }
        if(items==null)return true;
        long deadline=System.nanoTime()+1_000_000;
        for(int work=0;work<128 && System.nanoTime()<deadline;work++){
            if(!items.hasNext() || itemsChecked>=8192 || itemIds.size()>=32){items=null;return true;}
            var item=items.next();itemsChecked++;
            if(Component.translatable(item.getDescriptionId()).getString().toLowerCase(Locale.ROOT).contains(query))
                itemIds.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
        }
        return false;
    }
    public static void accept(CompoundTag response) {
        if(!bound(dimension) || !response.hasUUID("Request") || !response.getUUID("Request").equals(pending))return;
        long now=Util.getMillis(); pending=null;
        if(response.getBoolean("Unchanged")){misses=Math.min(3,misses+1);next=now+350+(375L<<misses);return;}
        rows.clear(); selectedRow=null;validUntil=0;
        if(now-sent>=15000 || response.getInt("TradeVersion")!=1 || !"ok".equals(response.getString("Result")))return;
        var list=response.getList("Offers",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(PublicTradeMap.MAX_ROWS,list.size());i++){
            var t=list.getCompound(i);
            if(!t.hasUUID("Id") || !t.hasUUID("OpenId") || !PublicTradeMap.isKind(t.getString("Kind")))continue;
            var r=read(t);if(r!=null)rows.put(r.id(),r);
        }
        if(pendingReference!=null){
            var t=response.getCompound("SelectedTrade");
            if(t.hasUUID("Id") && t.hasUUID("OpenId") && t.contains("Location",Tag.TAG_COMPOUND)){
                var selected=read(t);
                if(pendingReference.matches(selected))selectedRow=selected;
            }
            if(pendingReference.equals(returning))returning=null;
        }
        validUntil=sent+15000; next=Math.max(now+1000,sent+8000);misses=0;
        if(restoring!=null && restoring.equals(pendingRestore))restoreUntil=0;
    }
    private static Row read(CompoundTag t){
        String kind=t.getString("Kind"), place=t.getString("Place");
        BlockPos pos=null;
        if(t.contains("Location")){
            var loc=t.getCompound("Location");
            if(!dimension.toString().equals(loc.getString("Dimension")) || !loc.contains("Pos",Tag.TAG_LONG))return null;
            pos=BlockPos.of(loc.getLong("Pos"));
        }
        var item=t.contains("ItemKey")?Component.translatableWithFallback(t.getString("ItemKey"),t.getString("ItemName")):null;
        Component title=item==null?Component.literal(t.getString("Name")):text(kind,item);
        var details=new ArrayList<Component>();
        if(item!=null)details.add(text("terms",t.getInt("Quantity"),t.getLong("UnitPrice")));
        if(!t.getString("Name").isBlank())details.add(Component.literal(t.getString("Name")));
        if(pos==null)details.add(text("unlocated"));
        else details.add(text(t.getBoolean("CityLandmark")?"city":"place",place));
        long expires=Long.MAX_VALUE;
        if(t.contains("RemainingMs"))expires=sent+Math.clamp(t.getLong("RemainingMs"),0L,15000L);
        return new Row(t.getUUID("Id"),t.getUUID("OpenId"),kind,title,place,dimension,pos,
                List.copyOf(details),expires,t.getString("ItemId"));
    }
}
