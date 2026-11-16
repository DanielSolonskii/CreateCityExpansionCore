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
