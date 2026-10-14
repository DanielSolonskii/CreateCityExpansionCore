# Blast Furnace — vertical slice

The Blast Furnace is the second physical core of the future Metallurgical Works. Create remains responsible for rail unloading, elevated charging, external storage and the Hot Metal pipe/tank network.

## Canonical structure

Size: **11 × 13 × 11** (`X=0..10`, `Y=0..12`, `Z=0..10`). The Controller faces outward from front `Z=0`; local positive Z points toward the rear. `BlastFurnaceMultiblock.expectedAt` is the sole pattern source for validator, guide and future Ponder work.

| Role | Exact count |
|---|---:|
| Reinforced Concrete | 121 |
| Refractory Block | 233 |
| Blast Furnace Core | 81 |
| Mine Frame | 476 |
| Controller | 1 |
| Item Input Port | 1 |
| Hot Metal Output Port | 1 |
| Mine Power Port | 1 |
| Required air | 658 |
| Placed blocks | **915** |
| Bounding-box cells | **1573** |

Functional local coordinates:

- Controller: `(5,2,0)`
- Item Input: `(5,10,0)`
- Hot Metal Output: `(7,1,0)`
- reused Mine Power Port: `(5,1,10)`

Layers:

- `Y0`: full reinforced-concrete foundation.
- `Y1`: structural perimeter, 7×7 hearth, central 3×3 core and refractory hearth ring.
- `Y2..Y9`: structural perimeter, required service air, 5×5 shaft with central 3×3 core.
- `Y10`: charging perimeter and 7×7 refractory roof with 3×3 opening.
- `Y11`: perimeter and 5×5 refractory ring with 3×3 opening.
- `Y12`: perimeter and 3×3 refractory cap.

## Process

`8 Iron Ingots + 4 Industrial Coke + 2 Calcite -> 8000 mB Hot Metal`

- Provisional duration: 300 ticks / 15 seconds per batch.
- Input buffer: 9 item slots, insertion-only capability, filtered to the three ingredients.
- Output buffer: 32,000 mB, extraction-only NeoForge fluid capability.
- Ingredients are consumed only after a full recipe, power and 8000 mB output capacity have been confirmed.
- The Hot Metal insertion is transactional; an unexpected short fill rolls the ingredients back.
- The output port stores Hot Metal in its dropped item and restores it when placed again.

Hot Metal is a registered NeoForge fluid (`createcityexpansioncore:hot_metal`). It has no bucket and no placeable fluid block, but is exposed through the standard fluid capability for Create pipes and tanks.

## Electrical requirements

- Working demand: 60 kW.
- Standby demand: 200 W.
- Accepted working voltage: 500–600 V.
- Minimum measured working power: 51 kW.
- Existing Mine Power Port / EE `SimulatedDevice` path is reused.

## Build guide

- Empty-hand right-click the Controller: toggle guide and force validation.
- `Page Up`: next layer (`Y=0..12`).
- `Page Down`: previous layer.
- `Home`: selected layer / all layers.
- Blue: missing block; cyan: missing functional interface; red: wrong block; orange: occupied required-air cell; correct cells are hidden.
- Action bar shows the nearest error, required block, local/world coordinates and remaining errors of that type.

## Manual runtime checklist

1. Assemble the north-facing structure and verify `915/915`.
2. Repeat east, south and west rotations.
3. Traverse guide layers `Y0..Y12` and all-layer mode.
4. Verify missing, wrong and occupied-air highlights.
5. Verify Controller/Input/Output/Power coordinates.
6. Connect nominal EE power and observe the approximately 60 kW working load.
7. Disconnect or undervolt EE; progress must pause without consuming inputs.
8. Insert iron, Industrial Coke and calcite using Create belts/funnels/chutes; reject other items.
9. Complete a batch and verify exactly 8000 mB Hot Metal.
10. Extract using Create pipes into a Create tank; verify external filling of the output port is rejected.
11. Fill the 32,000 mB output and verify `OUTPUT_BLOCKED` without ingredient consumption.
12. Break the structure during processing and verify shutdown within one active validation interval.
13. Break/re-place the input port and verify items drop exactly once.
14. Break/re-place the output port and verify Hot Metal remains in the item.
15. Unload/reload chunks and restart the world mid-cycle; verify inventory, tank and progress.
16. Start a dedicated server and repeat capability and guide checks with a client.

## Deferred

- Final throughput benchmark and balance.
- CRE-57 Ponder scene.
- CRE-56 Blockbench model/animation pass.
- Slag, blast-furnace gas, pollution and maintenance.
- Steelmaking Furnace.

## Linear CRE-60 update text

Blast Furnace vertical slice implemented at 11×13×11: 915 placed blocks and 658 required-air cells. Functional coordinates are Controller `(5,2,0)`, charging input `(5,10,0)`, Hot Metal output `(7,1,0)` and EE power `(5,1,10)`. Registered `hot_metal`, added a 32-bucket extraction-only output, 60 kW EE load, safe 8 iron + 4 coke + 2 calcite -> 8000 mB processing, and a source-of-truth-driven layer guide. Build and static validation pass; runtime verification remains required before Done. Relates to CRE-35, CRE-54, CRE-55, CRE-56 and CRE-57.
