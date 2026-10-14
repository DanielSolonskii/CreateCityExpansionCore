# Полный функциональный чеклист RE:CRETED

Срез: 14.10.2026, Europe/Minsk. Done относится только к инвентаризации реализации в коде; не означает runtime acceptance. Каждый пункт содержит самостоятельный сценарий приёмки. Дополнительные открытые этапы собраны в TODO. Существующие задачи старого workspace не закрывались и не переносились автоматически.

## STATE-01 · M0 · Платформа и сборка

Ответственный профиль: Engineering. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `build.gradle`.

Minecraft 1.21.1; NeoForge 21.1.251; Java 21; Create 6.0.10-280; EE 1.1.2; CCEC 1.0.1. Gradle check/build и набор headless contracts.

- [x] Реализация в текущих исходниках найдена.
- [ ] Чистый клон: Gradle wrapper, EE и runtime Ponder/Flywheel/Create должны быть доступны.

## STATE-02 · M1 · Аккаунты и Telegram verification

Ответственный профиль: Backend. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `platform/backend/src/main/java/dev/createserver/backend/account`.

Регистрация pending→ACTIVE, BCrypt credentials, Telegram binding, alpha access keys, phone verification; Flyway V1–V5.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить новый аккаунт, неверный/повторный alpha key и реальное подтверждение телефона в Telegram.

## STATE-03 · M1 · Trusted devices и Credential Manager

Ответственный профиль: Backend. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `platform/backend/src/main/java/dev/createserver/backend/api/DeviceController.java`.

Создание, polling, approve, authenticate, revoke доверенного устройства; native launcher хранит device credential в Windows Credential Manager.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить approve/revoke, перезапуск launcher, выход и смену endpoint без утечки credentials.

## STATE-04 · M1 · Игровая авторизация и TLS admission

Ответственный профиль: Backend. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/auth/ConnectionAdmissionRegistry.java`.

Launcher capability → connection-bound challenge → одноразовый game ticket → consume в backend → допуск CONFIGURATION. Boot/nonce/UUID bindings и защита дубликатов.

- [x] Реализация в текущих исходниках найдена.
- [ ] Runtime: правильный вход, подмена имени, параллельный вход, expiry, revoke и outage. На production mode=REQUIRED.

## STATE-05 · M2 · Города, участники, роли и территория

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/city/CityManager.java`.

City Pylon, основание города, identity, claims, участники, поиск, приглашения и роли; SavedData и server-side permissions.

- [x] Реализация в текущих исходниках найдена.
- [ ] Два игрока: основать город, пригласить, изменить роль, claim/release и проверить чужую территорию.

## STATE-06 · M2 · Отношения между городами

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/city/CityAccessPolicy.java`.

Одностороннее доверие; взаимное доверие даёт PARTNER; политика доступа к private railway.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить A→B и B→A; доверие само по себе не является скидкой на тариф.

## STATE-07 · M2 · Развитие города и индустриальные проекты

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/city/CityDevelopmentRequirements.java`.

City level requirements, городские проекты промышленных объектов, commissioning/decommissioning шахты и развитие I→II.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить требования фактического уровня, корректные блоки и отключённые/утраченные объекты.

## STATE-08 · M2 · Проекты города и сервера

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/projects/ProjectManager.java`.

Scope CITY/SERVER; draft/publish/funding/applicants/assign/stages/submit/accept/return/cancel/withdraw. Reward/reserve, server budget, revisions и persistence.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить весь lifecycle двумя игроками/OP, stale revision, refund, restart, project zone conflicts.

## STATE-09 · M2 · Профили игроков

Ответственный профиль: UI/UX. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/profile/ProfileService.java`.

Profile screen, поиск/каталог, город, участие в проектах, метрики из существующих ledgers, настройки видимости, skins/offline cache и rate limits.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить privacy другого игрока, собственное редактирование, offline profile и отображение skin.

## STATE-10 · M2 · City Pylon workspace

Ответственный профиль: UI/UX. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/client/screen/CityPylonWorkspace.java`.

Hub, страницы города, участники, экономика, логистика, relations, проекты и переходы к WorldMap; общая тема/layout.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить все доступные страницы на минимальном размере окна и с длинными RU/EN строками.

## STATE-11 · M2 · WorldMap и слои

Ответственный профиль: UI/UX. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/client/screen/PolishedWorldMapScreen.java`.

Pan/zoom/поиск/карточки; города/territories/freight/stations/railway/trains/routes и project workspace. Server snapshots/read access.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить видимые слои и hitboxes в игре; headless layout не подтверждает внешний вид.

