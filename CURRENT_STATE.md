# CURRENT_STATE.md

Срез текущего локального состояния CreateCityExpansionCore.

Актуализирован: 2026-10-03. Source of truth — код текущего MERGED worktree.

## 1. Платформа

| Компонент | Версия |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251 |
| Java | 21 |
| Create | 6.0.10-280 |
| Create: Electro Energetics | 1.21.1-1.1.2 |
| Mod ID | `createcityexpansioncore` |

## 2. Реализованные домены

### City

- `CityManager` SavedData, City Pylon, роли, приглашения, территория.
- City Projects, Industrial Mine commissioning и развитие I → II.
- One-way trusted relations; mutual trust = partner.
- `CityAccessPolicy` для Railway private access и будущих Freight/tariff hooks.

### Geology v3

| Параметр | Текущее значение |
|---|---|
| SavedData format | 3 |
| Generator version | 1 |
| Macro-cell | 768×768 |
| Lookup | 3×3 macro-cell (`LOOKUP_RING = 1`) |
| Максимум кандидатов | 2 на ресурс / macro-cell |
| Ресурсы | iron, coal, limestone, copper, zinc |
| Радиус залежи | 150–480 блоков |
| Analyzer search | 200 блоков от границы |
| Analyzer cooldown | 60 ticks |

`DepositShape` хранит center, axes, rotation, core radius и shape seed. Граница получает smooth deterministic distortion; local potential плавно падает от ядра к краю. `depositsById` содержит канонический объект на stable ID, поэтому разные query/шахты списывают один remaining reserve.

Quality classes:

- MODEST: 25%, 500–1500 units/h;
- RICH: 35%, 1500–3000;
- DENSE: 25%, 3000–6000;
- PRIME: 12%, 6000–8500;
- EXCEPTIONAL: 3%, 8500–10000.

Size classes независимы от quality:

- SMALL: 20%, radius 150–230, 50–100 h;
- NORMAL: 50%, 220–330, 120–180 h;
- LARGE: 25%, 300–400, 180–300 h;
- GIANT: 5%, 380–480, 300–500 h.

Migration v1/v2 → v3 сохраняет resource, deposit ID, initial/remaining reserve и legacy grade. Недостающая геометрия восстанавливается детерминированно.

### Magnetic Analyzer

- Один server-authoritative `performScan` используется `use` и `useOn`.
- Первый scan не блокируется отсутствующим timestamp.
- Durability списывается только после успешной проверки storage/cooldown.
- Вне залежи: resource, direction, грубая дистанция.
- Внутри: local potential, оценка reserve, size class.
- Payload: `GeologyAnalysisPayload` / актуальные `GeologySnapshotCodecs`.
- Кодовая регрессия «ПКМ в воздухе ничего не делает» исправлена; нужен ручной runtime retest.

### Industrial Mine и Bulk iron

- Мультиблок 10×3×10: 120 Mine Frame, 128 Technical Block, 16 Drill Unit, Controller, Power Port, Gateway.
- EE power/state machine сохранены.
- Mine выбирает deposit по локальному potential и добывает общий reserve.
- Номинальная шкала — 3000 reserve units/h при 100% мощности; ниже 100 units/h добыча практически бессмысленна.
- `MineOutputMapping` разделяет physical output и reserve units.
- Iron: `physicalAmountFor(iron, 5) = 10`, `reserveUnitsFor(iron, 10) = 5`.
- Добавлены предметы/рецепты Bulk iron: raw ore, crushed ore, concentrate.

### Economy v2

- Wallets, City Treasury, escrow.
- P2P listings/contracts, community/exclusive contracts.
- External Market, MarketDefinition, MarketOffer, demand/saturation.
- Master warehouse и procurement budget.
- Smart Orders восстанавливаются/генерируются из admin definitions.
- Master City buy/sell использует physical Freight endpoints; inventory grants не используются.

### Freight Logistics v1

- Persistent Freight Points: city, name, SEND/RECEIVE/BOTH, generated address, Postbox, Packager, station, enabled/default flags, validation status.
- Guided registration/rebind с server-side проверкой территории и Create blocks.
- Freight Assignment связывает subject, source/destination point, route/train и dispatch state.
- ShipmentToken и ShippingManifest переиспользованы как внутренние ledger/proof механизмы.
- Packager создаёт физическую Create Package с destination address и token.
- Contract intake/unpacking валидирует destination, item, amount, replay и atomic acceptance.
- Full delivery автоматически закрывает contract/offer и выполняет settlement.
- Source outage приостанавливает flow без инвалидирования уже созданных tokens.

### Railway CRE-75

- `RailwayManager` format v5.
- City stations/fleet trains и Create schedule integration.
- OwnedRailwayNetwork / OwnedRailwaySection, owner PLAYER/CITY.
- PUBLIC/PRIVATE, freight tariff CR/1000 blocks/train.
- TrackGraph path references, marker reconciliation, protection from ownership takeover.
- RailwayTripLedger, reserve/actual settlement, owner payouts, restart и replay protection.
- RailwayRoutePlanner: CHEAPEST / FASTEST / BALANCED, duplicate collapse, private exclusion.
- SelectedRailwayRoute, RouteReservation, RouteGuard/topology validation.
- Freight dispatch переиспользует planner, accounting и guard.
- Train registration доступна через server-authoritative railway command/UI flow; legacy Dispatcher Key path также остаётся частью системы.

### City Relations

- Persistent A → B trusted relation.
- Mutual trust отображается как PARTNER.
- Собственный город всегда имеет полный доступ.
- Private railway access может разрешаться owner policy + relations; автоматической tariff discount/exemption механики пока нет.

### UI

- City Pylon hub, Logistics operational panels, Market, Railway management.
- Polished World Map: terrain, cities, territories, Freight Points, Create stations, networks/sections, trains, selected routes/reservations, context cards.
- Unified status/toast model и Problem Center.
- RU/EN localization и headless layout/status checks.

## 3. Производственный слой

Tier предмета не равен City Level.

- Tier 1: bulk raw resources.
- Tier 2: coke, billet, plate, beam, fasteners, reinforcement, rail profile, sleepers, concrete, ceramics, copper busbar.
- Tier 3: heavy machine frame, industrial control unit, power distribution module.

Create recipes и dependency override datapack остаются основным production path.

## 4. Persistence

Отдельные SavedData используются для CityManager/relations, Geology v3, Railway v5/ownership/trips/reservations, Economy v2 и Freight Points/assignments/tokens.

Ключевое правило миграций: неизвестная новая версия не должна молча перегенерировать или обнулять production data.

## 5. Headless verification

`check` включает suites для Freight ledger/reconciliation, dispatch/handoff/arrival, package identity, Railway ownership/accounting, relations, World Map/layout, City Pylon/invite/members, unified status, train management и procedural geology/Analyzer contract.

Точный список задач находится в `build.gradle` и является актуальнее этого перечня.

## 6. Известные ограничения и runtime backlog

- Minecraft runtime/world testing выполняется вручную владельцем проекта.
- Geology v3 и Analyzer interaction fix присутствуют в коде, но требуют ручного теста: ПКМ в воздухе, ПКМ по блоку, cooldown, restart и отображение survey.
- Нет traction billing, passenger tariffs, fleet balancing, timetable prediction, offline Create simulation и multi-train shipment splitting.
- Не считать старые chat summaries и GitHub-ветку источником истины, если они расходятся с текущим локальным MERGED worktree.
