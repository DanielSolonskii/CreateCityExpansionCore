package com.example.examplemod.client;
import com.example.examplemod.client.screen.BankScreen;
import com.example.examplemod.economy.BankBook;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class BankClient {
    private static UUID owner,pending;private static long sent,updated;private static long wallet;private static boolean ready;
    public static boolean hasCity;
    public static void ensure(){var mc=Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null)return;long now=net.minecraft.Util.getMillis();if(!mc.player.getUUID().equals(owner))clear();owner=mc.player.getUUID();if(pending!=null&&now-sent>8000)pending=null;if(pending==null&&now-sent>=2000){pending=UUID.randomUUID();sent=now;PacketDistributor.sendToServer(new BankRequest(pending,0,BankBook.Action.VIEW,null,null,null,0,0,"",0,true));}}
    public static void accept(BankState response){var mc=Minecraft.getInstance();var data=response.data();if(mc.player==null||!data.hasUUID("Owner")||!mc.player.getUUID().equals(data.getUUID("Owner")))return;if(data.contains("Wallet")){owner=mc.player.getUUID();wallet=data.getLong("Wallet");hasCity=data.getBoolean("HasCity");updated=net.minecraft.Util.getMillis();ready=true;}if(data.hasUUID("Request")&&data.getUUID("Request").equals(pending))pending=null;BankScreen.accept(response);}
    public static boolean ready(){return ready&&Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.getUUID().equals(owner)&&net.minecraft.Util.getMillis()-updated<10000;}
    public static long wallet(){return wallet;}
    public static String money(long value){return String.format(java.util.Locale.US,"%,d",value).replace(',',' ');}
    private static void clear(){owner=pending=null;ready=false;hasCity=false;sent=updated=wallet=0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
}
