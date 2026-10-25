package com.example.examplemod.client.screen;
import com.example.examplemod.client.WorldMapDataSource;
import com.example.examplemod.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Same contracts and escrow, filtered by the optional project association. */
public final class ProjectSuppliesScreen extends CityPylonPageScreen {
    private final Screen parent;private final WorldMapDataSource source;private final UUID project;private final String name;
    private UUID selected;private int scroll,ticks;private Button create,open;
    public ProjectSuppliesScreen(Screen parent,WorldMapDataSource source,UUID project,String name){super(Component.translatable("gui.createcityexpansioncore.trade.supplies"));this.parent=parent;this.source=source;this.project=project;this.name=name;}
    private List<EconomyStatePayload.ContractView> orders(){var s=source.getEconomyState();if(s==null)return List.of();var ids=new HashSet<UUID>();for(var raw:s.logistics().getList("ProjectOrders",10)){var t=(net.minecraft.nbt.CompoundTag)raw;if(t.hasUUID("Project")&&t.getUUID("Project").equals(project))ids.add(t.getUUID("Contract"));}return s.contracts().stream().filter(c->ids.contains(c.id())).toList();}
    private int left(){return Math.max(10,(width-600)/2);}private int span(){return Math.min(600,width-20);}private int rows(){return Math.max(1,(height-132)/45);}
    @Override protected void init(){clearWidgets();int x=left(),w=span(),bw=(w-16)/3;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,height-32,bw,22).build());
        create=addRenderableWidget(Button.builder(Component.translatable("gui.createcityexpansioncore.trade.create_order"),b->minecraft.setScreen(new EconomyCreateScreen(this,EconomyCreateScreen.Mode.CITY_CONTRACT,source.getPylonPos(),source.getEconomyState()).forProject(project,name))).bounds(x+bw+8,height-32,bw,22).build());
        open=addRenderableWidget(Button.builder(Component.translatable("gui.createcityexpansioncore.trade.open_order"),b->minecraft.setScreen(new MarketScreen(this,source).focusContract(selected))).bounds(x+2*(bw+8),height-32,bw,22).build());
    }
    @Override public void tick(){if(++ticks%40==0)PacketDistributor.sendToServer(EconomyCommandPayload.simple(source.getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xFF141A1D);int x=left(),w=span();panel(g,x,12,w,height-56);
        g.drawString(font,title,x+14,25,ACCENT,false);g.drawString(font,fit(font,Component.literal(name),w-28),x+14,43,TEXT,false);
        var orders=orders();scroll=Math.clamp(scroll,0,Math.max(0,orders.size()-rows()));
        if(orders.isEmpty())g.drawWordWrap(font,Component.translatable("gui.createcityexpansioncore.trade.supplies_empty"),x+14,78,w-28,MUTED);
        for(int i=scroll;i<Math.min(orders.size(),scroll+rows());i++){var c=orders.get(i);int y=69+(i-scroll)*45;rounded(g,x+9,y,w-18,39,3,c.id().equals(selected)?0xFF3C3529:INSET);g.drawString(font,fit(font,Component.literal(c.title()),w-36),x+17,y+6,TEXT,false);g.drawString(font,Component.translatable("gui.createcityexpansioncore.trade.order_progress",c.delivered(),c.amount(),c.reward()),x+17,y+23,MUTED,false);}
        var s=source.getEconomyState();create.active=s!=null&&s.unlocked()&&s.logistics().getBoolean("CanCreateOrder");open.active=orders.stream().anyMatch(c->c.id().equals(selected));super.render(g,mx,my,dt);
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&x>=left()+9&&x<left()+span()-9&&y>=69&&y<69+rows()*45){int i=scroll+(int)(y-69)/45;var all=orders();if(i<all.size()){selected=all.get(i).id();return true;}}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.clamp(scroll+(dy<0?1:-1),0,Math.max(0,orders().size()-rows()));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
