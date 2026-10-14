# Архитектура CreateServerPlatform

Локальная реализация authentication boundary 4B описана в
[Game Session 4B](game-session-4b.md). Production не изменён. Backend не получает
владение gameplay/SavedData: он авторизует platform identity до player creation.

## Контекст

Платформа дополняет CreateCityExpansionCore внешними каналами доступа и
доставкой клиентской сборки. Она не переносит доменную модель CCEC из мира
Minecraft во внешнюю базу.

```text
Telegram bot ─┐
              ├──> Backend API ──> Minecraft bridge ──> CCEC managers/SavedData
Launcher ─────┘          │
                         ├──> PostgreSQL (identity, audit, releases, projections)
                         └──> Redis (возможный будущий компонент; runtime пока отсутствует)
```

## Источники истины

| Данные | Источник истины |
| --- | --- |
| City, membership, permissions | CCEC `CityManager` |
| Wallet, treasury, listings, contracts, escrow | CCEC `EconomyManager` |
| Freight Points, shipment tokens, delivery | CCEC Freight pipeline |
| Railway ownership, routes, trips | CCEC Railway subsystem |
| Аккаунт платформы, Telegram binding | backend/PostgreSQL |
| Версии и подписанные manifests | backend/PostgreSQL + artifact storage |
| Внешние уведомления/read-модели | восстанавливаемая проекция CCEC events/snapshots |

## Инварианты интеграции

1. Minecraft server проверяет UUID, роль, состояние и предусловия каждой мутации.
2. Backend-команда имеет `commandId`, actor, timestamp и audit trail.
3. Повтор команды с тем же `commandId` не создаёт повторную мутацию.
4. Snapshot/event несёт монотонную ревизию; старое событие не перезаписывает новое.
5. Недоступность платформы не останавливает игровой сервер и физическую логистику.
6. Недоступность Minecraft переводит внешние операции в `unavailable`, а не в успех.
7. Telegram bot и launcher никогда не получают bridge token.

## Следующий технический шаг

Добавить в CCEC минимальный server-side bridge после отдельного impact analysis:
read-only snapshots сначала, затем небольшой allowlist команд. Не открывать
`SavedData` и внутренние manager-методы как универсальный удалённый API.

## Identity Foundation

Backend использует Java 21 / Spring Boot / Gradle, JPA и Flyway. Runtime Redis
пока отсутствует. Внутренний platform UUID отделён от Minecraft UUID и имени:
`platform_user` → optional `minecraft_identity`. PostgreSQL обеспечивает UNIQUE
Minecraft UUID и UNIQUE platform_user_id. Имя — изменяемое отображаемое поле.
Текущие read API не подтверждают владение Minecraft-аккаунтом. Решение: ADR-0002.

## Create Server Account / offline-mode

ADR-0003 уточняет account authority: backend владеет собственными credentials
и Telegram identity. Offline Minecraft username/UUID не является доказательством
личности; Microsoft/Mojang authentication не используется как identity сервера.
Telegram verification обязательна для activation. Будущий CCEC проверит game
session через backend; этот milestone не реализует admission protocol.
Игровое состояние сохраняет источники истины ADR-0001.
Phone verification уточняет только registration UX и proof lifecycle:
local QR + short code → explicit sender-bound Telegram button → atomic activation.
Additive V5 и durable code limiter: [ADR-0006](adr/0006-phone-telegram-verification.md).
Production Backend/Bot обновлены controlled deployment; существующий login
подтверждён пользователем. ACTIVE accounts и CCEC authority сохраняются.

Trusted devices добавляют отдельный Launcher credential lifecycle (V6), не
изменяя registration/account identity или Game Session. Credential хранится
в Windows Credential Manager, Backend — только hash; Telegram approval/revoke
проверяет numeric owner. Backend/Bot/V6 deployed 2026-10-04; manual device GUI/phone
E2E pending. [Deployment evidence](trusted-devices-deployment.md).
[Security/API/deployment](trusted-devices.md).
