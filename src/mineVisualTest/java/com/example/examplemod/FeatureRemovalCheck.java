package com.example.examplemod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Checks the shipped artifact, including stale compiler output and data-pack references. */
public final class FeatureRemovalCheck {
    private static final Pattern RETIRED_CLASS = Pattern.compile(
            "com/example/examplemod/(railway/|coke/|blast/|steel/|client/freight/|"
            + "economy/(Freight|Trade|Market|Shipping|Shipment|IndustrialContract|IndustrialShipment|"
            + "ContractPackage|EconomyInfrastructure|EconomyNetwork|EconomyTypes|ExternalMarket|PrivateListing|SmartOrder)|"
            + "client/screen/(Freight|Logistics|Commerce|Market|TradeCheckout|RemoteMarket|Economy)|"
            + "network/(Railway|Freight|Economy|Infrastructure|Trade|CokeBattery|BlastFurnace|SteelConverter)|"
            + "item/DispatcherKey|event/DispatcherKey)");
    private static final String RETIRED_ID = "(?:coke_battery_[a-z_]+|coke_oven_chamber|blast_furnace_[a-z_]+|"
            + "steel_converter_[a-z_]+|steelmaking_blower_port|railway_track_marker|dispatcher_key|"
            + "contract_intake|contract_dispatch_terminal|warehouse_gateway|trading_warehouse|shipping_manifest|shipment_seal)";
    private static final Pattern RETIRED_RESOURCE = Pattern.compile(
            "(?:^|/)" + RETIRED_ID + "(?:\\.|/)");
    private static final Pattern RETIRED_REFERENCE = Pattern.compile(
            "createcityexpansioncore:" + RETIRED_ID + "[\"/]");

    public static void main(String[] args) throws Exception {
        int checked = 0;
        try (var zip = new ZipFile(Path.of(args[0]).toFile())) {
            for (String entry : List.of(
                    "com/example/examplemod/mine/MineControllerBlockEntity.class",
                    "com/example/examplemod/multiblock/MineMultiblock.class",
                    "com/example/examplemod/economy/EconomyManager.class",
                    "com/example/examplemod/economy/BankBook.class",
                    "com/example/examplemod/projects/ProjectManager.class",
                    "com/example/examplemod/city/CityManager.class",
                    "com/example/examplemod/client/screen/CityPylonWorkspace.class",
                    "com/example/examplemod/client/map/CityMapPanel.class",
                    "assets/createcityexpansioncore/blockstates/mine_controller.json")) {
                if (zip.getEntry(entry) == null) throw new AssertionError("Missing retained feature: " + entry);
                checked++;
            }
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (RETIRED_CLASS.matcher(name).find() || RETIRED_RESOURCE.matcher(name).find())
                    throw new AssertionError("Retired feature in production JAR: " + name);
                if (name.endsWith(".json")) {
                    try (var in = zip.getInputStream(entry)) {
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        if (RETIRED_REFERENCE.matcher(json).find())
                            throw new AssertionError("Dangling registry reference: " + name);
                    }
                }
                checked++;
            }
        }
        System.out.println("PASS: " + checked + " retained-feature and production JAR removal checks");
    }
}
