# Account API — milestone 3

| Метод / путь | Credentials | Ответ |
| --- | --- | --- |
| POST /api/registrations | JSON username/password/alphaKey | 201 registrationId, pollingToken, telegramDeepLink, expiresAt, verificationCode |
| GET /api/registrations/{UUID} | Authorization: Bearer pollingToken | 200 state, expiresAt, platformUserId (только COMPLETED) |
| POST /api/auth/login | JSON username/password | 200 AUTHENTICATED, platformUserId, username; без session token |
| POST /api/registrations/{UUID}/restart | Authorization: Bearer pollingToken | 200 новая пара QR/code + pollingToken для того же session/account |
| POST /api/internal/telegram/prepare | X-Bot-Service-Secret | JSON registrationToken ИЛИ verificationCode, telegramUserId; 200 confirmationToken, username, expiresAt |
| POST /api/internal/telegram/accept | X-Bot-Service-Secret | JSON confirmationToken, telegramUserId, telegramUsername; 200 platformUserId |
| POST /api/internal/telegram/confirm | X-Bot-Service-Secret | legacy-only: registrationToken, telegramUserId, telegramUsername; новые V5 sessions отклоняются |
| GET /api/internal/telegram/users/{numericId} | X-Bot-Service-Secret | 200 platformUserId, username, status или 404 |
| GET /api/users/{UUID}, /api/identities/minecraft/{UUID} | X-Bot-Service-Secret | прежние identity DTO |

Все registration/login ответы имеют Cache-Control: no-store.
Telegram account lookup также no-store. username — platform account username,
не Telegram username и не Minecraft current name; nullable для identity без credentials.
status отражает platform_user. Это additive read-only расширение прежнего ответа:
platformUserId сохранён; никаких password hashes, verification/polling secrets
или credential entities. Новых routes/миграций для Bot UX нет.
Launcher генерирует QR telegramDeepLink локально, показывает verificationCode
и хранит pollingToken только в памяти открытого окна. Telegram Desktop не нужен:
QR сканируется телефоном; fallback — /code XXXXX-XXXXX в боте.
Bot сначала вызывает prepare и показывает username + явную кнопку подтверждения;
только callback вызывает accept. Numeric from.id берётся из Telegram update.
Public routes не принимают Telegram ID. Launcher опрашивает status каждые 3 секунды.
Secrets не показываются техническим текстом; код можно скопировать.

Status: PENDING, COMPLETED, EXPIRED. Completion/login не выдают Minecraft game
session. Не отправлять secrets в логи/аналитику. Backend по умолчанию loopback.

Ошибки: 400 invalid input/verification; 401 generic invalid login/service auth;
404 unknown registration/wrong polling secret; 409 username/Telegram conflict;
410 истёкшее completed polling; 429 rate limit; 503 verification/internal API не сконфигурирован.
Секреты polling и verification невзаимозаменяемы.

Closed alpha: alphaKey обязателен для новой регистрации. Неизвестный,
использованный, отозванный, истёкший или отсутствующий ключ даёт одинаковый
400 INVALID_ALPHA_KEY без деталей. Consume и создание pending account/session
атомарны; Telegram expiry не возвращает ключ. Login/существующие аккаунты
не требуют ключа. [V4 и генерация](alpha-access-keys.md).
Phone flow / V5 развёрнуты в production:
[deployment / E2E](phone-verification-deployment.md).
Ограниченный restart (до 3 раз за первые 24 часа) требует старый polling secret,
вращает QR/code и инвалидирует все старые кнопки. Polling secret остаётся прежним:
потеря ответа restart не лишает Launcher возможности повторить запрос.
Он не создаёт аккаунт,
не возвращает alpha key и недоступен после COMPLETED. После закрытия окна secret
утрачивается: полного account recovery/password reset нет.
Подробности security/deployment: [Phone verification](phone-verification.md),
[ADR-0006](adr/0006-phone-telegram-verification.md).

Trusted-device authentication is additive V6, deployed on 2026-10-04. Legacy login
and registration remain unchanged; the new Launcher uses password → phone device
approval → native Windows credential → remote-validated auto-login.
[Routes, credential model, tests and controlled rollout](trusted-devices.md).
