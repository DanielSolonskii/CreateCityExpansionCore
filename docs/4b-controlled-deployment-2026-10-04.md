# Controlled 4B deployment — 2026-10-04

User explicitly declined legacy identity migration. Existing world/playerdata/city ownership were preserved, not reassigned or deleted. Master City was not reset or recreated automatically. New identities will be provisioned on first authorized game capability issuance.

## Recovery set

Root-only VDS directory: `/opt/create-server/backups/4b-20261004T035022Z`.

- `world.tar`: complete world after console stop/all dimensions saved; 304 MiB; SHA-256 `9c12074c74ec2b87e7ed96bd3c985281ae0ba5e37184e9c8657878a37e4fccb4`.
- `database.dump`: full create_server custom-format pg_dump with Backend stopped; pg_restore list validated; SHA-256 `3b3eeb0fe75dce1bc4b8f0e79fd59960278d4d40aac4ef830f3184b5cebb7ad6`.
- Previous Backend/CCEC artifacts, Caddy configuration and protected configuration archive. Archive includes secrets: keep root-only; never publish/copy into Git.

Rollback requires coordinated restored artifacts/configuration/database/world and explicit forward recovery releases for clients that have seen sequence 3. Do not disable REQUIRED on a marked world. Do not blindly rerun the one-shot deployment script: it would back up changed state and rotate service credentials.

## Deployed

- Backend executable JAR SHA-256 `b60326b2ed4fcb009a0163ac5b459a11549af6a6151480b8be7e1dd45a96101d`; Flyway V7 success.
- Shared client/server CCEC SHA-256 `389a8f95cb81b2d638bf382ececb559776f6513334fd4afa3641e16a6351fbc3`.
- Server REQUIRED, domain recreated-alpha, separate root-only service credential, local Backend URL.
- Raw Minecraft TCP 127.0.0.1:25565; stunnel public 186.246.28.214:25565, certificate api.recreated.ru. Non-root service with systemd LoadCredential.
- Root certificate-refresh hourly timer copies Caddy-renewed certificate/key into protected files and restarts gateway only when changed. Enabled at boot. Actual renewal/reboot not exercised this turn.
- Caddy exposes only exact public game capability/ticket/logout routes; private internal game routes remain excluded.
- Signed game release alpha-0.1.2 sequence 3: only CCEC JAR and launch.json changed, no removals. Existing alpha-0.1.1 immutable artifacts retained.
- Versioned Launcher 0.1.0-4b.1 installer published; stable latest updated. SHA-256 `77609280096eb7db966509161b94bfdd008523aaff8a43bef9a6747112d94b5d`.

## Actual checks

Backend test/build/postgresTest pass after supplying local dev PostgreSQL test credentials; first PostgreSQL attempt failed authentication due to missing credentials. No production DB used by these tests.
Production Backend health UP (PostgreSQL UP), version 0.1.0-SNAPSHOT; V7 and recreated-alpha boot registration confirmed.
Minecraft exact startup invocation reached Done, REQUIRED config confirmed. Public gateway, raw loopback TCP, Sable UDP 25565, Voice Chat UDP 24454 confirmed listening. Firewall unchanged. PostgreSQL/Backend remain loopback-only; Bot remains active and WireGuard configuration untouched.
TLS hostname/certificate verification passes both VDS OpenSSL and Windows SslStream (TLS 1.3). Installer downloaded from public HTTPS to Windows and hash verified by publisher. Private boot GET returns public 404; public POST isolation is enforced by reviewed exact Caddy matchers, not claimed as runtime POST-tested.

Initial Caddy candidate validation failed because inline blocks were invalid. Cutover stopped with backups intact before service startup; corrected only route syntax, revalidated and resumed controlled startup. Existing third-party recipe/loot/Sable tag errors remain nonfatal; world reached Done. No production player admission, replay/duplicate attempt, game audio or GUI account login was tested this turn.

## User production E2E

Install 0.1.0-4b.1, authenticate/trust device, update to alpha-0.1.2, then Play. Connect to `api.recreated.ru:25565`, not a raw IP/old endpoint. Verify new technical identity, fresh player state and expected absence of old ownership; verify Sable/Voice Chat with a second client. Close Launcher while already connected and verify game connection survives. A subsequent reconnect requires an available authorized Launcher. Try old/non-authorized client and confirm admission is denied. Stop after acceptance feedback; no Master City reset or extra privileges applied automatically.
