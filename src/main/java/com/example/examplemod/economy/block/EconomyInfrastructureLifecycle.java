package com.example.examplemod.economy.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class EconomyInfrastructureLifecycle {
    private EconomyInfrastructureLifecycle(){}
    static void placed(Level level,BlockPos pos,LivingEntity placer,ItemStack stack){if(!(level instanceof ServerLevel server)||!(placer instanceof Player player)||!(level.getBlockEntity(pos) instanceof EconomyThroughputBlockEntity be))return;var data=EconomyInfrastructureItemData.read(stack).orElse(null);if(data==null)return;if(data.registrationId()==null){be.restoreIdentity(data.physicalId(),null);return;}EconomyManager manager=EconomyManager.get(server);EconomyInfrastructureRegistration registration=manager.registration(data.registrationId());if(registration==null||!registration.physicalInstanceId().equals(data.physicalId())||!allowed(player,registration,server)){ItemStack refund=stackFor(level.getBlockState(pos),data.physicalId(),data.registrationId());level.removeBlock(pos,false);Block.popResource(server,pos,refund);player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy.restore_denied"),true);return;}be.restoreIdentity(data.physicalId(),data.registrationId());manager.registerInfrastructure(data.physicalId(),registration.registeredBy(),registration.type(),server.dimension().location(),pos);}
    static void creativeBreak(Level level,BlockPos pos,Player player){if(level instanceof ServerLevel server&&player.getAbilities().instabuild&&level.getBlockEntity(pos) instanceof EconomyThroughputBlockEntity be)Block.popResource(server,pos,stackFor(level.getBlockState(pos),be.physicalInstanceId(),be.registrationId()));}
    static void survivalBreak(Level level,Player player,BlockPos pos,BlockState state,BlockEntity blockEntity){if(level instanceof ServerLevel server&&!player.getAbilities().instabuild&&blockEntity instanceof EconomyThroughputBlockEntity be)Block.popResource(server,pos,stackFor(state,be.physicalInstanceId(),be.registrationId()));}
    private static boolean allowed(Player player,EconomyInfrastructureRegistration registration,ServerLevel level){if(registration.cityId()==null)return registration.registeredBy().equals(player.getUUID());City city=CityManager.get(level.getServer().overworld()).getCity(registration.cityId());return city!=null&&city.canBuild(player.getUUID());}
    private static ItemStack stackFor(BlockState state,java.util.UUID physical,java.util.UUID registration){ItemStack stack=new ItemStack(state.getBlock() instanceof ContractIntakeBlock?CreateCityExpansionCore.CONTRACT_INTAKE_ITEM.get():CreateCityExpansionCore.WAREHOUSE_GATEWAY_ITEM.get());EconomyInfrastructureItemData.write(stack,physical,registration);return stack;}
}
