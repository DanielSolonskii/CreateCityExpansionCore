# Approved Launcher releases

Game distribution (`/releases`, alpha sequence) and Launcher installer updates
(`/launcher`, separate sequence) are independent. They share the existing pinned
Ed25519 public key. The private key remains `/opt/create-server/secrets/release-signing.pem`
on VDS, mode 0600; it is never downloaded or stored in source.

## Production build

`npm run tauri -- build` builds NSIS current-user installer. Release Rust build
embeds `https://api.recreated.ru`, TLS `api.recreated.ru:25565`, server
`recreated-alpha`. Conflicting environment overrides fail the build. The hostname
is the certificate SAN, not an arbitrary Minecraft address. Update Cargo,
tauri.conf.json, package.json/package-lock.json versions together. Set
`RECREATED_BUILD_ID` to the intended release revision. Version/build appear in UI.

## Explicit publication

From `CreateServerPlatform`, first stage, never activate before installer E2E:

```powershell
./infra/launcher/publish-launcher.ps1 -Version 0.1.4 -Sequence 4 -Mandatory -Stage -InstallerPath './launcher/src-tauri/target/release/bundle/nsis/RE-CREATED Launcher_0.1.4_x64-setup.exe'
# Only after upgrade AND clean-install acceptance:
./infra/launcher/publish-launcher.ps1 -Version 0.1.4 -ApproveExisting -InstallerPath './launcher/src-tauri/target/release/bundle/nsis/RE-CREATED Launcher_0.1.4_x64-setup.exe'
```

Next release increments both semver and independent Launcher sequence. Existing
versioned installers are immutable. Stage calculates/checks uploaded SHA-256,
signs metadata using the pinned key, and verifies real public download. Approval
rechecks artifact and signature, then atomically switches `latest` symlink.
Build/commit never publishes. A failed stage leaves current latest untouched.

Stable installer: `https://api.recreated.ru/launcher/latest/RE-CREATED-Launcher-x64-setup.exe`.
Startup manifest: `https://api.recreated.ru/launcher/latest/update.json`.

```json
{"manifest":{"schemaVersion":1,"kind":"launcher","version":"0.1.4","sequence":4,"installerUrl":"https://api.recreated.ru/launcher/versions/0.1.4/RE-CREATED-Launcher-x64-setup.exe","size":123456,"sha256":"64 lowercase hex characters","mandatory":true,"approved":true},"signature":"128 lowercase hex characters"}
```

Signature is strict Ed25519 over UTF-8 canonical JSON (sorted object keys, compact,
no ASCII escaping). Installer URL must match exact pinned HTTPS origin/versioned
path; redirects are rejected. Size is bounded; SHA checked after full download and
again by helper. Highest verified sequence/version is persisted separately from
game receipts to reject downgrade/replay of older known releases. TLS plus pinned
signature prevents substituted metadata. This is not Windows Authenticode signing.

## Install/recovery

Startup checks automatically; user explicitly selects download/install. Mandatory
verified update disables Play until installation. Network/check failure is shown,
not treated as "up to date". Installer is never run before signature/hash validation.
Native command accepts no URL/path supplied by WebView. Close Minecraft first.

Cache: `%LOCALAPPDATA%/RE-CREATED Launcher Updater/job-<uuid>`. Copied Launcher
helper waits for parent exit, re-verifies package, backs up the old EXE, runs verified
NSIS installer into existing current-user installation, starts new Launcher and
waits for frontend version-ready confirmation. Install/start/readiness failure
restores the previous EXE and restarts it; `failure.txt` and backup are retained.
Unexpected interruption retains downloaded installer/helper/previous.exe for
manual recovery; never delete the backup until the new release is accepted.
Rollback restores the executable, not necessarily installer registry metadata.
Explicitly install the previous immutable installer if full installer repair is needed.
No updater code writes game instance, user options or Windows Credential Manager.

Portable/development binaries cannot self-install. Existing public 0.1.0 installers
have no self-updater: players must install this first updater-enabled release once.

## Acceptance gate

Use a local `self-update-test` feature build at older semver with compile-time
`RECREATED_SELF_UPDATE_TEST_URL=https://api.recreated.ru/launcher/versions/<candidate>/candidate.json`.
This test-only override is absent in production builds. Verify download/hash,
NSIS upgrade/restart/new version, automatic trusted-device login and real 4B join.
Also test candidate installer into an empty installation directory. Do not clear
Credential Manager or game instance to simulate clean binary installation.
Real fresh-PC game dependency installation is outside this task.

Stage signs `candidate.json` with `approved:false`. Production clients reject that
metadata, even if validly signed. After E2E, approval signs and adds `update.json`
with `approved:true` (no overwrite of installer/candidate). Only then latest switches.

## 2026-10-04 release acceptance

0.1.4 fixes updater controls obscured by the fixed titlebar. Required Launcher
update blocks actual Play, not game-file verification. User explicitly authorized
publication with manual game E2E after release. Automated frontend/native/publisher
checks do not imply completed upgrade/clean-install/4B gameplay acceptance.
Local 0.1.2 was a test-only build pinned to candidate 0.1.3: install approved 0.1.4
manually once instead of treating that staging build as a production updater.
