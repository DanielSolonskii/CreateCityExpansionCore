# Phone verification — controlled production deployment

2026-10-03. Production API: https://api.recreated.ru.
Backend + Bot rollout completed; real phone-registration E2E awaits user.

## Backup / artifacts

Root-only directory: /opt/create-server/backups/phone-verification-20261003T205228Z (0700).

- create_server.dump: custom-format PostgreSQL dump, 20367 bytes, root:root 0600.
  Backend/Bot stopped gracefully before dump; pg_restore --list passed.
- backend-before.jar: previous Backend, 58103027 bytes, root:root 0600.
- bot-before.tar: previous Bot distribution, 5601280 bytes, root:root 0600.
- bot-original/: original runtime directory retained for atomic rollback.
- state.json: server-only integrity snapshot; deploy.py: explicit phase-gated helper.
- New artifacts were uploaded root-only and SHA-256 checked before installation.

Deployed Backend SHA-256:
f49c660fc56098df22f999a8e9de1bd520a8cdeab9d9a118f2391e67bb650319

Bot distribution SHA-256:
cd54f1730bd8302a31c34988c662754c5b176822878cbd7ed74b0bbca1610870

## Verified production state

- Normal Backend startup applied Flyway V5; V1–V4 checksums/history unchanged.
- Loopback and public HTTPS health UP / postgres UP; version 0.1.0-SNAPSHOT.
- Backend runs as createserver; Bot as createtelegram; both active/running, enabled,
  Restart=on-failure, NRestarts=0 at handoff.
- Temporary runtime Restart=no deployment guards removed after successful gates.
- Existing ACTIVE usernames/password hashes/Telegram identities unchanged.
  User explicitly confirmed successful drannch1k login after Backend deployment.
- Telegram getMe succeeded through existing createtelegram UID/WireGuard route;
  webhook absent; Bot long polling log and established HTTPS socket verified.
- Bot UID made an authenticated direct 127.0.0.1:8080 lookup of existing identity.
- Protected backend.env / telegram-bot.env / WireGuard config and drop-in hashes unchanged.
- Policy rules/default route, alpha manifest hash, Minecraft MainPID unchanged.
- PostgreSQL and Backend still listen on loopback only. No firewall/Caddy changes.
- No test registration, alpha key generation/consumption, binding or game update performed.

## Windows Launcher

Production VITE_ACCOUNT_API_BASE_URL=https://api.recreated.ru.
38 frontend tests, TypeScript/Vite and Windows Tauri release/MSI/NSIS builds passed.
Production URL presence checked in native executable.

Installers (local, not automatically installed/published):

- launcher/src-tauri/target/release/bundle/nsis/RE-CREATED Launcher_0.1.0_x64-setup.exe
- launcher/src-tauri/target/release/bundle/msi/RE-CREATED Launcher_0.1.0_x64_en-US.msi

EXE SHA-256: f1f03a491456eaaf11ead12a808e9420abf2c7e42b85badee25b2c0be452a1c0
MSI SHA-256: 3fde618a42f6a0c85a10854235ffd8d697f2ebbb281c5bac312f3e869c21a7cd6

## User E2E handoff

Close the existing Launcher and install the new Windows build.
Use a Telegram account NOT already linked to another platform account.
Do not remove existing bindings merely to test; an alpha key is not refunded
if Telegram confirmation cannot complete.

Generate ONE fresh alpha key yourself in your trusted VDS SSH terminal:

```sh
sudo systemd-run --quiet --wait --pipe --collect \
  -p User=createserver -p Group=createserver \
  -p EnvironmentFile=/opt/create-server/secrets/backend.env \
  -p WorkingDirectory=/opt/create-server/backend \
  /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xmx256m \
  -jar /opt/create-server/backend/app.jar --generate-alpha-keys=1
```

This explicit administrative command prints UUID + plaintext key once to the
invoking terminal through --pipe. Do not send it to chat/Git/docs or enable tracing.
It does not restart Backend/Bot; only the key hash is stored in PostgreSQL.
The command was NOT executed during deployment.

Register a fresh username with that key → scan QR with phone → check displayed
account username → press confirmation button → observe automatic COMPLETED →
login with the new account. Report success/error without passwords/keys/codes.

## Rollback, only if explicitly needed

Do not automatically restore PostgreSQL: that loses accounts created after backup.
V5 is additive. Artifact rollback is coordinated with old Launcher/Bot versions;
new phone clients are not compatible with the old Backend registration response.

Stop Bot then Backend. Restore backend-before.jar with root:createserver 0640.
Preserve current Bot directory under a new root-only backup name, then move
bot-original back to /opt/create-server/telegram-bot (root:createtelegram).
If a failed deployment left /run/systemd/system/*/phone-deployment.conf,
remove only these deployment-owned overrides and daemon-reload after inspection.
Start Backend, verify health/migrations, then start Bot. Retain V5 schema/history.
Full DB restore requires a separate controlled decision.

Minecraft, CCEC, signed game distribution, updater, trusted devices and 4B unchanged.
