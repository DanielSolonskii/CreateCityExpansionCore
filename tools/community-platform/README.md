# Community relay integration sources

Review copies for the Community v1 release. The authoritative Backend/Bot build
projects are the existing sibling `CreateServerPlatform/backend` and
`CreateServerPlatform/telegram-bot`, not a new parallel backend.

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

See `docs/community-v1.md` in the CCEC repository for privacy defaults, migration
behavior and the manual acceptance checklist. Do not copy these review snapshots
over future platform changes blindly.
