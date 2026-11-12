# Create Server Telegram bot

**Сверка состояния: 2026-11-04.** [Актуальный аудит CCEC/Cargo](../../docs/audit-2026-11-04/README.md) описывает интеграцию 1.0.2.15. Ранее датированные результаты проверок ниже сохраняют свою дату; новый игровой/production прогон ими не подтверждается.

Актуализировано 02.11.2026 по CCEC snapshot. Java 21, Gradle wrapper 9.2.1,
Telegram Bot API long polling, Jackson, OkHttp 4.12.0. Прямого PostgreSQL/JPA/Redis
доступа нет: accounts, permissions и delivery outbox принадлежат backend.
[STATE](../STATE.md) и [аудит](../../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md)
не подтверждают live Telegram/VDS или standalone CreateServerPlatform.

## Реализация

Второй этап реализован локально 11.11.2026 (ещё не выложен): «Мой профиль»,
«Мои города», «Обращения» внутри профиля и настоящие настройки уведомлений.
Источники — существующие CompanionService, server bridge, companion_profile,
trusted_device и player_report. Бот не хранит профили или настройки в кеше.
Города и обращения показываются по пять на страницу; состав города сокращается
с указанием числа оставшихся жителей. Время отображается с явным UTC+3.
Нет снимка / устаревший снимок / нет Minecraft-привязки отличаются от пустого списка.
Время в игре берётся из play_ticks последнего снимка, дата снимка видна в профиле;
отсутствующая статистика не заменяется нулём. Последняя сессия не называется
последним посещением: backend может знать только время начала незавершённой сессии.

Новые bot-only routes (X-Bot-Service-Secret, свежая проверка ACTIVE numeric owner,
Cache-Control: no-store):

- `GET /api/internal/telegram/companion/{telegramId}` — профиль и связанные данные.
- `GET /api/internal/telegram/companion/{telegramId}/preferences` — общие подписки.
- `POST /api/internal/telegram/companion/{telegramId}/preferences` —
  `{ "field": "news|maintenance|reports", "enabled": true|false }`.

Actor берётся из проверенного Telegram update, не из callback или тела запроса.
Запись одной настройки сериализована по той же строке platform_user, что и Mini App:
разные кнопки не затирают остальные каналы. Callback задаёт желаемое состояние,
поэтому повторное нажатие не переключает его обратно. При потере ответа бот
предлагает перечитать настройки и не утверждает, что изменение отклонено.
Старый callback notifications открывает настройки; старые меню остаются совместимы.
Настройки относятся к новым уведомлениям: ранее поставленные в outbox могут прийти.
Подтверждения входа и сообщения сообщества не относятся к этим трём подпискам.

Схема БД не меняется. Исправлено определение свежести городов: snapshot должен
принадлежать текущему boot сервера. Для rollout нужен Backend первым, затем Bot;
сохранить серверные доработки TelegramClient и BroadcastService, которых нет
полностью в текущем checkout. Первая итерация остаётся работающей production-версией.
Проверено локально: Backend test/build — 157 тестов, Bot test/build — 82 теста,
ошибок/пропусков нет. Backend suite использует H2; PostgreSQL suite второго этапа
не запускался (нет настроенной локальной аутентификации тестовой БД).

Первая итерация меню (11.11.2026): отдельные приветствия для нового и активного
аккаунта, выбор Windows/macOS, помощь по пяти ситуациям (старт, QR/код, вход,
запуск игры, доступ к альфе). `/start`, `/menu`, `/help`, `/cancel` и переходы
по кнопкам завершают ожидание ответа игроку. Короткий код регистрации также
выходит из режима ответа. `/cancel` отменяет ввод и возвращает главное меню.
Переход к сообщению сообщества отменяет ввод поста рассылки.

`LAUNCHER_WINDOWS_DOWNLOAD_URL` задаёт Windows-ссылку; если переменная отсутствует,
используется прежняя `LAUNCHER_DOWNLOAD_URL`. Явно пустой override отключает ссылку.
`LAUNCHER_MACOS_DOWNLOAD_URL` задаётся отдельно. Отсутствующая ссылка показывает
пояснение для выбранной платформы. Обе ссылки проходят проверку HTTPS без credentials
и fragment. Помощь и скачивание доступны и при недоступном Backend.
На экранах подтверждения регистрации/устройства кнопка переноса меню скрыта.
Профиль с игровыми данными, города и настройки подписок относятся к следующему этапу.
Выложено 11.11.2026: бот ACTIVE, long polling запущен, getMe/webhook/backend-проверки
прошли. Backend и Minecraft не перезапускались. Установщики проверены по HTTPS,
Ed25519-подписи текущих манифестов, размеру и SHA-256: Windows 0.1.15,
macOS Apple Silicon 0.1.16. Обе platform-specific ссылки настроены в server environment.
Резервная копия: `/opt/create-server/backups/bot-ux-20261111T184223Z`.
Ручной Telegram GUI/QR/device E2E после выкладки пока не выполнялся.

Deployment artifact собран из прежнего рабочего JAR с заменой только семейств
`BotPage`, `BotMain`, `BotHandler`, `CommunityBot` (включая вложенные классы).
Серверный `TelegramClient` содержит дополнительную поддержку загрузки изображений,
отсутствующую в этом checkout; он сохранён без изменения. Все 74 теста прошли
с этим точным deployment JAR в classpath. SHA-256 deployment JAR:
`794d3c3caf4090be057bae025d4a833587d05133f3527b020b5d9ab93f01991a`.
Обычный локальный `build` не воспроизводит эту серверную доработку: перед следующей
полной заменой runtime необходимо перенести её в исходники или сохранить рабочий класс.

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
