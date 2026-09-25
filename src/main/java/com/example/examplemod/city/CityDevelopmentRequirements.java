package com.example.examplemod.city;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public final class CityDevelopmentRequirements {

    public static final ResourceLocation IRON_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            "createcityexpansioncore", "iron"
    );
    public static final ResourceLocation IRON_SHEET = ResourceLocation.fromNamespaceAndPath(
            "create", "iron_sheet"
    );
    public static final ResourceLocation COAL_RESOURCE = resource("coal");
    public static final ResourceLocation COPPER_RESOURCE = resource("copper");
    public static final ResourceLocation ZINC_RESOURCE = resource("zinc");
    public static final ResourceLocation COAL = ResourceLocation.withDefaultNamespace("coal");
    public static final ResourceLocation COPPER_SHEET = ResourceLocation.fromNamespaceAndPath(
            "create", "copper_sheet"
    );
    public static final ResourceLocation ANDESITE_ALLOY = ResourceLocation.fromNamespaceAndPath(
            "create", "andesite_alloy"
    );
    public static final ResourceLocation TRANSFORMER = ee("transformer");
    public static final ResourceLocation COPPER_WIRE_SPOOL = ee("copper_wire_spool");
    public static final ResourceLocation CAPACITOR = ee("capacitor");
    public static final ResourceLocation RESISTOR = ee("resistor");

    private static final Map<ResourceLocation, ResourceLocation> PRIMARY_MATERIALS = Map.of(
            IRON_RESOURCE, IRON_SHEET,
            COAL_RESOURCE, COAL,
            COPPER_RESOURCE, COPPER_SHEET,
            ZINC_RESOURCE, ANDESITE_ALLOY
    );

    private CityDevelopmentRequirements() {
    }

    public static List<Requirement> forLevelTwo(ResourceLocation primaryResource) {
        ResourceLocation primaryMaterial = PRIMARY_MATERIALS.get(primaryResource);
        if (primaryMaterial == null) {
            return List.of();
        }
        return List.of(
                new Requirement(RequirementGroup.PRIMARY_INDUSTRY, primaryMaterial, 10_000L),
                new Requirement(RequirementGroup.ELECTRICAL_INFRASTRUCTURE, TRANSFORMER, 5L),
                new Requirement(RequirementGroup.ELECTRICAL_INFRASTRUCTURE, COPPER_WIRE_SPOOL, 64L),
                new Requirement(RequirementGroup.ELECTRICAL_INFRASTRUCTURE, CAPACITOR, 32L),
                new Requirement(RequirementGroup.ELECTRICAL_INFRASTRUCTURE, RESISTOR, 64L)
        );
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", path);
    }

    private static ResourceLocation ee(String path) {
        return ResourceLocation.fromNamespaceAndPath("electroenergetics", path);
    }

    public enum RequirementGroup {
        PRIMARY_INDUSTRY,
        ELECTRICAL_INFRASTRUCTURE
    }

    public record Requirement(
            RequirementGroup group,
            ResourceLocation itemId,
            long amount
    ) {
        public Requirement {
            if (group == null || itemId == null) {
                throw new IllegalArgumentException("requirement group and item must be present");
            }
            if (amount <= 0L) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }
    }
}
