package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.electrical.DrillUnitBlock;
import com.example.examplemod.electrical.TechnicalBlock;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class MineEffectLayoutGameTest {
    @GameTest(template="auger_empty", timeoutTicks=400)
    public static void effectPartsSyncAndClearOnDamage(GameTestHelper helper) {
        var level=helper.getLevel();
        var origin=helper.absolutePos(new BlockPos(1,2,1));
        var pos=origin.offset(4,1,0);
        var plan = MineBlueprints.current().plan(Tier.I);
        var anchor = origin.offset(plan.anchor());
        for (var part : plan.parts()) level.getChunkAt(plan.worldPosition(part.position(), anchor, Direction.NORTH));
        BlueprintEditorWorld.place(level, plan, anchor, Direction.NORTH);
        var controller=(MineControllerBlockEntity)level.getBlockEntity(pos);
        helper.assertTrue(controller!=null && MineMultiblock.validate(level,pos).assembled(),"Valid effect fixture");
        MineEffectLayout.publish(level,controller,true,Tier.I);
        helper.assertTrue(!controller.effectDrills().isEmpty() && !controller.effectVents().isEmpty(),"Both effect types found");
        helper.assertTrue(new java.util.HashSet<>(controller.effectDrills()).size()==controller.effectDrills().size(),"One anchor per auger");
        for(var drill:controller.effectDrills()) {
            var state=level.getBlockState(drill);
            helper.assertTrue(state.getBlock() instanceof DrillUnitBlock,"Drill anchor is actual auger");
            helper.assertTrue(state.getValue(DrillUnitBlock.PART)==DrillUnitBlock.Part.SINGLE
                    || DrillUnitBlock.origin(drill,state).equals(drill),"Canonical lower corner");
        }
        for(var vent:controller.effectVents()) helper.assertTrue(level.getBlockState(vent).getBlock() instanceof TechnicalBlock,"Actual vent");
        var copy=new MineControllerBlockEntity(pos,controller.getBlockState());
        copy.handleUpdateTag(controller.getUpdateTag(level.registryAccess()),level.registryAccess());
        helper.assertTrue(copy.effectDrills().equals(controller.effectDrills()) && copy.effectVents().equals(controller.effectVents()),"Client packet preserves exact anchors");
        level.destroyBlock(controller.effectDrills().getFirst(),false);
        helper.assertTrue(!MineMultiblock.validate(level,pos).assembled(),"Damage invalidates machine");
        MineEffectLayout.publish(level,controller,false,Tier.I);
        helper.assertTrue(controller.effectDrills().isEmpty() && controller.effectVents().isEmpty(),"Damage clears both effect lists");
        copy.handleUpdateTag(controller.getUpdateTag(level.registryAccess()),level.registryAccess());
        helper.assertTrue(copy.effectDrills().isEmpty() && copy.effectVents().isEmpty(),"Empty packet clears client effects");
        helper.succeed();
    }
}
