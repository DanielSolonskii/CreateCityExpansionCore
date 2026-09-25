package com.example.examplemod.menu;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.economy.block.EconomyThroughputBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import com.example.examplemod.network.InfrastructureStatePayload;

public final class EconomyInfrastructureMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private InfrastructureStatePayload state;
    public EconomyInfrastructureMenu(int id,Inventory inventory,RegistryFriendlyByteBuf data){this(id,inventory,data.readBlockPos());}
    public EconomyInfrastructureMenu(int id,Inventory inventory,BlockPos pos){super(CreateCityExpansionCore.ECONOMY_INFRASTRUCTURE_MENU.get(),id);this.pos=pos.immutable();}
    public BlockPos pos(){return pos;}
    public InfrastructureStatePayload state(){return state;}
    public void applyState(InfrastructureStatePayload value){if(pos.equals(value.pos()))state=value;}
    @Override public boolean stillValid(Player player){return player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&player.level().getBlockEntity(pos) instanceof EconomyThroughputBlockEntity;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
