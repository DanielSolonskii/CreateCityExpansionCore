# Launcher live server status

The homepage polls native `server_status` every 15 seconds, without overlapping requests. It uses Minecraft 1.21.1 STATUS (protocol 767) over verified TLS on the existing `RECREATED_GAME_TLS_HOST` / `RECREATED_GAME_TLS_PORT` build-time endpoint, identical to the game's gateway. No Backend/API deployment, game login, player credentials, CCEC modifications or log parsing.

Online requires valid bounded status JSON and matching ping/pong. Player count/limit are Minecraft's current `players.online` / `players.max`. Ping is the ping/pong round trip, not a fabricated number. Failed queries are UNKNOWN (no connection), not proof of global server downtime; count and ping become unavailable rather than retaining stale values. Browser preview cannot use native TCP and explicitly shows unavailable status. Homepage label: `Альфа тестирование`.

Build with existing production environment, including `VITE_ACCOUNT_API_BASE_URL=https://api.recreated.ru`, `RECREATED_GAME_TLS_HOST=api.recreated.ru`, `RECREATED_GAME_TLS_PORT=25565`, `RECREATED_GAME_SERVER_ID=recreated-alpha`. Updater, authentication and Play are unchanged.

Tests: `npm test`, `npm run build`, `cargo test`. Optional real read-only Windows-to-production transport check: set `RECREATED_STATUS_SMOKE=1`, then `cargo test server_status::tests::live_production_status -- --nocapture`. Installer: `npm run tauri build -- --bundles nsis`. Builds do not publish installers automatically.
