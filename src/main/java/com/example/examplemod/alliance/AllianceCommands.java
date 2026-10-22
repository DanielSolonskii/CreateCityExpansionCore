package com.example.examplemod.alliance;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.example.examplemod.network.AllianceStatePayload;
@EventBusSubscriber(modid="createcityexpansioncore")
public final class AllianceCommands {
    private AllianceCommands(){}
    @SubscribeEvent public static void register(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("ccec").then(Commands.literal("alliance").requires(s->s.getEntity() instanceof ServerPlayer).executes(c->{var out=new CompoundTag();out.putBoolean("Open",true);PacketDistributor.sendToPlayer(c.getSource().getPlayerOrException(),new AllianceStatePayload(out));return 1;})));}
}
