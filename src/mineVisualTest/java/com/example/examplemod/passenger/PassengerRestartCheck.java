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
