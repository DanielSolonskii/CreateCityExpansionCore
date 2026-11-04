# Аудит вспомогательных инструментов, моделей и архивных STATE — 02.11.2026

**Исторический отчёт; применимость уточнена 2026-11-04.** Анализ Freight/Railway и JAR 1.0.2.14 ниже относится к прежней ветке и не описывает действующую Cargo-систему. Сохранены исходные результаты проверок. [Актуальная Cargo-база, интеграция и ограничения](../audit-2026-11-04/README.md).

Проверен рабочий checkout CCEC, исходная база `ff4959ec8267291c36dc245357c27fba28cf8216`
на `main`. Результаты относятся к локальным файлам на момент аудита. Полный
статус проекта и общие проверки — [README](README.md),
[CURRENT_STATE](../../CURRENT_STATE.md). Изменения этого раздела — документация;
исходники Java/Python/PowerShell, JSON-модели и сохранённые исторические receipts
не исправлялись. Игровой клиент и production helpers не запускались.

## Охват README и STATE

| Обновлённый файл | Что сверено и уточнено |
| --- | --- |
| [art/industrial_mine/README.md](../../art/industrial_mine/README.md) | Генератор, 539 кубов, 10 runtime-частей, scale=8, renderer и освещение, действующая Gradle-проверка; старый server smoke отделён от текущего аудита |
| [art/city_pylon/README_RU.md](../../art/city_pylon/README_RU.md) | Существующие BlockEntity/BER и facing, 90 кубов, 15 JSON-моделей, 13 additional models, фактическая привязка Active/GUI; reference-состояния отделены от runtime |
| [tools/ftb-campaign/README_RU.md](../../tools/ftb-campaign/README_RU.md) | Builder/verifier, зависимости, output, baseline ID, ограничения parser/preview/исторического installer и датировка deployment |
| [tools/yuushya-quiet/README.md](../../tools/yuushya-quiet/README.md) | Pin Yuushya 2.3.0, JDK/cache prerequisites, artifact path, отдельный callback test и исторический deployment receipt |
| [tools/community-platform/README.md](../../tools/community-platform/README.md) | Архивный source-пакет, маппинг четырёх файлов в platform, отсутствие standalone build, schema-12 ограничение старого deploy helper |
| [tools/menu-platform/telegram-bot/README.md](../../tools/menu-platform/telegram-bot/README.md) | Архивный milestone-3 packet без wrapper/build files, drift относительно platform, рабочие команды и исправленные ссылки |
| [tools/telemetry-fix/README.md](../../tools/telemetry-fix/README.md) | Исторический incident/deployment, текущий source rejection path, hash/path/version pins старых patch/verify/deploy helpers |
| [devtools/README.md](../../src/main/java/com/example/examplemod/devtools/README.md) | `/ccec recipes`, singleplayer-owner guard, путь datapack, init-script tasks, граница headless/GUI и процедура отдельного удаления инструмента |
| [audit-2026-10-14/README.md](../audit-2026-10-14/README.md) | Архивный статус, дата прав доступа/ветки, ссылки на текущие документы |
| [audit-2026-10-14/STATE_RU.md](../audit-2026-10-14/STATE_RU.md) | Сохранён старый срез с явной датой и текущей навигацией; receipts не переизданы как новые |
| [STATE-2026-10-25.md](../STATE-2026-10-25.md) | Архивный статус, поправка о существующей admission/game-session реализации, различие версии JAR и формата SavedData |

Всего обновлено **11 существующих README/STATE**, создан этот отчёт.

## Проверки, выполненные в этом аудите

| Проверка | Результат и граница |
| --- | --- |
| Python AST | Прочитаны и разобраны 10 файлов: оба art generator, пять FTB Python-модулей, Community deploy helper и два telemetry Python helpers; без исполнения deployment кода |
| Blockbench / UV | Уникальные UUID, положительные размеры cuboids, UV в 0…64; City Pylon — 90 элементов, Industrial Mine — 539 |
| PNG header / runtime JSON | Три atlas PNG имеют заголовок 64×64; 15 pylon и 10 mine JSON читаются, координаты элементов в −16…32 |
| Pylon export ↔ mod resources | Все 17 файлов `art/city_pylon/runtime/assets/createcityexpansioncore/` имеют существующую и побайтово равную копию в `src/main/resources/assets/createcityexpansioncore/` |
| FTB builder | 128 quests / 6 chapters: 31 Survival, 68 Create, 5 Geology, 6 City, 14 Mine, 4 Chunks; 52 manual tasks |
| FTB verifier | 47 main / 81 optional, 450 translation keys; unique/stable IDs, main progression, optional isolation, no consumption/rewards, RU/EN parity, coordinates и `REDESIGN_OK` прошли |
| Community review copies | Все четыре mapped Java/test/migration файла побайтово совпали с соответствующими `platform/` файлами |
| Menu bot review copies | Из 14 Java source/test файлов 8 совпадают с `platform/telegram-bot`, 6 различаются; новые компоненты отсутствуют в старом packet |
| Yuushya callback | Исходники и `VerifyQuiet` скомпилированы Java 21; static callback отменяет `CallbackInfo`, вывод `PASS: isolated join-disclaimer callback cancels` |
| Markdown | Выполнен `git diff --check` для всех 11 файлов; относительные ссылки сверены с деревом, ссылки на новый аудит зависят от создания общего README этим же обновлением |

Builder/verifier FTB записали только игнорируемые локальные результаты:

