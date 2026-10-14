# Trusted-device authentication

Backend/Bot and additive Flyway V6 were deployed on 2026-10-04; production-configured
Windows installer is prepared locally. Manual trusted-device phone/GUI E2E is pending.
[Deployment evidence and rollback](trusted-devices-deployment.md).
This is Launcher account authentication only,
not Game Session/admission, updater authentication, or Microsoft authentication.
Existing registration/phone verification, alpha-key consumption, ACTIVE accounts,
and legacy `POST /api/auth/login` remain unchanged.

## Flow and boundaries

First login: username/password → PENDING device → phone QR/deep-link → explicit
owner Telegram button → TRUSTED → native credential store → AUTHENTICATED.
Subsequent Launcher startup reads Windows Credential Manager and calls Backend;
there is no offline authenticated fallback. Telegram → Account → Devices permits
explicit confirmed revocation. Logout revokes the credential and clears local
authentication. Offline logout persists a protected `revoke_pending` tombstone
before networking; subsequent restore cannot authenticate and retries revocation.

Approval expires after 15 minutes. Trust expires 180 days after device creation;
there is no silent renewal. Limit: 20 live pending/trusted devices per account.
Approval/revocation/authentication lock the device row. Concurrent approval has
one winner; revoke is terminal. Only an ACTIVE account with Telegram binding
can authenticate. Numeric Telegram ID is taken solely from authenticated Bot
updates. Device name is a display label, not a hardware attestation.

## V6 and credential model

Additive `trusted_device`: UUID, platform_user_id FK, label,
PENDING/TRUSTED/REVOKED, created_at, confirmation_expires_at, trusted_expires_at,
approved_at, revoked_at, last_used_at, credential_hash, polling_hash,
verification_hash. Each hash column is UNIQUE, length 64. Status timestamps and
expiry ordering are constrained. Deleting a platform user cascades devices.
V1–V5 are not modified; existing users need no data migration.

Three independent 256-bit random capabilities, domain-separated SHA-256 at rest:
device credential, Launcher polling token, Telegram proof. Password validation
creates a pending request but does NOT authorize native Play. Credential becomes
usable only after Telegram approval. Proof is not reusable after approval/expiry.
Polling does not grant authentication and cannot revoke. Responses use no-store.
Existing IP/username password rate limits apply; device polling/auth is capped
at 120/IP/minute in a shared bounded single-process limiter. Existing limitation:
limits are not distributed/durable and reverse-proxy peer handling is unchanged.

Rust keeps device/polling secrets out of WebView responses, files and JS storage.
Windows generic Credential Manager entry uses `CRED_PERSIST_LOCAL_MACHINE`, scoped
to the exact Backend base URL and current Windows user. No plaintext fallback,
no roaming credential, no password retention. Same-user malware can access a
Windows vault: this is not TPM-backed/non-exportable hardware authentication.
See [Microsoft Credential Manager](https://learn.microsoft.com/en-us/windows/win32/api/wincred/ns-wincred-credentialw).
Serialize native transitions so restore/poll cannot resurrect trust after logout.

## API

Public:

- `POST /api/auth/devices` `{username,password,label}` → 201
  `{deviceId,pollingToken,deviceCredential,telegramDeepLink,expiresAt}`.
  Native Rust consumes secrets; WebView receives only link + expiry.
- `GET /api/auth/devices/{id}`: Bearer polling token → state/label/timestamps,
  no credentials. Wrong capability/unknown ID: 404; expired pending: 410.
- `POST /api/auth/devices/{id}/authenticate`: Bearer device credential → existing
  AUTHENTICATED account DTO. Pending/revoked/expired/disabled: 401.
- `POST /api/auth/devices/{id}/revoke`: Bearer device credential → 204,
  idempotent for valid already-revoked credentials.

Internal Bot routes always require existing `X-Bot-Service-Secret`:

- `POST /api/internal/devices/preview` `{verificationToken,telegramUserId}`.
- `POST /api/internal/devices/{id}/approve` `{telegramUserId}` → 204.
- `GET /api/internal/devices/telegram/{telegramId}` → owned live devices.
- `POST /api/internal/devices/{id}/revoke` `{telegramUserId}` → 204.

Deep link payload `d_` + 43-character random proof is distinct from registration.
Bot shows account + device label, warns to approve only one's own request, and
uses callback `trust:{UUID}`. Backend re-checks numeric ownership at approval.
Revocation UI re-reads the owned list and asks for confirmation before mutation.
No service secret/device credential/password may be logged, traced or cached.

## Verification scope and controlled rollout

H2 and real local PostgreSQL contract suites cover lifecycle, HTTP error mapping,
capability separation, wrong owner/password, expiry, disabled accounts, replay,
concurrent approval and approve/revoke, DB constraints and live-device cap.
Bot tests use HTTP doubles for Telegram/Backend, preserve registration tests and
exercise phone device preview, explicit approval, private numeric sender guards,
revocation confirmation and safe Backend failures. Frontend tests cover QR,
pending/completion, expiry/cancel, native-only IPC and no JS credential storage.
Windows native test uses actual HTTP and Credential Manager with test-only
Backend responses: pending → approval → persisted trust → fresh runtime restore
→ revocation → online logout and genuinely offline logout. This is NOT live
Telegram/GUI manual acceptance and does not claim production E2E.

Controlled deployment requires explicit user authorization:

1. Backup production DB, Backend/Bot artifacts and protected environment/config.
2. Deploy Backend; apply V6 by normal Flyway startup. Stop on failure.
3. Add only the four public device route patterns to existing Caddy allowlist;
   keep `/api/internal/**` unreachable publicly, Backend/PostgreSQL loopback-only.
   Do not enable request-body/Authorization logging. Preserve WireGuard/secrets.
4. Deploy Bot; health/getMe/polling/internal communication and old-account login.
5. Build approved Windows Launcher with `https://api.recreated.ru`; publish only
   separately and explicitly. Local tests/build do not publish installers.
6. Manual phone + Windows E2E: first password login/QR approval, close/reopen
   auto-login, Telegram revoke then reopen denial, logout then reopen denial,
   offline logout, registration/login compatibility. No Minecraft changes.

Rollback: restore previous artifacts/config; do not drop V6 or restore an old DB
over new production writes automatically. Credential vault entries are scoped
and the old Launcher does not use them. Expiry/retention cleanup is deferred.
