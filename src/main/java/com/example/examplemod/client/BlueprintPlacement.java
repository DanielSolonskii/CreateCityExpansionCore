package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.screen.BlueprintTerminalScreen;
import com.example.examplemod.multiblock.BlueprintCatalog.Plan;
import com.example.examplemod.multiblock.BlueprintCatalog.Structure;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

/** The projection is a client-side construction guide and never places world blocks. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class BlueprintPlacement {
    private static final int AIM_DISTANCE = 32;
    private static final int MAX_OFFSET = 64;
    private static final RenderType GHOST_LAYER = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
    private static final int GHOST_ALPHA = 112;
    private static final String MESSAGE = "gui.createcityexpansioncore.blueprint.";
    private static BlueprintPlacement active;
    private static boolean awaitRelease;

    private final ClientLevel level;
    private final Plan plan;
    private final int bottomLayer;
    private final int topLayer;
    private Direction facing;
    private List<GhostCell> cells;
    private int visibleTopLayer;
    private BlockPos aimedBlock;
    private BlockPos offset = BlockPos.ZERO;
    private BlockPos fixedAnchor;

    private record GhostCell(BlockPos offset, BlockState state, boolean addition) {}

    public static void updateBlueprintAccess(Map<Structure, Integer> tiers) {
        if (active != null && active.plan.tier().level() > tiers.getOrDefault(active.plan.structure(), 0)) {
            active = null;
        }
    }

    private BlueprintPlacement(ClientLevel level, Plan plan, Direction facing) {
        this.level = level;
        this.plan = plan;
        orient(facing);
        bottomLayer = cells.stream().mapToInt(cell -> cell.offset().getY()).min().orElse(0);
        topLayer = cells.stream().mapToInt(cell -> cell.offset().getY()).max().orElse(0);
        visibleTopLayer = topLayer;
    }

    private void orient(Direction facing) {
        this.facing = facing;
        int modelTopLayer = BlueprintBlockModels.topLayer(plan);
        cells = plan.parts().stream()
                .map(part -> new GhostCell(plan.worldPosition(part.position(), BlockPos.ZERO, facing),
                        BlueprintBlockModels.state(part, modelTopLayer, facing),
                        plan.tier() != Tier.I && part.addedIn() == plan.tier()))
                .toList();
    }

    public static void begin(Plan plan) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        active = new BlueprintPlacement(minecraft.level, plan, minecraft.player.getDirection().getOpposite());
        minecraft.setScreen(null);
        active.updateAim(minecraft);
        showMessage(minecraft, "placement_controls");
    }

    @SubscribeEvent
    public static void useTerminal(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.screen != null || minecraft.player == null
                || !minecraft.player.getItemInHand(event.getHand()).is(CreateCityExpansionCore.BLUEPRINT_TERMINAL.get())) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (awaitRelease) {
            return;
        }
        awaitRelease = true;
        clearIfLevelChanged(minecraft);
        if (active != null && minecraft.player.isShiftKeyDown()) {
            active = null;
            showMessage(minecraft, "projection_cleared");
        } else if (active != null && active.fixedAnchor == null) {
            active.updateAim(minecraft);
            BlockPos target = active.anchor();
            if (target == null) {
                showMessage(minecraft, "aim_at_block");
                return;
            }
            active.fixedAnchor = target;
            showMessage(minecraft, "projection_fixed",
                    Component.keybind("key.createcityexpansioncore.build_guide.next_layer"),
                    Component.keybind("key.createcityexpansioncore.build_guide.previous_layer"));
        } else {
            minecraft.setScreen(new BlueprintTerminalScreen());
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        clearIfLevelChanged(minecraft);
        if (!minecraft.options.keyUse.isDown()) {
            awaitRelease = false;
        }
        int layerChange = 0;
        while (BuildGuideKeyMappings.consumeNext()) {
            layerChange++;
        }
        while (BuildGuideKeyMappings.consumePrevious()) {
            layerChange--;
        }
        if (active == null || minecraft.screen != null) {
            return;
        }
        if (active.fixedAnchor == null) {
            active.updateAim(minecraft);
        } else if (layerChange != 0) {
            active.visibleTopLayer = Mth.clamp(active.visibleTopLayer + layerChange,
                    active.bottomLayer, active.topLayer);
            showMessage(minecraft, "projection_layers", active.visibleTopLayer - active.bottomLayer + 1,
                    active.topLayer - active.bottomLayer + 1);
        }
    }

    @SubscribeEvent
    public static void adjust(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active == null || active.fixedAnchor != null || active.level != minecraft.level
                || minecraft.screen != null || minecraft.player == null
                || (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT)
                || (!minecraft.player.getMainHandItem().is(CreateCityExpansionCore.BLUEPRINT_TERMINAL.get())
                    && !minecraft.player.getOffhandItem().is(CreateCityExpansionCore.BLUEPRINT_TERMINAL.get()))) {
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_R && event.getAction() == GLFW.GLFW_PRESS) {
            active.orient(active.facing.getClockWise());
            return;
        }
        Direction forward = minecraft.player.getDirection();
        boolean shift = (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        Direction movement = switch (event.getKey()) {
            case GLFW.GLFW_KEY_LEFT -> forward.getCounterClockWise();
            case GLFW.GLFW_KEY_RIGHT -> forward.getClockWise();
            case GLFW.GLFW_KEY_UP -> shift ? Direction.UP : forward;
            case GLFW.GLFW_KEY_DOWN -> shift ? Direction.DOWN : forward.getOpposite();
            default -> null;
        };
        if (movement != null) {
            BlockPos next = active.offset.relative(movement);
            if (Math.abs(next.getX()) <= MAX_OFFSET && Math.abs(next.getY()) <= MAX_OFFSET
                    && Math.abs(next.getZ()) <= MAX_OFFSET) {
                active.offset = next;
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || active == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != active.level) {
            return;
        }
        BlockPos target = active.anchor();
        if (target == null) {
            return;
        }
        PoseStack poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = minecraft.renderBuffers().bufferSource();
        buffers.endBatch(GHOST_LAYER);
        VertexConsumer ghostVertices = buffers.getBuffer(GHOST_LAYER);
        MultiBufferSource normal = tinted(ghostVertices, 255, 255, 255, GHOST_ALPHA);
        MultiBufferSource additions = tinted(ghostVertices, 255, 225, 174, GHOST_ALPHA);
        MultiBufferSource conflict = tinted(ghostVertices, 255, 126, 126, 144);
        MultiBufferSource obstruction = tinted(ghostVertices, 255, 171, 91, 144);
        var renderer = minecraft.getBlockRenderer();
        for (GhostCell cell : active.cells) {
            if (cell.offset().getY() > active.visibleTopLayer) {
                continue;
            }
            BlockPos pos = target.offset(cell.offset());
            if (!active.level.hasChunkAt(pos)) {
                continue;
            }
            BlockState actual = active.level.getBlockState(pos);
            BlockState model = cell.state();
            boolean matches = model.getBlock() instanceof com.example.examplemod.electrical.DrillUnitBlock
                    ? actual == model : actual.is(model.getBlock());
            if (matches || (actual.isAir() && model.isAir())) {
                continue;
            }
            boolean overlaps = !actual.isAir();
            MultiBufferSource source;
            if (model.isAir()) {
                model = actual.getRenderShape() == RenderShape.MODEL
                        ? actual : Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
                source = obstruction;
            } else if (overlaps) {
                source = conflict;
            } else {
                source = cell.addition() ? additions : normal;
            }
            poses.pushPose();
            try {
                poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                if (overlaps) {
                    // Keep the same center and avoid depth fighting with occupied block faces.
                    poses.translate(-0.002, -0.002, -0.002);
                    poses.scale(1.004f, 1.004f, 1.004f);
                }
                renderer.renderSingleBlock(model, poses, source, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, GHOST_LAYER);
            } finally {
                poses.popPose();
            }
        }
        buffers.endBatch(GHOST_LAYER);
    }

    private static MultiBufferSource tinted(VertexConsumer vertices, int red, int green, int blue, int opacity) {
        VertexConsumer tint = new VertexConsumerWrapper(vertices) {
            @Override
            public VertexConsumer setColor(int r, int g, int b, int a) {
                return super.setColor(r * red / 255, g * green / 255, b * blue / 255, a * opacity / 255);
            }
        };
        return ignored -> tint;
    }

    private void updateAim(Minecraft minecraft) {
        HitResult hit = minecraft.player.pick(AIM_DISTANCE, 1.0F, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            aimedBlock = block.getBlockPos().immutable();
        }
    }

    private BlockPos anchor() {
        if (fixedAnchor != null) {
            return fixedAnchor;
        }
        return aimedBlock == null ? null : aimedBlock.offset(offset);
    }

    private static void clearIfLevelChanged(Minecraft minecraft) {
        if (active != null && (active.level != minecraft.level || minecraft.player == null)) {
            active = null;
        }
    }

    private static void showMessage(Minecraft minecraft, String key, Object... args) {
        minecraft.player.displayClientMessage(Component.translatable(MESSAGE + key, args), true);
    }
}
