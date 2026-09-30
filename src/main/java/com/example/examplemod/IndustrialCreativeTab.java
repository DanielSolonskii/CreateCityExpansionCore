package com.example.examplemod;

import com.example.examplemod.item.IndustrialMaterials;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/*
 * =========================================
 * СОДЕРЖИМОЕ CREATIVE TAB
 * =========================================
 *
 * Порядок разделов повторяет производственный
 * цикл мода: сырьё -> полуфабрикаты -> сложные
 * компоненты -> машины и инфраструктура.
 */
public final class IndustrialCreativeTab {

    private IndustrialCreativeTab() {
    }

    /*
     * =========================================
     * TIER 1-DERIVED — ИНСТРУМЕНТЫ АНАЛИЗА
     * =========================================
     *
     * Инструменты игрока. Не требуют Tier 2 / Tier 3,
     * доступны на раннем этапе игры.
     */

    private static final List<ItemLike> TIER1_DERIVED = List.of(
            CreateCityExpansionCore.MAGNETIC_ANALYZER.get(),
            CreateCityExpansionCore.DISPATCHER_KEY.get()
    );

    /*
     * =========================================
     * TIER 2 — ПРОМЫШЛЕННЫЕ МАТЕРИАЛЫ
     * =========================================
     *
     * Порядок соответствует цепочке производства:
     * кокс -> заготовка -> плита / балка ->
     * крепёж / арматура -> рельсы / шпалы ->
     * бетон / керамика / шина.
     */

    private static final List<ItemLike> TIER2 = List.of(
            IndustrialMaterials.INDUSTRIAL_COKE.get(),
            IndustrialMaterials.CRUSHED_LIMESTONE.get(),
            CreateCityExpansionCore.QUICKLIME.get(),
            IndustrialMaterials.STEEL_BILLET.get(),
            IndustrialMaterials.STEEL_PLATE.get(),
            IndustrialMaterials.STRUCTURAL_BEAM.get(),
            IndustrialMaterials.INDUSTRIAL_FASTENER_PACK.get(),
            IndustrialMaterials.REINFORCEMENT_BUNDLE.get(),
            IndustrialMaterials.RAIL_PROFILE.get(),
            IndustrialMaterials.SLEEPER_PACK.get(),
            IndustrialMaterials.DRY_CONCRETE_MIX.get(),
            IndustrialMaterials.CERAMIC_COMPOUND.get(),
            IndustrialMaterials.TECHNICAL_CERAMIC.get(),
            IndustrialMaterials.COPPER_BUSBAR.get()
    );

    /*
     * =========================================
     * TIER 3 — ИНЖЕНЕРНАЯ ПРОДУКЦИЯ
     * =========================================
     *
     * Сложные компоненты. Требуются единицами,
     * поэтому идут после массовых материалов.
     */

    private static final List<ItemLike> TIER3 = List.of(
            IndustrialMaterials.HEAVY_MACHINE_FRAME.get(),
            IndustrialMaterials.INDUSTRIAL_CONTROL_UNIT.get(),
            IndustrialMaterials.POWER_DISTRIBUTION_MODULE.get()
    );

    /*
     * =========================================
     * МАШИНЫ И ИНФРАСТРУКТУРА
     * =========================================
     *
     * Блоки мода. Сгруппированы по назначению,
     * а не по порядку регистрации.
     */

    private static final List<ItemLike> MINING_MACHINES = List.of(
            CreateCityExpansionCore.MINE_CONTROLLER_ITEM.get(),
            CreateCityExpansionCore.DRILL_UNIT_ITEM.get(),
            CreateCityExpansionCore.MINE_FRAME_ITEM.get(),
            CreateCityExpansionCore.TECHNICAL_BLOCK_ITEM.get(),
            CreateCityExpansionCore.CONTROL_BLOCK_ITEM.get(),
            CreateCityExpansionCore.MINE_POWER_PORT_ITEM.get(),
            CreateCityExpansionCore.MINE_GATEWAY_ITEM.get()
    );

    private static final List<ItemLike> ECONOMY_INFRASTRUCTURE = List.of(
            CreateCityExpansionCore.CITY_PYLON_ITEM.get(),
            CreateCityExpansionCore.CONTRACT_INTAKE_ITEM.get(),
            CreateCityExpansionCore.CONTRACT_DISPATCH_TERMINAL_ITEM.get(),
            CreateCityExpansionCore.WAREHOUSE_GATEWAY_ITEM.get()
    );

    private static final List<ItemLike> BUILDING_BLOCKS = List.of(
            CreateCityExpansionCore.REINFORCED_CONCRETE_ITEM.get(),
            CreateCityExpansionCore.REFRACTORY_BLOCK_ITEM.get(),
            CreateCityExpansionCore.COKE_OVEN_CHAMBER_ITEM.get(),
            CreateCityExpansionCore.COKE_BATTERY_CONTROLLER_ITEM.get(),
            CreateCityExpansionCore.COKE_BATTERY_INPUT_PORT_ITEM.get(),
            CreateCityExpansionCore.COKE_BATTERY_OUTPUT_PORT_ITEM.get(),
            CreateCityExpansionCore.BLAST_FURNACE_CORE_ITEM.get(),
            CreateCityExpansionCore.BLAST_FURNACE_CONTROLLER_ITEM.get(),
            CreateCityExpansionCore.BLAST_FURNACE_INPUT_PORT_ITEM.get(),
            CreateCityExpansionCore.BLAST_FURNACE_OUTPUT_PORT_ITEM.get()
            ,CreateCityExpansionCore.STEEL_CONVERTER_CONTROLLER_ITEM.get()
            ,CreateCityExpansionCore.STEEL_CONVERTER_FLUID_INPUT_ITEM.get()
            ,CreateCityExpansionCore.STEEL_CONVERTER_ITEM_INPUT_ITEM.get()
            ,CreateCityExpansionCore.STEEL_CONVERTER_OUTPUT_ITEM.get()
            ,CreateCityExpansionCore.STEELMAKING_BLOWER_PORT_ITEM.get()
    );

    public static void populate(
            CreativeModeTab.ItemDisplayParameters parameters,
            CreativeModeTab.Output output
    ) {
        acceptAll(output, TIER1_DERIVED);
        acceptAll(output, TIER2);
        acceptAll(output, TIER3);
        acceptAll(output, MINING_MACHINES);
        acceptAll(output, ECONOMY_INFRASTRUCTURE);
        acceptAll(output, BUILDING_BLOCKS);
    }

    /*
     * =========================================
     * ВСПОМОГАТЕЛЬНОЕ
     * =========================================
     */

    private static void acceptAll(
            CreativeModeTab.Output output,
            List<ItemLike> items
    ) {
        for (ItemLike item : items) {
            output.accept(item);
        }
    }
}