## STATE-12 · M2 · Terrain pyramid, atlas и cache

Ответственный профиль: UI/UX. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/client/map/TerrainAtlas.java`.

Региональные масштабы, terrain LOD/pyramid, relief, revisions, source colours, texture/cache/store и lifecycle карты.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить загрузку/переключение регионов, stale revision, повторное открытие и освобождение GPU textures.

## STATE-13 · M3 · Геология format 4 / generator 2

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/geology/GeologySavedDataCodec.java`.

Детерминированная геология iron/coal/limestone/copper/zinc, per-resource seed, формы/potential/quality/size, общий reserve. Старые форматы 1–3 пересоздаются.

- [x] Реализация в текущих исходниках найдена.
- [ ] Критично: старые reserves/выработка не переносятся; неизвестный будущий формат отклоняется. Проверить на копии сохранения.

## STATE-14 · M3 · Magnetic Analyzer

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/item/MagneticAnalyzerItem.java`.

Единый server-side scan для воздуха/блока, cooldown/durability, survey, direction/distance/local potential и reserve estimate.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить оба ПКМ, первый scan, cooldown, все пять ресурсов и локализацию.

## STATE-15 · M3 · Industrial Mine и электричество

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/mine/IndustrialMineService.java`.

Мультиблок, контроллер, EE power port, commissioning/start/stop, structure/power/output states, local deposit extraction и gateway output.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить 4 ориентации, power under load, OUTPUT_BLOCKED, истощение, restart и реальный выпуск.

## STATE-16 · M3 · Bulk iron и промышленные материалы

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/mine/MineOutputMapping.java`.

Physical output отделён от reserve units; raw/crushed iron/concentrate; рецепты промышленных материалов, slag/fluids и T1–T3 items.

- [x] Реализация в текущих исходниках найдена.
- [ ] Свести BOM/throughput/энергию из актуальных JSON; не приравнивать item tier к city level.

## STATE-17 · M3 · Coke Oven Battery

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/coke/CokeBatteryControllerBlockEntity.java`.

Фиксированный multiblock, input/output/controller/guide/state, партия 8, цикл 200 ticks; provisional balance отмечен в коде.

- [x] Реализация в текущих исходниках найдена.
- [ ] Сверить реальную выдачу/EE и баланс относительно Create Mixer.

## STATE-18 · M3 · Blast Furnace

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/blast/BlastFurnaceControllerBlockEntity.java`.

Multiblock, input/fluid output, EE power/state и guide; цикл 300 ticks, выход 8000 mB, номинально 60 kW.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить физические input/output и остановки при power/structure/output failure.

## STATE-19 · M3 · Steel Converter

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/steel/SteelConverterControllerBlockEntity.java`.

Multiblock/ports/guide; 8000 mB→8 billets, 300 ticks, 80 kW, диапазон 500–600 V.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить полный steel path в игре, баланс и rendering.

## STATE-20 · M3 · Chunk loader

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/chunkloader/ChunkLoaderSavedData.java`.

Рецепт/блок, максимум 3 на игрока, ownership, persistent registry, tickets и reconciliation/removal.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить 4-ю установку, чужое разрушение, explosion/piston/contraption и восстановление после restart.

## STATE-21 · M3 · Gameplay restrictions

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/policy/GameplayRestrictions.java`.

Запрет shulker boxes/elytra flight, cap лодки на льду, запрет поездов в Nether; kelp growth policy.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить bypass через предметы/теги/портал и сохранить работу лодки на воде/обычного транспорта.

## STATE-22 · M4 · Кошельки, казна и escrow

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/economy/EconomyManager.java`.

Persistent economy, wallets, city treasury, escrow, проверки балансов и atomic settlement.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить refund/replay/restart/overflow и отсутствие бесплатного выпуска валюты.

## STATE-23 · M4 · P2P рынок и контракты

Ответственный профиль: UI/UX. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/client/screen/MarketScreen.java`.

Listings buy/sell, contracts, community/exclusive operations, server read view, row selection и labelled actions/confirmation.

- [x] Реализация в текущих исходниках найдена.
- [ ] Клик строки только SELECT; проверить явные Accept/Cancel/Confirm и права другой стороны.

## STATE-24 · M4 · Master City / External Market

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/economy/ExternalMarketState.java`.

Definitions/offers/demand/saturation, smart orders, master warehouse/procurement budget; физические freight endpoints.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить supply/demand и реальный Packager путь, отсутствие inventory grants вместо перевозки.

## STATE-25 · M4 · Freight points и физические Packages

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/economy/FreightLogistics.java`.

