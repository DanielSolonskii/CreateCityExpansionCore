package com.example.examplemod.machine;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class PaintUseGameTest {
    @GameTest(template="auger_empty")
    public static void paintAndPaletteDoNotSwing(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(4,3,4));
        level.setBlockAndUpdate(pos,CreateCityExpansionCore.MINE_FRAME.get().defaultBlockState());
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"PaintNoSwing"),ClientInformation.createDefault());
        var stack=new ItemStack(CreateCityExpansionCore.PANEL_PAINTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var context=new UseOnContext(level,player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        var result=stack.getItem().useOn(context);
        h.assertTrue(result.consumesAction() && !result.shouldSwing(),"Painting consumes use without arm swing");
        h.assertTrue(!stack.getItem().use(level,player,InteractionHand.MAIN_HAND).getResult().shouldSwing(),"Palette does not swing");
        h.succeed();
    }
}
