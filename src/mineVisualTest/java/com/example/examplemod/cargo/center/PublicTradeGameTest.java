package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.CargoTrainRegistry.AccessMode;
import com.example.examplemod.economy.EconomyManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class PublicTradeGameTest {
    private static CompoundTag namedOffer(CompoundTag response,UUID id) {
        for(var tag:response.getList("Offers",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;
            if(row.getString("Kind").equals("offer") && row.getUUID("OpenId").equals(id))return row;
        }
        return new CompoundTag();
    }
    @GameTest(template="auger_empty",timeoutTicks=300)
    public static void publishedTermsAndPrivatePlaces(GameTestHelper h){
        var level=h.getLevel(); var server=level.getServer();
        var viewer=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"TradeGuest"),ClientInformation.createDefault());
        var seller=OwnerRef.player(UUID.randomUUID());
        var warehouse=UUID.randomUUID();
        var point=UUID.randomUUID();
        var existing=CargoWarehouseRegistry.get(server).save(new CompoundTag(),server.registryAccess());
        var entry=new CompoundTag();
        entry.putUUID("Id",warehouse);entry.putString("Name","Private staging dock");entry.putUUID("Creator",seller.id());
        entry.putUUID("Point",point);entry.putUUID("Instance",UUID.randomUUID());entry.putUUID("Network",UUID.randomUUID());
        var source=new CargoInfrastructure.Location(level.dimension().location(),new BlockPos(89999,85,-89999));
        entry.put("Ticker",source.save());
        existing.getList("Entries",Tag.TAG_COMPOUND).add(entry);
        // Load the same metadata format as a pre-existing save. No loaded block is needed for a published offer.
        server.overworld().getDataStorage().set("ccec_cargo_warehouses",CargoWarehouseRegistry.load(existing,server.registryAccess()));
        var book=EconomyManager.get(server.overworld()).cargo(); var id=UUID.randomUUID();
        var stack=Items.IRON_INGOT.getDefaultInstance();
        book.publish(id,seller.id(),seller,warehouse,(CompoundTag)stack.save(server.registryAccess()),"Iron offer",15,3);
        var req=new CompoundTag();req.putString("Dimension",level.dimension().location().toString());req.putString("Search","minecraft:iron_ingot");
        var result=namedOffer(PublicTradeMap.snapshot(viewer,req),id);
        h.assertTrue(result.getInt("Quantity")==15 && result.getLong("UnitPrice")==3 && !result.contains("Location") && !result.contains("Place")
                && !result.contains("Stock") && !result.contains("Orders"),"published terms do not expose private warehouse");
        var owners=CargoOwners.get(server);
        var publicAccess=new CargoOwners.Access(seller,AccessMode.PUBLIC,Set.of(),Set.of());
        var privateAccess=new CargoOwners.Access(seller,AccessMode.PRIVATE,Set.of(),Set.of());
        h.assertTrue(owners.change(server,seller.id(),"warehouse/"+warehouse,seller.id(),AccessMode.PRIVATE,publicAccess),"publish warehouse access");
        var located=PublicTradeMap.lookup(viewer,"offer",id);
        h.assertTrue(located!=null && source.save().equals(located.getCompound("Location")),"permitted warehouse opens from current lookup");
        h.assertTrue(owners.change(server,seller.id(),"offer/"+id,seller.id(),AccessMode.PUBLIC,privateAccess),"restrict offer");
        h.assertTrue(PublicTradeMap.lookup(viewer,"offer",id)==null && namedOffer(PublicTradeMap.snapshot(viewer,req),id).isEmpty(),
                "old marker and new search both lose the offer");
        h.assertTrue(owners.change(server,seller.id(),"offer/"+id,seller.id(),AccessMode.PUBLIC,publicAccess),"re-publish offer");
        h.assertTrue(book.close(id) && PublicTradeMap.lookup(viewer,"offer",id)==null,"closed public offer is unavailable");
        var iron = (CompoundTag)stack.save(server.registryAccess());
        var published=new ArrayList<UUID>();
        for(int i=0;i<=PublicTradeMap.PER_KIND;i++){
            UUID listing=UUID.randomUUID();published.add(listing);
            book.publish(listing,seller.id(),seller,warehouse,iron,"Iron "+i,1,3);
        }
        var page=PublicTradeMap.snapshot(viewer,req);
        h.assertTrue(page.getList("Offers",Tag.TAG_COMPOUND).size()==PublicTradeMap.PER_KIND,"public offer page remains capped");
        UUID offPage=published.stream().filter(listing->namedOffer(page,listing).isEmpty()).findFirst().orElseThrow();
        var restore=req.copy();restore.putString("SelectedKind","offer");restore.putUUID("SelectedObject",offPage);
        var exact=PublicTradeMap.snapshot(viewer,restore);
        h.assertTrue(exact.getList("Offers",Tag.TAG_COMPOUND).size()==PublicTradeMap.PER_KIND &&
                exact.getCompound("SelectedTrade").getUUID("OpenId").equals(offPage),"selected off-page public card is independently confirmed");
        restore.putString("Search","no matching terms");
        exact=PublicTradeMap.snapshot(viewer,restore);
        h.assertTrue(exact.getList("Offers",Tag.TAG_COMPOUND).isEmpty() && exact.getCompound("SelectedTrade").getUUID("OpenId").equals(offPage),
                "restoring a card leaves the current search unchanged");
        h.assertTrue(owners.change(server,seller.id(),"offer/"+offPage,seller.id(),AccessMode.PUBLIC,privateAccess),"revoke selected offer");
        h.assertTrue(!PublicTradeMap.snapshot(viewer,restore).contains("SelectedTrade"),"retained identifiers cannot recover a withdrawn card");
        published.forEach(book::close);
        for (int i = 0; i < 512; i++) {
            UUID hidden = UUID.randomUUID();
            book.publish(hidden,seller.id(),seller,warehouse,iron,"Restricted",1,3);
            h.assertTrue(owners.change(server,seller.id(),"offer/"+hidden,seller.id(),AccessMode.PUBLIC,privateAccess),"restrict listing");
        }
        var latest = UUID.randomUUID();
        book.publish(latest,seller.id(),seller,warehouse,iron,"Published",1,3);
        var firstPage = PublicTradeMap.snapshot(viewer,req).getList("Offers",Tag.TAG_COMPOUND);
        h.assertTrue(firstPage.stream().noneMatch(tag -> ((CompoundTag)tag).getString("Kind").equals("offer")),
                "the first bounded scan exposes no private listings");
        h.assertTrue(!namedOffer(PublicTradeMap.snapshot(viewer,req),latest).isEmpty(),
                "the next bounded scan reaches a public offer behind 512 private listings");
        h.succeed();
    }
}
