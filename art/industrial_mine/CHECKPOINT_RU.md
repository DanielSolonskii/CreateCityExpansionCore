# Industrial Mine visual v2 — checkpoint, 27.09.2026

Ветка: `codex/next-ai-work`. Автоматических коммитов нет. Исходный uncommitted
diff других задач (Coke Battery, Blast Furnace, industrial materials, electrical
demand) сохранён; он не является результатом этой visual-задачи.

## Состояние на checkpoint-аудите

- Уже существовали один Controller BER, semantic state sync, client particles,
  distance culling, модели и anchors v1.
- Уже были созданы generator v2, новый Blockbench core, atlas, отдельные
  Power/Output projects и масштабированные runtime JSON.
- Уже были добавлены два sync-поля interface positions, reusable visibility
  registry/wrapper, Mine client adapter и общий canonical validator для Level.
- Компиляция проходила, но интеграция была незавершённой: renderer/Models ещё
  использовали старый периметр, старые масштабы и pivots; anchors были v1.
  Наличие успешной compileJava само по себе не означало рабочий visual v2.
- Был простой клиентский накопитель угла только для RUNNING, не подключённый к BER.

## Доделано

- Подключены fixed_core и отдельные ротаторы/валы с правильным scale=8.
- Убраны v1 perimeter/support assets и их draw loops. Развит существующий v2:
  силовые балки, опоры мотора, плотные смазочные/редукторные корпуса, service access.
  Функциональная механика покрывает 67.7% footprint, не считая нижнего основания.
- Исправлены pivots и rotation/anchor transforms относительно центра Controller.
- Power/Output ставятся по canonical positions, корпуса смотрят наружу.
  EE contacts выделены отдельно и следуют реальному BlockState.FACING.
- STARTING/RUNNING разгоняют механизм; STOPPING/нерабочие состояния дают выбег
  и сохраняют фазу в покое. Не добавлены сетевые animation-frame updates.
- Renderer рисует skin только при активном скрытии underlying blocks.
- Исправлены очистка при unload, снятие регистрации unloaded Controller и
  исключение item-вариантов. Исправлено сопоставление реальных blockstate keys:
  `namespace:mine_frame#normal` ранее не получал wrapper из-за неверного пути.
- Добавлены проверка геометрических контрактов в build и generator assertions.

## Файлы этого изменения

Common:

- `src/main/java/com/example/examplemod/multiblock/MineMultiblock.java`
- `src/main/java/com/example/examplemod/mine/MineControllerBlockEntity.java`
- `src/main/java/com/example/examplemod/mine/IndustrialMineService.java`

Client:

- `src/main/java/com/example/examplemod/CreateCityExpansionCoreClient.java`
- `src/main/java/com/example/examplemod/client/render/IndustrialMineRenderer.java`
- `src/main/java/com/example/examplemod/client/render/IndustrialMineModels.java`
- `src/main/java/com/example/examplemod/client/render/IndustrialMineAnchors.java`
- `src/main/java/com/example/examplemod/client/render/IndustrialMineVisuals.java`
- `src/main/java/com/example/examplemod/client/render/AssembledBlockVisibility.java`

Assets / developer workflow:

- `tools/build_industrial_mine.py`
- `art/industrial_mine/`: три bbmodel, atlas, manifest, validation, три preview, документация.
- `src/main/resources/assets/createcityexpansioncore/models/block/industrial_mine/`: 10 JSON.
- `src/main/resources/assets/createcityexpansioncore/textures/block/industrial_mine.png`
- `src/mineVisualTest/java/com/example/examplemod/client/render/MineVisualCoordinatesCheck.java`
- `build.gradle`: отдельный headless source set и checkMineVisualCoordinates в check/build.

## Результаты проверок

- Финальный `gradlew -g C:\Users\Administrator\.gradle build --offline`: SUCCESS.
- `checkMineVisualCoordinates`: PASS — 64 drill placements, 116 power layouts,
  наружные нормали Gateway для всего периметра, четыре orientation,
  298 unique members, service-air исключены.
- `git diff --check`: clean.
- Generator: допустимые Java model coordinates/UV, уникальные cube names,
  16 валов, rotor sweep clearance, footprint coverage 65–75%.
- Dedicated-server smoke: отдельный временный мир, localhost:25586, успешно
  дошёл до `Done`, получил `stop` и сохранил все измерения. Client render classes
  не загружались согласно JVM class-load log. Логи остались в
  `build/mine-visual-smoke/`; server и RCON штатно остановлены.
- Превью проверены по экспортированной геометрии. Это не in-game visual review.

## Что остаётся проверить вручную

Клиентское подключение к dedicated server, все состояния анимации и индикации,
скрытие/возврат моделей при разборке, частичной выгрузке и reload, реальные
EE-провода, взаимодействие с интерфейсами и сохранёнными collision, 2–3 шахты,
guide/Ponder, F3+T, distance culling и частицы. Полный список в README.md.

Оставшиеся риски: обычная задержка terrain remesh после invalidation; physical
collision по-прежнему является исходным multiblock 10×3×10 и может отличаться
от промежутков в skin; альтернативные terrain renderers не проверены. Эти
ограничения не скрыты за утверждением о пройденном игровом checklist.
