package com.example.examplemod.client.render;

import com.example.examplemod.mine.IndustrialMineState;
import com.example.examplemod.mine.MineControllerBlockEntity;
import com.example.examplemod.mine.MineElectricalState;
import com.example.examplemod.multiblock.MineMultiblock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class IndustrialMineRenderer implements BlockEntityRenderer<MineControllerBlockEntity> {

    private static final double MAX_RENDER_DISTANCE_SQ = 96.0D * 96.0D;
    private static final double PARTICLE_DISTANCE_SQ = 40.0D * 40.0D;
    private static final float ASSET_SCALE = 8.0F;
    private static final int PARTICLE_INTERVAL_TICKS = 3;
    private static final IndustrialMineAnchors.Anchor[] DRILL_GROUPS = {
            IndustrialMineAnchors.Anchor.DRILL_GROUP_NW,
            IndustrialMineAnchors.Anchor.DRILL_GROUP_NE,
            IndustrialMineAnchors.Anchor.DRILL_GROUP_SW,
            IndustrialMineAnchors.Anchor.DRILL_GROUP_SE
    };

    private final ModelBlockRenderer modelRenderer;

    public IndustrialMineRenderer(BlockEntityRendererProvider.Context context) {
        modelRenderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
    }

    @Override
    public void render(
            MineControllerBlockEntity controller,
            float partialTicks,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        if (!(controller.getLevel() instanceof ClientLevel level)
                || !controller.isVisualAssembled()
                || !AssembledBlockVisibility.isActive(controller.getBlockPos())) {
            return;
        }

        BlockPos pos = controller.getBlockPos();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_RENDER_DISTANCE_SQ) {
            return;
        }

        BlockState state = controller.getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;
        IndustrialMineState machineState = controller.getRuntimeState();
        boolean running = machineState == IndustrialMineState.RUNNING;
        double driveAngle = IndustrialMineVisuals.angle(pos, partialTicks);
        double drillAngle = driveAngle * 2.0D;
        int machineLight = sampleLocalLight(level, pos, facing,
                new Vec3(1.0D, 4.25D, 5.0D),
                new Vec3(-4.25D, 1.5D, 5.0D),
                new Vec3(6.25D, 1.5D, 5.0D),
                new Vec3(1.0D, 1.5D, -0.25D),
                new Vec3(1.0D, 1.5D, 10.25D));

        VertexConsumer solid = buffers.getBuffer(RenderType.solid());
        pose.pushPose();
        applyFacing(pose, facing);

        renderModel(controller, pose, solid, machineLight, IndustrialMineModels.fixedCore());

        pose.pushPose();
        pose.translate(1.0D, 2.88D, 5.0D);
        pose.mulPose(Axis.YP.rotationDegrees((float) driveAngle));
        int driveLight = sampleLocalLight(level, pos, facing,
                new Vec3(1.0D, 3.5D, 5.0D), new Vec3(1.0D, 4.25D, 5.0D));
        renderModel(controller, pose, solid, driveLight, IndustrialMineModels.mainDrive());
        pose.popPose();

        for (int groupZ = 0; groupZ < 2; groupZ++) {
            for (int groupX = 0; groupX < 2; groupX++) {
                int group = groupZ * 2 + groupX;
                double x = groupX * 2.0D;
                double z = 4.0D + groupZ * 2.0D;
                int groupLight = sampleLocalLight(level, pos, facing,
                        new Vec3(x, 2.5D, z), new Vec3(x, 3.25D, z));
                pose.pushPose();
                pose.translate(x, 1.72D, z);
                pose.mulPose(Axis.YP.rotationDegrees((float) (-driveAngle + group * 15.0D)));
                renderModel(controller, pose, solid, groupLight, IndustrialMineModels.gearbox());
                pose.popPose();
            }
        }

        for (int localZ = 3; localZ <= 6; localZ++) {
            for (int localX = -1; localX <= 2; localX++) {
                int group = (localZ >= 5 ? 2 : 0) + (localX >= 1 ? 1 : 0);
                double reciprocation = Math.sin(Math.toRadians(driveAngle * 2.0D + group * 90.0D)) * .035D;
                int drillLight = sampleLocalLight(level, pos, facing,
                        new Vec3(localX + .5D, 2.25D, localZ + .5D));
                pose.pushPose();
                pose.translate(localX + .5D, reciprocation, localZ + .5D);
                pose.mulPose(Axis.YP.rotationDegrees((float)
                        (drillAngle + group * 22.5D + ((localX + localZ) & 1) * 180.0D)));
                renderModel(controller, pose, solid, drillLight, IndustrialMineModels.drillShaft());
                pose.popPose();
            }
        }

        renderIndicators(controller, pose, solid, packedLight, machineState);
        pose.popPose();

        // World-space attachments: the actual canonical positions, not fixed core coordinates.
        renderAttachment(controller, facing, controller.getVisualPowerPort(),
                IndustrialMineModels.powerModule(), pose, solid);
        renderAttachment(controller, facing, controller.getVisualGateway(),
                IndustrialMineModels.outputModule(), pose, solid);
        BlockPos power = controller.getVisualPowerPort();
        if (power != null && level.getBlockState(power).hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            // EE wires use the real block's FACING, not the side chosen for the assembled casing.
            Direction terminalFacing = level.getBlockState(power).getValue(BlockStateProperties.HORIZONTAL_FACING);
            pose.pushPose();
            BlockPos offset = power.subtract(pos);
            pose.translate(offset.getX(), offset.getY(), offset.getZ());
            applyFacing(pose, terminalFacing);
            renderModel(controller, pose, solid,
                    sampleInterfaceLight(level, power, terminalFacing), IndustrialMineModels.powerContacts());
            pose.popPose();
        }

        if (running && camera.distanceToSqr(Vec3.atCenterOf(pos)) <= PARTICLE_DISTANCE_SQ) {
            emitMiningParticle(level, controller, facing);
        }
    }

    private void renderIndicators(
            MineControllerBlockEntity controller,
            PoseStack pose,
            VertexConsumer solid,
            int light,
            IndustrialMineState state
    ) {
        boolean noPower = controller.getVisualElectricalState() != MineElectricalState.NORMAL;
        BakedModel lamp = state == IndustrialMineState.RUNNING
                ? IndustrialMineModels.greenIndicator()
                : state == IndustrialMineState.OUTPUT_BLOCKED
                ? IndustrialMineModels.amberIndicator()
                : noPower || state == IndustrialMineState.ERROR || state == IndustrialMineState.OVERLOAD
                ? IndustrialMineModels.redIndicator()
                : IndustrialMineModels.amberIndicator();
        pose.pushPose();
        renderModel(controller, pose, solid, LightTexture.FULL_BRIGHT, lamp);
        pose.popPose();
    }

    private void renderAttachment(MineControllerBlockEntity controller, Direction facing,
                                  BlockPos port, BakedModel model, PoseStack pose,
                                  VertexConsumer consumer) {
        if (port == null) return;
        Direction outward = MineMultiblock.interfaceOutward(controller.getBlockPos(), facing, port);
        int light = sampleInterfaceLight((ClientLevel) controller.getLevel(), port, outward);
        pose.pushPose();
        BlockPos offset = port.subtract(controller.getBlockPos());
        pose.translate(offset.getX(), offset.getY(), offset.getZ());
        applyFacing(pose, outward);
        renderModel(controller, pose, consumer, light, model);
        pose.popPose();
    }

    /**
     * The controller sits inside the hidden multiblock and commonly receives a zero light value.
     * Sample exposed points instead, preserving normal world lighting without making the machine
     * full-bright. Block and sky channels are combined independently so mixed light sources work.
     */
    private static int sampleLocalLight(ClientLevel level, BlockPos controllerPos,
                                        Direction facing, Vec3... localSamples) {
        int blockLight = 0;
        int skyLight = 0;
        for (Vec3 local : localSamples) {
            Vec3 world = IndustrialMineAnchors.transform(controllerPos, facing, local);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(world));
            blockLight = Math.max(blockLight, LightTexture.block(light));
            skyLight = Math.max(skyLight, LightTexture.sky(light));
        }
        return LightTexture.pack(blockLight, skyLight);
    }

    private static int sampleInterfaceLight(ClientLevel level, BlockPos port, Direction outward) {
        return maxPackedLight(
                LevelRenderer.getLightColor(level, port.relative(outward)),
                LevelRenderer.getLightColor(level, port.above())
        );
    }

    static int maxPackedLight(int... samples) {
        int blockLight = 0;
        int skyLight = 0;
        for (int light : samples) {
            blockLight = Math.max(blockLight, LightTexture.block(light));
            skyLight = Math.max(skyLight, LightTexture.sky(light));
        }
        return LightTexture.pack(blockLight, skyLight);
    }

    private static void emitMiningParticle(
            ClientLevel level,
            MineControllerBlockEntity controller,
            Direction facing
    ) {
        long tick = level.getGameTime();
        if (Math.floorMod(tick + controller.getBlockPos().asLong(), PARTICLE_INTERVAL_TICKS) != 0
                || !controller.claimVisualParticleTick(tick)) {
            return;
        }
        int index = Math.floorMod((int) (tick + controller.getBlockPos().asLong()), DRILL_GROUPS.length);
        Vec3 anchor = IndustrialMineAnchors.world(controller.getBlockPos(), facing, DRILL_GROUPS[index]);
        double jitterX = (level.random.nextDouble() - 0.5D) * 1.25D;
        double jitterZ = (level.random.nextDouble() - 0.5D) * 1.25D;
        level.addParticle(
                new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DEEPSLATE.defaultBlockState()),
                anchor.x + jitterX, anchor.y, anchor.z + jitterZ,
                0.0D, 0.015D, 0.0D
        );
        if (Math.floorMod(tick + index, 12L) == 0L) {
            level.addParticle(ParticleTypes.POOF,
                    anchor.x, anchor.y + 0.1D, anchor.z,
                    0.0D, 0.025D, 0.0D);
        }
    }

    private void renderModel(
            MineControllerBlockEntity controller,
            PoseStack pose,
            VertexConsumer consumer,
            int light,
            BakedModel model
    ) {
        if (model == null) {
            return;
        }
        pose.pushPose();
        pose.scale(ASSET_SCALE, ASSET_SCALE, ASSET_SCALE);
        // Use the supplied lightmap (including indicator full-bright), not controller-neighbor AO.
        modelRenderer.renderModel(pose.last(), consumer, controller.getBlockState(), model,
                1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, RenderType.solid());
        pose.popPose();
    }

    private static void applyFacing(PoseStack pose, Direction facing) {
        float degrees = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        if (degrees != 0.0F) {
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(-degrees));
            pose.translate(-0.5D, 0.0D, -0.5D);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(MineControllerBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() { return 96; }
}
