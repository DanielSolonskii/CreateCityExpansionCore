package com.example.examplemod.knowledge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Кэш PNG-картинок из базы знаний, зарегистрированных как динамические текстуры.
 *
 * <p>Файлы лежат локально рядом с .md документом, поэтому загрузка допустима на
 * клиенте без сети: NativeImage читает файл, текстура получает стабильный
 * ResourceLocation вида {@code ccec_knowledge_<n>}. Неудача загрузки фиксируется,
 * чтобы не читать сломанный файл каждый кадр.
 */
public final class KnowledgeImages {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ResourceLocation> LOADED = new HashMap<>();
    private static final Map<String, int[]> SIZES = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static int counter;

    private KnowledgeImages() {
    }

    /**
     * Загружает картинку один раз. Возвращает текстуру и размеры через
     * {@code size[0]} / {@code size[1]}; null — картинка не читается.
     */
    public static ResourceLocation request(Path file, int[] size) {
        if (file == null || !Files.isRegularFile(file)) return null;
        String key = file.toString();
        ResourceLocation loaded = LOADED.get(key);
        if (loaded != null) {
            if (size != null) {
                int[] dims = SIZES.get(key);
                if (dims != null) System.arraycopy(dims, 0, size, 0, Math.min(2, dims.length));
            }
            return loaded;
        }
        if (FAILED.contains(key)) return null;
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    com.example.examplemod.CreateCityExpansionCore.MODID,
                    "ccec_knowledge_" + (++counter));
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
            if (size != null && size.length >= 2) {
                size[0] = image.getWidth();
                size[1] = image.getHeight();
            }
            SIZES.put(key, new int[]{image.getWidth(), image.getHeight()});
            LOADED.put(key, location);
            // image не закрываем: DynamicTexture забирает владение и закроет при release.
            return location;
        } catch (Exception e) {
            FAILED.add(key);
            LOGGER.warn("Не удалось загрузить картинку {}", file, e);
            return null;
        }
    }

    /** Имя файла картинки без расширения — для alt-подписи. */
    public static String name(String file) {
        String raw = file == null ? "" : file;
        int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        String base = slash >= 0 ? raw.substring(slash + 1) : raw;
        int dot = base.lastIndexOf('.');
        return (dot > 0 ? base.substring(0, dot) : base).toLowerCase(Locale.ROOT);
    }
}
