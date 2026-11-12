package com.example.examplemod.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Explicit, serializable city identity, independent of colour and translated display text. */
public final class TabCityLabel {
    public static final String KEY = "gui.createcityexpansioncore.tab.city";
    public static final String HEADER = "gui.createcityexpansioncore.tab.header";
    private TabCityLabel() {}
    public record CityLabel(String id, String name, int rgb) {}
    /** Extract only the server-owned account name, excluding the separate city label. */
    public static String playerName(Component display, String fallback) {
        if (display == null) return fallback;
        StringBuilder text = new StringBuilder();
        appendName(display, text);
        String name = text.toString().replaceFirst("\\s+·\\s*$", "").trim();
        return name.matches("[A-Za-z0-9_]{3,16}") && !name.matches("cs[a-z0-9]{14}") ? name : fallback;
    }
    private static boolean appendName(Component component, StringBuilder text) {
        if (component.getContents() instanceof TranslatableContents contents && KEY.equals(contents.getKey())) return false;
        text.append(net.minecraft.network.chat.MutableComponent.create(component.getContents()).getString());
        for (Component sibling : component.getSiblings()) if (!appendName(sibling, text)) return false;
        return true;
    }
    public static Component create(String id, String name, int rgb) {
        return Component.translatableWithFallback(KEY, "%2$s", id, name)
                .withStyle(style -> style.withColor(rgb & 0xFFFFFF));
    }
    public static CityLabel read(Component component) {
        if (component == null) return null;
        if (component.getContents() instanceof TranslatableContents text && KEY.equals(text.getKey())) {
            Object[] args = text.getArgs();
            if (args.length == 2 && args[0] instanceof String id && args[1] instanceof String name) {
                try {
                    java.util.UUID.fromString(id);
                    return new CityLabel(id, name, component.getStyle().getColor() == null
                            ? 0x8C999B : component.getStyle().getColor().getValue());
                } catch (IllegalArgumentException ignored) { return null; }
            }
        }
        for (Component sibling : component.getSiblings()) {
            CityLabel found = read(sibling);
            if (found != null) return found;
        }
        return null;
    }
}
