package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.machine.MachinePanels;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import com.example.examplemod.multiblock.MineMultiblock;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.UUID;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class PanelPaintGameTest {
    @GameTest(template = "auger_empty")
    public static void paintedMachineKeepsItsAssemblyAndBlockEntities(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var controller = origin.offset(4, 1, 0);
        BlueprintEditorWorld.place(level, MineBlueprints.current().plan(Tier.I),
                origin.offset(MineBlueprints.ANCHOR), Direction.NORTH);
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "PaintTest"), ClientInformation.createDefault());
        var tool = new ItemStack(CreateCityExpansionCore.PANEL_PAINTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putString("PanelColor", "blue"));

        var paintedBlocks = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(9, 2, 9))) {
            var state = level.getBlockState(pos);
            if (!state.hasProperty(MachinePanels.COLOR) || !paintedBlocks.add(state.getBlock())) continue;
            var blockEntity = level.getBlockEntity(pos);
            player.setPos(Vec3.atCenterOf(pos.north()));
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(tool.getItem().onItemUseFirst(tool, context).consumesAction(), "Paint applied to " + state);
            helper.assertTrue(level.getBlockState(pos) == state.setValue(MachinePanels.COLOR, DyeColor.BLUE),
                    "Paint changes only the color property");
            helper.assertTrue(level.getBlockEntity(pos) == blockEntity, "Existing block entity is retained");
        }
        helper.assertTrue(paintedBlocks.size() == 6, "All six equipment blocks accept paint");
        helper.assertTrue(tool.getCount() == 1 && !tool.isDamageableItem(), "Painting uses no resource or durability");
        helper.assertTrue(MineMultiblock.validate(level, controller).assembled(), "Paint does not change mine assembly");
        helper.succeed();
    }
}
