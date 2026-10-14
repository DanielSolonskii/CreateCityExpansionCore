# Create Server — VDS deployment, 2026-10-03

Current public Backend HTTPS: https://api.recreated.ru. See
[HTTPS deployment](backend-https-deployment.md) for Caddy, TLS, firewall and
Launcher build configuration. The initial audit below is historical; Backend
and PostgreSQL themselves still listen only on localhost.

Deployment/migration only. No Milestone 4B, new gameplay, GUI changes, V4,
Redis or Docker. Backend source and Flyway V1–V3 were not changed.

## 1. Local Minecraft source

Selected `E:\CreateCityExpansionCore_MERGED\CREATED`, Minecraft 1.21.1,
NeoForge 21.1.251. The installed launcher arguments identify the versions;
latest.log records real connections and a clean save-all/stop. The development
checkout is not the standalone server. Process inspection before archiving found
only GradleDaemon, not Minecraft; world/session.lock was held exclusively during
the archive operation. See [pre-transfer selection](vds-source-selection.md).

User explicitly selected the working CCEC 1.0.1 JAR from October 1, not the newer
development build from October 3. Installed CCEC SHA-256:
`27ace54082ed53c25e4a9e5af2c9c1c6f3074de7ab3459a91d45475fc59d9ce2`.
The original server/world and CCEC source checkout remain untouched; all 499
transport payload files were byte-for-byte checked against the original after
migration, without extracting or modifying another local world copy.

## 2. VDS audit

- SSH: `root@186.246.28.214`; hostname `nsk-1-vm-elyh`.
- Actual OS: Ubuntu **26.04.1 LTS**, x86_64, not the stated 24.04. No OS reinstall.
- 3 QEMU vCPU, 5.8 GiB RAM, no swap; 19 GiB root filesystem.
- Initial free disk approximately 17 GiB; after deployment approximately 15 GiB.
- OpenJDK 21.0.12.1 headless and PostgreSQL 18.6 installed via Ubuntu packages.
- Git 2.53.0; systemd 259.5. SSH configuration/authentication unchanged.
- UFW already active; fail2ban and Docker absent and not installed.
- Existing Zabbix listener 10050 was left alone and remains blocked by UFW.

## 3. VDS layout and permissions

```text
/opt/create-server/
├── minecraft/      # world, server mods/configs/runtime, Linux console scripts
├── backend/        # app.jar
├── telegram-bot/   # application distribution bin/ + lib/
├── secrets/        # root:root 0700; backend.env 0600
└── backups/        # root-only source archive and small pre-change backups
```

Services run as the system user `createserver`, nologin shell. Runtime directories
are inaccessible to other ordinary users. Backend/bot executable artifacts and
launcher scripts are root-owned, readable/executable by the service group.
Minecraft has the write access required for world/configs/native extraction.
systemd reads the protected environment file as root; no password appears in units.

## 4. Transferred data

Original uncompressed server payload approximately 502 MiB; current runtime roughly
509 MiB including newly generated logs/native library; world approximately 174 MiB
(source 181680411 bytes). Transport archive 428325495 bytes (~408.5 MiB).
Archive SHA-256:
`7618c1f1b6b8c26c932b5339cd92e8631f3714012742ff288f9d51f427739d85`;
local and remote checksums matched before extraction.

Transferred world including playerdata, dimension directories, SavedData,
serverconfig, sublevels, datapacks directory; mods, libraries, config,
defaultconfigs (empty), server.properties/JVM args, EULA, ops, whitelist, bans,
user/name caches and existing bootstrap server.jar. CCEC bundled override datapack
is enabled by the existing mod. The initial archive contains all 31 original JARs.
Two demonstrably client-only mods (`@Mod(dist=CLIENT)`), CTM and BetterAdvancements,
are retained in root-only backups but excluded from the final dedicated runtime:
29 external JARs plus bundled dependencies. No other mod was removed or upgraded.

Excluded source logs/crash reports, extracted Windows Sable DLL cache, transient
session.lock, development/build directories, IDE/Gradle caches, old backups and
Windows launcher runtime. Linux Sable extracted `sable_rapier_x86_64_linux.so`.

Remote-only changes, with backups: clear obsolete `server-ip=26.5.192.146` binding;
retain `-Xms2G`, reduce `-Xmx4G` to `-Xmx3G`. Linux startup uses the installed
NeoForge 21.1.251 `unix_args.txt`, not a guessed classpath.

## 5. PostgreSQL

PostgreSQL 18 main cluster active/enabled. Production database and non-superuser
login role: `create_server`; role owns its database, connection limit 10.
A fresh strong random password was generated on VDS and written only to
`/opt/create-server/secrets/backend.env`; it is not in Git/docs/units/reports.
Local development create_server had only V1 infrastructure, no accounts to import.
No create_server_test or fixtures were copied. Production user/credentials/Telegram
tables remain empty. Flyway V1, V2 and V3 applied successfully; Hibernate validates
the schema. Listener only 127.0.0.1 and ::1:5432, no public firewall rule.

## 6. Backend

