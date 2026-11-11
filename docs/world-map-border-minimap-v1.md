# World border, minimap and railway visibility — 17.10.2026

## Implemented

- WorldMap and minimap use vanilla WorldBorder. Current dimension uses vanilla client synchronization immediately; directory responses carry an additive border snapshot for other selected dimensions, with size interpolation.
- Outside: translucent red mask, diagonal hatch, fixed-width outline and RU/EN unavailable/expansion hint. Four disjoint rectangles share pure MapBorderGeometry. The overlay batches its drawing. Tile requests wholly outside are skipped.
- Project placement and new claim requests reject zones/plots outside the live server border. Existing projects, reservations and territory saves are not rewritten. Existing projects can still be viewed and completed.
- Upper-right HUD minimap reuses CityMapPanel terrain, TerrainCache, TerrainTileStore and TerrainAtlas. No second tile engine, framebuffer or terrain-generation path. North remains up; player heading rotates. Movement smoothing is time based; teleports snap.
- N opens local settings; both settings and hide/show shortcuts are remappable. Hide/show starts unbound. Options: enabled, size, zoom, brightness, top corner, cities, selected project. Settings survive restart in config/ccec-minimap.properties. Brightness dims the map; it does not make terrain transparent.
- Selected project is the last project whose details were opened, held only for this connection and cleared on logout. Cities come from paginated public directory snapshots. No private profiles, wallets or message data are requested.
- Minimap sleeps in screens, F1 and F3; releases panel textures into the shared pool. Directory lease refresh is once per five seconds; dedicated HUD response types prevent late minimap pages from resetting an opened full atlas. Background tile requests are at most two per 500ms, retaining existing timeout/retry and texture-upload budgets. Terrain cache remains scoped to server/player/dimension.

## Railway diagnosis and fix

Source inspection found a fixed 768-block radius around the city pylon in buildMapSegments, membership-only railway initialization, and a low-contrast brown baseline. These explain missing distant/non-member views but are not a live-server reproduction.

Public atlas directory refresh now sends the bounded Create graph geometry for the selected dimension without the pylon radius. Directory initialization precedes the railway snapshot so session initialization cannot clear the new map. Passenger/private operational snapshots are not made public; route data remains limited to the viewer's own city. The existing 20,000 physical / 4,096 semantic segment caps and 30-second geometry cache remain. Public railway snapshots are additionally limited to one per second per player; direct/member paths reuse the same geometry cache.

Neutral unclassified physical track has dark casing and a matching legend entry. Registered ownership/access/offline colours and widths remain exactly those from railInnerColor/railInnerWidth. Paid ticks carry distance across curve samples. Selected route emphasis remains. Lines and tariff ticks clip to the viewport before rasterization.

Limits: very large railway graphs still hit the existing bounded snapshot cap. Non-current dimensions currently show physical geometry without operational/ownership classification. Create must have the track graph loaded; this pass does not reconstruct deleted/missing graphs from world blocks.

## Verification

- Targeted compileJava, checkMapHud, checkMapPresentation, checkWorldMapUiLayout and checkCityWorldMapSession passed using Java 21, the MERGED checkout and --no-configuration-cache.
- checkMapHud covers mask coverage/no overlaps, border edge inclusion, offscreen and extreme-coordinate rail clipping, small GUI layouts, settings persistence, corrupt-value clamping, real network codec roundtrips and HUD/full-atlas response isolation.
- Final build result is recorded in the task response. No Minecraft visual/runtime acceptance or production deployment is implied by a build.

## In-game acceptance

1. Enter the Alpha server with matching server/client JARs; open map as city member and as a player without a city.
2. Verify rails within and beyond 768 blocks, other dimensions, all registered legend colours, curve tariff ticks and selected route. Check that the Tracks layer is enabled.
3. Change worldborder centre/size and run a timed shrink on a test world; compare visible line with the vanilla border, hover outside and pan across a corner.
4. Attempt a new project and claim crossing the border; verify refusal. Existing project acceptance and refunds must still work.
5. Walk, sprint, teleport, change dimension and reconnect. Check city markers, selected project, fixed north and heading.
6. Test N, remapped shortcuts, each setting, small resolution/GUI Scale, F1/F3, full WorldMap and menus; confirm settings persist.
7. Compare frame time with minimap enabled/disabled and watch terrain streaming on an unexplored route. Server chunk generation must remain governed by the existing terrain sampler.

Final verification: full `build --offline --no-configuration-cache` passed (55 tasks). After final clipping/settings/network isolation fixes, `compileJava checkMapHud checkCityIdentity checkCityWorldMapSession jar --offline --no-configuration-cache` passed. The delivered JAR contains the final sources. The final targeted check includes real codec roundtrips; no in-game acceptance or production deployment was performed.
