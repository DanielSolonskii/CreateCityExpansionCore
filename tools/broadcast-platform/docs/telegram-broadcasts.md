# Telegram admin posts / broadcasts

Admin menu: `🛠 Администрирование → 📣 Рассылки → ✏️ Новый пост`.
Send text (up to 4000 UTF-16 characters) OR one photo with a caption (up to 1024).
Albums, arbitrary download URLs and other attachment types are not accepted.
The Bot sends an admin-only preview; the editable menu asks for explicit
`✅ Отправить всем`. Cancel, or `/start` while composing, leaves recipients untouched.
An immutable draft remains available in the latest-post list after a Bot restart.
Completed/queued posts show sent, failed, uncertain and pending counts. Refresh
the card to see progress. Existing report, account, registration and device menus
retain their protocols and numeric-ID authorization.

## Authority / security

V10 adds `broadcast_post`, `broadcast_delivery` and a one-row mutation lock.
All endpoints under `/api/internal/broadcasts` require existing
`X-Bot-Service-Secret` AND a numeric admin ID from `REPORT_ADMIN_TELEGRAM_IDS`.
No new secret, public recipient list or client API. Sender is taken from a private
Telegram update; forwarded messages and bot/group senders cannot compose posts.
Plain text and captions are not interpreted as HTML. Photo is an existing Telegram
`file_id`, not a user-provided URL; no image download/storage on VDS.

Create uses a deterministic command UUID from admin ID + update ID. Publish locks
and atomically snapshots only Telegram identities linked to ACTIVE platform users.
Repeated/concurrent confirmations cannot enqueue a second delivery per recipient.
Only drafts can be cancelled. Admins may inspect/publish each other's drafts.
At most ten new drafts per admin/hour. Publish is never triggered by build/startup.

## API (internal only)

- GET `/api/internal/broadcasts?telegramUserId=N`: latest ten posts.
- POST `/api/internal/broadcasts`: commandId, telegramUserId, text, nullable photoFileId.
- GET `/{id}?telegramUserId=N`: content/status/counts.
- POST `/{id}/publish`, `/{id}/cancel`: telegramUserId.
- POST `/claim`: telegramUserId; at most three leased deliveries.
- POST `/{id}/ack`: telegramUserId (admin), recipientId, attemptToken,
  outcome SENT/FAILED/UNCERTAIN/RETRY, messageId, retryAfter.

## Delivery guarantees / limitations

Single background worker; three messages per batch, 500ms spacing and five-second
idle interval. Main long polling is not blocked by a bulk send. Durable PostgreSQL
queue survives restart. Backend receives requests directly on localhost; Telegram
continues using the existing service-specific WireGuard routing.

Telegram has no send idempotency key. Exactly-once external delivery cannot be
guaranteed after a lost response. Network/5xx ambiguity or an expired 120s SENDING
lease becomes UNCERTAIN and is **not automatically retried**. Inspect manually
before deciding whether to create another post. Definitive 400/403 failures are
FAILED and do not abort the batch. 429 is deferred by Telegram retry_after.
SENT is recorded only after a valid positive Telegram message_id. An ack outage
leaves a lease uncertain rather than blindly duplicating a successfully sent post.

## Deployment / rollback

Backup production DB (custom pg_dump), Backend JAR, Bot bin/lib and protected env
files. Verify local H2/PostgreSQL/Bot suites and builds. Stop Bot, install Backend,
let normal Flyway apply additive V10, verify health/history, then install/start Bot.
Do not change secrets, WireGuard, Minecraft, Launcher or distribution.
For rollback, stop Bot, restore prior artifacts and restart Backend then Bot;
leave additive V10 tables in place. Never downgrade Flyway history or restore an
old DB over live account/game data without a separate maintenance decision.
Manual acceptance: compose a photo post, inspect preview, cancel first; then
explicitly approve a real announcement and inspect the delivery report.