Local Java 21 `gradlew test build`: successful, 36 tests, zero failures.
`gradlew postgresTest`: successful against separate local PostgreSQL test database,
27 tests, zero failures. Executable JAR deployed as `/opt/create-server/backend/app.jar`.
Version: `create-server-backend` / `0.1.0-SNAPSHOT`.
Deployed JAR SHA-256:
`e8f4577245ef0f4467240525776b1995d5d069db0191400924ed90032b37c3f7`.

Transient smoke test confirmed real PostgreSQL connection, all migrations and
startup before enabling the permanent service. systemd service active/enabled;
explicit restart tested. Heap 128–384 MiB, cgroup maximum 768 MiB.
`GET /api/health`: `{"status":"UP","checks":{"postgres":"UP"}}`.
`GET /api/version`: `{"name":"create-server-backend","version":"0.1.0-SNAPSHOT"}`.
Bind only 127.0.0.1:8080; external connection denied.
Internal APIs now have the VDS-generated service secret configured. Registration
remains disabled until the real bot username is supplied (see bring-up below).

## 7. Minecraft

Controlled smoke test reached `Done`; logs confirm NeoForge 21.1.251, MC 1.21.1,
Create 6.0.10, CCEC 1.0.1, world `server_map` in directory world, all three vanilla
dimensions and Sable native physics initialization. Permanent service active/enabled.
TCP 25565 listens; Sable additionally binds UDP 25565.
Public Java server-list ping from Windows returned version 1.21.1 / protocol 767,
0 players and maxPlayers 20. This is not a player login or gameplay acceptance test.

systemd ExecStop writes `stop` through a service-user-only console FIFO and waits
for Java to exit. Actual restart logs confirmed saving players, worlds and Sable
sublevels, `All dimensions are saved`, successful deactivation, then fresh `Done`.
The initial signal-only stop did not give reliable save log evidence; the final
service therefore uses the explicitly verified console stop mechanism.

No fatal registry/missing-mod/config startup failures observed. Existing nonfatal
modpack errors remain (optional Railways loot integrations, Create Deco/Yuushya
recipes, Sable copycats:copycat_catwalk physics tag) and version-check timeouts.
These do not justify destructive mod/config/world changes in a migration.

Offline-mode, disabled whitelist, ops and bans were preserved. Name impersonation,
including operator names, is possible on this public offline server. The platform
backend currently does NOT authenticate game admission; no fictitious
authentication.mode setting or 4B mechanism was added.

## 8. Telegram Bot

Java 21 `gradlew test build` successful, 5 tests / zero failures.
Only built application distribution deployed, version 0.1.0-SNAPSHOT.
Unit installed/validated, **disabled and inactive**. Protected bot environment now
exists with generated shared secret and local backend/temporary download URL, but
real Telegram token and username are still absent. Do not start until supplied.
Live Telegram polling/backend confirmation have NOT been verified.

Required protected server-side values:

- `TELEGRAM_BOT_TOKEN`: real BotFather token, bot environment only.
- `TELEGRAM_BOT_USERNAME`: real username without @, both backend and bot.
- `BOT_BACKEND_SECRET`: same strong secret, >=32 characters, backend and bot only.
- `BACKEND_URL=http://127.0.0.1:8080` in bot environment.
- `LAUNCHER_DOWNLOAD_URL`: temporarily `https://example.com/launcher`, as supported
  by current code; the Download button is NOT production-ready. Real hosting later.

Configure using server-side protected editing; never post values in Git or chat.
Add username/shared secret to backend.env without replacing its database password.
Both environment files are root:root 0600 inside root-only secrets/ (0700).
Ensure no webhook and no second polling instance; stop any old local bot first.
Restart backend, then explicitly enable/start bot and verify polling/confirmation.

## 9. Firewall

UFW active, default incoming deny; public allow rules on IPv4/IPv6:
22/tcp SSH, 25565/tcp Minecraft, **25565/udp Sable physics synchronization**.
UDP was explicitly authorized by the user, is a Sable requirement, not a generic
Minecraft Java requirement. Existing SSH allowance preserved throughout.
No public PostgreSQL, backend, RCON, query or Zabbix allowance was added.

## 10. Systemd

- `create-server-minecraft.service`: enabled, active, Restart=on-failure/15s,
  stop/save timeout 180s, 3-start/300s limit, protected write scope.
- `create-server-backend.service`: enabled, active, Restart=on-failure/10s,
  protected environment, journal logging, stop timeout 90s.
- `create-server-telegram-bot.service`: prepared/disabled/inactive pending secrets.
- Ubuntu `postgresql.service` and actual `postgresql@18-main.service` enabled/active.

Versioned unit/script copies are under infra; secrets remain exclusively server-side.
Short-lived smoke units were stopped and are not enabled.

## 11. Verification

Completed local backend H2/build, PostgreSQL suite, bot test/build; archive checksum;
real backend PG/Flyway/JPA startup/health/version; Minecraft dedicated startup/world/
Create/CCEC/Sable native; explicit stop/save/restart; external TCP and Java status
ping; negative public PG/backend connection checks; actual reboot and reconnect.
Boot ID changed from d411ce4d-de4a-41a7-8c46-6c0ba3422d8a to
3a5d9f11-6991-497c-87a8-653bcef5a32d. After reboot SSH, PostgreSQL, backend and Minecraft autostart were checked; backend
health remained UP and Minecraft ready/ping were rechecked. Bot remains disabled.

