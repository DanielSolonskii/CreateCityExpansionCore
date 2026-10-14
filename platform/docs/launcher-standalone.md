# Standalone Minecraft launch — local alpha baseline

2026-10-03. No Backend, CCEC, VDS, Telegram or Game Session changes.

## Installation

```text
%LOCALAPPDATA%/Recreated/
├── launch.json                      local prepared-instance manifest, not updater protocol
├── runtime/java-21/                 clean installed Java 21.0.12, no TLauncher runtime
├── versions/1.21.1/                 official Mojang metadata/client
├── versions/neoforge-21.1.251/       official installer metadata
├── libraries/                      official Minecraft + NeoForge dependencies/processor outputs
├── assets/                         asset index 17, objects and official log configuration
├── natives/neoforge-21.1.251/        game-generated native extraction
├── instances/alpha/
│   ├── mods/                       41 reference mod JARs, unchanged
│   ├── config/                     pack configuration without histories/caches/backups
│   ├── defaultconfigs/
│   ├── resourcepacks/CreateServerQuests/
│   └── options.txt                 fresh options enabling pack, not imported user preferences
└── logs/                           installer/game stdout+stderr
```

Reference `.minecraft` is read-only. No saves, screenshots, logs, crash reports,
accounts, profiles, tokens, server list, usercache, schematics, shaders, backup,
TLauncher metadata or modsv06.rar were imported. JEI world history, skin cache,
Sodium fingerprint and config .bak files were excluded. SkinRestorer's copied
configuration has an empty MineSkin API key. Empty saves/downloads/schematics
directories and new config/cache/log files may be created by the running game;
these are not reference user-data imports and must not become release payload.

Official `authlib:6.0.54` and `patchy:2.2.10` are verified against Mojang metadata;
no TLauncher replacement coordinates, javaagent or credentials are used.
107 classpath entries, 5 separately verified NeoForge processor output JARs,
3911 asset entries. Official vanilla bytes are also stored under the selected
NeoForge version JAR filename so NeoForge's official ignoreList excludes that
base JAR from the transforming module layer. This fixes the initial duplicate
Minecraft-package ResolutionException without changing/removing any mod.

CCEC SHA-256 (same alpha baseline as VDS):

`8113289f9a055ba0fc8f69903272986378d6b26ba5848dcbe8a3765542112d37`

## Identity and lifecycle

Native account_request records only Backend-authenticated username in process
memory. game_start takes no parameters from the GUI. Logout clears this local
name; it does not stop an already running game. Password/polling/alpha secrets
never enter game argv/env/files. Offline accessToken is the non-secret `0`.
UUID uses Java-compatible MD5 of `OfflinePlayer:<exact username>`; a mutable
display name or platform UUID is not substituted. This is ordinary offline
launch and offers no server-side account admission guarantee. Game Session/4B
remains deferred. Platform names >16 characters are rejected for this baseline.

Rust validates version/paths and hashes classpath, processor outputs and mod
JARs before spawning fixed Java 21 in the fixed instance directory. Argfile
avoids Windows command-length limits; no shell runs the game. JVM environment
injection variables are removed. STARTING/RUNNING/STOPPED/FAILED are reported
to the GUI. RUNNING means process alive, not a fabricated menu-ready percentage.
Game output is saved to timestamped Recreated/logs/game-*.log, with ordinary
Minecraft logs under instances/alpha/logs. Normal/nonzero exit is monitored.
An exclusive Windows file handle also blocks another Launcher process; it is
passed as child stdin to retain exclusivity if Launcher closes.

This is a local developer-prepared installation. No automatic downloading from
Play, clean-PC installation, updater, runtime distribution, Game Session, new
server authentication or Launcher GUI redesign was implemented. Other snapshot
data (online players/news/profile stats) stays demo data. RAM settings are not
connected yet; this launch uses -Xms1G -Xmx4G.

## Verification

- Frontend: 34 tests passed; production Vite/TypeScript build passed.
- Rust: 8 unit tests passed; manual game smoke is ignored in the default suite.
- Windows release build passed, NSIS and MSI generated with production API
  `VITE_ACCOUNT_API_BASE_URL=https://api.recreated.ru`.
- Real smoke invoked the same Rust spawn path on this prepared PC. NeoForge
  21.1.251, Minecraft 1.21.1, Create, CCEC and the modpack loaded. ModernFix
  reported completed startup in 39.318 seconds. Main menu explicitly confirmed
  by the user. There are existing nonfatal modpack warnings; no fatal error
  in the successful run. The first failed classpath attempt is retained in logs.
- Independent second-process exclusive-open probe was rejected while game ran.
- New production native Launcher executable opened for manual GUI acceptance.
- Login → GUI Play → working Minecraft explicitly confirmed by the user:
  "всё запущено и работает из лаунчера". A separate GUI game log confirms
  completed startup in 38.35 seconds and a normal "Stopping!" afterwards.
- Connection/gameplay on VDS still requires explicit user confirmation. This
  does not assert Game Session, server account admission or updater acceptance.

Installer paths:

- `launcher/src-tauri/target/release/bundle/nsis/RE-CREATED Launcher_0.1.0_x64-setup.exe`
- `launcher/src-tauri/target/release/bundle/msi/RE-CREATED Launcher_0.1.0_x64_en-US.msi`

Exit the smoke-test game normally, sign in to the new Launcher, press Play,
then manually connect to `186.246.28.214:25565`. Do not run another baseline
against the same game directory. Existing reference client remains available.

Official sources: [NeoForge client installation](https://docs.neoforged.net/user/docs/client/),
[Java requirements](https://docs.neoforged.net/user/docs/),
[installer tools](https://github.com/neoforged/InstallerTools).
