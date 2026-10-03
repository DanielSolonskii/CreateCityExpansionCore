package com.example.examplemod.item;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/*
 * =========================================
 * ПРОМЫШЛЕННЫЕ МАТЕРИАЛЫ
 * =========================================
 *
 * TIER 2 — промышленные полуфабрикаты.
 * Массовыеcommodities: сотни / тысячи штук.
 * Полностью производятся механиками Create,
 * ручной крафт не является основным путём.
 *
 * TIER 3 — инженерная продукция.
 * Сложные компоненты: единицы / десятки штук.
 * Собираются через Create Sequenced Assembly.
 *
 * Философия: сложность масштабирует ПРОИЗВОДСТВО,
 * а не количество ручных действий игрока.
 */
public final class IndustrialMaterials {

    private IndustrialMaterials() {
    }

    /*
     * =========================================
     * TIER 2 — ПРОМЫШЛЕННЫЕ МАТЕРИАЛЫ
     * =========================================
     */

    /*
     * =========================================
     * BULK-ЦЕПОЧКА ЖЕЛЕЗА (Alpha v1)
     * =========================================
     *
     * Промышленная шахта больше не выдаёт готовое железо.
     * Для залежи createcityexpansioncore:iron шлюз шахты
     * отдаёт IRON_ORE_RAW — несортированную рудную массу.
     *
     * Геологический production potential залежи при этом
     * НЕ меняется: он по-прежнему выражен в эквиваленте
     * конечного железа. Коэффициент пересчёта задан в
     * MineOutputMapping и равен 2, потому что 1 концентрат
     * даёт 2 железа:
     *
     *   500 Fe/ч  ->  1 000 сырья/ч
     *  2500 Fe/ч  ->  5 000 сырья/ч
     *  5000 Fe/ч  -> 10 000 сырья/ч
     * 10000 Fe/ч  -> 20 000 сырья/ч
     *
     * Уголь, известняк и медь намеренно не получают
     * собственных bulk-предметов: уголь остаётся обычным
     * углём, известняк — существующим CRUSHED_LIMESTONE,
     * медь — обычным create:raw_zinc.
     *
     * Обратных рецептов нет: ни железо, ни ванильная
     * железная руда / raw iron не превращаются в сырьё.
     * Ванильное железо остаётся обычным usable resource.
     */

