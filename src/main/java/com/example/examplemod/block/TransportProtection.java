package com.example.examplemod.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.railway.CreateRailwayAdapter;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Единая точка применения городских прав к транспорту.
 *
 * <p>Транспорт (contraption, поезд, физическая конструкция) защищён,
 * если внутри него стоит терминал регистрации транспорта
 * ({@link TransportRegistrationTerminalBlockEntity}), закреплённый за
 * городом. Права проверяются ТОЛЬКО через
 * {@link CityManager#allowsTerritoryAction} — тот же предикат, что
 * защищает участки 50x50: участники города (с правом строительства
 * для изменений), союзные и доверенные города через
 * grantsTerritoryAccess. Любые будущие изменения этой системы
 * (новые роли, типы союзов) автоматически распространяются на
 * транспорт — отдельной логики прав здесь нет и появляться не должно.
 *
 * <p>Вождение публично: сиденья ({@link SeatBlock}) и контролы
 * ({@link ControlsBlock}) доступны любому игроку — сеть дорог
 * общая, но груз, разборка и правки защищены.
 */
public final class TransportProtection {

    private TransportProtection() {
    }

    // =========================================================
    // ПРОВЕРКИ
    // =========================================================

    /** Может ли игрок использовать mounted-блок contraption'а. */
    public static boolean canInteractWithContraption(
            ServerPlayer player,
            AbstractContraptionEntity entity,
            BlockPos localPos
    ) {
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return true;
        }
        StructureBlockInfo info = contraption.getBlocks().get(localPos);
        Block block = info == null ? null : info.state().getBlock();

        // Езда публична: сиденья и контролы не защищаем.
        if (block instanceof SeatBlock || block instanceof ControlsBlock) {
            return true;
        }
        City city = registeredCity(contraption, player.serverLevel());
        if (city == null) {
            return true;
        }
        return cityManager(player.serverLevel()).allowsTerritoryAction(city, player.getUUID(), false);
    }

    /** Может ли игрок изменить поезд (перенос, разборка). */
    public static boolean canModifyTrain(
            ServerPlayer player,
            Train train
    ) {
        City city = registeredCityOfTrain(train, player.serverLevel());
        if (city == null) {
            return true;
        }
        return cityManager(player.serverLevel()).allowsTerritoryAction(city, player.getUUID(), true);
    }

    /** Может ли игрок разобрать поезд на станции. */
    public static boolean canDisassembleFromStation(
            ServerPlayer player,
            StationBlockEntity station
    ) {
        Train train = presentTrain(station);
        if (train == null) {
            return true;
        }
        return canModifyTrain(player, train);
    }

    /**
     * Может ли игрок разобрать (гайковёртом) contraption, собранный
     * на подшипнике/блоке в указанной мировой позиции.
     */
    public static boolean canModifyContraptionAtAnchor(
            ServerPlayer player,
            ServerLevel level,
            BlockPos anchor
    ) {
        List<AbstractContraptionEntity> entities = level.getEntitiesOfClass(
                AbstractContraptionEntity.class,
                new AABB(anchor).inflate(2.0D),
                entity -> entity.getContraption() != null
                        && anchor.equals(entity.getContraption().anchor)
        );
        for (AbstractContraptionEntity entity : entities) {
            City city = registeredCity(entity.getContraption(), level);
            if (city == null) {
                continue;
            }
            if (!cityManager(level).allowsTerritoryAction(city, player.getUUID(), true)) {
                return false;
            }
        }
        return true;
    }

    // =========================================================
    // ОПРЕДЕЛЕНИЕ ГОРОДА ТРАНСПОРТА
    // =========================================================

    /**
     * Город, за которым закреплён contraption, или null.
     *
     * <p>Терминал ищется среди блоков contraption'а. NBT
     * установленного терминала Create переносит внутрь
     * StructureBlockInfo при сборке, поэтому идентификатор города
     * читается прямо из NBT блока — BlockEntity инстанциировать
     * не нужно.
     */
    @Nullable
    public static City registeredCity(
            Contraption contraption,
            ServerLevel level
    ) {
        if (contraption == null) {
            return null;
        }
        Block terminal = CreateCityExpansionCore.TRANSPORT_REGISTRATION_TERMINAL.get();
        for (StructureBlockInfo info : contraption.getBlocks().values()) {
            if (info.state().getBlock() != terminal) {
                continue;
            }
            CompoundTag nbt = info.nbt();
            if (nbt == null || !nbt.hasUUID(TransportRegistrationTerminalBlockEntity.CITY_TAG)) {
                continue;
            }
            UUID cityId = nbt.getUUID(TransportRegistrationTerminalBlockEntity.CITY_TAG);
            City city = cityManager(level).getCity(cityId);
            if (city != null) {
                return city;
            }
        }
        return null;
    }

    @Nullable
    public static City registeredCityOfTrain(
            Train train,
            ServerLevel level
    ) {
        if (train == null) {
            return null;
        }
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity entity = carriage.anyAvailableEntity();
            if (entity == null) {
                continue;
            }
            City city = registeredCity(entity.getContraption(), level);
            if (city != null) {
                return city;
            }
        }
        return null;
    }

    @Nullable
    private static Train presentTrain(StationBlockEntity station) {
        var global = station.getStation();
        return global == null ? null : global.getPresentTrain();
    }

    // =========================================================
    // ВСПОМОГАТЕЛЬНОЕ
    // =========================================================

    private static CityManager cityManager(ServerLevel level) {
        return CityManager.get(level.getServer().overworld());
    }

    /** Единое сообщение об отказе — не расплескивать по mixin'ам. */
    public static void deny(ServerPlayer player) {
        player.sendSystemMessage(
                Component.translatable("message.createcityexpansioncore.transport_terminal.protected").withStyle(net.minecraft.ChatFormatting.RED)
        );
    }
}
