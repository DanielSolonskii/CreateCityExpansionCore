# ADR-0004: Game Sessions и CCEC authentication boundary

2026-10-04: пользователь явно одобрил постоянный технический alias (перенос
legacy playerdata отдельно) и замену loopback helper на стандартный Java TLS
в Minecraft/CCEC с сохранением remote address для UDP mods. Это уточнение
транспорта, не изменение account/world authority. Локальная реализация и
проверки: [4B](../game-session-4b.md). Production deployment ещё не выполнен.

- Статус: proposed / требует spikes и runtime acceptance Milestone 4B
- Дата: 2026-10-03
- Дополняет ADR-0001–0003; не отменяет world/server authority.
- Design и implementation plan: [game-session-protocol](../game-session-protocol.md).

## Контекст

Собственный Create Server Account и обязательная Telegram verification уже
реализованы. Существующий login response не credential. Offline Minecraft
username/UUID не доказывает account identity. Проверка после LoggedInEvent
слишком поздняя; CONFIGURATION возможна до player creation. Но vanilla
duplicate eviction происходит ещё раньше, поэтому требуется early guard.
Локальный DISABLED server должен работать без всей platform infrastructure.

## Предлагаемое решение

1. Отдельная короткая launcher opaque capability (10 min, no refresh token)
   только для собственного game profile и ticket issuance; password/service
   credentials в Minecraft не передаются.
2. Game ticket 120 sec, random secret hash в PostgreSQL, bound server/boot/
   connection/nonce + platform/minecraft identity. Атомарный single-use consume,
   limited same-attempt service retry; новый connection всегда новый ticket.
3. Launcher -> child stdin pipe -> CCEC CONFIGURATION payload. Issuance
   just-in-time по public challenge, чтобы время загрузки modpack не тратило TTL.
   Нет secrets в argv/env/files/logs. Client/launcher не доверены как authority.
4. Production HTTPS + standard TLS game tunnel/gateway; raw offline TCP порт
   loopback/private и закрыт снаружи. TLS helper живёт вместе с Minecraft,
   не launcher UI. Без этого bearer и gameplay на wire не защищены.
5. CONFIGURATION auth task завершается только trusted server после successful
   backend consume, до JoinWorldTask/ServerPlayer. Missing channel в REQUIRED
   означает denial, не skip. Дополнительный assertion закрывает PLAY transition.
6. Early server Mixin до vanilla duplicate eviction резервирует UUID;
   deny NEW/preserve OLD. Single production world server; distributed online
   leases/multi-server transfer не входят в первую версию.
7. platform UUID независим. Immutable generated technical join_name определяет
   persisted vanilla offline Minecraft UUID; этот UUID = ServerPlayer.getUUID()
   и gameplay/SavedData key. Mutable current_name остаётся display metadata.
   Existing legacy UUIDs не переписываются, требуют controlled reconciliation.
8. Один AuthenticationService boundary: verified platform либо LOCAL_DEV.
   City/economy/railway/mine не знают HTTP/tokens и не меняют storage keys.
9. SERVER config REQUIRED/DISABLED без rebuild. REQUIRED static misconfig
   abort startup; unreachable/unauthorized backend denies new joins, никогда
   automatic DISABLED. Verified gameplay continues outage. DISABLED не создаёт
   HTTP dependency и допускает ordinary offline local modpack connections.
10. Separate CCEC_BACKEND_SECRET service identity, не BOT_BACKEND_SECRET;
    secret env/server-only, не syncable SERVER TOML. Route policies разделены.
    Production world marker запрещает accidental запуск его в DISABLED.

## Причины и компромиссы

Сохраняется existing UUID-based world и vanilla playerdata без массового mapping
rewrite. Технический GameProfile name может быть виден в commands/других mods;
display hooks не обещают универсальный nickname override. Arbitrary stable UUID
при mutable profile name требует более инвазивного дизайна и пока отвергнут.

По одному username невозможно получить ticket чужого account. При этом
достоверно запретить alternative launcher, законно аутентифицирующий свой
verified account, нельзя: protocol не software attestation. Stolen raw bearer
до consume/локальный malware остаются реальным риском; TLS, memory-only IPC,
tuple binding и single-use его уменьшают, не делают machine trusted.

Нет backend request на каждом действии. Account revocation немедленно online
не гарантируется без отдельного admin kick/push. Single-server scope избегает
ложной гарантии distributed exclusivity при outage с продолжающейся игрой.
Два servers с одним service credential — запрещённый deployment split-brain.

## Альтернативы

Secret temp file проще, но disk residue/ACL/TOCTOU. Loopback broker гибче, но
требует дополнительной authenticated IPC boundary. Signature/PoP добавляет
key management и не решает active relay без защищённого game transport.
JVM argv bearer и PLAY-after-join gate отвергнуты. GameProfile UUID swap после
login отвергнут как несовместимый с profile cookie/playerdata/ownership.

## Acceptance gate и последствия

До принятия ADR: dedicated NeoForge 21.1.251 spikes подтверждают optional
configuration channels, required gate, early duplicate Mixin и malicious FINISH;
IPC/TLS spike подтверждает slow startup/launcher close/reconnect. Затем H2/
PostgreSQL concurrent contracts и runtime обеих modes/19 security cases.
В 4A нет production code, V4, новых endpoints или CCEC edits. Roadmap разделяет
завершённый design от ещё не реализованного 4B. Legacy world import требует
отдельного approved plan; обычное переименование labels UUID не меняет.
