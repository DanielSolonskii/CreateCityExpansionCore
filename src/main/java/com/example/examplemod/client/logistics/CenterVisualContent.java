package com.example.examplemod.client.logistics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.example.examplemod.client.CommunityFx;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Read-only presentation of server snapshots; slots never transfer or create items. */
final class CenterVisualContent {
    private CenterVisualContent() {}
    static int render(GuiGraphics g,CompoundTag row,int x,int y,int w){
        return render(g,row,x,y,w,0,Integer.MIN_VALUE,Integer.MIN_VALUE);
    }
    static int render(GuiGraphics g,CompoundTag row,int x,int y,int w,long opened,int mx,int my){
        var font=Minecraft.getInstance().font;
        String kind=row.getString("Kind");
        if(kind.equals("warehouse")){
            g.drawString(font,text("visual.stock"),x,y,TEAL,false);y+=18;
            g.drawWordWrap(font,tr("visual.stock_help"),x,y,w,MUTED);y+=font.wordWrapHeight(tr("visual.stock_help"),w)+12;
            if(!row.getBoolean("StockAvailable")){g.drawWordWrap(font,tr("stock_unavailable"),x,y,w,BRASS);return y+font.wordWrapHeight(tr("stock_unavailable"),w)+16;}
            var items=row.getList("Stock",10);int columns=Math.max(1,w/44);
            if(items.isEmpty()){g.drawString(font,text("visual.stock_empty"),x,y,MUTED,false);y+=22;}
            for(int i=0;i<items.size();i++){
                var item=items.getCompound(i);int sx=x+i%columns*44,sy=y+i/columns*40;
                boolean hovered=mx>=sx&&mx<sx+40&&my>=sy&&my<sy+36;
                surface(g,sx,sy,40,36,hovered?0xff284544:0xff1a2937);
                if(hovered)g.hLine(sx+3,sx+36,sy+35,TEAL);
                var stack=stack(item);if(!stack.isEmpty())g.renderItem(stack,sx+12,sy+3);
                else g.drawString(font,"?",sx+16,sy+6,MUTED,false);
                g.drawCenteredString(font,fit(Integer.toString(item.getInt("Count")),36),sx+20,sy+24,TEXT);
            }
            y+=((items.size()+columns-1)/columns)*40+12;
            if(row.getBoolean("StockLimited")){g.drawWordWrap(font,tr("visual.stock_limited"),x,y,w,BRASS);y+=font.wordWrapHeight(tr("visual.stock_limited"),w)+12;}
            return y;
        }
        if(kind.equals("shipment")){
            g.drawString(font,text("visual.journey"),x,y,TEAL,false);y+=18;
            int index=0;boolean flowing=java.util.Set.of("REQUESTED","PACKAGED","IN_TRANSIT","TRANSSHIPMENT").contains(row.getString("Status"));
            y=node(g,x,y,w,row.getString("SourceName"),text("visual.source"),5,false,opened,index++,flowing);
            for(Tag raw:row.getList("RouteNodes",10))y=node(g,x,y,w,((CompoundTag)raw).getString("Name"),text("visual.terminal"),4,false,opened,index++,flowing);
            y=node(g,x,y,w,row.getString("DestinationName"),text("visual.destination"),5,true,opened,index,flowing);
            g.drawWordWrap(font,Component.literal(value(row.getString("Status"))),x,y,w,TEAL);y+=font.wordWrapHeight(Component.literal(value(row.getString("Status"))),w)+14;
            int total=row.getInt("Quantity"),delivered=row.getInt("Delivered");
            g.fill(x,y,x+w,y+6,EDGE);
            int barWidth=total>0?(int)(w*Math.clamp(delivered/(double)total,0,1)):0;
            if(barWidth>0){g.fill(x,y,x+barWidth,y+6,TEAL);g.hLine(x,x+barWidth-1,y,0xffb1f1de);}y+=14;
            var progress=Component.translatable("ccec.center.visual.progress",row.getInt("Packaged"),delivered,total);
            g.drawWordWrap(font,progress,x,y,w,TEXT);y+=font.wordWrapHeight(progress,w)+14;
            String step=switch(row.getString("Status")){case "REQUESTED"->"requested";case "PACKAGED"->"packaged";case "IN_TRANSIT","TRANSSHIPMENT"->"transit";case "ARRIVED","DELIVERED"->"arrived";case "COMPLETED"->"completed";case "CANCELLED"->"cancelled";default->"attention";};
            var next=tr("visual.next."+step);g.drawWordWrap(font,next,x,y,w,MUTED);y+=font.wordWrapHeight(next,w)+18;
        }else if(kind.equals("line")){
            g.drawString(font,text("visual.network"),x,y,TEAL,false);y+=18;
            y=renderLine(g,row,x,y,w,opened);
        }
        return y;
    }
    static int lineHeight(CompoundTag row,int w){
        var nodes=row.getList("Details",10);
        if(nodes.isEmpty())return Math.max(36,Minecraft.getInstance().font.wordWrapHeight(tr("ui.network_empty"),Math.max(24,w-30))+12);
        int height=0;for(Tag raw:nodes)height+=nodeHeight(name((CompoundTag)raw),w);return height;
    }
    static int renderLine(GuiGraphics g,CompoundTag row,int x,int y,int w,long opened){
        var nodes=row.getList("Details",10);
        if(nodes.isEmpty()){g.drawWordWrap(Minecraft.getInstance().font,tr("ui.network_empty"),x+30,y,Math.max(24,w-30),MUTED);return y+lineHeight(row,w);}
        for(int i=0;i<nodes.size();i++){
            var part=nodes.getCompound(i);
            y=node(g,x,y,w,name(part),value(part.getString("Status")),4,i==nodes.size()-1,opened,i,!CenterPresentation.problem(row.getString("Status")));
        }
        return y;
    }
    private static int nodeHeight(String title,int w){
        var component=Component.literal(title.isBlank()?text("missing_name"):title);
        return Math.max(44,Minecraft.getInstance().font.wordWrapHeight(component,Math.max(24,w-30))+29);
    }
    private static int node(GuiGraphics g,int x,int y,int w,String title,String label,int symbol,boolean last,long opened,int index,boolean flowing){
        var font=Minecraft.getInstance().font;int height=nodeHeight(title,w);
        long now=net.minecraft.Util.getMillis();float reveal=opened==0||reducedMotion()?1:CommunityFx.ease((now-opened-index*45)/260d);
        surface(g,x,y,23,24,0xff1b303a);icon(g,x+5,y+5,symbol,TEAL);
        if(!last){
            g.vLine(x+11,y+25,y+height-1,EDGE);
            int length=Math.max(0,(int)((height-25)*reveal));
            if(length>0)g.vLine(x+11,y+25,y+25+length,blend(EDGE,TEAL,.55f));
            if(!reducedMotion()&&flowing&&length>4){int dot=y+26+(int)(((now%1700)/1700d+index*.19)%1*Math.max(1,length-3));g.fill(x+10,dot,x+13,dot+3,TEAL);}
        }
        var component=Component.literal(title.isBlank()?text("missing_name"):title);g.drawWordWrap(font,component,x+30,y+2,Math.max(24,w-30),TEXT);
        int next=y+font.wordWrapHeight(component,Math.max(24,w-30))+7;
        g.drawString(font,fit(label,w-30),x+30,next,MUTED,false);return y+height;
    }
    private static ItemStack stack(CompoundTag item){var level=Minecraft.getInstance().level;return level==null?ItemStack.EMPTY:ItemStack.parseOptional(level.registryAccess(),item.getCompound("Item"));}
    static void tooltip(GuiGraphics g,CompoundTag row,int x,int start,int w,int mx,int my){
        if(!row.getString("Kind").equals("warehouse")||!row.getBoolean("StockAvailable"))return;
        var font=Minecraft.getInstance().font;int y=start+18+font.wordWrapHeight(tr("visual.stock_help"),w)+12,columns=Math.max(1,w/44);
        var items=row.getList("Stock",10);
        for(int i=0;i<items.size();i++){int sx=x+i%columns*44,sy=y+i/columns*40;if(mx>=sx&&mx<sx+40&&my>=sy&&my<sy+36){var item=items.getCompound(i);var stack=stack(item);if(!stack.isEmpty()){var lines=new java.util.ArrayList<Component>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(),stack));lines.add(tr("visual.available").copy().append(": "+item.getInt("Count")));g.renderComponentTooltip(font,lines,mx,my);}else g.renderTooltip(font,Component.literal(name(item)),mx,my);return;}}
    }
}
