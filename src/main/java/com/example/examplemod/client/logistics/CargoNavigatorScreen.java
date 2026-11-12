package com.example.examplemod.client.logistics;

import com.example.examplemod.client.map.MapLineClip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Reviews the server's immutable quote. Geometry is real rail samples, never a simulated train. */
public final class CargoNavigatorScreen extends Screen implements CenterResponseScreen {
    private final LogisticsCenterScreen center;
    private final CenterActionScreen form;
    private CompoundTag quote;
    private CargoNavigatorLayout layout;
    private ActionButton order;
    private final List<ActionButton> alternatives=new ArrayList<>();
    private double centerX,centerZ,zoom=1;
    private String dimension="";
    private int scroll,contentHeight;
    private boolean roadQuote(){return quote.hasUUID("PathFingerprint")&&quote.contains("Rate")&&!quote.contains("Goods");}

    public CargoNavigatorScreen(LogisticsCenterScreen center,CenterActionScreen form,CompoundTag quote){
        super(tr("navigator.title"));this.center=center;this.form=form;this.quote=quote.copy();
    }
    public void updateQuote(CompoundTag next){quote=next.copy();scroll=0;fit();rebuildWidgets();}
    @Override protected void init(){
        layout=CargoNavigatorLayout.of(width,height);alternatives.clear();fit();
        var b=layout.back();addRenderableWidget(new ActionButton(b.x(),b.y(),b.width(),b.height(),tr("back"),this::onClose));
        b=layout.worldMap();addRenderableWidget(new ActionButton(b.x(),b.y(),b.width(),b.height(),tr("navigator.atlas"),()->
            LogisticsMapOverlay.reviewRoute(center,this,quote.getCompound("MapValues"),quote.getCompound("MapPreview"))).hint(tr("navigator.atlas_hint")));
        b=layout.order();order=addRenderableWidget(new ActionButton(b.x(),b.y(),b.width(),b.height(),
            roadQuote()?tr("navigator.register_road"):quote.getBoolean("ExternalPayer")?tr("navigator.supply"):Component.literal(text("navigator.order")+" "+quote.getLong("Total")+" CR"),form::confirmPrepared).primary().hint(tr(roadQuote()?"navigator.road_hint":"navigator.reserve_hint")));
        var summary=layout.summary();int gap=5,w=(summary.width()-20-gap*2)/3;
        String[] preferences={"CHEAPEST","NO_TOLLS","FASTEST"};
        for(int i=0;!roadQuote()&&i<preferences.length;i++){
            String preference=preferences[i];
            var button=new ActionButton(summary.x()+10+i*(w+gap),summary.y()+8,w,23,tr("navigator."+preference),()->form.requestPreference(preference));
            button.hint(tr("navigator.hint."+preference));addRenderableWidget(button);alternatives.add(button);
        }
        updateControls();
    }
    private void updateControls(){
        if(order==null)return;order.active=form.canConfirmPrepared();
        for(int i=0;i<alternatives.size();i++){
            var button=alternatives.get(i);button.active=!form.busy();
            button.selected(quote.getCompound("MapValues").getString("Preference").equals(new String[]{"CHEAPEST","NO_TOLLS","FASTEST"}[i]));
        }
    }
    @Override public void tick(){super.tick();form.tick();updateControls();}
    @Override public void accept(CompoundTag response){form.accept(response);updateControls();}
    private List<CompoundTag> rails(){
        var result=new ArrayList<CompoundTag>();for(Tag raw:quote.getCompound("MapPreview").getList("Rails",10)){
            var rail=(CompoundTag)raw;if(dimension.isEmpty()||dimension.equals(rail.getString("Dimension")))result.add(rail);
        }return result;
    }
    private void fit(){
        if(layout==null)return;
        var all=quote.getCompound("MapPreview").getList("Rails",10);
        if(!all.isEmpty())dimension=all.getCompound(0).getString("Dimension");
        else if(minecraft!=null&&minecraft.player!=null)dimension=minecraft.player.level().dimension().location().toString();
        var selected=rails();boolean focused=selected.stream().anyMatch(r->r.getBoolean("Focus")||r.contains("Phase"));
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for(var rail:selected){if(focused&&!rail.getBoolean("Focus")&&!rail.contains("Phase"))continue;
            minX=Math.min(minX,Math.min(rail.getDouble("X"),rail.getDouble("ToX")));maxX=Math.max(maxX,Math.max(rail.getDouble("X"),rail.getDouble("ToX")));
            minZ=Math.min(minZ,Math.min(rail.getDouble("Z"),rail.getDouble("ToZ")));maxZ=Math.max(maxZ,Math.max(rail.getDouble("Z"),rail.getDouble("ToZ")));
        }
        if(!Double.isFinite(minX)){centerX=centerZ=0;zoom=1;return;}
        centerX=(minX+maxX)/2;centerZ=(minZ+maxZ)/2;
        zoom=Math.clamp(Math.min(Math.max(10,layout.map().width()-42)/Math.max(16,maxX-minX),Math.max(10,layout.map().height()-40)/Math.max(16,maxZ-minZ)),.0001,8);
    }
    private double sx(double x){return layout.map().x()+layout.map().width()/2d+(x-centerX)*zoom;}
    private double sy(double z){return layout.map().y()+layout.map().height()/2d+(z-centerZ)*zoom;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        background(g,width,height);var heading=roadQuote()?tr("navigator.road_title"):title;String endpoints=quote.getString("Source").isBlank()&&quote.getString("Destination").isBlank()?text("navigator.check_failed"):quote.getString("Source")+" → "+quote.getString("Destination")+(roadQuote()?"":" · ×"+quote.getInt("Quantity"));
        if(height<240){g.drawString(font,LogisticsSkin.fit(heading.getString(),width-24),12,9,TEAL,false);g.drawString(font,LogisticsSkin.fit(endpoints,width-24),12,25,MUTED,false);}
        else header(g,layout.map().x(),8,width-layout.map().x()*2,heading,Component.literal(endpoints),3);
        renderMap(g);renderSummary(g);super.render(g,mx,my,partial);
        if(!form.quoteError().isBlank())g.drawString(font,LogisticsSkin.fit(form.quoteError(),width-24),12,height-42,BRASS,false);
    }
    private void renderMap(GuiGraphics g){
        var m=layout.map();surface(g,m.x(),m.y(),m.width(),m.height(),PANEL);g.enableScissor(m.x()+2,m.y()+2,m.right()-2,m.bottom()-2);
        for(int x=m.x()+16;x<m.right();x+=32)g.vLine(x,m.y()+2,m.bottom()-2,0x152f5261);
        for(int y=m.y()+16;y<m.bottom();y+=32)g.hLine(m.x()+2,m.right()-2,y,0x152f5261);
        var rails=rails();
        for(var rail:rails){String phase=rail.getString("Phase");boolean toll=rail.getBoolean("Toll")||rail.hasUUID("Claim");
            int color=toll?BRASS:phase.equals("APPROACH")?0xff9caebb:phase.equals("RETURN")?0xff8c9ccd:rail.getBoolean("Focus")||!phase.isEmpty()?TEAL:EDGE;
            var line=MapLineClip.clip((int)sx(rail.getDouble("X")),(int)sy(rail.getDouble("Z")),(int)sx(rail.getDouble("ToX")),(int)sy(rail.getDouble("ToZ")),m.x()+3,m.y()+3,m.right()-3,m.bottom()-3);
            if(line==null)continue;int count=Math.max(Math.abs(line.x2()-line.x1()),Math.abs(line.y2()-line.y1()));
            for(int n=0;n<=count;n++){double t=count==0?0:n/(double)count;int x=(int)Math.round(line.x1()+(line.x2()-line.x1())*t),y=(int)Math.round(line.y1()+(line.y2()-line.y1())*t);g.fill(x,y,x+2,y+2,color);}
        }
        int index=0;for(Tag raw:quote.getCompound("MapPreview").getList("RouteStops",10)){
            var stop=(CompoundTag)raw;var loc=stop.getCompound("Location");if(!dimension.equals(loc.getString("Dimension")))continue;
            var p=net.minecraft.core.BlockPos.of(loc.getLong("Pos"));int x=(int)sx(p.getX()+.5),y=(int)sy(p.getZ()+.5);
            surface(g,x-8,y-8,17,17,BG);g.drawCenteredString(font,Integer.toString(++index),x,y-4,TEXT);
        }
        if(rails.isEmpty())g.drawWordWrap(font,tr("navigator.no_geometry"),m.x()+12,m.y()+12,Math.max(10,m.width()-24),MUTED);
        if(m.height()>70){surface(g,m.x()+8,m.bottom()-24,m.width()-16,18,BG);g.drawString(font,LogisticsSkin.fit(text("navigator.legend"),m.width()-28),m.x()+14,m.bottom()-19,MUTED,false);}
        g.disableScissor();
    }
    private void renderSummary(GuiGraphics g){
        var s=layout.summary();surface(g,s.x(),s.y(),s.width(),s.height(),PANEL);
        int top=s.y()+(roadQuote()?8:38),bottom=s.bottom()-5,y=top+Math.min(5,Math.max(0,(bottom-top-9)/2))-scroll;
        g.enableScissor(s.x()+5,top,s.right()-5,bottom);
        if(roadQuote()){
            y=wrapped(g,text("navigator.road_review"),y,TEAL,s);
            y=wrapped(g,text("navigator.road_rate")+": "+quote.getLong("Rate")+" CR",y+8,TEXT,s);
            y=wrapped(g,text("navigator.distance")+": "+Math.round(quote.getDouble("Distance"))+" "+text("navigator.blocks"),y+8,TEXT,s);
            y=wrapped(g,text("navigator.road_hint"),y+12,MUTED,s);contentHeight=y+scroll-top+8;scroll=Math.clamp(scroll,0,CenterPresentation.scrollLimit(contentHeight,bottom-top));g.disableScissor();return;
        }
        y=wrapped(g,text("navigator.estimate"),y,TEAL,s);
        if(quote.getBoolean("ExternalPayer"))y=wrapped(g,text("navigator.payer")+": "+quote.getString("PayerName"),y,BRASS,s);
        y=cost(g,"navigator.goods",quote.getLong("Goods"),y,s);
        if(quote.contains("CarrierCost")){y=cost(g,"navigator.carrier",quote.getLong("CarrierCost"),y,s);y=cost(g,"navigator.tolls",quote.getLong("Tolls"),y,s);}
        else y=cost(g,"navigator.transport",quote.getLong("Transport"),y,s);
        y=cost(g,"navigator.total",quote.getLong("Total"),y+5,s);
        if(quote.contains("Distance"))y=wrapped(g,text("navigator.distance")+": "+Math.round(quote.getDouble("Distance"))+" "+text("navigator.blocks"),y+8,MUTED,s);
        if(quote.getLong("EtaSeconds")>0)y=wrapped(g,text("navigator.eta")+": ~"+Math.max(1,(quote.getLong("EtaSeconds")+59)/60)+" "+text("navigator.minutes"),y,MUTED,s);
        var carriers=new LinkedHashSet<String>();
        if(!quote.getString("CarrierName").isBlank())carriers.add(quote.getString("CarrierName"));
        else for(Tag raw:quote.getList("Route",10)){var leg=(CompoundTag)raw;if(!leg.getString("Carrier").isBlank())carriers.add(leg.getString("Carrier"));}
        for(String carrier:carriers)y=wrapped(g,text("navigator.carrier")+": "+carrier,y+6,TEXT,s);
        if(!quote.getString("TrainName").isBlank())y=wrapped(g,text("navigator.train")+": "+quote.getString("TrainName"),y,TEXT,s);
        if(quote.getInt("Cycles")>0)y=wrapped(g,text("navigator.cycles")+": "+quote.getInt("Cycles"),y,MUTED,s);
        y=wrapped(g,text("navigator.readiness"),y+12,TEAL,s);
        var checks=quote.getList("Checks",10);
        for(Tag raw:checks){var check=(CompoundTag)raw;String status=check.getString("Status"),code=check.getString("Code");
            String key=check.getString("MessageKey");if(key.isBlank())key="ccec.center.navigator.check."+code;
            String label=Component.translatable(key).getString();if(label.equals(key))label=code;
            String glyph=status.equals("BLOCKED")?"× ":status.equals("WARNING")?"! ":"✓ ";
            y=wrapped(g,glyph+label,y,status.equals("BLOCKED")||status.equals("WARNING")?BRASS:TEXT,s);
            if(!check.getString("Detail").isBlank()){String detail=check.getString("Detail");if(detail.matches("(?:master|rail|fleet|company|delivery)_[a-z_]+.*"))detail=LogisticsSkin.result("denied: "+detail);y=wrapped(g,detail,y,MUTED,s);}
            String action=check.getString("ActionKey");if(!action.isBlank())y=wrapped(g,Component.translatable(action).getString(),y,MUTED,s);
        }
        if(checks.isEmpty())y=wrapped(g,text("navigator.rechecked"),y,MUTED,s);
        y=wrapped(g,text("navigator.reserve_hint"),y+12,MUTED,s);contentHeight=y+scroll-top+8;
        scroll=Math.clamp(scroll,0,CenterPresentation.scrollLimit(contentHeight,bottom-top));g.disableScissor();
        if(contentHeight>bottom-top){int h=Math.max(10,(bottom-top)*(bottom-top)/contentHeight),dy=top+(bottom-top-h)*scroll/Math.max(1,contentHeight-(bottom-top));g.fill(s.right()-4,dy,s.right()-2,dy+h,TEAL);}
    }
    private int cost(GuiGraphics g,String key,long amount,int y,CargoNavigatorLayout.Rect s){
        g.drawString(font,LogisticsSkin.fit(text(key),Math.max(1,s.width()-110)),s.x()+12,y,MUTED,false);
        var number=amount+" CR";g.drawString(font,number,s.right()-12-font.width(number),y,TEXT,false);return y+17;
    }
    private int wrapped(GuiGraphics g,String text,int y,int color,CargoNavigatorLayout.Rect s){var c=Component.literal(text);g.drawWordWrap(font,c,s.x()+12,y,Math.max(1,s.width()-24),color);return y+font.wordWrapHeight(c,Math.max(1,s.width()-24))+6;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(layout.map().contains(x,y)){double old=zoom;zoom=Math.clamp(zoom*Math.pow(1.2,vertical),.0001,30);centerX+=(x-layout.map().x()-layout.map().width()/2d)*(1/old-1/zoom);centerZ+=(y-layout.map().y()-layout.map().height()/2d)*(1/old-1/zoom);return true;}
        scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(contentHeight,layout.summary().height()-43),scroll-(int)(vertical*25)));return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&layout.map().contains(x,y)){centerX-=dx/zoom;centerZ-=dy/zoom;return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public void onClose(){minecraft.setScreen(form);}
    @Override public boolean isPauseScreen(){return false;}
}
