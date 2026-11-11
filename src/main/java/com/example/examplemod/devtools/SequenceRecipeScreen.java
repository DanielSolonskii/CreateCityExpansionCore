package com.example.examplemod.devtools;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Consumer;

public final class SequenceRecipeScreen extends Screen {
    private final Screen parent;
    private final Consumer<JsonObject> accept;
    private final SequenceRecipeDraft draft;
    private int stage,resultIndex,selectedSlot;
    private float scale=1;
    private ItemStack sample=ItemStack.EMPTY;
    private String error="Выберите образец из инвентаря и нажмите нужную ячейку.";
    private EditBox loops,time,fluid,amount,count,chance;
    private boolean fluidTag;
    private final List<JsonObject> displayed=new ArrayList<>();
    public SequenceRecipeScreen(Screen parent,JsonObject json,Consumer<JsonObject> accept){super(Component.literal("Последовательная сборка"));this.parent=parent;this.accept=accept;draft=new SequenceRecipeDraft(json);}
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->{try{store();action.run();rebuild();}catch(Exception e){error=e.getMessage();}}).bounds(x,y,w,20).build());}
    private EditBox field(String value,int x,int y,int w){var box=addRenderableWidget(new EditBox(font,x,y,w,20,Component.empty()));box.setMaxLength(256);box.setValue(value);return box;}
    private String value(JsonObject json,String key,String fallback){return json.has(key)?json.get(key).getAsString():fallback;}
    private JsonObject step(){return draft.step(stage);}
    private String type(){return step().get("type").getAsString();}
    private JsonObject result(){var results=draft.recipe().getAsJsonArray("results");resultIndex=Math.clamp(resultIndex,0,results.size()-1);return results.get(resultIndex).getAsJsonObject();}
    private JsonObject extra(){var inputs=step().getAsJsonArray("ingredients");return inputs!=null&&inputs.size()>1&&inputs.get(1).isJsonObject()?inputs.get(1).getAsJsonObject():null;}
    @Override protected void init(){scale=Math.min(1f,Math.min(width/680f,height/480f));width=Math.round(width/scale);height=Math.round(height/scale);
        loops=time=fluid=amount=count=chance=null;displayed.clear();
        button("Применить в черновик",12,28,170,()->{accept.accept(draft.finish());minecraft.setScreen(parent);});
        button("Отмена",190,28,90,()->minecraft.setScreen(parent));
        loops=field(value(draft.recipe(),"loops","5"),540,65,90);
        displayed.add(draft.recipe().getAsJsonObject("ingredient"));displayed.add(draft.recipe().getAsJsonObject("transitional_item"));
        button("Предмет / тег",12,115,130,this::toggleIngredientTag);
        button("←",300,65,30,()->resultIndex=Math.max(0,resultIndex-1));
        button("→",424,65,30,()->resultIndex=Math.min(draft.recipe().getAsJsonArray("results").size()-1,resultIndex+1));
        button("+ выход",300,115,75,()->draft.recipe().getAsJsonArray("results").add(SequenceRecipeDraft.item("minecraft:stone",true)));
        button("− выход",380,115,75,()->{var results=draft.recipe().getAsJsonArray("results");if(results.size()>1)results.remove(resultIndex);});
        displayed.add(result());count=field(value(result(),"count","1"),470,95,65);chance=field(value(result(),"chance","1"),550,95,80);
        button("← Этап",12,164,85,()->stage=Math.max(0,stage-1));button("Этап →",105,164,85,()->stage=Math.min(draft.steps().size()-1,stage+1));
        button("+ этап",205,164,75,()->{draft.add();stage=draft.steps().size()-1;});button("− этап",288,164,75,()->{draft.remove(stage);stage=Math.min(stage,draft.steps().size()-1);});
        button("Выше",380,164,70,()->{if(stage>0){draft.move(stage,-1);stage--;}});button("Ниже",458,164,70,()->{if(stage+1<draft.steps().size()){draft.move(stage,1);stage++;}});
        String label=switch(type()){case "create:deploying"->"Добавление предмета";case "create:pressing"->"Прессование";case "create:cutting"->"Распиловка";case "create:filling"->"Заливка жидкостью";default->"Неизвестный тип: "+type();};
        button(label+" →",12,218,300,()->{int index=SequenceRecipeDraft.TYPES.indexOf(type());draft.type(stage,SequenceRecipeDraft.TYPES.get((index+1)%4));});
        if(type().equals("create:deploying")){
            displayed.add(extra());button(value(step(),"keep_held_item","false").equals("true")?"Инструмент не расходуется":"Предмет расходуется",12,276,235,()->step().addProperty("keep_held_item",!step().has("keep_held_item")||!step().get("keep_held_item").getAsBoolean()));
            if(extra()==null)error="Сложный ингредиент этапа сохранён. Для изменения используйте JSON.";
        }else if(type().equals("create:cutting"))time=field(value(step(),"processing_time","100"),12,276,120);
        else if(type().equals("create:filling")){
            var extra=extra();if(extra!=null){fluidTag=extra.get("type")!=null&&extra.get("type").getAsString().equals("neoforge:tag");
                fluid=field(value(extra,fluidTag?"tag":"fluid","minecraft:water"),12,276,300);amount=field(value(extra,"amount","250"),325,276,100);
                button(fluidTag?"Тег жидкости":"Конкретная жидкость",435,276,190,()->{fluidTag=!fluidTag;writeFluid();});}
            else error="Сложный ингредиент жидкости сохранён. Для изменения используйте JSON.";
        }
    }
    private int positive(EditBox box){int n=Integer.parseInt(box.getValue());if(n<1)throw new IllegalArgumentException("Значение должно быть больше нуля");return n;}
    private void writeFluid(){if(fluid==null)return;var id=ResourceLocation.parse(fluid.getValue());
        if(!fluidTag&&!BuiltInRegistries.FLUID.containsKey(id))throw new IllegalArgumentException("Неизвестная жидкость: "+id);
        var obj=new JsonObject();obj.addProperty("type",fluidTag?"neoforge:tag":"neoforge:single");obj.addProperty(fluidTag?"tag":"fluid",id.toString());obj.addProperty("amount",positive(amount));step().getAsJsonArray("ingredients").set(1,obj);}
    private void store(){if(loops==null)return;draft.recipe().addProperty("loops",positive(loops));
        result().addProperty("count",positive(count));double weight=Double.parseDouble(chance.getValue());if(!Double.isFinite(weight)||weight<=0)throw new IllegalArgumentException("Вес результата должен быть больше нуля");result().addProperty("chance",weight);
        if(time!=null)step().addProperty("processing_time",positive(time));writeFluid();draft.syncIntermediate();}
    private void rebuild(){if(minecraft.screen!=this)return;width=Math.round(width*scale);height=Math.round(height*scale);clearWidgets();init();}
    private void toggleIngredientTag(){if(selectedSlot!=0&&selectedSlot!=3){error="Теги доступны исходному и добавляемому ингредиенту.";return;}
        var obj=displayed.get(selectedSlot);if(obj==null)return;var stack=sample.isEmpty()?stack(obj):sample;var tags=stack.getTags().map(t->t.location().toString()).sorted().toList();
        int next=obj.has("tag")?tags.indexOf(obj.get("tag").getAsString())+1:0;obj.remove("item");obj.remove("tag");
        if(next<tags.size())obj.addProperty("tag",tags.get(next));else obj.addProperty("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());}
    private ItemStack stack(JsonObject obj){try{if(obj==null)return ItemStack.EMPTY;if(obj.has("tag"))return BuiltInRegistries.ITEM.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,ResourceLocation.parse(obj.get("tag").getAsString()))).flatMap(v->v.stream().findFirst()).map(v->new ItemStack(v.value())).orElse(ItemStack.EMPTY);
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(obj.get(obj.has("id")?"id":"item").getAsString())));}catch(Exception e){return ItemStack.EMPTY;}}
    private int sx(int i){return switch(i){case 0->22;case 1->190;case 2->370;default->340;};}
    private int sy(int i){return i==3?250:88;}
    @Override public boolean mouseClicked(double x,double y,int button){x/=scale;y/=scale;
        int invX=(width-180)/2,invY=height-80;
        for(int i=0;i<36;i++)if(x>=invX+i%9*20&&x<invX+i%9*20+18&&y>=invY+i/9*18&&y<invY+i/9*18+18){sample=minecraft.player.getInventory().getItem(i).copy();return true;}
        for(int i=0;i<displayed.size();i++)if(x>=sx(i)&&x<sx(i)+18&&y>=sy(i)&&y<sy(i)+18){selectedSlot=i;if(sample.isEmpty()||displayed.get(i)==null)return true;
            try{store();String id=BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();var target=displayed.get(i);target.remove("tag");target.remove("item");target.remove("id");target.addProperty(i==1||i==2?"id":"item",id);draft.syncIntermediate();rebuild();}catch(Exception e){error=e.getMessage();}return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){g.pose().pushPose();g.pose().scale(scale,scale,1);g.fill(0,0,width,height,0xff172026);super.render(g,(int)(mx/scale),(int)(my/scale),pt);
        g.drawString(font,title,12,10,0xffdfbd77);g.drawString(font,"Исходный ингредиент",12,65,0xffffffff);g.drawString(font,"Промежуточный предмет",170,65,0xffffffff);
        g.drawString(font,"Результат "+(resultIndex+1)+" / "+draft.recipe().getAsJsonArray("results").size(),335,65,0xffffffff);
        g.drawString(font,"Повторения",540,53,0xffffffff);g.drawString(font,"Количество",470,83,0xffffffff);g.drawString(font,"Вес / шанс",550,83,0xffffffff);
        g.drawString(font,"Этап "+(stage+1)+" из "+draft.steps().size(),12,195,0xffdfbd77);
        if(type().equals("create:deploying"))g.drawString(font,"Добавляемый предмет / тег",330,236,0xffffffff);
        if(time!=null)g.drawString(font,"Время, тики",12,262,0xffffffff);
        if(fluid!=null){g.drawString(font,"ID жидкости или тега (например minecraft:water)",12,262,0xffffffff);g.drawString(font,"Объём, mB",325,262,0xffffffff);}
        if(!SequenceRecipeDraft.TYPES.contains(type()))g.drawString(font,"Параметры неизвестной операции сохранены без изменений; доступны в JSON.",12,250,0xffe4cf94);
        for(int i=0;i<displayed.size();i++){g.fill(sx(i)-1,sy(i)-1,sx(i)+18,sy(i)+18,i==selectedSlot?0xffdfbd77:0xff53616c);g.renderItem(stack(displayed.get(i)),sx(i),sy(i));}
        int invX=(width-180)/2,invY=height-80;for(int i=0;i<36;i++){int x=invX+i%9*20,y=invY+i/9*18;g.fill(x-1,y-1,x+18,y+17,0xff34414b);g.renderItem(minecraft.player.getInventory().getItem(i),x,y);}
        g.drawString(font,font.plainSubstrByWidth(error,width-24),12,height-100,0xffe4cf94);g.pose().popPose();}
    @Override public void resize(Minecraft mc,int w,int h){try{store();}catch(Exception e){error=e.getMessage();}super.resize(mc,w,h);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean mouseReleased(double x,double y,int b){return super.mouseReleased(x/scale,y/scale,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return super.mouseDragged(x/scale,y/scale,b,dx/scale,dy/scale);}
    @Override public boolean isPauseScreen(){return false;}
}
