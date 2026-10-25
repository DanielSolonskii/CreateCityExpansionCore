package com.example.examplemod.client.screen;

import com.example.examplemod.economy.EconomyTypes;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.EconomyStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** P2P trading board, also usable inside the City Pylon workspace. */
public final class MarketScreen extends CityPylonPageScreen {
    private static final int BG=0xFF141A1D,PANEL=0xFF1D2529,PANEL_ALT=0xFF222C30,FRAME=CityPylonTheme.LINE;
    private static final int ACCENT=0xFF996215,TEXT=CityPylonTheme.TEXT,MUTED=CityPylonTheme.MUTED,GOOD=0xFF77B992,WARN=0xFFE2BD75,BAD=0xFFD07872;
    private static final int ROW_H=42,TAB_H=28;
    private final Screen parent;
    private final com.example.examplemod.client.WorldMapDataSource menu;
    private Tab tab=Tab.PLAYER;
    private int listingFilter;
    private static final Tab[] MAIN_TABS={Tab.PLAYER,Tab.CONTRACTS,Tab.MINE};
    private EconomyTypes.MarketDirection direction=EconomyTypes.MarketDirection.SELL_TO_MASTER;
    private String category="all";
    private EditBox search,quantity;
    private UUID selected;
    private int scroll,refreshTicks,detailScroll,detailMaximum;
    private Pending pending;
    private Component tooltip;

    public MarketScreen(CityPylonScreen parent){
        this(parent,parent.getMenu());
    }
    public MarketScreen(Screen parent,com.example.examplemod.client.WorldMapDataSource source){
        super(Component.translatable("gui.createcityexpansioncore.market.title"));
        this.parent=parent;this.menu=source;
    }

    public MarketScreen focusContract(UUID id){tab=Tab.CONTRACTS;selected=id;return this;}
    @Override protected void init(){
        clearWidgets();
        search=new EditBox(font,0,0,100,18,Component.translatable("gui.createcityexpansioncore.market.search"));
        search.setBordered(false);search.setMaxLength(64);search.setHint(Component.translatable("gui.createcityexpansioncore.market.search"));
        search.setResponder(value->{scroll=0;selected=null;});addRenderableWidget(search);
        quantity=new EditBox(font,0,0,80,18,Component.translatable("gui.createcityexpansioncore.market.quantity"));
        quantity.setBordered(false);quantity.setMaxLength(10);quantity.setFilter(v->v.isBlank()||v.chars().allMatch(Character::isDigit));quantity.setValue("1");addRenderableWidget(quantity);
        layoutFields();request();
    }

    @Override public void resize(net.minecraft.client.Minecraft minecraft,int width,int height){
        String q=search==null?"":search.getValue(),a=quantity==null?"1":quantity.getValue();
        UUID savedSelection=selected;int savedScroll=scroll;
        super.resize(minecraft,width,height);search.setValue(q);quantity.setValue(a);
        selected=savedSelection;scroll=savedScroll;
    }

    @Override public void onClose(){if(pending!=null){pending=null;return;}if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public void tick(){if(++refreshTicks>=40){refreshTicks=0;request();}layoutFields();}
    private void request(){PacketDistributor.sendToServer(EconomyCommandPayload.simple(menu.getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));}

    private void layoutFields(){
        if(search==null||quantity==null)return;Layout l=layout();
        search.setX(l.leftX+9);search.setY(l.y+32);search.setWidth(Math.max(64,l.leftW-18));
        search.setVisible(tab==Tab.EXTERNAL||tab==Tab.PLAYER);
        if(tab==Tab.PLAYER||tab==Tab.MINE){search.setX(16);search.setY(97);search.setWidth(146);}
        quantity.setX(l.detailX+31);quantity.setY(height-78);quantity.setWidth(Math.max(38,l.detailW-62));
        quantity.setVisible(tab==Tab.EXTERNAL&&selectedOffer()!=null&&pending==null);
    }

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        tooltip=null;g.fill(0,0,width,height,BG);EconomyStatePayload s=menu.getEconomyState();
        header(g,s,mx,my);
        if(pending!=null){confirmation(g,mx,my);return;}
        tabs(g,mx,my);
        if(s==null)g.drawCenteredString(font,Component.translatable("gui.createcityexpansioncore.economy.loading"),width/2,height/2,MUTED);
        else switch(tab){case OVERVIEW->overview(g,s);case EXTERNAL->external(g,s,mx,my);case PLAYER->player(g,s,mx,my);case MINE->activity(g,s,mx,my);case CONTRACTS->contracts(g,s,mx,my);case ACTIVITY->activity(g,s,mx,my);}
        super.render(g,mx,my,pt);
        if(pending!=null)confirmation(g,mx,my);
        else if(tooltip!=null)g.renderTooltip(font,font.split(tooltip,Math.min(240,width-24)),net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,mx,my);
    }

    private void header(GuiGraphics g,EconomyStatePayload s,int mx,int my){
        plate(g,8,7,width-16,44);
        g.drawString(font,title,16,14,TEXT,false);
        g.drawString(font,ellipsize(tr("board.subtitle"),width-270),16,34,MUTED,false);
        button(g,width-176,12,104,20,tr("board.external"),true,mx,my,false);
        button(g,width-68,12,52,20,tr("board.history"),true,mx,my,false);
        if(s!=null)g.drawString(font,ellipsize(tr("board.balances",s.wallet(),s.treasury()),224),width-240,37,MUTED,false);
    }

    private void tabs(GuiGraphics g,int mx,int my){
        int tw=(width-168)/MAIN_TABS.length,x=8;
        for(Tab value:MAIN_TABS){choice(g,x,58,tw-4,Component.translatable(value.key),value==tab,mx,my);x+=tw;}
    }

