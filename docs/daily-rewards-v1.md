# Daily Rewards v1 — 1.0.2.8

## Player flow

The gift in the Community header opens a compact, non-pausing seven-card window. Cards use actual ItemStacks, including real enchantment components. The wood selector offers oak, spruce, birch, jungle, acacia, dark oak, mangrove and cherry logs. Experience bottles are preview icons only: the server adds the configured number of levels to the current level.

An eligible login queues one automatic offer. The client waits for the player to enter the world, loading overlays and other screens to close, a normal player camera, and a safe moment. Closing the window suppresses that offer for this connection; the gift always remains available. The existing onboarding is contextual HUD guidance rather than an introductory cutscene. Existing GUI navigation and reduced-motion/sound preferences are reused. The gift pulse does not make network requests.

## Calendar and starter cycle

The server computes dates in the configured timezone. Consecutive calendar days advance the seven-day streak; a missed date resets the next day to 1. Already reserved/claimed starter days are remembered independently of the streak. A repeated starter day uses its regular reward, including the wood choice on day 1. Claiming all seven unique starter days completes the starter set permanently. Regular seven-day cycles then repeat. The current completed starter display remains visible on its final claim date; the next date starts the regular set.

Clock/timezone changes backwards never grant a second reward for an earlier date. Client requests carry the date from the last server snapshot: a request sent before midnight cannot accidentally claim the next day's reward.

## Delivery and persistence

- Independent additive SavedData: `world/data/ccec_daily_rewards.dat`, format version 1. Other saves and the economy are untouched. UUID is taken from the authenticated ServerPlayer.
- A claim first reserves the date/progression and exact rolled ItemStack/levels. The module writes the standard SavedData NBT envelope synchronously using a forced temporary file and atomic replacement before modifying inventory.
- The inventory operation is planned against all main inventory slots before applying anything. If the entire item reward cannot fit, no item is inserted or dropped. It remains pending and blocks another reward reservation until collected, even across dates, reconnects or restarts.
- Random trim/final-day results are chosen only during reservation. Repeated packets retry the saved reward rather than rolling again. The trim pool includes only vanilla `*_armor_trim_smithing_template` items; it excludes netherite upgrades.
- Delivery writes a unique receipt into NeoForge's `PlayerPersisted` compound, which survives respawn. The existing public vanilla `PlayerList.saveAll()` path saves player data and existing save hooks. This occurs only on reward delivery, not during polls or ticks. The module verifies and forces the recipient's persisted receipt before settling the pending journal record.
- If the process stops after the player save and before journal settlement, the matching receipt prevents another inventory/XP mutation. If player persistence has not happened, the saved pending reward can be retried. Delivered reward metadata is retained for the current card display.
- Disk/config/invalid save failures fail closed rather than silently replacing progress with a new starter set. Atomic file replacement support is required on the world filesystem. Manual world/playerdata rollback is outside this transaction guarantee.

## Configuration

On first use, the mod copies the bundled defaults from `data/createcityexpansioncore/daily_rewards.json` to **`config/ccec-daily-rewards.json`**. Restart the server after editing it.

`timeZone: "server"` uses the server operating system timezone, not the player's computer. It can be replaced with an IANA ID such as `Europe/Minsk`. Set it deliberately before launch; avoid changing it in the middle of an active series.

Each table has exactly seven entries. Supported simple entries are `item` (`item`, `count`), `book` (`enchantment`, `level`, `count`), `wood` (`count`), `trim` (`count`), `levels` (`levels`), and `chance` (`item`, `alternative`, `chance`, `count`). The default `chance: 0.1` means a 10% Nether Star and otherwise a netherite ingot. The provided tables match the requested starter and regular rewards. Existing reserved rewards retain their saved outcome after configuration changes.

## OP commands

- `/dailyrewards reset <player>` — reset all daily progress, starter flags and pending reward for an online name, saved offline reward name or UUID. Does not remove items already received.
- `/dailyrewards resetall` — same reset for every saved UUID, including offline players.
- `/dailyrewards open` — open the executing player's window even if already claimed.
- `/dailyrewards open <player>` — open an online target's window, without granting anything.

All four require vanilla permission level 2. Online state is synchronized after a reset. Resets are explicit testing operations and intentionally allow unique starter rewards to be earned again.

## Files

Created:
- `daily/DailyRewardProgress.java`, `DailyRewardsData.java`, `DailyRewardsConfig.java`, `DailyRewardsService.java`, `DailyRewardsNetwork.java`.
- `network/DailyRewardsRequest.java`, `DailyRewardsState.java`.
- `client/DailyRewardsClient.java`.
- `client/screen/DailyRewardsLayout.java`, `DailyRewardsScreen.java`.
- Default reward JSON, 16×16 gift texture and filter metadata.
- `src/mineVisualTest/java/com/example/examplemod/daily/DailyRewardsCheck.java`, `tools/check-daily-rewards.gradle`.

Modified: `SocialHubScreen.java`, `SocialHubLayout.java`, RU/EN language files and `gradle.properties`.

## Verification

`compileJava checkDailyRewards assemble --offline --no-configuration-cache -I tools/check-daily-rewards.gradle` builds the JAR and runs 25 focused calendar, duplicate-day, starter fallback, NBT, reset and layout checks. No new test framework. Existing NeoForge/Gradle deprecation warnings remain. These checks do not prove inventory delivery or UI rendering in Minecraft.

### In-game checklist

1. Fresh login: eligible offer opens after world loading; a previously claimed day does not open it. Close it and reopen through Community's gift. Another GUI must not be interrupted.
2. Claim once; double click, repeat request/reconnect and reopen. No second reward. Test both OP open commands without item grants.
3. Fill inventory, claim an item, reconnect/restart, free space and collect. The item and random outcome must remain identical, with nothing dropped on the ground.
4. Verify Mending I on days 3/6, Efficiency V on starter day 7, armour-trim-only templates, and actual log species on fallback/regular day 1.
5. Verify experience days add +10/+5 levels, not raw XP points; test pending receipt recovery and death/respawn on a test world.
6. Test consecutive days, a missed date, repeated starter-day fallback, all seven unique flags and permanent regular cycling using a controlled test date environment. Do not change the production host clock to test.
7. Reset an offline player by saved name/UUID; resetall; reconnect and confirm complete starter reset. Non-OP commands must be denied.
8. Check RU/EN, small resolutions, GUI Scale, item tooltips and gift hover/pulse; reduced motion must remove the pulse.

No production deployment, launcher update or push is included.
