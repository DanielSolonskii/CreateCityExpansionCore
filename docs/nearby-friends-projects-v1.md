# Nearby friends and project surveying — 1.0.2.6

## Behaviour

- The minimap footer displays the local player's block X and Z (including floor-correct negative coordinates). Its shared bounds include both lines.
- Mutual online friends in the same dimension appear as actual skin heads with turquoise frames. The sender must enable **N → Share with friends** (the existing minimap settings binding can be reassigned). Sharing is off by default and persists on the server by UUID.
- Friend locations are bounded to 256 horizontal blocks, never include Y, and exclude spectators. The nearest 32 are sent; positions interpolate for one second, teleports snap, stale data expires after 2.5 seconds. Removal, blocking, opt-out, logout and dimension changes are re-evaluated every server pulse (at most one second under normal ticks). No distant edge arrows. Overlapping heads form a count badge.
- Nearby active project footprints appear directly in the world. Rectangles have dashed survey edges and corner posts; lines have a dashed axis, cross ticks and end posts; points have a segmented ring and centre post. CITY uses existing WorldMap status colours; SERVER uses the existing amber and diamond survey caps. CITY posts have square caps.
- One nearby label, when facing the zone, displays the real project title, type and status. Completed/cancelled history stays on WorldMap and does not clutter the world overlay.
- Rendering uses depth-tested lines and labels, not walls of particles or through-wall glow. Visual boundaries never grant construction rights.

## Integration and limits

`NearbySceneServer` reads `SocialSavedData.book`, canonical `CityManager` and `EconomyManager.projects()`. It reuses `ProjectNetworkHandlers.actor` and `ProjectManager.visible(actor,0)` rather than introducing another permission policy. Thus city projects remain scoped to the viewer's city and draft permissions match WorldMap; published SERVER projects are visible without city membership. Only display metadata is transmitted: no budgets, descriptions, applicants, messages or inventories.

One bounded recipient-scoped scene push per second. Friend updates are at 1 Hz; project discovery/footprints refresh every five seconds, or sooner after moving 16 blocks, respawning/reconnecting, changing dimension, city or OP/admin scope. Discovery range 80 blocks; render range 64 blocks, eight nearest projects, at most 256 segments across all projects. Client heights are cached for three seconds, capped at 2,048 entries; at most 64 height reads per refresh, four refreshes per second, using loaded chunks only. No server chunk loading, terrain generation, new terrain textures or per-frame geometry discovery.

`SocialSavedData` gets one optional `NearbySharing` UUID list. All previous fields and their values stay unchanged. Old saves without this field load with sharing disabled. The preference payload contains only a boolean; the server chooses the actor from the authenticated connection. Nothing here is exported to Backend or Telegram.

## Files

- `social/NearbySceneServer.java`, `NearbySceneNetwork.java`, `NearbyScenePolicy.java`: snapshots, registration, proximity/privacy rules.
- `social/SocialSavedData.java`: additive sharing permission.
- `network/NearbyScenePayload.java`, `NearbySharingPayload.java`: bounded scene codec and own-preference command.
- `client/NearbySceneClient.java`: session/dimension isolation, TTL and interpolation.
- `client/map/FriendMinimapLayer.java`: skin heads and clusters using the existing live map transform.
- `client/map/MinimapClient.java`, `MinimapLayout.java`: coordinates, overlay insertion and shared bounds.
- `client/screen/MinimapSettingsScreen.java`: server-backed sharing control and explanation.
- `client/ProjectWorldGeometry.java`, `ProjectWorldRenderer.java`: bounded survey geometry, terrain height cache and world presentation.
- RU/EN language files, `gradle.properties`, `build.gradle`.
- `src/mineVisualTest/java/com/example/examplemod/social/NearbySceneCheck.java`: privacy, legacy/save compatibility, codecs, real ProjectManager visibility, geometry budgets and responsive bounds.

## Verification boundary

Headless checks and compilation do not verify Minecraft render/input behaviour, visual quality or performance on the live server. No production deployment is part of this change.

`compileJava check build --offline --no-configuration-cache` passed (`build/nearby-scene-build.log`). After the final rate-limit reboot fix and reduced-motion handling, `compileJava checkNearbyScene jar --offline --no-configuration-cache` passed with 50 targeted assertions (`build/nearby-scene-final.log`).

Artifact: `build/libs/createcityexpansioncore-1.0.2.6.jar`.
SHA-256: `dd3b2477c13bae2ca7f75c0d1d7db278d9f9b8986c5ba6dfe6ecfe140c213468`.

## Runtime checklist

1. Install the matching JAR on test server and clients. Open N settings; verify sharing starts off, then opt in on a friend's account. Check actual heads, movement, overlapping friends and GUI Scale 2/3/4.
2. Check unfriend/block/opt-out, logout/rejoin, death/respawn, dimension changes, teleport beyond 256 blocks, and spectators. No stale marker should remain after the next pulse; no marker should appear for strangers.
3. Check X/Z against F3 at positive and negative fractional positions, both minimap corners and small resolutions.
4. Check CITY/SERVER point, rectangle and diagonal line footprints, including slopes, water, cliff edges and chunk unloading. Confirm normal block interaction/build rights are unchanged.
5. Verify public SERVER projects, own-city projects and author/admin draft visibility; outsiders must not receive city projects or server drafts. Accept/cancel a project and wait for the five-second refresh; its world overlay must disappear while history remains in WorldMap.
6. Restart and verify existing friends/messages and the sharing preference survive. Observe frame time with several nearby projects; compare with the overlay out of range.