    private void overview(GuiGraphics g,EconomyStatePayload s){
        int y=92,gap=8,cw=Math.max(90,(width-40)/3);
        overviewCard(g,12,y,cw,item("minecraft:emerald"),tr("overview.opportunities",s.offers().stream().filter(o->o.status()==EconomyTypes.MarketOfferStatus.OPEN).count()),tr("overview.opportunities_hint"));
        overviewCard(g,12+cw+gap,y,cw,item("minecraft:writable_book"),tr("overview.contracts",s.contracts().stream().filter(c->!c.state().terminal()).count()),tr("overview.contracts_hint"));
        overviewCard(g,12+(cw+gap)*2,y,width-24-(cw+gap)*2,item("minecraft:clock"),tr("overview.active",s.activeObligations()),tr("overview.active_hint"));
        int lower=y+64,half=(width-32)/2;plate(g,12,lower,half,height-lower-10);plate(g,20+half,lower,width-half-32,height-lower-10);
        title(g,tr("overview.recent"),22,lower+12);title(g,tr("overview.infrastructure"),30+half,lower+12);
        List<Component> recent=new ArrayList<>();s.offers().stream().filter(EconomyStatePayload.OfferView::mine).limit(4).forEach(o->recent.add(tr("overview.offer_activity",icon(o.itemId()).getHoverName(),status(o.status()))));
        s.contracts().stream().filter(c->c.mine()||c.issuerMine()).limit(Math.max(0,5-recent.size())).forEach(c->recent.add(tr("overview.contract_activity",contractTitle(c),status(c.state()))));
        if(recent.isEmpty())empty(g,22,lower+38,tr("empty.activity"),half-22);else for(int i=0;i<recent.size();i++)g.drawString(font,ellipsize(recent.get(i),half-22),22,lower+38+i*18,i==0?TEXT:MUTED,false);
        long ready=s.infrastructure().stream().filter(EconomyStatePayload.InfrastructureView::available).count();
        g.drawString(font,tr("overview.infrastructure_ready",ready,s.infrastructure().size()),30+half,lower+38,TEXT,false);
        g.drawString(font,tr("overview.warehouse",s.warehouse().stream().mapToLong(EconomyStatePayload.StockView::amount).sum()),30+half,lower+56,MUTED,false);
    }

    private void external(GuiGraphics g,EconomyStatePayload s,int mx,int my){
        Layout l=layout();threePanels(g,l);title(g,tr("filters"),l.leftX+9,l.y+10);fieldBack(g,search);
        int cy=l.y+58;for(String value:categories(s)){choice(g,l.leftX+8,cy,l.leftW-16,categoryLabel(value),value.equals(category),mx,my);cy+=22;}
        int ty=l.y+l.h-50;choice(g,l.leftX+8,ty,l.leftW-16,tr("sell"),direction==EconomyTypes.MarketDirection.SELL_TO_MASTER,mx,my);choice(g,l.leftX+8,ty+23,l.leftW-16,tr("buy"),direction==EconomyTypes.MarketDirection.BUY_FROM_MASTER,mx,my);
        List<EconomyStatePayload.OfferView> values=visibleOffers(s);title(g,tr("external.count",values.size()),l.listX+9,l.y+10);offerRows(g,values,l,mx,my);offerDetail(g,selectedOffer(values),s,l,mx,my);
    }

    private void offerRows(GuiGraphics g,List<EconomyStatePayload.OfferView> values,Layout l,int mx,int my){
        int top=l.y+27,visible=Math.max(1,(l.h-32)/ROW_H),start=Math.min(scroll,Math.max(0,values.size()-visible));
        if(values.isEmpty()){empty(g,l.listX+12,top+16,tr("empty.external"),l.listW-24);return;}
        for(int i=start;i<Math.min(values.size(),start+visible);i++){var o=values.get(i);int y=top+(i-start)*ROW_H;row(g,l,y,o.id().equals(selected),mx,my);ItemStack stack=icon(o.itemId());g.renderItem(stack,l.listX+11,y+5);g.drawString(font,ellipsize(stack.getHoverName(),l.listW-126),l.listX+33,y+5,TEXT,false);g.drawString(font,ellipsize(tr("offer_meta",categoryLabel(o.category()),o.unitPrice(),o.remaining(),o.marketTarget()),l.listW-45),l.listX+33,y+18,MUTED,false);g.drawString(font,status(o.status()),l.listX+l.listW-82,y+5,statusColor(o.status()),false);progress(g,l.listX+33,y+31,l.listW-45,o.marketAvailable(),o.marketTarget());}
    }

    private void offerDetail(GuiGraphics g,EconomyStatePayload.OfferView o,EconomyStatePayload s,Layout l,int mx,int my){
        title(g,tr("details"),l.detailX+10,l.y+10);if(o==null){empty(g,l.detailX+10,l.y+42,tr("empty.selection"),l.detailW-20);return;}
        ItemStack stack=icon(o.itemId());g.renderItem(stack,l.detailX+10,l.y+31);g.drawString(font,ellipsize(stack.getHoverName(),l.detailW-42),l.detailX+32,l.y+35,TEXT,false);
        int y=l.y+60;detail(g,l,y,tr("category",categoryLabel(o.category())));detail(g,l,y+=16,tr("unit_price",o.unitPrice()));detail(g,l,y+=16,tr("reference_value",o.referenceValue()));detail(g,l,y+=16,tr("market_volume",o.marketAvailable(),o.marketTarget()));detail(g,l,y+=16,tr("condition",status(o.status())));detail(g,l,y+=16,tr("expiration",remainingTime(o.expiresAt())));detail(g,l,y+=16,tr("fulfillment",tr(o.direction()==EconomyTypes.MarketDirection.SELL_TO_MASTER?"fulfillment.intake":"fulfillment.gateway")));
        long max=maxAcceptable(o),amount=selectedQuantity(max);int qy=height-103,qw=Math.max(22,(l.detailW-29)/4);long[] quick={1,64,256,max};String[] labels={"1","64","256","MAX"};
        for(int i=0;i<4;i++)button(g,l.detailX+7+i*(qw+3),qy,qw,18,Component.literal(labels[i]),max>0,mx,my,false);
        button(g,l.detailX+8,height-79,18,18,Component.literal("−"),max>0,mx,my,false);fieldBack(g,quantity);button(g,l.detailX+l.detailW-26,height-79,18,18,Component.literal("+"),max>0,mx,my,false);long total=mul(amount,o.unitPrice()),bond=mul(total,o.bondBasisPoints())/10_000L;g.drawString(font,tr("preview",total,bond),l.detailX+10,height-55,MUTED,false);
        button(g,l.detailX+8,height-36,l.detailW-16,22,tr(o.direction()==EconomyTypes.MarketDirection.SELL_TO_MASTER?"action.sell":"action.buy"),o.status()==EconomyTypes.MarketOfferStatus.OPEN&&amount>0,mx,my,true);
    }

