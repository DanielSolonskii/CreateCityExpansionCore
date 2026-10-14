# Trusted devices — controlled deployment 2026-10-04

Backend/Bot/V6 and scoped public device routes deployed. Production-configured
Windows installer built locally; no installer publication/installation or live
phone/GUI E2E performed. Existing approved public Launcher 0.1.0 remains unchanged.

## Backup / rollback

`/opt/create-server/backups/trusted-devices-20261004-0326`, root-only 0700:

- `create_server.dump` (custom-format, 0600), validated with pg_restore --list.
- `backend-before.jar`, `bot-before.tar`, `bot-original/` — prior runtime artifacts.
- `configuration/` — protected originals of Backend/Bot environment, service units,
  Bot WireGuard drop-in/config, Caddyfile. Never export secrets into Git/chat.
- `state.json`, `deploy.py` — protected integrity snapshot and phase-gated rollout.
- `backend-new.jar`, `bot-new.tar` — uploaded and SHA-256 verified.

Rollback (not performed): stop Bot/Backend, preserve current artifacts, restore
backend-before.jar as `/opt/create-server/backend/app.jar` root:createserver 0640;
restore bot-original to `/opt/create-server/telegram-bot` (root:createtelegram).
Restore Caddyfile from configuration after checking for subsequent edits,
validate/reload it, start Backend then Bot and verify health. Keep additive V6:
old artifacts can run with the added table. Do NOT automatically restore old DB
or drop V6 over later production writes. Original secrets/WireGuard remain live.

## Verified

- Local Backend test/build: 62 H2 tests successful. Bot test/build successful
  (31 tests were up-to-date from passing suite). Frontend 43 tests reran/passed.
  Predeployment PostgreSQL suite: 53 passed. Rust suite: 18 passed, 2 explicitly
  ignored manual probes. Windows release/NSIS build successful.
- Uploaded/deployed Backend SHA-256:
  `a19ecc7b963f1b1f9910461ab74f20cbc452eca60b38b6e883dd74fe6cd2f7c2`.
- Bot archive SHA-256:
  `6af99947c5d00a67b6f4627fc69288adde59d39257c5aad9cd7473a0d5afea54`.
- V1–V5 SQL compared byte-for-byte old/new; original migration history/checksums
  unchanged. V6 applied by normal Flyway startup, all V1–V6 successful.
- Backend/Bot active/running/enabled under createserver/createtelegram;
  Restart=on-failure restored after temporary rollout guard; NRestarts=0.
- Public trusted HTTPS health 200 UP/postgres UP; version 0.1.0-SNAPSHOT.
- Public invalid-device poll: 404 JSON DEVICE_NOT_FOUND; invalid password device
  enrollment and legacy login: 401 JSON INVALID_LOGIN; public internal preview:404.
  No test accounts/alpha keys/devices inserted into production.
- Caddy candidate validated/reloaded. Only POST begin, GET UUID poll and POST UUID
  authenticate/revoke added to public allowlist. Internal APIs stay unreachable.
  Source `infra/caddy/Caddyfile` synchronized with deployed configuration.
- Telegram getMe under existing createtelegram UID/WireGuard succeeded,
  recreating_bot identity verified. Current Bot invocation shows long polling
  startup and no exception/fatal/failure. No second getUpdates worker ran.
- Authenticated Bot-UID → Backend 127.0.0.1 existing account lookup succeeded:
  drannch1k ACTIVE. Existing password/binding/user data integrity digest unchanged.
  A real password login and device approval require manual user acceptance;
  no password requested in chat or fabricated to claim login success.
- Protected env files/secrets, WG config/drop-in, IP rules/default route,
  Minecraft MainPID and alpha-0.1.1 manifest SHA unchanged.
- Backend8080/PostgreSQL5432 remain loopback-only. No firewall/SSH/CCEC/Minecraft
  or game distribution/runtime updater changes, no reboot.

Rollout verification initially caught `Failed with result exit-code` from the
OLD Bot's intentional SIGTERM143 stop. Inspected sanitized journal: new instance
healthy, NRestarts0. Verification was narrowed to the current systemd InvocationID;
no production networking or Bot behavior was altered to suppress an actual error.

## Launcher handoff / manual acceptance

`launcher/artifacts/trusted-devices-20261004/RE-CREATED-Launcher-trusted-devices-x64-setup.exe`
SHA-256: `15d7a65254c5e9e175bca1035dbca1c8486da820dbc9e7d0d3a3e6fca3de268c`.
Built with `VITE_ACCOUNT_API_BASE_URL=https://api.recreated.ru` for frontend/Rust.
Still version0.1.0: a local acceptance candidate, NOT an immutable approved release.
Do not publish over existing0.1.0; assign a new Launcher version and explicitly
approve/publicize after manual acceptance. Stable latest and Bot download URL
still target old approved installer hash
`f1f03a491456eaaf11ead12a808e9420abf2c7e42b85badee25b2c0be452a1c0`.
Previous local installer preserved outside repository in protected task staging.

Manual acceptance: install candidate with Launcher closed; first password login
on existing account → phone QR → explicit device approval → Launcher authenticated;
close/reopen → auto-login without password/Telegram; Telegram Account → Devices →
revoke → next Launcher start denied; fresh confirmation → logout → next start
requires login; repeat offline logout and reconnect; existing registration stays
compatible. These outcomes are pending user evidence, not claimed by deployment.
