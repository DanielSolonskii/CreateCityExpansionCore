package com.example.examplemod.passenger;

import com.example.examplemod.cargo.CargoPackageAssignment;
import com.example.examplemod.cargo.CargoPointRegistry;
import com.example.examplemod.cargo.CargoTrainRegistry;
import com.example.examplemod.cargo.center.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Used only in the isolated dedicated scenario. Replace instances the same way a world load would. */
public final class PassengerRestartCheck {
    public static void checkSharedDispatch(ServerPlayer author, ServerPlayer driver, UUID lineId) {
        PassengerService.setColor(author,lineId,"cyan");
        var marked=RailwayBinder.selected(driver,RailwayBinder.TRAINS);
        if(marked.size()!=1)throw new AssertionError("Tablet retains the real train UUID");
        PassengerService.chooseTrain(driver,lineId,marked.getFirst());
        PassengerService.start(driver,lineId,20);
        var server = author.getServer();
        var lookup = server.registryAccess();
        var snapshot = PassengerData.get(server).save(new CompoundTag(),lookup);
        var restored = PassengerData.load(snapshot,lookup).lines.get(lineId);
        if (!restored.trainOperator.equals(driver.getUUID()) || !restored.owner.equals(author.getUUID()))
            throw new AssertionError("Restore the train operator and line author independently");
        if(restored.color!=net.minecraft.world.item.DyeColor.CYAN || !marked.getFirst().equals(restored.chosenTrain))
            throw new AssertionError("Saved line retains color and chosen native train");
        for (var tag : snapshot.getList("Lines",Tag.TAG_COMPOUND)) {
            var row=(CompoundTag) tag;
            row.remove("TrainOperator");row.remove("Color");row.remove("ChosenTrain");
        }
        for(var tag:snapshot.getList("Platforms",Tag.TAG_COMPOUND))((CompoundTag)tag).remove("Feature");
        var legacy=PassengerData.load(snapshot,lookup);
        var previous=legacy.lines.get(lineId);
        if (!previous.trainOperator.equals(author.getUUID()) || previous.color!=net.minecraft.world.item.DyeColor.ORANGE
                || previous.chosenTrain!=null || !previous.stops.equals(restored.stops) || !previous.train.equals(restored.train)
                || previous.stops.stream().anyMatch(id->legacy.platforms.get(id).feature()!=null))
            throw new AssertionError("Legacy platforms, train, ordered stops and operator survive optional map fields");
        PassengerService.close(author,lineId,true);
    }
    public static void reload(MinecraftServer server) {
        var storage=server.overworld().getDataStorage();
        var lookup=server.registryAccess();
        storage.set("ccec_passenger_v1",PassengerData.load(PassengerData.get(server).save(new CompoundTag(),lookup),lookup));
        storage.set("ccec_cargo_lines",CargoLineRegistry.load(CargoLineRegistry.get(server).save(new CompoundTag(),lookup),lookup));
        storage.set("ccec_cargo_charters",CargoCharters.load(CargoCharters.get(server).save(new CompoundTag(),lookup),lookup));
        storage.set("ccec_public_fleet",PublicFleetRegistry.load(PublicFleetRegistry.get(server).save(new CompoundTag(),lookup),lookup));
        storage.set(CargoTrainRegistry.DATA_NAME,CargoTrainRegistry.load(CargoTrainRegistry.get(server).save(new CompoundTag(),lookup),lookup));
    }

    public static UUID charter(ServerPlayer actor, UUID train, UUID passengerLine, UUID a, UUID b) {
        var server=actor.getServer();
        var editor = new ServerPlayer(server, actor.serverLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "LineEditor"),
                net.minecraft.server.level.ClientInformation.createDefault());
        reject("op_required", () -> PassengerService.setDescription(editor, passengerLine, "", "Denied"));
        var ops = server.getPlayerList().getOps();
        ops.add(new net.minecraft.server.players.ServerOpListEntry(editor.getGameProfile(), 2, false));
        try {
            PassengerService.setDescription(editor, passengerLine, "", "Interchange service");
            reject("stale", () -> PassengerService.setDescription(actor, passengerLine, "", "Stale operator edit"));
        } finally { ops.remove(editor.getGameProfile()); }
        reject("op_required", () -> PassengerService.setDescription(editor, passengerLine, "Interchange service", ""));
        var owner=actor.getUUID();
        if(!CargoTrainRegistry.register(server,owner,train,CargoTrainRegistry.AccessMode.PRIVATE))
            throw new AssertionError("Register the physical QA train");
        var origin=CargoPointRegistry.create(server,owner,"QA charter west");
        var destination=CargoPointRegistry.create(server,owner,"QA charter east");
        // Pre-existing terminal bindings are fixture data. Line creation, authorization and both conflicts use production APIs.
        var stops=List.of(new CargoLineRegistry.Stop(origin.id(),a),new CargoLineRegistry.Stop(destination.id(),b));
        var lookup=server.registryAccess();
        var root=CargoLineRegistry.get(server).save(new CompoundTag(),lookup);
        var bindings=root.getList("Stations",Tag.TAG_COMPOUND);
        for(var stop:stops) {
            var binding=new CompoundTag();binding.putUUID("Station",stop.station());binding.putUUID("Point",stop.point());bindings.add(binding);
        }
        server.overworld().getDataStorage().set("ccec_cargo_lines",CargoLineRegistry.load(root,lookup));
        var line=CargoLineRegistry.get(server).create(actor,"QA cargo charter",OwnerRef.player(owner),stops,Set.of(train),
                CargoLineRegistry.Mode.CHARTER,0,"");
        CargoCharters.get(server).authorize(actor,line.id(),true,false);
        reload(server);
        if (!"Interchange service".equals(PassengerData.get(server).lines.get(passengerLine).description))
            throw new AssertionError("The second operator's line description survives the live data reload");
        reject("in_use",()->PassengerService.start(actor,passengerLine,10));
        CargoCharters.get(server).authorize(actor,line.id(),false,false);
        return line.id();
    }
    public static void rejectCharter(ServerPlayer actor, UUID charter, UUID train) {
        reject("shipment_in_use",()->CargoCharters.get(actor.getServer()).authorize(actor,charter,true,false));
        ItemStack original=actor.getMainHandItem();
        var box=com.simibubi.create.content.logistics.box.PackageItem.containing(List.of());
        try {
            actor.setItemInHand(InteractionHand.MAIN_HAND,box);
            if(CargoPackageAssignment.assignHeldPackage(actor,InteractionHand.MAIN_HAND,train))
                throw new AssertionError("Held package accepted on passenger-only train after reload");
        } finally {actor.setItemInHand(InteractionHand.MAIN_HAND,original);}
    }
    private static void reject(String message,Runnable action) {
        try {action.run();}
        catch(IllegalArgumentException denied) {if(message.equals(denied.getMessage()))return;throw denied;}
        throw new AssertionError("Expected "+message);
    }
    private PassengerRestartCheck(){}
}
