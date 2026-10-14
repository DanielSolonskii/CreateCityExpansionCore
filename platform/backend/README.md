# CreateServerPlatform backend — Create Server Account

Java 21, Spring Boot 4.0.8, Gradle 9.2.1, Spring Data JPA, PostgreSQL и Flyway.
Redis и игровые интеграции в runtime отсутствуют.

## Локальный PostgreSQL на Windows

Нужна запущенная служба PostgreSQL и отдельная база, например `create_server`.
Создайте login role `create_server` с паролем и назначьте её владельцем базы
через pgAdmin. Backend не создаёт базу или роль; Flyway создаёт таблицы внутри
уже существующей базы. Используйте отдельную базу без чужих таблиц.

Все параметры задаются environment variables. Значения по умолчанию:
`POSTGRES_HOST=127.0.0.1`, `POSTGRES_PORT=5432`,
`POSTGRES_DB=create_server`, `POSTGRES_USER=create_server`,
`PLATFORM_BIND_ADDRESS=127.0.0.1`, `PLATFORM_API_PORT=8080`.
`POSTGRES_PASSWORD` обязателен и не имеет значения по умолчанию.
Дополнительно: `DB_POOL_SIZE=5`, `DB_CONNECTION_TIMEOUT_MS=3000`.

`.env.example` — справочник; Spring Boot автоматически его не загружает.
Не сохраняйте реальные пароли в конфигурации, README или Git.

Из каталога `backend` в PowerShell:

```powershell
$env:POSTGRES_DB = 'create_server'
$env:POSTGRES_USER = 'create_server'
$credential = Get-Credential -UserName $env:POSTGRES_USER -Message 'Пароль PostgreSQL'
$env:POSTGRES_PASSWORD = $credential.GetNetworkCredential().Password
.\gradlew.bat bootRun
```

После остановки удалите пароль из текущего окружения:
`Remove-Item Env:POSTGRES_PASSWORD`.
В Linux/macOS используются те же переменные и `./gradlew bootRun`.

При запуске Flyway применяет `V1__platform_infrastructure.sql`: одну техническую
таблицу с единственной строкой версии схемы. V2 добавляет platform_user и
minecraft_identity с UUID, внешним ключом и ограничениями уникальности.
V3 добавляет credentials, Telegram identity и registration sessions; V1/V2 не меняются.
V4 добавляет hash-only одноразовые Alpha Access Keys; V1–V3 не меняются.
JPA подключён; Hibernate имеет `ddl-auto=validate`, schema управляется Flyway.
Ошибка подключения или миграции останавливает запуск приложения.

## API

- `GET /api/health`: HTTP 200 и
  `{"status":"UP","checks":{"postgres":"UP"}}`.
  Проверяется реальный SQL-запрос технического маркера миграции.
  При недоступной БД или отсутствии маркера — HTTP 503 и статусы `DOWN`.
  Endpoint не раскрывает exception, URL, username или пароль.
