# Production Windows Launcher delivery

2026-10-04 Europe/Minsk. First approved release: 0.1.0, Windows x64 NSIS installer.
No new DNS/domain: existing Caddy/TLS on api.recreated.ru reused.

- Stable latest: https://api.recreated.ru/launcher/latest/RE-CREATED-Launcher-x64-setup.exe
- Immutable release: https://api.recreated.ru/launcher/versions/0.1.0/RE-CREATED-Launcher-x64-setup.exe
- Metadata: https://api.recreated.ru/launcher/latest/release.json
- SHA-256: `f1f03a491456eaaf11ead12a808e9420abf2c7e42b85badee25b2c0be452a1c0`
- Size: 5753265 bytes.

Current source installer:
`launcher/src-tauri/target/release/bundle/nsis/RE-CREATED Launcher_0.1.0_x64-setup.exe`.
This is the approved phone-verification production build, not rebuilt/replaced here.
SHA-256 gives transfer integrity, not an Authenticode signature; this workflow does
not introduce executable signing or Launcher self-update.

## Server structure

```text
/opt/create-server/launcher-distribution/
├── versions/0.1.0/
│   ├── RE-CREATED-Launcher-x64-setup.exe
│   └── release.json
└── latest -> versions/0.1.0
```

Launcher artifacts are independent of `/opt/create-server/distribution/releases`
game manifests/blobs/alpha channel. Only existing HTTPS infrastructure is reused.
Caddy serves GET/HEAD, disables directory browsing, uses attachment headers for
installer and no-store to avoid stale latest downloads. Route implementation follows
[Caddy file_server documentation](https://caddyserver.com/docs/caddyfile/directives/file_server).
Only public release data lives inside this root; no credentials/staging/backups.
Files 0444, release directory 0555, owned by root; Caddy has read access only.

## Explicit publication

Build/test a new installer and approve it first. Do not reuse 0.1.0 for different
bytes. From the Platform root in PowerShell:

```powershell
.\infra\launcher\publish-launcher.ps1 -Version 0.1.1 `
  -InstallerPath 'C:\path\to\approved-installer.exe' -DryRun

.\infra\launcher\publish-launcher.ps1 -Version 0.1.1 `
  -InstallerPath 'C:\path\to\approved-installer.exe' -Approve
```

SSH uses existing key authentication; no private key, password or signing key is
copied/saved. Default target root@186.246.28.214; optional `-SshTarget user@host`
requires equivalent publication permissions and the existing route/layout.
No ordinary build, commit, game publish or service startup invokes this script.

Publication stages the installer outside the web root, verifies its uploaded hash
and PE magic, then checks the stored hash again. A server-side lock serializes
publication; duplicate versions are rejected, never overwritten. Complete release
directory is renamed into versions, then a temporary symlink is atomically replaced
as latest (installer and release.json switch together). Hash/version validation
failure leaves previous latest untouched. A crash after release creation but before
pointer activation may leave an immutable inactive release for manual inspection.
After publication, the PowerShell command performs a real HTTPS download to Windows
and compares its SHA-256. Failed post-publication HTTP verification must be investigated
before distribution; it does not silently undo a successfully activated pointer.
No service restart is needed to publish the next installer.

## Verification and rollout

- Two Linux staging tests passed: immutable release/hash rejection/latest preservation
  and second-version activation; unsafe paths/non-EXE rejected. No test versions published.
- PowerShell dry-run passed without upload; approved 0.1.0 explicitly published.
- SHA verified on VDS and after full Windows HTTPS download; latest/versioned HEAD 200,
  Content-Length 5753265, attachment/no-store/nosniff headers; HTTP 308 -> HTTPS.
- Windows download retained at
  `C:\Users\Administrator\AppData\Local\Temp\recreated-launcher-verify-d72ee506462e419a89ca37e7140e1110.exe`.
- Bot LAUNCHER_DOWNLOAD_URL set to stable latest only after successful public download;
  all other environment values compared unchanged, secrets remain root-only 0600.
- Caddy validated/reloaded; only Telegram Bot restarted. Backend/Minecraft MainPIDs,
  game alpha manifest SHA, backend artifact/config and WireGuard configuration/policy
  rules/default route compared unchanged. No Backend API/PG schema/game updater change.

Backup: `/opt/create-server/backups/launcher-download-20261003T223557Z` (root-only 0700),
containing original Caddyfile, protected Bot env and integrity snapshot. Rollback of
configuration can restore these originals after checking for later secret rotations.
Do not delete historical installer releases. To roll back latest, explicitly verify
the desired old version against its stored release.json SHA, then atomically replace
latest symlink to `versions/<approved-old-version>`; never copy over a versioned file.
This publication workflow does not automate rollback or alter game channels.
