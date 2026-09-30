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

/** Full-screen, selection-first market dashboard. */
public final class MarketScreen extends Screen {
    private static final int BG=0xFF101719,PANEL=0xEC182023,PANEL_ALT=0xF0222B2E,FRAME=0xFF596064;
    private static final int ACCENT=0xFFD09A3A,TEXT=0xFFD8DEDF,MUTED=0xFF98A3A7,GOOD=0xFF77B992,WARN=0xFFE2BD75,BAD=0xFFD07872;
    private static final int ROW_H=42,TAB_H=28;
    private final CityPylonScreen parent;
    private final CityPylonMenu menu;
    private Tab tab=Tab.OVERVIEW;
    private EconomyTypes.MarketDirection direction=EconomyTypes.MarketDirection.SELL_TO_MASTER;
    private String category="all";
    private EditBox search,quantity;
    private UUID selected;
    private int scroll,refreshTicks;
    private Pending pending;
    private Component tooltip;

    public MarketScreen(CityPylonScreen parent){
        super(Component.translatable("gui.createcityexpansioncore.market.title"));
        this.parent=parent;this.menu=parent.getMenu();
    }

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
        super.resize(minecraft,width,height);search.setValue(q);quantity.setValue(a);
    }

    @Override public void onClose(){if(pending!=null){pending=null;return;}if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public void tick(){if(++refreshTicks>=40){refreshTicks=0;request();}layoutFields();}
    private void request(){PacketDistributor.sendToServer(EconomyCommandPayload.simple(menu.getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));}

    private void layoutFields(){
        if(search==null||quantity==null)return;Layout l=layout();
        search.setX(l.leftX+9);search.setY(l.y+32);search.setWidth(Math.max(64,l.leftW-18));
        search.setVisible(tab==Tab.EXTERNAL||tab==Tab.PLAYER);
        quantity.setX(l.detailX+31);quantity.setY(height-78);quantity.setWidth(Math.max(38,l.detailW-62));
        quantity.setVisible(tab==Tab.EXTERNAL&&selectedOffer()!=null&&pending==null);
    }

    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        tooltip=null;g.fill(0,0,width,height,BG);EconomyStatePayload s=menu.getEconomyState();
        header(g,s,mx,my);tabs(g,mx,my);
        if(s==null)g.drawCenteredString(font,Component.translatable("gui.createcityexpansioncore.economy.loading"),width/2,height/2,MUTED);
        else switch(tab){case OVERVIEW->overview(g,s);case EXTERNAL->external(g,s,mx,my);case PLAYER->player(g,s,mx,my);case CONTRACTS->contracts(g,s,mx,my);case ACTIVITY->activity(g,s,mx,my);}
        super.render(g,mx,my,pt);
        if(pending!=null)confirmation(g,mx,my);
        else if(tooltip!=null)g.renderTooltip(font,font.split(tooltip,Math.min(240,width-24)),net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,mx,my);
    }

    private void header(GuiGraphics g,EconomyStatePayload s,int mx,int my){
        plate(g,8,7,width-16,44);g.renderItem(item("createcityexpansioncore:city_pylon"),14,14);
        g.drawString(font,title,36,12,0xFFFFD59A,false);g.drawString(font,tr("subtitle"),36,26,MUTED,false);
        if(s!=null&&width>=550){int cw=Math.max(78,Math.min(126,(width-300)/4));int x=Math.max(190,width-40-cw*4-18);
            stat(g,x,11,cw,tr("card.wallet"),Component.literal(Long.toString(s.wallet())));
            stat(g,x+cw+6,11,cw,tr("card.treasury"),Component.literal(Long.toString(s.treasury())));
            stat(g,x+(cw+6)*2,11,cw,tr("card.reputation"),Component.literal(Integer.toString(s.tradeReputation())));
            stat(g,x+(cw+6)*3,11,cw,tr("card.obligations"),Component.literal(s.activeObligations()+" / "+obligationLimit(s.tradeReputation())));
        }
        iconButton(g,width-37,15,item("minecraft:barrier"),mx,my,tr("close"));
    }

    private void tabs(GuiGraphics g,int mx,int my){
        int labelW=width>=780?92:0,tw=TAB_H+labelW,total=Tab.values().length*(tw+4)+4,x=Math.max(8,(width-total)/2);plate(g,x,56,total,30);x+=3;
        for(Tab value:Tab.values()){boolean active=value==tab,hover=in(mx,my,x,57,tw,TAB_H);g.fill(x,57,x+tw,57+TAB_H,active?0xFF4A391E:hover?0xFF303A3D:PANEL_ALT);if(active||hover)g.renderOutline(x,57,tw,TAB_H,active?ACCENT:FRAME);g.renderItem(value.icon,x+6,63);if(labelW>0)g.drawString(font,Component.translatable(value.key),x+28,67,active?0xFFFFD59A:TEXT,false);if(hover)tooltip=Component.translatable(value.key);x+=tw+4;}
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
        Layout l=layout();threePanels(g,l);title(g,tr("filters"),l.leftX+9,l.y+10);fieldBack(g,search);button(g,l.leftX+8,l.y+61,l.leftW-16,22,tr("create_listing"),true,mx,my,true);
        List<EconomyStatePayload.ListingView> values=visibleListings(s);title(g,tr("player.active",values.size()),l.listX+9,l.y+10);int top=l.y+27,visible=Math.max(1,(l.h-32)/ROW_H),start=Math.min(scroll,Math.max(0,values.size()-visible));
        if(values.isEmpty())empty(g,l.listX+12,top+16,tr("empty.player"),l.listW-24);
        for(int i=start;i<Math.min(values.size(),start+visible);i++){var v=values.get(i);int y=top+(i-start)*ROW_H;row(g,l,y,v.id().equals(selected),mx,my);ItemStack stack=icon(v.itemId());g.renderItem(stack,l.listX+11,y+5);g.drawString(font,ellipsize(stack.getHoverName(),l.listW-130),l.listX+33,y+5,TEXT,false);g.drawString(font,tr("listing.line",v.amount(),v.price(),v.creatorName()),l.listX+33,y+18,MUTED,false);g.drawString(font,listingType(v.type()),l.listX+l.listW-72,y+5,WARN,false);}
        listingDetail(g,selectedListing(values),l,mx,my);
    }

    private void listingDetail(GuiGraphics g,EconomyStatePayload.ListingView v,Layout l,int mx,int my){
        title(g,tr("details"),l.detailX+10,l.y+10);if(v==null){empty(g,l.detailX+10,l.y+42,tr("empty.selection"),l.detailW-20);return;}
        ItemStack stack=icon(v.itemId());g.renderItem(stack,l.detailX+10,l.y+31);g.drawString(font,ellipsize(stack.getHoverName(),l.detailW-42),l.detailX+32,l.y+35,TEXT,false);
        int y=l.y+61;detail(g,l,y,tr("listing.actor",v.creatorName()));detail(g,l,y+=16,tr("listing.type",listingType(v.type())));detail(g,l,y+=16,tr("listing.offer",side(v.offerItemId(),v.offerAmount())));detail(g,l,y+=16,tr("listing.request",side(v.requestItemId(),v.requestAmount())));detail(g,l,y+=16,tr("listing.status",status(v.state())));if(!v.meeting().isBlank())g.drawWordWrap(font,tr("listing.meeting",v.meeting()),l.detailX+10,y+18,l.detailW-20,MUTED);
        ActionSpec a=listingAction(v);if(a!=null)button(g,l.detailX+8,height-36,l.detailW-16,22,a.label,true,mx,my,a.danger);
    }

    private void contracts(GuiGraphics g,EconomyStatePayload s,int mx,int my){
        Layout l=wideLayout();twoPanels(g,l);List<EconomyStatePayload.ContractView> values=sortedContracts(s);title(g,tr("contracts.count",values.size()),l.listX+9,l.y+10);int top=l.y+27,visible=Math.max(1,(l.h-32)/ROW_H),start=Math.min(scroll,Math.max(0,values.size()-visible));
        if(values.isEmpty())empty(g,l.listX+12,top+16,tr("empty.contracts"),l.listW-24);
        for(int i=start;i<Math.min(values.size(),start+visible);i++){var c=values.get(i);int y=top+(i-start)*ROW_H;row(g,l,y,c.id().equals(selected),mx,my);g.renderItem(icon(c.itemId()),l.listX+11,y+5);g.drawString(font,ellipsize(contractTitle(c),l.listW-150),l.listX+33,y+5,TEXT,false);g.drawString(font,tr("contract.line",c.delivered(),c.amount(),c.reward()),l.listX+33,y+18,MUTED,false);g.drawString(font,status(c.state()),l.listX+l.listW-88,y+5,statusColor(c.state()),false);progress(g,l.listX+33,y+31,l.listW-45,c.delivered(),c.amount());}
        contractDetail(g,selectedContract(values),s,l,mx,my);
        button(g,l.listX+l.listW-190,l.y+3,90,20,FreightUi.tr("create_buy"),true,mx,my,false);
        button(g,l.listX+l.listW-95,l.y+3,90,20,FreightUi.tr("city_sales"),true,mx,my,false);
    }

    private void contractDetail(GuiGraphics g,EconomyStatePayload.ContractView c,EconomyStatePayload s,Layout l,int mx,int my){
        title(g,tr("details"),l.detailX+10,l.y+10);if(c==null){empty(g,l.detailX+10,l.y+42,tr("empty.selection"),l.detailW-20);return;}
        g.renderItem(icon(c.itemId()),l.detailX+10,l.y+31);g.drawString(font,ellipsize(contractTitle(c),l.detailW-42),l.detailX+32,l.y+35,TEXT,false);
        int y=l.y+61;detail(g,l,y,tr("contract.requester",c.issuerName()));detail(g,l,y+=16,tr("contract.cargo",icon(c.itemId()).getHoverName(),c.amount()));detail(g,l,y+=16,tr("contract.progress",c.delivered(),c.amount()));detail(g,l,y+=16,tr("contract.reward",c.reward()));detail(g,l,y+=16,tr("contract.deadline",remainingTime(c.deadlineAt())));detail(g,l,y+=16,tr("contract.location",destination(s,c.destinationId())));detail(g,l,y+=16,tr("contract.status",status(c.state())));
        ActionSpec a=contractAction(c);if(a!=null)button(g,l.detailX+8,height-36,l.detailW-16,22,a.label,true,mx,my,a.danger);
    }

    private void activity(GuiGraphics g,EconomyStatePayload s,int mx,int my){
        Layout l=wideLayout();twoPanels(g,l);List<Activity> values=activities(s);title(g,tr("activity.count",values.size()),l.listX+9,l.y+10);int top=l.y+27,visible=Math.max(1,(l.h-32)/ROW_H),start=Math.min(scroll,Math.max(0,values.size()-visible));
        if(values.isEmpty())empty(g,l.listX+12,top+16,tr("empty.activity"),l.listW-24);
        for(int i=start;i<Math.min(values.size(),start+visible);i++){Activity a=values.get(i);int y=top+(i-start)*ROW_H;row(g,l,y,a.id.equals(selected),mx,my);g.renderItem(icon(a.item),l.listX+11,y+5);g.drawString(font,ellipsize(a.title,l.listW-128),l.listX+33,y+5,TEXT,false);g.drawString(font,a.total>0?tr("activity.progress",a.current,a.total):a.kind,l.listX+33,y+18,MUTED,false);g.drawString(font,a.state,l.listX+l.listW-88,y+5,a.color,false);if(a.total>0)progress(g,l.listX+33,y+31,l.listW-45,a.current,a.total);}
        title(g,tr("details"),l.detailX+10,l.y+10);Activity a=values.stream().filter(v->v.id.equals(selected)).findFirst().orElse(null);if(a==null)empty(g,l.detailX+10,l.y+42,tr("empty.selection"),l.detailW-20);else{g.renderItem(icon(a.item),l.detailX+10,l.y+31);g.drawString(font,ellipsize(a.title,l.detailW-42),l.detailX+32,l.y+35,TEXT,false);detail(g,l,l.y+63,a.kind);detail(g,l,l.y+81,tr("activity.state",a.state));if(a.total>0)detail(g,l,l.y+99,tr("activity.progress",a.current,a.total));if(a.deadline>0)detail(g,l,l.y+117,tr("contract.deadline",remainingTime(a.deadline)));if(a.bond>0)detail(g,l,l.y+135,tr("bond",a.bond));}
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);if(pending!=null)return confirmClick(mx,my);if(in(mx,my,width-37,15,26,26)){onClose();return true;}if(tabClick(mx,my))return true;
        EconomyStatePayload s=menu.getEconomyState();if(s==null)return super.mouseClicked(mx,my,button);
        boolean handled=switch(tab){case OVERVIEW->false;case EXTERNAL->externalClick(mx,my,s);case PLAYER->playerClick(mx,my,s);case CONTRACTS->contractClick(mx,my,s);case ACTIVITY->activityClick(mx,my,s);};
        return handled||super.mouseClicked(mx,my,button);
    }

    private boolean tabClick(double mx,double my){int lw=width>=780?92:0,tw=TAB_H+lw,total=Tab.values().length*(tw+4)+4,x=Math.max(8,(width-total)/2)+3;for(Tab value:Tab.values()){if(in(mx,my,x,57,tw,TAB_H)){tab=value;selected=null;scroll=0;pending=null;layoutFields();return true;}x+=tw+4;}return false;}

    private boolean externalClick(double mx,double my,EconomyStatePayload s){
        Layout l=layout();int cy=l.y+58;for(String value:categories(s)){if(in(mx,my,l.leftX+8,cy,l.leftW-16,20)){category=value;selected=null;scroll=0;return true;}cy+=22;}int ty=l.y+l.h-50;
        if(in(mx,my,l.leftX+8,ty,l.leftW-16,20)){direction=EconomyTypes.MarketDirection.SELL_TO_MASTER;selected=null;return true;}if(in(mx,my,l.leftX+8,ty+23,l.leftW-16,20)){direction=EconomyTypes.MarketDirection.BUY_FROM_MASTER;selected=null;return true;}
        List<EconomyStatePayload.OfferView> values=visibleOffers(s);UUID id=clickedRow(mx,my,l,values.stream().map(EconomyStatePayload.OfferView::id).toList());if(id!=null){selected=id;quantity.setValue("1");layoutFields();return true;}
        var o=selectedOffer(values);if(o==null)return false;long max=maxAcceptable(o);int qy=height-103,qw=Math.max(22,(l.detailW-29)/4);long[] quick={1,64,256,max};for(int i=0;i<4;i++)if(in(mx,my,l.detailX+7+i*(qw+3),qy,qw,18)){quantity.setValue(Long.toString(Math.max(1,Math.min(max,quick[i]))));return true;}if(in(mx,my,l.detailX+8,height-79,18,18)){quantity.setValue(Long.toString(Math.max(1,selectedQuantity(max)-1)));return true;}if(in(mx,my,l.detailX+l.detailW-26,height-79,18,18)){quantity.setValue(Long.toString(Math.min(max,Math.max(1,selectedQuantity(max)+1))));return true;}
        if(in(mx,my,l.detailX+8,height-36,l.detailW-16,22)){long amount=selectedQuantity(max);if(o.status()==EconomyTypes.MarketOfferStatus.OPEN&&amount>0)minecraft.setScreen(new FreightConfirmScreen(this,parent,o.id(),true,amount));return true;}return false;
    }

    private boolean playerClick(double mx,double my,EconomyStatePayload s){
        Layout l=layout();if(in(mx,my,l.leftX+8,l.y+61,l.leftW-16,22)){if(minecraft!=null)minecraft.setScreen(new EconomyCreateScreen(this,EconomyCreateScreen.Mode.PRIVATE,menu.getPylonPos(),s));return true;}
        List<EconomyStatePayload.ListingView> values=visibleListings(s);UUID id=clickedRow(mx,my,l,values.stream().map(EconomyStatePayload.ListingView::id).toList());if(id!=null){selected=id;return true;}var v=selectedListing(values);ActionSpec a=listingAction(v);if(a!=null&&in(mx,my,l.detailX+8,height-36,l.detailW-16,22)){pending=new Pending(tr("confirm.listing",a.label,icon(v.itemId()).getHoverName()),a.action,v.id(),0);return true;}return false;
    }

    private boolean contractClick(double mx,double my,EconomyStatePayload s){
        Layout l=wideLayout();if(in(mx,my,l.listX+l.listW-190,l.y+3,90,20)){minecraft.setScreen(new EconomyCreateScreen(this,EconomyCreateScreen.Mode.CITY_CONTRACT,menu.getPylonPos(),s));return true;}if(in(mx,my,l.listX+l.listW-95,l.y+3,90,20)){minecraft.setScreen(new FreightSellScreen(parent));return true;}List<EconomyStatePayload.ContractView> values=sortedContracts(s);UUID id=clickedRow(mx,my,l,values.stream().map(EconomyStatePayload.ContractView::id).toList());if(id!=null){selected=id;return true;}var c=selectedContract(values);ActionSpec a=contractAction(c);if(a!=null&&in(mx,my,l.detailX+8,height-36,l.detailW-16,22)){if(a.action==null){if(minecraft!=null)minecraft.setScreen(new LogisticsScreen(parent));}else if(a.action==EconomyCommandPayload.Action.ACCEPT_CONTRACT)minecraft.setScreen(new FreightConfirmScreen(this,parent,c.id(),false,0));else pending=new Pending(tr("confirm.contract",a.label,contractTitle(c)),a.action,c.id(),0);return true;}return false;
    }

    private boolean activityClick(double mx,double my,EconomyStatePayload s){Layout l=wideLayout();List<Activity> values=activities(s);UUID id=clickedRow(mx,my,l,values.stream().map(Activity::id).toList());if(id!=null){selected=id;return true;}return false;}

    private void confirmation(GuiGraphics g,int mx,int my){g.fill(0,0,width,height,0xB0000000);int w=Math.min(360,width-24),x=(width-w)/2,y=height/2-55;plate(g,x,y,w,112);g.fill(x,y,x+w,y+3,ACCENT);g.drawCenteredString(font,tr("confirm.title"),x+w/2,y+13,0xFFFFD59A);g.drawWordWrap(font,pending.message,x+14,y+34,w-28,TEXT);int bw=(w-30)/2;button(g,x+12,y+78,bw,22,Component.translatable("gui.createcityexpansioncore.common.back"),true,mx,my,false);button(g,x+18+bw,y+78,bw,22,Component.translatable("gui.createcityexpansioncore.common.confirm"),true,mx,my,true);}
    private boolean confirmClick(double mx,double my){int w=Math.min(360,width-24),x=(width-w)/2,y=height/2-55,bw=(w-30)/2;if(in(mx,my,x+12,y+78,bw,22)){pending=null;return true;}if(in(mx,my,x+18+bw,y+78,bw,22)){Pending p=pending;pending=null;PacketDistributor.sendToServer(new EconomyCommandPayload(menu.getPylonPos(),p.action,p.id,null,null,null,p.amount,0,0,0,"","",""));selected=null;request();return true;}return true;}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(pending==null)scroll=Math.max(0,scroll+(sy<0?1:-1));return true;}

    private List<EconomyStatePayload.OfferView> visibleOffers(EconomyStatePayload s){String q=query();return s.offers().stream().filter(o->o.direction()==direction).filter(o->!o.status().terminal()||o.mine()).filter(o->category.equals("all")||category.equals(o.category())).filter(o->q.isBlank()||searchable(o.itemId()).contains(q)).sorted(Comparator.comparing((EconomyStatePayload.OfferView o)->o.status()!=EconomyTypes.MarketOfferStatus.OPEN).thenComparing(o->icon(o.itemId()).getHoverName().getString())).toList();}
    private List<EconomyStatePayload.ListingView> visibleListings(EconomyStatePayload s){String q=query();return s.listings().stream().filter(v->!v.state().terminal()).filter(v->q.isBlank()||searchable(v.itemId()).contains(q)||v.creatorName().toLowerCase(Locale.ROOT).contains(q)).sorted(Comparator.comparing((EconomyStatePayload.ListingView v)->v.state()!=EconomyTypes.ListingState.OPEN).thenComparing(EconomyStatePayload.ListingView::creatorName)).toList();}
    private List<EconomyStatePayload.ContractView> sortedContracts(EconomyStatePayload s){return s.contracts().stream().sorted(Comparator.comparing((EconomyStatePayload.ContractView c)->c.state().terminal()).thenComparingLong(EconomyStatePayload.ContractView::deadlineAt)).toList();}
    private List<String> categories(EconomyStatePayload s){List<String> values=new ArrayList<>();values.add("all");s.offers().stream().filter(o->o.direction()==direction).map(EconomyStatePayload.OfferView::category).filter(v->v!=null&&!v.isBlank()).distinct().sorted().forEach(values::add);return values;}
    private List<Activity> activities(EconomyStatePayload s){List<Activity> values=new ArrayList<>();s.offers().stream().filter(EconomyStatePayload.OfferView::mine).forEach(o->values.add(new Activity(o.id(),o.itemId(),icon(o.itemId()).getHoverName(),tr("activity.external"),status(o.status()),statusColor(o.status()),o.status().terminal(),o.quantity()-o.remaining(),o.quantity(),o.expiresAt(),o.bond())));s.listings().stream().filter(EconomyStatePayload.ListingView::mine).forEach(v->values.add(new Activity(v.id(),v.itemId(),icon(v.itemId()).getHoverName(),tr("activity.listing"),status(v.state()),statusColor(v.state()),v.state().terminal(),0,0,0,0)));s.contracts().stream().filter(c->c.mine()||c.issuerMine()).forEach(c->values.add(new Activity(c.id(),c.itemId(),contractTitle(c),tr("activity.contract"),status(c.state()),statusColor(c.state()),c.state().terminal(),c.delivered(),c.amount(),c.deadlineAt(),0)));values.sort(Comparator.comparing(Activity::terminal).thenComparing(a->a.title.getString()));return values;}

    private EconomyStatePayload.OfferView selectedOffer(){EconomyStatePayload s=menu.getEconomyState();return s==null?null:selectedOffer(visibleOffers(s));}
    private EconomyStatePayload.OfferView selectedOffer(List<EconomyStatePayload.OfferView> v){return v.stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);}
    private EconomyStatePayload.ListingView selectedListing(List<EconomyStatePayload.ListingView> v){return v.stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);}
    private EconomyStatePayload.ContractView selectedContract(List<EconomyStatePayload.ContractView> v){return v.stream().filter(o->o.id().equals(selected)).findFirst().orElse(null);}

    private ActionSpec listingAction(EconomyStatePayload.ListingView v){if(v==null)return null;UUID me=minecraft==null||minecraft.player==null?null:minecraft.player.getUUID();if(v.state()==EconomyTypes.ListingState.OPEN)return v.creator().equals(me)?new ActionSpec(EconomyCommandPayload.Action.CANCEL_LISTING,Component.translatable("gui.createcityexpansioncore.economy.cancel"),true):new ActionSpec(EconomyCommandPayload.Action.ACCEPT_LISTING,Component.translatable("gui.createcityexpansioncore.economy.accept"),false);UUID supplier=v.type()==EconomyTypes.ListingType.SELL?v.creator():v.counterparty(),buyer=v.type()==EconomyTypes.ListingType.BUY?v.creator():v.counterparty();if(v.state()==EconomyTypes.ListingState.RESERVED&&Objects.equals(supplier,me))return new ActionSpec(EconomyCommandPayload.Action.MARK_HANDED,Component.translatable("gui.createcityexpansioncore.economy.handed"),false);if(v.state()==EconomyTypes.ListingState.HANDED_OVER&&Objects.equals(buyer,me))return new ActionSpec(EconomyCommandPayload.Action.CONFIRM_LISTING,Component.translatable("gui.createcityexpansioncore.economy.confirm"),false);return null;}
    private ActionSpec contractAction(EconomyStatePayload.ContractView c){if(c==null)return null;if(c.state()==EconomyTypes.ContractState.OPEN&&!c.issuerMine())return new ActionSpec(EconomyCommandPayload.Action.ACCEPT_CONTRACT,Component.translatable("gui.createcityexpansioncore.economy.accept"),false);if(c.state()==EconomyTypes.ContractState.OPEN&&c.issuerMine())return new ActionSpec(EconomyCommandPayload.Action.CANCEL_CONTRACT,Component.translatable("gui.createcityexpansioncore.economy.cancel"),true);if((c.state()==EconomyTypes.ContractState.ACCEPTED||c.state()==EconomyTypes.ContractState.IN_DELIVERY)&&c.mine())return new ActionSpec(null,Component.translatable("gui.createcityexpansioncore.economy.shipment"),false);if(c.state()==EconomyTypes.ContractState.DELIVERED&&c.issuerMine())return new ActionSpec(EconomyCommandPayload.Action.CONFIRM_CONTRACT,Component.translatable("gui.createcityexpansioncore.economy.confirm"),false);return null;}

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
    private void button(GuiGraphics g,int x,int y,int w,int h,Component label,boolean enabled,int mx,int my,boolean strong){boolean hover=enabled&&in(mx,my,x,y,w,h);int color=!enabled?0xFF282E30:strong?(hover?0xFF76562E:0xFF493A29):(hover?0xFF39464A:PANEL_ALT);g.fill(x,y,x+w,y+h,color);g.renderOutline(x,y,w,h,enabled?(strong?ACCENT:FRAME):0xFF394043);g.drawCenteredString(font,ellipsize(label,w-8),x+w/2,y+(h-8)/2,enabled?(strong?0xFFFFD59A:TEXT):0xFF687174);}
    private void iconButton(GuiGraphics g,int x,int y,ItemStack icon,int mx,int my,Component tip){boolean hover=in(mx,my,x,y,26,26);g.fill(x,y,x+26,y+26,hover?0xFF303A3D:PANEL_ALT);g.renderOutline(x,y,26,26,FRAME);g.renderItem(icon,x+5,y+5);if(hover)tooltip=tip;}
    private static void progress(GuiGraphics g,int x,int y,int w,long value,long max){g.fill(x,y,x+w,y+3,0xFF30383B);int filled=max<=0?0:(int)Math.min(w,Math.max(0,java.math.BigInteger.valueOf(value).multiply(java.math.BigInteger.valueOf(w)).divide(java.math.BigInteger.valueOf(max)).longValue()));g.fill(x,y,x+filled,y+3,ACCENT);}
    private static void plate(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,PANEL);g.renderOutline(x,y,w,h,FRAME);}

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

    private record Layout(int leftX,int leftW,int listX,int listW,int detailX,int detailW,int y,int h){}
    private record Pending(Component message,EconomyCommandPayload.Action action,UUID id,long amount){}
    private record ActionSpec(EconomyCommandPayload.Action action,Component label,boolean danger){}
    private record Activity(UUID id,ResourceLocation item,Component title,Component kind,Component state,int color,boolean terminal,long current,long total,long deadline,long bond){}
    private enum Tab{
        OVERVIEW("gui.createcityexpansioncore.market.tab.overview","minecraft:compass"),EXTERNAL("gui.createcityexpansioncore.market.tab.external","minecraft:emerald"),PLAYER("gui.createcityexpansioncore.market.tab.player","minecraft:player_head"),CONTRACTS("gui.createcityexpansioncore.market.tab.contracts","minecraft:writable_book"),ACTIVITY("gui.createcityexpansioncore.market.tab.activity","minecraft:clock");
        final String key;final ItemStack icon;Tab(String key,String icon){this.key=key;this.icon=item(icon);}
    }
}
