package com.example.examplemod.block;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Защита физических конструкций (sublevel'ов Create Simulated /
 * Aeronautics).
 *
 * <p>Sable подмешивает plot-чанки sublevel'ов в конвейер чанков
 * серверного уровня, поэтому блоки летящей конструкции доступны
 * по обычным мировым координатам и vanilla-события (разрушение,
 * использование, установка) на них срабатывают. Точка применения
 * прав — принадлежность координаты sublevel'у с зарегистрированным
 * терминалом. Активный sublevel резолвится тем же вызовом, что и
 * внутренний assembly preventer Simulated —
 * {@code Sable.HELPER.getContaining}.
 *
 * <p>ВАЖНО: класс трогает типы Sable/Simulated, поэтому имеет право
 * использоваться только при установленном Simulated. Вход —
 * {@link com.example.examplemod.block.SimulatedCompat} (чистая
 * обёртка) и guarded mixin {@code mixin.simulated.AssemblePacketMixin};
 * из общего всегда-загружаемого кода на sable-типы ссылаться нельзя.
 *
 * <p>Права — те же, что и везде: {@link CityManager#allowsTerritoryAction}
 * (участники города, союзы и доверенные города).
 */
public final class TransportSubLevelProtection {

    /**
     * Разумный предел на объём сканируемого региона: это защита от
     * вечного цикла на вырожденном bounding box, а не оптимизация.
     */
    private static final int MAX_SCAN_VOLUME = 2_000_000;

    /** Сколько тиков живёт кэшированный отрицательный результат. */
    private static final long NEGATIVE_TTL_TICKS = 100;

    /**
     * Кэш «sublevel -> город». Привязка не меняется в полёте, а
     * sublevel пересоздаётся при пересборке — WeakHashMap чистится
     * сам. Отрицательный результат живёт ограниченное время: сразу
     * после сборки BlockEntity могут быть ещё не зарегистрированы в
     * plot-чанках, и закэшированный «пусто» навсегда отключил бы
     * защиту (баг первой версии).
     */
    private static final Map<ServerSubLevel, CachedCity> CITY_CACHE = new WeakHashMap<>();

    private record CachedCity(@Nullable UUID cityId, long cachedAt) {
    }

    private TransportSubLevelProtection() {
    }

    /**
     * Может ли игрок изменить/использовать блок по мировым
     * координатам, если этот блок принадлежит sublevel'у.
     *
     * <p>Точность блочная: чанк, пересекающийся с plot'ом, может
     * содержать и обычные блоки мира (территория вокруг стапеля),
     * их трогать нельзя по чужому городу.
     */
    public static boolean canModifyBlockAt(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        ServerSubLevel sublevel = containingSubLevel(level, pos);
        if (sublevel == null) {
            return true;
        }
        City city = registeredCity(sublevel, level);
        if (city == null) {
            return true;
        }
        boolean allowed = CityManager.get(level.getServer().overworld())
                .allowsTerritoryAction(city, player.getUUID(), true);
        return allowed;
    }

    /**
     * Может ли игрок собрать/разобрать конструкцию, собранную на
     * physics assembler в указанной позиции (путь пакета рычага).
     */
    public static boolean canModifySubLevelAt(
            ServerPlayer player,
            ServerLevel level,
            BlockPos assemblerPos
    ) {
        SubLevel sublevel = Sable.HELPER.getContaining(level, assemblerPos);
        if (!(sublevel instanceof ServerSubLevel serverSubLevel)) {
            // Сборка новой конструкции — защищать нечего.
            return true;
        }
        City city = registeredCity(serverSubLevel, level);
        if (city == null) {
            return true;
        }
        return CityManager.get(level.getServer().overworld())
                .allowsTerritoryAction(city, player.getUUID(), true);
    }

    /** Sublevel, чей plot содержит указанные координаты, или null. */
    @Nullable
    private static ServerSubLevel containingSubLevel(ServerLevel level, BlockPos pos) {
        SubLevel sublevel = Sable.HELPER.getContaining(level, new ChunkPos(pos));
        if (!(sublevel instanceof ServerSubLevel serverSubLevel)) {
            return null;
        }
        LevelPlot plot = serverSubLevel.getPlot();
        if (plot == null) {
            return null;
        }
        BoundingBox3ic bounds = plot.getBoundingBox();
        if (bounds != null && !bounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
            // Координата в чанке sublevel'а, но вне его блоков — это
            // обычный блок мира.
            return null;
        }
        return serverSubLevel;
    }

    /**
     * Город, за которым закреплён sublevel, или null.
     *
     * <p>Терминал ищется среди BlockEntity чанков plot'а: координаты
     * блоков в plot'е совпадают с исходными мировыми, а сборка
     * переносит BlockEntity как есть — идентификатор города
     * сохраняется в NBT.
     */
    @Nullable
    public static City registeredCity(ServerSubLevel sublevel, ServerLevel level) {
        CityManager cityManager = CityManager.get(level.getServer().overworld());
        CachedCity cached = CITY_CACHE.get(sublevel);
        long now = level.getGameTime();
        if (cached != null) {
            if (cached.cityId() != null) {
                return cityManager.getCity(cached.cityId());
            }
            if (now - cached.cachedAt() < NEGATIVE_TTL_TICKS) {
                return null;
            }
        }

        UUID found = scanForCity(sublevel, cityManager);
        CITY_CACHE.put(sublevel, new CachedCity(found, now));
        return found == null ? null : cityManager.getCity(found);
    }

    @Nullable
    private static UUID scanForCity(ServerSubLevel sublevel, CityManager cityManager) {
        LevelPlot plot = sublevel.getPlot();
        if (plot == null) {
            return null;
        }

        Block terminal = com.example.examplemod.CreateCityExpansionCore.TRANSPORT_REGISTRATION_TERMINAL.get();

        // Первичный источник: BE-карты самих plot-чанков. Координатная
        // арифметика не нужна вообще — plot хранит чанки в собственной
        // локальной системе (toLocal/toGlobal), а bbox может быть ещё
        // не посчитан.
        java.util.Collection<dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder> holders = plot.getLoadedChunks();
        if (holders != null) {
            for (dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder holder : holders) {
                net.minecraft.world.level.chunk.LevelChunk chunk = holder == null ? null : holder.getChunk();
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.getBlockState().getBlock() != terminal
                            || !(blockEntity instanceof TransportRegistrationTerminalBlockEntity terminalBe)) {
                        continue;
                    }
                    UUID cityId = terminalBe.cityId();
                    if (cityId != null && cityManager.getCity(cityId) != null) {
                        return cityId;
                    }
                }
            }
        }

        // Запасной путь: поблочный скан через embedded accessor в
        // пределах bbox plot'а (если он посчитан).
        EmbeddedPlotLevelAccessor accessor = plot.getEmbeddedLevelAccessor();
        BoundingBox3ic bounds = plot.getBoundingBox();
        if (accessor != null && bounds != null && bounds.volume() > 0 && bounds.volume() <= MAX_SCAN_VOLUME) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        cursor.set(x, y, z);
                        BlockEntity blockEntity = accessor.getBlockEntity(cursor);
                        if (blockEntity == null
                                || blockEntity.getBlockState().getBlock() != terminal
                                || !(blockEntity instanceof TransportRegistrationTerminalBlockEntity terminalBe)) {
                            continue;
                        }
                        UUID cityId = terminalBe.cityId();
                        if (cityId != null && cityManager.getCity(cityId) != null) {
                            return cityId;
                        }
                    }
                }
            }
        }

        return null;
    }}
