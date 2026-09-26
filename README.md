# CreateCityExpansionCore

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
| Create: Electro Energetics | 1.21.1-1.1.1 |
| FTB Quests | (опционально, для гайдов) |

**Mod ID:** `createcityexpansioncore`

---

## 🏭 Реализованные системы

### 🏙 Городская система
- **City Pylon** — центральный блок города с анимированной моделью (Blockbench), FACING, лампами статуса
- **CityPylonScreen** — большой интерфейс (620×345) с 8 вкладками: Обзор, Территория, Участники, Проекты, Карта, Развитие, Ж/Д, Рынок
- Роли: Owner / Admin / Builder / Resident — с правами и UI для управления
- Приглашения игроков, проекты развития, уровни города (I → II)
- Сохранение в `CityManager` (SavedData), перенос пилона через предмет

### ⛏ Промышленная шахта (Industrial Mine)
- Мультиблок **10×3×10**: Mine Frame, Technical Block, Drill Unit, Controller, Power Port, Gateway
- Питание от **Electro Energetics** (вольты, амперы, ватты, перегрузки)
- Геологические залежи: стадии, качество, глубина, устойчивая добыча
- Magnetic Analyzer — сканирование регионов (мгновенное, серверное)
- Звуковой контроллер: бурильщики, запуск/остановка, аварийная остановка
- Комиссионирование под город, привязка к Ж/Д станции

### 🚂 Железные дороги
- **Dispatcher Key** — регистрация поездов и станций
- **RailLine** — расписания (Create schedules), типы линий, цикличность
- **RailwayLineEditorScreen** — компактный редактор линий с остановками, режимами, таймингами
- Флот города, автоматизация, привязка к проектам шахт и экономике

### 💰 Экономика
- Личные кошельки, **City Treasury**, закупки (Procurement Budget, Smart Orders)
- **Marketplace** — частные листинги (продажа/покупка/бартер) + городские контракты
- **Contract Intake** / **Warehouse Gateway** — инфраструктура ввода/вывода ресурсов
- Эскроу, штрафы, доставки, подтверждения, мастер-город (Master City)
- Экономический интерфейс с вкладками: Рынок, Моя шахта, Создать, Инфраструктура, Мастер

### 🧭 Карта (CityMapPanel)
- Тайловый рендеринг рельефа (кэширование, LOD, запрос по вьюпорту)
- **Соседние города** — получаются один раз с сервера, фильтруются по дистанции (≤512 блоков), рисуются с viewport culling
- Собственный город — яркий акцент, чужие — приглушённые цвета (инфраструктурная карта, не политическая)
- Ж/Д слои: пути, станции, поезда (интерполяция), проекты шахт, экономическая инфраструктура
- Зум 0.5×–4×, панорамирование, кнопки слоёв

### 📊 Геология
- Детерминированная генерация залежей по чанкам (RegionSnapshot)
- Magnetic Analyzer — мгновенный анализ региона сервером
- Сохранение остатков залежей между сканами

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
2. Установите **Create 6.0.10** и **Create: Electro Energetics 1.1.1**
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
├── economy/            # EconomyManager, Contract, Listing, Infrastructure, Treasury
├── geology/            # RegionSnapshot, DepositSnapshot, MagneticAnalyzer
├── mine/               # MineController, MineMultiblock, Electrical, State
├── railway/            # RailLine, Station, Train, DispatcherKey, Fleet
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