# Roadmap

## Этап 0 — каркас

- [x] Разделить backend, Telegram bot, launcher, infra и docs.
- [x] Зафиксировать server-authority и источники истины.
- [x] Подготовить локальные PostgreSQL и Redis.
- [ ] Выбрать runtime и инструменты сборки для каждого приложения.

## Этап 1 — read-only vertical slice

- [ ] Версионированная схема snapshot/event.
- [ ] Минимальный read-only bridge в CCEC после GitNexus impact analysis.
- [ ] Backend health + server projection.
- [ ] Launcher compatibility check.
- [ ] Telegram `/status` через backend.

## Этап 2 — identity и distribution

- [x] Локальный phone registration flow: QR/code, explicit Telegram confirmation,
  V5, durable code attempts и bounded polling-authorized restart.
  [Security / controlled deployment](phone-verification.md).
  Production Backend/Bot rollout выполнен с backup и Flyway V5; существующий
  drannch1k login подтверждён пользователем. Новый phone/Launcher E2E ожидается:
  [deployment evidence](phone-verification-deployment.md).

- [x] Alpha Access Keys: локальная V4 / atomic registration gate / Launcher field,
  H2/PostgreSQL/frontend/Rust tests и Windows build.
  [Схема и генерация](alpha-access-keys.md); [production deployment](alpha-access-deployment.md)
  выполнен с backup и Flyway V4. Новый alpha-gated GUI E2E ожидает пользователя.

- [x] Launcher Account Integration production baseline: новая регистрация
  `drannch1k` → Telegram verification → polling COMPLETED → login.
  Полный GUI E2E подтверждён пользователем 2026-10-03 на
  `https://api.recreated.ru`; [acceptance record](launcher-account-baseline.md).
  `Play` остаётся mock; это не реализация Game Session / Milestone 4B.

- [x] Milestone 3: собственный Create Server Account, pending registration,
  Telegram verification через long-polling bot, password login без game session.
- [x] Milestone 4A: source/API research, [Game Session design](game-session-protocol.md),
  [ADR-0004 proposal](adr/0004-game-session-and-ccec-authentication.md) и план 4B.
- [ ] Milestone 4B production acceptance: локальные Game Sessions/V7,
  CCEC REQUIRED/DISABLED, native broker и Java TLS реализованы;
  [контракт и проверка readiness](game-session-4b.md). Controlled deployment,
  legacy world identity migration и production multi-client acceptance отдельно.
- [ ] Recovery/resend истёкшей регистрации, пароль reset и retention.

- [x] Identity Foundation: platform UUID, отдельная Minecraft identity, V2,
  read API, test-only fixtures и общий H2/PostgreSQL contract suite.

- [ ] Безопасная привязка Minecraft UUID и Telegram ID.
- [ ] Подписанные release manifests и проверка хэшей.
- [ ] Атомарное обновление отдельного игрового профиля.
- [ ] Аудит и rate limiting.

## Этап 3 — уведомления и ограниченные команды

- [ ] Outbox/replay событий и восстановление проекций.
- [ ] Уведомления о значимых `CcecStatus` transitions.
- [ ] Allowlist команд с server-side authorization и idempotency.
- [ ] Интеграционные и ручные runtime-сценарии.
