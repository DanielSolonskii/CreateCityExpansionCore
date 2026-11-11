package com.example.examplemod.client.screen;

import com.example.examplemod.network.CityMapPeersPayload;
import com.example.examplemod.network.ClaimCityPlotPayload;
import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityMember;
import com.example.examplemod.client.CityBoundaryRenderer;
import com.example.examplemod.client.gui.CityButton;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.sound.SoundHelper;
import com.example.examplemod.sound.ModSounds;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.InvitePlayerPayload;
import com.example.examplemod.network.SyncCityMembersPayload;
import com.example.examplemod.network.CityDevelopmentPayload;
import com.example.examplemod.network.CityRelationsCommandPayload;
import com.example.examplemod.network.DecommissionCityProjectPayload;
import com.example.examplemod.network.RequestCityTerritoriesPayload;
import com.example.examplemod.city.CityColors;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.city.CityProjectSnapshot;
import com.example.examplemod.city.CityDevelopmentRequirementSnapshot;
import com.example.examplemod.city.CityDevelopmentRequirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;
import com.example.examplemod.client.uiedit.UiEditLayout;

public class CityPylonScreen
        extends AbstractContainerScreen<CityPylonMenu> {

private boolean showTerritory = false;

    private int memberScroll = 0;
    private int inviteScroll = 0;

private int activeTab = 0;
    private UUID decommissionConfirmation;

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

    /** Five player-facing hub destinations. Legacy activeTab values remain internal. */
    private final CityButton[] tabButtons = new CityButton[3];
    private static boolean cityHubGuideSeen;
    private boolean cityHubGuideVisible;
    private boolean problemCenterVisible;

private float interfaceScale = 1.0F;

private int helpContentHeight;

    /*
     * =========================================
     * ОБЛАСТИ МАКЕТА CITY PYLON
     * =========================================
     *
     * Все вкладки считают координаты относительно этих областей,
     * а не от абсолютных чисел, оставшихся от старого маленького GUI.
     *
     * ROOT     = весь прямоугольник PANEL_WIDTH x PANEL_HEIGHT
     * HEADER   = верхняя полоса (город, уровень, статус)
     * SIDEBAR  = колонка вкладок слева
     * CONTENT  = рабочая область справа от sidebar
     * FOOTER   = нижняя полоса внутри sidebar (кнопка «Закрыть»)
     */

    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 345;
    private static final int OUTER_PADDING = 12;
    private static final int HEADER_HEIGHT = 51;
    private static final int SIDEBAR_WIDTH = 160;
    private static final int CONTENT_OFFSET_X = 180;
    private static final int CONTENT_OFFSET_Y = 75;
    private static final int CONTENT_RIGHT = PANEL_WIDTH - 20;
    private static final int CONTENT_BOTTOM = PANEL_HEIGHT - OUTER_PADDING;
    private static final int CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_OFFSET_X;
    private static final int CONTENT_BODY_BOTTOM = CONTENT_BOTTOM - CONTENT_OFFSET_Y;
    private static final int PANEL_GAP = 8;

    /*
     * =========================================
     * ОПОРЫ КОНТЕНТА
     * =========================================
     *
     * Панель выросла, поэтому вкладки больше не могут считать
     * от «магических» 300 px: центр, колонка значений и половина
     * кнопки считаются из CONTENT_WIDTH один раз здесь.
     */
    private static final int CONTENT_CENTER = CONTENT_WIDTH / 2;
    private static final int CONTENT_VALUE_X = CONTENT_WIDTH - 96;
    private static final int HALF_W = (CONTENT_WIDTH - 10) / 2;
    private static final int HALF_W2 = HALF_W + 10;
    /** Подсказки не шире экрана и не шире панели. */
    private static final int MAX_TOOLTIP_WIDTH = 320;

    /*
     * =========================================
     * БРЕНД В HEADER
     * =========================================
     *
     * Небольшой знак проекта в правом верхнем углу шапки.
     * Это branding element, а не декоративная картинка:
     * он не трогает название города, уровень и статусы —
     * заголовок просто обрезается по его левой границе.
     *
     * Текстура подготовлена из исходной эмблемы
     * (textures/gui/city_emblem.png) и лежит отдельным
     * ресурсом, чтобы каждый кадр не грузить большую PNG.
     */
    private static final ResourceLocation HEADER_EMBLEM =
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "textures/gui/city_emblem_badge.png"
            );
    private static final int HEADER_EMBLEM_TEXTURE_SIZE = 64;
    private static final int HEADER_EMBLEM_SIZE = 32;
    private static final int HEADER_EMBLEM_Y = 9;
    private static final int HEADER_EMBLEM_GAP = 10;

/*
     * =========================================
     * ГЕОМЕТРИЯ ТЕЛА «УЧАСТНИКИ»
     * =========================================
     *
     * Один источник для всего, что рисуется и кликается после Management tabs:
     * toolbar с надписью и кнопкой «ПРИГЛАСИТЬ», строки участников (аватар,
     * ник, статус + роль, шестерёнка), clip, скроллбар и все hitbox'ы.
     * Вычисляется от фактического managementBodyTop, поэтому при переносе
     * русских tabs на два ряда всё тело сдвигается целиком, а не часть.
     */
    private CityPylonMembersLayout.MembersLayout membersLayout() {
        return CityPylonMembersLayout.compute(
                this.contentLeft(),
                this.contentTop(),
                CONTENT_WIDTH,
                this.contentTop() + CONTENT_BODY_BOTTOM,
                managementRows(),
                /*
                 * The row height comes from the real font.lineHeight, not from a frozen constant,
                 * so the two text lines always fit whatever font is loaded.
                 */
                this.font.lineHeight
        );
    }

    /**
     * The invite CTA is only offered when the viewer may actually invite, so the button never leads
     * to a refusal. Same permission source the member rows already use.
     */
    private boolean canInviteMembers() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        UUID currentPlayer = this.minecraft.player.getUUID();
        return this.menu.getMembers().stream()
                .filter(member -> member.getPlayerId().equals(currentPlayer))
                .findFirst()
                .map(member -> member.isOwner() || member.isAdmin())
                .orElse(false);
    }

    /* Инфраструктура: сколько строк помещается между заголовком и низом. */

/*
     * =========================================
     * ВНУТРЕННИЕ ТЕЛА ВКЛАДОК УПРАВЛЕНИЯ
     * =========================================
     *
     * Все смещения ниже отсчитываются от общего bodyTop, а не от
     * contentTop: раньше каждая вкладка держала собственные
     * «contentTop + N», что молчаливо предполагало один ряд
     * navigation. При переносе русских tabs на два ряда тела
     * начинали рисоваться поверх самих tabs.
     *
     * Проекты: подпись на bodyTop, карточки под ней.
     */
    private static final int PROJECT_CARD_TOP = 15;
    private static final int PROJECT_CARD_PITCH = 36;

    /*
     * Территория: подпись на bodyTop, чекбокс границы ниже. Обе величины
     * отсчитываются от общего bodyTop, поэтому рендер и клик совпадают.
     */
    private static final int TERRITORY_CHECKBOX_TOP = 110;

    /** Смещение кнопки «Изменить» относительно образца цвета. */
    private static final int COLOR_BUTTON_OFFSET = 74;

    /*
     * Развитие: шапка и требования — от bodyTop, полоса прогресса и
     * кнопки — от нижней границы контента, поэтому перенос navigation
     * сжимает список, а не выталкивает кнопки за панель.
     */
    private static final int DEV_PRIMARY_TOP = 25;
    private static final int DEV_ELECTRICAL_TOP = 55;
    private static final int DEV_ELECTRICAL_PITCH = 20;
    private static final int DEV_FOOTER_TOP = 44;
    private static final int DEV_BUTTON_H = 19;
    private static final int DEV_ANIMATION_TOP = 9;

    /*
     * Железная дорога. Список начинается под переключателем страниц и
     * заканчивается перед нижним рядом действий. Смещения считаются от
     * bodyTop, поэтому при переносе navigation весь блок сдвигается
     * целиком и остаётся внутри панели (самый нижний элемент — bodyTop+252).
     */

/** Detail-panel tail lines (next station, speed) need this much panel height. */

/*
     * Железная дорога меряется внутри общей рамки тела: верх от bodyTop,
     * нижний ряд действий — от нижней границы контента. Раньше верх стоял
     * на contentTop+24/51, то есть переключатель страниц наезжал на сами
     * tabs даже при одном ряде navigation.
     */

/** Bottom edge of the detail panel; the detail action row sits 6px below it. */

/** How many rows of {@code pitch} fit between the list top and the action row. */

