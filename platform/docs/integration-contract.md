# Контракт CCEC ↔ Platform

Отдельный контракт допуска Game Session (не gameplay snapshot transport):
[локальная реализация 4B](game-session-4b.md). В ней зафиксированы V7, private
boot/consume endpoints, независимый service credential и обязательный admission.

Документ фиксирует границу до выбора транспорта (локальный HTTP, WebSocket или
очередь). Поля и схемы должны быть вынесены в версионируемую спецификацию.

## Envelope snapshot/event

- `schemaVersion` — версия схемы;
- `serverId` — стабильный идентификатор игрового сервера;
- `revision` — монотонная ревизия потока;
- `occurredAt` — серверное UTC-время;
- `type` — тип события или snapshot;
- `payload` — server-authoritative данные;
- `correlationId` — связь с командой, если применимо.

## Envelope command

- `schemaVersion`;
- `commandId` — idempotency key;
- `actor` — platform account и подтверждённый Minecraft UUID;
  в offline-mode UUID сам по себе не доказательство личности: будущая игровая
  session должна проверяться CCEC через backend (ADR-0003). Этот протокол пока
  не реализован, account login response не является game admission.
- `type` — значение из закрытого allowlist;
- `issuedAt`, `expiresAt`;
- `payload`;
- `signature` или service authentication context.

## Первая read-only версия

- server health и совместимая версия CCEC;
- собственный профиль игрока и city membership;
- публичная карточка города;
- собственные уведомления и `CcecStatus` transitions;
- release compatibility для launcher.

Экономические и логистические мутации не входят в первую версию. Перед их
добавлением нужны threat model, матрица прав, idempotency storage и runtime-тесты.

## Ошибки

Ответ всегда различает: `SUCCESS`, `ACTION_REQUIRED`, `BLOCKED`, `WARNING` и
транспортную ошибку. Внешний клиент не выводит успех из HTTP-кода без
подтверждённого результата CCEC.
