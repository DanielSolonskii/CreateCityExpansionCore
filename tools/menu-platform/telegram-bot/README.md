# Create Server Telegram bot — milestone 3

Java 21 / Gradle application, Telegram Bot API long polling. PostgreSQL, JPA
и Redis dependencies отсутствуют; аккаунтные действия выполняются через backend.
Реализованы /start, inline-меню, /help, /code и phone/deep-link verification.
Навигация, registration и device confirmation редактируют одно menu-message;
[текущий UX / admin / server bridge](../docs/telegram-menu-message-ux.md).

## Environment

- TELEGRAM_BOT_TOKEN: настоящий BotFather token, обязателен, без default.
- TELEGRAM_BOT_USERNAME: имя без @, одинаковое с backend.
- BOT_BACKEND_SECRET: одинаковый случайный secret минимум 32 characters в обоих
  приложениях; без default, не сохранять в Git.
- BACKEND_URL: http://127.0.0.1:8080; remote URL требует HTTPS.
- LAUNCHER_DOWNLOAD_URL: постоянный production HTTPS download URL без credentials
  и fragment. Default отсутствует. Без настройки Bot работает, но экран Launcher
  честно сообщает об отсутствии ссылки; не подставляет фиктивный адрес.

Bot должен быть создан через BotFather. У него не должно быть активного webhook:
getUpdates и webhook несовместимы. Webhook автоматически не меняется.
Запускайте один polling worker.

Из telegram-bot после задания environment variables:

```powershell
.\gradlew.bat test build
.\gradlew.bat run
```

Также создаётся application distribution в build/distributions.
В Linux/macOS используются ./gradlew test build и ./gradlew run.

## Verification

Launcher показывает локальный QR https://t.me/BOT_USERNAME?start=OPAQUE_TOKEN
и short code XXXXX-XXXXX. Телефон сканирует QR или отправляет /code с кодом.
Prepare показывает username и кнопку «Подтвердить регистрацию», не активируя account.
Только callback_query вызывает accept с отдельным bound confirmation ticket.
Bot обрабатывает private message от non-bot author, берёт настоящий numeric
from.id (64-bit long), username передаёт только как nullable metadata. Service
secret защищает bot/backend prepare/accept. Welcome не меняет аккаунты.
Allowed updates: message и callback_query. Group/bot/forwarded сообщения игнорируются.
Expiry/invalid/rate-limited codes получают safe reply, без бесконечного retry.
BackendClient использует защищённый lookup по numeric Telegram ID: platform UUID,
username и status. ACTIVE binding открывает меню аккаунта; другие состояния не
выдаются за ACTIVE. Ошибка lookup не трактуется как отсутствие binding.
Server bridge/telemetry v1 читает реальные status/counts и admin players/statistics
из Backend; [контракт](../docs/server-bridge-telemetry.md). Репорты доставляются
отдельными admin alerts. Release/restart subscriptions пока не реализованы.

При communication error bot повторяет polling через 3 секунды, offset
продвигается после обработки. Offset хранится в памяти; singleton worker.
Если confirmation завершилось, а ответ потерялся, повтор token даст invalid —
launcher polling остаётся источником статуса. Не логируются updates, secrets,
token-bearing Telegram URLs или exception details.

## Tests

JUnit использует local fake Telegram/backend HTTP servers: linked/unlinked/inactive
menus, повторный /start, navigation/edit, stale callback, missing/deleted message,
restart adoption, Backend status/transport failures, registration replay/conflicts,
numeric sender isolation, download URL validation и service secret header.
Proxy suite проверяет прямой Backend transport и Telegram-only CONNECT.
Живой Telegram token для tests не нужен.

## Telegram-only HTTP CONNECT proxy

Telegram HTTP transport использует OkHttp 4.12.0. BackendClient сохраняет JDK
HttpClient с explicit DIRECT selector; общий/system-wide proxy и global
Authenticator не устанавливаются. Proxy не применяется к backend даже при
наличии JVM/system proxy defaults. Registration/verification protocol не меняется.

Environment (все параметры только для Telegram):

- `TELEGRAM_PROXY_TYPE=HTTP` — HTTP CONNECT для исходящего Telegram HTTPS.
- `TELEGRAM_PROXY_HOST` — hostname/IP без scheme, path и credentials.
- `TELEGRAM_PROXY_PORT` — integer 1–65535.
- `TELEGRAM_PROXY_USERNAME` и `TELEGRAM_PROXY_PASSWORD` — optional pair;
  либо оба заданы, либо оба отсутствуют. Password — secret.

Если все пять переменных отсутствуют/пусты, Telegram работает напрямую.
Частичная/невалидная configuration останавливает startup с redacted error,
без silent fallback на прямое соединение. SOCKS5 не поддерживается этой версией.
Не использовать JVM http.proxyHost, HTTPS_PROXY или global network changes.

Для authenticated proxy credentials отправляются только в CONNECT к настроенному
proxy. Они не отправляются Telegram origin или backend, retries после failed auth
ограничены. HTTPS certificate/hostname validation остаётся включённой; redirects
не следуются, bodies/URLs/exception details не логируются.

HTTP proxy transport до proxy не шифрует Basic credentials: используйте доверенный
proxy и защищённый/приватный путь до него. Telegram token/запросы остаются внутри
проверенного TLS tunnel; TLS verification не отключайте. Не записывать proxy
password в Git, README, unit, argv или chat. На VDS использовать root-owned
0600 `/opt/create-server/secrets/telegram-bot.env` и EnvironmentFile.

Реализация CONNECT/proxyAuthenticator проверена по
[исходникам OkHttp 4.12.0](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/Authenticator.kt).

Tests: config validation/redaction, no-proxy default, hostile global proxy bypass,
реальный локальный HTTPS CONNECT с fake CA (anonymous/authenticated), secret
isolation, bounded wrong-auth failure. Backend protocol tests сохранены. Команда:
`gradlew test build`. Реальный proxy/Telegram остаются отдельным runtime acceptance.