/* Начало содержимого — один источник истины для всех вкладок. */
    private int contentLeft() {
        return this.leftPos + CONTENT_OFFSET_X;
    }

    private int contentTop() {
        return this.topPos + CONTENT_OFFSET_Y;
    }

    /**
     * Обрезка по границам области.
     * GuiGraphics ждёт координаты экрана, поэтому учитываем interfaceScale.
     */
    private void beginClip(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        CityPylonWorkspace.clip(graphics,
                Math.round(x1 * this.interfaceScale),
                Math.round(y1 * this.interfaceScale),
                Math.round(x2 * this.interfaceScale),
                Math.round(y2 * this.interfaceScale));
    }

    /**
     * Truncates a component so it can never be painted wider than {@code maxWidth}.
     *
     * <p>The old version returned the component untouched for narrow budgets, which meant a member
     * nickname or role could still be painted past its row's right edge. Now the result is always
     * bounded: the widest fitting prefix plus an ellipsis, and empty when even that does not fit.
     */
    private Component ellipsize(Component component, int maxWidth) {
        String text = component.getString();
        if (maxWidth <= 0) return Component.literal("");
        if (this.font.width(text) <= maxWidth) return component;
        String dots = "...";
        int dotsWidth = this.font.width(dots);
        if (dotsWidth >= maxWidth) {
            // Not even the ellipsis fits: take the longest prefix that does.
            return Component.literal(this.font.plainSubstrByWidth(text, maxWidth));
        }
        return Component.literal(
                this.font.plainSubstrByWidth(text, maxWidth - dotsWidth) + dots);
    }

    /*
     * =========================================
     * ОТЛОЖЕННЫЕ ПОДСКАЗКИ
     * =========================================
     *
     * Подсказка запоминается в момент отрисовки вкладки и выводится
     * уже после popPose(). Это решает три проблемы сразу:
     *
     *  1) её не режет scissor списка/карты;
     *  2) GuiGraphics клампит её по реальному экрану, а не по
     *     координатам, пересчитанным под interfaceScale;
     *  3) шрифт подсказки не мылится от дробного масштаба интерфейса.
     */
    private List<FormattedCharSequence> deferredTooltipLines;
    private ItemStack deferredTooltipStack;

    private int tooltipWrapWidth() {
        return Math.max(120, Math.min(MAX_TOOLTIP_WIDTH, this.width - 24));
    }

    private void deferTooltip(Component text) {
        if (this.deferredTooltipLines != null || this.deferredTooltipStack != null) return;
        this.deferredTooltipLines = this.font.split(text, tooltipWrapWidth());
    }

    private void deferTooltip(ItemStack stack) {
        if (this.deferredTooltipLines != null || this.deferredTooltipStack != null) return;
        if (stack == null || stack.isEmpty()) return;
        this.deferredTooltipStack = stack;
    }

    private void flushDeferredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack stack = this.deferredTooltipStack;
        List<FormattedCharSequence> lines = this.deferredTooltipLines;
        this.deferredTooltipStack = null;
        this.deferredTooltipLines = null;
        if (stack != null) {
            graphics.renderTooltip(this.font, stack, mouseX, mouseY);
        } else if (lines != null && !lines.isEmpty()) {
            graphics.renderTooltip(this.font, lines,
                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                    mouseX, mouseY);
        }
    }

    private int lastObservedCityLevel;
    private int developmentAnimationTicks = -1;

    private final CityMapPanel mapPanel;

    /**
     * Доступ к панели карты для сетевого обработчика соседних городов.
     */
    public CityMapPanel getMapPanel() {
        return this.mapPanel;
    }

    /**
     * Последний известный набор участков своего города.
     *
     * <p>Берётся из снимка территорий, а не из меню: снимок приходит с
     * сервера целиком и обновляется после каждого claim, тогда как меню
     * хранит только число участков. Нужен вкладке «Территория» и границе
     * в мире, чтобы обе показывали ровно то же, что показывает карта.
     */
    Set<CityPlot> ownTerritoryPlots() {
        CityMapPeersPayload.Peer own = null;
        for (CityMapPeersPayload.Peer peer : this.ownTerritoryCache) {
            if (peer.own()) {
                own = peer;
                break;
            }
        }
        return own != null ? own.plots() : Set.of();
    }

    /** Кеш последнего снимка территорий для своей карты. Обновляется серверным пакетом. */
    private List<CityMapPeersPayload.Peer> ownTerritoryCache = List.of();

    /**
     * Принимает снимок территорий и сразу обновляет границу в мире.
     *
     * <p>Снимок — единственный источник участков на клиенте, поэтому
     * карта, вкладка «Территория» и рендерер границы читают одно и то же
     * значение и не могут разойтись.
     */
    public void receiveTerritories(List<CityMapPeersPayload.Peer> peers) {
        this.ownTerritoryCache = List.copyOf(peers);
        /*
         * Граница обновляется сразу, а не в следующем тике: игрок сменил
         * цвет и ждёт реакции на экране, а не через 50 ms. Если граница
         * выключена, состояние всё равно запоминается, чтобы включение
         * позже показало актуальный цвет, а не тот, что был при последнем
         * обновлении снимка.
         */
        CityBoundaryRenderer.setVisible(
                this.showTerritory, ownTerritoryPlots(), this.menu.getColorRgb());
    }

    /**
     * Просит у сервера снимок территорий для этого пилона.
     *
     * <p>Вызывается при создании экрана и при переходе на вкладку «Карта»:
     * первое гарантирует данные для сетки и границы в мире, второе — свежие
     * данные, если город расширили, пока экран был открыт на другой вкладке.
     */
    private void requestTerritories() {
        PacketDistributor.sendToServer(new RequestCityTerritoriesPayload(this.menu.getPylonPos()));
    }

    /**
     * Стабильная ссылка на обработчик claim для панели карты.
     *
     * <p>Поле, а не новая лямбда в каждом тике: панель сравнивает
     * переданную ссылку с текущей, чтобы пересчитывать фронтир доступных
     * участков только при смене права. Лямбда каждый кадр сравнивалась бы
     * как «новая» и пересчитывала бы обход участков 20 раз в секунду.
     */
    private final java.util.function.LongConsumer plotClaimHandler = this::requestPlotClaim;

    void refreshMapClaimAccess() {
        var screen = this.minecraft == null ? null : this.minecraft.screen;
        boolean owningAtlas = screen instanceof PolishedWorldMapScreen atlas && atlas.getParentScreen() == this;
        boolean readOnlyAtlas = screen instanceof PolishedWorldMapScreen atlas && atlas.isReadOnly();
        boolean interactive = CityMapClaimAccess.allowed(this.menu.hasCity(),this.menu.canManageTerritory(),
                screen == this,CityPylonWorkspace.isEmbedded(this),owningAtlas,readOnlyAtlas);
        this.mapPanel.setPlotClaimRequest(interactive ? this.plotClaimHandler : null);
    }

    /**
     * Отправка подтверждённого claim на сервер.
     *
     * <p>Панель вызывает это только после явного подтверждения игроком, и
     * именно отсюда уходит пакет. Само подтверждение панель рисует сама:
     * отдельный экран закрывал собой ровно ту карту, ради которой игрок и
     * подтверждал участок.
     *
     * <p>Допустимость участка решает сервер: клиент рисует фронтир по
     * снимку, но не является источником правды.
     */
    private void requestPlotClaim(long packedPlot) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) {
            return;
        }
        this.minecraft.getConnection().send(new ClaimCityPlotPayload(
                this.menu.getPylonPos(),
                this.menu.getDimensionId(),
                (int) (packedPlot >> 32),
                (int) packedPlot
        ));
    }

/** Same as {@link #openTrainManagement(UUID)} but without pinning a specific train. */

