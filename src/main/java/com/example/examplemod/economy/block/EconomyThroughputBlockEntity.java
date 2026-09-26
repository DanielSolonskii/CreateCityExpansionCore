package com.example.examplemod.economy.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.economy.EconomyTypes;
import com.example.examplemod.sound.ModSounds;
import com.example.examplemod.sound.WorldSoundHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

public final class EconomyThroughputBlockEntity extends BlockEntity {
    private final ItemStackHandler buffer = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private UUID physicalInstanceId;
    private UUID registrationId;

    public EconomyThroughputBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, UUID.randomUUID());
    }

    private EconomyThroughputBlockEntity(BlockPos pos, BlockState state, UUID physicalId) {
        super(CreateCityExpansionCore.ECONOMY_THROUGHPUT_BLOCK_ENTITY.get(), pos, state);
        this.physicalInstanceId = physicalId;
    }

    public UUID physicalInstanceId() { return physicalInstanceId; }
    public UUID registrationId() { return registrationId; }
    public void restoreIdentity(UUID physicalId,UUID registrationId){this.physicalInstanceId=physicalId;this.registrationId=registrationId;setChanged();}
    public void setRegistrationId(UUID value) { if (!java.util.Objects.equals(registrationId, value)) { registrationId=value;setChanged(); } }
    public ItemStackHandler buffer() { return buffer; }

    public IItemHandler itemHandler(Direction side) {
        Direction facing = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (side == facing) return new SidedHandler(true, false);
        if (side == facing.getOpposite()) return new SidedHandler(false, true);
        return null;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  EconomyThroughputBlockEntity blockEntity) {
        if (level.getGameTime() % 5L != 0L) return;
        Direction outputDirection = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK,
                pos.relative(outputDirection), outputDirection.getOpposite());
        if (target == null) return;
        for (int slot = 0; slot < blockEntity.buffer.getSlots(); slot++) {
            ItemStack stored = blockEntity.buffer.getStackInSlot(slot);
            if (stored.isEmpty()) continue;
            int allowed = blockEntity.allowedOutput(stored, stored.getCount());
            if (allowed <= 0) continue;
            ItemStack candidate = stored.copyWithCount(allowed);
            ItemStack simulatedRemainder = ItemHandlerHelper.insertItem(target, candidate, true);
            int insertable = allowed - simulatedRemainder.getCount();
            if (insertable <= 0) continue;
            ItemStack extracted = blockEntity.buffer.extractItem(slot, insertable, false);
            ItemStack remainder = ItemHandlerHelper.insertItem(target, extracted, false);
            int moved = extracted.getCount() - remainder.getCount();
            if (!remainder.isEmpty()) blockEntity.buffer.insertItem(slot, remainder, false);
            if (moved > 0) blockEntity.recordFlow(extracted, moved);
            break;
        }
    }

    private int allowedOutput(ItemStack stack, int requested) {
        if (!(level instanceof ServerLevel serverLevel) || registrationId == null) return requested;
        if (getBlockState().getBlock() instanceof WarehouseGatewayBlock
                && WarehouseGatewayBlock.mode(getBlockState()) == EconomyTypes.GatewayMode.OUTPUT) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return (int) Math.min(requested,
                    EconomyManager.get(serverLevel).maximumGatewayOutput(registrationId, itemId, requested));
        }
        return requested;
    }

    private void recordFlow(ItemStack stack, int moved) {
        if (!(level instanceof ServerLevel serverLevel) || registrationId == null || moved <= 0) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        EconomyManager manager = EconomyManager.get(serverLevel);
        if (getBlockState().getBlock() instanceof ContractIntakeBlock) {
            manager.recordIntakeFlow(registrationId, itemId, moved, serverLevel);
            // Тихий щелчок перемещения. Строго ограничен по времени,
            // иначе поезд с тысячами предметов даст пулемёт.
            WorldSoundHelper.playFlowTick(serverLevel, worldPosition,
                    ModSounds.CONTRACT_DELIVERY_TICK.get(), 0.8F);
        } else if (getBlockState().getBlock() instanceof WarehouseGatewayBlock) {
            manager.recordGatewayFlow(registrationId, WarehouseGatewayBlock.mode(getBlockState()), itemId, moved);
            // Склад работает в обе стороны, но звук один и тот же:
            // различать вход и выход незачем, это создаёт шум.
            WorldSoundHelper.playFlowTick(serverLevel, worldPosition,
                    ModSounds.WAREHOUSE_INPUT_TICK.get(), 0.8F);
        }
    }

    public void dropContents(ServerLevel level) {
        for (int slot=0;slot<buffer.getSlots();slot++) {
            ItemStack stack=buffer.getStackInSlot(slot);
            if(!stack.isEmpty()) net.minecraft.world.level.block.Block.popResource(level,worldPosition,stack.copy());
            buffer.setStackInSlot(slot,ItemStack.EMPTY);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("PhysicalId",physicalInstanceId);
        if(registrationId!=null)tag.putUUID("RegistrationId",registrationId);
        tag.put("Buffer",buffer.serializeNBT(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if(tag.hasUUID("PhysicalId"))physicalInstanceId=tag.getUUID("PhysicalId");
        if(tag.hasUUID("RegistrationId"))registrationId=tag.getUUID("RegistrationId");
        if(tag.contains("Buffer",Tag.TAG_COMPOUND))buffer.deserializeNBT(registries,tag.getCompound("Buffer"));
    }

    private final class SidedHandler implements IItemHandler {
        private final boolean insert;
        private final boolean extract;
        private SidedHandler(boolean insert, boolean extract) { this.insert=insert;this.extract=extract; }
        @Override public int getSlots(){return buffer.getSlots();}
        @Override public ItemStack getStackInSlot(int slot){return buffer.getStackInSlot(slot);}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return insert?buffer.insertItem(slot,stack,simulate):stack;}
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate){
            if(!extract)return ItemStack.EMPTY;ItemStack stored=buffer.getStackInSlot(slot);if(stored.isEmpty())return ItemStack.EMPTY;
            int allowed=allowedOutput(stored,amount);ItemStack result=buffer.extractItem(slot,allowed,simulate);if(!simulate&&!result.isEmpty())recordFlow(result,result.getCount());return result;
        }
        @Override public int getSlotLimit(int slot){return buffer.getSlotLimit(slot);}
        @Override public boolean isItemValid(int slot,ItemStack stack){return insert&&buffer.isItemValid(slot,stack);}
    }
}
