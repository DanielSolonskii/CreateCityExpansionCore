package com.example.examplemod.passenger;

import com.example.examplemod.cargo.CargoTrainRegistry;
import com.example.examplemod.cargo.center.*;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.trains.RailwaySavedData;
import net.minecraft.server.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Test-only mod source, explicitly enabled in two successive dedicated JVMs with the same isolated world. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class PassengerProcessRestartCheck {
    @SubscribeEvent public static void started(ServerStartedEvent event) throws Exception {
        var role=System.getProperty("ccec.qa.restartRole","");
        if(role.isEmpty())return;
        var server=event.getServer();
        var root=Path.of(System.getProperty("ccec.qa.restartRoot")).toRealPath();
        var world=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!world.equals(root.resolve("world")) || !root.getParent().endsWith("map-stage6") ||
                !root.getFileName().toString().startsWith("restart-"))
            throw new AssertionError("Expected disposable stage 6 world");
        var marker=root.resolve("service-before-restart.txt");
        var data=PassengerData.get(server);
        var fleet=PublicFleetRegistry.get(server);
        var sorted=data.lines.values().stream().sorted(java.util.Comparator.comparing(l->l.name)).toList();
        if(sorted.size()!=2)throw new AssertionError("Expected two real lines from the acceptance scene");
        var passenger=sorted.getFirst();var alternate=sorted.getLast();
        var actor=new ServerPlayer(server,server.overworld(),new GameProfile(passenger.owner,"JourneyRider"),ClientInformation.createDefault());
        var physical=RailwaySavedData.load(server).getTrains().get(passenger.train);
        var shipment=CargoShipmentRegistry.get(server).all().stream().filter(s->s.parcelCount()>0).findFirst().orElseThrow();
        if(physical==null || !PassengerService.assigned(server,physical.id) || !physical.runtime.isAutoSchedule ||
                physical.runtime.getSchedule()==null || !physical.runtime.getSchedule().cyclic ||
                !CargoSchedulePlan.shape(physical.runtime.getSchedule().write(server.registryAccess())).equals(passenger.schedule))
            throw new AssertionError("World must retain the passenger reservation and its actual Create schedule");
        try {
            fleet.publish(actor,physical.id,OwnerRef.player(actor.getUUID()),passenger.stops.getFirst(),0,0);
            throw new AssertionError("Cargo stole passenger assignment");
        }catch(IllegalArgumentException expected){if(!"shipment_in_use".equals(expected.getMessage()))throw expected;}
        String identity=passenger.id+" "+passenger.generation+" "+physical.id+" "+shipment.id()+" "+shipment.parcelCount();
        if(role.equals("write")){
            var cargoTrain=alternate.train;
            PassengerService.close(actor,alternate.id,false);
            if(CargoTrainRegistry.find(server,cargoTrain).isEmpty())
                CargoTrainRegistry.register(server,actor.getUUID(),cargoTrain,CargoTrainRegistry.AccessMode.PRIVATE);
            fleet.publish(actor,cargoTrain,OwnerRef.player(actor.getUUID()),alternate.stops.getFirst(),0,0);
            Files.writeString(marker,identity+" "+cargoTrain);
            server.saveEverything(true,true,true);
        }else if(role.equals("read")){
            var saved=Files.readString(marker);
            var cargoTrain=UUID.fromString(saved.substring(saved.lastIndexOf(' ')+1));
            var restoredCargo=RailwaySavedData.load(server).getTrains().get(cargoTrain);
            if(!saved.equals(identity+" "+cargoTrain) || alternate.train!=null || PassengerService.assigned(server,cargoTrain) ||
                    !fleet.service(cargoTrain).orElseThrow().enabled() || restoredCargo==null || restoredCargo.runtime.getSchedule()!=null)
                throw new AssertionError("Passenger train, public cargo fleet and actual parcel must survive a new JVM");
            if(passenger.clock.measured(server.overworld().getGameTime()))throw new AssertionError("Old observations survived restart");
        }else throw new AssertionError("Unknown process role");
        com.mojang.logging.LogUtils.getLogger().info("PASS dedicated restart {} pid={} {}",role,ProcessHandle.current().pid(),identity);
        server.halt(false);
    }
    private PassengerProcessRestartCheck(){}
}