    private void player(GuiGraphics g,EconomyStatePayload s,int mx,int my){
        Layout l=boardLayout();twoPanels(g,l);fieldBack(g,search);
        button(g,width-146,58,138,20,tr("create_listing"),s.unlocked(),mx,my,true);
        for(int i=0;i<4;i++)choice(g,180+i*((width-188)/4),95,(width-188)/4-4,tr("board.filter."+i),listingFilter==i,mx,my);
        List<EconomyStatePayload.ListingView> values=visibleListings(s);
        title(g,tr(tab==Tab.MINE?"board.mine_count":"player.active",values.size()),l.listX+9,l.y+10);
        int visible=Math.max(1,(l.h-32)/ROW_H),start=Math.min(scroll,Math.max(0,values.size()-visible));
        if(values.isEmpty())empty(g,l.listX+12,l.y+45,tr(tab==Tab.MINE?"board.empty_mine":"empty.player"),l.listW-24);
        for(int i=start;i<Math.min(values.size(),start+visible);i++){
            var v=values.get(i);int y=l.y+27+(i-start)*ROW_H;row(g,l,y,v.id().equals(selected),mx,my);
            g.renderItem(icon(v.itemId()),l.listX+10,y+5);
            g.drawString(font,ellipsize(tr("item_amount",v.amount(),icon(v.itemId()).getHoverName()),l.listW-112),l.listX+32,y+5,TEXT,false);
            g.drawString(font,ellipsize(tr("board.listing_actor",v.creatorName(),status(v.state())),l.listW-42),l.listX+32,y+20,MUTED,false);
            g.drawString(font,ellipsize(v.isBarter()?tr("board.barter"):tr("board.lot",v.price()),80),l.listX+l.listW-85,y+5,WARN,false);
        }
        listingDetail(g,selectedListing(values),l,mx,my);
    }

    private void listingDetail(GuiGraphics g,EconomyStatePayload.ListingView v,Layout l,int mx,int my){
        title(g,tr("details"),l.detailX+10,l.y+10);
        if(v==null){empty(g,l.detailX+10,l.y+42,tr("empty.selection"),l.detailW-20);return;}
        int y=l.y+33;
        detail(g,l,y,tr("listing.actor",v.creatorName()));
        detail(g,l,y+=12,tr("listing.type",v.isBarter()?tr("board.barter"):listingType(v.type())));
        boolean creator=minecraft.player!=null&&minecraft.player.getUUID().equals(v.creator());
        Component offer=side(v.offerItemId(),v.offerAmount()),request=side(v.requestItemId(),v.requestAmount());
        detail(g,l,y+=12,tr("board.you_give",creator?offer:request));
        detail(g,l,y+=12,tr("board.you_receive",creator?request:offer));
        detail(g,l,y+=12,tr("listing.status",status(v.state())));
        if(!v.isBarter()){
            long fee=fee(v.price());
            detail(g,l,y+=14,tr("board.lot",v.price()));
            detail(g,l,y+=12,tr("board.fee",fee));
            detail(g,l,y+=12,tr("board.buyer_total",total(v.price(),fee)));
        }
        if(!v.meeting().isBlank())detail(g,l,y+=14,tr("listing.meeting",v.meeting()));
        Component manual=tr(v.isBarter()?"board.manual_barter":"board.manual");
        g.drawString(font,Component.literal("i"),l.detailX+l.detailW-16,l.y+10,MUTED,false);
        if(in(mx,my,l.detailX+l.detailW-24,l.y+5,20,20))tooltip=manual;
        if(height>=400&&y+48<height-70)g.drawWordWrap(font,manual,l.detailX+10,y+18,l.detailW-20,MUTED);
        ActionSpec a=listingAction(v);
        if(a!=null)button(g,l.detailX+8,height-36,l.detailW-16,22,a.label,true,mx,my,a.danger);
        if(isParticipant(v)&&v.state()==EconomyTypes.ListingState.RESERVED)
            button(g,l.detailX+8,height-63,l.detailW-16,20,Component.translatable("gui.createcityexpansioncore.economy.cancel"),true,mx,my,false);
        if(isParticipant(v)&&v.state()==EconomyTypes.ListingState.HANDED_OVER)
            button(g,l.detailX+8,height-63,l.detailW-16,20,tr("board.dispute"),true,mx,my,false);
    }

    private void contracts(GuiGraphics g,EconomyStatePayload s,int mx,int my){
        Layout l=wideLayout();twoPanels(g,l);List<EconomyStatePayload.ContractView> values=sortedContracts(s);title(g,tr("contracts.count",values.size()),l.listX+9,l.y+10);int top=l.y+27,visible=Math.max(1,(l.h-32)/ROW_H),start=Math.min(scroll,Math.max(0,values.size()-visible));
        if(values.isEmpty())empty(g,l.listX+12,top+16,tr("empty.contracts"),l.listW-24);
        for(int i=start;i<Math.min(values.size(),start+visible);i++){var c=values.get(i);int y=top+(i-start)*ROW_H;row(g,l,y,c.id().equals(selected),mx,my);g.renderItem(icon(c.itemId()),l.listX+11,y+5);g.drawString(font,ellipsize(contractTitle(c),l.listW-150),l.listX+33,y+5,TEXT,false);g.drawString(font,tr("contract.line",c.delivered(),c.amount(),c.reward()),l.listX+33,y+18,MUTED,false);g.drawString(font,status(c.state()),l.listX+l.listW-88,y+5,statusColor(c.state()),false);progress(g,l.listX+33,y+31,l.listW-45,c.delivered(),c.amount());}
        contractDetail(g,selectedContract(values),s,l,mx,my);
        button(g,l.listX+l.listW-190,l.y+3,90,20,FreightUi.tr("create_buy"),s.logistics().getBoolean("CanCreateOrder"),mx,my,false);
        button(g,l.listX+l.listW-95,l.y+3,90,20,FreightUi.tr("city_sales"),true,mx,my,false);
    }