public CityPylonScreen(
            CityPylonMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);

        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.mapPanel = new CityMapPanel(
                menu.getPylonPos(),
                menu.getDimensionId(),
                menu.getProjects()
        );
        /*
         * Снимок территорий мог прийти до создания экрана: сервер шлёт его
         * сразу после openMenu, а меню и пакет идут разными каналами. Без
         * этого карта осталась бы без заливки и фронтира до переоткрытия
         * пилона, а значит и ПКМ по участку нечего было бы принимать.
         */
        com.example.examplemod.client.CityTerritorySnapshot.applyPending(this);
        /*
         * Снимок территорий переспрашивается при открытии экрана, а не
         * только принимается один раз при openMenu. Экран к этому моменту
         * уже существует, поэтому ответ придёт в гарантированном порядке
         * и гонки «пакет раньше экрана» не будет: без неё карта оставалась
         * без заливки и без фронтира, то есть ПКМ было нечего принимать.
         */
        requestTerritories();
        this.lastObservedCityLevel = menu.getCityLevel();

        /*
         * Однократный звук пробуждения экрана при открытии.
         * Играется в конструкторе, а не в render(), поэтому
         * не повторяется каждый кадр.
         */
        SoundHelper.playUi(ModSounds.PYLON_SCREEN_WAKE.get());
    }

    /** Switch a workspace section without replacing the server-owned menu. */
    /** Map snapshots are shared with the full-screen atlas; no screen frame is rendered here. */
    void workspaceSyncMapData(){
        refreshMapClaimAccess();this.menu.drainMapTiles().forEach(this.mapPanel::receive);
        this.mapPanel.setProjects(this.menu.getProjects());
        if(this.menu.getRelationsState()!=null)this.mapPanel.setRelations(this.menu.getRelationsState().cities());
    }

    Component workspaceMemberNames() {
        String names=this.menu.getMembers().stream().limit(5).map(this::getMemberName)
                .reduce((a,b)->a+"  •  "+b).orElse("—");
        if(this.menu.getMemberCount()>5)names+="  +"+(this.menu.getMemberCount()-5);
        return Component.literal(names);
    }

    void prepareWorkspaceContent() {
        this.interfaceScale=1.0F;
        for(CityButton button:this.tabButtons)if(button!=null){button.visible=false;button.active=false;}
        if(this.closeButton!=null){this.closeButton.visible=false;this.closeButton.active=false;}

        updateInviteButton();
    }

    /** Paint only the selected management body. Never call AbstractContainerScreen.render here. */
    void renderWorkspaceContent(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        prepareWorkspaceContent();
        workspaceSyncMapData();
        int left=this.contentLeft(),top=this.contentTop();
        CityPylonTheme.panel(graphics,left-6,top-5,440,260);
        graphics.drawString(this.font,managementTabName(this.activeTab),left,top,CityPylonTheme.TEXT,false);
        graphics.fill(left,top+16,left+CONTENT_WIDTH,top+17,CityPylonTheme.LINE);
        renderManagementNav(graphics,left,CityPylonUiLayout.navTop(top),mouseX,mouseY);
        switch(this.activeTab) {
            case 1->renderTerritory(graphics,left,top,mouseX,mouseY);
            case 2->renderMembers(graphics,left,top,mouseX,mouseY);
            case 3->renderProjects(graphics,left,top);
            case 5->renderDevelopment(graphics,left,top,mouseX,mouseY);

            default->{}
        }
        if(this.activeTab==2&&this.inviteButton!=null&&this.inviteButton.visible)
            this.inviteButton.render(graphics,mouseX,mouseY,partialTick);
        flushDeferredTooltip(graphics,mouseX,mouseY);
    }

    int workspaceSection() { return this.activeTab == 0 ? 0 : 1; }

    void workspaceSelect(int tab) {
        this.activeTab = tab;
        this.cityHubGuideVisible = false;
        this.problemCenterVisible = false;

        this.decommissionConfirmation = null;
        updateTabButtons();
        updateInviteButton();

}

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!CityPylonWorkspace.isEmbedded(this)) super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void init() {

        super.init();
        if (!this.menu.hasCity()) {
            this.minecraft.setScreen(new CityCreationScreen(this));
            return;
        }
        this.interfaceScale = Math.min(1.0F, Math.min(
                (this.width - 12.0F) / this.imageWidth,
                (this.height - 12.0F) / this.imageHeight));
        if (this.interfaceScale < 1.0F) {
            this.leftPos = Math.round((this.width / this.interfaceScale - this.imageWidth) / 2.0F);
            this.topPos = Math.round((this.height / this.interfaceScale - this.imageHeight) / 2.0F);
        }
        // UI-редактор (F7): дочерние виджеты рисуются внутри pose().scale(interfaceScale),
        // их координаты логические — сообщаем масштаб для обводки и попаданий мышью.
        UiEditLayout.registerTransform(this, this.interfaceScale, 0f, 0f);

        /*
         * GuiGraphics.enableScissor работает в экранных координатах,
         * а сам панель рисуется внутри pose().scale(interfaceScale).
         * Передаём масштаб внутрь, чтобы обрезка карты была точной.
         */
        this.mapPanel.setGuiScale(this.interfaceScale);

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
                            this.topPos + PANEL_HEIGHT / 2 - 13,
                            PANEL_WIDTH - 40,
                            26,
                            Component.translatable(
                                    "gui.createcityexpansioncore.city.create"
                            ),
                            button -> {

                                if (this.minecraft != null) {

                                    this.minecraft.setScreen(
                                            new CityCreationScreen(
                                                    this
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
                        this.leftPos + 10,
                        this.topPos + PANEL_HEIGHT - 36,
                        140,
                        26,
                        Component.translatable("gui.createcityexpansioncore.common.close"),
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

        Component[] tabNames={Component.translatable("gui.createcityexpansioncore.city.tab.overview"),Component.translatable("gui.createcityexpansioncore.city.tab.management"),Component.translatable("gui.createcityexpansioncore.city.tab.map")};

for (int i = 0; i < this.tabButtons.length; i++) {

            final int tabIndex = i;

            CityPylonUiLayout.Rect navRow =
                    CityPylonUiLayout.navRow(
                            this.leftPos + 10,
                            this.topPos + 54,
                            i);

            this.tabButtons[i]=new CityButton(navRow.x(),navRow.y(),navRow.width(),navRow.height(),tabNames[i],button->{
                if(tabIndex==2){if(this.minecraft!=null)this.minecraft.setScreen(new PolishedWorldMapScreen(this));return;}
                SoundHelper.playUi(ModSounds.UI_TAB_SWITCH.get());
                this.activeTab=tabIndex==0?0:isManagementTab(this.activeTab)?this.activeTab:2;
                this.showInvitePlayers = false;this.inviteStatus="";this.decommissionConfirmation=null;
                this.memberScroll=0;updateTabButtons();updateInviteButton();
            });
            this.tabButtons[i].setIcon(navigationIcon(i));

            this.tabButtons[i].setActive(
                    hubTab() == i
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

            /*
             * Кнопка берёт свои bounds из той же геометрии, которой
             * пользуется renderMembers(): toolbar ниже Management tabs,
             * а не фиксированный Y от contentTop.
             */
            CityPylonMembersLayout.Rect inviteBounds =
                    this.membersLayout().invite();

            this.inviteButton =
                    new CityButton(
                            inviteBounds.x(),
                            inviteBounds.y(),
                            inviteBounds.width(),
                            inviteBounds.height(),
                            Component.translatable(
                                    "gui.createcityexpansioncore.city.invite"
                            ),
                            button -> {

                                /*
                                 * The single invite entry point. It opens the invite screen that already
                                 * exists, which searches on the server and therefore also finds offline
                                 * players; the old inline online-only list is no longer a second way in.
                                 */

                                if (this.minecraft != null) {

                                    this.minecraft.setScreen(
                                            new CityInvitePlayerScreen(
                                                    this
                                            )
                                    );
                                }
                            }
                    );

            this.addRenderableWidget(
                    this.inviteButton
            );

            updateInviteButton();
        }

        updateTabButtonsVisibility();
        this.cityHubGuideVisible = !CityPylonWorkspace.isEmbedded(this) && !cityHubGuideSeen;

PacketDistributor.sendToServer(new CityRelationsCommandPayload(
                this.menu.getPylonPos(), CityRelationsCommandPayload.Action.REQUEST_STATE, null));
    }

    /*
     * =========================================
     * ОБНОВЛЕНИЕ ВКЛАДОК
     * =========================================
     */

    /**
     * Label of a management sub-tab, by tab id. {@code -1} is the relations entry, which opens its own
     * screen, so it still needs a label for measuring and for the row.
     */

private ItemStack navigationIcon(int index){return new ItemStack(index==0?CreateCityExpansionCore.CITY_PYLON_ITEM.get():index==1?net.minecraft.world.item.Items.COMPARATOR:net.minecraft.world.item.Items.COMPASS);}

private String managementTabName(int tab) {
        return switch (tab) {
            case 1 -> tr("gui.createcityexpansioncore.city.tab.territory");
            case 2 -> tr("gui.createcityexpansioncore.city.tab.members");
            case 3 -> tr("gui.createcityexpansioncore.city.tab.projects");
            case 5 -> tr("gui.createcityexpansioncore.city.tab.development");

            default -> tr("gui.createcityexpansioncore.city.tab.relations");
        };
    }

    private void updateTabButtons() {

        for (int i = 0;
             i < this.tabButtons.length;
             i++) {

            if (this.tabButtons[i] != null) {

                this.tabButtons[i].setActive(
                        hubTab() == i
                );
            }
        }

        updateTabButtonsVisibility();
    }

    private int hubTab(){return this.activeTab==0?0:this.activeTab==4?2:1;}

    private static boolean isManagementTab(int tab) {
        return tab == 1 || tab == 2 || tab == 3 || tab == 5;
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

        /*
         * Визуальная кнопка — настоящий widget, поэтому её позиция и размер
         * всегда пересинхронизируются из membersLayout(): иначе фиксированный
         * X/Y из init() разъехался бы с toolbar после пересчёта layout.
         */
        CityPylonMembersLayout.Rect bounds =
                this.membersLayout().invite();

        this.inviteButton.visible =
                !this.showInvitePlayers && this.activeTab == 2 && canInvitePlayers();
        if (this.inviteButton.visible) {
            bounds = CityPylonWorkspace.pageControl(this, "members.invite",
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(), CityPylonMembersLayout.Rect::new);
        }
        this.inviteButton.setX(bounds.x());
        this.inviteButton.setY(bounds.y());
        this.inviteButton.setWidth(bounds.width());
        this.inviteButton.setHeight(bounds.height());
    }

    @Override
    public void onClose() {

        /*
         * Звук засыпания экрана, один раз за закрытие.
         * onClose вызывается один раз, а не каждый кадр.
         */
        SoundHelper.playUi(ModSounds.PYLON_SCREEN_SLEEP.get());

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
        mouseX /= this.interfaceScale;
        mouseY /= this.interfaceScale;

        /*
         * Если открыто приглашение,
         * скроллим список игроков.
         */

        if (this.showInvitePlayers) {

            List<PlayerInfo> invitePlayers = getInvitePlayers();
            int visibleRows = Math.max(1, (CONTENT_BODY_BOTTOM - 78) / 38);
            if (invitePlayers.size() > visibleRows) {
                this.inviteScroll = Math.clamp(
                        this.inviteScroll + (scrollY < 0 ? 1 : -1),
                        0,
                        invitePlayers.size() - visibleRows);
                return true;
            }

            return super.mouseScrolled(
                    mouseX,
                    mouseY,
                    scrollX,
                    scrollY
            );
        }

        if (this.activeTab == 2
                && this.menu.hasCity()) {

            /*
             * Колесо скроллит тот же clip, который режет renderMembers():
             * одна и та же MembersLayout-геометрия.
             */
            CityPylonMembersLayout.MembersLayout layout =
                    this.membersLayout();

            CityPylonMembersLayout.Rect clip =
                    layout.listClip();

            int memberCount =
                    this.menu.getMemberCount();

            if (mouseX >= clip.x()
                    && mouseX <= clip.right() + CityPylonMembersLayout.SCROLLBAR_GAP
                    && mouseY >= clip.y()
                    && mouseY <= clip.bottom()) {

                if (scrollY > 0) {

                    memberScroll =
                            layout.clampScroll(
                                    memberScroll - 1,
                                    memberCount
                            );
                }

                if (scrollY < 0) {

                    memberScroll =
                            layout.clampScroll(
                                    memberScroll + 1,
                                    memberCount
                            );
                }

                return true;
            }
        }

        if (this.activeTab == 4
                && this.menu.hasCity()
                && this.mapPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
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

    /**
     * Escape снимает открытое подтверждение claim, не закрывая экран.
     *
     * <p>Подтверждение — намерение внутри карты, а не отдельное окно, поэтому
     * Escape должен отменять именно его. Раньше Escape закрывал City Pylon
     * целиком, и случайно нажатый Escape терял подготовленный claim.
     */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (this.mapPanel.hasPendingClaim()) {
            boolean cancel = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
            boolean confirm = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
            if (cancel) {
                this.mapPanel.cancelPendingClaim();
                return true;
            }
            if (confirm) {
                this.mapPanel.confirmPendingClaim();
                return true;
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        mouseX /= this.interfaceScale;
        mouseY /= this.interfaceScale;

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
                    this.leftPos + CONTENT_OFFSET_X + CONTENT_WIDTH - 140;

            int backY =
                    this.topPos + CONTENT_OFFSET_Y + 23;

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
                    this.contentLeft();

            int listY =
                    this.contentTop() + 78;

            int rowHeight = 38;

            int visibleRows = Math.max(1,
                    (CONTENT_BODY_BOTTOM - 78) / rowHeight);

            for (int i = inviteScroll;
                 i < players.size();
                 i++) {

                int currentY =
                        listY
                                + (i - inviteScroll) * rowHeight;

                if (currentY > this.topPos + CONTENT_OFFSET_Y + CONTENT_BODY_BOTTOM) {
                    break;
                }

                if (mouseX >= listX
                        && mouseX <= listX + CONTENT_WIDTH
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

            int checkboxY = managementBody(this.contentTop()).top() + TERRITORY_CHECKBOX_TOP;
            boolean clicked = territoryControl(this.contentLeft(), checkboxY).contains(mouseX, mouseY);

            if (clicked) {

                this.showTerritory =
                        !this.showTerritory;

                /*
                 * Граница рисуется по тем же участкам, что и карта:
                 * снимок территорий, а не старый квадрат вокруг пилона.
                 */
                CityBoundaryRenderer.setVisible(
                        this.showTerritory,
                        ownTerritoryPlots(),
                        this.menu.getColorRgb()
                );

                return true;
            }

            /*
             * Палитра цвета города. Кнопка есть только у OWNER/ADMIN —
             * тем же условием, что и на сервере, поэтому клик не может
             * привести к заведомо отклонённому запросу. Право приходит с
             * сервером в состоянии города и обновляется при каждом
             * изменении, а не вычисляется на клиенте.
             */
            if (this.menu.canManageTerritory()
                    && paletteControl().contains(mouseX, mouseY)) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new CityColorPaletteScreen(
                            this, this.menu.getPylonPos(), this.menu.getColorRgb()));
                }
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

        if (button == 0 && this.activeTab == 0) {
            int left = this.contentLeft(), top = this.contentTop();
            if (this.problemCenterVisible) {
                if (inside(mouseX, mouseY, left + 315, top + 218, 112, 20)) {
                    this.problemCenterVisible = false;

                }
                return true;
            }
            if (this.cityHubGuideVisible) {
                this.cityHubGuideVisible = false; cityHubGuideSeen = true; return true;
            }
            if (inside(mouseX, mouseY, left + 352, top - 3, 24, 20)) {
                this.problemCenterVisible = true; return true;
            }
            if (inside(mouseX, mouseY, left + 382, top - 3, 24, 20)) {
                this.cityHubGuideVisible = true; return true;
            }
            if (inside(mouseX, mouseY, left + 406, top + 32, 29, 23)) {
                if (this.minecraft != null) this.minecraft.setScreen(new PolishedWorldMapScreen(this));
                return true;
            }
        }

        if (button == 0 && isManagementTab(this.activeTab)) {
            CityPylonUiLayout.Rect[] cells = managementTabs(
                    this.contentLeft(), CityPylonUiLayout.navTop(this.contentTop()));
            for (int i = 0; i < MANAGEMENT_TABS.length; i++) {
                if (!cells[i].contains(mouseX, mouseY)) continue;
                if (MANAGEMENT_TABS[i] == -1) {
                    if (this.minecraft != null) CommunityScreen.openRelations(this);
                } else {
                    this.activeTab = MANAGEMENT_TABS[i];
                    updateTabButtons();
                    updateInviteButton();
                }
                return true;
            }
        }

        if (this.activeTab == 4
                && this.mapPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (this.activeTab == 4 && button == 0
                && inside(mouseX, mouseY, this.contentLeft() + CONTENT_WIDTH - 104,
                this.contentTop() - 3, 104, 20)) {
            if (this.minecraft != null) this.minecraft.setScreen(new PolishedWorldMapScreen(this));
            return true;
        }

if (this.activeTab == 3 && button == 0) {
            int contentLeft = this.contentLeft();
            // The same body-relative origin renderProjects() paints, so hit and paint agree.
            int body = managementBody(this.contentTop()).top();
            List<CityProjectSnapshot> projects = this.menu.getProjects();
            for (int index = 0; index < Math.min(2, projects.size()); index++) {
                int cardY = body + PROJECT_CARD_TOP + index * PROJECT_CARD_PITCH;
                if (projectAction(contentLeft, cardY, index).contains(mouseX, mouseY)) {
                    CityProjectSnapshot project = projects.get(index);
                    if (project.projectId().equals(this.decommissionConfirmation)) {
                        PacketDistributor.sendToServer(new DecommissionCityProjectPayload(
                                this.menu.getPylonPos(), project.projectId()
                        ));
                        this.decommissionConfirmation = null;
                    } else {
                        this.decommissionConfirmation = project.projectId();
                    }
                    return true;
                }
            }

        }

        if (this.activeTab == 5 && button == 0) {
            if (this.developmentAnimationTicks >= 0) {
                return true;
            }
            int contentLeft = this.contentLeft();
            // Same footer the painter uses: bottom-anchored to the content bottom of the body frame.
            int buttonY = managementBody(this.contentTop()).bottom() - DEV_BUTTON_H;
            if (developmentAction(contentLeft, buttonY, false).contains(mouseX, mouseY)) {
                PacketDistributor.sendToServer(new CityDevelopmentPayload(
                        this.menu.getPylonPos(), CityDevelopmentPayload.Action.CONTRIBUTE
                ));
                return true;
            }
            if (developmentAction(contentLeft, buttonY, true).contains(mouseX, mouseY)
                    && this.menu.areDevelopmentRequirementsComplete() && this.menu.getCityLevel() < 2) {
                PacketDistributor.sendToServer(new CityDevelopmentPayload(
                        this.menu.getPylonPos(), CityDevelopmentPayload.Action.COMPLETE_LEVEL_TWO
                ));
                return true;
            }
        }

        /*
         * =========================================
         * УПРАВЛЕНИЕ УЧАСТНИКОМ
         * =========================================
         */

        if (this.menu.hasCity()
                && this.activeTab == 2
                && button == 0) {

            /*
             * Та же геометрия, что и при отрисовке: gear-прямоугольник
             * берётся из MembersLayout, а не пересчитывается заново.
             */
            CityPylonMembersLayout.MembersLayout layout =
                    this.membersLayout();

            int start =
                    this.memberScroll;

            int end =
                    Math.min(
                            start + layout.visibleRows(),
                            this.menu.getMemberCount()
                    );

            for (int i = start; i < end; i++) {

                CityMember member =
                        this.menu
                                .getMembers()
                                .get(i);

                int visibleIndex =
                        i - start;

                var row = memberControl("row", visibleIndex, layout.row(visibleIndex));

                /*
                 * Hitbox ровно по нарисованной иконке.
                 */
                boolean clicked = canManageMember(member) && memberGear(layout, visibleIndex, row).contains(mouseX, mouseY);

                if (clicked) {

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
                if (layout.listClip().contains(mouseX, mouseY)
                        && row.contains(mouseX, mouseY)) {
                    CommunityScreen.openMember(this, member.getPlayerId());
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

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        mouseX /= this.interfaceScale;
        mouseY /= this.interfaceScale;
        dragX /= this.interfaceScale;
        dragY /= this.interfaceScale;
        if (this.activeTab == 4
                && this.menu.hasCity()
                && this.mapPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX /= this.interfaceScale;
        mouseY /= this.interfaceScale;
        if (this.mapPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
                displayedPlayerName(playerInfo);

        PacketDistributor.sendToServer(
                new InvitePlayerPayload(
                        this.menu.getPylonPos(),
                        playerId
                )
        );

        this.inviteStatus = tr(
                "gui.createcityexpansioncore.city.invite_sent", playerName
        );
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

    private String getMemberName(CityMember member) {
        if (!member.getDisplayName().isBlank()
                && !member.getDisplayName().matches("cs[a-z0-9]{14}")) return member.getDisplayName();
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(member.getPlayerId());
            if (info != null) {
                String name = displayedPlayerName(info);
                if (!name.matches("cs[a-z0-9]{14}")) return name;
            }
        }
        return "Player " + member.getPlayerId().toString().substring(0, 8);
    }

    private static String displayedPlayerName(PlayerInfo info) {
        Component display = info.getTabListDisplayName();
        return display != null ? display.getString() : info.getProfile().getName();
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
                0xFFB78A43
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
                left + 160,
                top + this.imageHeight,
                0xFF181818
        );

        /*
         * Разделитель
         */

        guiGraphics.fill(
                left + 160,
                top + 51,
                left + 161,
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

        workspaceSyncMapData();

        this.renderBackground(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        final int screenMouseX = mouseX;
        final int screenMouseY = mouseY;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(this.interfaceScale, this.interfaceScale, 1.0F);
        mouseX = Math.round(mouseX / this.interfaceScale);
        mouseY = Math.round(mouseY / this.interfaceScale);

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

        /*
         * =====================================
         * ЭМБЛЕМА (BRANDING)
         * =====================================
         *
         * Знак рисуется первым и занимает фиксированный угол HEADER,
         * поэтому он не может наехать на название города или уровень:
         * заголовок ниже обрезается по левой границе знака.
         */
        int emblemX =
                left + PANEL_WIDTH - OUTER_PADDING - HEADER_EMBLEM_SIZE;

guiGraphics.drawString(
                this.font,
                ellipsize(
                        this.menu.hasCity()
                                ? Component.literal(this.menu.getCityName())
                                : Component.translatable("gui.createcityexpansioncore.city.pylon"),
                        emblemX - (left + 15) - HEADER_EMBLEM_GAP
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
                    Component.translatable("gui.createcityexpansioncore.city.not_created"),
                    left + 20,
                    top + 55,
                    0xFFFFFF
            );

            guiGraphics.drawString(
                    this.font,
                    Component.translatable("gui.createcityexpansioncore.city.pylon_hint"),
                    left + 20,
                    top + 80,
                    0xAAAAAA
            );

            guiGraphics.pose().popPose();
            return;
        }

        /*
         * =====================================
         * ПОДЗАГОЛОВОК
         * =====================================
         */

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.management"),
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
                    left + 180,
                    top + 75,
                    mouseX,
                    mouseY
            );

            guiGraphics.pose().popPose();
            return;
        }

        /*
         * =====================================
         * КОНТЕНТ
         * =====================================
         */

        int contentLeft =
                left + 180;

        int contentTop =
                top + 75;

        Component title;

        switch (this.activeTab) {

            case 1 ->
                    title = Component.translatable("gui.createcityexpansioncore.city.territory");

            case 2 ->
                    title = Component.translatable("gui.createcityexpansioncore.city.members");

            case 3 ->
                    title = Component.translatable("gui.createcityexpansioncore.city.projects");

            case 4 ->
                    title = Component.translatable("gui.createcityexpansioncore.city.map");

            case 5 ->
                    title = Component.translatable("gui.createcityexpansioncore.city.development");

default ->
                    title = Component.translatable("gui.createcityexpansioncore.city.overview");
        }

        guiGraphics.drawString(
                this.font,
                title,
                contentLeft,
                contentTop,
                0xFFFFFF
        );

        guiGraphics.fill(
                contentLeft,
                CityPylonUiLayout.divider(contentLeft, contentTop, this.imageWidth - 20).y(),
                left + this.imageWidth - 20,
                CityPylonUiLayout.divider(contentLeft, contentTop, this.imageWidth - 20).bottom(),
                0xFFB78A43
        );

        if (isManagementTab(this.activeTab)) {
            renderManagementNav(guiGraphics, contentLeft, CityPylonUiLayout.navTop(contentTop), mouseX, mouseY);
        }

        switch (this.activeTab) {

            case 0 ->
                    renderOverview(
                            guiGraphics,
                            contentLeft,
                            contentTop,
                            mouseX,
                            mouseY
                    );

            case 1 ->
                    renderTerritory(
                            guiGraphics,
                            contentLeft,
                            contentTop,
                            mouseX,
                            mouseY
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

            case 4 -> {
                    this.mapPanel.render(
                            guiGraphics,
                            contentLeft,
                            contentTop + 25,
                            CONTENT_WIDTH,
                            CONTENT_BODY_BOTTOM - 25,
                            mouseX,
                            mouseY
                    );
                    int atlasX = contentLeft + CONTENT_WIDTH - 104;
                    int atlasY = contentTop - 3;
                    boolean atlasHover = inside(mouseX, mouseY, atlasX, atlasY, 104, 20);
                    guiGraphics.fill(atlasX, atlasY, atlasX + 104, atlasY + 20,
                            atlasHover ? 0xFF4A3A20 : 0xFF303638);
                    guiGraphics.renderOutline(atlasX, atlasY, 104, 20,
                            atlasHover ? 0xFFB78A43 : 0xFF596064);
                    guiGraphics.drawCenteredString(this.font,
                            Component.translatable("gui.createcityexpansioncore.world_map.open"),
                            atlasX + 52, atlasY + 6, atlasHover ? 0xFFFFFFFF : 0xFFD3D8DA);
            }

            case 5 ->
                    renderDevelopment(
                            guiGraphics,
                            contentLeft,
                            contentTop,
                            mouseX,
                            mouseY
                    );

}

        if (this.activeTab == 0) renderHubOverlays(guiGraphics, contentLeft, contentTop, mouseX, mouseY);
        guiGraphics.pose().popPose();
        flushDeferredTooltip(guiGraphics, screenMouseX, screenMouseY);
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
                Component.translatable("gui.createcityexpansioncore.city.invite_player"),
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
                0xFFB78A43
        );

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.common.online"),
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
                left + CONTENT_WIDTH - 140;

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
                        ? 0xFFB78A43
                        : 0xFF555555
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.common.back"),
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
                    Component.translatable("gui.createcityexpansioncore.city.no_players"),
                    listX,
                    listY,
                    0xAAAAAA
            );

            return;
        }

        int visibleRows = Math.max(1,
                (this.contentTop() + CONTENT_BODY_BOTTOM - listY) / rowHeight);
        inviteScroll = Math.clamp(inviteScroll, 0, Math.max(0, players.size() - visibleRows));

        beginClip(guiGraphics, listX, listY, listX + CONTENT_WIDTH,
                this.contentTop() + CONTENT_BODY_BOTTOM);

        for (int i = inviteScroll;
             i < Math.min(players.size(), inviteScroll + visibleRows + 1);
             i++) {

            PlayerInfo player =
                    players.get(i);

            int currentY =
                    listY
                            + (i - inviteScroll) * rowHeight;

            boolean hovered =
                    mouseX >= listX
                            && mouseX <= listX + CONTENT_WIDTH
                            && mouseY >= currentY
                            && mouseY <= currentY + 32;

            /*
             * Фон строки
             */

            guiGraphics.fill(
                    listX,
                    currentY,
                    listX + CONTENT_WIDTH,
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
                            ? 0xFFB78A43
                            : 0xFF3A3A3A
            );

            /*
             * Скин
             */

            PlayerSkin skin =
                    player.getSkin();

            ResourceLocation skinTexture =
                    skin.texture();

/*
             * Имя
             */

            String playerName =
                    displayedPlayerName(player);

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
                    Component.translatable("gui.createcityexpansioncore.city.invite"),
                    listX + 190,
                    currentY + 7,
                    hovered
                            ? 0xFFB78A43
                            : 0xAAAAAA
            );
        }

        guiGraphics.disableScissor();
    }

    /*
     * =========================================
     * ОБЗОР
     * =========================================
     */

    private void renderOverview(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int mouseX,
            int mouseY
    ) {

        int problems=0;
        int statusY = top + 27;
        guiGraphics.fill(left, statusY, left + 206, statusY + 42, problems > 0 ? 0xFF34282A : 0xFF21322D);
        guiGraphics.renderOutline(left, statusY, 206, 42, problems > 0 ? 0xFFC86D68 : 0xFF5AB890);
        guiGraphics.drawString(this.font, Component.translatable(problems > 0
                        ? "gui.createcityexpansioncore.city.hub.action_required"
                        : "gui.createcityexpansioncore.city.hub.ready"),
                left + 9, statusY + 7, problems > 0 ? 0xFFFFB0A9 : 0xFF9FE1C0, false);
        Component next=Component.translatable(nextStepKey());
        List<net.minecraft.util.FormattedCharSequence> nextLines=this.font.split(next,188);
        for(int i=0;i<Math.min(2,nextLines.size());i++)guiGraphics.drawString(this.font,nextLines.get(i),left+9,statusY+21+i*10,0xFFD0D5D6,false);

        hubKpi(guiGraphics, left, top + 76, 98, Component.translatable("gui.createcityexpansioncore.city.hub.plots"),
                Integer.toString(this.menu.getPlotCount()));
        hubKpi(guiGraphics, left + 108, top + 76, 98, Component.translatable("gui.createcityexpansioncore.city.hub.projects"), Integer.toString(this.menu.getProjects().size()));
        hubKpi(guiGraphics, left, top + 116, 98, Component.translatable("gui.createcityexpansioncore.city.hub.level"), Integer.toString(this.menu.getCityLevel()));
        hubKpi(guiGraphics, left + 108, top + 116, 98, Component.translatable("gui.createcityexpansioncore.city.hub.members"), Integer.toString(this.menu.getMemberCount()));
        hubKpi(guiGraphics, left, top + 156, 98, Component.translatable("gui.createcityexpansioncore.city.hub.level"), Integer.toString(this.menu.getCityLevel()));
        hubKpi(guiGraphics, left + 108, top + 156, 98, Component.translatable("gui.createcityexpansioncore.city.hub.members"), Integer.toString(this.menu.getMemberCount()));

        this.mapPanel.setDrawInternalControls(false);
        this.mapPanel.render(guiGraphics, left + 216, top + 27, 224, 171, -1000, -1000);

        // The overview map is a calm preview. Its own layer chips (TER / PR / PUTI / ST / PZ) and
        // "k centru" belonged to the old cut-down GIS panel and are gone with the internal controls;
        // the full World Map keeps them off too and owns the real modes.
        int openMapX = left + 216, openMapY = top + 202;
        boolean openMapHover = inside(mouseX, mouseY, openMapX, openMapY, 224, 18);
        guiGraphics.fill(openMapX, openMapY, openMapX + 224, openMapY + 18,
                openMapHover ? 0xFF4A3A20 : 0xFF292E30);
        guiGraphics.renderOutline(openMapX, openMapY, 224, 18, 0xFF596064);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.createcityexpansioncore.city.hub.open_map"),
                openMapX + 112, openMapY + 5, openMapHover ? 0xFFFFD59A : 0xFFB9C1C3);

        guiGraphics.fill(left + 406, top + 32, left + 435, top + 55, 0xDD20282A);
        guiGraphics.renderOutline(left + 406, top + 32, 29, 23, 0xFFB78A43);
        guiGraphics.drawCenteredString(this.font, "?", left + 420, top + 39, 0xFFFFFFFF);

        guiGraphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.members_list"),
                left, top + 211, 0xFFB78A43, false);
        String names = this.menu.getMembers().stream().limit(5).map(this::getMemberName)
                .reduce((a, b) -> a + "  •  " + b).orElse("—");
        if (this.menu.getMemberCount() > 5) names += "  +" + (this.menu.getMemberCount() - 5);
        guiGraphics.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(names, CONTENT_WIDTH)),
                left, top + 226, 0xFFB9C1C3, false);
    }

    private void hubKpi(GuiGraphics graphics, int x, int y, int width, Component label, String value) {
        graphics.fill(x, y, x + width, y + 34, 0xFF282E30);
        graphics.fill(x, y, x + 2, y + 34, 0xFFB78A43);
        graphics.drawString(this.font, label, x + 7, y + 6, 0xFF929DA0, false);
        graphics.drawString(this.font, value, x + 7, y + 19, 0xFFF0F2F2, false);
    }

    /** Ids of the secondary management tabs; {@code -1} is the relations entry with its own screen. */
    private static final int[] MANAGEMENT_TABS = {1, 2, 3, 5, -1};

    /** Measured width of every secondary tab label, in tab order. */
    private int[] managementLabelWidths() {
        int[] labels = new int[MANAGEMENT_TABS.length];
        for (int i = 0; i < MANAGEMENT_TABS.length; i++) {
            labels[i] = this.font.width(managementTabName(MANAGEMENT_TABS[i]));
        }
        return labels;
    }

    /**
     * The one flow layout for the secondary tabs.
     *
     * <p>Each tab is as wide as its own label plus padding, placed left to right and wrapped onto a
     * new row when the next one no longer fits. Both the painting and the hit test read this single
     * array of rectangles, so a tab can never be drawn where it cannot be clicked, or vice versa.
     */
    private CityPylonUiLayout.Rect workspaceControl(String name, int x, int y, int w, int h) {
        return CityPylonWorkspace.pageControl(this, name, x, y, w, h, CityPylonUiLayout.Rect::new);
    }

    private CityPylonUiLayout.Rect projectAction(int left, int cardY, int index) {
        return workspaceControl("project.decommission[" + index + "]",
                left + CONTENT_WIDTH - 54, cardY + 3, 50, 28);
    }

    private CityPylonUiLayout.Rect developmentAction(int left, int buttonY, boolean complete) {
        int offset = complete ? HALF_W2 : 0;
        return workspaceControl(complete ? "development.confirm" : "development.contribute",
                left + offset, buttonY, complete ? CONTENT_WIDTH - offset : HALF_W, DEV_BUTTON_H);
    }

    private CityPylonMembersLayout.Rect memberControl(String name, int index, CityPylonMembersLayout.Rect bounds) {
        return CityPylonWorkspace.pageControl(this, "members." + name + "[" + index + "]",
                bounds.x(), bounds.y(), bounds.width(), bounds.height(), CityPylonMembersLayout.Rect::new);
    }

    private CityPylonMembersLayout.Rect memberGear(CityPylonMembersLayout.MembersLayout layout, int index,
            CityPylonMembersLayout.Rect row) {
        var original = layout.row(index);
        var gear = layout.gear(index);
        return memberControl("gear", index, new CityPylonMembersLayout.Rect(
                gear.x() + row.right() - original.right(),
                gear.y() + row.y() - original.y() + (row.height() - original.height()) / 2,
                gear.width(), gear.height()));
    }

    private CityPylonUiLayout.Rect[] managementTabs(int left, int top) {
        CityPylonUiLayout.Rect[] tabs = CityPylonUiLayout.flow(left, top, CONTENT_WIDTH, managementLabelWidths());
        for (int i = 0; i < tabs.length; i++) {
            var tab = tabs[i];
            tabs[i] = workspaceControl("management.tab[" + MANAGEMENT_TABS[i] + "]",
                    tab.x(), tab.y(), tab.width(), tab.height());
        }
        return tabs;
    }

    /**
     * How many rows the tabs really need, derived from the same flow the tabs are painted with.
     *
     * <p>The members body reads this so a wrapped row pushes the body down with it.
     */
    private int managementRows() {
        return CityPylonUiLayout.rows(CONTENT_WIDTH, MANAGEMENT_TABS.length, managementLabelWidths());
    }

    /** Top of the management body, derived from how many rows the tabs really need. */
    private int managementBodyTop(int contentTop) {
        return managementBody(contentTop).top();
    }

private CityPylonUiLayout.ManagementBody managementBody(int contentTop) {
        return CityPylonUiLayout.managementBody(contentTop, CONTENT_BODY_BOTTOM, managementRows());
    }

    private void renderManagementNav(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        CityPylonUiLayout.Rect[] tabs = managementTabs(left, top);

        for (int i = 0; i < MANAGEMENT_TABS.length; i++) {
            CityPylonUiLayout.Rect cell = tabs[i];
            boolean active = this.activeTab == MANAGEMENT_TABS[i];
            boolean hover = !active && cell.contains(mouseX, mouseY);

            /*
             * Quiet Create-style surface: dark neutral plate, and a single calm gold accent for the
             * active tab instead of a filled block of colour.
             */
            graphics.fill(
                    cell.x(),
                    cell.y(),
                    cell.right(),
                    cell.bottom(),
                    active ? 0xFF322A1B : hover ? 0xFF2F3436 : 0xFF262A2C
            );

            if (active) {
                // 1px gold border plus a 2px accent bar, both inside the tab's own bounds.
                graphics.renderOutline(
                        cell.x(),
                        cell.y(),
                        cell.width() - 1,
                        cell.height() - 1,
                        0xFFB78A43
                );
                graphics.fill(
                        cell.x() + 1,
                        cell.y() + 1,
                        cell.x() + 3,
                        cell.bottom() - 1,
                        0xFFB78A43
                );
            }

            /*
             * The cell is exactly font.width(label) + padding, so the full label fits by
             * construction. The truncation below is only a safety net for a pathologically narrow
             * panel, never the normal case.
             */
            String full = managementTabName(MANAGEMENT_TABS[i]);
            String fitted = full;
            int textWidth = this.font.width(full);
            int available = cell.width() - CityPylonUiLayout.TAB_PAD_X * 2;
            if (textWidth > available) {
                fitted = this.font.plainSubstrByWidth(
                        full, Math.max(1, available - this.font.width("…")));
                if (fitted.isEmpty()) {
                    fitted = this.font.plainSubstrByWidth(full, Math.max(1, available));
                }
            }

            graphics.drawCenteredString(
                    this.font,
                    fitted,
                    cell.x() + cell.width() / 2,
                    cell.y() + (cell.height() - this.font.lineHeight) / 2 + 1,
                    active ? 0xFFF3D9A6 : hover ? 0xFFD5DBDD : 0xFF9BA5A8
            );
        }
    }

    private void renderHubOverlays(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int problems = hubProblemCount();
        hubIconButton(graphics, left + 352, top - 3, problems > 0 ? "!" + problems : "?", problems > 0, mouseX, mouseY);
        hubIconButton(graphics, left + 382, top - 3, "?", false, mouseX, mouseY);
        if (this.problemCenterVisible) {
            graphics.fill(left + 55, top + 28, left + 430, top + 245, 0xF3182023);
            graphics.renderOutline(left + 55, top + 28, 375, 217, 0xFFB78A43);
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problems"), left + 68, top + 42, 0xFFFFD59A, false);
            graphics.drawWordWrap(this.font, Component.translatable(nextStepKey()), left + 68, top + 67, 345, 0xFFD7DDDE);
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problem_count", problems), left + 68, top + 104, problems > 0 ? 0xFFFFA7A0 : 0xFF9FE1C0, false);
            graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problem_hint"), left + 68, top + 126, 345, 0xFF9DA8AB);
            graphics.fill(left + 315, top + 218, left + 427, top + 238, 0xFF4A3A20);
            graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.workspace.overview.manage"), left + 371, top + 224, 0xFFFFFFFF);
        } else if (this.cityHubGuideVisible) {
            graphics.fill(left - 4, top + 20, left + CONTENT_WIDTH + 4, top + 246, 0xD5101517);
            graphics.fill(left + 48, top + 52, left + 392, top + 184, 0xFF222B2E);
            graphics.renderOutline(left + 48, top + 52, 344, 132, 0xFFB78A43);
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.guide.title"), left + 64, top + 68, 0xFFFFD59A, false);
            graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.guide.text"), left + 64, top + 91, 312, 0xFFD7DDDE);
            graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.guide.dismiss"), left + 220, top + 162, 0xFFB9C1C3);
        }
    }

    private void hubIconButton(GuiGraphics graphics, int x, int y, String text, boolean warning, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, 24, 20);
        graphics.fill(x, y, x + 24, y + 20, hover ? 0xFF4A3A20 : 0xFF303638);
        graphics.renderOutline(x, y, 24, 20, warning ? 0xFFD07872 : 0xFF596064);
        graphics.drawCenteredString(this.font, text, x + 12, y + 6, warning ? 0xFFFFAAA3 : 0xFFD3D8DA);
    }

    private int hubProblemCount(){return 0;}

    private String nextStepKey(){return "gui.createcityexpansioncore.city.hub.next.manage";}

    /*
     * =========================================
     * ТЕРРИТОРИЯ
     * =========================================
     */

    private void renderTerritory(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int mouseX,
            int mouseY
    ) {

        int row =
                managementBodyTop(top);

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.plots_label"),
                left,
                row,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.plots_value",
                        this.menu.getPlotCount()),
                left + 220,
                row,
                0xFFFFFF
        );

        /*
         * Площадь в блоках — производная от числа участков. Старое
         * «N × N» исчезло намеренно: квадрат вокруг пилона больше не
         * является территорией, и показывать его размер значило бы
         * показывать то, чего у города нет.
         */
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.territory_blocks"),
                left,
                row + 17,
                0xAAAAAA
        );

        guiGraphics.drawString(
                this.font,
                Component.literal(formatCount(this.menu.getPlotCount() * 2500L)),
                left + 220,
                row + 17,
                0xFFFFFF
        );

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.level_short"),
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

        /*
         * Собственный цвет города: тот же, что рисует карта и граница
         * в мире. Кнопка палитры есть только у OWNER/ADMIN, поэтому
         * интерфейс не предлагает действие, которое сервер отклонит.
         */
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.color_label"),
                left,
                row + 70,
                0xAAAAAA
        );

        colorPaletteButtonX = left + 220;
        colorPaletteButtonY = row + 64;
        int swatchColor = this.menu.getColorRgb();
        guiGraphics.fill(colorPaletteButtonX, colorPaletteButtonY,
                colorPaletteButtonX + 18, colorPaletteButtonY + 14,
                CityColors.argb(swatchColor, 0xFF));
        guiGraphics.renderOutline(colorPaletteButtonX - 1, colorPaletteButtonY - 1, 20, 16, 0xFFB78A43);
        guiGraphics.drawString(
                this.font,
                Component.literal(String.format(java.util.Locale.ROOT, "#%06X", swatchColor)),
                colorPaletteButtonX + 24,
                colorPaletteButtonY + 3,
                0xFFFFFF
        );

        if (this.menu.canManageTerritory()) {
            var palette = paletteControl();
            boolean hovered = palette.contains(mouseX, mouseY);
            guiGraphics.fill(palette.x(), palette.y(), palette.right(), palette.bottom(),
                    hovered ? 0xFF4A3A20 : 0xFF303638);
            guiGraphics.renderOutline(palette.x(), palette.y(), palette.width(), palette.height(),
                    hovered ? 0xFFB78A43 : 0xFF596064);
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.createcityexpansioncore.city.color.change"),
                    palette.x() + palette.width()/2, palette.y() + (palette.height()-8)/2,
                    hovered ? 0xFFFFFFFF : 0xFFD3D8DA);
        }

        var checkbox = territoryControl(left, row + TERRITORY_CHECKBOX_TOP);
        int checkboxX = checkbox.x();
        int checkboxY = checkbox.y() + 3;
        int boxSize = Math.max(4, Math.min(checkbox.width(), checkbox.height() - 7));

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
                    0xFFB78A43
            );
        }

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.show_boundaries"),
                checkboxX + 22,
                checkboxY + 3,
                showTerritory
                        ? 0xFFFFFF
                        : 0xAAAAAA
        );

        if (this.menu.canManageTerritory()) {
            guiGraphics.drawString(
                    this.font,
                    Component.translatable("gui.createcityexpansioncore.city.claim_hint"),
                    checkboxX,
                    checkboxY + 22,
                    0xAAAAAA
            );
        }
    }

    private CityPylonUiLayout.Rect paletteControl() {
        return workspaceControl("territory.palette", colorPaletteButtonX + COLOR_BUTTON_OFFSET,
                colorPaletteButtonY - 1, 96, 16);
    }

    private CityPylonUiLayout.Rect territoryControl(int left, int top) {
        return workspaceControl("territory.boundaries", left, top - 3, CONTENT_WIDTH - 39, 21);
    }

    /** Границы кнопки палитры считаются при отрисовке и переиспользуются обработкой клика. */
    private int colorPaletteButtonX;
    private int colorPaletteButtonY;

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

        /*
         * Вся геометрия тела — из одной MembersLayout: она же задаёт
         * позицию invite-кнопки, hitbox шестерёнок и колесный скролл.
         */
        CityPylonMembersLayout.MembersLayout layout =
                this.membersLayout();

        CityPylonMembersLayout.Rect toolbar =
                layout.toolbar();

        /*
         * Заголовок: одна строка внутри toolbar, правее — счётчик,
         * в правом конце той же строки — реальная кнопка «ПРИГЛАСИТЬ».
         */

        String residentsLabel = tr("gui.createcityexpansioncore.city.residents_title");

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        residentsLabel
                ),
                toolbar.x(),
                layout.toolbarLabelY(),
                0xAAAAAA
        );

        int countX =
                toolbar.x()
                        + this.font.width(
                        residentsLabel
                )
                        + 6;

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        String.valueOf(
                                this.menu.getMemberCount()
                        )
                ),
                countX,
                layout.toolbarLabelY(),
                0xFFFFFF
        );

        /*
         * =====================================
         * СПИСОК
         * =====================================
         */

        int total = this.menu.getMemberCount();

        memberScroll =
                layout.clampScroll(
                        memberScroll,
                        total
                );

        int start =
                memberScroll;

        int end =
                Math.min(
                        start + layout.visibleRows(),
                        total
                );

        CityPylonMembersLayout.Rect clip =
                layout.listClip();

        beginClip(guiGraphics, clip.x(), clip.y(), clip.right(), clip.bottom());

        for (int i = start; i < end; i++) {

            CityMember member =
                    this.menu
                            .getMembers()
                            .get(i);

            int visibleIndex =
                    i - start;

            /*
             * The mouse arrives already divided by interfaceScale in render(), which is exactly the
             * space the layout lives in. Passing it down removes the old second conversion that
             * ignored interfaceScale and lit the gear up at the wrong row on a scaled panel.
             */
            renderMemberCard(
                    guiGraphics,
                    member,
                    layout,
                    visibleIndex,
                    mouseX,
                    mouseY
            );
        }

        guiGraphics.disableScissor();

        /*
         * =====================================
         * SCROLLBAR
         * =====================================
         */

        if (total > layout.visibleRows()) {

            CityPylonMembersLayout.Rect thumb =
                    layout.scrollThumb(
                            total,
                            memberScroll
                    );

            CityPylonMembersLayout.Rect track =
                    layout.scrollTrack();

            guiGraphics.fill(
                    track.x() - 2,
                    track.y(),
                    track.right() + 2,
                    track.bottom(),
                    0xFF292929
            );

            guiGraphics.fill(
                    thumb.x(),
                    thumb.y(),
                    thumb.right(),
                    thumb.bottom(),
                    0xFFB78A43
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
            CityPylonMembersLayout.MembersLayout layout,
            int visibleIndex,
            int mouseX,
            int mouseY
    ) {

        /*
         * Compact two-line row:
         *   [avatar] Nickname
         *            ● ONLINE   ВЛАДЕЛЕЦ          [gear]
         * Все координаты — из layout; весь контент гарантированно
         * внутри row bounds.
         */
        var original = layout.row(visibleIndex);
        var card = memberControl("row", visibleIndex, original);
        int offsetX = card.x() - original.x();
        int offsetY = card.y() - original.y();

        guiGraphics.fill(
                card.x(),
                card.y(),
                card.right(),
                card.bottom(),
                card.contains(mouseX, mouseY) ? 0xFF263137 : 0xFF181818
        );

        CityMember.Role role =
                member.getRole();

        int roleColor =
                switch (role) {

                    case OWNER ->
                            0xFFB78A43;

                    case ADMIN ->
                            0xFFCC5555;

                    case BUILDER ->
                            0xFF55AAFF;

                    case ENGINEER, OPERATOR_MANAGER -> 0xFF78B7C5;
                    case TREASURER -> 0xFFB9A16A;
                    case RESIDENT ->
                            0xFF777777;
                };

        guiGraphics.fill(
                card.x(),
                card.y(),
                card.x() + 3,
                card.bottom(),
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

        /*
         * Аватар: только от фактической строки. 11-аргументный оверлоад
         * GuiGraphics.blit: (texture, x, y, destW, destH, srcU, srcV,
         * srcW, srcH, textureW, textureH) — голова 8×8 из 64×64 текстуры.
         */
        var originalAvatar = layout.avatar(visibleIndex);
        var avatar = new CityPylonMembersLayout.Rect(originalAvatar.x() + offsetX, originalAvatar.y() + offsetY,
                originalAvatar.width(), originalAvatar.height());

        if (playerInfo != null) {

            PlayerSkin skin =
                    playerInfo.getSkin();

            ResourceLocation skinTexture =
                    skin.texture();

            guiGraphics.blit(
                    skinTexture,
                    avatar.x(),
                    avatar.y(),
                    avatar.width(),
                    avatar.height(),
                    8,
                    8,
                    8,
                    8,
                    64,
                    64
            );

        } else {

            guiGraphics.fill(
                    avatar.x(),
                    avatar.y(),
                    avatar.right(),
                    avatar.bottom(),
                    0xFF252525
            );
        }

        String playerName = getMemberName(member);

        /*
         * Line 1: nickname, обрезается по доступной ширине строки
         * (между avatar и gear), а не за рёбра row.
         */
        int textX = layout.textX(visibleIndex) + offsetX;
        int textRight = layout.textRight(visibleIndex) + card.right() - original.right();
        int textWidth = Math.max(1, textRight - textX);

        guiGraphics.drawString(
                this.font,
                ellipsize(
                        Component.literal(playerName),
                        textWidth
                ),
                textX,
                layout.nicknameY(visibleIndex) + offsetY,
                0xFFFFFF
        );

        /*
         * Line 2: status dot + ONLINE/OFFLINE + role, та же ширина.
         */
        int metadataY = layout.metadataY(visibleIndex) + offsetY;

        int dotSize = 5;
        guiGraphics.fill(
                textX,
                metadataY + 1,
                textX + dotSize,
                metadataY + 1 + dotSize,
                online
                        ? 0xFF55CC55
                        : 0xFFCC5555
        );

        String statusLabel =
                online
                        ? tr("gui.createcityexpansioncore.common.online")
                        : tr("gui.createcityexpansioncore.common.offline");

        int statusX = textX + dotSize + 3;
        guiGraphics.drawString(
                this.font,
                Component.literal(statusLabel),
                statusX,
                metadataY,
                online
                        ? 0xFF77DD77
                        : 0xFFDD7777
        );

        String roleName =
                switch (role) {

                    case OWNER ->
                            tr("role.createcityexpansioncore.owner");

                    case ADMIN ->
                            tr("role.createcityexpansioncore.admin");

                    case BUILDER ->
                            tr("role.createcityexpansioncore.builder");

                    case ENGINEER -> tr("role.createcityexpansioncore.engineer");
                    case OPERATOR_MANAGER -> tr("role.createcityexpansioncore.operator_manager");
                    case TREASURER -> tr("role.createcityexpansioncore.treasurer");
                    case RESIDENT ->
                            tr("role.createcityexpansioncore.resident");
                };

        /*
         * The role shares the same right edge as the nickname, so it can never be painted over the
         * gear. A budget too small for even one character leaves the status alone rather than
         * letting the role spill past the row.
         */
        int roleX = statusX + this.font.width(statusLabel) + 8;
        int roleWidth = textRight - roleX;

        if (roleWidth >= 8) {

            guiGraphics.drawString(
                    this.font,
                    ellipsize(
                            Component.literal(roleName),
                            roleWidth
                    ),
                roleX,
                    metadataY,
                    roleColor
            );
        }

        if (canManageMember(member)) {

            CityPylonMembersLayout.Rect gear =
                    memberGear(layout, visibleIndex, card);

            drawGear(
                    guiGraphics,
                    gear.x(),
                    gear.y(),
                    gear.width(),
                    gear.height(),
                    gear.contains(mouseX, mouseY)
                            ? 0xFFFFC978
                            : 0xFFB78A43
            );
        }
    }

    /**
     * Рисует шестерёнку целочисленными прямоугольниками.
     *
     * <p>Раньше здесь был Unicode-глиф «?» размером со шрифт
     * (Ð¾ÐºÐ¾Ð»Ð¾ 8×8 Ð¸ Ð·Ð°Ð¼ÐµÑ‚Ð½Ð¾ Ð¼ÐµÐ½ÑŒÑˆÐµ Ð¾ÑÑ‚Ð°Ð»ÑŒÐ½Ñ‹Ñ… ÑÐ»ÐµÐ¼ÐµÐ½Ñ‚Ð¾Ð² ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸).
     * Ð¢ÐµÐ¿ÐµÑ€ÑŒ ÑÑ‚Ð¾ Ð½Ð°ÑÑ‚Ð¾ÑÑ‰Ð°Ñ Ð¸ÐºÐ¾Ð½ÐºÐ° 16×16, Ð½Ð°Ñ€Ð¸ÑÐ¾Ð²Ð°Ð½Ð½Ð°Ñ Ð¿Ð¸ÐºÑÐµÐ»ÑŒ-Ð°Ñ€Ñ‚Ð¾Ð¼
     * в стиле интерфейса: без сглаживания, без растягивания текстуры.
     *
     * @param width ширина иконки в логических пикселях GUI
     */
    private void drawGear(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        /*
         * Ð¡Ñ…ÐµÐ¼Ð° 16×16. '#' â€” Ð¿Ð¸ÐºÑÐµÐ»ÑŒ Ð¸ÐºÐ¾Ð½ÐºÐ¸, '.' â€” Ð¿Ñ€Ð¾Ð·Ñ€Ð°Ñ‡Ð½Ñ‹Ð¹.
         * Углы и центр оставлены пустыми, чтобы иконка читалась
         * как шестерня, а не как сплошной квадрат.
         */
                final String[] GEAR = {
                "..................",
                "....##........##....",
                "...####......####...",
                "..##############..",
                "...############....",
                "..###.######.###....",
                ".####..####..####...",
                "#####..#..#..#####..",
                "#####..#..#..#####..",
                ".####..####..####...",
                "..###.######.###....",
                "...############....",
                "..##############..",
                "...####......####...",
                "....##........##....",
                "..................",
                "..................",
                ".................."
        };

        final int dark = 0xFF8A6320;
        int columns = java.util.Arrays.stream(GEAR).mapToInt(String::length).max().orElse(1);

        for (int row = 0; row < GEAR.length; row++) {
            String line = GEAR[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) != '#') {
                    continue;
                }
                int px = x + column * width / columns;
                int py = y + row * height / GEAR.length;
                int right = x + (column + 1) * width / columns;
                int bottom = y + (row + 1) * height / GEAR.length;
                /*
                 * Нижний ряд пикселя рисуется темнее — это даёт
                 * объём без единого сглаженного пикселя.
                 */
                guiGraphics.fill(px, py, right, bottom,
                        (row >= GEAR.length / 2) ? dark : color);
            }
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

        /*
         * Everything is measured from the shared management body top. This body used to anchor
         * itself to the raw content column plus a fixed offset, which assumed a single navigation
         * row and painted the level line straight through the wrapped second row of tabs.
         */
        int body = managementBody(top).top();

        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.city.level", romanLevel(this.menu.getCityLevel())),
                left, body + 0, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.city.project_capacity",
                        this.menu.getProjects().size(), this.menu.getProjectCapacity()),
                left + 125, body + 0, 0xFFB78A43);

        List<CityProjectSnapshot> projects = this.menu.getProjects();
        for (int index = 0; index < Math.min(2, projects.size()); index++) {
            CityProjectSnapshot project = projects.get(index);
            int cardY = body + PROJECT_CARD_TOP + index * PROJECT_CARD_PITCH;
            guiGraphics.fill(left, cardY, left + CONTENT_WIDTH, cardY + 34, 0xFF192125);
            guiGraphics.renderOutline(left, cardY, CONTENT_WIDTH, 34, 0xFF455159);
            Component resource = resourceName(project.resourceId());
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.project.mine_summary",
                            resource, Math.round(project.richness() * 100.0)),
                    left + 6, cardY + 3, 0xFFFFFFFF);
            guiGraphics.drawString(this.font,
                    projectStatus(project.status()), left + 6, cardY + 13, 0xFF9EADB3);

            String creator = project.creatorId().toString().substring(0, 6);
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.project.location",
                            project.controllerPos().getX(), project.controllerPos().getZ(),
                            project.dimensionId().getPath(), creator),
                    left + 6, cardY + 23, 0xFF7E8D93);
            boolean confirm = project.projectId().equals(this.decommissionConfirmation);
            var action = projectAction(left, cardY, index);
            guiGraphics.fill(action.x(), action.y(), action.right(), action.bottom(),
                    confirm ? 0xFF6B2828 : 0xFF33262A);
            guiGraphics.drawCenteredString(this.font, Component.translatable(confirm
                            ? "gui.createcityexpansioncore.common.confirm"
                            : "gui.createcityexpansioncore.project.decommission"),
                    action.x() + action.width()/2, action.y() + (action.height()-8)/2, confirm ? 0xFFFFFFFF : 0xFFFF8C8C);
        }
        if (projects.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.project.none"),
                    left, body + PROJECT_CARD_TOP + 13, 0xFF888888);
        }
    }

    private void renderDevelopment(
            GuiGraphics guiGraphics,
            int left,
            int top,
            int mouseX,
            int mouseY
    ) {
        if (developmentAnimationTicks >= 0) {
            renderDevelopmentAnimation(guiGraphics, left, top);
            return;
        }

        /*
         * Верхние элементы — от bodyTop, нижняя полоса прогресса и кнопки — от
         * нижней границы контента. Раньше всё считалось от contentTop, поэтому
         * блок требований наезжал на второй ряд tabs.
         */
        CityPylonUiLayout.ManagementBody body = managementBody(top);
        int bodyTop = body.top();
        int bodyBottom = body.bottom();

        if (this.menu.getCityLevel() >= 2) {
            guiGraphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.complete"),
                    left + CONTENT_CENTER, bodyTop + 20, 0xFF77C98D);
            guiGraphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.reward_capacity", 1, 2),
                    left + CONTENT_CENTER, bodyTop + 42, 0xFFB78A43);
            return;
        }

        List<CityDevelopmentRequirementSnapshot> requirements =
                this.menu.getDevelopmentRequirements();
        if (requirements.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.register_mine_first"),
                    left, bodyTop + 7, 0xFFFFA060);
            return;
        }

        CityProjectSnapshot primaryMine = this.menu.getProjects().stream()
                .filter(project -> project.resourceId().equals(
                        this.menu.getDevelopmentPrimaryResource()))
                .findFirst().orElse(null);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.level_transition"),
                left, bodyTop + 0, 0xFFB78A43);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.primary_industry"),
                left, bodyTop + 13, 0xFF9EADB3);
        if (primaryMine != null) {
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.primary_mine",
                            resourceName(primaryMine.resourceId()),
                            Math.round(primaryMine.richness() * 100.0)),
                    left + 128, bodyTop + 13, 0xFFFFFFFF);
        }
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.reward_level"),
                left + 186, bodyTop + 0, 0xFF77C98D);

        CityDevelopmentRequirementSnapshot primary = requirements.stream()
                .filter(value -> value.group()
                        == CityDevelopmentRequirements.RequirementGroup.PRIMARY_INDUSTRY)
                .findFirst().orElse(null);
        if (primary != null) {
            renderRequirement(guiGraphics, primary, left, bodyTop + DEV_PRIMARY_TOP,
                    CONTENT_WIDTH, mouseX, mouseY, true);
        }

        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.electrical"),
                left, bodyTop + DEV_ELECTRICAL_TOP, 0xFF9EADB3);
        List<CityDevelopmentRequirementSnapshot> electrical = requirements.stream()
                .filter(value -> value.group()
                        == CityDevelopmentRequirements.RequirementGroup.ELECTRICAL_INFRASTRUCTURE)
                .toList();

        /*
         * Список требований не должен доезжать до полосы прогресса: при переносе
         * navigation тело становится ниже, и без ограничения последняя строка
         * закрывала бы прогресс.
         */
        int electricalLimit = bodyBottom - DEV_FOOTER_TOP - 6;
        int visible = Math.max(0, Math.min(
                electrical.size(),
                (electricalLimit - (bodyTop + DEV_ELECTRICAL_TOP + 13)) / DEV_ELECTRICAL_PITCH));
        for (int index = 0; index < visible; index++) {
            renderRequirement(guiGraphics, electrical.get(index), left,
                    bodyTop + DEV_ELECTRICAL_TOP + 13 + index * DEV_ELECTRICAL_PITCH,
                    CONTENT_WIDTH, mouseX, mouseY, false);
        }

        double progress = requirements.stream()
                .mapToDouble(CityDevelopmentRequirementSnapshot::progress)
                .average().orElse(0.0);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.total_progress",
                        Math.round(progress * 100.0)),
                left, bodyBottom - DEV_FOOTER_TOP, 0xFFFFFFFF);
        guiGraphics.fill(left, bodyBottom - DEV_FOOTER_TOP + 10, left + CONTENT_WIDTH,
                bodyBottom - DEV_FOOTER_TOP + 16, 0xFF252B2E);
        guiGraphics.fill(left, bodyBottom - DEV_FOOTER_TOP + 10,
                left + (int) Math.round(CONTENT_WIDTH * progress),
                bodyBottom - DEV_FOOTER_TOP + 16, 0xFFB78A43);

        int buttonY = bodyBottom - DEV_BUTTON_H;
        var contribute = developmentAction(left, buttonY, false);
        guiGraphics.fill(contribute.x(), contribute.y(), contribute.right(), contribute.bottom(), 0xFF30434A);
        guiGraphics.drawCenteredString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.contribute_all"),
                contribute.x() + contribute.width()/2, contribute.y() + (contribute.height()-8)/2, 0xFFFFFFFF);
        boolean complete = this.menu.areDevelopmentRequirementsComplete();
        var confirm = developmentAction(left, buttonY, true);
        guiGraphics.fill(confirm.x(), confirm.y(), confirm.right(), confirm.bottom(),
                complete ? 0xFF32633C : 0xFF273036);
        guiGraphics.drawCenteredString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.confirm"),
                confirm.x() + confirm.width()/2, confirm.y() + (confirm.height()-8)/2, complete ? 0xFFFFFFFF : 0xFF777777);
    }

    private void renderRequirement(
            GuiGraphics graphics,
            CityDevelopmentRequirementSnapshot requirement,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY,
            boolean prominent
    ) {
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(requirement.itemId())
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        int height = prominent ? 28 : 19;
        graphics.fill(x, y, x + width, y + height,
                requirement.complete() ? 0xFF1D3028 : 0xFF192125);
        graphics.renderOutline(x, y, width, height,
                requirement.complete() ? 0xFF557D68 : 0xFF3F4A50);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 3, y + (height - 16) / 2);
        }
        graphics.drawString(this.font, stack.isEmpty()
                        ? Component.literal(requirement.itemId().toString())
                        : stack.getHoverName(),
                x + 23, y + 4, requirement.complete() ? 0xFFB8D8C4 : 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal(
                        formatCount(requirement.current()) + " / " + formatCount(requirement.required())
                                + (requirement.complete() ? " ?" : "")),
                x + width - 88, y + 4, requirement.complete() ? 0xFF77C98D : 0xFFB78A43);
        if (prominent) {
            int barX = x + 23;
            int barY = y + 18;
            int barWidth = width - 28;
            graphics.fill(barX, barY, barX + barWidth, barY + 5, 0xFF252B2E);
            graphics.fill(barX, barY, barX + (int) Math.round(barWidth * requirement.progress()),
                    barY + 5, requirement.complete() ? 0xFF557D68 : 0xFFB78A43);
        }
        if (!stack.isEmpty() && mouseX >= x + 2 && mouseX < x + 21
                && mouseY >= y && mouseY < y + height) {
            deferTooltip(stack);
        }
    }

    private void renderDevelopmentAnimation(GuiGraphics graphics, int left, int top) {
        // The overlay covers exactly the management body: below the tabs, down to the content bottom.
        CityPylonUiLayout.ManagementBody body = managementBody(top);
        int overlayTop = body.top() + DEV_ANIMATION_TOP;
        graphics.fill(left, overlayTop, left + CONTENT_WIDTH, body.bottom(), 0xEE111719);
        graphics.renderOutline(left, overlayTop, CONTENT_WIDTH, body.bottom() - overlayTop, 0xFFB78A43);
        int phase = Math.min(3, developmentAnimationTicks / 30);
        Component[] checks = {
                Component.translatable("gui.createcityexpansioncore.development.animation.industry"),
                Component.translatable("gui.createcityexpansioncore.development.animation.electrical"),
                Component.translatable("gui.createcityexpansioncore.development.animation.systems")
        };
        for (int index = 0; index < checks.length; index++) {
            int color = phase > index ? 0xFF77C98D
                    : phase == index ? 0xFFB78A43 : 0xFF667176;
            Component line = phase > index
                    ? Component.translatable(
                    "gui.createcityexpansioncore.development.animation.done", checks[index])
                    : checks[index];
            graphics.drawString(this.font, line, left + 18, overlayTop + 24 + index * 28,
                    color, false);
        }
        if (phase >= 3) {
            graphics.hLine(left + 18, left + 282, overlayTop + 115, 0xFF596064);
            graphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.animation.complete"),
                    left + 150, overlayTop + 129, 0xFF77C98D);
            graphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.level_transition"),
                    left + 150, overlayTop + 150, 0xFFFFFFFF);
            graphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.reward_capacity", 1, 2),
                    left + 150, overlayTop + 171, 0xFFB78A43);
        }
    }

