# ADR-0002: Identity Foundation

- Статус: принято
- Дата: 2026-10-03

## Решение

`platform_user.id` — внутренний UUID, независимый от Minecraft, Telegram и
будущего способа входа. Минимальные статусы: `ACTIVE`, `DISABLED`.
Статус описывает запись платформы и пока не является механизмом authorization.
`created_at` и `updated_at` — UTC Instant, управляемые JPA callbacks;
updated_at меняется при изменении самого platform_user, а не имени Minecraft.

Minecraft хранится в отдельной `minecraft_identity`: Minecraft UUID является
первичным ключом; обязательный FK `platform_user_id` имеет UNIQUE constraint.
Так PostgreSQL обеспечивает уникальность Minecraft UUID и максимум одну
Minecraft identity на пользователя. Platform user может существовать без identity.
Удаление пользователя с существующей identity блокируется FK; cascade отсутствует.

`current_name` — изменяемое отображаемое имя длиной до 16 символов, не ключ.
Одинаковые имена разрешены. Чтение и связи всегда используют UUID.
Наличие записи не доказывает владение Minecraft-аккаунтом. Проверяемая привязка
остаётся будущим milestone; fixtures не являются способом регистрации.

Будущая `telegram_identity` сможет ссылаться на тот же platform UUID своей
таблицей. Launcher authentication также сможет ссылаться на platform_user.
Обобщённая provider/credential-модель пока не добавляется.

## Read API

- `GET /api/users/{platformUserId}` — id, status, createdAt, updatedAt и
  minecraftIdentity (null при отсутствии).
- `GET /api/identities/minecraft/{minecraftUuid}` — platformUserId,
  minecraftUuid, currentName.
- Ошибочный UUID: 400; отсутствующая запись: 404; успешный ответ: 200.
- Read API не меняет данные и возвращает DTO, а не JPA entities.

Это внутренний foundation API без authentication; публикация в недоверенную
сеть требует отдельного решения об access control. По умолчанию backend слушает
loopback. Статус DISABLED не скрывает запись из внутреннего read API.

## Тестирование

Test-only `IdentityFixtures` создаёт записи через repositories и транзакции.
Helper не входит в production JAR и не имеет HTTP route. Один набор контрактных
тестов выполняется на H2 и на PostgreSQL через `postgresTest`: schema constraints,
repository/service поведение, переименование и настоящий HTTP API.

## Согласованность с предыдущими решениями

ADR-0001 сохраняется: внешние аккаунты принадлежат backend, игровая идентичность
и permissions проверяются Minecraft server. Integration contract не расширен:
создание записей fixtures не означает подтверждённую привязку actor.
