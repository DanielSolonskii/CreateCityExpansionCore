# Platform STATE — 07.11.2026

## Актуальное обновление

- Windows Launcher **0.1.15**, independent sequence **15**, APPROVED, optional self-update; актуальная Companion-база с боковой навигацией и отдельным профилем. Исходники platform/launcher синхронизированы с новым standalone checkout, затем дополнены локальной кастомизацией профиля и анимациями. Отсутствующие Rust-модули social_links/presence/presence_ipc и memory API восстановлены из исходной платформы; прежний native compile blocker устранён.
- Ошибочно опубликованный 0.1.14 содержал старый UI из импортированной копии. Его заменяет forward-release 0.1.15, а не downgrade. Выбор исходников по одному package version недопустим.
- Финальные frontend build / 80 tests и Rust release/NSIS build PASS. Визуальная и полная installer/game E2E-приёмка не выполнялась по просьбе пользователя не продолжать проверки.
- NSIS 0.1.15: 5574300 bytes, SHA-256 f0bd6abe63f2d139fc7eabbff195a955998186c05d513cd89c32f7cb200ba56c. Публичный установщик опубликован штатным signed publisher.
- Игровой канал **alpha-0.1.38 / sequence 39** опубликован после успешного серверного deploy. Сервер предупреждён за минуту через ccec maintenance announce 60; создан свежий backup мира. Backend/bot/migrations не развёртывались, macOS не обновлялся.
- Полный текущий отчёт, границы проверки и сохранённый backup: [CURRENT_STATE](../CURRENT_STATE.md).

Ниже сохранён исторический аудит 04.11.2026: его native-blocker и старые версии относятся к той дате и не являются текущим статусом.

---

## Исторический аудит 04.11.2026

**Сверка состояния: 2026-11-04.** [Актуальный аудит CCEC/Cargo](../docs/audit-2026-11-04/README.md) описывает интеграцию 1.0.2.15. Ранее датированные результаты проверок ниже сохраняют свою дату; новый игровой/production прогон ими не подтверждается.

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

## Обновление release safety — 2026-11-04

CCEC game release и launcher installer остаются разными продуктами. Целевой мод 1.0.2.15 не изменяет package version launcher 0.1.13 и не устраняет native compile blocker.

В `scripts/server-release.py` / `inspect-server.mjs` добавлен preflight реального кандидатного JAR и Economy/Cargo SavedData до stop/deploy, повтор после final save и проверка files-only rollback. 12 новых hermetic regression tests PASS; существующие 19 Python release tests: 11 PASS, 8 Linux-only skips на Windows. `node --check` PASS. Эти проверки не устанавливали мод и не переключали каналы.

Прежний production CCEC восстановлен на 1.0.2.13 / Economy15, Windows alpha-0.1.32 и macOS alpha-0.1.32-mac.1 не переключались. Финальные deployment/channel receipts публикуются отдельно после совместимого выпуска. Старые JAR без save metadata требуют ручной проверки восстановления.
