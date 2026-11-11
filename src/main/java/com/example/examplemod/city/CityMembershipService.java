package com.example.examplemod.city;
import com.example.examplemod.community.CommunityData;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.SyncCityMembersPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** One authoritative removal/transfer path for community UI and legacy pylon payloads. */
public final class CityMembershipService {
    public enum Action { LEAVE, KICK, TRANSFER }
    private CityMembershipService(){}
    public static boolean mayKick(City city,UUID actor,UUID target){return city!=null&&actor!=null&&target!=null&&!actor.equals(target)&&city.hasMember(target)&&!city.isOwner(target)&&city.isAdmin(actor)&&(city.isOwner(actor)||!city.isAdmin(target));}
    public static boolean mayTransfer(City city,UUID actor,UUID target){return city!=null&&actor!=null&&target!=null&&city.isOwner(actor)&&!actor.equals(target)&&city.hasMember(target);}
    public static boolean atPylon(ServerPlayer p,City city){return city!=null&&p.containerMenu instanceof CityPylonMenu m&&m.getPylonPos().equals(city.getPylonPos())&&p.level().dimension().location().equals(city.getPylonDimension())&&p.distanceToSqr(city.getPylonPos().getX()+.5,city.getPylonPos().getY()+.5,city.getPylonPos().getZ()+.5)<=64&&p.serverLevel().getBlockEntity(city.getPylonPos()) instanceof com.example.examplemod.block.CityPylonBlockEntity;}
    public static String change(ServerPlayer p,Action action,UUID expectedCity,UUID requestedTarget,UUID request){
        if(p instanceof net.neoforged.neoforge.common.util.FakePlayer||expectedCity==null||request==null)return "permission";
        UUID target=action==Action.LEAVE?p.getUUID():requestedTarget;if(target==null)return "state";
        var s=p.getServer();var manager=CityManager.get(s.overworld());var events=CityMembershipEvents.get(s);var receipt=new CityMembershipEvents.Receipt(p.getUUID(),request,expectedCity,target,action.name());if(events.done(receipt))return "ok";
        var city=manager.getCity(expectedCity);var own=manager.getMemberCity(p.getUUID());if(city==null||own!=city)return "membership";
        if(action==Action.LEAVE){if(city.isOwner(p.getUUID()))return "owner_leave";}
        else {if(!atPylon(p,city))return "pylon_required";if(action==Action.KICK?!mayKick(city,p.getUUID(),target):!mayTransfer(city,p.getUUID(),target))return "permission";}
        if(action==Action.TRANSFER){if(!city.transferOwnership(p.getUUID(),target))return "state";}else city.removeMember(target);
        manager.setDirty();events.record(receipt);
        if(action!=Action.LEAVE)com.example.examplemod.community.CommunityInbox.get(s).add(target,request,action==Action.KICK?"city_removed":"city_owner",p.getUUID(),city.getId(),null,city.getName(),System.currentTimeMillis());CommunityData.get(s).audit(city.getId(),p.getUUID(),target,action==Action.KICK?"kicked":action==Action.LEAVE?"left":"ownership_transferred");
        // Close existing containers as well as revoking future interaction checks.
        var affected=s.getPlayerList().getPlayer(target);if(action!=Action.TRANSFER&&affected!=null&&(action==Action.KICK||affected.containerMenu!=affected.inventoryMenu))affected.closeContainer();
        var entries=city.getMembers().stream().map(m->new SyncCityMembersPayload.Entry(m.getPlayerId(),m.getRole().ordinal(),com.example.examplemod.auth.PlayerNames.name(s,m.getPlayerId()))).toList();
        for(var viewer:s.getPlayerList().getPlayers())if(viewer.containerMenu instanceof CityPylonMenu m&&viewer.level().dimension().location().equals(city.getPylonDimension())&&m.getPylonPos().equals(city.getPylonPos())){if(action==Action.TRANSFER)viewer.closeContainer();else PacketDistributor.sendToPlayer(viewer,new SyncCityMembersPayload(city.getPylonPos(),entries));}
        if(action==Action.KICK)events.notify(s,target,"kicked",city.getName());if(action==Action.TRANSFER)events.notify(s,target,"owner",city.getName());
        if(action==Action.LEAVE)p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.createcityexpansioncore.community.membership.notice.left",city.getName()));
        if(action==Action.TRANSFER)p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.createcityexpansioncore.community.membership.notice.transferred",city.getName()));
        if(affected!=null){affected.getPersistentData().remove("CCECBoundaryRefresh");CityBoundaryNetwork.request(affected);}
        return "ok";
    }
}
