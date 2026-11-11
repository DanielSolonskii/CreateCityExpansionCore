package com.example.examplemod.devtools;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Server-thread snapshots/validation. File paths are restricted to the selected project's generated pack. */
public final class RecipeWorkbenchFiles {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String PACK = "recipe_workbench";
    public static final String META = "{\"pack\":{\"pack_format\":48,\"description\":\"Project recipe overrides\"}}";
    public record Entry(ResourceLocation id, String type, ItemStack output, JsonObject json, boolean disabled) {}
    public static List<Entry> snapshot(MinecraftServer server,ItemStack filter) {
        if(filter.isEmpty())return List.of();
        var result = new ArrayList<Entry>();
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        for(var holder:server.getRecipeManager().getRecipes()) {
            try {
                if(!ItemStack.isSameItem(holder.value().getResultItem(server.registryAccess()),filter))continue;
                var encoded=Recipe.CODEC.encodeStart(ops,holder.value()).getOrThrow().getAsJsonObject();
                var source=server.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(holder.id().getNamespace(),"recipe/"+holder.id().getPath()+".json"));
                if(source.isPresent())try(var reader=source.get().openAsReader()){encoded=JsonParser.parseReader(reader).getAsJsonObject();}
                result.add(new Entry(holder.id(),encoded.get("type").getAsString(),holder.value().getResultItem(server.registryAccess()).copy(),encoded,false));
            } catch(Exception ignored) { /* Special recipes without a serializable JSON form are not editable. */ }
        }
        result.sort(Comparator.comparing(e->e.id().toString()));
        return List.copyOf(result);
    }
    public static List<Entry> snapshot(MinecraftServer server,Path project,ItemStack filter) {
        if(filter.isEmpty())return List.of();
        var result=new ArrayList<>(snapshot(server,filter));
        if(project!=null&&Files.isDirectory(pack(project)))try(var files=Files.walk(pack(project).resolve("data"))){
            for(var file:files.filter(p->p.toString().endsWith(".json")).toList())try{
                var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(!json.has("_workbench_original"))continue;
                var original=json.getAsJsonObject("_workbench_original");
                var relative=pack(project).resolve("data").relativize(file);if(relative.getNameCount()<3||!relative.getName(1).toString().equals("recipe"))continue;
                var path=relative.subpath(2,relative.getNameCount()).toString().replace('\\','/');
                var id=ResourceLocation.fromNamespaceAndPath(relative.getName(0).toString(),path.substring(0,path.length()-5));
                var recipe=Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),original).getOrThrow();
                if(!ItemStack.isSameItem(recipe.getResultItem(server.registryAccess()),filter))continue;
                result.removeIf(e->e.id().equals(id));
                result.add(new Entry(id,original.get("type").getAsString(),recipe.getResultItem(server.registryAccess()),original,true));
            }catch(Exception ignored){}
        }catch(Exception ignored){}
        result.sort(Comparator.comparing(e->e.id().toString()));return List.copyOf(result);
    }
    public static Path root(Path root) throws Exception {
        root=root.toRealPath();
        if(!Files.isRegularFile(root.resolve("build.gradle"))||!Files.isDirectory(root.resolve("src/main/resources")))
            throw new IllegalArgumentException("Выберите корень проекта с build.gradle и src/main/resources");
        return root;
    }
    public static Path pack(Path root){return root.resolve("src/main/resources/datapacks/"+PACK);}
    public static Path recipePath(Path pack,ResourceLocation id) {
        pack=pack.toAbsolutePath().normalize();
        var directory=pack.resolve("data").resolve(id.getNamespace()).resolve("recipe");
        var target=directory.resolve(id.getPath()+".json").normalize();
        if(!target.startsWith(directory)||id.getPath().contains(".."))throw new IllegalArgumentException("Недопустимый путь рецепта");
        return target;
    }
    public static JsonObject disabled() {
        return JsonParser.parseString("{\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}],\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:barrier\"}],\"result\":{\"id\":\"minecraft:barrier\"}}").getAsJsonObject();
    }
    public static java.util.concurrent.CompletableFuture<Void> save(MinecraftServer server,Path selected,ResourceLocation id,JsonObject json,boolean disable) throws Exception {
        var root=root(selected);var pack=pack(root);
        if(!disable)Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),json).getOrThrow();
        var target=recipePath(pack,id);var saved=disable?disabled():json.deepCopy();
        if(disable)saved.add("_workbench_original",json.deepCopy());
        var text=JSON.toJson(saved);
        if(Files.exists(target)) {
            var backup=root.resolve(".recipe-workbench-backups").resolve(Instant.now().toEpochMilli()+"-"+UUID.randomUUID())
                    .resolve(id.getNamespace()).resolve(id.getPath()+".json");
            Files.createDirectories(backup.getParent());Files.copy(target,backup);
        }
        atomic(target,text);atomic(pack.resolve("pack.mcmeta"),META);
        var worldPack=server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK).toAbsolutePath().normalize();
        try(var paths=Files.walk(pack)) {
            for(var source:paths.filter(Files::isRegularFile).toList()) {
                var dest=worldPack.resolve(pack.relativize(source)).normalize();
                if(!dest.startsWith(worldPack))throw new IllegalArgumentException("Недопустимый путь datapack");
                atomic(dest,Files.readString(source));
            }
        }
        server.getPackRepository().reload();
        var selectedPacks=new LinkedHashSet<>(server.getPackRepository().getSelectedIds());selectedPacks.add("file/"+PACK);
        return server.reloadResources(selectedPacks);
    }
    private static void atomic(Path file,String text) throws Exception {
        for(Path parent=file;parent!=null;parent=parent.getParent())
            if(Files.isSymbolicLink(parent))throw new IllegalArgumentException("Запись через символические ссылки запрещена: "+parent);
        Files.createDirectories(file.getParent());
        var temp=Files.createTempFile(file.getParent(),"recipe-",".tmp");
        try {Files.writeString(temp,text);try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
        finally {Files.deleteIfExists(temp);}
    }
}
