package com.example.examplemod.client.freight;

import com.example.examplemod.client.BuildGuideRenderHelper;
import com.example.examplemod.economy.FreightGuideNetwork;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/** Opt-in guide, no global block interception while inactive. */
public final class FreightGuideClient {
    private static CompoundTag state=new CompoundTag();
    private static UUID session;
    private static boolean awaiting,picking;
    private static long requestedAt,nextSend;
    private static int ticks;
    public static String draft="";public static int draftMode=2;
    private static Object world;
    private FreightGuideClient() {}
    public static Component tr(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.freight_guide."+key,args);}
    public static CompoundTag state(){return state;}
    public static boolean active(){return session!=null;}
    public static boolean picking(){return picking;}
    public static void begin(BlockPos pylon,UUID point){begin(pylon,point,false);}
    public static void begin(BlockPos pylon,UUID point,boolean repair) {
        draft="";draftMode=2;awaiting=true;requestedAt=net.minecraft.Util.getMillis();world=Minecraft.getInstance().level;
        PacketDistributor.sendToServer(new FreightGuideRequest(repair?FreightGuideRequest.Action.BEGIN_REBIND:FreightGuideRequest.Action.BEGIN,null,point,pylon,2,""));
    }
    public static void command(FreightGuideRequest.Action action,BlockPos pos,int mode,String name) {
        if(session!=null)PacketDistributor.sendToServer(new FreightGuideRequest(action,session,null,pos,mode,name));
    }
    public static void accept(FreightGuideState payload) {
        var mc=Minecraft.getInstance();var next=payload.data();
        if(next.getBoolean("Closed")) {
            boolean relevant=active()||awaiting;clear();
            if(mc.screen instanceof FreightGuideScreen)mc.setScreen(null);
            if(relevant&&mc.player!=null)mc.player.displayClientMessage(tr(next.getString("Result")),false);
            return;
        }
        if(!next.hasUUID("Session")||(!awaiting&&!next.getUUID("Session").equals(session)))return;
        int old=state.getInt("Step");boolean started=awaiting;
        state=next.copy();session=next.getUUID("Session");awaiting=false;
        if(started){draft=next.getString("Name");draftMode=next.getInt("Mode");picking=false;mc.setScreen(new FreightGuideScreen());}
        else if(next.getInt("Step")>old){
            if(mc.player!=null)mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(),.3f,1.2f);
            if(next.getInt("Step")==2&&mc.screen==null)mc.setScreen(new FreightGuideScreen());
        }
        if(mc.screen instanceof FreightGuideScreen screen)screen.update();
    }
    public static void enterWorld(boolean select) { picking=select;Minecraft.getInstance().setScreen(null); }
    public static void clear(){draft="";draftMode=2;state=new CompoundTag();session=null;awaiting=false;picking=false;nextSend=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        var mc=Minecraft.getInstance();
        if(world!=mc.level||mc.player==null){clear();world=mc.level;return;}
        if(awaiting&&net.minecraft.Util.getMillis()-requestedAt>8000){awaiting=false;mc.player.displayClientMessage(tr("no_response"),false);}
        if(!active())return;
        if(!mc.level.dimension().location().toString().equals(state.getString("Dimension"))){clear();return;}
        if(++ticks%40==0)command(FreightGuideRequest.Action.REFRESH,BlockPos.ZERO,draftMode,"");
    }
    @SubscribeEvent public static void input(InputEvent.InteractionKeyMappingTriggered e) {
        var mc=Minecraft.getInstance();
        if(!active()||mc.screen!=null||mc.player==null||!e.isUseItem()||e.getHand()!=InteractionHand.MAIN_HAND)return;
        boolean settings=mc.player.isShiftKeyDown()&&mc.player.getMainHandItem().isEmpty();
        if(!settings&&!picking)return;
        e.setCanceled(true);e.setSwingHand(false);
        if(settings){mc.setScreen(new FreightGuideScreen());return;}
        long now=net.minecraft.Util.getMillis();if(now<nextSend)return;
        if(mc.hitResult instanceof BlockHitResult hit){nextSend=now+200;command(FreightGuideRequest.Action.PICK,hit.getBlockPos(),draftMode,"");}
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance();if(!active()||mc.screen!=null||mc.options.hideGui)return;
        int w=Math.min(300,mc.getWindow().getGuiScaledWidth()-16);if(w<80)return;
        var title=tr(state.getBoolean("Existing")?"diagnostics":"step."+state.getInt("Step"));
        var help=tr(picking?"world_select":"world_build");
        var lines=mc.font.split(help,w-20);
        Component problem=null;
        for(var raw:state.getList("Checks",10)){
            var check=(CompoundTag)raw;
            if(!check.getBoolean("Ok")){problem=tr("check."+check.getString("Key")+".false");break;}
        }
        var problems=problem==null?java.util.List.<net.minecraft.util.FormattedCharSequence>of():mc.font.split(problem,w-20);
        int h=36+lines.size()*10+(problems.isEmpty()?0:8+problems.size()*10);
        var g=e.getGuiGraphics();g.fill(8,38,8+w,38+h,0xE9162024);g.fill(8,38,10,38+h,0xFFD5AA62);
        g.drawString(mc.font,mc.font.plainSubstrByWidth(title.getString(),w-20),18,47,0xFFD5AA62,false);
        for(int i=0;i<lines.size();i++)g.drawString(mc.font,lines.get(i),18,64+i*10,0xFFD5DEDB,false);
        for(int i=0;i<problems.size();i++)g.drawString(mc.font,problems.get(i),18,72+(lines.size()+i)*10,0xFFF0B581,false);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        var mc=Minecraft.getInstance();
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!active()||mc.level==null||mc.player==null||mc.options.hideGui)return;
        var pose=e.getPoseStack();var source=mc.renderBuffers().bufferSource();var lines=source.getBuffer(RenderType.lines());var cam=e.getCamera().getPosition();
        pose.pushPose();pose.translate(-cam.x,-cam.y,-cam.z);
        for(String key:new String[]{"Packager","Warehouse","Postbox","Station"})if(state.contains(key)){
            BlockPos p=BlockPos.of(state.getLong(key));
            if(p.distSqr(mc.player.blockPosition())>64*64||!mc.level.hasChunkAt(p))continue;
            var status=mc.level.getBlockState(p).isAir()?BuildGuideRenderHelper.Status.MISSING:BuildGuideRenderHelper.Status.FUNCTIONAL;
            BuildGuideRenderHelper.renderOutline(pose,lines,p,status);
        }
        if(state.contains("Warehouse")&&state.contains("Packager")){
            var from=BlockPos.of(state.getLong("Warehouse")).getCenter().add(0,.65,0);
            var to=BlockPos.of(state.getLong("Packager")).getCenter().add(0,.65,0);
            var direction=to.subtract(from);var side=new net.minecraft.world.phys.Vec3(-direction.z,0,direction.x).scale(.18);
            if(from.distanceToSqr(mc.player.position())<64*64){
                line(pose,lines,from,to);line(pose,lines,to,to.subtract(direction.scale(.28)).add(side));
                line(pose,lines,to,to.subtract(direction.scale(.28)).subtract(side));
            }
        }
        pose.popPose();source.endBatch(RenderType.lines());
    }
    private static void line(com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer out,net.minecraft.world.phys.Vec3 a,net.minecraft.world.phys.Vec3 b){
        var n=b.subtract(a).normalize();var p=pose.last();
        out.addVertex(p,(float)a.x,(float)a.y,(float)a.z).setColor(.84f,.68f,.39f,.95f).setNormal(p,(float)n.x,(float)n.y,(float)n.z);
        out.addVertex(p,(float)b.x,(float)b.y,(float)b.z).setColor(.84f,.68f,.39f,.95f).setNormal(p,(float)n.x,(float)n.y,(float)n.z);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();world=null;}
}
