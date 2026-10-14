# Reports / Telegram admin panel (local, not deployed)

`/report <text>` -> server-side player UUID/context -> authenticated private Backend -> PostgreSQL V8 -> durable per-admin notification -> Telegram admin panel.

No changes to 4B admission/session APIs, Launcher, updater or gameplay managers. CCEC reads its existing Backend URL/server ID and server-only `CCEC_BACKEND_SECRET`; the new endpoint uses the existing `X-Game-Service-Secret` guard. Backend derives platform identity/username from Minecraft UUID, never from client-supplied account names. ACTIVE mapped identity required. No local fake identity fallback.

## API (private only, never add to public Caddy allowlist)

- `POST /api/internal/game/reports`: CCEC credential; commandId, minecraftUuid, serverId, text, dimension, x/y/z, occurredAt. Backend assigns canonical account and createdAt. Same command/payload is idempotent; changed payload conflicts.
- `GET /api/internal/reports?telegramUserId=N&offset=0`: bot credential + configured numeric admin ID; ten OPEN/IN_PROGRESS reports, stable creation-time/UUID ordering.
- `GET /api/internal/reports/{id}` and `/{id}/player`: bot credential + admin query ID. Player card exposes only username/status/platform+Minecraft UUID/current join alias/binding boolean, never credentials.
- `POST /api/internal/reports/{id}/actions`: bot credential; commandId, numeric telegramUserId, TAKE/CLOSE/REJECT. Pessimistic row lock, audited transition. Replayed command id is idempotent. Conflicting/stale transitions rejected; only assignee can finish a taken report.
- `POST /api/internal/reports/notifications/claim`: bot credential + admin ID; bounded 120-second leased batch.
- `POST /api/internal/reports/notifications/{id}/ack`: matching admin ID/lease token, only after Telegram send succeeds. Durable across Bot restarts.

Backend is the sole authorization/state authority; Bot repeats the allowlist check for UX. Users cannot supply admin IDs through callback payloads: the Bot always takes the numeric sender ID from the private Telegram update, validating sender=chat and refusing bot/forwarded/group events. UUID is not authorization.

## Database V8

- player_report: UUID ID/idempotency key, platform and Minecraft FKs, username snapshot, text, server/dimension/coordinates, occurredAt/createdAt/updatedAt, request hash, status and assigned admin.
- report_admin_action: unique command UUID, report FK, numeric admin ID, action, before/after status, timestamp.
- report_notification: report/admin PK, createdAt, lease UUID/deadline, deliveredAt. Created atomically with report.

OPEN -> TAKE -> IN_PROGRESS -> CLOSE/REJECT -> CLOSED/REJECTED. OPEN can also be closed/rejected directly. No deletion or automatic reopening.

## Limits / delivery

Text: 5–1000 UTF-16 units with at least five Unicode code points, trimmed; CCEC refuses control characters. Backend permits newline for compatibility but refuses other control characters. Coordinates finite and bounded; dimension identifier validated; server timestamps within ten minutes past/one minute future.

CCEC: per-player 60-second monotonic cooldown, capped actor cache, two daemon workers, bounded queue. No blocking HTTP on Minecraft thread. At most two same-ID transport attempts, five-second request deadline. Receipt is shown only after Backend confirms its ID; ambiguous failures explicitly say the report may have been saved. Backend: durable per-platform 60-second cooldown and ten reports/hour under actor-row lock. Existing service/IP guard remains in place.

Notifications are at-least-once: a crash after Telegram accepted a message but before Backend ACK can produce a duplicate notification. It cannot duplicate a report or audited action. No notification cursor is kept in Telegram. Revoked/unconfigured admin IDs cannot claim pending notifications. Configure admins before accepting production reports; adding an admin later does not backfill historical notifications (existing reports remain available in the panel).

## UI

Configured admins get 🛠 Админ · Репорты and `/admin`, independently of ordinary account registration. Open list -> report -> Взять / Закрыть / Отклонить / Игрок; navigation edits the existing message. Unknown/stale callbacks and Backend downtime show a retry/list action, never fake success. Regular registration, device callbacks and user menus retain their protocols. Report text is Telegram plain text, not HTML/Markdown. No player kick/ban/game mutation action.

## Configuration / controlled deployment

Set identical `REPORT_ADMIN_TELEGRAM_IDS=123456789,987654321` (actual approved numeric IDs, at most 20) in protected Backend and Bot environment files. Empty default disables admin access/notifications; it does not make anybody admin. No usernames/group IDs/secrets in this list.

Future explicit deployment: back up PostgreSQL and artifacts/configuration; deploy Backend and apply unchanged V1–V7 + V8 normally, configure IDs; deploy/test Bot; backup/stop Minecraft and install matching CCEC client/server JAR through the controlled release workflow. Never expose these internal routes publicly, never publish from a build. Smoke test private report ingestion and admin authorization, then real `/report`, notification, take/close/reject, replay, ordinary-user denial and Bot restart/retry. Launcher installer and updater do not need changes. Production not changed in this task.
