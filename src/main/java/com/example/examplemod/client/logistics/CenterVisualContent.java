package com.example.examplemod.client.logistics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Read-only presentation of server snapshots; slots never transfer or create items. */
final class CenterVisualContent {
    private CenterVisualContent() {}
    static int render(GuiGraphics g,CompoundTag row,int x,int y,int w){
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
                surface(g,sx,sy,40,36,0xff203239);
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
            y=node(g,x,y,w,row.getString("SourceName"),text("visual.source"),5);
            for(Tag raw:row.getList("RouteNodes",10))y=node(g,x,y,w,((CompoundTag)raw).getString("Name"),text("visual.terminal"),4);
            y=node(g,x,y,w,row.getString("DestinationName"),text("visual.destination"),5);
            g.drawWordWrap(font,Component.literal(value(row.getString("Status"))),x,y,w,TEAL);y+=font.wordWrapHeight(Component.literal(value(row.getString("Status"))),w)+14;
            int total=row.getInt("Quantity"),delivered=row.getInt("Delivered");
            g.fill(x,y,x+w,y+5,EDGE);if(total>0)g.fill(x,y,x+(int)(w*Math.clamp(delivered/(double)total,0,1)),y+5,TEAL);y+=12;
            var progress=Component.translatable("ccec.center.visual.progress",row.getInt("Packaged"),delivered,total);
            g.drawWordWrap(font,progress,x,y,w,TEXT);y+=font.wordWrapHeight(progress,w)+14;
            String step=switch(row.getString("Status")){case "REQUESTED"->"requested";case "PACKAGED"->"packaged";case "IN_TRANSIT","TRANSSHIPMENT"->"transit";case "ARRIVED","DELIVERED"->"arrived";case "COMPLETED"->"completed";case "CANCELLED"->"cancelled";default->"attention";};
            var next=tr("visual.next."+step);g.drawWordWrap(font,next,x,y,w,MUTED);y+=font.wordWrapHeight(next,w)+18;
        }else if(kind.equals("line")){
            g.drawString(font,text("visual.network"),x,y,TEAL,false);y+=18;
            for(Tag raw:row.getList("Details",10)){var part=(CompoundTag)raw;y=node(g,x,y,w,name(part),value(part.getString("Status")),4);}
        }
        return y;
    }
    private static int node(GuiGraphics g,int x,int y,int w,String title,String label,int symbol){
        var font=Minecraft.getInstance().font;icon(g,x+2,y+3,symbol,TEAL);g.vLine(x+8,y+19,y+43,EDGE);
        var component=Component.literal(title.isBlank()?text("missing_name"):title);g.drawWordWrap(font,component,x+25,y,w-25,TEXT);
        int next=y+font.wordWrapHeight(component,w-25)+5;g.drawString(font,fit(label,w-25),x+25,next,MUTED,false);return next+22;
    }
    private static ItemStack stack(CompoundTag item){var level=Minecraft.getInstance().level;return level==null?ItemStack.EMPTY:ItemStack.parseOptional(level.registryAccess(),item.getCompound("Item"));}
    static void tooltip(GuiGraphics g,CompoundTag row,int x,int start,int w,int mx,int my){
        if(!row.getString("Kind").equals("warehouse")||!row.getBoolean("StockAvailable"))return;
        var font=Minecraft.getInstance().font;int y=start+18+font.wordWrapHeight(tr("visual.stock_help"),w)+12,columns=Math.max(1,w/44);
        var items=row.getList("Stock",10);
        for(int i=0;i<items.size();i++){int sx=x+i%columns*44,sy=y+i/columns*40;if(mx>=sx&&mx<sx+40&&my>=sy&&my<sy+36){var item=items.getCompound(i);var stack=stack(item);if(!stack.isEmpty()){var lines=new java.util.ArrayList<Component>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(),stack));lines.add(tr("visual.available").copy().append(": "+item.getInt("Count")));g.renderComponentTooltip(font,lines,mx,my);}else g.renderTooltip(font,Component.literal(name(item)),mx,my);return;}}
    }
}