/**
     * Applies an authoritative member snapshot. Ignored unless the pylon matches, and idempotent: a
     * snapshot replaces the list wholesale, so the same one applied twice cannot duplicate anybody,
     * and an out-of-order arrival still converges because every snapshot is complete.
     */
    public void applyMembersSnapshot(SyncCityMembersPayload payload) {
        if (!this.menu.getPylonPos().equals(payload.pylonPos())) {
            return;
        }
        List<CityMember> members = new ArrayList<>();
        for (SyncCityMembersPayload.Entry entry : payload.members()) {
            CityMember.Role[] roles = CityMember.Role.values();
            int roleId = entry.roleId();
            CityMember.Role role = roleId >= 0 && roleId < roles.length
                    ? roles[roleId]
                    : CityMember.Role.RESIDENT;
            members.add(new CityMember(entry.playerId(), role, entry.displayName()));
        }
        this.menu.replaceMembers(members);
        // The section is redrawn from the menu every frame, so a fresh list and a reset
        // scroll are all it takes; no extra invalidation flag is needed.
        this.memberScroll = 0;
    }

private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

@Override
    protected void containerTick() {
        super.containerTick();
        int currentLevel = this.menu.getCityLevel();
        if (lastObservedCityLevel < 2 && currentLevel >= 2) {
            developmentAnimationTicks = 0;
            activeTab = 5;
            updateTabButtons();
        }
        lastObservedCityLevel = currentLevel;
        if (developmentAnimationTicks >= 0 && ++developmentAnimationTicks > 125) {
            developmentAnimationTicks = -1;
        }
        this.mapPanel.setProjects(this.menu.getProjects());
        /*
         * Правая кнопка по карте имеет смысл только пока City Pylon —
         * верхний экран: вложенные экраны используют ту же панель и не
         * должны предлагать claim от имени города. Право приходит с
         * сервера вместе с состоянием города.
         */
        /*
         * Ссылка на метод, а не новая лямбда каждый тик: setPlotClaimRequest
         * сравнивает по ссылке и пересчитывает фронтир доступных участков
         * только при реальном изменении права, а не 20 раз в секунду.
         */
        refreshMapClaimAccess();
        if (this.showTerritory) {
            CityBoundaryRenderer.setVisible(
                    true, ownTerritoryPlots(), this.menu.getColorRgb());
        }

if (this.activeTab == 4 && this.minecraft != null
                && this.minecraft.level != null
                && this.minecraft.level.getGameTime() % 600 == 0) {

        }
    }

    private static Component resourceName(ResourceLocation resourceId) {
        return Component.translatable(
                "resource.createcityexpansioncore." + resourceId.getPath()
        );
    }

    private static Component projectStatus(String status) {
        String key = switch (status) {
            case "OFFLINE / UNLOADED" -> "offline_unloaded";
            case "OFFLINE / CONTROLLER MISSING" -> "controller_missing";
            default -> status.toLowerCase(java.util.Locale.ROOT);
        };
        return Component.translatable("status.createcityexpansioncore.mine." + key);
    }

    private static String romanLevel(int level) {
        return level >= 2 ? "II" : "I";
    }

    private static String formatCount(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
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
     *
     * Размер территории больше не зависит от уровня города: площадь —
     * это число занятых участков 50×50, и растёт оно только через
     * claim с карты. Поэтому «следующего размера» у города больше нет,
     * и метод удалён вместе со старым квадратом.
     */

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
