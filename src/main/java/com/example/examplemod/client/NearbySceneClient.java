package com.example.examplemod.client;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class NearbySceneClient {
    private NearbySceneClient(){}
    public record Friend(UUID id,double fromX,double fromZ,double x,double z,long received){
        public double x(long now){return fromX+(x-fromX)*Math.clamp((now-received)/1000d,0,1);}
        public double z(long now){return fromZ+(z-fromZ)*Math.clamp((now-received)/1000d,0,1);}
    }
    private static ResourceLocation dimension;
    private static Object world;
    private static List<Friend> friends=List.of();
    private static List<NearbyScenePayload.Site> sites=List.of();
    private static long received,sitesReceived;
    public static boolean sharing,known;
    private static void clear(){friends=List.of();sites=List.of();dimension=null;received=sitesReceived=0;sharing=known=false;}
    private static boolean valid(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.level==world&&mc.level.dimension().location().equals(dimension);}
    public static void accept(NearbyScenePayload p){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(p.dimension()))return;
        if(world!=mc.level){clear();world=mc.level;}long now=net.minecraft.Util.getMillis();
        CommunityFx.load();
        Map<UUID,Friend> old=new HashMap<>();if(valid())for(var f:friends)old.put(f.id,f);
        var next=new ArrayList<Friend>();for(var f:p.friends()){
            var previous=old.get(f.id());boolean smooth=!CommunityFx.reduced&&previous!=null&&now-received<2500&&Math.hypot(previous.x(now)-f.x(),previous.z(now)-f.z())<48;
            next.add(new Friend(f.id(),smooth?previous.x(now):f.x(),smooth?previous.z(now):f.z(),f.x(),f.z(),now));
        }
        dimension=p.dimension();friends=List.copyOf(next);received=now;sharing=p.sharing();known=true;
        if(p.replaceProjects()){sites=p.projects();sitesReceived=now;}
    }
    public static List<Friend> friends(){return valid()&&net.minecraft.Util.getMillis()-received<2500?friends:List.of();}
    public static List<NearbyScenePayload.Site> projects(){return valid()&&net.minecraft.Util.getMillis()-sitesReceived<8000?sites:List.of();}
    public static void share(boolean value){if(known&&Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new NearbySharingPayload(value));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var level=Minecraft.getInstance().level;if(world!=level){clear();world=level;}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();world=null;}
}
