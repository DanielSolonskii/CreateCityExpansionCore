package com.example.examplemod.chunkloader;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Contract checks for the chunk loader's limit, ownership and persistence.
 *
 * <p>Runs headless against {@link ChunkLoaderSavedData} directly. That is deliberate: the limit is a plain data
 * question, and the failure this guards against — a limit recomputed from whatever chunks happen to be loaded
 * — only shows up once a player walks away, which is far too late. Pinning the data model catches it before a
 * server ever starts.
 *
 * <p>What cannot be checked here is the ticket. Whether a real chunk stops ticking, and whether a restart
 * restores it, needs a live server, because NeoForge's {@code ForcedChunkManager} writes into
 * {@code ForcedChunksSavedData} and reinstates through {@code ServerLevel}. Those are on the manual checklist.
 */
public final class ChunkLoaderSavedDataCheck {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        limitCountsUpToThreeAndThenRefuses();
        removalFreesExactlyOneSlot();
        duplicateRegistrationDoesNotConsumeASlot();
        dimensionsShareOnePersonalLimit();
        ownersAreIndependent();
        ownershipIsTheUuidNotTheName();
        persistenceRoundTrips();
        persistenceKeepsDimensionsApart();
        limitConstantIsSingleAndThree();

        System.out.println("PASS: " + checks + " chunk loader limit, ownership and persistence checks");
    }

    private static ChunkLoaderSavedData fresh() {
        return new ChunkLoaderSavedData();
    }

    /**
     * Dimension ids are written out rather than read from {@code Level.OVERWORLD.dimension()}.
     *
     * <p>Touching {@code Level} pulls in registry state that cannot be initialised in a plain JVM, and this
     * check is deliberately registry-free. These are the same ids vanilla uses; the registry model only ever
     * compares them for equality.
     */
    private static ResourceLocation overworld() {
        return ResourceLocation.withDefaultNamespace("overworld");
    }

    private static ResourceLocation nether() {
        return ResourceLocation.withDefaultNamespace("the_nether");
    }

    // ------------------------------------------------------------------ the limit

    private static void limitCountsUpToThreeAndThenRefuses() {
        ChunkLoaderSavedData data = fresh();
        UUID owner = UUID.randomUUID();

        require(data.canPlaceMore(owner), "0/3 must allow a placement");
        require(data.count(owner) == 0, "a new owner starts at zero");

        data.register(owner, overworld(), new BlockPos(0, 64, 0));
        require(data.canPlaceMore(owner) && data.count(owner) == 1, "1/3 must allow a placement");

        data.register(owner, overworld(), new BlockPos(16, 64, 0));
        require(data.canPlaceMore(owner) && data.count(owner) == 2, "2/3 must allow a placement");

        data.register(owner, overworld(), new BlockPos(32, 64, 0));
        require(data.count(owner) == 3, "the third loader must be counted");
        require(!data.canPlaceMore(owner), "3/3 must refuse a fourth placement");
    }

    private static void removalFreesExactlyOneSlot() {
        ChunkLoaderSavedData data = fresh();
        UUID owner = UUID.randomUUID();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(16, 64, 0);
        BlockPos third = new BlockPos(32, 64, 0);
        data.register(owner, overworld(), first);
        data.register(owner, overworld(), second);
        data.register(owner, overworld(), third);
        require(!data.canPlaceMore(owner), "the owner must start full");

        require(data.unregister(owner, overworld(), second), "removing a registered loader must report success");
        require(data.count(owner) == 2, "removing one loader must free exactly one slot");
        require(data.canPlaceMore(owner), "2/3 must allow a placement again");

        require(data.unregister(owner, overworld(), first), "removing the first loader must report success");
        require(data.unregister(owner, overworld(), third), "removing the last loader must report success");
        require(data.count(owner) == 0, "removing every loader must leave the owner at 0");

        require(!data.unregister(owner, overworld(), first),
                "removing a loader that is already gone must be a no-op, not an error");
    }

    /**
     * Two placement events arriving for the same position must not consume two slots, which is the race that
     * could otherwise produce a 4/3 out of a single block.
     */
    private static void duplicateRegistrationDoesNotConsumeASlot() {
        ChunkLoaderSavedData data = fresh();
        UUID owner = UUID.randomUUID();
        BlockPos pos = new BlockPos(0, 64, 0);

        require(data.register(owner, overworld(), pos), "the first registration is new");
        require(!data.register(owner, overworld(), pos),
                "registering the same position again must report it was already known");
        require(data.count(owner) == 1, "a duplicate registration must not consume a second slot");
    }

    // ------------------------------------------------------------------ dimensions

    /** Two in the overworld and one in the nether are three of the same three; a fourth anywhere is refused. */
    private static void dimensionsShareOnePersonalLimit() {
        ChunkLoaderSavedData data = fresh();
        UUID owner = UUID.randomUUID();

        data.register(owner, overworld(), new BlockPos(0, 64, 0));
        data.register(owner, overworld(), new BlockPos(16, 64, 0));
        require(data.canPlaceMore(owner), "2/3 across dimensions must still allow a placement");

        data.register(owner, nether(), new BlockPos(0, 64, 0));
        require(data.count(owner) == 3, "the nether loader must count against the same personal three");
        require(!data.canPlaceMore(owner), "3/3 must refuse a fourth loader in any dimension");

        // Same coordinates, two dimensions: two loaders, charged separately. A fresh owner is used so the
        // positions are genuinely new rather than already-held entries.
        UUID fresh = UUID.randomUUID();
        require(data.register(fresh, overworld(), new BlockPos(0, 64, 0)),
                "the overworld loader is a new entry");
        require(data.register(fresh, nether(), new BlockPos(0, 64, 0)),
                "the same coordinates in the nether are a separate new entry, not a duplicate");
        require(data.count(fresh) == 2,
                "identical coordinates in two dimensions must be two separate loaders");

        // And a re-register of an existing entry still does not grow the count.
        require(!data.register(fresh, nether(), new BlockPos(0, 64, 0)),
                "re-registering an existing nether loader must report it was already known");
        require(data.count(fresh) == 2, "a duplicate must not grow the count across dimensions either");
    }

    private static void ownersAreIndependent() {
        ChunkLoaderSavedData data = fresh();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        data.register(first, overworld(), new BlockPos(0, 64, 0));
        data.register(first, overworld(), new BlockPos(16, 64, 0));
        data.register(first, overworld(), new BlockPos(32, 64, 0));

        require(!data.canPlaceMore(first), "the first player is full");
        require(data.canPlaceMore(second), "a second player must be unaffected by the first being full");

        data.register(second, overworld(), new BlockPos(48, 64, 0));
        require(data.count(second) == 1, "the second player counts only their own loaders");
        require(data.count(first) == 3, "the first player's count must be untouched");
    }

    /** Ownership is the UUID. Names are never stored, so a rename cannot move or lose a loader. */
    private static void ownershipIsTheUuidNotTheName() {
        ChunkLoaderSavedData data = fresh();
        UUID owner = UUID.randomUUID();
        BlockPos pos = new BlockPos(0, 64, 0);
        data.register(owner, overworld(), pos);

        require(data.ownerAt(overworld(), pos).equals(owner),
                "the loader must resolve to the UUID that placed it");

        // Standing in for a rename: nothing in the registry holds a name, so the same UUID is the same owner
        // regardless of what the account is called now.
        require(data.ownerAt(overworld(), pos).equals(owner),
                "a rename must not change which loaders belong to the player");
        require(data.count(owner) == 1, "a rename must not add or remove a loader");

        require(data.ownerAt(overworld(), new BlockPos(999, 64, 0)) == null,
                "a position with no loader must resolve to no owner");
    }

    // ------------------------------------------------------------------ persistence

    private static void persistenceRoundTrips() {
        ChunkLoaderSavedData data = fresh();
        UUID owner = UUID.randomUUID();
        BlockPos pos = new BlockPos(100, 70, -250);

        data.register(owner, overworld(), new BlockPos(0, 64, 0));
        data.register(owner, overworld(), new BlockPos(16, 64, 0));
        data.register(owner, overworld(), pos);
        require(!data.canPlaceMore(owner), "the owner must be full before the round trip");

        // The registry holds no holders, so it never touches the provider; null is what the rest of this project's
        // SavedData checks pass for the same reason.
        ChunkLoaderSavedData restored =
                ChunkLoaderSavedData.load(data.save(new CompoundTag(), null), null);

        require(restored.count(owner) == 3, "all three loaders must survive a save and load");
        require(restored.ownerAt(overworld(), pos).equals(owner), "ownership must survive a restart");
        require(!restored.canPlaceMore(owner),
                "a restored owner must still be at the limit, or a restart would hand out a free slot");
    }

    private static void persistenceKeepsDimensionsApart() {
        ChunkLoaderSavedData data = fresh();
        UUID owner = UUID.randomUUID();
        data.register(owner, overworld(), new BlockPos(0, 64, 0));
        data.register(owner, nether(), new BlockPos(0, 64, 0));

        ChunkLoaderSavedData restored =
                ChunkLoaderSavedData.load(data.save(new CompoundTag(), null), null);

        require(restored.count(owner) == 2, "a loader in each dimension must round-trip as two loaders");
        require(restored.ownerAt(overworld(), new BlockPos(0, 64, 0)).equals(owner),
                "the overworld loader must keep its owner across a save");
        require(restored.ownerAt(nether(), new BlockPos(0, 64, 0)).equals(owner),
                "the nether loader must keep its owner across a save");
    }

    // ------------------------------------------------------------- single source

    /** The number must live in exactly one place, so a later edit cannot widen the limit by accident. */
    private static void limitConstantIsSingleAndThree() {
        require(ChunkLoaderSavedData.MAX_LOADERS_PER_PLAYER == 3,
                "the Alpha limit is three loaders per player, but the constant says "
                        + ChunkLoaderSavedData.MAX_LOADERS_PER_PLAYER);
    }
}