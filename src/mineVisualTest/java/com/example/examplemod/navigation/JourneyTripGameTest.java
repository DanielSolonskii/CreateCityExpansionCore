package com.example.examplemod.navigation;

import com.example.examplemod.city.CityMapSavedData;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.city.CityPublicAtlas;
import com.example.examplemod.citymap.CityMapFeature;
import com.example.examplemod.citymap.CityMapNetwork;
import com.example.examplemod.citymap.CityMapService;
import com.example.examplemod.network.RequestCityWorldMapPayload;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.projects.*;
import com.example.examplemod.network.ProjectPlanRequest;
import com.example.examplemod.community.ProjectPlanService;
import com.example.examplemod.cargo.*;
import com.example.examplemod.cargo.center.*;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.example.examplemod.passenger.PassengerService;
import com.example.examplemod.passenger.PassengerRestartCheck;
import com.example.examplemod.places.AtlasPlace;
import com.example.examplemod.places.PlaceService;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Temporary test-world railway. Create assembles the carriage and drives both directions on a native schedule. */
@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class JourneyTripGameTest {
    public record Rail(String name,UUID train,UUID west,UUID east,BlockPos westEntrance,BlockPos eastEntrance) {}
    public record Interactive(UUID player,UUID home,UUID goal,BlockPos start,BlockPos entrance,
                              List<Rail> rails,UUID project,UUID shipment,UUID parcel) {}
    private static volatile Interactive interactive;
    private static volatile UUID completedGoal;
    private static volatile UUID interactiveOffer;
    private static volatile String interactiveFailure="";
    public static String interactiveFailure(){return interactiveFailure;}
    public static UUID interactiveOffer(){return interactiveOffer;}
    public static Interactive interactive(){return interactive;}
    /** QA client calls this only after its normal trip payload reports completion. */
    public static void completed(UUID player,UUID goal){
        var scene=interactive;
        if(scene!=null && scene.player.equals(player) && scene.goal.equals(goal))completedGoal=goal;
    }
    @GameTest(template="atlas_interchange",timeoutTicks=4800)
    public static void walkRideAndArriveAtEntrance(GameTestHelper helper) {
        new JourneyTripGameTest(helper).start();
    }

    /** Used only by the optional graphical card-return scenario, without setting up a railway. */
    public static void prepareInfrastructureCards(GameTestHelper test) {
        var scene=new JourneyTripGameTest(test);
        test.assertTrue(scene.interactiveRun,"Card-return fixture needs its isolated graphical client");
        scene.level.setBlockAndUpdate(scene.start.below(),Blocks.STONE.defaultBlockState());
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AtlasSurveyor");
        scene.member=new ServerPlayer(scene.level.getServer(),scene.level,profile,ClientInformation.createDefault());
        scene.checked("Active shipment and collaborative project",scene::industry);
        test.startSequence().thenWaitUntil(()->test.assertTrue(scene.delivery.packaged()==3 && scene.delivery.parcelCount()==1,
                "Waiting for the physical router to record its package")).thenExecute(scene::openInteractive).thenSucceed();
    }

    private final GameTestHelper test;
    private final ServerLevel level;
    private final ServerPlayer actor;
    private final boolean interactiveRun;
    private final BlockPos base,start,goal;
    private record Service(String name, StationBlockEntity a, StationBlockEntity b, BlockPos west, BlockPos east) {}
    private record Ride(Service service, Train train, UUID from, UUID to, long departure) {}
    private final java.util.Map<Service, Train> trains=new java.util.LinkedHashMap<>();
    private final java.util.Map<Service, UUID> lines=new java.util.HashMap<>();
    private final java.util.List<Ride> itinerary=new java.util.ArrayList<>();
    private final java.util.Map<UUID,PassengerService.Ride> expectedRuns=new java.util.HashMap<>();
    private final java.util.Map<UUID,String> observedDocks=new java.util.HashMap<>();
    private final java.util.Set<UUID> boarded=new java.util.HashSet<>(), left=new java.util.HashSet<>(), exited=new java.util.HashSet<>();
    private com.example.examplemod.cargo.center.CargoShipmentRegistry.Shipment delivery;
    private ServerPlayer member;
    private UUID project;
    private final UUID home=UUID.randomUUID(),shop=UUID.randomUUID();
    private UUID selectedPlace;
    private TripNetwork.Query query;
    private CompoundTag reply;
    private String observedPhase="";
    private UUID lastVehicle;
    private boolean reachedStationEntrance;
    private record DistrictWalk(ServerPlayer rider, TripNetwork.Query query, BlockPos bend, BlockPos vestibule,
                                BlockPos boarding, BlockPos otherBoarding, String selectedName) {}
    private DistrictWalk districtWalk;
    private CompoundTag districtReply;
    private boolean districtBegan;

    private JourneyTripGameTest(GameTestHelper test) {
        this.test=test;level=test.getLevel();interactiveRun=Boolean.getBoolean("ccec.qa.interactiveRide");
        if(interactiveRun){
            String configured=System.getProperty("ccec.qa.interactiveRoot","");
            var root=java.nio.file.Path.of(configured).toAbsolutePath().normalize();
            var world=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
            boolean qaRoot=false;
            for(var path=root;path!=null && path.getParent()!=null;path=path.getParent())
                if(path.getFileName().toString().equals("map-stage6") && path.getParent().getFileName().toString().equals("build"))qaRoot=true;
            if(configured.isBlank() || !qaRoot || !level.getServer().isSingleplayer() || !world.startsWith(root))
                throw new IllegalArgumentException("Interactive GameTest requires its own integrated world under build/map-stage6");
            interactive=null;completedGoal=null;interactiveOffer=null;interactiveFailure="";
        }
        // A GameTest server has no client negotiating the mod's login-only payloads.
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"JourneyRider");
        actor=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        actor.connection=new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                actor, CommonListenerCookie.createInitial(profile,false)){
            // The dedicated fixture consumes response data server-side; no client performed a network handshake.
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        // A train with a passenger checks entity ticking sixteen blocks ahead in either direction.
        // Keep the railway in the middle of vanilla's GameTest footprint, including its neighbors.
        var point=test.absolutePos(new BlockPos(24,1,24));
        base=new BlockPos(point.getX() & ~15,level.getMaxBuildHeight()-26,point.getZ() & ~15);
        start=base.offset(2,0,6);goal=base.offset(13,1,9);
    }
    private void start() {
        test.assertTrue(level.isPositionEntityTicking(base.west(12)) && level.isPositionEntityTicking(base.east(27)),
                "Native carriage lookahead is inside the ticking test area");
        for(int x=0;x<16;x++) for(int z=0;z<16;z++)
            level.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        level.setBlockAndUpdate(goal.below(),Blocks.STONE.defaultBlockState());
        for(int side : new int[]{1,-1}) {
            int track=side==1?2:13, stationZ=track+4*side, platformZ=track+2*side;
            for(int x=0;x<16;x++) level.setBlockAndUpdate(base.offset(x,0,track),
                    create("track").setValue(TrackBlock.SHAPE,TrackShape.XO));
            var west=base.offset(4,1,platformZ);var east=base.offset(11,1,platformZ);
            level.setBlockAndUpdate(west.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(east.below(),Blocks.STONE.defaultBlockState());
            var service=new Service(side==1?"South":"North",station(4,stationZ,track,true),station(11,stationZ,track,false),west,east);
            trains.put(service,null);
        }
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AtlasSurveyor");
        member=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        var commands = level.getServer().getCommands().getDispatcher().getRoot().getChild("ccec").getChild("passenger");
        test.assertTrue(!commands.canUse(member.createCommandSourceStack()),"Passenger setup commands require OP");
        try {
            PassengerService.create(member,"Unauthorized");
            throw new AssertionError("Non-OP created a line");
        } catch (IllegalArgumentException denied) {
            test.assertTrue("op_required".equals(denied.getMessage()),"Line creation requires OP");
        }
        level.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(actor.getGameProfile(),2,false));
        if(interactiveRun && Boolean.getBoolean("ccec.qa.passengerEditor")){
            prepareEditor();
            return;
        }
        place(home,start);place(shop,goal);standAt(start);
        var viewId=UUID.randomUUID();
        com.example.examplemod.city.CityPublicAtlas.open(actor,new com.example.examplemod.network.RequestCityWorldMapPayload(viewId,UUID.randomUUID()));
        var scope=com.example.examplemod.city.CityPublicAtlas.view(actor,viewId);
        test.assertTrue(scope!=null,"Open an authorized atlas view before reading places");
        var directory=com.example.examplemod.places.PlaceService.handle(actor,new com.example.examplemod.places.PlaceNetwork.Request(UUID.randomUUID(),
                viewId,scope.worldId(),scope.dimension(),com.example.examplemod.places.PlaceNetwork.Action.LIST,home,0,null,null));
        var matches=directory.rows().stream().filter(p->p.name().equals("QA depot")).toList();
        test.assertTrue("ok".equals(directory.status()) && matches.size()==2,"Find both identically named places in the authorized directory");
        var repeat=PlaceService.handle(actor,new com.example.examplemod.places.PlaceNetwork.Request(UUID.randomUUID(),
                viewId,scope.worldId(),scope.dimension(),com.example.examplemod.places.PlaceNetwork.Action.LIST,directory.nonce(),0,null,null));
        test.assertTrue("unchanged".equals(repeat.status()) && repeat.rows().isEmpty(),"Acknowledged unchanged catalog omits its rows");
        selectedPlace=matches.stream().filter(p->p.entrances().contains(goal)).findFirst().orElseThrow().id();
        test.assertTrue(selectedPlace.equals(shop),"Select the place with this surveyed entrance");
        com.example.examplemod.city.CityPublicAtlas.close(actor,viewId);
        request(TripPlanner.Mode.WALK);
        test.startSequence()
                .thenWaitUntil(()->test.assertTrue(reply!=null && "options".equals(reply.getString("State")),"Walking comparison: "+reply))
                .thenExecute(()->{
                    test.assertTrue(reply.getList("Options",Tag.TAG_COMPOUND).getCompound(0).getInt("Rides")==0,"Walking option");
                    send(query.action(TripNetwork.Action.CANCEL,0));
                    checked("Active delivery and collaborative project",this::industry);
                })
                .thenWaitUntil(()->test.assertTrue(delivery!=null && delivery.packaged()==3 && delivery.parcelCount()==1,
                        delivery==null?"Delivery setup failed":"Live Cargo router observes its real package: "+delivery.stage()+" / "+delivery.history()))
                .thenWaitUntil(()->test.assertTrue(trains.keySet().stream().allMatch(s->s.a.getStation()!=null && s.b.getStation()!=null),
                        "Create registers four physical stations"))
                .thenExecute(()->checked("Assembly",()->trains.keySet().forEach(this::assemble)))
                .thenWaitUntil(()->{
                    for(var s:trains.keySet()){
                        var t=s.a.getStation().getPresentTrain();
                        test.assertTrue(t!=null && t.doubleEnded && t.hasForwardConductor() && t.hasBackwardConductor(),
                                "Native train and conductors "+s.name);
                        trains.put(s,t);
                    }
                })
                .thenExecute(()->checked("Passenger services",()->trains.forEach(this::launch)))
                .thenWaitUntil(()-> {
                    test.assertTrue(interactiveRun || reachedStationEntrance, "City station route ends at its saved passenger entrance");
                    var visible=PassengerService.departures(member).stream().map(PassengerService.Ride::line).distinct().toList();
                    test.assertTrue(visible.containsAll(lines.values()),"Both public services must complete their own cycle");
                })
                .thenExecute(() -> { if (!interactiveRun) checked("Linked district and two physical platforms", this::checkDistrict); })
                .thenWaitUntil(() -> { if (!interactiveRun) verifyDistrict(); })
                .thenExecute(()->{standAt(start);if(interactiveRun)openInteractive();else request(TripPlanner.Mode.MIXED);})
                .thenWaitUntil(()->test.assertTrue(interactiveRun || reply!=null && "options".equals(reply.getString("State")),"Mixed options: "+reply))
                .thenExecute(()->{if(!interactiveRun)choose();})
                .thenWaitUntil(()->{if(interactiveRun)followInteractive();else follow();})
                .thenSucceed();
    }
    private void openInteractive(){
        var players=level.getServer().getPlayerList().getPlayers();
        test.assertTrue(players.size()==1,"Exactly one connected QA client");
        var driver=players.getFirst();
        var city=CityManager.get(level.getServer().overworld()).getMemberCity(actor.getUUID());
        city.addMember(driver.getUUID());
        driver.teleportTo(level,start.getX()+.5,start.getY(),start.getZ()+.5,0,0);
        var rails=trains.entrySet().stream().map(e->{
            var s=e.getKey();
            return new Rail(s.name,e.getValue().id,s.a.getStation().id,s.b.getStation().id,s.west,s.east);
        }).toList();
        var parcel=delivery.parcelPositions().getFirst().getUUID("Id");
        interactive=new Interactive(driver.getUUID(),home,shop,start,goal,rails,project,delivery.id(),parcel);
        System.out.println("QA interactive ready client="+driver.getUUID()+" places="+home+"/"+shop+" rails="+rails);
    }
    private void prepareEditor(){
        var driver=level.getServer().getPlayerList().getPlayers().getFirst();
        var players = level.getServer().getPlayerList();
        players.op(driver.getGameProfile());
        players.sendPlayerPermissionLevel(driver);
        var s=trains.keySet().iterator().next();
        test.startSequence()
                .thenWaitUntil(()->test.assertTrue(s.a.getStation()!=null && s.b.getStation()!=null,"Editor stations"))
                .thenExecute(()->checked("Assemble editor train",()->assemble(s,driver.getUUID())))
                .thenWaitUntil(()->test.assertTrue(s.a.getStation().getPresentTrain()!=null,"Physical train at west station"))
                .thenWaitUntil(()->{
                    var train=s.a.getStation().getPresentTrain();
                    test.assertTrue(train!=null && (train.hasForwardConductor() || train.doubleEnded && train.hasBackwardConductor()),
                            "Create registered the assembled train's conductor");
                })
                .thenExecute(()->checked("Publish editor platforms",()->{
                    var tablet=com.example.examplemod.CreateCityExpansionCore.RAILWAY_BINDER.get().getDefaultInstance();
                    // The publishing operator stays authorized when the train owner later returns as an ordinary visitor.
                    actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,tablet.copy());
                    markWithTablet(actor,s.a.getBlockPos(),s.west,false);
                    markWithTablet(actor,s.b.getBlockPos(),s.east,false);
                    driver.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,tablet);
                    markWithTablet(driver,s.b.getBlockPos(),s.east,false);
                    markWithTablet(driver,s.a.getBlockPos(),s.west,false);
                    markWithTablet(driver,s.a.getBlockPos(),s.west,true);
                    markCarriageWithTablet(driver,s.a.getStation().getPresentTrain());
                    standAt(s.west);face(s.a.getBlockPos());
                    var train=s.a.getStation().getPresentTrain();
                    var shared = PassengerService.create(actor,"Shared operator fixture");
                    PassengerService.append(actor,shared);
                    standAt(s.east);face(s.b.getBlockPos());PassengerService.append(actor,shared);
                    PassengerRestartCheck.checkSharedDispatch(actor,driver,shared);
                    var rail=new Rail(s.name,train.id,s.a.getStation().id,s.b.getStation().id,s.west,s.east);
                    var remote = s.west.east(10);
                    level.setBlockAndUpdate(remote.below(),Blocks.STONE.defaultBlockState());
                    driver.teleportTo(level,remote.getX()+.5,remote.getY(),remote.getZ()+.5,0,-80);
                    interactive=new Interactive(driver.getUUID(),home,shop,remote,s.east,List.of(rail),null,null,null);
                }))
                .thenWaitUntil(()->test.assertTrue(shop.equals(completedGoal),"Client creates and dispatches its passenger line"))
                .thenSucceed();
    }
    private void markWithTablet(ServerPlayer driver,BlockPos station,BlockPos boarding,boolean selectTrain) {
        driver.teleportTo(level,boarding.getX()+.5,boarding.getY(),boarding.getZ()+.5,0,0);
        var delta=Vec3.atCenterOf(station).subtract(driver.getEyePosition());
        driver.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        driver.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));
        driver.setOnGround(true);driver.setShiftKeyDown(selectTrain);
        var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(station),Direction.UP,station,false);
        driver.gameMode.useItemOn(driver,level,driver.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        driver.setShiftKeyDown(false);
        var be=(StationBlockEntity)level.getBlockEntity(station);
        test.assertTrue(PassengerService.stop(driver,be.getStation().id)!=null,"Tablet publishes physical platform "+station);
    }
    private void markCarriageWithTablet(ServerPlayer driver,Train train) {
        var carriage=train.carriages.getFirst().anyAvailableEntity();
        for(var pos:carriage.getContraption().getBlocks().keySet()){
            var aim=carriage.toGlobalVector(Vec3.atCenterOf(pos),1F);
            var delta=aim.subtract(driver.getEyePosition());
            driver.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            driver.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));
            if(com.example.examplemod.passenger.RailwayBinder.aimedCarriage(driver)!=carriage)continue;
            driver.gameMode.useItem(driver,level,driver.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
        throw new AssertionError("Tablet ray missed carriage "+carriage.getBoundingBox()+" from "+driver.getEyePosition()
                +" blocks="+carriage.getContraption().getBlocks().keySet().stream().map(p->carriage.toGlobalVector(Vec3.atCenterOf(p),1F)).toList()
                +" reach="+driver.blockInteractionRange()+" nativeEntities="+level.getEntitiesOfClass(
                        com.simibubi.create.content.trains.entity.CarriageContraptionEntity.class,driver.getBoundingBox().inflate(8)).size());
    }
    private void followInteractive(){
        var setup=interactive;
        var driver=level.getServer().getPlayerList().getPlayer(setup.player);
        test.assertTrue(driver!=null && driver.serverLevel()==level,"QA client connected in the expected level");
        UUID vehicle=PassengerService.riding(driver);
        if(vehicle!=null && setup.rails.stream().anyMatch(r->r.train.equals(vehicle))){
            boarded.add(vehicle);
            if(PassengerService.stoppedAt(level.getServer(),vehicle)==null)left.add(vehicle);
        }else if(vehicle==null && lastVehicle!=null && left.contains(lastVehicle))exited.add(lastVehicle);
        lastVehicle=vehicle;
        test.assertTrue(goal.equals(driver.blockPosition()) && shop.equals(completedGoal) && exited.size()==2,
                "Waiting for the real client to finish its transfer and reach "+goal);
        System.out.println("QA interactive complete client="+driver.getUUID()+" trains="+exited+" entrance="+driver.blockPosition());
        interactive=null;completedGoal=null;
    }
    private void choose(){
        var options=reply.getList("Options",Tag.TAG_COMPOUND);
        int selected=-1,walking=-1;
        for(int i=0;i<options.size();i++){
            int rides=options.getCompound(i).getInt("Rides");
            if(rides==0)walking=i;
            if(rides==2)selected=i;
        }
        test.assertTrue(walking>=0 && selected>=0,"Compare walking and a measured transfer in the same plan: "+options);
        var steps=options.getCompound(selected).getList("Steps",Tag.TAG_COMPOUND);
        int lastRide=-1;
        for(int i=0;i<steps.size();i++){
            var step=steps.getCompound(i);if(!step.contains("Train"))continue;
            var s=trains.keySet().stream().filter(service->service.name.equals(step.getString("Line"))).findFirst().orElseThrow();
            test.assertTrue(lastRide<0 || i>lastRide+1 && steps.getCompound(i-1).contains("To"),"Confirmed walking transfer between platforms");
            itinerary.add(new Ride(s,trains.get(s),stationId(s,step.getString("From")),stationId(s,step.getString("Stop")),step.getLong("Start")));
            PassengerService.departures(actor).stream().filter(r->r.line().equals(lines.get(s))
                            && r.fromName().equals(step.getString("From")) && r.toName().equals(step.getString("Stop")))
                    .min(java.util.Comparator.comparingLong(r->Math.abs(r.departTick()-step.getLong("Start"))))
                    .ifPresent(r->expectedRuns.put(r.train(),r));
            lastRide=i;
        }
        test.assertTrue(itinerary.size()==2 && itinerary.get(0).train!=itinerary.get(1).train,"Distinct assigned trains");
        System.out.println("QA compare walk arrival="+options.getCompound(walking).getLong("Arrival")+
                " transfer arrival="+options.getCompound(selected).getLong("Arrival")+" steps="+steps);
        send(query.action(TripNetwork.Action.BEGIN,selected));
    }
    private StationBlockEntity station(int x,int z,int track,boolean positive) {
        var pos=base.offset(x,0,z);
        level.setBlockAndUpdate(pos,create("track_station"));
        var be=(StationBlockEntity)level.getBlockEntity(pos);
        var settings=new CompoundTag();
        settings.putUUID("Id",UUID.randomUUID());settings.put("TargetTrack",NbtUtils.writeBlockPos(new BlockPos(0,0,track-z)));
        settings.putBoolean("TargetDirection",positive);settings.putBoolean("Ortho",true);
        be.edgePoint.read(settings,level.registryAccess(),false);
        return be;
    }
    private void place(UUID id,BlockPos entrance) {
        standAt(entrance);
        var place=new AtlasPlace(id,actor.getUUID(),0,"QA depot",AtlasPlace.Category.PUBLIC,"",
                level.dimension().location(),entrance.above(4),List.of(entrance),interactiveRun,null);
        var saved=PlaceService.save(actor,place);
        test.assertTrue("saved".equals(saved),"PlaceService registers the physical entrance: "+saved);
    }
    private void standAt(BlockPos p) {
        actor.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);actor.setOnGround(true);
    }
    private void face(BlockPos p) {
        var delta=Vec3.atCenterOf(p).subtract(actor.getEyePosition());
        actor.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        actor.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));
    }
    private void assemble(Service service) {
        assemble(service,actor.getUUID());
    }
    private void assemble(Service service, UUID owner) {
        var a=service.a;var b=service.b;
        a.updateName(service.name+" West");b.updateName(service.name+" East");
        test.assertTrue(a.enterAssemblyMode(actor),"Enter Create assembly mode");
        var bogey=a.edgePoint.getGlobalPosition().relative(a.getAssemblyDirection()).above();
        level.setBlockAndUpdate(bogey,create("small_bogey").setValue(AbstractBogeyBlock.AXIS,Direction.Axis.X));
        var burner=bogey.above();
        int seatSide=Integer.compare(service.west.getZ(),bogey.getZ());
        // Leave the platform-side seat face exposed; controls are supported along the bogey axis.
        level.setBlockAndUpdate(bogey.west(),create("railway_casing"));
        level.setBlockAndUpdate(bogey.east(),create("railway_casing"));
        level.setBlockAndUpdate(burner,create("blaze_burner")
                .setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));
        level.setBlockAndUpdate(burner.east(),create("controls").setValue(ControlsBlock.FACING,Direction.WEST));
        level.setBlockAndUpdate(burner.west(),create("controls").setValue(ControlsBlock.FACING,Direction.EAST));
        if (!Boolean.getBoolean("ccec.qa.passengerEditor"))
            level.setBlockAndUpdate(burner.offset(0,0,seatSide),create("blue_seat"));
        var min=bogey.offset(-1,0,Math.min(0,seatSide));var max=bogey.offset(2,2,1+Math.max(0,seatSide));
        level.addFreshEntity(new SuperGlueEntity(level,new AABB(min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ())));
        a.assemble(owner); a.exitAssemblyMode();
    }
    private static BlockState create(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create",name)).defaultBlockState();
    }
    private void launch(Service s,Train train) {
        var a=s.a;var b=s.b;var platformA=s.west;var platformB=s.east;
        // Station administration uses the same actor, eye ray, ownership and feet checks as the commands.
        level.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(actor.getGameProfile(),2,false));
        test.assertTrue(actor.hasPermissions(2),"QA operator has explicit publication authority");
        if (!interactiveRun && lines.isEmpty()) registerMapStation(s);
        else {standAt(platformA);face(a.getBlockPos());PassengerService.platform(actor,false);}
        standAt(platformB);face(b.getBlockPos());PassengerService.platform(actor,false);
        var line=PassengerService.create(actor,s.name);lines.put(s,line);
        standAt(platformA);face(a.getBlockPos());PassengerService.append(actor,line);
        standAt(platformB);face(b.getBlockPos());PassengerService.append(actor,line);
        var owner=com.example.examplemod.cargo.center.OwnerRef.player(actor.getUUID());
        standAt(platformA);face(a.getBlockPos());
        boolean restore=lines.size()==1;
        UUID charter=restore?PassengerRestartCheck.charter(actor,train.id,line,a.getStation().id,b.getStation().id):null;
        var fleet=com.example.examplemod.cargo.center.PublicFleetRegistry.get(level.getServer());
        fleet.publish(actor,train.id,owner,a.getStation().id,0,0);
        if(restore) {
            PassengerRestartCheck.reload(level.getServer());
            fleet=com.example.examplemod.cargo.center.PublicFleetRegistry.get(level.getServer());
        }
        try {
            PassengerService.start(actor,line,10);
            throw new AssertionError("Passenger line took a train published in Cargo");
        } catch(IllegalArgumentException expected) {
            test.assertTrue("in_use".equals(expected.getMessage()) && train.runtime.getSchedule()==null,
                    "Public fleet retains the train and its schedule");
        }
        fleet.disable(actor,train.id);PassengerService.start(actor,line,10);
        if(restore) {
            PassengerRestartCheck.reload(level.getServer());
            PassengerRestartCheck.rejectCharter(actor,charter,train.id);
        }
        test.assertTrue(!com.example.examplemod.cargo.CargoPackageAssignment.mayAssignTrain(level.getServer(),owner,train.id),
                "Passenger assignment blocks native Cargo reassignment");
        try {
            com.example.examplemod.cargo.center.PublicFleetRegistry.get(level.getServer()).publish(actor,train.id,owner,a.getStation().id,0,0);
            throw new AssertionError("Cargo fleet accepted an assigned passenger train");
        } catch(IllegalArgumentException expected) {
            test.assertTrue("shipment_in_use".equals(expected.getMessage()),"Fleet rejects passenger assignment before mutating state");
        }
        standAt(start);
        test.assertTrue(PassengerService.departures(actor).isEmpty(),"Unmeasured rail connectivity is insufficient");
    }

    private void registerMapStation(Service service) {
        var dim = level.dimension().location();
        var cities = CityManager.get(level.getServer().overworld());
        var city = cities.getMemberCity(actor.getUUID());
        var station = service.a.getBlockPos();
        var entrance = service.west;
        var cityPlot = CityPlot.at(dim, base);
        var stationPlot = CityPlot.at(dim, station);
        cities.claimPlot(city, new CityPlot(dim, cityPlot.plotX(), stationPlot.plotZ()), actor.getUUID());
        cities.claimPlot(city, stationPlot, actor.getUUID());
        cities.claimPlot(city, CityPlot.at(dim, entrance), actor.getUUID());
        var feature = new CityMapFeature(UUID.randomUUID(), city.getId(), 0, dim, CityMapFeature.Kind.STATION, CityMapFeature.Shape.POINT,
                "Platform house", "7", "", List.of(station), List.of(entrance));
        var view = UUID.randomUUID();
        CityPublicAtlas.open(actor, new RequestCityWorldMapPayload(view, UUID.randomUUID()));
        var world = CityMapSavedData.get(level).worldId();
        var saved = CityMapService.handle(actor, new CityMapNetwork.Request(
                UUID.randomUUID(), view, world, dim, city.getId(), CityMapNetwork.Action.SAVE,
                feature.id(), 0, feature));
        test.assertTrue("ok".equals(saved.status()), "Saved a city station: "+saved.status());
        var current = CityMapService.resolve(actor, feature.id());
        try {
            PassengerService.registerCityStation(member, current);
            throw new AssertionError("Non-operator registered a map station");
        } catch (IllegalArgumentException denied) {
            test.assertTrue("op_required".equals(denied.getMessage()), "Setting up a platform requires operator authority");
        }
        var registered = CityMapService.handle(actor, new CityMapNetwork.Request(
                UUID.randomUUID(), view, world, dim, city.getId(), CityMapNetwork.Action.REGISTER_STATION,
                current.id(), current.revision(), null));
        test.assertTrue("ok".equals(registered.status()), "Registered a real Create station through the atlas: "+registered.status());
        var stop = PassengerService.stop(actor, service.a.getStation().id);
        test.assertTrue(stop != null && entrance.equals(stop.entrance()) && station.equals(stop.stationPos()),
                "Cartography publishes the real station UUID and saved platform entrance");
        var emptyPlot = new CityMapFeature(UUID.randomUUID(), city.getId(), 0, dim, CityMapFeature.Kind.STATION,
                CityMapFeature.Shape.POINT, "Future station", "", "", List.of(entrance), List.of(entrance));
        var drawn = CityMapService.handle(actor, new CityMapNetwork.Request(UUID.randomUUID(), view, world, dim, city.getId(),
                CityMapNetwork.Action.SAVE, emptyPlot.id(), 0, emptyPlot));
        test.assertTrue("ok".equals(drawn.status()), "A planned station can stay in the atlas");
        var missing = CityMapService.handle(actor, new CityMapNetwork.Request(UUID.randomUUID(), view, world, dim, city.getId(),
                CityMapNetwork.Action.REGISTER_STATION, emptyPlot.id(), CityMapService.resolve(actor, emptyPlot.id()).revision(), null));
        test.assertTrue("station_missing".equals(missing.status()) && PassengerService.stop(actor, emptyPlot.id()) == null,
                "A map symbol alone cannot register a passenger platform: "+missing.status());
        CityPublicAtlas.close(actor, view);
        member.moveTo(start.getX()+.5, start.getY(), start.getZ()+.5, 0, 0);
        var trip = new TripNetwork.Query(TripNetwork.Action.PLAN, UUID.randomUUID(), world, TripRef.PLAYER,
                new TripRef(TripRef.Kind.CARTOGRAPHY, current.id()), TripPlanner.Mode.WALK, 0);
        TripServer.handle(member, trip, (p, answer) -> {
            if (!"options".equals(answer.data().getString("State"))) return;
            var choices = answer.data().getList("Options", Tag.TAG_COMPOUND);
            var steps = choices.getCompound(0).getList("Steps", Tag.TAG_COMPOUND);
            reachedStationEntrance = !steps.isEmpty() && BlockPos.of(steps.getCompound(steps.size()-1).getLong("To")).equals(entrance);
        });
    }
    private ServerPlayer districtPlayer(String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        return player;
    }

    private CityMapNetwork.Reply districtEdit(ServerPlayer editor, UUID view, UUID city, CityMapNetwork.Action action,
                                               UUID id, long revision, CityMapFeature value, UUID platform) {
        var request = new CityMapNetwork.Request(UUID.randomUUID(), view, CityMapSavedData.get(level).worldId(),
                level.dimension().location(), city, action, id, revision, value, platform);
        var result = CityMapService.handle(editor, request);
        test.assertTrue("ok".equals(result.status()), "District " + action + " returned " + result.status());
        return result;
    }

    private void checkDistrict() {
        var dim = level.dimension().location();
        var cities = CityManager.get(level.getServer().overworld());
        var city = cities.getMemberCity(actor.getUUID());
        var id = city.getId();
        remoteCityAccess(cities, city);
        var resident = districtPlayer("DistrictResident");
        var operator = districtPlayer("DistrictOperator");
        level.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(operator.getGameProfile(), 2, false));
        city.addMember(resident.getUUID());
        for (int x = CityPlot.plotXOf(base.getX()); x <= CityPlot.plotXOf(base.getX() + 15); x++)
            for (int z = CityPlot.plotZOf(base.getZ()); z <= CityPlot.plotZOf(base.getZ() + 15); z++) {
                var plot = new CityPlot(dim, x, z);
                cities.claimPlot(city, plot, actor.getUUID());
                test.assertTrue(city.hasClaim(plot), "District fixture lies in one city's territory");
            }
        resident.moveTo(start.getX() + .5, start.getY(), start.getZ() + .5, 0, 0);
        UUID residentView = UUID.randomUUID(), operatorView = UUID.randomUUID();
        CityPublicAtlas.open(resident, new RequestCityWorldMapPayload(residentView, UUID.randomUUID()));
        CityPublicAtlas.open(operator, new RequestCityWorldMapPayload(operatorView, UUID.randomUUID()));
        var rail = trains.keySet().stream().filter(service -> "North".equals(service.name)).findFirst().orElseThrow();
        var bend = base.offset(2, 0, 8);
        var vestibule = base.offset(10, 0, 11);
        var street = new CityMapFeature(UUID.randomUUID(), id, 0, dim, CityMapFeature.Kind.STREET, CityMapFeature.Shape.LINE,
                "Вокзальная", "", "", List.of(base.offset(1, 0, 7), base.offset(8, 0, 7)), List.of());
        var house = new CityMapFeature(UUID.randomUUID(), id, 0, dim, CityMapFeature.Kind.BUILDING, CityMapFeature.Shape.POLYGON,
                "", "12", "", List.of(start.west(), start, start.north(), start.north().west()), List.of(start),
                new CityMapFeature.Links(street.id(), null, null));
        var station = new CityMapFeature(UUID.randomUUID(), id, 0, dim, CityMapFeature.Kind.STATION, CityMapFeature.Shape.POINT,
                "Северный вокзал", "", "", List.of(rail.a.getBlockPos()), List.of(vestibule));
        var walkway = new CityMapFeature(UUID.randomUUID(), id, 0, dim, CityMapFeature.Kind.PATH, CityMapFeature.Shape.LINE,
                "Проход к вокзалу", "", "", List.of(start, bend, base.offset(10, 0, 8), vestibule), List.of(),
                new CityMapFeature.Links(null, new CityMapFeature.Endpoint(house.id(), start), new CityMapFeature.Endpoint(station.id(), vestibule)));
        for (var row : List.of(street, house, station, walkway))
            districtEdit(resident, residentView, id, CityMapNetwork.Action.SAVE, row.id(), 0, row, null);

        var revision = CityMapService.resolve(operator, station.id()).revision();
        var first = districtEdit(operator, operatorView, id, CityMapNetwork.Action.BIND_PLATFORM, station.id(), revision, null, rail.a.getStation().id);
        revision = first.features().stream().filter(f -> f.id().equals(station.id())).findFirst().orElseThrow().revision();
        districtEdit(operator, operatorView, id, CityMapNetwork.Action.BIND_PLATFORM, station.id(), revision, null, rail.b.getStation().id);
        var catalog = districtEdit(resident, residentView, id, CityMapNetwork.Action.LIST, CityMapNetwork.ALL, 0, null, null);
        var stops = catalog.platforms().stream().filter(p -> station.id().equals(p.feature())).toList();
        test.assertTrue(stops.size() == 2, "One saved station lists exactly two distinct native platforms");
        var selected = stops.stream().filter(p -> p.id().equals(rail.b.getStation().id)).findFirst().orElseThrow();
        test.assertTrue(rail.east.equals(selected.entrance()) && !vestibule.equals(selected.entrance())
                        && stops.stream().anyMatch(p -> p.id().equals(rail.a.getStation().id) && rail.west.equals(p.entrance())),
                "A passenger selects the east platform independently of the public entrance and the west platform");
        CityPublicAtlas.close(resident, residentView);
        CityPublicAtlas.close(operator, operatorView);
        var plan = new TripNetwork.Query(TripNetwork.Action.PLAN, UUID.randomUUID(), CityMapSavedData.get(level).worldId(),
                new TripRef(TripRef.Kind.CARTOGRAPHY, house.id()), new TripRef(TripRef.Kind.STATION, selected.id()), TripPlanner.Mode.WALK, 0);
        districtWalk = new DistrictWalk(resident, plan, bend, vestibule, selected.entrance(), rail.west, selected.name());
        TripServer.handle(resident, plan, (p, answer) -> districtReply = answer.data());
    }

    private void remoteCityAccess(CityManager cities, com.example.examplemod.city.City homeCity) {
        var player = districtPlayer("RemoteCartograph");
        homeCity.addMember(player.getUUID());
        player.moveTo(start.getX() + .5, start.getY(), start.getZ() + .5, 0, 0);
        var dim = level.dimension().location();
        var position = base.offset(base.getX() > 0 ? -12_000 : 12_000, 0, 0);
        var remote = cities.createCity("Remote cartography fixture", UUID.randomUUID(), dim, position);
        test.assertTrue(remote != null && !remote.hasMember(player.getUUID()) && !level.hasChunkAt(position), "Remote city is unloaded and has a different membership");
        var view = UUID.randomUUID();
        var world = CityMapSavedData.get(level).worldId();
        CityPublicAtlas.open(player, new RequestCityWorldMapPayload(view, UUID.randomUUID()));
        test.assertTrue(CityPublicAtlas.view(player, view).cities().stream().anyMatch(c -> c.id().equals(remote.getId()) && !c.own()),
                "The server directory includes the remote city even outside the visible area");

        var feature = new CityMapFeature(UUID.randomUUID(), remote.getId(), 0, dim, CityMapFeature.Kind.BUILDING,
                CityMapFeature.Shape.POINT, "Remote house", "", "", List.of(position), List.of());
        var denied = CityMapService.handle(player, new CityMapNetwork.Request(UUID.randomUUID(), view, world, dim, remote.getId(),
                CityMapNetwork.Action.SAVE, feature.id(), 0, feature));
        test.assertTrue("permission".equals(denied.status()) && !denied.canEdit(), "A resident of another city cannot edit its map");
        var ops = level.getServer().getPlayerList().getOps();
        ops.add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(), 2, false));
        var result = districtEdit(player, view, remote.getId(), CityMapNetwork.Action.SAVE, feature.id(), 0, feature, null);
        test.assertTrue(result.canEdit() && !level.hasChunkAt(position), "OP2 edits the selected city without travelling or loading its terrain");
        var saved = result.features().stream().filter(row -> row.id().equals(feature.id())).findFirst().orElseThrow();
        var outside = new CityMapFeature(saved.id(), saved.city(), saved.revision(), dim, saved.kind(), saved.shape(), saved.name(),
                saved.houseNumber(), saved.description(), List.of(homeCity.getPylonPos()), List.of());
        var wrongClaim = CityMapService.handle(player, new CityMapNetwork.Request(UUID.randomUUID(), view, world, dim, remote.getId(),
                CityMapNetwork.Action.SAVE, saved.id(), saved.revision(), outside));
        test.assertTrue("territory".equals(wrongClaim.status()) && saved.equals(CityMapService.resolve(player, saved.id())),
                "Selecting another city cannot use the operator's own-city claim as the target footprint");
        ops.remove(player.getGameProfile());
        var revoked = CityMapService.handle(player, new CityMapNetwork.Request(UUID.randomUUID(), view, world, dim, remote.getId(),
                CityMapNetwork.Action.DELETE, saved.id(), saved.revision(), null));
        test.assertTrue("permission".equals(revoked.status()) && !revoked.canEdit(), "Removing OP revokes remote editing in an already open atlas");
        CityPublicAtlas.close(player, view);
        System.out.println("QA remote city outside viewport: directory, cross-city OP2 edit, claim enforcement and immediate revocation passed");
    }

    private void verifyDistrict() {
        var walk = districtWalk;
        if (!districtBegan) {
            test.assertTrue(districtReply != null && "options".equals(districtReply.getString("State")), "Waiting for the district route: " + districtReply);
            test.assertTrue("Вокзальная 12".equals(districtReply.getString("Origin")) && walk.selectedName.equals(districtReply.getString("Goal")),
                    "Trip offer identifies the house address and selected physical platform");
            districtBegan = true;
            TripServer.handle(walk.rider, walk.query.action(TripNetwork.Action.BEGIN, 0), (p, answer) -> districtReply = answer.data());
        }
        test.assertTrue("guide".equals(districtReply.getString("State")), "Waiting for guide to the selected platform: " + districtReply);
        var ahead = java.util.Arrays.stream(districtReply.getLongArray("Ahead")).mapToObj(BlockPos::of).toList();
        test.assertTrue(!districtReply.getBoolean("Replan") && walk.boarding.equals(BlockPos.of(districtReply.getLong("GoalPos")))
                        && ahead.contains(walk.bend) && ahead.contains(walk.vestibule) && ahead.getLast().equals(walk.boarding)
                        && !ahead.contains(walk.otherBoarding), "Verified guide follows the drawn path through the station entrance to only the chosen boarding point: " + ahead);
        System.out.println("QA district Вокзальная 12 via " + walk.vestibule + " to selected " + walk.selectedName
                + " at " + walk.boarding + ", alternate platform " + walk.otherBoarding);
        TripServer.handle(walk.rider, walk.query.action(TripNetwork.Action.CANCEL, 0), (p, answer) -> {});
    }

    private void checked(String action,Runnable work) {
        try {work.run();}
        catch (RuntimeException failure) {
            if(interactiveRun)interactiveFailure=action+": "+failure;
            System.err.println("QA setup failed at "+action);
            failure.printStackTrace(System.err);
            test.fail(action+" failed: "+failure);
        }
    }
    private UUID stationId(Service s,String name) {
        var a=s.a;var b=s.b;
        if (a.getStation().name.equals(name)) return a.getStation().id;
        test.assertTrue(b.getStation().name.equals(name),"Native station is identified by the selected stop");
        return b.getStation().id;
    }
    private void request(TripPlanner.Mode mode) {
        query=new TripNetwork.Query(TripNetwork.Action.PLAN,UUID.randomUUID(),CityMapSavedData.get(level).worldId(),
                new TripRef(TripRef.Kind.PLACE,home),new TripRef(TripRef.Kind.PLACE,selectedPlace),mode,0);
        reply=null;send(query);
    }
    private void send(TripNetwork.Query q) {TripServer.handle(actor,q,(p,answer)->reply=answer.data());}

    private void follow() {
        if (actor.isPassenger()) actor.getVehicle().positionRider(actor);
        if (level.getGameTime()%30==0) send(query.action(TripNetwork.Action.POLL,0));
        TripServer.advance(actor);
        if (reply==null) {test.assertTrue(false,"Waiting for navigation");return;}
        if(!reply.getBoolean("Replan")) expectedRuns.forEach((id,run)->{
            var visit=PassengerService.visit(level.getServer(),run);
            String state=visit.number()+"@"+visit.station();
            if(!state.equals(observedDocks.put(id,state))){
                var consist=trains.values().stream().filter(t->t.id.equals(id)).findFirst().orElseThrow();
                System.out.println("QA rail "+run.lineName()+" tick="+level.getGameTime()+" ordinal="+state
                        +" selected="+run.fromVisit()+"/"+run.toVisit()+" native="+PassengerService.stoppedAt(level.getServer(),id)
                        +" active="+PassengerService.operating(actor,run)+" ETA="+PassengerService.running(level.getServer(),run.line(),id)
                        +" speed="+consist.speed+" current="+consist.runtime.currentEntry);
            }
        });
        if ("done".equals(reply.getString("State"))) {
            test.assertTrue(boarded.size()==2 && left.size()==2 && exited.size()==2 && actor.blockPosition().equals(goal),
                    "Boarded, rode and exited both trains, reached the exact final X/Y/Z entrance");
            test.assertTrue(delivery.packaged()==3 && delivery.delivered()==0,"Delivery remained active throughout the trip");
            System.out.println("QA arrived at "+goal+" trains="+exited+" project="+project+" cargo="+delivery.stage());
            return;
        }
        String phase=reply.contains("PhaseDetail")?reply.getString("PhaseDetail"):reply.getString("Phase");
        if(!phase.equals(observedPhase)){
            observedPhase=phase;
            System.out.println("QA guide "+phase+" tick="+level.getGameTime()+" actual="+actor.blockPosition()
                    +" train="+PassengerService.riding(actor)+" destination="+reply.getString("Stop"));
        }
        test.assertTrue("guide".equals(reply.getString("State")) && !reply.getBoolean("Replan"),"Expected active trip: "+reply+
                " feet="+actor.blockPosition()+" boarded="+boarded+" exited="+exited);
        if("walk".equals(phase) || "exit_walk".equals(phase)){
            var target=Vec3.atBottomCenterOf(BlockPos.of(reply.getLong("Pos")));
            var delta=target.subtract(actor.position());
            if(delta.y>.04 && delta.y<=1.01 && delta.horizontalDistance()<1.6)
                actor.move(MoverType.SELF,new Vec3(0,Math.min(.23,delta.y),0));
            else if(delta.length()>.025)actor.move(MoverType.SELF,delta.scale(Math.min(1,.22/delta.length())));
        }else{
            var ride=itinerary.get(Math.min(exited.size(),1));
            var train=ride.train;
            UUID dock=PassengerService.stoppedAt(level.getServer(),train.id);
            if("board".equals(phase) && !actor.isPassenger() && ride.from.equals(dock)
                    ){
                var mounted=train.carriages.getFirst().getDimensionalIfPresent(level.dimension()).entity.get();
                var carriage=(CarriageContraption)mounted.getContraption();
                var seat=carriage.getSeats().stream().filter(p->!carriage.conductorSeats.containsKey(p)).findFirst().orElseThrow();
                test.assertTrue(actor.getEyePosition().distanceTo(mounted.toGlobalVector(Vec3.atCenterOf(seat),1))<=4.5,
                        "Actor reached the selected train's seat");
                mounted.handlePlayerInteraction(actor,seat,Direction.UP,InteractionHand.MAIN_HAND);
                if(train.id.equals(PassengerService.riding(actor)))boarded.add(train.id);
            }else if("ride".equals(phase) && train.id.equals(PassengerService.riding(actor)) && dock==null){
                left.add(train.id);
            }else if("exit".equals(phase) && train.id.equals(PassengerService.riding(actor)) && ride.to.equals(dock)){
                actor.stopRiding();exited.add(train.id);
            }
        }
        test.assertTrue(false,"Following "+phase+" at "+actor.blockPosition());
    }

    private void industry() {
        project=JourneyIndustryScene.project(test,actor,member,base);
        var owner=interactiveRun?level.getServer().getPlayerList().getPlayers().getFirst():actor;
        var before=owner.position();float yaw=owner.getYRot(),pitch=owner.getXRot();
        delivery=JourneyIndustryScene.shipment(test,owner,base);
        var server=level.getServer();
        var seller=OwnerRef.player(owner.getUUID());
        var id=UUID.randomUUID();
        var item=new ItemStack(Items.IRON_INGOT);
        EconomyManager.get(server.overworld()).cargo().publish(id,owner.getUUID(),seller,delivery.sourceWarehouse(),
                (CompoundTag)item.save(server.registryAccess()),"Interchange iron",6,2);
        var access=new CargoOwners.Access(seller,CargoTrainRegistry.AccessMode.PUBLIC,java.util.Set.of(),java.util.Set.of());
        test.assertTrue(CargoOwners.get(server).change(server,owner.getUUID(),"warehouse/"+delivery.sourceWarehouse(),
                owner.getUUID(),CargoTrainRegistry.AccessMode.PRIVATE,access),"Publish the staging warehouse");
        test.assertTrue(PublicTradeMap.lookup(member,"offer",id)!=null,"Separate atlas user sees the published offer");
        if(interactiveRun)interactiveOffer=id;
        if(interactiveRun)owner.teleportTo(level,before.x,before.y,before.z,yaw,pitch);
        standAt(start);
    }
}