SEND/RECEIVE/BOTH endpoints, postbox/packager/address/station bind/rebind, assignment/token/manifest; physical dispatch/intake.

- [x] Реализация в текущих исходниках найдена.
- [ ] E2E Package→поезд→receiver→unpacking→settlement; partial delivery, duplicate, outage и restart.

## STATE-26 · M4 · Железные дороги, собственность и маршруты

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/railway/RailwayManager.java`.

Stations/fleet/Create schedules; PLAYER/CITY ownership, PUBLIC/PRIVATE, CR/1000 blocks/train tariffs, planner CHEAPEST/FASTEST/BALANCED.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить topology change, private/trust policy, route reservation, duplicate routes и чужое присвоение.

## STATE-27 · M4 · Railway reserve/settlement и Freight dispatch

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/java/com/example/examplemod/railway/RailwayTripAccountingService.java`.

Trip ledger, upfront reserve/actual settlement/owner payouts, route guards и restart/replay protection; freight reuse.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить денежный итог для всех участников при arrival/cancel/restart и неполной перевозке.

## STATE-28 · M5 · Reports и moderation backend/bot

Ответственный профиль: Backend. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `platform/backend/src/main/java/dev/createserver/backend/report`.

Игровой report bridge, ограничения текста/cooldown, PostgreSQL reports, admin actions, notifications/claim/ack и player card.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить игровой report→реальный Telegram→action и недоступность private endpoints снаружи.

## STATE-29 · M5 · Telemetry bridge и Telegram панели

Ответственный профиль: Backend. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `platform/backend/src/main/java/dev/createserver/backend/bridge`.

Bounded game telemetry queue, immutable retry/ACK/overflow; backend summaries; Telegram server/menu/admin панели.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить свежесть/overflow/outage и реальные Telegram callbacks; service active недостаточно для acceptance.

## STATE-30 · M5 · Broadcasts и новости launcher

Ответственный профиль: UI/UX. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `platform/backend/src/main/java/dev/createserver/backend/broadcast`.

Draft/publish/cancel broadcasts, claim/ack deliveries; публичные safe news DTO для launcher/news pages.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить compose→news rendering→Telegram delivery, ссылки/форматирование и права автора.

## STATE-31 · M5 · Launcher UI и native game launch

Ответственный профиль: UI/UX. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `platform/launcher/src-tauri/src/lib.rs`.

React/Tauri 2, account flows, trusted devices, game prepare/start/status/logout, signed game update, settings, news, window controls и live TLS server status.

- [x] Реализация в текущих исходниках найдена.
- [ ] Browser preview не проверяет native Play/TLS/Credential Manager. Нужен Windows installer E2E.

## STATE-32 · M5 · Launcher self-update и releases

Ответственный профиль: Engineering. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `platform/launcher/src-tauri/src/self_update.rs`.

Проверка/скачивание/установка launcher update, deferred update при работающем Minecraft; signed game manifests и release dry-run→server-deploy→publish.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить upgrade с production 0.1.5, mandatory optional update и rollback/recovery без перезаписи старых releases.

## STATE-33 · M5 · Production и storage

Ответственный профиль: Ops. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `platform/infra`.

Systemd Minecraft/backend/bot/TLS, Caddy HTTPS, PostgreSQL, loopback raw Minecraft/API/DB, защищённые secrets/backups и game/launcher distribution.

- [x] Реализация в текущих исходниках найдена.
- [ ] 14.10 SSH: четыре службы active/running, health/postgres UP; production CCEC hash отличается от локальной новой сборки.

## STATE-34 · M6 · FTB Quests RU/EN onboarding

Ответственный профиль: Game Design. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `tools/ftb-campaign/build_campaign.py`.

6 глав / 128 квестов; survival/Create/geology/city/mine/optional chunks. Item + manual checkmark, consume_items=false, optional branches и stable IDs.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить книгу в клиенте, видимость линий, основной маршрут, ручные подтверждения и сохранность progress.

## STATE-35 · M6 · HUD, локализация, модели и звуки

Ответственный профиль: UI/UX. Статус: реализация обнаружена в коде; runtime отдельно.

Источник: `src/main/resources/assets/createcityexpansioncore`.

RU/EN, item/block models/textures/sounds, multiblock guides/renderers, unified statuses/toasts, city bossbar/maintenance countdown.

