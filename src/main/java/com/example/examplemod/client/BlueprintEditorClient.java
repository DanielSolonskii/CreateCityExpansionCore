package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.screen.BlueprintEditorScreen;
import com.example.examplemod.devtools.BlueprintEditorAccess;
import com.example.examplemod.devtools.BlueprintEditorFiles;
import com.example.examplemod.devtools.BlueprintEditorFiles.Project;
import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.devtools.BlueprintEditorWorld.Capture;
import com.example.examplemod.multiblock.BlueprintCatalog.Plan;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class BlueprintEditorClient {
    private enum Mode { IDLE, SELECT, PLACE }

    private static Mode mode = Mode.IDLE;
    private static ClientLevel world;
    private static Project project;
    private static BlockPos first;
    private static int selectionHeight;
    private static Tier tier = Tier.I;
    private static Plan placement;
    private static Direction facing;
    private static boolean held;
    private static boolean busy;

    private BlueprintEditorClient() {}

    public static Project project() {
        return project;
    }

    public static boolean busy() {
        return busy;
    }

    public static void selectProject(Path path, Consumer<Component> feedback, Runnable refresh) {
        submit(player -> {
            Project selected = BlueprintEditorFiles.open(path);
            MineBlueprints.setLocalOverride(selected.layout());
            return selected;
        }, selected -> {
            project = selected;
            refresh.run();
            feedback.accept(label("project_loaded"));
        }, feedback);
    }

    public static void save(Tier selectedTier, Capture capture, Consumer<Component> feedback, Runnable refresh) {
        Project selectedProject = project;
        if (selectedProject == null) {
            feedback.accept(label("choose_first"));
            return;
        }
        submit(player -> BlueprintEditorFiles.replace(selectedProject, selectedTier, capture), updated -> {
            project = updated;
            refresh.run();
            feedback.accept(label("saved", BlueprintEditorFiles.relativeFile(selectedTier)));
        }, feedback);
    }

    public static void beginPlacement(Tier selectedTier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        tier = selectedTier;
        placement = MineBlueprints.current().plan(tier);
        facing = minecraft.player.getDirection().getOpposite();
        mode = Mode.PLACE;
        minecraft.setScreen(null);
        actionBar(label("place_controls"));
    }

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.player == null || minecraft.screen != null
                || !minecraft.player.getItemInHand(event.getHand()).is(CreateCityExpansionCore.BLUEPRINT_EDITOR.get())) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (held || busy) {
            return;
        }
        held = true;
        if (minecraft.player.isShiftKeyDown()) {
            submit(player -> true, ignored -> {
                mode = mode == Mode.SELECT ? Mode.IDLE : Mode.SELECT;
                first = null;
                selectionHeight = 0;
                placement = null;
                actionBar(label(mode == Mode.SELECT ? "select_first" : "selection_cancelled"));
            }, BlueprintEditorClient::actionBar);
        } else if (mode == Mode.SELECT) {
            pickPoint(minecraft);
        } else if (mode == Mode.PLACE) {
            Plan plan = placement;
            Direction orientation = facing;
            submit(player -> {
                BlueprintEditorWorld.place(player.serverLevel(), plan, BlueprintEditorWorld.aim(player, true), orientation);
                return true;
            }, ignored -> {
                mode = Mode.IDLE;
                placement = null;
                actionBar(label("placed", plan.tier().name()));
            }, BlueprintEditorClient::actionBar);
        } else {
            submit(player -> true, ignored -> minecraft.setScreen(new BlueprintEditorScreen(tier, null)),
                    BlueprintEditorClient::actionBar);
        }
    }

    private static void pickPoint(Minecraft minecraft) {
        BlockPos start = first;
        int height = selectionHeight;
        if (start == null) {
            submit(player -> BlueprintEditorWorld.aim(player, false).above(height), point -> {
                first = point;
                selectionHeight = 0;
                actionBar(label("select_second", point.toShortString()));
            }, BlueprintEditorClient::actionBar);
        } else {
            submit(player -> BlueprintEditorWorld.capture(player.serverLevel(), start, BlueprintEditorWorld.aim(player, false).above(height)),
                    capture -> {
                        mode = Mode.IDLE;
                        first = null;
                        selectionHeight = 0;
                        minecraft.setScreen(new BlueprintEditorScreen(tier, capture));
                    }, BlueprintEditorClient::actionBar);
        }
    }

    @SubscribeEvent
    public static void rotate(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (busy || minecraft.screen != null || !holdingEditor(minecraft)) {
            return;
        }
        if (mode == Mode.PLACE && event.getKey() == GLFW.GLFW_KEY_R && event.getAction() == GLFW.GLFW_PRESS) {
            facing = facing.getClockWise();
        }
        if (mode == Mode.SELECT && (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0
                && (event.getAction() == GLFW.GLFW_PRESS || event.getAction() == GLFW.GLFW_REPEAT)
                && (event.getKey() == GLFW.GLFW_KEY_UP || event.getKey() == GLFW.GLFW_KEY_DOWN)) {
            int step = event.getKey() == GLFW.GLFW_KEY_UP ? 1 : -1;
            int height = minecraft.level == null ? 0 : minecraft.level.getHeight() - 1;
            selectionHeight = Math.clamp(selectionHeight + step, -height, height);
            actionBar(label("selection_height", selectionHeight));
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (world != minecraft.level) {
            world = minecraft.level;
            resetPlacement();
        }
        if (!minecraft.options.keyUse.isDown()) {
            held = false;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetPlacement();
        project = null;
        tier = Tier.I;
        world = null;
    }

    private static void resetPlacement() {
        mode = Mode.IDLE;
        first = null;
        selectionHeight = 0;
        placement = null;
        busy = false;
        held = false;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || mode == Mode.IDLE
                || !holdingEditor(minecraft)
                || !(minecraft.player.pick(BlueprintEditorWorld.REACH, 1.0F, false) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || minecraft.level == null) {
            return;
        }
        AABB bounds;
        List<BlockPos> blocked = new ArrayList<>();
        if (mode == Mode.SELECT) {
            BlockPos end = hit.getBlockPos().above(selectionHeight);
            bounds = box(first == null ? end : first, end);
        } else {
            BlockPos target = hit.getBlockPos().relative(hit.getDirection());
            BlockPos min = target;
            BlockPos max = target;
            for (var part : placement.parts()) {
                BlockPos pos = placement.worldPosition(part.position(), target, facing);
                min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
                max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
                if (blocked.size() < 32 && (!minecraft.level.hasChunkAt(pos)
                        || !BlueprintEditorWorld.canPlace(part.state(), minecraft.level.getBlockState(pos)))) {
                    blocked.add(pos);
                }
            }
            bounds = box(min, max);
        }
        var camera = event.getCamera().getPosition();
        var buffer = minecraft.renderBuffers().bufferSource();
        var lines = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(event.getPoseStack(), lines, bounds.move(-camera.x, -camera.y, -camera.z),
                blocked.isEmpty() ? 0.35f : 1f, blocked.isEmpty() ? 0.85f : 0.3f, 0.35f, 1f);
        for (BlockPos pos : blocked) {
            LevelRenderer.renderLineBox(event.getPoseStack(), lines, box(pos, pos).move(-camera.x, -camera.y, -camera.z),
                    1f, 0.25f, 0.25f, 0.9f);
        }
        buffer.endBatch(RenderType.lines());
    }

    private static AABB box(BlockPos a, BlockPos b) {
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    private static boolean holdingEditor(Minecraft minecraft) {
        return minecraft.player != null && (minecraft.player.getMainHandItem().is(CreateCityExpansionCore.BLUEPRINT_EDITOR.get())
                || minecraft.player.getOffhandItem().is(CreateCityExpansionCore.BLUEPRINT_EDITOR.get()));
    }

    @FunctionalInterface
    private interface ServerAction<T> {
        T run(ServerPlayer player) throws Exception;
    }

    private static <T> void submit(ServerAction<T> action, Consumer<T> done, Consumer<Component> failure) {
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        ClientLevel activeWorld = minecraft.level;
        if (busy) {
            return;
        }
        if (server == null || minecraft.player == null || activeWorld == null) {
            failure.accept(label("access_denied"));
            return;
        }
        var id = minecraft.player.getUUID();
        busy = true;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (!BlueprintEditorAccess.canUse(player) || !player.serverLevel().dimension().equals(activeWorld.dimension())) {
                finish(minecraft, activeWorld, () -> failure.accept(label("access_denied")));
                return;
            }
            try {
                T result = action.run(player);
                finish(minecraft, activeWorld, () -> done.accept(result));
            } catch (Exception exception) {
                String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                finish(minecraft, activeWorld, () -> failure.accept(label("error", reason)));
            }
        });
    }

    private static void finish(Minecraft minecraft, ClientLevel activeWorld, Runnable action) {
        minecraft.execute(() -> {
            if (minecraft.level == activeWorld) {
                busy = false;
                action.run();
            }
        });
    }

    private static void actionBar(Component component) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(component, true);
        }
    }

    public static Component label(String suffix, Object... args) {
        return Component.translatable(BlueprintEditorAccess.MESSAGE + suffix, args);
    }
}
