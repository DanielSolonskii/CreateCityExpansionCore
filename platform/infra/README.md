# Инфраструктура платформы

Актуализировано 02.11.2026. Dev compose и deployment templates/helpers под
конкретное `/opt/create-server` окружение. Это не live VDS inventory: SSH,
installed configs, ports/TLS/health/systemd runtime здесь не проверялись.
[STATE](../STATE.md), [аудит](../../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md).

## Локальный PostgreSQL

[compose.yaml](compose.yaml): PostgreSQL **17-alpine**, Redis **7-alpine**,
loopback ports, persistent volumes. Backend/bot запускаются отдельно; Redis
в их runtime не используется и для текущего backend не нужен.
Нужны Docker Engine/Desktop и Compose v2. Из корня CCEC:

```powershell
$env:POSTGRES_DB = 'create_server_dev'
$env:POSTGRES_USER = 'create_server_dev'
$dbCredential = Get-Credential -UserName $env:POSTGRES_USER -Message 'Пароль локальной dev БД'
$env:POSTGRES_PASSWORD = $dbCredential.GetNetworkCredential().Password
docker compose -f platform/infra/compose.yaml up -d postgres
docker compose -f platform/infra/compose.yaml ps
# После работы:
docker compose -f platform/infra/compose.yaml stop postgres
Remove-Item Env:POSTGRES_PASSWORD
```

Команды не выполнялись в аудите. Init database/user/password применяются при
создании нового data volume; env change не пересоздаёт существующую БД.
`down -v` не является обычной остановкой. Backend получает те же variables
отдельным процессом. `platform/.env.example` — старый справочник с частично
неиспользуемыми переменными, `change-me`/`replace-me` не рабочие secrets.

## Сохранённые templates

| Файлы | Назначение / ограничение |
| --- | --- |
| [backend unit](systemd/create-server-backend.service) | Java 21 app.jar, `createserver`, protected EnvironmentFile |
| [Minecraft unit](systemd/create-server-minecraft.service), [start](start-linux.sh), [stop](stop-linux.sh) | MC 1.21.1/NeoForge 21.1.251, FIFO console, save/stop wait; фиксированные paths |
| [bot unit](systemd/create-server-telegram-bot.service), [drop-in](systemd/create-server-telegram-bot.service.d/wireguard.conf) | Base `createserver`; drop-in меняет на `createtelegram`, требует routing/guard/WireGuard |
| [WireGuard helpers](wireguard/), [units](systemd/) | Selective routing/fail-closed для bot; нужна подготовленная Linux конфигурация |
| [Caddyfile](caddy/Caddyfile) | Public API allowlist, `/launcher`/`/releases` file roots под `api.recreated.ru`, loopback backend |
| [game-auth](game-auth/) | stunnel TLS template и отдельный secret EnvironmentFile |
| [launcher publisher](launcher/) | Signed installer publication/channel, POSIX file locking |
| [broadcast helpers](broadcasts/) | Server-specific deployment/smoke, читают installed secrets; не generic local tests |

Units hardcode Java path `/usr/lib/jvm/java-21-openjdk-amd64`, service users,
memory limits и `/opt/create-server`; это не переносимый installer.
WireGuard private config, world, DB dumps, TLS private keys и реальные
EnvironmentFiles не должны добавляться в Git.

## Proxy contract и gaps

Public origin/file roots в Caddyfile описывают snapshot, не live TLS/DNS.
Backend default — `127.0.0.1:8080`, PostgreSQL должен оставаться private.
Public edge использует allowlist с catch-all 404, не proxy всех `/api/**`.
Private bot/game/identity endpoints публично открывать нельзя.

Caddy snapshot не содержит `/api/launcher/news`, registration `/restart` и
`/api/mini-app/**`, хотя эти API реализованы в коде. Их доступность через этот
файл не обеспечивается. Нужна отдельная конфигурационная/runtime сверка;
это не доказательство ошибки live production. Game TLS template требует
согласовать public IP, bind interface, certificate и CCEC auth settings.

## Проверки

Node suite из `platform/launcher`: `npm run test:release`. Python helpers из
корня CCEC:

```text
python -B -m unittest discover -s platform/launcher/scripts -p test_release_workflow.py
python -B -m unittest discover -s platform/infra/launcher -p test_publish_launcher.py
```

Вторая suite требует POSIX `fcntl`/Linux: на Windows import падает. Первая на
Windows пропускает client atomic-symlink publication tests. Это hermetic checks,
не реальные systemd/world/Telegram tests. `broadcasts/check-broadcasts.py`
читает server secrets и обращается к live endpoints; не запускайте его как
обычный локальный unit test.

Install/enable/restart, routing/firewall/TLS changes и publication — отдельные
операции. История: [VDS](../docs/vds-deployment.md),
[HTTPS](../docs/backend-https-deployment.md), [WireGuard](../docs/telegram-wireguard.md),
[controlled release](../docs/controlled-release-workflow.md).
