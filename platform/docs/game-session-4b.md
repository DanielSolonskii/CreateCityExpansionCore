# Game Session 4B — local implementation, not deployed

## Approved refinements of ADR 0004 / 4A

The user approved permanent technical Minecraft aliases (`cs` + 14 lowercase
alphanumeric characters), with migration of old playerdata/ownership explicitly
deferred. The user also approved TLS directly in the Minecraft Java channel,
instead of a Launcher loopback TCP tunnel. These are explicit refinements, not a
silent replacement of 4A. Production remains unchanged.

## Flow

1. Native Launcher reads its existing endpoint-scoped trusted-device credential
   from Windows Credential Manager. Password-only login is not a game credential.
2. Backend validates the trusted device, ACTIVE platform account and Telegram
   binding, then issues a 10-minute native-only Launcher capability and immutable
   Minecraft profile. Existing accounts/login/registration remain unchanged.
3. Minecraft starts with the technical alias and corresponding offline UUID.
   Arguments contain TLS host/port only: no device credential, capability or ticket.
4. CCEC reserves the actual login profile before vanilla duplicate-player eviction.
   A second connection cannot evict the first. REQUIRED always installs a mandatory
   CONFIGURATION task, including when the client lacks the auth payload channel.
5. CCEC creates a fresh server boot ID, connection ID and random 256-bit nonce.
   The public challenge crosses stdout; native Launcher exchanges its capability
   for a 120-second ticket and writes the proof to child stdin only.
6. Proof crosses Minecraft TCP protected by standard Java TLS with normal
   certificate and hostname validation. A TLS gateway forwards to private raw TCP.
   No loopback client tunnel: the original gateway remote address remains available
   to UDP mods. Closing Launcher does not close the established game connection;
   reconnecting requires an available authorized Launcher broker.
7. CCEC asks Backend to consume the ticket using a separate server service secret.
   Backend locks device/user/capability/boot/ticket, verifies every binding, and
   returns the authoritative platform/Minecraft identity. Only then may CCEC finish
   CONFIGURATION and create the player.

This authenticates an account and protocol, not an unmodifiable executable.
Someone implementing the protocol with their own legitimate credentials is not
prevented from using an alternative binary. It does prevent admission without a
valid account capability and spoofing another player's alias/UUID.

Remote-address preservation here means the **client's** Minecraft channel still
sees the actual game gateway IP/port (needed by UDP mods). A standard TCP gateway
does not preserve the server-side TCP peer IP: vanilla sees the loopback gateway.
Sable authenticates its separate UDP sender independently. Do not assume existing
vanilla IP bans or TCP source-IP diagnostics retain their previous semantics;
audit any such policy during controlled cutover. No untrusted forwarded-IP field
is accepted as an identity or authentication decision.

## API and V7

- `POST /api/auth/devices/{id}/game-session-capability`: existing device Bearer
  credential; returns short `bearer`, `expiresAt`, authoritative account `username`
  and `profile`. Native Launcher checks that this is its displayed authorized account.
- `POST /api/game-sessions`: Launcher capability Bearer;
  body `{serverId, bootId, connectionId, nonce}`; returns ticket ID/secret/expiry.
- `DELETE /api/launcher/sessions/current`: revokes the supplied short capability.
- `POST /api/internal/game/boot`: private server-authenticated boot registration.
- `POST /api/internal/game/sessions/consume`: private server-authenticated consume;
  includes the ticket, challenge tuple, actual login UUID and exact join name.

`X-Game-Service-Secret` is separate from `BOT_BACKEND_SECRET`; neither service
credential is accepted for the other's endpoints. Private game routes must never
be added to the public reverse-proxy allowlist. Responses are `no-store`.

V7 adds nullable unique `minecraft_identity.join_name`, `launcher_session`,
`game_server_boot`, and `game_session`. Secrets/nonces are stored as domain-separated
SHA-256 hashes only. V1–V6 are unchanged. An existing Minecraft identity without
the approved alias is rejected, not silently rewritten. New identities are assigned
the canonical offline UUID derived from their permanent alias.

Same-attempt consume retry is idempotent; different connection/nonce/boot is denied.
Expiry, revoked device/capability, non-ACTIVE user, wrong profile or Backend outage
deny new admission. Pending attempts are bounded and timed out. Verified players
are not disconnected merely because a short issuance credential expires or Backend
goes offline. Immediate revocation of already-online players is outside this slice.

## Configuration (future controlled deployment only)

Backend protected EnvironmentFile: `GAME_SERVER_ID` and strong independently
generated `CCEC_BACKEND_SECRET`. Never reuse the bot secret. CCEC needs the same
server secret in its own protected environment, not a synced config or JVM argument.

World server config `createcityexpansioncore-authentication.toml`:

```toml
[authentication]
mode = "REQUIRED"
backendBaseUrl = "http://127.0.0.1:8080"
serverId = "recreated-alpha"
```

Set `CCEC_AUTH_TRANSPORT=TLS_GATEWAY` in the protected Minecraft EnvironmentFile.
TCP binding precedes loading world SERVER configs: the required vanilla TCP-only
mixin forces its ServerBootstrap to `127.0.0.1`, and REQUIRED checks that this
binding actually ran for this server. Missing transport configuration fails startup.
Keep `server-port=25565` and `server-ip=` (wildcard declaration) so Sable's separate
Datagram bootstrap retains public UDP 25565. Only vanilla TCP is overridden.
TLS gateway binds specifically `186.246.28.214:25565`, not `0.0.0.0:25565`, and
forwards to `127.0.0.1:25565`; validate that the public IP is assigned locally
before deploying this template. Same TCP/UDP port matters: the current Sable
client derives its UDP destination port from the Minecraft TCP remote endpoint.

