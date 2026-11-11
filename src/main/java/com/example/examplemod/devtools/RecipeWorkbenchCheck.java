package com.example.examplemod.devtools;

import java.nio.file.*;
import net.minecraft.resources.ResourceLocation;

public final class RecipeWorkbenchCheck {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Check "+checks);}
    public static void main(String[] args) throws Exception {
        var temp=Files.createTempDirectory(Path.of("build"),"recipe-workbench-check-").toAbsolutePath();
        Files.writeString(temp.resolve("build.gradle"),"");Files.createDirectories(temp.resolve("src/main/resources"));
        check(RecipeWorkbenchFiles.root(temp).equals(temp.toRealPath()));
        var pack=RecipeWorkbenchFiles.pack(temp);
        check(RecipeWorkbenchFiles.recipePath(pack,ResourceLocation.parse("create:crafting/example")).equals(pack.resolve("data/create/recipe/crafting/example.json")));
        for(String id:new String[]{"create:../escape","create:a/../../escape","create:../../../../escape"}){
            boolean refused=false;try{RecipeWorkbenchFiles.recipePath(pack,ResourceLocation.parse(id));}catch(IllegalArgumentException e){refused=true;}check(refused);
        }
        boolean refused=false;try{RecipeWorkbenchFiles.root(temp.resolve("src"));}catch(IllegalArgumentException e){refused=true;}check(refused);
        var disabled=RecipeWorkbenchFiles.disabled();
        check(disabled.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("type").getAsString().equals("neoforge:false"));
        check(disabled.get("type").getAsString().equals("minecraft:crafting_shapeless"));
        check(RecipeWorkbenchFiles.JSON.fromJson(RecipeWorkbenchFiles.JSON.toJson(disabled),com.google.gson.JsonObject.class).equals(disabled));
        System.out.println("PASS: "+checks+" workbench path and disabled recipe checks");
    }
}
