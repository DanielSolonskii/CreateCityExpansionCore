package com.example.examplemod.client.screen;

import com.example.examplemod.network.CityMapPeersPayload;
import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityMember;
import com.example.examplemod.client.CityBoundaryRenderer;
import com.example.examplemod.client.gui.CityButton;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.sound.SoundHelper;
import com.example.examplemod.client.status.CcecStatusUi;
import com.example.examplemod.client.status.CcecProblemCenter;
import com.example.examplemod.status.CcecStatus;
import com.example.examplemod.status.CcecStatusResolver;
import com.example.examplemod.sound.ModSounds;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.InvitePlayerPayload;
import com.example.examplemod.network.SyncCityMembersPayload;
import com.example.examplemod.network.CityDevelopmentPayload;
import com.example.examplemod.network.CityRelationsCommandPayload;
import com.example.examplemod.network.DecommissionCityProjectPayload;
import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.EconomyStatePayload;
import com.example.examplemod.railway.RailwayLineSnapshot;
import com.example.examplemod.railway.RailwayStationSnapshot;
import com.example.examplemod.railway.RailwayTrainSnapshot;
import com.example.examplemod.railway.RailwayTrainCandidate;
import com.example.examplemod.railway.RailwayOwnershipSnapshot;
import com.example.examplemod.railway.RailwayOwnershipTypes;
import com.example.examplemod.railway.RailwayTypes;
import com.example.examplemod.city.CityProjectSnapshot;
import com.example.examplemod.city.CityDevelopmentRequirementSnapshot;
import com.example.examplemod.city.CityDevelopmentRequirements;

import java.util.ArrayList;
import java.util.List;
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