REQUIRED requires offline-mode and proven private TCP binding. The world
gets a public domain marker; it cannot later start with DISABLED or another domain.
Local development must use a **separate world** with explicit `mode = "DISABLED"`.
It makes no Backend requests; do not remove a production marker to bypass admission.

Build candidate Launcher with these public, non-secret settings:

```powershell
$env:VITE_ACCOUNT_API_BASE_URL='https://api.recreated.ru'
$env:RECREATED_GAME_TLS_HOST='api.recreated.ru'
$env:RECREATED_GAME_TLS_PORT='25565'
$env:RECREATED_GAME_SERVER_ID='recreated-alpha'
npm run tauri build -- --bundles nsis
```

`api.recreated.ru:25565` is a proposed future TLS game endpoint, **not currently
deployed**. Add/select this endpoint in Minecraft. No Game Session ticket is put
in the server list. Only TLS TCP changes; Sable UDP 25565 and Voice Chat UDP keep
their existing approved ports/routes. PostgreSQL and Backend remain private.

## Controlled deployment gate

1. Back up PostgreSQL, Backend/Launcher/CCEC artifacts, world and relevant config.
   Resolve legacy world identity migration separately before inviting existing players.
2. Deploy Backend with V7 and independent game service credential; test existing
   login/registration plus new private boot/consume. Expose only the three public
   Launcher routes above through Caddy, preserving all existing exact matchers.
3. Prepare Java-trusted certificate/key for the TCP TLS gateway and automated
   renewal. Root-only private key, non-root gateway. Stage the supplied stunnel/unit
   templates; do not install or enable them from a normal build.
4. Controlled stop, matching CCEC client/server JAR and REQUIRED server config,
   proven loopback raw TCP via the TCP-only mixin and protected transport variable.
   Verify boot readiness, TLS, admission, Sable UDP authentication and normal save/stop.
   Keep existing public TCP/UDP 25565; public TCP now belongs to the TLS gateway,
   never to the raw Minecraft listener. Do not change SSH/WireGuard routes.
5. Publish the matching approved client release and candidate Launcher explicitly,
   not by overwriting immutable existing installer/game releases. Verify two clients,
   duplicate/replay/wrong-name denial, Launcher-close survival, UDP mods, restart.
6. Rollback is controlled: stop services and restore matching artifacts/config and
   database backup if required. Do not disable authentication on a marked world or
   switch a client channel automatically after failed server admission.

No production commands were executed for this implementation.

## Verification — 2026-10-04

- Backend `gradlew test build postgresTest`: PASS, 76 ordinary tests and 67 real
  PostgreSQL tests, zero failures. Includes atomic/idempotent consume, concurrent
  attempts/provisioning, wrong tuple/name/UUID/secrets, boot restart, expiry,
  revocation, service credential isolation, legacy identity refusal and V7 constraints.
- Frontend `npm test`: 46 PASS; production `npm run build`: PASS.
- Native `cargo test --lib -- --test-threads=1`: 20 PASS, 3 explicit manual tests
  ignored. The native helper test was then run explicitly against the actual Tauri
  candidate: PASS. The other two ignored tests remain unrun (prepared-PC manual
  launch and live production-baseline updater probe).
- Native broker test uses real local HTTP and a temporary endpoint-scoped Windows
  Credential Manager record, verifies private pipe proof and logout tombstone.
- CCEC `gradlew build`: PASS, including `checkConnectionAdmission` and existing
  headless checks. Headless checks alone do not establish gameplay acceptance.
- Real full-modpack NeoForge fixture reaches Done; early duplicate NEW is denied,
  absent auth channel and forged CONFIGURATION finish cannot create a player.
- Real modded Minecraft client and Java TLS test gateway: hostname mismatch rejected;
  standard TLS on the actual channel, unchanged remote IP/port, private stdin proof,
  CONFIGURATION consume and player creation PASS. Gateway uses `127.0.0.2:25585`,
  raw TCP `127.0.0.1:25585`; Sable's untouched wildcard UDP listener on 25585
  authenticates successfully. Closing IPC does not break the established connection.
- Real dedicated local DISABLED world starts without Backend/service secret;
  the same fixture world with a domain marker rejects DISABLED startup. All tests
  stop/save their isolated worlds; production/reference worlds are never used.

Runtime transport tests use a fake local authority with fixed test credentials;
Backend authority and native broker are independently covered by their real HTTP/
database tests. A complete production GUI → Backend → VDS run is **not** claimed.
Voice Chat two-player audio and the Linux stunnel/Caddy templates still require
controlled deployment validation. Existing unrelated mod warnings were not removed.

Windows NSIS candidate (built, not installed or published):
`launcher/src-tauri/target/release/bundle/nsis/RE-CREATED Launcher_0.1.0-4b.1_x64-setup.exe`
SHA-256: `77609280096eb7db966509161b94bfdd008523aaff8a43bef9a6747112d94b5d`.
CCEC candidate `build/libs/createcityexpansioncore-1.0.1.jar` SHA-256:
`389a8f95cb81b2d638bf382ececb559776f6513334fd4afa3641e16a6351fbc3`.
Neither artifact replaces an approved immutable production release.

Local implementation is ready for the **controlled deployment gates above**, not
an automatic production switch. Old world identity migration remains a separate
explicit step, as approved by the user.
