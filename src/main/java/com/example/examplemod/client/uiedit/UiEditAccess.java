package com.example.examplemod.client.uiedit;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Доступ к редактору UI: только игроки из allowlist-файла.
 *
 * <p><b>Файл:</b> {@code config/createcityexpansioncore/ui-editor-allow.txt} —
 * по одному нику или UUID в строке, регистр не важен, {@code #} — комментарий.
 * Файл создаётся автоматически с инструкцией и ником владельца; дополнить его
 * можно без пересборки — содержимое перечитывается по времени изменения.
 *
 * <p><b>Что это обеспечивает и чего не обеспечивает.</b> Редактор — чисто
 * клиентский инструмент: он двигает и подписывает только ваш экран, серверу
 * не отправляется ничего. Allowlist делает инструмент «только для нас двоих»
 * по умолчанию, но это удобство, а не защита: игрок, собравший jar сам,
 * может выключить проверку. Серверная защита не нужна ровно потому, что
 * менять игру для других через этот путь нельзя — как с клавишей карты:
 * скрытие действия не заменяет проверку прав, но здесь и проверять нечего.
 */
public final class UiEditAccess {

    private static Set<String> cached;
    private static long cachedMtime = -1L;
    private static Path cachedPath;

    private UiEditAccess() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get()
                .resolve("createcityexpansioncore")
                .resolve("ui-editor-allow.txt");
    }

    /** Разбор строк allowlist: комментарии и пустые строки пропускаются. */
    public static Set<String> parse(List<String> lines) {
        Set<String> out = new HashSet<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.replace("\ufeff", "").trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            out.add(line.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public static boolean allows(Set<String> entries, String name, String uuid) {
        if (entries.isEmpty()) {
            return false;
        }
        if (name != null && entries.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return uuid != null && entries.contains(uuid.toLowerCase(Locale.ROOT));
    }

    /** Содержимое вновь созданного файла: инструкция на русском + ник владельца. */
    public static String defaultFileText() {
        return """
                # Редактор UI (клавиша F7) — список разрешённых игроков.
                # По одному нику или UUID в строке, регистр не важен.
                # Строки, начинающиеся с #, — комментарии.
                # Файл создаётся автоматически; отредактируйте его и перезайдите в мир.

                goddzerzh
                # главный разработчик — впишите его игровой ник ниже
                """;
    }

    /** Гарантирует наличие файла: при отсутствии создаёт с инструкцией. */
    public static Path ensureFile() {
        Path file = file();
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaultFileText(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // Нет доступа к диску — доступ просто не откроется; это видно по отказу F7.
        }
        return file;
    }

    private static Set<String> entries() {
        Path file = ensureFile();
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (cached != null && file.equals(cachedPath) && mtime == cachedMtime) {
                return cached;
            }
            cached = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
            cachedPath = file;
            cachedMtime = mtime;
        } catch (IOException e) {
            cached = Set.of();
        }
        return cached;
    }

    /** Разрешено ли редактирование текущему игроку. */
    public static boolean isAllowed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        String name = minecraft.player.getName().getString();
        String uuid = minecraft.player.getStringUUID();
        return allows(entries(), name, uuid);
    }
}
