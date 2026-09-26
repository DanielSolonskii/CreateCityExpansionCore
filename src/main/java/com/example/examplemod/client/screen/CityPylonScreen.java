package com.example.examplemod.client.screen;

import com.example.examplemod.network.CityMapPeersPayload;
import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityMember;
import com.example.examplemod.client.CityBoundaryRenderer;
import com.example.examplemod.client.gui.CityButton;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.sound.SoundHelper;
import com.example.examplemod.sound.ModSounds;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.InvitePlayerPayload;
import com.example.examplemod.network.CityDevelopmentPayload;
import com.example.examplemod.network.DecommissionCityProjectPayload;
import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.EconomyStatePayload;
import com.example.examplemod.railway.RailwayLineSnapshot;
import com.example.examplemod.railway.RailwayStationSnapshot;
import com.example.examplemod.railway.RailwayTrainSnapshot;
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
     * 0 = ÐžÐ±Ð·Ð¾Ñ€
     * 1 = Ð¢ÐµÑ€Ñ€Ð¸Ñ‚Ð¾Ñ€Ð¸Ñ
     * 2 = Ð£Ñ‡Ð°ÑÑ‚Ð½Ð¸ÐºÐ¸
     * 3 = ÐŸÑ€Ð¾ÐµÐºÑ‚Ñ‹
     * 4 = ÐšÐ°Ñ€Ñ‚Ð°
     * 5 = Ð Ð°Ð·Ð²Ð¸Ñ‚Ð¸Ðµ
     * 6 = Ð–ÐµÐ»ÐµÐ·Ð½Ð°Ñ Ð´Ð¾Ñ€Ð¾Ð³Ð°
     * 7 = Ð Ñ‹Ð½Ð¾Ðº
     */
    private int activeTab = 0;
    private UUID decommissionConfirmation;

    /*
     * =========================================
     * ÐžÐšÐÐž ÐŸÐ Ð˜Ð“Ð›ÐÐ¨Ð•ÐÐ˜Ð¯
     * =========================================
     */

    private boolean showInvitePlayers = false;

    private String inviteStatus = "";

    /*
     * =========================================
     * ÐšÐÐžÐŸÐšÐ˜
     * =========================================
     */

    private CityButton inviteButton;

    private CityButton closeButton;

    private final CityButton[] tabButtons =
            new CityButton[8];

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
    /** ÐŸÑ€Ð¾ÐºÑ€ÑƒÑ‚ÐºÐ° Ñ‚ÐµÐºÑÑ‚Ð° ÑÐ¿Ñ€Ð°Ð²ÐºÐ¸ Ñ€Ñ‹Ð½ÐºÐ° (Ð´Ð»Ð¸Ð½Ð½Ñ‹Ð¹ help Ð½Ðµ Ñ€ÐµÐ¶ÐµÐ¼, Ð° Ð»Ð¸ÑÑ‚Ð°ÐµÐ¼). */
    private int marketplaceHelpScroll;
    private int helpContentHeight;

    /*
     * =========================================
     * ÐžÐ‘Ð›ÐÐ¡Ð¢Ð˜ ÐœÐÐšÐ•Ð¢Ð CITY PYLON
     * =========================================
     *
     * Ð’ÑÐµ Ð²ÐºÐ»Ð°Ð´ÐºÐ¸ ÑÑ‡Ð¸Ñ‚Ð°ÑŽÑ‚ ÐºÐ¾Ð¾Ñ€Ð´Ð¸Ð½Ð°Ñ‚Ñ‹ Ð¾Ñ‚Ð½Ð¾ÑÐ¸Ñ‚ÐµÐ»ÑŒÐ½Ð¾ ÑÑ‚Ð¸Ñ… Ð¾Ð±Ð»Ð°ÑÑ‚ÐµÐ¹,
     * Ð° Ð½Ðµ Ð¾Ñ‚ Ð°Ð±ÑÐ¾Ð»ÑŽÑ‚Ð½Ñ‹Ñ… Ñ‡Ð¸ÑÐµÐ», Ð¾ÑÑ‚Ð°Ð²ÑˆÐ¸Ñ…ÑÑ Ð¾Ñ‚ ÑÑ‚Ð°Ñ€Ð¾Ð³Ð¾ Ð¼Ð°Ð»ÐµÐ½ÑŒÐºÐ¾Ð³Ð¾ GUI.
     *
     * ROOT     = Ð²ÐµÑÑŒ Ð¿Ñ€ÑÐ¼Ð¾ÑƒÐ³Ð¾Ð»ÑŒÐ½Ð¸Ðº PANEL_WIDTH x PANEL_HEIGHT
     * HEADER   = Ð²ÐµÑ€Ñ…Ð½ÑÑ Ð¿Ð¾Ð»Ð¾ÑÐ° (Ð³Ð¾Ñ€Ð¾Ð´, ÑƒÑ€Ð¾Ð²ÐµÐ½ÑŒ, ÑÑ‚Ð°Ñ‚ÑƒÑ)
     * SIDEBAR  = ÐºÐ¾Ð»Ð¾Ð½ÐºÐ° Ð²ÐºÐ»Ð°Ð´Ð¾Ðº ÑÐ»ÐµÐ²Ð°
     * CONTENT  = Ñ€Ð°Ð±Ð¾Ñ‡Ð°Ñ Ð¾Ð±Ð»Ð°ÑÑ‚ÑŒ ÑÐ¿Ñ€Ð°Ð²Ð° Ð¾Ñ‚ sidebar
     * FOOTER   = Ð½Ð¸Ð¶Ð½ÑÑ Ð¿Ð¾Ð»Ð¾ÑÐ° Ð²Ð½ÑƒÑ‚Ñ€Ð¸ sidebar (ÐºÐ½Ð¾Ð¿ÐºÐ° Â«Ð—Ð°ÐºÑ€Ñ‹Ñ‚ÑŒÂ»)
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
     * ÐžÐŸÐžÐ Ð« ÐšÐžÐÐ¢Ð•ÐÐ¢Ð
     * =========================================
     *
     * ÐŸÐ°Ð½ÐµÐ»ÑŒ Ð²Ñ‹Ñ€Ð¾ÑÐ»Ð°, Ð¿Ð¾ÑÑ‚Ð¾Ð¼Ñƒ Ð²ÐºÐ»Ð°Ð´ÐºÐ¸ Ð±Ð¾Ð»ÑŒÑˆÐµ Ð½Ðµ Ð¼Ð¾Ð³ÑƒÑ‚ ÑÑ‡Ð¸Ñ‚Ð°Ñ‚ÑŒ
     * Ð¾Ñ‚ Â«Ð¼Ð°Ð³Ð¸Ñ‡ÐµÑÐºÐ¸Ñ…Â» 300 px: Ñ†ÐµÐ½Ñ‚Ñ€, ÐºÐ¾Ð»Ð¾Ð½ÐºÐ° Ð·Ð½Ð°Ñ‡ÐµÐ½Ð¸Ð¹ Ð¸ Ð¿Ð¾Ð»Ð¾Ð²Ð¸Ð½Ð°
     * ÐºÐ½Ð¾Ð¿ÐºÐ¸ ÑÑ‡Ð¸Ñ‚Ð°ÑŽÑ‚ÑÑ Ð¸Ð· CONTENT_WIDTH Ð¾Ð´Ð¸Ð½ Ñ€Ð°Ð· Ð·Ð´ÐµÑÑŒ.
     */
    private static final int CONTENT_CENTER = CONTENT_WIDTH / 2;
    private static final int CONTENT_VALUE_X = CONTENT_WIDTH - 96;
    private static final int HALF_W = (CONTENT_WIDTH - 10) / 2;
    private static final int HALF_W2 = HALF_W + 10;
    /** ÐŸÐ¾Ð´ÑÐºÐ°Ð·ÐºÐ¸ Ð½Ðµ ÑˆÐ¸Ñ€Ðµ ÑÐºÑ€Ð°Ð½Ð° Ð¸ Ð½Ðµ ÑˆÐ¸Ñ€Ðµ Ð¿Ð°Ð½ÐµÐ»Ð¸. */
    private static final int MAX_TOOLTIP_WIDTH = 320;

    /*
     * =========================================
     * Ð‘Ð Ð•ÐÐ” Ð’ HEADER
     * =========================================
     *
     * ÐÐµÐ±Ð¾Ð»ÑŒÑˆÐ¾Ð¹ Ð·Ð½Ð°Ðº Ð¿Ñ€Ð¾ÐµÐºÑ‚Ð° Ð² Ð¿Ñ€Ð°Ð²Ð¾Ð¼ Ð²ÐµÑ€Ñ…Ð½ÐµÐ¼ ÑƒÐ³Ð»Ñƒ ÑˆÐ°Ð¿ÐºÐ¸.
     * Ð­Ñ‚Ð¾ branding element, Ð° Ð½Ðµ Ð´ÐµÐºÐ¾Ñ€Ð°Ñ‚Ð¸Ð²Ð½Ð°Ñ ÐºÐ°Ñ€Ñ‚Ð¸Ð½ÐºÐ°:
     * Ð¾Ð½ Ð½Ðµ Ñ‚Ñ€Ð¾Ð³Ð°ÐµÑ‚ Ð½Ð°Ð·Ð²Ð°Ð½Ð¸Ðµ Ð³Ð¾Ñ€Ð¾Ð´Ð°, ÑƒÑ€Ð¾Ð²ÐµÐ½ÑŒ Ð¸ ÑÑ‚Ð°Ñ‚ÑƒÑÑ‹ â€”
     * Ð·Ð°Ð³Ð¾Ð»Ð¾Ð²Ð¾Ðº Ð¿Ñ€Ð¾ÑÑ‚Ð¾ Ð¾Ð±Ñ€ÐµÐ·Ð°ÐµÑ‚ÑÑ Ð¿Ð¾ ÐµÐ³Ð¾ Ð»ÐµÐ²Ð¾Ð¹ Ð³Ñ€Ð°Ð½Ð¸Ñ†Ðµ.
     *
     * Ð¢ÐµÐºÑÑ‚ÑƒÑ€Ð° Ð¿Ð¾Ð´Ð³Ð¾Ñ‚Ð¾Ð²Ð»ÐµÐ½Ð° Ð¸Ð· Ð¸ÑÑ…Ð¾Ð´Ð½Ð¾Ð¹ ÑÐ¼Ð±Ð»ÐµÐ¼Ñ‹
     * (textures/gui/city_emblem.png) Ð¸ Ð»ÐµÐ¶Ð¸Ñ‚ Ð¾Ñ‚Ð´ÐµÐ»ÑŒÐ½Ñ‹Ð¼
     * Ñ€ÐµÑÑƒÑ€ÑÐ¾Ð¼, Ñ‡Ñ‚Ð¾Ð±Ñ‹ ÐºÐ°Ð¶Ð´Ñ‹Ð¹ ÐºÐ°Ð´Ñ€ Ð½Ðµ Ð³Ñ€ÑƒÐ·Ð¸Ñ‚ÑŒ Ð±Ð¾Ð»ÑŒÑˆÑƒÑŽ PNG.
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

    /* Ð Ñ‹Ð½Ð¾Ðº: ÑÐ»ÐµÐ²Ð° Ð¿Ñ€Ð¾ÐºÑ€ÑƒÑ‡Ð¸Ð²Ð°ÐµÐ¼Ñ‹Ð¹ ÑÐ¿Ð¸ÑÐ¾Ðº, ÑÐ¿Ñ€Ð°Ð²Ð° Ð¿Ð°Ð½ÐµÐ»ÑŒ Ð´ÐµÑ‚Ð°Ð»ÐµÐ¹. */
    private static final int MARKET_LIST_W = 180;
    private static final int MARKET_DETAIL_W = CONTENT_WIDTH - MARKET_LIST_W - PANEL_GAP;
    private static final int MARKET_TOOLBAR_Y = 61;
    private static final int MARKET_TOOLBAR_H = 23;
    private static final int MARKET_BODY_Y = MARKET_TOOLBAR_Y + MARKET_TOOLBAR_H;
    private static final int MARKET_ROW_H = 34;
    private static final int MARKET_ROW_PITCH = 38;
    private static final int MARKET_VISIBLE_ROWS =
            Math.max(1, (CONTENT_BODY_BOTTOM - MARKET_BODY_Y) / MARKET_ROW_PITCH);

    /* Ð£Ñ‡Ð°ÑÑ‚Ð½Ð¸ÐºÐ¸: ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ°, ÐµÑ‘ ÑˆÐ°Ð³ Ð¸ ÑÐºÐ¾Ð»ÑŒÐºÐ¾ Ð¿Ð¾Ð¼ÐµÑ‰Ð°ÐµÑ‚ÑÑ Ð² ÑÐ¿Ð¸ÑÐºÐµ. */
    private static final int MEMBER_CARD_W = CONTENT_WIDTH - 30;
    private static final int MEMBER_CARD_H = 48;
    private static final int MEMBER_CARD_PITCH = MEMBER_CARD_H + 6;
    private static final int MEMBER_LIST_OFFSET = 73;
    private static final int MEMBER_ROWS = Math.max(2,
            (CONTENT_BODY_BOTTOM - MEMBER_LIST_OFFSET) / MEMBER_CARD_PITCH);

    /**
     * Шестерёнка «управления ролями» на карточке участника.
     *
     * <p>Размер 18×18 — чуть крупнее для лучшей кликабельности и видимости
     * при GUI Scale 2/3/4. Пиксель-арт не растягивается: рисуется целочисленно.
     */
    private static final int MEMBER_GEAR_SIZE = 18;

    /** Отступ шестерёнки от правого края карточки. */
    private static final int MEMBER_GEAR_RIGHT_MARGIN = 6;

    /** Отступ шестерёнки от верхнего края карточки. */
    private static final int MEMBER_GEAR_TOP_MARGIN = 6;

    /**
     * Ð•Ð´Ð¸Ð½Ñ‹Ð¹ Ð¸ÑÑ‚Ð¾Ñ‡Ð½Ð¸Ðº Ð¿Ñ€Ð°Ð²Ð´Ñ‹ Ð¿Ð¾ Ð³ÐµÐ¾Ð¼ÐµÑ‚Ñ€Ð¸Ð¸ ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸ ÑƒÑ‡Ð°ÑÑ‚Ð½Ð¸ÐºÐ°.
     *
     * <p>Ð Ð°Ð½ÑŒÑˆÐµ ÐºÐ¾Ð¾Ñ€Ð´Ð¸Ð½Ð°Ñ‚Ñ‹ ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸ Ð²Ñ‹Ñ‡Ð¸ÑÐ»ÑÐ»Ð¸ÑÑŒ Ð´Ð²Ð°Ð¶Ð´Ñ‹ Ð¸ Ð¿Ð¾-Ñ€Ð°Ð·Ð½Ð¾Ð¼Ñƒ:
     * Ð¾Ð´Ð¸Ð½ Ñ€Ð°Ð· Ð² renderMembers(), Ð´Ñ€ÑƒÐ³Ð¾Ð¹ â€” Ð² mouseClicked(). Ð˜Ð·-Ð·Ð°
     * Ñ€Ð°ÑÑ…Ð¾Ð¶Ð´ÐµÐ½Ð¸Ñ ÑˆÐ¸Ñ€Ð¸Ð½Ñ‹ ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸ (270 Ð¿Ñ€Ð¾Ñ‚Ð¸Ð² MEMBER_CARD_W) ÐºÐ½Ð¾Ð¿ÐºÐ°
     * ÑˆÐµÑÑ‚ÐµÑ€Ñ‘Ð½ÐºÐ¸ ÑƒÐµÐ·Ð¶Ð°Ð»Ð° Ð½Ð° 120 Ð¿Ð¸ÐºÑÐµÐ»ÐµÐ¹ Ð²Ð¿Ñ€Ð°Ð²Ð¾ Ð¾Ñ‚ Ð½Ð°Ñ€Ð¸ÑÐ¾Ð²Ð°Ð½Ð½Ð¾Ð¹.
     *
     * <p>Ð¢ÐµÐ¿ÐµÑ€ÑŒ Ð¸ Ð¾Ñ‚Ñ€Ð¸ÑÐ¾Ð²ÐºÐ°, Ð¸ hitbox Ð±ÐµÑ€ÑƒÑ‚ Ð¾Ð´Ð½Ð¸ Ð¸ Ñ‚Ðµ Ð¶Ðµ Ð·Ð½Ð°Ñ‡ÐµÐ½Ð¸Ñ
     * Ð¾Ñ‚ÑÑŽÐ´Ð°, Ð¿Ð¾ÑÑ‚Ð¾Ð¼Ñƒ Ñ€Ð°Ð·ÑŠÐµÑ…Ð°Ñ‚ÑŒÑÑ Ð¾Ð½Ð¸ ÑƒÐ¶Ðµ Ð½Ðµ Ð¼Ð¾Ð³ÑƒÑ‚.
     *
     * @param x Ð»ÐµÐ²Ñ‹Ð¹ ÐºÑ€Ð°Ð¹ ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸
     * @param y Ð²ÐµÑ€Ñ…Ð½Ð¸Ð¹ ÐºÑ€Ð°Ð¹ ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸
     */
    private record MemberCardGeometry(int x, int y, int width, int height) {

        int gearX() {
            return this.x + this.width - MEMBER_GEAR_RIGHT_MARGIN - MEMBER_GEAR_SIZE;
        }

        int gearY() {
            return this.y + MEMBER_GEAR_TOP_MARGIN;
        }
    }

    /**
     * Ð“ÐµÐ¾Ð¼ÐµÑ‚Ñ€Ð¸Ñ i-Ð¹ Ð²Ð¸Ð´Ð¸Ð¼Ð¾Ð¹ ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸ ÑÐ¿Ð¸ÑÐºÐ° ÑƒÑ‡Ð°ÑÑ‚Ð½Ð¸ÐºÐ¾Ð².
     * Ð˜Ð½Ð´ÐµÐºÑ â€” Ð¾Ñ‚Ð½Ð¾ÑÐ¸Ñ‚ÐµÐ»ÑŒÐ½Ð¾ Ð½Ð°Ñ‡Ð°Ð»Ð° Ð²Ð¸Ð´Ð¸Ð¼Ð¾Ð¹ Ð¾ÐºÐ½Ð°.
     */
    private MemberCardGeometry memberCard(int cardX, int cardY, int visibleIndex) {
        return new MemberCardGeometry(
                cardX,
                cardY + visibleIndex * MEMBER_CARD_PITCH,
                MEMBER_CARD_W,
                MEMBER_CARD_H
        );
    }

    /* Ð˜Ð½Ñ„Ñ€Ð°ÑÑ‚Ñ€ÑƒÐºÑ‚ÑƒÑ€Ð°: ÑÐºÐ¾Ð»ÑŒÐºÐ¾ ÑÑ‚Ñ€Ð¾Ðº Ð¿Ð¾Ð¼ÐµÑ‰Ð°ÐµÑ‚ÑÑ Ð¼ÐµÐ¶Ð´Ñƒ Ð·Ð°Ð³Ð¾Ð»Ð¾Ð²ÐºÐ¾Ð¼ Ð¸ Ð½Ð¸Ð·Ð¾Ð¼. */
    private static final int INFRA_ROW_H = 42;
    private static final int INFRA_ROWS = Math.max(1,
            (CONTENT_BODY_BOTTOM - 72) / INFRA_ROW_H);

    /*
     * Ð–ÐµÐ»ÐµÐ·Ð½Ð°Ñ Ð´Ð¾Ñ€Ð¾Ð³Ð°: ÑÐ¿Ð¸ÑÐ¾Ðº ÑÐ²ÐµÑ€Ñ…Ñƒ, Ð¿Ð°Ð½ÐµÐ»ÑŒ Ð´ÐµÐ¹ÑÑ‚Ð²Ð¸Ð¹ Ð¿Ñ€Ð¸Ð¶Ð°Ñ‚Ð° Ðº Ð½Ð¸Ð·Ñƒ
     * ÐºÐ¾Ð½Ñ‚ÐµÐ½Ñ‚Ð°, Ð¿Ð¾ÑÑ‚Ð¾Ð¼Ñƒ ÑÑ‚Ñ€Ð¾ÐºÐ¸ ÑÐ¿Ð¸ÑÐºÐ¾Ð² ÑÑ‡Ð¸Ñ‚Ð°ÑŽÑ‚ÑÑ Ð¾Ñ‚ ÑÐ²Ð¾Ð±Ð¾Ð´Ð½Ð¾Ð¹ Ð²Ñ‹ÑÐ¾Ñ‚Ñ‹.
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

    /* ÐÐ°Ñ‡Ð°Ð»Ð¾ ÑÐ¾Ð´ÐµÑ€Ð¶Ð¸Ð¼Ð¾Ð³Ð¾ â€” Ð¾Ð´Ð¸Ð½ Ð¸ÑÑ‚Ð¾Ñ‡Ð½Ð¸Ðº Ð¸ÑÑ‚Ð¸Ð½Ñ‹ Ð´Ð»Ñ Ð²ÑÐµÑ… Ð²ÐºÐ»Ð°Ð´Ð¾Ðº. */
    private int contentLeft() {
        return this.leftPos + CONTENT_OFFSET_X;
    }

    private int contentTop() {
        return this.topPos + CONTENT_OFFSET_Y;
    }

    /**
     * ÐžÐ±Ñ€ÐµÐ·ÐºÐ° Ð¿Ð¾ Ð³Ñ€Ð°Ð½Ð¸Ñ†Ð°Ð¼ Ð¾Ð±Ð»Ð°ÑÑ‚Ð¸.
     * GuiGraphics Ð¶Ð´Ñ‘Ñ‚ ÐºÐ¾Ð¾Ñ€Ð´Ð¸Ð½Ð°Ñ‚Ñ‹ ÑÐºÑ€Ð°Ð½Ð°, Ð¿Ð¾ÑÑ‚Ð¾Ð¼Ñƒ ÑƒÑ‡Ð¸Ñ‚Ñ‹Ð²Ð°ÐµÐ¼ interfaceScale.
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
     * ÐžÐ¢Ð›ÐžÐ–Ð•ÐÐÐ«Ð• ÐŸÐžÐ”Ð¡ÐšÐÐ—ÐšÐ˜
     * =========================================
     *
     * ÐŸÐ¾Ð´ÑÐºÐ°Ð·ÐºÐ° Ð·Ð°Ð¿Ð¾Ð¼Ð¸Ð½Ð°ÐµÑ‚ÑÑ Ð² Ð¼Ð¾Ð¼ÐµÐ½Ñ‚ Ð¾Ñ‚Ñ€Ð¸ÑÐ¾Ð²ÐºÐ¸ Ð²ÐºÐ»Ð°Ð´ÐºÐ¸ Ð¸ Ð²Ñ‹Ð²Ð¾Ð´Ð¸Ñ‚ÑÑ
     * ÑƒÐ¶Ðµ Ð¿Ð¾ÑÐ»Ðµ popPose(). Ð­Ñ‚Ð¾ Ñ€ÐµÑˆÐ°ÐµÑ‚ Ñ‚Ñ€Ð¸ Ð¿Ñ€Ð¾Ð±Ð»ÐµÐ¼Ñ‹ ÑÑ€Ð°Ð·Ñƒ:
     *
     *  1) ÐµÑ‘ Ð½Ðµ Ñ€ÐµÐ¶ÐµÑ‚ scissor ÑÐ¿Ð¸ÑÐºÐ°/ÐºÐ°Ñ€Ñ‚Ñ‹;
     *  2) GuiGraphics ÐºÐ»Ð°Ð¼Ð¿Ð¸Ñ‚ ÐµÑ‘ Ð¿Ð¾ Ñ€ÐµÐ°Ð»ÑŒÐ½Ð¾Ð¼Ñƒ ÑÐºÑ€Ð°Ð½Ñƒ, Ð° Ð½Ðµ Ð¿Ð¾
     *     ÐºÐ¾Ð¾Ñ€Ð´Ð¸Ð½Ð°Ñ‚Ð°Ð¼, Ð¿ÐµÑ€ÐµÑÑ‡Ð¸Ñ‚Ð°Ð½Ð½Ñ‹Ð¼ Ð¿Ð¾Ð´ interfaceScale;
     *  3) ÑˆÑ€Ð¸Ñ„Ñ‚ Ð¿Ð¾Ð´ÑÐºÐ°Ð·ÐºÐ¸ Ð½Ðµ Ð¼Ñ‹Ð»Ð¸Ñ‚ÑÑ Ð¾Ñ‚ Ð´Ñ€Ð¾Ð±Ð½Ð¾Ð³Ð¾ Ð¼Ð°ÑÑˆÑ‚Ð°Ð±Ð° Ð¸Ð½Ñ‚ÐµÑ€Ñ„ÐµÐ¹ÑÐ°.
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
     * Ð”Ð¾ÑÑ‚ÑƒÐ¿ Ðº Ð¿Ð°Ð½ÐµÐ»Ð¸ ÐºÐ°Ñ€Ñ‚Ñ‹ Ð´Ð»Ñ ÑÐµÑ‚ÐµÐ²Ð¾Ð³Ð¾ Ð¾Ð±Ñ€Ð°Ð±Ð¾Ñ‚Ñ‡Ð¸ÐºÐ° ÑÐ¾ÑÐµÐ´Ð½Ð¸Ñ… Ð³Ð¾Ñ€Ð¾Ð´Ð¾Ð².
     */
    public CityMapPanel getMapPanel() {
        return this.mapPanel;
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
         * ÐžÐ´Ð½Ð¾ÐºÑ€Ð°Ñ‚Ð½Ñ‹Ð¹ Ð·Ð²ÑƒÐº Ð¿Ñ€Ð¾Ð±ÑƒÐ¶Ð´ÐµÐ½Ð¸Ñ ÑÐºÑ€Ð°Ð½Ð° Ð¿Ñ€Ð¸ Ð¾Ñ‚ÐºÑ€Ñ‹Ñ‚Ð¸Ð¸.
         * Ð˜Ð³Ñ€Ð°ÐµÑ‚ÑÑ Ð² ÐºÐ¾Ð½ÑÑ‚Ñ€ÑƒÐºÑ‚Ð¾Ñ€Ðµ, Ð° Ð½Ðµ Ð² render(), Ð¿Ð¾ÑÑ‚Ð¾Ð¼Ñƒ
         * Ð½Ðµ Ð¿Ð¾Ð²Ñ‚Ð¾Ñ€ÑÐµÑ‚ÑÑ ÐºÐ°Ð¶Ð´Ñ‹Ð¹ ÐºÐ°Ð´Ñ€.
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
         * GuiGraphics.enableScissor Ñ€Ð°Ð±Ð¾Ñ‚Ð°ÐµÑ‚ Ð² ÑÐºÑ€Ð°Ð½Ð½Ñ‹Ñ… ÐºÐ¾Ð¾Ñ€Ð´Ð¸Ð½Ð°Ñ‚Ð°Ñ…,
         * Ð° ÑÐ°Ð¼ Ð¿Ð°Ð½ÐµÐ»ÑŒ Ñ€Ð¸ÑÑƒÐµÑ‚ÑÑ Ð²Ð½ÑƒÑ‚Ñ€Ð¸ pose().scale(interfaceScale).
         * ÐŸÐµÑ€ÐµÐ´Ð°Ñ‘Ð¼ Ð¼Ð°ÑÑˆÑ‚Ð°Ð± Ð²Ð½ÑƒÑ‚Ñ€ÑŒ, Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð¾Ð±Ñ€ÐµÐ·ÐºÐ° ÐºÐ°Ñ€Ñ‚Ñ‹ Ð±Ñ‹Ð»Ð° Ñ‚Ð¾Ñ‡Ð½Ð¾Ð¹.
         */
        this.mapPanel.setGuiScale(this.interfaceScale);

        this.showTerritory =
                CityBoundaryRenderer.isVisible();

        /*
         * =====================================
         * Ð•Ð¡Ð›Ð˜ Ð“ÐžÐ ÐžÐ”Ð ÐÐ•Ð¢
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
         * Ð—ÐÐšÐ Ð«Ð¢Ð¬
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
         * Ð’ÐšÐ›ÐÐ”ÐšÐ˜
         * =====================================
         */

        Component[] tabNames = {
                Component.translatable("gui.createcityexpansioncore.city.tab.overview"),
                Component.translatable("gui.createcityexpansioncore.city.tab.territory"),
                Component.translatable("gui.createcityexpansioncore.city.tab.members"),
                Component.translatable("gui.createcityexpansioncore.city.tab.projects"),
                Component.translatable("gui.createcityexpansioncore.city.tab.map"),
                Component.translatable("gui.createcityexpansioncore.city.tab.development"),
                Component.translatable("gui.createcityexpansioncore.city.tab.railway"),
                Component.translatable("gui.createcityexpansioncore.city.tab.marketplace")
        };

        /*
         * Ð’ÐºÐ»Ð°Ð´ÐºÐ¸ Ð·Ð°Ð¿Ð¾Ð»Ð½ÑÑŽÑ‚ ÑÐ°Ð¹Ð´Ð±Ð°Ñ€ Ñ†ÐµÐ»Ð¸ÐºÐ¾Ð¼: 8 ÐºÐ½Ð¾Ð¿Ð¾Ðº Ñ ÑˆÐ°Ð³Ð¾Ð¼ 30 px
         * Ð¾Ñ‚ ÑˆÐ°Ð¿ÐºÐ¸ Ð¸ Ð´Ð¾ Ð¾Ñ‚ÑÑ‚ÑƒÐ¿Ð° Ð½Ð°Ð´ Â«Ð—Ð°ÐºÑ€Ñ‹Ñ‚ÑŒÂ».
         */
        for (int i = 0; i < this.tabButtons.length; i++) {

            final int tabIndex = i;

            this.tabButtons[i] =
                    new CityButton(
                            this.leftPos + 10,
                            this.topPos + 54 + i * 30,
                            150,
                            24,
                            tabNames[i],
                            button -> {

                                /*
                                 * Ð¢Ð¸Ñ…Ð¸Ð¹ Ð·Ð²ÑƒÐº Ð¿ÐµÑ€ÐµÐºÐ»ÑŽÑ‡ÐµÐ½Ð¸Ñ Ð²ÐºÐ»Ð°Ð´ÐºÐ¸.
                                 * ÐŸÐ¾Ð¼Ð¾Ð³Ð°ÐµÑ‚ Ð¾Ñ€Ð¸ÐµÐ½Ñ‚Ð¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒÑÑ Ð² Ð¿Ð°Ð½ÐµÐ»Ð¸
                                 * Ñ Ð²Ð¾ÑÐµÐ¼ÑŒÑŽ Ð²ÐºÐ»Ð°Ð´ÐºÐ°Ð¼Ð¸. ÐžÐ±Ñ‹Ñ‡Ð½Ñ‹Ðµ
                                 * ÐºÐ½Ð¾Ð¿ÐºÐ¸ Ð¾ÑÑ‚Ð°Ð²Ð»ÑÑŽÑ‚ Ð²Ð°Ð½Ð¸Ð°Ð½ÑÐºÐ¸Ð¹
                                 * Ñ‰ÐµÐ»Ñ‡Ð¾Ðº, Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð½Ðµ Ð½Ð°ÑÐ»Ð°Ð¸Ð²Ð°Ñ‚ÑŒ Ð·Ð²ÑƒÐºÐ¸.
                                 */
                                SoundHelper.playUi(
                                        ModSounds.UI_TAB_SWITCH.get()
                                );

                                this.activeTab =
                                        tabIndex;

                                this.showInvitePlayers =
                                        false;

                                this.inviteStatus =
                                        "";

                                this.decommissionConfirmation = null;

                                if (tabIndex == 4 || tabIndex == 6) {
                                    requestRailwayState();
                                }
                                if (tabIndex == 4) {
                                    requestRailwayMap();
                                    requestEconomyState();
                                }
                                if (tabIndex == 7) {
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

            this.tabButtons[i].setActive(
                    this.activeTab == i
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
         * ÐŸÐ Ð˜Ð“Ð›ÐÐ¡Ð˜Ð¢Ð¬
         * =====================================
         */

        if (canInvitePlayers()) {

            this.inviteButton =
                    new CityButton(
                            this.leftPos + CONTENT_OFFSET_X + CONTENT_WIDTH - 110,
                            this.topPos + CONTENT_OFFSET_Y + 38,
                            110,
                            24,
                            Component.translatable(
                                    "gui.createcityexpansioncore.city.invite"
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
     * ÐžÐ‘ÐÐžÐ’Ð›Ð•ÐÐ˜Ð• Ð’ÐšÐ›ÐÐ”ÐžÐš
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
     * Ð’Ð˜Ð”Ð˜ÐœÐžÐ¡Ð¢Ð¬ Ð’ÐšÐ›ÐÐ”ÐžÐš
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
     * Ð’Ð˜Ð”Ð˜ÐœÐžÐ¡Ð¢Ð¬ ÐšÐÐžÐŸÐšÐ˜ ÐŸÐ Ð˜Ð“Ð›ÐÐ¡Ð˜Ð¢Ð¬
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
         * Ð—Ð²ÑƒÐº Ð·Ð°ÑÑ‹Ð¿Ð°Ð½Ð¸Ñ ÑÐºÑ€Ð°Ð½Ð°, Ð¾Ð´Ð¸Ð½ Ñ€Ð°Ð· Ð·Ð° Ð·Ð°ÐºÑ€Ñ‹Ñ‚Ð¸Ðµ.
         * onClose Ð²Ñ‹Ð·Ñ‹Ð²Ð°ÐµÑ‚ÑÑ Ð¾Ð´Ð¸Ð½ Ñ€Ð°Ð·, Ð° Ð½Ðµ ÐºÐ°Ð¶Ð´Ñ‹Ð¹ ÐºÐ°Ð´Ñ€.
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
         * Ð•ÑÐ»Ð¸ Ð¾Ñ‚ÐºÑ€Ñ‹Ñ‚Ð¾ Ð¿Ñ€Ð¸Ð³Ð»Ð°ÑˆÐµÐ½Ð¸Ðµ,
         * ÑÐºÑ€Ð¾Ð»Ð»Ð¸Ð¼ ÑÐ¿Ð¸ÑÐ¾Ðº Ð¸Ð³Ñ€Ð¾ÐºÐ¾Ð².
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

            int listLeft = this.contentLeft();

            int listTop =
                    this.topPos + CONTENT_OFFSET_Y + MEMBER_LIST_OFFSET;

            int listRight =
                    this.contentLeft() + MEMBER_CARD_W + 14;

            int listBottom =
                    this.topPos + CONTENT_OFFSET_Y + MEMBER_LIST_OFFSET
                            + MEMBER_ROWS * MEMBER_CARD_PITCH - 6;

            if (mouseX >= listLeft
                    && mouseX <= listRight
                    && mouseY >= listTop
                    && mouseY <= listBottom) {

                int memberCount =
                        this.menu.getMemberCount();

                int visibleCount = MEMBER_ROWS;

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
                case 2 -> this.menu.getRailwayTrains().size();
                default -> this.menu.getRailwayStations().size();
            };
            this.railwayScroll = Math.clamp(this.railwayScroll + (scrollY < 0 ? 1 : -1),
                    0, Math.max(0, count - visible));
            return count > visible;
        }

        /*
         * Ð¡Ð¿Ñ€Ð°Ð²ÐºÐ° Ñ€Ñ‹Ð½ÐºÐ° â€” Ð¼Ð¾Ð´Ð°Ð»ÑŒÐ½Ñ‹Ð¹ Ð±Ð»Ð¾Ðº: ÐºÐ¾Ð»ÐµÑÐ¾ Ð»Ð¸ÑÑ‚Ð°ÐµÑ‚ Ð¸Ð¼ÐµÐ½Ð½Ð¾ ÐµÑ‘.
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
     * ÐšÐ›Ð˜ÐšÐ˜
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
         * ÐžÐšÐÐž ÐŸÐ Ð˜Ð“Ð›ÐÐ¨Ð•ÐÐ˜Ð¯
         * =====================================
         */

        if (this.showInvitePlayers
                && button == 0) {

            /*
             * ÐšÐ½Ð¾Ð¿ÐºÐ° ÐÐÐ—ÐÐ”
             *
             * ÐžÐ½Ð° Ð¿Ð¾ÐºÐ° Ñ€Ð¸ÑÑƒÐµÑ‚ÑÑ Ð²Ñ€ÑƒÑ‡Ð½ÑƒÑŽ,
             * Ð¿Ð¾Ñ‚Ð¾Ð¼Ñƒ Ñ‡Ñ‚Ð¾ ÑÑƒÑ‰ÐµÑÑ‚Ð²ÑƒÐµÑ‚ Ñ‚Ð¾Ð»ÑŒÐºÐ¾
             * Ð²Ð½ÑƒÑ‚Ñ€Ð¸ ÑÐºÑ€Ð°Ð½Ð° Ð¿Ñ€Ð¸Ð³Ð»Ð°ÑˆÐµÐ½Ð¸Ñ.
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
             * Ð¡Ð¿Ð¸ÑÐ¾Ðº Ð¸Ð³Ñ€Ð¾ÐºÐ¾Ð²
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
         * Ð“Ð ÐÐÐ˜Ð¦Ð«
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
         * Ð•Ð¡Ð›Ð˜ Ð“ÐžÐ ÐžÐ”Ð ÐÐ•Ð¢
         * =====================================
         */

        if (!this.menu.hasCity()) {

            return super.mouseClicked(
                    mouseX,
                    mouseY,
                    button
            );
        }

        if (this.activeTab == 4
                && this.mapPanel.mouseClicked(mouseX, mouseY, button)) {
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
                // Ð¢Ð° Ð¶Ðµ Ð³ÐµÐ¾Ð¼ÐµÑ‚Ñ€Ð¸Ñ, Ñ‡Ñ‚Ð¾ Ð¸ Ð² renderProjects(): ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ° 34 Ð¿Ð¸ÐºÑÐµÐ»Ñ.
                int cardY = contentTop + 44 + index * 36;
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
         * Ð£ÐŸÐ ÐÐ’Ð›Ð•ÐÐ˜Ð• Ð£Ð§ÐÐ¡Ð¢ÐÐ˜ÐšÐžÐœ
         * =========================================
         */

        if (this.menu.hasCity()
                && this.activeTab == 2
                && button == 0) {

            int cardX =
                    this.contentLeft();

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
                 * Ð¢Ð° Ð¶Ðµ Ð³ÐµÐ¾Ð¼ÐµÑ‚Ñ€Ð¸Ñ, Ñ‡Ñ‚Ð¾ Ð¸ Ð¿Ñ€Ð¸ Ð¾Ñ‚Ñ€Ð¸ÑÐ¾Ð²ÐºÐµ.
                 * Ð Ð°Ð½ÑŒÑˆÐµ Ð·Ð´ÐµÑÑŒ ÑÑ‚Ð¾ÑÐ»Ð¸ ÑÐ¾Ð±ÑÑ‚Ð²ÐµÐ½Ð½Ñ‹Ðµ cardWidth/spacing
                 * Ð¸ ÑÐ¾Ð±ÑÑ‚Ð²ÐµÐ½Ð½Ñ‹Ð¹ Ð¿Ñ€ÑÐ¼Ð¾ÑƒÐ³Ð¾Ð»ÑŒÐ½Ð¸Ðº Ð´Ð»Ñ ÑˆÐµÑÑ‚ÐµÑ€Ñ‘Ð½ÐºÐ¸,
                 * Ð¸Ð·-Ð·Ð° Ñ‡ÐµÐ³Ð¾ ÐºÐ½Ð¾Ð¿ÐºÐ° ÑƒÐµÐ·Ð¶Ð°Ð»Ð° Ð¾Ñ‚ Ð½Ð°Ñ€Ð¸ÑÐ¾Ð²Ð°Ð½Ð½Ð¾Ð¹.
                 */
                MemberCardGeometry card =
                        memberCard(cardX, cardY, visibleIndex);

                int gearX = card.gearX();
                int gearY = card.gearY();

                /*
                 * Hitbox Ñ€Ð¾Ð²Ð½Ð¾ Ð¿Ð¾ Ð½Ð°Ñ€Ð¸ÑÐ¾Ð²Ð°Ð½Ð½Ð¾Ð¹ Ð¸ÐºÐ¾Ð½ÐºÐµ:
                 * Ñ€Ð°Ð½ÑŒÑˆÐµ Ð¾Ð½ Ð±Ñ‹Ð» ÑˆÐ¸Ñ€Ðµ Ð¸ Ð½Ð¸Ð¶Ðµ, Ñ‡ÐµÐ¼ ÑÐ°Ð¼Ð° ÑˆÐµÑÑ‚ÐµÑ€Ñ‘Ð½ÐºÐ°.
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
     * ÐŸÐžÐ›Ð£Ð§Ð•ÐÐ˜Ð• Ð˜Ð“Ð ÐžÐšÐžÐ’ Ð”Ð›Ð¯ ÐŸÐ Ð˜Ð“Ð›ÐÐ¨Ð•ÐÐ˜Ð¯
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
             * ÐÐµ Ð¿Ð¾ÐºÐ°Ð·Ñ‹Ð²Ð°ÐµÐ¼ ÑÐ°Ð¼Ð¾Ð³Ð¾ ÑÐµÐ±Ñ.
             */

            if (playerId.equals(currentPlayer)) {
                continue;
            }

            /*
             * ÐÐµ Ð¿Ð¾ÐºÐ°Ð·Ñ‹Ð²Ð°ÐµÐ¼ Ð¸Ð³Ñ€Ð¾ÐºÐ¾Ð²,
             * ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ðµ ÑƒÐ¶Ðµ ÑÐ¾ÑÑ‚Ð¾ÑÑ‚ Ð² ÑÑ‚Ð¾Ð¼ Ð³Ð¾Ñ€Ð¾Ð´Ðµ.
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
     * ÐŸÐ Ð˜Ð“Ð›ÐÐ¡Ð˜Ð¢Ð¬ Ð˜Ð“Ð ÐžÐšÐ
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
     * ÐœÐžÐ–Ð•Ð¢ ÐŸÐ Ð˜Ð“Ð›ÐÐ¨ÐÐ¢Ð¬
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
     * Ð˜ÐœÐ¯ Ð£Ð§ÐÐ¡Ð¢ÐÐ˜ÐšÐ
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
     * Ð¤ÐžÐ
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
         * ÐžÑÐ½Ð¾Ð²Ð½Ð°Ñ Ð¿Ð°Ð½ÐµÐ»ÑŒ
         */

        guiGraphics.fill(
                left,
                top,
                left + this.imageWidth,
                top + this.imageHeight,
                0xFF202020
        );

        /*
         * Ð’ÐµÑ€Ñ…Ð½ÑÑ Ð¿Ð°Ð½ÐµÐ»ÑŒ
         */

        guiGraphics.fill(
                left,
                top,
                left + this.imageWidth,
                top + 50,
                0xFF353535
        );

        /*
         * Ð—Ð¾Ð»Ð¾Ñ‚Ð°Ñ Ð»Ð¸Ð½Ð¸Ñ

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
         * Ð›ÐµÐ²Ð°Ñ Ð¿Ð°Ð½ÐµÐ»ÑŒ
         */

        guiGraphics.fill(
                left,
                top + 51,
                left + 160,
                top + this.imageHeight,
                0xFF181818
        );

        /*
         * Ð Ð°Ð·Ð´ÐµÐ»Ð¸Ñ‚ÐµÐ»ÑŒ
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
        if(this.menu.getEconomyState()!=null)this.mapPanel.setEconomyInfrastructure(this.menu.getEconomyState().infrastructure());
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
         * Ð—ÐÐ“ÐžÐ›ÐžÐ’ÐžÐš
         * =====================================
         */

        /*
         * =====================================
         * Ð­ÐœÐ‘Ð›Ð•ÐœÐ (BRANDING)
         * =====================================
         *
         * Ð—Ð½Ð°Ðº Ñ€Ð¸ÑÑƒÐµÑ‚ÑÑ Ð¿ÐµÑ€Ð²Ñ‹Ð¼ Ð¸ Ð·Ð°Ð½Ð¸Ð¼Ð°ÐµÑ‚ Ñ„Ð¸ÐºÑÐ¸Ñ€Ð¾Ð²Ð°Ð½Ð½Ñ‹Ð¹ ÑƒÐ³Ð¾Ð» HEADER,
         * Ð¿Ð¾ÑÑ‚Ð¾Ð¼Ñƒ Ð¾Ð½ Ð½Ðµ Ð¼Ð¾Ð¶ÐµÑ‚ Ð½Ð°ÐµÑ…Ð°Ñ‚ÑŒ Ð½Ð° Ð½Ð°Ð·Ð²Ð°Ð½Ð¸Ðµ Ð³Ð¾Ñ€Ð¾Ð´Ð° Ð¸Ð»Ð¸ ÑƒÑ€Ð¾Ð²ÐµÐ½ÑŒ:
         * Ð·Ð°Ð³Ð¾Ð»Ð¾Ð²Ð¾Ðº Ð½Ð¸Ð¶Ðµ Ð¾Ð±Ñ€ÐµÐ·Ð°ÐµÑ‚ÑÑ Ð¿Ð¾ Ð»ÐµÐ²Ð¾Ð¹ Ð³Ñ€Ð°Ð½Ð¸Ñ†Ðµ Ð·Ð½Ð°ÐºÐ°.
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
         * Ð•Ð¡Ð›Ð˜ Ð“ÐžÐ ÐžÐ”Ð ÐÐ•Ð¢
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
         * ÐŸÐžÐ”Ð—ÐÐ“ÐžÐ›ÐžÐ’ÐžÐš
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
         * Ð­ÐšÐ ÐÐ ÐŸÐ Ð˜Ð“Ð›ÐÐ¨Ð•ÐÐ˜Ð¯
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
         * ÐšÐžÐÐ¢Ð•ÐÐ¢
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

            case 4 ->
                    this.mapPanel.render(
                            guiGraphics,
                            contentLeft,
                            contentTop + 25,
                            CONTENT_WIDTH,
                            CONTENT_BODY_BOTTOM - 25,
                            mouseX,
                            mouseY
                    );

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
        guiGraphics.pose().popPose();
        flushDeferredTooltip(guiGraphics, screenMouseX, screenMouseY);
    }

    /*
     * =========================================
     * Ð­ÐšÐ ÐÐ Ð’Ð«Ð‘ÐžÐ Ð Ð˜Ð“Ð ÐžÐšÐ
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
         * ÐšÐÐžÐŸÐšÐ ÐÐÐ—ÐÐ”
         * =====================================
         *
         * ÐŸÐ¾ÐºÐ° Ð¾ÑÑ‚Ð°Ñ‘Ñ‚ÑÑ Ñ€ÑƒÑ‡Ð½Ð¾Ð¹,
         * Ð¿Ð¾Ñ‚Ð¾Ð¼Ñƒ Ñ‡Ñ‚Ð¾ Ð¾Ð½Ð° ÑÑƒÑ‰ÐµÑÑ‚Ð²ÑƒÐµÑ‚ Ñ‚Ð¾Ð»ÑŒÐºÐ¾
         * Ð²Ð½ÑƒÑ‚Ñ€Ð¸ ÑÐºÑ€Ð°Ð½Ð° Ð¿Ñ€Ð¸Ð³Ð»Ð°ÑˆÐµÐ½Ð¸Ñ.
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
         * Ð¡Ð¢ÐÐ¢Ð£Ð¡
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
         * Ð˜Ð“Ð ÐžÐšÐ˜
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
             * Ð¤Ð¾Ð½ ÑÑ‚Ñ€Ð¾ÐºÐ¸
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
             * Ð¡ÐºÐ¸Ð½
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
             * Ð˜Ð¼Ñ
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
     * ÐžÐ‘Ð—ÐžÐ 
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
                Component.translatable("gui.createcityexpansioncore.city.level_label"),
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
                Component.translatable("gui.createcityexpansioncore.city.territory"),
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
                Component.translatable("gui.createcityexpansioncore.city.residents"),
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
                Component.translatable("gui.createcityexpansioncore.city.pylon"),
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
     * Ð¢Ð•Ð Ð Ð˜Ð¢ÐžÐ Ð˜Ð¯
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
     * Ð£Ð§ÐÐ¡Ð¢ÐÐ˜ÐšÐ˜
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
         * Ð—Ð°Ð³Ð¾Ð»Ð¾Ð²Ð¾Ðº
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
         * Ð¡ÐŸÐ˜Ð¡ÐžÐš
         * =====================================
         */

        /*
         * Ð•Ð´Ð¸Ð½Ð°Ñ Ð³ÐµÐ¾Ð¼ÐµÑ‚Ñ€Ð¸Ñ: Ð¸ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÑ‚ÑÑ Ð¸ Ð·Ð´ÐµÑÑŒ, Ð¸ Ð² mouseClicked().
         * hardcoded 270 Ñ€Ð°Ð½ÑŒÑˆÐµ Ð½Ðµ ÑÐ¾Ð²Ð¿Ð°Ð´Ð°Ð» Ñ MEMBER_CARD_W.
         */
        int cardX =
                left;

        int cardY =
                row + 28;

        int cardWidth =
                MEMBER_CARD_W;

        int cardHeight =
                MEMBER_CARD_H;

        int spacing =
                MEMBER_CARD_PITCH - MEMBER_CARD_H;

        int visibleCount =
                MEMBER_ROWS;

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

        beginClip(guiGraphics, cardX, cardY, cardX + cardWidth + 14,
                cardY + visibleCount * (cardHeight + spacing) - spacing);

        for (int i = start; i < end; i++) {

            CityMember member =
                    this.menu
                            .getMembers()
                            .get(i);

            int visibleIndex =
                    i - start;

            MemberCardGeometry card =
                    memberCard(cardX, cardY, visibleIndex);

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
     * ÐšÐÐ Ð¢ÐžÐ§ÐšÐ
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
     * Ð Ð¸ÑÑƒÐµÑ‚ ÑˆÐµÑÑ‚ÐµÑ€Ñ‘Ð½ÐºÑƒ Ñ†ÐµÐ»Ð¾Ñ‡Ð¸ÑÐ»ÐµÐ½Ð½Ñ‹Ð¼Ð¸ Ð¿Ñ€ÑÐ¼Ð¾ÑƒÐ³Ð¾Ð»ÑŒÐ½Ð¸ÐºÐ°Ð¼Ð¸.
     *
     * <p>Ð Ð°Ð½ÑŒÑˆÐµ Ð·Ð´ÐµÑÑŒ Ð±Ñ‹Ð» Unicode-Ð³Ð»Ð¸Ñ„ Â«âš™Â» Ñ€Ð°Ð·Ð¼ÐµÑ€Ð¾Ð¼ ÑÐ¾ ÑˆÑ€Ð¸Ñ„Ñ‚
     * (Ð¾ÐºÐ¾Ð»Ð¾ 8×8 Ð¸ Ð·Ð°Ð¼ÐµÑ‚Ð½Ð¾ Ð¼ÐµÐ½ÑŒÑˆÐµ Ð¾ÑÑ‚Ð°Ð»ÑŒÐ½Ñ‹Ñ… ÑÐ»ÐµÐ¼ÐµÐ½Ñ‚Ð¾Ð² ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸).
     * Ð¢ÐµÐ¿ÐµÑ€ÑŒ ÑÑ‚Ð¾ Ð½Ð°ÑÑ‚Ð¾ÑÑ‰Ð°Ñ Ð¸ÐºÐ¾Ð½ÐºÐ° 16×16, Ð½Ð°Ñ€Ð¸ÑÐ¾Ð²Ð°Ð½Ð½Ð°Ñ Ð¿Ð¸ÐºÑÐµÐ»ÑŒ-Ð°Ñ€Ñ‚Ð¾Ð¼
     * Ð² ÑÑ‚Ð¸Ð»Ðµ Ð¸Ð½Ñ‚ÐµÑ€Ñ„ÐµÐ¹ÑÐ°: Ð±ÐµÐ· ÑÐ³Ð»Ð°Ð¶Ð¸Ð²Ð°Ð½Ð¸Ñ, Ð±ÐµÐ· Ñ€Ð°ÑÑ‚ÑÐ³Ð¸Ð²Ð°Ð½Ð¸Ñ Ñ‚ÐµÐºÑÑ‚ÑƒÑ€Ñ‹.
     *
     * @param size ÑÑ‚Ð¾Ñ€Ð¾Ð½Ð° Ð¸ÐºÐ¾Ð½ÐºÐ¸ Ð² Ð»Ð¾Ð³Ð¸Ñ‡ÐµÑÐºÐ¸Ñ… Ð¿Ð¸ÐºÑÐµÐ»ÑÑ… GUI
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
         * Ð£Ð³Ð»Ñ‹ Ð¸ Ñ†ÐµÐ½Ñ‚Ñ€ Ð¾ÑÑ‚Ð°Ð²Ð»ÐµÐ½Ñ‹ Ð¿ÑƒÑÑ‚Ñ‹Ð¼Ð¸, Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð¸ÐºÐ¾Ð½ÐºÐ° Ñ‡Ð¸Ñ‚Ð°Ð»Ð°ÑÑŒ
         * ÐºÐ°Ðº ÑˆÐµÑÑ‚ÐµÑ€Ð½Ñ, Ð° Ð½Ðµ ÐºÐ°Ðº ÑÐ¿Ð»Ð¾ÑˆÐ½Ð¾Ð¹ ÐºÐ²Ð°Ð´Ñ€Ð°Ñ‚.
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
                 * ÐÐ¸Ð¶Ð½Ð¸Ð¹ Ñ€ÑÐ´ Ð¿Ð¸ÐºÑÐµÐ»Ñ Ñ€Ð¸ÑÑƒÐµÑ‚ÑÑ Ñ‚ÐµÐ¼Ð½ÐµÐµ â€” ÑÑ‚Ð¾ Ð´Ð°Ñ‘Ñ‚
                 * Ð¾Ð±ÑŠÑ‘Ð¼ Ð±ÐµÐ· ÐµÐ´Ð¸Ð½Ð¾Ð³Ð¾ ÑÐ³Ð»Ð°Ð¶ÐµÐ½Ð½Ð¾Ð³Ð¾ Ð¿Ð¸ÐºÑÐµÐ»Ñ.
                 */
                guiGraphics.fill(px, py, px + 1, py + 1,
                        (row >= GEAR.length / 2) ? dark : color);
            }
        }
    }

    /**
     * ÐÐ°Ñ…Ð¾Ð´Ð¸Ñ‚ÑÑ Ð»Ð¸ ÐºÑƒÑ€ÑÐ¾Ñ€ Ð½Ð°Ð´ ÑˆÐµÑÑ‚ÐµÑ€Ñ‘Ð½ÐºÐ¾Ð¹ ÐºÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ¸.
     * Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÑ‚ Ñ‚Ñƒ Ð¶Ðµ Ð³ÐµÐ¾Ð¼ÐµÑ‚Ñ€Ð¸ÑŽ, Ñ‡Ñ‚Ð¾ Ð¸ Ð¾Ñ‚Ñ€Ð¸ÑÐ¾Ð²ÐºÐ°.
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
     * ÐŸÐ ÐžÐ•ÐšÐ¢Ð«
     * =========================================
     */

    private void renderProjects(
            GuiGraphics guiGraphics,
            int left,
            int top
    ) {

        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.city.level", romanLevel(this.menu.getCityLevel())),
                left, top + 29, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.city.project_capacity",
                        this.menu.getProjects().size(), this.menu.getProjectCapacity()),
                left + 125, top + 29, 0xFFD09A3A);

        List<CityProjectSnapshot> projects = this.menu.getProjects();
        for (int index = 0; index < Math.min(2, projects.size()); index++) {
            CityProjectSnapshot project = projects.get(index);
            int cardY = top + 44 + index * 36;
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
                    left, top + 57, 0xFF888888);
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
                                + (requirement.complete() ? " âœ“" : "")),
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

    private void renderRailTrains(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<RailwayTrainSnapshot> trains = this.menu.getRailwayTrains();
        RailwayTrainSnapshot detail = trains.stream()
                .filter(train -> train.fleetTrainId().equals(this.railwayTrainDetail))
                .findFirst().orElse(null);
        if (detail != null) {
            renderRailTrainDetail(graphics, left, top, detail, mouseX, mouseY);
            return;
        }
        this.railwayTrainDetail = null;
        int end = Math.min(trains.size(), this.railwayScroll + railRows(31));
        beginClip(graphics, left, top + 48, left + CONTENT_WIDTH, top + RAIL_LIST_BOTTOM);
        for (int i = this.railwayScroll; i < end; i++) {
            RailwayTrainSnapshot train = trains.get(i);
            int y = top + 51 + (i - this.railwayScroll) * 31;
            graphics.fill(left, y, left + CONTENT_WIDTH, y + 27, 0xFF292F31);
            graphics.fill(left, y, left + 3, y + 27, railStatusColor(train.statusKey()));
            graphics.drawString(this.font, train.name(), left + 9, y + 4,
                    train.cityOwned() ? 0xFFE6E9EA : 0xFF9AA5A9, false);
            graphics.drawString(this.font,
                    train.cityOwned() ? railStatus(train.statusKey())
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
        graphics.fill(left, top + 51, left + 4, top + 196, railStatusColor(train.statusKey()));
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
        graphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.detail.status", railStatus(train.statusKey())),
                left + 12, top + 103, railStatusColor(train.statusKey()), false);
        if (!train.nextStation().isEmpty()) {
            graphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.railway.next_station", train.nextStation()),
                    left + 12, top + 117, 0xFFB7C0C3, false);
        }
        graphics.drawString(this.font, Component.translatable(
                        "gui.createcityexpansioncore.railway.speed", String.format(java.util.Locale.ROOT, "%.1f", train.speed())),
                left + 12, top + 131, 0xFFB7C0C3, false);
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
                if (inside(mouseX, mouseY, left + HALF_W2, top + RAIL_ACTION_Y, HALF_W, 22)) {
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
            int end = Math.min(trains.size(), this.railwayScroll + railRows(31));
            for (int i = this.railwayScroll; i < end; i++) {
                int y = top + 51 + (i - this.railwayScroll) * 31;
                if (inside(mouseX, mouseY, left, y, 165, 27)) {
                    this.railwayTrainDetail = trains.get(i).fleetTrainId();
                    return true;
                }
                if (!inside(mouseX, mouseY, left + 165, y, CONTENT_WIDTH - 165, 27)) continue;
                RailwayTrainSnapshot train = trains.get(i);
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
     * Ð Ð«ÐÐžÐš: Ð¡ÐŸÐ˜Ð¡ÐžÐš Ð¡Ð›Ð•Ð’Ð + Ð”Ð•Ð¢ÐÐ›Ð˜ Ð¡ÐŸÐ ÐÐ’Ð
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

    /** ÐšÐ°Ñ€Ñ‚Ð¾Ñ‡ÐºÐ° Ð¾Ð±ÑŠÑÐ²Ð»ÐµÐ½Ð¸Ñ/ÐºÐ¾Ð½Ñ‚Ñ€Ð°ÐºÑ‚Ð° Ð² Ð»ÐµÐ²Ð¾Ð¹ ÐºÐ¾Ð»Ð¾Ð½ÐºÐµ. */
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
        String symbol=l.isBarter()?"⇄ ":"→ ";
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
     * ÐŸÐ°Ð½ÐµÐ»ÑŒ Ð´ÐµÑ‚Ð°Ð»ÐµÐ¹ ÑÐ¿Ñ€Ð°Ð²Ð°.
     * ÐšÐ¾Ð½Ñ‚ÐµÐ½Ñ‚ Ð¾Ð±Ñ€ÐµÐ·Ð°ÐµÑ‚ÑÑ Ð¿Ð¾ ÑÐ²Ð¾ÐµÐ¹ Ð¾Ð±Ð»Ð°ÑÑ‚Ð¸, Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ñ‚ÐµÐºÑÑ‚ Ð½Ðµ Ð»ÐµÐ· Ð½Ð° ÐºÐ½Ð¾Ð¿ÐºÐ¸.
     */
    private void renderEconomyDetailPanel(GuiGraphics g,int x,int y,EconomyEntry entry,EconomyStatePayload state,int mouseX,int mouseY){
        boolean isContract=entry.contract()!=null;
        int width=MARKET_DETAIL_W;
        int height=CONTENT_BODY_BOTTOM-MARKET_TOOLBAR_Y;
        /*
         * ÐÐ¸Ð· Ð¾Ð±Ñ€ÐµÐ·ÐºÐ¸ Ð¾Ñ‚ÑÑ‚ÑƒÐ¿Ð°ÐµÑ‚ Ð¿Ð¾Ð´ ÑÑ‚Ñ€Ð¾ÐºÑƒ Ð±Ð°Ñ€Ñ‚ÐµÑ€Ð° (Ð¸Ð½Ð°Ñ‡Ðµ Ð¾Ð½Ð° Ñ€ÐµÐ·Ð°Ð»Ð°ÑÑŒ),
         * Ð° ÐºÐ½Ð¾Ð¿ÐºÐ¸ Ð¿Ñ€Ð¸Ð¶Ð°Ñ‚Ñ‹ Ðº Ð½Ð¸Ð·Ñƒ Ð¿Ð°Ð½ÐµÐ»Ð¸ â€” Ð¼Ñ‘Ñ€Ñ‚Ð²Ð°Ñ Ð¿Ð¾Ð»Ð¾ÑÐ° Ð¸ÑÑ‡ÐµÐ·Ð°ÐµÑ‚.
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
        cursor=detailExchangeRow(g,x+4,cursor,inner,l.requestItemId(),l.requestAmount(),l.isBarter()?"⇄ ":"→ ");

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

    /** Ð¡Ñ‚Ñ€Ð¾ÐºÐ° Â«[Ð¸ÐºÐ¾Ð½ÐºÐ°] 64 ÐœÐµÐ´Ð½Ñ‹Ð¹ Ð»Ð¸ÑÑ‚Â» Ð¸Ð»Ð¸ Â«Ð’Ð°Ð»ÑŽÑ‚Ð° 800Â» Ð² Ð¿Ð°Ð½ÐµÐ»Ð¸ Ð´ÐµÑ‚Ð°Ð»ÐµÐ¹. */
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
        g.drawString(this.font,ellipsize(Component.translatable("gui.createcityexpansioncore.economy.contractor",c.contractorName().isBlank()?"â€”":c.contractorName()),inner),
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
     * ÐŸÐ¾Ð´ÑÐºÐ°Ð·ÐºÐ¸ ÑˆÐ°Ð¿ÐºÐ¸ ÑÐºÐ¾Ð½Ð¾Ð¼Ð¸ÐºÐ¸: Â«?Â» Ð¸ ÑÑ‚Ñ€Ð¾ÐºÐ° Ñ ÑÑÐºÑ€Ð¾Ñƒ.
     * Ð—Ð°Ð¿Ð¾Ð¼Ð¸Ð½Ð°ÐµÐ¼, Ð° Ð½Ðµ Ñ€Ð¸ÑÑƒÐµÐ¼ â€” ÑÐ¼. flushDeferredTooltip().
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
         * Ð¡Ð¿Ñ€Ð°Ð²ÐºÐ° Ð½Ðµ Ð¾Ð±Ñ€ÐµÐ·Ð°ÐµÑ‚ÑÑ: ÐµÑÐ»Ð¸ Ñ‚ÐµÐºÑÑ‚ Ð½Ðµ Ð²Ð»ÐµÐ· â€” Ð¾Ð½ Ð¿Ñ€Ð¾ÐºÑ€ÑƒÑ‡Ð¸Ð²Ð°ÐµÑ‚ÑÑ
         * ÐºÐ¾Ð»ÐµÑÐ¾Ð¼, Ð° ÑÐ½Ð¸Ð·Ñƒ Ð²Ð¸Ð´Ð½Ð° ÑÑ‚Ñ€ÐµÐ»ÐºÐ° Â«ÐµÑ‰Ñ‘ ÐµÑÑ‚ÑŒÂ».
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
            g.drawString(this.font,marketplaceHelpScroll>0?"â–²":"â–¼",left+CONTENT_WIDTH-18,contentBottom-11,0xFFFFD59A,false);
        if(marketplaceHintVisible)
            renderEconomyButton(g,left+CONTENT_CENTER-58,buttonY,116,Component.translatable("gui.createcityexpansioncore.economy.understood"),mouseX,mouseY);
        else
            renderEconomyButton(g,left+CONTENT_CENTER-45,buttonY,90,Component.translatable("gui.createcityexpansioncore.common.close"),mouseX,mouseY);
    }

    /** Ð—Ð°Ð³Ð¾Ð»Ð¾Ð²Ð¾Ðº ÑÐµÐºÑ†Ð¸Ð¸ + Ð¿ÐµÑ€ÐµÐ½Ð¾ÑÐ¸Ð¼Ñ‹Ð¹ Ñ‚ÐµÐºÑÑ‚; Ð²Ð¾Ð·Ð²Ñ€Ð°Ñ‰Ð°ÐµÑ‚ Y ÑÐ»ÐµÐ´ÑƒÑŽÑ‰ÐµÐ¹ ÑÑ‚Ñ€Ð¾ÐºÐ¸. */
    private int drawHelpSection(GuiGraphics g,Component title,Component body,int x,int y,int width){
        g.drawString(this.font,title,x,y,0xFFFFFFFF,false);
        return drawWrapped(g,body,x,y+this.font.lineHeight,width,0xFFB7C0C3);
    }

    /** ÐŸÐµÑ€ÐµÐ½Ð¾Ñ Ð±ÐµÐ· Â«...Â»: Ð¿Ð¾Ð»Ð½Ñ‹Ð¹ Ñ‚ÐµÐºÑÑ‚ Ð¸ ÑÑ€Ð°Ð·Ñƒ Ð²Ñ‹ÑÐ¾Ñ‚Ð° Ð·Ð°Ð½ÑÑ‚Ñ‹Ñ… ÑÑ‚Ñ€Ð¾Ðº. */
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
    private void performEconomyAction(EconomyEntry entry){EconomyCommandPayload.Action action;if(entry.contract()!=null){var c=entry.contract();boolean communityJoin=c.mode()==com.example.examplemod.economy.EconomyTypes.ContractMode.COMMUNITY&&!c.mine()&&!c.issuerMine()&&(c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.ACCEPTED||c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.IN_DELIVERY);if((c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.ACCEPTED||c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.IN_DELIVERY)&&c.mine()){if(this.minecraft!=null&&this.menu.getEconomyState()!=null)this.minecraft.setScreen(new EconomyShipmentScreen(this,this.menu.getPylonPos(),c,this.menu.getEconomyState(),this.menu.getRailwayTrains(),this.menu.getRailwayStations()));return;}action=c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.OPEN?(c.issuerMine()?EconomyCommandPayload.Action.CANCEL_CONTRACT:EconomyCommandPayload.Action.ACCEPT_CONTRACT):communityJoin?EconomyCommandPayload.Action.ACCEPT_CONTRACT:c.state()==com.example.examplemod.economy.EconomyTypes.ContractState.DELIVERED?EconomyCommandPayload.Action.CONFIRM_CONTRACT:null;}else{var l=entry.listingView();action=switch(l.state()){case OPEN->l.mine()?EconomyCommandPayload.Action.CANCEL_LISTING:EconomyCommandPayload.Action.ACCEPT_LISTING;case RESERVED->EconomyCommandPayload.Action.MARK_HANDED;case HANDED_OVER->EconomyCommandPayload.Action.CONFIRM_LISTING;default->null;};}if(action!=null)PacketDistributor.sendToServer(EconomyCommandPayload.simple(this.menu.getPylonPos(),action,entry.id()));}
    private void openEconomyCreate(EconomyCreateScreen.Mode mode){if(this.minecraft!=null&&this.menu.getEconomyState()!=null)this.minecraft.setScreen(new EconomyCreateScreen(this,mode,this.menu.getPylonPos(),this.menu.getEconomyState()));}
    private List<EconomyEntry> economyVisibleEntries(EconomyStatePayload state){List<EconomyEntry> result=new ArrayList<>();for(var c:state.contracts())if(economyPage==0||c.mine()||c.issuerMine())result.add(new EconomyEntry(c.id(),false,c.itemId(),c,null));for(var l:state.listings())if(economyPage==0||l.mine())result.add(new EconomyEntry(l.id(),true,l.itemId(),null,l));String query=economySearch==null?"":economySearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);if(!query.isEmpty())result.removeIf(e->{ResourceLocation requested=e.contract()!=null?null:e.listingView().requestItemId();String text=e.item()+" "+economyIcon(e.item()).getHoverName().getString()+" "+(requested==null?"":economyIcon(requested).getHoverName().getString()+" ")+(e.contract()!=null?e.contract().title()+" "+e.contract().issuerName():e.listingView().creatorName()+" "+e.listingView().meeting());return!text.toLowerCase(java.util.Locale.ROOT).contains(query);});if(economySort==0)java.util.Collections.reverse(result);else if(economySort==1)result.sort(java.util.Comparator.comparingLong(e->e.contract()!=null?e.contract().reward():e.listingView().price()));else result.sort(java.util.Comparator.comparingLong(e->e.contract()!=null&&e.contract().deadlineAt()>0?e.contract().deadlineAt():Long.MAX_VALUE));return result;}
    private static ItemStack economyIcon(ResourceLocation id){var item=BuiltInRegistries.ITEM.get(id);return new ItemStack(item);}
    private static int economyStateColor(String name){return name.equals("COMPLETED")||name.equals("DELIVERED")?0xFF77B992:name.equals("FAILED")||name.equals("DISPUTED")?0xFFD07872:0xFFD09A3A;}
    private record EconomyEntry(UUID id,boolean listing,ResourceLocation item,EconomyStatePayload.ContractView contract,EconomyStatePayload.ListingView listingView){}

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
     * Ð£ÐŸÐ ÐÐ’Ð›Ð•ÐÐ˜Ð• Ð£Ð§ÐÐ¡Ð¢ÐÐ˜ÐšÐžÐœ
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
     * Ð¡Ð›Ð•Ð”Ð£Ð®Ð©Ð˜Ð™ Ð£Ð ÐžÐ’Ð•ÐÐ¬
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
         * ÐÐ¸Ñ‡ÐµÐ³Ð¾ Ð½Ðµ Ñ€Ð¸ÑÑƒÐµÐ¼.
         */
    }

    /*
     * =========================================
     * ÐŸÐžÐ›Ð®Ð¡ ÐŸÐ Ð˜Ð“Ð›ÐÐ¨Ð•ÐÐ˜Ð¯
     * =========================================
     */

    public BlockPos getInvitePylonPos() {

        return this.menu.getPylonPos();
    }
}
