package com.example.examplemod.geology;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Renders the same polygons queried by the mine and magnetic analyzer. */
final class VeinMapImages {
    private static final int MIN_BLOCK = -2_048;
    private static final int MAX_BLOCK = 2_048;
    private static final int BLOCKS_PER_PIXEL = 4;
    private static final int MAP_SIZE = (MAX_BLOCK - MIN_BLOCK) / BLOCKS_PER_PIXEL;
    private static final Color BACKGROUND = new Color(0x272C33);
    private static final Map<String, Color> COLORS = Map.of(
            "iron", new Color(0x62C2B1),
            "coal", new Color(0x6F809B),
            "limestone", new Color(0xDBD1A8),
            "copper", new Color(0xD78960),
            "zinc", new Color(0xB8A0CC)
    );

    private VeinMapImages() {}

    static void write(Path directory, long seed, double coverage, List<GeologicalDeposit> deposits)
            throws IOException {
        Files.createDirectories(directory);
        BufferedImage map = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D base = map.createGraphics();
        base.setColor(BACKGROUND);
        base.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
        base.dispose();

        for (GeologicalDeposit deposit : deposits) {
            VeinFootprint footprint = deposit.getShape().footprint();
            int extent = VeinFootprint.SIDE * VeinFootprint.STEP;
            int color = COLORS.get(deposit.getResourceId().getPath()).getRGB();
            int fromX = Math.max(MIN_BLOCK, footprint.originX());
            int fromZ = Math.max(MIN_BLOCK, footprint.originZ());
            int toX = Math.min(MAX_BLOCK, footprint.originX() + extent);
            int toZ = Math.min(MAX_BLOCK, footprint.originZ() + extent);
            for (int z = fromZ; z < toZ; z += BLOCKS_PER_PIXEL) {
                for (int x = fromX; x < toX; x += BLOCKS_PER_PIXEL) {
                    if (footprint.contains(x + 2, z + 2)) {
                        map.setRGB((x - MIN_BLOCK) / BLOCKS_PER_PIXEL,
                                (z - MIN_BLOCK) / BLOCKS_PER_PIXEL, color);
                    }
                }
            }
        }

        int padding = 24;
        int header = 80;
        BufferedImage page = new BufferedImage(MAP_SIZE + 2 * padding, MAP_SIZE + header + 64,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = page.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(0x171B20));
        graphics.fillRect(0, 0, page.getWidth(), page.getHeight());
        graphics.setColor(new Color(0xEEF2F3));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        graphics.drawString("Карта жил · сид " + seed, padding, 34);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        graphics.drawString(String.format(Locale.forLanguageTag("ru-RU"),
                "4096 × 4096 блоков вокруг (0, 0) · покрытие на проверенной территории %.1f%%",
                coverage * 100.0), padding, 60);
        graphics.drawImage(map, padding, header, null);

        int legendY = header + MAP_SIZE + 27;
        swatch(graphics, 24, legendY, "Железо", COLORS.get("iron"));
        swatch(graphics, 173, legendY, "Уголь", COLORS.get("coal"));
        swatch(graphics, 322, legendY, "Известняк", COLORS.get("limestone"));
        swatch(graphics, 493, legendY, "Медь", COLORS.get("copper"));
        swatch(graphics, 629, legendY, "Цинк", COLORS.get("zinc"));
        swatch(graphics, 765, legendY, "Без жил", BACKGROUND);
        int scaleX = 913;
        graphics.setColor(new Color(0xEEF2F3));
        graphics.fillRect(scaleX, legendY - 3, 512 / BLOCKS_PER_PIXEL, 2);
        graphics.drawString("512 блоков", scaleX, legendY + 17);
        graphics.dispose();
        ImageIO.write(page, "png", directory.resolve("veins-seed-" + seed + ".png").toFile());
    }

    private static void swatch(Graphics2D graphics, int x, int y, String label, Color color) {
        graphics.setColor(color);
        graphics.fillRect(x, y - 12, 15, 15);
        graphics.setColor(new Color(0xEEF2F3));
        graphics.drawString(label, x + 23, y);
    }
}
