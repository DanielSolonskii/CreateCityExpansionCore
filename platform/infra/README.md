# Infrastructure

Локальная инфраструктура и будущие deployment-манифесты.

- `compose.yaml` поднимает только PostgreSQL и Redis для разработки.
- backend, bot и launcher пока запускаются отдельно после выбора стека.
- production secrets не хранятся в Git и не передаются в launcher.
- Minecraft server bridge должен слушать приватный интерфейс или защищённую сеть.

Для локального запуска из корня проекта:

```text
docker compose -f infra/compose.yaml --env-file .env up -d
```

## Текущий VDS (без Docker и Redis)

Production использует штатные Ubuntu PostgreSQL/OpenJDK 21 packages и systemd.
`compose.yaml` — прежний dev example, на VDS он не используется.

- `systemd/create-server-backend.service`: localhost API, защищённый EnvironmentFile.
- `systemd/create-server-minecraft.service`: NeoForge runtime, штатный console stop.
- `systemd/create-server-telegram-bot.service` + `create-server-telegram-bot.service.d/wireguard.conf`:
  активный бот от отдельного пользователя `createtelegram`; secrets только на VDS.
- `systemd/create-server-telegram-guard.service`, `create-server-telegram-routing.service`
  и `wireguard/`: selective routing + fail-closed для бота; `wg-quick@wgtelegram`
  использует закрытый server-side config с `Table = off`, без изменения default route.
- `start-linux.sh` / `stop-linux.sh`: Linux запуск через установленный unix_args.txt
  и сохранение мира через service-user-only console FIFO.

Backend опубликован через Caddy: https://api.recreated.ru (80/tcp и 443/tcp).
Сам Backend, PostgreSQL и Caddy admin остаются только на localhost.
Существующие SSH, Minecraft/Sable и правило 10050/tcp сохранены.
Launcher остаётся Windows-клиентом и на этом этапе не пересобирался.
Конфигурация: `caddy/Caddyfile`; актуальный
[HTTPS deployment и Launcher URL](../docs/backend-https-deployment.md).
Units/scripts соответствуют текущему `/opt/create-server`; не устанавливать их
вслепую поверх другого runtime. Секреты и мир в Git не добавляются.
Полный [deployment report и runbook](../docs/vds-deployment.md).
Актуальный [WireGuard deployment и проверки после reboot](../docs/telegram-wireguard.md)
уточняет более раннее состояние Telegram Bot в migration report.
