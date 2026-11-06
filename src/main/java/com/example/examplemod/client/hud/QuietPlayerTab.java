package com.example.examplemod.client.hud;

import com.example.examplemod.hud.TabCityLabel;
import com.example.examplemod.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import java.util.*;

/** A quiet tab roster, with city colour confined to the city column. */
public final class QuietPlayerTab {
    private static final int TEXT=0xFFE5E9E7, MUTED=0xFF8C999B;
    private QuietPlayerTab() {}

    public static List<PlayerInfo> sorted(List<PlayerInfo> source) {
        var mc=Minecraft.getInstance();
        var own=mc.player==null||mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(mc.player.getUUID());
        var city=own==null?null:TabCityLabel.read(own.getTabListDisplayName());
        var players=new ArrayList<>(source);
        players.sort(Comparator.comparingInt((PlayerInfo info)->{
            var other=TabCityLabel.read(info.getTabListDisplayName());
            return city!=null&&other!=null&&city.id().equals(other.id())?0:1;
        }).thenComparing(info->info.getProfile().getName(),String.CASE_INSENSITIVE_ORDER)
                .thenComparing(info->info.getProfile().getId()));
        return players;
    }

    public static Component hints() {
        var result=Component.empty();
        for(var key:Minecraft.getInstance().options.keyMappings) {
            String label=switch(key.getName()) {
                case "key.createcityexpansioncore.open_world_map" -> "gui.createcityexpansioncore.tab.map";
                case "key.createcityexpansioncore.logistics" -> "gui.createcityexpansioncore.tab.logistics";
                default -> null;
            };
            if(label==null||key.isUnbound())continue;
            if(!result.getString().isEmpty())result.append(" · ");
            result.append(Component.translatable(label)).append(" [").append(key.getTranslatedKeyMessage()).append("]");
        }
        return result.withStyle(style->style.withColor(MUTED & 0xFFFFFF));
    }

    public static void render(GuiGraphics g,List<PlayerInfo> players,Component header) {
        var mc=Minecraft.getInstance();var font=mc.font;
        int nameWidth=70,cityWidth=70;
        for(var info:players) {
            nameWidth=Math.max(nameWidth,Math.min(120,font.width(info.getProfile().getName())));
            var city=TabCityLabel.read(info.getTabListDisplayName());
            if(city!=null)cityWidth=Math.max(cityWidth,Math.min(120,font.width(city.name())));
        }
        int cellWidth=14+nameWidth+18+cityWidth+22;
        int bosses=((BossHealthOverlayAccessor)mc.gui.getBossOverlay()).ccec$events().size();
        var layout=QuietTabLayout.calculate(g.guiWidth(),g.guiHeight(),players.size(),cellWidth,bosses);
        g.pose().pushPose();
        try {
            g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);
            int w=layout.width(),h=layout.height();
            g.fill(0,0,w,h,0xEC151B1E);
            g.renderOutline(0,0,w,h,0xFF344044);
            g.drawString(font,"RE:CREATED",12,12,TEXT,false);
            String[] headerLines=header.getString().split("\n",2);
            String stats=headerLines.length>1?headerLines[1]:"";
            // Header and footer never enlarge the player columns.
            stats=fit(font,stats,w-24-font.width("RE:CREATED")-20);
            g.drawString(font,stats,w-12-font.width(stats),12,MUTED,false);
            g.fill(12,29,w-12,30,0xFF344044);
            var self=mc.player==null?null:mc.player.getUUID();
            for(int i=0;i<players.size();i++) {
                var info=players.get(i);int column=i/layout.rows(),row=i%layout.rows();
                int x=12+column*cellWidth,y=38+row*15;
                if(info.getProfile().getId().equals(self))g.fill(x-3,y-3,x+cellWidth-5,y+11,0x183B5550);
                PlayerFaceRenderer.draw(g,info.getSkin(),x,y,8);
                var name=Component.literal(fit(font,info.getProfile().getName(),nameWidth));
                boolean spectator=info.getGameMode()==GameType.SPECTATOR;
                name.withStyle(style->style.withItalic(spectator));
                g.drawString(font,name,x+14,y,spectator?MUTED:TEXT,false);
                var city=TabCityLabel.read(info.getTabListDisplayName());
                if(city!=null)g.drawString(font,fit(font,city.name(),cityWidth),x+14+nameWidth+18,y,0xFF000000|city.rgb(),false);
                ping(g,x+cellWidth-16,y,info.getLatency());
            }
            g.fill(12,h-25,w-12,h-24,0xFF344044);
            g.drawString(font,fit(font,hints().getString(),w-24),12,h-16,MUTED,false);
        } finally { g.pose().popPose(); }
    }

    private static String fit(Font font,String text,int width) {
        if(font.width(text)<=width)return text;
        return font.plainSubstrByWidth(text,Math.max(0,width-font.width("…")))+"…";
    }

    private static void ping(GuiGraphics g,int x,int y,int latency) {
        int bars=latency<0?0:latency<150?4:latency<300?3:latency<600?2:1;
        int colour=latency<0?MUTED:latency>=600?0xFFC69567:0xFF9BAEA4;
        for(int i=0;i<4;i++)g.fill(x+i*3,y+7-i*2,x+i*3+2,y+9,i<bars?colour:0xFF344044);
    }
}
