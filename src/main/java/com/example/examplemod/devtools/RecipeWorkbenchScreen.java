package com.example.examplemod.devtools;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

/** Entire UI is client-only; server access is restricted to the local operator's integrated server. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class RecipeWorkbenchScreen extends Screen {
    private static Path project;
    private final List<RecipeWorkbenchFiles.Entry> recipes;
    private final Map<String,JsonObject> templates=new TreeMap<>();
    private boolean editing, busy;
    private boolean draggingRecipes;
    private int searchVersion;
    private int page, typeIndex, activeSlot;
    private ItemStack sample=ItemStack.EMPTY, filter=ItemStack.EMPTY;
    private String status="Выберите папку проекта. Слоты образцов не расходуют предметы.";
    private String draft="", recipeId="createcityexpansioncore:dev/new_recipe";
    private MultiLineEditBox editor;
    private float uiScale=1;
    private EditBox id;
    private final List<JsonObject> slots=new ArrayList<>();
    private JsonObject document;
    private static final Set<String> VANILLA=Set.of("minecraft:crafting_shaped","minecraft:crafting_shapeless","minecraft:smelting","minecraft:blasting","minecraft:smoking","minecraft:campfire_cooking","minecraft:stonecutting","minecraft:smithing_transform","minecraft:smithing_trim");
    private RecipeWorkbenchScreen(List<RecipeWorkbenchFiles.Entry> recipes) {
        super(Component.literal("Редактор рецептов • разработка"));this.recipes=new ArrayList<>(recipes);
        for(var entry:recipes)if(entry.type().startsWith("create:")||VANILLA.contains(entry.type()))templates.putIfAbsent(entry.type(),entry.json().deepCopy());
        template("minecraft:crafting_shaped","\"pattern\":[\"ABC\",\"DEF\",\"GHI\"],\"key\":{\"A\":{\"item\":\"minecraft:stone\"},\"B\":{\"item\":\"minecraft:stone\"},\"C\":{\"item\":\"minecraft:stone\"},\"D\":{\"item\":\"minecraft:stone\"},\"E\":{\"item\":\"minecraft:stone\"},\"F\":{\"item\":\"minecraft:stone\"},\"G\":{\"item\":\"minecraft:stone\"},\"H\":{\"item\":\"minecraft:stone\"},\"I\":{\"item\":\"minecraft:stone\"}},\"result\":{\"id\":\"minecraft:stone\",\"count\":1}");
        template("minecraft:crafting_shapeless","\"ingredients\":[{\"item\":\"minecraft:stone\"}],\"result\":{\"id\":\"minecraft:stone\",\"count\":1}");
        for(String type:List.of("smelting","blasting","smoking","campfire_cooking"))template("minecraft:"+type,"\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":{\"id\":\"minecraft:stone\"},\"experience\":0.1,\"cookingtime\":200");
        template("minecraft:stonecutting","\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":{\"id\":\"minecraft:stone\",\"count\":1}");
        template("minecraft:smithing_transform","\"template\":{\"item\":\"minecraft:netherite_upgrade_smithing_template\"},\"base\":{\"item\":\"minecraft:diamond_sword\"},\"addition\":{\"item\":\"minecraft:netherite_ingot\"},\"result\":{\"id\":\"minecraft:netherite_sword\"}");
        template("minecraft:smithing_trim","\"template\":{\"item\":\"minecraft:coast_armor_trim_smithing_template\"},\"base\":{\"tag\":\"minecraft:trimmable_armor\"},\"addition\":{\"item\":\"minecraft:iron_ingot\"}");
        for(String type:List.of("mixing","compacting","pressing","cutting","crushing","milling","splashing","haunting","sandpaper_polishing","deploying","filling","emptying")) {
            String ingredients=type.equals("filling")?"[{\"item\":\"minecraft:glass_bottle\"},{\"type\":\"neoforge:single\",\"fluid\":\"minecraft:water\",\"amount\":250}]":
                    type.equals("deploying")?"[{\"item\":\"minecraft:stone\"},{\"item\":\"minecraft:iron_ingot\"}]":"[{\"item\":\"minecraft:stone\"}]";
            String results=type.equals("emptying")?"[{\"id\":\"minecraft:stone\"},{\"id\":\"minecraft:water\",\"amount\":250}]":"[{\"id\":\"minecraft:stone\",\"count\":1}]";
            templates.putIfAbsent("create:"+type,JsonParser.parseString("{\"type\":\"create:"+type+"\",\"ingredients\":"+ingredients+",\"results\":"+results+",\"processing_time\":100}").getAsJsonObject());
        }
        templates.putIfAbsent("create:mechanical_crafting",templates.get("minecraft:crafting_shaped").deepCopy());
        templates.get("create:mechanical_crafting").addProperty("type","create:mechanical_crafting");
        templates.get("create:mechanical_crafting").addProperty("accept_mirrored",false);
        template("create:sequenced_assembly","\"ingredient\":{\"item\":\"minecraft:stone\"},\"transitional_item\":{\"id\":\"minecraft:stone\"},\"sequence\":[{\"type\":\"create:pressing\",\"ingredients\":[{\"item\":\"minecraft:stone\"}],\"results\":[{\"id\":\"minecraft:stone\"}]}],\"results\":[{\"id\":\"minecraft:stone\"}],\"loops\":1");
        typeIndex=new ArrayList<>(templates.keySet()).indexOf("minecraft:crafting_shaped");
        load(templates.get("minecraft:crafting_shaped"));
    }
    private void template(String type,String fields){templates.put(type,JsonParser.parseString("{\"type\":\""+type+"\","+fields+"}").getAsJsonObject());}
    private String typeName(String type){
        String jeiKey=switch(type){
            case "minecraft:smelting" -> "gui.jei.category.smelting";
            case "minecraft:blasting" -> "gui.jei.category.blasting";
            case "minecraft:smoking" -> "gui.jei.category.smoking";
            case "minecraft:campfire_cooking" -> "gui.jei.category.campfire";
            case "minecraft:stonecutting" -> "gui.jei.category.stoneCutter";
            default -> "";
        };
        if(!jeiKey.isEmpty()&&net.minecraft.client.resources.language.I18n.exists(jeiKey))
            return Component.translatable(jeiKey).getString();
        String key=switch(type){
            case "minecraft:crafting_shaped" -> "create.recipe.automatic_shaped";
            case "minecraft:crafting_shapeless" -> "create.recipe.automatic_shapeless";
            case "minecraft:smelting" -> "block.minecraft.furnace";
            case "minecraft:blasting" -> "block.minecraft.blast_furnace";
            case "minecraft:smoking" -> "block.minecraft.smoker";
            case "minecraft:campfire_cooking" -> "block.minecraft.campfire";
            case "minecraft:stonecutting" -> "block.minecraft.stonecutter";
            case "minecraft:smithing_transform","minecraft:smithing_trim" -> "block.minecraft.smithing_table";
            case "create:compacting" -> "create.recipe.packing";
            case "create:cutting" -> "create.recipe.sawing";
            case "create:splashing" -> "create.recipe.fan_washing";
            case "create:haunting" -> "create.recipe.fan_haunting";
            case "create:filling" -> "create.recipe.spout_filling";
            case "create:emptying" -> "create.recipe.draining";
            default -> type.replace(':','.') .replaceFirst("^create\\.","create.recipe.");
        };
        return net.minecraft.client.resources.language.I18n.exists(key)?Component.translatable(key).getString():type;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        RecipeWorkbench.openClient=uuid->{var mc=Minecraft.getInstance();mc.execute(()->{
            var server=mc.getSingleplayerServer();
            if(!mc.hasSingleplayerServer()||server==null||mc.player==null||!mc.player.getUUID().equals(uuid))return;
            server.execute(()->{var player=server.getPlayerList().getPlayer(uuid);
                if(player==null||!player.hasPermissions(2)||!server.isSingleplayerOwner(player.getGameProfile()))return;
                mc.execute(()->mc.setScreen(new RecipeWorkbenchScreen(List.of())));
            });
        });};
    }
    private void load(JsonObject json){document=json.deepCopy();normalizeGrid();draft=RecipeWorkbenchFiles.JSON.toJson(document);refreshSlots();if(editor!=null)editor.setValue(draft);}
    private boolean grid(){return document.has("pattern")&&document.has("key")&&document.getAsJsonArray("pattern").size()<=3
            &&document.getAsJsonArray("pattern").asList().stream().allMatch(e->e.getAsString().length()<=3);}
    private void normalizeGrid(){
        if(!grid())return;var pattern=document.getAsJsonArray("pattern");var old=document.getAsJsonObject("key");var keys=new JsonObject();var rows=new JsonArray();
        for(int row=0;row<3;row++){String line=row<pattern.size()?pattern.get(row).getAsString():"";StringBuilder next=new StringBuilder();
            for(int col=0;col<3;col++){char source=col<line.length()?line.charAt(col):' ';char key=(char)('A'+row*3+col);
                if(source!=' '&&old.has(String.valueOf(source))){keys.add(String.valueOf(key),old.get(String.valueOf(source)).deepCopy());next.append(key);}else next.append(' ');}
            rows.add(next.toString());}
        document.add("pattern",rows);document.add("key",keys);
    }
    private void refreshSlots(){slots.clear();collect(document);activeSlot=Math.min(activeSlot,Math.max(0,slots.size()-1));}
    private boolean outputSlot(JsonObject slot){
        if(document.get("result")==slot)return true;
        if(document.has("results")&&document.get("results").isJsonArray())
            for(var result:document.getAsJsonArray("results"))if(result==slot)return true;
        return false;
    }
    private int groupIndex(int index){boolean output=outputSlot(slots.get(index));int group=0;
        for(int i=0;i<index;i++)if(outputSlot(slots.get(i))==output)group++;
        return group;
    }
    private int slotX(int index){return width/2+8+(outputSlot(slots.get(index))?90:0)+groupIndex(index)%3*20;}
    private int slotY(int index){return 104+groupIndex(index)/3*24;}
    private void collect(JsonElement element){
        if(element.isJsonObject()){var obj=element.getAsJsonObject();
            if((obj.has("item")||obj.has("tag")||obj.has("id"))&&!obj.has("type")&&!obj.has("fluid")&&!obj.has("amount")){slots.add(obj);return;}
            for(var entry:obj.entrySet())if(!entry.getKey().equals("components")&&!entry.getKey().equals("neoforge:conditions"))collect(entry.getValue());
        }else if(element.isJsonArray())for(var child:element.getAsJsonArray())collect(child);
    }
    private void retain(){if(editor!=null)draft=editor.getValue();if(id!=null)recipeId=id.getValue();}
    private boolean parse(){try{document=JsonParser.parseString(editor.getValue()).getAsJsonObject();normalizeGrid();refreshSlots();return true;}catch(Exception e){status="Ошибка JSON: "+e.getMessage();return false;}}
    private void sync(){draft=RecipeWorkbenchFiles.JSON.toJson(document);editor.setValue(draft);}
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->{if(!busy)action.run();}).bounds(x,y,w,20).build());}
    @Override protected void init(){
        uiScale=Math.min(1f,Math.min(width/680f,height/480f));
        width=Math.round(width/uiScale);height=Math.round(height/uiScale);
        if(project==null)try{var saved=FMLPaths.CONFIGDIR.get().resolve("ccec-recipe-workbench-root.txt");if(Files.exists(saved))project=Path.of(Files.readString(saved).trim());}catch(Exception ignored){}
        int left=12, right=width-12;
        button("Папка проекта",left,28,120,this::chooseFolder);
        button("Создание",140,28,90,()->{retain();editing=false;page=0;rebuild();});
        button("Редактирование",238,28,120,()->{retain();editing=true;page=0;rebuild();});
        button("Закрыть",right-76,28,76,this::onClose);
        id=addRenderableWidget(new EditBox(font,left,58,Math.max(120,width-280),20,Component.literal("ID рецепта")));id.setMaxLength(256);id.setValue(recipeId);
        button(editing?"Предыдущие":"← Тип",right-250,58,115,()->{retain();if(editing)page=Math.max(0,page-4);else{typeIndex=Math.floorMod(typeIndex-1,templates.size());load(new ArrayList<>(templates.values()).get(typeIndex));}rebuild();});
        button(editing?"Следующие":"Тип →",right-125,58,125,()->{retain();if(editing)page+=4;else{typeIndex=(typeIndex+1)%templates.size();load(new ArrayList<>(templates.values()).get(typeIndex));}rebuild();});
        if(editing){var found=filtered();page=Math.min(page,Math.max(0,found.size()-4));
            for(int i=page;i<Math.min(found.size(),page+4);i++){var entry=found.get(i);int y=100+(i-page)*21;
                button((entry.disabled()?"[ОТКЛ] ":"")+entry.id()+" ["+typeName(entry.type())+"]",12,y,Math.max(130,width/2-40),()->{recipeId=entry.id().toString();id.setValue(recipeId);load(entry.json());status="Источник: "+entry.id().getNamespace()+". Сохранение создаст override или восстановит отключённый рецепт.";});}
        }else{
            button("+ ингредиент",12,102,125,()->{if(!parse())return;if(document.has("ingredients")){document.getAsJsonArray("ingredients").add(JsonParser.parseString("{\"item\":\"minecraft:stone\"}"));refreshSlots();sync();}else status="Для этого типа измените pattern/key или ingredient в JSON.";});
            button("− ингредиент",12,126,125,()->{if(!parse())return;if(document.has("ingredients")){var a=document.getAsJsonArray("ingredients");if(a.size()>1)a.remove(a.size()-1);refreshSlots();sync();}});
            button("Слоты из JSON",12,150,125,()->{if(parse())status="Слоты обновлены из JSON";});
        }
        int editorY=210, editorH=Math.max(50,height-355);
        editor=addRenderableWidget(new MultiLineEditBox(font,12,editorY,width-24,editorH,Component.empty(),Component.literal("JSON рецепта")));
        editor.setCharacterLimit(131072);editor.setValue(draft);
        int actionsY=editorY+editorH+4;
        button(editing?"Сохранить изменения":"Добавить крафт",12,actionsY,150,()->confirm(false));
        if(editing)button("Отключить рецепт",170,actionsY,145,()->confirm(true));
        button("Предмет / тег",width-150,actionsY,138,this::toggleTag);
    }
    private void rebuild(){width=Math.round(width*uiScale);height=Math.round(height*uiScale);clearWidgets();init();}
    private List<RecipeWorkbenchFiles.Entry> filtered(){return filter.isEmpty()?List.of():recipes;}
    private void chooseFolder(){retain();new Thread(()->{
        String selected=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_selectFolderDialog("Корень проекта",project==null?System.getProperty("user.home"):project.toString());
        if(selected!=null)try{var root=RecipeWorkbenchFiles.root(Path.of(selected));
            Files.writeString(FMLPaths.CONFIGDIR.get().resolve("ccec-recipe-workbench-root.txt"),root.toString());
            minecraft.execute(()->{project=root;status="Папка проекта сохранена";refresh();});
        }catch(Exception e){minecraft.execute(()->status=e.getMessage());}
    },"recipe-project-picker").start();}
    private void toggleTag(){if(!parse()||slots.isEmpty())return;var slot=slots.get(activeSlot);var stack=sample.isEmpty()?stack(slot):sample;
        if(slot.has("id")){status="Результат должен быть конкретным предметом; теги доступны ингредиентам.";return;}
        if(stack.isEmpty()){status="Выберите предмет в инвентаре";return;}
        var tags=stack.getTags().map(t->t.location().toString()).sorted().toList();
        if(slot.has("tag")){String current=slot.get("tag").getAsString();int index=tags.indexOf(current)+1;
            if(index<tags.size()){slot.addProperty("tag",tags.get(index));status="Тег: #"+tags.get(index);}else{slot.remove("tag");slot.addProperty("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());}}
        else if(!tags.isEmpty()){slot.remove("item");slot.remove("id");slot.addProperty("tag",tags.get(0));status="Тег: #"+tags.get(0)+". Повторное нажатие перебирает теги.";}
        else status="У предмета нет тегов";
        sync();
    }
    private ItemStack stack(JsonObject obj){try{
        if(obj.has("tag")){var key=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,ResourceLocation.parse(obj.get("tag").getAsString()));
            return BuiltInRegistries.ITEM.getTag(key).flatMap(v->v.stream().findFirst()).map(v->new ItemStack(v.value())).orElse(ItemStack.EMPTY);}
        String key=obj.has("item")?"item":"id";return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(obj.get(key).getAsString())));
    }catch(Exception e){return ItemStack.EMPTY;}}
    @Override public boolean mouseClicked(double x,double y,int button){
        x/=uiScale;y/=uiScale;
        if(editing&&filtered().size()>4&&x>=width/2-23&&x<width/2-11&&y>=100&&y<182){draggingRecipes=true;scrollRecipes(y);return true;}
        if(editing&&x>=width-38&&x<width-16&&y>=104&&y<126){filter=sample.copy();page=0;refresh();return true;}
        int invX=Math.max(12,(width-9*20)/2),invY=height-80;
        if(minecraft.player!=null)for(int i=0;i<36;i++){int px=invX+(i%9)*20,py=invY+(i/9)*18;
            if(x>=px&&x<px+18&&y>=py&&y<py+18){sample=minecraft.player.getInventory().getItem(i).copy();
                if(editing&&button==1){filter=sample.copy();page=0;refresh();}
                else status="Образец: "+(sample.isEmpty()?"пусто":sample.getHoverName().getString())+". Нажмите слот ингредиента/результата.";
                return true;
            }}
        int start=width/2+8;
        if(grid()&&x>=start&&x<start+60&&y>=104&&y<176){
            if(!parse())return true;int row=(int)(y-104)/24,col=(int)(x-start)/20;
            char key=(char)('A'+row*3+col);var pattern=document.getAsJsonArray("pattern");char[] line=pattern.get(row).getAsString().toCharArray();
            if(sample.isEmpty()){line[col]=' ';document.getAsJsonObject("key").remove(String.valueOf(key));}
            else{line[col]=key;var ingredient=new JsonObject();ingredient.addProperty("item",BuiltInRegistries.ITEM.getKey(sample.getItem()).toString());document.getAsJsonObject("key").add(String.valueOf(key),ingredient);}
            pattern.set(row,new JsonPrimitive(new String(line)));refreshSlots();
            var selected=document.getAsJsonObject("key").get(String.valueOf(key));if(selected!=null)activeSlot=slots.indexOf(selected.getAsJsonObject());
            sync();return true;
        }
        if(grid()&&x>=start+90&&x<start+108&&y>=128&&y<146){
            if(!parse())return true;var result=document.getAsJsonObject("result");activeSlot=slots.indexOf(result);
            if(!sample.isEmpty()){result.addProperty("id",BuiltInRegistries.ITEM.getKey(sample.getItem()).toString());sync();}return true;
        }
        if(!grid())
        for(int i=0;i<slots.size();i++){if(groupIndex(i)>=9)continue;int px=slotX(i),py=slotY(i);
            if(x>=px&&x<px+18&&y>=py&&y<py+18){if(!parse())return true;activeSlot=i;
                if(!sample.isEmpty()){var obj=slots.get(i);boolean result=obj.has("id");obj.remove("tag");obj.remove("item");obj.remove("id");obj.addProperty(result?"id":"item",BuiltInRegistries.ITEM.getKey(sample.getItem()).toString());sync();}
                return true;
            }}
        return super.mouseClicked(x,y,button);
    }
    private void confirm(boolean disable){retain();if(busy)return;
        if(project==null){status="Сначала выберите папку проекта";return;}
        if(!parse())return;
        ResourceLocation target;try{target=ResourceLocation.parse(id.getValue());}catch(Exception e){status="Неверный ID рецепта";return;}
        var payload=document.deepCopy();var selected=project;
        minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)save(selected,target,payload,disable);},
                Component.literal(disable?"Отключить рецепт?":"Сохранить и применить рецепт?"),
                Component.literal(target+"\n"+RecipeWorkbenchFiles.recipePath(RecipeWorkbenchFiles.pack(selected),target)+"\nJSON показан в редакторе. Существующий override будет скопирован в резервную папку.")));
    }
    private void save(Path root,ResourceLocation target,JsonObject json,boolean disable){
        var server=minecraft.getSingleplayerServer();var uuid=minecraft.player.getUUID();
        if(server==null){status="Доступно только в одиночном мире";return;}busy=true;status="Сохранение и перезагрузка datapack…";
        server.execute(()->{try{var player=server.getPlayerList().getPlayer(uuid);
            if(server.isDedicatedServer()||player==null||!player.hasPermissions(2)||!server.isSingleplayerOwner(player.getGameProfile()))throw new IllegalStateException("Нет прав оператора локального мира");
            RecipeWorkbenchFiles.save(server,root,target,json,disable).whenComplete((v,error)->minecraft.execute(()->{busy=false;status=error==null?"Сохранено и применено.":"Файл сохранён; ошибка reload: "+error.getMessage();if(error==null)refresh();}));
        }catch(Exception error){minecraft.execute(()->{busy=false;status="Ошибка: "+error.getMessage();});}});
    }
    @Override public void resize(Minecraft mc,int w,int h){retain();super.resize(mc,w,h);}
    private void refresh(){var server=minecraft.getSingleplayerServer();if(server==null)return;var selected=project;var requested=filter.copy();int version=++searchVersion;
        recipes.clear();retain();rebuild();
        if(requested.isEmpty()){status="Поместите предмет в фильтр, чтобы найти рецепты.";return;}
        status="Поиск рецептов: "+requested.getHoverName().getString();
        server.execute(()->{var updated=RecipeWorkbenchFiles.snapshot(server,selected,requested);minecraft.execute(()->{
            if(version!=searchVersion)return;
            recipes.clear();recipes.addAll(updated);status="Найдено рецептов: "+recipes.size();retain();rebuild();});});}
    private void scrollRecipes(double y){int maximum=Math.max(0,filtered().size()-4);
        page=(int)Math.round(Math.clamp((y-100-6)/70d,0,1)*maximum);retain();rebuild();}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        g.pose().pushPose();g.pose().scale(uiScale,uiScale,1);mx=(int)(mx/uiScale);my=(int)(my/uiScale);
        g.fill(0,0,width,height,0xff172026);super.render(g,mx,my,pt);
        g.drawString(font,title,12,10,0xffdfbd77);
        g.drawString(font,font.plainSubstrByWidth(project==null?"Папка не выбрана":project.toString(),width-24),12,82,0xffa0afb8);
        g.drawString(font,editing?(filter.isEmpty()?"Поместите предмет в фильтр для поиска рецептов":"Найдено: "+filtered().size()+" · колёсико или полоса прокрутки"):"Тип: "+typeName(document.get("type").getAsString()),12,187,0xffc5d7df);
        if(editing&&filtered().size()>4){int x=width/2-22;g.fill(x,100,x+8,182,0xff34414b);
            int y=100+(int)Math.round(70d*page/(filtered().size()-4));g.fill(x,y,x+8,y+12,0xffdfbd77);}
        g.drawString(font,"Образцы → слоты; Предмет / тег: перебор тегов. Количества, жидкости и шансы — в JSON.",12,198,0xff9ab0bd);
        if(editing){g.drawString(font,"Фильтр",width-70,92,0xffbbbbbb);g.fill(width-38,103,width-16,126,0xff7b6950);g.renderItem(filter,width-35,106);}
        if(grid()){
            for(int i=0;i<9;i++){int x=width/2+8+i%3*20,y=104+i/3*24;var ingredient=document.getAsJsonObject("key").get(String.valueOf((char)('A'+i)));
                g.fill(x-1,y-1,x+18,y+18,ingredient!=null&&slots.indexOf(ingredient.getAsJsonObject())==activeSlot?0xffad873d:0xff53616c);
                if(ingredient!=null){g.renderItem(stack(ingredient.getAsJsonObject()),x,y);if(ingredient.getAsJsonObject().has("tag"))g.drawString(font,"#",x+10,y+9,0xffffff66);}}
            int x=width/2+98;g.fill(x-1,127,x+18,146,0xffad873d);g.renderItem(stack(document.getAsJsonObject("result")),x,128);
            g.drawString(font,"→",width/2+77,133,0xffffffff);
        }else{
            boolean hasOutput=slots.stream().anyMatch(this::outputSlot);
            if(hasOutput)g.drawString(font,"→",width/2+77,133,0xffffffff);
            for(int i=0;i<slots.size();i++){if(groupIndex(i)>=9)continue;int x=slotX(i),y=slotY(i);
                g.fill(x-1,y-1,x+18,y+18,i==activeSlot||outputSlot(slots.get(i))?0xffad873d:0xff53616c);g.renderItem(stack(slots.get(i)),x,y);
                if(slots.get(i).has("tag"))g.drawString(font,"#",x+10,y+9,0xffffff66);}
        }
        if(java.util.stream.IntStream.range(0,slots.size()).anyMatch(i->groupIndex(i)>=9))
            g.drawString(font,"Остальные ячейки доступны в JSON",width/2+8,176,0xffbbbbbb);
        int invX=Math.max(12,(width-180)/2),invY=height-80;
        if(minecraft.player!=null)for(int i=0;i<36;i++){int x=invX+i%9*20,y=invY+i/9*18;g.fill(x-1,y-1,x+18,y+17,0xff34414b);g.renderItem(minecraft.player.getInventory().getItem(i),x,y);}
        g.drawString(font,font.plainSubstrByWidth(status,width-24),12,height-94,0xffe4cf94);
        g.pose().popPose();
    }
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x/uiScale,y/uiScale);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){if(draggingRecipes){scrollRecipes(y/uiScale);return true;}return super.mouseDragged(x/uiScale,y/uiScale,b,dx/uiScale,dy/uiScale);}
    @Override public boolean mouseReleased(double x,double y,int b){if(draggingRecipes){draggingRecipes=false;return true;}return super.mouseReleased(x/uiScale,y/uiScale,b);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(editing&&x/uiScale<width/2&&y/uiScale>=100&&y/uiScale<182){page=Math.clamp(page-(int)Math.signum(dy),0,Math.max(0,filtered().size()-4));retain();rebuild();return true;}return super.mouseScrolled(x/uiScale,y/uiScale,dx,dy);}
    @Override public boolean isPauseScreen(){return false;}
}
