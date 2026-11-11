package com.example.examplemod.client;
import com.example.examplemod.alliance.AllianceBook;
import com.example.examplemod.network.*;
import com.example.examplemod.client.screen.AllianceScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.*;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class AllianceClient {
    private AllianceClient(){}
    private static final Map<UUID,CompoundTag> summaries=new HashMap<>(),staging=new HashMap<>();
    private static final Map<UUID,UUID> players=new HashMap<>();
    private static final Map<UUID,UUID> cityIndex=new HashMap<>();
    private static net.minecraft.nbt.CompoundTag own=new net.minecraft.nbt.CompoundTag();
    public static net.minecraft.nbt.CompoundTag ownSummary(){return own.copy();}
    private static UUID pending;private static int nextPage;private static long requested,completeAt,activeUntil,nextSend;
    public static Set<UUID> focusedCities=Set.of();
    public static UUID focusedAlliance;
    public static CompoundTag forCity(UUID id){return summaries.get(cityIndex.get(id));}
    private static void putSummary(CompoundTag a){UUID id=a.getUUID("Id");summaries.put(id,a.copy());cityIndex.values().removeIf(id::equals);for(Tag raw:a.getList("Cities",10))cityIndex.put(((CompoundTag)raw).getUUID("Id"),id);}
    public static CompoundTag forPlayer(UUID id){return summaries.get(players.get(id));}
    public static void profile(UUID id){if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new AllianceRequestPayload(new AllianceBook.Command(UUID.randomUUID(),AllianceBook.Action.PLAYER,null,id,0,"","",0,0),0));}
    public static void ensure(){activeUntil=net.minecraft.Util.getMillis()+2000;}
    public static void focus(CompoundTag alliance){var ids=new LinkedHashSet<UUID>();for(Tag t:alliance.getList("Cities",10))ids.add(((CompoundTag)t).getUUID("Id"));focusedCities=Set.copyOf(ids);focusedAlliance=alliance.getUUID("Id");}
    public static void clearFocus(){focusedCities=Set.of();focusedAlliance=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){own=new net.minecraft.nbt.CompoundTag();summaries.clear();players.clear();cityIndex.clear();staging.clear();pending=null;nextPage=0;completeAt=activeUntil=nextSend=0;clearFocus();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){long now=net.minecraft.Util.getMillis();if(Minecraft.getInstance().getConnection()==null||now>activeUntil)return;
        if(pending!=null&&now-requested>8000){pending=null;nextPage=0;staging.clear();nextSend=now+2000;}
        if(pending==null&&now>=nextSend&&(nextPage>0||completeAt==0||now-completeAt>30000)){pending=UUID.randomUUID();requested=now;PacketDistributor.sendToServer(new AllianceRequestPayload(new AllianceBook.Command(pending,AllianceBook.Action.DIRECTORY,null,null,0,"","",0,0),nextPage));}
    }
    public static void accept(AllianceStatePayload payload){var t=payload.data();if(t.getBoolean("Open")){AllianceScreen.open(Minecraft.getInstance().screen,null);return;}
        if(t.contains("OwnSummary",10))own=t.getCompound("OwnSummary").copy();else if(!t.getString("Result").equals("busy"))own=new net.minecraft.nbt.CompoundTag();
        if(t.contains("Alliance",10))putSummary(t.getCompound("Alliance"));
        if(t.hasUUID("Player")){if(t.contains("Alliance",10))players.put(t.getUUID("Player"),t.getCompound("Alliance").getUUID("Id"));else players.remove(t.getUUID("Player"));}
        if(pending!=null&&t.hasUUID("Request")&&pending.equals(t.getUUID("Request"))){pending=null;nextSend=net.minecraft.Util.getMillis()+180;if(t.getString("Result").equals("busy")){nextSend+=1500;return;}
            if(t.getInt("DirectoryPage")==0)staging.clear();for(Tag raw:t.getList("Directory",10)){var a=(CompoundTag)raw;staging.put(a.getUUID("Id"),a.copy());}
            nextPage=t.getInt("DirectoryPage")+1;if(nextPage*32>=t.getInt("DirectoryTotal")){summaries.clear();cityIndex.clear();staging.values().forEach(AllianceClient::putSummary);nextPage=0;completeAt=net.minecraft.Util.getMillis();if(focusedAlliance!=null){var a=summaries.get(focusedAlliance);if(a==null)clearFocus();else focus(a);}}
        }
        AllianceScreen.accept(payload);
    }
}
