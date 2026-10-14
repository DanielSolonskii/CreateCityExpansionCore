# Milestone 4A — Game Session Protocol & CCEC Authentication Design

Уточнение 2026-10-04, явно подтверждённое пользователем: постоянный `cs…` alias,
старые world identities переносить отдельно; TLS непосредственно в Java
Minecraft/CCEC, без Launcher loopback TCP tunnel. Ниже сохранён исторический
дизайн 4A; актуальный локальный API/V7 и deployment gate:
[Game Session 4B](game-session-4b.md). Production пока не изменён.

Дата исследования: 2026-10-03. Статус: proposed; production implementation отсутствует.
Решение предлагается ADR-0004. Все имена новых классов, таблиц, endpoints и
настроек ниже — план 4B, а не описание уже существующего API.

## 1. Границы и результаты исследования

Изучены backend account/identity services, controllers, AccountGuard, settings,
JPA model, application.yaml, Gradle, V1–V3, account-api, architecture,
integration-contract, roadmap, ADR-0001–0003 и milestone-3-verification.
В CCEC исследованы initialization/networking/configuration и UUID-dependent
city/economy/railway/mine seams; не весь gameplay.

Backend сейчас: Java 21, Spring Boot 4.0.8, Gradle 9.2.1, JPA, PostgreSQL,
Flyway; Redis отсутствует. V1 — infrastructure marker. V2 — platform_user и
отдельная minecraft_identity с UNIQUE minecraft UUID и platform user FK.
V3 — credentials, Telegram identity и registration lifecycle. Регистрация
НЕ создаёт Minecraft identity. AccountService.login проверяет BCrypt, ACTIVE
и наличие Telegram binding, но возвращает только AUTHENTICATED/UUID/username.
Это не credential, не launcher session, не разрешение на join.

