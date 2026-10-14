# CreateCityExpansionCore

> Актуальный срез команды от 14.10.2026: [State, подключения, роли и чеклист](docs/audit-2026-10-14/README.md).
> Backend, Telegram bot, Windows launcher и infra находятся в `platform/`. Рабочая ветка: `codex/next-ai-work`.

> **Индустриальный мод для Minecraft 1.21.1 (NeoForge)**  
> Связывает Create, Create: Electro Energetics, железные дороги и городскую экономику в единую производственную прогрессию.

---

## 🎯 Концепция

**CreateCityExpansionCore** — это мод для индустриального survival-сервера, где **Create является основой геймплея**.

Мод **НЕ заменяет** Create волшебными машинами. Он создаёт **причины** строить большие производственные линии Create:
- Города растут за счёт реальных ресурсов, а не абстрактных очков
- Заводы работают на физических сырьё и энергии
- Железные дороги перевозят миллионы предметов, а не стеки
- Экономика базируется на контрактах, складах и логистике

> **Это НЕ RPG. Это НЕ геополитический симулятор.**  
> Это инженерный песочник, где физика материалов диктует архитектуру мира.

---

## ⚙️ Технологический стек

| Компонент | Версия |
|-----------|--------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251 |
| Java | 21 |
| Create | 6.0.10 (280) |
| Create: Electro Energetics | 1.21.1-1.1.2 |
| FTB Quests | (опционально, для гайдов) |

**Mod ID:** `createcityexpansioncore`

---

## 🏭 Реализованные системы

### 🏙 Городская система
- **City Pylon** — центральный блок города и единый hub для Overview, Economy, Logistics, Management, Railway и World Map
- Роли, приглашения, проекты, развитие I → II, односторонний Trusted и взаимный Partner
- Роли: Owner / Admin / Builder / Resident — с правами и UI для управления
- Приглашения игроков, проекты развития, уровни города (I → II)
- Сохранение в `CityManager` (SavedData), перенос пилона через предмет

### ⛏ Промышленная шахта и Geology v3
- Мультиблок **10×3×10**: Mine Frame, Technical Block, Drill Unit, Controller, Power Port, Gateway
- Питание от **Electro Energetics** (вольты, амперы, ватты, перегрузки)
- Процедурные пространственные залежи радиусом 150–480 блоков, общий reserve и локальный potential
- Magnetic Analyzer — поиск аномалии и локальный замер; ПКМ в воздухе и по блоку, server-authoritative cooldown
- Bulk iron с Fe-equivalent accounting и цепочкой raw ore → crushed → concentrate → ingot
- Звуковой контроллер: бурильщики, запуск/остановка, аварийная остановка
- Комиссионирование под город, привязка к Ж/Д станции

### 🚂 Железные дороги
- Регистрация/управление станциями и поездами через City Pylon; Dispatcher Key сохранён как физический/legacy инструмент
- **RailLine** — расписания (Create schedules), типы линий, цикличность
- **RailwayLineEditorScreen** — компактный редактор линий с остановками, режимами, таймингами
- Persistent ownership сетей/участков, PUBLIC/PRIVATE, freight tariff
- Trip reserve/settlement, Route Planner, RouteReservation и RouteGuard

### 💰 Экономика
- Личные кошельки, **City Treasury**, закупки (Procurement Budget, Smart Orders)
- **Marketplace** — частные листинги (продажа/покупка/бартер) + городские контракты
- **Contract Intake** / **Warehouse Gateway** — инфраструктура ввода/вывода ресурсов
- Эскроу, physical delivery settlement и Master City warehouse/procurement
- Экономический интерфейс с вкладками: Рынок, Моя шахта, Создать, Инфраструктура, Мастер

### 📦 Freight Logistics
- Единые SEND/RECEIVE/BOTH Freight Points поверх Create Station + Postbox + Packager
- P2P и оба направления Master City используют один physical Package pipeline
- Автоматический Create address + ShipmentToken; cargo не телепортируется
- Успешная валидированная распаковка является proof of delivery

### 🧭 World Map
- Тайловый рендеринг рельефа (кэширование, LOD, запрос по вьюпорту)
- **Соседние города** — получаются один раз с сервера, фильтруются по дистанции (≤512 блоков), рисуются с viewport culling
- Собственный город — яркий акцент, чужие — приглушённые цвета (инфраструктурная карта, не политическая)
- Ж/Д слои: ownership, sections, tariff/access, stations, trains, selected routes и active reservations
- Freight Points, Master City, relation links, context cards и Problem Center
- Зум 0.5×–4×, панорамирование, кнопки слоёв

### 📊 Статусы и persistence
- Общий `CcecStatus`: SUCCESS / ACTION_REQUIRED / BLOCKED / WARNING
- SavedData для города, relations, Geology v3, Economy/Freight и Railway v5
- Restart-safe escrow, reserves, shipment tokens, ownership, trips и reservations

---

## 📦 Ресурсные Tier (не путать с City Level)

