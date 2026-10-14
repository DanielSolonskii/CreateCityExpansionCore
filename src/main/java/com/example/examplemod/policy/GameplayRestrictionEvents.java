package com.example.examplemod.policy;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/**
 * The only class that wires {@link GameplayRestrictions} to the server.
 *
 * <p>Every hook here is server-side and authoritative. Hiding an item from JEI or from the recipe book
 * would be a client-side courtesy any player can undo, so no rule here relies on one: a forbidden item
 * that arrives by {@code /give}, by trade or from an old world still cannot be used.
 *
 * <p>Nothing here destroys or truncates a forbidden item's contents. Placement is refused, so a
 * shulker box can never be created fresh, and an existing box stays breakable so its contents come back
 * out as normal drops instead of vanishing.
 *
 * <p>Two rules deliberately avoid cancelling an entity tick. Cancelling {@code EntityTickEvent.Pre}
 * stops the entity doing anything at all that tick, which for a Create carriage also strands a portal
 * transition and for a boat stops ordinary passenger handling. Both restrictions instead act on a value
 * the entity reads, leaving the tick itself intact.
 *
 * <p>Hook choices are recorded with the reason, because several plausible events turned out to be
 * non-cancellable or missing in NeoForge 21.1.251:
 * <ul>
 *   <li>{@code PlayerContainerEvent.Open} is <b>not</b> cancellable and there is no
 *       {@code PlayerChangeArmorEvent}, so container opening and chest-slot equipping cannot be
 *       intercepted at their natural hook. Right-click events are used instead, which is where every
 *       player-initiated shulker path actually starts.</li>
 *   <li>{@code PlayerEvent.ItemCraftedEvent} is <b>not</b> cancellable. Crafting is therefore removed
 *       data-driven instead — see the {@code neoforge:conditions} recipe overrides in this mod's
 *       resources — and this listener only explains a box that predates the change.</li>
 *   <li>{@code PlayerTickEvent.Pre} is fired from the head of {@code Player#tick()}, before the tick that
 *       applies elytra physics, which is what makes the elytra stop deterministic rather than one tick
 *       late.</li>
 * </ul>
 */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class GameplayRestrictionEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GameplayRestrictionEvents() {
    }

    // ------------------------------------------------------------------ shulker boxes

    /**
     * Placement, by a player or by anything else placing a block. Refusing here is what keeps a refused
     * box from ever existing in the world, so there is no container to open later.
     */
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!GameplayRestrictions.isShulkerBoxBlock(event.getPlacedBlock())) {
            return;
        }
        event.setCanceled(true);
        notifyIfPlayer(event.getEntity(), "message.createcityexpansioncore.restriction.shulker_place");
    }

    /**
     * Create rail placement in the Nether.
     *
     * <p>This is the safest available point for the "no railway in the Nether" rule because it needs no
     * Create internals at all: a carriage cannot be assembled without a rail to assemble it on, and a
     * route cannot be planned across track that does not exist. It also leaves everything about existing
     * trains and portal transitions completely untouched.
     *
     * <p>What it cannot cover is a train that was already running and drives itself through a portal.
     * That case is handled by the speed cap below, not here.
     */
    @SubscribeEvent
    public static void onTrackPlace(BlockEvent.EntityPlaceEvent event) {
        if (!GameplayRestrictions.isCreateTrackBlock(event.getPlacedBlock().getBlock())) {
            return;
        }
        if (GameplayRestrictions.trainsAllowedIn(event.getBlockSnapshot().getDimension())) {
            return;
        }
        event.setCanceled(true);
        notifyIfPlayer(event.getEntity(), "message.createcityexpansioncore.restriction.nether_track");
    }

    /**
     * Right-clicking a block. Two cases matter and both land here: opening an already placed shulker box,
     * and placing a shulker box item against some other block.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        boolean placedBox = GameplayRestrictions.isShulkerBoxBlock(event.getLevel().getBlockState(event.getPos()));
        boolean heldBox = GameplayRestrictions.isShulkerBoxItem(event.getItemStack());
        if (!placedBox && !heldBox) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyIfPlayer(event.getEntity(), placedBox
                ? "message.createcityexpansioncore.restriction.shulker_open"
                : "message.createcityexpansioncore.restriction.shulker_place");
    }

    /**
     * Right-clicking a shulker box item in hand or in the inventory. This is the path that opens a box as
     * a carried container, and it is separate from the block path, so it needs its own hook.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!GameplayRestrictions.isShulkerBoxItem(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyIfPlayer(event.getEntity(), "message.createcityexpansioncore.restriction.shulker_open");
    }

    /**
     * Explains a freshly crafted box instead of silently handing out a dead item. Crafting is already
     * removed data-driven, so this only fires for a box crafted from a recipe that survived a datapack
     * override. The event fires after the result is taken and cannot be cancelled, so this is
     * informational only.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (GameplayRestrictions.isShulkerBoxItem(event.getCrafting())) {
            notify(event.getEntity(), "message.createcityexpansioncore.restriction.shulker_crafted");
        }
    }

    // -------------------------------------------------------------------- elytra flight

    /**
     * Elytra flight, refused before the tick that applies it.
     *
     * <p>{@code PlayerTickEvent.Pre} runs at the head of {@code Player#tick()}, while the glide physics
     * live further down the same tick inside {@code LivingEntity}. Clearing the flag first means it is
     * never true when the physics read it, whatever order packet handling happens in. The only way back
     * into flight is {@code Player#tryToStartFallFlying()}, which runs in the packet handler and needs an
     * elytra in the chest slot — so the item itself is left in place and simply never grants flight.
     *
     * <p>Nothing removes the elytra from anywhere. In 1.21.1 vanilla grants it from no recipe and no loot
     * table at all, so there is no normal acquisition left to close either.
     */
    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || !player.isFallFlying()) {
            return;
        }
        player.stopFallFlying();
        notifyIfPlayer(serverPlayer, "message.createcityexpansioncore.restriction.elytra");
    }

    // ------------------------------------------------------------------ boats on ice

    /**
     * A speed cap, not a stopped boat. The boat and its passenger tick exactly as they would on water;
     * only the horizontal movement is capped, and only while the hull is over a bypass surface.
     *
     * <p>Clamping after the tick rather than before is deliberate. The speed bonus is read out of the
     * movement the boat's own tick produced, so clamping afterwards is the only place the cap can actually
     * apply, and it costs one tick of slide when a boat first touches ice.
     */
    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Boat boat) || !GameplayRestrictions.isOnIceBoatSurface(boat)) {
            return;
        }
        var clamped = GameplayRestrictions.clampIceBoatSpeed(
                boat.getDeltaMovement(), GameplayRestrictions.MAX_ICE_BOAT_SPEED);
        if (!boat.getDeltaMovement().equals(clamped)) {
            boat.setDeltaMovement(clamped);
            notifyRider(boat, "message.createcityexpansioncore.restriction.ice_boat");
        }
    }

    // ------------------------------------------------------------------ nether trains

    /**
     * Zeroes the speed of a train that is entirely inside the Nether. The tick is not cancelled, so the
     * carriage keeps loading its chunks, keeps its bogies consistent and keeps any portal transition it
     * is part of running to completion.
     *
     * <p>The "entirely inside" condition is what keeps a train that straddles a portal moving. Create
     * reports such a train as present in two dimensions at once, and it must be allowed to finish
     * crossing in either direction; a rule that stopped it there would leave a train frozen across a
     * portal boundary.
     */
    @SubscribeEvent
    public static void onTrainTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!GameplayRestrictions.isCreateTrain(entity)
                || GameplayRestrictions.trainsAllowedIn(entity.level())) {
            return;
        }
        if (!(entity instanceof CarriageContraptionEntity carriage)) {
            return;
        }

        // Create's live railway manager lives on its saved data rather than in a static accessor, so it is
        // read from there. If it cannot be read the rule is skipped rather than guessed at.
        var railway = RailwaySavedData.load((net.minecraft.server.MinecraftServer) entity.getServer());
        if (railway == null) {
            return;
        }
        Train train = railway.getTrains().get(carriage.trainId);
        if (train == null || !trainFullyInsideNether(train)) {
            return;
        }

        if (train.speed != 0.0D || train.targetSpeed != 0.0D) {
            LOGGER.info("Refused Create train movement in the Nether at {} in {}",
                    entity.blockPosition().toShortString(), entity.level().dimension().location());
            train.speed = 0.0D;
            train.targetSpeed = 0.0D;
            notifyNetherRiders(train);
        }
    }

    /** Tells whoever is riding the train, so a stopped train is not a mystery. */
    private static void notifyNetherRiders(Train train) {
        for (Carriage member : train.carriages) {
            var dimensional = member.getDimensionalIfPresent(net.minecraft.world.level.Level.NETHER);
            if (dimensional == null) {
                continue;
            }
            var carriageEntity = dimensional.entity.get();
            if (carriageEntity == null) {
                continue;
            }
            for (Entity passenger : carriageEntity.getPassengers()) {
                if (passenger instanceof Player rider) {
                    notifyIfPlayer(rider, "message.createcityexpansioncore.restriction.nether_train");
                }
            }
        }
    }

    /** Every carriage of the train sits in the Nether, so the train is not mid-portal. */
    private static boolean trainFullyInsideNether(Train train) {
        for (Carriage member : train.carriages) {
            if (!GameplayRestrictions.trainFullyInside(member.getPresentDimensions(),
                    net.minecraft.world.level.Level.NETHER)) {
                return false;
            }
        }
        return !train.carriages.isEmpty();
    }

    // ---------------------------------------------------------------------- messaging

    private static void notifyIfPlayer(Entity entity, String key) {
        if (entity instanceof Player player) {
            notify(player, key);
        }
    }

    private static void notifyRider(Entity vehicle, String key) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                notify(player, key);
                return;
            }
        }
    }

    private static void notify(Player player, String key) {
        if (player instanceof ServerPlayer serverPlayer
                && GameplayRestrictions.shouldNotify(serverPlayer.getUUID(), key)) {
            serverPlayer.sendSystemMessage(Component.translatable(key));
        }
    }
}