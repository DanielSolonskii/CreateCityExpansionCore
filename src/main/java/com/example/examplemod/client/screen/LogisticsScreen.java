package com.example.examplemod.client.screen;

import com.example.examplemod.client.WorldMapDataSource;
import com.example.examplemod.client.freight.*;
import com.example.examplemod.client.status.CcecStatusUi;
import com.example.examplemod.economy.*;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.*;
import com.example.examplemod.status.CcecStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Task-oriented workspace. List rows select; labelled controls perform validated actions. */
public final class LogisticsScreen extends CityPylonPageScreen {
    private enum View{SOURCE,LEGACY,LIST,DELIVERY,NODE,ROUTE,TRAIN,RENAME,REMOVE,REVOKE,BUDGET}
    private final Screen parent;private final WorldMapDataSource source;
    private EconomyStatePayload state;private LogisticsLayout layout;private View view=View.LIST;
    private int tab,filter,scroll,total,ticks;private UUID selected;private String draft="",error="";
    private boolean regional,pendingRoute;private long pendingAt,pendingRevision;
    private final List<UUID> hubs=new ArrayList<>();private EditBox field;
    private final List<Runnable> enabled=new ArrayList<>();private final List<RowHit> rows=new ArrayList<>();private boolean building;private int pointerX=-10000,pointerY=-10000;
    private record RowHit(LogisticsLayout.Rect bounds,Runnable run){}
    public LogisticsScreen(CityPylonScreen parent){this(parent,parent.getMenu(),null);}
    public LogisticsScreen(Screen parent,WorldMapDataSource source,UUID subject){super(tr("title"));this.parent=parent;this.source=source;if(subject!=null){selected=subject;view=View.DELIVERY;}}
    public static Component tr(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.logistics2."+key,args);}
    private List<CompoundTag> tags(CompoundTag root,String key){var result=new ArrayList<CompoundTag>();for(Tag raw:root.getList(key,10))result.add((CompoundTag)raw);return result;}
    private List<FreightPoint> points(){return FreightUi.points(state);}
    private FreightPoint point(UUID id){return points().stream().filter(p->p.id.equals(id)).findFirst().orElse(null);}
    private CompoundTag delivery(){return deliveries().stream().filter(a->a.getUUID("Subject").equals(selected)).findFirst().orElse(null);}
    private List<CompoundTag> deliveries(){return state==null?List.of():tags(state.logistics(),"Assignments");}
    private boolean local(){return state!=null&&state.logistics().getBoolean("CanManageNodes");}
    private boolean control(CompoundTag a){return state!=null&&state.logistics().getBoolean("CanDispatch")&&(a.getBoolean("OwnsDispatch")||!a.getBoolean("BuyerTransport")&&a.getBoolean("Outgoing"));}
    private boolean done(CompoundTag a){return Set.of("complete","expired").contains(a.getString("State"));}
    private CcecStatus status(CompoundTag a){return a.contains("UiStatus")?CcecStatus.load(a.getCompound("UiStatus")):CcecStatus.of(CcecStatus.Category.WARNING,CcecStatus.Code.SHIPMENT_INVALIDATED,CcecStatus.Action.FIX_PROBLEM);}
    private void request(){if(ticks%100==0)PacketDistributor.sendToServer(RailwayCommandPayload.simple(source.getPylonPos(),RailwayCommandPayload.Action.REQUEST_STATE,null,null));PacketDistributor.sendToServer(EconomyCommandPayload.simple(source.getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));}
    private void send(EconomyCommandPayload.Action action,UUID id,UUID second,long amount,int value,String title,String note){PacketDistributor.sendToServer(new EconomyCommandPayload(source.getPylonPos(),action,id,second,null,null,amount,0,0,value,title,"",note));request();}
    private void awaitMutation(EconomyCommandPayload.Action action,UUID id,UUID second,long amount,String note){pendingRevision=state.logistics().getLong("MutationRevision");pendingAt=net.minecraft.Util.getMillis();pendingRoute=true;send(action,id,second,amount,0,"",note);rebuild();}
    private void network(FreightNetworkRequest.Action action,UUID id,boolean flag,List<UUID> stops){PacketDistributor.sendToServer(new FreightNetworkRequest(source.getPylonPos(),action,id,flag,stops));request();}
    private void open(View next,UUID id){view=next;selected=id;scroll=0;error="";rebuild();}
    @Override protected void init(){layout=LogisticsLayout.of(width,height);state=source.getEconomyState();rebuild();request();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        ticks++;if(ticks%40==0)request();var update=source.getEconomyState();
        if(update!=state){state=update;for(var p:points())if(state!=null&&p.cityId.equals(state.cityId()))CcecStatusUi.observePoint(p.id,p.status);if(pendingRoute&&state!=null&&state.logistics().getLong("MutationRevision")>pendingRevision&&state.logistics().hasUUID("MutationTarget")&&state.logistics().getUUID("MutationTarget").equals(selected)){
            pendingRoute=false;error=state.logistics().getString("MutationResult");if(error.equals("ok"))view=View.DELIVERY;
        }rebuild();}
        if(pendingRoute&&net.minecraft.Util.getMillis()-pendingAt>8000){pendingRoute=false;error="timeout";rebuild();}
        var options=source.drainRouteOptions();if(options!=null&&view==View.TRAIN)minecraft.setScreen(new RailwayRouteSelectionScreen(this,options,option->{awaitMutation(EconomyCommandPayload.Action.SELECT_FREIGHT_ROUTE,selected,options.fleetTrainId(),0,option.id().toString());}));
    }
    private void rebuild(){
        if(layout==null||font==null)return;boolean focused=field!=null&&field.isFocused();int cursor=field==null?0:field.getCursorPosition();clearWidgets();field=null;rows.clear();enabled.clear();
        for(int i=0;i<3;i++){final int index=i;button(layout.tab(i),tr(new String[]{"deliveries","nodes","network"}[i]),tab==i,()->true,()->{tab=index;selected=null;view=View.LIST;scroll=0;rebuild();});}
        button(new LogisticsLayout.Rect(layout.right()-78,1,78,22),tr("help"),false,()->true,()->FreightPonderPlugin.open(minecraft.screen));
        building=true;total=content(null);building=false;int next=layout.clampScroll(scroll,total);if(next!=scroll){scroll=next;rebuild();return;}
        var action=primary();button(layout.footerButton(0,3),tr("back"),false,()->!pendingRoute,this::onClose);
        button(layout.footerButton(1,3),tr(view==View.LIST?"market":"on_map"),false,()->state!=null,()->{if(view==View.LIST)minecraft.setScreen(new MarketScreen(this,source));else onMap();});
        button(layout.footerButton(2,3),action.label,true,action.enabled,action.run);
        if(focused&&field!=null){setInitialFocus(field);field.moveCursorTo(Math.min(cursor,draft.length()),false);}
    }
    private void button(LogisticsLayout.Rect r,Component label,boolean primary,java.util.function.BooleanSupplier can,Runnable run){var b=addRenderableWidget(new LogisticsStyle.Action(r.x(),r.y(),r.width(),label,primary,run));b.setTooltip(Tooltip.create(label));b.active=can.getAsBoolean()&&!pendingRoute;enabled.add(()->b.active=can.getAsBoolean()&&!pendingRoute);}
    private void button(int x,int y,int w,Component label,boolean primary,boolean can,Runnable run){if(building&&y>=layout.top()&&y+22<=layout.bottom())button(new LogisticsLayout.Rect(x,y,w,22),label,primary,()->can,run);}
    private int text(GuiGraphics g,Component text,int x,int y,int w,int color){var lines=font.split(text,Math.max(20,w));if(g!=null)for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),x,y+i*12,color,false);return lines.size()*12;}
    private int heading(GuiGraphics g,Component title,Component subtitle,int y){y+=text(g,title,layout.left()+12,y,layout.width()-24,TEXT)+7;return y+text(g,subtitle,layout.left()+12,y,layout.width()-24,MUTED)+14;}
    private void row(GuiGraphics g,int y,int h,ItemStack icon,Component title,Component subtitle,int color,Runnable run){int x=layout.left(),w=layout.width();if(g!=null){rounded(g,x,y,w,h,4,layout.contains(pointerX,pointerY)&&pointerX>=x&&pointerX<x+w&&pointerY>=y&&pointerY<y+h?RAISED:INSET);g.fill(x+1,y+7,x+3,y+h-7,color);g.renderItem(icon,x+10,y+11);g.drawString(font,fit(font,title,w-53),x+34,y+11,TEXT,false);g.drawString(font,fit(font,subtitle,w-46),x+12,y+32,MUTED,false);}if(building)rows.add(new RowHit(new LogisticsLayout.Rect(x,y,w,h),run));}
    private int content(GuiGraphics g){int start=layout.top()-scroll,y=start;var a=delivery();if(state==null)return heading(g,tr("loading"),tr("loading_help"),y)-start;
        if(!error.isBlank()&&!error.equals("ok"))y=heading(g,tr("attention"),Component.translatable("gui.createcityexpansioncore.freight_network.result."+error),y);
        if(view==View.LEGACY)y=sourceList(g,y,null);
        else if(view==View.LIST){if(tab==0)y=deliveryList(g,y);else if(tab==1)y=nodeList(g,y);else y=networkList(g,y);}
        else if(view==View.NODE||view==View.RENAME||view==View.REMOVE)y=node(g,y);
        else if(a==null)y=heading(g,tr("missing"),tr("missing_help"),y);
        else if(view==View.SOURCE)y=sourceList(g,y,a);
        else if(view==View.ROUTE)y=route(g,y,a);
        else if(view==View.TRAIN)y=trains(g,y,a);
        else if(view==View.BUDGET){y=heading(g,tr("budget"),tr("budget_help"),y);input(y,true);y+=35;y+=text(g,tr("budget_used",a.getLong("RoadSpent"),a.getLong("RoadReserved")),layout.left()+12,y,layout.width()-24,MUTED)+12;}
        else if(view==View.REVOKE)y=heading(g,tr("cancel_order"),Component.translatable("gui.createcityexpansioncore.freight.console.revoke_help"),y);
        else y=shipment(g,y,a);
        return y-start+12;
    }
    private int deliveryList(GuiGraphics g,int y){
        y=heading(g,tr("deliveries"),tr("delivery_list_help"),y);
        for(var legacy:tags(state.logistics(),"Legacy")){UUID subject=legacy.getUUID("Subject");button(layout.left(),y,layout.width(),tr("restore_delivery"),false,state.logistics().getBoolean("CanCreateOrder"),()->open(View.LEGACY,subject));y+=30;}
        button(layout.left(),y,layout.width(),tr("filter."+filter),false,true,()->{filter=(filter+1)%4;scroll=0;rebuild();});y+=30;
        var list=deliveries().stream().filter(a->filter==3?done(a):!done(a)).filter(a->filter!=1||a.getBoolean("Outgoing")).filter(a->filter!=2||!a.getBoolean("Outgoing")).sorted(Comparator.comparing((CompoundTag a)->status(a).category()==CcecStatus.Category.SUCCESS)).toList();
        if(list.isEmpty())return heading(g,tr("empty_deliveries"),tr("first_delivery"),y);
        for(var a:list){UUID id=a.getUUID("Subject");var s=point(a.hasUUID("Source")?a.getUUID("Source"):null);var d=point(a.getUUID("Destination"));var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(a.getString("Item")));
            var title=Component.literal(a.getString("Title").isBlank()?new ItemStack(item).getHoverName().getString():a.getString("Title"));
            row(g,y,70,new ItemStack(item),title,Component.literal(name(s)+" → "+name(d)),ProjectMapStyle.color(com.example.examplemod.projects.Project.Status.OPEN),()->open(View.DELIVERY,id));
            if(g!=null){g.drawString(font,fit(font,phase(a),layout.width()-24),layout.left()+12,y+51,ACCENT,false);}y+=78;
        }return y;
    }
    private int nodeList(GuiGraphics g,int y){y=heading(g,tr("nodes"),tr(local()?"nodes_help":"pylon_required"),y);var own=points().stream().filter(p->p.cityId.equals(state.cityId())&&!p.removed).toList();if(own.isEmpty())return heading(g,tr("first_terminal"),tr("first_terminal_help"),y);
        for(var p:own){row(g,y,63,new ItemStack(p.kind==FreightPoint.Kind.HUB?Items.BARREL:Items.CHEST),Component.literal(p.name),tr(p.kind==FreightPoint.Kind.HUB?"hub":"terminal"),p.online()?0xff83d5b3:ACCENT,()->open(View.NODE,p.id));if(g!=null)g.drawString(font,fit(font,FreightUi.tr(p.status),layout.width()/2),layout.left()+12,y+48,MUTED,false);y+=71;}return y;}
    private int registrations(GuiGraphics g,int y){
        y=heading(g,tr("register_train"),tr(local()?"register_help":"pylon_required"),y);
        for(var c:source.getRailwayTrainCandidates()){row(g,y,62,new ItemStack(Items.MINECART),Component.literal(c.name()),Component.literal(c.stationName()),ACCENT,()->{});button(layout.left(),y+34,layout.width(),tr("register_train"),false,local()&&c.canManage(),()->{PacketDistributor.sendToServer(RailwayCommandPayload.simple(source.getPylonPos(),RailwayCommandPayload.Action.REGISTER_TRAIN,c.createTrainId(),null));request();});y+=70;}return y;
    }
    private int networkList(GuiGraphics g,int y){y=registrations(g,y);y=heading(g,tr("network"),tr("network_help"),y);y=heading(g,tr("trade_fleet"),tr("publish_help"),y);for(var t:tags(state.logistics().getCompound("Carriers"),"Fleet")){UUID id=t.getUUID("Id");row(g,y,58,new ItemStack(Items.MINECART),Component.literal(t.getString("Name")),tr(t.getBoolean("Published")?"published":"private_train"),ACCENT,()->{});button(layout.left()+layout.width()-122,y+29,112,tr(t.getBoolean("Published")?"unpublish":"publish"),false,state.logistics().getBoolean("CanCreateOrder")&&!t.getBoolean("Busy"),()->send(EconomyCommandPayload.Action.PUBLISH_FREIGHT_TRAIN,id,null,0,t.getBoolean("Published")?0:1,"",""));y+=66;}
        for(var p:points())if(p.kind==FreightPoint.Kind.HUB&&!p.removed){row(g,y,60,new ItemStack(Items.BARREL),Component.literal(p.name),tr(p.publicTransit?"public_hub":"private_hub"),ACCENT,()->{if(p.cityId.equals(state.cityId()))open(View.NODE,p.id);});y+=68;}return y;}
    private int shipment(GuiGraphics g,int y,CompoundTag a){
        y=heading(g,Component.literal(a.getString("Title").isBlank()?FreightUi.tr("cargo",a.getLong("Amount"),new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(a.getString("Item")))).getHoverName()).getString():a.getString("Title")),phase(a),y);
        var from=point(a.hasUUID("Source")?a.getUUID("Source"):null);var to=point(a.getUUID("Destination"));
        y+=text(g,tr("from",name(from)),layout.left()+12,y,layout.width()-24,TEXT)+9;
        if(a.getBoolean("Regional"))for(var hop:tags(a,"Via"))y+=text(g,Component.literal("  ◇ "+name(point(hop.getUUID("Id")))),layout.left()+12,y,layout.width()-24,ACCENT)+9;
        y+=text(g,tr("to",name(to)),layout.left()+12,y,layout.width()-24,TEXT)+12;
        long amount=Math.max(1,a.getLong("Amount")),delivered=a.getLong("Delivered");if(g!=null){g.fill(layout.left()+12,y,layout.right()-12,y+5,EDGE);g.fill(layout.left()+12,y,layout.left()+12+(int)((layout.width()-24)*Math.clamp(delivered/(double)amount,0,1)),y+5,0xff83d5b3);}y+=15;
        y+=text(g,tr("receipt",delivered,amount,a.getLong("InFlight")),layout.left()+12,y,layout.width()-24,MUTED)+12;
        if(!done(a)){y=heading(g,tr("next_step"),nextHint(a),y);}
        int bw=(layout.width()-6)/2;button(layout.left(),y,bw,tr(a.getBoolean("Regional")?"regional":"direct"),false,control(a)&&!a.getBoolean("RouteLocked"),()->{regional=a.getBoolean("Regional");hubs.clear();for(var h:tags(a,"Via"))hubs.add(h.getUUID("Id"));open(View.ROUTE,selected);});
        button(layout.left()+bw+6,y,bw,tr("equipment"),false,from!=null,()->open(View.NODE,from.id));y+=32;
        if(a.getBoolean("BuyerTransport")){y+=text(g,tr("road_summary",a.getLong("RoadSpent"),a.getLong("RoadReserved"),a.getLong("RoadLimit")),layout.left()+12,y,layout.width()-24,MUTED)+10;button(layout.left(),y,layout.width(),tr("budget"),false,control(a)&&state.logistics().getBoolean("CanCreateOrder"),()->{draft=Long.toString(a.getLong("RoadLimit"));open(View.BUDGET,selected);});y+=30;}
        y+=text(g,tr("goods",a.getLong("Reward")),layout.left()+12,y,layout.width()-24,MUTED)+12;
        var order=state.contracts().stream().filter(c->c.id().equals(a.getUUID("Subject"))).findFirst().orElse(null);
        if(order!=null&&order.kind()==EconomyTypes.ContractKind.ORDINARY){long fee=EconomyMath.applyBasisPoints(order.reward(),state.cityFeeBasisPoints());y+=text(g,tr("goods_fee",fee),layout.left()+12,y,layout.width()-24,MUTED)+12;}
        y+=text(g,tr("settlement"),layout.left()+12,y,layout.width()-24,MUTED)+12;
        if(a.hasUUID("Train")){y+=text(g,tr("assigned_train",a.getString("TrainName")),layout.left()+12,y,layout.width()-24,TEXT)+10;}
        button(layout.left(),y,layout.width(),tr("choose_train"),false,control(a)&&!a.getBoolean("Regional")&&!a.getBoolean("Running"),()->open(View.TRAIN,selected));y+=30;
        button(layout.left(),y,layout.width(),tr("cancel_order"),false,state.logistics().getBoolean("CanDispatch")&&a.getBoolean("Revocable"),()->open(View.REVOKE,selected));return y+32;
    }
    private Component phase(CompoundTag a){if(done(a))return tr("complete");if(a.getBoolean("BuyerTransport")&&!a.getString("DispatchError").isBlank())return tr("phase."+a.getString("DispatchError"));if(a.getBoolean("BuyerTransport")&&!a.getBoolean("Regional")){return tr("phase."+(a.getBoolean("Running")?a.getString("TransportPhase"):"paused"));}return CcecStatusUi.title(status(a));}
    private Component nextHint(CompoundTag a){if(!control(a))return tr("receiver_help");if(!a.hasUUID("Source"))return tr("choose_source_help");if(a.getBoolean("Regional"))return tr("regional_help");if(!a.hasUUID("Train"))return tr("choose_train_help");if(a.getBoolean("BuyerTransport")&&a.getString("DispatchError").equals("transport_budget"))return tr("budget_help");return a.getBoolean("Running")?tr("running_help"):tr("start_help");}
    private int node(GuiGraphics g,int y){var p=point(selected);if(p==null)return heading(g,tr("missing"),tr("missing_help"),y);y=heading(g,Component.literal(p.name),FreightUi.tr(p.status),y);
        if(view==View.RENAME){input(y,false);return y+35;}if(view==View.REMOVE)return heading(g,tr("remove"),tr("remove_help"),y);
        if(p.kind==FreightPoint.Kind.HUB){for(var hub:tags(state.logistics().getCompound("Transit"),"Hubs"))if(hub.getUUID("Id").equals(p.id)){y+=text(g,tr("hub_load",hub.getInt("Packages"),hub.getInt("Capacity")),layout.left()+12,y,layout.width()-24,ACCENT)+12;y+=text(g,Component.translatable("gui.createcityexpansioncore.freight_network.status."+hub.getString("Status")),layout.left()+12,y,layout.width()-24,MUTED)+12;}}
        y+=text(g,tr(p.kind==FreightPoint.Kind.HUB?"hub_help":"terminal_help"),layout.left()+12,y,layout.width()-24,MUTED)+12;
        y+=text(g,FreightUi.tr("station",p.stationName),layout.left()+12,y,layout.width()-24,TEXT)+12;
        button(layout.left(),y,layout.width(),tr("inspect"),false,local(),()->FreightGuideClient.begin(source.getPylonPos(),p.id));y+=30;
        button(layout.left(),y,layout.width(),tr("repair"),false,local()&&p.kind!=FreightPoint.Kind.HUB,()->FreightGuideClient.begin(source.getPylonPos(),p.id,true));y+=30;
        if(p.mode.receives()){button(layout.left(),y,layout.width(),tr(p.defaultReceive?"default_receiver":"make_default"),false,local()&&!p.defaultReceive,()->send(EconomyCommandPayload.Action.MANAGE_FREIGHT,p.id,null,0,0,"","default"));y+=30;}
        button(layout.left(),y,layout.width(),tr(p.kind==FreightPoint.Kind.HUB?"make_terminal":"make_hub"),false,local(),()->network(FreightNetworkRequest.Action.KIND,p.id,p.kind!=FreightPoint.Kind.HUB,List.of()));y+=30;
        if(p.kind==FreightPoint.Kind.HUB){button(layout.left(),y,layout.width(),tr(p.publicTransit?"close_hub":"open_hub"),false,local(),()->network(FreightNetworkRequest.Action.PUBLIC,p.id,!p.publicTransit,List.of()));y+=30;button(layout.left(),y,layout.width(),tr(p.forwarding?"pause_forward":"resume_forward"),false,local(),()->network(FreightNetworkRequest.Action.FORWARD,p.id,!p.forwarding,List.of()));y+=30;}
        button(layout.left(),y,layout.width(),tr("rename"),false,local(),()->{draft=p.name;open(View.RENAME,p.id);});y+=30;button(layout.left(),y,layout.width(),tr(p.enabled?"disable":"enable"),false,local(),()->send(EconomyCommandPayload.Action.MANAGE_FREIGHT,p.id,null,0,0,"","toggle"));y+=30;button(layout.left(),y,layout.width(),tr("remove"),false,local(),()->open(View.REMOVE,p.id));y+=35;
        if(!local())y+=text(g,tr("pylon_required"),layout.left()+12,y,layout.width()-24,MUTED)+12;
        y+=text(g,tr("equipment_path"),layout.left()+12,y,layout.width()-24,ACCENT)+12;return y;
    }
    private int sourceList(GuiGraphics g,int y,CompoundTag a){y=heading(g,tr(a==null?"choose_receiver":"choose_source"),tr("source_help"),y);
        for(var p:points())if(p.cityId.equals(state.cityId())&&p.enabled&&!p.removed&&(a==null?p.mode.receives():p.mode.sends())){UUID subject=selected;row(g,y,62,new ItemStack(Items.CHEST),Component.literal(p.name),FreightUi.tr(p.status),ACCENT,()->{});button(layout.left(),y+33,layout.width(),tr("select"),false,p.online(),()->{send(a==null?EconomyCommandPayload.Action.SETUP_FREIGHT:EconomyCommandPayload.Action.CONTROL_DISPATCH,subject,p.id,0,0,"","");open(View.DELIVERY,subject);});y+=70;}return y;}
    private int trains(GuiGraphics g,int y,CompoundTag a){y=heading(g,tr("choose_train"),tr("train_help"),y);for(var train:tags(a,"TrainCandidates")){var id=train.getUUID("Id");row(g,y,65,new ItemStack(Items.MINECART),Component.literal(train.getString("Name")),tr(train.getBoolean("Own")?"own_train":"seller_train"),train.getBoolean("Available")?ACCENT:MUTED,()->{});button(layout.left(),y+35,layout.width(),tr(train.getBoolean("Available")?"quote_route":"train_unavailable"),false,train.getBoolean("Available"),()->send(EconomyCommandPayload.Action.REQUEST_FREIGHT_ROUTES,selected,id,0,0,"",""));y+=74;}return registrations(g,y);}
    private int route(GuiGraphics g,int y,CompoundTag a){y=heading(g,tr("route"),tr("route_help"),y);int bw=(layout.width()-6)/2;button(layout.left(),y,bw,tr("direct"),!regional,true,()->{regional=false;rebuild();});button(layout.left()+bw+6,y,bw,tr("regional"),regional,true,()->{regional=true;rebuild();});y+=32;
        if(regional){y+=text(g,tr("regional_help"),layout.left()+12,y,layout.width()-24,MUTED)+12;
            for(int i=0;i<hubs.size();i++){final int pos=i;var p=point(hubs.get(i));y+=text(g,Component.literal((i+1)+" · "+name(p)),layout.left()+12,y,layout.width()-24,TEXT)+5;int w=(layout.width()-10)/3;button(layout.left(),y,w,tr("up"),false,i>0,()->{Collections.swap(hubs,pos,pos-1);rebuild();});button(layout.left()+w+5,y,w,tr("down"),false,i<hubs.size()-1,()->{Collections.swap(hubs,pos,pos+1);rebuild();});button(layout.left()+2*(w+5),y,w,tr("remove_stop"),false,true,()->{hubs.remove(pos);rebuild();});y+=30;}
            for(var p:points())if(p.kind==FreightPoint.Kind.HUB&&!p.removed&&p.enabled&&(p.cityId.equals(state.cityId())||p.publicTransit)&&!hubs.contains(p.id)&&!p.id.equals(a.getUUID("Destination"))&&(!a.hasUUID("Source")||!p.id.equals(a.getUUID("Source")))){button(layout.left(),y,layout.width(),tr("add_stop",p.name),false,hubs.size()<4,()->{hubs.add(p.id);rebuild();});y+=30;}
        }return y;
    }
    private void input(int y,boolean numeric){if(building&&y>=layout.top()&&y+22<=layout.bottom()){field=new EditBox(font,layout.left()+12,y,layout.width()-24,22,tr("budget"));field.setMaxLength(numeric?18:64);if(numeric)field.setFilter(v->v.isBlank()||v.chars().allMatch(Character::isDigit));field.setValue(draft);field.setResponder(v->draft=v);addRenderableWidget(field);}}
    private record Primary(Component label,java.util.function.BooleanSupplier enabled,Runnable run){}
    private Primary primary(){var a=delivery();var p=point(selected);if(state==null)return new Primary(tr("loading"),()->false,()->{});
        if(view==View.ROUTE)return new Primary(tr(pendingRoute?"saving":"save_route"),()->a!=null&&control(a)&&!a.getBoolean("RouteLocked"),()->{pendingRevision=state.logistics().getLong("MutationRevision");pendingAt=net.minecraft.Util.getMillis();pendingRoute=true;network(FreightNetworkRequest.Action.ROUTE,selected,regional,List.copyOf(hubs));rebuild();});
        if(view==View.BUDGET)return new Primary(tr("save_budget"),()->!draft.isBlank(),()->{try{awaitMutation(EconomyCommandPayload.Action.FREIGHT_BUDGET,selected,null,Long.parseLong(draft),"");}catch(NumberFormatException e){error="invalid";rebuild();}});
        if(view==View.REVOKE)return new Primary(tr("confirm_cancel"),()->a!=null&&a.getBoolean("Revocable"),()->{send(EconomyCommandPayload.Action.REVOKE_FREIGHT,selected,null,0,0,"","");open(View.DELIVERY,selected);});
        if(view==View.RENAME)return new Primary(tr("save"),()->local()&&!draft.isBlank(),()->{send(EconomyCommandPayload.Action.MANAGE_FREIGHT,selected,null,0,0,draft,"rename");open(View.NODE,selected);});
        if(view==View.REMOVE)return new Primary(tr("confirm_remove"),()->local(),()->{send(EconomyCommandPayload.Action.MANAGE_FREIGHT,selected,null,0,0,"","remove");open(View.LIST,null);});
        if(view==View.DELIVERY&&a!=null&&!done(a)){
            if(!a.hasUUID("Source")&&a.getBoolean("BuyerTransport"))return new Primary(tr("waiting_supplier"),()->false,()->{});
            if(!a.hasUUID("Source"))return new Primary(tr("choose_source"),()->control(a),()->open(View.SOURCE,selected));
            if(!a.hasUUID("Train")&&!a.getBoolean("Regional"))return new Primary(tr("choose_train"),()->control(a),()->open(View.TRAIN,selected));
            if(a.getBoolean("BuyerTransport")&&a.getString("DispatchError").equals("transport_budget"))return new Primary(tr("budget"),()->control(a)&&state.logistics().getBoolean("CanCreateOrder"),()->{draft=Long.toString(a.getLong("RoadLimit"));open(View.BUDGET,selected);});
            return new Primary(tr(a.getBoolean("Running")?"pause":"start"),()->control(a)&&a.hasUUID("Source"),()->send(EconomyCommandPayload.Action.CONTROL_DISPATCH,selected,null,0,a.getBoolean("Running")?0:1,"",""));
        }
        return new Primary(tr(tab==1?"create_terminal":"market"),()->tab!=1||local(),()->{if(tab==1)FreightGuideClient.begin(source.getPylonPos(),null);else minecraft.setScreen(new MarketScreen(this,source));});
    }
    private String name(FreightPoint p){return p==null?tr("not_selected").getString():p.name;}
    private void onMap(){var p=view==View.NODE?point(selected):delivery()!=null?point(delivery().getUUID("Destination")):null;if(p!=null)minecraft.setScreen(new PolishedWorldMapScreen(source).freightFocus(this,p.dimension,p.postbox));}
    @Override public void onClose(){if(pendingRoute){pendingRoute=false;}if(view!=View.LIST){view=View.LIST;selected=null;scroll=0;rebuild();}else minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){pointerX=mx;pointerY=my;g.fill(0,0,width,height,0xFF141A1D);g.drawString(font,fit(font,title,layout.width()-90),layout.left(),12,ACCENT,false);CityPylonWorkspace.clip(g,layout.left(),layout.top(),layout.right(),layout.bottom());content(g);g.disableScissor();for(var check:enabled)check.run();super.render(g,mx,my,pt);}
    @Override public boolean mouseClicked(double x,double y,int b){if(super.mouseClicked(x,y,b))return true;if(b==0&&layout.contains(x,y))for(var hit:rows)if(hit.bounds.contains(x,y)){minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),.25f,1f);hit.run.run();return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!layout.contains(x,y)||dy==0)return false;scroll=layout.clampScroll(scroll+(dy<0?1:-1)*Math.min(28,Math.max(1,layout.viewport()-22)),total);rebuild();return true;}
}
