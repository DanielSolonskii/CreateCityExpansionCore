package com.example.examplemod.client.screen;
import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.network.CommunityNoticePayload;
import com.example.examplemod.client.screen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import java.util.*;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class CommunityNoticeClient {
    private CommunityNoticeClient(){}
    private record Notice(String name,String kind,long start,int count){}
    private static Notice visible;private static long lastSound;
    public static void accept(CommunityNoticePayload p){var mc=Minecraft.getInstance();if(mc.player==null||CommunityFx.muted(p.peer())||p.kind().equals("new_message")&&SocialScreen.reading(p.peer()))return;
        long now=net.minecraft.Util.getMillis();int count=visible!=null&&now-visible.start()<5000&&visible.kind().equals(p.kind())?visible.count()+1:1;visible=new Notice(p.name(),p.kind(),now,count);
        if(now-lastSound>2500){CommunityFx.sound(p.kind().equals("new_message")?"message":"invite");lastSound=now;}}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){var mc=Minecraft.getInstance();if(mc.player==null){visible=null;return;}if(visible==null)return;long elapsed=net.minecraft.Util.getMillis()-visible.start();if(elapsed>5000){visible=null;return;}
        var g=event.getGuiGraphics();int w=Math.min(246,mc.getWindow().getGuiScaledWidth()-16),h=46;double enter=CommunityFx.ease(elapsed/180.0),leave=CommunityFx.ease((5000-elapsed)/180.0);int x=mc.getWindow().getGuiScaledWidth()-w-8+(int)((1-Math.min(enter,leave))*(w+8)),y=42;
        WorldMapChrome.rounded(g,x,y,w,h,4,0xF020292D);g.fill(x+1,y+9,x+3,y+h-9,WorldMapChrome.ACCENT);
        var title=Component.translatable("gui.createcityexpansioncore.social."+visible.kind(),visible.name());g.drawString(mc.font,WorldMapChrome.fit(mc.font,title,w-20),x+10,y+9,WorldMapChrome.TEXT,false);
        g.drawString(mc.font,Component.translatable("gui.createcityexpansioncore.community.notice_hint",visible.count()),x+10,y+28,WorldMapChrome.MUTED,false);
    }
}
