package com.example.examplemod.command;

import com.example.examplemod.geology.GeologicalRegion;
import com.example.examplemod.geology.GeologyManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class GeologyCommand {

    private static final String MOD_ID =
            "createcityexpansioncore";

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {

        dispatcher.register(
                Commands.literal("geology")
                        .executes(context -> {

                            ServerPlayer player =
                                    context
                                            .getSource()
                                            .getPlayerOrException();

                            ServerLevel level =
                                    player.serverLevel();

                            GeologicalRegion region =
                                    GeologyManager.getRegion(
                                            level,
                                            player.blockPosition()
                                    );

                            /*
                             * Заголовок.
                             */
                            player.sendSystemMessage(
                                    Component.literal(
                                            "§6=== GEOLOGICAL ANALYSIS ==="
                                    )
                            );

                            /*
                             * Позиция игрока.
                             */
                            player.sendSystemMessage(
                                    Component.literal(
                                            "§7Позиция: §f"
                                                    + player.getBlockX()
                                                    + " / "
                                                    + player.getBlockY()
                                                    + " / "
                                                    + player.getBlockZ()
                                    )
                            );

                            /*
                             * ID геологического поля.
                             */
                            player.sendSystemMessage(
                                    Component.literal(
                                            "§7Поле: §f"
                                                    + region.getId()
                                    )
                            );

                            player.sendSystemMessage(
                                    Component.literal("")
                            );

                            /*
                             * Статус территории.
                             */
                            if (isUnproductive(region)) {

                                player.sendSystemMessage(
                                        Component.literal(
                                                "§cСтатус: ЗЕМЛЯ НЕПРИГОДНА ДЛЯ ДОБЫЧИ РЕСУРСОВ"
                                        )
                                );

                                player.sendSystemMessage(
                                        Component.literal(
                                                "§7Полезные геологические залежи практически отсутствуют."
                                        )
                                );

                            } else {

                                player.sendSystemMessage(
                                        Component.literal(
                                                "§aСтатус: территория пригодна для добычи"
                                        )
                                );
                            }

                            player.sendSystemMessage(
                                    Component.literal("")
                            );

                            /*
                             * Ресурсы.
                             */
                            sendResource(
                                    player,
                                    region,
                                    "copper",
                                    "Медь"
                            );

                            sendResource(
                                    player,
                                    region,
                                    "iron",
                                    "Железо"
                            );

                            sendResource(
                                    player,
                                    region,
                                    "coal",
                                    "Уголь"
                            );

                            sendResource(
                                    player,
                                    region,
                                    "zinc",
                                    "Цинк"
                            );

                            /*
                             * Основной ресурс.
                             */
                            ResourceLocation primary =
                                    region.getPrimaryResource();

                            player.sendSystemMessage(
                                    Component.literal("")
                            );

                            if (primary != null) {

                                player.sendSystemMessage(
                                        Component.literal(
                                                "§aОсновной ресурс: §f"
                                                        + getResourceName(primary)
                                        ));

                            } else {

                                player.sendSystemMessage(
                                        Component.literal(
                                                "§8Основной ресурс: отсутствует"
                                        )
                                );
                            }

                            /*
                             * Средний потенциал.
                             */
                            int average =
                                    calculateAverage(region);

                            player.sendSystemMessage(
                                    Component.literal(
                                            "§7Средний потенциал: §f"
                                                    + average
                                                    + "%"
                                    )
                            );

                            player.sendSystemMessage(
                                    Component.literal(
                                            "§6=========================="
                                    )
                            );

                            return 1;
                        })
        );
    }

    /**
     * Выводит информацию об одном ресурсе.
     */
    private static void sendResource(
            ServerPlayer player,
            GeologicalRegion region,
            String id,
            String name
    ) {

        ResourceLocation resource =
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID,
                        id
                );

        int value =
                region.getRichness(resource);

        String bar =
                createBar(value);

        String color =
                getColor(value);

        player.sendSystemMessage(
                Component.literal(
                        "§f"
                                + name
                                + " "
                                + color
                                + bar
                                + " §f"
                                + value
                                + "%"
                )
        );
    }

    /**
     * Создаёт шкалу:
     *
     * ███████░░░
     */
    private static String createBar(
            int value
    ) {

        int segments = 10;

        int filled =
                Math.round(
                        value / 100.0f
                                * segments
                );

        StringBuilder bar =
                new StringBuilder();

        for (int i = 0; i < segments; i++) {

            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        return bar.toString();
    }

    /**
     * Цвет концентрации ресурса.
     */
    private static String getColor(
            int value
    ) {

        if (value >= 80) {
            return "§a";
        }

        if (value >= 60) {
            return "§2";
        }

        if (value >= 40) {
            return "§e";
        }

        if (value >= 20) {
            return "§6";
        }

        return "§8";
    }

    /**
     * Определяет, является ли территория
     * практически бесплодной.
     *
     * Если ВСЕ ресурсы <= 10%,
     * добыча здесь считается нецелесообразной.
     */
    private static boolean isUnproductive(
            GeologicalRegion region
    ) {

        for (
                int value :
                region.getResources().values()
        ) {

            if (value > 10) {
                return false;
            }
        }

        return true;
    }

    /**
     * Средний потенциал всех ресурсов.
     */
    private static int calculateAverage(
            GeologicalRegion region
    ) {

        if (region.getResources().isEmpty()) {
            return 0;
        }

        int total = 0;

        for (
                int value :
                region.getResources().values()
        ) {

            total += value;
        }

        return total /
                region.getResources().size();
    }

    /**
     * Перевод ResourceLocation
     * в красивое название.
     */
    private static String getResourceName(
            ResourceLocation resource
    ) {

        return switch (resource.getPath()) {

            case "copper" -> "Медь";

            case "iron" -> "Железо";

            case "coal" -> "Уголь";

            case "zinc" -> "Цинк";

            default -> resource.getPath();
        };
    }
}