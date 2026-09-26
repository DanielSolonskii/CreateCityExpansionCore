package com.example.examplemod.recipe;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/*
 * =========================================
 * OVERRIDE DATAPACK
 * =========================================
 *
 * Механизм: вместо runtime-мутации RecipeManager
 * (которая рассинхронизирует сервер и клиент)
 * используется штатный datapack-пайплайн.
 *
 * Регистрируется через AddPackFindersEvent как
 * always-active SERVER_DATA pack с Pack.Position.TOP,
 * поэтому он имеет приоритет над обычными
 * mod datapack'ами Create и Electro Energetics.
 *
 * Внутри pack'а лежат рецепты с ТОЧНО ТЕМИ ЖЕ
 * ResourceLocation, что и оригиналы, поэтому
 * дешёвый вариант полностью заменяется, а не
 * дополняется. Никакого «bypass» не остаётся.
 *
 * ВАЖНО: этот pack содержит ИСКЛЮЧИТЕЛЬНО
 * намеренно заменяемые рецепты зависимостей.
 * Собственные рецепты мода живут в обычном
 * data/createcityexpansioncore/recipe/.
 */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class DependencyRecipeOverride {

    /**
     * Путь к override-паку внутри ресурсов мода.
     * Используется как packLocation в AddPackFindersEvent:
     * namespace = modid, path = путь относительно resources/.
     */
    private static final ResourceLocation PACK_LOCATION =
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "datapacks/dependency_recipe_overrides"
            );

    private static final String PACK_DISPLAY_NAME =
            "CreateCityExpansionCore Dependency Recipe Overrides";

    /**
     * Идентификаторы рецептов зависимостей, которые
     * намеренно заменяются этим pack'ом.
     *
     * Используются ТОЧНО те же path, что и в оригиналах:
     *
     * Create 6.0.10:
     *   create:sequenced_assembly/track
     *   create:crafting/kinetics/track_station
     *   create:crafting/kinetics/track_signal
     *   create:crafting/kinetics/controller_rail
     *
     * Electro Energetics 1.1.1:
     *   electroenergetics:crafting/connector
     *   electroenergetics:crafting/insulator
     */
    public static final String[] OVERRIDDEN_RECIPES = {
            "create:sequenced_assembly/track",
            "create:crafting/kinetics/track_station",
            "create:crafting/kinetics/track_signal",
            "create:crafting/kinetics/controller_rail",
            "electroenergetics:crafting/connector",
            "electroenergetics:crafting/insulator"
    };

    private DependencyRecipeOverride() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {

        /*
         * Регистрируем ТОЛЬКО для SERVER_DATA.
         * Клиентские ресурс-паки не трогаем: клиент
         * получает итоговый список рецептов от сервера
         * через ClientboundUpdateRecipesPacket в составе
         * штатного datapack reload.
         */
        event.addPackFinders(
                PACK_LOCATION,
                PackType.SERVER_DATA,
                Component.literal(PACK_DISPLAY_NAME),
                PackSource.DEFAULT,
                true,
                Pack.Position.TOP
        );

        CreateCityExpansionCore.LOGGER.info(
                "[RECIPES] Override datapack enabled: {} (position TOP, always active)",
                PACK_LOCATION
        );
    }
}
