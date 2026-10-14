package com.example.examplemod.city;

import com.example.examplemod.client.map.CityEmblemCanvas;
import com.example.examplemod.network.CityDirectoryPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import java.util.*;

/** Real SavedData roundtrips, permission conflicts, public discovery and pixel editing. */
public final class CityIdentityCheck {
    private static int checks;
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        var dimension = Level.OVERWORLD.location();
        CityManager manager = new CityManager(); UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        City city = manager.createCity("Identity", owner, dimension, new BlockPos(-120, 64, 75));
        require(city != null, "create test city");
        String image = CityIdentity.template(1), description = "Промышленный город\nУ реки";
        int colour = CityColors.PALETTE.get(7);
        require(manager.updateIdentity(city, stranger, 0, colour, description, image).equals("forbidden"), "stranger cannot edit");
        UUID resident = UUID.randomUUID(), builder = UUID.randomUUID(), admin = UUID.randomUUID();
        city.addMember(resident, CityMember.Role.RESIDENT); city.addMember(builder, CityMember.Role.BUILDER); city.addMember(admin, CityMember.Role.ADMIN);
        for (UUID actor : List.of(resident, builder)) require(manager.updateIdentity(city, actor, 0, colour, description, image).equals("forbidden"), "non-admin member cannot edit");
        require(manager.updateIdentity(city, owner, 0, colour, description, image).equals("saved"), "owner can edit");
        require(city.getIdentityRevision() == 1 && city.getColorRgb() == colour, "atomic revision and colour");
        require(manager.updateIdentity(city, admin, 0, colour, "stale", image).equals("conflict"), "stale admin draft rejected");
        require(city.getIdentity().description().equals(description), "conflict cannot overwrite newer description");
        require(manager.updateIdentity(city, admin, 1, colour, "Admin", image).equals("saved"), "admin may save current draft");
        for (String invalid : List.of("bad\u00a7c", "a".repeat(401), "bad\u0000")) require(manager.updateIdentity(city, owner, 2, colour, invalid, image).equals("invalid"), "invalid text rejected");
        require(manager.updateIdentity(city, owner, 2, 0, "valid", image).equals("invalid"), "invalid territory colour rejected");
        require(!CityIdentity.valid("", "g".repeat(1024)) && !CityIdentity.valid("", "0".repeat(1023)), "bounded raster validation");
        CompoundTag saved = manager.save(new CompoundTag(), null);
        CityManager restored = CityManager.load(saved, null); City reloaded = restored.getCity(city.getId());
        require(reloaded.getIdentity().equals(city.getIdentity()) && reloaded.getIdentityRevision() == 2 && reloaded.getColorRgb() == colour, "description/emblem/revision/colour survive restart");
        for (var raw : saved.getList("Cities", 10)) { var tag = (CompoundTag)raw; tag.remove("Description"); tag.remove("Emblem"); tag.remove("IdentityRevision"); }
        var legacy = CityManager.load(saved, null).getCity(city.getId());
        require(legacy.getIdentity().description().isEmpty() && legacy.getIdentity().pixels().equals(CityIdentity.template(0)) && legacy.getIdentityRevision() == 0, "legacy cities get a stable default emblem");
        require(CityIdentity.restore("", "broken").pixels().equals(CityIdentity.template(0)), "corrupt saved raster recovers");
        require(CityIdentity.restore(description, "broken").description().equals(description), "corrupt emblem never deletes a valid saved description");
        for (int i = 0; i < 40; i++) require(manager.createCity("Foreign" + i, UUID.randomUUID(), dimension, new BlockPos(100000 + i * 500, 64, -100000)) != null, "create distant foreign city");
        manager.createCity("Nether", UUID.randomUUID(), Level.NETHER.location(), new BlockPos(-120, 64, 75));
        var publicCities = CityPublicAtlas.profiles(manager, stranger, dimension);
        require(publicCities.size() == 41 && publicCities.stream().noneMatch(CityPublicProfile::editable), "outsider sees every city beyond radius and 16-city limit");
        require(publicCities.stream().noneMatch(CityPublicProfile::own), "outsider has no fabricated ownership");
        require(CityPublicAtlas.profiles(manager, owner, dimension).stream().filter(CityPublicProfile::editable).count() == 1, "edit affordance uses real city role");
        require(CityPublicAtlas.profiles(manager, stranger, Level.NETHER.location()).size() == 1, "dimensions stay isolated");
        List<Integer> thousands = java.util.stream.IntStream.range(0, 1025).boxed().toList(); List<Integer> decoded = new ArrayList<>();
        for (int offset = 0; offset < thousands.size(); offset += CityDirectoryPager.PAGE_SIZE) { var page = CityDirectoryPager.page(thousands, offset); require(page.size() <= 32, "packet page bounded"); decoded.addAll(page); }
        require(decoded.equals(thousands), "all 1025 entries arrive without omissions or duplicates");
        require(CityDirectoryPager.page(thousands, 1025).isEmpty(), "final empty page safe");
        try { new CityDirectoryPayload(UUID.randomUUID(), BlockPos.ZERO, dimension, 0, 41, List.of(dimension), publicCities); throw new AssertionError("oversized page accepted"); } catch (IllegalArgumentException expected) { checks++; }
        CityEmblemCanvas canvas = new CityEmblemCanvas("0".repeat(1024)); String empty = canvas.image();
        canvas.begin(); canvas.paint(0, 0, 2, true); canvas.paint(1, 0, 2, true); canvas.end();
        require(canvas.image().charAt(31) == '2' && canvas.image().charAt(30) == '2', "symmetric brush"); canvas.undo(); require(canvas.image().equals(empty), "one undo removes entire stroke"); canvas.redo(); require(canvas.image().charAt(0) == '2', "redo restores stroke");
        canvas.begin(); canvas.fill(1, 1, 3); canvas.end(); require(canvas.image().charAt(1023) == '3' && canvas.image().charAt(0) == '2', "bounded flood fill preserves border ink");
        canvas.undo(); require(canvas.image().charAt(1023) == '0', "fill undo");
        for (int i = 0; i < 3; i++) { canvas.template(i); require(CityIdentity.valid("", canvas.image()), "valid native template"); }
        var session = com.example.examplemod.client.CityWorldMapSession.INSTANCE;
        session.awaitAnchor();
        session.acceptDirectory(new CityDirectoryPayload(UUID.randomUUID(), new BlockPos(-10, 64, 25), dimension, 0, 0, List.of(dimension), List.of()));
        require(session.isReady() && session.getDimensionId().equals(dimension), "real session opens public atlas without city membership");
        session.acceptMapTile(new com.example.examplemod.network.CityMapTilePayload(session.getPylonPos(), Level.NETHER.location(), 0, 0, true, new int[1024], new BitSet()));
        require(session.drainMapTiles().isEmpty(), "real session rejects stale cross-dimension terrain");
        session.acceptMapTile(new com.example.examplemod.network.CityMapTilePayload(session.getPylonPos(), dimension, 0, 0, true, new int[1024], new BitSet()));
        require(session.drainMapTiles().size() == 1, "real session accepts current public map terrain");
        session.clear(); require(!session.isReady() && session.getCityName().isEmpty(), "closed session clears identity");
        require(com.example.examplemod.client.CityPublicDirectory.profile(null) == null, "uncreated pylon card cannot crash on null city id");
        var page = new CityDirectoryPayload(UUID.randomUUID(), city.getPylonPos(), dimension, 32, 41, List.of(dimension, Level.NETHER.location()), CityDirectoryPager.page(publicCities, 32));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            CityDirectoryPayload.STREAM_CODEC.encode(buffer, page);
            require(CityDirectoryPayload.STREAM_CODEC.decode(buffer).equals(page), "real public directory codec preserves final page, raster and roles");
            buffer.clear();
            var edit = new com.example.examplemod.network.SetCityIdentityPayload(city.getId(), UUID.randomUUID(), city.getIdentityRevision(), colour, description, image);
            com.example.examplemod.network.SetCityIdentityPayload.STREAM_CODEC.encode(buffer, edit);
            require(com.example.examplemod.network.SetCityIdentityPayload.STREAM_CODEC.decode(buffer).equals(edit), "edit codec preserves exact draft and request identity");
            buffer.clear();
            var ack = new com.example.examplemod.network.CityIdentityResultPayload(city.getId(), edit.requestId(), "saved");
            com.example.examplemod.network.CityIdentityResultPayload.STREAM_CODEC.encode(buffer, ack);
            require(com.example.examplemod.network.CityIdentityResultPayload.STREAM_CODEC.decode(buffer).equals(ack), "save acknowledgement preserves request identity");
        } finally { buffer.release(); }
        var projection = com.example.examplemod.client.map.CityMapCities.merge(List.of(), publicCities, true);
        require(com.example.examplemod.client.map.CityMapCities.matching(projection, "Foreign").size() == 40, "map search projection includes every distant foreign city");
        require(com.example.examplemod.client.map.CityMapCities.matching(projection, "  IDENTITY ").size() == 1, "case-insensitive city card search");
        var emptyProjection = com.example.examplemod.client.map.CityMapCities.merge(projection, List.of(), true);
        require(emptyProjection.isEmpty(), "deleted cities disappear from public map");
        var known = new com.example.examplemod.network.CityMapPeersPayload.Peer(city.getId(), city.getName(), city.getPylonPos().getX(), city.getPylonPos().getZ(), colour, city.getClaimedPlots(), city.getMemberCount(), true, false);
        var merged = com.example.examplemod.client.map.CityMapCities.merge(List.of(known), publicCities, true);
        require(merged.size() == 41 && merged.stream().filter(c -> c.cityId().equals(city.getId())).findFirst().orElseThrow().plots().equals(city.getClaimedPlots()), "directory merge preserves actual nearby claims without duplicate markers");
        CityEmblemCanvas stroke = new CityEmblemCanvas("0".repeat(1024)); stroke.begin(); stroke.line(0, 0, 31, 31, 2, false); stroke.end();
        for (int i = 0; i < 32; i++) require(stroke.image().charAt(i * 33) == '2', "fast brush stroke has no pixel gaps");
        stroke.undo(); require(stroke.image().equals("0".repeat(1024)), "one undo removes interpolated brush stroke");
        System.out.println("PASS: " + checks + " city identity checks");
    }
}
