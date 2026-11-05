package com.example.examplemod.block;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.TransportGrantStatus;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
 * (участники города, союзы и доверенные города). Поверх базовых прав
 * канонических грантов работает пульт управления доступом: точечное
 * разрешение по дельте блока относительно терминала регистрации
 * ({@code CityManager#allowsTransportAccess}) открывает взаимодействие
 * без права менять блоки конструкции.
 */
public final class TransportSubLevelProtection {

    private enum LookupState { FOUND, UNREGISTERED, UNRESOLVED }

    /** Missing plot data is not proof that a vessel is unregistered. */
    private record TerminalLookup(LookupState state, @Nullable RegisteredTerminal terminal) {
        private static TerminalLookup found(RegisteredTerminal terminal) {
            return new TerminalLookup(LookupState.FOUND, terminal);
        }
    }

    /** Найденный сканом терминал: город, токен пульта, позиция в координатах plot'а. */
    private record RegisteredTerminal(
            UUID cityId,
            @Nullable UUID token,
            BlockPos terminalPos,
            @Nullable TransportRegistrationTerminalBlockEntity blockEntity
    ) {
    }

    public record OutlineTerminal(UUID cityId, UUID token, BlockPos pos) {}

    public static OutlineTerminal outlineTerminal(ServerSubLevel sublevel, ServerLevel level) {
        var terminal = registeredTerminal(sublevel, level).terminal();
        return terminal == null || terminal.token() == null ? null
                : new OutlineTerminal(terminal.cityId(), terminal.token(), terminal.terminalPos());
    }

    public static void invalidateTerminalCache(ServerLevel level, BlockPos pos) {
        var sublevel = Sable.HELPER.getContaining(level, new ChunkPos(pos));
        if (sublevel instanceof ServerSubLevel server) {
            // These notifications can precede a cancelled edit. A subsequent lookup
            // always reads live plot BEs again, so it can rebuild the binding safely.
            server.setUserDataTag(TransportTerminalBinding.clear(server.getUserDataTag()));
        }
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
        TerminalLookup lookup = registeredTerminal(sublevel, level);
        if (lookup.state() == LookupState.UNRESOLVED) return denyLoading(player);
        if (lookup.state() == LookupState.UNREGISTERED) return true;
        City city = CityManager.get(level.getServer().overworld()).getCity(lookup.terminal().cityId());
        if (city == null) return true;
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
        TerminalLookup lookup = registeredTerminal(serverSubLevel, level);
        if (lookup.state() == LookupState.UNRESOLVED) return denyLoading(player);
        if (lookup.state() == LookupState.UNREGISTERED) return true;
        City city = CityManager.get(level.getServer().overworld()).getCity(lookup.terminal().cityId());
        if (city == null) return true;
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
            // It is still a physical vessel; the permission check must fail closed.
            return serverSubLevel;
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
        RegisteredTerminal terminal = registeredTerminal(sublevel, level).terminal();
        if (terminal == null) {
            return null;
        }
        return CityManager.get(level.getServer().overworld()).getCity(terminal.cityId());
    }

    /** Read only the plot's own chunks. Never enter the vanilla chunk pipeline. */
    private static TerminalLookup registeredTerminal(ServerSubLevel sublevel, ServerLevel level) {
        CityManager cities = CityManager.get(level.getServer().overworld());
        TerminalLookup lookup = scanForTerminal(sublevel, cities);
        if (lookup.state() == LookupState.FOUND && lookup.terminal().blockEntity() != null) {
            RegisteredTerminal terminal = lookup.terminal();
            var binding = new TransportTerminalBinding(sublevel.getUniqueId(), terminal.cityId(),
                    terminal.token(), terminal.terminalPos().subtract(sublevel.getPlot().getCenterBlock()));
            sublevel.setUserDataTag(binding.store(sublevel.getUserDataTag()));
        } else if (lookup.state() == LookupState.UNREGISTERED) {
            sublevel.setUserDataTag(TransportTerminalBinding.clear(sublevel.getUserDataTag()));
        }
        return lookup;
    }

    private static boolean denyLoading(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.createcityexpansioncore.transport_protection.loading"), true);
        return false;
    }

    /**
     * Может ли игрок взаимодействовать (ПКМ) с блоком по мировым
     * координатам, если этот блок принадлежит sublevel'у.
     *
     * <p>Отличие от {@link #canModifyBlockAt}: ломать и ставить
     * участник-проверка требует права строительства, а взаимодействие
     * пропускается и по точечному гранту пульта. Гайковёрт в этот
     * путь не должен попадать — вызывающий (TransportProtectionEvents)
     * направляет его на строгую проверку.
     */
    public static boolean canInteractWithBlockAt(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        ServerSubLevel sublevel = containingSubLevel(level, pos);
        if (sublevel == null) {
            return true;
        }
        CityManager cityManager = CityManager.get(level.getServer().overworld());
        TerminalLookup lookup = registeredTerminal(sublevel, level);
        if (lookup.state() == LookupState.UNRESOLVED) return denyLoading(player);
        RegisteredTerminal terminal = lookup.terminal();
        if (terminal == null) return true;
        City city = cityManager.getCity(terminal.cityId());
        if (city == null) {
            return true;
        }
        if (cityManager.allowsTerritoryAction(city, player.getUUID(), false)) {
            return true;
        }
        UUID token = terminal.token();
        if (token == null) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(pos).getBlock());
        return cityManager.allowsTransportAccess(
                city, token, pos.subtract(terminal.terminalPos()), blockId);
    }

    /**
     * Переключает грант пульта у блока sublevel'а (пульт-тумблер).
     *
     * <p>Игрок должен быть участником города конструкции. Для
     * терминалов старых миров токен генерируется здесь и пишется в
     * NBT живого BlockEntity — следующий поиск сохраняет привязку
     * уже с токеном. Блок вне sublevel'а даёт NOT_PHYSICAL: caller
     * уходит на статический путь участков, чужая конструкция без
     * терминала — UNREGISTERED.
     */
    public static TransportGrantStatus toggleBlockGrantAt(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        ServerSubLevel sublevel = containingSubLevel(level, pos);
        if (sublevel == null) {
            return TransportGrantStatus.NOT_PHYSICAL;
        }
        CityManager cityManager = CityManager.get(level.getServer().overworld());
        TerminalLookup lookup = registeredTerminal(sublevel, level);
        if (lookup.state() == LookupState.UNRESOLVED) return TransportGrantStatus.UNRESOLVED;
        RegisteredTerminal terminal = lookup.terminal();
        if (terminal == null) return TransportGrantStatus.UNREGISTERED;
        City city = cityManager.getCity(terminal.cityId());
        if (city == null) {
            return TransportGrantStatus.UNREGISTERED;
        }
        if (!city.hasMember(player.getUUID())) {
            return TransportGrantStatus.NOT_MEMBER;
        }
        if (terminal.token() == null && terminal.blockEntity() == null) {
            return TransportGrantStatus.UNRESOLVED;
        }
        UUID token = terminal.token() != null
                ? terminal.token()
                : terminal.blockEntity().ensureGrantToken();
        if (terminal.token() == null) registeredTerminal(sublevel, level);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(pos).getBlock());
        boolean granted = cityManager.toggleTransportAccess(
                city, token, pos.subtract(terminal.terminalPos()), blockId);
        return granted ? TransportGrantStatus.GRANTED : TransportGrantStatus.REVOKED;
    }

    /** Committed edits only; terminal discovery never loads or generates chunks. */
    public static void onBlockChanged(ServerLevel level, BlockPos pos, ResourceLocation previous,
                                      ResourceLocation next, CityManager cities) {
        if (!cities.hasTransportAccessGrants()) return;
        ServerSubLevel sublevel = containingSubLevel(level, pos);
        if (sublevel == null) return;
        RegisteredTerminal terminal = registeredTerminal(sublevel, level).terminal();
        if (terminal == null || terminal.token() == null) return;
        cities.onTransportBlockChanged(cities.getCity(terminal.cityId()), terminal.token(),
                pos.subtract(terminal.terminalPos()), previous, next);
    }

    private static TerminalLookup scanForTerminal(ServerSubLevel sublevel, CityManager cities) {
        LevelPlot plot = sublevel.getPlot();
        if (plot == null) return new TerminalLookup(LookupState.UNRESOLVED, null);

        // Sable serializes userDataTag with the vessel. Positions are plot-relative,
        // and the UUID prevents a copied/split vessel inheriting someone else's binding.
        var binding = TransportTerminalBinding.read(sublevel.getUserDataTag(), sublevel.getUniqueId());
        if (binding != null && cities.getCity(binding.cityId()) != null) {
            BlockPos pos = binding.localPos().offset(plot.getCenterBlock());
            var holder = plot.getChunkHolder(plot.toLocal(new ChunkPos(pos)));
            LevelChunk chunk = holder == null ? null : holder.getChunk();
            if (chunk == null || (chunk.getBlockEntitiesPos().contains(pos)
                    && !chunk.getBlockEntities().containsKey(pos))) {
                // Keep known protection while the terminal chunk / its BE is unavailable.
                return TerminalLookup.found(new RegisteredTerminal(
                        binding.cityId(), binding.token(), pos, null));
            }
            RegisteredTerminal live = terminalOf(chunk.getBlockEntities().get(pos),
                    com.example.examplemod.CreateCityExpansionCore.TRANSPORT_REGISTRATION_TERMINAL.get(), cities);
            if (live != null) return TerminalLookup.found(live);
            // The terminal chunk is present and its old terminal is gone/changed.
            // Do not trust a positive cache forever after committed removal.
        }

        RegisteredTerminal discovered = scanLoadedTerminals(sublevel, cities);
        if (discovered != null) return TerminalLookup.found(discovered);

        // Sable loads a plot synchronously. Do not declare it unregistered while its
        // bounds, holders or block entities are still being initialized. Empty slots
        // in a sparse plot are legitimate: never probe them via Level.getChunk().
        var holders = plot.getLoadedChunks();
        boolean complete = plot.getBoundingBox() != null && holders != null && !holders.isEmpty();
        if (holders != null) {
            for (var holder : holders) {
                LevelChunk chunk = holder == null ? null : holder.getChunk();
                if (chunk == null || !chunk.getBlockEntities().keySet().containsAll(chunk.getBlockEntitiesPos())) {
                    complete = false;
                }
            }
        }
        return new TerminalLookup(complete ? LookupState.UNREGISTERED : LookupState.UNRESOLVED, null);
    }

    /** Loaded block entities are enough for lifecycle cleanup, including after a server restart. */
    @Nullable
    private static RegisteredTerminal scanLoadedTerminals(ServerSubLevel sublevel, CityManager cityManager) {
        LevelPlot plot = sublevel.getPlot();
        if (plot == null) return null;
        Block terminal = com.example.examplemod.CreateCityExpansionCore.TRANSPORT_REGISTRATION_TERMINAL.get();
        var holders = plot.getLoadedChunks();
        if (holders == null) return null;
        for (var holder : holders) {
            var chunk = holder == null ? null : holder.getChunk();
            if (chunk == null) continue;
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                RegisteredTerminal found = terminalOf(entity, terminal, cityManager);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Nullable
    private static RegisteredTerminal terminalOf(
            BlockEntity blockEntity,
            Block terminal,
            CityManager cityManager
    ) {
        if (blockEntity == null || blockEntity.isRemoved()
                || blockEntity.getBlockState().getBlock() != terminal
                || !(blockEntity instanceof TransportRegistrationTerminalBlockEntity terminalBe)) {
            return null;
        }
        UUID cityId = terminalBe.cityId();
        if (cityId == null || cityManager.getCity(cityId) == null) {
            return null;
        }
        return new RegisteredTerminal(cityId, terminalBe.grantToken(), blockEntity.getBlockPos(), terminalBe);
    }
}
