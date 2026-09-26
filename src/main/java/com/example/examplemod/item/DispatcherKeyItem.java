package com.example.examplemod.item;

import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.economy.EconomyInfrastructureRegistration;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.economy.EconomyTypes;
import com.example.examplemod.economy.block.ContractIntakeBlock;
import com.example.examplemod.economy.block.EconomyThroughputBlockEntity;
import com.example.examplemod.economy.block.WarehouseGatewayBlock;
import com.example.examplemod.railway.CreateRailwayAdapter;
import com.example.examplemod.sound.ModSounds;
import com.example.examplemod.sound.WorldSoundHelper;
import com.example.examplemod.railway.RailwayManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.UUID;

public final class DispatcherKeyItem extends Item {
    private static final String ECONOMY_ROOT = "createcityexpansioncore:economy_dispatcher";
    public DispatcherKeyItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof EconomyThroughputBlockEntity
                    || context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof CityPylonBlock) {
                return InteractionResult.SUCCESS;
            }
        }
        if (context.getLevel() instanceof ServerLevel economyLevel
                && context.getPlayer() instanceof ServerPlayer economyPlayer) {
            if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof EconomyThroughputBlockEntity blockEntity) {
                return registerEconomyInfrastructure(economyLevel, economyPlayer, context.getItemInHand(), blockEntity);
            }
            if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof CityPylonBlock) {
                return bindEconomyInfrastructure(economyLevel, economyPlayer, context.getItemInHand(), context.getClickedPos());
            }
        }
        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof StationBlock)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        return useOnStation(level, player, context.getClickedPos());
    }

    private static InteractionResult registerEconomyInfrastructure(ServerLevel level, ServerPlayer player,
                                                                    ItemStack key, EconomyThroughputBlockEntity blockEntity) {
        EconomyTypes.InfrastructureType type = blockEntity.getBlockState().getBlock() instanceof ContractIntakeBlock
                ? EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                : blockEntity.getBlockState().getBlock() instanceof WarehouseGatewayBlock
                ? EconomyTypes.InfrastructureType.WAREHOUSE_GATEWAY : null;
        if (type == null) return InteractionResult.PASS;
        EconomyManager manager = EconomyManager.get(level);
        EconomyInfrastructureRegistration registration = manager.registerInfrastructure(
                blockEntity.physicalInstanceId(), player.getUUID(), type,
                level.dimension().location(), blockEntity.getBlockPos());
        if (!registration.registeredBy().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy.already_registered")
                    .withStyle(ChatFormatting.RED), true);
            WorldSoundHelper.playAt(level, blockEntity.getBlockPos(),
                    ModSounds.DISPATCHER_REGISTER_FAIL.get(), 0.8F, 1.0F);
            return InteractionResult.CONSUME;
        }
        blockEntity.setRegistrationId(registration.registrationId());
        writeEconomySelection(key, registration.registrationId());
        player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy.infrastructure_registered"), true);
        WorldSoundHelper.playAt(level, blockEntity.getBlockPos(),
                ModSounds.DISPATCHER_REGISTER_SUCCESS.get(), 0.8F, 1.0F);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult bindEconomyInfrastructure(ServerLevel level, ServerPlayer player,
                                                                ItemStack key, BlockPos pylonPos) {
        UUID registrationId = readEconomySelection(key);
        if (registrationId == null) {
            player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy.key_empty")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        CityManager cities = CityManager.get(level.getServer().overworld());
        City city = cities.getCityByPylon(level.dimension().location(), pylonPos);
        if (city == null || !city.isAdmin(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy.permission_denied")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (!EconomyManager.get(level).bindInfrastructure(registrationId, player.getUUID(), city.getId())) {
            player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy.bind_failed")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        clearEconomySelection(key);
        player.displayClientMessage(Component.translatable("message.createcityexpansioncore.economy.infrastructure_bound", city.getName()), true);
        return InteractionResult.CONSUME;
    }

    private static void writeEconomySelection(ItemStack stack, UUID id) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag data = new CompoundTag(); data.putUUID("RegistrationId", id); custom.put(ECONOMY_ROOT, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
    private static UUID readEconomySelection(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!custom.contains(ECONOMY_ROOT, CompoundTag.TAG_COMPOUND)) return null;
        CompoundTag data = custom.getCompound(ECONOMY_ROOT); return data.hasUUID("RegistrationId") ? data.getUUID("RegistrationId") : null;
    }
    private static void clearEconomySelection(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); custom.remove(ECONOMY_ROOT);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    public static InteractionResult useOnStation(
            ServerLevel level,
            ServerPlayer player,
            BlockPos stationPos
    ) {
        RailwayManager manager = RailwayManager.get(level);
        GlobalStation station = CreateRailwayAdapter.stationAt(level.dimension(), stationPos);
        if (station == null) {
            player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.station_not_connected")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            Train train = station.getPresentTrain();
            if (train == null) {
                player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.train_not_present")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            var existing = manager.trainByCreateId(train.id);
            if (existing != null && !existing.registeredBy().equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.already_registered")
                        .withStyle(ChatFormatting.RED), true);
                WorldSoundHelper.playAt(level, stationPos,
                        ModSounds.DISPATCHER_REGISTER_FAIL.get(), 0.8F, 1.0F);
                return InteractionResult.CONSUME;
            }
            manager.registerTrain(train.id, player.getUUID(), train.name.getString());
            player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.train_registered", train.name), true);
            WorldSoundHelper.playAt(level, stationPos,
                    ModSounds.DISPATCHER_REGISTER_SUCCESS.get(), 0.8F, 1.1F);
            return InteractionResult.CONSUME;
        }

        var existing = manager.stationByCreateId(station.id);
        if (existing != null && !existing.registeredBy().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.already_registered")
                    .withStyle(ChatFormatting.RED), true);
            WorldSoundHelper.playAt(level, stationPos,
                    ModSounds.DISPATCHER_REGISTER_FAIL.get(), 0.8F, 1.0F);
            return InteractionResult.CONSUME;
        }
        manager.registerStation(station.id, player.getUUID(), station.name,
                station.getBlockEntityDimension().location(), station.getBlockEntityPos());
        player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.station_registered", station.name), true);
        WorldSoundHelper.playAt(level, stationPos,
                ModSounds.DISPATCHER_REGISTER_SUCCESS.get(), 0.8F, 1.0F);
        return InteractionResult.CONSUME;
    }
}
