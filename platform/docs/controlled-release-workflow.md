# Controlled alpha release workflow

Explicit operator commands only. No build/commit hook publishes or stops a
service. Existing Launcher manifest schema, pinned Ed25519 key, Backend,
Minecraft launch scripts and Telegram protocol are unchanged.

Run PowerShell from `E:\CreateCityExpansionCore_MERGED\CreateServerPlatform\launcher`.

## Prepared sources

```text
release-input/
  client/                           full prepared client runtime/modpack
    launch.json
    instances/alpha/mods/*.jar
    instances/alpha/config/...
    instances/alpha/defaultconfigs/...
    libraries/ assets/ versions/ runtime/java-21/ ...
  server/                           ONLY deliberately selected server changes
    mods/*.jar
    config/...                      replacements, not client seeds
    defaultconfigs/...
    remove.json                     optional pinned removals
```

Client source is initialized from signed alpha-0.1.0 artifacts, not by copying a
player's whole `.minecraft` or Recreated. No saves/options/accounts are imported.
`release-input/` and generated `releases/` are ignored by Git. Put a new client
mod into `client/instances/alpha/mods/`; put the same shared mod into
`server/mods/`. Client-only mods must never be copied to the dedicated server.
Place each side's config under its corresponding config directory. Nested
voicechat `.properties` is supported without manually editing manifest or
launch.json. Shared same-named client/server mod bytes must match.

If changing a mod filename/version, remove the old JAR from the curated client
source. On the server, removal is explicit, with a hash precondition:

```json
[{"path":"mods/old-mod.jar","expectedSha256":"<old file SHA-256>"}]
```

This is an operator removal request, not a hand-authored release manifest. The
server never sweeps its mods/config directories. Unlisted server files remain.

For a new local source from a saved signed manifest (one-time, empty target):

```powershell
node scripts/init-release-source.mjs --manifest releases/workflow/previous-alpha.json --instance release-input/client --objects releases/releases/objects
```

Missing cached blobs download through the signed HTTPS URLs and verify size/hash.
Existing target is refused; source initialization is atomic and copies (not
hardlinks) blobs. Changes to a source cannot mutate the immutable blob cache.

## 1. Dry-run

```powershell
.\scripts\Release.ps1 dry-run alpha-0.1.1
```

Fetches/verifies the current signed alpha manifest; sequence defaults to current + 1.
Rebuilds launch.json's mod inventory in LOCAL staging, never in source.
Existing libraries/runtime hashes must still match the prepared official
installation. Uses the existing allowlist publisher, calculates SHA-256/size,
and lists added/changed/removed client entries and new bytes/blobs. With server
candidates present, read-only SSH compares them against actual server files and
lists added/changed/removed server operations. No remote upload, service action,
signing or channel switch occurs in dry-run.

Output: `releases/workflow/prepared/<version>/plan.json` and
`server-dry-run.json`. Drafts may be corrected/rebuilt before publication;
published VDS versions remain immutable. `-Sequence N` is optional and must
exceed the current sequence. `-Instance` / `-ServerSource` override source paths.
Offline preview requires `-Offline -Previous <saved-signed-manifest>` and cannot
verify the live server diff. It does not pretend server candidates were deployed.

Client config entries remain **seed**: existing client settings are never
overwritten/deleted; changed seeds only affect missing files/new installs. A
mandatory migration of an existing client config is a separate future feature.
Dry-run's removed entries mean removed from manifest; only prior owned managed
files are eligible for actual client deletion.

## 2. Explicit server deployment

```powershell
.\scripts\Release.ps1 server-deploy alpha-0.1.1
```

Uploads a unique root-only package/helper directory. Validates allowlist,
hashes/size, current-file preconditions, free space and known systemd service
user/directory/console stop before replacing anything. Runs the existing
`systemctl stop`/console `stop` mechanism, confirms process exit/save. Then makes
one consistent stopped-world backup and backups of only changed/removed files.
Installs files with service-compatible ownership/permissions, starts the service
and checks **Done from this exact systemd invocation**, rejecting crashes,
automatic restarts and known fatal startup markers. Timeout defaults to 180s.

No operations means no stop/world backup, but current invocation readiness is
still required. Any failure after stop leaves a
protected state/report and keeps the failed deployment stopped where possible;
never kill -9, remove mods to force a green start, or automatically promote
clients. Failed/unresolved transactions block another deployment until explicit
rollback/reconciliation. Report includes backup path and `channelChanged:false`.
Disk checks include world + changed-file backups + installation headroom.

