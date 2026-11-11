# City boundary presentation — 2026-10-18

Existing claims remain authoritative. No new ownership, economy or role system.

- Cached external outline only, in city colour: sparse terrain-following dashes, true corner frames and 2.4-block corner masts, moving accents and restrained dust particles.
- Automatic subtle reveal within 12 blocks of a border; a short emphasis and access hint when crossing. G toggles the wider nearby contour (72-block range). No custom settings screen or properties file; vanilla particle settings reduce/disable the particle emission. F1 hides the overlay and stops new particles.
- Cached geometry reused across snapshots when claims are unchanged. At most 320 nearby ground marks, heights refreshed on client ticks only after loaded-chunk checks. Neighbouring city marks at the same position are deduplicated. No terrain queries or outline rebuilding per rendered frame; no distant chunk loads.
- A server-selected read-only neighbourhood snapshot every five seconds supplies city identity/colour/claims and effective build access. Four-second server rate gate, 32-city and global 4096-plot budgets. The physical dimension is retained. Very large legacy cities exceeding the complete-shape budget are omitted rather than showing truncated false borders. Clients discard stale snapshots after 15 seconds and clear presentation on world change/logout.
- New request/reply packets require matching client/server mod updates. Does not change BossBars or city access decisions. Hint permission can lag the authoritative action check by one refresh interval.

Validation: compileJava and jar --offline --no-configuration-cache succeeded; 84 geometry/packet checks passed (joined plots, genuine corners, negative coordinates, distance limits, codec aggregate and size budget). Existing 64 city relation checks passed during this pass. Actual Minecraft visual quality, shader interactions, particle appearance and FPS have not been runtime-verified.

Runtime checklist: own/foreign/friendly city entry/exit; G on/off; hill/water/buildings; shared city border; vanilla particles minimal/decreased; F1; teleport/dimension/reconnect; changing claims or granting/revoking friendly access. Surface markers follow WORLD_SURFACE, including roofs/tree canopies; no underground through-wall rendering is introduced.

Artifact: build/libs/createcityexpansioncore-1.0.2.1.jar, also includes the preceding explicit city-access switch and 25-plot limit. Not deployed to production.
