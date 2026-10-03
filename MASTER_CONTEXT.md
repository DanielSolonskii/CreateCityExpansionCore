# MASTER_CONTEXT.md

Канонический высокоуровневый контекст CreateCityExpansionCore (CCEC).

Актуализирован: 2026-10-03. Текущий локальный код репозитория всегда является источником истины; этот документ объясняет его архитектуру и известный runtime-статус.

## Что это за проект

CCEC — серверный индустриальный мод для Minecraft 1.21.1 / NeoForge 21.1.251, Java 21, Create 6.0.10-280 и Create: Electro Energetics 1.1.2.

Он связывает в один физический игровой цикл:

```text
процедурная геология → промышленная добыча → Create-переработка →
город → Freight Points → физические Package → железная дорога →
рынок / контракты / Master City → инфраструктурное развитие
```

Это не RPG и не геополитический симулятор. Create остаётся основной системой производства и физической логистики; CCEC добавляет ownership, persistence, authorization, accounting, городской UX и экономические причины строить масштабно.

## Неподвижные принципы

1. **Physical Truth.** Груз существует как предметы, Package и поезд. Экономика не телепортирует cargo и не выдаёт покупки прямо в inventory.
2. **Create-first.** Производство использует штатные механики Create и Create Railway.
3. **Одна доменная система.** P2P, Sell to Master City и Buy from Master City используют общий Freight/Railway pipeline.
4. **Server authority.** Клиент показывает server snapshot и не придумывает route, tariff или состояние Freight Point.
5. **Industrial grind, not manual grind.** Массовые материалы выпускаются партиями и должны естественно автоматизироваться.
6. **Persistence before convenience.** Wallet, treasury, escrow, deposits, freight assignments, tokens, railway ownership и active trips переживают restart.

## Главные системы

### City / City Pylon

- CityManager, роли Owner/Admin/Builder/Resident, территория, приглашения, проекты и развитие города I → II.
- City Pylon — основной городской интерфейс: Overview, Economy, Logistics, Management, World Map и Railway management.
- One-way `TRUSTED` отношения; взаимное доверие отображается как `PARTNER`. Relations используются reusable permission layer для private railway/service access.

### Geology v3

- Мир делится на детерминированные macro-cell 768×768, но macro-cell — только пространственный индекс, а не граница месторождения.
- Deposit имеет стабильный ID, центр, эллиптическую форму, поворот, искажённую границу, ядро и локальный production potential.
- Радиусы 150–480 блоков; одна залежь может пересекать несколько macro-cell.
- Quality class и size class выбираются независимо.
- Один `depositId` соответствует одному общему authoritative reserve для всех шахт.
- Формат SavedData v3; legacy v1/v2 читаются без потери remaining reserve.
- Analyzer ищет аномалию в пределах 200 блоков от границы либо показывает локальный potential внутри залежи. Работают ПКМ в воздухе (`use`) и ПКМ по блоку (`useOn`); cooldown 60 ticks хранится в persistent player data.

### Industrial Mine / Bulk iron

- Мультиблок 10×3×10 с Controller, Power Port, Gateway, Drill Units и корпусом.
- Питание и electrical state идут через Electro Energetics.
- Производительность зависит от локального geology potential.
- Для железа действует Fe-equivalent: 5 reserve units → 10 физических предметов Bulk-цепочки. Обратное списание не удваивает расход общего запаса.
- Bulk iron: `iron_ore_raw` → `crushed_iron_ore` → `iron_concentrate` → iron ingot.

### Economy v2 / Master City

- Player Wallet, City Treasury, escrow, P2P listings/contracts, External Market, MarketDefinition/MarketOffer и Smart Orders.
- Master City — физический системный город с warehouse, procurement budget, SEND/RECEIVE Freight Points и тем же Freight pipeline, что используют игроки.
- Покупка Master City ограничена budget и demand/saturation; продажа — warehouse stock и supply. Ассортимент задаётся администратором один раз и затем восстанавливается/генерируется автоматически.

### Freight Logistics v1

- `FreightPoint` — логический городской SEND/RECEIVE/BOTH endpoint поверх Create Station + Postbox + Packager.
- Routing address генерируется CCEC; display name не равен routing identifier.
- Контракт хранит source/destination Freight Point references, а не обязательный Contract Intake BlockPos.
- Dispatch маркирует штатную Create Package destination address и ShipmentToken.
- Delivery proof — успешная server-authoritative распаковка на правильной точке; ручное подтверждение покупателем не требуется.
- Один SEND Freight Point обслуживает максимум один active assignment в v1.

### Railway CRE-75

- Persistent networks/sections с owner PLAYER/CITY, PUBLIC/PRIVATE и тарифом CR / 1000 блоков / поезд.
- Реальные участки регистрируются по Create TrackGraph между markers; существующее ownership нельзя перехватить.
- Trip accounting резервирует ожидаемую сумму, оплачивает actual foreign PUBLIC usage, возвращает остаток и защищён от double settlement.
- Route Planner строит реальные cheapest/fastest/balanced варианты с учётом Create travel restrictions и private access.
- SelectedRoute → persistent RouteReservation → RouteGuard. Reroute/topology change инвалидирует маршрут вместо тихого перехода.
- Freight Assignment сохраняет train/route и запускает общий railway accounting/guard pipeline без cargo teleportation.

### World Map / Unified Status UX

- Один World Map data flow показывает города, территории, Master City, Freight Points, stations, networks, owned sections, selected routes и active trips.
- Нет force-load chunks и network request на hover.
- Общий `CcecStatus`/toast language используется Railway и Logistics: SUCCESS, ACTION_REQUIRED, BLOCKED, WARNING.
- Problem Center показывает тот же server-authoritative status, что карточки.

## Что пока не является готовой механикой

- traction/energy billing поездов;
- passenger tariffs;
- timetable/signal prediction и fleet balancing;
- multi-train splitting и multi-contract smart sorting одного Packager;
- войны, treaties, reputation и geopolitics;
- offline Create simulation.

## Runtime truth на 2026-10-03

- Проект собирается и имеет широкий набор headless checks, но зелёный Gradle не заменяет ручной Minecraft runtime test.
- Geology v3 и исправление обоих interaction paths Analyzer находятся в текущем локальном дереве; финальная runtime-проверка остаётся ручной.
- Не считать GitHub/старые описания актуальнее локального MERGED worktree.
- Не запускать Minecraft автоматически: runtime/world testing выполняет владелец проекта.

Подробная инвентаризация: `CURRENT_STATE.md`.
Зафиксированные решения: `DECISIONS.md`.
Среда разработки и проверки: `README_DEV.md`.
