package com.example.examplemod.client.compat;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.BlueprintPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import snownee.jade.api.*;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;
import java.util.function.Function;

@WailaPlugin(CreateCityExpansionCore.MODID)
public final class BlueprintJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerAccessorHandler(ProjectionAccessor.class, ProjectionHandler.INSTANCE);
        registration.addRayTraceCallback((hit, accessor, original) -> {
            var projection = BlueprintPlacement.pickProjection();
            return projection == null ? accessor : new ProjectionAccessor(projection);
        });
    }

    private static final class ProjectionAccessor extends AccessorImpl<BlockHitResult> {
        private final BlueprintPlacement.ProjectionHit projection;

        private ProjectionAccessor(BlueprintPlacement.ProjectionHit projection) {
            super(Minecraft.getInstance().level, Minecraft.getInstance().player, new CompoundTag(),
                    projection::hit, false, false);
            this.projection = projection;
        }

        @Override
        public ItemStack getPickedResult() {
            return new ItemStack(projection.required().getBlock());
        }

        @Override
        public Object getTarget() {
            return projection.required();
        }

        @Override
        public Class<? extends Accessor<?>> getAccessorType() {
            return ProjectionAccessor.class;
        }

        @Override
        public boolean verifyData(CompoundTag data) {
            return false;
        }
    }

    private enum ProjectionHandler implements AccessorClientHandler<ProjectionAccessor>, IJadeProvider {
        INSTANCE;

        private static final String KEY = "gui.createcityexpansioncore.blueprint.jade.";

        @Override
        public boolean shouldDisplay(ProjectionAccessor accessor) {
            return true;
        }

        @Override
        public List<IServerDataProvider<ProjectionAccessor>> shouldRequestData(ProjectionAccessor accessor) {
            return List.of();
        }

        @Override
        public void requestData(ProjectionAccessor accessor, List<IServerDataProvider<ProjectionAccessor>> providers) {}

        @Override
        public IElement getIcon(ProjectionAccessor accessor) {
            return IElementHelper.get().item(accessor.getPickedResult());
        }

        @Override
        public void gatherComponents(ProjectionAccessor accessor, Function<IJadeProvider, ITooltip> tooltips) {
            var tooltip = tooltips.apply(this);
            var projection = accessor.projection;
            tooltip.add(projection.required().getBlock().getName(), JadeIds.CORE_OBJECT_NAME);
            tooltip.add(Component.translatable(KEY + "projection").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(KEY + "required", projection.required().getBlock().getName()));
            tooltip.add(Component.translatable(KEY + "installed", projection.installed().getBlock().getName()));
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(projection.required().getBlock());
            String modName = ModList.get().getModContainerById(id.getNamespace())
                    .map(container -> container.getModInfo().getDisplayName()).orElse(id.getNamespace());
            tooltip.add(Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC),
                    JadeIds.CORE_MOD_NAME);
        }

        @Override
        public ResourceLocation getUid() {
            return ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "blueprint_projection");
        }
    }
}
