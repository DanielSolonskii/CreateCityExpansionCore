# Launcher Account Integration — production baseline

Accepted: 2026-10-03. Backend origin: `https://api.recreated.ru`.
Windows Launcher: version 0.1.0, production Tauri build with
`VITE_ACCOUNT_API_BASE_URL=https://api.recreated.ru` for frontend and native build.

## Production GUI acceptance

The user explicitly confirmed a NEW account `drannch1k` was created through
the installed production Launcher, verified in Telegram, polled to completion,
and successfully logged in through the Launcher.

Accepted sequence:

```text
Launcher registration → Telegram verification → polling COMPLETED → Launcher login
```

This is human-confirmed production GUI E2E, not an automated GUI observation.
Windows automation was unavailable (`failed to write kernel assets`), so the
agent did not directly observe the successful GUI sequence.

## Build and automated checks

- 29 frontend tests passed.
- 3 Rust tests passed.
- Frontend production build and Windows Tauri release packaging succeeded.
- NSIS installer installed successfully (exit 0); installed Launcher started.
- Confirmed production origin in the installed native executable.
- Storage tests verify legacy plaintext mock credentials are removed and
  remember-login persists only username. No production password is recorded here.

Installer, relative to the project root:
`launcher/src-tauri/target/release/bundle/nsis/RE-CREATED Launcher_0.1.0_x64-setup.exe`.
MSI: `launcher/src-tauri/target/release/bundle/msi/RE-CREATED Launcher_0.1.0_x64_en-US.msi`.
Build outputs are local artifacts, not files to commit.

## Scope and remaining checks

`Play` remains mock. Login is not a Game Session and does not establish
Minecraft admission. Milestone 4B remains unimplemented.
Backend API, Telegram protocol, CCEC and Minecraft were not changed by the
production Launcher rebuild or this acceptance record.

Error handling and password-storage safety have automated test coverage;
negative-error scenarios and inspection of runtime WebView storage were not
separately confirmed in production GUI. A transient VPN HTTPS timeout observed
during diagnostics was not assigned a proven root cause; the user subsequently
confirmed the successful end-to-end account flow. No network changes were made
to bypass that timeout.
