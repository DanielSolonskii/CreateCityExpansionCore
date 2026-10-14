package com.example.examplemod.client.screen;

import com.example.examplemod.client.status.CcecProblemCenter;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.EconomyCommandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import static com.example.examplemod.client.screen.CityPylonTheme.*;

/** Native content page. It never renders CityPylonScreen's frame, widgets or overlays. */
public final class CityPylonOverviewScreen extends CityPylonPageScreen {
    private final CityPylonScreen root;
    private final CityPylonMenu menu;
    private int ticks;
    private int view;
    public CityPylonOverviewScreen(CityPylonScreen root) {
        super(Component.translatable("gui.createcityexpansioncore.city.tab.overview"));
        this.root=root;this.menu=root.getMenu();
    }
    @Override public void tick() {
        if(++ticks%40==0)PacketDistributor.sendToServer(EconomyCommandPayload.simple(menu.getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));
    }
    private int problems() {return CcecProblemCenter.count(menu.getEconomyState(),menu.getRailwayOwnership(),menu.getRailwayTrains());}
    private Component tr(String key,Object...args) {return Component.translatable("gui.createcityexpansioncore.workspace."+key,args);}
    private Component hub(String key,Object...args) {return Component.translatable("gui.createcityexpansioncore.city.hub."+key,args);}
    private static boolean in(double x,double y,int left,int top,int w,int h) {return x>=left&&x<left+w&&y>=top&&y<top+h;}
    private void line(GuiGraphics g,Component text,int x,int y,int w,int color) {g.drawString(font,fit(font,text,w),x,y,color,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float pt) {
        g.fill(0,0,width,height,BG);
        root.workspaceSyncMapData();
        line(g,tr("overview.eyebrow"),0,2,width-86,BRASS_TEXT);
        line(g,title,0,19,width-86,TEXT);
        button(g,font,width-78,5,78,23,tr("overview.help"),false,in(mx,my,width-78,5,78,23));
        if(view!=0){renderDetail(g,mx,my);return;}
        int count=problems();
        rounded(g,0,43,width,43,6,DEEP);
        g.fill(0,49,2,80,count>0?0xFFB98C58:GREEN);
        line(g,count>0?hub("problem_count",count):hub("ready"),12,52,width-150,count>0?0xFFE0B58B:GREEN);
        line(g,Component.translatable(CcecProblemCenter.nextStepKey(menu.getEconomyState(),count)),12,68,width-150,MUTED);
        button(g,font,width-130,53,118,23,count>0?tr("overview.show_tasks"):tr("overview.trade"),count==0,in(mx,my,width-130,53,118,23));
        var economy=menu.getEconomyState();
        String treasury=economy==null?"—":economy.treasury()+" CR";
        long contracts=economy==null?0:economy.contracts().stream().filter(c->!c.state().terminal()).count();
        long shipments=economy==null?0:economy.shipments().size();
        long delivered=economy==null?0:economy.shipments().stream().filter(s->s.verified()>=s.declared()).count();
        String[] labels={"treasury","contracts","shipments","delivered","level","members"};
        String[] values={treasury,Long.toString(contracts),Long.toString(shipments),Long.toString(delivered),Integer.toString(menu.getCityLevel()),Integer.toString(menu.getMemberCount())};
        int cw=(width-2*GAP)/3;
        for(int i=0;i<6;i++) {
            int x=(i%3)*(cw+GAP),y=99+(i/3)*57,w=i%3==2?width-x:cw;
            panel(g,x,y,w,44);
            line(g,hub(labels[i]),x+11,y+9,w-22,MUTED);
            line(g,Component.literal(values[i]),x+11,y+25,w-22,TEXT);
        }
        int leftW=(width-GAP)/2,lower=213,cardH=height-lower-1,rightX=leftW+GAP,rightW=width-rightX;
        panel(g,0,lower,leftW,cardH);
        line(g,hub("members_list"),11,lower+11,leftW-22,TEXT);
        line(g,root.workspaceMemberNames(),11,lower+29,leftW-22,MUTED);
        button(g,font,11,height-34,leftW-22,23,tr("overview.manage"),false,in(mx,my,11,height-34,leftW-22,23));
        panel(g,rightX,lower,rightW,cardH);
        line(g,tr("overview.territory"),rightX+11,lower+11,rightW-22,TEXT);
        line(g,tr("overview.plots",menu.getPlotCount()),rightX+11,lower+27,rightW-22,MUTED);
        if(cardH>=116) {
            var map=root.getMapPanel();map.setDrawInternalControls(false);
            map.render(g,rightX+11,lower+43,rightW-22,cardH-84,-10000,-10000);
        }
        button(g,font,rightX+11,height-34,rightW-22,23,hub("open_map"),false,in(mx,my,rightX+11,height-34,rightW-22,23));
    }
    private void renderDetail(GuiGraphics g,int mx,int my) {
        panel(g,0,43,width,height-44);
        line(g,view==1?hub("problems"):hub("guide.title"),13,57,width-26,TEXT);
        Component text=view==1?Component.translatable(CcecProblemCenter.nextStepKey(menu.getEconomyState(),problems())):hub("guide.text");
        g.drawWordWrap(font,text,13,82,width-26,MUTED);
        if(view==1) {
            line(g,hub("problem_count",problems()),13,126,width-26,BRASS_TEXT);
            g.drawWordWrap(font,hub("problem_hint"),13,148,width-26,MUTED);
            button(g,font,width-169,height-36,156,23,hub("open_logistics"),true,in(mx,my,width-169,height-36,156,23));
        }
        button(g,font,13,height-36,94,23,Component.translatable("gui.createcityexpansioncore.common.back"),false,in(mx,my,13,height-36,94,23));
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button!=0)return false;
        if(in(x,y,width-78,5,78,23)){view=2;return true;}
        if(view!=0) {
            if(in(x,y,13,height-36,94,23)){view=0;return true;}
            if(view==1&&in(x,y,width-169,height-36,156,23)){minecraft.setScreen(new LogisticsScreen(root));return true;}
            return false;
        }
        if(in(x,y,width-130,53,118,23)) {
            if(problems()>0)view=1;else minecraft.setScreen(new MarketScreen(root));return true;
        }
        int leftW=(width-GAP)/2;
        if(in(x,y,11,height-34,leftW-22,23)){root.workspaceSelect(2);minecraft.setScreen(root);return true;}
        if(in(x,y,leftW+GAP+11,height-34,width-leftW-GAP-22,23)){minecraft.setScreen(new PolishedWorldMapScreen(root));return true;}
        return false;
    }
    @Override public void onClose() {if(view!=0)view=0;else root.onClose();}
}