/** Live surroundings for the trip. Both warehouses serve one local Cargo Point. */
final class JourneyIndustryScene {
    private JourneyIndustryScene() {}

    static UUID project(GameTestHelper test,ServerPlayer owner,ServerPlayer member,BlockPos base){
        var level=test.getLevel();var server=level.getServer();var dim=level.dimension().location();
        var cities=CityManager.get(server.overworld());
        var city=cities.createCity("Atlas interchange",owner.getUUID(),dim,base);
        test.assertTrue(city!=null,"Create a city for the shared project");city.addMember(member.getUUID());
        var manager=EconomyManager.get(server.overworld()).projects();
        var author=ProjectNetworkHandlers.actor(owner,cities,city);
        var plot=com.example.examplemod.city.CityPlot.at(dim,base);
        int x=Math.min(base.getX(),plot.maxX()-11), z=Math.min(base.getZ(),plot.maxZ()-11);
        var zone=new ProjectZone(dim,ProjectZone.Shape.RECTANGLE,x,z,x+11,z+11);
        var spec=new ProjectManager.Draft(Project.Scope.CITY,"Interchange platforms","",Project.Category.RAILWAY,zone,
                List.of(new Project.Stage("Access",false)),Project.Funding.FREE,0);
        var zero=ProjectPlanRequest.LIST;
        var create=new ProjectManager.Command(UUID.randomUUID(),zero,0,ProjectManager.Action.CREATE,zero,0,false,"",spec);
        test.assertTrue("ok".equals(manager.execute(author,create)),"Create city project");
        var result=manager.all().stream().filter(p->p.creationRequest().equals(create.request())).findFirst().orElseThrow();
        test.assertTrue("ok".equals(manager.execute(author,new ProjectManager.Command(UUID.randomUUID(),result.id(),result.revision(),
                ProjectManager.Action.PUBLISH,zero,0,false,"",null))),"Publish project");
        var doc=new EngineeringPlan(UUID.randomUUID(),dim,EngineeringPlan.Kind.LINE,"Platform walk","",
                List.of(new EngineeringPlan.Node(x+2,z+2),new EngineeringPlan.Node(x+9,z+9)));
        var response=ProjectPlanService.process(member,new ProjectPlanRequest(UUID.randomUUID(),result.id(),doc.id(),0,doc));
        test.assertTrue("saved".equals(response.getString("Result")),"Second city member publishes the shared drawing");
        return result.id();
    }