- `GET /api/version`: HTTP 200, `name` и `version` из Gradle build-info.
  Пример: `{"name":"create-server-backend","version":"0.1.0-SNAPSHOT"}`.

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/api/version
```

Это health внешней платформы, а не статус Minecraft/CCEC. HTTP 503 проверяется
после потери соединения уже запущенным приложением; при недоступной БД на старте
приложение не запускается. Ответы запрещены к кэшированию.
Connection acquisition ограничено `DB_CONNECTION_TIMEOUT_MS`; это не полный
HTTP deadline. Права роли не должны позволять посторонним менять marker.

## Проверки и сборка

```powershell
.\gradlew.bat test build
java -jar build/libs/create-server-backend-0.1.0-SNAPSHOT.jar
```

Обычные тесты не требуют PostgreSQL: проверяют 200/503, version, обработку
ошибки JDBC, identity repositories/services, ограничения БД, read API и запуск
Spring/JPA/Flyway на H2 в PostgreSQL mode. H2 используется
только в тестах и не доказывает совместимость с настоящим PostgreSQL.

Отдельная проверка PostgreSQL (после задания тех же переменных окружения):

```powershell
$env:POSTGRES_DB = 'create_server_test'
.\gradlew.bat postgresTest
```

Тест применяет миграции и проверяет health/JPA и тот же identity-контракт на
PostgreSQL. Создайте отдельную тестовую базу заранее. Fixtures удаляют только
созданные ими записи UUID; схема не очищается, чужие записи не удаляются.
Регистрация и password login доступны через account API; игровые sessions пока отсутствуют.

## Identity Foundation

- `GET /api/users/{platformUserId}` возвращает внутренний `id`, `status`,
  `createdAt`, `updatedAt` и `minecraftIdentity` (или null).
- `GET /api/identities/minecraft/{minecraftUuid}` возвращает `platformUserId`,
  `minecraftUuid`, `currentName`.
- Успех: 200; отсутствующая запись: 404; UUID не в канонической форме: 400.
  UUID нечувствителен к регистру. POST/PUT/DELETE для identity не реализованы.

UUID платформы независим от Minecraft UUID. Minecraft UUID уникален; один
пользователь может иметь не более одной Minecraft identity. Имя изменяемо,
не уникально и не используется в lookup. Статусы `ACTIVE` и `DISABLED`
описывают запись платформы; V3 добавляет PENDING_VERIFICATION. Все статусы видимы
в authenticated internal read API. Login дополнительно требует Telegram identity.
Timestamps пользователя управляются JPA; изменение Minecraft name не меняет
updatedAt самого пользователя. Привязка не является доказательством владения
Minecraft-аккаунтом. Подробнее: `docs/adr/0002-identity-foundation.md` в корне проекта.

Identity read API теперь требует X-Bot-Service-Secret и предназначен для internal
использования. Игровое подтверждение identity остаётся отдельным milestone.
Создание fixtures реализовано только в `src/test/.../identity/IdentityFixtures.java`.
Оно использует repositories внутри транзакций и не попадает в production JAR.

## Account + Telegram Verification

Собственный login независим от Minecraft name. Telegram verification обязательна
для ACTIVE/login. Offline-mode game session и CCEC authentication пока отсутствуют.

Дополнительные environment variables:

- TELEGRAM_BOT_USERNAME: bot username без @, одинаковый с telegram-bot.
- BOT_BACKEND_SECRET: случайный secret минимум 32 characters, одинаковый с bot;
  без default. Не передавать launcher.
- REGISTRATION_TTL_SECONDS: 900 по умолчанию (60–3600).

Без bot username/service secret регистрация отключена (503). Health/version
продолжают работать. .env автоматически не загружается.

Username: 3–32 ASCII characters, первая буква, далее буквы/цифры/underscore;
lowercase. Password: 12+ code points, максимум 72 UTF-8 bytes.
BCrypt strength 12, random salt, {bcrypt} prefix. Password/secrets не логировать.

POST /api/registrations принимает JSON username/password/alphaKey и возвращает
registrationId, pollingToken, telegramDeepLink, expiresAt, verificationCode.
Launcher показывает локальный QR и короткий код, опрашивает GET /api/registrations/{id}
с Authorization: Bearer pollingToken. Telegram Desktop не требуется.
Status: PENDING, COMPLETED, EXPIRED; completion содержит platformUserId.
POST /api/auth/login успешен только для ACTIVE с Telegram: возвращает
AUTHENTICATED и platform UUID, без access token/game session.

Bot POST /api/internal/telegram/prepare передаёт registrationToken ИЛИ verificationCode
и numeric telegramUserId; затем explicit button отправляет confirmationToken,
telegramUserId, nullable telegramUsername в /api/internal/telegram/accept.
Оба endpoints требуют X-Bot-Service-Secret. Старый confirm — только pre-V5 sessions.
GET /api/internal/telegram/users/{numericId} возвращает связанный platform UUID,
platform username (nullable для identity без credentials) и status в безопасном
read-only DTO. Password hash и verification secrets не сериализуются.
Контракт: docs/account-api.md; решение: ADR-0003 в docs/adr.

Создать локальный secret без вывода в terminal:

```powershell
$secretBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
$env:BOT_BACKEND_SECRET = [Convert]::ToBase64String($secretBytes)
```

Передайте тот же secret процессу bot защищённым способом. Внешний deployment
требует TLS и proxy configuration. In-memory limits защищают один instance,
сбрасываются после restart. Phone code limiter дополнительно хранится в PostgreSQL;
bounded restart по polling secret — до 3 раз за 24 часа, без возврата alpha key.
V5 / deployment plan: [Phone verification](../docs/phone-verification.md).

Первый запуск Gradle Wrapper требует доступ к services.gradle.org, Gradle
Plugin Portal и Maven Central. Gradle/JDK caches можно перенаправить через
`GRADLE_USER_HOME` в доступный для записи каталог.

## Closed alpha: административная генерация

В защищённом окружении с PostgreSQL variables:

```sh
java -jar build/libs/create-server-backend-0.1.0-SNAPSHOT.jar --generate-alpha-keys=10
```

Необязательно `--alpha-keys-expires-at=2027-01-01T00:00:00Z` (будущий UTC instant).
CLI не поднимает HTTP server; batch commit предшествует одноразовому выводу
UUID и plaintext. Hash-only storage, concurrent consume, revoke и безопасное
обращение с выводом: [Alpha Access Keys](../docs/alpha-access-keys.md).
Новый JAR/CLI применяет V4 при запуске: НЕ запускать на production без отдельного
разрешения на deployment/migration. На VDS этот milestone не развёрнут.
