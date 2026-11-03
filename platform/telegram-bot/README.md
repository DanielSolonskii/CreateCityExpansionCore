# Create Server Telegram bot

Актуализировано 02.11.2026 по CCEC snapshot. Java 21, Gradle wrapper 9.2.1,
Telegram Bot API long polling, Jackson, OkHttp 4.12.0. Прямого PostgreSQL/JPA/Redis
доступа нет: accounts, permissions и delivery outbox принадлежат backend.
[STATE](../STATE.md) и [аудит](../../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md)
не подтверждают live Telegram/VDS или standalone CreateServerPlatform.

## Реализация

- Private `/start`, `/help`, `/code`, inline menu-message navigation.
- Registration prepare → explicit confirmation button → accept; sender —
  настоящий numeric Telegram ID, username — nullable metadata.
- Trusted-device confirmation, account/server/admin views, reports, telemetry
  analytics, создание/подтверждение broadcasts.
- Background broadcasts/community/companion delivery и media cache;
  optional Mini App menu button.

Источники: [BotMain](src/main/java/dev/createserver/bot/BotMain.java),
[BotHandler](src/main/java/dev/createserver/bot/BotHandler.java),
[BackendClient](src/main/java/dev/createserver/bot/BackendClient.java).
Старое утверждение «рассылки не реализованы» больше не описывает код.
Живые доставка/меню/права требуют отдельной runtime проверки.

## Сборка и запуск

Из `platform/telegram-bot`:

```powershell
.\gradlew.bat test build --console=plain
```

Linux/macOS: `./gradlew test build --console=plain`. JUnit использует локальные
fake Telegram/backend HTTP/TLS servers без настоящего token; проверяет menus,
callback isolation, verification, reports/broadcasts и Telegram-only proxy.
`build` создаёт `build/distributions`; `installDist` —
`build/install/create-server-telegram-bot` со scripts/dependencies.
Root CCEC build/CI эти tests не запускает.

Проверено 02.11.2026: `test build --offline --console=plain` с доступным local
Gradle cache завершилось успешно, 67 tests, 0 failures/errors/skips. Это
подтверждение локальных tests/build, не живой Telegram delivery.

После безопасной передачи environment, только для отдельного development bot:

```powershell
$env:BACKEND_URL = 'http://127.0.0.1:8080'
$env:TELEGRAM_BOT_USERNAME = 'your_development_bot'
# TELEGRAM_BOT_TOKEN и BOT_BACKEND_SECRET передайте защищённым способом.
.\gradlew.bat run
```

`run` обращается к Telegram и может отправлять уведомления; обычная проверка —
`test`, без production credentials. Java не читает `.env` автоматически.
Нужен BotFather bot без активного webhook: webhook/getUpdates несовместимы.
Запускайте один polling worker на token; offset хранится в памяти.

## Конфигурация

| Переменная | Назначение |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Обязательный secret, без default |
| `TELEGRAM_BOT_USERNAME` | Имя без `@`, одинаковое с backend |
| `BOT_BACKEND_SECRET` | Общий с backend random secret 32+ символа |
| `BACKEND_URL` | Default `http://127.0.0.1:8080`; remote требует HTTPS |
| `LAUNCHER_DOWNLOAD_URL` | Optional HTTPS без credentials/fragment; без него меню честно сообщает об отсутствии ссылки |
| `REPORT_ADMIN_TELEGRAM_IDS` | До 20 numeric IDs через запятую, согласованных с backend; пусто — без admin |
| `MINI_APP_URL` | Optional HTTPS без credentials/query/fragment; вызывает `setChatMenuButton` |
| `COMPANION_MEDIA_DIR` | Optional media cache directory |

Не передавайте service secret launcher и не логируйте raw updates, token-bearing
URLs, proxy passwords, exception messages с endpoint. Backend повторно проверяет
права независимо от видимых admin buttons.

## Telegram-only proxy / routing

HTTP CONNECT: `TELEGRAM_PROXY_TYPE=HTTP`, `TELEGRAM_PROXY_HOST`,
`TELEGRAM_PROXY_PORT`, optional пара `TELEGRAM_PROXY_USERNAME` и
`TELEGRAM_PROXY_PASSWORD`. Host без scheme/path/credentials; port 1–65535.
Пустой набор — direct Telegram; partial/invalid config блокирует startup без
fallback. SOCKS5 не поддерживается.

Backend использует отдельный JDK HttpClient с DIRECT selector. Не заменяйте
это JVM/global proxy. TLS/hostname validation включена. Basic proxy credentials
требуют доверенного защищённого пути до proxy; не храните их в argv/Git/unit.
WireGuard/systemd [samples](../infra/README.md) — отдельная конфигурация,
не доказательство текущего routing или Telegram runtime.

Исторические контракты/runbooks: [menu UX](../docs/telegram-menu-message-ux.md),
[broadcasts](../docs/telegram-broadcasts.md),
[bridge](../docs/server-bridge-telemetry.md), [WireGuard](../docs/telegram-wireguard.md).
