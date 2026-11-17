package com.example.examplemod.electrical.bridge;

import com.drmangotea.tfmg.content.electricity.utilities.resistor.ResistorBlockEntity;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.function.Consumer;

@GameTestHolder(PowerBridge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DcBridgeDualGameTest {
    @GameTest(template="power_bridge_empty",timeoutTicks=140)
    public static void nativeWireStartingAtBridge(GameTestHelper h) { wire(h,true); }
    @GameTest(template="power_bridge_empty",timeoutTicks=140)
    public static void nativeWireEndingAtBridge(GameTestHelper h) { wire(h,false); }
    private static void wire(GameTestHelper h,boolean bridgeFirst) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));var root=place(h,pos,Direction.NORTH);root.reverse();
        var port=(TfmgBridgePort)level.getBlockEntity(pos.east());var remote=pos.east().south(3);
        level.setBlockAndUpdate(remote,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tfmg:cable_connector")).defaultBlockState()
                .setValue(com.drmangotea.tfmg.base.blocks.WallMountBlock.FACING,Direction.UP));
        level.setBlockAndUpdate(remote.below(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tfmg:creative_generator")).defaultBlockState());
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        var spool=BuiltInRegistries.ITEM.stream().filter(i->i instanceof com.drmangotea.tfmg.content.machinery.misc.winding_machine.SpoolItem s
                && !s.cableTypeKey.getPath().equals("empty")).findFirst().orElseThrow();
        var stack=new ItemStack(spool);stack.set(com.drmangotea.tfmg.registry.TFMGDataComponents.SPOOL_AMOUNT,1000);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        for(var target:bridgeFirst?new BlockPos[]{pos.east(),remote}:new BlockPos[]{remote,pos.east()}) {
            player.setPos(target.getX(),target.getY(),target.getZ()+1);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target),Direction.UP,target,false);
            var result=spool.useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
            h.assertTrue(result.consumesAction(),"Native spool accepts endpoint");
        }
        h.assertTrue(level.getBlockState(pos.east().above()).isAir(),"No extra connector block");
        h.assertTrue(port.terminal().connections.size()==1,"Bridge retains native cable");
        var saved=new net.minecraft.nbt.CompoundTag();port.write(saved,level.registryAccess(),false);
        port.terminal().connections.clear();port.read(saved,level.registryAccess(),false);
        h.assertTrue(port.terminal().connections.size()==1,"Cable survives save/read");
        var endpoint=port.terminal().getCablePosition();
        h.assertTrue(Math.abs(endpoint.y()+.5-(pos.getY()+13.5/16))<1e-6,"Cable ends at copper terminal height");
        Consumer<AddToElectricGraphEvent> circuit=event->{if(event.level!=level)return;
            var negative=new InWorldNode(0,pos);var positive=new InWorldNode(1,pos);var ret=new InWorldNode(24,pos);
            event.builder.connect(negative,ret,ElectricalProperties.resistor(.01));
            event.builder.connect(ret,positive,ElectricalProperties.resistor(500));
        };
        NeoForge.EVENT_BUS.addListener(circuit);
        h.runAfterDelay(65,()->{
            NeoForge.EVENT_BUS.unregister(circuit);
            h.assertTrue(root.outputWatts()>40,"Actual TFMG wire powers EE load: "+root.readings(true)+" / "+root.readings(false));
            var connector=(com.drmangotea.tfmg.content.electricity.connection.cables.CableConnectorBlockEntity)level.getBlockEntity(remote);
            level.destroyBlock(pos.east(),false);
            h.runAfterDelay(5,()->{h.assertTrue(connector.connections.isEmpty(),"Native endpoint cleans up dismantled bridge");h.succeed();});
        });
    }
    private static DcBridgeEntity place(GameTestHelper h, BlockPos pos, Direction facing) {
        var state=PowerBridge.BLOCK.get().defaultBlockState().setValue(DcBridgeBlock.FACING,facing);
        h.getLevel().setBlockAndUpdate(pos,state);
        PowerBridge.BLOCK.get().setPlacedBy(h.getLevel(),pos,state,null,new ItemStack(PowerBridge.ITEM.get()));
        return (DcBridgeEntity)h.getLevel().getBlockEntity(pos);
    }
    @GameTest(template="power_bridge_empty",timeoutTicks=60)
    public static void facingAndSingleDrop(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(4,2,4));
        for (var facing:Direction.Plane.HORIZONTAL) for(boolean secondary:new boolean[]{false,true}) {
            var root=place(h,pos,facing); var state=root.getBlockState(); var other=DcBridgeBlock.partner(pos,state);
            h.assertTrue(root.assembled(),"Two matching halves for "+facing);
            h.assertTrue(PowerBridge.BLOCK.get().getNodePositions(level,pos,state).size()==2,"EE has two terminals");
            h.assertTrue(PowerBridge.BLOCK.get().getNodePositions(level,other,level.getBlockState(other)).isEmpty(),"TFMG does not expose EE terminals");
            var port=(TfmgBridgePort)level.getBlockEntity(other);
            h.assertTrue(port.hasElectricitySlot(Direction.UP) && !((TfmgBridgePort)root).hasElectricitySlot(Direction.UP),"Only small section joins TFMG");
            level.destroyBlock(secondary?other:pos,true);
            h.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(other).isAir(),"Both halves removed");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3));
            int count=drops.stream().filter(e->e.getItem().is(PowerBridge.ITEM.get())).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(count==1,"Exactly one item from either half, got "+count);
            drops.forEach(ItemEntity::discard);
        }
        h.succeed();
    }
    @GameTest(template="power_bridge_empty",timeoutTicks=60)
    public static void placementRejectsObstruction(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3);
        player.setYRot(0);
        var stack=new ItemStack(PowerBridge.ITEM.get());player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);
        var ctx=new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit);
        var state=PowerBridge.BLOCK.get().getStateForPlacement(ctx);
        h.assertTrue(state!=null,"Free two-block space accepted");
        var other=DcBridgeBlock.partner(ctx.getClickedPos(),state);
        level.setBlockAndUpdate(other,Blocks.STONE.defaultBlockState());
        h.assertTrue(PowerBridge.BLOCK.get().getStateForPlacement(ctx)==null,"Occupied second half rejected");
        h.assertTrue(level.getBlockState(other).is(Blocks.STONE),"Obstruction preserved");
        level.setBlockAndUpdate(other,Blocks.AIR.defaultBlockState());
        h.assertTrue(PowerBridge.ITEM.get().place(ctx).consumesAction(),"Item places complete structure");
        h.assertTrue(((DcBridgeEntity)level.getBlockEntity(ctx.getClickedPos())).assembled(),"Item placed both sections");
        h.succeed();
    }
    @GameTest(template="power_bridge_empty",timeoutTicks=120)
    public static void eeToTfmgAndShutdown(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));var root=place(h,pos,Direction.NORTH);
        var loadPos=pos.east().above();
        level.setBlockAndUpdate(loadPos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tfmg:resistor")).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP));
        var load=(ResistorBlockEntity)level.getBlockEntity(loadPos);load.resistance=50;
        Consumer<AddToElectricGraphEvent> circuit=event->{if(event.level!=level)return;
            var negative=new InWorldNode(0,pos);var positive=new InWorldNode(1,pos);var supply=new InWorldNode(23,pos);
            event.builder.connect(negative,supply,ElectricalProperties.fromThevenin(.05,230));
            event.builder.connect(supply,positive,ElectricalProperties.resistor(.01));event.builder.defaultZeroPotential(negative,100);
        };
        NeoForge.EVENT_BUS.addListener(circuit);
        h.runAfterDelay(55,()->{
            NeoForge.EVENT_BUS.unregister(circuit);
            h.assertTrue(root.readingWatts(true)>1 && root.readingWatts(false)>1,"EE -> TFMG real load: "+root.readings(true)+" / "+root.readings(false));
            h.assertTrue(load.getData().getVoltage()>100,"TFMG top connector supplies load");
            h.assertTrue(root.readingWatts(false)<=root.readingWatts(true)+20,"No duplicated energy");
            level.destroyBlock(pos.east(),false);
            h.runAfterDelay(12,()->{h.assertTrue(level.getBlockState(pos).isAir(),"Controller removed with TFMG section");h.assertTrue(load.getData().getVoltage()==0,"Load loses power after dismantling");h.succeed();});
        });
    }
    @GameTest(template="power_bridge_empty",timeoutTicks=60)
    public static void creativeAndOrphanCleanup(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));
        var player=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        for(boolean secondary:new boolean[]{false,true}) {
            var root=place(h,pos,Direction.NORTH);
            var broken=secondary?pos.east():pos;
            PowerBridge.BLOCK.get().playerWillDestroy(level,broken,level.getBlockState(broken),player);
            level.destroyBlock(broken,false);
            h.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.east()).isAir(),"Creative removes both halves");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).isEmpty(),"Creative has no item duplication");
        }
        // Simulate a half loaded after its partner was already destroyed in another chunk.
        level.setBlockAndUpdate(pos,PowerBridge.BLOCK.get().defaultBlockState().setValue(DcBridgeBlock.TFMG,true));
        h.runAfterDelay(3,()->{
            h.assertTrue(level.getBlockState(pos).isAir(),"Orphan TFMG section cleans itself up");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).isEmpty(),"Orphan secondary never drops a second item");
            h.succeed();
        });
    }
    @GameTest(template="power_bridge_empty",timeoutTicks=120)
    public static void tfmgToEeAndLiveReadings(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));var root=place(h,pos,Direction.NORTH);root.reverse();
        var source=pos.east().above();level.setBlockAndUpdate(source,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tfmg:creative_generator")).defaultBlockState());
        Consumer<AddToElectricGraphEvent> circuit=event->{if(event.level!=level)return;
            var negative=new InWorldNode(0,pos);var positive=new InWorldNode(1,pos);var ret=new InWorldNode(24,pos);
            event.builder.connect(negative,ret,ElectricalProperties.resistor(.01));event.builder.connect(ret,positive,ElectricalProperties.resistor(500));
        };
        NeoForge.EVENT_BUS.addListener(circuit);
        h.runAfterDelay(55,()->{
            NeoForge.EVENT_BUS.unregister(circuit);
            h.assertTrue(root.outputWatts()>40,"TFMG -> EE load: "+root.readings(true)+" / "+root.readings(false));
            var port=(DcBridgeEntity)level.getBlockEntity(pos.east());h.assertTrue(port.controller()==root,"Both displays use one controller");
            var tag=new net.minecraft.nbt.CompoundTag();root.write(tag,level.registryAccess(),true);
            h.assertTrue(tag.getDouble("InputVolts")>0 && tag.getDouble("OutputVolts")>0,"Both live readings synced to client");
            level.destroyBlock(source,false);
            h.runAfterDelay(15,()->{h.assertTrue(root.outputWatts()==0,"No phantom power after input removal");h.succeed();});
        });
    }
}
