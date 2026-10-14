package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.item.IndustrialMaterials;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Сопоставление геологического ресурса с предметом,
 * который Industrial Mine кладёт в шлюз шахты.
 *
 * Отдельная таблица OUTPUT_FACTORS переводит
 * «единицу геологического production potential»
 * в единицу выдаваемого предмета. Потенциал залежи
 * исторически выражен в эквиваленте конечного железа,
 * а не в единицах рудной массы.
 */
public final class MineOutputMapping {

    /**
     * Коэффициент пересчёта потенциала в единицы сырья
     * для железной залежи.
     *
     * 1 железорудный концентрат -> 2 железа, то есть
     * 4 сырья -> 1 концентрат -> 2 железа, то есть
     * 1 сырьё = 0.5 железа.
     *
     * Чтобы геологический потенциал остался выражен
     * в Fe-эквиваленте, шахта выдаёт вдвое больше
     * единиц сырья:
     *
     *   500 Fe/ч  ->  1 000 сырья/ч
     *  2500 Fe/ч  ->  5 000 сырья/ч
     *  5000 Fe/ч  -> 10 000 сырья/ч
     * 10000 Fe/ч  -> 20 000 сырья/ч
     *
     * Остальные ресурсы выдаются напрямую (коэффициент 1):
     * уголь остаётся обычным углём, медь — обычной медной
     * рудой, цинк — create:raw_zinc.
     */
    public static final long IRON_ORE_RAW_PER_IRON_EQUIVALENT = 2L;

    /**
     * Значения намеренно не eagerly создаются:
     * класс может быть загружен раньше, чем
     * отложенные реестровые владельцы будут разрешены.
     * Предмет читается только в момент реального запроса.
     */
    private static final Map<ResourceLocation, Supplier<Item>> OUTPUTS = Map.of(
            resource("iron"), () -> IndustrialMaterials.IRON_ORE_RAW.get(),
            resource("copper"), () -> Items.RAW_COPPER,
            resource("gold"), () -> Items.RAW_GOLD,
            resource("coal"), () -> Items.COAL,
            resource("limestone"), () -> Items.CALCITE,
            resource("zinc"), () -> BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("create", "raw_zinc")
            )
    );

    private static final Map<ResourceLocation, Long> OUTPUT_FACTORS = Map.of(
            resource("iron"), IRON_ORE_RAW_PER_IRON_EQUIVALENT
    );

    private MineOutputMapping() {
    }

    public static Optional<Item> outputItem(ResourceLocation geologicalResource) {
        Supplier<Item> supplier = OUTPUTS.get(geologicalResource);
        return supplier == null ? Optional.empty() : Optional.ofNullable(supplier.get());
    }

    /**
     * Сколько единиц выдаваемого предмета приходится на
     * одну единицу геологического production potential.
     * По умолчанию ресурс выдаётся 1:1.
     */
    public static long outputFactor(ResourceLocation geologicalResource) {
        return OUTPUT_FACTORS.getOrDefault(geologicalResource, 1L);
    }

    /**
     * Обратный пересчёт: сколько единиц геологического запаса
     * списывается за указанное количество физических предметов.
     *
     * Reserve хранится в эквиваленте базового полезного ресурса,
     * а не в количестве bulk-предметов. Для железа это значит,
     * что 2 железорудного сырья = 1 единица запаса: физический
     * выход шахты удвоен, а расход reserve — нет.
     */
    public static long reserveUnitsFor(
            ResourceLocation geologicalResource,
            long physicalAmount
    ) {
        if (physicalAmount <= 0L) {
            return 0L;
        }
        long factor = outputFactor(geologicalResource);
        return physicalAmount / factor;
    }

    /**
     * Сколько физических предметов соответствует указанному
     * объёму базового ресурса. Всегда >= reserveUnits, чтобы
     * округление вниз не теряло добычу.
     */
    public static long physicalAmountFor(
            ResourceLocation geologicalResource,
            long reserveUnits
    ) {
        if (reserveUnits <= 0L) {
            return 0L;
        }
        long factor = outputFactor(geologicalResource);
        return reserveUnits * factor;
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID,
                path
        );
    }
}