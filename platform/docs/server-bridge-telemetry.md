# Server bridge / Telemetry v1 (local only)

CCEC remains gameplay authority. V9 stores a read projection and coarse telemetry; Backend/Bot never mutate SavedData. No changes to 4B admission, credentials, Launcher or updater. All ingress uses existing private `X-Game-Service-Secret`; read routes require Bot service authentication. Admin players/stats additionally require configured numeric `REPORT_ADMIN_TELEGRAM_IDS`. Never expose `/api/internal` through Caddy.

## Captures

- `PLAYER_JOIN`, `PLAYER_LEAVE`, `SESSION_START`, `SESSION_END`: NeoForge real-player events, excluding FakePlayer. Separate UUID per login session.
- `SESSION_CHECKPOINT`: cumulative monotonic duration once per minute, not incremental sums. Backend takes maximum per session, so retries/end do not double playtime. Crash loses at most the unsent checkpoint plus any telemetry gap; not a billing/anti-cheat authority.
- `CITY_CREATED`: after successful CityManager creation; subject = owner.
- `CITY_JOIN`: accepted invite; subject = joining member. `CITY_LEAVE`: successful removal; subject = removed member, including kicking. It does NOT claim this member performed the kick.
- `TERRITORY_CHANGED`: city level changes alter existing square territory size (100/150). Subject = city owner, detail = new level. CCEC has no arbitrary claim/unclaim subsystem; no invented chunk events.
- `PROJECT_ADDED/REMOVED`: successful city project mutation (e.g. industrial mine); subject = project creator, not necessarily remover.
- `ECONOMY_ACTION`, `RAILWAY_ACTION`: successful existing handler actions only; subject = actual server-side player, detail = allowlisted action enum. Requests for state/map/routes and denied/failed actions excluded. Does not claim exhaustive coverage of automated industrial production, all admin commands or passive logistics cycles.

`minecraftUuid` means affected/performing subject as specified above, NOT a universal actor/audit claim. Backend resolves platform UUID from existing identity mapping; unknown legacy subject remains explicit nullable platform UUID with original Minecraft UUID. No username identity matching. Events have UUID, boot UUID, UTC occurred/received timestamps, bounded type/detail, optional subject object UUID and cumulative duration. No free-text action content, credentials, clicks, movement, positions, inventory contents or raw packets.

## Transport

One daemon worker; capture/offer only on server thread. Bounded in-memory queue (2048 incl. pending), immutable batches of 50, 5-second HTTP deadline, no redirects, exponential retry 2–60 seconds, stable batch/event IDs. JSON serialization/HTTP never occurs on tick, join/leave or city mutation thread. HTTP failure never rolls back gameplay. Full queue drops new events and increments dropped count; newest heartbeat is independently coalesced. Shutdown attempts pending + final offline batch asynchronously without waiting; if JVM exits before delivery, heartbeat expiry reports UNKNOWN. No guarantee of lossless crash delivery; a future bounded disk spool would be a separate step.

Snapshot every 20 seconds: independent boot UUID/start time/revision, online flag, monotonic uptime, real player UUID list (v1 cap 100), configured maximum, Minecraft/CCEC/NeoForge versions and dropped count. Missing configuration disables optional telemetry safely. Secret is read only from existing server environment; existing AuthenticationServerConfig endpoint/server ID used without changing REQUIRED/DISABLED semantics.

Backend serializes per boot and rejects stale revisions, conflicting replays and invalid batches. Whole batch is transactional. A newly started boot is latest; old-boot retries cannot overwrite it. Status ONLINE only when observed heartbeat <=90 sec; graceful OFFLINE explicit; missing/stale = UNKNOWN, never fake online. Replaying a batch does not renew heartbeat freshness. Current players hidden when offline/stale. Old versions/uptime are last-observed values, not live inferred figures.

## V9 / APIs

`server_bridge_boot`, `telemetry_batch`, `telemetry_event`, `telemetry_session`; V1–V8 unchanged. Boot revisions, idempotency hashes and identity FKs; indexes for latest status/event times/users. Session cumulative duration means counted playtime, not perfectly reconstructed crash time. Event retention currently explicit/no automatic deletion; operational retention/archival should be chosen before long-term usage.

- POST `/api/internal/game/telemetry`: game service credential, batch receipt UUID.
- GET `/api/internal/bridge/status`: Bot service credential, counts/status/versions only (no player identities).
- GET `/api/internal/bridge/admin?telegramUserId=N`: Bot credential plus Backend admin allowlist, current canonical names/UUIDs, events last 24h, sessions and counted playtime. All-time session totals include only delivered telemetry.

## Telegram / deployment

Existing ordinary `🎮 Сервер` shows status, real counts, uptime and versions with refresh/back buttons. Configured admins get `📊 Статистика сервера` with current players and concise totals; every callback checked locally and in Backend. No new registration/device/report protocols. Backend failure is displayed as unavailable, not OFFLINE.

Production untouched. Controlled deployment requires DB/artifact/config backups, normal Flyway V9 startup, Backend/Bot update, matching CCEC server/client artifact deployment and real runtime checks: join/play/leave, city creation/invite/kick/level, successful/denied economics and railway actions, backend outage/recovery, heartbeat expiry, admin/ordinary isolation. Do not publish from build or change alpha channel automatically. Existing secrets/WireGuard/4B preserved.
