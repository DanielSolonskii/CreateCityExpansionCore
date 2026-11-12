# CCEC — Cargo equipment / Blockbench sources

Three original models based on the approved industrial concept sheet.

| Project | Block | Elements |
| --- | --- | ---: |
| `cargo_router/cargo_router.bbmodel` | Грузовой маршрутизатор | 50 |
| `cargo_ticker/cargo_ticker.bbmodel` | Грузовой терминал запросов | 60 |
| `chunk_loader/chunk_loader.bbmodel` | Загрузчик чанка | 63 |

Open a `.bbmodel` with **File → Open Model** in Blockbench. Format: **Java
Block/Item**, face UV, embedded 128×128 RGBA texture. No plugins required.
Named outliner groups separate housing, frame, fasteners, ports and controls.
Individual PNG files are included for external painting; the source projects
remain portable without them because the texture is also embedded.

Coordinates use Minecraft model units (16 = one block), front is north (-Z).
Every element, including the terminal's rotated monitor, stays inside 0..16.
The monitor uses a legal Java block rotation of -22.5° around X. Other parts
are unrotated cuboids. There are no meshes or unsupported multi-axis rotations.
The decorative green/cyan textures use ordinary Minecraft lighting; they do
not claim runtime emissive rendering or indicate actual machine state.

Runtime JSONs and PNGs are installed in `src/main/resources/assets/` under
the existing block identifiers. Existing blockstates and item parents already
reference these models. The block mechanics, placement direction and collision
boxes are unchanged. In particular the terminal currently has a fixed north
front, matching its existing block state, rather than player-facing placement.

`cargo_equipment_preview.png` shows a deterministic textured render of the
exported Java geometry, with front and rear views. It is not a Minecraft or
Blockbench screenshot. `atlas_preview.png` enlarges the original pixel atlas.

Regenerate from the repository root:

```text
python tools/build_cargo_models.py
```

Requires Python 3, Pillow and numpy. This overwrites these generated projects,
their textures, previews and the three runtime block models; keep manual
Blockbench edits in a separate copy or incorporate them into the generator.

`CCEC-cargo-equipment-Blockbench.zip` contains all editable sources, textures,
previews, this guide and the corresponding runtime model/texture files.

Validation: JSON parsing, texture dimensions and embedded bytes, unique UUIDs,
complete group membership, UV limits, positive cuboid dimensions, transformed
0..16 bounds, legal rotations, blockstate/item links and ZIP CRCs. Gradle
`processResources --offline` passes. Interactive Blockbench import validation
was interrupted with Escape; Minecraft runtime rendering has not been checked.
