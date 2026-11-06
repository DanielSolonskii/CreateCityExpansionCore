package com.example.examplemod.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Explicit, serializable city identity, independent of colour and translated display text. */
public final class TabCityLabel {
    public static final String KEY = "gui.createcityexpansioncore.tab.city";
    public static final String HEADER = "gui.createcityexpansioncore.tab.header";
    private TabCityLabel() {}
    public record CityLabel(String id, String name, int rgb) {}
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
