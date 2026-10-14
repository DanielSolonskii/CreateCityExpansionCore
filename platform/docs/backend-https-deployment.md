# Backend production HTTPS — 2026-10-03

Current endpoint: https://api.recreated.ru, DNS A: 186.246.28.214.
This report supersedes the localhost-only public-access statements in the initial
VDS migration report. The Backend itself still binds only to localhost.

## Deployment

- Official stable Caddy package 2.11.7; service `caddy` is active and enabled.
- `/etc/caddy/Caddyfile` proxies allowed API requests to `127.0.0.1:8080`.
- Let's Encrypt certificate obtained automatically; automatic renewal enabled.
- HTTP redirects to HTTPS with status 308.
- Public routes: GET health/version, POST registrations/login, GET registration
  status by UUID. Other routes, including internal Telegram APIs, return 404.
- No API changes, credentials in configuration, or request-body/access logging.
- Caddy configuration source: `infra/caddy/Caddyfile`.
- Pre-change UFW and initial Caddy configuration backup:
  `/opt/create-server/backups/caddy-20261003-162310` (root-only).

UFW added only 80/tcp and 443/tcp, for IPv4 and IPv6. Existing SSH 22/tcp,
Minecraft 25565/tcp, Sable 25565/udp, and previously present 10050/tcp were
preserved. The current 10050 rule differs from the initial migration audit;
this deployment did not add or remove it. PostgreSQL 5432, Backend 8080 and
Caddy administration 2019 remain loopback-only, without public firewall rules.
No default-route or WireGuard policy changes were made.

## Verified

- DNS resolution on VDS points to 186.246.28.214.
- Trusted HTTPS without disabling certificate validation, on VDS and Windows.
- `/api/health`: `{"status":"UP","checks":{"postgres":"UP"}}`.
- `/api/version`: `create-server-backend`, `0.1.0-SNAPSHOT`.
- HTTP health request: 308, Location `https://api.recreated.ru/api/health`.
- Internal Telegram route: 404 through public HTTPS.
- Caddy restart: active/enabled; health and version still succeed.
- Fresh SSH connection succeeds; Backend, Minecraft and Telegram Bot stay active.
- Listener inspection confirms Backend/PostgreSQL/Caddy admin are loopback-only.

One initial Windows HTTPS attempt had a DNS-resolution failure; subsequent
normal DNS requests succeeded. A separate `--resolve` HTTPS check also succeeded
with normal certificate validation. No reboot or Launcher account E2E was run
for this HTTPS deployment.

## Launcher configuration for the next build

```text
VITE_ACCOUNT_API_BASE_URL=https://api.recreated.ru
```

Launcher was not rebuilt or modified. Set this variable in the build process
environment for both frontend and native Tauri compilation, as described in its
README. Existing installed builds are not reconfigured merely by this document.
The native client does not need browser CORS; browser development uses the
existing development proxy. No general cross-origin browser policy was added.

## Operations

```sh
sudo systemctl status caddy
sudo journalctl -u caddy -n 100 --no-pager
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
sudo systemctl restart caddy
curl -fsS https://api.recreated.ru/api/health
curl -fsS https://api.recreated.ru/api/version
curl -sSI http://api.recreated.ru/api/health
```

Preserve Caddy's protected certificate storage and automatic renewal. Do not
expose Backend/ PostgreSQL directly or publish internal endpoints for testing.
