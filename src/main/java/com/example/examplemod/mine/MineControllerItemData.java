package com.example.examplemod.mine;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityProject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

public final class MineControllerItemData {

    private static final String ROOT = "createcityexpansioncore:mine_controller";

    private MineControllerItemData() {
    }

    public static Data capture(ServerLevel level, MineControllerBlockEntity controller) {
        CityManager manager = CityManager.get(level.getServer().overworld());
        CityProject project = controller.getProjectId().map(manager::getProject).orElse(null);
        ResourceLocation resourceId = controller.getSpecializedResourceId()
                .orElse(project == null ? null : project.resourceId());
        UUID creatorId = controller.getCreatorId().orElse(null);
        String creatorName = controller.getCreatorName();
        if (creatorId != null) {
            creatorName = com.example.examplemod.auth.PlayerNames.name(level.getServer(), creatorId);
        }
        City city = project == null ? null : manager.getCity(project.cityId());
        boolean registered = controller.isCommissioned()
                && project != null
                && project.dimensionId().equals(level.dimension().location())
                && project.controllerPos().equals(controller.getBlockPos())
                && controller.getCityId().filter(project.cityId()::equals).isPresent()
                && controller.getActiveDepositId().filter(project.depositId()::equals).isPresent();
        return new Data(
                creatorId,
                creatorName,
                controller.getActiveDepositId().orElse(null),
                resourceId,
                registered,
                registered ? project.cityId() : null,
                registered ? project.projectId() : null,
                city == null ? "" : city.getName()
        );
    }

    public static void write(ItemStack stack, Data value) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag data = new CompoundTag();
        if (value.creatorId() != null) data.putUUID("Creator", value.creatorId());
        if (!value.creatorName().isBlank()) data.putString("CreatorName", value.creatorName());
        if (value.depositId() != null) data.putString("Deposit", value.depositId().toString());
        if (value.resourceId() != null) data.putString("Resource", value.resourceId().toString());
        data.putBoolean("Commissioned", value.commissioned());
        if (value.commissioned()) {
            data.putUUID("CityId", value.cityId());
            data.putUUID("ProjectId", value.projectId());
            if (!value.cityName().isBlank()) data.putString("CityName", value.cityName());
        }
        custom.put(ROOT, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    public static Optional<Data> read(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!custom.contains(ROOT, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag data = custom.getCompound(ROOT);
        UUID creator = data.hasUUID("Creator") ? data.getUUID("Creator") : null;
        ResourceLocation deposit = ResourceLocation.tryParse(data.getString("Deposit"));
        ResourceLocation resource = ResourceLocation.tryParse(data.getString("Resource"));
        boolean commissioned = data.getBoolean("Commissioned")
                && data.hasUUID("CityId") && data.hasUUID("ProjectId")
                && creator != null && deposit != null && resource != null;
        return Optional.of(new Data(
                creator,
                trim(data.getString("CreatorName"), 64),
                deposit,
                resource,
                commissioned,
                commissioned ? data.getUUID("CityId") : null,
                commissioned ? data.getUUID("ProjectId") : null,
                trim(data.getString("CityName"), 96)
        ));
    }

    private static String trim(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public record Data(
            UUID creatorId,
            String creatorName,
            ResourceLocation depositId,
            ResourceLocation resourceId,
            boolean commissioned,
            UUID cityId,
            UUID projectId,
            String cityName
    ) {
        public Data {
            creatorName = creatorName == null ? "" : creatorName;
            cityName = cityName == null ? "" : cityName;
            if (commissioned && (creatorId == null || depositId == null || resourceId == null
                    || cityId == null || projectId == null)) {
                throw new IllegalArgumentException("Commissioned controller data is incomplete");
            }
        }

        public boolean configured() {
            return depositId != null;
        }

        public Data withoutProject() {
            return new Data(
                    creatorId, creatorName, depositId, resourceId,
                    false, null, null, ""
            );
        }
    }
}
