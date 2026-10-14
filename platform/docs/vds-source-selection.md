# VDS migration — source selection, 2026-10-03

Before transfer, the selected actual server is `E:\CreateCityExpansionCore_MERGED\CREATED`.
Minecraft 1.21.1, NeoForge 21.1.251 (installed unix_args.txt/win_args.txt).
Real player connections and a clean save-all/stop appear in latest.log; the final
shutdown saved overworld, Nether and End. The other main project is a development
checkout, not this standalone installation. No other configured standalone instance
was found under the workspace. Windows process inspection found only GradleDaemon,
not Minecraft; no Minecraft listener was found. Recheck before archiving.

Runtime selection: preserve the working installed CCEC 1.0.1 JAR, SHA256
27ace54082ed53c25e4a9e5af2c9c1c6f3074de7ab3459a91d45475fc59d9ce2.
A different, newer build/libs JAR exists; do not silently conflate migration and
gameplay upgrade. User explicitly confirmed preserving the working October 1 JAR.

Transfer world (including serverconfig, sublevels, playerdata and datapacks), mods,
config, defaultconfigs, libraries, server.properties, JVM args, accepted EULA,
ops/whitelist/bans and username caches. Exclude logs, crashes, Gradle/IDE files,
backups and the extracted Windows-only .sable native DLL. Linux native resources
must be loaded from the existing mod distribution. Preserve local source entirely.
World 181680411 bytes; mods 159167713; libraries 184333105.

Remote audit: root@186.246.28.214, hostname nsk-1-vm-elyh; actual Ubuntu 26.04.1
LTS (not requested 24.04), 3 vCPU, 5.8 GiB RAM, no swap, 19 GiB disk / 17 GiB free.
SSH active/enabled and UFW active allowing SSH only. No existing create-server
deployment; Java/PostgreSQL absent before this deployment. No Docker installation.

Local create_server contains only Flyway V1 and the technical infrastructure table,
no user/domain tables or production account data. Create a clean VDS database,
do not copy create_server_test or fixtures. Production secret generated on VDS only.

Remote-only adaptations: remove obsolete local server-ip=26.5.192.146 binding;
heap 2 GiB initial / 3 GiB maximum to leave RAM for OS, PostgreSQL/backend/native
physics. Keep offline-mode and current whitelist settings. This exposes name
impersonation including ops: no game authentication exists before Milestone 4B.
No authentication.mode option, new migrations, account redesign or gameplay edits.
