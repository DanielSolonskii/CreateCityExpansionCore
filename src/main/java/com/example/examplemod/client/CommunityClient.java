package com.example.examplemod.client;
import com.example.examplemod.network.CommunityStatePayload;
import net.minecraft.client.Minecraft;
import java.util.UUID;
public final class CommunityClient {
    private CommunityClient(){}private static UUID owner;private static String role="";
    public static void accept(CommunityStatePayload p){var mc=Minecraft.getInstance();if(mc.player==null||p.data().getString("Result").equals("busy"))return;if(p.data().hasUUID("MutedPeer"))CommunityFx.setMute(p.data().getUUID("MutedPeer"),p.data().getBoolean("Muted"));owner=mc.player.getUUID();role=p.data().getString("OwnRole");}
    public static boolean canInvite(){var p=Minecraft.getInstance().player;return p!=null&&p.getUUID().equals(owner)&&(role.equals("OWNER")||role.equals("ADMIN"));}
}
