package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityProjectSnapshot;
import com.example.examplemod.city.CityProjectType;
import com.example.examplemod.city.CityDevelopmentRequirementSnapshot;
import com.example.examplemod.city.CityDevelopmentRequirements;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record CityPylonStatePayload(
        BlockPos pylonPos,
        int cityLevel,
        int projectCapacity,
        List<CityProjectSnapshot> projects,
        ResourceLocation primaryResource,
        List<CityDevelopmentRequirementSnapshot> developmentRequirements
) implements CustomPacketPayload {
    public static final Type<CityPylonStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "city_pylon_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CityPylonStatePayload> STREAM_CODEC =
            StreamCodec.of(CityPylonStatePayload::encode, CityPylonStatePayload::decode);

    public CityPylonStatePayload {
        projects = List.copyOf(projects);
        developmentRequirements = List.copyOf(developmentRequirements);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CityPylonStatePayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
        buffer.writeVarInt(payload.cityLevel());
        buffer.writeVarInt(payload.projectCapacity());
        buffer.writeVarInt(payload.projects().size());
        for (CityProjectSnapshot project : payload.projects()) {
            buffer.writeUUID(project.projectId());
            buffer.writeEnum(project.projectType());
            buffer.writeResourceLocation(project.dimensionId());
            BlockPos.STREAM_CODEC.encode(buffer, project.controllerPos());
            buffer.writeUUID(project.creatorId());
            buffer.writeResourceLocation(project.resourceId());
            buffer.writeDouble(project.richness());
            buffer.writeUtf(project.status(), 128);
        }
        buffer.writeBoolean(payload.primaryResource() != null);
        if (payload.primaryResource() != null) buffer.writeResourceLocation(payload.primaryResource());
        buffer.writeVarInt(payload.developmentRequirements().size());
        for (CityDevelopmentRequirementSnapshot requirement : payload.developmentRequirements()) {
            buffer.writeEnum(requirement.group());
            buffer.writeResourceLocation(requirement.itemId());
            buffer.writeVarLong(requirement.current());
            buffer.writeVarLong(requirement.required());
        }
    }

    private static CityPylonStatePayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pylonPos = BlockPos.STREAM_CODEC.decode(buffer);
        int level = buffer.readVarInt();
        int capacity = buffer.readVarInt();
        int count = Math.min(64, Math.max(0, buffer.readVarInt()));
        List<CityProjectSnapshot> projects = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            projects.add(new CityProjectSnapshot(
                    buffer.readUUID(), buffer.readEnum(CityProjectType.class),
                    buffer.readResourceLocation(), BlockPos.STREAM_CODEC.decode(buffer),
                    buffer.readUUID(), buffer.readResourceLocation(),
                    buffer.readDouble(), buffer.readUtf(128)
            ));
        }
        ResourceLocation primary = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        int requirementCount = Math.min(16, Math.max(0, buffer.readVarInt()));
        List<CityDevelopmentRequirementSnapshot> requirements = new ArrayList<>(requirementCount);
        for (int index = 0; index < requirementCount; index++) {
            requirements.add(new CityDevelopmentRequirementSnapshot(
                    buffer.readEnum(CityDevelopmentRequirements.RequirementGroup.class),
                    buffer.readResourceLocation(),
                    buffer.readVarLong(),
                    buffer.readVarLong()
            ));
        }
        return new CityPylonStatePayload(
                pylonPos, level, capacity, projects, primary, requirements
        );
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