    private void contractDetail(GuiGraphics g,EconomyStatePayload.ContractView c,EconomyStatePayload s,Layout l,int mx,int my){
        title(g,tr("details"),l.detailX+10,l.y+10);if(c==null){empty(g,l.detailX+10,l.y+42,tr("empty.selection"),l.detailW-20);return;}
        g.renderItem(icon(c.itemId()),l.detailX+10,l.y+31);g.drawString(font,ellipsize(contractTitle(c),l.detailW-42),l.detailX+32,l.y+35,TEXT,false);
        var facts=s.logistics().getCompound("ContractFacts").getCompound(c.id().toString());
        int start=l.y+58,bottom=height-91,y=start-detailScroll;
        CityPylonWorkspace.clip(g,l.detailX+6,start,l.detailX+l.detailW-6,Math.max(start+1,bottom));
        var lines=new ArrayList<Component>();
        lines.add(tr("contract.requester",c.issuerName()));
        lines.add(Component.translatable("gui.createcityexpansioncore.trade.by",facts.getString("Author")));
        lines.add(tr("contract.cargo",icon(c.itemId()).getHoverName(),c.amount()));
        lines.add(tr("contract.progress",c.delivered(),c.amount()));
        lines.add(tr("contract.reward",c.reward()));
        lines.add(Component.translatable("gui.createcityexpansioncore.trade.reserved",facts.getLong("Reserved")));
        lines.add(Component.translatable("gui.createcityexpansioncore.trade.payment_rule"));
        if(facts.getLong("ClosurePaid")>0)lines.add(Component.translatable("gui.createcityexpansioncore.trade.closed_paid",facts.getLong("ClosurePaid")));
        lines.add(tr("contract.deadline",remainingTime(c.deadlineAt())));
        lines.add(tr("contract.location",destination(s,c.destinationId())));
        if(!c.description().isBlank())lines.add(Component.literal(c.description()));
        if(facts.hasUUID("Project"))lines.add(Component.translatable("gui.createcityexpansioncore.trade.linked_project"));
        for(var line:lines){for(var part:font.split(line,l.detailW-24)){g.drawString(font,part,l.detailX+10,y,MUTED,false);y+=12;}y+=7;}
        detailMaximum=Math.max(0,y+detailScroll-bottom);detailScroll=Math.min(detailScroll,detailMaximum);g.disableScissor();
        button(g,l.detailX+8,height-64,l.detailW-16,20,Component.translatable("gui.createcityexpansioncore.trade.on_map"),FreightUi.point(s,c.destinationId())!=null,mx,my,false);
        ActionSpec a=contractAction(c);if(a!=null)button(g,l.detailX+8,height-36,l.detailW-16,22,a.label,true,mx,my,a.danger);
    }

