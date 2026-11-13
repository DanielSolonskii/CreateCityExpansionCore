package com.example.examplemod.machine;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MachineDisplayBlockEntity extends BlockEntity implements MachineDisplaySource {
    private BlockPos controller;
    public MachineDisplayBlockEntity(BlockPos pos, BlockState state) { super(CreateCityExpansionCore.TECHNICAL_SCREEN_ENTITY.get(), pos, state); }
    public void bind(BlockPos pos) {
        if (pos.equals(controller)) return;
        controller = pos.immutable(); setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public Role displayRole() { return getBlockState().getValue(MachineDisplayBlock.ROLE); }
    @Override public MachineTelemetry telemetry() {
        if (level == null || controller == null || !level.hasChunkAt(controller)
                || !(level.getBlockEntity(controller) instanceof MachineDisplaySource source)) return MachineTelemetry.EMPTY;
        if (source.displayRole() != Role.CONTROLLER) return MachineTelemetry.EMPTY;
        return source.telemetry();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); if (controller != null) tag.putLong("Controller", controller.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