- `output/ftb-campaign/quests/` — 10 SNBT-файлов;
- `output/ftb-campaign/manifest.json`, `quest-catalog.json`;
- `output/ftb-campaign/structural-validation.json`, `CATALOG_RU_EN.md`.

Art generators не запускались: ручные исходники и действующие ресурсы не
перегенерировались. Проверка PNG выше читает заголовок; это не визуальный просмотр
изображения или декодирование всех его пикселей. Старые `validation.json` и
`asset_validation.json` сохранены как исходные отчёты.

`tools/yuushya-quiet/build.ps1` создал игнорируемый
`tools/yuushya-quiet/build/recreated-yuushya-quiet-1.0.0.jar`, но первый вызов
Java compiler сообщил Windows `AccessDeniedException` для Gradle-cache JAR
при закрытии zip filesystem. Его exit code и наличие JAR не трактовались как
чистый прогон. Повторная локальная компиляция с копиями тех же двух dependency
JAR в `tools/yuushya-quiet/build/audit-e23cc64394b4410c984a8b98839795fa/`
и отдельный callback test завершились без этой ошибки. Остались два warning
`unknown enum constant Dist.CLIENT/Dist.DEDICATED_SERVER`: узкий classpath не
включает distmarker. Совместимость Mixin injection требует настоящего старта
с Yuushya; он здесь не выполнялся.

На машине аудита Windows App Execution Alias `python` не запускался. Для
проверок использован установленный Python 3.14 напрямую. В нём нет Pillow:
art/preview генераторы не проверялись исполнением; AST и структурные проверки
использовали стандартную библиотеку. Это ограничение окружения, а не изменение
требований генераторов (Pillow + numpy).

## Найденные расхождения и открытые проверки

1. **README пилона отставал от кода.** Он утверждал, что нет BlockEntity/renderer
   и блок остаётся железным кубом. В текущем `CityPylonBlock` есть BaseEntityBlock,
   `CityPylonRenderer` зарегистрирован, blockstate имеет четыре facing. README
   теперь отражает интеграцию. Отрицание renderer в сохранённом
   `art/city_pylon/validation.json` относится к первоначальному art-этапу.
2. **Художественная спецификация не равна реализации событий.** У пилона
   синхронизируется `Active`, GUI определяется клиентом, красная лампа всегда
   выключена. WARNING/DATA UPDATE/LEVEL UP из reference-таблицы не привязаны
   к реальным событиям. Наличие констант скорости не подтверждает игровой тайминг.
3. **Есть повод отдельно проверить animation/collision пилона.** В
   [renderer](../../src/main/java/com/example/examplemod/client/render/CityPylonRenderer.java)
   `advanceRingPhase`/`advanceGearPhase` вызываются при render с множителем
   `partialTicks`, а не с измеренным временем кадра; это основание для проверки
   зависимости скорости от FPS. В
   [block](../../src/main/java/com/example/examplemod/block/CityPylonBlock.java)
   collision/selection возвращают один `SHAPE` для всех facing. Это результаты
   source review; воспроизведения в Minecraft и исправления кода не проводились.
4. **FTB verifier не полностью воспроизводим из чистого клона.** Он требует
   `output/ftb-campaign/manifest-before-redesign.json`, который игнорируется Git
   и не создаётся builder. В текущем окружении baseline был доступен. Без
   проверенного исторического manifest нельзя заявлять успешное сравнение ID.
5. **FTB live и local output не сверены.** Сохранённый startup log установки
   11.10 сообщает 127 quests, текущий output — 128. Это не доказательство
   текущего live-дефекта; нужна отдельная сверка definitions и server log.
6. **Исторические platform helpers имеют жёсткие предпосылки.** Community deploy
   ожидает migration V12. Telemetry patch/verify helpers указывают на старый
   sibling checkout, 1.0.1 JAR и старые alpha-версии. Они сохранены для истории,
   текущая сборка платформы выполняется в `platform/`.
7. **Полная визуальная приёмка остаётся открытой.** Реальный клиент, EE wires,
   facing/collision пилона, brightness/timing, mine clipping/visibility при
   unload/reconnect, прохождение FTB solo/team и chat после reconnect не проверены
   этими локальными тестами.

## GitNexus перед изменением документации

Использован repo `CreateCityExpansionCore`, индекс от
`2026-11-02T19:44:05.276Z`, commit `ff4959ec8267291c36dc245357c27fba28cf8216`.
Сначала выполнены concept queries, затем contexts для `CityPylonRenderer`,
`IndustrialMineRenderer`, `IndustrialMineModels`, `RecipeWorkbench`; после этого
прочитаны файлы. Текстовые сверки восполнили пустые concept results и проверили
буквальные пути/ID.

Перед правками выполнен upstream file impact для всех 11 существующих документов:

- `docs/audit-2026-10-14/README.md`: **LOW**, один direct caller — корневой README;
- `docs/audit-2026-10-14/STATE_RU.md`: **LOW**, direct callers — `CURRENT_STATE.md`,
  старые ONBOARDING/README и `platform/README.md`; корневой README — indirect;
- остальные девять: **UNKNOWN**, граф не разрешил callers. Это не «не используются»:
  выполнены текстовые сверки ссылок и source/resource проверки; менялась только
  документация, имена и пути сохранены;
- новый файл этого отчёта не был в индексе: impact вернул `Target not found`,
  риск **UNKNOWN**. На него добавлены две явные ссылки из art README.

Graph impact не перечислил execution processes для этих file targets. Это
ограничение документационного графа; окончательный общий `detect_changes` и
прогоны Gradle фиксируются в основном аудите перед commit.
