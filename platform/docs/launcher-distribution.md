# Launcher game distribution — alpha-0.1.0

## Published baseline

Minecraft 1.21.1, NeoForge 21.1.251, Java 21, 41 mods. Production channel:
`https://api.recreated.ru/releases/channels/alpha/manifest.json`.
The signed payload contains 4439 files / 1426094686 bytes. The baseline CCEC
SHA-256 is `8113289f9a055ba0fc8f69903272986378d6b26ba5848dcbe8a3765542112d37`.

This is an explicitly approved interim static artifact publisher. Architecture's
future backend/PostgreSQL release registry is not implemented or redefined:
signed immutable artifacts currently provide release authority, served by Caddy.
Backend account authority and API are unchanged.

## Manifest format

```json
{
  "manifest": {
    "schemaVersion": 1,
    "version": "alpha-0.1.0",
    "sequence": 1,
    "platform": "windows-x86_64",
    "minecraftVersion": "1.21.1",
    "neoForgeVersion": "21.1.251",
    "files": [
      {
        "path": "instances/alpha/mods/example.jar",
        "size": 123,
        "sha256": "<64 lowercase hex characters>",
        "url": "https://api.recreated.ru/releases/objects/<sha256>",
        "mode": "managed"
      }
    ]
  },
  "signature": "<Ed25519 signature in hex>"
}
```

Example entries are illustrative, not a deployable manifest. Signature covers
recursively key-sorted compact JSON of `manifest`; public key is pinned in
Launcher. HTTPS, same-origin content-addressed URLs and SHA-256 are mandatory.
Sequence prevents rollback relative to the last accepted local receipt.

`managed`: launch.json, explicitly listed libraries/assets/versions/Java runtime,
mod JARs and CreateServerQuests resourcepack. Changed/missing files are repaired.
`seed`: allowlisted config/defaultconfigs; installed only when absent, never
overwritten or removed. Updating existing user configs requires a separate
explicit migration, not silently switching them to managed.

Options, saves, screenshots, logs, caches, credentials, archives such as
modsv06.rar and unlisted user files are outside ownership. No directory sweeping.
Deletion requires prior signed ownership receipt and a currently allowed path.
Traversal, ADS, reserved Windows names, duplicate paths and reparse points are
rejected. All downloads are staged and hash-verified before any live replacement.
Same-volume atomic file replacements have a recovery journal; failed batches
roll back. Exclusive game.lock covers update and game launch, including another
Launcher process. Running games cannot be updated.

## Publication

VDS layout:

```text
/opt/create-server/distribution/releases/
  objects/<sha256>
  versions/alpha-0.1.0/manifest.json
  channels/alpha -> ../versions/alpha-0.1.0
```

Caddy serves only static GET/HEAD under `/releases/`. Existing API routes remain
unchanged. Signing private key is server-only root-readable
`/opt/create-server/secrets/release-signing.pem`; never copy it into source/docs.
Public key: `launcher/src-tauri/release-public-key.hex`.

Prepare a curated release source separately from the player's installation.
The new [controlled workflow](controlled-release-workflow.md) automatically updates
the staged launch.json mod list and hashes from the curated mods directory. The
lower-level publisher still follows that explicit inventory. Keep generated payloads
and runtime source snapshots outside Git. Version and sequence are immutable;
new content requires a new version and greater sequence.

From launcher directory, for an approved future release:

```powershell
.\scripts\Release.ps1 dry-run alpha-X.Y.Z -Instance C:\path\to\curated-instance
# Separate explicit actions, after inspecting the plan:
.\scripts\Release.ps1 server-deploy alpha-X.Y.Z -Instance C:\path\to\curated-instance
.\scripts\Release.ps1 publish alpha-X.Y.Z -Instance C:\path\to\curated-instance
```

Replace placeholders with the approved version. Sequence defaults to current + 1.
For client-only changes with no server candidates, omit server-deploy. The wrapper builds an
allowlisted manifest, inventories remote hashes, transfers only new objects,
validates all objects on VDS, signs there, and atomically switches the channel.
Existing production version payloads cannot be mutated. Channel backup, unique
upload paths, locks and an expected-previous guard protect publication. Caddy is
not reconfigured by this workflow. Source hashes are checked before publishing.
Release artifacts are public: do not put secrets into the curated source.

Developer helpers: publish-release.mjs (snapshot), package-release.mjs (delta),
deploy-release.py (server validation/signing/channel), verify-live-release.mjs
(external signature and representative object checks).

## Verified 2026-10-03

- Rust: 13 passed; 2 opt-in smoke/probe tests ignored in ordinary suite.
- Frontend: 36 passed; TypeScript/Vite and Windows Tauri NSIS/MSI builds passed.
- Live HTTPS: signature and launch.json / Java / CCEC / asset index hashes passed.
- Actual baseline: zero downloads; isolated full copy with changed CCEC and
  missing asset index repaired two files using production HTTPS; options intact.
- Negative tests: bad download SHA rejected before applying any files; signature,
  rollback, unsafe paths, ownership cleanup, interrupted recovery and lock tested.
- User confirmed GUI `Готово · alpha-0.1.0` after account login/update check.

Fresh-install logic is tested on fixtures; complete clean-PC bootstrap has not
yet been accepted on a separate PC. Current prepared standalone model supplies
official Minecraft/NeoForge/Java payloads. No Microsoft authentication, Game
Session/4B, voice chat alpha-0.1.1 or Launcher self-update was added.
