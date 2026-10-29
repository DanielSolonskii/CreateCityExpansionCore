package com.example.examplemod.item;

import com.example.examplemod.block.SimulatedCompat;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.InteractionGrant;
import com.example.examplemod.city.TransportGrantStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Пульт управления доступом.
 *
 * <p>ПКМ предметом по блоку на территории города, участником которого
 * является игрок, открывает этот блок для взаимодействия всем:
 * любой игрок — даже не член города — получает право right-click по этому
 * блоку (открыть сундук, нажать кнопку, открыть дверь). Ломать и ставить
 * блоки открытые блоки не позволяют никак: {@code canModify} смотрит на
 * обычные права и от грантов не зависит.
 *
 * <p>Пульт — выключатель: повторное использование по уже открытому
 * блоку возвращает его под охрану. Неверный блок (за пределами городов
 * или в чужом городе) даёт конкретную причину отказа, потому что
 * «молчаливое ничего» после клика читается как поломка пульта.
 *
 * <p>Открытие работает и на блоках с собственным ПКМ-действием
 * (сундук, дверь, машины): для них нужно применять пульт в
 * приседании, тогда событие доходит до предмета, а не открывается
 * сам блок. Разрешение валидируется по блоку: после замены блока
 * разрешение снимается первой же проверкой защиты.
 *
 * <p>Блоки физических конструкций (sublevel'ы Create Simulated /
 * Aeronautics) открываются отдельным контуром: грант адресуется
 * дельтой относительно терминала регистрации конструкции и хранится
 * в CityManager, поэтому переживает полёт и пересборку. Показ
 * сообщения — тот же тумблер, ломать и ставить блоки открытый
 * транспорт по-прежнему не позволяет.
 */
public final class AccessControlRemoteItem extends Item {

    public AccessControlRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        /*
         * Физическая конструкция (sublevel Create Aeronautics)
         * числится по терминалу регистрации, а не по участку города:
         * транспортная ветка идёт вперёд статической. NOT_PHYSICAL
         * означает «блок не принадлежит конструкции» — статический
         * путь участков ниже.
         */
        switch (SimulatedCompat.toggleTransportBlockGrant(player, level, context.getClickedPos())) {
            case GRANTED -> {
                player.sendSystemMessage(Component.translatable(
                        "message.createcityexpansioncore.access_control_remote.transport_granted"));
                return InteractionResult.CONSUME;
            }
            case REVOKED -> {
                player.sendSystemMessage(Component.translatable(
                        "message.createcityexpansioncore.access_control_remote.transport_revoked"));
                return InteractionResult.CONSUME;
            }
            case NOT_MEMBER -> {
                refuse(player, "transport_not_member");
                return InteractionResult.CONSUME;
            }
            case UNREGISTERED -> {
                refuse(player, "transport_unregistered");
                return InteractionResult.CONSUME;
            }
            case NOT_PHYSICAL -> {
            }
        }

        return toggleAccess(level, player, context.getClickedPos());
    }

    private static InteractionResult toggleAccess(ServerLevel level, ServerPlayer player, BlockPos pos) {
        CityManager cities = CityManager.get(level.getServer().overworld());
        ResourceLocation dimension = level.dimension().location();
        City city = cities.getCityAt(dimension, pos);

        if (city == null) {
            refuse(player, "not_city");
            return InteractionResult.CONSUME;
        }
        if (!city.hasMember(player.getUUID())) {
            refuse(player, "not_member");
            return InteractionResult.CONSUME;
        }

        InteractionGrant existing = cities.interactionGrant(city, pos);
        if (existing != null && existing.dimension().equals(dimension)) {
            cities.removeInteractionGrant(city, pos);
            player.sendSystemMessage(Component.translatable(
                    "message.createcityexpansioncore.access_control_remote.revoked"));
            return InteractionResult.CONSUME;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(pos).getBlock());
        cities.addInteractionGrant(city, dimension, pos, blockId);
        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.access_control_remote.granted"));
        return InteractionResult.CONSUME;
    }

    private static void refuse(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.access_control_remote." + reason)
                .withStyle(net.minecraft.ChatFormatting.RED));
    }
}