public class CityPylonScreen
        extends AbstractContainerScreen<CityPylonMenu> {

    private static boolean marketplaceHintSeen;

    private boolean showTerritory = false;

    private int memberScroll = 0;
    private int inviteScroll = 0;

    /*
     * 0 = Обзор
     * 1 = Территория
     * 2 = Участники
     * 3 = Проекты
     * 4 = Карта
     * 5 = Развитие
     * 6 = Железная дорога
     * 7 = Рынок
     */
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
    private final CityButton[] tabButtons = new CityButton[5];
    private static boolean cityHubGuideSeen;
    private boolean cityHubGuideVisible;
    private boolean problemCenterVisible;

    private int railwayPage;
    private int railwayRefreshTicks;
    private int railwayScroll;
    private int railwaySelectedLine;
    private UUID railwayTrainDetail;
    private UUID railwayStationDetail;
    private int economyPage;
    private int economyScroll;
    private UUID economySelection;
    private boolean economySelectionListing;
    private int economyRefreshTicks;
    private int economySort;
    private EditBox economySearch;
    private float interfaceScale = 1.0F;
    private boolean marketplaceHintVisible;
    private boolean marketplaceHelpVisible;
    /** Прокрутка текста справки рынка (длинный help не режем, а листаем). */
    private int marketplaceHelpScroll;
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

    /* Рынок: слева прокручиваемый список, справа панель деталей. */
    private static final int MARKET_LIST_W = 180;
    private static final int MARKET_DETAIL_W = CONTENT_WIDTH - MARKET_LIST_W - PANEL_GAP;
    private static final int MARKET_TOOLBAR_Y = 61;
    private static final int MARKET_TOOLBAR_H = 23;
    private static final int MARKET_BODY_Y = MARKET_TOOLBAR_Y + MARKET_TOOLBAR_H;
    private static final int MARKET_ROW_H = 34;
    private static final int MARKET_ROW_PITCH = 38;
    private static final int MARKET_VISIBLE_ROWS =
            Math.max(1, (CONTENT_BODY_BOTTOM - MARKET_BODY_Y) / MARKET_ROW_PITCH);

    /* Участники: карточка, её шаг и сколько помещается в списке. */
    private static final int MEMBER_LIST_OFFSET = 73;
    /**
     * Row geometry is owned by {@link CityInviteUiLayout} so the members list and the invite screen
     * share one rhythm, and so the painted row and the clickable row cannot drift apart. The old
     * MEMBER_CARD_W / MEMBER_CARD_H / MEMBER_CARD_PITCH triple with its own magic offsets is gone.
     */
    private static final int MEMBER_ROW_H = CityInviteUiLayout.ROW_HEIGHT - CityInviteUiLayout.MICRO;
    private static final int MEMBER_ROWS = Math.max(2,
            (CONTENT_BODY_BOTTOM - MEMBER_LIST_OFFSET) / CityInviteUiLayout.ROW_HEIGHT);

    /**
     * Шестерёнка «управления ролями» на карточке участника.
     *
     * <p>Размер 18×18 — чуть крупнее для лучшей кликабельности и видимости
     * при GUI Scale 2/3/4. Пиксель-арт не растягивается: рисуется целочисленно.
     */
    private static final int MEMBER_GEAR_SIZE = 12;

    /** Отступ шестерёнки от правого края карточки. */
    private static final int MEMBER_GEAR_RIGHT_MARGIN = 6;

    /** Отступ шестерёнки от верхнего края карточки. */
    private static final int MEMBER_GEAR_TOP_MARGIN = 6;

    /**
     * Единый источник правды по геометрии карточки участника.
     *
     * <p>Раньше координаты карточки вычислялись дважды и по-разному:
     * один раз в renderMembers(), другой — в mouseClicked(). Из-за
     * расхождения ширины карточки (270 против MEMBER_CARD_W) кнопка
     * шестерёнки уезжала на 120 пикселей вправо от нарисованной.
     *
     * <p>Теперь и отрисовка, и hitbox берут одни и те же значения
     * отсюда, поэтому разъехаться они уже не могут.
     *
     * @param x левый край карточки
     * @param y верхний край карточки
     */
    private record MemberCardGeometry(int x, int y, int width, int height) {

        int gearX() {
            return this.x + this.width - MEMBER_GEAR_RIGHT_MARGIN - MEMBER_GEAR_SIZE;
        }

        int gearY() {
            return this.y + Math.max(0, (this.height - MEMBER_GEAR_SIZE) / 2);
        }
    }

    /**
     * One geometry, produced by {@link CityInviteUiLayout}: rendering, hit-testing, scrolling and
     * the scrollbar all read the very same rectangle, so a row can never be painted somewhere the
     * click handler is not.
     */
    private MemberCardGeometry memberCard(int listTop, int visibleIndex, int memberCount) {
        CityInviteUiLayout.Rect row =
                CityInviteUiLayout.memberRow(
                        this.width,
                        listTop + visibleIndex * CityInviteUiLayout.ROW_HEIGHT,
                        0,
                        memberCount
                );
        return new MemberCardGeometry(
                row.x(),
                row.y(),
                row.width(),
                row.height()
        );
    }

    /** Top of the member list in screen coordinates. */
    private int memberListTop() {
        return this.topPos + CONTENT_OFFSET_Y + MEMBER_LIST_OFFSET;
    }

    /** The single management control of a row, from the same layout model. */
    private CityInviteUiLayout.Rect memberActionBounds(int visibleIndex, int memberCount) {
        return CityInviteUiLayout.memberAction(
                this.width,
                memberListTop() + visibleIndex * CityInviteUiLayout.ROW_HEIGHT,
                0,
                memberCount
        );
    }

    /**
     * Геометрия i-й видимой карточки списка участников.
     * Индекс — относительно начала видимой окна.
     */
    private MemberCardGeometry memberCardAt(int listTop, int visibleIndex) {
        return memberCard(listTop, visibleIndex, this.menu.getMemberCount());
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
    private static final int INFRA_ROW_H = 42;
    private static final int INFRA_ROWS = Math.max(1,
            (CONTENT_BODY_BOTTOM - 72) / INFRA_ROW_H);

    /*
     * Железная дорога: список сверху, панель действий прижата к низу
     * контента, поэтому строки списков считаются от свободной высоты.
     */
    private static final int RAIL_LIST_TOP = 48;
    private static final int RAIL_ACTION_H = 22;
    private static final int RAIL_ACTION_Y = CONTENT_BODY_BOTTOM - RAIL_ACTION_H - 6;
    private static final int RAIL_LIST_BOTTOM = RAIL_ACTION_Y - 6;
    private static final int RAIL_ACTION_MAIN_W = CONTENT_WIDTH - 82;
    private static final int RAIL_ACTION_REMOVE_X = CONTENT_WIDTH - 74;
    private static final int RAIL_CLOSE_X = CONTENT_WIDTH - 14;
    private static final int RAIL_REMOVE_HIT_X = CONTENT_WIDTH - 30;

    private static int railRows(int pitch) {
        return Math.max(1, (RAIL_LIST_BOTTOM - 51) / pitch);
    }

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
        graphics.enableScissor(
                Math.round(x1 * this.interfaceScale),
                Math.round(y1 * this.interfaceScale),
                Math.round(x2 * this.interfaceScale),
                Math.round(y2 * this.interfaceScale));
    }

    private Component ellipsize(Component component, int maxWidth) {
        if (maxWidth <= 16) return component;
        String text = component.getString();
        if (this.font.width(text) <= maxWidth) return component;
        return Component.literal(this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...");
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

    /** Context-only entry point: railway management no longer occupies a hub tab. */
    public void openRailwayManagement() {
        this.activeTab = 6;
        requestRailwayState();
        requestRailwayMap();
        updateTabButtons();
    }

    /**
     * Train Management entry used by the World Map context card and the Management nav.
     * Lands directly on the Trains page so registration/type work is one click away from
     * whatever the player came from; {@code fleetTrainId} pins a train detail when known.
     */
    public void openTrainManagement(UUID fleetTrainId) {
        this.activeTab = 6;
        this.railwayPage = 2;
        this.railwayScroll = 0;
        this.railwayStationDetail = null;
        this.railwayTrainDetail = fleetTrainId;
        requestRailwayState();
        requestRailwayMap();
        updateTabButtons();
        updateInviteButton();
    }

    /** Same as {@link #openTrainManagement(UUID)} but without pinning a specific train. */
    public void openTrainManagement() {
        openTrainManagement(null);
    }

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
                menu.getTerritorySize(),
                menu.getProjects()
        );
        this.lastObservedCityLevel = menu.getCityLevel();

        /*
         * Однократный звук пробуждения экрана при открытии.
         * Играется в конструкторе, а не в render(), поэтому
         * не повторяется каждый кадр.
         */
        SoundHelper.playUi(ModSounds.PYLON_SCREEN_WAKE.get());
    }

    @Override
    protected void init() {

        super.init();
        this.interfaceScale = Math.min(1.0F, Math.min(
                (this.width - 12.0F) / this.imageWidth,
                (this.height - 12.0F) / this.imageHeight));
        if (this.interfaceScale < 1.0F) {
            this.leftPos = Math.round((this.width / this.interfaceScale - this.imageWidth) / 2.0F);
            this.topPos = Math.round((this.height / this.interfaceScale - this.imageHeight) / 2.0F);
        }

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

        Component[] tabNames = {
                Component.translatable("gui.createcityexpansioncore.city.tab.overview"),
                Component.translatable("gui.createcityexpansioncore.city.tab.marketplace"),
                Component.translatable("gui.createcityexpansioncore.freight.title"),
                Component.translatable("gui.createcityexpansioncore.city.tab.management"),
                Component.translatable("gui.createcityexpansioncore.city.tab.map")
        };

        /*
         * Вкладки заполняют сайдбар целиком: 8 кнопок с шагом 30 px
         * от шапки и до отступа над «Закрыть».
         */
        for (int i = 0; i < this.tabButtons.length; i++) {

            final int tabIndex = i;

            CityPylonUiLayout.Rect navRow =
                    CityPylonUiLayout.navRow(
                            this.leftPos + 10,
                            this.topPos + 54,
                            i);

            this.tabButtons[i] =
                    new CityButton(
                            navRow.x(),
                            navRow.y(),
                            navRow.width(),
                            navRow.height(),
                            tabNames[i],
                            button -> {

                                if (tabIndex == 2) {
                                    requestEconomyState();
                                    if (this.minecraft != null) this.minecraft.setScreen(new LogisticsScreen(this));
                                    return;
                                }
                                if (tabIndex == 4) {
                                    requestRailwayState(); requestRailwayMap(); requestEconomyState();
                                    if (this.minecraft != null) this.minecraft.setScreen(new PolishedWorldMapScreen(this));
                                    return;
                                }

                                /*
                                 * Тихий звук переключения вкладки.
                                 * Помогает ориентироваться в панели
                                 * с восемью вкладками. Обычные
                                 * кнопки оставляют ванианский
                                 * щелчок, чтобы не наслаивать звуки.
                                 */
                                SoundHelper.playUi(
                                        ModSounds.UI_TAB_SWITCH.get()
                                );

                                this.activeTab = switch (tabIndex) {
                                    case 1 -> 7;
                                    case 3 -> isManagementTab(this.activeTab) ? this.activeTab : 2;
                                    default -> 0;
                                };

                                this.showInvitePlayers =
                                        false;

                                this.inviteStatus =
                                        "";

                                this.decommissionConfirmation = null;

                                if (tabIndex == 1) {
                                    requestEconomyState();
                                    requestRailwayState();
                                    if (!marketplaceHintSeen) {
                                        marketplaceHintVisible = true;
                                    }
                                }

                                if (tabIndex != 2) {

                                    this.memberScroll =
                                            0;
                                }

                                updateTabButtons();
                                updateInviteButton();
                            }
                    );

            this.tabButtons[i].setIcon(navigationIcon(i));

            this.tabButtons[i].setActive(
                    hubTab() == i
            );

            this.addRenderableWidget(
                    this.tabButtons[i]
            );
        }

        this.economySearch = new EditBox(
                this.font, this.leftPos + CONTENT_OFFSET_X, this.topPos + CONTENT_OFFSET_Y + MARKET_TOOLBAR_Y, 100, 18,
                Component.translatable("gui.createcityexpansioncore.economy.search")
        );
        this.economySearch.setHint(Component.translatable("gui.createcityexpansioncore.economy.search"));
        this.economySearch.setMaxLength(64);
        this.economySearch.visible = this.activeTab == 7;
        this.addRenderableWidget(this.economySearch);

        /*
         * =====================================
         * ПРИГЛАСИТЬ
         * =====================================
         */

        if (canInvitePlayers()) {

            this.inviteButton =
                    new CityButton(
                            this.leftPos + CONTENT_OFFSET_X + CONTENT_WIDTH - 110,
                            this.topPos + CONTENT_OFFSET_Y + 44,
                            110,
                            24,
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
        this.cityHubGuideVisible = !cityHubGuideSeen;
        requestEconomyState();
        requestRailwayState();
        requestRailwayMap();
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
    /**
 * Icon for each main section. Existing CCEC/Minecraft/Create items only — no drawn or Unicode glyphs.
 * Обзор = city pylon, Экономика = traded goods, Логистика = the dispatch seal, Управление = a
 * comparator (management-like), Карта = compass.
 */
private net.minecraft.world.item.ItemStack navigationIcon(int index) {
    String id = switch (index) {
        case 0 -> "createcityexpansioncore:city_pylon";
        case 1 -> "minecraft:gold_ingot";
        case 2 -> "createcityexpansioncore:shipment_seal";
        case 3 -> "minecraft:comparator";
        default -> "minecraft:compass";
    };
    return new net.minecraft.world.item.ItemStack(
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse(id)));
}

private String managementTabName(int tab) {
        return switch (tab) {
            case 1 -> tr("gui.createcityexpansioncore.city.tab.territory");
            case 2 -> tr("gui.createcityexpansioncore.city.tab.members");
            case 3 -> tr("gui.createcityexpansioncore.city.tab.projects");
            case 5 -> tr("gui.createcityexpansioncore.city.tab.development");
            case 6 -> tr("gui.createcityexpansioncore.city.tab.railway");
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

    private int hubTab() {
        if (this.activeTab == 7) return 1;
        if (this.activeTab == 6) return 3;
        if (isManagementTab(this.activeTab)) return 3;
        return 0;
    }

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
        if (this.economySearch != null) {
            EconomyStatePayload economyState = this.menu.getEconomyState();
            this.economySearch.visible = visible && this.activeTab == 7
                    && (this.economyPage == 0 || this.economyPage == 1)
                    && !this.marketplaceHintVisible && !this.marketplaceHelpVisible
                    && economyState != null && economyState.unlocked();
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

            int listTop = memberListTop() + 28;

            CityInviteUiLayout.Rect clip =
                    CityInviteUiLayout.memberClip(
                            this.width,
                            listTop,
                            MEMBER_ROWS,
                            this.menu.getMemberCount()
                    );

            int listRight =
                    clip.right() + 14;

            int listBottom =
                    clip.bottom();

            if (mouseX >= clip.x()
                    && mouseX <= listRight
                    && mouseY >= clip.y()
                    && mouseY <= listBottom) {

                int memberCount =
                        this.menu.getMemberCount();

                int visibleCount = MEMBER_ROWS;

                if (scrollY > 0) {

                    memberScroll =
                            CityInviteUiLayout.clampMemberScroll(
                                    memberScroll - 1,
                                    memberCount,
                                    visibleCount
                            );
                }

                if (scrollY < 0) {

                    memberScroll =
                            CityInviteUiLayout.clampMemberScroll(
                                    memberScroll + 1,
                                    memberCount,
                                    visibleCount
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

        if (this.activeTab == 6 && this.menu.hasCity()) {
            int pitch = switch (this.railwayPage) {
                case 1 -> 32;
                case 2, 3 -> 31;
                default -> 0;
            };
            if (pitch == 0) {
                return false;
            }
            int visible = railRows(pitch);
            int count = switch (this.railwayPage) {
                case 1 -> this.menu.getRailwayLines().size();
                case 2 -> trainRows().size();
                default -> this.menu.getRailwayStations().size();
            };
            this.railwayScroll = Math.clamp(this.railwayScroll + (scrollY < 0 ? 1 : -1),
                    0, Math.max(0, count - visible));
            return count > visible;
        }

        /*
         * Справка рынка — модальный блок: колесо листает именно её.
         */
        if (this.activeTab == 7 && this.menu.hasCity()
                && (this.marketplaceHintVisible || this.marketplaceHelpVisible)) {
            this.marketplaceHelpScroll += scrollY < 0 ? 27 : -27;
            return true;
        }

        if (this.activeTab == 7 && this.menu.hasCity()) {
            EconomyStatePayload state=this.menu.getEconomyState();
            if(state!=null&&state.unlocked()&&(this.economyPage==0||this.economyPage==1||this.economyPage==3)){
                boolean infra=this.economyPage==3;
                int count=infra?state.infrastructure().size():economyVisibleEntries(state).size();
                int visible=infra?INFRA_ROWS:MARKET_VISIBLE_ROWS;
                this.economyScroll=Math.clamp(this.economyScroll+(scrollY<0?1:-1),0,Math.max(0,count-visible));
                return count>visible;
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

            int checkboxX =
                    this.contentLeft();

            int checkboxY =
                    this.contentTop() + 45 + 110;

            int boxSize = 14;

            boolean clicked =
                    mouseX >= checkboxX
                            && mouseX <= checkboxX + CONTENT_WIDTH - 40
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

        if (button == 0 && this.activeTab == 0) {
            int left = this.contentLeft(), top = this.contentTop();
            if (this.problemCenterVisible) {
                if (inside(mouseX, mouseY, left + 315, top + 218, 112, 20)) {
                    this.problemCenterVisible = false;
                    requestEconomyState();
                    if (this.minecraft != null) this.minecraft.setScreen(new LogisticsScreen(this));
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
            int[] tabs = {1, 2, 3, 5, -1, 6};
            // Measured columns instead of a fixed width: Russian labels are far wider than their
            // English originals and a fixed column made them touch or truncate.
            int[] labelWidths = new int[tabs.length];
            for (int i = 0; i < tabs.length; i++) {
                labelWidths[i] = this.font.width(managementTabName(tabs[i]));
            }
            int width = CityPylonUiLayout.columnWidth(CONTENT_WIDTH, tabs.length, labelWidths);
            int rows = CityPylonUiLayout.rows(CONTENT_WIDTH, tabs.length, labelWidths);
            for (int i = 0; i < tabs.length; i++) {
                CityPylonUiLayout.Rect cell = CityPylonUiLayout.tab(
                        this.contentLeft(), this.contentTop() + 22, i, width, tabs.length, rows);
                if (cell.contains(mouseX, mouseY)) {
                    if (i == 4) {
                        if (this.minecraft != null) this.minecraft.setScreen(new CityRelationsScreen(this));
                    } else if (i == 5) {
                        openTrainManagement();
                    } else {
                        this.activeTab = tabs[i]; updateTabButtons(); updateInviteButton();
                    }
                    return true;
                }
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

        if (this.activeTab == 6 && button == 0
                && handleRailwayClick(mouseX, mouseY)) {
            return true;
        }

        if (this.activeTab == 7 && button == 0 && handleEconomyClick(mouseX, mouseY)) {
            return true;
        }

        if (this.activeTab == 3 && button == 0) {
            int contentLeft = this.contentLeft();
            int contentTop = this.contentTop();
            List<CityProjectSnapshot> projects = this.menu.getProjects();
            for (int index = 0; index < Math.min(2, projects.size()); index++) {
                // Та же геометрия, что и в renderProjects(): карточка 34 пикселя.
                int cardY = contentTop + 62 + index * 36;
                if (mouseX >= contentLeft + CONTENT_WIDTH - 54 && mouseX <= contentLeft + CONTENT_WIDTH - 4
                        && mouseY >= cardY + 3 && mouseY <= cardY + 31) {
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
            int buttonY = this.topPos + CONTENT_OFFSET_Y + CONTENT_BODY_BOTTOM - 19;
            if (mouseY >= buttonY && mouseY <= buttonY + 19) {
                if (mouseX >= contentLeft && mouseX <= contentLeft + HALF_W) {
                    PacketDistributor.sendToServer(new CityDevelopmentPayload(
                            this.menu.getPylonPos(), CityDevelopmentPayload.Action.CONTRIBUTE
                    ));
                    return true;
                }
                if (mouseX >= contentLeft + HALF_W2 && mouseX <= contentLeft + CONTENT_WIDTH
                        && this.menu.areDevelopmentRequirementsComplete()
                        && this.menu.getCityLevel() < 2) {
                    PacketDistributor.sendToServer(new CityDevelopmentPayload(
                            this.menu.getPylonPos(), CityDevelopmentPayload.Action.COMPLETE_LEVEL_TWO
                    ));
                    return true;
                }
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

            int cardY =
                    this.contentTop() + MEMBER_LIST_OFFSET;

            int visibleCount = MEMBER_ROWS;

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

                /*
                 * Та же геометрия, что и при отрисовке.
                 * Раньше здесь стояли собственные cardWidth/spacing
                 * и собственный прямоугольник для шестерёнки,
                 * из-за чего кнопка уезжала от нарисованной.
                 */
                MemberCardGeometry card =
                        memberCardAt(cardY, visibleIndex);

                int gearX = card.gearX();
                int gearY = card.gearY();

                /*
                 * Hitbox ровно по нарисованной иконке:
                 * раньше он был шире и ниже, чем сама шестерёнка.
                 */
                boolean clicked =
                        mouseX >= gearX
                                && mouseX < gearX + MEMBER_GEAR_SIZE
                                && mouseY >= gearY
                                && mouseY < gearY + MEMBER_GEAR_SIZE;

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
                playerInfo
                        .getProfile()
                        .getName();

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
                ? tr("role.createcityexpansioncore.owner")
                : tr("role.createcityexpansioncore.resident");
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

        this.menu.drainMapTiles().forEach(this.mapPanel::receive);
        RailwayMapPayload railwayMap = this.menu.drainRailwayMap();
        if (railwayMap != null) {
            this.mapPanel.receive(railwayMap);
        }
        this.mapPanel.setProjects(this.menu.getProjects());
        this.mapPanel.setTerritorySize(this.menu.getTerritorySize());
        if(this.menu.getEconomyState()!=null){
            this.mapPanel.setEconomyInfrastructure(this.menu.getEconomyState().infrastructure());
            this.mapPanel.setFreightData(this.menu.getEconomyState().logistics());
        }
        if(this.menu.getRelationsState()!=null)this.mapPanel.setRelations(this.menu.getRelationsState().cities());
        this.mapPanel.setRailwayData(
                this.menu.getRailwayStations(),
                this.menu.getRailwayTrains()
        );

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

        guiGraphics.blit(
                HEADER_EMBLEM,
                emblemX,
                top + HEADER_EMBLEM_Y,
                HEADER_EMBLEM_SIZE,
                HEADER_EMBLEM_SIZE,
                0.0F,
                0.0F,
                HEADER_EMBLEM_TEXTURE_SIZE,
                HEADER_EMBLEM_TEXTURE_SIZE,
                HEADER_EMBLEM_TEXTURE_SIZE,
                HEADER_EMBLEM_TEXTURE_SIZE
        );

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

            case 6 ->
                    title = Component.translatable("gui.createcityexpansioncore.city.railway");

            case 7 ->
                    title = Component.translatable("gui.createcityexpansioncore.city.marketplace");

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
                contentTop + 16,
                left + this.imageWidth - 20,
                contentTop + 18,
                0xFFD09A3A
        );

        if (isManagementTab(this.activeTab)) {
            renderManagementNav(guiGraphics, contentLeft, contentTop, mouseX, mouseY);
        }

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
                            atlasHover ? 0xFFD09A3A : 0xFF596064);
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

            case 6 ->
                    renderRailway(
                            guiGraphics,
                            contentLeft,
                            contentTop,
                            mouseX,
                            mouseY
                    );

            case 7 -> renderEconomy(guiGraphics, contentLeft, contentTop, mouseX, mouseY);
        }
        if (this.activeTab == 7) deferEconomyHeaderTooltip(contentLeft, contentTop, mouseX, mouseY);
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
                0xFFD09A3A
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
                        ? 0xFFD09A3A
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
                    Component.translatable("gui.createcityexpansioncore.city.invite"),
                    listX + 190,
                    currentY + 7,
                    hovered
                            ? 0xFFD09A3A
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
            int top
    ) {

        EconomyStatePayload economy = this.menu.getEconomyState();
        int problems = hubProblemCount();
        int activeContracts = economy == null ? 0 : (int) economy.contracts().stream()
                .filter(v -> !v.state().terminal()).count();
        int activeShipments = economy == null ? 0 : economy.shipments().size();
        int delivered = economy == null ? 0 : (int) economy.shipments().stream()
                .filter(v -> v.verified() >= v.declared()).count();

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

        hubKpi(guiGraphics, left, top + 76, 98, Component.translatable("gui.createcityexpansioncore.city.hub.treasury"),
                economy == null ? "—" : economy.treasury() + " CR");
        hubKpi(guiGraphics, left + 108, top + 76, 98, Component.translatable("gui.createcityexpansioncore.city.hub.contracts"), Integer.toString(activeContracts));
        hubKpi(guiGraphics, left, top + 116, 98, Component.translatable("gui.createcityexpansioncore.city.hub.shipments"), Integer.toString(activeShipments));
        hubKpi(guiGraphics, left + 108, top + 116, 98, Component.translatable("gui.createcityexpansioncore.city.hub.delivered"), Integer.toString(delivered));
        hubKpi(guiGraphics, left, top + 156, 98, Component.translatable("gui.createcityexpansioncore.city.hub.level"), Integer.toString(this.menu.getCityLevel()));
        hubKpi(guiGraphics, left + 108, top + 156, 98, Component.translatable("gui.createcityexpansioncore.city.hub.members"), Integer.toString(this.menu.getMemberCount()));

        this.mapPanel.render(guiGraphics, left + 216, top + 27, 224, 171, -1000, -1000);
        guiGraphics.fill(left + 406, top + 32, left + 435, top + 55, 0xDD20282A);
        guiGraphics.renderOutline(left + 406, top + 32, 29, 23, 0xFFD09A3A);
        guiGraphics.drawCenteredString(this.font, "?", left + 420, top + 39, 0xFFFFFFFF);

        guiGraphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.members_list"),
                left, top + 211, 0xFFD09A3A, false);
        String names = this.menu.getMembers().stream().limit(5).map(this::getMemberName)
                .reduce((a, b) -> a + "  •  " + b).orElse("—");
        if (this.menu.getMemberCount() > 5) names += "  +" + (this.menu.getMemberCount() - 5);
        guiGraphics.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(names, CONTENT_WIDTH)),
                left, top + 226, 0xFFB9C1C3, false);
    }

    private void hubKpi(GuiGraphics graphics, int x, int y, int width, Component label, String value) {
        graphics.fill(x, y, x + width, y + 34, 0xFF282E30);
        graphics.fill(x, y, x + 2, y + 34, 0xFFD09A3A);
        graphics.drawString(this.font, label, x + 7, y + 6, 0xFF929DA0, false);
        graphics.drawString(this.font, value, x + 7, y + 19, 0xFFF0F2F2, false);
    }

    private void renderManagementNav(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int[] tabs = {1, 2, 3, 5, -1, 6};
        String[] keys = {"territory", "members", "projects", "development", "relations", "railway"};
        int width = (CONTENT_WIDTH - 12) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int x = left + i * (width + 3), y = top + 22;
            boolean hover = inside(mouseX, mouseY, x, y, width, 18);
            graphics.fill(x, y, x + width, y + 18, this.activeTab == tabs[i] ? 0xFF4A3A20 : hover ? 0xFF34393B : 0xFF292E30);
            graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.city.tab." + keys[i]), x + width / 2, y + 5,
                    this.activeTab == tabs[i] ? 0xFFFFD59A : 0xFFB9C1C3);
        }
    }

    private void renderHubOverlays(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int problems = hubProblemCount();
        hubIconButton(graphics, left + 352, top - 3, problems > 0 ? "!" + problems : "?", problems > 0, mouseX, mouseY);
        hubIconButton(graphics, left + 382, top - 3, "?", false, mouseX, mouseY);
        if (this.problemCenterVisible) {
            graphics.fill(left + 55, top + 28, left + 430, top + 245, 0xF3182023);
            graphics.renderOutline(left + 55, top + 28, 375, 217, 0xFFD09A3A);
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problems"), left + 68, top + 42, 0xFFFFD59A, false);
            graphics.drawWordWrap(this.font, Component.translatable(nextStepKey()), left + 68, top + 67, 345, 0xFFD7DDDE);
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problem_count", problems), left + 68, top + 104, problems > 0 ? 0xFFFFA7A0 : 0xFF9FE1C0, false);
            graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problem_hint"), left + 68, top + 126, 345, 0xFF9DA8AB);
            graphics.fill(left + 315, top + 218, left + 427, top + 238, 0xFF4A3A20);
            graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.open_logistics"), left + 371, top + 224, 0xFFFFFFFF);
        } else if (this.cityHubGuideVisible) {
            graphics.fill(left - 4, top + 20, left + CONTENT_WIDTH + 4, top + 246, 0xD5101517);
            graphics.fill(left + 48, top + 52, left + 392, top + 184, 0xFF222B2E);
            graphics.renderOutline(left + 48, top + 52, 344, 132, 0xFFD09A3A);
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

    private int hubProblemCount() {
        return CcecProblemCenter.count(this.menu.getEconomyState(), this.menu.getRailwayOwnership(),
                this.menu.getRailwayTrains());
    }

    private String nextStepKey() {
        return CcecProblemCenter.nextStepKey(this.menu.getEconomyState(), hubProblemCount());
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
                Component.translatable("gui.createcityexpansioncore.city.current_size"),
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

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.next_expansion"),
                left,
                row + 70,
                0xAAAAAA
        );

        int nextSize =
                getNextTerritorySize();

        guiGraphics.drawString(
                this.font,
                this.menu.getCityLevel() >= 2
                        ? Component.translatable("gui.createcityexpansioncore.city.maximum_level")
                        : Component.literal(nextSize + " × " + nextSize),
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
                Component.translatable("gui.createcityexpansioncore.city.show_boundaries"),
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

        String residentsLabel = tr("gui.createcityexpansioncore.city.residents_title");

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

        /*
         * Единая геометрия: используется и здесь, и в mouseClicked().
         * hardcoded 270 раньше не совпадал с MEMBER_CARD_W.
         */
        int cardX =
                left;

        int cardY =
                row + 28;

        int visibleCount =
                MEMBER_ROWS;

        int total = this.menu.getMemberCount();

        memberScroll =
                CityInviteUiLayout.clampMemberScroll(
                        memberScroll,
                        total,
                        visibleCount
                );

        int start =
                memberScroll;

        int end =
                Math.min(
                        start + visibleCount,
                        total
                );

        CityInviteUiLayout.Rect clip =
                CityInviteUiLayout.memberClip(
                        this.width,
                        cardY,
                        visibleCount,
                        total
                );

        beginClip(guiGraphics, clip.x(), clip.y(), clip.right(), clip.bottom());

        for (int i = start; i < end; i++) {

            CityMember member =
                    this.menu
                            .getMembers()
                            .get(i);

            int visibleIndex =
                    i - start;

            MemberCardGeometry card =
                    memberCard(cardY, visibleIndex, total);

            renderMemberCard(
                    guiGraphics,
                    member,
                    card
            );
        }

        guiGraphics.disableScissor();

        /*
         * =====================================
         * SCROLLBAR
         * =====================================
         */

        if (total > visibleCount) {

            CityInviteUiLayout.Rect thumb =
                    CityInviteUiLayout.memberScrollThumb(
                            this.width,
                            cardY,
                            visibleCount,
                            total,
                            memberScroll
                    );

            guiGraphics.fill(
                    thumb.x() - 2,
                    clip.y(),
                    thumb.right() + 2,
                    clip.bottom(),
                    0xFF292929
            );

            guiGraphics.fill(
                    thumb.x(),
                    thumb.y(),
                    thumb.right(),
                    thumb.bottom(),
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
            MemberCardGeometry card
    ) {

        final int cardX = card.x();
        final int cardY = card.y();
        final int cardWidth = card.width();
        final int cardHeight = card.height();

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
                            ? tr("role.createcityexpansioncore.owner")
                            : tr("role.createcityexpansioncore.resident");
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
                                ? tr("gui.createcityexpansioncore.common.online")
                                : tr("gui.createcityexpansioncore.common.offline")
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
                            tr("role.createcityexpansioncore.owner");

                    case ADMIN ->
                            tr("role.createcityexpansioncore.admin");

                    case BUILDER ->
                            tr("role.createcityexpansioncore.builder");

                    case RESIDENT ->
                            tr("role.createcityexpansioncore.resident");
                };

        guiGraphics.drawString(
                this.font,
                Component.literal(roleName),
                cardX + 58,
                cardY + 34,
                roleColor
        );

        if (canManageMember(member)) {

            drawGear(
                    guiGraphics,
                    card.gearX(),
                    card.gearY(),
                    MEMBER_GEAR_SIZE,
                    this.mouseOverGear(card)
                            ? 0xFFFFC978
                            : 0xFFD09A3A
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
     * @param size сторона иконки в логических пикселях GUI
     */
    private void drawGear(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int size,
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
        guiGraphics.fill(x, y, x + size, y + size, 0x00000000);

        for (int row = 0; row < GEAR.length; row++) {
            String line = GEAR[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) != '#') {
                    continue;
                }
                int px = x + column;
                int py = y + row;
                /*
                 * Нижний ряд пикселя рисуется темнее — это даёт
                 * объём без единого сглаженного пикселя.
                 */
                guiGraphics.fill(px, py, px + 1, py + 1,
                        (row >= GEAR.length / 2) ? dark : color);
            }
        }
    }

    /**
     * Находится ли курсор над шестерёнкой карточки.
     * Использует ту же геометрию, что и отрисовка.
     */
    private boolean mouseOverGear(MemberCardGeometry card) {
        double mouseX =
                this.minecraft.mouseHandler.xpos()
                        / (this.minecraft.getWindow()
                        .getGuiScaledWidth()
                                / (double) this.width);
        double mouseY =
                this.minecraft.mouseHandler.ypos()
                        / (this.minecraft.getWindow()
                        .getGuiScaledHeight()
                                / (double) this.height);
        int gx = card.gearX();
        int gy = card.gearY();
        return mouseX >= gx
                && mouseX < gx + MEMBER_GEAR_SIZE
                && mouseY >= gy
                && mouseY < gy + MEMBER_GEAR_SIZE;
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

        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.city.level", romanLevel(this.menu.getCityLevel())),
                left, top + 47, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.city.project_capacity",
                        this.menu.getProjects().size(), this.menu.getProjectCapacity()),
                left + 125, top + 47, 0xFFD09A3A);

        List<CityProjectSnapshot> projects = this.menu.getProjects();
        for (int index = 0; index < Math.min(2, projects.size()); index++) {
            CityProjectSnapshot project = projects.get(index);
            int cardY = top + 62 + index * 36;
            guiGraphics.fill(left, cardY, left + CONTENT_WIDTH, cardY + 34, 0xFF181D20);
            guiGraphics.renderOutline(left, cardY, CONTENT_WIDTH, 34, 0xFF455159);
            Component resource = resourceName(project.resourceId());
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.project.mine_summary",
                            resource, Math.round(project.richness() * 100.0)),
                    left + 6, cardY + 3, 0xFFFFFFFF);
            guiGraphics.drawString(this.font,
                    projectStatus(project.status()), left + 6, cardY + 13, 0xFF9EADB3);
            RailwayStationSnapshot linkedStation = this.menu.getRailwayStations().stream()
                    .filter(station -> project.projectId().equals(station.linkedProjectId()))
                    .findFirst().orElse(null);
            guiGraphics.drawString(this.font, linkedStation == null
                            ? Component.translatable("gui.createcityexpansioncore.railway.project_station_none")
                            : Component.translatable("gui.createcityexpansioncore.railway.project_station",
                            linkedStation.name(),
                            linkedStation.available()
                                    ? Component.translatable("status.createcityexpansioncore.railway.available")
                                    : Component.translatable("status.createcityexpansioncore.railway.missing")),
                    left + 92, cardY + 13,
                    linkedStation == null ? 0xFF7E8D93 : 0xFF77B992, false);
            String creator = project.creatorId().toString().substring(0, 6);
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.project.location",
                            project.controllerPos().getX(), project.controllerPos().getZ(),
                            project.dimensionId().getPath(), creator),
                    left + 6, cardY + 23, 0xFF7E8D93);
            boolean confirm = project.projectId().equals(this.decommissionConfirmation);
            guiGraphics.fill(left + CONTENT_WIDTH - 54, cardY + 3, left + CONTENT_WIDTH - 4, cardY + 31,
                    confirm ? 0xFF6B2828 : 0xFF33262A);
            guiGraphics.drawCenteredString(this.font, Component.translatable(confirm
                            ? "gui.createcityexpansioncore.common.confirm"
                            : "gui.createcityexpansioncore.project.decommission"),
                    left + CONTENT_WIDTH - 29, cardY + 13, confirm ? 0xFFFFFFFF : 0xFFFF8C8C);
        }
        if (projects.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.project.none"),
                    left, top + 75, 0xFF888888);
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
        if (this.menu.getCityLevel() >= 2) {
            guiGraphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.complete"),
                    left + CONTENT_CENTER, top + 82, 0xFF77C98D);
            guiGraphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.reward_capacity", 1, 2),
                    left + CONTENT_CENTER, top + 104, 0xFFD09A3A);
            return;
        }

        List<CityDevelopmentRequirementSnapshot> requirements =
                this.menu.getDevelopmentRequirements();
        if (requirements.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.register_mine_first"),
                    left, top + 32, 0xFFFFA060);
            return;
        }

        CityProjectSnapshot primaryMine = this.menu.getProjects().stream()
                .filter(project -> project.resourceId().equals(
                        this.menu.getDevelopmentPrimaryResource()))
                .findFirst().orElse(null);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.level_transition"),
                left, top + 25, 0xFFD09A3A);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.primary_industry"),
                left, top + 38, 0xFF9EADB3);
        if (primaryMine != null) {
            guiGraphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.primary_mine",
                            resourceName(primaryMine.resourceId()),
                            Math.round(primaryMine.richness() * 100.0)),
                    left + 128, top + 38, 0xFFFFFFFF);
        }
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.reward_level"),
                left + 186, top + 25, 0xFF77C98D);

        CityDevelopmentRequirementSnapshot primary = requirements.stream()
                .filter(value -> value.group()
                        == CityDevelopmentRequirements.RequirementGroup.PRIMARY_INDUSTRY)
                .findFirst().orElse(null);
        if (primary != null) {
            renderRequirement(guiGraphics, primary, left, top + 50, CONTENT_WIDTH, mouseX, mouseY, true);
        }

        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.electrical"),
                left, top + 83, 0xFF9EADB3);
        List<CityDevelopmentRequirementSnapshot> electrical = requirements.stream()
                .filter(value -> value.group()
                        == CityDevelopmentRequirements.RequirementGroup.ELECTRICAL_INFRASTRUCTURE)
                .toList();
        for (int index = 0; index < electrical.size(); index++) {
            renderRequirement(guiGraphics, electrical.get(index), left, top + 97 + index * 20,
                    CONTENT_WIDTH, mouseX, mouseY, false);
        }

        double progress = requirements.stream()
                .mapToDouble(CityDevelopmentRequirementSnapshot::progress)
                .average().orElse(0.0);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.total_progress",
                        Math.round(progress * 100.0)),
                left, top + CONTENT_BODY_BOTTOM - 44, 0xFFFFFFFF);
        guiGraphics.fill(left, top + CONTENT_BODY_BOTTOM - 34, left + CONTENT_WIDTH,
                top + CONTENT_BODY_BOTTOM - 28, 0xFF252B2E);
        guiGraphics.fill(left, top + CONTENT_BODY_BOTTOM - 34,
                left + (int) Math.round(CONTENT_WIDTH * progress),
                top + CONTENT_BODY_BOTTOM - 28, 0xFFD09A3A);

        int buttonY = top + CONTENT_BODY_BOTTOM - 19;
        guiGraphics.fill(left, buttonY, left + HALF_W, buttonY + 19, 0xFF30434A);
        guiGraphics.drawCenteredString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.contribute_all"),
                left + HALF_W / 2, buttonY + 6, 0xFFFFFFFF);
        boolean complete = this.menu.areDevelopmentRequirementsComplete();
        guiGraphics.fill(left + HALF_W2, buttonY, left + CONTENT_WIDTH, buttonY + 19,
                complete ? 0xFF32633C : 0xFF273036);
        guiGraphics.drawCenteredString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.development.confirm"),
                left + HALF_W2 + HALF_W / 2, buttonY + 6, complete ? 0xFFFFFFFF : 0xFF777777);
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
                requirement.complete() ? 0xFF1D3028 : 0xFF181D20);
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
                x + width - 88, y + 4, requirement.complete() ? 0xFF77C98D : 0xFFD09A3A);
        if (prominent) {
            int barX = x + 23;
            int barY = y + 18;
            int barWidth = width - 28;
            graphics.fill(barX, barY, barX + barWidth, barY + 5, 0xFF252B2E);
            graphics.fill(barX, barY, barX + (int) Math.round(barWidth * requirement.progress()),
                    barY + 5, requirement.complete() ? 0xFF557D68 : 0xFFD09A3A);
        }
        if (!stack.isEmpty() && mouseX >= x + 2 && mouseX < x + 21
                && mouseY >= y && mouseY < y + height) {
            deferTooltip(stack);
        }
    }

    private void renderDevelopmentAnimation(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top + 24, left + CONTENT_WIDTH, top + CONTENT_BODY_BOTTOM, 0xEE111719);
        graphics.renderOutline(left, top + 24, CONTENT_WIDTH, CONTENT_BODY_BOTTOM - 24, 0xFFD09A3A);
        int phase = Math.min(3, developmentAnimationTicks / 30);
        Component[] checks = {
                Component.translatable("gui.createcityexpansioncore.development.animation.industry"),
                Component.translatable("gui.createcityexpansioncore.development.animation.electrical"),
                Component.translatable("gui.createcityexpansioncore.development.animation.systems")
        };
        for (int index = 0; index < checks.length; index++) {
            int color = phase > index ? 0xFF77C98D
                    : phase == index ? 0xFFD09A3A : 0xFF667176;
            Component line = phase > index
                    ? Component.translatable(
                    "gui.createcityexpansioncore.development.animation.done", checks[index])
                    : checks[index];
            graphics.drawString(this.font, line, left + 18, top + 48 + index * 28,
                    color, false);
        }
        if (phase >= 3) {
            graphics.hLine(left + 18, left + 282, top + 139, 0xFF596064);
            graphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.animation.complete"),
                    left + 150, top + 153, 0xFF77C98D);
            graphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.level_transition"),
                    left + 150, top + 174, 0xFFFFFFFF);
            graphics.drawCenteredString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.development.reward_capacity", 1, 2),
                    left + 150, top + 195, 0xFFD09A3A);
        }
    }

    private void renderRailway(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        Component[] pages = {
                Component.translatable("gui.createcityexpansioncore.railway.page.network"),
                Component.translatable("gui.createcityexpansioncore.railway.page.lines"),
                Component.translatable("gui.createcityexpansioncore.railway.page.trains"),
                Component.translatable("gui.createcityexpansioncore.railway.page.stations")
        };
        int pageWidth = CONTENT_WIDTH / pages.length;
        for (int i = 0; i < pages.length; i++) {
            int x = left + i * pageWidth;
            graphics.fill(x, top + 24, x + pageWidth - 3, top + 42,
                    this.railwayPage == i ? 0xFF4A3A20 : 0xFF292F31);
            graphics.renderOutline(x, top + 24, pageWidth - 3, 18,
                    this.railwayPage == i ? 0xFFD09A3A : 0xFF596064);
            graphics.drawCenteredString(this.font, pages[i], x + (pageWidth - 3) / 2, top + 29,
                    this.railwayPage == i ? 0xFFFFE0A6 : 0xFFB8C0C2);
        }

        switch (this.railwayPage) {
            case 1 -> renderRailLines(graphics, left, top, mouseX, mouseY);
            case 2 -> renderRailTrains(graphics, left, top, mouseX, mouseY);
            case 3 -> renderRailStations(graphics, left, top, mouseX, mouseY);
            default -> renderRailNetwork(graphics, left, top);
        }
    }

    private void renderRailNetwork(GuiGraphics graphics, int left, int top) {
        List<RailwayOwnershipSnapshot> owned=this.menu.getRailwayOwnership();
        if(!owned.isEmpty()){
            int y=top+51,end=Math.min(owned.size(),3);for(int i=0;i<end;i++){var network=owned.get(i);int color=network.onlineSections()==network.sections()?0xFF77B992:0xFFD07968;graphics.fill(left,y,left+CONTENT_WIDTH,y+35,0xFF292F31);graphics.fill(left,y,left+3,y+35,color);graphics.drawString(this.font,network.name(),left+9,y+4,0xFFE6E9EA,false);graphics.drawString(this.font,Component.translatable("gui.createcityexpansioncore.railway.ownership_summary",network.sections(),network.onlineSections(),String.format(java.util.Locale.ROOT,"%.0f",network.totalLength())),left+9,y+15,0xFFAEB8BB,false);graphics.drawString(this.font,Component.translatable("gui.createcityexpansioncore.railway.ownership_access_tariff",Component.translatable("gui.createcityexpansioncore.railway.access."+network.access().name().toLowerCase()),network.freightTariff()),left+9,y+25,0xFFD09A3A,false);y+=39;for(var section:this.menu.getRailwayOwnedSections().stream().filter(s->s.networkId().equals(network.networkId())).limit(2).toList()){graphics.drawString(this.font,Component.translatable("gui.createcityexpansioncore.railway.section_summary",section.sectionId().toString().substring(0,8),String.format(java.util.Locale.ROOT,"%.0f",section.length()),Component.translatable("gui.createcityexpansioncore.railway.section_status."+section.status().name().toLowerCase())),left+12,y,section.status()==RailwayOwnershipTypes.SectionStatus.ONLINE?0xFF77B992:0xFFD07968,false);y+=12;}}
            graphics.drawWordWrap(this.font,Component.translatable("gui.createcityexpansioncore.railway.ownership_hint"),left,top+CONTENT_BODY_BOTTOM-24,CONTENT_WIDTH,0xFF8F9A9E);return;
        }
        long availableStations = this.menu.getRailwayStations().stream()
                .filter(RailwayStationSnapshot::cityOwned).filter(RailwayStationSnapshot::available).count();
        long missingStations = this.menu.getRailwayStations().stream()
                .filter(RailwayStationSnapshot::cityOwned).filter(station -> !station.available()).count();
        long runningTrains = this.menu.getRailwayTrains().stream()
                .filter(RailwayTrainSnapshot::cityOwned)
                .filter(train -> "in_transit".equals(train.statusKey())
                        || "at_station".equals(train.statusKey())
                        || "waiting".equals(train.statusKey())
                        || "cargo".equals(train.statusKey())).count();
        int y = top + 57;
        renderRailMetric(graphics, left, y,
                Component.translatable("gui.createcityexpansioncore.railway.stations_count"),
                availableStations, missingStations == 0 ? 0xFF77B992 : 0xFFD07968);
        renderRailMetric(graphics, left, y + 38,
                Component.translatable("gui.createcityexpansioncore.railway.lines_count"),
                this.menu.getRailwayLines().size(), 0xFFD09A3A);
        renderRailMetric(graphics, left, y + 76,
                Component.translatable("gui.createcityexpansioncore.railway.trains_running"),
                runningTrains, 0xFF70A9C0);
        if (missingStations > 0) {
            graphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.railway.missing_stations", missingStations),
                    left, top + CONTENT_BODY_BOTTOM - 34, 0xFFD07968, false);
        }
        graphics.drawWordWrap(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.dispatcher_hint"),
                left, top + CONTENT_BODY_BOTTOM - 22, CONTENT_WIDTH, 0xFF8F9A9E);
    }

    private void renderRailMetric(GuiGraphics graphics, int x, int y,
                                  Component label, long value, int color) {
        graphics.fill(x, y, x + CONTENT_WIDTH, y + 31, 0xFF292F31);
        graphics.fill(x, y, x + 3, y + 31, color);
        graphics.drawString(this.font, label, x + 12, y + 5, 0xFFB7C0C3, false);
        graphics.drawString(this.font, Long.toString(value), x + 12, y + 17, color, false);
    }

    private void renderRailLines(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<RailwayLineSnapshot> lines = this.menu.getRailwayLines();
        int end = Math.min(lines.size(), this.railwayScroll + railRows(32));
        beginClip(graphics, left, top + 48, left + CONTENT_WIDTH, top + RAIL_LIST_BOTTOM);
        for (int i = this.railwayScroll; i < end; i++) {
            RailwayLineSnapshot line = lines.get(i);
            int y = top + 51 + (i - this.railwayScroll) * 32;
            boolean hovered = inside(mouseX, mouseY, left, y, CONTENT_WIDTH, 28);
            graphics.fill(left, y, left + CONTENT_WIDTH, y + 28, hovered ? 0xFF343C3F : 0xFF292F31);
            graphics.fill(left, y, left + 3, y + 28, railStatusColor(line.statusKey()));
            graphics.drawString(this.font, line.name(), left + 9, y + 4, 0xFFE6E9EA, false);
            graphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.railway.line_summary",
                            line.stops().size(), line.assignedTrains(), railStatus(line.statusKey())),
                    left + 9, y + 16, 0xFFAEB8BB, false);
            graphics.drawString(this.font, "×", left + RAIL_CLOSE_X, y + 9, 0xFFD07872, false);
        }
        graphics.disableScissor();
        if (lines.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable(
                    "gui.createcityexpansioncore.railway.no_lines"), left + CONTENT_CENTER, top + 96, 0xFF8F9A9E);
        }
        renderRailAction(graphics, left, top + RAIL_ACTION_Y, CONTENT_WIDTH,
                Component.translatable("gui.createcityexpansioncore.railway.create_line"),
                mouseX, mouseY);
    }

    /** One row of the Trains page: a registered train, or a Create train still awaiting one. */
    private record TrainRow(RailwayTrainSnapshot train, RailwayTrainCandidate candidate) {
        boolean registered() { return this.train != null; }
    }

    private List<TrainRow> trainRows() {
        List<TrainRow> rows = new ArrayList<>();
        for (RailwayTrainSnapshot train : this.menu.getRailwayTrains()) rows.add(new TrainRow(train, null));
        for (RailwayTrainCandidate candidate : this.menu.getRailwayTrainCandidates())
            rows.add(new TrainRow(null, candidate));
        return rows;
    }

    private void renderRailTrainRow(GuiGraphics graphics, int left, int y, RailwayTrainSnapshot train) {
        CcecStatusUi.observe(train.fleetTrainId(),train.uiStatus());
        graphics.fill(left, y, left + CONTENT_WIDTH, y + 27, 0xFF292F31);
        graphics.fill(left, y, left + 3, y + 27, CcecStatusUi.color(train.uiStatus()));
        graphics.drawString(this.font, train.name(), left + 9, y + 4,
                train.cityOwned() ? 0xFFE6E9EA : 0xFF9AA5A9, false);
        graphics.drawString(this.font,
                train.cityOwned() ? CcecStatusUi.title(train.uiStatus())
                        : Component.translatable("gui.createcityexpansioncore.railway.personal"),
                left + 9, y + 15, 0xFFAEB8BB, false);
        if (train.cityOwned()) {
            graphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.railway.train_type."
                                    + train.type().name().toLowerCase()),
                    left + 170, y + 9, 0xFF70A9C0, false);
        }
        Component action = !train.cityOwned()
                ? Component.translatable("gui.createcityexpansioncore.railway.attach")
                : "missing".equals(train.statusKey())
                ? Component.translatable("gui.createcityexpansioncore.railway.rebind")
                : train.automationSuspended()
                ? Component.translatable("gui.createcityexpansioncore.railway.restore")
                : train.activeLineId() == null
                ? Component.translatable("gui.createcityexpansioncore.railway.assign")
                : Component.translatable("gui.createcityexpansioncore.railway.unassign");
        graphics.drawString(this.font, action, left + CONTENT_WIDTH - 150, y + 9, 0xFFD09A3A, false);
        if (train.cityOwned()) graphics.drawString(this.font, "×", left + RAIL_CLOSE_X, y + 9, 0xFFD07872, false);
    }

    private void renderRailTrainCandidateRow(GuiGraphics graphics, int left, int y, RailwayTrainCandidate candidate) {
        CcecStatus status = CcecStatusResolver.unregisteredTrain();
        graphics.fill(left, y, left + CONTENT_WIDTH, y + 27, 0xFF292F31);
        graphics.fill(left, y, left + 3, y + 27, CcecStatusUi.color(status));
        graphics.drawString(this.font, candidate.name(), left + 9, y + 4, 0xFFE6E9EA, false);
        graphics.drawString(this.font, CcecStatusUi.title(status), left + 9, y + 15, 0xFFAEB8BB, false);
        graphics.drawString(this.font, CcecStatusUi.action(status), left + CONTENT_WIDTH - 150, y + 9,
                candidate.canManage() ? 0xFFD09A3A : 0xFF6E777A, false);
    }

    private void renderRailTrains(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<RailwayTrainSnapshot> trains = this.menu.getRailwayTrains();
        RailwayTrainSnapshot detail = trains.stream()
                .filter(train -> train.fleetTrainId().equals(this.railwayTrainDetail))
                .findFirst().orElse(null);
        if (detail != null) {
            renderRailTrainDetail(graphics, left, top, detail, mouseX, mouseY);
            return;
        }
        // Keep a detail pinned while the state request is still in flight; clear it only once we
        // know the train really is gone, otherwise opening a train from the World Map drops it.
        if (!trains.isEmpty()) this.railwayTrainDetail = null;
        List<TrainRow> rows = trainRows();
        int end = Math.min(rows.size(), this.railwayScroll + railRows(31));
        beginClip(graphics, left, top + 48, left + CONTENT_WIDTH, top + RAIL_LIST_BOTTOM);
        for (int i = this.railwayScroll; i < end; i++) {
            TrainRow row = rows.get(i);
            int y = top + 51 + (i - this.railwayScroll) * 31;
            if (row.registered()) renderRailTrainRow(graphics, left, y, row.train());
            else renderRailTrainCandidateRow(graphics, left, y, row.candidate());
        }
        graphics.disableScissor();
        List<RailwayLineSnapshot> lines = this.menu.getRailwayLines();
        Component selected = lines.isEmpty()
                ? Component.translatable("gui.createcityexpansioncore.railway.no_lines")
                : Component.translatable("gui.createcityexpansioncore.railway.selected_line",
                lines.get(Math.floorMod(this.railwaySelectedLine, lines.size())).name());
        renderRailAction(graphics, left, top + RAIL_ACTION_Y, CONTENT_WIDTH, selected, mouseX, mouseY);
    }

    private void renderRailStations(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<RailwayStationSnapshot> stations = this.menu.getRailwayStations();
        RailwayStationSnapshot detail = stations.stream()
                .filter(station -> station.registrationId().equals(this.railwayStationDetail))
                .findFirst().orElse(null);
        if (detail != null) {
            renderRailStationDetail(graphics, left, top, detail, mouseX, mouseY);
            return;
        }
        this.railwayStationDetail = null;
        int end = Math.min(stations.size(), this.railwayScroll + railRows(31));
        beginClip(graphics, left, top + 48, left + CONTENT_WIDTH, top + RAIL_LIST_BOTTOM);
        for (int i = this.railwayScroll; i < end; i++) {
            RailwayStationSnapshot station = stations.get(i);
            int y = top + 51 + (i - this.railwayScroll) * 31;
            graphics.fill(left, y, left + CONTENT_WIDTH, y + 27, 0xFF292F31);
            graphics.fill(left, y, left + 3, y + 27,
                    station.available() ? 0xFF77B992 : 0xFFD07872);
            graphics.drawString(this.font, station.name(), left + 9, y + 4,
                    station.cityOwned() ? 0xFFE6E9EA : 0xFF9AA5A9, false);
            graphics.drawString(this.font,
                    station.cityOwned() ? Component.translatable(
                            "gui.createcityexpansioncore.railway.station_type." + station.type().name().toLowerCase())
                            : Component.translatable("gui.createcityexpansioncore.railway.personal"),
                    left + 9, y + 15, 0xFFAEB8BB, false);
            graphics.drawString(this.font, station.cityOwned() && !station.available()
                            ? Component.translatable("gui.createcityexpansioncore.railway.rebind")
                            : station.cityOwned()
                            ? Component.translatable("gui.createcityexpansioncore.railway.change_type_short")
                            : Component.translatable("gui.createcityexpansioncore.railway.attach"),
                    left + CONTENT_WIDTH - 150, y + 9, 0xFFD09A3A, false);
            if (station.cityOwned() && station.linkedProjectId() != null) {
                graphics.drawString(this.font, Component.translatable(
                                "gui.createcityexpansioncore.railway.project_linked_short"),
                        left + 170, y + 9, 0xFF77B992, false);
            } else if (station.cityOwned() && !this.menu.getProjects().isEmpty()) {
                graphics.drawString(this.font, Component.translatable(
                                "gui.createcityexpansioncore.railway.link_project_short"),
                        left + 170, y + 9, 0xFF70A9C0, false);
            }
            if (station.cityOwned()) graphics.drawString(this.font, "×", left + RAIL_CLOSE_X, y + 9, 0xFFD07872, false);
        }
        graphics.disableScissor();
    }

    private void renderRailTrainDetail(GuiGraphics graphics, int left, int top,
                                       RailwayTrainSnapshot train, int mouseX, int mouseY) {
        graphics.fill(left, top + 51, left + CONTENT_WIDTH, top + 196, 0xFF292F31);
        CcecStatusUi.observe(train.fleetTrainId(),train.uiStatus());
        graphics.fill(left, top + 51, left + 4, top + 196, CcecStatusUi.color(train.uiStatus()));
        graphics.drawString(this.font, train.name(), left + 12, top + 58, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.detail.type",
                        Component.translatable("gui.createcityexpansioncore.railway.train_type."
                                + train.type().name().toLowerCase())),
                left + 12, top + 75, 0xFFB7C0C3, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.detail.line",
                        train.lineName().isEmpty()
                                ? Component.translatable("status.createcityexpansioncore.railway.no_schedule")
                                : Component.literal(train.lineName())),
                left + 12, top + 89, 0xFFB7C0C3, false);
        CcecStatusUi.render(graphics,this.font,train.uiStatus(),left+12,top+101,CONTENT_WIDTH-24,false);
        if (!train.nextStation().isEmpty()) {
            graphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.railway.next_station", train.nextStation()),
                    left + 12, top + 140, 0xFFB7C0C3, false);
        }
        graphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.speed", String.format(java.util.Locale.ROOT, "%.1f", train.speed())),
                left + 12, top + 153, 0xFFB7C0C3, false);
        if(train.cityOwned()&&train.uiStatus().action()!=CcecStatus.Action.NONE)renderRailAction(graphics,left+12,top+170,CONTENT_WIDTH-24,CcecStatusUi.action(train.uiStatus()),mouseX,mouseY);
        renderRailAction(graphics, left, top + 202, RAIL_ACTION_MAIN_W,
                trainActionLabel(train), mouseX, mouseY);
        renderRailAction(graphics, left + RAIL_ACTION_REMOVE_X, top + 202, 74,
                Component.translatable("gui.createcityexpansioncore.railway.remove"), mouseX, mouseY);
        renderRailAction(graphics, left, top + RAIL_ACTION_Y, HALF_W,
                Component.translatable("gui.createcityexpansioncore.railway.change_type"), mouseX, mouseY);
        renderRailAction(graphics, left + HALF_W2, top + RAIL_ACTION_Y, HALF_W,
                Component.translatable("gui.createcityexpansioncore.common.back"), mouseX, mouseY);
    }

    private void renderRailStationDetail(GuiGraphics graphics, int left, int top,
                                         RailwayStationSnapshot station, int mouseX, int mouseY) {
        graphics.fill(left, top + 51, left + CONTENT_WIDTH, top + 196, 0xFF292F31);
        graphics.fill(left, top + 51, left + 4, top + 196,
                station.available() ? 0xFF77B992 : 0xFFD07872);
        graphics.drawString(this.font, station.name(), left + 12, top + 58, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.detail.type",
                        Component.translatable("gui.createcityexpansioncore.railway.station_type."
                                + station.type().name().toLowerCase())),
                left + 12, top + 76, 0xFFB7C0C3, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.detail.status",
                        Component.translatable("status.createcityexpansioncore.railway."
                                + (station.available() ? "available" : "missing"))),
                left + 12, top + 92, station.available() ? 0xFF77B992 : 0xFFD07872, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.coordinates",
                        station.pos().getX(), station.pos().getY(), station.pos().getZ(),
                        station.dimension().getPath()),
                left + 12, top + 108, 0xFFB7C0C3, false);
        graphics.drawString(this.font, station.linkedProjectId() == null
                        ? Component.translatable("gui.createcityexpansioncore.railway.project_station_none")
                        : Component.translatable("gui.createcityexpansioncore.railway.project_linked"),
                left + 12, top + 124,
                station.linkedProjectId() == null ? 0xFF8F9A9E : 0xFF77B992, false);
        renderRailAction(graphics, left, top + 202, RAIL_ACTION_MAIN_W,
                station.available()
                        ? Component.translatable("gui.createcityexpansioncore.railway.change_type")
                        : Component.translatable("gui.createcityexpansioncore.railway.rebind"),
                mouseX, mouseY);
        renderRailAction(graphics, left + RAIL_ACTION_REMOVE_X, top + 202, 74,
                Component.translatable("gui.createcityexpansioncore.railway.remove"), mouseX, mouseY);
        renderRailAction(graphics, left, top + RAIL_ACTION_Y, HALF_W,
                Component.translatable(station.linkedProjectId() == null
                        ? "gui.createcityexpansioncore.railway.link_project_short"
                        : "gui.createcityexpansioncore.railway.unlink_project_short"), mouseX, mouseY);
        renderRailAction(graphics, left + HALF_W2, top + RAIL_ACTION_Y, HALF_W,
                Component.translatable("gui.createcityexpansioncore.common.back"), mouseX, mouseY);
    }

    private Component trainActionLabel(RailwayTrainSnapshot train) {
        if (!train.cityOwned()) return Component.translatable("gui.createcityexpansioncore.railway.attach");
        if ("missing".equals(train.statusKey())) return Component.translatable("gui.createcityexpansioncore.railway.rebind");
        if (train.automationSuspended()) return Component.translatable("gui.createcityexpansioncore.railway.restore");
        return train.activeLineId() == null
                ? Component.translatable("gui.createcityexpansioncore.railway.assign")
                : Component.translatable("gui.createcityexpansioncore.railway.unassign");
    }

    private void renderRailAction(GuiGraphics graphics, int x, int y, int width,
                                  Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, 22);
        graphics.fill(x, y, x + width, y + 22, hovered ? 0xFF4A3A20 : 0xFF303638);
        graphics.renderOutline(x, y, width, 22, hovered ? 0xFFD09A3A : 0xFF596064);
        graphics.drawCenteredString(this.font, label, x + width / 2, y + 7,
                hovered ? 0xFFFFE0A6 : 0xFFD3D8DA);
    }

    private boolean handleRailwayClick(double mouseX, double mouseY) {
        int left = this.contentLeft();
        int top = this.contentTop();
        int pageWidth = CONTENT_WIDTH / 4;
        for (int i = 0; i < 4; i++) {
            if (inside(mouseX, mouseY, left + i * pageWidth, top + 24, pageWidth - 3, 18)) {
                this.railwayPage = i;
                this.railwayScroll = 0;
                this.railwayTrainDetail = null;
                this.railwayStationDetail = null;
                return true;
            }
        }
        if(this.railwayPage==0){List<RailwayOwnershipSnapshot> owned=this.menu.getRailwayOwnership();int end=Math.min(owned.size(),5);for(int i=0;i<end;i++){int y=top+51+i*39;if(!inside(mouseX,mouseY,left,y,CONTENT_WIDTH,35))continue;var network=owned.get(i);if(mouseX<left+CONTENT_WIDTH/2){var next=network.access()==RailwayOwnershipTypes.Access.PUBLIC?RailwayOwnershipTypes.Access.PRIVATE:RailwayOwnershipTypes.Access.PUBLIC;PacketDistributor.sendToServer(new RailwayCommandPayload(this.menu.getPylonPos(),RailwayCommandPayload.Action.SET_NETWORK_ACCESS,network.networkId(),null,"",next.ordinal(),false,List.of()));}else{long delta=hasShiftDown()?-100:100;long tariff=Math.max(0,network.freightTariff()+delta);PacketDistributor.sendToServer(new RailwayCommandPayload(this.menu.getPylonPos(),RailwayCommandPayload.Action.SET_NETWORK_TARIFF,network.networkId(),null,Long.toString(tariff),0,false,List.of()));}return true;}return false;}
        if (this.railwayPage == 1) {
            List<RailwayLineSnapshot> lines = this.menu.getRailwayLines();
            int end = Math.min(lines.size(), this.railwayScroll + railRows(32));
            for (int i = this.railwayScroll; i < end; i++) {
                int y = top + 51 + (i - this.railwayScroll) * 32;
                if (!inside(mouseX, mouseY, left, y, CONTENT_WIDTH, 28)) continue;
                RailwayLineSnapshot line = lines.get(i);
                if (mouseX >= left + RAIL_REMOVE_HIT_X) {
                    sendRailway(RailwayCommandPayload.Action.REMOVE_LINE, line.lineId(), null);
                } else if (this.minecraft != null) {
                    this.minecraft.setScreen(new RailwayLineEditorScreen(
                            this, this.menu.getPylonPos(), line, this.menu.getRailwayStations()));
                }
                return true;
            }
            if (inside(mouseX, mouseY, left, top + RAIL_ACTION_Y, CONTENT_WIDTH, 22)
                    && this.minecraft != null) {
                this.minecraft.setScreen(new RailwayLineEditorScreen(
                        this, this.menu.getPylonPos(), null, this.menu.getRailwayStations()));
                return true;
            }
        }
        if (this.railwayPage == 2) {
            List<RailwayTrainSnapshot> trains = this.menu.getRailwayTrains();
            RailwayTrainSnapshot detail = trains.stream()
                    .filter(train -> train.fleetTrainId().equals(this.railwayTrainDetail))
                    .findFirst().orElse(null);
            if (detail != null) {
                if(detail.cityOwned()&&detail.uiStatus().action()!=CcecStatus.Action.NONE&&inside(mouseX,mouseY,left+12,top+170,CONTENT_WIDTH-24,22)){
                    if(detail.uiStatus().action()==CcecStatus.Action.OPEN_FREIGHT_POINT&&this.minecraft!=null)this.minecraft.setScreen(new LogisticsScreen(this));
                    else if(detail.uiStatus().action()==CcecStatus.Action.SELECT_ROUTE||detail.uiStatus().action()==CcecStatus.Action.CONFIRM_DEPARTURE)sendRailway(RailwayCommandPayload.Action.REQUEST_ROUTES,detail.fleetTrainId(),null);
                    else operateTrain(detail);
                } else if (inside(mouseX, mouseY, left + HALF_W2, top + RAIL_ACTION_Y, HALF_W, 22)) {
                    this.railwayTrainDetail = null;
                } else if (inside(mouseX, mouseY, left + RAIL_ACTION_REMOVE_X, top + 202, 74, 22)) {
                    sendRailway(RailwayCommandPayload.Action.REMOVE_TRAIN, detail.fleetTrainId(), null);
                    this.railwayTrainDetail = null;
                } else if (inside(mouseX, mouseY, left, top + RAIL_ACTION_Y, HALF_W, 22)) {
                    RailwayTypes.TrainType next = RailwayTypes.TrainType.values()[
                            (detail.type().ordinal() + 1) % RailwayTypes.TrainType.values().length];
                    PacketDistributor.sendToServer(new RailwayCommandPayload(
                            this.menu.getPylonPos(), RailwayCommandPayload.Action.SET_TRAIN_TYPE,
                            detail.fleetTrainId(), null, "", next.ordinal(), true, List.of()));
                } else if (inside(mouseX, mouseY, left, top + 202, RAIL_ACTION_MAIN_W, 22)) {
                    operateTrain(detail);
                }
                return true;
            }
            if (inside(mouseX, mouseY, left, top + RAIL_ACTION_Y, CONTENT_WIDTH, 22)) {
                if (!this.menu.getRailwayLines().isEmpty()) this.railwaySelectedLine++;
                return true;
            }
            int railActionX = left + CONTENT_WIDTH - 150;
            List<TrainRow> rows = trainRows();
            int end = Math.min(rows.size(), this.railwayScroll + railRows(31));
            for (int i = this.railwayScroll; i < end; i++) {
                int y = top + 51 + (i - this.railwayScroll) * 31;
                TrainRow row = rows.get(i);
                if (!row.registered()) {
                    // Unregistered Create train: the row itself is the register action, and the
                    // server replies with a state push that re-renders it as a registered train.
                    if (inside(mouseX, mouseY, left, y, CONTENT_WIDTH, 27) && row.candidate().canManage()) {
                        sendRailway(RailwayCommandPayload.Action.REGISTER_TRAIN,
                                row.candidate().createTrainId(), null);
                    }
                    return true;
                }
                RailwayTrainSnapshot train = row.train();
                if (inside(mouseX, mouseY, left, y, 165, 27)) {
                    this.railwayTrainDetail = train.fleetTrainId();
                    return true;
                }
                if (!inside(mouseX, mouseY, left + 165, y, CONTENT_WIDTH - 165, 27)) continue;
                if (train.cityOwned() && mouseX >= left + RAIL_REMOVE_HIT_X) {
                    sendRailway(RailwayCommandPayload.Action.REMOVE_TRAIN, train.fleetTrainId(), null);
                } else if (train.cityOwned() && mouseX < railActionX) {
                    RailwayTypes.TrainType next = RailwayTypes.TrainType.values()[
                            (train.type().ordinal() + 1) % RailwayTypes.TrainType.values().length];
                    PacketDistributor.sendToServer(new RailwayCommandPayload(
                            this.menu.getPylonPos(), RailwayCommandPayload.Action.SET_TRAIN_TYPE,
                            train.fleetTrainId(), null, "", next.ordinal(), true, List.of()));
                } else {
                    operateTrain(train);
                }
                return true;
            }
        }
        if (this.railwayPage == 3) {
            List<RailwayStationSnapshot> stations = this.menu.getRailwayStations();
            RailwayStationSnapshot detail = stations.stream()
                    .filter(station -> station.registrationId().equals(this.railwayStationDetail))
                    .findFirst().orElse(null);
            if (detail != null) {
                if (inside(mouseX, mouseY, left + HALF_W2, top + RAIL_ACTION_Y, HALF_W, 22)) {
                    this.railwayStationDetail = null;
                } else if (inside(mouseX, mouseY, left + RAIL_ACTION_REMOVE_X, top + 202, 74, 22)) {
                    sendRailway(RailwayCommandPayload.Action.REMOVE_STATION, detail.registrationId(), null);
                    this.railwayStationDetail = null;
                } else if (inside(mouseX, mouseY, left, top + RAIL_ACTION_Y, HALF_W, 22)) {
                    if (detail.linkedProjectId() != null) {
                        sendRailway(RailwayCommandPayload.Action.LINK_PROJECT,
                                detail.linkedProjectId(), null);
                    } else if (!this.menu.getProjects().isEmpty()) {
                        sendRailway(RailwayCommandPayload.Action.LINK_PROJECT,
                                this.menu.getProjects().getFirst().projectId(), detail.registrationId());
                    }
                } else if (inside(mouseX, mouseY, left, top + 202, RAIL_ACTION_MAIN_W, 22)) {
                    operateStation(detail);
                }
                return true;
            }
            int railActionX = left + CONTENT_WIDTH - 150;
            int end = Math.min(stations.size(), this.railwayScroll + railRows(31));
            for (int i = this.railwayScroll; i < end; i++) {
                int y = top + 51 + (i - this.railwayScroll) * 31;
                if (inside(mouseX, mouseY, left, y, 165, 27)) {
                    this.railwayStationDetail = stations.get(i).registrationId();
                    return true;
                }
                if (!inside(mouseX, mouseY, left + 165, y, CONTENT_WIDTH - 165, 27)) continue;
                RailwayStationSnapshot station = stations.get(i);
                if (station.cityOwned() && mouseX >= left + RAIL_REMOVE_HIT_X) {
                    sendRailway(RailwayCommandPayload.Action.REMOVE_STATION, station.registrationId(), null);
                } else if (!station.cityOwned()) {
                    sendRailway(RailwayCommandPayload.Action.ATTACH_STATION, station.registrationId(), null);
                } else if (mouseX < railActionX && !this.menu.getProjects().isEmpty()) {
                    sendRailway(RailwayCommandPayload.Action.LINK_PROJECT,
                            this.menu.getProjects().getFirst().projectId(), station.registrationId());
                } else if (!station.available()) {
                    this.menu.getRailwayStations().stream()
                            .filter(candidate -> !candidate.cityOwned() && candidate.available())
                            .findFirst().ifPresent(candidate -> sendRailway(
                                    RailwayCommandPayload.Action.REBIND_STATION,
                                    station.registrationId(), candidate.registrationId()));
                } else {
                    RailwayTypes.StationType next = RailwayTypes.StationType.values()[
                            (station.type().ordinal() + 1) % RailwayTypes.StationType.values().length];
                    PacketDistributor.sendToServer(new RailwayCommandPayload(
                            this.menu.getPylonPos(), RailwayCommandPayload.Action.SET_STATION_TYPE,
                            station.registrationId(), null, "", next.ordinal(), true, List.of()));
                }
                return true;
            }
        }
        return false;
    }

    private void operateTrain(RailwayTrainSnapshot train) {
        if (!train.cityOwned()) {
            sendRailway(RailwayCommandPayload.Action.ATTACH_TRAIN, train.fleetTrainId(), null);
        } else if ("missing".equals(train.statusKey())) {
            this.menu.getRailwayTrains().stream()
                    .filter(candidate -> !candidate.cityOwned() && !"missing".equals(candidate.statusKey()))
                    .findFirst().ifPresent(candidate -> sendRailway(
                            RailwayCommandPayload.Action.REBIND_TRAIN,
                            train.fleetTrainId(), candidate.fleetTrainId()));
        } else if (train.automationSuspended()) {
            sendRailway(RailwayCommandPayload.Action.RESTORE_SCHEDULE, train.fleetTrainId(), null);
        } else if (train.activeLineId() != null) {
            sendRailway(RailwayCommandPayload.Action.UNASSIGN_LINE, train.fleetTrainId(), null);
        } else if (!this.menu.getRailwayLines().isEmpty()) {
            List<RailwayLineSnapshot> lines = this.menu.getRailwayLines();
            sendRailway(RailwayCommandPayload.Action.ASSIGN_LINE, train.fleetTrainId(),
                    lines.get(Math.floorMod(this.railwaySelectedLine, lines.size())).lineId());
        }
    }

    private void operateStation(RailwayStationSnapshot station) {
        if (!station.cityOwned()) {
            sendRailway(RailwayCommandPayload.Action.ATTACH_STATION, station.registrationId(), null);
        } else if (!station.available()) {
            this.menu.getRailwayStations().stream()
                    .filter(candidate -> !candidate.cityOwned() && candidate.available())
                    .findFirst().ifPresent(candidate -> sendRailway(
                            RailwayCommandPayload.Action.REBIND_STATION,
                            station.registrationId(), candidate.registrationId()));
        } else {
            RailwayTypes.StationType next = RailwayTypes.StationType.values()[
                    (station.type().ordinal() + 1) % RailwayTypes.StationType.values().length];
            PacketDistributor.sendToServer(new RailwayCommandPayload(
                    this.menu.getPylonPos(), RailwayCommandPayload.Action.SET_STATION_TYPE,
                    station.registrationId(), null, "", next.ordinal(), true, List.of()));
        }
    }

    private void renderEconomy(GuiGraphics graphics,int left,int top,int mouseX,int mouseY){
        EconomyStatePayload state=this.menu.getEconomyState();
        if(state==null){graphics.drawString(this.font,Component.translatable("gui.createcityexpansioncore.economy.loading"),left,top+32,0xFFB7C0C3,false);return;}
        if(!state.unlocked()){graphics.fill(left,top+29,left+CONTENT_WIDTH,top+139,0xCC171D20);graphics.drawCenteredString(this.font,Component.translatable("gui.createcityexpansioncore.economy.locked"),left+CONTENT_WIDTH/2,top+45,0xFFD09A3A);graphics.drawCenteredString(this.font,Component.translatable("gui.createcityexpansioncore.economy.locked_hint"),left+CONTENT_WIDTH/2,top+63,0xFF9CA6A9);graphics.drawWordWrap(this.font,Component.translatable("gui.createcityexpansioncore.economy.locked_action"),left+28,top+84,CONTENT_WIDTH-56,0xFFB7C0C3);return;}
        graphics.drawString(this.font,Component.translatable("gui.createcityexpansioncore.economy.wallet",formatCount(state.wallet())),left,top+23,0xFFB7C0C3,false);
        graphics.drawString(this.font,Component.translatable("gui.createcityexpansioncore.economy.treasury",formatCount(state.treasury()),formatCount(state.treasuryEscrow())),left+145,top+23,0xFFB7C0C3,false);
        renderEconomyButton(graphics,left+CONTENT_WIDTH-128,top+19,100,Component.translatable("gui.createcityexpansioncore.market.open"),mouseX,mouseY);
        renderEconomyButton(graphics,left+CONTENT_WIDTH-23,top+19,23,Component.literal("?"),mouseX,mouseY);
        graphics.drawString(this.font,Component.translatable("gui.createcityexpansioncore.economy.fees",state.privateFeeBasisPoints()/100.0,state.cityFeeBasisPoints()/100.0),left,top+32,0xFF657177,false);
        if(marketplaceHintVisible||marketplaceHelpVisible){renderMarketplaceOverlay(graphics,left,top,mouseX,mouseY);return;}
        int pageCount=state.masterCity()?5:4;
        int pageWidth=CONTENT_WIDTH/pageCount;
        for(int i=0;i<pageCount;i++){int x=left+i*pageWidth;boolean selected=economyPage==i;graphics.fill(x,top+42,x+pageWidth-3,top+60,selected?0xFF6B4D24:0xFF252D31);graphics.drawCenteredString(this.font,Component.translatable("gui.createcityexpansioncore.economy.page."+switch(i){case 1->"mine";case 2->"create";case 3->"infrastructure";case 4->"master";default->"market";}),x+(pageWidth-3)/2,top+47,selected?0xFFFFD59A:0xFFAEB8BB);}
        if(economyPage==2){renderEconomyCreate(graphics,left,top,state,mouseX,mouseY);return;}
        if(economyPage==3){renderEconomyInfrastructure(graphics,left,top,state,mouseX,mouseY);return;}
        if(economyPage==4&&state.masterCity()){renderEconomyMaster(graphics,left,top,state,mouseX,mouseY);return;}
        renderEconomyMarket(graphics,left,top,state,mouseX,mouseY);
    }

    /*
     * =========================================
     * РЫНОК: СПИСОК СЛЕВА + ДЕТАЛИ СПРАВА
     * =========================================
     */

    private void renderEconomyMarket(GuiGraphics g,int left,int top,EconomyStatePayload state,int mouseX,int mouseY){
        List<EconomyEntry> entries=economyVisibleEntries(state);
        EconomyEntry selected=entries.stream().filter(e->economySelection!=null&&economySelection.equals(e.id())&&economySelectionListing==e.listing()).findFirst().orElse(null);

        renderEconomyButton(g,left+104,top+MARKET_TOOLBAR_Y,64,Component.translatable("gui.createcityexpansioncore.economy.sort.short."+switch(economySort){case 1->"price";case 2->"deadline";default->"new";}),mouseX,mouseY);

        int bodyTop=top+MARKET_BODY_Y;
        int bodyBottom=top+CONTENT_BODY_BOTTOM;

        beginClip(g,left,bodyTop,left+MARKET_LIST_W,bodyBottom);
        int max=Math.min(entries.size(),economyScroll+MARKET_VISIBLE_ROWS);
        for(int i=economyScroll;i<max;i++){
            EconomyEntry entry=entries.get(i);
            int y=bodyTop+(i-economyScroll)*MARKET_ROW_PITCH;
            boolean active=economySelection!=null&&economySelection.equals(entry.id())&&economySelectionListing==entry.listing();
            renderEconomyCard(g,left,y,MARKET_LIST_W,entry,active,mouseX,mouseY);
        }
        g.disableScissor();

        if(entries.isEmpty()){
            g.drawWordWrap(this.font,Component.translatable(economyPage==1?"gui.createcityexpansioncore.economy.empty_mine":"gui.createcityexpansioncore.economy.empty_action"),
                    left+8,bodyTop+6,MARKET_LIST_W-16,0xFF8F9A9E);
        }

        int detailX=left+MARKET_LIST_W+PANEL_GAP;
        if(selected!=null)renderEconomyDetailPanel(g,detailX,top+MARKET_TOOLBAR_Y,selected,state,mouseX,mouseY);
        else renderEconomyDetailPlaceholder(g,detailX,top+MARKET_TOOLBAR_Y);
    }

    /** Карточка объявления/контракта в левой колонке. */
    private void renderEconomyCard(GuiGraphics g,int x,int y,int width,EconomyEntry entry,boolean selected,int mouseX,int mouseY){
        boolean hover=inside(mouseX,mouseY,x,y,width,MARKET_ROW_H);
        g.fill(x,y,x+width,y+MARKET_ROW_H,selected?0xFF26323A:(hover?0xFF242C31:0xFF20272B));
        if(selected)g.renderOutline(x,y,width,MARKET_ROW_H,0xFFD09A3A);

        ItemStack icon=economyIcon(entry.item());
        g.renderItem(icon,x+4,y+3);

        Component badge=economyCardBadge(entry);
        int badgeWidth=this.font.width(badge)+8;
        int badgeX=x+width-4-badgeWidth;
        g.fill(badgeX,y+3,badgeX+badgeWidth,y+14,economyCardBadgeColor(entry));
        g.drawCenteredString(this.font,badge,badgeX+badgeWidth/2,y+4,0xFFFFFFFF);

        g.drawString(this.font,ellipsize(Component.literal(economyCardTitle(entry)),badgeX-x-26),
                x+22,y+5,0xFFE2E6E7,false);

        Component exchange=economyCardExchange(entry);
        Component author=economyCardAuthor(entry);
        g.drawString(this.font,ellipsize(exchange,width-12-56),x+4,y+21,0xFFFFD59A,false);
        g.drawString(this.font,ellipsize(author,56),x+width-4-56,y+21,0xFF8F9A9E,false);
    }

    private Component economyCardBadge(EconomyEntry entry){
        if(entry.contract()!=null)return Component.translatable("gui.createcityexpansioncore.economy.badge.contract");
        var l=entry.listingView();
        if(l.isBarter())return Component.translatable("gui.createcityexpansioncore.economy.badge.barter");
        return Component.translatable(l.offersCurrency()?"gui.createcityexpansioncore.economy.badge.buy":"gui.createcityexpansioncore.economy.badge.sell");
    }

    private int economyCardBadgeColor(EconomyEntry entry){
        if(entry.contract()!=null)return 0xFF3A4A55;
        var l=entry.listingView();
        if(l.isBarter())return 0xFF6B4D24;
        return l.offersCurrency()?0xFF2E4A3B:0xFF4A3A2E;
    }

    private String economyCardTitle(EconomyEntry entry){
        if(entry.contract()!=null){
            var c=entry.contract();
            return c.title().isBlank()?economyIcon(entry.item()).getHoverName().getString():c.title();
        }
        var l=entry.listingView();
        ResourceLocation id=l.offerItemId();
        if(id==null)return Component.translatable("gui.createcityexpansioncore.economy.card.money_line",formatCount(l.offerAmount())).getString();
        return formatCount(l.offerAmount())+" "+economyIcon(id).getHoverName().getString();
    }

    private Component economyCardExchange(EconomyEntry entry){
        if(entry.contract()!=null)return Component.translatable("gui.createcityexpansioncore.economy.card_contract",formatCount(entry.contract().amount()),formatCount(entry.contract().reward()));
        var l=entry.listingView();
        String symbol=l.isBarter()?"? ":"? ";
        ResourceLocation id=l.requestItemId();
        if(id==null)return Component.literal(symbol+Component.translatable("gui.createcityexpansioncore.economy.card.money_line",formatCount(l.requestAmount())).getString());
        return Component.literal(symbol+formatCount(l.requestAmount())+" "+economyIcon(id).getHoverName().getString());
    }

    private Component economyCardAuthor(EconomyEntry entry){
        return Component.literal(entry.contract()!=null?entry.contract().issuerName():entry.listingView().creatorName());
    }

    private void renderEconomyDetailPlaceholder(GuiGraphics g,int x,int y){
        g.fill(x,y,x+MARKET_DETAIL_W,y+(CONTENT_BODY_BOTTOM-MARKET_TOOLBAR_Y),0xFF1C2327);
        g.renderOutline(x,y,MARKET_DETAIL_W,CONTENT_BODY_BOTTOM-MARKET_TOOLBAR_Y,0xFF30383C);
        g.drawCenteredString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.detail.empty"),MARKET_DETAIL_W-8),
                x+MARKET_DETAIL_W/2,y+24,0xFF8F9A9E);
        g.drawWordWrap(this.font,Component.translatable("gui.createcityexpansioncore.economy.detail.empty_hint"),
                x+8,y+44,MARKET_DETAIL_W-16,0xFF657177);
    }

    /*
     * Панель деталей справа.
     * Контент обрезается по своей области, чтобы текст не лез на кнопки.
     */
    private void renderEconomyDetailPanel(GuiGraphics g,int x,int y,EconomyEntry entry,EconomyStatePayload state,int mouseX,int mouseY){
        boolean isContract=entry.contract()!=null;
        int width=MARKET_DETAIL_W;
        int height=CONTENT_BODY_BOTTOM-MARKET_TOOLBAR_Y;
        /*
         * Низ обрезки отступает под строку бартера (иначе она резалась),
         * а кнопки прижаты к низу панели — мёртвая полоса исчезает.
         */
        int contentBottom=y+139;
        int actionY=y+height-26;
        g.fill(x,y,x+width,y+height,0xFF1C2327);
        g.renderOutline(x,y,width,height,0xFF30383C);

        beginClip(g,x+1,y+1,x+width-1,contentBottom);
        if(isContract)renderEconomyContractDetail(g,x,y,width,entry,state);
        else renderEconomyListingDetail(g,x,y,width,entry,mouseX,mouseY);
        g.disableScissor();

        Component action=economyAction(entry);
        if(action!=null){
            renderEconomyButton(g,x+4,actionY,width-8,action,mouseX,mouseY);
        }else{
            g.fill(x+4,actionY,x+width-4,actionY+22,0xFF2B3032);
            g.drawCenteredString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.action_unavailable"),width-12),
                    x+width/2,actionY+7,0xFF727A7D);
            if(inside(mouseX,mouseY,x+4,actionY,width-8,22))deferTooltip(economyNextStep(entry));
        }
        if(isContract)renderEconomyButton(g,x+4,actionY-28,width-8,Component.translatable("gui.createcityexpansioncore.economy.map"),mouseX,mouseY);
    }

    private void renderEconomyListingDetail(GuiGraphics g,int x,int y,int width,EconomyEntry entry,int mouseX,int mouseY){
        var l=entry.listingView();
        int inner=width-8;
        int cursor=y+3;
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.detail.header"),inner),
                x+4,cursor,0xFFFFD59A,false);
        cursor+=10;
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.detail.offers",l.creatorName()),inner),
                x+4,cursor,0xFF8F9A9E,false);
        cursor+=10;

        cursor=detailExchangeRow(g,x+4,cursor,inner,l.offerItemId(),l.offerAmount(),"");
        cursor=detailExchangeRow(g,x+4,cursor,inner,l.requestItemId(),l.requestAmount(),l.isBarter()?"? ":"? ");

        boolean escrowHeld=!l.isBarter()&&(l.offersCurrency()
                ||l.state()!=com.example.examplemod.economy.EconomyTypes.ListingState.OPEN);
        if(escrowHeld){
            g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.detail.escrow",formatCount(l.price())),inner),
                    x+4,cursor,0xFFFFD59A,false);
            if(inside(mouseX,mouseY,x+4,cursor,inner,10))deferTooltip(Component.translatable("tooltip.createcityexpansioncore.economy.escrow"));
            cursor+=10;
        }

        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.journey.listing."+l.state().name().toLowerCase(java.util.Locale.ROOT)),inner),
                x+4,cursor,economyStateColor(l.state().name()),false);
        cursor+=10;
        g.fill(x+4,cursor,x+width-4,cursor+1,0xFF3A4246);
        cursor+=4;
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.detail.journey"),inner),
                x+4,cursor,0xFFE2E6E7,false);
        cursor+=11;
        for(int step=1;step<=4;step++){
            g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.detail.step."+step),inner),
                    x+4,cursor,0xFFB7C0C3,false);
            cursor+=9;
        }
        if(l.isBarter())g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.detail.barter_note"),inner),
                x+4,cursor,0xFF657177,false);
    }

    /** Строка «[иконка] 64 Медный лист» или «Валюта 800» в панели деталей. */
    private int detailExchangeRow(GuiGraphics g,int x,int y,int width,ResourceLocation id,long amount,String prefix){
        if(id==null){
            g.drawString(this.font,ellipsize(Component.literal(prefix+Component.translatable("gui.createcityexpansioncore.economy.card.money_line",formatCount(amount)).getString()),width),
                    x,y+4,0xFFFFD59A,false);
            return y+17;
        }
        ItemStack stack=economyIcon(id);
        g.renderItem(stack,x,y);
        g.drawString(this.font,ellipsize(Component.literal(prefix+formatCount(amount)+" "+stack.getHoverName().getString()),width-20),
                x+20,y+4,0xFFE2E6E7,false);
        return y+17;
    }

    private void renderEconomyContractDetail(GuiGraphics g,int x,int y,int width,EconomyEntry entry,EconomyStatePayload state){
        var c=entry.contract();
        int inner=width-8;
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.detail.contract_header"),inner),
                x+4,y+3,0xFFFFD59A,false);
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.issuer",c.issuerName()),inner),
                x+4,y+14,0xFFAEB8BB,false);
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.contractor",c.contractorName().isBlank()?"—":c.contractorName()),inner),
                x+4,y+25,0xFFAEB8BB,false);
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.reward",formatCount(c.reward())),inner),
                x+4,y+36,0xFFFFD59A,false);
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.delivery",formatCount(c.delivered()),formatCount(c.amount())),inner),
                x+4,y+47,0xFFAEB8BB,false);
        double progress=c.amount()<=0?0.0:Math.min(1.0,(double)c.delivered()/c.amount());
        g.fill(x+4,y+58,x+width-4,y+62,0xFF30383C);
        g.fill(x+4,y+58,x+4+(int)Math.round((width-8)*progress),y+62,0xFFD09A3A);
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.journey.contract."+c.state().name().toLowerCase(java.util.Locale.ROOT)),inner),
                x+4,y+66,economyStateColor(c.state().name()),false);
        g.drawWordWrap(this.font,ellipsize(economyNextStep(entry),inner*2),x+4,y+78,inner,0xFFB7C0C3);
    }

    private void renderEconomyHeaderTooltip(GuiGraphics graphics,int left,int top,int mouseX,int mouseY){deferEconomyHeaderTooltip(left,top,mouseX,mouseY);}

    /**
     * Подсказки шапки экономики: «?» и строка с эскроу.
     * Запоминаем, а не рисуем — см. flushDeferredTooltip().
     */
    private void deferEconomyHeaderTooltip(int left,int top,int mouseX,int mouseY){
        EconomyStatePayload state=this.menu.getEconomyState();
        if(state==null||!state.unlocked())return;
        if(inside(mouseX,mouseY,left+CONTENT_WIDTH-23,top+19,23,22))
            deferTooltip(Component.translatable("gui.createcityexpansioncore.economy.help.hover"));
        else if(inside(mouseX,mouseY,left+145,top+20,128,13))
            deferTooltip(Component.translatable("tooltip.createcityexpansioncore.economy.escrow"));
    }

    private void renderMarketplaceOverlay(GuiGraphics g,int left,int top,int mouseX,int mouseY){
        int overlayTop=top+40,overlayBottom=top+CONTENT_BODY_BOTTOM;
        int buttonY=overlayBottom-30;
        int textX=left+15,textW=CONTENT_WIDTH-30;
        int contentBottom=buttonY-6;
        g.fill(left+3,overlayTop,left+CONTENT_WIDTH-3,overlayBottom,0xF51A2125);
        g.renderOutline(left+3,overlayTop,CONTENT_WIDTH-6,overlayBottom-overlayTop,0xFFD09A3A);

        /*
         * Справка не обрезается: если текст не влез — он прокручивается
         * колесом, а снизу видна стрелка «ещё есть».
         */
        int maxScroll=Math.max(0,helpContentHeight-(contentBottom-overlayTop-14));
        marketplaceHelpScroll=Math.clamp(marketplaceHelpScroll,0,maxScroll);
        beginClip(g,left+4,overlayTop+1,left+CONTENT_WIDTH-4,contentBottom);
        g.pose().pushPose();
        g.pose().translate(0.0D,-marketplaceHelpScroll,0.0D);
        int cursor;
        if(marketplaceHintVisible){
            g.drawCenteredString(this.font,Component.translatable("gui.createcityexpansioncore.economy.overview.title"),left+CONTENT_CENTER,overlayTop+16,0xFFFFD59A);
            cursor=drawWrapped(g,Component.translatable("gui.createcityexpansioncore.economy.overview.intro"),textX,overlayTop+34,textW,0xFFC5CCCE);
            cursor=drawHelpSection(g,Component.translatable("gui.createcityexpansioncore.economy.overview.private_title"),Component.translatable("gui.createcityexpansioncore.economy.overview.private_text"),textX,cursor+7,textW);
            cursor=drawHelpSection(g,Component.translatable("gui.createcityexpansioncore.economy.overview.contract_title"),Component.translatable("gui.createcityexpansioncore.economy.overview.contract_text"),textX,cursor+7,textW);
            cursor=drawHelpSection(g,Component.translatable("gui.createcityexpansioncore.economy.overview.smart_title"),Component.translatable("gui.createcityexpansioncore.economy.overview.smart_text"),textX,cursor+7,textW);
        }else{
            g.drawCenteredString(this.font,Component.translatable("gui.createcityexpansioncore.economy.help.title"),left+CONTENT_CENTER,overlayTop+16,0xFFFFD59A);
            g.drawString(this.font,Component.translatable("gui.createcityexpansioncore.economy.help.industrial"),textX,overlayTop+34,0xFFFFFFFF,false);
            cursor=drawWrapped(g,Component.translatable("gui.createcityexpansioncore.economy.help.industrial_steps"),textX,overlayTop+47,textW,0xFFB7C0C3);
            cursor=drawHelpSection(g,Component.translatable("gui.createcityexpansioncore.economy.help.private"),Component.translatable("gui.createcityexpansioncore.economy.help.private_steps"),textX,cursor+7,textW);
        }
        g.pose().popPose();
        g.disableScissor();
        helpContentHeight=cursor-overlayTop;
        if(marketplaceHelpScroll>0||marketplaceHelpScroll<maxScroll)
            g.drawString(this.font,marketplaceHelpScroll>0?"?":"?",left+CONTENT_WIDTH-18,contentBottom-11,0xFFFFD59A,false);
        if(marketplaceHintVisible)
            renderEconomyButton(g,left+CONTENT_CENTER-58,buttonY,116,Component.translatable("gui.createcityexpansioncore.economy.understood"),mouseX,mouseY);
        else
            renderEconomyButton(g,left+CONTENT_CENTER-45,buttonY,90,Component.translatable("gui.createcityexpansioncore.common.close"),mouseX,mouseY);
    }

    /** Заголовок секции + переносимый текст; возвращает Y следующей строки. */
    private int drawHelpSection(GuiGraphics g,Component title,Component body,int x,int y,int width){
        g.drawString(this.font,title,x,y,0xFFFFFFFF,false);
        return drawWrapped(g,body,x,y+this.font.lineHeight,width,0xFFB7C0C3);
    }

    /** Перенос без «...»: полный текст и сразу высота занятых строк. */
    private int drawWrapped(GuiGraphics g,Component text,int x,int y,int width,int color){
        for(FormattedCharSequence line:this.font.split(text,width)){
            g.drawString(this.font,line,x,y,color,false);
            y+=this.font.lineHeight;
        }
        return y;
    }

    private void renderEconomyCreate(GuiGraphics g,int left,int top,EconomyStatePayload state,int mouseX,int mouseY){
        g.fill(left,top+65,left+CONTENT_WIDTH,top+CONTENT_BODY_BOTTOM,0xFF20272B);
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.create_hint"),CONTENT_WIDTH-24),left+12,top+76,0xFFB7C0C3,false);
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.create_private_hint"),CONTENT_WIDTH-24),left+12,top+87,0xFF657177,false);
        renderEconomyButton(g,left+12,top+101,CONTENT_WIDTH-24,Component.translatable("gui.createcityexpansioncore.economy.create_private"),mouseX,mouseY);
        renderEconomyButton(g,left+12,top+132,CONTENT_WIDTH-24,Component.translatable("gui.createcityexpansioncore.economy.create_contract"),mouseX,mouseY);
        if(state.masterCity())renderEconomyButton(g,left+12,top+163,CONTENT_WIDTH-24,Component.translatable("gui.createcityexpansioncore.economy.create_special"),mouseX,mouseY);
    }
    private void renderEconomyInfrastructure(GuiGraphics g,int left,int top,EconomyStatePayload state,int mouseX,int mouseY){
        g.fill(left,top+65,left+CONTENT_WIDTH,top+CONTENT_BODY_BOTTOM,0xFF20272B);
        List<EconomyStatePayload.InfrastructureView> values=state.infrastructure();
        if(values.isEmpty()){g.drawWordWrap(this.font,Component.translatable("gui.createcityexpansioncore.economy.infrastructure.empty_action"),left+14,top+79,CONTENT_WIDTH-28,0xFF8F9A9E);return;}
        int end=Math.min(values.size(),economyScroll+INFRA_ROWS);
        beginClip(g,left,top+64,left+CONTENT_WIDTH,top+CONTENT_BODY_BOTTOM);
        for(int i=economyScroll;i<end;i++){
            var value=values.get(i);int y=top+68+(i-economyScroll)*INFRA_ROW_H;
            g.fill(left+5,y,left+CONTENT_WIDTH-5,y+39,value.cityOwned()?0xFF26332E:0xFF292E31);
            Component name=Component.translatable(value.type()==com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE?"block.createcityexpansioncore.contract_intake":"block.createcityexpansioncore.warehouse_gateway");
            g.drawString(this.font,name,left+10,y+3,value.available()?0xFFE2E6E7:0xFF8F9A9E,false);
            g.drawString(this.font,Component.translatable(value.cityOwned()?"gui.createcityexpansioncore.economy.infrastructure.city":"gui.createcityexpansioncore.economy.infrastructure.personal",value.pos().getX(),value.pos().getY(),value.pos().getZ()),left+10,y+14,value.cityOwned()?0xFF77B992:0xFFAEB8BB,false);
            if(value.type()==com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE&&value.cityOwned()){
                RailwayStationSnapshot station=this.menu.getRailwayStations().stream().filter(candidate->candidate.registrationId().equals(value.stationId())).findFirst().orElse(null);
                Component stationText=station==null?Component.translatable("gui.createcityexpansioncore.economy.infrastructure.station_missing"):Component.translatable("gui.createcityexpansioncore.economy.infrastructure.station",station.name());
                Component detail=value.lastError()==com.example.examplemod.economy.EconomyTypes.IntakeAttributionError.NONE?stationText:Component.translatable("status.createcityexpansioncore.economy.intake_error."+value.lastError().name().toLowerCase());
                g.drawString(this.font,detail,left+10,y+26,value.lastError()==com.example.examplemod.economy.EconomyTypes.IntakeAttributionError.NONE?(station==null?0xFFD09A3A:0xFFB7C0C3):0xFFD07872,false);
                boolean stationAvailable=!eligibleFreightStations().isEmpty();
                if(stationAvailable)renderEconomyButton(g,left+CONTENT_WIDTH-84,y+15,78,Component.translatable(value.stationId()==null?"gui.createcityexpansioncore.economy.infrastructure.assign_station":"gui.createcityexpansioncore.economy.infrastructure.change_station"),mouseX,mouseY);
            }else if(!value.cityOwned())renderEconomyButton(g,left+CONTENT_WIDTH-76,y+9,70,Component.translatable("gui.createcityexpansioncore.economy.infrastructure.attach"),mouseX,mouseY);
            if(inside(mouseX,mouseY,left+5,y,CONTENT_WIDTH-10,39))deferTooltip(Component.translatable(value.type()==com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE?"tooltip.createcityexpansioncore.economy.contract_intake":"tooltip.createcityexpansioncore.economy.warehouse_gateway"));
        }
        g.disableScissor();
    }

    private List<RailwayStationSnapshot> eligibleFreightStations(){return this.menu.getRailwayStations().stream().filter(RailwayStationSnapshot::cityOwned).filter(RailwayStationSnapshot::available).filter(value->value.type()==RailwayTypes.StationType.FREIGHT||value.type()==RailwayTypes.StationType.INDUSTRIAL||value.type()==RailwayTypes.StationType.MIXED).toList();}
    private RailwayStationSnapshot nextFreightStation(UUID current){List<RailwayStationSnapshot> values=eligibleFreightStations();if(values.isEmpty())return null;for(int i=0;i<values.size();i++)if(values.get(i).registrationId().equals(current))return values.get((i+1)%values.size());return values.getFirst();}
    private void renderEconomyMaster(GuiGraphics g,int left,int top,EconomyStatePayload state,int mouseX,int mouseY){g.fill(left,top+65,left+CONTENT_WIDTH,top+CONTENT_BODY_BOTTOM,0xFF20272B);g.drawString(this.font,Component.translatable("gui.createcityexpansioncore.economy.procurement",formatCount(state.procurementAvailable()),formatCount(state.procurementReserved()),formatCount(state.procurementCap())),left+10,top+74,0xFFFFD59A,false);g.drawString(this.font,Component.translatable("gui.createcityexpansioncore.economy.warehouse"),left+10,top+93,0xFFE2E6E7,false);int y=top+108;for(var stock:state.warehouse().stream().limit(5).toList()){ItemStack icon=economyIcon(stock.itemId());g.renderItem(icon,left+10,y);g.drawString(this.font,icon.getHoverName(),left+32,y+4,0xFFB7C0C3,false);g.drawString(this.font,formatCount(stock.amount()),left+CONTENT_WIDTH-75,y+4,0xFF77B992,false);y+=18;}if(state.warehouse().isEmpty())g.drawWordWrap(this.font,Component.translatable("gui.createcityexpansioncore.economy.warehouse_empty"),left+10,y,CONTENT_WIDTH-20,0xFF8F9A9E);if(inside(mouseX,mouseY,left+8,top+70,CONTENT_WIDTH-16,18))deferTooltip(Component.translatable("tooltip.createcityexpansioncore.economy.procurement_budget"));else if(inside(mouseX,mouseY,left+8,top+90,CONTENT_WIDTH-16,96))deferTooltip(Component.translatable("tooltip.createcityexpansioncore.economy.smart_order"));}
    private Component economyNextStep(EconomyEntry entry){if(entry.contract()!=null){var c=entry.contract();String key=switch(c.state()){case OPEN->c.issuerMine()?"waiting_accept":"accept";case ACCEPTED->c.mine()?"create_shipment":"awaiting_shipment";case IN_DELIVERY->c.mine()?"continue_delivery":"awaiting_delivery";case DELIVERED->c.issuerMine()?"confirm_delivery":"awaiting_confirmation";case COMPLETED->"paid";case CANCELLED->"cancelled";case FAILED->"failed";case DISPUTED->"disputed";};return Component.translatable("gui.createcityexpansioncore.economy.next.contract."+key);}var l=entry.listingView();UUID me=this.minecraft==null||this.minecraft.player==null?null:this.minecraft.player.getUUID();UUID supplier=l.type()==com.example.examplemod.economy.EconomyTypes.ListingType.SELL?l.creator():l.counterparty();UUID buyer=l.type()==com.example.examplemod.economy.EconomyTypes.ListingType.BUY?l.creator():l.counterparty();String key=switch(l.state()){case OPEN->l.mine()?"waiting_accept":"accept";case RESERVED->java.util.Objects.equals(supplier,me)?"handover":"wait_goods";case HANDED_OVER->java.util.Objects.equals(buyer,me)?"confirm":"wait_confirmation";case COMPLETED->l.isBarter()?"done":"paid";case CANCELLED->"cancelled";case DISPUTED->"disputed";};return Component.translatable("gui.createcityexpansioncore.economy.next.listing."+key);}
    private void renderEconomyButton(GuiGraphics g,int x,int y,int width,Component label,int mouseX,int mouseY){boolean hover=inside(mouseX,mouseY,x,y,width,22);g.fill(x,y,x+width,y+22,hover?0xFF76562E:0xFF493A29);g.drawCenteredString(this.font,label,x+width/2,y+7,0xFFFFD59A);}
    private Component economyAction(EconomyEntry entry){if(entry.contract()!=null){var c=entry.contract();if(c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.OPEN&&!c.issuerMine())return Component.translatable("gui.createcityexpansioncore.economy.accept");if(c.mode()==com.example.examplemod.economy.EconomyTypes.ContractMode.COMMUNITY&&!c.mine()&&!c.issuerMine()&&(c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.ACCEPTED||c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.IN_DELIVERY))return Component.translatable("gui.createcityexpansioncore.economy.accept");if(c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.OPEN&&c.issuerMine())return Component.translatable("gui.createcityexpansioncore.economy.cancel");if((c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.ACCEPTED||c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.IN_DELIVERY)&&c.mine())return Component.translatable("gui.createcityexpansioncore.economy.shipment");if(c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.DELIVERED&&c.issuerMine())return Component.translatable("gui.createcityexpansioncore.economy.confirm");return null;}var l=entry.listingView();UUID me=this.minecraft==null||this.minecraft.player==null?null:this.minecraft.player.getUUID();if(l.state()==com.example.examplemod.economy.EconomyTypes.ListingState.OPEN&&!l.creator().equals(me))return Component.translatable("gui.createcityexpansioncore.economy.accept");if(l.state()==com.example.examplemod.economy.EconomyTypes.ListingState.OPEN&&l.creator().equals(me))return Component.translatable("gui.createcityexpansioncore.economy.cancel");UUID supplier=l.type()==com.example.examplemod.economy.EconomyTypes.ListingType.SELL?l.creator():l.counterparty();UUID buyer=l.type()==com.example.examplemod.economy.EconomyTypes.ListingType.BUY?l.creator():l.counterparty();if(l.state()==com.example.examplemod.economy.EconomyTypes.ListingState.RESERVED&&java.util.Objects.equals(supplier,me))return Component.translatable("gui.createcityexpansioncore.economy.handed");if(l.state()==com.example.examplemod.economy.EconomyTypes.ListingState.HANDED_OVER&&java.util.Objects.equals(buyer,me))return Component.translatable("gui.createcityexpansioncore.economy.confirm");return null;}
    private boolean handleEconomyClick(double mouseX,double mouseY){
        int left=this.leftPos+180,top=this.topPos+75;EconomyStatePayload state=this.menu.getEconomyState();if(state==null||!state.unlocked())return false;
        if(marketplaceHintVisible){if(inside(mouseX,mouseY,left+CONTENT_CENTER-58,top+CONTENT_BODY_BOTTOM-30,116,22)){marketplaceHintVisible=false;marketplaceHintSeen=true;marketplaceHelpScroll=0;updateTabButtonsVisibility();}return true;}
        if(marketplaceHelpVisible){if(inside(mouseX,mouseY,left+CONTENT_CENTER-45,top+CONTENT_BODY_BOTTOM-30,90,22)){marketplaceHelpVisible=false;marketplaceHelpScroll=0;updateTabButtonsVisibility();}return true;}
        if(inside(mouseX,mouseY,left+CONTENT_WIDTH-23,top+19,23,22)){marketplaceHelpVisible=true;updateTabButtonsVisibility();return true;}
        if(inside(mouseX,mouseY,left+CONTENT_WIDTH-128,top+19,100,22)){if(this.minecraft!=null)this.minecraft.setScreen(new MarketScreen(this));return true;}
        int pages=state.masterCity()?5:4;int pageW=CONTENT_WIDTH/pages;for(int i=0;i<pages;i++)if(inside(mouseX,mouseY,left+i*pageW,top+42,pageW-3,18)){economyPage=i;economyScroll=0;economySelection=null;updateTabButtonsVisibility();return true;}
        if(economyPage==2){if(inside(mouseX,mouseY,left+12,top+101,CONTENT_WIDTH-24,22)){openEconomyCreate(EconomyCreateScreen.Mode.PRIVATE);return true;}if(inside(mouseX,mouseY,left+12,top+132,CONTENT_WIDTH-24,22)){openEconomyCreate(EconomyCreateScreen.Mode.CITY_CONTRACT);return true;}if(state.masterCity()&&inside(mouseX,mouseY,left+12,top+163,CONTENT_WIDTH-24,22)){openEconomyCreate(EconomyCreateScreen.Mode.MASTER_SPECIAL);return true;}return false;}
        if(economyPage==3){int end=Math.min(state.infrastructure().size(),economyScroll+INFRA_ROWS);for(int i=economyScroll;i<end;i++){var value=state.infrastructure().get(i);int y=top+68+(i-economyScroll)*INFRA_ROW_H;if(!value.cityOwned()&&inside(mouseX,mouseY,left+CONTENT_WIDTH-76,y+9,70,22)){PacketDistributor.sendToServer(EconomyCommandPayload.simple(this.menu.getPylonPos(),EconomyCommandPayload.Action.BIND_INFRASTRUCTURE,value.id()));return true;}if(value.cityOwned()&&value.type()==com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE&&inside(mouseX,mouseY,left+CONTENT_WIDTH-84,y+15,78,22)){RailwayStationSnapshot station=nextFreightStation(value.stationId());if(station!=null)PacketDistributor.sendToServer(new EconomyCommandPayload(this.menu.getPylonPos(),EconomyCommandPayload.Action.BIND_INTAKE_STATION,value.id(),station.registrationId(),null,null,0,0,0,0,"","",""));return true;}}return false;}
        if(economyPage==4)return false;
        List<EconomyEntry> entries=economyVisibleEntries(state);EconomyEntry selected=entries.stream().filter(e->economySelection!=null&&economySelection.equals(e.id())&&economySelectionListing==e.listing()).findFirst().orElse(null);
        if(inside(mouseX,mouseY,left+104,top+MARKET_TOOLBAR_Y,64,22)){economySort=(economySort+1)%3;economyScroll=0;return true;}
        int detailX=left+MARKET_LIST_W+PANEL_GAP;
        if(selected!=null){
            if(economyAction(selected)!=null&&inside(mouseX,mouseY,detailX+4,top+CONTENT_BODY_BOTTOM-26,MARKET_DETAIL_W-8,22)){performEconomyAction(selected);return true;}
            if(selected.contract()!=null&&inside(mouseX,mouseY,detailX+4,top+CONTENT_BODY_BOTTOM-54,MARKET_DETAIL_W-8,22)){state.infrastructure().stream().filter(v->v.id().equals(selected.contract().destinationId())).findFirst().ifPresent(v->{mapPanel.centerOn(v.pos());activeTab=4;updateTabButtons();requestRailwayMap();});return true;}
        }
        int end=Math.min(entries.size(),economyScroll+MARKET_VISIBLE_ROWS);
        for(int i=economyScroll;i<end;i++){int y=top+MARKET_BODY_Y+(i-economyScroll)*MARKET_ROW_PITCH;if(inside(mouseX,mouseY,left,y,MARKET_LIST_W,MARKET_ROW_H)){EconomyEntry e=entries.get(i);economySelection=e.id();economySelectionListing=e.listing();updateTabButtonsVisibility();return true;}}
        return false;
    }
