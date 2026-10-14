# Telegram Bot UX — controlled production deployment

2026-10-04 (Europe/Minsk). Backend and Bot rollout completed.
Public API: https://api.recreated.ru. Manual Telegram menu/QR/code E2E awaits user.

## Backup and rollback artifacts

Root-only directory: `/opt/create-server/backups/bot-ux-20261003T214500Z` (0700).

- `backend-before.jar`: previous executable Backend, root-only 0600.
- `bot-before.tar`, `bot-original/`: previous Bot distribution/runtime directory.
- `configuration/`: original Backend/Bot env files, systemd units/drop-ins,
  WireGuard configuration and Caddyfile. Protected by 0700 parent; never export
  these files into Git/chat or print their contents.
- `create_server.dump`: consistent PostgreSQL custom-format backup, 0600;
  `pg_restore --list` passed. No schema migration or DB restore performed.
- `state.json`: root-only integrity snapshot; `deploy.py`: explicit phase gates.
- `backend-new.jar`, `bot-new.tar`: uploaded artifacts, SHA-256 verified.

Deployment Backend SHA-256:
`011596f7e3cc40f1a92fc2cb0d9b2dc0086379362d9641ddf8ec7e19375838bf`.

Bot tar SHA-256:
`c0a529d4b2b2fd7e224e5468ae389ea2045485bcad2ba7ec0b700fabc25f2f53`.

## Actual checks

- Local Java 21 Gradle test/build succeeded: Backend 56 tests; Bot 29 tests.
  Bot tasks were up-to-date from the successful UX test run; Backend tests reran.
  The preceding local PostgreSQL contract suite had 47 passing tests.
- New/old JAR Flyway migration SQL files compared byte-for-byte before replacement.
  V1–V5 history/checksums/success unchanged after startup; no new migration.
- Backend health UP/postgres UP on loopback and public HTTPS; version
  `0.1.0-SNAPSHOT`. User confirmed existing Launcher login after Backend update.
- `create-server-backend.service`: active/running, enabled, createserver user.
- `create-server-telegram-bot.service`: active/running, enabled, createtelegram user.
  Restart=on-failure restored after temporary rollout guard; NRestarts=0 at handoff.
- Telegram getMe succeeded under createtelegram UID / existing WireGuard route;
  correct bot username and absence of webhook verified.
- Long polling startup log and established Bot HTTPS socket verified; no
  communication failures during controlled startup observation.
- Authenticated direct Bot-UID -> `127.0.0.1:8080` existing account lookup succeeded
  with actual platform username/status ACTIVE. No Telegram updates consumed by
  a second getUpdates worker, test registration or account mutation performed.
- Existing ACTIVE account credential/binding integrity digest unchanged.
- Secrets/service auth values preserved. Only legacy LAUNCHER_DOWNLOAD_URL
  placeholder removed from protected Bot env; all other env values compared unchanged.
- WireGuard config/drop-in, policy rules and ordinary default route unchanged.
  Minecraft MainPID and alpha channel manifest hash unchanged. No CCEC/game release/
  updater/SSH/firewall/Caddy change performed.

## Launcher download

No public current Windows installer was found in the Caddy-served distribution
tree; current phone-flow installers are local, not published by this task.
LAUNCHER_DOWNLOAD_URL remains unconfigured (no invented URL). The Launcher menu
explains that the link is not configured and asks users to contact the alpha organizer.
A separate explicit publication step is required before setting a verified permanent
HTTPS download URL in the root-only Bot EnvironmentFile.

## Manual Telegram acceptance

1. Linked account: /start, Account/Server/Launcher/Notifications/Help and back.
2. Repeat /start: one menu message reused; Account shows own username/ACTIVE/binding.
3. Launcher shows honest missing-download-link state.
4. Unlinked Telegram: greeting, Launcher, code prompt, help; no ACTIVE account menu.
5. Existing QR/code -> explicit confirmation -> Launcher COMPLETED/login still works.
6. Old buttons and unknown commands handled without technical secrets in replies.

## Rollback (not executed)

Stop Bot, then Backend. Preserve current artifacts under a new root-only backup;
restore backend-before.jar to `/opt/create-server/backend/app.jar` with 0640
root:createserver, and restore bot-original to `/opt/create-server/telegram-bot`.
If reverting environment, restore only configuration/1-telegram-bot.env to its
original protected 0600 root:root location (check for subsequent secret rotations first).
Start Backend, verify health, then start Bot; retain existing V5 schema and accounts.
Do not automatically restore the DB dump: accounts created since backup would be lost.
Deployment-owned runtime restart overrides were removed on successful finish.
