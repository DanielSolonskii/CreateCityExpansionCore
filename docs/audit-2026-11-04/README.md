# Актуальный аудит CCEC — 2026-11-04

Область: текущий репозиторий CCEC со всеми вложенными проектами. Целевая версия **1.0.2.15**; установка на сервер и публикация Windows/macOS пока требуют финальной квитанции. Соседний самостоятельный CreateServerPlatform не входит в аудит.

## Главный результат сверки

Прежний аудит 2–3 ноября выполнялся на устаревшей Freight-базе main. На сервере уже работали Cargo и Economy 15. Свежесть Git-ветки и startup `Done` не доказали совместимость мира; первый кандидат 1.0.2.14 был отозван, экономический файл и прежний JAR восстановлены. Клиентские каналы не переключались. [Полный incident receipt](../releases/2026-11-03-compatibility-incident.md).

Подтверждённая production source-база найдена в сохранённом index snapshot `0839bbb1fd43fbb3aca217478f37c5b5f28c73c1`. Неизменённая сборка прошла `assemble check` (62 задачи); **751/751 production Java-классов побайтово идентичны** установленному JAR. ZIP/JAR целиком не обязан иметь тот же хеш из-за упаковки и ресурсов. Stash и общая main не очищались.

Интеграция возвращает действующие Cargo registry IDs и сохраняет новые общие улучшения карты, территории/доступа, рецептов и интерфейсов. Требуемая отмена переносится в CargoCommerce/Shipment Registry. Старые Freight/Railway и металлургические мультиблоки не возрождаются.

## Карта проектов

| Область | Реализация и вывод |
| --- | --- |
| Мод / Cargo | [CURRENT_STATE](../../CURRENT_STATE.md), [Cargo](../../src/main/java/com/example/examplemod/cargo), [центр](../../src/main/java/com/example/examplemod/cargo/center). Физическая приёмка и денежные квитанции — серверные |
| Карта / GUI | [client/map](../../src/main/java/com/example/examplemod/client/map), [client/logistics](../../src/main/java/com/example/examplemod/client/logistics). Общая WorldMap и отдельный Cargo overlay, headless проверки не равны OpenGL |
| Backend | [README](../../platform/backend/README.md): Spring Boot4, account/game-session/bridge/community, Flyway V1–V14; 153 tests PASS в выполненном platform-прогоне |
| Bot | [README](../../platform/telegram-bot/README.md): 67 tests PASS; живой Telegram не проверялся |
| Launcher | [README](../../platform/launcher/README.md): 72 frontend tests и TS/Vite build PASS; native Rust compile заблокирован отсутствующими модулями |
| Infra / release | [STATE](../../platform/STATE.md), [server-release.py](../../platform/launcher/scripts/server-release.py): explicit backup/stop/deploy/readiness, новый save compatibility preflight |
| Art / tools | [предыдущий структурный отчёт](../audit-2026-11-02/TOOLS_ART_AUDIT_RU.md). Модели не перегенерировались; FTB и старые repair packets требуют проверки применимости к Cargo |

## Контракты и защита выпуска

Целевые схемы: **Economy 16, CargoCommerce 2, Cargo shipments 2**, остальные Cargo SavedData — 1. Production Economy 15 и старые Cargo версии должны читаться миграцией. Непрозрачные прежние экономические теги сохраняются.

Кандидатный JAR содержит `META-INF/ccec-save-formats.json`. Release helper сверяет hash/size реального объекта, читает ограниченные gzip NBT заголовки существующего мира и отказывает при неизвестном/неподдерживаемом формате, отсутствующей metadata или повреждённых данных **до остановки**. После штатного final save проверка повторяется до замены файлов. Files-only rollback также проверяет диапазоны: старый JAR нельзя запускать на новых данных автоматически.

12 новых preflight regression tests PASS: повторение сбоя Economy15→13, отсутствие metadata, вложенный CargoCommerce, будущий Shipment, неизвестный Cargo файл, повреждённый NBT, hash mismatch, CCEC removal, совместимый выпуск, post-stop recheck и недопустимый downgrade rollback. Существующие Python release tests — 11 PASS / 8 Linux-only skips на Windows; Node syntax check PASS. Это disposable worlds/FakeSystemd, без live деплоя.

## Проверки интеграции и выпуск

Финальная полная сборка, tests, хеш кандидатного JAR, Git commit и server/channel receipts добавляются после завершения интеграции. Предыдущие 72 задачи и JAR 1.0.2.14 в [старой квитанции](../audit-2026-11-02/VERIFICATION.json) являются историей и не подтверждают новую Cargo-сборку.

Документация: все README/STATE получают текущую навигацию; датированные срезы сохраняют прежние факты с явной пометкой истории. Portable checker `node tools/audit-docs.mjs` проверяет относительные ссылки и хеши; его manifest остаётся по [исторически закреплённому пути](../audit-2026-11-02/DOCS_COVERAGE.json), дата manifest должна соответствовать финальному прогону.

## Открытые вопросы

1. Проверить реальный Create цикл: однонаправленная дорога, отмена до/во время native упаковки, коробки на каждом плече, физическое изъятие, полный инвентарь, повтор запроса, restart/unload и идемпотентный расчёт.
2. Выполнить GUI/OpenGL проверку карты, анимаций, разных GUI scales, длинных подписей и запоздалых ответов. Headless layout tests не подтверждают вид на экране.
3. Native launcher: восстановить согласованные `social_links`, `presence`, `presence_ipc`, `settings::launcher_memory`; затем Cargo/NSIS/UI acceptance. Игровые профили обновляются существующим signed release pipeline отдельно от installer.
4. Platform: PostgreSQL integration, фактический Caddy allowlist/TLS и живые registration/Telegram/game-session потоки не следуют из H2/frontend PASS.
5. Историческая FTB campaign структурно проверена, но её предметы/инструкции нельзя объявлять актуальными для Cargo без сверки с финальным JAR. Старые installers не запускать автоматически.
6. Полное fleet balancing, offline Create simulation, traction/passenger tariffs и полный прогноз сигналов не заявлены готовыми.

[GitHub README](../../README.md) · [STATE](../../CURRENT_STATE.md) · [разработка](../../README_DEV.md) · [игроку](../../GUIDE_RU.md) · [платформа](../../platform/STATE.md)

## Граница выпуска по запросу владельца

Дальнейший аудит остановлен по явному запросу пользователя: выпуск текущей согласованной версии без новых доработок. `assemble` PASS; целевые Cargo/native Economy и карта PASS. Первый общий `assemble check` остановился на устаревшем требовании непрозрачности terrain; требование скорректировано для alpha-перехода, целевые проверки карты повторно PASS. Полный общий прогон после последнего изменения не заявлен.

Открытый риск: подтверждение native происхождения коробки не завершено; восстановление зарегистрированной коробки пока не защищено полностью от подмены. Коробка без зарегистрированного Parcel UUID не принимается. Игровая приёмка, модераторский сценарий изъятия и графическая проверка остаются невыполненными.
