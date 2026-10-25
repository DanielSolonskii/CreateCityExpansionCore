package com.example.examplemod.knowledge;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Серверная сторона базы знаний.
 *
 * <p>Ответственность: держать в playerdata игрока список доступных файлов и
 * синхронизировать его на клиент. Список хранится в {@code PlayerPersisted}-области
 * — то есть ровно в файле {@code world/playerdata/<uuid>.dat}, переживает смерть
 * (NeoForge переносит эту область в клона при воскрешении) и рестарт.
 *
 * <p>{@code default: visible} выдаётся каждому вошедшему игроку: при входе каталог
 * сканируется, отсутствующие в списке файлы добавляются. Файлы со значением
 * {@code hidden} или без поля никогда не добавляются автоматически, но и не
 * удаляются — если их ранее вписали командой, они остаются до явного
 * {@code remove}.
 */
public final class KnowledgeBaseServer {
    /** Ключ строки знаний внутри PlayerPersisted-области playerdata. */
    public static final String PERSIST_KEY = "CcecKnowledge";
    private static final Logger LOGGER = LogUtils.getLogger();

    private KnowledgeBaseServer() {
    }

    /** Вход игрока: выдаём default: visible и синхронизируем актуальный список. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        ensureDefaultVisibility(player);
        sync(player);
    }

    /**
     * Додаёт каждому входящему игроку файлы с {@code default: visible}.
     * Каталог читается на сервере; если папки нет — прав нет (однократная запись
     * в лог о факте остаётся в логах FML, без спама на каждый вход).
     */
    private static void ensureDefaultVisibility(ServerPlayer player) {
        List<KnowledgeFileMeta> scanned = scan();
        List<String> owned = new ArrayList<>(readList(player));
        boolean changed = false;
        for (KnowledgeFileMeta meta : scanned) {
            if (!meta.visibleByDefault()) continue;
            if (KnowledgeAccess.hasAccess(owned, meta.path())) continue;
            owned.add(meta.path());
            changed = true;
        }
        if (changed) writeList(player, owned);
    }

    /** Отправляет игроку актуальный список. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new com.example.examplemod.network.KnowledgeAccessPayload(
                KnowledgeAccess.join(readList(player))));
    }

    /**
     * Командное изменение: {@code add} / {@code remove}.
     *
     * @return true, если список изменился.
     */
    public static boolean edit(MinecraftServer server, UUID playerId, boolean add, String rawName) {
        if (rawName == null || rawName.isBlank()) return false;
        String name = rawName.replace('\\', '/').trim().replace(';', '-');
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            List<String> list = new ArrayList<>(readList(online));
            boolean changed = apply(list, add, name);
            if (changed) {
                writeList(online, list);
                sync(online);
            }
            return changed;
        }
        return offlineEdit(server, playerId, add, name);
    }

    /**
     * Правка списка сразу в файл {@code world/playerdata/<uuid>.dat} — знание
     * можно выдать и игроку, который сейчас оффлайн.
     */
    private static boolean offlineEdit(MinecraftServer server, UUID playerId, boolean add, String name) {
        Path folder = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Path file = folder.resolve(playerId + ".dat");
        if (!Files.isRegularFile(file)) return false;
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            CompoundTag persistent = root.getCompound(Player.PERSISTED_NBT_TAG);
            List<String> list = new ArrayList<>(KnowledgeAccess.parse(persistent.getString(PERSIST_KEY)));
            boolean changed = apply(list, add, name);
            if (!changed) return false;
            persistent.putString(PERSIST_KEY, KnowledgeAccess.join(list));
            root.put(Player.PERSISTED_NBT_TAG, persistent);
            try (OutputStream out = Files.newOutputStream(file)) {
                NbtIo.writeCompressed(root, out);
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("Не удалось изменить знания оффлайн-игрока {}", playerId, e);
            return false;
        }
    }

    /**
     * Один шаг списка. add добавляет имя; remove удаляет все варианты записи
     * одного и того же файла (путь, путь без .md, короткое имя).
     */
    private static boolean apply(List<String> list, boolean add, String name) {
        if (add) {
            if (list.contains(name)) return false;
            list.add(name);
            return true;
        }
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            String granted = list.get(i);
            if (granted.equalsIgnoreCase(name) || KnowledgeAccess.matches(name, granted)) {
                list.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    /** Читает список знаний игрока. */
    public static List<String> readList(Player player) {
        CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return KnowledgeAccess.parse(persistent.getString(PERSIST_KEY));
    }

    /** Записывает список знаний игроку. */
    public static void writeList(Player player, List<String> names) {
        var data = player.getPersistentData();
        CompoundTag persistent = data.contains(Player.PERSISTED_NBT_TAG)
                ? data.getCompound(Player.PERSISTED_NBT_TAG)
                : new CompoundTag();
        persistent.putString(PERSIST_KEY, KnowledgeAccess.join(names));
        data.put(Player.PERSISTED_NBT_TAG, persistent);
    }

    /**
     * Скан каталога с разбором передней части. Один проход на вход игрока: папка
     * маленькая, а данные нужны осмысленные (default, parent_item).
     */
    private static List<KnowledgeFileMeta> scan() {
        List<KnowledgeFileMeta> result = new ArrayList<>();
        KnowledgeStorage.forEachMarkdown(path ->
                result.add(KnowledgeFileMeta.parse(path.rel(), KnowledgeStorage.firstLines(path.file(), 8))));
        return result;
    }
}
