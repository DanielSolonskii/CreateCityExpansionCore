# ADR-0003: Create Server Account + Telegram Verification

- Статус: принято
- Дата: 2026-10-03
- Уточняет ADR-0001/0002: platform identity участвует в activation lifecycle.

## Offline-mode и account authority

Minecraft server работает в offline-mode. Microsoft/Mojang authentication не
используется как identity Create Server. Пользователь регистрирует собственный
Create Server Account; backend владеет platform account, credentials и external
identities. Login не выводится из Minecraft name или Telegram username.
Offline username/UUID и наличие minecraft_identity не доказывают личность.

Telegram verification обязательна для активации и будущего допуска к игре.
Будущий CCEC должен проверять game session server-side через backend.
Этот milestone не реализует game session или CCEC authentication. ADR-0001
сохраняет игровое состояние, экономику и физическую логистику на Minecraft server.

## Database и state machine

V3 расширяет status platform_user: PENDING_VERIFICATION / ACTIVE / DISABLED.
Существующие значения сохраняются; inline V2 check заменяется через временный
column status_v2. Файлы V1/V2 не меняются.

- account_credentials: platform_user_id PK/FK, username UNIQUE, password_hash.
- telegram_identity: положительный telegram_user_id BIGINT PK, platform_user_id
  UNIQUE FK, nullable current_username, verified_at.
- registration_session: UUID PK, platform_user_id UNIQUE FK, verification_hash
  и polling_hash UNIQUE, state, created_at, expires_at, completed_at.
  CHECK обеспечивает разные hashes, expiry после creation, completion timestamp
  только для COMPLETED.

Registration атомарно создаёт pending user, credentials и PENDING session.
TTL по умолчанию 15 минут, configurable 60–3600 seconds. Истечение проверяется
при каждом polling/confirmation; polling материализует EXPIRED. Не требуется
background job для отказа в истёкшем token.

Trusted bot confirmation блокирует session row (PESSIMISTIC_WRITE), вставляет
Telegram identity, устанавливает user ACTIVE и session COMPLETED в одной
транзакции. Повтор token отклоняется. Duplicate Telegram ID делает rollback,
вторая registration остаётся pending. Disabled user не активируется confirmation.

Legacy ACTIVE user без Telegram не проходит login. Minecraft identity остаётся
отдельной; регистрация не создаёт её и не копирует login в Minecraft name.

## Password model

Username: 3–32 ASCII characters; первая буква, дальше буквы/цифры/underscore;
case-insensitive через lowercase. Пароль: минимум 12 Unicode code points,
максимум 72 UTF-8 bytes, без trim/нормализации. Spring Security
DelegatingPasswordEncoder использует BCrypt strength 12, random salt и prefix
{bcrypt} для будущего upgrade.

Отсутствующий username проверяется через BCrypt dummy hash. Wrong password,
pending, disabled и отсутствие Telegram дают одинаковый 401 INVALID_LOGIN.
Login проверяет credentials и возвращает AUTHENTICATED + platform UUID.
Access token/cookie/game session не выдаются: пока нет launcher protected
операций, требующих session. Этот ответ нельзя использовать как доказательство
допуска к offline Minecraft server. Session model требует следующего ADR.

## Registration secrets

Два независимых SecureRandom 32-byte token, base64url без padding (43 characters).
В БД только SHA-256 hashes высокоэнтропийных secrets. Telegram verification
token идёт в deep link, polling token остаётся у launcher и передаётся
Authorization: Bearer для registration UUID. Tokens не взаимозаменяемы.
COMPLETED verification token не пригоден для повторного использования.
Completed polling доступен только до expires_at, затем 410 REGISTRATION_EXPIRED.
Expired pending polling возвращает EXPIRED без platform UUID.

Это bearer capability: захват verification link позволяет подтвердить pending
registration. Поэтому обязательны TLS в deployment, короткий TTL и отсутствие
token logging. Не открывать ссылки, переданные посторонними вместо launcher.

## Telegram / backend trust boundary

Bot получает numeric from.id из официального HTTPS getUpdates update в private
chat; ID положителен и совпадает с chat.id, author не является bot.
Telegram username — nullable display metadata. Group и forwarded author metadata
не являются источниками identity.

Backend confirmation доступен только через /api/internal/** с
X-Bot-Service-Secret. Secret задаётся BOT_BACKEND_SECRET в обоих приложениях,
без default, минимум 32 characters; сравнение constant-time. Пустой/короткий
secret отключает internal API и регистрацию (503). Foundation /api/users/**
и /api/identities/** тоже требуют internal secret. Launcher не получает его.
Backend доверяет этому authenticated bot; compromise бота/secret нарушает boundary.
Бот не имеет PostgreSQL credentials и не использует Redis.

HTTP bot/backend разрешён лишь на loopback; remote endpoint требует HTTPS.
TLS launcher/backend и защита reverse proxy обязательны перед внешним deployment.
Passwords/hashes, bodies, auth headers, registration secrets и Telegram URLs
не логируются. SQL diagnostic logger с failing rows отключён; API errors
sanitised. Proxy/tracing/debug также не должны логировать такие данные.

## Rate limits и отложенные требования

Single-instance in-memory fixed windows: registration 5/IP/15min; login
20/IP/5min и 5/username/5min; polling/internal 120/IP/min. Username limiter
также действует на service login. Keys bounded 10000, при переполнении отказ.
X-Forwarded-For не доверяется, используется servlet remote peer.

Limits сбрасываются при restart и не распределены между instances. До внешнего
deployment нужны durable/distributed limits, proxy trust, abuse policy,
password breach checking, audit/alerts. Redis сейчас не добавляется.
Pending usernames остаются зарезервированы. Resend, expired-registration recovery,
пароль reset и retention cleanup требуют следующего milestone; expired account
пока требует административного решения.

## Long polling

Один bot worker, allowed_updates = message. Offset продвигается после обработки.
Нет webhook, постоянного offset storage или DB. При network/server error retry.
Если confirmation завершился, но ответ потерян, повтор token будет invalid:
источником completion остаётся launcher polling.

## Проверка

Один account DB/service/HTTP contract suite выполняется на H2/PostgreSQL.
Bot проверяется fake HTTP и не требует настоящего Telegram token.
Live Telegram, launcher UX и game admission не считаются проверенными этим suite.
