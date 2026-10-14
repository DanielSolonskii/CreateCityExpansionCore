# ADR-0006: Telegram verification с телефона

- Статус: принято; Backend/Bot развёрнуты controlled production deployment.
- Новый phone/Launcher E2E ещё ожидает пользовательской проверки.
- Дополняет ADR-0003/0005; account authority, login и alpha consumption неизменны.
- Уточняет ранее отложенный resend: только bounded restart по polling capability.

QR генерируется локально в Launcher. Telegram Desktop/PC VPN не требуются.
Внутри QR прежний 256-bit deep-link capability. Рядом короткий случайный код
из 10 символов (31-symbol alphabet, около 49.5 бит), формат XXXXX-XXXXX.
Код нормализуется по ASCII, хранится только HMAC-SHA256 с purpose domain
registration-code-v1 и существующим BOT_BACKEND_SECRET как server-side pepper.
Без pepper утечка БД не позволяет перебрать короткие коды офлайн.
Случайные long tokens сохраняют SHA-256. Credentials не взаимозаменяемы.

Prepare по QR/коду НЕ активирует account. Он выдаёт отдельный random 256-bit
confirmation ticket на максимум 5 минут, bound session generation + numeric
Telegram user ID. В БД только ticket hash. Callback принят только от того же ID,
в private chat, non-bot, chat.id == from.id. Username — nullable metadata.
Forwarded messages не используются. Bot показывает account username и предупреждение
подтверждать только свой запрос. Это уменьшает ошибки, но не устраняет phishing:
получатель чужого QR/кода всё ещё может привязать к чужой pending registration
свой Telegram. Не пересылать QR/коды; TLS и доверенный бот обязательны.

Accept блокирует session и атомарно создаёт unique Telegram identity,
активирует platform_user и завершает session. Replay/expiry/stale generation/
wrong sender запрещены. Unique numeric Telegram ID и platform UUID в V3 остаются
финальной защитой от concurrent duplicate identity. После completion ни QR,
ни code, ни tickets не пригодны для повторной активации.

Долговечный fixed-window limiter в PostgreSQL: code — 5 попыток / ID / 15 минут;
QR — 10 / ID / минуту; общий prepare — 200 / минуту. Успешные попытки тоже считаются.
Счётчики commit ДО основной transaction и сохраняются при отказе/restart процесса.
Keys HMAC-domain-separated; expired buckets очищаются при новых обращениях,
при достижении 10000 активных buckets новые keys fail closed. Session имеет
максимум 32 live confirmation tickets. Servlet limits ADR-0003 сохраняются.
Limiter не резервирует nested connection внутри session TX: пул из пяти
соединений выдерживает конкурентные запросы без pool starvation.
Это не полноценная distributed abuse/botnet защита; monitoring остаётся deployment gate.

V5 additive: nullable code_hash для совместимости старых sessions, restart_count,
telegram_confirmation и verification_rate_bucket. ACTIVE users не переписываются.
V1–V4 неизменны. Legacy confirm принимает только старые sessions без code_hash;
новые sessions требуют prepare/accept. Низкоуровневый AccountService.confirm
сохранён для внутренних service-level fixtures, не exposed для новых sessions.

Restart по текущему polling secret: максимум 3 за первые 24 часа от created_at.
Тот же account/session; свежие QR/code, старые tickets удалены. Polling secret
сохраняется, чтобы потеря ответа restart не отрезала Launcher от своей регистрации.
После COMPLETED запрещён, alpha key никогда не возвращается. Потерянный polling
secret не восстанавливается по username/password. Полное recovery не входит сюда.

BOT_BACKEND_SECRET должен быть случайным, защищённым и одинаковым у backend/bot.
Его rotation инвалидирует outstanding short codes и limiter keys; длинные QR
сохраняют валидность до TTL. Согласовать rotation отдельно; не логировать значения.
Redis, новые login credentials, trusted devices и Game Session не добавлены.
