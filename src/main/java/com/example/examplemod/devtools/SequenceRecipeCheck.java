package com.example.examplemod.devtools;

import com.google.gson.*;

/** Headless contracts for stage preservation and intermediate-item wiring. */
public final class SequenceRecipeCheck {
    private static int count;
    private static void check(boolean value){if(!value)throw new AssertionError("Sequence check "+count);count++;}
    public static void main(String[] args){
        var json=JsonParser.parseString("{\"type\":\"create:sequenced_assembly\",\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"transitional_item\":{\"id\":\"createcityexpansioncore:incomplete_access_control_remote\"},\"results\":[{\"id\":\"createcityexpansioncore:access_control_remote\"}],\"sequence\":[],\"loops\":3}").getAsJsonObject();
        var draft=new SequenceRecipeDraft(json);check(json.getAsJsonArray("sequence").isEmpty());check(draft.steps().size()==1);
        draft.type(0,"create:deploying");draft.step(0).getAsJsonArray("ingredients").set(1,SequenceRecipeDraft.item("minecraft:diamond",false));draft.step(0).addProperty("keep_held_item",true);
        draft.type(0,"create:cutting");draft.step(0).addProperty("processing_time",321);
        draft.type(0,"create:deploying");check(draft.step(0).getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString().equals("minecraft:diamond"));check(draft.step(0).get("keep_held_item").getAsBoolean());
        draft.add();draft.move(0,1);draft.type(1,"create:cutting");check(draft.step(1).get("processing_time").getAsInt()==321);
        draft.recipe().getAsJsonObject("transitional_item").addProperty("id","minecraft:gold_ingot");
        var saved=draft.finish();for(var step:saved.getAsJsonArray("sequence")){check(step.getAsJsonObject().getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:gold_ingot"));check(step.getAsJsonObject().getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString().equals("minecraft:gold_ingot"));}
        draft.remove(1);draft.remove(0);check(draft.steps().size()==1);
        draft.type(0,"create:filling");check(draft.step(0).getAsJsonArray("ingredients").get(1).getAsJsonObject().get("type").getAsString().equals("neoforge:single"));
        var unknown=JsonParser.parseString("{\"type\":\"other:custom_step\",\"custom\":{\"retain\":true}}");draft.steps().set(0,unknown);check(draft.finish().getAsJsonArray("sequence").get(0).equals(unknown));
        System.out.println("PASS: "+count+" sequence draft checks");
    }
}
