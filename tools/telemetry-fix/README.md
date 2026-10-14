# Telemetry contract correction (2026-10-08)

Production blockage was captured read-only: revision 16253 contained CITY_PLOT_CLAIMED, not supported by the deployed Backend. Its immutable retry prevented future heartbeat delivery.

Backend accepts CITY_PLOT_CLAIMED, CITY_LEVEL_CHANGED and CITY_COLOR_CHANGED. Colour details are restricted to exactly #RRGGBB; other events retain the existing safe 64-character detail validator. No schema migration.

CCEC additionally classifies only HTTP 400 INVALID_TELEMETRY from the telemetry endpoint as permanent rejection. It counts discarded pending events, clears that pending batch and allows the next fresh snapshot to proceed. Unknown errors, network failures, authentication failures, conflicts, throttling and server errors retain retry behavior. 4B/report endpoints are unaffected.

Verification: regression test failed before the Backend fix; after the fix 117 H2 and 107 PostgreSQL tests passed. PostgreSQL used isolated telemetry_fix_20261008 schema after the existing public test schema lacked broadcast tables. CCEC checkTelemetryBuffer, checkAuthenticationStartupPolicy (89 checks), checkReportLimits and jar passed.

Backend deployed with DB/JAR backup at /opt/create-server/backups/telemetry-fix-20261008T191531Z; migrations, secrets and existing Minecraft/Bot/WireGuard processes unchanged. Bot authenticated localhost lookup returned ONLINE and two players after deployment.

The additional CCEC guard is local only until a controlled Minecraft restart is approved. Do not deploy the full dirty worktree automatically: preserve the existing server artifact and avoid mixing unrelated source changes into this repair.
