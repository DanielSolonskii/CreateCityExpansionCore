package com.example.examplemod.client.freight;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.Screen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.ui.PonderUI;

/** Also available through Create's hold-to-Ponder item tooltips. */
public final class FreightPonderPlugin implements PonderPlugin {
    public static final ResourceLocation PYLON=ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","city_pylon");
    @Override public String getModId(){return "createcityexpansioncore";}
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper){
        for(ResourceLocation item:new ResourceLocation[]{PYLON,ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","contract_intake"),ResourceLocation.fromNamespaceAndPath("create","packager")}){
            helper.addStoryBoard(item,"freight_terminal",FreightPonderScenes::terminal);
            helper.addStoryBoard(item,"freight_terminal",FreightPonderScenes::warehouse);
            helper.addStoryBoard(item,"freight_terminal",FreightPonderScenes::delivery);
            helper.addStoryBoard(item,"freight_terminal",FreightPonderScenes::hub);
        }
    }
    @Override public void registerSharedText(SharedTextRegistrationHelper helper){
        helper.registerSharedText("fl_restocker","For automatic replenishment, attach a Factory Gauge to a SEPARATE receiving Packager and set the item, stock level and address. It requests shortages from the Stock Link network; incoming parcels unpack into the dispatch buffer.");
        helper.registerSharedText("fl_warehouse","Production continuously fills storage. CCEC automatically requests goods from its Packager for an active delivery.");
        helper.registerSharedText("fl_facing","Storage is BEHIND the Packager. The guide’s gold arrow points outwards from storage.");
        helper.registerSharedText("fl_physical","Packages travel on a real belt. Connect it through funnels to the Postbox: nearby blocks do not connect automatically.");
        helper.registerSharedText("fl_station","Link the Postbox to a station using Create. A stopped train needs attached cargo inventory.");
        helper.registerSharedText("fl_setup","Pylon → Logistics → Terminals → New terminal. Select Packager and Postbox in the world; save after the checks pass.");
        helper.registerSharedText("fl_network","Stock Links and a Stock Ticker can collect resources from a large Create network.");
        helper.registerSharedText("fl_replenish","The receiving Packager replenishes the shared buffer. A separate CCEC Packager sends goods only to the active delivery address.");
        helper.registerSharedText("fl_stock_scope","CCEC draws from the Packager’s local storage. Replenish that buffer using normal Create machinery; CCEC does not issue Stock Ticker requests.");
        helper.registerSharedText("fl_quote","The buyer chooses their free train or a published seller train, compares routes and sets the overall road CR allowance.");
        helper.registerSharedText("fl_load","Empty pickup runs automatically. After a batch loads, the train departs for the receiver; the whole order need not fit in one trip.");
        helper.registerSharedText("fl_receive","The receiver is a DIFFERENT warehouse. Here CCEC checks the seal and address, accepts goods and updates the delivered quantity.");
        helper.registerSharedText("fl_pay","Goods and roads are separate. Road escrow settles for actual travel and refunds the remainder. Another batch may follow.");
        helper.registerSharedText("fl_hub_store","At a centre, parcels remain SEALED in warehouse storage. This neither accepts the goods nor pays the seller.");
        helper.registerSharedText("fl_hub_address","The centre preserves the parcel seal and changes only its next-stop address. Multiple orders can share one train.");
        helper.registerSharedText("fl_hub_schedule","Configure a real train on each leg with a cyclic Create schedule: adjacent stations, stops and time for parcel exchange.");
        helper.registerSharedText("fl_hub_final","Only the final terminal counts goods. Shared trip road costs split by cargo quantity within each order’s allowance.");

    }
    public static void open(Screen previous){ScreenOpener.open(previous,PonderUI.of(PYLON));}
}
