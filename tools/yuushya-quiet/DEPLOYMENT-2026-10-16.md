# Yuushya disclaimer suppression

- Cause: Yuushya 2.3.0 registers an unconditional server PLAYER_JOIN handler,
  `com.yuushya.Yuushya.lambda$init$4(ServerPlayer)`, which sends its declaration.
  The existing client `check=false` setting controls a separate client screen.
- Fix: standalone `recreated_yuushya_quiet` 1.0.0 Mixin cancels only that handler.
  No original Yuushya artifact, CCEC, world data or unrelated config is changed.
- Artifact SHA-256:
  `f56f89ecea9619af16cda6a7c4affd1b84995cfa37e6a88579d207e6ec450d3c`.
- Original Yuushya SHA-256, identical in curated client and VDS:
  `c410c51e1ecdd9d3ff55eb34b84d71da761a8990ec0993a766c9ba40e8c360e8`.
- An initial packaging error (mod entry class inside Mixin package) was found
  at runtime. It was corrected by separating packages. The failed deployment
  was explicitly rolled back without restoring the world.
- Failed-attempt backup:
  `/opt/create-server/backups/server-deploy-814cb6649237448d8cb121b49ba76326`.
- Successful deployment backup:
  `/opt/create-server/backups/server-deploy-78bdded53ad5455e8836c0bd1244da37`.
- Corrected callback cancellation test passed. Dedicated server ACTIVE, reached
  `Done (4.463s)` at 23:49:11 server log time. No injection/class-load errors in
  that startup. Real-player reconnect/chat acceptance remains manual.
- Client candidate: alpha-0.1.20, sequence 21; only compat JAR and launch.json
  differ from alpha-0.1.19. Published successfully through the existing signed
  workflow. Live signature, version, launch.json and compat blob HTTPS SHA-256
  verified; client publication backup:
  `/opt/create-server/backups/client-publish-a605abb7728d40f38e285102e039ec41`.
