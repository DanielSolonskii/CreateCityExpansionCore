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
 * постановки блока, и стабильный токен грантов пульта
 * ({@code GrantToken}) — ключ всех точечных разрешений для
 * конструкции, содержащей этот терминал. Блок без привязки к городу
 * юридически установлен, но функционала не имеет — это фиксируется
 * сообщением игроку при установке.
 *
 * <p>Города хранятся в CityManager поверх overworld SavedData,
 * поэтому достаточно идентификатора города: он валиден в любом
 * измерении и переживает выгрузку чанка.
 */
public final class TransportRegistrationTerminalBlockEntity extends BlockEntity {

    /** NBT-ключ города. Используется и при чтении из NBT contraption'ов (см. TransportProtection). */
    public static final String CITY_TAG = "City";

    /** NBT-ключ токена грантов пульта. Создаётся при постановке блока, переживает сборку конструкции. */
    public static final String GRANT_TOKEN_TAG = "GrantToken";

    /** Город, участником которого был ставящий игрок (null — терминал вне города). */
    @Nullable
    private UUID cityId;

    /** Стабильный ключ грантов пульта для всей конструкции, где стоит этот терминал. */
    @Nullable
    private UUID grantToken;

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

    /** Токен грантов пульта этой конструкции или {@code null} у терминалов старых миров. */
    @Nullable
    public UUID grantToken() {
        return grantToken;
    }

    /**
     * Возвращает токен грантов, генерируя его при первом обращении.
     *
     * <p>Генерация сразу пишется в NBT: Create переносит NBT блока
     * при сборке, а Sable переносит BlockEntity как есть — поэтому
     * токен единообразно переживает и полёт, и пересборку конструкции.
     * Вызывается из постановки блока и из ветки переключения пульта
     * (только там, где игрок уже доказал права); сканы защиты токен
     * только читают и мутацию BE не производят.
     */
    public UUID ensureGrantToken() {
        if (grantToken == null) {
            grantToken = UUID.randomUUID();
            setChanged();
        }
        return grantToken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (cityId != null) {
            tag.putUUID(CITY_TAG, cityId);
        }
        if (grantToken != null) {
            tag.putUUID(GRANT_TOKEN_TAG, grantToken);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cityId = tag.hasUUID(CITY_TAG) ? tag.getUUID(CITY_TAG) : null;
        grantToken = tag.hasUUID(GRANT_TOKEN_TAG) ? tag.getUUID(GRANT_TOKEN_TAG) : null;
    }
}
