package com.example.examplemod.daily;

import com.google.gson.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Seven explicit recipes per cycle, not a general reward scripting engine. */
public final class DailyRewardsConfig {
    public static final List<Item> WOODS=List.of(Items.OAK_LOG,Items.SPRUCE_LOG,Items.BIRCH_LOG,Items.JUNGLE_LOG,Items.ACACIA_LOG,Items.DARK_OAK_LOG,Items.MANGROVE_LOG,Items.CHERRY_LOG);
    public record Spec(String type,String item,int count,String enchantment,int level,int levels,String alternative,double chance){}
    public record Reward(ItemStack item,int levels,String type,double chance){}
    public final List<Spec> starter,regular;public final ZoneId zone;
    private DailyRewardsConfig(List<Spec> starter,List<Spec> regular,ZoneId zone){this.starter=starter;this.regular=regular;this.zone=zone;}
    public long today(){return LocalDate.now(zone).toEpochDay();}
    public static DailyRewardsConfig load(MinecraftServer server){
        Path file=FMLPaths.CONFIGDIR.get().resolve("ccec-daily-rewards.json");
        try{if(!Files.exists(file)){Files.createDirectories(file.getParent());try(var in=DailyRewardsConfig.class.getResourceAsStream("/data/createcityexpansioncore/daily_rewards.json")){if(in==null)throw new IllegalStateException("Missing defaults");Files.copy(in,file);}}
            var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();String timezone=root.get("timeZone").getAsString();var config=new DailyRewardsConfig(read(root.getAsJsonArray("starter")),read(root.getAsJsonArray("regular")),timezone.equals("server")?ZoneId.systemDefault():ZoneId.of(timezone));
            for(var table:List.of(config.starter,config.regular))for(var spec:table)config.reward(server,spec,0,false);return config;
        }catch(Exception e){throw new IllegalStateException("Invalid config/ccec-daily-rewards.json; daily claims disabled",e);}
    }
    private static List<Spec> read(JsonArray list){
        if(list==null||list.size()!=7)throw new IllegalArgumentException("Daily table must have seven rewards");var out=new ArrayList<Spec>();
        for(var element:list){var t=element.getAsJsonObject();var s=new Spec(t.get("type").getAsString(),string(t,"item"),number(t,"count",1),string(t,"enchantment"),number(t,"level",1),number(t,"levels",0),string(t,"alternative"),t.has("chance")?t.get("chance").getAsDouble():0);
            if(!Set.of("item","wood","book","trim","levels","chance").contains(s.type)||s.count<1||s.count>64||s.level<1||s.level>255||s.levels<0||s.levels>1000||!Double.isFinite(s.chance)||s.chance<0||s.chance>1||s.type.equals("levels")&&s.levels==0)throw new IllegalArgumentException("Daily reward values");out.add(s);
        }return List.copyOf(out);
    }
    private static String string(JsonObject t,String key){return t.has(key)?t.get(key).getAsString():"";}
    private static int number(JsonObject t,String key,int fallback){return t.has(key)?t.get(key).getAsInt():fallback;}
    private static Item item(String id){var key=ResourceLocation.parse(id);if(!BuiltInRegistries.ITEM.containsKey(key)||BuiltInRegistries.ITEM.get(key)==Items.AIR)throw new IllegalArgumentException("Unknown reward item "+id);return BuiltInRegistries.ITEM.get(key);}
    public Spec spec(boolean starterCycle,int mask,int day){return starterCycle&&(mask&(1<<(day-1)))==0?starter.get(day-1):regular.get(day-1);}
    public Reward reward(MinecraftServer server,Spec spec,int wood,boolean roll){
        ItemStack stack=ItemStack.EMPTY;int levels=0;
        switch(spec.type){
            case "item"->stack=new ItemStack(item(spec.item),spec.count);
            case "wood"->{if(wood<0||wood>=WOODS.size())throw new IllegalArgumentException("Wood choice");stack=new ItemStack(WOODS.get(wood),spec.count);}
            case "book"->{var enchantment=server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.parse(spec.enchantment)));stack=EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment,spec.level));stack.setCount(spec.count);}
            case "trim"->{var trims=BuiltInRegistries.ITEM.stream().filter(i->{var key=BuiltInRegistries.ITEM.getKey(i);return key.getNamespace().equals("minecraft")&&key.getPath().endsWith("_armor_trim_smithing_template");}).toList();if(trims.isEmpty())throw new IllegalStateException("No armour trims");stack=new ItemStack(trims.get(roll?server.overworld().random.nextInt(trims.size()):0),spec.count);}
            case "levels"->levels=spec.levels;
            case "chance"->{var common=item(spec.item);var rare=item(spec.alternative);stack=new ItemStack(roll&&server.overworld().random.nextDouble()<spec.chance?rare:common,spec.count);}
        }
        return new Reward(stack,levels,spec.type,spec.chance);
    }
}
