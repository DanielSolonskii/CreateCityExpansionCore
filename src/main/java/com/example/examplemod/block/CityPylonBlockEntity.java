package com.example.examplemod.block;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity для City Pylon.
 *
 * <p>Существует для двух вещей:
 * <ol>
 *   <li>чтобы vanilla BlockEntityRenderer мог отрисовать восемь
 *       анимируемых частей модели;</li>
 *   <li>чтобы держать один синхронизируемый флаг активности пилона:
 *       CityManager живёт на сервере и клиенту недоступен.</li>
 * </ol>
 *
 * <p>Игровая логика (владелец города, приглашения, проекты, идентичность
 * города) остаётся в CityManager / City и в NBT предмета CityPylonItem.
 * Здесь хранится только визуальное состояние.
 *
 * <p>Блок раньше не имел BlockEntity, поэтому сохранённых данных здесь нет
 * и миграция существующих миров ничего не теряет: при загрузке чанка
 * Minecraft создаёт пустой BlockEntity, а флаг вычисляется в onLoad.
 */
public class CityPylonBlockEntity extends BlockEntity {

    private static final String KEY_ACTIVE = "Active";

    /**
     * True, если за пилоном стоит город и пилон активен.
     * Управляет зелёной лампой, экраном и верхним индикатором.
     */
    private boolean active;

    public CityPylonBlockEntity(BlockPos pos, BlockState state) {
        super(
                CreateCityExpansionCore.CITY_PYLON_BLOCK_ENTITY.get(),
                pos,
                state
        );
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Меняет флаг и, если он действительно изменился, отправляет
     * стандартный BlockEntity-апдейт клиенту.
     */
    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    getBlockPos(),
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_ALL
            );
        }
    }

    /**
     * Вызывается, когда BlockEntity привязывается к уровню: при загрузке
     * чанка и при размещении блока. Это единственный серверный момент,
     * где можно прочитать CityManager для пилонов, установленных до
     * появления BlockEntity.
     *
     * <p>Два важных ограничения:
     *
     * <ol>
     *   <li>Нельзя обращаться к {@code level.getBlockEntity(pos)}.
     *       Вызов происходит изнутри
     *       {@code LevelChunk#addAndRegisterBlockEntity -> setBlockEntity},
     *       то есть ДО записи этого BlockEntity в чанк. Такой lookup
     *       вызвал бы повторную регистрацию и мгновенное переполнение
     *       стека. Флаг вычисляется напрямую и присваивается полем,
     *       без setChanged/sendBlockUpdated: уведомление на этом этапе
     *       снова вошло бы в регистрацию.</li>
     *   <li>На клиенте ничего не считать. При загрузке чанка сначала
     *       выполняется {@code loadAdditional} (читает синхронизированное
     *       значение), и только потом {@code setLevel}. Если бы здесь
     *       считалось заново, серверное значение затёрлось бы на
     *       {@code false}.</li>
     * </ol>
     */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide) {
            this.active = CityPylonBlock.computeActive(level, getBlockPos());
        }
    }

    /*
     * =========================================
     * СОХРАНЕНИЕ И СИНХРОНИЗАЦИЯ
     * =========================================
     *
     * Штатный vanilla-механизм BlockEntity. Отдельных сетевых
     * payload'ов мода не вводится.
     */

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        active = tag.getBoolean(KEY_ACTIVE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(KEY_ACTIVE, active);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_ACTIVE, active);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /*
     * =========================================
     * КЛИЕНТСКАЯ АНИМАЦИЯ (НЕ СОХРАНЯЕТСЯ)
     * =========================================
     *
     * Накопительные фазы нужны, чтобы менять скорость механизма
     * (например, ускорить при открытом GUI) без скачка угла.
     *
     * Поля transient: в NBT не попадают, состояние не протекает на диск.
     * BlockEntityRenderer существует только на клиенте.
     */

    private transient double ringPhase;
    private transient double gearPhase;
    private transient float openBlend;

    public double getRingPhase() {
        return ringPhase;
    }

    public void advanceRingPhase(double degrees) {
        ringPhase = normalize(ringPhase + degrees);
    }

    public double getGearPhase() {
        return gearPhase;
    }

    public void advanceGearPhase(double degrees) {
        gearPhase = normalize(gearPhase + degrees);
    }

    /**
     * @return сглаженное состояние «GUI открыт», 0..1
     */
    public float getOpenBlend() {
        return openBlend;
    }

    public void setOpenBlend(float value) {
        this.openBlend = value;
    }

    private static double normalize(double degrees) {
        double value = degrees % 360.0D;
        return value < 0.0D ? value + 360.0D : value;
    }
}
