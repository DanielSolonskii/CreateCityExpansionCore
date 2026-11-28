package com.example.examplemod.citymap;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityDirectoryPager;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.city.CityPublicAtlas;
import com.example.examplemod.passenger.PassengerService;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.BiPredicate;

public final class CityMapFeatureCheck {
    private static final ResourceLocation DIM = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation OTHER_DIM = ResourceLocation.parse("minecraft:the_nether");
    private static final UUID CITY = UUID.randomUUID(), OTHER = UUID.randomUUID(), ID = UUID.randomUUID();
    private static final BiPredicate<UUID, CityPlot> OURS = (owner, plot) -> CITY.equals(owner) && DIM.equals(plot.dimension());
    private static int checks;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        ++checks;
    }
    private static BlockPos pos(int x, int z) { return new BlockPos(x, 71, z); }
    private static CityMapFeature feature(CityMapFeature.Shape shape, List<BlockPos> vertices, List<BlockPos> entrances) {
        return new CityMapFeature(ID, CITY, 0, DIM, CityMapFeature.Kind.BUILDING, shape,
                "Вокзальная", "12А", "Вход со двора", vertices, entrances);
    }

    public static void main(String[] args) {
        var owner = UUID.randomUUID();
        var resident = UUID.randomUUID();
        var city = new City(CITY, "District", owner, pos(0, 0));
        city.addMember(resident);
        require(CityMapService.mayEdit(city, owner, false, DIM), "owner can edit");
        require(CityMapService.mayEdit(city, resident, false, DIM), "resident can edit");
        require(!CityMapService.mayEdit(city, OTHER, false, DIM), "outsiders cannot edit");
        require(CityMapService.mayEdit(city, OTHER, true, DIM), "OP can edit another city");
        require(!CityMapService.mayEdit(city, owner, true, OTHER_DIM), "writes require the city's dimension");

        var line = feature(CityMapFeature.Shape.LINE, List.of(pos(-75, 5), pos(75, 5)), List.of());
        var middle = new CityPlot(DIM, 0, 0);
        BiPredicate<UUID, CityPlot> hole = OURS.and((c, p) -> !middle.equals(p));
        var book = new CityMapBook();
        require(line.footprint().contains(new CityPlot(DIM, -2, 0)), "negative coordinates use floor division");
        require("territory".equals(book.change(CITY, DIM, ID, 0, line, hole)), "crossing an unowned intermediate plot is denied");
        var polygon = feature(CityMapFeature.Shape.POLYGON, List.of(pos(-70,-70), pos(100,-70), pos(100,100), pos(-70,100)),
                List.of(new BlockPos(2, 74, 3)));
        require(polygon.containsXZ(0, 0) && !polygon.containsXZ(108, 0), "interior picking follows contour");
        require("territory".equals(book.change(CITY, DIM, ID, 0, polygon, hole)), "enclosed unowned plots are denied");
        var entranceOutside = feature(CityMapFeature.Shape.POINT, List.of(pos(-1,-1)), List.of(pos(1,1)));
        require("territory".equals(book.change(CITY, DIM, ID, 0, entranceOutside, hole)), "entrances also require ownership");
        var diagonal = feature(CityMapFeature.Shape.LINE, List.of(pos(45,45), pos(55,55)), List.of());
        require(diagonal.footprint().containsAll(List.of(new CityPlot(DIM, 0, 1), new CityPlot(DIM, 1, 0))),
                "crossing a territory corner touches both neighbouring plots");
        boolean crossingRejected = false;
        try { feature(CityMapFeature.Shape.POLYGON, List.of(pos(0,0),pos(3,5),pos(4,0),pos(0,4)),List.of()); }
        catch (IllegalArgumentException badPolygon) { crossingRejected = true; }
        require(crossingRejected, "self-intersecting rings rejected");

        require("ok".equals(book.change(CITY, DIM, ID, 0, polygon, OURS)), "save");
        var first = book.resolve(ID, DIM, OURS);
        require(first.revision() > 0 && first.id().equals(ID), "stable ID and server revision");
        require("Вокзальная 12А".equals(first.label()) && first.entrances().getFirst().getY() == 74, "address and elevation preserved");
        require(book.visible(DIM, null, hole).isEmpty() && book.resolve(ID, DIM, hole) == null, "losing a claim hides geometry and route");
        require(book.visible(OTHER_DIM, null, OURS).isEmpty(), "other dimensions stay separate");
        require("permission".equals(book.change(OTHER, DIM, ID, first.revision(), null, OURS)), "another city cannot delete a known ID");
        require("ok".equals(book.change(CITY, DIM, ID, first.revision(), first, OURS)), "current revision updates");
        require("stale".equals(book.change(CITY, DIM, ID, first.revision(), first, OURS)), "interleaved old update denied");
        require("stale".equals(book.change(CITY, DIM, ID, first.revision(), null, OURS)), "interleaved old delete denied");

        var latest = book.resolve(ID, DIM, OURS);
        var v1 = book.save(new CompoundTag(), null);
        v1.putInt("Format", 1);
        require(latest.equals(CityMapBook.load(v1, null).resolve(ID, DIM, OURS)), "v1 geometry keeps its ID, revision and entrances");
        book = CityMapBook.load(book.save(new CompoundTag(), null), null);
        require(latest.equals(book.resolve(ID, DIM, OURS)), "persistent identity, description, XYZ and revision");
        require("ok".equals(book.change(CITY, DIM, ID, latest.revision(), null, OURS)), "delete current revision");
        require(book.resolve(ID, DIM, OURS) == null, "deleted ID no longer resolves");
        book = CityMapBook.load(book.save(new CompoundTag(), null), null);
        require("ok".equals(book.change(CITY, DIM, ID, 0, line, OURS)) && book.resolve(ID, DIM, OURS).revision() > latest.revision(),
                "reusing an ID after deletion and restart cannot revive a stale edit");
        var unknown = book.save(new CompoundTag(), null);
        unknown.putInt("Format", 999);
        var preserved = CityMapBook.load(unknown, null);
        require(!preserved.available() && preserved.save(new CompoundTag(), null).equals(unknown), "unsupported formats kept intact");

        var packet = new CityMapNetwork.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DIM, CITY,
                CityMapNetwork.Action.SAVE, ID, latest.revision(), latest);
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CityMapNetwork.Request.CODEC.encode(wire, packet);
            require(CityMapNetwork.Request.CODEC.decode(wire).equals(packet), "world, view, city, revision and XYZ survive request codec");
            wire.clear();
            var reply = new CityMapNetwork.Reply(packet.nonce(), CITY, "ok", true, List.of(latest));
            CityMapNetwork.Reply.CODEC.encode(wire, reply);
            require(CityMapNetwork.Reply.CODEC.decode(wire).equals(reply), "public object list and capability survive reply codec");
        } finally { wire.release(); }
        var outside = CityMapNetwork.OUTSIDE;
        require(CityMapService.mayEditScope(outside,DIM,null,OTHER,true,DIM), "OP can survey unclaimed land");
        require(!CityMapService.mayEditScope(outside,DIM,null,OTHER,false,DIM), "outside scope requires OP");
        require(!CityMapService.mayEditScope(outside,DIM,null,OTHER,true,OTHER_DIM), "outside scope is dimension bound");
        var owns = CityMapService.ownership(new CityManager());
        var rural = new CityMapFeature(ID,outside,0,DIM,CityMapFeature.Kind.BUILDING,CityMapFeature.Shape.POINT,
                "Depot","","",List.of(pos(0,0)),List.of()).withIcon(CityMapFeature.Icon.WAREHOUSE);
        var ruralBook = new CityMapBook();
        require("ok".equals(ruralBook.change(outside,DIM,ID,0,rural,owns)), "unclaimed building saves");
        var stored = ruralBook.visible(DIM,null,owns).getFirst();
        require(stored.icon()==rural.icon() && CityMapFeature.load(stored.save()).equals(stored), "public icon survives revision and restart");
        var v3 = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var rq = new CityMapNetwork.RequestV3(new CityMapNetwork.Request(packet.nonce(),packet.view(),packet.world(),
                    DIM,outside,CityMapNetwork.Action.SAVE,ID,0,rural));
            CityMapNetwork.RequestV3.CODEC.encode(v3,rq);
            require(rq.equals(CityMapNetwork.RequestV3.CODEC.decode(v3)), "building icon survives save request");
            var rp = new CityMapNetwork.ReplyV3(new CityMapNetwork.Reply(packet.nonce(),outside,"ok",true,List.of(stored,latest)));
            CityMapNetwork.ReplyV3.CODEC.encode(v3,rp);
            require(rp.equals(CityMapNetwork.ReplyV3.CODEC.decode(v3)), "independent icons survive atlas reply");
        } finally { v3.release(); }
        linkedDistrict();
        packetBudget();
        remoteDirectory();
        System.out.println("PASS: " + checks + " city cartography checks");
    }

    private static void remoteDirectory() {
        var manager = new CityManager();
        var player = UUID.randomUUID();
        var home = manager.createCity("Home", player, DIM, pos(0, 0));
        for (int i = 1; i <= CityDirectoryPager.PAGE_SIZE; i++)
            manager.createCity("City " + i, UUID.randomUUID(), DIM, pos(i * 100, 0));
        var remote = manager.createCity("Z - Remote", UUID.randomUUID(), DIM, pos(20_000, -20_000));
        manager.createCity("Other dimension", UUID.randomUUID(), OTHER_DIM, pos(0, 0));
        var directory = CityPublicAtlas.profiles(manager, player, DIM);
        var secondPage = CityDirectoryPager.page(directory, CityDirectoryPager.PAGE_SIZE);
        require(secondPage.stream().anyMatch(entry -> entry.id().equals(remote.getId()) && !entry.own() && !entry.editable()),
                "another city's remote profile remains discoverable past page one");
        require(directory.stream().allMatch(entry -> entry.dimension().equals(DIM)) &&
                directory.stream().filter(entry -> entry.own()).allMatch(entry -> entry.id().equals(home.getId())),
                "public discovery covers exactly the requested dimension; own still identifies membership");
        require(CityMapService.mayEdit(remote, player, true, DIM) && !CityMapService.mayEdit(remote, player, false, DIM)
                && !CityMapService.mayEdit(remote, player, true, OTHER_DIM), "selected-city cartography uses live OP and dimension independently of identity rights");
    }

    private static void linkedDistrict() {
        var book = new CityMapBook();
        var roadNode = pos(8, 9);
        var door = pos(12, 16);
        var street = new CityMapFeature(UUID.randomUUID(), CITY, 0, DIM, CityMapFeature.Kind.STREET, CityMapFeature.Shape.LINE,
                "Набережная", "", "", List.of(pos(1,9), roadNode, pos(20,9)), List.of());
        var address = new CityMapFeature(ID, CITY, 0, DIM, CityMapFeature.Kind.BUILDING, CityMapFeature.Shape.POINT,
                "", "7", "Дом", List.of(pos(11,16)), List.of(door), new CityMapFeature.Links(street.id(), null, null));
        require("invalid_link".equals(book.change(CITY, DIM, ID, 0, address, OURS)), "unknown street denied");
        require("ok".equals(book.change(CITY, DIM, street.id(), 0, street, OURS)), "saved street with a path node");
        require("ok".equals(book.change(CITY, DIM, ID, 0, address, OURS)), "unnamed house with address saved");
        require("Набережная 7".equals(address.label(id -> book.resolve(id, DIM, OURS))), "address resolves exact street UUID");

        var crossing = new CityMapFeature(UUID.randomUUID(), CITY, 0, DIM, CityMapFeature.Kind.PATH, CityMapFeature.Shape.LINE,
                "Проход", "", "", List.of(door, roadNode), List.of(), new CityMapFeature.Links(null,
                new CityMapFeature.Endpoint(ID, door), new CityMapFeature.Endpoint(street.id(), roadNode)));
        require("ok".equals(book.change(CITY, DIM, crossing.id(), 0, crossing, OURS)), "path snaps to the selected entrance and street vertex");
        var upperDoor = door.above();
        var wrongFloor = new CityMapFeature(UUID.randomUUID(), CITY, 0, DIM, CityMapFeature.Kind.PATH, CityMapFeature.Shape.LINE,
                "Wrong floor", "", "", List.of(upperDoor, roadNode), List.of(), new CityMapFeature.Links(null,
                new CityMapFeature.Endpoint(ID, upperDoor), null));
        require("invalid_link".equals(book.change(CITY, DIM, wrongFloor.id(), 0, wrongFloor, OURS)), "nearby XYZ on another floor is not a door");

        var foreign = new CityMapFeature(UUID.randomUUID(), OTHER, 0, DIM, CityMapFeature.Kind.BUILDING, CityMapFeature.Shape.POINT,
                "", "9", "", List.of(pos(1,1)), List.of(), address.links());
        require("invalid_link".equals(book.change(OTHER, DIM, foreign.id(), 0, foreign, (city, plot) -> true)), "cross-city address denied even to OP");
        var savedStreet = book.resolve(street.id(), DIM, OURS);
        require("in_use".equals(book.change(CITY, DIM, savedStreet.id(), savedStreet.revision(), null, OURS)), "referenced street protected from deletion");
        var renamed = new CityMapFeature(street.id(), CITY, savedStreet.revision(), DIM, street.kind(), street.shape(), "Садовая", "", "",
                street.vertices(), street.entrances());
        require("ok".equals(book.change(CITY, DIM, renamed.id(), renamed.revision(), renamed, OURS))
                && "Садовая 7".equals(address.label(id -> book.resolve(id, DIM, OURS))), "renaming the street immediately updates the address");
        var house = book.resolve(ID, DIM, OURS);
        var movedDoor = new CityMapFeature(ID, CITY, house.revision(), DIM, house.kind(), house.shape(), "", "7", "",
                house.vertices(), List.of(door.east()), house.links());
        require("in_use".equals(book.change(CITY, DIM, ID, house.revision(), movedDoor, OURS)), "referenced door cannot silently move");
        var savedPath = book.resolve(crossing.id(), DIM, OURS);
        var restored = CityMapBook.load(book.save(new CompoundTag(), null), null);
        require(restored.available() && savedPath.equals(restored.resolve(crossing.id(), DIM, OURS))
                && house.equals(restored.resolve(ID, DIM, OURS)), "stable street, node and entrance links survive restart");

        var station = new CityMapFeature(UUID.randomUUID(), CITY, 0, DIM, CityMapFeature.Kind.STATION, CityMapFeature.Shape.POINT,
                "Центральный", "", "", List.of(pos(25,25)), List.of(pos(24,25)));
        require("ok".equals(book.change(CITY, DIM, station.id(), 0, station, OURS)), "station saved");
        var before = book.resolve(station.id(), DIM, OURS);
        book.platformsChanged(before.id());
        require(book.resolve(before.id(), DIM, OURS).revision() > before.revision()
                && "stale".equals(book.change(CITY, DIM, before.id(), before.revision(), before, OURS)), "binding a platform invalidates concurrent station edits");
        var physical = List.of(new PassengerService.Stop(UUID.randomUUID(), "Путь 1", DIM, pos(22,23), pos(22,22), station.id()),
                new PassengerService.Stop(UUID.randomUUID(), "Путь 2", DIM, pos(28,23), pos(28,22), station.id()));
        var packet = new CityMapNetwork.Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DIM, CITY,
                CityMapNetwork.Action.SAVE, ID, house.revision(), house);
        var modern = new CityMapNetwork.RequestV2(packet);
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CityMapNetwork.RequestV2.CODEC.encode(wire, modern);
            require(modern.equals(CityMapNetwork.RequestV2.CODEC.decode(wire)), "extended address survives client to server");
            wire.clear();
            var binding = new CityMapNetwork.RequestV2(new CityMapNetwork.Request(packet.nonce(), packet.view(), packet.world(), DIM, CITY,
                    CityMapNetwork.Action.BIND_PLATFORM, before.id(), before.revision(), null, physical.getFirst().id()));
            CityMapNetwork.RequestV2.CODEC.encode(wire, binding);
            require(binding.equals(CityMapNetwork.RequestV2.CODEC.decode(wire)), "bind selects the actual physical stop UUID");
            wire.clear();
            var reply = new CityMapNetwork.Reply(packet.nonce(), CITY, "ok", true, List.of(house, savedPath, before), physical);
            var response = new CityMapNetwork.ReplyV2(reply);
            CityMapNetwork.ReplyV2.CODEC.encode(wire, response);
            require(response.equals(CityMapNetwork.ReplyV2.CODEC.decode(wire)), "two independent boarding points and complete geometry survive v2");
            wire.clear();
            CityMapNetwork.Reply.CODEC.encode(wire, reply);
            var legacy = CityMapNetwork.Reply.CODEC.decode(wire);
            require(legacy.features().size() == 3 && legacy.platforms().isEmpty()
                    && legacy.features().getFirst().links().equals(CityMapFeature.Links.EMPTY), "v1 reads geometry while richer associations stay server-side");
        } finally { wire.release(); }
    }

    private static void packetBudget() {
        var dim = ResourceLocation.parse("atlas:" + "d".repeat(154));
        var points = new ArrayList<BlockPos>();
        for (int n = 0; n < CityMapFeature.MAX_VERTICES; n++) points.add(pos(n, n));
        var feature = new CityMapFeature(ID, CITY, Long.MAX_VALUE, dim, CityMapFeature.Kind.PATH, CityMapFeature.Shape.LINE,
                "街".repeat(CityMapFeature.NAME), "街".repeat(CityMapFeature.HOUSE_NUMBER), "街".repeat(CityMapFeature.DESCRIPTION),
                points, points.subList(0, CityMapFeature.MAX_ENTRANCES), new CityMapFeature.Links(null,
                new CityMapFeature.Endpoint(OTHER, points.getFirst()), new CityMapFeature.Endpoint(CITY, points.getLast())));
        var stop = new PassengerService.Stop(ID, "街".repeat(96), dim, pos(2, 1), pos(2, 2), OTHER);
        var packet = new CityMapNetwork.ReplyV2(new CityMapNetwork.Reply(ID, CITY, "ok", true,
                Collections.nCopies(CityMapBook.MAX, feature), Collections.nCopies(96, stop)));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CityMapNetwork.ReplyV2.CODEC.encode(buf, packet);
            require(buf.readableBytes() + 150 * 1024 < 1_048_576, "maximum district and platform catalog leaves 150 KiB for rails");
        } finally { buf.release(); }
    }
}
