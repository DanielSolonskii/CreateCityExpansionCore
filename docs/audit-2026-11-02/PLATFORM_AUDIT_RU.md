# Аудит вложенной платформы CCEC — 02.11.2026

## Область и provenance

Проверена только вложенная [platform/](../../platform/README.md) в
CreateCityExpansionCore. Исходный HEAD: `main`,
`ff4959ec8267291c36dc245357c27fba28cf8216`. Соседний standalone
CreateServerPlatform не читался и не изменялся. Production SSH, Telegram,
DNS/TLS endpoints, базы/миры, installed artifacts и deployment не проверялись.
Локальные fake HTTP servers, используемые test suites, не являются production.

GitNexus явно привязан к `CreateCityExpansionCore`: `list_repos` показал индекс
от `2026-11-02T19:44:05.276Z` на указанном HEAD, 1496 files / 22144 nodes /
72115 edges / 795 processes. Выполнены query по platform/game/bot flows и context
`GameSessionService` с exact file path. Перед README edits выполнены upstream
file impacts: `source-readme.md` — LOW, один resolved dependent; остальные
README — UNKNOWN, ноль resolved callers. UNKNOWN не трактовался как safety:
ссылки/контракты дополнительно проверены текстом. Production code не изменялся.
Затем после обновления PDG-индекса от `2026-11-02T20:00:53.267Z`
(90055 nodes / 235414 edges / 795 processes) выполнены exact-UID upstream
impacts трёх backend test methods перед исправлением schema assertions.
У всех UNKNOWN/0 static callers: source `@Test`, Gradle `useJUnitPlatform` и
реальные XML receipts подтверждают JUnit invocation; identity contract
наследуют H2 и tagged PostgreSQL test classes. Неоднозначные historical
copies исключены exact UID. Graph change gate перед commit выполняет основной
аудит репозитория после финального index refresh.

## Источники истины

| Предмет | Проверенный источник |
| --- | --- |
| Java/backend versions и test tasks | [backend/build.gradle](../../platform/backend/build.gradle), [wrapper](../../platform/backend/gradle/wrapper/gradle-wrapper.properties) |
| Backend environment/defaults | [application.yaml](../../platform/backend/src/main/resources/application.yaml), [GameSettings](../../platform/backend/src/main/java/dev/createserver/backend/game/GameSettings.java), [ReportSettings](../../platform/backend/src/main/java/dev/createserver/backend/report/ReportSettings.java), [TelegramProof](../../platform/backend/src/main/java/dev/createserver/backend/miniapp/TelegramProof.java) |
| Schema | [Flyway migrations](../../platform/backend/src/main/resources/db/migration/), V1–V14; V14 обновляет marker до 14 |
| Bot versions/config/worker behavior | [build.gradle](../../platform/telegram-bot/build.gradle), [BotMain](../../platform/telegram-bot/src/main/java/dev/createserver/bot/BotMain.java), [MiniAppMenu](../../platform/telegram-bot/src/main/java/dev/createserver/bot/MiniAppMenu.java) |
| Launcher versions/build/runtime | [package.json](../../platform/launcher/package.json), [Cargo.toml](../../platform/launcher/src-tauri/Cargo.toml), [tauri.conf.json](../../platform/launcher/src-tauri/tauri.conf.json), [build.rs](../../platform/launcher/src-tauri/build.rs), [lib.rs](../../platform/launcher/src-tauri/src/lib.rs) |
| Public proxy, services, local DB | [Caddyfile](../../platform/infra/caddy/Caddyfile), [systemd](../../platform/infra/systemd/), [compose](../../platform/infra/compose.yaml) |
| CI coverage | [.github/workflows/build.yml](../../.github/workflows/build.yml), root-only `./gradlew build` |

Backend имеет accounts/alpha keys/verification/devices, Game Session,
reports/bridge/telemetry, broadcasts/news, companion/Mini App/community. Bot
имеет verification/admin/broadcast/community/companion workers. Launcher имеет
реальные native adapters, но также display bootstrap mock. Ни одна из этих
source capabilities автоматически не подтверждает live production readiness.

