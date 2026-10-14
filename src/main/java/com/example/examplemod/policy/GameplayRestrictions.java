package com.example.examplemod.policy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Alpha gameplay-restriction policy, in one place.
 *
 * <p>Every rule here is a pure decision over registry ids, block tags and block friction. No event
 * wiring, no world mutation, no live server state. {@link GameplayRestrictionEvents} is the only class
 * that listens to anything, so the rules can be reviewed without tracing event flow, and the decisions
 * can be checked without booting a server.
 *
 * <p>Deciding on ids rather than on item or block objects is what lets one rule cover the item form
 * and the placed form at once: 1.21.1 has a {@code BlockTags#SHULKER_BOXES} but no matching item tag,
 * so a rule that read "is this a shulker box item" would need a second, parallel definition. It also
 * means an item handed over by {@code /give}, by a trade or by an old world is still recognised.
 *
 * <p>The intent is narrow. Trains, cities and industrial freight have to be worth building, so only the
 * cheap universal bypasses of physical logistics are closed:
 *
 * <ul>
 *   <li><b>Shulker boxes</b> are refused entirely. A box is a portable freight container by
 *       definition, so it competes with every warehouse the server asks players to build. Ordinary
 *       chests, barrels, hoppers and Create storage are untouched, and so is the Ender Chest.</li>
 *   <li><b>Elytra</b> cannot fly, so passenger geography keeps roads and stations relevant. The item is
 *       never removed; it simply never grants flight.</li>
 *   <li><b>Boats</b> still work on water, but not on ice, because an ice track is a faster and cheaper
 *       stand-in for a railway.</li>
 *   <li><b>Create trains</b> may not run in the Nether, so no cargo network can exploit the 1:8
 *       coordinate compression. Player portals and manual Nether travel are untouched.</li>
 * </ul>
 *
 * <p>Deliberately not restricted: manual packages, villages, Mending, drills/quarries, schematicannon,
 * beacon/Haste, ordinary contraptions, stationary warehouses, boats on water and Nether roof travel.
 */
public final class GameplayRestrictions {
    private GameplayRestrictions() {
    }

    private static final String MINECRAFT = "minecraft";

    // ------------------------------------------------------------------ shulker boxes

    /**
     * Vanilla's own tag of shulker boxes. The canonical source for the placed form, and kept ahead of
     * the id test so a datapack that adds or removes a shulker box is still honoured.
     */
    public static final net.minecraft.tags.TagKey<Block> SHULKER_BOXES = BlockTags.SHULKER_BOXES;

    private static final String UNDYED_SHULKER_BOX = "undyed_shulker_box";
    private static final String SHULKER_BOX_SUFFIX = "_shulker_box";

    /**
     * Whether an id names one of the seventeen vanilla shulker boxes: {@code undyed_shulker_box} or
     * {@code <colour>_shulker_box}.
     *
     * <p>This is not redundant with {@link #SHULKER_BOXES}. A tag lookup only means something once tags
     * are bound, and this policy has to answer correctly where that is not guaranteed — and the id
     * test is the only one that can run in a headless check at all, since 1.21.1 registry lookups throw
     * before {@code Bootstrap} has run.
     */
    public static boolean shulkerBoxId(ResourceLocation id) {
        if (id == null || !MINECRAFT.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return UNDYED_SHULKER_BOX.equals(path) || path.endsWith(SHULKER_BOX_SUFFIX);
    }

    /** The id form of {@link #shulkerBoxId}, for tests that cannot touch a registry. */
    public static boolean shulkerBoxId(String id) {
        return id != null && shulkerBoxId(ResourceLocation.tryParse(id));
    }

    public static boolean isShulkerBoxBlock(Block block) {
        return isInShulkerBoxTag(block) || shulkerBoxId(BuiltInRegistries.BLOCK.getKey(block));
    }

    public static boolean isShulkerBoxBlock(BlockState state) {
        return !state.isAir() && isShulkerBoxBlock(state.getBlock());
    }

    private static boolean isInShulkerBoxTag(Block block) {
        return block.defaultBlockState().is(SHULKER_BOXES);
    }

    /**
     * Shulker box items are named exactly like their blocks, so one id rule covers both forms. A
     * {@code BlockItem} check is kept as a second authority so a modded block added to the vanilla tag
     * cannot be carried around either.
     */
    public static boolean isShulkerBoxItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (shulkerBoxId(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return true;
        }
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && isInShulkerBoxTag(blockItem.getBlock());
    }

    // ------------------------------------------------------------------------ elytra

    private static final String ELYTRA_ID = "minecraft:elytra";

    /** The id form, for tests that cannot touch a registry. */
    public static boolean elytraId(String id) {
        return ELYTRA_ID.equals(id);
    }

    public static boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && elytraId(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    // ------------------------------------------------------------------- boat on ice

    /**
     * Friction at which a boat becomes a bypass rather than a boat. Vanilla ice sits at 0.98, ordinary
     * blocks at 0.6, and {@code Boat#getGroundFriction()} sums this value over the collision shapes
     * under the hull, so a high-friction surface is what produces the ice speed.
     *
     * <p>The threshold rather than a fixed block list is deliberate: a modded ice that copies vanilla
     * ice inherits the same friction and the same bypass, and this catches it without the policy
     * needing to know the mod exists.
     */
    public static final float MIN_ICE_BOAT_FRICTION = 0.95F;

    public static boolean isIceBoatSurface(String blockId, float friction) {
        return "minecraft:ice".equals(blockId)
                || "minecraft:packed_ice".equals(blockId)
                || "minecraft:blue_ice".equals(blockId)
                || friction >= MIN_ICE_BOAT_FRICTION;
    }

    public static boolean isIceBoatSurface(Block block, float friction) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return isIceBoatSurface(id == null ? null : id.toString(), friction);
    }

    /**
     * The block layer a hull actually rests on: the one just below its bounding box. For a boat on ice
     * this is the ice block, and for a boat floating on water it is the water block, which is why one
     * expression serves both with no special case.
     */
    public static int iceSurfaceLayer(AABB hull) {
        return Mth.floor(hull.minY - 0.1D);
    }

    /**
     * Ordinary paddled boat speed, in blocks per tick. A boat held to this on ice stays a boat: it can
     * still be paddled and steered, it just cannot be turned into a high-speed transport the way the
     * 0.98 ice friction normally allows.
     *
     * <p>Clamping rather than freezing was chosen deliberately. Cancelling the entity tick stops the
     * boat dead but also stops everything else that tick does, which is a far larger behavioural change
     * than this rule asks for. A speed cap leaves the entity and its passenger ticking normally and only
     * removes the ice bonus.
     */
    public static final double MAX_ICE_BOAT_SPEED = 0.4D;

    /**
     * Caps the horizontal part of a boat's movement and leaves the vertical part alone, so landing,
     * falling and stepping out of a boat on ice still behave normally.
     *
     * <p>Only called for a boat whose current surface has been judged a bypass surface, so boats on
     * water are never passed through here.
     */
    public static Vec3 clampIceBoatSpeed(Vec3 motion, double maxHorizontalSpeed) {
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal <= maxHorizontalSpeed || horizontal == 0.0D) {
            return motion;
        }
        double scale = maxHorizontalSpeed / horizontal;
        return new Vec3(motion.x * scale, motion.y, motion.z * scale);
    }

    /**
     * Whether a boat is riding a bypass surface. Looked up from where the hull actually is, every tick,
     * so it answers "is this boat on ice now" rather than "was this boat placed on ice" — a boat that
     * rowed out of the water onto an ice track is caught on the tick it arrives there.
     */
    public static boolean isOnIceBoatSurface(Entity boat) {
        if (boat.level().isClientSide()) {
            return false;
        }
        AABB hull = boat.getBoundingBox();
        int y = iceSurfaceLayer(hull);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(hull.minX); x <= Mth.floor(hull.maxX); x++) {
            for (int z = Mth.floor(hull.minZ); z <= Mth.floor(hull.maxZ); z++) {
                cursor.set(x, y, z);
                BlockPos pos = cursor.immutable();
                BlockState state = boat.level().getBlockState(pos);
                if (isIceBoatSurface(state.getBlock(), state.getFriction(boat.level(), pos, boat))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ nether trains

    /**
     * A Create train carriage. Create 6 has no {@code TrainCarEntity}: the carriage is a contraption
     * entity, and {@code CarriageContraptionEntity} is what a running train consists of.
     */
    public static boolean isCreateTrain(Entity entity) {
        return entity instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
    }

    /**
     * Any Create rail block. {@code ITrackBlock} is the interface the rails implement, so this covers
     * plain track, curved track and the station and signal variants without naming each block.
     */
    public static boolean isCreateTrackBlock(Block block) {
        return block instanceof com.simibubi.create.content.trains.track.ITrackBlock;
    }

    /**
     * Trains may run anywhere except the Nether. Kept on the dimension key rather than on a live level
     * so the rule stays a pure decision.
     */
    public static boolean trainsAllowedIn(ResourceKey<Level> dimension) {
        return !dimension.equals(Level.NETHER);
    }

    /**
     * Whether a train is entirely inside one dimension, given the dimensions its carriages are present
     * in.
     *
     * <p>This is the whole reason the Nether rule does not stop a train outright. Create models a train
     * straddling a portal as present in several dimensions at once, and a rule that froze it there would
     * strand it halfway through a portal transition with its bogies and its chunk loading split across
     * two dimensions. Requiring every carriage to be in the Nether means a train that is mid-crossing
     * keeps its speed and can finish getting out, while a train properly inside the Nether cannot run.
     *
     * <p>An empty list is not "fully inside": a train with no carriage present cannot be reasoned about,
     * so it is left alone rather than stopped.
     */
    public static boolean trainFullyInside(List<ResourceKey<Level>> presentDimensions, ResourceKey<Level> dimension) {
        return !presentDimensions.isEmpty() && presentDimensions.size() == 1
                && presentDimensions.getFirst().equals(dimension);
    }

    /**
     * Checked per dimension rather than once at load, so a train that crosses a portal with it stops
     * at the border instead of continuing.
     */
    public static boolean trainsAllowedIn(Level level) {
        return trainsAllowedIn(level.dimension());
    }

    // ---------------------------------------------------------------- player notifications

    /**
     * Throttles a refusal message per player per reason. The boat and elytra rules run every tick, so
     * without this a refused player is flooded; four seconds is long enough to read a short line and
     * short enough that a player who tries again still hears the reason.
     */
    private static final long NOTIFY_COOLDOWN_MILLIS = 4_000L;

    private static final Map<UUID, Map<String, Long>> NOTIFIED_AT = new HashMap<>();

    /** Whether the player should be told about this refusal now. Consumes the cooldown when true. */
    public static boolean shouldNotify(UUID player, String reasonKey) {
        if (player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Map<String, Long> reasons = NOTIFIED_AT.computeIfAbsent(player, key -> new HashMap<>());
        Long last = reasons.get(reasonKey);
        if (last != null && now - last < NOTIFY_COOLDOWN_MILLIS) {
            return false;
        }
        // One entry per reason per player, but a long-lived server would still hold every id forever,
        // so anyone not seen lately gets trimmed when a new reason shows up for them.
        if (last == null && reasons.size() > 1) {
            reasons.entrySet().removeIf(entry -> now - entry.getValue() >= NOTIFY_COOLDOWN_MILLIS * 20L);
        }
        reasons.put(reasonKey, now);
        return true;
    }

    /** Drops all cooldown state. */
    public static void resetNotifications() {
        NOTIFIED_AT.clear();
    }
}