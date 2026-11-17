package com.example.examplemod.economy;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityProjectNetworkHandlers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyCommands {
    private EconomyCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                .executes(context -> pay(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"),
                                        LongArgumentType.getLong(context, "amount"))))));

        dispatcher.register(Commands.literal("citymoney")
                .then(Commands.literal("deposit").then(Commands.argument("amount", LongArgumentType.longArg(1L))
                        .executes(context -> cityTransfer(context.getSource(), LongArgumentType.getLong(context,"amount"), true))))
                .then(Commands.literal("withdraw").then(Commands.argument("amount", LongArgumentType.longArg(1L))
                        .executes(context -> cityTransfer(context.getSource(), LongArgumentType.getLong(context,"amount"), false)))));

        dispatcher.register(Commands.literal("cityadmin").requires(source -> source.hasPermission(2))
                .then(Commands.literal("setmaster").then(Commands.argument("cityName", StringArgumentType.greedyString())
                        .executes(context -> setMaster(context.getSource(), StringArgumentType.getString(context,"cityName")))))
                .then(Commands.literal("clearmaster").executes(context -> setMaster(context.getSource(), null)))
                .then(Commands.literal("level2").executes(context -> setLevelTwo(context.getSource(), null))
                        .then(Commands.argument("cityName", StringArgumentType.greedyString())
                                .executes(context -> setLevelTwo(context.getSource(), StringArgumentType.getString(context,"cityName")))))
                .then(Commands.literal("wallet").then(Commands.argument("player",EntityArgument.player())
                        .then(Commands.argument("amount",LongArgumentType.longArg(0L))
                                .executes(context -> setWallet(context.getSource(),EntityArgument.getPlayer(context,"player"),LongArgumentType.getLong(context,"amount"))))))
                .then(Commands.literal("treasury").then(Commands.argument("cityId",StringArgumentType.word())
                        .then(Commands.argument("amount",LongArgumentType.longArg(0L))
                                .executes(context -> setTreasury(context.getSource(),StringArgumentType.getString(context,"cityId"),LongArgumentType.getLong(context,"amount"))))))
        );
    }

    private static int pay(CommandSourceStack source, ServerPlayer receiver, long amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sender=source.getPlayerOrException();boolean ok=EconomyManager.get(sender.serverLevel()).pay(sender.getUUID(),receiver.getUUID(),amount);
        source.sendSuccess(()->Component.translatable(ok?"command.createcityexpansioncore.economy.success":"command.createcityexpansioncore.economy.failed"),false);return ok?1:0;
    }
    private static int cityTransfer(CommandSourceStack source,long amount,boolean deposit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();City city=CityManager.get(player.getServer().overworld()).getCityByMember(player.getUUID());
        if(city==null||(deposit?!city.hasMember(player.getUUID()):!city.isAdmin(player.getUUID())))return fail(source);
        EconomyManager manager=EconomyManager.get(player.serverLevel());boolean ok=deposit?manager.depositToCity(player.getUUID(),city.getId(),amount):manager.withdrawFromCity(city.getId(),player.getUUID(),amount);
        return result(source,ok);
    }
    private static int setMaster(CommandSourceStack source,String name){CityManager cities=CityManager.get(source.getServer().overworld());City city=name==null?null:cities.getCityByName(name);if(name!=null&&city==null)return fail(source);return result(source,EconomyManager.get(source.getServer().overworld()).setMasterCity(city==null?null:city.getId()));}
    /**
     * Отладочная команда: переводит город сразу на уровень II,
     * минуя проверку требований и прав. Отправляет {@link CityProjectNetworkHandlers#createState}
     * всем онлайн-участникам города, чтобы открытый экран пилона обновился сразу же.
     *
     * <p>Без аргумента берёт город игрока-источника (сначала по членству,
     * затем по территории под ногами). С аргументом — город по имени.
     */
    private static int setLevelTwo(CommandSourceStack source, String name) {
        CityManager cities = CityManager.get(source.getServer().overworld());
        City city;
        if (name != null) {
            city = cities.getCityByName(name);
        } else {
            ServerPlayer player = source.getPlayer();
            if (player == null) return fail(source);
            city = cities.getCityByMember(player.getUUID());
            if (city == null) city = cities.getCityAt(source.getLevel().dimension().location(), player.blockPosition());
        }
        if (city == null) return fail(source);

        boolean already = city.getLevel() >= 2;
        city.setLevel(2);
        cities.markChanged();

        net.minecraft.server.MinecraftServer server = source.getServer();
        ServerPlayer sender = source.getPlayer();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online == sender || city.hasMember(online.getUUID())) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(online,
                        CityProjectNetworkHandlers.createState(server, city, online));
            }
        }

        if (already) {
            source.sendSuccess(() -> Component.translatable(
                    "message.createcityexpansioncore.development.already_level_two"), false);
        } else {
            String cityName = city.getName();
            source.sendSuccess(() -> Component.translatable(
                    "message.createcityexpansioncore.development.level_two_debug", cityName), false);
        }
        return 1;
    }
    private static int setWallet(CommandSourceStack source,ServerPlayer player,long amount){return result(source,EconomyManager.get(source.getServer().overworld()).adminSetWallet(player.getUUID(),amount));}

    private static int setTreasury(CommandSourceStack source,String raw,long amount){try{java.util.UUID id=java.util.UUID.fromString(raw);if(CityManager.get(source.getServer().overworld()).getCity(id)==null)return fail(source);return result(source,EconomyManager.get(source.getServer().overworld()).adminSetTreasury(id,amount));}catch(IllegalArgumentException ex){return fail(source);}}

    private static int result(CommandSourceStack source,boolean ok){source.sendSuccess(()->Component.translatable(ok?"command.createcityexpansioncore.economy.success":"command.createcityexpansioncore.economy.failed"),false);return ok?1:0;}
    private static int fail(CommandSourceStack source){return result(source,false);}
}
