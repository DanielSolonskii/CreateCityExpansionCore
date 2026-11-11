# Launcher registration hotfix — 17.10.2026

The registration transport validated VITE_ACCOUNT_API_BASE_URL before detecting Tauri. An empty Vite build variable therefore raised NOT_CONFIGURED locally before the native account_request command could run. Trusted-device login uses a different native gateway, explaining why existing users could log in while registration failed.

Windows registration now delegates to the native transport first. The release Rust build already pins https://api.recreated.ru and validates HTTPS; its policy is unchanged. Browser transport continues to reject missing/invalid origins before sending credentials. Native failures never fall back to browser fetch. The user-facing missing-configuration message now recommends updating the launcher instead of exposing VDS deployment wording.

Updated files: platform/launcher/src/services/accountApi.ts and accountApi.test.ts, mirrored to the current CreateServerPlatform launcher checkout.

Validation: 36 account API/form tests passed, including native registration/status with an empty frontend URL, sanitized native failure/no fetch fallback, and browser fail-closed behaviour. TypeScript and production Vite build passed. The native installer build result is reported in the task response. No real user registration, public release/channel change or backend/account-policy change was performed.

Separate existing rule: AccountService rejects linking a Telegram identity that is already bound to another account with TELEGRAM_ALREADY_LINKED. This is independent of the launcher configuration bug; account limits were not relaxed.

Windows release compilation and NSIS packaging succeeded using the pinned production HTTPS configuration. Local hotfix installer: output/launcher-registration-fix/RE-CREATED-Launcher-0.1.6-registration-fix-setup.exe. This is a manual hotfix of 0.1.6; the signed public update channel was not changed. Actual account registration in the running Windows UI remains a manual check.

2026-10-17: The transport fix is now included in signed public launcher 0.1.7 (sequence 7). Publication and public installer SHA-256 verification succeeded. Actual Windows UI registration remains a manual acceptance check.
