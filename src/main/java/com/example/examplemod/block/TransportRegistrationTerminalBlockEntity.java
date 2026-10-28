package com.example.examplemod.block;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * BlockEntity терминала регистрации транспорта.
 *
 * <p>Хранит город, участником которого был игрок в момент
 * постановки блока. Блок без привязки к городу юридически
 * установлен, но функционала не имеет — это фиксируется
 * сообщением игроку при установке.
 *
 * <p>Города хранятся в CityManager поверх overworld SavedData,
 * поэтому достаточно идентификатора города: он валиден в любом
 * измерении и переживает выгрузку чанка.
 */
public final class TransportRegistrationTerminalBlockEntity extends BlockEntity {

    /** NBT-ключ города. Используется и при чтении из NBT contraption'ов (см. TransportProtection). */
    public static final String CITY_TAG = "City";

    /** Город, участником которого был ставящий игрок (null — терминал вне города). */
    @Nullable
    private UUID cityId;

    public TransportRegistrationTerminalBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(
                CreateCityExpansionCore.TRANSPORT_REGISTRATION_TERMINAL_BLOCK_ENTITY.get(),
                pos,
                state
        );
    }

    @Nullable
    public UUID cityId() {
        return cityId;
    }

    public void setCityId(@Nullable UUID value) {
        cityId = value;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (cityId != null) {
            tag.putUUID(CITY_TAG, cityId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cityId = tag.hasUUID(CITY_TAG) ? tag.getUUID(CITY_TAG) : null;
    }
}
