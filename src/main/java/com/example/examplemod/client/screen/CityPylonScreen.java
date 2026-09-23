package com.example.examplemod.client.screen;

import com.example.examplemod.city.CityMember;
import com.example.examplemod.client.CityBoundaryRenderer;
import com.example.examplemod.client.gui.CityButton;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.InvitePlayerPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import net.neoforged.neoforge.network.PacketDistributor;

public class CityPylonScreen
        extends AbstractContainerScreen<CityPylonMenu> {

    private boolean showTerritory = false;

    private int memberScroll = 0;

    /*
     * 0 = Обзор
     * 1 = Территория
     * 2 = Участники
     * 3 = Проекты
     */
    private int activeTab = 0;

    /*
     * =========================================
     * ОКНО ПРИГЛАШЕНИЯ
     * =========================================
     */

    private boolean showInvitePlayers = false;

    private String inviteStatus = "";

    /*
     * =========================================
     * КНОПКИ
     * =========================================
     */

    private CityButton inviteButton;

    private CityButton closeButton;

    private final CityButton[] tabButtons =
            new CityButton[4];

    public CityPylonScreen(
            CityPylonMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);

        this.imageWidth = 520;
        this.imageHeight = 300;
    }

    @Override
    protected void init() {

        super.init();

        this.showTerritory =
                CityBoundaryRenderer.isVisible();

        /*
         * =====================================
         * ЕСЛИ ГОРОДА НЕТ
         * =====================================
         */

        if (!this.menu.hasCity()) {

            this.addRenderableWidget(
                    new CityButton(
                            this.leftPos + 20,
                            this.topPos + 125,
                            480,
                            26,
                            Component.literal(
                                    "СОЗДАТЬ ГОРОД"
                            ),
                            button -> {

                                if (this.minecraft != null) {

                                    this.minecraft.setScreen(
                                            new CityCreationScreen(
                                                    this.menu.getPylonPos()
                                            )
                                    );
                                }
                            }
                    )
            );

            return;
        }

        /*
         * =====================================
         * ЗАКРЫТЬ
         * =====================================
         */

        this.closeButton =
                new CityButton(
                        this.leftPos + 390,
                        this.topPos + 255,
                        110,
                        26,
                        Component.literal("ЗАКРЫТЬ"),
                        button -> this.onClose()
                );

        this.addRenderableWidget(
                this.closeButton
        );

        /*
         * =====================================
         * ВКЛАДКИ
         * =====================================
         */

        String[] tabNames = {
                "ОБЗОР",
                "ТЕРРИТОРИЯ",
                "УЧАСТНИКИ",
                "ПРОЕКТЫ"
        };

        int[] tabY = {
                this.topPos + 75,
                this.topPos + 110,
                this.topPos + 145,
                this.topPos + 180
        };

        for (int i = 0; i < 4; i++) {

            final int tabIndex = i;

            this.tabButtons[i] =
                    new CityButton(
                            this.leftPos + 10,
                            tabY[i],
                            150,
                            26,
                            Component.literal(
                                    tabNames[i]
                            ),
                            button -> {

                                this.activeTab =
                                        tabIndex;

                                this.showInvitePlayers =
                                        false;

                                this.inviteStatus =
                                        "";

                                if (tabIndex != 2) {

                                    this.memberScroll =
                                            0;
                                }

                                updateTabButtons();
                                updateInviteButton();
                            }
                    );

            this.tabButtons[i].setActive(
                    this.activeTab == i
            );

            this.addRenderableWidget(
                    this.tabButtons[i]
            );
        }

        /*
         * =====================================
         * ПРИГЛАСИТЬ
         * =====================================
         */

        if (canInvitePlayers()) {

            this.inviteButton =
                    new CityButton(
                            this.leftPos + 200 + 185,
                            this.topPos + 75 + 45 - 7,
                            110,
                            24,
                            Component.literal(
                                    "ПРИГЛАСИТЬ"
                            ),
                            button -> {

                                this.showInvitePlayers =
                                        true;

                                this.inviteStatus =
                                        "";

                                updateInviteButton();
                                updateTabButtonsVisibility();
                            }
                    );

            this.addRenderableWidget(
                    this.inviteButton
            );

            updateInviteButton();
        }

        updateTabButtonsVisibility();
    }

    /*
     * =========================================
     * ОБНОВЛЕНИЕ ВКЛАДОК
     * =========================================
     */

    private void updateTabButtons() {

        for (int i = 0;
             i < this.tabButtons.length;
             i++) {

            if (this.tabButtons[i] != null) {

                this.tabButtons[i].setActive(
                        this.activeTab == i
                );
            }
        }

        updateTabButtonsVisibility();
    }

    /*
     * =========================================
     * ВИДИМОСТЬ ВКЛАДОК
     * =========================================
     */

    private void updateTabButtonsVisibility() {

        boolean visible =
                !this.showInvitePlayers;

        for (CityButton button :
                this.tabButtons) {

            if (button != null) {

                button.visible =
                        visible;
            }
        }

        if (this.closeButton != null) {

            this.closeButton.visible =
                    !this.showInvitePlayers;
        }
    }

    /*
     * =========================================
     * ВИДИМОСТЬ КНОПКИ ПРИГЛАСИТЬ
     * =========================================
     */

    private void updateInviteButton() {

        if (this.inviteButton == null) {
            return;
        }

        this.inviteButton.visible =
                !this.showInvitePlayers
                        && this.activeTab == 2
                        && canInvitePlayers();
    }

    @Override
    public void onClose() {

        super.onClose();
    }

    /*
     * =========================================
     * SCROLL
     * =========================================
     */

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {

        /*
         * Если открыто приглашение,
         * скроллим список игроков.
         */

        if (this.showInvitePlayers) {

            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    scrollX,
                    scrollY
            );
        }

        if (this.activeTab == 2
                && this.menu.hasCity()) {

            int listLeft =
                    this.leftPos + 200;

            int listTop =
                    this.topPos + 148;

            int listRight =
                    this.leftPos + 490;

            int listBottom =
                    this.topPos + 248;

            if (mouseX >= listLeft
                    && mouseX <= listRight
                    && mouseY >= listTop
                    && mouseY <= listBottom) {

                int memberCount =
                        this.menu.getMemberCount();

                int visibleCount = 2;

                int maxScroll =
                        Math.max(
                                0,
                                memberCount
                                        - visibleCount
                        );

                if (scrollY > 0) {

                    memberScroll =
                            Math.max(
                                    0,
                                    memberScroll - 1
                            );
                }

                if (scrollY < 0) {

                    memberScroll =
                            Math.min(
                                    maxScroll,
                                    memberScroll + 1
                            );
                }

                return true;
            }
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    /*
     * =========================================
     * КЛИКИ
     * =========================================
     */

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {

        /*
         * =====================================
         * ОКНО ПРИГЛАШЕНИЯ
         * =====================================
         */

        if (this.showInvitePlayers
                && button == 0) {

            /*
             * Кнопка НАЗАД
             *
             * Она пока рисуется вручную,
             * потому что существует только
             * внутри экрана приглашения.
             */

            int backX =
                    this.leftPos + 200 + 160;

            int backY =
                    this.topPos + 75 + 23;

            int backWidth = 140;
            int backHeight = 24;

            if (mouseX >= backX
                    && mouseX <= backX + backWidth
                    && mouseY >= backY
                    && mouseY <= backY + backHeight) {

                this.showInvitePlayers =
                        false;

                this.inviteStatus =
                        "";

                updateInviteButton();
                updateTabButtonsVisibility();

                return true;
            }

            /*
             * Список игроков
             */

            List<PlayerInfo> players =
                    getInvitePlayers();

            int listX =
                    this.leftPos + 200;

            int listY =
                    this.topPos + 75 + 78;

            int rowHeight = 38;

            for (int i = 0;
                 i < players.size();
                 i++) {

                int currentY =
                        listY
                                + i * rowHeight;

                if (mouseX >= listX
                        && mouseX <= listX + 290
                        && mouseY >= currentY
                        && mouseY <= currentY + 32) {

                    PlayerInfo player =
                            players.get(i);

                    invitePlayer(player);

                    return true;
                }
            }

            return true;
        }

        /*
         * =====================================
         * ГРАНИЦЫ
         * =====================================
         */

        if (this.menu.hasCity()
                && button == 0
                && this.activeTab == 1) {

            int checkboxX =
                    this.leftPos + 200;

            int checkboxY =
                    this.topPos
                            + 75
                            + 45
                            + 110;

            int boxSize = 14;

            boolean clicked =
                    mouseX >= checkboxX
                            && mouseX <= checkboxX + 190
                            && mouseY >= checkboxY - 3
                            && mouseY <= checkboxY
                            + boxSize
                            + 3;

            if (clicked) {

                this.showTerritory =
                        !this.showTerritory;

                CityBoundaryRenderer.setVisible(
                        this.showTerritory,
                        this.menu.getPylonPos(),
                        this.menu.getTerritorySize()
                );

                return true;
            }
        }

        /*
         * =====================================
         * ЕСЛИ ГОРОДА НЕТ
         * =====================================
         */

        if (!this.menu.hasCity()) {

            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        /*
         * =========================================
         * УПРАВЛЕНИЕ УЧАСТНИКОМ
         * =========================================
         */

        if (this.menu.hasCity()
                && this.activeTab == 2
                && button == 0) {

            int cardX =
                    this.leftPos + 200;

            int cardY =
                    this.topPos + 75 + 45 + 28;

            int cardWidth = 270;
            int cardHeight = 48;
            int spacing = 6;

            int visibleCount = 2;

            int start =
                    this.memberScroll;

            int end =
                    Math.min(
                            start + visibleCount,
                            this.menu.getMemberCount()
                    );

            for (int i = start; i < end; i++) {

                CityMember member =
                        this.menu
                                .getMembers()
                                .get(i);

                int visibleIndex =
                        i - start;

                int currentY =
                        cardY
                                + visibleIndex
                                * (cardHeight + spacing);

                int gearX =
                        cardX + cardWidth - 30;

                int gearY =
                        currentY + 8;

                boolean clicked =
                        mouseX >= gearX
                                && mouseX <= cardX + cardWidth
                                && mouseY >= gearY
                                && mouseY <= currentY + 35;

                if (clicked
                        && canManageMember(member)) {

                    String playerName =
                            getMemberName(member);

                    this.minecraft.setScreen(
                            new CityRoleScreen(
                                    this,
                                    this.menu.getPylonPos(),
                                    member.getPlayerId(),
                                    playerName
                            )
                    );

                    return true;
                }
            }
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    /*
     * =========================================
     * ПОЛУЧЕНИЕ ИГРОКОВ ДЛЯ ПРИГЛАШЕНИЯ
     * =========================================
     */

    private List<PlayerInfo> getInvitePlayers() {

        List<PlayerInfo> players =
                new ArrayList<>();

        if (this.minecraft == null
                || this.minecraft.getConnection() == null
                || this.minecraft.player == null) {

            return players;
        }

        UUID currentPlayer =
                this.minecraft.player.getUUID();

        for (PlayerInfo playerInfo :
                this.minecraft
                        .getConnection()
                        .getOnlinePlayers()) {

            UUID playerId =
                    playerInfo
                            .getProfile()
                            .getId();

            /*
             * Не показываем самого себя.
             */

            if (playerId.equals(currentPlayer)) {
                continue;
            }

            /*
             * Не показываем игроков,
             * которые уже состоят в этом городе.
             */

            boolean alreadyMember =
                    this.menu
                            .getMembers()
                            .stream()
                            .anyMatch(
                                    member ->
                                            member.getPlayerId()
                                                    .equals(
                                                            playerId
                                                    )
                            );

            if (alreadyMember) {
                continue;
            }

            players.add(playerInfo);
        }

        return players;
    }

    /*
     * =========================================
     * ПРИГЛАСИТЬ ИГРОКА
     * =========================================
     */

    private void invitePlayer(
            PlayerInfo playerInfo
    ) {

        if (playerInfo == null) {
            return;
        }

        UUID playerId =
                playerInfo
                        .getProfile()
                        .getId();

        String playerName =
                playerInfo
                        .getProfile()
                        .getName();

        PacketDistributor.sendToServer(
                new InvitePlayerPayload(
                        this.menu.getPylonPos(),
                        playerId
                )
        );

        this.inviteStatus =
                "Приглашение отправлено: "
                        + playerName;
    }

    /*
     * =========================================
     * МОЖЕТ ПРИГЛАШАТЬ
     * =========================================
     */

    private boolean canInvitePlayers() {

        if (this.minecraft == null
                || this.minecraft.player == null) {

            return false;
        }

        UUID currentPlayer =
                this.minecraft.player.getUUID();

        CityMember currentMember =
                this.menu.getMembers()
                        .stream()
                        .filter(
                                member ->
                                        member.getPlayerId()
                                                .equals(
                                                        currentPlayer
                                                )
                        )
                        .findFirst()
                        .orElse(null);

        if (currentMember == null) {
            return false;
        }

        return currentMember.getRole()
                == CityMember.Role.OWNER
                || currentMember.getRole()
                == CityMember.Role.ADMIN;
    }

    /*
     * =========================================
     * ИМЯ УЧАСТНИКА
     * =========================================
     */

    private String getMemberName(
            CityMember member
    ) {

        if (this.minecraft != null
                && this.minecraft.getConnection() != null) {

            PlayerInfo playerInfo =
                    this.minecraft
                            .getConnection()
                            .getPlayerInfo(
                                    member.getPlayerId()
                            );

            if (playerInfo != null) {

                return playerInfo
                        .getProfile()
                        .getName();
            }
        }

        return member.getRole()
                == CityMember.Role.OWNER
                ? "Владелец"
                : "Участник";
    }

    /*
     * =========================================
     * ФОН
     * =========================================
     */

    @Override
    protected void renderBg(
            GuiGraphics guiGraphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {

        int left =
                this.leftPos;

        int top =
                this.topPos;

        /*
         * Основная панель
         */

        guiGraphics.fill(
                left,
                top,
                left + this.imageWidth,
                top + this.imageHeight,
                0xFF202020
        );

        /*
         * Верхняя панель
         */

        guiGraphics.fill(
                left,
                top,
                left + this.imageWidth,
                top + 50,
                0xFF353535
        );

        /*
         * Золотая линия

         */

        guiGraphics.fill(
                left,
                top + 48,
                left + this.imageWidth,
                top + 51,
                0xFFD09A3A
        );

        if (!this.menu.hasCity()) {
            return;
        }

        /*
         * Левая панель
         */

        guiGraphics.fill(
                left,
                top + 51,
                left + 170,
                top + this.imageHeight,
                0xFF181818
        );

        /*
         * Разделитель
         */

        guiGraphics.fill(
                left + 170,
                top + 51,
                left + 171,
                top + this.imageHeight,
                0xFF444444
        );
    }

    /*
     * =========================================
     * RENDER
     * =========================================
     */

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

        super.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        int left =
                this.leftPos;

        int top =
                this.topPos;

        /*
         * =====================================
         * ЗАГОЛОВОК
         * =====================================
         */

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        this.menu.hasCity()
                                ? this.menu.getCityName()
                                : "ГОРОДСКОЙ ПИЛОН"
                ),
                left + 15,
                top + 10,
                0xFFFFFF
        );

        /*
         * =====================================
         * ЕСЛИ ГОРОДА НЕТ
         * =====================================
         */

        if (!this.menu.hasCity()) {

            guiGraphics.drawString(
                    this.font,
                    Component.literal(
                            "Город ещё не создан"
                    ),
                    left + 20,
                    top + 55,
                    0xFFFFFF
            );

            guiGraphics.drawString(
                    this.font,
                    Component.literal(
                            "Этот пилон станет центром управления городом."
                    ),
                    left + 20,
                    top + 80,
                    0xAAAAAA
            );

            return;
        }

        /*
         * =====================================
         * ПОДЗАГОЛОВОК
         * =====================================
         */

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "УПРАВЛЕНИЕ ГОРОДОМ"
                ),
                left + 15,
                top + 28,
                0xAAAAAA
        );

        /*
         * =====================================
         * ЭКРАН ПРИГЛАШЕНИЯ
         * =====================================
         */

        if (this.showInvitePlayers) {

            renderInvitePlayers(
                    guiGraphics,
                    left + 200,
                    top + 75,
                    mouseX,
                    mouseY
            );

            return;
        }

        /*
         * =====================================
         * КОНТЕНТ
         * =====================================
         */

        int contentLeft =
                left + 200;

        int contentTop =
                top + 75;

        String title;

        switch (this.activeTab) {

            case 1 ->
                    title = "ТЕРРИТОРИЯ";

            case 2 ->
                    title = "УЧАСТНИКИ";

            case 3 ->
                    title = "ПРОЕКТЫ";

            default ->
                    title = "ОБЗОР ГОРОДА";
        }

        guiGraphics.drawString(
                this.font,
                Component.literal(title),
                contentLeft,
                contentTop,
                0xFFFFFF
        );

        guiGraphics.fill(
                contentLeft,
                contentTop + 16,
                left + this.imageWidth - 20,
                contentTop + 18,
                0xFFD09A3A
        );

        switch (this.activeTab) {

            case 0 ->
                    renderOverview(
                            guiGraphics,
                            contentLeft,
                            contentTop
                    );

            case 1 ->
                    renderTerritory(
                            guiGraphics,
                            contentLeft,
                            contentTop
                    );

            case 2 ->
                    renderMembers(
                            guiGraphics,
                            contentLeft,
                            contentTop,
                            mouseX,
                            mouseY
                    );

            case 3 ->
                    renderProjects(
                            guiGraphics,
                            contentLeft,
                            contentTop
                    );
        }
    }

    /*
     * =========================================
     * ЭКРАН ВЫБОРА ИГРОКА
     * =========================================
     */

    private void renderInvitePlayers(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int mouseX,
            int mouseY
    ) {

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ПРИГЛАСИТЬ ИГРОКА"
                ),
                left,
                top,
                0xFFFFFF
        );

        guiGraphics.fill(
                left,
                top + 16,
                this.leftPos
                        + this.imageWidth
                        - 20,
                top + 18,
                0xFFD09A3A
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ОНЛАЙН"
                ),
                left,
                top + 32,
                0xAAAAAA
        );

        /*
         * =====================================
         * КНОПКА НАЗАД
         * =====================================
         *
         * Пока остаётся ручной,
         * потому что она существует только
         * внутри экрана приглашения.
         */

        int backX =
                left + 160;

        int backY =
                top + 23;

        int backWidth = 140;
        int backHeight = 24;

        boolean backHovered =
                mouseX >= backX
                        && mouseX <= backX + backWidth
                        && mouseY >= backY
                        && mouseY <= backY + backHeight;

        guiGraphics.fill(
                backX,
                backY,
                backX + backWidth,
                backY + backHeight,
                backHovered
                        ? 0xFF3A3A3A
                        : 0xFF292929
        );

        guiGraphics.renderOutline(
                backX,
                backY,
                backWidth,
                backHeight,
                backHovered
                        ? 0xFFD09A3A
                        : 0xFF555555
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "НАЗАД"
                ),
                backX + backWidth / 2,
                backY + 8,
                backHovered
                        ? 0xFFFFFF
                        : 0xCCCCCC
        );

        /*
         * =====================================
         * СТАТУС
         * =====================================
         */

        if (!this.inviteStatus.isEmpty()) {

            guiGraphics.drawString(
                    this.font,
                    Component.literal(
                            this.inviteStatus
                    ),
                    left,
                    top + 60,
                    0xFF77DD77
            );
        }

        /*
         * =====================================
         * ИГРОКИ
         * =====================================
         */

        List<PlayerInfo> players =
                getInvitePlayers();

        int listX =
                left;

        int listY =
                top + 78;

        int rowHeight = 38;

        if (players.isEmpty()) {

            guiGraphics.drawString(
                    this.font,
                    Component.literal(
                            "Нет доступных игроков."
                    ),
                    listX,
                    listY,
                    0xAAAAAA
            );

            return;
        }

        for (int i = 0;
             i < players.size();
             i++) {

            PlayerInfo player =
                    players.get(i);

            int currentY =
                    listY
                            + i * rowHeight;

            boolean hovered =
                    mouseX >= listX
                            && mouseX <= listX + 290
                            && mouseY >= currentY
                            && mouseY <= currentY + 32;

            /*
             * Фон строки
             */

            guiGraphics.fill(
                    listX,
                    currentY,
                    listX + 290,
                    currentY + 32,
                    hovered
                            ? 0xFF303030
                            : 0xFF181818
            );

            guiGraphics.renderOutline(
                    listX,
                    currentY,
                    290,
                    32,
                    hovered
                            ? 0xFFD09A3A
                            : 0xFF3A3A3A
            );

            /*
             * Скин
             */

            PlayerSkin skin =
                    player.getSkin();

            ResourceLocation skinTexture =
                    skin.texture();

            guiGraphics.blit(
                    skinTexture,
                    listX + 4,
                    currentY + 2,
                    28,
                    28,
                    8,
                    8,
                    8,
                    8,
                    64,
                    64
            );

            /*
             * Имя
             */

            String playerName =
                    player.getProfile()
                            .getName();

            guiGraphics.drawString(
                    this.font,
                    Component.literal(
                            playerName
                    ),
                    listX + 40,
                    currentY + 7,
                    0xFFFFFF
            );

            guiGraphics.drawString(
                    this.font,
                    Component.literal(
                            "ПРИГЛАСИТЬ"
                    ),
                    listX + 190,
                    currentY + 7,
                    hovered
                            ? 0xFFD09A3A
                            : 0xAAAAAA
            );
        }
    }

    /*
     * =========================================
     * ОБЗОР
     * =========================================
     */

    private void renderOverview(
            GuiGraphics guiGraphics,
            int left,
            int top
    ) {

        int row =
                top + 45;

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "УРОВЕНЬ ГОРОДА"
                ),
                left,
                row,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        String.valueOf(
                                this.menu.getCityLevel()
                        )
                ),
                left + 220,
                row,
                0xFFFFFF
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ТЕРРИТОРИЯ"
                ),
                left,
                row + 35,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        this.menu.getTerritorySize()
                                + " × "
                                + this.menu.getTerritorySize()
                ),
                left + 220,
                row + 35,
                0xFFFFFF
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ЖИТЕЛИ"
                ),
                left,
                row + 70,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        String.valueOf(
                                this.menu.getMemberCount()
                        )
                ),
                left + 220,
                row + 70,
                0xFFFFFF
        );

        BlockPos pos =
                this.menu.getPylonPos();

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ГОРОДСКОЙ ПИЛОН"
                ),
                left,
                row + 105,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        pos.getX()
                                + ", "
                                + pos.getY()
                                + ", "
                                + pos.getZ()
                ),
                left + 220,
                row + 105,
                0xFFFFFF
        );
    }

    /*
     * =========================================
     * ТЕРРИТОРИЯ
     * =========================================
     */

    private void renderTerritory(
            GuiGraphics guiGraphics,
            int left,
            int top
    ) {

        int row =
                top + 45;

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ТЕКУЩИЙ РАЗМЕР"
                ),
                left,
                row,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        this.menu.getTerritorySize()
                                + " × "
                                + this.menu.getTerritorySize()
                ),
                left + 220,
                row,
                0xFFFFFF
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "УРОВЕНЬ"
                ),
                left,
                row + 35,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        String.valueOf(
                                this.menu.getCityLevel()
                        )
                ),
                left + 220,
                row + 35,
                0xFFFFFF
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "СЛЕДУЮЩЕЕ РАСШИРЕНИЕ"
                ),
                left,
                row + 70,
                0xAAAAAA
        );

        int nextSize =
                getNextTerritorySize();

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        nextSize
                                + " × "
                                + nextSize
                ),
                left + 220,
                row + 70,
                0xFFFFFF
        );

        int checkboxX =
                left;

        int checkboxY =
                row + 110;

        int boxSize = 14;

        guiGraphics.fill(
                checkboxX,
                checkboxY,
                checkboxX + boxSize,
                checkboxY + boxSize,
                0xFF1A1A1A
        );

        guiGraphics.renderOutline(
                checkboxX,
                checkboxY,
                boxSize,
                boxSize,
                0xFF6A6A6A
        );

        if (showTerritory) {

            guiGraphics.fill(
                    checkboxX + 3,
                    checkboxY + 3,
                    checkboxX + boxSize - 3,
                    checkboxY + boxSize - 3,
                    0xFFD09A3A
            );
        }

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ПОКАЗЫВАТЬ ГРАНИЦЫ"
                ),
                checkboxX + 22,
                checkboxY + 3,
                showTerritory
                        ? 0xFFFFFF
                        : 0xAAAAAA
        );
    }

    /*
     * =========================================
     * УЧАСТНИКИ
     * =========================================
     */

    private void renderMembers(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int mouseX,
            int mouseY
    ) {

        int row =
                top + 45;

        /*
         * Заголовок
         */

        String residentsLabel =
                "ЖИТЕЛИ ГОРОДА";

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        residentsLabel
                ),
                left,
                row,
                0xAAAAAA
        );

        int countX =
                left
                        + this.font.width(
                        residentsLabel
                )
                        + 8;

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        String.valueOf(
                                this.menu.getMemberCount()
                        )
                ),
                countX,
                row,
                0xFFFFFF
        );

        /*
         * =====================================
         * СПИСОК
         * =====================================
         */

        int cardX =
                left;

        int cardY =
                row + 28;

        int cardWidth =
                270;

        int cardHeight =
                48;

        int spacing =
                6;

        int visibleCount =
                2;

        int maxScroll =
                Math.max(
                        0,
                        this.menu.getMemberCount()
                                - visibleCount
                );

        if (memberScroll > maxScroll) {
            memberScroll = maxScroll;
        }

        int start =
                memberScroll;

        int end =
                Math.min(
                        start + visibleCount,
                        this.menu.getMemberCount()
                );

        for (int i = start; i < end; i++) {

            CityMember member =
                    this.menu
                            .getMembers()
                            .get(i);

            int visibleIndex =
                    i - start;

            int currentY =
                    cardY
                            + visibleIndex
                            * (cardHeight + spacing);

            renderMemberCard(
                    guiGraphics,
                    member,
                    cardX,
                    currentY,
                    cardWidth,
                    cardHeight
            );
        }

        /*
         * =====================================
         * SCROLLBAR
         * =====================================
         */

        if (this.menu.getMemberCount()
                > visibleCount) {

            int scrollbarX =
                    left + cardWidth + 8;

            int scrollbarTop =
                    cardY;

            int scrollbarHeight =
                    visibleCount
                            * (cardHeight + spacing)
                            - spacing;

            guiGraphics.fill(
                    scrollbarX,
                    scrollbarTop,
                    scrollbarX + 4,
                    scrollbarTop + scrollbarHeight,
                    0xFF292929
            );

            int total =
                    this.menu.getMemberCount();

            int thumbHeight =
                    Math.max(
                            12,
                            scrollbarHeight
                                    * visibleCount
                                    / total
                    );

            int available =
                    scrollbarHeight
                            - thumbHeight;

            int thumbY =
                    scrollbarTop;

            if (maxScroll > 0) {

                thumbY +=
                        available
                                * memberScroll
                                / maxScroll;
            }

            guiGraphics.fill(
                    scrollbarX,
                    thumbY,
                    scrollbarX + 4,
                    thumbY + thumbHeight,
                    0xFFD09A3A
            );
        }
    }

    /*
     * =========================================
     * КАРТОЧКА
     * =========================================
     */

    private void renderMemberCard(
            GuiGraphics guiGraphics,
            CityMember member,
            int cardX,
            int cardY,
            int cardWidth,
            int cardHeight
    ) {

        guiGraphics.fill(
                cardX,
                cardY,
                cardX + cardWidth,
                cardY + cardHeight,
                0xFF181818
        );

        CityMember.Role role =
                member.getRole();

        int roleColor =
                switch (role) {

                    case OWNER ->
                            0xFFD09A3A;

                    case ADMIN ->
                            0xFFCC5555;

                    case BUILDER ->
                            0xFF55AAFF;

                    case RESIDENT ->
                            0xFF777777;
                };

        guiGraphics.fill(
                cardX,
                cardY,
                cardX + 3,
                cardY + cardHeight,
                roleColor
        );

        PlayerInfo playerInfo =
                null;

        if (this.minecraft.getConnection()
                != null) {

            playerInfo =
                    this.minecraft
                            .getConnection()
                            .getPlayerInfo(
                                    member.getPlayerId()
                            );
        }

        boolean online =
                playerInfo != null;

        if (playerInfo != null) {

            PlayerSkin skin =
                    playerInfo.getSkin();

            ResourceLocation skinTexture =
                    skin.texture();

            guiGraphics.blit(
                    skinTexture,
                    cardX + 8,
                    cardY + 4,
                    40,
                    40,
                    8,
                    8,
                    8,
                    8,
                    64,
                    64
            );

        } else {

            guiGraphics.fill(
                    cardX + 8,
                    cardY + 4,
                    cardX + 48,
                    cardY + 44,
                    0xFF252525
            );
        }

        String playerName;

        if (playerInfo != null) {

            playerName =
                    playerInfo
                            .getProfile()
                            .getName();

        } else {

            playerName =
                    role == CityMember.Role.OWNER
                            ? "Владелец"
                            : "Участник";
        }

        guiGraphics.drawString(
                this.font,
                Component.literal(playerName),
                cardX + 58,
                cardY + 5,
                0xFFFFFF
        );

        int statusY =
                cardY + 19;

        guiGraphics.fill(
                cardX + 58,
                statusY + 1,
                cardX + 64,
                statusY + 7,
                online
                        ? 0xFF55CC55
                        : 0xFFCC5555
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        online
                                ? "ОНЛАЙН"
                                : "ОФФЛАЙН"
                ),
                cardX + 68,
                statusY,
                online
                        ? 0xFF77DD77
                        : 0xFFDD7777
        );

        String roleName =
                switch (role) {

                    case OWNER ->
                            "ВЛАДЕЛЕЦ";

                    case ADMIN ->
                            "АДМИНИСТРАТОР";

                    case BUILDER ->
                            "СТРОИТЕЛЬ";

                    case RESIDENT ->
                            "ЖИТЕЛЬ";
                };

        guiGraphics.drawString(
                this.font,
                Component.literal(roleName),
                cardX + 58,
                cardY + 34,
                roleColor
        );

        if (canManageMember(member)) {

            guiGraphics.drawString(
                    this.font,
                    Component.literal("⚙"),
                    cardX + cardWidth - 18,
                    cardY + 18,
                    0xFFD09A3A
            );
        }
    }

    /*
     * =========================================
     * ПРОЕКТЫ
     * =========================================
     */

    private void renderProjects(
            GuiGraphics guiGraphics,
            int left,
            int top
    ) {

        int row =
                top + 45;

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "ПРОЕКТЫ ГОРОДА"
                ),
                left,
                row,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        "АКТИВНЫЕ ПРОЕКТЫ"
                ),
                left,
                row + 35,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal("0"),
                left + 220,
                row + 35,
                0xFFFFFF
        );
    }

    /*
     * =========================================
     * УПРАВЛЕНИЕ УЧАСТНИКОМ
     * =========================================
     */

    private boolean canManageMember(
            CityMember member
    ) {

        if (this.minecraft == null
                || this.minecraft.player == null) {

            return false;
        }

        UUID currentPlayer =
                this.minecraft.player.getUUID();

        CityMember currentMember =
                this.menu.getMembers()
                        .stream()
                        .filter(
                                m -> m.getPlayerId()
                                        .equals(currentPlayer)
                        )
                        .findFirst()
                        .orElse(null);

        if (currentMember == null) {
            return false;
        }

        if (currentMember.getRole()
                == CityMember.Role.OWNER) {

            return !member.getPlayerId()
                    .equals(currentPlayer);
        }

        if (currentMember.getRole()
                == CityMember.Role.ADMIN) {

            return member.getRole()
                    != CityMember.Role.OWNER
                    && member.getRole()
                    != CityMember.Role.ADMIN;
        }

        return false;
    }

    /*
     * =========================================
     * СЛЕДУЮЩИЙ УРОВЕНЬ
     * =========================================
     */

    private int getNextTerritorySize() {

        return switch (
                this.menu.getCityLevel()
                ) {

            case 1 -> 150;

            case 2 -> 250;

            default -> 250;
        };
    }

    /*
     * =========================================
     * LABELS
     * =========================================
     */

    @Override
    protected void renderLabels(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {

        /*
         * Ничего не рисуем.
         */
    }

    /*
     * =========================================
     * ПОЛЮС ПРИГЛАШЕНИЯ
     * =========================================
     */

    public BlockPos getInvitePylonPos() {

        return this.menu.getPylonPos();
    }
}