# Yuushya quiet join compatibility mod

**Сверка состояния: 2026-11-04.** [Актуальный аудит CCEC/Cargo](../../docs/audit-2026-11-04/README.md) описывает интеграцию 1.0.2.15. Ранее датированные результаты проверок ниже сохраняют свою дату; новый игровой/production прогон ими не подтверждается.

Source/build audit: **2026-11-02**. See the [current project audit](../../docs/audit-2026-11-02/README.md).

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

Run from the repository root with `javac` and `jar` on PATH. The script expects
Gradle cache artifacts `net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7` and
`net.neoforged.fancymodloader:loader:4.0.44`; it does not download them.
Output: `tools/yuushya-quiet/build/recreated-yuushya-quiet-1.0.0.jar`, followed
by SHA-256. The build directory is ignored. Packaging timestamps can change
the digest; the historical deployment hash is not a reproducible-build promise.

The isolated callback test under `test/` checks cancellation and static handler
shape. Actual injection compatibility is checked by dedicated server startup;
chat UX still requires reconnecting a real player.

`build.ps1` does not run `test/ru/recreated/yuushyaquiet/VerifyQuiet.java`.
Compile that file with Java 21 against the built JAR and Sponge Mixin into a
separate test directory, then run `ru.recreated.yuushyaquiet.VerifyQuiet` with
those three entries on the classpath. Do not include test classes in the mod JAR.
On 2026-11-02 this callback test passed. The initial compiler run reported a
Windows cache `AccessDeniedException`; compilation and the test succeeded after
copying the same dependencies into the ignored local build directory. Two
`Dist` enum warnings remain because this small compile classpath omits the
distmarker artifact. No game/server startup was performed in this audit.

[Deployment receipt 2026-10-16](DEPLOYMENT-2026-10-16.md) records a previous
dedicated-server injection check and alpha-0.1.20 publication. It is historical
evidence; current server installation and real-player chat acceptance are separate.