Backups: `/opt/create-server/backups/server-deploy-<uuid>/world.tar`, `files/`,
`state.json`. These are not periodic/offsite backups. Retention is manual; no
automatic backup deletion is introduced.

## 3. Explicit client publication

```powershell
.\scripts\Release.ps1 publish alpha-0.1.1
```

For a client+server candidate, requires a successful server deployment receipt
for the SAME client/server hashes and rechecks actual server files/current Done. Changing
the source or rolling server files back invalidates promotion. Client-only
releases with no server candidates do not stop/deploy the server.

Inventories existing remote objects; transfers only absent SHA-256 blobs.
Protected remote signing key stays `/opt/create-server/secrets/release-signing.pem`;
its derived public key must match Launcher's pinned public key. The publisher
checks the expected previous signed manifest (compare-and-swap guard), strictly
greater sequence and immutable version. It validates all received/reused blobs,
installs immutable artifacts, re-reads hashes at final storage, signs the new
manifest, and verifies the candidate manifest through public HTTPS. Only THEN
the final atomic symlink replacement switches alpha. Caddy is not reconfigured.
Any pre-commit error leaves the old channel; unpromoted immutable candidate
artifacts can remain for inspection/retry. No object garbage collection occurs.

Local workflow lock + VDS client/server locks prevent same-flow concurrent
mutations. Uploads use unique UUID directories, never a shared upload filename.
Post-commit staging cleanup errors are warnings, not a false publication failure.
If SSH disconnects near commit, status is UNKNOWN: inspect the public channel
before retrying; do not assume a failed transport implies no server-side commit.

Existing `Publish-Release.ps1` remains a compatibility entry point into this
controlled client publication command. Builds/tests/commits never invoke it.

## 4. Stop or rollback

Before publish: simply stop after dry-run/server checks; alpha is unchanged.
On server failure, inspect the protected state and journal, then explicitly:

```powershell
.\scripts\Release.ps1 server-rollback -Backup /opt/create-server/backups/server-deploy-<uuid>
```

Default rollback restores only changed server files and checks Done again.
It preserves the current world. Independent modifications cause a conflict and
are not blindly overwritten. If world restoration is actually needed, add
`-RestoreWorld`: validates the archive hash and preserves the current world under
`world.before-rollback-<uuid>` before restoring the backup. This is a deliberate
gameplay rollback; review lost progress implications first. Never delete the
preserved world automatically.

After successful client publication, **do not point alpha backwards to a lower
sequence**: already updated Launchers reject it. Use an explicit forward recovery
release (new version, higher sequence) whose curated client files match the last
good signed artifacts. `init-release-source.mjs` can initialize that recovery
source from an older version's signed manifest and cache. Roll server files back
separately; verify, dry-run, then publish the recovery release explicitly.

Voice chat's future UDP port/firewall and mod compatibility require separate
approval/testing during the real alpha-0.1.1 release. This workflow neither opens
ports nor modifies SSH, Minecraft/Sable routing, Backend or Telegram.

## Verification, 2026-10-03

- 6 Node checks: automatic inventory/configs, immutable source, delta archive,
  signature/sequence rejection, client/server drift, safe initialization.
- 19 Python checks on isolated Linux staging: real test Ed25519 signing,
  atomic channel swap/failure/concurrency, corrupt new/reused hashes, stale
  predecessor/sequence, fake HTTPS failure, archive paths, fake service stop,
  consistent world/file backups, Done/current invocation checks, file/world
  rollback and conflict preservation. No production key/service was used.
- Windows Python checks cover server logic/readiness; Linux channel primitive
  checks require Linux (Windows symlink creation is unavailable in this session).
- Frontend 36 tests + TypeScript/Vite build; native Launcher code was unchanged.
- Actual command dry-run uses a fictitious local version, with read-only server
  comparison; production alpha remains alpha-0.1.0. No alpha-0.1.1 is published.

```powershell
npm run test:release
python scripts/test_release_workflow.py
npm test
npm run build
```

Python server helpers require Python 3.12+ / OpenSSL on Ubuntu. Local Node tooling
uses the already installed Node runtime and tar/ssh/scp. Full new-mod runtime
acceptance is intentionally deferred to the explicitly approved real release.