User has accepted the Minecraft migration baseline after a real client connection:
existing world and Sable work. This is user-confirmed runtime acceptance, not an
automated agent gameplay test.

Not verified by the agent: real player join/gameplay/Sable client UDP exchange, live Telegram,
public backend HTTPS, launcher integration or Milestone 4B authentication.

## 12. Required user actions

1. Minecraft baseline accepted by user; no further migration action required.
2. Configure real Telegram values securely on VDS when ready (see section 8).
3. Choose domain/public HTTPS backend URL and download/update hosting for launcher.

No SSH password/private key action is required. Do not delete the local source.

## 13. Operations commands

Connect first: `ssh root@186.246.28.214`. Run the following on the VDS:

```sh
systemctl status create-server-minecraft --no-pager
journalctl -u create-server-minecraft -f
systemctl restart create-server-minecraft

systemctl status create-server-backend --no-pager
journalctl -u create-server-backend -f
systemctl restart create-server-backend

systemctl status postgresql@18-main --no-pager

systemctl status create-server-telegram-bot --no-pager
journalctl -u create-server-telegram-bot -f
# Only after configuring real credentials:
systemctl restart create-server-telegram-bot

curl -fsS http://127.0.0.1:8080/api/health
curl -fsS http://127.0.0.1:8080/api/version
ufw status verbose
```

Restart is a real stop/save/start, not an instantaneous operation. Allow ~45 seconds
for startup; inspect `Done`, not merely systemctl active. Never kill -9 during save.
Before configuration bot remains disabled; first launch requires
`systemctl enable --now create-server-telegram-bot` after protected editing.

## 14. Next deployment step

Domain/reverse proxy/TLS and explicit external route policy before exposing backend
or registration. Resolve proxy trust/rate-limit/abuse requirements in ADR-0003;
do not publish internal endpoints for convenience. Launcher stays Windows-only:
future production backend public base URL and download/update URL are undecided;
Minecraft address is 186.246.28.214:25565. GUI/runtime was not moved or changed.

Telegram: configure real token/username/shared secret/download URL, verify singleton
long polling and an end-to-end registration confirmation. Then separate controlled
evaluation of the October 3 CCEC build against the accepted migration baseline.
4B: still separate implementation/lifecycle/IPC/TLS/security acceptance according
to proposed ADR-0004; no game sessions were implemented during deployment.

Current backups are a one-time migration snapshot, not a recurring backup system.
Before production play, add scheduled world/PG backups, off-VDS retention and a
restore test as a separate operations step; avoid endless copies on a 19 GiB disk.

## Telegram production bring-up — configuration gate, 2026-10-03

Current deployed BotMain was inspected: required TELEGRAM_BOT_TOKEN,
TELEGRAM_BOT_USERNAME (5–32 letters/digits/underscore, no @), BOT_BACKEND_SECRET
(>=32 characters); BACKEND_URL defaults to loopback HTTP and LAUNCHER_DOWNLOAD_URL
requires HTTPS but permits the existing example.com placeholder.

A new cryptographically random service secret was generated directly on VDS,
stored identically in backend.env and telegram-bot.env, never printed or copied
to the local project. Existing backend environment was backed up in a root-only
directory, preserving its database password. Backend restarted successfully:
health UP, version 0.1.0-SNAPSHOT. Internal read endpoint checks: no secret 401,
wrong secret 401, correct secret 404 for an absent lookup identity (authorized
read, no fixture inserted). A value-based journal scan found no configured service
secret/database password. Backend/PG remain localhost-only; firewall unchanged.

Token/username remain unknown; bot disabled/inactive and no live API call, polling,
/start, real registration, Telegram identity insertion, replay or login test has
yet been performed. These remain pending user input, not reported as successful.
Launcher URL is explicitly temporary/non-downloadable, not production-ready.
Minecraft baseline, CCEC JAR/world, Flyway V1–V3 and Launcher GUI unchanged.

User runs ONE command in their existing interactive SSH session on VDS:

```sh
sudo python3 /opt/create-server/secrets/configure-telegram.py
```

The root-only helper prompts for username and hidden token through the terminal,
not argv/environment/history/chat; validates format; backs up existing protected
files and atomically writes both environment files with 0600 permissions. It does
not start services. Never include token/service secret in a prompt or documentation.
After saving, report readiness in chat without values; controlled API/startup and
user-assisted end-to-end verification follow.

Operations on VDS:

```sh
systemctl status create-server-telegram-bot --no-pager
journalctl -u create-server-telegram-bot -f
# Only after real configuration and startup verification:
systemctl restart create-server-telegram-bot
systemctl status create-server-backend --no-pager
journalctl -u create-server-backend -f
```

Domain/reverse proxy/TLS and external route policy remain separate. GUI Launcher
backend integration and Game Sessions/Milestone 4B were not implemented.