- [x] Реализация в текущих исходниках найдена.
- [ ] Проверить assets в игре, читаемость/tooltip/empty/error states, maintenance cancel и UI без client imports на server.

## TODO-01 · M0 · Воспроизвести сборку из чистого Windows-клона

Ответственный профиль: Engineering. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Обеспечить EE/Ponder/Flywheel/Create для dev runtime, npm ci, Rust prerequisites; build/check без локальных абсолютных зависимостей. Записать точные версии и вывод.

## TODO-02 · M1 · Провести Windows login → Play → join E2E

Ответственный профиль: Backend. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Чистая установка/upgrade, Telegram verification/device approve, native broker/TLS и REQUIRED admission. Проверить spoof/duplicate/expiry/backend outage; сохранить evidence без credentials.

## TODO-03 · M2 · Проверить города, роли, проекты и profile privacy двумя игроками

Ответственный профиль: Game Design. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Полный lifecycle города/территории/relations/projects, server/city funding, restart/refunds, privacy/offline profiles и права другой роли.

## TODO-04 · M3 · Принять геологию format 4 и влияние на старые saves

Ответственный профиль: Game Design. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] На резервной копии проверить переход 1–3→4, потерю старых reserves/ID и работу старых шахт. Зафиксировать продуктовую политику миграций; ничего не удалять на production.

## TODO-05 · M3 · Провести промышленный runtime pass и свести BOM

Ответственный профиль: Game Design. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Analyzer→mine→ore processing→coke→blast→steel; throughput/energy/reserve, все orientation/power/output/structure failures; сравнить с ванильными/Create альтернативами.

## TODO-06 · M3 · Проверить transport bypass и chunk-loading нагрузку

Ответственный профиль: Game Design. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Elytra/shulker/ice boats/Nether trains/kelp; 3-loader limit, crash/restart cleanup, реальные удалённые объекты и train chunks. Измерить TPS/MSPT.

## TODO-07 · M4 · Пройти физический Freight/Railway E2E с денежной сверкой

Ответственный профиль: Game Design. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Два города, public/private/trust routes, Packages/Postboxes, partial/duplicate/source outage, tariffs/reserve/owner payout и restart. Сверить balances и физические предметы до/после.

## TODO-08 · M5 · Проверить reports/broadcasts/telemetry в настоящем Telegram

Ответственный профиль: Backend. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Сверить admin permissions, callbacks, delivery/ACK/retry и публичную news выдачу. Не брать service active за доказательство получения сообщения.

## TODO-09 · M5 · Выполнить PostgreSQL integration suite на отдельной БД

Ответственный профиль: Backend. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Запустить opt-in postgresTest только на выделенной тестовой БД, проверить Flyway V1–V10, uniqueness/locks/replays. Production базу не использовать для тестовых insert.

## TODO-10 · M6 · Провести UI/UX runtime pass и визуальную приёмку

Ответственный профиль: UI/UX. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Матрица min/default/max окна × RU/EN × роли × empty/loading/error/stale states; WorldMap/terrain, pylon, market/freight, projects/profiles, analyzer, launcher. Скриншоты, клики и контраст.

## TODO-11 · M6 · Пройти FTB Quests с новым игроком

Ответственный профиль: Game Design. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] 128 quests/6 chapters; optional не блокируют обязательные, electron tube независим от brass, manual actions объяснены; проверить progress после restart.

## TODO-12 · M6 · Зафиксировать визуальный стандарт CCEC

Ответственный профиль: UI/UX. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Инвентаризация icons/models/textures; нативные 32×32 для новых согласованных Minecraft textures. Не массово перерисовывать утверждённые формы/палитру. Подготовить reviewable макеты.

## TODO-13 · M0 · Выдать сотрудникам личные GitHub/Linear/SSH-доступы

Ответственный профиль: Ops. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Получить GitHub usernames, email Linear и публичные Ed25519 ключи. Добавить collaborator write/Linear members, создать отдельные SSH users по согласованным правам. Приватные ключи/root key не пересылать.

## TODO-14 · M7 · Уточнить post-alpha roadmap после baseline

Ответственный профиль: Game Design. Статус: открытый этап.

Источник: `docs/audit-2026-10-14/STATE_RU.md`.

Открытый этап проверки/работы. Реализация смежных модулей сама по себе не закрывает эту задачу.

- [ ] Этап принят с доказательствами.
- [ ] Отдельные предложения: oil industry, traction/passenger billing, timetable/fleet, offline Create simulation, multi-train splitting. Не отмечать их реализованными по старым Linear-задачам.
