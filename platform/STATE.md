# Platform STATE — 02.11.2026

## Область доказательств

Вложенный `platform/` в CreateCityExpansionCore, исходная точка `main` /
`ff4959ec8267291c36dc245357c27fba28cf8216`. Соседний standalone
CreateServerPlatform, installed artifacts/VDS, Telegram и Minecraft runtime
не входят в аудит. [README](README.md),
[команды и результаты](../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md).

## Исходники

| Компонент | Реализация | Версии / граница |
| --- | --- | --- |
| Backend | Account/alpha keys/verification/devices, Game Session, reports, bridge/telemetry, broadcasts/news, companion/Mini App/community | Java 21, Spring Boot 4.0.8, Gradle 9.2.1; artifact 0.1.0-SNAPSHOT |
| Database | Flyway V1–V14, JPA validate/startup migrations | PostgreSQL, dev compose 17-alpine; H2 только tests |
| Bot | Private menus, registration/devices, admin/reports/broadcast/telemetry, community/companion workers, optional Mini App | Java 21, Gradle 9.2.1, OkHttp 4.12.0; singleton polling |
| Launcher | Registration/devices, Windows credentials, native game auth/update/launch, self-update, news/status/profile/settings/telemetry | 0.1.13, Windows NSIS; есть compile blocker |
| Infra | Dev DB/Redis, systemd/routing/TLS/Caddy и explicit release helpers | Config snapshot, не live inventory |

Game Session 4B присутствует в коде. Старые README-фразы «game session не
реализован», «bot отключён» и «Flyway только V1–V3» удалены. Это исправление
документации не устанавливает текущий статус внешних сервисов.

## Подтверждённые ограничения

1. Native launcher: `lib.rs` объявляет отсутствующие `social_links`, `presence`,
   `presence_ipc` и handler несуществующей `settings::launcher_memory`.
   `cargo test --manifest-path src-tauri/Cargo.toml --locked --offline` дал
   E0583/E0433, exit 1. Features не урезались, missing source не восстанавливался.
2. Root CI запускает только root Gradle build; platform tasks не включены.
3. Frontend после locked `npm ci`: 14 Vitest files / 72 tests PASS,
   TypeScript/Vite production build PASS, Node release suite 6/6 PASS.
   Package manifests/lock не менялись. NSIS не проверялся; frontend checks
   не устраняют native blocker.
4. Python release helpers на Windows: 19 discovered, 11 passed, 8 skipped
   (Linux client symlink tests). Installer publisher требует POSIX `fcntl`.
5. Caddy snapshot не публикует launcher news, registration restart, Mini App;
   installed proxy rules не проверены.
6. Backend первоначально имел stale schema assertions (11 вместо 14) и
   Windows TempDir cleanup AccessDeniedException. Четыре test-only literals
   исправлены; с workspace `java.io.tmpdir` полный offline `test build`
   прошёл: 153 tests без failures. CompanionMediaTest не изменялся.
   Bot offline `test build` прошёл: 67 tests без failures. `postgresTest`
   не запускался; H2 не равен PostgreSQL acceptance.
7. Launcher имеет bootstrap mock display metadata и Windows-specific runtime.
   Версии mod artifact, launcher package и signed game release различаются.

## Следующая приёмка

- Отдельной code-задачей восстановить согласованный полный launcher source,
  затем frontend/native/build и Windows installer/UI acceptance.
- Проверить Java builds и PostgreSQL contracts на выделенной БД; решить вопрос
  отдельного platform CI coverage.
- При разрешённом runtime audit сверить installed hashes/configs/migrations,
  public route allowlist, TLS и health.
- Отдельно проверить registration → Telegram → trusted-device restore →
  TLS/game ticket consume → Minecraft login/logout, update/repair и delivery.

Документационный аудит не применял production migrations, не менял доступы,
не устанавливал/перезапускал сервисы и не публиковал signed release channels.
Исторические deployment notes — прошлые receipt, не readiness сегодня.
