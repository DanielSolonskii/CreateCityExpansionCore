# Launcher profile and background launch v1

Local candidate: Launcher **0.1.6**, build
`production-profile-background-20261017`.

Implementation and artifacts are prepared. This task did not change production,
publish a Launcher update, apply Flyway V13 to production, restart Minecraft, or
commit/push the repositories.

## Behavior

- **Play** starts one native job: signed reconciliation, changed-file download,
  verification, installation and Minecraft launch. Rust owns the full chain;
  WebView timers/focus do not dispatch the next stage.
- Play returns STARTING immediately. Native completion events update the UI;
  polling and focus refresh recover current status on return to the window.
- Minimize/hide remains available. Closing the window while preparing hides it
  in the tray instead of terminating the update. Explicit tray exit while busy
  restores the window; exiting is allowed after the task finishes.
- Background completion/error uses a native Windows notification, subject to OS
  notification settings. The job does not require notification delivery to finish.
- Default-on auto-start is an additive setting. Existing settings deserialize
  with auto-start enabled. When disabled, preparation stops at READY; the next
  Play launches the game. Existing hide-after-launch settings are preserved.
- Full check/repair is available in Settings, including asset verification.
  It does not overwrite existing config seeds or player files.
- A mandatory Launcher update continues to block actual Play while allowing game
  file preparation, preserving the existing update contract.

## Verification and performance

Managed Java, mods, libraries and launch descriptors are SHA-256 checked on each
launch. The verification pass creates an in-memory proof tied to the expected
signed hashes and file metadata. The launcher checks that proof before using
launch files instead of reading the same code bytes a second time. Proofs are not
restored from the persistent asset cache. Account changes during preparation
prevent launching for the preceding account.

Only `assets/objects/<prefix>/<content hash>` can use the persistent index at
`.recreated/asset-index-v1.json`. A hit requires expected manifest SHA-256, file
size, modification/creation stamps and a verification age under seven days.
Changed/missing/expired entries are hashed. Full repair bypasses acceleration.
Metadata acceleration is not a cryptographic integrity proof for asset contents;
critical code never uses it. Disk readers are bounded to two workers.

Read-only release-mode measurement against the prepared **alpha-0.1.22** source,
whose descriptor was aligned with the already signed published manifest:

| Measure | Strict pass | Warm asset cache |
| --- | ---: | ---: |
| Files | 4448 | 4448 |
| Elapsed | 3257 ms | 1538 ms |
| Hashed bytes | 1,443,916,961 | 619,145,188 |
| Cached assets | 0 | 3888 |

This compares file scans on this machine, not complete cold startup, network
download or Minecraft loading. Filesystem cache/hardware affect timings. The
removed second code pass is an additional change, not included in this comparison.

## Authoritative profile

`GET /api/auth/devices/{UUID}/profile` calls the existing
`DeviceService.authenticate`: hashed device secret, TRUSTED state, trust expiry,
ACTIVE account and linked owner. The owner UUID comes only from that result;
there is no target-player argument. Response caching is disabled. Device secrets
remain in Windows Credential Manager and never enter the profile DTO/WebView.

The DTO contains the bound Minecraft identity, registration date, own city and
city role, projection freshness, measured vanilla play ticks and texture URL.
It omits money, coordinates, messages, friend lists, reports and devices.
UI distinguishes CURRENT, STALE, UNKNOWN and network failure, and does not
present a local city label as official membership. A local alias remains clearly
separate from the server-confirmed Minecraft name.

Companion snapshots append optional presentation fields. Flyway **V13** adds
nullable columns to `companion_profile`; old snapshots/constructors remain valid
and do not erase the last measured statistics. The existing bridge captures
online vanilla PLAY_TIME and current GameProfile textures every 30 seconds.
Reading the current profile handles SkinRestorer updates after login. An explicit
default/reset skin clears the old texture; offline captures retain the last known
Backend presentation. Players without a captured texture/statistic show a fallback
and unavailable-data state until a real capture occurs.

Skin requests allow only HTTPS `textures.minecraft.net/texture/<hex>`, no
redirects, bounded PNG bytes and 64×32/64×64 dimensions. Native code caches the PNG
by texture hash; the UI draws face and hat layers with pixelated sampling. It does
not guess a Mojang account from the platform UUID or player nickname.

## Source changes

Authoritative platform root:
`E:/CreateCityExpansionCore_MERGED/CreateServerPlatform`.

- `launcher/src-tauri/src/game.rs`, `update.rs`, new `update/cache.rs`: native job,
  one-operation verification, asset index and integrity boundaries.
- `settings.rs`, `windows.rs`, new `windows_notice.rs`, `lib.rs`: persisted option,
  tray lifecycle, notifications and IPC registration.
- New `profile.rs`; `device.rs`, `Cargo.toml`/lock: private native owner request,
  bounded texture loading and platform APIs.
- `launcher/src/App.tsx`, `ProfilePopover.tsx`, new `PlayerAvatar.tsx`,
  `profileGateway.ts`, profile CSS/types: official information, skin and states.
- SettingsPanel, GameUpdatePanel, launcherSettings and focused tests: controls,
  progress and compatibility.
- Backend new `launcher/LauncherProfileController.java`, V13 migration,
  CompanionService optional fields and LauncherProfileTest.
- `infra/caddy/Caddyfile`: local template for the exact GET route only. Do not
  overwrite a different live Caddy configuration with the whole template.
- CCEC `tools/companion-bridge/src/.../CompanionBridge.java`: bounded public/owner
  presentation capture. No CityManager, economy or game persistence rewrite.

## Checks and release boundary

- Frontend: 69 tests passed; TypeScript/Vite production build passed.
- Rust: 42 tests passed, 5 opt-in tests ignored; the read-only timing test was
  explicitly run separately and passed. Vault tests ran serially to avoid shared
  native-store fixture races.
- Backend: 4 profile + 10 Companion checks passed; bootJar built. These used H2;
  production PostgreSQL migration/HTTP checks remain a deployment gate.
- Companion Bridge JAR built against the current Minecraft/NeoForge classpath.
- Windows release EXE and NSIS installer built offline. No real account login,
  minimize-to-Minecraft workflow, real skin/notification or installation acceptance
  was claimed from these tests.

Release order: verified backup → Backend V13 → narrow Caddy route merged into the
actual live config and validated → bridge JAR through stopped-world backup and
readiness checks → real profile/snapshot check → approve/sign/publish Launcher.
Keep the existing authentication, WireGuard and unrelated routes unchanged.

Manual acceptance: own/no-city/offline/stale profile; registration date; SkinRestorer
change and reset; account switch; one-click Play while minimized/hidden; notification;
auto-start disabled; full repair; changed JAR; old settings; recovery after interruption.
Use disposable accounts for auth tests and do not revoke a real user's device.
