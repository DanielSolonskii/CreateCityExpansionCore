# Telemetry v2 — 17.10.2026

## Sources and identity

The existing ServerTelemetryBridge and telemetry_session remain authoritative for joins, leaves and playtime. Launcher events use a device-authenticated owner-scoped endpoint; neither WebView nor payload can select another account. Each launch receives a UUID attempt, passed as a non-secret JVM property and associated with the server-owned session. Backend attribution also requires the same authenticated platform user.

Server events: session start/end/checkpoint, explicit disconnect reason codes when observable, server stop, real-time online reconciliation, mean tick work, city/market/railway/project outcomes, friend acceptance (no peer identity), and existing city milestones.

Client reports (not authoritative gameplay facts): FPS, frame-time p95, frames >=100ms, ping (-1 means unavailable), map disk tile reuse, tile replies/max wait, and whitelisted screen surfaces. Fixed numeric bounds and per-player rate limits apply. Summaries every 30s; no per-frame packets.

Launcher stages: LAUNCH_CLICK, AUTH_READY, UPDATE_READY, PROCESS_START, PROCESS_EXIT and LAUNCH_FAILED; cumulative elapsed time and native exit code, version, UUID attempt/event. Nonzero process exit is ABNORMAL, not a proven Minecraft crash. The actual stage order follows the current launcher implementation.

## Persistence and load

- Existing 2048-event server memory queue, immutable batches of <=50, disk outbox <=256 batches x128KiB. Background persistence precedes HTTP; IDs survive retry/restart. Capture/flush is asynchronous. Events since the last worker checkpoint can still be lost on an abrupt OS/process failure; the queue does not promise transactional gameplay durability.
- Native outbox <=256 events x4KiB, <=32/request, five-second retry loop, seven-day local expiration. No credential is stored in the telemetry files. Revoked credentials cannot upload; another device/account cannot upload their queue.
- Backend batch/event deduplication remains in force. V14 adds launcher_telemetry_event and indexes; prior migrations, worlds, SavedData, money and project records are not rewritten.
- Client diagnostics and surveys can be disabled in launcher settings; basic server session/administrative facts remain enabled. Changes apply to the next launched client.

## Admin UI and surveys

Telegram /admin → «Аналитика игроков»: Online/session timelines, launch funnel, problems/quality, retention/milestones. Backend rechecks configured admins on every request; Caddy never publishes internal analytics. Online section includes existing fresh server status. Session absence/unfinished end is explicitly not treated as proof of a crash.

D1/D7/D30 means an observed session on that specific UTC day after the first recorded session. Only fully elapsed target days enter the denominator. Small cohorts do not establish causality. Launch-to-join attribution requires matching account and attempt; launcher closure during a running game can leave native exit information unavailable.

After a process lasting >=10min, an optional launcher survey asks a coded reason and what the player wants to continue. At most once per seven days; Skip closes it. No free-text upload. Server observations and client reports are distinguished.

Never collected by this telemetry: chat/DM text, passwords/tokens, logs/crash dumps, wallet balance, exact movement routes or arbitrary typed form content.

## Scope and verification

Client alpha-0.1.23 / CCEC 1.0.2 includes the previously approved WorldMap border/minimap/railway fixes. Launcher 0.1.7 includes the registration transport hotfix. Other pack mods/config seeds are unchanged.

Built CCEC compileJava+jar, Backend bootJar, bot installDist, frontend tsc+Vite and native NSIS. Only the new telemetry contract and existing bridge accounting tests were run; no repeated complete mod/frontend test suite. Final deployment hashes/backups/health and signed release receipts are recorded in output/telemetry-v2. In-game FPS/panels/survey/real account registration still require runtime acceptance by players.

## Deployment compatibility correction

The first CCEC 1.0.2 server start was rejected by the optional Companion Bridge's exact [1.0.1] dependency. The release workflow kept the client channel unchanged. A normal file rollback restored the server without restoring/replacing the world. Bridge metadata now accepts [1.0.1,1.0.3), with unchanged Java source; its build classpath uses CCEC 1.0.2. The rebuilt bridge is included in both release candidates. Runtime readiness is required again before promotion.

Final deployment: launcher 0.1.7 (sequence 7), client alpha-0.1.23 (sequence 24), server CCEC 1.0.2 with compatible bridge, Backend V14 and updated bot. Services active, PostgreSQL health UP. See output/telemetry-v2/DEPLOYED.md for hashes and backup receipts. Remaining VDS free space approximately 817 MiB; no further backups deleted.
