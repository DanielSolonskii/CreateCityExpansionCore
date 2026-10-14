# Launcher settings

Windows native settings are stored in `%LOCALAPPDATA%\Recreated\launcher-settings.json`
(preferences only, no credentials). Settings apply at the next Play launch:
RAM 4–16 GiB (`-Xmx`), window resolution 1280x720 / 1600x900 / 1920x1080, and hiding
the Launcher in the existing tray after Minecraft starts. Default RAM remains 4 GiB.
Leave 2–4 GiB for Windows. Save explicitly with “Сохранить настройки”.

“Папка игры” opens the existing separate alpha instance in Explorer. It does not
change the installation root or modify user files. The unused mock sound toggle
has been removed. Fullscreen/options configured inside Minecraft remain independent.
Registration, credentials, Game Session, updater and server settings are unchanged.

Validation: frontend existing 51 tests + 2 SettingsPanel tests; native settings
validation/JSON roundtrip test. Installer built through production Tauri NSIS.
Manual Windows game/UI acceptance remains required; automated tests are not GUI E2E.
