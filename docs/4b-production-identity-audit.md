# 4B production identity audit — 2026-10-04

Read-only SSH/PostgreSQL/NBT audit. Production was not changed or restarted.
Live saved files were inspected, not a stopped, transactionally consistent world backup.

## Findings

Production PostgreSQL has Flyway V1–V6, two ACTIVE accounts, and no Minecraft identity for either account:

| Account | Platform UUID | Legacy Minecraft evidence |
| --- | --- | --- |
| drannch1k | fb7ad648-0510-454a-b13a-3ee26b743862 | Local standalone latest.log uses username drannch1k and UUID 0f4fd1aa-19cd-3cf0-9bb9-894697f4bec6. No matching production playerdata or current usercache entry. |
| dzerzh | e5904314-29ca-49a9-9263-3079433d878e | Production usercache/playerdata UUID 7cb7f7bd-2aeb-38bc-9067-7ee8a5fbcd0b; saved inventory has 12 entries. |

Other production saved players:

- danckztop1gg: 0dd94c0e-bb18-37c4-a27d-255e78e54240, 11 inventory entries.
- kastrula8976: 6b7c99fb-2b08-3594-a6ed-9f3ccbe3b3e9, 37 entries.
- DZERZH: e6cbaa5d-5e62-366c-8123-c7c38d59cdc8, 23 entries. Case-sensitive offline identity differs from dzerzh; never automatically merge them.
- Unknown legacy name: 9a356b3f-0d22-4935-9d59-2e0dd7d36b6b, 0 entries. Not in current usercache; ownership must be established before mapping.

Inventory entry counts describe saved NBT, not total item quantities or current in-memory state.
Other names have no matching production platform account. Name matching is evidence, not proof of account ownership or Launcher provenance.

## Master City

Economy SavedData MasterCity is c1afa59f-5351-404e-9a4d-495ad495a6d8.
Cities SavedData identifies this city as Моржеград:

- Owner and owner-role member: 7cb7f7bd-2aeb-38bc-9067-7ee8a5fbcd0b (dzerzh).
- Other member, Role 1: 0dd94c0e-bb18-37c4-a27d-255e78e54240 (danckztop1gg).
- No drannch1k membership/ownership in the saved city record.
- No saved invites/projects in this city at inspection time.

The MasterCity pointer, city ID, city treasury and city-to-city relations are city identities, not player identities. Preserve these IDs.

## 4B consequence

GameSessionService.profile generates a random permanent cs + 14-character alias for an account without minecraft_identity. Its Minecraft UUID is Java nameUUIDFromBytes("OfflinePlayer:" + alias), not the platform UUID and not the old username UUID. Exact future aliases/UUIDs are not allocated yet; allocating them would mutate production and was not done.

V7 does not migrate world data or rewrite existing identities. Existing rows with a null join_name would fail GAME_IDENTITY_NOT_PROVISIONED instead of being silently remapped.

REQUIRED without migration would leave old playerdata on disk but load a new player record and fail existing UUID-based CCEC owner/member checks. Master City itself persists, but its owner would not automatically acquire access under the new UUID.

## Controlled migration plan (not executed)

1. Establish a reviewed one-to-one mapping: platform account -> proven legacy Minecraft UUID -> new permanent alias/UUID. Resolve drannch1k vs dzerzh explicitly, as well as DZERZH, unmatched players and the unknown UUID. Do not assign Master City to drannch1k merely because it is the operator's account. Multiple old identities require a separately approved merge policy, not overwrites.
2. Rehearse on an isolated restored world + database, no production services or public ports. Build a typed migration tool with dry-run and an immutable mapping journal; enforce uniqueness, source hashes, destination collision checks and idempotence.
3. For production, freeze identity provisioning/admission, stop Minecraft normally, and back up the complete world, PostgreSQL, artifacts and relevant configuration as one rollback set. Take final saved-data inventory after stop.
4. Provision the reviewed canonical minecraft_identity rows through a controlled internal operation compatible with V7. Preserve platform account credentials/Telegram/trusted devices. Do not edit historical Flyway migrations.
5. Transfer playerdata, stats and advancements to the new UUID; update the player's own embedded UUID and typed UUID references where required. Preserve inventory, EnderItems, XP, position, dimensions and mod attachments. Abort on an existing destination; do not blindly overwrite or merge.
6. Rewrite CCEC player-valued fields only: Cities.Owner, Members.Player, Invites.Player/Inviter, Projects.Creator and any player-valued economy/railway/mine/attachment records discovered by codec review. Check region/block-entity/entity NBT too, including pets and mod ownership. Never globally replace UUIDs: City.Id, MasterCity, city relations, contracts, trains, projects and deposits have independent identity domains.
7. Preserve Моржеград city ID and MasterCity pointer; remap its owner-role member and owner together, and its other member only after their account mapping is approved. Update ops/whitelist/bans consistently with the new aliases/UUIDs; never accidentally grant additional operator privileges.
8. Validate the clone: unchanged item data/XP/position, stats/advancements, city ownership/membership/permissions, Master City economy references, mod-owned resources, REQUIRED admission and old-identity rejection. Review a complete typed before/after diff. Graph analysis identifies dependencies but is not proof of complete third-party NBT coverage.
9. Apply the same reviewed operation offline, start and run controlled login checks. Keep admission closed until checks pass. On failure stop and restore database/world/artifacts/configuration together; a cross-system world/DB migration is not a single database transaction.

Decision: migration and verified legacy-account mapping are required before enabling production REQUIRED if existing gameplay state and ownership must be retained.
