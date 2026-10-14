# RE:CREATED Telegram Bot — пользовательское меню

Текущий [единый menu-message UX](telegram-menu-message-ux.md) включает server
bridge V9, отдельное администрирование, компактные репорты и общий message для
регистрации/устройств. Числа тестов ниже — историческая проверка первого UX milestone.

Реализация и controlled production deployment, 2026-10-04.
[Deployment evidence / backup / manual acceptance](telegram-bot-ux-deployment.md).
Ручной Telegram menu и QR/code E2E после rollout ожидает пользователя.

## Меню

| Пользователь | Главное меню |
| --- | --- |
| numeric Telegram ID связан с ACTIVE account с username | 👤 Аккаунт · 🎮 Сервер · 📥 Launcher · 🔔 Уведомления · ❓ Помощь |
| Telegram не связан | Приветствие и регистрация через Launcher · 📥 Скачать Launcher · 🔑 Ввести код · ❓ Помощь |
| Binding существует, account не активен | Честное сообщение о неактивном account, без ACTIVE меню |
| Lookup недоступен/невалиден | Временная недоступность, повторить/Launcher/помощь; не выдавать ошибку за unlinked |

Подстраницы имеют «← Главное меню». /start обновляет существующее меню;
/help открывает помощь; неизвестный ввод не отражается в ответе, а предлагает
навигацию или /code. Launcher использует LAUNCHER_DOWNLOAD_URL из environment:
постоянный HTTPS URL без userinfo/fragment. Нет placeholder default; без URL
кнопка открывает честное пояснение, а не мёртвую ссылку.

Аккаунт показывает только platform username, ACTIVE status и факт Telegram binding.
Уведомления — presentation foundation для release/restart/maintenance: нет рассылки,
outbox, предпочтений/подписки или fake toggle. Для действительной доставки нужен
отдельный контракт событий и согласованные настройки согласия/отписки.

## API и источники истины

- GET /api/internal/telegram/users/{numericId}: существующая служебная авторизация;
  additive response platformUserId/username/status, Cache-Control: no-store.
  Раньше возвращался только UUID, а identity read API не содержал platform username:
  именно поэтому понадобилось расширение read DTO. Новых routes нет.
- POST /api/internal/telegram/prepare и /accept: прежние phone-verification API.
- GET /api/internal/bridge/status и /bridge/admin?telegramUserId=N: реальные
  heartbeat/status/online/versions и protected players/session/playtime statistics.
  /api/health и /api/version относятся только к Backend. Бот не парсит Minecraft logs.
- GET /api/internal/reports/summary?telegramUserId=N: глобальный count OPEN/IN_PROGRESS,
  Bot service authentication + Backend numeric admin allowlist; без schema изменений.

DB/schema/Flyway V1–V5 не менялись. Регистрация/login, alpha consumption, proof
TTL/rate limits, one-to-one binding и numeric ID authority остаются прежними.

## Сообщения и callbacks

Используются штатные [sendMessage, editMessageText, answerCallbackQuery](https://core.telegram.org/bots/api#editmessagetext),
проверенные по официальной документации Bot API. Клавиатуры menu:v1:* не содержат
account UUID/secrets. Каждая navigation заново проверяет binding/status, не доверяет
старой клавиатуре. Private/non-bot/chat.id == from.id/не forwarded checks сохранены.

Кэш содержит только пары chat ID/message ID, максимум 10000; не содержит профили,
коды, токены или passwords. При restart/eviction новый /start может создать одно
новое меню; callback после restart позволяет продолжить в прежнем сообщении.
Callback старого меню возвращает актуальное меню, неизвестный callback — безопасный
toast и home, без вызовов account mutations. Отсутствующее/недоступное для edit
сообщение заменяется одним новым. «Message is not modified» — нормальный no-op.
Не создавать replacement при произвольной сетевой ошибке Telegram.

Confirmation prompt теперь редактирует общий menu-message с прежним verify:ticket.
/start возвращает home; свежий QR/code вновь открывает confirmation через прежний
prepare API. Accept result редактирует menu-message и убирает proof button. При Backend outage сохраняется та же
кнопка для retry; если ответ accept потерялся, Launcher polling остаётся источником
завершения, повторный proof получает безопасный invalid/replay ответ. QR, /code,
bare code, explicit confirmation и sender-bound ticket protocol не меняются.
При Backend outage помощь/скачивание/инструкция /code доступны, activation никогда
не симулируется. Telegram token, service/proxy secrets и response bodies не логируются.

## Проверки

Локальные команды из соответствующих каталогов:

```powershell
# Java 21
cd E:\CreateCityExpansionCore_MERGED\CreateServerPlatform\telegram-bot
.\gradlew.bat test build
cd ..\backend
.\gradlew.bat test build
# Только отдельная локальная create_server_test, не production!
# Задать локальные PostgreSQL variables защищённым способом, затем:
.\gradlew.bat postgresTest
```

Bot fake HTTP tests проверяют обе группы меню, inactive binding, navigation,
repeat /start, stale/cross-user callbacks, restore after restart, deleted/not-modified
messages, отсутствие fallback при transport error, Backend HTTP/network failures,
QR/code/confirmation/replay/conflict/rate-limit и URL validation.
Backend общий H2/PostgreSQL contract дополнен read DTO/auth/no-store/no-credentials
проверками; существующие phone/security/concurrency suites сохранены.
Живой Telegram GUI acceptance откладывается до разрешённого deployment.

Фактически выполнено 2026-10-04 на Java 21: Bot test/build — 29 tests;
Backend test/build — 56 tests; postgresTest на локальной create_server_test —
47 tests. Все прошли, failures/errors/skipped = 0. Bot tar/zip и Backend JAR
собраны локально. VDS, production PostgreSQL и текущий polling worker не трогались.

## Controlled deployment plan

1. По отдельному разрешению: backup PostgreSQL, текущих Backend/Bot artifacts и
   защищённого Bot environment file. Не выводить secrets.
2. Backend deploy первым: additive lookup без новой Flyway migration; health/version,
   служебный lookup правильным numeric ID и существующий login. При ошибке остановиться.
3. Настроить постоянный production LAUNCHER_DOWNLOAD_URL в root-only environment file.
   Token/BOT_BACKEND_SECRET/BACKEND_URL и WireGuard policy routing не менять.
4. Обновить только Bot distribution, restart единственного polling worker; проверить
   service/journal без secret values и bot -> Backend по loopback напрямую.
5. Ручной Telegram E2E: linked/unlinked /start, все разделы/back, повторный /start,
   QR и /code, explicit confirmation, Launcher COMPLETED/login, stale callbacks.
6. При ошибке откатить Bot artifact/env, затем Backend artifact при необходимости.
   Новых schema изменений нет. Minecraft/CCEC/Launcher/updater не затрагивать.
