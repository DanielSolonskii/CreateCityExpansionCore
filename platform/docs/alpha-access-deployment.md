# Alpha Access Keys — controlled production deployment

2026-10-03. Backend: https://api.recreated.ru. Alpha GUI E2E pending user test.

## Backup and deployment

- Pre-change root-only directory: `/opt/create-server/backups/alpha-20261003-153739`, 0700.
- `create_server.dump`: PostgreSQL custom-format production backup, root:root 0600.
  Backend was stopped for the dump; archive TOC validated with pg_restore --list.
- `app.jar`: pre-deployment Backend artifact, root:root 0600.
- New JAR transfer SHA-256 checked against local artifact before installation.
- Backend restarted as createserver; normal Flyway applied V4. V1–V3 unchanged.
- Live Flyway history: 1, 2, 3, 4 successful.
- Public HTTPS health UP/PostgreSQL UP; version 0.1.0-SNAPSHOT.
- Backend, PostgreSQL, Caddy, Telegram Bot and Minecraft services active.
- No Minecraft/CCEC/Play, firewall or routing changes.

## Alpha keys and checks

10 keys generated through the new non-web CLI, without expiration.
Each hash/state/expiry was checked against PostgreSQL: UNUSED, expires_at NULL.
Plaintext captured privately and saved in:
`/opt/create-server/secrets/alpha-keys-20261003-153739.tsv` — root:root 0600,
inside the existing root-only secrets directory.
No plaintext in this document or deployment output; no key consumed by probes.

Registration without alphaKey and with a malformed key returns
400 INVALID_ALPHA_KEY. The existing drannch1k account remains ACTIVE; its UUID,
password hash and Telegram binding were compared before/after and are unchanged.
Actual password login requires the user's GUI check; no password was requested
or guessed by the deployment.

Pre-deploy recheck passed: 44 backend H2/unit, 35 PostgreSQL, 31 frontend, 4 Rust
tests; Backend build and production Windows Tauri MSI/NSIS packaging.
Launcher build-time origin: https://api.recreated.ru. New installer rebuilt,
not automatically installed; close the old app and install the matching build.

## Safe first-key retrieval

Execute locally in your trusted SSH terminal (do not post the result in chat):

```sh
ssh root@186.246.28.214 "head -n 1 /opt/create-server/secrets/alpha-keys-20261003-153739.tsv | cut -f2"
```

This reads the FIRST generated key, not a new generation and not a consumption.
The file is retained for private distribution; mark issued keys operationally
without changing their database state. Database consume occurs at registration.
Do not rerun generation to retrieve keys.

Test sequence: install new Launcher → check existing drannch1k login → register
a new username with first alpha key → Telegram confirmation → COMPLETED →
ACTIVE → login. A Telegram account already bound to drannch1k cannot confirm a
second platform account; use a different unbound Telegram account for this E2E.
Existing bindings must not be deleted merely for testing.

Stop after handoff and wait for user E2E feedback. No production test account
was created automatically; no new alpha GUI E2E acceptance has been recorded.
