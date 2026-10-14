package com.example.examplemod.profile;

import com.example.examplemod.client.screen.PlayerProfileLayout;
import com.example.examplemod.network.*;
import com.example.examplemod.projects.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import java.util.*;
import static com.example.examplemod.profile.PlayerProfile.*;
import static com.example.examplemod.projects.Project.*;

/** Production persistence, authorization, public packet privacy, historical roles and responsive geometry. */
public final class PlayerProfileCheck {
    private static int checks;
    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void rejected(Runnable action, String message) {
        boolean rejected = false; try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }
    public static void main(String[] args) {
        settings(); history(); packets(); layout(); budget();
        System.out.println("PASS: " + checks + " player profile privacy, persistence, participation and UI checks");
    }
    private static void settings() {
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        ProfileSavedData data = ProfileSavedData.load(new CompoundTag(), null);
        require(data.settings(owner).equals(Settings.defaults()), "old world with no profiles loads defaults");
        var proposed = new Settings("Городской строитель / Builder", List.of(Metric.PROJECTS, Metric.DISTANCE, Metric.JUMPS), 0);
        require(data.update(stranger, owner, proposed).equals("permission"), "forged target UUID cannot edit another profile");
        require(data.settings(owner).equals(Settings.defaults()), "denied edit has no side effects");
        require(data.update(owner, owner, proposed).equals("saved"), "owner can edit");
        require(data.settings(owner).revision() == 1, "revision advances once");
        require(data.update(owner, owner, proposed).equals("saved") && data.settings(owner).revision() == 1, "identical retry is idempotent");
        require(data.update(owner, owner, new Settings("  " + proposed.about() + "  ", proposed.showcase(), 0)).equals("saved"), "whitespace-normalized retry is also idempotent");
        require(data.update(owner, owner, new Settings("stale overwrite", DEFAULT_SHOWCASE, 0)).equals("stale"), "stale write cannot clobber settings");
        GameProfile skin = new GameProfile(owner, "Builder"); skin.getProperties().put("textures", new Property("textures", "publicTextureValue", "signature"));
        data.rememberSkin(skin);
        CompoundTag saved = data.save(new CompoundTag(), null);
        var restored = ProfileSavedData.load(saved, null);
        require(restored.settings(owner).about().equals(proposed.about()), "RU about survives restart");
        require(restored.settings(owner).showcase().equals(proposed.showcase()), "showcase survives restart");
        require(restored.skin(owner).texture().equals("publicTextureValue"), "offline head texture retained");
        require(!saved.contains("Wallet") && !saved.contains("Stats") && !saved.contains("Projects"), "settings save doesn't copy economy, vanilla counters or project model");
        CompoundTag corrupt = new CompoundTag(); corrupt.putUUID("Id", stranger); corrupt.putString("About", "x"); corrupt.putString("Texture", "x");
        saved.getList("Players", Tag.TAG_COMPOUND).add(corrupt);
        require(ProfileSavedData.load(saved, null).settings(owner).equals(restored.settings(owner)), "bad entry cannot lose other players");
        rejected(() -> new Settings("a".repeat(181), DEFAULT_SHOWCASE, 0), "about bounded");
        for (String value : List.of("new\nline", "§aColor", "bad\u202Ename", "bad\u0000value")) rejected(() -> new Settings(value, DEFAULT_SHOWCASE, 0), "format/control text refused");
        rejected(() -> new Settings("", List.of(Metric.MINED, Metric.MINED, Metric.JUMPS), 0), "duplicate showcase refused");
        rejected(() -> new Settings("", List.of(Metric.MINED), 0), "exactly three stats");
        rejected(() -> new Settings("", DEFAULT_SHOWCASE, -1), "negative revision refused");
        require(PlayerProfile.mayEdit(owner, owner) && !PlayerProfile.mayEdit(stranger, owner), "stable UUID ownership, no nickname authority");
    }
    private static Project project(UUID author, Applicant executor, Status status, Scope scope, List<Applicant> applicants) {
        return new Project(UUID.randomUUID(), UUID.randomUUID(), scope, scope == Scope.CITY ? UUID.randomUUID() : null, author,
                "Verified build", "Private project details", Category.BUILDING,
                new ProjectZone(ResourceLocation.parse("minecraft:overworld"), ProjectZone.Shape.POINT, 0, 0, 0, 0),
                List.of(new Stage("Build", status == Status.COMPLETED)), scope == Scope.SERVER ? Funding.SERVER_ISSUANCE : Funding.FREE,
                0, status, applicants, executor, 0, status == Status.COMPLETED, 1, "private review");
    }
    private static void history() {
        UUID author = UUID.randomUUID(), worker = UUID.randomUUID(), applicant = UUID.randomUUID(), resident = UUID.randomUUID();
        var executor = Applicant.player(worker); var waiting = Applicant.player(applicant);
        Project city = project(author, executor, Status.COMPLETED, Scope.CITY, List.of(executor, waiting));
        require(ProfileHistory.completed(List.of(city), worker).getFirst().roles() == ProjectEntry.EXECUTOR, "final player executor confirmed");
        require(ProfileHistory.completed(List.of(city), author).getFirst().roles() == ProjectEntry.AUTHOR, "author labelled as author, not builder");
        require(ProfileHistory.completed(List.of(city), applicant).isEmpty(), "application does not count as work");
        require(ProfileHistory.completed(List.of(city), resident).isEmpty(), "unrelated resident gets no contribution");
        Project self = project(worker, executor, Status.COMPLETED, Scope.CITY, List.of(executor));
        var combined = ProfileHistory.completed(List.of(self), worker);
        require(combined.size() == 1 && combined.getFirst().roles() == 3, "one project counts once even with two roles");
        for (Status status : List.of(Status.BUILDING, Status.REVIEW, Status.REWORK, Status.CANCELLED))
            require(ProfileHistory.completed(List.of(project(author, executor, status, Scope.CITY, List.of(executor))), worker).isEmpty(), "non-completed excluded: " + status);
        UUID cityId = UUID.randomUUID(); Applicant cityExecutor = new Applicant(ApplicantKind.CITY, cityId, worker);
        Project server = project(author, cityExecutor, Status.COMPLETED, Scope.SERVER, List.of(cityExecutor));
        var represented = ProfileHistory.completed(List.of(server), worker);
        require(represented.getFirst().roles() == ProjectEntry.REPRESENTATIVE && represented.getFirst().server(), "only retained city representative confirmed");
        require(ProfileHistory.completed(List.of(server), resident).isEmpty(), "city contract does not attribute all residents");
        Project old = ProjectCodec.read(ProjectCodec.write(city));
        require(ProfileHistory.completed(List.of(old), worker).equals(ProfileHistory.completed(List.of(city), worker)), "existing project format supplies roles unchanged");
    }
    private static void packets() {
        UUID owner = UUID.randomUUID(); var settings = Settings.defaults();
        List<Long> source = List.of(72000L, 100L, 99999999L, 1L, 77777777L, 88888888L);
        var publicStats = PlayerProfile.publicValues(source, settings, false);
        require(publicStats.equals(List.of(72000L, 100L, HIDDEN, 1L, HIDDEN, HIDDEN)), "hidden values removed before serialization");
        require(PlayerProfile.publicValues(source, settings, true).equals(source), "owner can inspect all measured stats");
        var p = new PlayerProfile(owner, "Builder", false, "", "", settings, "Builder", "skin", "sig", publicStats, 1, 0,
                List.of(new ProjectEntry(UUID.randomUUID(), "Town hall", false, ProjectEntry.EXECUTOR)));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var request = new ProfileRequestPayload(UUID.randomUUID(), owner, true, 0, settings);
            ProfileRequestPayload.STREAM_CODEC.encode(buffer, request);
            require(ProfileRequestPayload.STREAM_CODEC.decode(buffer).equals(request), "bounded request roundtrip"); buffer.clear();
            var reply = new ProfileResponsePayload(request.request(), "ok", p);
            ProfileResponsePayload.STREAM_CODEC.encode(buffer, reply);
            var decoded = ProfileResponsePayload.STREAM_CODEC.decode(buffer);
            require(decoded.equals(reply) && buffer.readableBytes() == 0, "public response roundtrip and frame consumption");
            require(decoded.profile().values().get(Metric.DISTANCE.ordinal()) == HIDDEN, "hidden stat never arrives on client"); buffer.clear();
            var missing = new ProfileResponsePayload(request.request(), "unknown", null);
            ProfileResponsePayload.STREAM_CODEC.encode(buffer, missing);
            require(ProfileResponsePayload.STREAM_CODEC.decode(buffer).equals(missing), "unknown/offline absence isn't fabricated data"); buffer.clear();
            var empty = new PlayerProfile(owner, "Builder", true, "", "", settings, "", "", "", publicStats, 0, 0, List.of());
            ProfileResponsePayload.STREAM_CODEC.encode(buffer, new ProfileResponsePayload(request.request(), "ok", empty));
            buffer.setByte(buffer.writerIndex() - 1, 127);
            rejected(() -> ProfileResponsePayload.STREAM_CODEC.decode(buffer), "oversized project list rejected before allocation");
        } finally { buffer.release(); }
        Set<String> fields = new HashSet<>(); for (var field : PlayerProfile.class.getRecordComponents()) fields.add(field.getName());
        require(Collections.disjoint(fields, Set.of("wallet", "treasury", "position", "coordinates", "applicants", "reviewNote", "token")), "public DTO contains no private economy/location/auth records");
        rejected(() -> new ProfileRequestPayload(UUID.randomUUID(), owner, false, -1, settings), "negative page refused");
        rejected(() -> new ProfileRequestPayload(UUID.randomUUID(), owner, false, 100001, settings), "unbounded page refused");
    }
    private static boolean contained(PlayerProfileLayout.Rect outer, PlayerProfileLayout.Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y() && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
    private static void layout() {
        // Regression: at GUI Scale 2 the old local scissor ended near x=862 screen pixels,
        // cutting the centered 432-GUI-pixel profile in half as in the reported screenshot.
        var centered = PlayerProfileLayout.calculate(960, 506);
        var fullClip = centered.screenBounds(centered.frame(), 0);
        var bodyClip = centered.screenBounds(centered.body(), 0);
        require(fullClip.x() == 264 && fullClip.right() == 696, "centered clip includes screen translation");
        require(bodyClip.x() == 278 && bodyClip.right() == 682, "body clip spans all three statistic cards");
        var compact = PlayerProfileLayout.calculate(320, 180);
        var animatedClip = compact.screenBounds(compact.body(), 5);
        double right = compact.x() + compact.body().right() * compact.scale();
        double bottom = compact.y() + (compact.body().bottom() + 5) * compact.scale();
        require(animatedClip.right() >= right && animatedClip.right() - right < 1, "clip applies responsive scale with outward rounding");
        require(animatedClip.bottom() >= bottom && animatedClip.bottom() - bottom < 1, "clip includes entrance animation");
        for (int width : new int[]{256, 320, 426, 640, 854, 1280, 1920}) for (int height : new int[]{144, 180, 240, 360, 480, 720, 1080}) {
            var l = PlayerProfileLayout.calculate(width, height);
            require(l.x() >= 11.9 && l.y() >= 11.9, "bounded window margins");
            require(l.x() + PlayerProfileLayout.WIDTH * l.scale() <= width - 11.9, "horizontal containment");
            require(l.y() + (l.height() + 8) * l.scale() <= height - 3.9, "animated window stays in screen");
            require(Math.abs(l.x() * 2 + PlayerProfileLayout.WIDTH * l.scale() - width) < .01, "centered window");
            require(contained(l.frame(), l.close()) && contained(l.frame(), l.footerAction()), "visible controls in frame");
            require(contained(l.body(), l.aboutField()), "edit field fits body");
            for (int i = 0; i < 3; i++) {
                require(contained(l.body(), l.picker(i)), "all editor pickers fit smallest body");
                require(contained(l.frame(), l.tab(i)), "tabs fit frame");
                var r = l.tab(i); double sx = l.x() + (r.x() + 3) * l.scale(), sy = l.y() + (r.y() + 3 + 5) * l.scale();
                require(r.contains(l.localX(sx), l.localY(sy, 5)), "animation and scale share hitbox transform");
            }
            require(l.scrollMaximum(600) == 600 - l.body().height(), "scroll reaches all project rows");
            for (int content : new int[]{0, 1, l.body().height(), 400, 10000}) {
                require(contained(l.frame(), l.scrollTrack()), "scroll input region stays in window");
                var first = l.scrollThumb(0, content); var last = l.scrollThumb(l.scrollMaximum(content), content);
                require(first.y() == l.body().y(), "scroll starts at top");
                require(last.bottom() == l.body().bottom(), "scroll reaches bottom");
                require(contained(l.scrollTrack(), first) && contained(l.scrollTrack(), last), "scroll thumb and track share bounds");
            }
        }
    }
    private static void budget() {
        long[] b = new long[2]; for (int i = 0; i < 6; i++) require(ProfileService.consume(b, 1000), "normal interactions allowed");
        require(!ProfileService.consume(b, 1999), "packet burst limited");
        require(ProfileService.consume(b, 2000), "budget recovers on real time without world tick dependency");
    }
}
