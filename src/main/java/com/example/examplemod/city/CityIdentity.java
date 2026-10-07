package com.example.examplemod.city;

/** Bounded public identity. Palette index 0 is transparent, 1 follows the city colour. */
public record CityIdentity(String description, String pixels) {
    public static final int SIZE = 32, PIXELS = SIZE * SIZE, MAX_DESCRIPTION = 400;
    private static final int[] INK = {0, 0, 0xE7E8DB, 0x182022, 0xD3A363, 0xD0483C, 0xD0793A,
            0xD6B23C, 0x4FA845, 0x2FA37C, 0x2E9BB5, 0x3A78C4, 0x6B4FB5, 0xC0437E, 0x8C5A3C, 0x5C7C99};

    public CityIdentity {
        if (!valid(description, pixels)) throw new IllegalArgumentException("Invalid public city identity");
    }
    public static boolean valid(String description, String pixels) {
        if (description == null || description.length() > MAX_DESCRIPTION || pixels == null || pixels.length() != PIXELS) return false;
        for (int i = 0; i < description.length(); i++) {
            char c = description.charAt(i);
            if (Character.isISOControl(c) && c != '\n') return false;
            if (c == '\u00a7') return false;
        }
        for (int i = 0; i < pixels.length(); i++) if ("0123456789abcdef".indexOf(pixels.charAt(i)) < 0) return false;
        return true;
    }
    public static CityIdentity restore(String description, String pixels) {
        String fallback = template(0);
        return new CityIdentity(valid(description, fallback) ? description : "", valid("", pixels) ? pixels : fallback);
    }
    public static int colour(int index, int cityColour) { return index == 0 ? 0 : 0xFF000000 | (index == 1 ? cityColour : INK[index]); }
    public static String template(int variant) {
        char[] image = new char[PIXELS]; java.util.Arrays.fill(image, '0');
        for (int y = 3; y < 29; y++) for (int x = 4; x < 28; x++) {
            int inset = Math.max(0, y - 20);
            if (x >= 4 + inset && x < 28 - inset) image[y * SIZE + x] = '1';
        }
        for (int y = 8; y < 23; y++) for (int x = 9; x < 23; x++) {
            boolean ink = switch (Math.floorMod(variant, 3)) {
                case 0 -> x >= 13 && x <= 18 || y >= 16 && x >= 10 && x <= 21 || y == 9 && x % 3 == 1;
                case 1 -> Math.abs(x - 16) + Math.abs(y - 15) < 7 && (x < 14 || x > 17 || y < 13 || y > 17);
                default -> y >= 15 && y <= 21 || x >= 18 && x <= 20 || x >= 11 && x <= 13 && y >= 11;
            };
            if (ink) image[y * SIZE + x] = '2';
        }
        return new String(image);
    }
}
