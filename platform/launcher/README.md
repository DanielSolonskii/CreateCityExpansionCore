# Create Server Launcher — RE:CREATED GUI

Existing React/TypeScript GUI and Tauri 2 desktop shell. Layout, styles, artwork,
navigation and launcher gateway remain intact. Account flow uses the actual
[account contract](../docs/account-api.md). Play now launches the prepared local
Minecraft/NeoForge instance through Rust after a signed release update check.
See [game distribution](../docs/launcher-distribution.md) for alpha-0.1.0,
managed/seed files, progress, verification and publication. Server status/news and game profile
statistics remain demo data. Milestone 4B is not implemented.

## Standalone local Minecraft baseline

See [standalone launch](../docs/launcher-standalone.md). The game installation
is `%LOCALAPPDATA%\Recreated`, independent of the reference `.minecraft`.
Minecraft 1.21.1 / NeoForge 21.1.251 / Java 21 / 41 mod JARs.
Play requires a successful login in the current native Launcher process; native
Rust uses the Backend-returned username, never local displayName or a username
parameter from the WebView. No password/platform token is given to Minecraft.
This is offline-mode launch, not account admission or Game Session.

States: STARTING (validation), RUNNING (child process alive), STOPPED (normal
exit), FAILED (validation/spawn/nonzero exit). RUNNING does not itself prove
main-menu readiness. A Windows exclusive handle prevents another process from
launching the same instance and is inherited by game stdin. Game output is
retained under Recreated/logs; Minecraft also writes instance logs. Launcher
close does not intentionally terminate the game. Java argument files contain
only a dummy offline access token and are removed on observed child exit.

Developer preparation only (not run automatically by Play):

```powershell
$env:RECREATED_JAVA_SOURCE = 'C:\Program Files\Java\jdk-21.0.12'
$env:RECREATED_NEOFORGE_INSTALLER = 'C:\path\to\neoforge-21.1.251-installer.jar'
node scripts/prepare-local-instance.mjs
```

The script refuses to overwrite an existing launch.json. It uses the official
Minecraft manifest and the official NeoForge client installer, checks published
SHA-1 downloads, imports only matching reference cache artifacts, prepares a
SHA-256 local launch manifest and imports an allowlisted modpack subset.
Installer and Java must be supplied by the developer for source preparation.
Published payloads now include the official runtime dependencies and Java;
Play checks the signed manifest and repairs managed files automatically.
Full clean-PC acceptance and Microsoft authentication remain outside this milestone.
Current Java heap is fixed at 1G/4G; the existing settings-panel controls are
not connected to this initial runtime. Names longer than 16 characters cannot
launch vanilla Minecraft and are rejected, never truncated or remapped.

## Controlled game releases (operator only)

See [controlled release workflow](../docs/controlled-release-workflow.md).
Curated source: `release-input/client`; explicit server changes: `release-input/server`.
From this directory: `scripts/Release.ps1 dry-run alpha-X.Y.Z`, then separately
`server-deploy alpha-X.Y.Z` and `publish alpha-X.Y.Z`. Sequence/mod hashes are
calculated automatically. No build/commit hook deploys anything. Existing client
config seeds are preserved. Run `npm run test:release` for Node release tooling checks.

## Account behavior

- Registration: POST /api/registrations with username/password/alphaKey.
  One-use alpha key is required for closed alpha; no user-provided Telegram ID
  or Telegram username sent to Backend. Key stays memory-only and is cleared
  with password on request completion/close; no persistent key storage.
