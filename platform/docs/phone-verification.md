# Phone registration verification

Backend/Bot deployed in production after controlled backup and verification:
[deployment evidence / installers / E2E / rollback](phone-verification-deployment.md).
Real phone-registration E2E awaits user; implementation tests below remain valid.

## Flow

Alpha Key + username/password → PENDING account/session → QR + XXXXX-XXXXX →
Telegram на телефоне → preview username → «Подтвердить регистрацию» →
atomic COMPLETED + ACTIVE → Launcher polling → успешная регистрация → обычный login.

Вместо QR можно открыть бота по имени и отправить /code XXXXX-XXXXX,
либо /code и затем сам код. Пароль и alpha key никогда не отправляются боту.
Launcher: copy-code, expiry, ручной retry polling при network error,
bounded restart QR/code по polling capability. Закрытие окна теряет capability;
после успешного подтверждения обычный login продолжает работать.

TTL QR/code прежний REGISTRATION_TTL_SECONDS (default 900, 60–3600).
Confirmation button ticket живёт min(5 минут, остаток TTL).
Code guesses ограничены durable 5/ID/15min; malformed и invalid тоже учитываются.
ID берётся только из официального update.from.id, не из Telegram username.
Неправильный/истёкший/used code даёт один INVALID_VERIFICATION.
Duplicate Telegram / identity conflict не активирует второй account.
Ключ alpha остаётся USED при expiry и любых незавершённых verification.

DB: V5 добавляет registration_session.code_hash UNIQUE nullable,
restart_count CHECK 0..3, hash-only telegram_confirmation (FK cascade),
verification_rate_bucket с hashed keys/count/window. V1–V4 не менять.
Secrets раздельны: polling / QR / short code / bound button ticket.
HMAC short code использует защищённый BOT_BACKEND_SECRET с domain separation.
Не отправлять QR стороннему генератору, не писать API bodies в access/debug logs.
Не публиковать internal endpoints без service-secret protection.

## Local verification

Backend: gradlew test build; gradlew postgresTest с отдельной локальной
create_server_test, никогда production DB. Проверки QR/code hash storage,
wrong sender, expiry/ticket expiry, polling isolation, replay, restart rotation,
legacy sessions, concurrent confirmations/duplicate identities, durable
concurrent wrong-code limits. H2 и PostgreSQL выполняют общий suite.
Bot: gradlew test build, fake Telegram/backend HTTP; real token не нужен.
Frontend: npm test / npm run build; QR/copy/expiry/restart/polling/login/password
storage checks. Rust: cargo test --locked --offline (live game probes ignored).
Real QR scan/clipboard WebView/phone Telegram E2E — separate acceptance после deployment.

Фактический финальный прогон:

- Backend test/build: 55 tests, 0 failures.
- Локальный PostgreSQL 18.6 create_server_test: 46 tests, 0 failures;
  Flyway V1–V5 success. Production database не использовалась.
- Bot test/build: 19 tests, 0 failures; только fake HTTP.
- Frontend: 38 tests и tsc/Vite build успешны.
- Rust/Tauri: 14 passed, 2 intentional live-game probes ignored, при
  cargo test --locked --offline -- --test-threads=1.
  Один параллельный прогон дал сбой существующего updater HTTP fixture;
  последовательный повтор прошёл. Updater source не изменялся.
- Windows installer не пересобран/не установлен; новый production phone E2E
  не заявляется проверенным.

## Controlled deployment plan — execute only with explicit approval

1. Проверить финальные локальные tests/build, зафиксировать SHA-256 artifacts.
2. Backup production PostgreSQL, текущие Backend JAR и Bot distribution,
   protected env/config. Секреты не выводить и не копировать в репозиторий.
3. Остановить Bot на короткое coordinated maintenance окно; Minecraft не трогать.
4. Deploy Backend; штатный Flyway V5. Проверить schema/history, health/version
   и login существующего ACTIVE account. Не запускать alpha CLI случайно:
   новый executable тоже применяет V5 при запуске.
5. Deploy Bot с новым prepare/accept + callback_query. Сохранить WireGuard,
   loopback Backend и secrets; новых env secrets не требуется. Restart/polling smoke.
6. Собрать production Windows Launcher, сохранив production API URL.
   Signed game manifests/updater/alpha channel не менять.
7. Controlled real E2E с новым alpha key: QR с телефона, второй account через
   /code, explicit button → COMPLETED → ACTIVE → login; expiry/retry/replay,
   old account login; проверить clipboard в native WebView.
8. Rollback: остановить новый Bot, вернуть прежние artifacts согласованно.
   V5 additive допускает старый backend для старых клиентов, но новые phone
   clients с ним несовместимы. Не откатывать БД удалением V5 history/columns:
   restore backup требует отдельного решения, иначе теряются новые аккаунты.
   При проблеме остановить новые регистрации/выпуск Launcher, сохранить данные.

QR остаётся bearer capability, не proof-of-person/device. Полный account recovery,
password reset, trusted devices, Game Session/4B здесь не реализуются.
