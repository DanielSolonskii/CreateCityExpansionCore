# RE:CREATED Launcher — CCEC snapshot

**Сверка состояния: 2026-11-04.** [Актуальный аудит CCEC/Cargo](../../docs/audit-2026-11-04/README.md) описывает интеграцию 1.0.2.15. Ранее датированные результаты проверок ниже сохраняют свою дату; новый игровой/production прогон ими не подтверждается.

Актуализировано 02.11.2026. Windows desktop launcher: React 19, TypeScript 5.7,
Vite 6, Tauri 2/Rust. `package.json`, `Cargo.toml`, `tauri.conf.json` согласованы
на **0.1.13**; это source version, не подтверждённый latest installer.
[STATE](../STATE.md) и [аудит](../../docs/audit-2026-11-02/PLATFORM_AUDIT_RU.md)
описывают эту вложенную копию; standalone CreateServerPlatform не проверялся.

**Native сборка snapshot заблокирована:** `cargo test --locked --offline`
подтвердил отсутствующие `social_links`, `presence`, `presence_ipc` и handler
несуществующей команды `settings::launcher_memory` в [lib.rs](src-tauri/src/lib.rs).
Из текущего checkout не подтверждено получение рабочего installer.

## Реализовано в коде

- Registration с alpha key, QR/коротким кодом и Telegram confirmation/polling.
- Trusted devices и восстановление через Windows Credential Manager.
- Native Game Session capability/ticket, TLS transport, запуск Java/NeoForge.
- Signed game manifest, SHA-256 repair/download managed files и config seeds.
- Отдельный signed launcher self-update с sequence/version/hash checks.
- Status/news/profile, persisted settings и telemetry adapters.

Исходники: [Rust](src-tauri/src/), [frontend gateways](src/services/),
[settings](docs/settings.md). `MockLauncherGateway` остаётся bootstrap display
snapshot; Play/status/news/profile используют отдельные adapters. Его fixture
build `0.4.2-alpha` не является current release. [Source README](docs/source-readme.md)
сохраняет provenance GUI, не mock-login инструкцию.

## Требования и проверка

Frontend требует Node.js/npm; точный `engines` в manifest не закреплён.
Node 24.21.0 выполнил release tests в аудите. Используйте `npm ci` с lockfile.
Native Windows требует Rust/Cargo, MSVC C++ Build Tools/Windows SDK и WebView2.
Точный MSRV не закреплён; dependencies фиксирует Cargo.lock. Default packaging
target — NSIS current-user. Credential store, Explorer и process helpers
ориентированы на Windows; macOS/Linux acceptance не подтверждена.

Из `platform/launcher`:

```powershell
npm ci
npm test
npm run test:release
npm run build
cargo test --manifest-path src-tauri/Cargo.toml --locked
```

`npm test` — Vitest/Testing Library; `test:release` — независимый Node suite,
который не требует node_modules; `build` — TypeScript + frontend bundle.
Cargo tests не являются GUI/Minecraft E2E. После исправления blocker и сверки
production config отдельная packaging-команда — `npm run tauri build`.
Проверено 02.11.2026 после `npm ci --no-audit --no-fund`: Vitest — 14 files /
72 tests PASS; `npm run build` (TypeScript + Vite production bundle) PASS.
`package.json`/`package-lock.json` не менялись. Node release tests прошли 6/6.
NSIS не собирался; native compile завершился описанной выше ошибкой.

Hermetic Python helpers:

```powershell
python -B -m unittest discover -s scripts -p test_release_workflow.py
```

Нужны Python 3/OpenSSL в PATH. На Windows client symlink tests пропускаются;
полный helper suite требует изолированного Linux staging. Test-команды не
публикуют release и не перезапускают сервер.

## Разработка и build configuration

После отдельного запуска dev backend:

