# CreateServerPlatform

> Этот README содержит исторические milestone-описания. Актуальное состояние 14.10.2026 и onboarding команды: [State команды](../docs/audit-2026-10-14/STATE_RU.md).
> Game Session 4B уже реализован; production auth REQUIRED и Telegram service active подтверждены SSH-аудитом. Текущая копия платформы для команды — `platform/` в CCEC GitHub.

Внешняя платформа Create Server рядом с CreateCityExpansionCore:
backend API, Telegram bot, launcher, infra и docs.

## Реализовано

- Backend Java 21 / Spring Boot / Gradle, PostgreSQL, JPA и Flyway V1–V3.
- Infrastructure health/version и Identity Foundation read API.
- Create Server Account: BCrypt password, pending registration, обязательная
  Telegram verification, login для ACTIVE verified accounts.
- Telegram bot Java 21 / Gradle: long polling, /start, launcher download button,
  verification deep link; без прямого доступа к PostgreSQL.
- Backend H2/PostgreSQL tests и bot fake-HTTP tests.

## Структура и запуск

- backend/ — API; запуск и environment: backend/README.md.
- telegram-bot/ — самостоятельное приложение; telegram-bot/README.md.
- launcher/ — Windows React/TypeScript/Tauri GUI; на VDS как runtime не переносится.
- infra/ — примеры инфраструктуры; для установленного локального PostgreSQL
  Docker не нужен. Redis в runtime приложений отсутствует.
- docs/ — architecture, integration-contract, ADR-0001/0002/0003 и roadmap.

Backend health/version работают с PostgreSQL. Для registration нужны
TELEGRAM_BOT_USERNAME и BOT_BACKEND_SECRET. Bot дополнительно требует настоящий
TELEGRAM_BOT_TOKEN. Secrets задаются только в environment; .env.example —
справочник, приложения не загружают его автоматически.

## Identity boundary

Create Server использует собственный аккаунт, Minecraft работает в offline-mode.
Microsoft/Mojang authentication не является identity платформы. Offline
username/UUID не доказывают личность. Будущий CCEC проверит game session через
backend. Game session и CCEC authentication пока не реализованы.

Backend владеет account/identity. CCEC остаётся источником истины для игрового
мира, экономики, Freight/Railway и физических операций. Account login сейчас
проверяет credentials, но не выдаёт Minecraft admission token.

Последняя проверка: docs/milestone-3-verification.md.

## VDS deployment

Рабочий Minecraft instance `CREATED`, PostgreSQL и backend перенесены на VDS;
Telegram service подготовлен, но отключён до настройки реальных secrets.
[Отчёт, проверки и команды обслуживания](docs/vds-deployment.md),
[выбор исходного instance до переноса](docs/vds-source-selection.md).
Systemd units и Linux start/stop scripts находятся в `infra/`.
Milestone 4B не реализован; публичный offline-mode Minecraft пока не имеет
platform authentication и допускает подмену имени. Проверка входа игроком нужна
для полного runtime acceptance. Secrets хранятся только на VDS, не в проекте.
