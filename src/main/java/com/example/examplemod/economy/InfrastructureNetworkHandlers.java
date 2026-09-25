package com.example.examplemod.economy;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.economy.block.EconomyThroughputBlockEntity;
import com.example.examplemod.economy.block.WarehouseGatewayBlock;
import com.example.examplemod.menu.EconomyInfrastructureMenu;
import com.example.examplemod.network.InfrastructureCommandPayload;
import com.example.examplemod.network.InfrastructureStatePayload;
import com.example.examplemod.railway.RailwayManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InfrastructureNetworkHandlers {
    private InfrastructureNetworkHandlers(){}

    public static void handle(ServerPlayer player,InfrastructureCommandPayload payload){
        Context context=context(player,payload.pos());if(context==null)return;
        if(payload.action()==InfrastructureCommandPayload.Action.REQUEST){send(context);return;}
        boolean ok=false;
        if(payload.action()==InfrastructureCommandPayload.Action.TOGGLE_GATEWAY&&context.city!=null
                &&context.city.isAdmin(player.getUUID())
                &&player.serverLevel().getBlockState(payload.pos()).getBlock() instanceof WarehouseGatewayBlock){
            player.serverLevel().setBlock(payload.pos(),player.serverLevel().getBlockState(payload.pos()).cycle(WarehouseGatewayBlock.OUTPUT),Block.UPDATE_ALL);ok=true;
        }
        if(!ok)player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.createcityexpansioncore.economy.operation_failed"),true);
        send(context);
    }

    private static void send(Context context){
        var state=context.player.serverLevel().getBlockState(context.be.getBlockPos());
        EconomyTypes.GatewayMode mode=state.getBlock() instanceof WarehouseGatewayBlock?WarehouseGatewayBlock.mode(state):EconomyTypes.GatewayMode.INPUT;
        String stationName="";
        if(context.registration.stationRegistrationId()!=null){var station=RailwayManager.get(context.player.serverLevel()).station(context.registration.stationRegistrationId());if(station!=null)stationName=station.lastKnownName();}
        PacketDistributor.sendToPlayer(context.player,new InfrastructureStatePayload(context.be.getBlockPos(),context.registration.registrationId(),context.registration.type(),mode,context.city==null?"":context.city.getName(),stationName,context.registration.lastAttributionError()));
    }

    private static Context context(ServerPlayer player,net.minecraft.core.BlockPos pos){if(!(player.containerMenu instanceof EconomyInfrastructureMenu menu)||!menu.pos().equals(pos)||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!(player.serverLevel().getBlockEntity(pos) instanceof EconomyThroughputBlockEntity be)||be.registrationId()==null)return null;EconomyManager manager=EconomyManager.get(player.serverLevel());var registration=manager.registration(be.registrationId());if(registration==null||!registration.physicalInstanceId().equals(be.physicalInstanceId()))return null;City city=registration.cityId()==null?null:CityManager.get(player.getServer().overworld()).getCity(registration.cityId());if(city==null&&!registration.registeredBy().equals(player.getUUID()))return null;if(city!=null&&!city.hasMember(player.getUUID()))return null;return new Context(player,be,manager,registration,city);}
    private record Context(ServerPlayer player,EconomyThroughputBlockEntity be,EconomyManager manager,EconomyInfrastructureRegistration registration,City city){}
}
