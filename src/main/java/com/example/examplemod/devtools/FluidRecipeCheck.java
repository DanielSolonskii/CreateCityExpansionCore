package com.example.examplemod.devtools;

import com.google.gson.*;

/** Headless contracts for fluid entries: support matrix, add/set/toggle/remove and finish validation. */
public final class FluidRecipeCheck {
    private static int count;
    private static void check(boolean value) { if (!value) throw new AssertionError("Fluid check " + count); count++; }

    public static void main(String[] args) {
        check(FluidRecipeDraft.supportsInput("create:mixing") && FluidRecipeDraft.supportsResult("create:mixing"));
        check(FluidRecipeDraft.supportsInput("create:compacting") && !FluidRecipeDraft.supportsResult("create:compacting"));
        check(FluidRecipeDraft.supportsInput("create:filling") && !FluidRecipeDraft.supportsResult("create:filling"));
        check(FluidRecipeDraft.supportsResult("create:emptying") && !FluidRecipeDraft.supportsInput("create:emptying"));
        check(!FluidRecipeDraft.supportsInput("create:cutting") && !FluidRecipeDraft.supportsResult("create:cutting"));

        var json = JsonParser.parseString("{\"type\":\"create:mixing\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}],\"results\":[{\"id\":\"createcityexpansioncore:iron_concentrate\",\"count\":1}],\"processing_time\":100}").getAsJsonObject();
        var draft = new FluidRecipeDraft(json);
        check(draft.entries().isEmpty());
        draft.add(false);draft.add(true);
        check(draft.entries().size() == 2);
        check(!draft.entries().get(0).result());
        check(draft.entries().get(1).result());
        var input = draft.entries().get(0).json();
        check(input.get("type").getAsString().equals("neoforge:single"));
        check(input.get("fluid").getAsString().equals("minecraft:water"));
        check(input.get("amount").getAsInt() == 250);
        var output = draft.entries().get(1).json();
        check(!output.has("type"));
        check(output.get("id").getAsString().equals("minecraft:water"));
        draft.set(1, "minecraft:milk", false, 300);
        check(output.get("id").getAsString().equals("minecraft:milk"));
        check(!output.has("type") && !output.has("fluid") && !output.has("tag"));
        check(output.get("amount").getAsInt() == 300);
        check(FluidRecipeDraft.nameOf(output).equals("minecraft:milk"));
        boolean resultToggle = false;
        try { draft.toggleTag(1); } catch (IllegalArgumentException expected) { resultToggle = true; }
        check(resultToggle);
        draft.set(0, "minecraft:lava", false, 500);
        check(input.get("fluid").getAsString().equals("minecraft:lava"));
        check(input.get("amount").getAsInt() == 500);
        draft.toggleTag(0);
        check(input.has("tag") && !input.has("fluid"));
        check(input.get("type").getAsString().equals("neoforge:tag"));
        check(input.get("tag").getAsString().equals("minecraft:lava"));
        draft.set(0, "c:hot_fluids", true, 300);
        check(input.get("tag").getAsString().equals("c:hot_fluids"));
        check(input.get("amount").getAsInt() == 300);
        draft.toggleTag(0);
        check(input.get("type").getAsString().equals("neoforge:single"));
        check(input.get("fluid").getAsString().equals("c:hot_fluids"));

        boolean thrown = false;
        try { draft.set(0, "", false, 10); } catch (IllegalArgumentException expected) { thrown = true; }
        check(thrown);
        thrown = false;
        try { draft.set(0, "minecraft:lava", false, 0); } catch (IllegalArgumentException expected) { thrown = true; }
        check(thrown);
        thrown = false;
        try { draft.remove(9); } catch (IllegalArgumentException expected) { thrown = true; }
        check(thrown);

        draft.remove(0);
        draft.remove(0);
        check(draft.entries().isEmpty());
        check(json.getAsJsonArray("ingredients").size() == 1);
        check(!json.get("ingredients").isJsonArray() || json.getAsJsonArray("ingredients").get(0).getAsJsonObject().has("item"));
        check(json.getAsJsonArray("results").get(0).getAsJsonObject().get("count").getAsInt() == 1);

        var filling = JsonParser.parseString("{\"type\":\"create:filling\",\"ingredients\":[{\"item\":\"minecraft:glass_bottle\"},{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\",\"amount\":250}],\"results\":[{\"id\":\"minecraft:glass_bottle\"}]}").getAsJsonObject();
        var water = new FluidRecipeDraft(filling);
        check(water.entries().size() == 1 && !water.entries().get(0).result());
        water.toggleTag(0);
        check(water.entries().get(0).json().get("tag").getAsString().equals("minecraft:water"));
        check(water.entries().get(0).json().get("type").getAsString().equals("neoforge:tag"));
        boolean caught = false;
        try { water.add(true); } catch (IllegalArgumentException bad) { caught = true; }
        check(caught);

        var broken = JsonParser.parseString("{\"type\":\"create:mixing\",\"ingredients\":[{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\",\"amount\":0},{\"item\":\"minecraft:iron_ingot\"}],\"results\":[]}").getAsJsonObject();
        var bad = new FluidRecipeDraft(broken);
        caught = false;
        try { bad.finish(); } catch (IllegalArgumentException expected) { caught = true; }
        check(caught);
        bad.remove(0);
        check(bad.recipe().getAsJsonArray("ingredients").size() == 1);
        check(bad.recipe().getAsJsonArray("ingredients").get(0).getAsJsonObject().has("item"));
        check(filling.getAsJsonArray("ingredients").size() == 2);
        check(filling.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString().equals("minecraft:glass_bottle"));
        System.out.println("PASS: " + count + " fluid draft checks");
    }
}
