package com.example.examplemod.blast;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlastFurnaceOutputPortBlock extends Block implements EntityBlock {
    private static final String DATA_KEY = "BlastFurnaceTank";
    public BlastFurnaceOutputPortBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlastFurnaceOutputBlockEntity(pos, state);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof BlastFurnaceOutputBlockEntity output) {
            CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (custom.contains(DATA_KEY)) output.restoreTank(server.registryAccess(), custom.getCompound(DATA_KEY));
        }
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && player.getAbilities().instabuild)
            Block.popResource(server, pos, transferableStack(server, level.getBlockEntity(pos)));
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                                        BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (level instanceof ServerLevel server && !player.getAbilities().instabuild)
            Block.popResource(server, pos, transferableStack(server, blockEntity));
    }
    private static ItemStack transferableStack(ServerLevel level, BlockEntity blockEntity) {
        ItemStack stack = new ItemStack(CreateCityExpansionCore.BLAST_FURNACE_OUTPUT_PORT_ITEM.get());
        if (blockEntity instanceof BlastFurnaceOutputBlockEntity output && output.amount() > 0) {
            CompoundTag custom = new CompoundTag();
            custom.put(DATA_KEY, output.saveTank(level.registryAccess()));
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        }
        return stack;
    }
}
