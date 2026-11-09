package com.example.examplemod.devtools;

import com.google.gson.*;
import java.util.*;

/** Lossless fluid entries draft for Create processing recipes. Entries keep unrelated fields untouched. */
public final class FluidRecipeDraft {
    /** Recipe types that accept fluid ingredients. */
    public static boolean supportsInput(String type) {
        return Set.of("create:mixing", "create:compacting", "create:filling").contains(type);
    }

    /** Recipe types that can output fluids (target beside item results). */
    public static boolean supportsResult(String type) {
        return Set.of("create:mixing", "create:emptying").contains(type);
    }

    public record Entry(JsonObject json, boolean result) {}

    private final JsonObject recipe;
    private final List<Entry> entries = new ArrayList<>();

    public FluidRecipeDraft(JsonObject source) {
        recipe = source.deepCopy();
        scan();
    }

    private void scan() {
        entries.clear();
        if (recipe.has("ingredients") && recipe.get("ingredients").isJsonArray())
            for (var element : recipe.getAsJsonArray("ingredients")) {
                if (!element.isJsonObject()) continue;
                var obj = element.getAsJsonObject();
                if (obj.has("fluid") || (obj.has("tag") && obj.has("amount"))) entries.add(new Entry(obj, false));
            }
        if (recipe.has("results") && recipe.get("results").isJsonArray())
            for (var element : recipe.getAsJsonArray("results")) {
                if (!element.isJsonObject()) continue;
                var obj = element.getAsJsonObject();
                if (obj.has("id") && obj.has("amount") && !obj.has("item")) entries.add(new Entry(obj, true));
            }
    }

    public JsonObject recipe() {
        return recipe;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean input() {
        return supportsInput(type());
    }

    public boolean result() {
        return supportsResult(type());
    }

    private String type() {
        return recipe.has("type") ? recipe.get("type").getAsString() : "";
    }

    private JsonArray host(boolean result) {
        String key = result ? "results" : "ingredients";
        if (!recipe.has(key) || !recipe.get(key).isJsonArray()) recipe.add(key, new JsonArray());
        return recipe.getAsJsonArray(key);
    }

    public void add(boolean asResult) {
        String type = type();
        if (asResult ? !supportsResult(type) : !supportsInput(type))
            throw new IllegalArgumentException("Тип " + (type.isEmpty() ? "неизвестен" : type)
                    + " не поддерживает жидкости в " + (asResult ? "результатах" : "ингредиентах"));
        var entry = new JsonObject();
        if (asResult) {
            entry.addProperty("id", "minecraft:water");
            entry.addProperty("amount", 250);
        } else {
            entry.addProperty("type", "neoforge:single");
            entry.addProperty("fluid", "minecraft:water");
            entry.addProperty("amount", 250);
        }
        host(asResult).add(entry);
        scan();
    }

    public void remove(int index) {
        check(index);
        var target = entries.get(index).json();
        var array = host(entries.get(index).result());
        for (int i = 0; i < array.size(); i++)
            if (array.get(i) == target) {
                array.remove(i);
                break;
            }
        scan();
    }

    public void set(int index, String id, boolean tag, int amount) {
        check(index);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Укажите ID жидкости или тега");
        if (amount < 1) throw new IllegalArgumentException("Объём должен быть больше нуля");
        write(entries.get(index), id, tag, amount);
    }

    private void write(Entry entry, String id, boolean tag, int amount) {
        var obj = entry.json();
        if (entry.result()) {
            obj.remove("fluid");
            obj.remove("tag");
            obj.addProperty("id", id);
            obj.addProperty("amount", amount);
        } else {
            obj.remove("fluid");
            obj.remove("tag");
            obj.addProperty("type", tag ? "neoforge:tag" : "neoforge:single");
            obj.addProperty(tag ? "tag" : "fluid", id);
            obj.addProperty("amount", amount);
        }
    }

    /** Flips a fluid id entry to a fluid-tag entry and back, keeping the name and amount. Ingredients only. */
    public void toggleTag(int index) {
        check(index);
        var entry = entries.get(index);
        if (entry.result()) throw new IllegalArgumentException("Результаты жидкостей всегда конкретные, теги не поддерживаются");
        var obj = entry.json();
        String name = nameOf(obj);
        write(entry, name, !isTag(obj), amountOf(obj));
    }

    /** Fluid name (tag preferred), matching neoforge:single / neoforge:tag conventions. */
    public static boolean isTag(JsonObject obj) {
        return !obj.has("fluid") && (obj.has("tag"));
    }

    public static String nameOf(JsonObject obj) {
        if (obj.has("tag")) return obj.get("tag").getAsString();
        if (obj.has("fluid")) return obj.get("fluid").getAsString();
        if (obj.has("id") && obj.has("amount")) return obj.get("id").getAsString();
        return "";
    }

    public static int amountOf(JsonObject obj) {
        return obj.has("amount") ? obj.get("amount").getAsInt() : 250;
    }

    private void check(int index) {
        if (index < 0 || index >= entries.size()) throw new IllegalArgumentException("Выберите жидкость в списке");
    }

    public JsonObject finish() {
        for (var entry : entries) {
            var obj = entry.json();
            if (nameOf(obj).isEmpty())
                throw new IllegalArgumentException("У liquid-записи нет имени жидкости или тега");
            if (amountOf(obj) < 1) throw new IllegalArgumentException("Объём должен быть больше нуля");
        }
        return recipe.deepCopy();
    }
}
