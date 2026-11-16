# Industrial Electro Energetics

For Minecraft 1.21.1 with Electro Energetics 1.1.2 and The Factory Must Grow 1.2.0.

1. Copy the `industrial-ee` directory into the server world's `datapacks` directory.
2. Use `/datapack list` to confirm it is active. If needed, run `/datapack enable "file/industrial-ee" last`.
3. Set these entries in the existing `[rotor]` section of the server's EE config, `config/electroenergetics-server.toml`, and restart the server to apply them.

```toml
[rotor]
rotorPowerMultiplier = 18.0
rotorStressMultiplier = 1.0
rotorFullLoadCurrent = 100.0
```

The pack replaces the rotor, stator and DC brush recipes and adds a recipe for three-phase brushes. A full EE section requires one TFMG rotor and eight TFMG stators. The rotor is made in Create mechanical crafters. The other three recipes use a normal crafting grid.

Transformer oil now uses the TFMG Chemical Vat instead of the Create mixer. A heated steel or firebrick-lined vat with a mixing attachment turns 250 mB of plant oil and 250 mB of water into 125 mB of transformer oil.

The datapack controls recipes. The EE config applies the power setting to both brush types and to existing alternators. A full section has one rotor and four correctly oriented stators. Its nominal output is 13.824 kW at 64 RPM, 27.648 kW at 128 RPM and 55.296 kW at 256 RPM.

## Powering a tier I mine

The verified setup uses a full TFMG generator at 256 RPM, connected to the TFMG side of the DC bridge. Set the bridge to TFMG → EE and select 600 V. Set the bridge's global current limit to 150 A with `/ccec powerbridge maximumAmps 150`, or set `maximumAmps = 150.0` in `config/createcityexpansioncore-powerbridge.toml` before startup. This setting applies to every bridge. Keep the standard 100 kW limit and 95% efficiency.

The test measured 503.0 V in standby and 503.0 V, 20.12 A and 10.12 kW with the actual mine power port requesting its tier I operating load. At the standard 100 A bridge limit the operating voltage was only 483.8 V. The verified setup uses short connections. Check the voltage at the mine port if using a longer line.

## Motor balance

With the tested settings, an EE motor supplied 1,600 SU to a shaft load at 32 RPM and drew 2,000 W. Its electrical resistance setting scales mechanical capacity and electrical consumption together, so changing that setting alone does not change the full-load conversion ratio of 0.80 SU/W.

With the TFMG motor's stock 30 Ω resistance and base capacity of 12 SU per RPM, the maximum capacity per input watt occurs at 80 V after integer rounding. The server test measured 192 SU of available capacity and 213 W of draw at that point. The best of the tested survival generators, a fully built EE section, returns less than 1 W per input SU. Both motor ratios therefore leave a loss in the round trip, even before cable and bridge losses. These results assume Create stress is enabled and the tested generator and motor settings are applied.

The `IndustrialEnergyGameTest` suite checks the loaded recipes, a four-stator EE section on a test load at each speed, and the TFMG-to-EE converter feeding an actual mine power port. For development, use `-I tools/powerbridge-gametest.init.gradle -I tools/industrial-energy-gametest.init.gradle runGameTestServer -x prepareDevMods` with both configs above in `build/checks/powerbridge-with-tfmg/config`.

## Industrial Create Propulsion

Install `industrial-propulsion` separately in the world's `datapacks` directory on a server with Create Propulsion 1.1.5, its required dependencies and TFMG 1.2.0. Enable it with `/datapack enable "file/industrial-propulsion" last` if needed. The two packs can be installed independently.

- Both burners use a TFMG gas lamp in place of the blast furnace.
- The liquid and solid fuel thrusters use a TFMG heavy machinery casing in place of the brass casing and chute, respectively.
- The ion thruster uses a TFMG steel mechanism in place of the precision mechanism.
- One pine resin and 250 mB of water produce 250 mB of turpentine in an unheated steel or firebrick-lined Chemical Vat with a mixing attachment.

The recipe test loads and checks the Propulsion pack when the Propulsion mod is present in the test game.

## Industrial TFMG ore recovery

Requires Minecraft 1.21.1, Create 6.0.10 and TFMG 1.2.0. Copy the `industrial-tfmg-ores` folder into the world's `datapacks` folder and run `/reload`. Check `/datapack list enabled` and run `/datapack enable "file/industrial-tfmg-ores" last` if needed.

Use a steel or firebrick-lined Chemical Vat with a powered Industrial Mixer. Fit the mixing blade for heated recipes and the centrifuge attachment for the other recipes. A heated vat needs at least two fed Blaze Burners underneath it with ordinary fuel. Raw ores in the table are the items dropped by mining.

| Product | Inputs per batch | Guaranteed outputs | Vat setup |
| --- | --- | --- | --- |
| Nickel | 4 raw copper and 125 mB sulfuric acid | 1 raw nickel and 2 crushed copper | Mixing blade, heated |
| Lead | 4 raw zinc and 250 mB water | 1 raw lead and 2 crushed zinc | Centrifuge |
| Bauxite | 2 raw iron, 1 clay ball and 250 mB water | 1 bauxite powder and 1 crushed iron | Centrifuge |
| Lithium | 2 clay balls, 1 redstone dust and 500 mB sulfuric acid | 1 raw lithium | Mixing blade, heated |
| Sulfur | 4 coal and 250 mB water | 1 sulfur dust and 2 coal | Centrifuge |

For the first vat and centrifuge, use the following recipes in a regular Create Mechanical Mixer and Basin. These batches yield no ore byproducts.

| Inputs | Output | Heat |
| --- | --- | --- |
| 8 raw copper, 1 charcoal and 500 mB water | 1 raw nickel | Heated |
| 8 raw zinc, 1 charcoal and 500 mB water | 1 raw lead | None |
| 5 raw iron, 4 clay balls and 1000 mB water | 1 bauxite powder | None |

Process the starter bauxite powder into aluminum using the stock TFMG vat recipe with two electrodes. The centrifuge then gives access to sulfur for the stock sulfuric acid recipe.

For fireclay, combine 3 clay balls and 1 brick in any crafting grid to make 4 fireclay balls. The brick represents crushed fired clay mixed into the raw clay.
