package com.example.examplemod.mine;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.command.BlueprintCommands;
import com.example.examplemod.economy.EconomyCommands;
import com.example.examplemod.geology.DepositQualityClass;
import com.example.examplemod.geology.DepositShape;
import com.example.examplemod.geology.DepositSizeClass;
import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import com.example.examplemod.multiblock.BlueprintCatalog.Structure;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.network.BlueprintAccessNetwork;
import com.example.examplemod.network.MineControllerSnapshotPayload;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BlueprintProgressionCheck {
    public static void main(String[] args) {
        checkAdminCommand();
        checkCityProgress();
        checkNetworkState();
        checkCommissionedMineWithoutDepositData();
        checkMineRates();
        System.out.println("PASS: admin command permissions, tier bounds, city locks and unlocks, network state, five mine rates and power limits.");
    }

    private static void checkCommissionedMineWithoutDepositData() {
        var deposit = ResourceLocation.fromNamespaceAndPath("test", "bound_vein");
        var city = UUID.randomUUID();
        var project = UUID.randomUUID();
        for (boolean assembled : new boolean[]{false, true}) {
            // A broken mine has no region in the packet. A regenerated region may lack the old vein.
            Optional<GeologyRegionSnapshot> region = assembled
                    ? Optional.of(new GeologyRegionSnapshot(ResourceLocation.fromNamespaceAndPath("test", "region"), 0, 0, List.of()))
                    : Optional.empty();
            var snapshot = new MineControllerSnapshot(BlockPos.ZERO, ResourceLocation.withDefaultNamespace("overworld"),
                    assembled ? IndustrialMineState.NO_DEPOSIT : IndustrialMineState.ERROR,
                    MineElectricalState.NO_POWER, 0, 0, 0, 0, 0, 0, assembled,
                    assembled ? "" : "A required block was broken", true,
                    new MinePowerSnapshot(false, false, 0, 0, 0, 120),
                    new MineOutputSnapshot(false, 0, 0), region, Optional.of(deposit), Optional.empty(),
                    true, true, Optional.of(city), Optional.of(project), 0, 0, false, "");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                MineControllerSnapshotPayload.STREAM_CODEC.encode(buffer, new MineControllerSnapshotPayload(snapshot, false));
                var decoded = MineControllerSnapshotPayload.STREAM_CODEC.decode(buffer).snapshot();
                require(decoded.equals(snapshot), "Mine status and registration must survive the packet");
                require(decoded.selectedDeposit().isEmpty() && decoded.selectedDepositId().orElseThrow().equals(deposit),
                        "The bound deposit ID survives without display data");
            } finally {
                buffer.release();
            }
        }
    }

    private static void checkAdminCommand() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        EconomyCommands.register(dispatcher);
        dispatcher.register(Commands.literal("ccec").then(Commands.literal("sample").executes(context -> 1)));
        BlueprintCommands.register(dispatcher);
        for (String action : List.of("unlock", "lock")) {
            String prefix = "ccec blueprint " + action + " \"Old Foundry\" ";
            for (Structure structure : Structure.values()) {
                String command = prefix + structure.key() + " " + structure.maxTier().level();
                require(canRun(dispatcher, command, 2), "Operator should be able to " + action + " " + structure);
                require(!canRun(dispatcher, command, 0), "Regular players cannot use " + action);
                require(!canRun(dispatcher, command, 1), "Operator level 1 cannot use " + action);
                require(canRun(dispatcher, prefix + structure.key() + " 1", 2), "Tier I can be targeted");
                require(!canRun(dispatcher, prefix + structure.key() + " 0", 2), "Tier zero is invalid");
                require(!canRun(dispatcher, prefix + structure.key() + " " + (structure.maxTier().level() + 1), 2),
                        "Unsupported tiers are rejected for " + structure);
            }
            require(!canRun(dispatcher, prefix + "unknown 2", 2), "Unknown structure is rejected");
        }
        require(!canRun(dispatcher, "cityadmin blueprint unlock Foundry mine 2", 2), "Old blueprint path is removed");
        require(canRun(dispatcher, "ccec sample", 0), "Other ccec branches keep their permission rules");
    }

    private static boolean canRun(CommandDispatcher<CommandSourceStack> dispatcher, String command, int permission) {
        var source = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "Test", Component.literal("Test"), null, null);
        var parsed = dispatcher.parse(command, source);
        return !parsed.getReader().canRead() && parsed.getContext().getCommand() != null;
    }

    private static void checkCityProgress() {
        CityManager manager = new CityManager();
        UUID firstOwner = UUID.randomUUID();
        var first = manager.createCity("Foundry", firstOwner, BlockPos.ZERO);
        var second = manager.createCity("Harbor", UUID.randomUUID(), new BlockPos(500, 0, 500));
        CompoundTag legacy = manager.save(new CompoundTag(), null);
        for (Tag raw : legacy.getList("Cities", Tag.TAG_COMPOUND)) {
            ((CompoundTag) raw).remove("BlueprintTiers");
        }
        CityManager restored = CityManager.load(legacy, null);
        for (Structure structure : Structure.values()) {
            require(restored.getMemberCity(firstOwner).getUnlockedBlueprintTier(structure) == Tier.I.level(),
                    "Legacy city should have the first tier of " + structure);
        }
        require(restored.unlockBlueprintTier(first.getId(), Structure.MINE, Tier.IV), "Mine unlock should succeed");
        require(restored.unlockBlueprintTier(first.getId(), Structure.TANK, Tier.II), "Tank unlock should succeed");
        require(!restored.unlockBlueprintTier(first.getId(), Structure.MINE, Tier.II), "Unlock cannot downgrade a city");
        require(!restored.unlockBlueprintTier(first.getId(), Structure.VAULT, Tier.IV), "Vault has three tiers");
        require(!restored.unlockBlueprintTier(UUID.randomUUID(), Structure.MINE, Tier.II), "Unknown city cannot unlock");
        require(restored.lockBlueprintTier(first.getId(), Structure.MINE, Tier.III), "Lock removes tier III and above");
        require(!restored.lockBlueprintTier(first.getId(), Structure.MINE, Tier.IV), "Lock cannot grant tiers");
        require(!restored.lockBlueprintTier(first.getId(), Structure.VAULT, Tier.IV), "Lock validates the structure limit");
        require(restored.lockBlueprintTier(first.getId(), Structure.TANK, Tier.I), "Lock I hides the structure");
        require(!restored.lockBlueprintTier(first.getId(), Structure.TANK, Tier.I), "Duplicate locks are harmless");

        CityManager reloaded = CityManager.load(restored.save(new CompoundTag(), null), null);
        var foundry = reloaded.getMemberCity(firstOwner);
        require(foundry.getUnlockedBlueprintTier(Structure.MINE) == Tier.II.level(), "Mine downgrade survives save and load");
        require(foundry.getUnlockedBlueprintTier(Structure.TANK) == 0, "Full lock survives save and load");
        require(foundry.getUnlockedBlueprintTier(Structure.VAULT) == Tier.I.level(), "Other structures remain at their own tier");
        require(reloaded.getCity(second.getId()).getUnlockedBlueprintTier(Structure.MINE) == Tier.I.level(),
                "A city's unlock must never affect another city");
        require(reloaded.unlockBlueprintTier(first.getId(), Structure.TANK, Tier.I)
                && foundry.getUnlockedBlueprintTier(Structure.TANK) == Tier.I.level(), "Hidden structures can be restored");
    }

    private static void checkNetworkState() {
        var state = new BlueprintAccessNetwork.State(new UUID(0, 0),
                Map.of(Structure.MINE, 2, Structure.VAULT, 1, Structure.TANK, 0));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            BlueprintAccessNetwork.State.STREAM_CODEC.encode(buffer, state);
            var decoded = BlueprintAccessNetwork.State.STREAM_CODEC.decode(buffer);
            require(decoded.equals(state) && decoded.isCityUpdate(), "City push round-trips including hidden structures");
            require(decoded.unlockedLevel(Structure.TANK) == 0, "Tier I stays locked on the client");
        } finally {
            buffer.release();
        }
    }

    private static void checkMineRates() {
        double[] shares = {0.10, 0.25, 0.50, 0.75, 1.00};
        double[] workingWatts = {10_000, 30_000, 65_000, 105_000, 150_000};
        GeologicalDeposit iron = new GeologicalDeposit(ResourceLocation.fromNamespaceAndPath("test", "vein"),
                ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "iron"), 0, 0.5, 0, 1.0, 1.0,
                new DepositShape(0, 0, 200, 200, 0.0, 0.3, 1L), 5_000.0,
                DepositQualityClass.DENSE, DepositSizeClass.SMALL);
        double previousCost = 0.0;
        for (Tier tier : Tier.values()) {
            int index = tier.ordinal();
            for (int x : new int[]{0, 150}) {
                MineProductionProfile profile = MineProductionProfile.from(iron, x, 0, tier);
                double expectedBasePerHour = iron.localPotentialPerHour(x, 0) * shares[index];
                double actualBasePerHour = profile.itemsPerMinute() * 60.0 / 2.0;
                require(Math.abs(actualBasePerHour - expectedBasePerHour) < 1e-8, "Local iron-equivalent rate for " + tier);
            }
            double watts = MinePowerAccess.requiredWatts(true, tier);
            require(watts == workingWatts[index], "Working demand for " + tier);
            require(MinePowerAccess.requiredWatts(false, tier) == 120.0, "Standby remains unchanged for " + tier);
            require(watts / shares[index] > previousCost, "Energy per unit must increase at " + tier);
            previousCost = watts / shares[index];
            require(classifyAt(tier, 500.0, watts, watts) == MineElectricalState.NORMAL, "Nominal operation for " + tier);
            require(classifyAt(tier, 500.0, watts * 0.8, watts) == MineElectricalState.INSUFFICIENT_POWER,
                    "Insufficient working power for " + tier);
            require(classifyAt(tier, 500.0, watts * 1.6, watts) == MineElectricalState.OVERLOAD,
                    "Overload protection for " + tier);
        }
        require(!MineProductionProfile.from(iron, 400, 0, Tier.I).viable(), "Zero local potential cannot produce");
        require(MineProductionProfile.from(iron, 150, 0, Tier.I).exactCycleTicks() > 1_200.0,
                "The new production model must permit cycles longer than the old progress cap");
    }

    private static MineElectricalState classifyAt(Tier tier, double voltage, double power, double requiredPower) {
        MinePowerSnapshot sample = new MinePowerSnapshot(true, true, voltage, power / voltage, power, requiredPower);
        return MinePowerAccess.classify(sample, true, tier);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