    private void activity(GuiGraphics g,EconomyStatePayload s,int mx,int my){
        Layout l=wideLayout();twoPanels(g,l);List<Activity> values=activities(s);title(g,tr("activity.count",values.size()),l.listX+9,l.y+10);int top=l.y+27,visible=Math.max(1,(l.h-32)/ROW_H),start=Math.min(scroll,Math.max(0,values.size()-visible));
        if(values.isEmpty())empty(g,l.listX+12,top+16,tr("empty.activity"),l.listW-24);
        for(int i=start;i<Math.min(values.size(),start+visible);i++){Activity a=values.get(i);int y=top+(i-start)*ROW_H;row(g,l,y,a.id.equals(selected),mx,my);g.renderItem(icon(a.item),l.listX+11,y+5);g.drawString(font,ellipsize(a.title,l.listW-128),l.listX+33,y+5,TEXT,false);g.drawString(font,a.total>0?tr("activity.progress",a.current,a.total):a.kind,l.listX+33,y+18,MUTED,false);g.drawString(font,a.state,l.listX+l.listW-88,y+5,a.color,false);if(a.total>0)progress(g,l.listX+33,y+31,l.listW-45,a.current,a.total);}
        title(g,tr("details"),l.detailX+10,l.y+10);Activity a=values.stream().filter(v->v.id.equals(selected)).findFirst().orElse(null);if(a==null)empty(g,l.detailX+10,l.y+42,tr("empty.selection"),l.detailW-20);else{g.renderItem(icon(a.item),l.detailX+10,l.y+31);g.drawString(font,ellipsize(a.title,l.detailW-42),l.detailX+32,l.y+35,TEXT,false);detail(g,l,l.y+63,a.kind);detail(g,l,l.y+81,tr("activity.state",a.state));if(a.total>0)detail(g,l,l.y+99,tr("activity.progress",a.current,a.total));if(a.deadline>0)detail(g,l,l.y+117,tr("contract.deadline",remainingTime(a.deadline)));if(a.bond>0)detail(g,l,l.y+135,tr("bond",a.bond));button(g,l.detailX+8,height-36,l.detailW-16,22,Component.translatable("gui.createcityexpansioncore.trade.open_order"),s.contracts().stream().anyMatch(c->c.id().equals(selected))||s.offers().stream().anyMatch(o->o.id().equals(selected))||s.listings().stream().anyMatch(v->v.id().equals(selected)&&!v.state().terminal()),mx,my,false);}
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);if(pending!=null)return confirmClick(mx,my);if(tabClick(mx,my))return true;
        EconomyStatePayload s=menu.getEconomyState();if(s==null)return super.mouseClicked(mx,my,button);
        boolean handled=switch(tab){case OVERVIEW->false;case EXTERNAL->externalClick(mx,my,s);case PLAYER->playerClick(mx,my,s);case MINE->activityClick(mx,my,s);case CONTRACTS->contractClick(mx,my,s);case ACTIVITY->activityClick(mx,my,s);};
        return handled||super.mouseClicked(mx,my,button);
    }

    private boolean tabClick(double mx,double my){
        if(in(mx,my,width-176,12,104,20)){tab=Tab.EXTERNAL;selected=null;scroll=0;layoutFields();return true;}
        if(in(mx,my,width-68,12,52,20)){tab=Tab.ACTIVITY;selected=null;scroll=0;layoutFields();return true;}
        int tw=(width-168)/MAIN_TABS.length,x=8;
        for(Tab value:MAIN_TABS){if(in(mx,my,x,58,tw-4,20)){tab=value;selected=null;scroll=0;pending=null;layoutFields();return true;}x+=tw;}
        return false;
    }

    private boolean externalClick(double mx,double my,EconomyStatePayload s){
        Layout l=layout();int cy=l.y+58;for(String value:categories(s)){if(in(mx,my,l.leftX+8,cy,l.leftW-16,20)){category=value;selected=null;scroll=0;return true;}cy+=22;}int ty=l.y+l.h-50;
        if(in(mx,my,l.leftX+8,ty,l.leftW-16,20)){direction=EconomyTypes.MarketDirection.SELL_TO_MASTER;selected=null;return true;}if(in(mx,my,l.leftX+8,ty+23,l.leftW-16,20)){direction=EconomyTypes.MarketDirection.BUY_FROM_MASTER;selected=null;return true;}
        List<EconomyStatePayload.OfferView> values=visibleOffers(s);UUID id=clickedRow(mx,my,l,values.stream().map(EconomyStatePayload.OfferView::id).toList());if(id!=null){selected=id;quantity.setValue("1");layoutFields();return true;}
        var o=selectedOffer(values);if(o==null)return false;long max=maxAcceptable(o);int qy=height-103,qw=Math.max(22,(l.detailW-29)/4);long[] quick={1,64,256,max};for(int i=0;i<4;i++)if(in(mx,my,l.detailX+7+i*(qw+3),qy,qw,18)){quantity.setValue(Long.toString(Math.max(1,Math.min(max,quick[i]))));return true;}if(in(mx,my,l.detailX+8,height-79,18,18)){quantity.setValue(Long.toString(Math.max(1,selectedQuantity(max)-1)));return true;}if(in(mx,my,l.detailX+l.detailW-26,height-79,18,18)){quantity.setValue(Long.toString(Math.min(max,Math.max(1,selectedQuantity(max)+1))));return true;}
        if(in(mx,my,l.detailX+8,height-36,l.detailW-16,22)){long amount=selectedQuantity(max);if(o.status()==EconomyTypes.MarketOfferStatus.OPEN&&amount>0)minecraft.setScreen(new FreightConfirmScreen(this,menu,o.id(),true,amount));return true;}return false;
    }

    private boolean playerClick(double mx,double my,EconomyStatePayload s){
        Layout l=boardLayout();
        if(in(mx,my,width-146,58,138,20)){if(s.unlocked())minecraft.setScreen(new EconomyCreateScreen(this,EconomyCreateScreen.Mode.PRIVATE,menu.getPylonPos(),s));return true;}
        for(int i=0;i<4;i++)if(in(mx,my,180+i*((width-188)/4),95,(width-188)/4-4,20)){listingFilter=i;selected=null;scroll=0;return true;}
        List<EconomyStatePayload.ListingView> values=visibleListings(s);
        UUID id=clickedRow(mx,my,l,values.stream().map(EconomyStatePayload.ListingView::id).toList());
        if(id!=null){selected=id;return true;}
        var v=selectedListing(values);if(v==null)return false;
        ActionSpec a=listingAction(v);
        if(isParticipant(v)&&in(mx,my,l.detailX+8,height-63,l.detailW-16,20)){
            if(v.state()==EconomyTypes.ListingState.RESERVED)a=new ActionSpec(EconomyCommandPayload.Action.CANCEL_LISTING,Component.translatable("gui.createcityexpansioncore.economy.cancel"),true);
            else if(v.state()==EconomyTypes.ListingState.HANDED_OVER)a=new ActionSpec(EconomyCommandPayload.Action.DISPUTE_LISTING,tr("board.dispute"),true);
            else return false;
        }else if(!in(mx,my,l.detailX+8,height-36,l.detailW-16,22))return false;
        if(a!=null){
            Component message=tr("board.confirm_listing",a.label,side(v.offerItemId(),v.offerAmount()),side(v.requestItemId(),v.requestAmount()));
            if(!v.isBarter())message=message.copy().append("\n").append(tr("board.confirm_money",v.price(),fee(v.price()),total(v.price(),fee(v.price()))));
            pending=new Pending(message,a.action,v.id(),0);
        }
        return true;
    }

    private boolean contractClick(double mx,double my,EconomyStatePayload s){
        Layout l=wideLayout();if(in(mx,my,l.listX+l.listW-190,l.y+3,90,20)){if(!s.logistics().getBoolean("CanCreateOrder"))return true;minecraft.setScreen(new EconomyCreateScreen(this,EconomyCreateScreen.Mode.CITY_CONTRACT,menu.getPylonPos(),s));return true;}if(in(mx,my,l.listX+l.listW-95,l.y+3,90,20)){minecraft.setScreen(new FreightSellScreen(this,menu));return true;}List<EconomyStatePayload.ContractView> values=sortedContracts(s);UUID id=clickedRow(mx,my,l,values.stream().map(EconomyStatePayload.ContractView::id).toList());if(id!=null){selected=id;detailScroll=0;return true;}var c=selectedContract(values);
        if(c!=null&&in(mx,my,l.detailX+8,height-64,l.detailW-16,20)){var p=FreightUi.point(s,c.destinationId());if(p!=null)minecraft.setScreen(new PolishedWorldMapScreen(menu).freightFocus(this,p.dimension,p.postbox));return true;}
        ActionSpec a=contractAction(c);if(a!=null&&in(mx,my,l.detailX+8,height-36,l.detailW-16,22)){if(a.action==null){if(minecraft!=null)minecraft.setScreen(new LogisticsScreen(this,menu,c.id()));}else if(a.action==EconomyCommandPayload.Action.ACCEPT_CONTRACT)minecraft.setScreen(new FreightConfirmScreen(this,menu,c.id(),false,0));else pending=new Pending(tr("confirm.contract",a.label,contractTitle(c)),a.action,c.id(),0);return true;}return false;
    }

    private boolean activityClick(double mx,double my,EconomyStatePayload s){Layout l=wideLayout();
        if(in(mx,my,l.detailX+8,height-36,l.detailW-16,22)&&selected!=null){
            if(s.contracts().stream().anyMatch(c->c.id().equals(selected)))tab=Tab.CONTRACTS;
            else if(s.listings().stream().anyMatch(v->v.id().equals(selected)&&!v.state().terminal()))tab=Tab.PLAYER;
            else if(s.offers().stream().anyMatch(o->o.id().equals(selected))){tab=Tab.EXTERNAL;direction=s.offers().stream().filter(o->o.id().equals(selected)).findFirst().orElseThrow().direction();}
            scroll=0;layoutFields();return true;
        }List<Activity> values=activities(s);UUID id=clickedRow(mx,my,l,values.stream().map(Activity::id).toList());if(id!=null){selected=id;return true;}return false;}

    private void confirmation(GuiGraphics g,int mx,int my){
        g.fill(8,56,width-8,height-8,PANEL);
        int w=Math.min(460,width-24),x=(width-w)/2,y=height/2-105;
        title(g,tr("confirm.title"),x+12,y+13);
        g.drawWordWrap(font,pending.message,x+12,y+38,w-24,TEXT);
        g.drawWordWrap(font,tr("board.confirm_safety"),x+12,y+108,w-24,MUTED);
        int bw=(w-30)/2;
        button(g,x+12,y+172,bw,24,Component.translatable("gui.createcityexpansioncore.common.back"),true,mx,my,false);
        button(g,x+18+bw,y+172,bw,24,Component.translatable("gui.createcityexpansioncore.common.confirm"),true,mx,my,true);
    }

    private boolean confirmClick(double mx,double my){
        int w=Math.min(460,width-24),x=(width-w)/2,y=height/2-105,bw=(w-30)/2;
        if(in(mx,my,x+12,y+172,bw,24)){pending=null;return true;}
        if(in(mx,my,x+18+bw,y+172,bw,24)){
            Pending p=pending;pending=null;
            if(isListingCommand(p.action)&&!currentListingAction(p.id,p.action)){request();return true;}
            PacketDistributor.sendToServer(new EconomyCommandPayload(menu.getPylonPos(),p.action,p.id,null,null,null,p.amount,0,0,0,"","",""));
            request();return true;
        }
        return true;
    }

    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(pending==null&&tab==Tab.CONTRACTS&&mx>=wideLayout().detailX){detailScroll=Math.clamp(detailScroll+(sy<0?20:-20),0,detailMaximum);return true;}if(pending==null)scroll=Math.max(0,scroll+(sy<0?1:-1));return true;}

    private List<EconomyStatePayload.OfferView> visibleOffers(EconomyStatePayload s){String q=query();return s.offers().stream().filter(o->o.direction()==direction).filter(o->!o.status().terminal()||o.mine()).filter(o->category.equals("all")||category.equals(o.category())).filter(o->q.isBlank()||searchable(o.itemId()).contains(q)).sorted(Comparator.comparing((EconomyStatePayload.OfferView o)->o.status()!=EconomyTypes.MarketOfferStatus.OPEN).thenComparing(o->icon(o.itemId()).getHoverName().getString())).toList();}
    private List<EconomyStatePayload.ListingView> visibleListings(EconomyStatePayload s){
        String q=query();
        return s.listings().stream()
                .filter(v->tab==Tab.MINE?isParticipant(v):v.state()==EconomyTypes.ListingState.OPEN||isParticipant(v)&&!v.state().terminal())
                .filter(v->listingFilter==0||listingFilter==1&&!v.isBarter()&&v.type()==EconomyTypes.ListingType.SELL||listingFilter==2&&!v.isBarter()&&v.type()==EconomyTypes.ListingType.BUY||listingFilter==3&&v.isBarter())
                .filter(v->q.isBlank()||searchable(v.itemId()).contains(q)||v.requestedItemId()!=null&&searchable(v.requestedItemId()).contains(q)||v.creatorName().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing((EconomyStatePayload.ListingView v)->v.state().terminal()).thenComparing(v->v.state()!=EconomyTypes.ListingState.OPEN).thenComparing(EconomyStatePayload.ListingView::creatorName)).toList();
    }

    private List<EconomyStatePayload.ContractView> sortedContracts(EconomyStatePayload s){return s.contracts().stream().sorted(Comparator.comparing((EconomyStatePayload.ContractView c)->c.state().terminal()).thenComparingLong(EconomyStatePayload.ContractView::deadlineAt)).toList();}
    private List<String> categories(EconomyStatePayload s){List<String> values=new ArrayList<>();values.add("all");s.offers().stream().filter(o->o.direction()==direction).map(EconomyStatePayload.OfferView::category).filter(v->v!=null&&!v.isBlank()).distinct().sorted().forEach(values::add);return values;}
    private List<Activity> activities(EconomyStatePayload s){List<Activity> values=new ArrayList<>();s.offers().stream().filter(EconomyStatePayload.OfferView::mine).forEach(o->values.add(new Activity(o.id(),o.itemId(),icon(o.itemId()).getHoverName(),tr("activity.external"),status(o.status()),statusColor(o.status()),o.status().terminal(),o.quantity()-o.remaining(),o.quantity(),o.expiresAt(),o.bond())));s.listings().stream().filter(EconomyStatePayload.ListingView::mine).forEach(v->values.add(new Activity(v.id(),v.itemId(),icon(v.itemId()).getHoverName(),tr("activity.listing"),status(v.state()),statusColor(v.state()),v.state().terminal(),0,0,0,0)));s.contracts().stream().filter(c->c.mine()||c.issuerMine()).forEach(c->values.add(new Activity(c.id(),c.itemId(),contractTitle(c),tr("activity.contract"),status(c.state()),statusColor(c.state()),c.state().terminal(),c.delivered(),c.amount(),c.deadlineAt(),0)));values.sort(Comparator.comparing(Activity::terminal).thenComparing(a->a.title.getString()));return values;}

    private EconomyStatePayload.OfferView selectedOffer(){EconomyStatePayload s=menu.getEconomyState();return s==null?null:selectedOffer(visibleOffers(s));}
    private EconomyStatePayload.OfferView selectedOffer(List<EconomyStatePayload.OfferView> v){return v.stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);}
    private EconomyStatePayload.ListingView selectedListing(List<EconomyStatePayload.ListingView> v){return v.stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);}
    private EconomyStatePayload.ContractView selectedContract(List<EconomyStatePayload.ContractView> v){return v.stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);}

    private ActionSpec listingAction(EconomyStatePayload.ListingView v){if(v==null)return null;UUID me=minecraft==null||minecraft.player==null?null:minecraft.player.getUUID();if(v.state()==EconomyTypes.ListingState.OPEN)return v.creator().equals(me)?new ActionSpec(EconomyCommandPayload.Action.CANCEL_LISTING,Component.translatable("gui.createcityexpansioncore.economy.cancel"),true):new ActionSpec(EconomyCommandPayload.Action.ACCEPT_LISTING,Component.translatable("gui.createcityexpansioncore.economy.accept"),false);UUID supplier=v.type()==EconomyTypes.ListingType.SELL?v.creator():v.counterparty(),buyer=v.type()==EconomyTypes.ListingType.BUY?v.creator():v.counterparty();if(v.state()==EconomyTypes.ListingState.RESERVED&&Objects.equals(supplier,me))return new ActionSpec(EconomyCommandPayload.Action.MARK_HANDED,Component.translatable("gui.createcityexpansioncore.economy.handed"),false);if(v.state()==EconomyTypes.ListingState.HANDED_OVER&&Objects.equals(buyer,me))return new ActionSpec(EconomyCommandPayload.Action.CONFIRM_LISTING,Component.translatable("gui.createcityexpansioncore.economy.confirm"),false);return null;}
    private ActionSpec contractAction(EconomyStatePayload.ContractView c){if(c==null)return null;var s=menu.getEconomyState();boolean admin=s!=null&&s.logistics().getBoolean("CanCreateOrder"),builder=s!=null&&s.logistics().getBoolean("CanFulfillOrder");if(c.state()==EconomyTypes.ContractState.OPEN&&!c.issuerMine()&&builder)return new ActionSpec(EconomyCommandPayload.Action.ACCEPT_CONTRACT,Component.translatable("gui.createcityexpansioncore.economy.accept"),false);if(c.state()==EconomyTypes.ContractState.OPEN&&c.issuerMine()&&admin)return new ActionSpec(EconomyCommandPayload.Action.CANCEL_CONTRACT,Component.translatable("gui.createcityexpansioncore.economy.cancel"),true);if((c.state()==EconomyTypes.ContractState.ACCEPTED||c.state()==EconomyTypes.ContractState.IN_DELIVERY)&&(c.mine()||c.issuerMine()))return new ActionSpec(null,Component.translatable("gui.createcityexpansioncore.trade.delivery"),false);if(c.state()==EconomyTypes.ContractState.DELIVERED&&c.issuerMine()&&admin)return new ActionSpec(EconomyCommandPayload.Action.CONFIRM_CONTRACT,Component.translatable("gui.createcityexpansioncore.economy.confirm"),false);return null;}

    private UUID clickedRow(double mx,double my,Layout l,List<UUID> ids){int visible=Math.max(1,(l.h-32)/ROW_H),start=Math.min(scroll,Math.max(0,ids.size()-visible)),top=l.y+27;for(int i=start;i<Math.min(ids.size(),start+visible);i++){int y=top+(i-start)*ROW_H;if(in(mx,my,l.listX+5,y,l.listW-10,ROW_H-3))return ids.get(i);}return null;}
    private Layout layout(){int y=92,h=Math.max(90,height-y-8),leftW=width>=700?154:112,detailW=width>=820?248:Math.max(156,width/4),leftX=8,listX=leftX+leftW+7,detailX=width-detailW-8,listW=Math.max(112,detailX-listX-7);return new Layout(leftX,leftW,listX,listW,detailX,detailW,y,h);}
    private Layout wideLayout(){Layout b=layout();return new Layout(8,0,8,Math.max(130,b.detailX-15),b.detailX,b.detailW,b.y,b.h);}

    private void threePanels(GuiGraphics g,Layout l){plate(g,l.leftX,l.y,l.leftW,l.h);twoPanels(g,l);}
    private void twoPanels(GuiGraphics g,Layout l){plate(g,l.listX,l.y,l.listW,l.h);plate(g,l.detailX,l.y,l.detailW,l.h);}
    private void row(GuiGraphics g,Layout l,int y,boolean chosen,int mx,int my){boolean hover=in(mx,my,l.listX+5,y,l.listW-10,ROW_H-3);g.fill(l.listX+5,y,l.listX+l.listW-5,y+ROW_H-3,chosen?0xFF493A29:hover?0xFF303A3D:PANEL_ALT);if(chosen||hover)g.renderOutline(l.listX+5,y,l.listW-10,ROW_H-3,chosen?ACCENT:FRAME);}
    private void stat(GuiGraphics g,int x,int y,int w,Component label,Component value){g.fill(x,y,x+w,y+34,0xD5182023);g.fill(x,y,x+2,y+34,0xA9D09A3A);g.drawString(font,ellipsize(label,w-10),x+7,y+6,MUTED,false);g.drawString(font,ellipsize(value,w-10),x+7,y+19,TEXT,false);}
    private void overviewCard(GuiGraphics g,int x,int y,int w,ItemStack icon,Component value,Component hint){plate(g,x,y,w,56);g.renderItem(icon,x+10,y+10);g.drawString(font,ellipsize(value,w-40),x+33,y+12,TEXT,false);g.drawString(font,ellipsize(hint,w-20),x+10,y+36,MUTED,false);}
    private void detail(GuiGraphics g,Layout l,int y,Component value){g.drawString(font,ellipsize(value,l.detailW-20),l.detailX+10,y,MUTED,false);}
    private void title(GuiGraphics g,Component value,int x,int y){g.drawString(font,value,x,y,0xFFFFD59A,false);}
    private void empty(GuiGraphics g,int x,int y,Component value,int w){g.drawWordWrap(font,value,x,y,Math.max(60,w),MUTED);}
    private void fieldBack(GuiGraphics g,EditBox field){if(field==null||!field.visible)return;g.fill(field.getX()-3,field.getY()-2,field.getX()+field.getWidth()+3,field.getY()+18,0xFF111719);g.renderOutline(field.getX()-3,field.getY()-2,field.getWidth()+6,20,FRAME);}
    private void choice(GuiGraphics g,int x,int y,int w,Component label,boolean selected,int mx,int my){boolean hover=in(mx,my,x,y,w,20);g.fill(x,y,x+w,y+20,selected?0xFF4A391E:hover?0xFF303A3D:PANEL_ALT);if(selected)g.fill(x,y,x+2,y+20,ACCENT);g.drawString(font,ellipsize(label,w-14),x+8,y+6,selected?0xFFFFD59A:TEXT,false);}
    private void button(GuiGraphics g,int x,int y,int w,int h,Component label,boolean enabled,int mx,int my,boolean strong){boolean hover=enabled&&in(mx,my,x,y,w,h);int color=!enabled?0xFF282E30:strong?(hover?0xFF76562E:0xFF493A29):(hover?0xFF39464A:PANEL_ALT);CityPylonTheme.rounded(g,x,y,w,h,4,enabled?(strong?ACCENT:FRAME):0xFF394043);CityPylonTheme.rounded(g,x+1,y+1,w-2,h-2,3,color);g.drawCenteredString(font,ellipsize(label,w-8),x+w/2,y+(h-8)/2,enabled?(strong?0xFFFFD59A:TEXT):0xFF687174);}
    private void iconButton(GuiGraphics g,int x,int y,ItemStack icon,int mx,int my,Component tip){boolean hover=in(mx,my,x,y,26,26);g.fill(x,y,x+26,y+26,hover?0xFF303A3D:PANEL_ALT);g.renderOutline(x,y,26,26,FRAME);g.renderItem(icon,x+5,y+5);if(hover)tooltip=tip;}
    private static void progress(GuiGraphics g,int x,int y,int w,long value,long max){g.fill(x,y,x+w,y+3,0xFF30383B);int filled=max<=0?0:(int)Math.min(w,Math.max(0,java.math.BigInteger.valueOf(value).multiply(java.math.BigInteger.valueOf(w)).divide(java.math.BigInteger.valueOf(max)).longValue()));g.fill(x,y,x+filled,y+3,ACCENT);}
    private static void plate(GuiGraphics g,int x,int y,int w,int h){CityPylonTheme.panel(g,x,y,w,h);}

    private Component destination(EconomyStatePayload s,UUID id){var p=FreightUi.point(s,id);return p==null?FreightUi.tr("requires_setup"):Component.literal(p.name); }
    private Component side(ResourceLocation id,long amount){return id==null?tr("currency",amount):tr("item_amount",amount,icon(id).getHoverName());}
    private Component remainingTime(long deadline){if(deadline<=0)return tr("no_deadline");long now=minecraft==null||minecraft.level==null?0:minecraft.level.getGameTime(),ticks=Math.max(0,deadline-now);return tr("minutes",Math.max(1,ticks/1200));}
    private String query(){return search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);}
    private String searchable(ResourceLocation id){return(id+" "+icon(id).getHoverName().getString()).toLowerCase(Locale.ROOT);}
    private long selectedQuantity(long max){if(max<=0||quantity==null)return 0;try{return Math.max(1,Math.min(max,Long.parseLong(quantity.getValue())));}catch(NumberFormatException ignored){return 0;}}
    private static long maxAcceptable(EconomyStatePayload.OfferView o){return Math.max(0,Math.min(o.remaining(),o.acceptLimit()));}
    private static int obligationLimit(int reputation){return 1+Math.max(0,reputation)/250;}
    private static long mul(long a,long b){return a<=0||b<=0?0:a>Long.MAX_VALUE/b?Long.MAX_VALUE:a*b;}
    private Component listingType(EconomyTypes.ListingType v){return tr("listing_type."+v.name().toLowerCase(Locale.ROOT));}
    private Component categoryLabel(String value){String safe=value==null||value.isBlank()?"other":value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]","_");return tr("category."+safe);}
    private Component status(Enum<?> v){return tr("status."+v.name().toLowerCase(Locale.ROOT));}
    private static int statusColor(Enum<?> v){String n=v.name();if(n.equals("COMPLETED"))return GOOD;if(n.equals("FAILED")||n.equals("CANCELLED")||n.equals("EXPIRED")||n.equals("DISPUTED"))return BAD;if(n.equals("OPEN"))return GOOD;return WARN;}
    private Component contractTitle(EconomyStatePayload.ContractView c){return c.title().isBlank()?icon(c.itemId()).getHoverName():Component.literal(c.title());}
    private Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.market."+key,args);}
    private Component ellipsize(Component c,int max){if(max<=12||font.width(c)<=max)return c;String tail="…";return Component.literal(font.plainSubstrByWidth(c.getString(),Math.max(1,max-font.width(tail)))+tail);}
    private static ItemStack item(String id){ResourceLocation key=ResourceLocation.tryParse(id);return key==null||!BuiltInRegistries.ITEM.containsKey(key)?new ItemStack(Items.BARRIER):new ItemStack(BuiltInRegistries.ITEM.get(key));}
    private static ItemStack icon(ResourceLocation id){return id==null||!BuiltInRegistries.ITEM.containsKey(id)?new ItemStack(Items.BARRIER):new ItemStack(BuiltInRegistries.ITEM.get(id));}
    private static boolean in(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}

    private Layout boardLayout(){
        int detailW=Math.min(256,Math.max(224,width*2/5)),detailX=width-detailW-8;
        return new Layout(8,0,8,detailX-21,detailX,detailW,126,Math.max(90,height-134));
    }
    private boolean isParticipant(EconomyStatePayload.ListingView v){
        UUID me=minecraft==null||minecraft.player==null?null:minecraft.player.getUUID();
        return me!=null&&(me.equals(v.creator())||me.equals(v.counterparty()));
    }
    private long fee(long price){var state=menu.getEconomyState();return state==null?0:com.example.examplemod.economy.EconomyMath.applyBasisPoints(price,state.privateFeeBasisPoints());}
    private static long total(long price,long fee){return price>Long.MAX_VALUE-fee?Long.MAX_VALUE:price+fee;}
    private static boolean isListingCommand(EconomyCommandPayload.Action a){return switch(a){case ACCEPT_LISTING,MARK_HANDED,CONFIRM_LISTING,CANCEL_LISTING,DISPUTE_LISTING->true;default->false;};}
    private boolean currentListingAction(UUID id,EconomyCommandPayload.Action action){
        var state=menu.getEconomyState();if(state==null)return false;
        var v=state.listings().stream().filter(l->l.id().equals(id)).findFirst().orElse(null);if(v==null)return false;
        if(action==EconomyCommandPayload.Action.CANCEL_LISTING&&v.state()==EconomyTypes.ListingState.RESERVED)return isParticipant(v);
        if(action==EconomyCommandPayload.Action.DISPUTE_LISTING&&v.state()==EconomyTypes.ListingState.HANDED_OVER)return isParticipant(v);
        ActionSpec expected=listingAction(v);return expected!=null&&expected.action==action;
    }

    private record Layout(int leftX,int leftW,int listX,int listW,int detailX,int detailW,int y,int h){}
    private record Pending(Component message,EconomyCommandPayload.Action action,UUID id,long amount){}
    private record ActionSpec(EconomyCommandPayload.Action action,Component label,boolean danger){}
    private record Activity(UUID id,ResourceLocation item,Component title,Component kind,Component state,int color,boolean terminal,long current,long total,long deadline,long bond){}
    private enum Tab{
        MINE("gui.createcityexpansioncore.market.board.mine","minecraft:clock"),OVERVIEW("gui.createcityexpansioncore.market.tab.overview","minecraft:compass"),EXTERNAL("gui.createcityexpansioncore.market.tab.external","minecraft:emerald"),PLAYER("gui.createcityexpansioncore.market.tab.player","minecraft:player_head"),CONTRACTS("gui.createcityexpansioncore.market.tab.contracts","minecraft:writable_book"),ACTIVITY("gui.createcityexpansioncore.market.tab.activity","minecraft:clock");
        final String key;final ItemStack icon;Tab(String key,String icon){this.key=key;this.icon=item(icon);}
    }
}
