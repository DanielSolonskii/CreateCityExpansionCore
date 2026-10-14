# Telegram menu-message controlled deployment — 2026-10-04

Production updated: Backend and Telegram Bot only. Minecraft/CCEC invocation,
secrets, WireGuard, REQUIRED 4B config and Flyway V1–V9 checksums unchanged.

Backup (root-only): `/opt/create-server/backups/menu-ux-20261004T134622Z`.
Contains verified PostgreSQL custom-format dump, previous Backend JAR, Bot runtime
archive and protected configuration archive. No plaintext credentials in this report.

Artifacts SHA-256:

- Backend JAR: `e61abc7a7a5eab204e4f5b9cf8b559c0c3c4da2fdb731e4201e35781a7f9d0c4`
- Bot tar: `f697200351dbd6cce21a99b2b0a046ddd17263e085bb73809024c23ea17909bd`

Verification: Bot 51 tests, Backend 107 default/H2 and 97 PostgreSQL tests, zero
failures/errors. Bot/Backend builds successful. CCEC build/checks successful,
including checkTelemetryBuffer; no CCEC source edit/deploy in this task.

Production health: PostgreSQL UP, public HTTPS health UP, version 0.1.0-SNAPSHOT.
Backend/Bot/Minecraft/WireGuard ACTIVE. getMe successful through Bot's selective
WireGuard UID, polling startup logged, service restart successful. Protected API
reads from the Bot UID to 127.0.0.1:8080 successful; non-admin bridge/summary 403.
Existing linked account ACTIVE. Launcher URL retained.

Observed snapshot: ONLINE 0/15, open reports 0, delivered events24h/sessions/playtime
all 0 at deployment check. This proves real data paths, not player gameplay E2E.
No test events/reports were fabricated in production.

Manual Telegram acceptance pending: /start -> all sections/back/refresh, admin
reports/players/statistics, repeated /start, report take/player/close, QR/code and
device confirmation. A new /start after Bot process restart may create one fresh
menu; subsequent navigation reuses it. Existing callback can recover old menu.

Rollback: stop Bot and Backend, restore backup/backend.jar (createserver ownership,
0640), restore bot.tar as root:createtelegram with runtime executable permissions,
start Backend and verify health, then start Bot. No DB restore is required for
artifact-only rollback because schema did not change; keep the DB dump for recovery.
Do not restore configuration or DB over live services without a separate diagnosis.