```powershell
$env:VITE_ACCOUNT_API_BASE_URL = 'http://127.0.0.1:8080'
npm run dev -- --host 127.0.0.1
# Native debug после исправления отсутствующих исходников:
npm run tauri dev
```

Vite слушает 1420 и проксирует `/account-api` в указанный origin.
`VITE_ACCOUNT_API_BASE_URL` — public build-time origin: HTTPS или loopback HTTP,
без credentials/path/query/fragment. `.env.local` настраивает Vite, но не Rust:
native debug получает compile-time environment процесса. Browser UI не запускает
Minecraft и не заменяет native trusted-device flow.

[build.rs](src-tauri/build.rs) для **release** закрепляет следующие public
values и отклоняет несовпадающие overrides:

| Параметр | Значение |
| --- | --- |
| `VITE_ACCOUNT_API_BASE_URL` | `https://api.recreated.ru` |
| `RECREATED_GAME_TLS_HOST` | `api.recreated.ru` |
| `RECREATED_GAME_TLS_PORT` | `25565` |
| `RECREATED_GAME_SERVER_ID` | `recreated-alpha` |

Frontend Vite build требует согласованного public environment. Эти адреса
описывают код, не проверенную доступность сегодня. Native game debug требует
согласованных `RECREATED_GAME_*`, backend/CCEC/TLS; dev frontend не создаёт их.
Не помещайте bot/backend/game service secrets в VITE, bundle или repository.

## Данные и identity

Game/runtime/settings — `%LOCALAPPDATA%\Recreated`; alpha instance отделён от
мира сервера. Preferences — `launcher-settings.json`, device secret — Windows
Credential Manager с endpoint-scoped target. Self-updater имеет отдельный
`%LOCALAPPDATA%\RE-CREATED Launcher Updater`. Не удаляйте saves/config/logs ради
«чистой» сборки.

Backend выдаёт стабильный `cs…` join name и offline UUID отдельно от platform
username. Password login сам по себе не авторизует запуск: требуется trusted
device. Display preferences не меняют identity/город. Legacy username helper
не доказывает, что текущий flow передаёт platform username напрямую в Minecraft.

## Релизы — отдельная операция

Быстрый deploy текущего CCEC на сервер и в игровые каналы Windows/macOS: из корня `./Deploy.ps1`. Подготовка без публикации — `./Deploy.ps1 -DryRun`. Версия релиза и источники выбираются автоматически, подробности пишутся в лог; [инструкция и продолжение после сбоя](../docs/quick-deploy.md).

[Release.ps1](scripts/Release.ps1): `dry-run`, `server-deploy`, `publish`,
`server-rollback`. Обычный dry-run может читать live manifest/сервер по сети;
локальный вариант — `dry-run -Offline` с ранее проверенным signed `-Previous`.
`server-deploy` меняет server files и проверяет readiness, `publish` переключает
client channel, rollback требует отдельный backup contract.

Inputs `release-input/client` и `release-input/server` — локальные allowlisted
источники, не гарантированно включённые в checkout. Private signing key не
хранится в Git. Build/commit/push сами по себе ничего не развёртывают.
См. [controlled release](../docs/controlled-release-workflow.md),
[distribution](../docs/launcher-distribution.md),
[self-update](../docs/launcher-self-update.md).

## Защита обновления CCEC — 2026-11-04

`Release.ps1 dry-run/server-deploy` проверяет Economy/Cargo SavedData по metadata из фактического кандидатного CCEC JAR. Read-only `inspect-server.mjs` передаёт JAR в память Python через SSH stdin; production файлы не записываются. Deploy повторяет проверку после graceful final save до установки.

```text
python -B scripts/test_save_compatibility.py
python -B scripts/test_release_workflow.py
```

Новый набор: 12 regression tests PASS; прежний набор на Windows: 11 PASS / 8 Linux-only skips. Это не сборка installer и не live acceptance. При неподдерживаемом downgrade rollback останавливается; legacy JAR без metadata требует ручного восстановления.
