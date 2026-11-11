package com.example.examplemod.cargo.center;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.UUID;
@EventBusSubscriber(modid="createcityexpansioncore")
public final class CenterCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("ccec").then(Commands.literal("logistics")
            .executes(c->{var data=new CompoundTag();data.putUUID("Request",UUID.randomUUID());data.putString("Action","OPEN");CenterService.handle(c.getSource().getPlayerOrException(),data);return 1;})
            .then(Commands.literal("cancel").then(Commands.argument("shipment",UuidArgument.uuid())
                .executes(c->{c.getSource().sendSuccess(()->Component.translatable("message.createcityexpansioncore.logistics.cancel_confirm"),false);return 0;})
                .then(Commands.literal("confirm").executes(c->{try{CargoFulfillment.cancel(c.getSource().getPlayerOrException(),UuidArgument.getUuid(c,"shipment"));c.getSource().sendSuccess(()->Component.translatable("message.createcityexpansioncore.logistics.cancel_requested"),false);return 1;}catch(IllegalArgumentException|java.util.NoSuchElementException invalid){c.getSource().sendFailure(failure(invalid));return 0;}}))))
            .then(Commands.literal("recover").then(Commands.literal("confirm").executes(c->{try{CargoParcelRecovery.recover(c.getSource().getPlayerOrException());c.getSource().sendSuccess(()->Component.translatable("message.createcityexpansioncore.logistics.recovered"),false);return 1;}catch(IllegalArgumentException|java.util.NoSuchElementException invalid){c.getSource().sendFailure(failure(invalid));return 0;}})))));
    }
    private static Component failure(RuntimeException failure){
        String code=java.util.Objects.toString(failure.getMessage(),"");
        if(!code.matches("shipment_[a-z_]+"))code="shipment_not_found";
        return Component.translatable("gui.createcityexpansioncore.logistics.error."+code);
    }
}
