# Create Server backend

Актуализировано 02.11.2026: Java 21, Spring Boot 4.0.8, Gradle wrapper 9.2.1,
Spring Data JPA/JDBC, PostgreSQL и Flyway V1–V14. Artifact version —
`0.1.0-SNAPSHOT`, не номер последнего deployment. Это вложенный snapshot CCEC:
[границы](../README.md), [STATE](../STATE.md),
[аудит](../../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md).

## Реализация

| Область | Исходники / контракт |
| --- | --- |
| Health/version, identity read | [api](src/main/java/dev/createserver/backend/api/), [identity](src/main/java/dev/createserver/backend/identity/) |
| Alpha keys, registration, Telegram verification, trusted devices | [account](src/main/java/dev/createserver/backend/account/), [API](../docs/account-api.md) |
| Game Session capability/ticket/boot/consume | [game](src/main/java/dev/createserver/backend/game/), [protocol](../docs/game-session-protocol.md) |
| Server bridge, telemetry v1/v2, launcher status/profile/experience telemetry | [bridge](src/main/java/dev/createserver/backend/bridge/), [launcher](src/main/java/dev/createserver/backend/launcher/) |
| Reports, admin broadcasts, launcher news | [report](src/main/java/dev/createserver/backend/report/), [broadcast](src/main/java/dev/createserver/backend/broadcast/) |
| Mini App/companion snapshots, community relay | [miniapp](src/main/java/dev/createserver/backend/miniapp/), [community](src/main/java/dev/createserver/backend/community/) |

Redis и прямое управление игровым миром в runtime отсутствуют. CCEC исполняет
игровые действия и передаёт snapshots. Endpoint в коде не доказывает его
публичную доступность или установленную версию на сервере.

## Сборка и тесты

Из `platform/backend`:

```powershell
.\gradlew.bat test build --console=plain
```

Linux/macOS: `./gradlew test build --console=plain`. `test` исключает tag
`postgres`; использует H2 и локальные HTTP fixtures, без настоящего Telegram.
`build` создаёт executable `build/libs/create-server-backend-0.1.0-SNAPSHOT.jar`
через `bootJar`; plain jar отключён. Root CCEC Gradle/CI эти tests не запускают.
Проверено 02.11.2026: после актуализации schema assertions 11→14 полный
offline `test build` прошёл, 153 tests без failures. Для ограниченного
Windows окружения использован workspace `java.io.tmpdir`; точные команды и
разбор первоначальных ошибок — в
[аудите](../../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md).

Реальный PostgreSQL проверяется отдельно; заранее создайте выделенную пустую
тестовую БД/роль и задайте connection environment:

```powershell
$env:POSTGRES_DB = 'create_server_test'
$env:POSTGRES_USER = 'create_server_test'
$dbCredential = Get-Credential -UserName $env:POSTGRES_USER -Message 'Пароль отдельной тестовой БД'
$env:POSTGRES_PASSWORD = $dbCredential.GetNetworkCredential().Password
.\gradlew.bat postgresTest --console=plain
Remove-Item Env:POSTGRES_PASSWORD
```

`postgresTest` применяет миграции и пишет fixtures; не направляйте его на
production/игровую БД. H2 tests не доказывают PostgreSQL acceptance.

## Локальный запуск

Нужны JDK 21 и PostgreSQL с заранее созданной отдельной database/login role.
Backend не создаёт саму БД/роль. Docker необязателен:
[dev compose](../infra/README.md) содержит PostgreSQL 17-alpine.
Другие PostgreSQL major versions в этом аудите не проверялись.

Из `platform/backend`, после настройки локальной БД:

```powershell
$env:POSTGRES_DB = 'create_server_dev'
$env:POSTGRES_USER = 'create_server_dev'
$dbCredential = Get-Credential -UserName $env:POSTGRES_USER -Message 'Пароль локальной БД'
$env:POSTGRES_PASSWORD = $dbCredential.GetNetworkCredential().Password
.\gradlew.bat bootRun
# После завершения процесса:
Remove-Item Env:POSTGRES_PASSWORD
```

После сборки также доступен `java -jar build/libs/create-server-backend-0.1.0-SNAPSHOT.jar`.
Startup применяет все pending Flyway migrations; Hibernate использует
`ddl-auto=validate`, Flyway clean отключён. Ошибка БД/миграции блокирует startup.

| Переменные | Default / назначение |
| --- | --- |
| `PLATFORM_BIND_ADDRESS`, `PLATFORM_API_PORT` | `127.0.0.1`, `8080` |
| `POSTGRES_HOST`, `POSTGRES_PORT` | `127.0.0.1`, `5432` |
| `POSTGRES_DB`, `POSTGRES_USER` | Оба `create_server`; для разработки задайте свои |
| `POSTGRES_PASSWORD` | Обязательный secret без default |
| `DB_POOL_SIZE`, `DB_CONNECTION_TIMEOUT_MS` | `5`, `3000` |
| `TELEGRAM_BOT_USERNAME`, `BOT_BACKEND_SECRET` | Имя без `@`, общий с bot secret 32+ символа; без них регистрация недоступна |
| `REGISTRATION_TTL_SECONDS` | `900`, диапазон 60–3600 |
| `GAME_SERVER_ID`, `CCEC_BACKEND_SECRET` | ID `[a-z0-9_-]{1,64}`, отдельный game service secret 32–512 символов |
| `REPORT_ADMIN_TELEGRAM_IDS` | До 20 positive numeric IDs через запятую; пусто — без admin |
| `MINI_APP_BOT_TOKEN` | Secret для проверки Telegram initData; без него Mini App proof недоступен |
| `COMPANION_MEDIA_DIR` | Optional локальный каталог companion media |

[application.yaml](src/main/resources/application.yaml) и settings classes —
источники истины. `.env.example` автоматически не читается. Secrets передаются
защищённым способом; не сохраняйте реальные значения в Git/чат/VITE.

## API и доверие

`GET /api/health` проверяет SQL marker: 200/UP либо 503/DOWN. `GET /api/version`
показывает build info. Это health backend/БД, не Minecraft. Для отдельно
запущенного локального backend:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/api/version
```

Регистрация требует alpha key и подтверждения numeric Telegram ID через bot.
Password hash использует BCrypt. Polling secret, trusted-device credential и
game capability имеют разные назначения; password login не выдаёт разрешение
на Minecraft-вход. Mini App initData — отдельное proof, не game credential.
Identity/internal bot API защищены service-secret guard; game bridge использует
отдельный CCEC secret. Подробнее: [account API](../docs/account-api.md),
[trusted devices](../docs/trusted-devices.md), [game session](../docs/game-session-4b.md).

Public edge должен публиковать необходимые routes через TLS, не весь backend.
[Caddy snapshot](../infra/caddy/Caddyfile) отстаёт от части новых API: news,
Mini App и registration restart требуют отдельной сверки. In-memory rate
limits сбрасываются при restart и не заменяют multi-instance защиту.
Не логируйте credentials/proof/raw updates/DB errors с содержимым строк.

CLI `--generate-alpha-keys=N` изменяет БД и однократно выводит plaintext keys:
это отдельная административная операция по [runbook](../docs/alpha-access-keys.md).
Наличие CLI/миграций не подтверждает их deployment сегодня.
