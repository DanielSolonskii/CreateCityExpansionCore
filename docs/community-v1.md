# Community v1

The O key opens the centered Community screen. Existing player profiles, player
search and friend-only messages remain the authoritative views, with navigation
back to Community. WorldMap remains a separate full-screen screen.

## In-game features

- Home: real unread counts, friend requests, city invitations and city projects
  awaiting funding/review. No generated activity or public message feed.
- Cities: city directory, public description, paginated members, profile/message
  actions, incoming/outgoing invitations and explicit role assignment.
- Roles: OWNER and ADMIN retain their existing authority; their display labels
  are city leader and deputy. ENGINEER can build and review city projects;
  TREASURER can approve/reject city-project funding. Legacy BUILDER retains its
  previous build rights. Existing ordinal values are preserved.
- Settings: quiet sound volume, message sounds, reduced motion, per-dialog mute;
  server-persisted optional Telegram notifications, previews, offline-only mode
  and quiet hours (22:00–08:00, UTC+3 in v1).
- New notifications do not steal keyboard focus. A conversation being read at its
  bottom does not play a new-message notification. Motion respects reduced mode.

City, membership, finance and project sources are unchanged. The additive
`ccec_community` SavedData contains preferences, bounded audit and notification
outbox. City invitations receive persisted unique tokens; legacy invitations
receive tokens during load. Old tokens cannot accept a reissued invitation.

## Telegram relay

Requires the existing REQUIRED authentication mode, configured Game Backend and
an actively linked Telegram identity. Notifications default OFF; previews default
OFF; offline-only and quiet hours default ON. The existing read-only Companion
projection does not export friends or conversation history.

The separate authenticated relay carries bounded, owner-directed notice events
and explicitly requested replies/invitation actions. Preview text is optional.
Backend derives the recipient and actor from current verified identity bindings;
the game server rechecks friendship, block state, membership, inviter authority
and invitation token. Telegram does not grant city privileges.

Backend Flyway V12 adds delivery/command queues, owner policy and event receipts.
Pending messages are grouped. Ambiguous Telegram sends become UNCERTAIN and are
not blindly sent again. Game commands expire after five minutes; relay rows are
retained at most seven days. No bot tokens or message bodies are logged.

## Runtime acceptance checklist

- Open O at GUI scales 2/3/4 and a small window; check all content and hitboxes,
  scrolling, back navigation, keyboard input and WorldMap separation.
- Open own/foreign/offline/no-city profiles; search players; send/read a friend
  message; check no duplicate chat notification while reading that conversation.
- Invite a known offline player; accept/decline/revoke from Community. Reissue an
  invitation and verify the prior token cannot act on the new one.
- Assign engineer/treasurer as leader; verify their separate project permissions.
  Deputy cannot edit/promote deputies or transfer ownership. Legacy builders
  keep their prior permissions after restart.
- Change settings/mute, reconnect and restart; check settings survive and quiet
  sounds/reduced motion behave consistently.
- With a disposable linked owner, enable Telegram notices; receive an offline
  message without preview, opt into previews, reply and check delivery in game.
  Accept/decline an invitation; test a revoked/stale invitation and unlinked
  account. Do not use another player's private messages as test fixtures.
- Check existing city treasury, contracts, projects, WorldMap and Companion
  snapshot after restart. Automated checks do not prove in-client rendering or
  real-phone Telegram acceptance.

## Release safety

Deploy Backend/Bot with database/artifact backup first, then the matching CCEC
server JAR through the existing stopped-world-backup/readiness workflow, and only
then publish the signed client channel. Do not hot-replace a running client JAR.
Additive V12 tables can remain on application rollback; never restore an old
database/world automatically over newer player activity.