private void performEconomyAction(EconomyEntry entry){EconomyCommandPayload.Action action;if(entry.contract()!=null){var c=entry.contract();boolean communityJoin=c.mode()==com.example.examplemod.economy.EconomyTypes.ContractMode.COMMUNITY&&!c.mine()&&!c.issuerMine()&&(c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.ACCEPTED||c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.IN_DELIVERY);if((c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.ACCEPTED||c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.IN_DELIVERY)&&c.mine()){if(this.minecraft!=null&&this.menu.getEconomyState()!=null)this.minecraft.setScreen(new LogisticsScreen(this));return;}action=c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.OPEN?(c.issuerMine()?EconomyCommandPayload.Action.CANCEL_CONTRACT:EconomyCommandPayload.Action.ACCEPT_CONTRACT):communityJoin?EconomyCommandPayload.Action.ACCEPT_CONTRACT:c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.DELIVERED?EconomyCommandPayload.Action.CONFIRM_CONTRACT:null;}else{var l=entry.listingView();action=switch(l.state()){case OPEN->l.mine()?EconomyCommandPayload.Action.CANCEL_LISTING:EconomyCommandPayload.Action.ACCEPT_LISTING;case RESERVED->EconomyCommandPayload.Action.MARK_HANDED;case HANDED_OVER->EconomyCommandPayload.Action.CONFIRM_LISTING;default->null;};}if(action==EconomyCommandPayload.Action.ACCEPT_CONTRACT){this.minecraft.setScreen(new FreightConfirmScreen(this,this,entry.id(),false,0));return;}if(action!=null)PacketDistributor.sendToServer(EconomyCommandPayload.simple(this.menu.getPylonPos(),action,entry.id()));}
    private void openEconomyCreate(EconomyCreateScreen.Mode mode){if(this.minecraft!=null&&this.menu.getEconomyState()!=null)this.minecraft.setScreen(new EconomyCreateScreen(this,mode,this.menu.getPylonPos(),this.menu.getEconomyState()));}
    private List<EconomyEntry> economyVisibleEntries(EconomyStatePayload state){List<EconomyEntry> result=new ArrayList<>();for(var c:state.contracts())if(economyPage==0||c.mine()||c.issuerMine())result.add(new EconomyEntry(c.id(),false,c.itemId(),c,null));for(var l:state.listings())if(economyPage==0||l.mine())result.add(new EconomyEntry(l.id(),true,l.itemId(),null,l));String query=economySearch==null?"":economySearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);if(!query.isEmpty())result.removeIf(e->{ResourceLocation requested=e.contract()!=null?null:e.listingView().requestItemId();String text=e.item()+" "+economyIcon(e.item()).getHoverName().getString()+" "+(requested==null?"":economyIcon(requested).getHoverName().getString()+" ")+(e.contract()!=null?e.contract().title()+" "+e.contract().issuerName():e.listingView().creatorName()+" "+e.listingView().meeting());return!text.toLowerCase(java.util.Locale.ROOT).contains(query);});if(economySort==0)java.util.Collections.reverse(result);else if(economySort==1)result.sort(java.util.Comparator.comparingLong(e->e.contract()!=null?e.contract().reward():e.listingView().price()));else result.sort(java.util.Comparator.comparingLong(e->e.contract()!=null&&e.contract().deadlineAt()>0?e.contract().deadlineAt():Long.MAX_VALUE));return result;}
    private static ItemStack economyIcon(ResourceLocation id){var item=BuiltInRegistries.ITEM.get(id);return new ItemStack(item);}
    private static int economyStateColor(String name){return name.equals("COMPLETED")||name.equals("DELIVERED")?0xFF77B992:name.equals("FAILED")||name.equals("DISPUTED")?0xFFD07872:0xFFD09A3A;}
    private record EconomyEntry(UUID id,boolean listing,ResourceLocation item,EconomyStatePayload.ContractView contract,EconomyStatePayload.ListingView listingView){}


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
            members.add(new CityMember(entry.playerId(), role));
        }
        this.menu.replaceMembers(members);
        // The section is redrawn from the menu every frame, so a fresh list and a reset
        // scroll are all it takes; no extra invalidation flag is needed.
        this.memberScroll = 0;
    }
    private void requestEconomyState(){PacketDistributor.sendToServer(EconomyCommandPayload.simple(this.menu.getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));}

    private void requestRailwayState() {
        sendRailway(RailwayCommandPayload.Action.REQUEST_STATE, null, null);
    }

    private void requestRailwayMap() {
        sendRailway(RailwayCommandPayload.Action.REQUEST_MAP, null, null);
    }

    private void sendRailway(RailwayCommandPayload.Action action, UUID primary, UUID secondary) {
        PacketDistributor.sendToServer(RailwayCommandPayload.simple(
                this.menu.getPylonPos(), action, primary, secondary));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static Component railStatus(String status) {
        return Component.translatable("status.createcityexpansioncore.railway." + status);
    }

    private static int railStatusColor(String status) {
        return switch (status) {
            case "running", "in_transit", "at_station", "waiting", "cargo" -> 0xFF77B992;
            case "unserved", "no_schedule", "missing" -> 0xFF8A9498;
            default -> 0xFFD07872;
        };
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        var routeOptions=this.menu.drainRouteOptions();if(routeOptions!=null&&this.minecraft!=null)this.minecraft.setScreen(new RailwayRouteSelectionScreen(this,routeOptions));
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
        this.mapPanel.setTerritorySize(this.menu.getTerritorySize());
        if ((this.activeTab == 4 || this.activeTab == 6) && ++this.railwayRefreshTicks >= 20) {
            this.railwayRefreshTicks = 0;
            requestRailwayState();
        }
        if((this.activeTab==4||this.activeTab==7)&&++this.economyRefreshTicks>=40){this.economyRefreshTicks=0;requestEconomyState();}
        if (this.activeTab == 4 && this.minecraft != null
                && this.minecraft.level != null
                && this.minecraft.level.getGameTime() % 600 == 0) {
            requestRailwayMap();
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
     */

    private int getNextTerritorySize() {

        return switch (
                this.menu.getCityLevel()
                ) {

            case 1 -> 150;

            default -> 150;
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
