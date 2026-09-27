# CURRENT_STATE.md

Текущее состояние проекта CreateCityExpansionCore.

## 1. Платформа

| Компонент | Версия |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251 |
| Java | 21 |
| Create | 6.0.10-280 |
| Create: Electro Energetics | 1.21.1-1.1.2 |

Mod ID: `createcityexpansioncore`

## 2. Реализованные системы

- **Город** — CityManager, City Pylon, CityPylonMenu, CityPylonScreen, роли, приглашения, проекты, развитие I -> II, карта города, CityBoundaryRenderer.
- **Геология** — детерминированная генерация залежей, Magnetic Analyzer, сохранение остатков залежей.
- **Промышленная шахта** — Mine Controller, Mine Power Port, Mine Gateway, Mine Frame, Drill Unit, Technical Block, Control Block. Мультиблок 10x3x10, питание от Electro Energetics.
- **Железные дороги** — Dispatcher Key, регистрация станций и поездов, RailLine, City Railway Fleet.
- **Экономика** — личные кошельки, City Treasury, Marketplace (частный рынок), Contract Intake, Warehouse Gateway, Master City, Smart Orders, Procurement Budget, Shipment.
- **Persistence** — SavedData для города, геологии, железных дорог, экономики.

## 3. Industrial recipe layer

### 3.1 Модель ресурсных Tier

Tier предмета НЕ равен City Level.

**Tier 1 — сырьё.** Железо, медь, цинк, уголь, камень, песок, гравий, кальцит, глина, кварц.
Нужны десятками тысяч. Основной железнодорожный bulk cargo.

**Tier 2 — промышленные материалы.** Кокс, стальная заготовка, стальная плита, конструкционная балка, крепёж, арматура, рельсовый профиль, шпалы, бетонные материалы, железобетон, техническая керамика, медная силовая шина.
Нужны сотнями / тысячами. Полностью производятся механиками Create.

**Tier 3 — инженерная продукция.** Рама тяжёлой машины, промышленный блок управления, силовой распределительный модуль.
Нужны единицами / десятками. Собираются через Create Sequenced Assembly и Mechanical Crafting.

### 3.2 Новые предметы

| Registry ID | Tier | Название (ru) |
|---|---|---|
| `industrial_coke` | 2 | Промышленный кокс |
| `steel_billet` | 2 | Стальная заготовка |
| `steel_plate` | 2 | Стальная плита |
| `structural_beam` | 2 | Конструкционная балка |
| `industrial_fastener_pack` | 2 | Комплект промышленного крепежа |
| `reinforcement_bundle` | 2 | Арматурный пакет |
| `rail_profile` | 2 | Рельсовый профиль |
| `sleeper_pack` | 2 | Пакет железнодорожных шпал |
| `dry_concrete_mix` | 2 | Сухая бетонная смесь |
| `ceramic_compound` | 2 | Керамическая масса |
| `technical_ceramic` | 2 | Техническая керамика |
| `copper_busbar` | 2 | Медная силовая шина |
| `heavy_machine_frame` | 3 | Рама тяжёлой машины |
| `industrial_control_unit` | 3 | Промышленный блок управления |
| `power_distribution_module` | 3 | Силовой распределительный модуль |

Служебные (не входят в экономику, переходные элементы Create Sequenced Assembly):
`incomplete_heavy_machine_frame`, `incomplete_industrial_control_unit`.

### 3.3 Новый блок

`reinforced_concrete` — Железобетон. Массовый строительный блок.

### 3.4 Цепочка производства

```
Уголь ──Mixing(нагрев)──> Промышленный кокс
Железо + Кокс + Кальцит ──Mixing(нагрев)──> Стальная заготовка
Стальная заготовка ──Pressing──> Стальная плита
Стальная заготовка ──Cutting──> Конструкционная балка
Стальная плита ──Cutting──> Комплект крепежа (x8)
Конструкционная балка ──Cutting──> Арматурный пакет (x2)
Конструкционная балка ──Pressing──> Рельсовый профиль (x2)
Песок + Гравий + Кальцит ──Mixing──> Сухая бетонная смесь (x8)
Сухая смесь + Арматура + Вода ──Compacting──> Железобетон (x8)
Глина + Кварц + Кремень ──Mixing──> Керамическая масса (x4)
Керамическая масса ──Smelting / Blasting──> Техническая керамика
Медный лист Create ──Cutting──> Медная силовая шина (x4)
Деревянные плиты + Крепёж ──Crafting──> Пакет шпал (x4)

Andesite Casing ──Sequenced Assembly (2 петли)──> Рама тяжёлой машины
Precision Mechanism ──Sequenced Assembly──> Промышленный блок управления
Рама + ICU + Busbar + Керамика + Transformer Core ──Mechanical Crafting──> Силовой модуль
```

### 3.5 Стоимость полного мультиблока 10x3x10

Структура по коду `MineMultiblock`:
120 Mine Frame, 128 Technical Block, 16 Drill Unit, 1 Controller, 1 Power Port, 1 Gateway.

Производство: 5 крафтов Mine Frame (x24) + 6 крафтов Technical Block (x24) + 8 крафтов Drill Unit (x2).

## 4. Creative tab

Собственная вкладка `createcityexpansioncore:industrial`.
Порядок: Tier 1-derived (инструменты) -> Tier 2 -> Tier 3 -> машины -> инфраструктура -> строительные блоки.

## 5. Recipe override datapack

Отдельный always-active SERVER_DATA pack, зарегистрированный через
`AddPackFindersEvent` с `Pack.Position.TOP`.

- Путь в ресурсах: `datapacks/dependency_recipe_overrides/`
- Pack ID: `mod/createcityexpansioncore:datapacks/dependency_recipe_overrides`
- Позиция: TOP (приоритетнее обычных mod datapack)
- Содержит только намеренно заменяемые рецепты зависимостей

| Overridden ResourceLocation | Причина |
|---|---|
| `create:crafting/kinetics/track` | Дешёвый рельс из камня и нугетов обходил стальную цепочку |
| `create:crafting/kinetics/track_station` | Станция без промышленного слоя |
| `create:crafting/kinetics/track_signal` | Сигнал без промышленной автоматики |
| `create:crafting/kinetics/controller_rail` | Train Controls: нужен реальный id `controller_rail` |
| `electroenergetics:crafting/connector` | Terracotta -> Technical Ceramic |
| `electroenergetics:crafting/insulator` | Terracotta -> Technical Ceramic |

## 6. Что намеренно НЕ изменено

- City Level I -> II требования (10 000 профильного ресурса, EE-компоненты, логика deposit, project slot logic).
- EconomyManager, escrow, Marketplace domain logic.
- Railway SavedData, City persistence.
- Mine runtime behavior, EE simulation.
- Карта, UI, economy screens.