AccountGuard сейчас защищает ВСЕ /api/internal/** одним BOT_BACKEND_SECRET.
Нельзя просто добавить /api/internal/game/**: иначе bot credential станет
game credential. В 4B нужны отдельные route policies до общей bot-проверки.
Bot не участвует в игровом входе после verification.

Согласованность документов: ADR-0001 сохраняется, gameplay/SavedData остаются
на сервере. Его требование продолжать игру при outage относится к игровому
циклу, не к новым непроверенным joins. ADR-0002 сохраняет независимый platform
UUID, один Minecraft UUID и изменяемый current_name. Техническое join_name
предлагается отдельным неизменяемым полем, НЕ заменой current_name.
ADR-0003 требует backend admission — проектируем именно его.
Обнаруженная историческая неточность: ADR-0002 описывает read API без auth;
актуальный AccountGuard и ADR-0003 уже требуют bot secret. Код имеет приоритет;
ADR-0002 как историческое решение здесь не переписывается.

### Проверенные CCEC seams

Корень: E:/CreateCityExpansionCore_MERGED/createcityexpansioncore-template-1.21.1.
Все следующие пути относительно этого корня. Исследован текущий checkout
HEAD 93975da59142674a4ae3c644829d520540827e42 с пользовательскими незакоммиченными
изменениями. gradle.properties: Minecraft 1.21.1 / NeoForge 21.1.251 / Java 21.

| Реальный файл / символ | Найденное поведение / значение для дизайна |
| --- | --- |
| src/main/java/com/example/examplemod/CreateCityExpansionCore.java:775, 1058 | Constructor регистрирует common setup, RegisterPayloadHandlersEvent, game-bus listeners и COMMON Config.SPEC. registerPayloads использует registrar("1") и PLAY handlers; server actor берётся из context.player(), а не client UUID. |
| src/main/java/com/example/examplemod/CreateCityExpansionCoreClient.java | Dist.CLIENT entrypoint, client setup/screens/renderers и client payload delegates. Сейчас нет credential reader или game auth. Существующие PLAY delegates обращаются к Minecraft.player и не подходят CONFIGURATION. |
| src/main/java/com/example/examplemod/Config.java | Общая ModConfigSpec с gameplay/economy settings; authentication mode отсутствует. Нужна отдельная SERVER spec без secrets, не изменение gameplay settings. |
| src/main/java/com/example/examplemod/network/CreateCityPayload.java | CustomPacketPayload и StreamCodec — существующий pattern, не auth proof. |
| src/main/java/com/example/examplemod/city/CityManager.java:428, 448, 671, 723 | SavedData.Factory/load/save; createCity принимает owner UUID; getCityByOwner/getCityByMember сравнивают UUID. Owner/member/invite/project creator сохраняются как UUID NBT. |
| src/main/java/com/example/examplemod/city/City.java, CityMember.java, CityInvite.java | Owner/member/invite identities — UUID; display names не меняют ключи permissions. |
| src/main/java/com/example/examplemod/city/CityMapSavedData.java | Дополнительный world SavedData; не место для credentials или platform authority. |
| src/main/java/com/example/examplemod/economy/EconomyManager.java:1094 и EconomyNetworkHandlers.java | Wallets по player UUID, listing creator/counterparty, contract createdBy/acceptedBy, infrastructure registeredBy, shipment actor UUID. Handlers проверяют права через server player UUID. City/trip/offer UUID — отдельные domain IDs, не player IDs. |
| src/main/java/com/example/examplemod/railway/RailwayManager.java и RailwayNetworkHandlers.java:119 | Personal train/station/network owner — player UUID; CITY owner — city UUID. Нельзя массово заменять все UUID на platform UUID. |
| src/main/java/com/example/examplemod/mine/MineNetworkHandlers.java:69, 107, 245 | Commission/build permissions по player.getUUID(); creatorId связан с controller/project. |
| src/main/java/com/example/examplemod/electrical/MineControllerBlock.java:81, 115; mine/MineControllerItemData.java:57; mine/MineControllerBlockEntity.java | Creator UUID записывается при placement и переносится в item/block state; имя — metadata. Это тоже часть потенциальной world migration, не только CityManager. |
| src/main/resources/createcityexpansioncore.mixins.json | Уже используется Mixin: required=true, JAVA_21, CarriageBogeyAccessor, defaultRequire=1. Ранний login guard можно добавить отдельным server Mixin, не подменой city logic. |

Использован GitNexus Exploring, bound repository CreateCityExpansionCore:
query/context для city/mine/network flows, затем актуальные source files.
Индекс обновлён через analyze --index-only, overview подтверждает HEAD выше,
8574 symbols / 705 processes. Analyzer предупреждает об ограничении process
coverage; часть MCP query ещё возвращала старый кэш (4 commits behind), а старый
process resource уже отсутствовал. Поэтому graph — навигация, не доказательство
полноты; выводы и lifecycle проверены исходниками. Отсутствие графового caller
не принято за доказательство отсутствия вызова. Перед 4B edits обязательны
новые impact checks и предупреждения HIGH/UNKNOWN по правилам CCEC.

### Проверенные dependency sources, а не предполагаемые события

Прочитаны exact-version NeoForge sources и patched Minecraft sources из
build/moddev/artifacts/neoforge-21.1.251-sources.jar, а также cached
net.neoforged:neoforge:21.1.251 sources. Это доступные локальные артефакты,
не результаты запуска сервера.

| Реальный API / source | Подтверждённый контракт |
| --- | --- |
| RegisterConfigurationTasksEvent | Mod bus, getListener(), register(ConfigurationTask). |
| ICustomConfigurationTask | run(Consumer<CustomPacketPayload>), type(); finish явно на сервере. |
| PayloadRegistrar | configurationToClient/configurationToServer; optional() и versioned(); отдельный CONFIGURATION protocol, не playToServer. |
| IPayloadContext | listener(), connection(), reply(), disconnect(Component), enqueueWork(), finishCurrentTask(Type). player() в CONFIGURATION бросает исключение. |
| ICommonPacketListener | getConnection(), hasChannel(Type/ResourceLocation). |
| ServerCommonPacketListenerImpl | public getOwner() возвращает GameProfile; это способ получить profile у captured configuration listener после проверки concrete type, без client-supplied UUID и reflection. |
| ServerConfigurationPacketListenerImpl:83–110, 163–188 | Mod configuration tasks добавляются перед JoinWorldTask; только после completion создаётся ServerPlayer и вызывается placeNewPlayer. |
| ServerConfigurationPacketListenerImpl:213–219 | finishCurrentTask проверяет текущий task type; ранний FinishConfiguration не может легально завершить auth task как JoinWorldTask. Нужен malicious-packet runtime test. |
| ServerLoginPacketListenerImpl:119–161 | offline profile вычисляется из packet.name; canPlayerLogin, затем disconnectAllPlayersWithProfile идут ДО CONFIGURATION. |
| PlayerList:434–453 | disconnectAllPlayersWithProfile отключает старого игрока по UUID. CONFIGURATION auth сам по себе НЕ предотвращает duplicate-kick DoS. |
| UUIDUtil.createOfflinePlayerUUID | UUID.nameUUIDFromBytes(UTF-8("OfflinePlayer:" + exactName)); case-sensitive bytes. client --uuid не задаёт authoritative server UUID. |
| PlayerEvent.PlayerLoggedInEvent/LoggedOutEvent, NameFormat/TabListNameFormat | Login event слишком поздний для основной проверки; logout — cleanup; name events — display formatting, не смена identity. |
| PlayerNegotiationEvent | Класс существует, содержит Connection/GameProfile и enqueueWork(Future). В исследованном ServerLoginPacketListenerImpl нет его firing/gate перед duplicate eviction. Не выбирается как гарантированный ранний admission hook только по наличию класса. |

Официальное описание CONFIGURATION-before-join сверено с
[NeoForge 1.21.1 configuration tasks](https://docs.neoforged.net/docs/1.21.1/networking/configuration-tasks/),
регистрация сообщений — с [payload documentation](https://docs.neoforged.net/docs/1.21.1/networking/payload/),
SERVER configuration — с [configuration documentation](https://docs.neoforged.net/docs/1.21.1/misc/config/).
В 4B обязательна compilation/runtime проверка Mixin и ordering на этой exact version.

## 2. Рекомендуемая architecture и trust model

Минимальная версия обслуживает ОДИН production Minecraft world server и одну
service identity serverId. Один пользователь — одно активное TCP gameplay
подключение. Multi-server roaming, distributed online leases и transfer пока
не поддерживаются. Это устраняет распределённый lease split-brain при условии
«проверенная игра продолжается во время backend outage».

Backend — authority: own account credentials, ACTIVE, numeric Telegram binding,
Minecraft identity provisioning, launcher capability и single-use tickets.
CCEC server доверенный: наблюдает реальный GameProfile, connection, boot ID,
admission state и permissions. Клиент и launcher недоверенные: могут изменять
любые отправленные поля. Telegram username, Create account username и Minecraft
display name — labels. Bot подтверждает аккаунт один раз, не каждый join.

Граница одна: AuthenticationService + ConnectionAdmissionRegistry.
PlatformAuthenticationService устанавливает VerifiedPlatformIdentity;
DisabledAuthenticationService устанавливает LocalDevelopmentIdentity.
Обе предоставляют стабильный gameplayMinecraftUuid; только verified variant
имеет platformUserId и authenticated server/boot/connection context.
Gameplay продолжает получать ServerPlayer.getUUID(), не вызывает HTTP и не
знает пароля, Telegram, token или launcher. Future bridge принимает verified
actor либо явно LOCAL_DEV и не публикует LOCAL_DEV как platform account.

Flow:

```text
Password + existing Telegram binding
  -> Backend: short launcher capability (launcher memory only)
  -> immutable Minecraft join profile
  -> Minecraft via verified TLS game tunnel
  -> early duplicate reservation (before vanilla eviction)
  -> CONFIGURATION auth challenge from CCEC server
  -> CCEC client -> launcher IPC: public connection tuple
  -> Launcher -> Backend: issue short connection-bound game ticket
  -> Launcher -> child stdin -> CCEC client -> configuration proof
  -> CCEC server -> Backend: atomic consume with independent service credential
  -> verified identity bound to actual connection -> finish auth task -> PLAY
```

Обязательный production transport: HTTPS для backend и стандартный TLS для
всего Minecraft TCP stream. Offline login сам не устанавливает encryption:
в ServerLogin.handleHello offline branch не отправляет encryption challenge.
Нельзя отправлять reusable/bearer secret по открытому Интернету только потому,
что ticket короткий. TLS gateway forwards raw Minecraft bytes в loopback
Minecraft port на том же доверенном host; raw port не публикуется, firewall
проверяется извне. Client helper использует standard TLS hostname/certificate
validation, без trust-all и без своего шифрования. Это не Minecraft proxy с
client-controlled forwarded UUID. Сервер не доверяет UUID из proxy headers.

TLS helper — отдельный процесс с lifetime Minecraft, не launcher UI: закрытие
launcher не рвёт уже установленный game tunnel. Он слушает только loopback,
не принимает remote clients, работает без platform/service secrets; bounded
connections и cleanup при завершении Minecraft. Local same-user malware,
administrator/debugger и memory capture вне защищаемой границы. Выбор helper
packaging не реализован; transport interoperability — обязательный spike 4B.

## 3. Identity/UUID strategy

Platform identity — неизменяемый platform_user.id. Gameplay identity —
minecraft_identity.minecraft_uuid, равный UUID настоящего ServerPlayer.
Platform UUID никогда не подставляется вместо city/trip/entity UUID.

Для новых production accounts Backend один раз генерирует неизменяемое
техническое join_name: например `cs` + 14 lowercase random alphanumeric chars
(16 ASCII), UNIQUE. Вычисляет vanilla offline UUID точной строки, сохраняет
UUID UNIQUE и один FK на platform user. Collision -> новый alias до commit.
Это provisioning, а не доказательство владения Mojang account. Не используем
username пользователя как источник alias. join_name публичен, не secret и
сам по себе НЕ даёт admission. UUID является persisted identity key; formula
используется один раз при provisioning и как invariant validation.

Launcher получает join_name через authenticated profile endpoint и передаёт
Minecraft как обычный --username. Сервер независимо вычисляет offline profile;
Backend consume проверяет profile UUID И exact join_name по своей identity.
Нет изменения GameProfile UUID после login, нет произвольной UUID injection,
нет mapping lookup на каждом действии. Binding устанавливается лишь после
backend consume, до player creation. Profile UUID на клиенте нельзя считать
authority; серверное совпадение обязательно.

current_name остаётся изменяемым отображаемым именем до 16 chars. Account login
до 32 chars не обязан помещаться в MC profile, и его переименование (будущий
API) не меняет join_name/UUID. Display formatting через NameFormat/tab event и
CCEC UI; команды/прочие mods могут показывать технический alias — это UX
компромисс, не потеря ownership. Не обещаем глобальный nickname rewrite всех
Create/vanilla UI. Telegram numeric ID не зависит от username.

City owner/member/invites, player wallet, listings, personal railway и mine
creator остаются теми же Minecraft UUID в SavedData/NBT/playerdata после смены
display/account names. В 4B менять CityManager/EconomyManager/RailwayManager или
переписывать SavedData не требуется. Death/respawn и dimension change сохраняют
connection-bound verified identity; нельзя привязывать её к одному объекту
ServerPlayer, заменяемому при respawn.

Legacy minecraft_identity из V2 может содержать произвольный UUID без alias.
V4 НЕ перезаписывает такие IDs и НЕ «вычисляет» alias из текущего display name.
Для них nullable join_name = not provisioned; game ticket issuance запрещена
до явного administrative reconciliation. Если existing UUID соответствует
доказанному immutable alias, можно привязать alias отдельной controlled action.
Иначе нужен отдельный migration/import milestone, ownership audit и backup.
Username не доказывает право получить старое имущество.

DISABLED: ServerPlayer использует обычный offline UUID имени друзей. City,
membership, economy, mine и railway работают локально как сейчас; rename
vanilla имени может изменить локальный UUID и потерять доступ — известная
vanilla semantics, не platform bug. Никакой автоматической platform mapping.
Dev world не мигрирует сам в production. Нельзя открыть production world в
DISABLED: знающий техническое имя сможет impersonate owner. Production world
должен иметь локальный persistent auth-domain marker (не secret), запрещающий
его запуск в DISABLED; явный offline administrative export/override отдельная
опасная операция вне 4B. Новый DISABLED world не создаёт verified marker.
Переключение DISABLED world -> REQUIRED не переносит ownership; рекомендуем
отдельный новый production world. Forced import requires separate approved plan.

## 4. Launcher session и Game Session model

Launcher session — отдельная opaque capability: ID, platform FK, unique SHA-256
hash 32-byte SecureRandom secret, created/expires/revoked timestamps; lifetime
10 minutes absolute, без refresh token в первой версии. Scope только собственный
game profile, ticket issuance и logout. Backend повторно проверяет ACTIVE и
Telegram при каждом issue/consume. Не reuse registration polling token и не
reuse login response UUID. Новый endpoint создания launcher sessions вызывает
существующую проверку credentials; /api/auth/login semantics сохраняется.
Passwords только ввод/HTTPS login, не передаются Minecraft, не сохраняются.

Game Session (planned table game_session):

- id UUID, platform_user_id FK, minecraft_uuid FK;
- server_id, server_boot_id UUID, connection_id UUID, challenge_nonce hash;
- secret_hash UNIQUE (SHA-256 random 32-byte base64url secret); plaintext только
  однократно в issue response и transient launcher/client memory;
- created_at / expires_at UTC: TTL 120 seconds, bounded config 30–180;
- state ISSUED / CONSUMED / REVOKED / EXPIRED, consumed_at;
- saved verified decision/context для same-attempt idempotent consume.

Backend также хранит единственную configured game server identity и текущий
boot ID, подтверждённый service-authenticated boot registration. Server boot ID
новый каждый процесс. New boot registration разрешена только для выделенного
single deployment; два одновременных server processes с одним credential —
misconfiguration вне этого доверительного контракта. Supervisor предотвращает
split-brain. Старые boot-bound issued tickets после restart невалидны.
Backend restart сохраняет boot record и ticket states в PostgreSQL.

Ticket создаётся just-in-time при CONFIGURATION challenge, не до длительной
загрузки NeoForge. Tuple server/boot/connection/nonce создаёт server CSPRNG;
передаётся клиентом issuer, но не доверяется до сравнения с server consume.
Issuer проверяет configured serverId и current boot record. Атакующий может
просить ticket для произвольного tuple своего аккаунта, но не может заставить
сервер принять его на другой connection/UUID. Challenge НЕ credential.

Atomic consume: service auth -> lock platform user -> lock game session
(фиксированный порядок во всех кодовых путях) -> проверить hash, tuple,
deadline по backend clock, state, ACTIVE, Telegram, exact authoritative profile
-> CONSUMED + verified decision в одной PostgreSQL transaction. DB failure
rollback и deny. Все ID в consume приходят от trusted server; не от клиента
как actor. Секрет сравнивается constant-time. Consumed expiration не означает
истечение уже установленной gameplay connection.

Первый корректный consume выигрывает; different connection/boot/server/nonce
после consume получает отказ. Same tuple + same secret повторным service
запросом может получить ту же decision только до первоначального expires_at,
при ещё ACTIVE/Telegram; local server применяет её лишь если connection жива,
auth task всё ещё current и deadline не истёк. Ошибка HTTP после DB commit не
превращает session обратно в ISSUED. Retry не новый join, не новую сессию;
одно разрешённое повторение в пределах timeout. Новый connection всегда новый
ticket. Повтор proof packet на текущем connection не запускает parallel HTTP:
local state WAITING_PROOF -> VERIFYING -> VERIFIED, повтор coalesce/ignore;
different proof mid-verification -> disconnect. Поздний async callback после
disconnect/timeout ничего не завершает и не снимает чужую reservation.

Revocation/expiry — admission semantics. Уже допущенный игрок продолжает
играть при outage без постоянного account lookup. Немедленный online-ban
потребует отдельного trusted push/admin kick, не обещается данным протоколом.
Expired/consumed hashes могут удаляться после retention, но never resurrect.
Audit без secrets: reason code, session/connection ID, server ID, time; данные
минимизировать, не публиковать usernames/account existence в отказах.

## 5. Реалистичные варианты transport

| Вариант | Security / сложность | Launcher / client / server изменения | Lifecycle, reconnect, сторонний client |
| --- | --- | --- | --- |
| **A: child stdin pipe + CONFIGURATION ticket + TLS tunnel (выбран)** | Standard OS pipe, без credentials в argv/env/files; encrypted wire. Умеренная сложность: IPC, task, HTTP и TLS packaging. Copied raw bearer до consume всё ещё first-winner risk, но connection tuple ограничивает использование. | Launcher owns child input и HTTPS issuance; CCEC client читает pipe на worker и отвечает config payload; server gates configuration. TLS helper/gateway обязательны remote REQUIRED. | Just-in-time ticket, wipe references после send. Новый ticket при reconnect. Без CCEC/protocol/ticket REQUIRED deny; DISABLED не требует pipe/helper. |
| B: short-lived ACL-protected temp file + CONFIGURATION + TLS | Проще IPC, но secret на disk, backup/AV/crash residue, TOCTOU. One-use deletion не доказывает отсутствие копии. | Launcher writes restricted temp; client reads+deletes; server тот же. Для just-in-time нужны файловый request/response channel и cleanup. | Reconnect новый файл/ticket; чужой клиент без ticket deny; orphan files опасны. Не основной вариант. |
| C: loopback HTTP/IPC broker + CONFIGURATION + TLS | Гибкий reconnect, но port interception, broker auth capability, origin/CSRF, accidental public bind; больше attack surface. Windows named pipe ACL — сильнее, но отдельная OS implementation. | Launcher/broker server; client broker client с inherited bootstrap capability; game server тот же. | Broker живёт для reconnect, capability в памяти; launcher close needs independent broker. Не нужен первой версии. |
| D: ephemeral standard keypair и signature challenge / client HTTPS claim | PoP уменьшает риск copied transcript, но нужны key lifecycle, standard signing protocol, more endpoints. Без TLS active relay/gameplay hijack остаётся. Самодельное signing envelope не предлагается. | Launcher/key bridge, client signature или HTTPS claim, server/backend challenge registry. | Новое proof на reconnect; client still modifiable. Не даёт attestation официального launcher. Отложено. |
| E: JVM args/environment bearer / PLAY payload после join | argv часто виден process tools/crash logs; env наследуется, попадает в dumps. PLAY-after-login позволяет загрузить playerdata/войти в мир до gate. | Формально проще, но security boundary неправильная. | Отклонено. Password, launcher token и service secret так не передавать. |

Все серьёзные config-варианты работают в offline-mode, но НЕ делают username
proof. Для них REQUIRED server gate обязателен; optional channel не admission
permission. Ни один вариант не позволяет достоверно определить, официальный
ли launcher запущен: пользователь может написать свой клиент, войти в СОБСТВЕННЫЙ
ACTIVE+verified аккаунт и реализовать протокол. Цель — account authorization,
не software attestation/DRM. Иначе требование невыполнимо без иной trust model.

### Выбранный IPC контракт (proposal)

Launcher запускает JVM напрямую ProcessBuilder, без shell; stdin PIPE закрыт
для других пользователей средствами ОС. Никаких secrets в JVM flags,
properties, environment, log messages, launch manifests или client config.
--username содержит публичный immutable alias. Service secret никогда не
появляется в child process. Launcher session остаётся только launcher memory.

Client worker читает bounded length-prefixed frame со stdin: version, local
requestId, sessionId, 43-char game secret, matching tuple, expiresAt. Max frame
2 KiB, максимум один outstanding request, timeout, строгий decode, redacted
toString. JVM stdin — стандартный Java API, не выдуманный NeoForge event.
Работоспособность concurrent stdin reader с данной сборкой — spike 4B.

Client отправляет launcher публичный request marker в child stdout: version,
requestId, serverId/boot/connection/nonce. Launcher не доверяет произвольным
stdout logs: только bounded exact marker, expected configured endpoint/serverId,
один process и outstanding request; rate limit. Stdout не содержит proof,
password или launcher token. Unknown URL из server payload не посещается:
backend address/allowed server deployment берутся из launcher trusted config.
Модифицированный клиент может запросить лишь capability своего аккаунта.

Без pipe (обычный launcher) client не падает в DISABLED и не читает stdin
без нужды; в REQUIRED показывает «Запустите игру через авторизованный профиль».
С закрытым launcher нет new issuance: текущий PLAY остаётся, reconnect просит
открыть launcher/login заново. Нет reconnect grace credential в Minecraft.
Launcher session absolute TTL истёк -> reauthentication перед новым ticket.
TLS helper для текущего connection живёт независимо от закрытия launcher UI.

## 6. NeoForge connection boundary

Две разные защиты нужны одновременно.

1. **Early duplicate guard**: server-only Mixin в
   ServerLoginPacketListenerImpl.verifyLoginAndFinishConnectionSetup(GameProfile),
   HEAD, cancellable. В REQUIRED до vanilla disconnectAllPlayersWithProfile
   атомарно reserve profile UUID для Connection; если UUID online/reserved
   другим connection, disconnect NEW connection и cancel. Повторный tick той
   же connection идемпотентен. Не доверяет знанию alias, не выполняет HTTP,
   не authenticates user; это DoS/duplicate boundary. DISABLED — pass-through
   vanilla semantics. В actual sources нет проверенного cancellable NeoForge
   login event на этой точке: Mixin выбран явно, не объявлен существующим API.
2. **CONFIGURATION auth task**: RegisterConfigurationTasksEvent на mod bus.
   REQUIRED регистрирует task для КАЖДОГО connection; если auth channels
   отсутствуют, deny, не skip. DISABLED не регистрирует task и не создаёт
   backend client. auth registrar("auth-1").optional() только для новых
   auth messages: это совместимость DISABLED, а не возможность пропустить gate.
   Не менять существующие mandatory gameplay channels globally.

Task захватывает concrete ServerConfigurationPacketListenerImpl и getOwner()
GameProfile + Connection, связывает с early reservation, server boot ID,
random connection ID/32-byte challenge. Проверяет mode/ready и channel support.
Отправляет challenge, ожидает proof; HTTP worker bounded pool, не server tick
thread. Результат возвращается на server executor (server.execute / context
enqueueWork) и проверяет generation/current state/connection/deadline.
Успех -> publish verified identity в registry -> finishCurrentTask(AUTH_TYPE).
Клиент НЕ посылает «auth complete» как разрешающий ACK. Не принимать success
boolean из client payload. Проверенный backend response parser validates IDs.

Точка auth: до JoinWorldTask и до getPlayerForLogin/placeNewPlayer. Поэтому
непроверенный игрок не получает player inventory, movement, CCEC actions,
city data, gameplay permissions. Registry/config sync до task возможны:
не обещаем скрыть публичные registry/modpack metadata. Login event — лишь
consistency assertion/transfer registry в PLAY; если verified entry нет в
REQUIRED, disconnect как defense-in-depth, а не основной gate.

Deadline: auth task 30 seconds с момента challenge, HTTP total 5 seconds,
connect 2 seconds; max pending admissions например 128, per-peer limits и
bounded payloads. Early reservation total cap 120 seconds до PLAY, включая
mod sync; при истечении закрыть connection прежде чем release UUID. Нельзя
просто освободить reservation оставив поздний callback способным впустить.
Default NeoForge task может ждать бесконечно — own timeout обязателен.
Disconnect/failed registry negotiation до PLAY очищается по connection state
на bounded server tick sweep; PlayerLoggedOutEvent один не покрывает этот путь.
Logout cleanup только compare-remove того же Connection/generation, respawn
не освобождает запись, shutdown очистка всего server registry.

Дополнительный server-only Mixin assertion в
ServerConfigurationPacketListenerImpl.handleConfigurationFinished: перед
переходом к PLAY REQUIRED требует matching VERIFIED registry entry. Его цель
закрыть неожиданный bypass/reconfiguration path; no backend HTTP и no UUID
rewrite. Valid finish task semantics остаются vanilla. Mixin injections
required=1, exact signatures, tests на обновление NeoForge; если target не
найден, mod startup fail, не degraded unauthenticated operation.

Недопустимо использовать server.transfer/reconfiguration без отдельного
дизайна: returnToWorld path также требует ранее установленный verified context.
Singleplayer/in-memory connections не получают production exception в REQUIRED:
для dev явно DISABLED. При DISABLED модовые gameplay registry requirements
сохраняются: «обычный join» не означает vanilla client способен играть в CCEC
modpack без нужных mods. Auth не добавляет новой обязательной зависимости.

## 7. Modes, configuration и service credential

Новая AuthenticationServerConfig зарегистрирована как ModConfig.Type.SERVER
в constructor CCEC; spec не содержит secret. Server settings proposal:

| Setting | Default / rule |
| --- | --- |
| authentication.mode | REQUIRED как fail-safe для нового config; друзья явно ставят DISABLED, без rebuild. |
| authentication.backendBaseUrl | Empty; REQUIRED требует HTTPS remote либо explicit loopback HTTP dev integration. |
| authentication.serverId | Empty; REQUIRED требует stable configured ID matching backend service identity. |
| authentication.authTimeoutSeconds | 30, bounded 10–60. |
| authentication.httpTimeoutSeconds | 5, меньше auth timeout. |
| authentication.maxPendingConnections | 128, bounded; no unbounded futures/maps. |
| authentication.transport | TLS_GATEWAY в REQUIRED; raw Minecraft listener loopback; DISABLED обычный direct TCP. |
| service credential | Только env CCEC_BACKEND_SECRET, no dev default, independently random >=32 bytes; optional nonsecret key ID для rotation. |

Backend получает matching game-service credential из отдельной env config,
сопоставленной serverId; например GAME_SERVER_ID + CCEC_BACKEND_SECRET.
HTTP header X-Game-Service-Secret, отдельный game-server route policy.
Сравнение constant-time; secret не token игрока. Bot secret не принимается
на game routes; game secret не принимается на bot routes или public launcher
operations. Rotation coordinated: revoke old key, retries/maintenance; minimal
single key достаточен, сложная PKI не нужна. Remote TLS обязателен.
Env задаётся service supervisor на сервере, не launcher/client, не Git/TOML.
SERVER configs синхронизируются NeoForge client, поэтому secrets там запрещены.

Mode immutable на lifetime server: config reload требует restart и не
переключает live connections. Startup logging: selected mode/server ID,
endpoint host, ready state; redact credentials. Явный громкий warning DISABLED.
Backend может быть установлен локально Windows/PostgreSQL; Docker не требуется.

REQUIRED invalid static config (mode, empty URL/ID/secret, insecure remote URL,
wrong offline/transport binding) -> abort server startup. Valid config, но
backend unavailable/401 -> server может стартовать в NOT_READY и deny ALL new
joins; восстановление через authenticated backend readiness/boot registration.
Неверный service credential никогда не превращается в DISABLED. Existing
verified gameplay не останавливается при позже возникшем outage. Readiness
возвращается только после authenticated successful check, не просто /api/health
200. Front-door TLS/firewall deployment verification обязательна отдельно:
TOML setting сам по себе не доказывает отсутствие bypass raw port.

DISABLED не валидирует URL/secret, не регистрирует remote boot, не создаёт HTTP
workers/retry loop и не обращается к backend/PG/bot. Local ordinary offline
join and UUID systems работают как до protocol. Independent deployment profiles:
dev world + DISABLED; production world + REQUIRED. Production marker запрещает
accidental mode downgrade при рестарте.

## 8. Reconnect, duplicates, restarts и outage

Duplicate policy: **deny NEW, preserve OLD**. Single-server local registry
резервирует UUID до vanilla eviction, затем сохраняет ownership до фактического
закрытия old connection. Это ловит два различных tickets одного platform user
также: UNIQUE one identity -> один game UUID. Backend single-use consume и
CCEC reservation решают разные races, оба обязательны. Другой backend instance
не нарушает consume: PostgreSQL row locks, не in-memory consumed map.
Backend не управляет gameplay online lease и не снимает его по timeout outage.
Multi-server admission требует нового ADR/fencing, не включён в guarantee.

Потеря Internet, reconnect через 20 секунд: новый TCP tuple и ticket;
consumed proof непригоден. Launcher active/session valid -> автоматическое
replacement issuance. Old connection ещё считается alive -> ACCOUNT_ALREADY_ONLINE,
не kick old; клиент ждёт bounded network disconnect timeout/retries с backoff,
не flood. Launcher closed/expired -> просить login. Никакого долгого game token
или reconnect grace secret. Ticket не хранит reconnect authority.

Minecraft restart: online players disconnect, registry пуст, новый boot ID,
service-authenticated register invalidates old boot tickets; новые joins лишь
после readiness. Backend down -> no new joins. Backend restart: PostgreSQL
states и current boot сохраняются, consumed tickets не воскресают; server-side
verified PLAY registry остаётся и игра продолжается. Launcher restart: памяти
capability нет, login заново; existing game unaffected. Minecraft client
restart: новый connection, новый ticket. Launcher close after join: PLAY жив,
нет issue capability для reconnect; TLS helper не убивается launcher close.

Backend outage на consume: denial AUTH_SERVICE_UNAVAILABLE, no cached permit.
HTTP timeout после возможного commit: idempotent retry только SAME live tuple
до deadline; если неизвестно/late — disconnect и next attempt новый ticket.
Backend down после join: город, экономика, логистика продолжаются локально.
Backend recovered: readiness check, новые tickets/consume опять доступны;
не reconsume tickets старых PLAY и не revoke их автоматически.
Bot down: ACTIVE verified accounts могут получать launcher/game sessions;
новая Telegram activation временно невозможна, не skip verification.

User-facing codes (localized RU/EN, no sensitive detail):
AUTH_REQUIRED / «Нужен авторизованный игровой профиль»;
AUTH_EXPIRED / «Разрешение истекло, повторите подключение»;
AUTH_DENIED / «Вход не разрешён»;
AUTH_SERVICE_UNAVAILABLE / «Сервис входа временно недоступен»;
ACCOUNT_ALREADY_ONLINE / «Предыдущее подключение ещё активно»;
AUTH_PROTOCOL_MISMATCH / «Обновите игровую сборку».
Operational logs отдельно различают wrong credential/config/timeouts без secrets.

## 9. Security cases — обязательная матрица

| # | Сценарий | Ожидаемое безопасное поведение |
| --- | --- | --- |
| 1 | Знание чужого Minecraft name/alias | Без чужой valid ticket admission denied; ранний duplicate guard не даёт выбить уже вошедшего владельца. Alias не secret. |
| 2 | Неофициальный launcher | Без account capability/ticket deny. Alternative launcher, законно authenticating свой verified account и implementing protocol, не отличим и может войти своим account. |
| 3 | Modified launcher | Не может подменить actor: issuer берёт user из capability, consume проверяет server profile binding. Может украсть введённый пароль на контролируемой машине; endpoint не защищает от локального malware. |
| 4 | Modified CCEC client | Client success/UUID/username не authority. Missing proof, fabricated tuple, PLAY-before-auth/FinishConfiguration/oversized payload -> reject; server state authoritative. |
| 5 | Copied proof | TLS и IPC снижают перехват. На другом tuple отказ. Raw secret с current tuple, stolen before consume и injected в тот же controlled connection — first valid consume может выиграть; local compromise не устраняется. Не заявлять полной защиты stolen bearer. |
| 6 | Old/reused session | expiry/consumed/revoked/old boot deny. Cached login UUID и polling token не допускаются как game proof. |
| 7 | Одновременный consume одной session | Row lock, один transition. Different connection всегда deny; same live tuple retries return same result, не второй player. |
| 8 | Два подключения одного user, разные tickets | Early per-UUID reservation, NEW denied, OLD остаётся. Pending reservation bounded, timeout closes pending before release. |
| 9 | Backend unavailable | REQUIRED new joins fail closed; verified PLAY continues; DISABLED entirely independent. |
| 10 | Bot unavailable | Existing verified accounts continue login/game issuance; pending registration не активируется без trusted confirmation. |
| 11 | Minecraft restart | New boot, no persisted online permit; old tickets invalid, new ticket required; readiness gate. |
| 12 | Backend restart | PostgreSQL consumption persists; no token resurrection; existing game registry unaffected. |
| 13 | Launcher close | Current Minecraft/TLS helper connection continues; new issuance/reconnect requires reopened launcher/login. |
| 14 | Create username rename | Future rename changes credentials label, not user UUID, join alias or property ownership. Current rename endpoint отсутствует. |
| 15 | Telegram username rename | Numeric telegram_user_id остаётся; current_username metadata, admission unaffected. |
| 16 | Minecraft display rename | current_name formatting changes; immutable join_name/UUID не меняется; SavedData keys прежние. |
| 17 | DISABLED | Ordinary offline local identity; no Backend/Telegram/PG/launcher requirement; no verified platform claims. Production world marker refuses unsafe downgrade. |
| 18 | REQUIRED unconfigured | Static validation abort startup. Не silent fallback; user must explicitly select DISABLED for a dev world. |
| 19 | REQUIRED wrong service credential | Auth readiness fails, joins denied; actionable server log; existing verified PLAY unaffected by later credential failure. Bot credential не заменяет game credential. |

Дополнительно: no secrets in URLs/logs/exception toString/SQL failing rows,
no token telemetry; bounds всех payload fields/requests; no arbitrary backend
URL из client; private raw Minecraft port, validated TLS certificates; rate
limits per account/service/peer, public login generic errors. TLS gateway
loopback hides remote IP от game server, поэтому per-peer rate limit там
дополняется gateway limits, не trusted X-Forwarded-For. Local hostile clients
могут занимать pending alias reservation до timeout (bounded DoS), не получить
identity; gateway/global caps и timeout нужны, полной DDoS гарантии нет.
Compromised trusted server/service secret может выдавать false server context:
это breach infrastructure boundary, не свойство client protocol.

## 10. Milestone 4B — concrete implementation plan

### 4B-0: два release-blocking spikes

Сначала маленькая runnable dedicated-server fixture на exact MC/NeoForge:
optional config payload + task, missing channel deny, malicious early finish,
GET owner profile, async timeout/disconnect; early Mixin duplicate guard с
двумя clients и сохранением OLD, reservation cleanup до PLAY. Compile/runtime
gate нельзя заменить unit mocks. Второй spike: client stdout-request/stdin
response, slow mod startup, TLS helper/gateway, launcher close, reconnect;
verify no secrets в logs/argv/env и no direct public Minecraft port.
Если lifecycle signature/order иной — обновить proposal прежде реализации,
не переносить auth в LoggedInEvent. До spikes ADR remains proposed.

### Backend

После подтверждения spikes новая V4 (НЕ создаётся в 4A):

- nullable UNIQUE immutable minecraft_identity.join_name для совместимости
  existing rows; CHECK ASCII length/lowercase pattern; provisioning service
  enforces exact offline UUID; existing IDs не менять;
- launcher_session, game_session и singleton game_server_boot mapping;
  FKs без unexpected cascade, UTC timestamps/check state/expiry/consumption,
  unique secret hashes и indices expiry/user/server;
- JPA LauncherSession/GameSession/GameServerBoot, repositories с lock methods;
  GameIdentityProvisioningService, LauncherSessionService, GameAdmissionService;
  existing MinecraftIdentity расширить отдельным alias, сохранить rename semantics;
- новые controllers/DTO с redacted toString, bounded validation, no-store;
  proposal routes POST /api/launcher/sessions (username/password),
  DELETE /api/launcher/sessions/current, GET /api/launcher/game-profile,
  POST /api/game-sessions (launcher bearer),
  POST /api/internal/game/boot, POST /api/internal/game/sessions/consume
  (game service only). Никаких actor IDs из public body как authority;
- AccountService reuse/refactor credential validator без увеличения разрешений
  /api/auth/login; AccountGuard split policies bot/game/launcher, not prefix-wide
  BOT secret; Accounts registration/confirmation contracts остаются прежними;
- отдельные GameServerSettings env secrets, Clock TTL, bounded rate limits,
  strict service-server binding, generic errors; public actor получает только
  собственный profile. Не открывать foundation read API launcher через bot key;
- shared H2/PostgreSQL contract suite, настоящие concurrent transactions,
  V1–V3->V4 upgrade/legacy not provisioned and checksum tests; gradlew test build
  плюс postgresTest на локальном Windows PG. Redis/Docker не добавляются.

### CCEC common/server

Существующие классы/файлы, которые потребуется изменить:

1. CreateCityExpansionCore.java: constructor registers SERVER config,
   configuration task listener, explicit lifecycle/tick/logout listeners;
   registerPayloads подключает отдельный auth registrar. Existing
   onServerStarting @SubscribeEvent сам по себе не доказательство регистрации
   instance: нужны явные listeners, не предположение о firing.
2. CreateCityExpansionCoreClient.java: initializes dedicated config auth handler
   и IPC worker; не использовать существующий Object/reflection PLAY delegate.
3. src/main/resources/createcityexpansioncore.mixins.json: required server-only
   ServerLoginAdmissionMixin и ConfigurationAdmissionAssertionMixin.
4. ru_ru/en_us localization: messages auth/timeout/unavailable/duplicate/protocol.

Config.java НЕ нужно переписывать: добавить новую AuthenticationServerConfig.
CityManager/City/CityMember/CityInvite/CityMapSavedData, EconomyManager,
RailwayManager, MineControllerBlockEntity/ItemData и gameplay handlers остаются
без UUID/storage изменений; перечислены как affected invariants для regression
tests, а не список автоматических edits. Только display UI seam может later
потребовать отдельный impact-reviewed change, не auth checks в gameplay.

Новые proposed classes в com.example.examplemod.auth:
AuthenticationService, DisabledAuthenticationService,
PlatformAuthenticationService, ConnectionAdmissionRegistry,
PlayerIdentity (verified/local variants), AuthenticationConfigurationTask,
GameBackendClient, AuthenticationLifecycle, AuthenticationServerConfig,
ProductionWorldAuthMarker. New mixins в существующем mixin package.
HTTP client server-only: nonblocking tick, bounded executor, HTTPS validation,
service header redaction, deadline/cancellation, no client class loading on
dedicated server. World marker local metadata не хранит credential/accounts.

### CCEC client / Networking

Новые LauncherProofBridge и ConfigurationAuthClient в client-only package;
stdin worker, public stdout request, correlation/deadlines, erase secret refs,
no secret logger; safe absence in DISABLED. Config network messages:

- GameAuthChallengePayload S2C: protocol=1, serverId, bootId, connectionId,
  nonce32, deadline (server clock advisory; authoritative expiry backend);
- GameAuthProofPayload C2S: correlation tuple, gameSessionId, secret32;
  никакого password/launcher bearer/service secret;
- optional nonsecret GameAuthResultPayload S2C для UX. Не является permission,
  client ACK не завершает task. Disconnect reason достаточен для errors.

Bounded StreamCodec<FriendlyByteBuf,...>, UUID codec, exact 32-byte nonce/secret
или строго 43-character base64url, enum/protocol validation, unexpected state
denial. No giant generic JSON tunnel и no proof в PLAY.

### Launcher / infra / config

Не UI/updater milestone: минимальный login+process launch+IPC coordinator;
keeps 10min capability in memory, profiles own immutable alias, HTTPS issuance
just-in-time, replacement on reconnect, no stored password/refresh token;
stdin PIPE/filtered stdout protocol, known server endpoints, lifecycle cleanup.
Independent standard TLS helper with validated gateway certificate и MC-owned
lifetime; gateway loopback forwarding и firewall/default private port.
Separate dev/production world configurations и supervisor singleton guard.
TLS tool/runtime packaging и service secret injection documented operationally.

### Acceptance tests 4B

- Unit: clock boundaries equality expiry, token parsing/hashing/redaction,
  state machine, tuple mismatch, deadline/generation/cancellation, duplicate
  reservation ownership and compare-remove; DISABLED constructs no HTTP client.
- DB/HTTP H2 + postgresTest: unique identity/alias/hash/FKs, concurrent consume,
  lost-response same-tuple retry, replay different tuple, ACTIVE/Telegram changes,
  legacy rows, boot restart, instance restart persistence; bot/game/launcher
  credentials cross-use all denied. Existing registration tests unchanged.
- NeoForge dedicated-server integration/manual: no-channel/ordinary launcher
  REQUIRED denied before player creation; forged UUID/success/early FINISH denied;
  disconnected/timed-out callback cannot join; two same alias connections cannot
  kick OLD; parallel tickets only one player; banned/whitelisted checks preserved.
- Real clients: TLS failure/hostname mismatch/raw port blocked, sniffed traffic
  no plaintext proof, long mod startup, stdin EOF, launcher close, reconnect20s,
  old half-open connection, MC/backend restart, wrong service credential.
- Gameplay regression both modes: city ownership/membership/invites, wallet/
  trading, personal railway, mine creator and player inventory; display rename
  preserves UUID and saved properties after world restart; respawn/dimension.
- DISABLED with Backend, PG, bot and launcher stopped: dedicated server starts,
  friends using normal modpack connect/play. REQUIRE missing settings abort;
  valid settings + outage starts NOT_READY but nobody unverified joins; verified
  players continue existing gameplay through outage and recovery.
- Production-world marker refuses DISABLED boot. User dev world unchanged;
  no auto import ownership. All 19 security matrix cases mapped to test evidence.

## 11. Фактически проверено в 4A и открытые вопросы

Проверены существующие source/config/migrations/documents и exact dependency
APIs/source ordering. Это source research, не работающий protocol. 4A не
запускает Gradle suites как доказательство ещё отсутствующего admission code,
не подключает друзей, не запускает production TLS/MC integration. Числа tests
milestone-3-verification — исторический отчёт, не новый запуск 4A.
Production code, V1–V3, Telegram flow, launcher, CCEC networking/SavedData не
изменяются. Создаются только этот design, proposed ADR и roadmap ссылки.

Действительные open questions / release gates:

1. Подтверждение Mixin injection и optional CONFIGURATION ordering на живом
   dedicated server с текущим полным modpack; исходники подтверждают точки,
   но не отсутствие конфликтов с другими Mixins. Это spike, не неизвестный API.
2. Выбор конкретного TLS helper packaging/certificate/deployment hostname и
   supervisor lifecycle на целевой Windows/VDS инфраструктуре; topology выбран,
   deployment ещё отсутствует. Публичный REQUIRED без проверки закрытого raw
   порта выпускать нельзя.
3. Product acceptance technical immutable GameProfile alias в сторонних UI
   вместо mutable красивого имени. Если нужен настоящий mutable GameProfile
   name при arbitrary stable UUID, потребуется иной, более инвазивный ранний
   profile rewrite/proxy design и отдельное решение до 4B, не поздняя UUID swap.
4. Нужен ли перенос конкретного dev world/legacy Minecraft identities? Текущие
   данные не доказывают право на старые offline UUID; auto migration исключена.

TTL/duplicate policy/outage/modes/service separation/reconnect — предложенные
решения, не оставлены неопределёнными. Multi-server, immediate online account
revocation, recovery/refresh credentials, updater и platform gameplay bridge
остаются вне данного milestone.
