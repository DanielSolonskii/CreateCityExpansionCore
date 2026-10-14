# ADR-0005: Closed-alpha registration gate

- Status: accepted for local implementation, not deployed
- Date: 2026-10-03
- Extends ADR-0003; does not change identity authority or Telegram activation.

Require a high-entropy one-use invitation for NEW registration. Store only its
SHA-256 hash in alpha_access_key (V4). Use database row locking in the existing
registration transaction; consume together with pending account and session.
No consumption refund after verification expiry. Existing accounts/login and
Telegram protocol are unaffected. Key expiry and revocation fail with the same
public INVALID_ALPHA_KEY error as missing/used keys.

Administrative generation is a non-web CLI using the protected DB environment;
plaintext is emitted once after batch commit and never stored/retrievable by
Backend. Revocation of UNUSED keys is server-side SQL by administrative UUID.
This is invitation gating, not a game session or CCEC authentication mechanism.
See [schema, operator workflow and verification](../alpha-access-keys.md).
