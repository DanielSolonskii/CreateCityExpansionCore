# RE:CREATED Telegram menu-message UX

Main menu, sections, report navigation/actions, registration preview/confirmation
and trusted-device preview/confirmation reuse one presentation message per private
chat via `editMessageText`. `/start` returns this message to home. Callback replies
always acknowledge the spinner. Deleted/uneditable messages are replaced once;
unchanged edits are successful; network/API failures never trigger blind sends.
After a Bot restart an existing callback adopts its message if the bounded
in-memory menu cache is empty. A plain `/start` after restart may create one fresh
menu; no credentials or authorization decisions are cached.

Main linked menu: Account, Server, Launcher, Notifications, Help. Configured numeric
admins additionally see Administration -> Reports, Players, Statistics. Open count
includes OPEN and IN_PROGRESS across all pages, obtained from the protected new
GET `/api/internal/reports/summary?telegramUserId=N`. Both Bot allowlist and Backend
admin authorization apply. No migration beyond existing V9 is needed.

Server status/counts at home and the Server page come from the existing bridge
status API; admin names, sessions, playtime and events from the existing bridge
admin API. Stale heartbeat displays UNKNOWN without old player counts. Backend
failure displays unavailable, never invented OFFLINE/zero reports. Telemetry
remains bounded asynchronous batched CCEC v1; no tick/network changes here.

New reports remain separate durable-outbox alerts (not menu navigation). Clicking
their buttons opens the report in the current menu. An action updates both the old
alert card and canonical menu if they differ. Report state/audit/idempotency stay
in PostgreSQL. Old callbacks are reauthorized; registration/device confirmations
retain their existing numeric sender-bound Backend credentials/protocol.

Every plain-text page is HTML-escaped centrally before adding one trusted bold
title; usernames, labels and report text cannot inject markup. No Telegram,
verification, service or device secrets appear in message text or logs.

Controlled deployment: backup DB, Backend/Bot artifacts and configuration;
deploy Backend then verify health, protected count/admin isolation; deploy Bot,
verify getMe through existing WireGuard UID, polling and direct localhost Backend.
Minecraft/CCEC/4B, schema, game distribution and updater remain unchanged.
Manual acceptance: `/start`, all sections/back/refresh, repeated `/start`, report
take/player/close, registration QR/code, device approval/revocation. Automated
fake Telegram tests do not replace this real-device acceptance.
