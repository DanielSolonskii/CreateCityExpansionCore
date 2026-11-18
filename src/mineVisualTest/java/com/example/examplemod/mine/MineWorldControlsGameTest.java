package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.*;
import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.machine.*;
import com.example.examplemod.multiblock.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class MineWorldControlsGameTest {
    private static ServerPlayer player(ServerLevel level, BlockPos pos) {
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "InstrumentTest"), ClientInformation.createDefault()) {
            @Override public void displayClientMessage(Component message, boolean actionBar) {}
        };
        player.setPos(Vec3.atCenterOf(pos.north(2)));
        return player;
    }

    private static MineControllerBlockEntity build(ServerLevel level, BlockPos pos) {
        var origin = pos.offset(-4,-1,0);
        for (int x : new int[]{0,9}) for (int z : new int[]{0,9}) level.getChunkAt(origin.offset(x,0,z));
        BlueprintEditorWorld.place(level, MineBlueprints.current().plan(BlueprintCatalog.Tier.I), origin.offset(MineBlueprints.current().plan(BlueprintCatalog.Tier.I).anchor()), Direction.NORTH);
        return (MineControllerBlockEntity) level.getBlockEntity(pos);
    }

    @GameTest(template="auger_empty")
    public static void decorationInBlueprintAirKeepsMineAssembled(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(5, 3, 1));
        build(level, pos);
        var origin = MineMultiblock.getOrigin(pos, Direction.NORTH);
        var blueprint = MineBlueprints.current();
        int decorated = 0;
        for (var entry : blueprint.blocks(BlueprintCatalog.Tier.I).entrySet()) {
            if (!entry.getValue().isAir()) continue;
            var local = entry.getKey();
            var world = MineMultiblock.getWorldPosition(origin, Direction.NORTH,
                    local.getX(), local.getY(), local.getZ());
            level.setBlockAndUpdate(world, decorated++ % 2 == 0
                    ? Blocks.STONE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
        }
        h.assertTrue(decorated > 0, "Test fills blueprint air cells");
        h.assertTrue(MineMultiblock.validate(level, pos).assembled(), "Decoration preserves assembly: " + MineMultiblock.validate(level, pos));
        h.assertTrue(MineMultiblock.getAssemblyProgress(level, pos) == 1, "Decoration preserves full progress");
        var power = MineMultiblock.validate(level, pos).powerPort();
        level.setBlockAndUpdate(power, Blocks.AIR.defaultBlockState());
        h.assertTrue(!MineMultiblock.validate(level, pos).assembled(), "Required port is still mandatory");
        h.succeed();
    }

    @GameTest(template="auger_empty", timeoutTicks=200)
    public static void placerCityAndLocalDepositAreAutomatic(GameTestHelper h) {
        var level=h.getLevel();
        // GameTestServer has a fixed world seed but moves its test structures between runs.
        var deposits=GeologyManager.get(level).queryNearby(BlockPos.ZERO,768);
        int y=level.getMaxBuildHeight()-32;
        var pos=deposits.stream().map(d->new BlockPos(d.getCenterX(),y,d.getCenterZ()))
                .min(Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO))).orElseThrow();
        var controller=build(level,pos);
        var owner=player(level,pos); var stranger=player(level,pos);
        var cities=CityManager.get(level.getServer().overworld());
        var city=cities.createCity("Instruments "+owner.getUUID().toString().substring(0,8),owner.getUUID(),
                level.dimension().location(),h.absolutePos(new BlockPos(2000,3,0)));
        h.assertTrue(city!=null,"Create test city in an unclaimed plot");
        MineInteraction.placed(level,controller,owner);
        h.assertTrue(city.getId().equals(controller.placementCity()),"City captured from placer despite distant city location");
        var expected=GeologyManager.getRegion(level,pos).getDeposits().stream().filter(d->d.localPotentialPerHour(pos.getX(),pos.getZ())>0).findFirst().orElseThrow();
        h.assertTrue(controller.getDisplayDepositId().orElseThrow().equals(expected.getId()),"Resource selected at controller location before commissioning");
        h.assertTrue(controller.telemetry().product()!=null,"Controller has actual resource icon");
        IndustrialMineService.selectDepositSnapshot(level,pos,controller,ResourceLocation.parse("test:forged_selection"));
        h.assertTrue(controller.getDisplayDepositId().orElseThrow().equals(expected.getId()),"Legacy selection cannot replace the spatial deposit");
        var validation=MineMultiblock.validate(level,pos);
        controller.setElectricalState(MineElectricalState.NORMAL);
        for(var tier:BlueprintCatalog.Tier.values()) {
            double scale=tier.powerPercent()/100.0;
            MineInstrumentation.publish(level,controller,validation,tier,new MinePowerSnapshot(true,true,500,20*scale,10000*scale,10000*scale));
            h.assertTrue(controller.telemetry().status()==MachineTelemetry.Status.READY && controller.telemetry().power().fraction()==1,"Working power gives a checkmark for "+tier);
            MineInstrumentation.publish(level,controller,validation,tier,new MinePowerSnapshot(true,false,500,5*scale,2500*scale,10000*scale));
            h.assertTrue(controller.telemetry().status()==MachineTelemetry.Status.WARNING,"Low current gives amber warning for "+tier);
            MineInstrumentation.publish(level,controller,validation,tier,new MinePowerSnapshot(false,false,0,0,0,10000*scale));
            h.assertTrue(controller.telemetry().status()==MachineTelemetry.Status.NO_POWER,"No power gives crossed bolt for "+tier);
            MineInstrumentation.publish(level,controller,validation,tier,new MinePowerSnapshot(true,false,700,20*scale,14000*scale,10000*scale));
            h.assertTrue(controller.telemetry().status()==MachineTelemetry.Status.UNSAFE,"Unsafe voltage gives warning for "+tier);
        }
        MineInteraction.press(stranger,pos);
        h.assertTrue(!controller.isCommissioned(),"Outsider cannot register or operate city machine");
        MineInteraction.press(owner,pos);
        h.assertTrue(controller.isCommissioned() && controller.getCityId().orElseThrow().equals(city.getId())
                && controller.getActiveDepositId().orElseThrow().equals(expected.getId()),"Button registers exactly the placer city and local deposit");
        int projects=city.getProjects().size();
        MineInteraction.press(owner,pos);
        h.assertTrue(city.getProjects().size()==projects,"Repeated button press cannot duplicate project");
        h.assertTrue(!controller.isUserRequestedStart(),"No electrical supply cannot start mining");
        var loaded=new MineControllerBlockEntity(pos,controller.getBlockState());
        loaded.loadWithComponents(controller.saveWithoutMetadata(level.registryAccess()),level.registryAccess());
        h.assertTrue(loaded.placementCity().equals(city.getId()) && loaded.getProjectId().equals(controller.getProjectId())
                && !loaded.isUserRequestedStart(),"Persistent city/project survive reload without automatic start");
        loaded.initializeRedstone(true);
        h.assertTrue(!loaded.consumeRedstoneEdge(true) && loaded.consumeRedstoneEdge(false)
                && !loaded.consumeRedstoneEdge(false) && loaded.consumeRedstoneEdge(true),"Redstone uses edges, including safe high-signal reload");
        controller.setRuntimeState(IndustrialMineState.RUNNING,"test");controller.setUserRequestedStart(true);
        MineInteraction.press(owner,pos);
        h.assertTrue(!controller.isUserRequestedStart() && controller.getRuntimeState()==IndustrialMineState.STOPPING,"Physical button stops active machine");
        controller.initializeRedstone(true);controller.setRuntimeState(IndustrialMineState.RUNNING,"test");controller.setUserRequestedStart(true);
        MineInteraction.redstone(level,controller);
        h.assertTrue(!controller.isUserRequestedStart(),"Falling redstone signal stops machine");
        cities.removeProject(city,controller.getProjectId().orElseThrow());
        cities.removeCityByOwner(owner.getUUID());
        var plan=MineBlueprints.current().plan(BlueprintCatalog.Tier.I);
        var target=MineMultiblock.getOrigin(pos,Direction.NORTH).offset(plan.anchor());
        for(var part:plan.parts()) {
            if(!part.state().isAir()) level.removeBlock(plan.worldPosition(part.position(),target,Direction.NORTH),false);
        }
        h.succeed();
    }

    @GameTest(template="auger_empty", timeoutTicks=120)
    public static void mandatoryScreensAndDamageKeepBindings(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,3,1));var controller=build(level,pos);
        var owner=UUID.randomUUID();var city=CityManager.get(level.getServer().overworld()).createCity("Screen "+owner.toString().substring(0,8),owner,level.dimension().location(),pos.offset(6000,0,0));
        var project=UUID.randomUUID();var deposit=ResourceLocation.parse("test:screen_vein");var resource=ResourceLocation.parse("createcityexpansioncore:iron");
        CityManager.get(level.getServer().overworld()).addProject(city,new CityProject(project,city.getId(),CityProjectType.INDUSTRIAL_MINE,level.dimension().location(),pos,owner,deposit,resource,.5));
        controller.commission(city.getId(),project,deposit,resource);
        var validation=MineMultiblock.validate(level,pos);h.assertTrue(validation.assembled(),"Screens included in valid blueprint");
        var screenPos=MachineDisplayBlock.adjacentDisplay(validation.powerPort(),MachineDisplaySource.Role.ENERGY,level::getBlockState);
        var outputPos=MachineDisplayBlock.adjacentDisplay(validation.gateway(),MachineDisplaySource.Role.OUTPUT,level::getBlockState);
        h.assertTrue(screenPos!=null && level.getBlockEntity(screenPos) instanceof MachineDisplayBlockEntity,"Power port has an energy screen");
        h.assertTrue(outputPos!=null && level.getBlockEntity(outputPos) instanceof MachineDisplayBlockEntity,"Gateway has an output screen");
        var screenState=level.getBlockState(screenPos);
        var screen=(MachineDisplayBlockEntity)level.getBlockEntity(screenPos);
        h.assertTrue(!screen.telemetry().assembled(),"Unbound screen starts blank");
        IndustrialMineService.inspectSnapshot(level,pos,controller);
        h.assertTrue(screen.telemetry().assembled(),"Assembled machine binds energy screen");
        var output=(MachineDisplayBlockEntity)level.getBlockEntity(outputPos);
        h.assertTrue(output.telemetry().equals(controller.telemetry()),"Both instruments share authoritative controller readings");
        level.setBlockAndUpdate(screenPos,screenState.setValue(MachineDisplayBlock.ROLE,MachineDisplaySource.Role.OUTPUT));
        h.assertTrue(!MineMultiblock.validate(level,pos).assembled(),"Wrong screen role rejects assembly");
        level.setBlockAndUpdate(screenPos,screenState);
        controller.setRuntimeState(IndustrialMineState.RUNNING,"test");controller.setUserRequestedStart(true);controller.setMiningTicks(37);
        level.destroyBlock(screenPos,false);
        h.runAfterDelay(11,()->{
            h.assertTrue(!controller.isUserRequestedStart() && controller.getRuntimeState()==IndustrialMineState.ERROR,"Destroyed screen stops active mining");
            h.assertTrue(controller.getMiningTicks()==37 && controller.getCityId().orElseThrow().equals(city.getId())
                    && controller.getProjectId().orElseThrow().equals(project) && controller.getActiveDepositId().orElseThrow().equals(deposit),"Progress and all bindings survive damage");
            h.assertTrue(!output.telemetry().assembled(),"Other screen blanks after assembly is broken");
            level.setBlockAndUpdate(screenPos,screenState);
            IndustrialMineService.inspectSnapshot(level,pos,controller);
            h.assertTrue(MineMultiblock.validate(level,pos).assembled() && !controller.isUserRequestedStart(),"Repair requires explicit restart");
            h.succeed();
        });
    }

    @GameTest(template="auger_empty")
    public static void instrumentSynchronizationAndGoggles(GameTestHelper h) {
        var t=new MachineTelemetry(true,3,ResourceLocation.parse("minecraft:iron_ore"),"RUNNING","Running",
                MachineTelemetry.Status.READY,new MachineTelemetry.Power(500,20,10000,10000,500,600,17,26,8500,15000),
                new MachineTelemetry.Production(.5,2.5,120,128,256));
        h.assertTrue(MachineTelemetry.read(t.save()).equals(t),"All display values survive network NBT round-trip");
        h.assertTrue(t.power().fraction()==1 && t.production().fullness()==.5,"Correct energy and output bar scales");
        for(var role:List.of(MachineDisplaySource.Role.ENERGY,MachineDisplaySource.Role.OUTPUT)) {
            MachineDisplaySource source=new MachineDisplaySource(){public MachineTelemetry telemetry(){return t;}public Role displayRole(){return role;}};
            var lines=new ArrayList<Component>();
            h.assertTrue(source.addToGoggleTooltip(lines,false) && lines.size()>=5,"Standard Create goggles expose instrument details: "+role);
        }
        var bad=t.save();bad.putDouble("v0",Double.NaN);bad.putString("Status","INVALID");
        h.assertTrue(MachineTelemetry.read(bad).power().voltage()==0 && MachineTelemetry.read(bad).status()==MachineTelemetry.Status.INCOMPLETE,"Invalid saved readings cannot poison display");
        h.succeed();
    }
}
