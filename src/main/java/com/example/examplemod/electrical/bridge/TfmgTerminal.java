package com.example.examplemod.electrical.bridge;

import com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity;
import com.drmangotea.tfmg.content.electricity.connection.cables.CablePos;
import com.drmangotea.tfmg.base.blocks.WallMountBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native cable endpoint view of the small section, not an extra world block/entity. */
public final class TfmgTerminal extends CableConnectorBlockEntity {
    private final TfmgBridgePort port;
    TfmgTerminal(TfmgBridgePort port) {
        super(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("tfmg:cable_connector")), port.getBlockPos(), net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse("tfmg:cable_connector")).defaultBlockState()
                .setValue(WallMountBlock.FACING, Direction.UP));
        this.port=port;
        this.data=port.getData();
    }
    public TfmgBridgePort port() { return port; }
    @Override public void onConnected() {
        // Cable traversal changes network IDs without adding the endpoint itself.
        // Register the real converter load/source before propagating the wire network.
        getOrCreateElectricNetwork().add(port);
        super.onConnected();
    }
    public static BlockEntity view(BlockEntity entity) {
        return entity instanceof TfmgBridgePort port && port.isTfmgSection() ? port.terminal() : entity;
    }
    @Override public boolean hasElectricitySlot(Direction side) { return port.hasElectricitySlot(side); }
    @Override public void sendData() { port.sendData(); }
    @Override public void setChanged() { if (port!=null) port.setChanged(); }
    @Override public CablePos getCablePosition() {
        var front=port.getBlockState().getValue(DcBridgeBlock.FACING);
        // Native wire renderer adds half a block to every CablePos component.
        return new CablePos(worldPosition.getX()-front.getStepX()*.125,
                worldPosition.getY()+13.5/16-.5,worldPosition.getZ()-front.getStepZ()*.125);
    }
}
