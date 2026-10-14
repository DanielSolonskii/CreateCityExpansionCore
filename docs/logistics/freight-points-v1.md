# City freight points v1

Implementation: 2026-09-30. Minecraft 1.21.1 / NeoForge / Create 6.0.10.

## Architecture

`EconomyManager` owns `FreightLogistics` in the same versioned SavedData as wallets,
treasuries, contracts, escrow, market offers, manifests and shipment tokens.
`FreightPoint` is a logical city identity; Postbox/Packager positions are physical
references. `FreightAssignment` connects a trade to source and destination points
and persists the destination address and paused/running flag. Token destinations
are recorded separately so changing source never retargets an in-flight package.

P2P procurement, selling to Master City and buying from Master City share those
models and the same `ContractPackageUnpackingHandler` / `acceptShipmentPackage`
validation. Personal wallet listings and barter retain their existing handover UI;
city freight sales are available from Market → Contracts → City sales.

## Player flow

1. Open City Pylon → Logistics → Freight points → Add.
2. Build a Create Postbox linked to a real station, and a regular Packager within
   16 blocks. Connect their package transport physically using Create.
3. For receiving, the Packager must face away from a chest, barrel or existing
   Contract Intake. Choose the detected pair, mode, and display name.
4. Registration validates city admin permissions, city territory, loaded blocks,
   Packager compatibility, station link, receiver and duplicate registrations.
5. The server assigns `CCEC_<city UUID>_<point UUID>` to the Postbox. Display names
   are independent; no manual address entry or Intake coordinate binding is needed.
6. Select a contract/market offer, review cargo, quantity, reward/cost, deadline,
   bond, destination and server-calculated graph connectivity; choose your point
   and explicitly confirm. Card selection alone never accepts.
7. Open Logistics → Outgoing → select shipment → Start shipping. Feed the source
   Packager's inventory. Native `PackagingRequest` extracts only the assigned item,
   in batches of at most 64 and within the remaining authorization. It creates
   ordinary Create packages, which receive the address and token. Packaging does
   not credit contract progress.
8. Receipt through the destination Packager uses the existing atomic validator.
   The last valid delivery settles escrow automatically, without buyer approval.

For city SELL advertisements, the buyer selects a destination and funds a normal
city contract. The seller then selects a source in Logistics → Outgoing → Change
source, followed by Start shipping. No destination exists before the buyer accepts.

Master City uses registered points too. Import orders choose the player's receive
point; the free Master send point creates a native physical package at its
Packager and debits existing warehouse stock. One nonterminal assignment reserves
each send point, including while paused. Master imports start automatically.

## Persistence and recovery

Economy save format is 6. Existing v1–v5 fields retain their loaders. v5 contracts
and imports without an assignment preserve their money and ledgers and appear in
the Logistics overview as requiring setup. The owner can assign its default
receive point there. Existing v5 tokens retain their old validation path.

Unregistering preserves a disabled identity (tombstone), assignments and tokens.
Enable restores it after rebuilding infrastructure. Disabling/enabling also
restores the managed Postbox address. Source loss pauses dispatch; destination
loss prevents new sending/receipt but does not invalidate in-flight tokens.
Restoration of a source requires Start shipping. No chunks are force-loaded.

## Automated validation

`gradlew checkFreightLogistics` exercises the actual economy manager:

- simulation does not mutate accounting, token state, dirty flag or physical storage;
- insufficient space and failed commit leave accounting unchanged;
- wrong receiver, wrong item, excess cargo, expiry and replay reject;
- disabling either endpoint preserves existing token identity;
- in-flight reservations prevent extra authorization;
- final delivery pays escrow once;
- import simulation, receipt and bond repayment are one-time;
- paused assignments, stable addresses and token destinations survive serialization;
- complete v6 reload and v5 migration preserve existing economic records.

These are headless checks, not proof that a live train or rendered UI works.

## Runtime checklist — pending in a live test world

| Scenario | Expected observation |
| --- | --- |
| New city with zero points | Logistics shows setup guidance; registration reads station and generates address |
| P2P BUY, 512 billets | Explicit source selection, Start, eight or more partial packages; progress only at receiver; final escrow payout |
| City SELL | Buyer chooses receiver and pays escrow; seller subsequently chooses source |
| Sell to Master | Same dispatch and receipt; offer/contract complete together |
| Buy from Master | No inventory grant; Master Packager emits addressed packages; destination receipt completes order |
| No cargo / stopped dispatch | No authorized outgoing package or economic progress |
| Missing source Postbox/Packager | Point offline, dispatch paused, existing tokens remain usable |
| Disabled/deleted destination | Visible unavailable state, no silent order deletion; enable/rebuild restores receipt |
| Repackager / wrong city / duplicate blocks | Registration rejects with localized feedback |
| Disconnected stations | Server connectivity warning; Start refuses |
| Wrong destination or replayed package | Package rejected, no progress or duplicate payout |
| Overfull destination / excess package | Whole-package rejection; no partial economic credit |
| Restart in transit / paused | Stable token destination and paused state; delivery can continue |
| Extra package after completion | Rejected, no second payout |
| GUI scale / small window | Test registration, scrolling review, list selection and confirmation |

## Limits

- Route preview shows station names and same-Create-TrackGraph connectivity, not
  a navigable itinerary, distance, timetable or signal state. No tariffs are invented.
- Player-built belts/funnels and the train schedule remain Create configuration.
  CCEC detects nearby blocks; it cannot prove that a belt network actually connects them.
- Receive containers supported in this version: chest, barrel and Contract Intake.
  A double chest uses the targeted chest half for the transactional buffer.
- The existing Master warehouse-stock ledger supplies imports; funding/populating
  that stock still uses existing Master administration/warehouse infrastructure.
- Create consumes the outer package after a successful handler return; atomicity
  covers the server-thread transaction, not a JVM crash between independent saves.
- A free Master send point is required to accept an import. Multi-order scheduling,
  multi-source dispatch, tariffs and offline simulation are not implemented.

## Files in this change

New economy classes: FreightPoint, FreightAssignment, FreightLogistics, FreightSellOffer.
New screens: LogisticsScreen, FreightConfirmScreen, FreightSellScreen, FreightUi.
Updated: EconomyManager, EconomyNetworkHandlers, EconomyCommandPayload,
EconomyStatePayload, ContractPackageUnpackingHandler, ContractDispatchTerminalBlockEntity,
CreateCityExpansionCore, CityPylonScreen, MarketScreen, EconomyCreateScreen,
both language JSONs and build.gradle.
New check: src/mineVisualTest/java/com/example/examplemod/economy/FreightLogisticsCheck.java.

The checkout already contained unrelated local changes; no commit was created.