    static CargoShipmentRegistry.Shipment shipment(GameTestHelper test,ServerPlayer actor,BlockPos base){
        var level=test.getLevel();var server=level.getServer();
        var point=CargoPointRegistry.create(server,actor.getUUID(),"Interchange stores");
        var owner=OwnerRef.player(actor.getUUID());var warehouses=CargoWarehouseRegistry.get(server);
        var routerAt=base.offset(8,0,15);
        var router=place(level,actor,routerAt,CargoBlocks.ROUTER.get().defaultBlockState());
        test.assertTrue(CargoPointRegistry.bindRouter(actor,point.id(),level,routerAt),"Bind the physical Cargo router");
        var registered=new java.util.ArrayList<CargoWarehouseRegistry.Warehouse>();
        for(int x:new int[]{2,13}){
            var tickerAt=base.offset(x,0,15);
            place(level,actor,tickerAt,CargoBlocks.TICKER.get().defaultBlockState());
            actor.moveTo(tickerAt.getX()+.5,tickerAt.getY()+1,tickerAt.getZ()+.5,0,90);
            registered.add(warehouses.register(actor,point.id(),"Store "+x,owner));
        }
        var pack=base.offset(10,0,15);
        level.setBlockAndUpdate(pack.west(),Blocks.BARREL.defaultBlockState());
        var packState=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","packager")).defaultBlockState();
        level.setBlockAndUpdate(pack,packState.setValue(BlockStateProperties.FACING,Direction.EAST));
        var loc=new CargoInfrastructure.Location(level.dimension().location(),pack);
        var destination=registered.get(1).id();
        test.assertTrue(warehouses.attachReceiverAt(actor,destination,loc),"Bind native packager and receiving inventory");
        var item=new ItemStack(Items.IRON_INGOT,3);var id=UUID.randomUUID();
        var route=CargoRoutePlanner.plan(server,owner,point.id(),point.id(),CargoRoutePlanner.Preference.CHEAPEST).orElseThrow();
        var shipment=CargoShipmentRegistry.get(server).create(server,id,actor.getUUID(),owner,registered.getFirst(),
                warehouses.find(destination).orElseThrow(),item,3,route);
        var commerce=EconomyManager.get(server.overworld()).cargo();
        commerce.reserve(id,owner,owner,id,null,3,0,List.of());commerce.markAttempted(id);
        var parcel=PackageItem.containing(List.of(item));
        PackageItem.addAddress(parcel,new CargoAddress(point.id(),point.id(),id).toString());
        var buffer=((CargoRouterBlockEntity)router).inputHandler();
        test.assertTrue(buffer.insertItem(0,parcel,false).isEmpty(),"The actual Create package enters the Cargo router");
        return shipment;
    }

    private static net.minecraft.world.level.block.entity.BlockEntity place(ServerLevel level,ServerPlayer actor,BlockPos pos,BlockState state){
        level.setBlockAndUpdate(pos,state);
        state.getBlock().setPlacedBy(level,pos,state,actor,new ItemStack(state.getBlock()));
        return level.getBlockEntity(pos);
    }
}
