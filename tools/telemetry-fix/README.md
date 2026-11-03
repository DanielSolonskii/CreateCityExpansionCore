# Telemetry contract correction (2026-10-08)

**Archived repair packet. Reviewed 2026-11-02.** The incident, test counts,
deployment and hashes below describe October 8. They are not evidence of the
current production version. Current sources are in [platform/backend](../../platform/backend/README.md)
and [CCEC auth](../../src/main/java/com/example/examplemod/auth/); see the
[current audit](../../docs/audit-2026-11-02/README.md).

Production blockage was captured read-only: revision 16253 contained CITY_PLOT_CLAIMED, not supported by the deployed Backend. Its immutable retry prevented future heartbeat delivery.

Backend accepts CITY_PLOT_CLAIMED, CITY_LEVEL_CHANGED and CITY_COLOR_CHANGED. Colour details are restricted to exactly #RRGGBB; other events retain the existing safe 64-character detail validator. No schema migration.

CCEC additionally classifies only HTTP 400 INVALID_TELEMETRY from the telemetry endpoint as permanent rejection. It counts discarded pending events, clears that pending batch and allows the next fresh snapshot to proceed. Unknown errors, network failures, authentication failures, conflicts, throttling and server errors retain retry behavior. 4B/report endpoints are unaffected.

Verification: regression test failed before the Backend fix; after the fix 117 H2 and 107 PostgreSQL tests passed. PostgreSQL used isolated telemetry_fix_20261008 schema after the existing public test schema lacked broadcast tables. CCEC checkTelemetryBuffer, checkAuthenticationStartupPolicy (89 checks), checkReportLimits and jar passed.

Backend deployed with DB/JAR backup at /opt/create-server/backups/telemetry-fix-20261008T191531Z; migrations, secrets and existing Minecraft/Bot/WireGuard processes unchanged. Bot authenticated localhost lookup returned ONLINE and two players after deployment.

At the time of this repair, the additional CCEC guard was local pending a
controlled Minecraft restart. That historical statement does not describe
today's deployment. Do not use this packet to deploy a full dirty worktree.

## Current source and helper limits

The current [GameBackendClient](../../src/main/java/com/example/examplemod/auth/GameBackendClient.java),
[TelemetryBuffer](../../src/main/java/com/example/examplemod/auth/TelemetryBuffer.java)
and [ServerTelemetryBridge](../../src/main/java/com/example/examplemod/auth/ServerTelemetryBridge.java)
retain the explicit permanent-rejection path. Backend
[BridgeService](../../platform/backend/src/main/java/dev/createserver/backend/bridge/BridgeService.java)
accepts the three city event types above among its current event set. This audit
checked source; it did not send telemetry to production.

- `BridgeService.java` and `BridgeContract.java` here are historical loose copies,
  not the current Backend build project.
- `prepare_hotfix.py` patches a hash-pinned `1.0.1` baseline at a fixed sibling
  launcher path. It does not build or release the current mod.
- `deploy_backend.py` operates on a Linux `/opt/create-server` deployment,
  writes backups and replaces/restarts Backend. It is not a local verifier.
- `verify_published.mjs` pins alpha-0.1.11 / sequence 12; both it and
  `verify-release-artifact.mjs` import from a fixed sibling launcher path and
  search the historical `createcityexpansioncore-1.0.1.jar` manifest entry.
  They are not generic current-release verification commands.
- `current-alpha*.json`, `published-alpha*.json` and old JARs are historical
  snapshots/artifacts; their filenames do not identify the live channel.

Use [platform/launcher](../../platform/launcher/README.md) and its controlled
release tooling for current releases. A local regression command from the
repository root is `.\gradlew.bat checkTelemetryBuffer --offline` (Java 21 and
the dependencies in [README_DEV](../../README_DEV.md)). No legacy deployment,
artifact patching, publication or remote verification helper was run in this audit.
