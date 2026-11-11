# Create Server Platform — snapshot внутри CCEC

**Сверка состояния: 2026-11-04.** [Актуальный аудит CCEC/Cargo](../docs/audit-2026-11-04/README.md) описывает интеграцию 1.0.2.15. Ранее датированные результаты проверок ниже сохраняют свою дату; новый игровой/production прогон ими не подтверждается.

Актуализировано 02.11.2026 по исходникам этого репозитория. `platform/` содержит
backend, Telegram bot, Windows launcher и инфраструктурные шаблоны. Это
самостоятельные сборки внутри CreateCityExpansionCore, а не Gradle subprojects
мода. Соседний checkout CreateServerPlatform в этот аудит не входит.

Текущий [STATE](STATE.md) и [аудит](../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md)
отделяют исходники, локальные проверки и runtime. Документы с датами в
[docs/](docs/) сохраняют историю; прошлый deployment/SSH/GUI receipt не
подтверждает состояние сервисов сегодня.

## Состав

| Компонент | Назначение | Контракт сборки |
| --- | --- | --- |
| [backend](backend/README.md) | Accounts/verification/devices, game sessions, reports, bridge/telemetry, broadcasts/news, companion/community | Java 21, Gradle 9.2.1, Spring Boot 4.0.8, PostgreSQL/Flyway V1–V14 |
| [telegram-bot](telegram-bot/README.md) | Long polling, verification, меню/admin, уведомления | Java 21, Gradle 9.2.1, OkHttp 4.12.0; без прямой БД |
| [launcher](launcher/README.md) | React/TypeScript/Vite + Tauri/Rust, Windows NSIS | npm/Cargo; package version 0.1.13; есть native compile blocker |
| [infra](infra/README.md) | Dev PostgreSQL/Redis, systemd/Caddy/TLS/routing и release helpers | Шаблоны под конкретное окружение, не автоматическая установка |

Backend владеет platform identity и credentials. CCEC остаётся источником мира,
экономики, городов и физических операций. Game Session реализован в коде:
trusted device → capability → ticket → server consume. Password login сам по
себе не разрешает Minecraft-вход. Microsoft/Mojang authentication не является
реализованной identity платформы; offline UUID не доказывает владение аккаунтом.

## Локальная проверка

Из корня CCEC в PowerShell:

```powershell
Push-Location platform/backend
.\gradlew.bat test build --console=plain
Pop-Location
Push-Location platform/telegram-bot
.\gradlew.bat test build --console=plain
Pop-Location
Push-Location platform/launcher
npm ci
npm test
npm run test:release
npm run build
cargo test --manifest-path src-tauri/Cargo.toml --locked
Pop-Location
```

Linux/macOS Java wrapper: `./gradlew`. Для свежих caches нужны registry network
access и toolchains; `--offline` работает только с заполненными caches.
При ограниченных правах задайте `GRADLE_USER_HOME` на доступный каталог.
Корневой Gradle build и [CI](../.github/workflows/build.yml) собирают мод, а не
platform applications. `postgresTest` — отдельная backend task на выделенной БД.
Команды описывают интерфейс проверки, а не обещание успешной сборки: текущие
результаты и блокеры приведены в [STATE](STATE.md).

## Конфигурация и эксплуатация

Начните с README компонента. Java приложения не читают `.env` автоматически.
Корневой [пример](.env.example) содержит устаревшие/неиспользуемые переменные;
источники истины — `application.yaml`, settings classes, `BotMain`, launcher
`build.rs` и manifests. `change-me`/`replace-me` не являются рабочими secrets.

Backend по умолчанию слушает `127.0.0.1:8080`. Bot и CCEC используют разные
service secrets, которые нельзя передавать launcher/VITE. Внешние credentials
требуют проверенного HTTPS. Private `/api/internal/**` нельзя открывать через
public proxy. [Caddyfile](infra/caddy/Caddyfile) — snapshot allowlist: часть
новых news/Mini App/restart routes в нём отсутствует, live rules не проверялись.

Build/test/commit сами по себе не публикуют release. Server deploy/rollback,
client channel switch, installer publication и systemd installation — отдельные
операторские действия: [controlled release](docs/controlled-release-workflow.md).
В этом аудите сервисы не запускались, deployment и production network checks
не выполнялись; соседний standalone checkout и live production не проверялись.
