# Production deployment: 2026-10-12 (Europe/Minsk)

Explicitly requested Launcher publication + current built CCEC deployment.

## Published

- Launcher 0.1.5, independent sequence 5, approved, optional self-update.
- Build ID: production-news-ui-20261012. Release configuration pins production
  HTTPS Backend and Game Session TLS api.recreated.ru:25565/recreated-alpha.
- Installer: https://api.recreated.ru/launcher/latest/RE-CREATED-Launcher-x64-setup.exe
- Immutable installer: https://api.recreated.ru/launcher/versions/0.1.5/RE-CREATED-Launcher-x64-setup.exe
- Installer size 5997103; SHA-256
  `e3dd3592c299dac80ee4a3e50ae3b295977e955930d2eb86e542e3118643328f`.
- Game alpha-0.1.18, sequence 19. Changed only CCEC and launch.json inventory.
- CCEC build from 2026-10-12 21:50, size 4173985; SHA-256
  `7c62c72fde7634f208d3db9d4e90b5f19d349835500ad3248e4022e5cd40fd86`.

Dry-run detected six missing existing mods in the curated local source. Restored
them from the verified previous alpha manifest with individual SHA checks before
deployment. Final plan has no added/removed files and no config changes.

## Backend/Bot

Updated previously tested Backend/Bot artifacts to provide the public news API
and composer instructions. No migration: Flyway history/checksums unchanged.
Existing configuration/secrets, WireGuard and default/policy routes verified
unchanged. Added only `/api/launcher/news` to Caddy's existing GET allowlist after
backing up Caddyfile; configuration validated and Caddy reloaded.
Public HTTPS health UP/postgres UP; public news feed returns 10 safe DTOs.

Bot long polling started. Some generic communication failures occurred after
restart, then stopped. Diagnostic getMe succeeded under createtelegram UID;
Backend health from the same UID UP. No recent errors in final two-minute window;
service active with zero automatic restarts. No networking changes were made.
Actual new message delivery remains a manual acceptance check.

## Backups and verification

- Backend/Bot/config + consistent PostgreSQL dump:
  `/opt/create-server/backups/launcher-news-20261012T192049Z`.
- Stopped-world + replaced server files:
  `/opt/create-server/backups/server-deploy-04901d7e5a7f4de6a60021451f543825`.
- Client pointer publication backup:
  `/opt/create-server/backups/client-publish-3bb1ffb9d12349bbaead785fe19a07e9`.
- Protected Backend/Bot deployment staging:
  `/opt/create-server/staging/launcher-news-20261012` (retained for rollback).

Minecraft stopped through the existing stop mechanism. Logs confirm players/world
saved and all dimensions saved. New invocation reached Done at 22:24:22.
Voice Chat started UDP 24454; Sable UDP 25565 listening. Minecraft, Backend, Bot,
game TLS, WireGuard and Caddy active. Downloaded signed public game manifest and
selected objects verified, including exact deployed CCEC SHA-256.
Both candidate and latest installer fully downloaded to Windows and SHA checked.

Frontend 63/63 + release workflow 6/6 passed; production NSIS build successful.
Earlier same-task implementation suites: Backend H2 121, PostgreSQL 109, Bot 67,
Rust 35 passed/3 ignored. These are not a new production GUI/gameplay E2E claim.
Installer upgrade/clean-install GUI and login → Play → join require user acceptance.

Rollback: retain backups. Server files can be restored using Release.ps1
server-rollback with the listed server backup. After client publication use a
new forward recovery game release with higher sequence, not a backwards pointer.
Backend/Bot rollback restores artifacts only after checking current configuration;
no schema rollback needed. Launcher recovery uses a new approved higher version
and sequence; historical versioned installers were not overwritten.