| Tier | Описание | Примеры | Нужно |
|------|----------|---------|-------|
| **Tier 1** | Сырьё | Железо, медь, цинк, уголь, камень, песок, глина, кварц | Десятки тысяч (rail bulk) |
| **Tier 2** | Пром. материалы | Кокс, сталь, балки, крепёж, арматура, рельсы, шпалы, бетон, керамика, медная шина | Сотни/тысячи (Create automation) |
| **Tier 3** | Инж. продукция | Рама тяж. машины, ICU, силовой модуль | Единицы/десятки (Sequenced Assembly) |

> **Принцип:** *Industrial grind, not manual grind.* Массовые материалы имеют batch-выход и естественно автоматизируются через Create.

---

## 🔧 Рецепты и датапаки

- **Override datapack** (SERVER_DATA, Pack.Position.TOP) заменяет дешёвые рецепты Create/EE:
  - `create:track`, `track_station`, `track_signal`, `controller_rail`
  - `electroenergetics:connector`, `insulator` (Terracotta → Technical Ceramic)
- Собственные рецепты в `data/createcityexpansioncore/recipe/` (blasting, compacting, crafting, cutting, mechanical_crafting, mixing, pressing, sequenced_assembly, smelting)
- **Creative tab:** `createcityexpansioncore:industrial` — порядок Tier 1-derived → Tier 2 → Tier 3 → Машины → Инфраструктура → Строительные блоки

---

## 🎨 Ассеты

- **City Pylon** — полная Blockbench-модель (`art/city_pylon/`) с 12 анимированными частями, pivot-based rotation, эмиссивные текстуры
- **Reinforced Concrete** — строительный блок
- **26 новых предметов** с текстурами 16×16 (Create-style)
- **Звуковой пакет** — 60+ OGG: пилон, шахта, экономика, Ж/Д, UI, анализатор, проекты, диспетчер, склад
- Локализация: `en_us.json`, `ru_ru.json`

---

## 🚀 Установка

1. Установите **NeoForge 21.1.251** для Minecraft 1.21.1
2. Установите **Create 6.0.10** и **Create: Electro Energetics 1.1.2**
3. Положите `CreateCityExpansionCore-<version>.jar` в `mods/`
4. (Опционально) FTB Quests для встроенных гайдов

> Мод регистрирует свой **always-active datapack** для рецептов — отдельного датапака ставить не нужно.

---

## 🛠 Для разработчиков

### Сборка
```bash
./gradlew build
```
Артефакт: `build/libs/CreateCityExpansionCore-<version>.jar`

### Структура пакетов
```
com.example.examplemod
├── block/              # CityPylonBlock, BlockEntity, ReinforcedConcrete
├── city/               # City, CityManager, CityMember, CityProject, Development
├── client/
│   ├── map/            # CityMapPanel (тайлы, peers, Ж/Д, проекты)
│   ├── render/         # CityPylonRenderer, CityPylonModels
│   ├── screen/         # CityPylonScreen, MagneticAnalyzerScreen, MineControllerScreen, ModalOverlayScreen...
│   ├── gui/            # CityButton
│   └── sound/          # PylonSoundController, MineSoundController, LoopSoundManager
├── economy/            # Economy v2, Freight Points, contracts, Master City, tokens
├── geology/            # Spatial deposits, Geology v3, survey/analyzer
├── mine/               # MineController, MineMultiblock, Electrical, State
├── railway/            # Fleet, ownership, planner, trips, reservations/guard
├── network/            # Payloadы (City, Economy, Mine, Railway, Geology)
├── recipe/             # DependencyRecipeOverride (datapack registration)
└── sound/              # ModSounds, WorldSoundHelper
```

### Ключевые архитектурные решения
- **Create = основа производства** — никаких магических машин (D-001)
- **Tier ≠ City Level** — материал и прогресс города разделены (D-002)
- **Batch output** для массовых материалов (D-003)
- **Специализация через экономику**, а не классы (D-004)
- **Recipe override через datapack**, а не runtime-мутацию (D-006)
- **Physical Truth** — руда добыта физически → переработана → привезена → использована

---

## 📝 Лицензия

Проект распространяется под лицензией **MIT** (или укажите свою).  
Ассеты City Pylon (Blockbench) — авторство **Astra** (integration_manifest.json).

---

## 🔗 Полезные ссылки

- [Create Mod](https://www.cursedforge.com/minecraft/mc-mods/create)
- [Create: Electro Energetics](https://www.cursedforge.com/minecraft/mc-mods/create-electro-energetics)
- [NeoForge](https://neoforged.net/)
- [Blockbench](https://blockbench.net/) — для редактирования модели пилона

---

## 🤝 Контрибьюция

1. Форкните репозиторий
2. Создайте ветку (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'feat: amazing feature'`)
4. Запушьте (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

**Стиль коммитов:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`, `build:`)

---

> *Сделано для индустриального survival-сообщества. Строите заводы, прокладывайте рельсы, объединяйтесь в города — и пусть паровые гиганты будут с вами.*
