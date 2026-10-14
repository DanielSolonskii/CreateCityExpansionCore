# Milestone 3: фактическая проверка

Дата: 2026-10-03. Java 21, Windows, PostgreSQL 18.6.

- backend: gradlew test build — SUCCESS, 36 tests, 0 failures/errors.
- backend: gradlew postgresTest — SUCCESS, 27 tests, 0 failures/errors.
- telegram-bot: gradlew test build — SUCCESS, 5 tests, 0 failures/errors.
- PostgreSQL database: create_server_test, миграции 1/2/3 successful.
- После cleanup: platform_user/account_credentials/telegram_identity/
  registration_session содержат 0 fixture rows.
- V1/V2 сравнивались побайтово до установки; они не изменены.
- Production backend JAR и application distribution бота собраны.
- CCEC не изменялся; Minecraft, launcher UI и updater не запускались.
- Живой Telegram bot не запускался; все bot HTTP tests использовали local fakes.
- Password/service secrets/registration secrets не сохранены в project files.

Новый contract suite покрывает pending account, salted BCrypt, duplicate login,
validation limits, invalid/expired/reused token, разные verification/polling
secrets, Telegram identity conflict/DB constraints, ACTIVE transition, disabled/
unverified login, wrong password, polling status, internal authentication,
concurrent confirmation и username rate limit.

Сборка бота потребовала явной JavaCompile UTF-8 на Windows; после исправления
clean build и final build прошли без encoding errors. Jackson deprecation
warnings остаются; они не являются failed tests.

Решение и ограничения: ADR-0003. Контракты: docs/account-api.md.
Дальше нужны game session ADR/CCEC integration, registration recovery/retention,
live Telegram acceptance и deployment TLS/rate-limit policy.
