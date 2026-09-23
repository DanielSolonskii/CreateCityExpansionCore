package com.example.examplemod.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.menu.CityPylonMenu;
import com.mojang.serialization.MapCodec;
import com.example.examplemod.city.CityMember;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CityPylonBlock extends Block {

    public static final MapCodec<CityPylonBlock> CODEC =
            simpleCodec(CityPylonBlock::new);

    public CityPylonBlock(
            BlockBehaviour.Properties properties
    ) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public MenuProvider getMenuProvider(
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) ->
                        new CityPylonMenu(
                                containerId,
                                inventory,
                                pos
                        ),
                Component.translatable(
                        "menu.createcityexpansioncore.city_pylon"
                )
        );
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {

        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer) {

            ServerLevel overworld =
                    serverPlayer
                            .serverLevel()
                            .getServer()
                            .overworld();

            CityManager cityManager =
                    CityManager.get(overworld);

            City city =
                    cityManager.getCityByPylon(pos);

            serverPlayer.openMenu(
                    state.getMenuProvider(
                            level,
                            pos
                    ),
                    data -> {

                        /*
                         * =================================
                         * ПОЗИЦИЯ ПИЛОНА
                         * =================================
                         */

                        data.writeBlockPos(pos);

                        /*
                         * =================================
                         * ЕСТЬ ЛИ ГОРОД
                         * =================================
                         */

                        data.writeBoolean(
                                city != null
                        );

                        /*
                         * =================================
                         * НАЗВАНИЕ
                         * =================================
                         */

                        data.writeUtf(
                                city != null
                                        ? city.getName()
                                        : ""
                        );

                        /*
                         * =================================
                         * УРОВЕНЬ
                         * =================================
                         */

                        data.writeVarInt(
                                city != null
                                        ? city.getLevel()
                                        : 1
                        );

                        /*
                         * =================================
                         * ТЕРРИТОРИЯ
                         * =================================
                         */

                        data.writeVarInt(
                                city != null
                                        ? city.getTerritorySize()
                                        : 100
                        );

                        /*
                         * =================================
                         * ЕСТЬ ЛИ ВЛАДЕЛЕЦ
                         * =================================
                         */

                        data.writeBoolean(
                                city != null
                        );

                        /*
                         * =================================
                         * UUID ВЛАДЕЛЬЦА
                         * =================================
                         */

                        if (city != null) {

                            data.writeUUID(
                                    city.getOwner()
                            );
                        }

                        /*
                         * =================================
                         * УЧАСТНИКИ
                         * =================================
                         */

                        if (city != null) {

                            data.writeVarInt(
                                    city.getMembers().size()
                            );

                            for (var member : city.getMembers()) {
                                data.writeUUID(member.getPlayerId());
                                data.writeVarInt(member.getRole().ordinal());
                            }

                        } else {

                            data.writeVarInt(0);
                        }
                    }
            );
        }

        return InteractionResult.sidedSuccess(
                level.isClientSide
        );
    }
    @Override
    public void playerDestroy(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity,
            net.minecraft.world.item.ItemStack tool
    ) {

        super.playerDestroy(
                level,
                player,
                pos,
                state,
                blockEntity,
                tool
        );

        if (!level.isClientSide
                && level instanceof ServerLevel serverLevel) {

            ServerLevel overworld =
                    serverLevel
                            .getServer()
                            .overworld();

            CityManager cityManager =
                    CityManager.get(overworld);

            City city =
                    cityManager.getCityByPylon(pos);

            if (city != null) {

                cityManager.removeCityByPylon(pos);

                if (player instanceof ServerPlayer serverPlayer) {

                    serverPlayer.sendSystemMessage(
                            Component.literal(
                                    "Город «"
                                            + city.getName()
                                            + "» был распущен."
                            )
                    );
                }
            }
        }
    }
}