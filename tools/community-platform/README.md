# Community relay integration sources

**Archive / legacy integration packet. Reviewed 2026-11-02.** Current Backend/Bot
sources in this repository are [platform/backend](../../platform/backend/README.md)
and [platform/telegram-bot](../../platform/telegram-bot/README.md). Start with
[platform/README.md](../../platform/README.md) and the [current audit](../../docs/audit-2026-11-02/README.md).
The sibling CreateServerPlatform checkout is not an automatic source of truth
for this repository. These loose review files do not form a standalone build.

Mappings:

- `CommunityRelayController.java` → Backend
  `src/main/java/dev/createserver/backend/community/CommunityRelayController.java`
- `V12__community_relay.sql` → Backend `src/main/resources/db/migration/`
- `CommunityRelayTest.java` → Backend
  `src/test/java/dev/createserver/backend/community/CommunityRelayTest.java`
- `CommunityBot.java` → Bot `src/main/java/dev/createserver/bot/CommunityBot.java`

Additional integration edits in the authoritative platform:

- `miniapp/CompanionService.java`: allow appended ENGINEER/TREASURER roles in
  existing public city snapshots; private conversations are not projected.
- `BotHandler.java`: handle owner-bound Community callbacks/reply prompts after
  existing private-chat/sender/forward validation.
- `BotMain.java`: call bounded Community delivery from the existing delivery loop.

`deploy-community.py` is an explicit deployment helper. It does not run on game
startup. It requires a protected remote staging directory, exact artifact hashes
and a verified backup before installation. It never changes WireGuard, Caddy,
authentication settings or the existing Companion bridge JAR.

The helper targets a historical Linux `/opt/create-server` deployment and assumes
the Community v1 transition to Flyway schema **12**. It stops Backend/Bot during
installation and is not compatible by assumption with later migration histories.
Preserve it as release evidence; use the current platform release instructions
for a new deployment. It was not executed during the 2026-11-02 audit.

All four mapped source/migration files were byte-identical to their `platform/`
counterparts on 2026-11-02. This equality does not make the deployment helper current.
Build and test the actual projects from the repository root:

```powershell
.\platform\backend\gradlew.bat -p platform/backend test bootJar
.\platform\telegram-bot\gradlew.bat -p platform/telegram-bot test installDist
```

Requires Java 21 and the project dependencies described in the linked READMEs.

See [docs/community-v1.md](../../docs/community-v1.md) for privacy defaults, migration
behavior and the manual acceptance checklist. Do not copy these review snapshots
over future platform changes blindly.
