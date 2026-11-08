package com.example.examplemod.devtools;

import com.google.gson.*;
import java.util.*;

/** Lossless JSON draft. Operation drafts move with the step and survive type switching. */
public final class SequenceRecipeDraft {
    public static final List<String> TYPES=List.of("create:deploying","create:pressing","create:cutting","create:filling");
    private final JsonObject recipe;
    private final List<Map<String,JsonObject>> memories=new ArrayList<>();
    public SequenceRecipeDraft(JsonObject source){recipe=source.deepCopy();if(!recipe.has("sequence"))recipe.add("sequence",new JsonArray());
        if(!recipe.has("loops"))recipe.addProperty("loops",5);
        if(!recipe.has("transitional_item"))recipe.add("transitional_item",item("minecraft:stone",true));
        for(var step:steps())memories.add(new HashMap<>());
        if(steps().isEmpty())add();
    }
    public JsonObject recipe(){return recipe;}
    public JsonArray steps(){return recipe.getAsJsonArray("sequence");}
    public JsonObject step(int index){return steps().get(index).getAsJsonObject();}
    public static JsonObject item(String id,boolean output){var result=new JsonObject();result.addProperty(output?"id":"item",id);return result;}
    public String intermediate(){return recipe.getAsJsonObject("transitional_item").get("id").getAsString();}
    public void add(){steps().add(fresh("create:pressing"));memories.add(new HashMap<>());}
    public void remove(int index){if(steps().size()>1){steps().remove(index);memories.remove(index);}}
    public void move(int index,int direction){int other=index+direction;if(other<0||other>=steps().size())return;
        var value=steps().get(index);steps().set(index,steps().get(other));steps().set(other,value);Collections.swap(memories,index,other);}
    public void type(int index,String type){var previous=step(index);memories.get(index).put(previous.get("type").getAsString(),previous.deepCopy());
        steps().set(index,memories.get(index).containsKey(type)?memories.get(index).get(type).deepCopy():fresh(type));syncIntermediate();}
    private JsonObject fresh(String type){var step=new JsonObject();step.addProperty("type",type);var inputs=new JsonArray();inputs.add(item(intermediate(),false));
        if(type.equals("create:deploying"))inputs.add(item("minecraft:iron_ingot",false));
        if(type.equals("create:filling")){var fluid=new JsonObject();fluid.addProperty("type","neoforge:single");fluid.addProperty("fluid","minecraft:water");fluid.addProperty("amount",250);inputs.add(fluid);}
        step.add("ingredients",inputs);var outputs=new JsonArray();outputs.add(item(intermediate(),true));step.add("results",outputs);
        if(type.equals("create:cutting"))step.addProperty("processing_time",100);return step;}
    public void syncIntermediate(){for(var element:steps()){var step=element.getAsJsonObject();if(!TYPES.contains(step.get("type").getAsString()))continue;
        var inputs=step.getAsJsonArray("ingredients");if(inputs==null||inputs.isEmpty())continue;inputs.set(0,item(intermediate(),false));
        var results=new JsonArray();results.add(recipe.getAsJsonObject("transitional_item").deepCopy());step.add("results",results);}}
    public JsonObject finish(){syncIntermediate();if(recipe.get("loops").getAsInt()<1)throw new IllegalArgumentException("Число повторений должно быть положительным");return recipe.deepCopy();}
}
