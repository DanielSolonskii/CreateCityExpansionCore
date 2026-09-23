package com.example.examplemod.client.screen;

import com.example.examplemod.network.InvitePlayerPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CityInvitePlayerScreen extends Screen {

    private final Screen parent;

    private final List<PlayerInfo> players =
            new ArrayList<>();

    private int scroll = 0;

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 260;

    public CityInvitePlayerScreen(
            Screen parent
    ) {

        super(
                Component.literal(
                        "Пригласить игрока"
                )
        );

        this.parent = parent;
    }

    @Override
    protected void init() {

        super.init();

        players.clear();

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.getConnection() != null) {

            UUID currentPlayer =
                    minecraft.player != null
                            ? minecraft.player.getUUID()
                            : null;

            players.addAll(
                    minecraft.getConnection()
                            .getOnlinePlayers()
                            .stream()
                            .filter(
                                    info ->
                                            currentPlayer == null
                                                    || !info.getProfile()
                                                    .getId()
                                                    .equals(
                                                            currentPlayer
                                                    )
                            )
                            .toList()
            );
        }

        /*
         * Кнопка НАЗАД
         */

        this.addRenderableWidget(
                Button.builder(
                        Component.literal("НАЗАД"),
                        button -> closeScreen()
                ).bounds(
                        this.width / 2 - 75,
                        (this.height + PANEL_HEIGHT) / 2 - 42,
                        150,
                        28
                ).build()
        );
    }

    private void closeScreen() {

        this.minecraft.setScreen(
                this.parent
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {

        return true;
    }

    @Override
    public void onClose() {

        closeScreen();
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {

        int maxScroll =
                Math.max(
                        0,
                        players.size() - 4
                );

        if (scrollY < 0) {

            scroll =
                    Math.min(
                            maxScroll,
                            scroll + 1
                    );

            return true;
        }

        if (scrollY > 0) {

            scroll =
                    Math.max(
                            0,
                            scroll - 1
                    );

            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        this.renderBackground(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        int left =
                (this.width - PANEL_WIDTH) / 2;

        int top =
                (this.height - PANEL_HEIGHT) / 2;

        /*
         * Панель
         */

        guiGraphics.fill(
                left,
                top,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0xFF202020
        );

        /*
         * Верхняя часть
         */

        guiGraphics.fill(
                left,
                top,
                left + PANEL_WIDTH,
                top + 52,
                0xFF353535
        );

        guiGraphics.fill(
                left,
                top + 49,
                left + PANEL_WIDTH,
                top + 52,
                0xFFD09A3A
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "ПРИГЛАСИТЬ В ГОРОД"
                ),
                this.width / 2,
                top + 14,
                0xFFFFFF
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ОНЛАЙН ИГРОКИ"
                ),
                left + 20,
                top + 67,
                0xAAAAAA
        );

        /*
         * Список
         */

        int start =
                scroll;

        int end =
                Math.min(
                        start + 4,
                        players.size()
                );

        for (int i = start; i < end; i++) {

            PlayerInfo player =
                    players.get(i);

            int visibleIndex =
                    i - start;

            int y =
                    top + 88
                            + visibleIndex * 34;

            renderPlayer(
                    guiGraphics,
                    player,
                    left + 20,
                    y,
                    left + PANEL_WIDTH - 20,
                    mouseX,
                    mouseY
            );
        }

        if (players.isEmpty()) {

            guiGraphics.drawCenteredString(
                    this.font,
                    Component.literal(
                            "Нет доступных игроков"
                    ),
                    this.width / 2,
                    top + 125,
                    0x888888
            );
        }

        super.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private void renderPlayer(
            GuiGraphics guiGraphics,
            PlayerInfo player,
            int left,
            int top,
            int right,
            int mouseX,
            int mouseY
    ) {

        boolean hovered =
                mouseX >= left
                        && mouseX <= right
                        && mouseY >= top
                        && mouseY <= top + 28;

        guiGraphics.fill(
                left,
                top,
                right,
                top + 28,
                hovered
                        ? 0xFF303030
                        : 0xFF181818
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        player.getProfile()
                                .getName()
                ),
                left + 10,
                top + 9,
                0xFFFFFF
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ПРИГЛАСИТЬ"
                ),
                right - 80,
                top + 9,
                hovered
                        ? 0xFFD09A3A
                        : 0xAAAAAA
        );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {

        if (button != 0) {
            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        int left =
                (this.width - PANEL_WIDTH) / 2;

        int top =
                (this.height - PANEL_HEIGHT) / 2;

        int start =
                scroll;

        int end =
                Math.min(
                        start + 4,
                        players.size()
                );

        for (int i = start; i < end; i++) {

            PlayerInfo player =
                    players.get(i);

            int visibleIndex =
                    i - start;

            int playerY =
                    top + 88
                            + visibleIndex * 34;

            boolean clicked =
                    mouseX >= left + 20
                            && mouseX <= left + PANEL_WIDTH - 20
                            && mouseY >= playerY
                            && mouseY <= playerY + 28;

            if (!clicked) {
                continue;
            }

            PacketDistributor.sendToServer(
                    new InvitePlayerPayload(
                            getPylonPos(),
                            player.getProfile().getId()
                    )
            );

            /*
             * Возвращаемся в меню пилона.
             */

            closeScreen();

            return true;
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    private net.minecraft.core.BlockPos getPylonPos() {

        if (parent instanceof CityPylonScreen cityScreen) {

            /*
             * Здесь нам нужен пион позиции из меню.
             */

            return cityScreen.getInvitePylonPos();
        }

        return net.minecraft.core.BlockPos.ZERO;
    }
}