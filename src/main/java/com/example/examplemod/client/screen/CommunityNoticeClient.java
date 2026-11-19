package com.example.examplemod.client.screen;
import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.network.CommunityNoticePayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
    private record Notice(Component title, boolean project, long start) {}
    private static Notice visible;

    public static void accept(CommunityNoticePayload packet) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || CommunityFx.muted(packet.peer())
                || packet.kind().equals("new_message") && SocialScreen.reading(packet.peer())
                || !CommunityFx.feedback.banners(false, System.currentTimeMillis())) return;
        visible = new Notice(Component.translatable("gui.createcityexpansioncore.social." + packet.kind(),
                packet.name()), false, net.minecraft.Util.getMillis());
        CommunityFx.sound(packet.kind().equals("new_message") ? "message" : "invite");
    }

    public static void project(UUID event, boolean attention) {
        if (Minecraft.getInstance().player == null) return;
        CommunityFx.load();
        if (!CommunityFx.feedback.projectNotice(event, System.currentTimeMillis())) return;
        visible = new Notice(Component.translatable("gui.createcityexpansioncore.hub."
                + (attention ? "project_attention" : "project_notice")), true, net.minecraft.Util.getMillis());
        CommunityFx.sound(attention ? "map_warning" : "map_event");
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        visible = null;
        CommunityFx.feedback.resetSession();
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (visible == null) return;
        long elapsed = net.minecraft.Util.getMillis() - visible.start();
        if (mc.player == null || elapsed > 5000 || !CommunityFx.feedback.banners(visible.project(), System.currentTimeMillis())) {
            visible = null;
            return;
        }
        var g=event.getGuiGraphics();int w=Math.min(246,mc.getWindow().getGuiScaledWidth()-16),h=46;double enter=CommunityFx.ease(elapsed/180.0),leave=CommunityFx.ease((5000-elapsed)/180.0);int x=mc.getWindow().getGuiScaledWidth()-w-8+(int)((1-Math.min(enter,leave))*(w+8)),y=42;
        WorldMapChrome.rounded(g,x,y,w,h,4,0xF020292D);g.fill(x+1,y+9,x+3,y+h-9,WorldMapChrome.ACCENT);
        var title=visible.title();g.drawString(mc.font,WorldMapChrome.fit(mc.font,title,w-20),x+10,y+9,WorldMapChrome.TEXT,false);
        g.drawString(mc.font,Component.translatable("gui.createcityexpansioncore.community.notice_hint",1),x+10,y+28,WorldMapChrome.MUTED,false);
    }
}
