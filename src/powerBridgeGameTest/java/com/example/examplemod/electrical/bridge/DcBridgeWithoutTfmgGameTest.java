package com.example.examplemod.electrical.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PowerBridge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DcBridgeWithoutTfmgGameTest {
    @GameTest(template = "power_bridge_empty", timeoutTicks = 90)
    public static void startsWithoutTfmg(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(pos, PowerBridge.BLOCK.get().defaultBlockState());
        helper.assertTrue(!PowerBridge.tfmgSupported(), "The fixture has no TFMG");
        helper.assertTrue(level.getBlockEntity(pos) instanceof DcBridgeEntity bridge && !bridge.enabled(),
                "The saved bridge can load without linking TFMG");
        BlockPos eeConverter = pos.west(2);
        level.setBlockAndUpdate(eeConverter, BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("electroenergetics:converter")).defaultBlockState());
        helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, eeConverter, Direction.EAST) == null,
                "Server FE remains blocked when TFMG is absent");
        helper.succeed();
    }
}
