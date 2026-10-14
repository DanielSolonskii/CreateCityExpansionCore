# Alpha Access Keys — closed alpha

Implemented and deployed on 2026-10-03: production Flyway V4 successful.
See [controlled deployment record](alpha-access-deployment.md).
New alpha-gated GUI E2E is pending the user's test. The previous human-confirmed
Launcher account baseline is not an acceptance test of this new gate.

## Registration and failure semantics

`POST /api/registrations` requires JSON `username`, `password`, `alphaKey`.
Alpha keys are case-sensitive, 32 SecureRandom bytes, base64url without padding
(43 characters, 256-bit entropy). PostgreSQL stores only SHA-256 hashes.
Launcher trims surrounding copy/paste whitespace, then sends the exact key.

Missing/malformed/unknown/used/revoked/expired keys return the same
400 `{"code":"INVALID_ALPHA_KEY"}`, with Cache-Control: no-store.
No owner, state, expiry or key is exposed in that error.
Username/password validation and existing rate limits remain in force.

One transaction locks the key row via SELECT FOR UPDATE, validates state and
expiry, creates PENDING_VERIFICATION user + credentials + PENDING registration,
and sets key USED with account UUID and used_at. Concurrent use has exactly one
winner. Failure rolls back account/session/key consumption together.
Successful registration permanently spends the key, even if Telegram
confirmation never completes or the registration expires. No automatic refund,
resend, public key administration or retrieval endpoint.

Telegram confirmation still transitions account to ACTIVE and registration to
COMPLETED. Confirmation protocol, polling response and login are unchanged.
Existing ACTIVE accounts without alpha keys still log in. Play remains mock.

## Flyway V4

`V4__alpha_access_keys.sql` adds:

| Column | Constraint / meaning |
| --- | --- |
| id | UUID primary key; administrative identifier, not the secret |
| key_hash | varchar(64), NOT NULL, UNIQUE; SHA-256 hex |
| state | UNUSED / USED / REVOKED, NOT NULL |
| created_at | timestamptz, NOT NULL |
| expires_at | nullable timestamptz; must be after created_at |
| used_at | nullable timestamptz; required for USED and >= created_at |
| platform_user_id | nullable UUID FK platform_user(id), UNIQUE |

CHECK: USED requires used_at and platform_user_id; UNUSED/REVOKED requires both
NULL. Expired keys remain UNUSED but cannot be consumed. Infrastructure marker
advances to 4. No backfill/gate is applied to existing accounts. V1–V3 unchanged.

## Generate N keys, server-side only

After an explicitly approved deployment/migration (now completed), configure the same protected
PostgreSQL environment as Backend, then run:

```sh
java -jar create-server-backend-0.1.0-SNAPSHOT.jar --generate-alpha-keys=10
# Optional common expiry (replace with a future UTC instant):
java -jar create-server-backend-0.1.0-SNAPSHOT.jar --generate-alpha-keys=10 --alpha-keys-expires-at=2027-01-01T00:00:00Z
```

CLI starts a non-web Spring context, validates schema and uses normal Flyway.
Therefore do NOT run the new JAR/CLI against production until V4 is approved.
Count must be 1..10000; expiry must be future. The entire batch commits before
output. Each stdout key line is `UUID<TAB>plaintext`; startup logs may also appear.
Plaintext is printed only in this generation command, not logged by services or
recoverable from the database. If output is lost, revoke these UNUSED IDs and
generate replacements; there is no retrieval command. Do not rerun to retrieve
the same keys: another invocation creates a new batch.

Run interactively in a trusted administrator terminal, not through systemd
journal/CI transcripts. Do not commit output, redirect it into project files,
put keys/passwords in command arguments, or enable request/SQL debug logging.
Distribute keys through a private channel. CLI needs no bot token/service secret.

Revoke an unused key through protected server-side SQL using its generation ID:

```sql
UPDATE alpha_access_key SET state = 'REVOKED'
WHERE id = '<generated UUID>' AND state = 'UNUSED';
```

Never reset USED to UNUSED or detach its account to reuse the secret.

## Local verification

- Backend `gradlew test build postgresTest`: 44 H2/unit tests, 35 PostgreSQL
  integration tests; zero failures/errors.
- PostgreSQL: local `create_server_test`, V1–V4 successful; no production access.
- Duplicate/concurrent use, expiry/revocation/missing key, rollback, schema
  constraints, HTTP errors, existing-account login and full Telegram flow covered.
- CLI executable-JAR smoke generated 2 test keys; verified hashes, cleaned only
  those fixtures, did not print plaintext in the test report.
- Frontend: 31 tests; frontend production build successful.
- Rust: 4 tests; Windows Tauri MSI/NSIS build successful with production origin.
- New installer was not installed; new alpha-gated GUI E2E was not run.
- These local checks preceded deployment; production deployment is recorded
  separately. No CCEC/Minecraft/bot protocol edits.

Backend V4 is deployed; install the matching rebuilt Launcher for the user E2E.
Older Launcher builds cannot supply alphaKey after Backend deployment; the new
Launcher must not be treated as enforcing the gate against the old Backend.
