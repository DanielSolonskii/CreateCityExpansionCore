# Yuushya quiet join compatibility mod

Targets only the unconditional `Yuushya.lambda$init$4(ServerPlayer)` disclaimer
in Yuushya 2.3.0. Original Yuushya artifacts and game content are unchanged.
This is a server-originated message: existing clients also benefit immediately
after the server restart. Matching client distribution includes this mod for
integrated servers as well.

The dependency is pinned to 2.3.0, and injection requires one match. Reinspect
the original bytecode before updating Yuushya; never silently accept a missing
injection. `client_config.json: check=false` addresses a different client screen.

Build using Java 21 and the locally cached NeoForge/Mixin dependencies:

```powershell
.\tools\yuushya-quiet\build.ps1
```

The isolated callback test under `test/` checks cancellation and static handler
shape. Actual injection compatibility is checked by dedicated server startup;
chat UX still requires reconnecting a real player.