## Локальные проверки

Окружение: Windows, Java 21.0.12, Node 24.21.0/npm 11.19.0, Cargo 1.99.0,
Python 3.14. Locked frontend toolchain: Vitest 3.2.7 и Vite 6.4.3.
Java wrapper 9.2.1 использовал существующий writable repo-local `.gradle-user`:

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user'
$backendTestTemp = Join-Path $PWD 'build/test-tmp'
New-Item -ItemType Directory -Force -Path $backendTestTemp | Out-Null
$env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir="' + $backendTestTemp + '"'
Push-Location platform/backend
.\gradlew.bat --offline test build --console=plain
Pop-Location
Push-Location platform/telegram-bot
.\gradlew.bat --offline test build --console=plain
Pop-Location
```

Первый запуск с default `C:\.gradle` не дошёл до tests из-за невозможности
создать wrapper lock. После выбора repo-local cache wrapper работал offline;
никакая эскалация/загрузка dependencies не потребовалась.

| Проверка | Наблюдавшийся результат |
| --- | --- |
| Backend, первый `--offline test build --console=plain` | FAIL, 153 tests / 3 failures; stale schema assertions и external temp cleanup |
| Backend, финальный `--offline test build --console=plain` с workspace temp после test-only fix | PASS, 153 tests / 0 failures/errors/skips; compileTestJava, bootJar, check/build завершились успешно, 1m31s |
| Bot `--offline test build --console=plain` | PASS, 67 tests / 0 failures/errors/skips; application archives собраны |
| Launcher `node --test scripts/release-workflow.checks.mjs` | PASS, 6/6, без node_modules |
| Launcher `npm ci --no-audit --no-fund` с workspace cache | PASS, 160 packages; package.json/package-lock.json не менялись |
| Launcher `npm test` | PASS, 14 test files / 72 tests, Vitest 3.2.7 |
| Launcher `npm run build` | PASS, TypeScript и Vite 6.4.3 production bundle, 1633 transformed modules |
| Launcher `cargo test --manifest-path src-tauri/Cargo.toml --locked --offline` | FAIL при compile, E0583/E0433; native unit tests не выполнились |
| `python -B -m unittest discover -s scripts -p test_release_workflow.py` из launcher | OK, 19 discovered: 11 passed, 8 skipped на Windows; OpenSSL из Git в PATH |
| `python -B -m unittest discover -s platform/infra/launcher -p test_publish_launcher.py` из root | Не импортируется на Windows: `ModuleNotFoundError: fcntl`; нужен Linux |
| NSIS native packaging | Не запускался; отсутствующие Rust modules/command остаются blocker |
| Backend `postgresTest`, Compose/services, GUI/Minecraft/Telegram acceptance | Не запускались |

Frontend dependencies первоначально отсутствовали. После разрешённой locked
установки выполнены обе frontend проверки:

```powershell
Push-Location platform/launcher
npm ci --no-audit --no-fund --cache ../../.npm-cache
npm test
npm run build
Pop-Location
```

Sandbox registry fetch вернул EACCES; та же команда с разрешённым network
доступом завершилась успешно. Запись ограничена local node_modules/workspace
cache/build outputs; `git diff --exit-code -- platform/launcher/package.json
platform/launcher/package-lock.json` подтвердил неизменность package files.
Npm installation/frontend tests не обращались к production сервисам.
Созданный audit cache после проверки перенесён из `.npm-cache` в ignored
`build/npm-cache-audit-2026-11-02`; node_modules/dist также ignored.

XML receipts: `platform/backend/build/test-results/test/TEST-*.xml`,
`platform/telegram-bot/build/test-results/test/TEST-*.xml`; HTML:
`platform/*/build/reports/tests/test/index.html`. Это локальные generated
артефакты, не обещание, что они включены в GitHub checkout.

## Выявленные проблемы

### Native launcher snapshot неполон

[lib.rs](../../platform/launcher/src-tauri/src/lib.rs) строки 38–40 объявляют
`social_links`, `presence`, `presence_ipc`; файлов `*.rs`/`mod.rs` нет на диске
или в `git ls-files`. Строки 27–33 регистрируют эти commands и
`settings::launcher_memory`, отсутствующую в
[settings.rs](../../platform/launcher/src-tauri/src/settings.rs).
Cargo эмпирически выдал 11 diagnostics для lib/lib test и exit 1. Не следует
урезать features ради зелёной документации: нужен согласованный полный source.
Во время Cargo Tauri обновил generated schemas; содержательный `git diff`
для них пустой, только EOL/stat effects. Launcher source не менялся.

### Backend schema expectations: найдено и исправлено

[InfrastructureIntegrationTest](../../platform/backend/src/test/java/dev/createserver/backend/InfrastructureIntegrationTest.java)
строка 31 и [IdentityDatabaseContract](../../platform/backend/src/test/java/dev/createserver/backend/identity/IdentityDatabaseContract.java)
строки 40–43 ожидали 11, тогда как Flyway/marker после V14 равны 14.
Оба H2 failures показали `expected: "11", but was: "14"`.
[PostgresIntegrationTest](../../platform/backend/src/test/java/dev/createserver/backend/PostgresIntegrationTest.java)
строка 20 тоже ожидала 11 (не исполнялся). Это stale test expectations,
а не доказательство падения startup migrations. В трёх test files исправлены
четыре литерала 11→14, сверенные с V14 и фактическим Flyway result.
Тесты/проверки не удалялись и не ослаблялись; production migrations не менялись.

Третья ошибка — [CompanionMediaTest](../../platform/backend/src/test/java/dev/createserver/backend/miniapp/CompanionMediaTest.java):
JUnit `Failed to close extension context`, cause `AccessDeniedException` при
удалении TempDir на Windows. Сами media assertions не указаны как failure.
Stack указывает на `WindowsPath.toRealPath` для JUnit temp root вне workspace.
Media source использует закрывающие handles `Files.readAllBytes`/`Files.write`;
конкретного открытого stream не обнаружено. Повтор только CompanionMediaTest с
workspace `java.io.tmpdir` прошёл без code changes, без отключения cleanup или
ослабления assertions. Полный повторный `test build` с тем же workspace temp
также прошёл: 153 tests, 0 failures/errors/skips. Команда приведена выше.

### Public proxy snapshot отстаёт от API

[Caddyfile](../../platform/infra/caddy/Caddyfile) содержит allowlisted account,
device/game/status routes, но не launcher news, registration restart и Mini App.
News вызывается [news gateway](../../platform/launcher/src/services/newsGateway.ts)
и [native adapter](../../platform/launcher/src-tauri/src/news.rs), реализован
[controller](../../platform/backend/src/main/java/dev/createserver/backend/api/LauncherNewsController.java).
При использовании именно этого proxy файла новые routes попадут в catch-all
404. Установленный live proxy не читался, поэтому вывод ограничен snapshot.

### CI и эксплуатационные ограничения

Root workflow не запускает отдельные platform Java/npm/Cargo suites.
`platform/.env.example` содержит устаревшие placeholders/variables;
README теперь направляет к actual config classes и не предлагает его как
полный production config. Bot offset хранится в памяти, polling singleton;
runtime credentials/permissions/delivery требуют отдельного acceptance.
Java H2, Node helper tests и fake systemd не проверяют PostgreSQL, live save,
Minecraft physical state, TLS game join или реальный Telegram transport.

## Изменённая документация

Полностью актуализированы README платформы, backend, launcher, source GUI,
bot, infra; добавлен [platform/STATE.md](../../platform/STATE.md).
Удалены противоречивые milestone claims из текущих входных документов,
исправлены repo-relative пути/команды, добавлены prerequisites/config/test
boundaries, отделены snapshot от live/standalone. Исторические deployment
документы сохранены как история, а не выданы за сегодняшние live receipts.

Не выполнялись staging/commit/push, production SSH, service start/restart,
deployment, migrations на внешней БД, release publication или изменение
secrets/access. Из Java source изменены только четыре schema assertions в трёх
test files. Остальные production code/config defects остаются отдельной задачей.
