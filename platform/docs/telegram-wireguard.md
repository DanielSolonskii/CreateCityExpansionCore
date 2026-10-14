# Telegram Bot: selective WireGuard routing

Configured on VDS 186.246.28.214 on 2026-10-03. No backend, Minecraft/CCEC,
Flyway or launcher code changed. The transferred client profile is exclusively
allocated to this VDS: do not activate the same WireGuard peer on a PC.

## Isolation

- Bot runs as dedicated `createtelegram` (current UID 994), not `createserver`.
- `/etc/wireguard/wgtelegram.conf`: root:root 0600; directory 0700. Private and
  preshared keys exist only in the protected VDS configuration, never this repo.
- `Table = off`: wg-quick does not replace the host default route. DNS settings
  from the imported client profile are not applied to the host.
- IPv4 policy rule priority 10000 selects table 51821 only for the bot UID.
  This table has a tunnel default and a permanent unreachable fallback.
- nftables `inet create_telegram_guard` permits that UID's loopback IPv4/IPv6
  and external IPv4 through wgtelegram; other external traffic is rejected.
- WireGuard outer UDP packets retain the originating socket UID. A narrowly
  scoped exception permits only mark 51820, the configured VPN endpoint/UDP port
  and eth0. Priority 9999 routes these encrypted marked packets through main.
  Bot has no CAP_NET_ADMIN/CAP_NET_RAW, so cannot set this bypass mark itself.
- No external IPv6 for bot; Java uses preferIPv4Stack. DNS uses the existing
  localhost systemd-resolved stub. Resolver traffic remains on the normal route.
- Backend remains http://127.0.0.1:8080, directly over lo. All TELEGRAM_PROXY_*
  variables remain blank: WireGuard is not an HTTP proxy or system-wide proxy.
- This isolates the entire bot service's external traffic, not a hardcoded list
  of Telegram destination IPs. Other service users retain their normal routes.

Guard, wg-quick and policy-routing units are enabled. Bot requires the guard and
policy-routing units, binds to wg-quick, and starts after the route. The guard has
no stop action: stopping WireGuard does not remove the fail-closed firewall.

## Verified

- Fresh SSH connections work; main default remains via 186.246.28.1 on eth0.
- Public Minecraft TCP status returns Minecraft 1.21.1. TCP/UDP 25565 listeners
  and existing UFW rules are unchanged. UDP is Sable, not generic Minecraft.
  A listener/firewall check is not a client-level Sable synchronization test.
- Backend PostgreSQL health UP, including under the bot UID.
- WireGuard handshake and HTTPS to api.telegram.org pass under bot UID.
- getMe returns recreating_bot; no webhook configured; long polling establishes
  a TLS connection sourced from 10.8.0.33. Bot systemd restart succeeds.
- With WireGuard stopped, external HTTPS fails immediately and localhost health
  still succeeds; unreachable fallback stays installed. Tunnel was restored.
- Actual /start reply was confirmed by the user. A reboot was explicitly
  authorized and completed: new boot ID, working SSH, all services active,
  restored policy/guard, fresh handshake/getMe and authenticated local Backend
  lookup. Backend health UP after startup; Minecraft reached Done and restored
  both TCP/UDP listeners. Nonfatal recipe and Sable tag errors remain unchanged
  in scope: no mod/config/world alterations were made to suppress them.

Backup before changes: `/opt/create-server/backups/telegram-wireguard-20261003-062553`
(root-only), containing original bot unit, netplan, routing/firewall snapshots,
and protected intermediate WG configuration. Netplan and SSH configuration were
not changed. No PostgreSQL/backend public ports were opened.

## Operations

```sh
systemctl status create-server-telegram-bot
journalctl -u create-server-telegram-bot -f
systemctl restart create-server-telegram-bot
systemctl status wg-quick@wgtelegram create-server-telegram-guard create-server-telegram-routing
ip rule show
ip route show table 51821
ip route show default
nft list table inet create_telegram_guard
wg show wgtelegram latest-handshakes
```

Do not run `wg showconf` or export secrets to tickets/logs. To recover after an
intentional VPN stop: start `create-server-telegram-routing.service` (pulls in
WireGuard), then start the bot. Starting WireGuard alone does not necessarily
restart a bot stopped by BindsTo. To change UID/interface/endpoint, review policy
rules and guard together and retain an independent working SSH session.
