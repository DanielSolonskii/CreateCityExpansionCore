package com.example.examplemod.client.screen;
import com.example.examplemod.client.NearbySceneClient;
import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.ProjectCardKeys;
import com.example.examplemod.client.screen.*;
import com.example.examplemod.network.NearbyScenePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class ProjectArrivalHud {
    private static Object world;private static NearbyScenePayload.Site active;
    private static long entered;private static final Map<UUID,Long> shown=new HashMap<>();
    private ProjectArrivalHud(){}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();if(world!=mc.level){world=mc.level;active=null;shown.clear();}
        if(mc.player==null)return;long now=net.minecraft.Util.getMillis();
        shown.entrySet().removeIf(a->now-a.getValue()>600000);
        var next=NearbySceneClient.projects().stream().filter(s->!s.status().terminal()&&s.zone().distance(mc.player.getX(),mc.player.getZ())<=2)
            .min(Comparator.comparingDouble((NearbyScenePayload.Site s)->s.zone().distance(mc.player.getX(),mc.player.getZ())).thenComparing(NearbyScenePayload.Site::id)).orElse(null);
        if(next!=null&&(active==null||!active.id().equals(next.id()))){
            boolean first=!shown.containsKey(next.id())||now-shown.get(next.id())>30000;
            entered=first?now:now-5000;if(first){shown.put(next.id(),now);if(mc.screen==null)CommunityFx.sound("open");}
        }active=next;
        if(mc.screen==null)while(ProjectCardKeys.OPEN.consumeClick())if(active!=null)mc.setScreen(new ProjectPublicCardScreen(null,active));
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(active==null||mc.screen!=null||mc.options.hideGui||mc.player==null)return;
        var g=e.getGuiGraphics();long age=net.minecraft.Util.getMillis()-entered;boolean expanded=age<5200;
        int w=Math.min(244,mc.getWindow().getGuiScaledWidth()-20),h=99-(int)(68*CommunityFx.ease((age-5000)/200d));
        int x=10-(int)((1-CommunityFx.ease(age/200d))*(w+12)),y=Math.max(8,Math.min(Math.max(48,mc.getWindow().getGuiScaledHeight()/2-h/2-15),mc.getWindow().getGuiScaledHeight()-h-8));
        rounded(g,x,y,w,h,4,0xED192327);int color=ProjectMapStyle.color(active.scope(),active.status());g.fill(x+1,y+7,x+3,y+h-7,color);
        g.drawString(mc.font,fit(mc.font,Component.literal(active.title()),w-20),x+10,y+8,TEXT,false);
        if(expanded&&age<5000){
            g.drawString(mc.font,Component.translatable("gui.createcityexpansioncore.projects.status."+active.status().name().toLowerCase(Locale.ROOT)),x+10,y+24,color,false);
            g.drawString(mc.font,fit(mc.font,Component.translatable("gui.createcityexpansioncore.trade.by",active.authorName()),w-20),x+10,y+40,MUTED,false);
            g.drawString(mc.font,fit(mc.font,Component.translatable("gui.createcityexpansioncore.trade.builder",active.executorName().isBlank()?"—":active.executorName()),w-20),x+10,y+54,MUTED,false);
            g.drawString(mc.font,Component.translatable("gui.createcityexpansioncore.trade.stages",active.completed(),active.stages()),x+10,y+68,MUTED,false);
            g.drawString(mc.font,fit(mc.font,Component.translatable("gui.createcityexpansioncore.trade.card_hint",ProjectCardKeys.OPEN.getTranslatedKeyMessage()),w-20),x+10,y+84,ACCENT,false);
        }else g.drawString(mc.font,Component.translatable("gui.createcityexpansioncore.trade.card_hint",ProjectCardKeys.OPEN.getTranslatedKeyMessage()),x+10,y+20,MUTED,false);
    }
}
