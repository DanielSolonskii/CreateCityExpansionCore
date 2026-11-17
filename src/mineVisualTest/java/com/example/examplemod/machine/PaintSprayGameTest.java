package com.example.examplemod.machine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.network.PaintSprayFeedback;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class PaintSprayGameTest {
    @GameTest(template="auger_empty")
    public static void paintingAndSprayPacket(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(4,3,4));
        level.setBlockAndUpdate(pos,CreateCityExpansionCore.MINE_FRAME.get().defaultBlockState());
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"PaintTest"),ClientInformation.createDefault());
        player.setPos(Vec3.atCenterOf(pos.south(2)));
        var stack=new ItemStack(CreateCityExpansionCore.PANEL_PAINTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var target=Vec3.atCenterOf(pos).add(0,.5,0);
        var context=new UseOnContext(level,player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(target,Direction.UP,pos,false));
        for (var color:DyeColor.values()) {
            CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("PanelColor",color.getName()));
            h.assertTrue(stack.getItem().useOn(context).consumesAction(),"Paint succeeds for "+color);
            h.assertTrue(level.getBlockState(pos).getValue(MachinePanels.COLOR)==color,"Selected paint applied");
            var packet=new PaintSprayFeedback.Spray(level.dimension().location(),player.getId(),InteractionHand.OFF_HAND,target,color.getId());
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
            try {
                PaintSprayFeedback.Spray.CODEC.encode(buffer,packet);
                h.assertTrue(packet.equals(PaintSprayFeedback.Spray.CODEC.decode(buffer)),"Spray preserves hand, color and endpoint");
            } finally { buffer.release(); }
        }
        player.getAbilities().mayBuild=false;
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("PanelColor","white"));
        h.assertTrue(stack.getItem().useOn(context)==InteractionResult.FAIL,"No spray/paint without build permission");
        h.assertTrue(level.getBlockState(pos).getValue(MachinePanels.COLOR)==DyeColor.BLACK,"Denied painting leaves block unchanged");
        h.succeed();
    }
}
