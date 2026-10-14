# Photo broadcasts — production rollout

2026-10-04. Backend/Bot rollout completed; Minecraft was not restarted.

Root-only backup (0700):
`/opt/create-server/backups/broadcasts-20261004T194720Z`.
Contents: PostgreSQL custom-format database.dump (pg_restore --list checked),
previous backend.jar, bot.tar and configuration.tar (all 0600).
Configuration includes original secrets, systemd, WireGuard and Caddy files.
Never export that archive into source control or chat.

Build evidence:

- Backend `gradlew test build`: 115 tests, no failures/errors.
- Backend `gradlew postgresTest`: 105 tests, no failures/errors; local
  create_server_test only, Java 21/PostgreSQL 18.
- Bot `gradlew test build installDist`: 60 tests, no failures/errors.
- Backend deployed SHA-256:
  `8c1286ba3f83f2606a9439aee330bc32785c4766e379f11774f3fe341292e10f`.
- Bot distribution SHA-256:
  `31096334704d49252487bc1baff41f05965b83f3e81bd8423c38087a18bc42d6`.

Production checks:

- Uploaded/installed artifacts verified against local hashes.
- Flyway V10 applied by standard Backend startup; V1-V9 checksum/history unchanged.
- Existing platform/account credential/binding digest unchanged; no passwords
  requested or login performed on the user's behalf.
- Loopback/public HTTPS health UP/postgres UP; version 0.1.0-SNAPSHOT.
- Backend and Bot active/running, NRestarts=0.
- Telegram getMe succeeded as createtelegram through existing WireGuard route;
  @recreating_bot, long-polling startup seen, no startup communication failures.
- Protected Bot -> Backend broadcast API works directly on 127.0.0.1:8080;
  ordinary numeric ID denied 403, existing linked admin ACTIVE.
- Secrets/config hashes, WireGuard rules/default route and Minecraft invocation
  unchanged. Launcher, 4B, reports, CCEC and distribution artifacts not changed.
- No post or delivery created; no arbitrary announcement sent to players.

The first deployment health gate incorrectly expected a top-level postgres field.
Actual response is checks.postgres. Only the script's gate was corrected, then
health/history/account checks repeated successfully before installing the Bot.
There was no migration or service startup failure.

Manual Telegram E2E still required: /start -> Administration -> Broadcasts,
compose one photo with caption, inspect preview, cancel; then approve an intended
announcement and refresh its delivery report. Photo sending/bulk behavior was
tested using a fake Telegram API, not an unsolicited production broadcast.

Rollback: stop Bot, restore previous Backend JAR and Bot bin/lib from this backup,
start Backend, verify health, start Bot. Additive V10 can remain; no automatic DB
restore/down migration. Keep the archive for recovery.
