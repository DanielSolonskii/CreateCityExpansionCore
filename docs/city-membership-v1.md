# City membership actions — 1.0.2.4

## Opening and behavior

- **Remote exit:** Community → My city → Leave city. A confirmation describes the loss of city role, membership-based land access and alliance chat. Personal CR, friends, profile, direct messages and project records/reserves remain intact. Active project assignments are not cancelled or refunded; coordinate them with city management.
- **Removal:** open the city's pylon, choose a member (or its management gear), then Remove from city. A row selection does not remove anyone. The explicit action has a confirmation showing player and city. Offline members can be removed; the latest queued notice is persisted and delivered on login.
- **Ownership transfer:** at the pylon, the owner selects a current member and confirms Transfer city ownership. The target becomes OWNER, the previous owner becomes ADMIN. The former owner can then leave remotely. Treasury/project ownership remains with the existing city UUID. Transfer does not delete the city or move money.
- The owner cannot leave directly or be kicked. An owner can remove other members including administrators. Administrators cannot remove the owner, themselves, or other administrators. Other roles cannot remove members.

## Server and compatibility

`CityMembershipService` is the common server path for community actions and the existing `RemoveCityMemberPayload`. Remote exit derives the target from the authenticated sender and checks the explicitly expected city. Removal/transfer require the canonical city, an open matching CityPylonMenu, matching dimension, distance at most eight blocks and an existing pylon block entity. All permissions are re-evaluated from live CityManager/City data.

`City.transferOwnership` updates the existing owner UUID and both existing member roles. The existing CityManager SavedData format already persists these fields; no parallel roles or ownership table is introduced. Old saves require no migration.

`CityMembershipEvents` adds `ccec_membership_events.dat`: up to 1,024 successful actor/request/city/target/action receipts and the latest offline notice per player (up to 4,096 queued recipients). Retries in the receipt window do not remove a rejoined player or transfer ownership twice. It is not a Telegram outbox. Audit entries reuse CommunityData.

City role and membership checks immediately reflect the change. Existing containers are closed for removed players; pylon viewers receive refreshed member snapshots. Ownership transfer closes city pylon views because their owner flag was captured when the menu was opened. The affected player's nearby boundary presentation is refreshed. Profile city/role are already derived live when requested; alliance access/representative fallback are already derived from live membership. Existing client snapshots refresh through their usual polling.

Projects and economy records are untouched. A departed CITY project author/executor may no longer have city-scoped project permissions; the confirmation explicitly warns that active assignments need coordination. Historical contribution is retained.

## Files

- `city/CityMembershipService.java`, `city/CityMembershipEvents.java`: server authority, synchronization, bounded receipts and notices.
- `city/City.java`: guarded ownership transfer using existing owner/member fields.
- `CreateCityExpansionCore.java`: legacy removal handler delegates to the common service.
- `community/CommunityService.java`, `network/CommunityRequestPayload.java`: LEAVE/KICK/TRANSFER and authoritative action availability. Existing enum values are unchanged; new actions are appended.
- `client/screen/CommunityScreen.java`, `CommunityLayout.java`: My city entry, explicit actions and shared confirmation geometry.
- `client/screen/CityPylonScreen.java`: member selection opens the member workspace; existing management gear remains available.
- RU/EN resources, `CityMembershipCheck`, Gradle check task, mod version.

Matching client/server 1.0.2.4 is required for the new actions. This task does not deploy, restart, push or commit.

## Validation and runtime checklist

`checkCityMembership` covers the owner/admin/other-role matrix, owner protection, immediate membership-based territory revocation, transfer and persistence through the real CityManager codec, old/new owner lookups, duplicate removal, actor-bound receipts and offline notice persistence, alliance representative fallback, payload round trips and confirmation geometry at small GUI sizes. It is included in `check`.

Manual test on a disposable server:

1. A resident leaves through Community while far from the pylon. Verify role/city removal, land access, alliance chat access and personal CR preservation. Reconnect/restart and verify the exit persists.
2. Owner attempts to leave: button is disabled with a transfer hint. Transfer to a current member at the pylon; verify one OWNER, old owner ADMIN, treasury/project records intact, then let the old owner leave. Restart and verify ownership.
3. Owner removes an administrator; administrator removes an ordinary resident. Administrator cannot remove another administrator or owner. Test all existing nonadmin roles.
4. Remove an offline player, restart, then log in as that player: notice arrives once. Existing profile, friends and direct messages remain.
5. Try using removal/transfer after closing the pylon, moving away, changing dimension or losing authority: server refuses it. A forged leave target must not remove another player.
6. Confirm a removed player's already open container is closed; remaining pylon viewers see a refreshed roster. Check an alliance representative's departure restores the city's owner as fallback.
7. RU/EN, small window, GUI Scale 2/3: player selection, action buttons, scrolling, confirmation, cancel/Escape and repeated clicks.

Headless/build results do not establish in-game visual or multiplayer acceptance.
