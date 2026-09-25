package com.example.examplemod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    public static final ModConfigSpec.LongValue ECONOMY_PROCUREMENT_CAP = BUILDER
            .comment("Maximum Master City procurement budget.")
            .defineInRange("economy.procurementCap", 100_000L, 0L, Long.MAX_VALUE);
    public static final ModConfigSpec.LongValue ECONOMY_PROCUREMENT_REPLENISH_PER_HOUR = BUILDER
            .comment("Master procurement budget replenished per real-time hour of server ticks.")
            .defineInRange("economy.procurementReplenishPerHour", 5_000L, 0L, Long.MAX_VALUE);
    public static final ModConfigSpec.IntValue ECONOMY_PRIVATE_FEE_BPS = BUILDER
            .comment("Private marketplace fee in basis points (100 = 1%).")
            .defineInRange("economy.privateFeeBasisPoints", 100, 0, 5_000);
    public static final ModConfigSpec.IntValue ECONOMY_CITY_FEE_BPS = BUILDER
            .comment("Ordinary city contract fee in basis points (100 = 1%).")
            .defineInRange("economy.cityContractFeeBasisPoints", 100, 0, 5_000);
    public static final ModConfigSpec.IntValue ECONOMY_DELIVERED_TIMEOUT_MINUTES = BUILDER
            .comment("Minutes before a fully delivered ordinary contract is auto-confirmed.")
            .defineInRange("economy.deliveredConfirmationTimeoutMinutes", 1_440, 1, 43_200);
    public static final ModConfigSpec.LongValue ECONOMY_MAX_TRANSACTION = BUILDER
            .comment("Maximum value accepted by one economy operation.")
            .defineInRange("economy.maximumTransaction", 1_000_000_000L, 1L, Long.MAX_VALUE);
    public static final ModConfigSpec.LongValue ECONOMY_MAX_CONTRACT_AMOUNT = BUILDER
            .comment("Maximum item amount in one listing or industrial contract.")
            .defineInRange("economy.maximumContractAmount", 10_000_000L, 1L, 100_000_000L);
    public static final ModConfigSpec.IntValue ECONOMY_MAX_DEADLINE_HOURS = BUILDER
            .comment("Maximum accepted contract deadline duration in hours.")
            .defineInRange("economy.maximumDeadlineHours", 720, 1, 8_760);
    public static final ModConfigSpec.IntValue ECONOMY_SMART_ORDER_INTERVAL_TICKS = BUILDER
            .comment("How often the Master smart-order generator is evaluated.")
            .defineInRange("economy.smartOrderIntervalTicks", 1_200, 200, 72_000);
    public static final ModConfigSpec.IntValue ECONOMY_HISTORY_LIMIT = BUILDER
            .comment("Maximum terminal contracts and private listings retained in each recent-history archive.")
            .defineInRange("economy.historyLimit", 256, 16, 4_096);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