    /**
     * Железорудное сырьё.
     * Несортированная рудная масса, выдаваемая
     * Industrial Mine на железной залежи.
     * Create Crushing: 1 сырьё -> 1 CRUSHED_IRON_ORE.
     */
    public static final DeferredItem<Item> IRON_ORE_RAW =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "iron_ore_raw",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Дроблёное железорудное сырьё.
     * Промежуточный продукт Create Crushing на пути
     * к обогащению.
     */
    public static final DeferredItem<Item> CRUSHED_IRON_ORE =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "crushed_iron_ore",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Железорудный концентрат.
     * Create Mixing: 4 дроблёного сырья + 250 mB воды
     * -> 1 концентрат.
     * Далее: 1 концентрат -> 2 железа (Mixing, нагрев).
     */
    public static final DeferredItem<Item> IRON_CONCENTRATE =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "iron_concentrate",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Дроблёный известняк.
     * Промежуточный продукт Create Crushing для получения негашёной извести.
     */
    public static final DeferredItem<Item> CRUSHED_LIMESTONE =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "crushed_limestone",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Промышленный кокс.
     * Bootstrap-продукт угольной цепочки: 4 угля -> 2 кокса (Mixing, нагрев).
     * Coke Oven Battery сохраняет промышленную эффективность 1:1.
     * Создаёт отдельную угольную промышленность вместо
     * прямого использования угля в металлургии.
     */
    public static final DeferredItem<Item> INDUSTRIAL_COKE =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "industrial_coke",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Стальная заготовка.
     * Ранний steel commodity: 4 железа + 4 кокса + 1 негашёная известь
     * -> 2 заготовки (Mixing, сверхнагрев).
     *
     * Будущий City Project «Metallurgical Works» сможет производить
     * её эффективнее и большими партиями, но не будет единственным
     * способом получить сталь.
     */
    public static final DeferredItem<Item> STEEL_BILLET =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "steel_billet",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Стальная плита.
     * 1 заготовка -> 1 плита (Pressing).
     */
    public static final DeferredItem<Item> STEEL_PLATE =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "steel_plate",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Конструкционная балка.
     * 1 заготовка -> 2 балки (Cutting).
     * Основной профильный материал строительства.
     */
    public static final DeferredItem<Item> STRUCTURAL_BEAM =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "structural_beam",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Комплект промышленного крепежа.
     * 1 стальная плита -> 8 комплектов (Cutting).
     *
     * Один предмет представляет полный комплект крепежа.
     * Отдельные болты / гайки / шайбы / заклёпки намеренно
     * не введены, чтобы не создавать ручной grind.
     */
    public static final DeferredItem<Item> INDUSTRIAL_FASTENER_PACK =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "industrial_fastener_pack",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Арматурный пакет.
     * 1 конструкционная балка -> 2 пакета (Cutting).
     */
    public static final DeferredItem<Item> REINFORCEMENT_BUNDLE =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "reinforcement_bundle",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Рельсовый профиль.
     * 1 конструкционная балка -> 2 профиля (Pressing).
     */
    public static final DeferredItem<Item> RAIL_PROFILE =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "rail_profile",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Пакет железнодорожных шпал.
     * 4 деревянные плиты + 1 комплект крепежа -> 4 пакета.
     * Индивидуальные шпалы не вводятся.
     */
    public static final DeferredItem<Item> SLEEPER_PACK =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "sleeper_pack",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Сухая бетонная смесь.
     * 4 песок + 4 гравий + 2 кальцит -> 8 смеси (Mixing).
     * Промежуточная стадия перед железобетоном.
     */
    public static final DeferredItem<Item> DRY_CONCRETE_MIX =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "dry_concrete_mix",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Керамическая масса.
     * 4 глиняных шарика + 1 кварц + 1 кремень -> 4 массы (Mixing).
     */
    public static final DeferredItem<Item> CERAMIC_COMPOUND =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "ceramic_compound",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Техническая керамика.
     * Печь: керамическая масса -> техническая керамика.
     * Blasting: то же самое, но быстрее.
     * Create Bulk Blasting работает через стандартную механику Create.
     */
    public static final DeferredItem<Item> TECHNICAL_CERAMIC =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "technical_ceramic",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /**
     * Медная силовая шина.
     * 1 медный лист Create -> 4 шины (Cutting).
     *
     * НЕ является заменой проводам Electro Energetics.
     * Провод EE — физическая передача электричества по миру.
     * Шина — внутренний компонент тяжёлого электрооборудования.
     */
    public static final DeferredItem<Item> COPPER_BUSBAR =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "copper_busbar",
                    properties -> new Item(properties),
                    new Item.Properties()
            );

    /*
     * =========================================
     * TIER 3 — ИНЖЕНЕРНАЯ ПРОДУКЦИЯ
     * =========================================
     */

    /**
     * Рама тяжёлой машины.
     * Create Sequenced Assembly на базе Create Andesite Casing.
     * Требуется единицами / десятками, не расходник.
     */
    public static final DeferredItem<Item> HEAVY_MACHINE_FRAME =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "heavy_machine_frame",
                    properties -> new Item(properties),
                    new Item.Properties().rarity(Rarity.UNCOMMON)
            );

    /**
     * Промышленный блок управления.
     * Create Sequenced Assembly на базе Create Precision Mechanism.
     * Сердце всей тяжёлой инфраструктуры мода.
     */
    public static final DeferredItem<Item> INDUSTRIAL_CONTROL_UNIT =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "industrial_control_unit",
                    properties -> new Item(properties),
                    new Item.Properties().rarity(Rarity.UNCOMMON)
            );

    /**
     * Силовой распределительный модуль.
     * Create Mechanical Crafting.
     * Верхний компонент тяжёлой электрической иерархии.
     */
    public static final DeferredItem<Item> POWER_DISTRIBUTION_MODULE =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "power_distribution_module",
                    properties -> new Item(properties),
                    new Item.Properties().rarity(Rarity.RARE)
            );

    /*
     * =========================================
     * ПЕРЕХОДНЫЕ ЭЛЕМЕНТЫ SEQUENCED ASSEMBLY
     * =========================================
     *
     * Create Sequenced Assembly требует реальный
     * зарегистрированный предмет, который несёт
     * прогресс сборки между шагами.
     *
     * Эти предметы — техническая деталь механики
     * Create, а не часть экономики мода:
     * они не имеют самостоятельной ценности,
     * не торгуются и не показываются в creative tab.
     */

    public static final DeferredItem<Item> INCOMPLETE_HEAVY_MACHINE_FRAME =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "incomplete_heavy_machine_frame",
                    properties -> new Item(properties)
            );

    public static final DeferredItem<Item> INCOMPLETE_INDUSTRIAL_CONTROL_UNIT =
            CreateCityExpansionCore.ITEMS.registerItem(
                    "incomplete_industrial_control_unit",
                    properties -> new Item(properties)
            );
}