- Shows the returned Telegram verification link and opens it through the
  system browser in Tauri (restricted to https://t.me/{bot}?start={token}).
- Polls GET /api/registrations/{UUID} with Bearer pollingToken every 3 seconds,
  sequentially; 429 backs off 15 seconds. COMPLETED/EXPIRED stops polling.
  Network/server errors pause with a retry action. Close/unmount aborts UI
  requests, clears verification data and stops scheduling. Native in-flight
  requests are bounded by 15 seconds; their late results are ignored.
- After COMPLETED explicitly asks the user to log in; registration is not a
  fake login. POST /api/auth/login requires AUTHENTICATED + platform UUID.
- Safe Russian errors for input, unavailable username, invalid login,
  verification expiry/not found, rate limits and unavailable Backend.
  Telegram confirmation errors visible only in the bot cannot be retrieved
  through the existing polling API; the launcher does not invent such fields.
- Password stays in memory for the request and is cleared on result/close.
  Polling and verification tokens are memory-only, never persisted/logged.
  Legacy mock-user/plaintext-password and fake-session storage is purged at
  startup. Remember login stores only username; after restart login is required.
- Login result is UI-only, not a persistent authentication capability or game
  session. Local profile display preferences are not identity/city mutations.
  Closing a pending flow does not cancel its server-side registration. After
  confirming in Telegram, use Login. Expired registration may still reserve
  the username in the current Backend; no recovery endpoint is invented.

## Configuration and launch

`VITE_ACCOUNT_API_BASE_URL` is a public **build-time** root origin: HTTPS, or
HTTP loopback for dev only. No embedded credentials, path/query/fragment.
No default production URL: an unset value shows a configuration error and
does not silently send passwords to a demo or remote plaintext endpoint.

Set the process environment before both Vite and Cargo builds. `.env.local`
alone configures Vite, not the Rust compile-time endpoint. Never put passwords,
bot tokens, BOT_BACKEND_SECRET or polling secrets into VITE variables.

```powershell
npm ci
# Example for a LOCAL Backend or a private SSH tunnel, not public port 8080:
$env:VITE_ACCOUNT_API_BASE_URL = 'http://127.0.0.1:18080'
npm run dev -- --host 127.0.0.1
# or, with Rust/Cargo, MSVC Build Tools and WebView2:
npm run tauri dev
```

For a private development connection to the existing VDS, a separate terminal:

```powershell
ssh -N -L 127.0.0.1:18080:127.0.0.1:8080 root@186.246.28.214
```

No firewall/public Backend change is needed for this tunnel. Do not distribute
a launcher build configured for somebody else's localhost; use the production
HTTPS origin once it exists.

Browser dev uses a Vite same-origin /account-api proxy. Native Tauri uses a
restricted Rust reqwest adapter with exactly register/status/login operations,
compile-time endpoint, normal certificate validation, disabled redirects and
sanitized transport errors. No unrestricted HTTP/filesystem/game command.
Browser production needs explicit allowed-origin CORS at the reverse proxy;
native desktop HTTP does not require browser CORS. Browser Play is explicitly
unsupported, not a successful mock launch.

## Tests and build

```powershell
npm test
npm run build
cargo test --manifest-path src-tauri/Cargo.toml
npm run tauri build
```

Frontend tests cover request shape, polling credential separation, safe errors,
input/response validation, storage migration, PENDING→COMPLETED, expiry and
modal cancellation. Rust tests cover endpoint/route/deep-link restrictions.
`src-tauri/Cargo.lock` and package-lock.json pin resolved dependencies.
Windows packaging uses `RE-CREATED Launcher` (colon is illegal in productName);
the in-app RE:CREATED branding/window title is unchanged. Packaging icon source
is `src-tauri/icon.svg` and generated assets are in `src-tauri/icons`.

## Production E2E blocker / minimal next deployment step

Historical section below: public HTTPS is now deployed at
`https://api.recreated.ru`; previous account E2E is recorded in
[baseline](../docs/launcher-account-baseline.md). New
[Alpha Access Keys](../docs/alpha-access-keys.md) implementation/build is LOCAL
ONLY and requires a separately approved Backend V4 deployment before release.

Backend remains bound to VDS localhost:8080. Do **not** expose 8080 publicly.
Choose a domain and A-record pointing to the VDS, then deploy Nginx/Caddy with
valid TLS on 443 and proxy only the public account routes to 127.0.0.1:8080:

- POST /api/registrations
- GET /api/registrations/{UUID}
- POST /api/auth/login

Reject /api/internal/** and protected identity routes at the public edge;
forward Authorization for polling, preserve no-store, do not log request bodies
or credentials, and apply appropriate edge rate limits. Port 5432 remains
private. Allow 80 only as needed for certificate issuance/HTTPS redirect.
Set VITE_ACCOUNT_API_BASE_URL to the confirmed HTTPS origin and rebuild both
frontend and native app. Then validate an actual launcher registration →
Telegram confirmation → COMPLETED → Login using a dedicated user account.
No VDS networking, API, Telegram protocol or Minecraft changes were made here.

See [architecture](../docs/architecture.md), [game-session design](../docs/game-session-protocol.md),
[GUI import](../docs/launcher-gui-import.md). The [original source README](docs/source-readme.md)
is historical provenance and does not describe the new account implementation.
