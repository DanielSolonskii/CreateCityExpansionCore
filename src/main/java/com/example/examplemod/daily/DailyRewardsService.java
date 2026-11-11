package com.example.examplemod.daily;

import com.example.examplemod.auth.PlayerNames;
import com.example.examplemod.network.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** All calendar decisions, randomness, inventory planning and journal transitions run on the server thread. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class DailyRewardsService {
    private static final String RECEIPT="CCECDailyDelivery";
    private static final Map<MinecraftServer,DailyRewardsConfig> CONFIGS=new WeakHashMap<>();
    private DailyRewardsService(){}
    public static DailyRewardsConfig config(MinecraftServer server){return CONFIGS.computeIfAbsent(server,DailyRewardsConfig::load);}
    public static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.daily."+key,args);}
    public static void request(ServerPlayer player,DailyRewardsRequest request){
        if(player instanceof FakePlayer)return;long window=net.minecraft.Util.getMillis()/1000;var budget=player.getPersistentData();
        if(budget.getLong("CCECDailyWindow")!=window){budget.putLong("CCECDailyWindow",window);budget.putInt("CCECDailyRequests",0);}
        int count=budget.getInt("CCECDailyRequests");if(count>=6)return;budget.putInt("CCECDailyRequests",count+1);
        String result="ready";
        try{if(request.claim())result=claim(player,request);send(player,request.request(),result,false,false);}
        catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("Daily rewards request failed for {}",player.getUUID(),failure);error(player,request.request());}
    }
    private static String claim(ServerPlayer player,DailyRewardsRequest request)throws IOException{
        var server=player.getServer();var config=config(server);long today=config.today();if(request.expectedDate()!=today)return "date_changed";
        if(player.isDeadOrDying()||player.isSpectator())return "unavailable";
        var data=DailyRewardsData.get(server);var id=player.getUUID();var entry=data.entry(id);
        if(entry.pending()==null||entry.delivered()){
            if(entry.progress().claimed(today))return "already";
            int day=entry.progress().nextDay(today);var spec=config.spec(!entry.progress().completedStarter(),entry.progress().starterMask(),day);
            var reward=config.reward(server,spec,request.wood(),true);var pending=new DailyRewardsData.Pending(UUID.randomUUID(),reward.item(),reward.levels());
            var next=new DailyRewardsData.Entry(PlayerNames.name(player),entry.progress().reserve(today),pending);
            data.put(id,next);try{data.flush(server);}catch(IOException failure){data.put(id,entry);throw failure;}entry=next;
        }
        var pending=entry.pending();var persistent=player.getPersistentData();String receiptScope=net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG;
        if(!persistent.contains(receiptScope,10))persistent.put(receiptScope,new CompoundTag());var receipt=persistent.getCompound(receiptScope);
        if(!receipt.hasUUID(RECEIPT)||!receipt.getUUID(RECEIPT).equals(pending.id())){
            var slots=plan(player.getInventory().items,pending.item());if(slots==null)return "full";
            if((long)player.experienceLevel+pending.levels()>Integer.MAX_VALUE)return "xp_limit";
            for(int i=0;i<slots.size();i++)player.getInventory().items.set(i,slots.get(i));
            if(pending.levels()>0)player.giveExperienceLevels(pending.levels());
            receipt.putUUID(RECEIPT,pending.id());player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
        }
        // Public vanilla save path retains other mods' player-save hooks. Rare daily action, never a tick loop.
        server.getPlayerList().saveAll();
        Path file=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id+".dat");
        var disk=NbtIo.readCompressed(file,NbtAccounter.create(32L*1024*1024));var stored=disk.getCompound("NeoForgeData").getCompound(receiptScope);
        if(!stored.hasUUID(RECEIPT)||!pending.id().equals(stored.getUUID(RECEIPT)))throw new IOException("Player delivery receipt did not reach disk");
        try(var channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}
        var settled=new DailyRewardsData.Entry(entry.name(),entry.progress(),pending,true);data.put(id,settled);
        try{data.flush(server);}catch(IOException failure){data.put(id,entry);throw failure;}
        return "received";
    }
    /** Main inventory only. Preflight the entire award so no partial stack is ever dropped or inserted. */
    public static List<ItemStack> plan(List<ItemStack> inventory,ItemStack reward){
        var result=new ArrayList<ItemStack>();inventory.forEach(s->result.add(s.copy()));int remaining=reward.getCount();if(reward.isEmpty())return result;
        for(var stack:result)if(!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,reward)){int add=Math.min(remaining,Math.max(0,Math.min(64,stack.getMaxStackSize())-stack.getCount()));stack.grow(add);remaining-=add;if(remaining==0)return result;}
        for(int i=0;i<result.size()&&remaining>0;i++)if(result.get(i).isEmpty()){int add=Math.min(remaining,Math.min(64,reward.getMaxStackSize()));result.set(i,reward.copyWithCount(add));remaining-=add;}
        return remaining==0?result:null;
    }
    public static void send(ServerPlayer player,UUID request,String result,boolean auto,boolean open){
        try{var config=config(player.getServer());var entry=DailyRewardsData.get(player.getServer()).entry(player.getUUID());var p=entry.progress();long today=config.today();boolean pending=entry.pending()!=null&&!entry.delivered();
            int day=pending?p.day():p.nextDay(today);boolean starter=pending?p.lastStarter():p.starterCycle(today);var t=new CompoundTag();
            t.putUUID("Owner",player.getUUID());t.putUUID("Request",request);t.putLong("Today",today);t.putString("Zone",config.zone.getId());t.putInt("Day",day);t.putInt("StarterMask",p.starterMask());t.putBoolean("Starter",starter);t.putBoolean("StarterComplete",p.completedStarter());t.putBoolean("Claimed",p.claimed(today));t.putBoolean("Pending",pending);t.putBoolean("Available",pending||!p.claimed(today));t.putBoolean("Auto",auto);t.putBoolean("Open",open);t.putString("Result",result);
            var cards=new ListTag();boolean needsWood=false;
            for(int i=1;i<=7;i++){boolean thisRun=starter&&(p.runUniqueMask()&(1<<(i-1)))!=0&&(i<day||p.claimed(today)||pending);var spec=config.spec(starter,thisRun?p.starterMask()&~(1<<(i-1)):p.starterMask(),i);var reward=config.reward(player.getServer(),spec,0,false);ItemStack icon=reward.levels()>0?new ItemStack(Items.EXPERIENCE_BOTTLE):reward.item();int levels=reward.levels();String type=reward.type();
                if(i==day&&entry.pending()!=null&&(pending||p.claimed(today))){icon=entry.pending().levels()>0?new ItemStack(Items.EXPERIENCE_BOTTLE):entry.pending().item();levels=entry.pending().levels();type=levels>0?"levels":"item";}
                if(i==day&&type.equals("wood")&&!pending&&!p.claimed(today))needsWood=true;
                var card=new CompoundTag();card.put("Icon",icon.saveOptional(player.registryAccess()));card.putInt("Levels",levels);card.putString("Type",type);card.putDouble("Chance",spec.chance());card.putBoolean("StarterUsed",starter&&(p.starterMask()&(1<<(i-1)))!=0);if(type.equals("chance"))card.put("Alternative",new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(spec.alternative()))).saveOptional(player.registryAccess()));cards.add(card);
            }
            t.putBoolean("NeedsWood",needsWood);t.put("Cards",cards);PacketDistributor.sendToPlayer(player,new DailyRewardsState(t));
        }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("Cannot prepare daily rewards state",failure);error(player,request);}
    }
    private static void error(ServerPlayer player,UUID request){var t=new CompoundTag();t.putUUID("Owner",player.getUUID());t.putUUID("Request",request);t.putString("Result","error");PacketDistributor.sendToPlayer(player,new DailyRewardsState(t));}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p&&!(p instanceof FakePlayer))send(p,UUID.randomUUID(),"ready",true,false);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){CONFIGS.remove(event.getServer());}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        var root=Commands.literal("dailyrewards").requires(s->s.hasPermission(2));
        root.then(Commands.literal("open").executes(c->{send(c.getSource().getPlayerOrException(),UUID.randomUUID(),"ready",false,true);c.getSource().sendSuccess(()->tr("opened"),false);return 1;})
            .then(Commands.argument("player",EntityArgument.player()).executes(c->{send(EntityArgument.getPlayer(c,"player"),UUID.randomUUID(),"ready",false,true);c.getSource().sendSuccess(()->tr("opened"),false);return 1;})));
        root.then(Commands.literal("resetall").executes(c->reset(c.getSource(),null)));
        root.then(Commands.literal("reset").then(Commands.argument("player",StringArgumentType.word()).suggests((c,b)->{
            c.getSource().getServer().getPlayerList().getPlayers().forEach(p->b.suggest(PlayerNames.name(p)));DailyRewardsData.get(c.getSource().getServer()).entries().values().stream().map(DailyRewardsData.Entry::name).filter(n->!n.isBlank()).distinct().forEach(b::suggest);return b.buildFuture();
        }).executes(c->reset(c.getSource(),StringArgumentType.getString(c,"player")))));
        event.getDispatcher().register(root);
    }
    private static int reset(net.minecraft.commands.CommandSourceStack source,String name){
        var server=source.getServer();try{var data=DailyRewardsData.get(server);var before=data.entries();UUID id=null;
            if(name!=null){try{id=UUID.fromString(name);}catch(IllegalArgumentException ignored){}
                if(id==null){for(var p:server.getPlayerList().getPlayers())if(PlayerNames.name(p).equalsIgnoreCase(name)||p.getGameProfile().getName().equalsIgnoreCase(name)){id=p.getUUID();break;}}
                if(id==null)id=before.entrySet().stream().filter(e->e.getValue().name().equalsIgnoreCase(name)).map(Map.Entry::getKey).findFirst().orElse(null);
                if(id==null){source.sendFailure(tr("unknown_player"));return 0;}data.reset(id);
            }else data.resetAll();
            try{data.flush(server);}catch(IOException e){for(var old:before.entrySet())data.put(old.getKey(),old.getValue());throw e;}
            for(var p:server.getPlayerList().getPlayers())if(id==null||p.getUUID().equals(id))send(p,UUID.randomUUID(),"reset",false,false);
            source.sendSuccess(()->tr(name==null?"reset_all":"reset_player",name==null?"":name),true);return 1;
        }catch(Exception e){com.mojang.logging.LogUtils.getLogger().error("Daily rewards reset failed",e);source.sendFailure(tr("result.error"));return 0;}
    }
}
