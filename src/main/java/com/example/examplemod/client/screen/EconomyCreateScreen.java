package com.example.examplemod.client.screen;

import com.example.examplemod.economy.EconomyTypes;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.EconomyStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Форма создания записи рынка.
 * <p>
 * Частное объявление описывается с точки зрения игрока —
 * «Я ПРЕДЛАГАЮ» → «Я ХОЧУ ПОЛУЧИТЬ». Это покрывает item→currency,
 * currency→item и item→item. Технические registry id игрок не вводит:
 * предмет выбирается через {@link ItemSelectorOverlay}.
 */
public final class EconomyCreateScreen extends Screen {

    public enum Mode { PRIVATE, CITY_CONTRACT, MASTER_SPECIAL }

    private enum Side { ITEM, CURRENCY }

    private record Hit(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private static final int PAD_X = 12;
    private static final int MEETING_HEIGHT = 34;
    private static final int PICK_WIDTH = 118;

    private static final int COLOR_PANEL = 0xEE151B1E;
    private static final int COLOR_ACCENT = 0xFFD09A3A;
    private static final int COLOR_TEXT = 0xFFE2E6E7;
    private static final int COLOR_MUTED = 0xFF8F9A9E;
    private static final int COLOR_LABEL = 0xFFFFFFFF;
    private static final int COLOR_GOLD = 0xFFFFD59A;
    private static final int COLOR_ERROR = 0xFFD07872;
    private static final int COLOR_BTN = 0xFF493A29;
    private static final int COLOR_BTN_HOVER = 0xFF76562E;
    private static final int COLOR_BTN_OFF = 0xFF2B3032;
    private static final int COLOR_TOGGLE = 0xFF252D31;
    private static final int COLOR_TOGGLE_ON = 0xFF6B4D24;
    private static final int COLOR_FIELD = 0xFF20272B;

    private final Screen parent;
    private final Mode mode;
    private final BlockPos pylonPos;
    private final EconomyStatePayload state;
    private final ItemSelectorOverlay selector = new ItemSelectorOverlay();

    // Частное объявление.
    private Side offerSide = Side.ITEM;
    private Side wantSide = Side.CURRENCY;
    private ResourceLocation offerItem;
    private ResourceLocation wantItem;

    // Промышленный контракт.
    private ResourceLocation contractItem;
    private int intakeIndex;
    private EconomyTypes.ContractMode contractMode = EconomyTypes.ContractMode.EXCLUSIVE;

    // Текстовые значения, переживающие пересоздание виджетов.
    private String offerAmountText = "64";
    private String wantAmountText = "800";
    private String meetingText = "";
    private String amountText = "64";
    private String rewardText = "1000";
    private String titleText = "";
    private String noteText = "";
    private String deadlineText = "24";

    private EditBox offerBox;
    private EditBox wantBox;
    private EditBox meetingBox;
    private EditBox amountBox;
    private EditBox rewardBox;
    private EditBox titleBox;
    private EditBox noteBox;
    private EditBox deadlineBox;

    // Геометрия, единая для отрисовки, кликов и размещения виджетов.
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int innerX;
    private int innerW;

    private Hit backHit;
    private Hit submitHit;
    private int errorY;

    private int offerLabelY;
    private Hit offerItemHit;
    private Hit offerMoneyHit;
    private Hit offerPickHit;
    private int offerValueY;

    private int wantLabelY;
    private Hit wantItemHit;
    private Hit wantMoneyHit;
    private Hit wantPickHit;
    private int wantValueY;
    private int dividerY;

    private int meetingLabelY;

    private int contractItemY;
    private Hit contractPickHit;
    private int contractValueY;
    private int contractTitleY;
    private int contractNoteY;
    private int contractIntakeY;
    private Hit intakeHit;
    private Hit contractModeHit;
    private int contractCoordsY;

    private Component error = Component.empty();

    public EconomyCreateScreen(Screen parent, Mode mode, BlockPos pylonPos, EconomyStatePayload state) {
        super(Component.translatable("gui.createcityexpansioncore.economy.create_title"));
        this.parent = parent;
        this.mode = mode;
        this.pylonPos = pylonPos;
        this.state = state;
    }

    private boolean isPrivate() {
        return mode == Mode.PRIVATE;
    }

    // ---------------------------------------------------------------------
    // Геометрия
    // ---------------------------------------------------------------------

    private int sideHeight(Side side) {
        return side == Side.ITEM ? 11 + 18 + 19 + 19 : 11 + 18 + 19;
    }

    private int privateBaseHeight() {
        return 23 + sideHeight(offerSide) + 5 + sideHeight(wantSide) + 5 + 11 + 3 + 21 + 6;
    }

    private int desiredPanelHeight() {
        if (isPrivate()) {
            int base = privateBaseHeight();
            return base + (meetingFits() ? MEETING_HEIGHT : 0);
        }
        return 23 + 24 + 4 + 20 + 4 + 20 + 4 + 20 + 4 + 20 + 14 + 11 + 3 + 21 + 6;
    }

    private boolean meetingFits() {
        return isPrivate() && privateBaseHeight() + MEETING_HEIGHT <= Math.max(180, this.height - 16);
    }

    private void layout() {
        int availableWidth = Math.max(240, this.width - 16);
        int availableHeight = Math.max(180, this.height - 16);
        boolean withMeeting = meetingFits();

        this.panelW = Math.min(420, availableWidth);
        this.panelH = Math.min(desiredPanelHeight(), availableHeight);
        this.panelX = (this.width - panelW) / 2;
        this.panelY = (this.height - panelH) / 2;
        this.innerX = panelX + PAD_X;
        this.innerW = panelW - PAD_X * 2;

        int half = (innerW - 6) / 2;
        int y = panelY + 23;

        if (isPrivate()) {
            offerLabelY = y;
            y += 11;
            offerItemHit = new Hit(innerX, y, half, 18);
            offerMoneyHit = new Hit(innerX + half + 6, y, half, 18);
            y += 18;
            offerPickHit = offerSide == Side.ITEM
                    ? new Hit(innerX + innerW - PICK_WIDTH, y, PICK_WIDTH, 19) : null;
            if (offerSide == Side.ITEM) y += 19;
            offerValueY = y;
            y += 19 + 5;
            dividerY = y;
            y += 1;

            wantLabelY = y;
            y += 11;
            wantItemHit = new Hit(innerX, y, half, 18);
            wantMoneyHit = new Hit(innerX + half + 6, y, half, 18);
            y += 18;
            wantPickHit = wantSide == Side.ITEM
                    ? new Hit(innerX + innerW - PICK_WIDTH, y, PICK_WIDTH, 19) : null;
            if (wantSide == Side.ITEM) y += 19;
            wantValueY = y;
            y += 19 + 5;

            meetingLabelY = withMeeting ? y : -1;
            if (withMeeting) y += MEETING_HEIGHT;

            errorY = y;
            y += 11 + 3;
            submitHit = new Hit(innerX + innerW - 150, y, 150, 21);
            backHit = new Hit(innerX, y, 100, 21);
        } else {
            contractItemY = y;
            contractPickHit = new Hit(innerX + innerW - PICK_WIDTH - 12, y, PICK_WIDTH + 12, 20);
            y += 24 + 4;
            contractValueY = y;
            y += 20 + 4;
            contractTitleY = y;
            y += 20 + 4;
            contractNoteY = y;
            y += 20 + 4;
            contractIntakeY = y;
            intakeHit = new Hit(innerX, y, half, 20);
            contractModeHit = mode == Mode.MASTER_SPECIAL
                    ? new Hit(innerX + half + 6, y, half, 20) : null;
            y += 20;
            contractCoordsY = y;
            y += 14;
            errorY = y;
            y += 11 + 3;
            submitHit = new Hit(innerX + innerW - 150, y, 150, 21);
            backHit = new Hit(innerX, y, 100, 21);
        }
    }

    @Override
    protected void init() {
        syncFromBoxes();
        this.clearWidgets();
        offerBox = wantBox = meetingBox = amountBox = rewardBox = titleBox = noteBox = deadlineBox = null;

        layout();

        if (isPrivate()) {
            offerBox = field(innerX, offerValueY, innerW, 19, offerValueHint(), offerAmountText);
            wantBox = field(innerX, wantValueY, innerW, 19, wantValueHint(), wantAmountText);
            if (meetingLabelY >= 0) {
                meetingBox = field(innerX, meetingLabelY + 11, innerW, 19,
                        "gui.createcityexpansioncore.economy.field.note", meetingText);
            }
        } else {
            int half = (innerW - 6) / 2;
            int noteWidth = innerW - 96;
            amountBox = field(innerX, contractValueY, half, 19,
                    "gui.createcityexpansioncore.economy.field.amount", amountText);
            rewardBox = field(innerX + half + 6, contractValueY, half, 19,
                    "gui.createcityexpansioncore.economy.field.reward", rewardText);
            titleBox = field(innerX, contractTitleY, innerW, 19,
                    "gui.createcityexpansioncore.economy.field.title", titleText);
            noteBox = field(innerX, contractNoteY, noteWidth, 19,
                    "gui.createcityexpansioncore.economy.field.note", noteText);
            deadlineBox = field(innerX + noteWidth + 6, contractNoteY, 90, 19,
                    "gui.createcityexpansioncore.economy.field.deadline", deadlineText);
        }
    }

    private String offerValueHint() {
        return offerSide == Side.ITEM
                ? "gui.createcityexpansioncore.economy.field.amount"
                : "gui.createcityexpansioncore.economy.form.money_offer";
    }

    private String wantValueHint() {
        return wantSide == Side.ITEM
                ? "gui.createcityexpansioncore.economy.field.amount"
                : "gui.createcityexpansioncore.economy.form.money_want";
    }

    private EditBox field(int x, int y, int width, int height, String hintKey, String value) {
        EditBox box = new EditBox(this.font, x, y, width, height, Component.translatable(hintKey));
        box.setHint(Component.translatable(hintKey));
        box.setValue(value == null ? "" : value);
        box.setMaxLength(64);
        this.addRenderableWidget(box);
        return box;
    }

    private void syncFromBoxes() {
        if (offerBox != null) offerAmountText = offerBox.getValue();
        if (wantBox != null) wantAmountText = wantBox.getValue();
        if (meetingBox != null) meetingText = meetingBox.getValue();
        if (amountBox != null) amountText = amountBox.getValue();
        if (rewardBox != null) rewardText = rewardBox.getValue();
        if (titleBox != null) titleText = titleBox.getValue();
        if (noteBox != null) noteText = noteBox.getValue();
        if (deadlineBox != null) deadlineText = deadlineBox.getValue();
    }

    private List<EditBox> activeBoxes() {
        List<EditBox> boxes = new ArrayList<>(4);
        if (offerBox != null) boxes.add(offerBox);
        if (wantBox != null) boxes.add(wantBox);
        if (meetingBox != null) boxes.add(meetingBox);
        if (amountBox != null) boxes.add(amountBox);
        if (rewardBox != null) boxes.add(rewardBox);
        if (titleBox != null) boxes.add(titleBox);
        if (noteBox != null) boxes.add(noteBox);
        if (deadlineBox != null) boxes.add(deadlineBox);
        return boxes;
    }

    private List<EconomyStatePayload.InfrastructureView> intakes() {
        return state.infrastructure().stream()
                .filter(value -> value.cityOwned()
                        && value.type() == EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                        && value.available())
                .toList();
    }

    // ---------------------------------------------------------------------
    // Ввод
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selector.isOpen()) {
            selector.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (button == 0 && overAnyBox(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && handleClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean overAnyBox(double mouseX, double mouseY) {
        for (EditBox box : activeBoxes()) {
            if (mouseX >= box.getX() && mouseX < box.getX() + box.getWidth()
                    && mouseY >= box.getY() && mouseY < box.getY() + box.getHeight()) {
                return true;
            }
        }
        return false;
    }

    private boolean handleClick(double mouseX, double mouseY) {
        if (isPrivate()) {
            if (offerItemHit.contains(mouseX, mouseY)) {
                offerSide = Side.ITEM;
                refresh();
                return true;
            }
            if (offerMoneyHit.contains(mouseX, mouseY)) {
                offerSide = Side.CURRENCY;
                refresh();
                return true;
            }
            if (wantItemHit.contains(mouseX, mouseY)) {
                wantSide = Side.ITEM;
                refresh();
                return true;
            }
            if (wantMoneyHit.contains(mouseX, mouseY)) {
                wantSide = Side.CURRENCY;
                refresh();
                return true;
            }
            if (offerPickHit != null && offerPickHit.contains(mouseX, mouseY)) {
                openSelector(true);
                return true;
            }
            if (wantPickHit != null && wantPickHit.contains(mouseX, mouseY)) {
                openSelector(false);
                return true;
            }
        } else {
            if (contractPickHit.contains(mouseX, mouseY)) {
                openContractSelector();
                return true;
            }
            if (intakeHit.contains(mouseX, mouseY)) {
                List<EconomyStatePayload.InfrastructureView> values = intakes();
                if (!values.isEmpty()) intakeIndex = (intakeIndex + 1) % values.size();
                return true;
            }
            if (contractModeHit != null && contractModeHit.contains(mouseX, mouseY)) {
                contractMode = contractMode == EconomyTypes.ContractMode.EXCLUSIVE
                        ? EconomyTypes.ContractMode.COMMUNITY
                        : EconomyTypes.ContractMode.EXCLUSIVE;
                return true;
            }
        }
        if (backHit.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (submitHit.contains(mouseX, mouseY)) {
            submit();
            return true;
        }
        return false;
    }

    private void refresh() {
        syncFromBoxes();
        this.rebuildWidgets();
    }

    private void openSelector(boolean forOffer) {
        selector.open(panelX, panelY, panelW, panelH, id -> {
            if (forOffer) offerItem = id;
            else wantItem = id;
        });
    }

    private void openContractSelector() {
        selector.open(panelX, panelY, panelW, panelH, id -> contractItem = id);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selector.isOpen()) {
            selector.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selector.isOpen()) {
            selector.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (selector.isOpen()) {
            selector.charTyped(codePoint, modifiers);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    // ---------------------------------------------------------------------
    // Отправка
    // ---------------------------------------------------------------------

    private static long parsePositive(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private static long parseNonNegative(String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed >= 0L ? parsed : -1L;
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private static boolean validItem(ResourceLocation id) {
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR;
    }

    private void submit() {
        syncFromBoxes();
        if (isPrivate()) {
            submitPrivate();
        } else {
            submitContract();
        }
    }

    private void submitPrivate() {
        boolean offerCurrency = offerSide == Side.CURRENCY;
        boolean wantCurrency = wantSide == Side.CURRENCY;
        if (offerCurrency && wantCurrency) {
            error = Component.translatable("gui.createcityexpansioncore.economy.form.both_currency");
            return;
        }
        long offerValue = parsePositive(offerBox == null ? "" : offerBox.getValue());
        long wantValue = parsePositive(wantBox == null ? "" : wantBox.getValue());
        if (offerValue <= 0L || wantValue <= 0L) {
            error = Component.translatable("gui.createcityexpansioncore.economy.invalid_input");
            return;
        }
        if (!offerCurrency && !validItem(offerItem)) {
            error = Component.translatable("gui.createcityexpansioncore.economy.form.need_offer_item");
            return;
        }
        if (!wantCurrency && !validItem(wantItem)) {
            error = Component.translatable("gui.createcityexpansioncore.economy.form.need_want_item");
            return;
        }

        ResourceLocation item;
        ResourceLocation requested;
        long amount;
        long money;
        int ordinal;

        if (!offerCurrency) {
            item = offerItem;
            amount = offerValue;
            money = wantValue;
            requested = wantCurrency ? null : wantItem;
            ordinal = EconomyTypes.ListingType.SELL.ordinal();
        } else {
            item = wantItem;
            amount = wantValue;
            money = offerValue;
            requested = null;
            ordinal = EconomyTypes.ListingType.BUY.ordinal();
        }

        PacketDistributor.sendToServer(new EconomyCommandPayload(
                pylonPos, EconomyCommandPayload.Action.CREATE_LISTING, null, null,
                item, requested, amount, money, 0L, ordinal, "", "",
                meetingBox == null ? "" : meetingBox.getValue()));
        onClose();
    }

    private void submitContract() {
        List<EconomyStatePayload.InfrastructureView> values = intakes();
        if (values.isEmpty()) {
            error = Component.translatable("gui.createcityexpansioncore.economy.no_intake");
            return;
        }
        if (!validItem(contractItem)) {
            error = Component.translatable("gui.createcityexpansioncore.economy.form.need_offer_item");
            return;
        }
        long amountValue = parsePositive(amountBox == null ? "" : amountBox.getValue());
        long money = parsePositive(rewardBox == null ? "" : rewardBox.getValue());
        long hours = parseNonNegative(deadlineBox == null ? "" : deadlineBox.getValue());
        if (amountValue <= 0L || money <= 0L || hours < 0L) {
            error = Component.translatable("gui.createcityexpansioncore.economy.invalid_input");
            return;
        }
        long deadlineTicks;
        try {
            deadlineTicks = Math.multiplyExact(hours, 72_000L);
        } catch (ArithmeticException ex) {
            error = Component.translatable("gui.createcityexpansioncore.economy.invalid_input");
            return;
        }
        PacketDistributor.sendToServer(new EconomyCommandPayload(
                pylonPos,
                mode == Mode.MASTER_SPECIAL
                        ? EconomyCommandPayload.Action.CREATE_MASTER_SPECIAL
                        : EconomyCommandPayload.Action.CREATE_CITY_CONTRACT,
                values.get(Math.floorMod(intakeIndex, values.size())).id(),
                null, contractItem, null, amountValue, money, deadlineTicks,
                mode == Mode.MASTER_SPECIAL ? contractMode.ordinal() : EconomyTypes.ContractMode.EXCLUSIVE.ordinal(),
                titleBox == null ? "" : titleBox.getValue(),
                noteBox == null ? "" : noteBox.getValue(),
                ""));
        onClose();
    }

    // ---------------------------------------------------------------------
    // Отрисовка
    // ---------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (selector.isOpen()) {
            selector.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 3, COLOR_ACCENT);
        graphics.renderOutline(panelX, panelY, panelW, panelH, COLOR_ACCENT);
        graphics.drawCenteredString(this.font, titleComponent(), panelX + panelW / 2, panelY + 9, COLOR_LABEL);

        if (isPrivate()) {
            drawPrivate(graphics, mouseX, mouseY);
        } else {
            drawContract(graphics, mouseX, mouseY);
        }

        if (!error.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, ellipsize(error, innerW),
                    panelX + panelW / 2, errorY, COLOR_ERROR);
        }

        button(graphics, backHit, Component.translatable("gui.createcityexpansioncore.common.back"),
                true, mouseX, mouseY);
        button(graphics, submitHit, Component.translatable("gui.createcityexpansioncore.economy.publish"),
                true, mouseX, mouseY);

        for (EditBox box : activeBoxes()) {
            box.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private Component titleComponent() {
        if (isPrivate()) {
            return Component.translatable("gui.createcityexpansioncore.economy.form.title");
        }
        return Component.translatable("gui.createcityexpansioncore.economy.create_mode."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private void drawPrivate(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.economy.form.offer_title"),
                innerX, offerLabelY, COLOR_GOLD, false);
        if (inside(mouseX, mouseY, innerX, offerLabelY, innerW / 2, 11)) {
            renderWrappedTooltip(graphics,
                    Component.translatable("gui.createcityexpansioncore.economy.form.offer_help"),
                    mouseX, mouseY);
        }

        toggle(graphics, offerItemHit, "gui.createcityexpansioncore.economy.form.use_item",
                offerSide == Side.ITEM, mouseX, mouseY);
        toggle(graphics, offerMoneyHit, "gui.createcityexpansioncore.economy.form.use_money",
                offerSide == Side.CURRENCY, mouseX, mouseY);
        if (offerPickHit != null) {
            itemRow(graphics, offerItem, offerPickHit, offerLabelY + 11 + 18, mouseX, mouseY);
        }
        valueLabel(graphics, offerValueY, offerSide == Side.ITEM
                ? Component.translatable("gui.createcityexpansioncore.economy.field.amount")
                : Component.translatable("gui.createcityexpansioncore.economy.form.money_offer"));

        graphics.fill(innerX, dividerY, innerX + innerW, dividerY + 1, 0xFF3A4246);

        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.economy.form.want_title"),
                innerX, wantLabelY, COLOR_GOLD, false);
        toggle(graphics, wantItemHit, "gui.createcityexpansioncore.economy.form.use_item",
                wantSide == Side.ITEM, mouseX, mouseY);
        toggle(graphics, wantMoneyHit, "gui.createcityexpansioncore.economy.form.use_money",
                wantSide == Side.CURRENCY, mouseX, mouseY);
        if (wantPickHit != null) {
            itemRow(graphics, wantItem, wantPickHit, wantLabelY + 11 + 18, mouseX, mouseY);
        }
        valueLabel(graphics, wantValueY, wantSide == Side.ITEM
                ? Component.translatable("gui.createcityexpansioncore.economy.field.amount")
                : Component.translatable("gui.createcityexpansioncore.economy.form.money_want"));

        if (meetingLabelY >= 0) {
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.economy.form.meeting_label"),
                    innerX, meetingLabelY, COLOR_MUTED, false);
        }

        if (inside(mouseX, mouseY, innerX, wantLabelY, innerW / 2, 11)) {
            renderWrappedTooltip(graphics,
                    Component.translatable("gui.createcityexpansioncore.economy.form.want_help"),
                    mouseX, mouseY);
        }
        tooltipOn(graphics, mouseX, mouseY,
                offerSide == Side.ITEM ? offerPickHit : offerMoneyHit,
                offerSide == Side.ITEM
                        ? "gui.createcityexpansioncore.economy.form.item_help"
                        : "gui.createcityexpansioncore.economy.form.money_help");
        tooltipOn(graphics, mouseX, mouseY,
                wantSide == Side.ITEM ? wantPickHit : wantMoneyHit,
                wantSide == Side.ITEM
                        ? "gui.createcityexpansioncore.economy.form.item_help"
                        : "gui.createcityexpansioncore.economy.form.money_help");
    }

    private void drawContract(GuiGraphics graphics, int mouseX, int mouseY) {
        itemRow(graphics, contractItem, contractPickHit, contractItemY, mouseX, mouseY);

        int half = (innerW - 6) / 2;
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.economy.field.amount"),
                innerX, contractValueY + 5, COLOR_MUTED, false);
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.economy.field.reward"),
                innerX + half + 6, contractValueY + 5, COLOR_MUTED, false);
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.economy.field.title"),
                innerX, contractTitleY + 5, COLOR_MUTED, false);
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.economy.field.note"),
                innerX, contractNoteY + 5, COLOR_MUTED, false);
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.economy.field.deadline"),
                innerX + innerW - 90, contractNoteY + 5, COLOR_MUTED, false);

        button(graphics, intakeHit,
                Component.translatable("gui.createcityexpansioncore.economy.intake"), true, mouseX, mouseY);
        if (contractModeHit != null) {
            button(graphics, contractModeHit, contractModeLabel(), true, mouseX, mouseY);
        }

        List<EconomyStatePayload.InfrastructureView> values = intakes();
        Component coords = values.isEmpty()
                ? Component.translatable("gui.createcityexpansioncore.economy.no_intake")
                : Component.translatable("gui.createcityexpansioncore.economy.intake_coordinates",
                values.get(Math.floorMod(intakeIndex, values.size())).pos().toShortString());
        graphics.drawString(this.font, ellipsize(coords, innerW), innerX, contractCoordsY, 0xFFAEB8BB, false);
    }

    private Component contractModeLabel() {
        return Component.translatable("gui.createcityexpansioncore.economy.mode."
                + contractMode.name().toLowerCase(Locale.ROOT));
    }

    private void valueLabel(GuiGraphics graphics, int y, Component label) {
        graphics.drawString(this.font, label, innerX, y + 5, COLOR_MUTED, false);
    }

    /**
     * Строка «[иконка] Название» + кнопка выбора.
     * Registry id игроку не показывается.
     */
    private void itemRow(GuiGraphics graphics, ResourceLocation id, Hit pickHit, int y,
                         int mouseX, int mouseY) {
        int rowRight = pickHit.x() - 6;
        graphics.fill(innerX, y, rowRight, y + pickHit.h(), COLOR_FIELD);
        if (validItem(id)) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            graphics.renderItem(stack, innerX + 2, y + (pickHit.h() - 16) / 2);
            graphics.drawString(this.font,
                    ellipsize(Component.literal(stack.getHoverName().getString()),
                            rowRight - innerX - 24),
                    innerX + 21, y + (pickHit.h() - 8) / 2, COLOR_TEXT, false);
        } else {
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.economy.form.no_item"),
                    innerX + 7, y + (pickHit.h() - 8) / 2, COLOR_MUTED, false);
        }
        button(graphics, pickHit,
                Component.translatable("gui.createcityexpansioncore.economy.form.pick_item"),
                true, mouseX, mouseY);
    }

    private void toggle(GuiGraphics graphics, Hit hit, String key, boolean selected,
                        int mouseX, int mouseY) {
        if (hit == null) return;
        boolean hover = hit.contains(mouseX, mouseY);
        graphics.fill(hit.x(), hit.y(), hit.x() + hit.w(), hit.y() + hit.h(),
                selected ? COLOR_TOGGLE_ON : (hover ? 0xFF31404A : COLOR_TOGGLE));
        graphics.drawCenteredString(this.font, Component.translatable(key),
                hit.x() + hit.w() / 2, hit.y() + 5, selected ? COLOR_GOLD : COLOR_MUTED);
    }

    private void button(GuiGraphics graphics, Hit hit, Component label, boolean active,
                        int mouseX, int mouseY) {
        if (hit == null) return;
        boolean hover = active && hit.contains(mouseX, mouseY);
        graphics.fill(hit.x(), hit.y(), hit.x() + hit.w(), hit.y() + hit.h(),
                !active ? COLOR_BTN_OFF : (hover ? COLOR_BTN_HOVER : COLOR_BTN));
        graphics.drawCenteredString(this.font, ellipsize(label, hit.w() - 6),
                hit.x() + hit.w() / 2, hit.y() + 6, active ? COLOR_GOLD : 0xFF727A7D);
    }

    private void tooltipOn(GuiGraphics graphics, int mouseX, int mouseY, Hit hit, String key) {
        if (hit != null && hit.contains(mouseX, mouseY)) {
            renderWrappedTooltip(graphics, Component.translatable(key), mouseX, mouseY);
        }
    }

    private void renderWrappedTooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
        graphics.renderTooltip(this.font,
                this.font.split(text, Math.max(120, Math.min(240, this.width - 24))),
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                mouseX, mouseY);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private Component ellipsize(Component component, int maxWidth) {
        if (maxWidth <= 16) return component;
        String text = component.getString();
        if (this.font.width(text) <= maxWidth) return component;
        String trimmed = this.font.plainSubstrByWidth(text, maxWidth - this.font.width("..."));
        return Component.literal(trimmed + "...");
    }
}
