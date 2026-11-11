# City Alliances v1 — 1.0.2.3

## In game

Open **Community → City alliances**, a city card in Community or WorldMap, an alliance badge in a player profile, or `/ccec alliance`. The command only opens the screen; it grants no permissions.

The centered graphite/brass workspace contains a public directory and three tabs: Overview, Council, Alliance chat. The window scales down with GUI Scale. Forms stay in the same window. Rows select; explicit buttons submit actions. Joining, declining, leaving, dissolving and submitting council proposals have confirmation dialogs. Request timeouts provide an explicit retry with the same request UUID.

1. A city owner creates one alliance, choosing a name, short purpose, colour and one of three emblems.
2. A council representative proposes inviting an independent city. The proposing city votes yes. A majority approves the invitation; this does **not** add the city automatically.
3. The invited city owner accepts or declines. Invitations appear in the directory sidebar. A city can belong to one alliance.
4. Every member city has one representative, initially its owner. The owner may appoint a current resident. If that resident leaves the city, the owner immediately regains representation.
5. Each city gets one immutable vote per proposal. Invite, rename and exclusion require more than half of eligible cities. The exclusion target is excluded from that vote. Votes and approved invitations expire after seven real-time days. Membership changes cancel pending ballots, preventing stale electorates from deciding for the new membership.
6. Any resident of a member city can read and send alliance messages. Retention is the latest 200 messages, fetched in pages of 40. Messages remain in game; this module has no Backend/Telegram bridge, relay or outbox.
7. Only the city owner can leave voluntarily. When the last city leaves, the alliance and its chat are removed; the confirmation explicitly explains this.

Alliances never merge territory, claim intervening wilderness, modify city roles, grant land access or expose a treasury. Land access remains an explicit separate city-relations permission. Shared finance and joint projects are outside v1.

## WorldMap and profiles

Public summaries contain only alliance identity and member city identities. Map badges use the alliance colour. “Show member cities on map” fits known member cities in the **current dimension**, with focus frames on markers. It does not recolour city territory or imply a claimed corridor between cities. The alliance legend explains the badge and focus frame; clicking that legend row clears focus.

Map summaries are cached by city UUID for constant-time lookups; directory refresh is limited to once per 30 seconds while relevant UI is active, in pages of 32 alliances. Private workspace state refreshes every three seconds while open. No alliance renderer requests terrain or scans blocks.

## Implementation and compatibility

- `alliance/AllianceBook`: common server-thread aggregate, live CityManager-derived authority, majority voting, expiry, bounded chat and replay protection.
- `alliance/AllianceSavedData`: additive overworld SavedData `ccec_alliances.dat`, schema version 1. Existing city/economy/project saves are not rewritten. Missing city references are reconciled on requests; a removed representative immediately falls back to the live owner. Unsupported future schema versions are rejected instead of overwritten.
- `alliance/AllianceService`: validates the authenticated sender, derives own city from CityManager, checks every mutation, separates public summary from member-only chat/council projections. Player-profile lookup returns public data only.
- `AllianceNetwork`, `AllianceRequestPayload`, `AllianceStatePayload`: separately registered bounded v1 payloads. Client and server need the matching updated mod.
- `AllianceCommands`: `/ccec alliance` entry point.
- `client/AllianceClient`: public summary cache and connection cleanup.
- `client/screen/AllianceScreen`, `AllianceLayout`: shared geometry for fields, clipping, scrolling and hit tests; entrance easing, hover states and existing CommunityFx click/success/error sounds. RU/EN localization.
- Integration: `CommunityScreen`, `PlayerProfileScreen`/`PlayerProfileLayout`, `PolishedWorldMapScreen`, `CityMapPanel`, `AllianceMapPresentation`.

Limits: 32 cities per alliance, 256 alliances, 16 pending proposals and 32 outgoing invitations per alliance. Command rate is bounded at 8 requests/player/second; chat has an additional 700 ms cooldown. Names are case-insensitively unique. User text rejects formatting/control characters. A revision check prevents actions based on stale state; persistent bounded request IDs prevent duplicate mutations after retry/reload. No rewards, transfers or new currency are involved.

## Validation

`./gradlew checkAlliances --offline --no-configuration-cache` runs the actual aggregate, NBT and payload codecs without a Minecraft client. It covers owner/resident/outsider permissions, one alliance per city, majority/rejection/exclusion, changing membership, representative fallback, stale mutations, request replay across reload, chat bounds and privacy, expiry, deleted cities and GUI bounds/transforms.

Use Java 21. The complete offline build also includes `checkAlliances` in the existing `check` task. Build success is not Minecraft visual or multiplayer acceptance.

## Runtime acceptance checklist

- Use two or three cities and one cityless player. Open via Community and `/ccec alliance`, in RU and EN, at GUI Scale 2/3 and a small window.
- Create as owner; verify resident/cityless users cannot create. Type and edit both text fields, choose colour/emblem and resize the window while editing.
- Invite a second city. Verify it does not join before its owner accepts; test decline and a fresh invitation.
- With three member cities, exercise yes/no majority, a rejected rename, exclusion with no vote from its target, and a delegated representative. Check owner fallback after that representative leaves the city.
- Send messages as a resident. Verify another alliance/cityless player cannot read the chat, council or send messages; expelled/left members lose access on refresh.
- Check profile badges, city cards, marker badges, map focus in each dimension and clearing focus via the legend. Territory colours/claims remain the same.
- Restart a **test server** with an open vote and invitation. Verify members, representative, votes, chat and request replay are restored.
- Leave during an open vote, delete a test city, leave the last city. Verify pending ballots close and the final dissolve confirmation explains chat deletion.
- Verify allied residents still cannot build in another city until its separate land-access permission is explicitly granted. Check city treasury and existing CITY/SERVER projects are unaffected.

No production deploy, restart, push or commit is part of this implementation task.
