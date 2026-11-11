# Unified Community v1 — 1.0.2.5

## Player experience

The existing Community entry/key opens one actual Minecraft screen, `SocialHubScreen`, with a persistent graphite/brass sidebar. Seven sections: Home, Messages, Friends, My city, Alliance, Notifications and My profile. Search and Settings are shared header/sidebar actions. WorldMap remains a separate full-screen screen.

Existing social, city, alliance and profile pages are mounted inside the shell. They keep their own widgets, server protocols, permission checks and editing forms. Navigation preserves the page object, unsent fields, selection and scroll; history is bounded. Hidden-page responses are routed by request UUID instead of being discarded merely because another section is active. Inactive pages do not tick or acknowledge messages. Resize recalculates the active page; other pages recalculate lazily when reopened.

Player inspection opens a side card with the actual available skin, nickname, online status, city role, alliance, short bio and explicit actions. The same card is available from friends, search, city participants and the conversation's profile action. It offers full profile, friendship request/accept, messaging for friends, city invitation and a member-management entry when appropriate. Final authority stays server-side; the card does not grant permissions.

Messages lists personal conversations and a labelled alliance-chat entry. Personal drafts are retained when changing conversations. Existing history/read rules are reused. Scrolled history is not automatically pulled to the newest message; an explicit jump button is shown. Overlaying a player card does not mark the covered conversation as read. Opening alliance chat acknowledges its unread marker only while the newest page is actually visible at its end.

Home shows actual unread messages, friendship requests, city invitations, actionable city projects, alliance invitations and outstanding council votes. A cityless player gets clear routes to cities, invitations and founding help. Ownership/role changes and city management still use the existing pylon restrictions and confirmations.

My city also contains a paged Relations and land access view. Members can inspect their city's existing trust/partner/incoming-access status remotely. Explicit set-trust and set-land-access actions reuse CityManager's existing mechanisms and require the actual owner/admin role, the matching open pylon, its dimension, nearby distance and pylon block entity. Territory access has a confirmation. The pylon and WorldMap relations entries route into this same workspace. No alliance automatically grants access.

## Notifications and privacy

New SavedData `ccec_community_inbox.dat` stores owner-scoped metadata: event UUID/type, public display label, peer/city/alliance reference, timestamp and read flag. No message bodies, balances, coordinates, auth data or duplicate social graph are stored in it. It is not connected to the Backend/Telegram outbox. Existing notification preferences/delivery outside this inbox are unchanged.

Events begin accumulating with this version; old history is not fabricated. Sources:

- Direct-message, friendship-request and accepted-friendship events from successful SocialService operations.
- City invitation and its acceptance/decline from CommunityService.
- Removal/ownership events from the existing common CityMembershipService.
- Approved alliance invitations, new/closed council votes and new alliance messages from AllianceService.

Each owner retains the latest 80 events, shown in two pages of 40. Unread message events coalesce by peer; unread alliance-message events coalesce by alliance. Duplicate source nonces do not create duplicate retained notices. Reading a conversation clears only that owner's corresponding message marker. Reading an invitation or notification never accepts membership automatically. Mark-read requests derive the inbox owner from the authenticated connection, not a supplied UUID. Global owner storage is bounded at 4,096 recipients.

Notification rows open their destination. Historical city/alliance entries may no longer be actionable; current server rules determine availability. State and read flags survive normal server save/restart.

## Motion and sound

- Soft opening/content crossfades, approximately 140–180 ms.
- Smooth sidebar selection and hover response.
- Side-card entrance with shared render/hit geometry throughout motion.
- Existing message-arrival highlights and explicit scroll-to-newest.
- Smoothed council majority progress.
- Existing quiet CommunityFx click/success/error sounds and settings.

No constant background animation, extra particle settings or new game HUD. Existing reduced-motion preference is respected.

## Main implementation files

- `client/screen/SocialHubScreen.java`, `SocialHubLayout.java`: shared shell, bounded navigation, response routing, inspection card, inbox view and map return.
- `CommunityScreen.java`, `CommunityLayout.java`: embedded home/city/settings, cityless help and council attention cards.
- `CityPylonScreen.java`: relations entry routes into the shared city workspace.
- `SocialScreen.java`, `SocialLayout.java`: embedded friends/messages, alliance conversation entry, drafts and unobstructed read handling.
- `PlayerProfileScreen.java`: embedded profile/search and shared card entry.
- `AllianceScreen.java`, `client/AllianceClient.java`: embedded alliance/council/chat, cached own summary, read markers and vote motion.
- `PolishedWorldMapScreen.java`: explicit return to the retained community session after normal map cleanup; no terrain changes.
- `community/CommunityInbox.java`: additive owner-scoped metadata history.
- `CommunityService`, `SocialService`, `AllianceService`, `CityMembershipService`: event production and live availability; existing gameplay/economic authority remains in place.
- `CommunityRequestPayload`/`CommunityStatePayload`: appended actions and bounded inbox rows. Matching updated client/server required.
- `OnboardingClient`/`ClientTelemetry`: recognize the new shell using existing completion/surface mechanisms; no new personal telemetry fields.
- RU/EN language resources; `SocialHubCheck` and Gradle check registration.

No existing economy, friendship, project, city or alliance save schema is replaced. No new treasury, role or messaging backend is introduced.

## Verification boundary

`checkSocialHub` exercises the actual inbox's owner isolation, foreign read attempts, replay, coalescing, message/group acknowledgements, save/reload, bounded retention, pagination and real shell/page/drawer geometry at several resolutions. It is included in `check`. These checks do not run Minecraft's renderer or simulate actual mouse input.

## Runtime acceptance

1. Open from the existing Community key/menu, `/ccec profile`, city pylon participant selection and alliance entry. Confirm one sidebar and one centered window.
2. Visit every section, open search and Settings, select a player, open/close the side card, use Back/Escape and the global close button. Repeat with a cityless player and an offline player.
3. Type an unsent DM, navigate to city/profile/alliance and back; switch conversations and restore the draft. Resize/change GUI Scale with an edited profile and message draft.
4. Open a player from friends, search, city and DM. Check skin, city role and alliance. Send/accept friendship requests, invite to city, use the management entry; unauthorized actions must remain unavailable/server-denied.
5. Generate each notification source with two players and two or more cities. Verify owner-only history, unread counters, pagination, reconnect and server restart. Reading a notification must not join a city/alliance or cast a vote.
6. Read a DM/group chat at the bottom, while scrolled into history and while covered by a player card. Verify only actually visible latest content is acknowledged. Check that the jump button works and group entry opens the correct alliance chat.
7. Visit the council; vote, invite a city, rename/exclude according to existing rules. Verify progress animation, resolved/closed notifications and role changes.
   In My city → Relations, test trust and territory permissions separately; changes must require the nearby open pylon and the correct role. Confirm incoming access and disabled states for residents/remote viewers.
8. Open WorldMap from Community or Alliance. Verify it is full-screen, closes normally and returns to the retained Community page. Test paths originating from a pylon and from an already open map.
9. RU/EN, GUI Scale 2/3 and small windows; verify text clipping, fields, scrolling, confirmations and animation hitboxes. Enable reduced motion and mute presentation sounds.

No production deployment, restart, commit or push is included.
