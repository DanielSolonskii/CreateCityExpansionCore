package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.*;
import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.multiblock.*;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class MineRestartGameTest {
    @GameTest(template="auger_empty", timeoutTicks=200)
    public static void resumesOnlyAfterSupplyReturns(GameTestHelper h) { restart(h, false); }

    @GameTest(template="auger_empty", timeoutTicks=200)
    public static void unsafeSupplyCancelsRestart(GameTestHelper h) { restart(h, true); }

    private static void restart(GameTestHelper h, boolean unsafe) {
        var level=h.getLevel();
        // GameTest randomizes its arena millions of blocks away; use deterministic geology sites.
        var base=new BlockPos(unsafe?2048:0,80,0);
        BlockPos found=null;
        search: for(int x=0;x<1024;x+=8) for(int z=0;z<1024;z+=8) {
            var p=base.offset(x,0,z);
            if(GeologyManager.getRegion(level,p).getDeposits().stream()
                    .anyMatch(d->d.localPotentialPerHour(p.getX(),p.getZ())>0)) {found=p;break search;}
        }
        h.assertTrue(found!=null,"Real deposit found");
        var pos=found;
        var origin=pos.subtract(MineBlueprints.CONTROLLER);
        var plan=MineBlueprints.current().plan(BlueprintCatalog.Tier.I);
        // The test uses the actual editable blueprint, including all its occupied chunks.
        var chunks=plan.parts().stream().map(part->new net.minecraft.world.level.ChunkPos(origin.offset(part.position()))).distinct().toList();
        for(var chunk:chunks) {level.setChunkForced(chunk.x,chunk.z,true);level.getChunk(chunk.x,chunk.z);}
        for(var part:plan.parts()) level.destroyBlock(origin.offset(part.position()),false);
        BlueprintEditorWorld.place(level,plan,origin.offset(plan.anchor()),Direction.NORTH);
        var controller=(MineControllerBlockEntity)level.getBlockEntity(pos);
        var validation=MineMultiblock.validate(level,pos);
        h.assertTrue(validation.assembled(),"Actual mine blueprint assembled: "+validation.message());
        var deposit=GeologyManager.getRegion(level,pos).getDeposits().stream()
                .filter(d->d.localPotentialPerHour(pos.getX(),pos.getZ())>0).findFirst().orElseThrow();
        var owner=UUID.randomUUID();var project=UUID.randomUUID();
        var cities=CityManager.get(level.getServer().overworld());
        var city=cities.createCity("Restart "+owner.toString().substring(0,8),owner,level.dimension().location(),
                h.absolutePos(new BlockPos(unsafe?20000:40000,80,0)));
        h.assertTrue(city!=null,"Test city created outside previous claims");
        cities.addProject(city,new CityProject(project,city.getId(),CityProjectType.INDUSTRIAL_MINE,
                level.dimension().location(),pos,owner,deposit.getId(),deposit.getResourceId(),deposit.getGrade()));
        controller.commission(city.getId(),project,deposit.getId(),deposit.getResourceId());
        controller.setUserRequestedStart(true);
        controller.setRuntimeState(IndustrialMineState.RUNNING,"Before restart");
        controller.setStartupTicks(MineControllerBlockEntity.STARTUP_DURATION_TICKS);
        controller.setMiningTicks(7);
        reload(h,controller);
        h.assertTrue(controller.getRuntimeState()==IndustrialMineState.STOPPED && controller.getStartupTicks()==0
                && controller.isUserRequestedStart(),"Reload restores intent but resets live state and startup");
        h.assertTrue(controller.getProjectId().orElseThrow().equals(project)
                && controller.getActiveDepositId().orElseThrow().equals(deposit.getId()),"Bindings retained");
        h.runAfterDelay(15,()->{
            h.assertTrue(controller.getRuntimeState()==IndustrialMineState.STOPPED && controller.isUserRequestedStart()
                    && controller.getMiningTicks()==7,"No mining until supply returns; intent retained");
            reload(h,controller);
            h.assertTrue(controller.isUserRequestedStart(),"Second reload during waiting keeps intent");
            var port=validation.powerPort();
            Consumer<AddToElectricGraphEvent> circuit=event->{
                if(event.level!=level)return;
                var negative=new InWorldNode(0,port);var positive=new InWorldNode(1,port);var source=new InWorldNode(42,port);
                event.builder.connect(negative,source,ElectricalProperties.fromThevenin(.01,unsafe?700:510));
                event.builder.connect(source,positive,ElectricalProperties.resistor(.01));
                event.builder.defaultZeroPotential(negative,100);
            };
            NeoForge.EVENT_BUS.addListener(circuit);
            h.runAfterDelay(145,()->{
                NeoForge.EVENT_BUS.unregister(circuit);
                for(var chunk:chunks) level.setChunkForced(chunk.x,chunk.z,false);
                if(unsafe) {
                    h.assertTrue(controller.getRuntimeState()==IndustrialMineState.OVERLOAD && !controller.isUserRequestedStart(),
                            "Unsafe supply cancels restart: "+controller.getStatusMessage());
                    h.assertTrue(controller.getMiningTicks()==7,"No production on unsafe supply");
                    reload(h,controller);
                    h.assertTrue(!controller.isUserRequestedStart(),"Trip remains stopped after another reload");
                } else {
                    h.assertTrue(controller.getRuntimeState()==IndustrialMineState.RUNNING
                            && controller.getStartupTicks()==MineControllerBlockEntity.STARTUP_DURATION_TICKS,
                            "Full startup before running: "+controller.getStatusMessage());
                    h.assertTrue(controller.getMiningTicks()>7,"Mining resumes after startup: "+controller.getMiningTicks());
                }
                level.destroyBlock(pos,false);
                h.succeed();
            });
        });
    }

    @GameTest(template="auger_empty")
    public static void manualStopAndLegacySavesDoNotAutostart(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,3,3));
        level.setBlockAndUpdate(pos,CreateCityExpansionCore.MINE_CONTROLLER.get().defaultBlockState());
        var controller=(MineControllerBlockEntity)level.getBlockEntity(pos);
        controller.commission(UUID.randomUUID(),UUID.randomUUID(),ResourceLocation.parse("test:deposit"),ResourceLocation.parse("test:iron"));
        controller.setUserRequestedStart(true);
        reload(h,controller);
        IndustrialMineService.controlSnapshot(level,pos,controller,MineControlCommand.STOP);
        reload(h,controller);
        h.assertTrue(!controller.isUserRequestedStart(),"STOP cancels pending restart even while stopped");
        controller.setUserRequestedStart(true);
        IndustrialMineService.controlSnapshot(level,pos,controller,MineControlCommand.EMERGENCY);
        reload(h,controller);
        h.assertTrue(!controller.isUserRequestedStart(),"Emergency stop persists");
        CompoundTag legacy=controller.saveWithoutMetadata(level.registryAccess());legacy.remove("UserRequestedStart");
        controller.loadWithComponents(legacy,level.registryAccess());
        h.assertTrue(!controller.isUserRequestedStart(),"Legacy save without intent stays stopped");
        legacy.putBoolean("UserRequestedStart",true);legacy.putBoolean("Commissioned",false);
        controller.loadWithComponents(legacy,level.registryAccess());
        h.assertTrue(!controller.isUserRequestedStart(),"Uncommissioned machine cannot restore intent");
        h.succeed();
    }

    private static void reload(GameTestHelper h, MineControllerBlockEntity controller) {
        controller.loadWithComponents(controller.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
    }
}
