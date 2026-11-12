package com.example.examplemod.client.journey;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.journey.JourneyCodec;
import com.example.examplemod.journey.JourneyEditorPayload;
import com.example.examplemod.journey.JourneyModel.Marker;
import com.example.examplemod.journey.JourneyModel.Point;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.example.examplemod.client.journey.JourneyAtlasStyle.BRASS;
import static com.example.examplemod.client.journey.JourneyAtlasStyle.EDGE;
import static com.example.examplemod.client.journey.JourneyAtlasStyle.MUTED;
import static com.example.examplemod.client.journey.JourneyAtlasStyle.RAISED;
import static com.example.examplemod.client.journey.JourneyAtlasStyle.SURFACE;
import static com.example.examplemod.client.journey.JourneyAtlasStyle.TEXT;
import static com.example.examplemod.journey.JourneyRouteDraft.MAX_DESCRIPTION;
import static com.example.examplemod.journey.JourneyRouteDraft.MAX_STEPS;
import static com.example.examplemod.journey.JourneyRouteDraft.MAX_TITLE;
import static com.example.examplemod.journey.JourneyRouteDraft.MAX_WAYPOINTS;

/** Step-first workspace for the published newcomer route and its independent draft. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class JourneyRouteEditorScreen extends Screen {
    private static final int CARD_HEIGHT = 33;
    private static final int BODY_TOP = 62;
    private static final int BACKGROUND = 0xff10171b;
    private static final int SELECTED = 0xff35413f;
    private static final int ERROR = 0xfff2a496;
    private static Placement placement;

    private enum Action {
        VISIT(0), INTERACT(2), ITEM(4), OPEN_PYLON(3);

        final int icon;

        Action(int icon) { this.icon = icon; }

        static Action of(CompoundTag step) {
            try {
                return valueOf(step.getString("Type"));
            } catch (IllegalArgumentException ignored) {
                return VISIT;
            }
        }

        Component label() { return tr("action." + name().toLowerCase(Locale.ROOT)); }
        boolean usesBlock() { return this == INTERACT || this == OPEN_PYLON; }
    }

    private enum Pick { STEP, START, WAYPOINT }
    private enum Dialog { NONE, ADD, ACTION, ITEM, TEST, EXIT, RELOAD, PUBLISH }

    private record Placement(JourneyRouteEditorScreen editor, Pick pick, ClientLevel level, LocalPlayer player) {}
    private record Label(Component text, int x, int y) {}
    private record ItemChoice(Item item, String id, Component name) {}

    private final JourneyAtlasScreen parent;
    private final CityMapPanel map;
    private final List<Label> labels = new ArrayList<>();
    private CompoundTag draft = new CompoundTag();
    private CompoundTag savedDraft = new CompoundTag();
    private String revision = "";
    private String pending = "";
    private long pendingAt;
    private Component status = tr("loading");
    private boolean statusError;
    private boolean reloadNeeded;
    private boolean loaded;
    private boolean initialRequest;
    private boolean suspended;
    private boolean returning;
    private boolean playtestLaunched;
    private boolean compactMap;
    private boolean advanced;
    private boolean draggingMap;
    private boolean pickMap;
    private int selected = -1;
    private int listOffset;
    private int formScroll;
    private int formHeight;
    private int dragIndex = -1;
    private int stepError = -1;
    private Dialog dialog = Dialog.NONE;
    private List<ItemChoice> itemChoices = List.of();
    private List<ItemChoice> filteredItems = List.of();
    private int itemOffset;
    private int itemSelection;
    private int listWidth;
    private int formLeft;
    private int mapLeft;
    private int mapWidth;
    private int bodyBottom;
    private boolean wide;
    private Button saveButton;

    JourneyRouteEditorScreen(JourneyAtlasScreen parent, CityMapPanel map) {
        super(tr("title"));
        this.parent = parent;
        this.map = map;
    }

    public boolean uses(CityMapPanel panel) { return map == panel; }

    private static Component tr(String key, Object... args) {
        return JourneyClient.tr("route_editor." + key, args);
    }

    private ListTag steps() { return draft.getList("Steps", Tag.TAG_COMPOUND); }

    private CompoundTag step() {
        var steps = steps();
        return selected >= 0 && selected < steps.size() ? steps.getCompound(selected) : null;
    }

    private boolean dirty() { return !draft.equals(savedDraft); }

    @Override
    protected void init() {
        if (playtestLaunched) {
            playtestLaunched = false;
            suspended = false;
            Component error = JourneyPlaytest.lastError();
            message(error == null ? tr("test_return") : error, error != null);
        }
        if (!initialRequest) {
            initialRequest = true;
            send("load");
        }
        wide = width >= 640;
        listWidth = wide ? 154 : Math.clamp(width / 3, 94, 154);
        formLeft = wide ? width - 244 : listWidth;
        mapLeft = wide ? listWidth : 0;
        mapWidth = wide ? formLeft - mapLeft : width;
        bodyBottom = Math.max(BODY_TOP + 32, height - 53);
        labels.clear();
        saveButton = null;
        if (dialog != Dialog.NONE) {
            initDialog();
            return;
        }

        button(8, 6, 68, tr("back"), this::onClose);
        if (!wide) button(width - 98, 6, 90, tr(compactMap ? "steps" : "map"), this::toggleMap);
        if (!loaded) {
            if (pending.isEmpty()) button(10, BODY_TOP + 22, 104, tr("retry"), () -> send("load"));
            return;
        }
        boolean ready = pending.isEmpty();
        textBox(8, 34, Math.max(32, width - 218), tr("route_name"), draft.getString("Title"), MAX_TITLE,
                value -> {
                    draft.putString("Title", value);
                    changed();
                }).active = ready;
        Button startButton = button(width - 202, 33, 119, tr(draft.contains("Start", Tag.TAG_COMPOUND) ? "move_start" : "set_start"),
                () -> beginPlacement(Pick.START));
        startButton.active = ready;
        startButton.setTooltip(Tooltip.create(tr("start_tooltip")));
        button(width - 77, 33, 69, tr("clear_start"), this::clearStart).active = ready && draft.contains("Start", Tag.TAG_COMPOUND);

        if (wide || !compactMap) {
            button(8, bodyBottom - 25, listWidth - 16, tr("add_step"), () -> show(Dialog.ADD)).active = ready && steps().size() < MAX_STEPS;
            CompoundTag step = step();
            if (step != null) initForm(step, ready);
        }

        int buttonWidth = (width - 34) / 4;
        int footer = height - 27;
        saveButton = button(8, footer, buttonWidth, tr("save"), () -> send("save"));
        saveButton.active = ready && dirty();
        button(14 + buttonWidth, footer, buttonWidth, tr("check"), () -> send("check")).active = ready && !steps().isEmpty();
        button(20 + 2 * buttonWidth, footer, buttonWidth, tr("test"), () -> {
            if (selected > 0) show(Dialog.TEST);
            else testDraft(0);
        }).active = ready && !steps().isEmpty();
        button(26 + 3 * buttonWidth, footer, buttonWidth, tr("publish"), () -> show(Dialog.PUBLISH)).active = ready && !steps().isEmpty();
        if (reloadNeeded && ready) {
            button(width - 86, height - 51, 78, tr("reload"), () -> {
                if (dirty()) show(Dialog.RELOAD);
                else send("load");
            });
        }
    }

    private void initForm(CompoundTag step, boolean ready) {
        int x = formLeft + 10;
        int w = width - x - 10;
        int top = BODY_TOP + 8 - formScroll;
        int y = top;
        Action action = Action.of(step);
        formButton(x, y, w, action.label(), () -> show(Dialog.ACTION), ready);
        y += 26;
        formText(x, y, w, "step_name", step.getString("Title"), MAX_TITLE, value -> {
            step.putString("Title", value);
            changed();
        }, ready);
        y += 37;
        formLabel(x, y, step.getString("Dimension").isEmpty() ? tr("no_location") : tr("location", step.getInt("X"), step.getInt("Y"), step.getInt("Z")));
        y += 14;
        int half = (w - 5) / 2;
        formButton(x, y, action.usesBlock() ? w : half, tr(action.usesBlock() ? "pick_block" : "pick_world"), () -> beginPlacement(Pick.STEP), ready);
        if (!action.usesBlock()) {
            formButton(x + half + 5, y, w - half - 5, tr("pick_map"), () -> {
                pickMap = true;
                compactMap = true;
                message(tr("pick_map_hint", placementHeight(step)), false);
                rebuildWidgets();
            }, ready);
        }
        y += 28;

        if (action == Action.ITEM) {
            var id = ResourceLocation.tryParse(step.getString("Item"));
            var item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            formButton(x, y, w, item == Items.AIR ? tr("choose_item") : item.getDescription(), this::chooseItem, ready);
            y += 26;
            formNumber(x, y, w, "item_count", step, "Count", ready);
            y += 37;
        }

        formHint(x, y, w, step, ready);
        y += 70;

        formButton(x, y, w, tr(advanced ? "hide_details" : "details"), () -> {
            advanced = !advanced;
            rebuildWidgets();
        }, ready);
        y += 26;
        if (advanced) {
            formNumber(x, y, w, "radius", step, "Radius", ready);
            y += 37;
            int count = step.getList("Route", Tag.TAG_COMPOUND).size();
            formLabel(x, y, tr("waypoint_count", count));
            y += 14;
            formButton(x, y, w, tr("add_waypoint"), () -> beginPlacement(Pick.WAYPOINT), ready && totalWaypoints() < MAX_WAYPOINTS);
            y += 26;
            formButton(x, y, w, tr("remove_waypoint"), () -> {
                step.getList("Route", Tag.TAG_COMPOUND).removeLast();
                changed();
                rebuildWidgets();
            }, ready && count > 0);
            y += 26;
        }
        formButton(x, y, half, tr("up"), () -> move(selected, selected - 1), ready && selected > 0);
        formButton(x + half + 5, y, w - half - 5, tr("down"), () -> move(selected, selected + 1), ready && selected < steps().size() - 1);
        y += 26;
        formButton(x, y, w, tr("remove_step"), this::removeSelected, ready);
        formHeight = y + 28 - top;
    }

    private void formHint(int x, int y, int w, CompoundTag step, boolean active) {
        if (!inForm(y, 65)) return;
        labels.add(new Label(tr("step_hint"), x, y));
        MultiLineEditBox box = new MultiLineEditBox(font, x, y + 12, w, 47, tr("hint_placeholder"), tr("step_hint")) {
            @Override
            protected void renderBackground(GuiGraphics graphics) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), RAISED);
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), isFocused() ? BRASS : EDGE);
            }
        };
        box.setCharacterLimit(MAX_DESCRIPTION);
        box.setValue(step.getString("Description"));
        box.setValueListener(value -> {
            step.putString("Description", value);
            changed();
        });
        box.active = active;
        addRenderableWidget(box);
    }

    private Button button(int x, int y, int w, Component title, Runnable action) {
        return addRenderableWidget(new JourneyAtlasStyle.FlatButton(x, y, w, 20, title, action));
    }

    private EditBox textBox(int x, int y, int w, Component title, String value, int limit, Consumer<String> change) {
        EditBox box = new EditBox(font, x, y, w, 19, title);
        box.setMaxLength(limit);
        box.setHint(title);
        box.setValue(value);
        box.setResponder(change);
        return addRenderableWidget(box);
    }

    private boolean inForm(int y, int h) { return y >= BODY_TOP + 2 && y + h <= bodyBottom - 3; }

    private void formButton(int x, int y, int w, Component title, Runnable action, boolean active) {
        if (inForm(y, 20)) button(x, y, w, title, action).active = active;
    }

    private void formLabel(int x, int y, Component label) {
        if (inForm(y, 10)) labels.add(new Label(label, x, y));
    }

    private void formText(int x, int y, int w, String key, String value, int limit, Consumer<String> change, boolean active) {
        if (!inForm(y, 33)) return;
        labels.add(new Label(tr(key), x, y));
        textBox(x, y + 12, w, tr(key), value, limit, change).active = active;
    }

    private void formNumber(int x, int y, int w, String key, CompoundTag step, String property, boolean active) {
        if (!inForm(y, 33)) return;
        labels.add(new Label(tr(key), x, y));
        int current = step.getInt(property);
        var box = textBox(x, y + 12, w, tr(key), current == 0 ? "" : Integer.toString(current), 4, value -> {
            step.putInt(property, value.isEmpty() ? 0 : Integer.parseInt(value));
            changed();
        });
        box.setFilter(value -> value.chars().allMatch(c -> c >= '0' && c <= '9'));
        box.active = active;
    }

    private void toggleMap() {
        compactMap = !compactMap;
        pickMap = false;
        status = null;
        rebuildWidgets();
    }

    private void clearStart() {
        draft.remove("Start");
        changed();
        rebuildWidgets();
    }

    private void show(Dialog next) {
        dialog = next;
        rebuildWidgets();
    }

    private int dialogWidth() { return Math.min(312, width - 24); }

    private void initDialog() {
        int w = dialogWidth();
        int x = (width - w) / 2;
        if (dialog == Dialog.ADD || dialog == Dialog.ACTION) {
            int y = height / 2 - 50;
            for (Action action : Action.values()) {
                button(x + 12, y, w - 24, action.label(), () -> setAction(action));
                y += 25;
            }
            button(x + 12, y + 2, w - 24, tr("cancel"), () -> show(Dialog.NONE));
        } else if (dialog == Dialog.ITEM) {
            EditBox filter = textBox(x + 12, 45, w - 24, tr("search_item"), "", 64, this::filterItems);
            setInitialFocus(filter);
            button(x + 12, height - 49, w - 24, tr("cancel"), () -> show(Dialog.NONE));
        } else if (dialog == Dialog.TEST) {
            button(x + 12, height / 2 - 13, w - 24, tr("test_from_start"), () -> launchTestFromDialog(0));
            button(x + 12, height / 2 + 12, w - 24, tr("test_from_step"), () -> launchTestFromDialog(selected));
            button(x + 12, height / 2 + 37, w - 24, tr("cancel"), () -> show(Dialog.NONE));
        } else {
            int half = (w - 29) / 2;
            int y = height / 2 + 20;
            button(x + 12, y, half, tr("cancel"), () -> show(Dialog.NONE));
            String label = switch (dialog) {
                case EXIT -> "discard";
                case RELOAD -> "reload";
                case PUBLISH -> "publish";
                default -> throw new IllegalStateException();
            };
            button(x + 17 + half, y, half, tr(label), () -> {
                Dialog confirmed = dialog;
                dialog = Dialog.NONE;
                if (confirmed == Dialog.EXIT) returnToAtlas();
                else send(confirmed == Dialog.RELOAD ? "load" : "publish");
            });
        }
    }

    private void setAction(Action action) {
        boolean adding = dialog == Dialog.ADD;
        var step = adding ? new CompoundTag() : step();
        if (step == null) return;
        if (adding) {
            var steps = steps();
            if (steps.size() >= MAX_STEPS) return;
            step.putInt("Radius", 4);
            step.putInt("Count", 1);
            steps.add(step);
            draft.put("Steps", steps);
            selected = steps.size() - 1;
            listOffset = Math.max(0, selected - listRows() + 1);
        } else if (Action.of(step) != action && (Action.of(step).usesBlock() != action.usesBlock() || action == Action.OPEN_PYLON)) {
            // A player's standing point cannot double as the exact block to interact with.
            step.remove("Dimension");
            step.remove("X");
            step.remove("Y");
            step.remove("Z");
        }
        step.putString("Type", action.name());
        step.putInt("Icon", action.icon);
        formScroll = 0;
        changed();
        show(Dialog.NONE);
    }

    private void chooseItem() {
        itemChoices = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                .map(item -> new ItemChoice(item, BuiltInRegistries.ITEM.getKey(item).toString(), item.getDescription()))
                .sorted(Comparator.comparing(choice -> choice.name().getString(), String.CASE_INSENSITIVE_ORDER)).toList();
        filterItems("");
        show(Dialog.ITEM);
    }

    private void filterItems(String query) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        filteredItems = itemChoices.stream().filter(item -> item.id().contains(term)
                || item.name().getString().toLowerCase(Locale.ROOT).contains(term)).toList();
        itemSelection = itemOffset = 0;
    }

    private int itemRows() { return Math.max(1, (height - 128) / 20); }

    private void chooseItem(int index) {
        var step = step();
        if (step == null || index < 0 || index >= filteredItems.size()) return;
        step.putString("Item", filteredItems.get(index).id());
        changed();
        show(Dialog.NONE);
    }

    private void select(int index) {
        if (index < 0 || index >= steps().size()) return;
        selected = index;
        formScroll = 0;
        advanced = false;
        pickMap = false;
        focusSelected();
        rebuildWidgets();
    }

    private void focusSelected() {
        var step = step();
        if (step == null) return;
        var point = point(step);
        if (point != null && point.dimension().equals(map.dimensionId().toString())) {
            map.restoreView(point.x() + .5, point.z() + .5, map.zoom());
        }
    }

    private void move(int from, int to) {
        var steps = steps();
        if (to < 0 || to >= steps.size() || from < 0 || from >= steps.size() || from == to) return;
        steps.add(to, steps.remove(from));
        selected = to;
        listOffset = Math.clamp(listOffset, Math.max(0, to - listRows() + 1), to);
        changed();
        rebuildWidgets();
    }

    private void removeSelected() {
        if (step() == null) return;
        var steps = steps();
        steps.remove(selected);
        selected = Math.min(selected, steps.size() - 1);
        formScroll = 0;
        changed();
        rebuildWidgets();
    }

    private int totalWaypoints() {
        int count = 0;
        for (Tag step : steps()) count += ((CompoundTag) step).getList("Route", Tag.TAG_COMPOUND).size();
        return count;
    }

    private void changed() {
        status = null;
        statusError = false;
        stepError = -1;
        if (saveButton != null) saveButton.active = dirty() && pending.isEmpty();
    }

    private void message(Component component, boolean error) {
        status = component;
        statusError = error;
    }

    private void send(String action) {
        if (!pending.isEmpty() || minecraft == null || minecraft.getConnection() == null) return;
        String value = action.equals("load") ? "" : draft.toString();
        if (value.length() > 24000 || value.getBytes(StandardCharsets.UTF_8).length > 30000) {
            message(JourneyClient.tr("error.limit"), true);
            return;
        }
        pending = action;
        pendingAt = Util.getMillis();
        message(tr(action.equals("load") ? "loading" : "working"), false);
        PacketDistributor.sendToServer(new JourneyEditorPayload(action, "route", "newcomer", revision, value));
        if (loaded) rebuildWidgets();
    }

    public void receive(CompoundTag reply) {
        if (!reply.getString("Group").equals("route") || !reply.getString("Id").equals("newcomer") || pending.isEmpty()) return;
        String event = reply.getString("Event");
        if (event.equals("error")) {
            pending = "";
            stepError = reply.getInt("Step") - 1;
            String code = reply.getString("Error");
            reloadNeeded = code.equals("conflict");
            Component error = JourneyClient.tr("error." + code);
            message(stepError < 0 ? error : tr("step_error", stepError + 1, error), true);
            if (stepError >= 0 && stepError < steps().size()) {
                listOffset = Math.max(0, stepError - listRows() + 1);
                compactMap = false;
                select(stepError);
            } else rebuildWidgets();
            return;
        }
        String expected = switch (pending) {
            case "load" -> "loaded";
            case "save" -> "saved";
            case "check" -> "checked";
            case "publish" -> "published";
            default -> "";
        };
        if (!event.equals(expected)) return;
        pending = "";
        reloadNeeded = false;
        if (!event.equals("checked")) {
            draft = reply.getCompound("Value").copy();
            if (draft.isEmpty()) draft.putString("Title", tr("default_name").getString());
            if (!draft.contains("Steps", Tag.TAG_LIST)) draft.put("Steps", new ListTag());
            savedDraft = draft.copy();
            revision = reply.getString("Revision");
            if (event.equals("loaded")) {
                loaded = true;
                selected = steps().isEmpty() ? -1 : 0;
                listOffset = formScroll = 0;
                focusSelected();
            }
        }
        stepError = -1;
        message(event.equals("loaded") ? null : tr(event), false);
        rebuildWidgets();
    }

    private void launchTestFromDialog(int start) {
        dialog = Dialog.NONE;
        rebuildWidgets();
        testDraft(start);
    }

    private void testDraft(int start) {
        if (steps().isEmpty()) return;
        suspended = true;
        playtestLaunched = true;
        if (!JourneyPlaytest.start(this, draft.copy(), start, this::abandon)) {
            suspended = false;
            playtestLaunched = false;
            Component error = JourneyPlaytest.lastError();
            message(error == null ? tr("test_unavailable") : error, true);
        }
    }

    private void beginPlacement(Pick pick) {
        if (minecraft.player == null || minecraft.level == null || placement != null) return;
        if (pick == Pick.WAYPOINT && (step() == null || point(step()) == null)) {
            message(tr("no_location"), true);
            return;
        }
        pickMap = false;
        message(null, false);
        suspended = true;
        placement = new Placement(this, pick, minecraft.level, minecraft.player);
        minecraft.setScreen(null);
        if (minecraft.screen != null) {
            placement = null;
            suspended = false;
        }
    }

    private void capture(Placement session) {
        CompoundTag step = step();
        BlockPos pos = session.player().blockPosition();
        if (session.pick() == Pick.STEP && step != null && Action.of(step).usesBlock()) {
            if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                message(tr("aim_block"), true);
                return;
            }
            pos = hit.getBlockPos();
            if (Action.of(step) == Action.OPEN_PYLON && !session.level().getBlockState(pos).is(CreateCityExpansionCore.CITY_PYLON.get())) {
                message(tr("aim_pylon"), true);
                return;
            }
        }
        Point point;
        try {
            point = new Point(session.level().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        } catch (IllegalArgumentException invalid) {
            message(JourneyClient.tr("error.location"), true);
            return;
        }
        if (session.pick() == Pick.START) {
            draft.put("Start", JourneyCodec.point(point));
        } else if (step == null) {
            return;
        } else if (session.pick() == Pick.WAYPOINT) {
            if (!point.dimension().equals(step.getString("Dimension")) || totalWaypoints() >= MAX_WAYPOINTS) {
                message(tr("waypoint_dimension"), true);
                return;
            }
            var path = step.getList("Route", Tag.TAG_COMPOUND);
            path.add(JourneyCodec.point(point));
            step.put("Route", path);
        } else if (!setLocation(step, point)) {
            return;
        }
        changed();
        message(tr("location_saved"), false);
        if (point.dimension().equals(map.dimensionId().toString())) map.restoreView(point.x() + .5, point.z() + .5, map.zoom());
        placement = null;
        suspended = false;
        minecraft.setScreen(this);
    }

    private boolean setLocation(CompoundTag step, Point point) {
        var path = step.getList("Route", Tag.TAG_COMPOUND);
        if (!path.isEmpty() && !point.dimension().equals(path.getCompound(0).getString("Dimension"))) {
            message(tr("waypoint_dimension"), true);
            return false;
        }
        step.merge(JourneyCodec.point(point));
        return true;
    }

    private int placementHeight(CompoundTag step) {
        var point = point(step);
        if (point != null && point.dimension().equals(map.dimensionId().toString())) return point.y();
        return minecraft.player == null ? 0 : minecraft.player.blockPosition().getY();
    }

    private static Point point(CompoundTag value) {
        if (!value.contains("Dimension", Tag.TAG_STRING) || value.getString("Dimension").isEmpty()) return null;
        try {
            return JourneyCodec.point(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean valid(Placement session, Minecraft minecraft) {
        return minecraft.level == session.level() && minecraft.player == session.player() && session.player().isAlive();
    }

    @SubscribeEvent
    public static void placementKey(InputEvent.Key event) {
        var session = placement;
        Minecraft minecraft = Minecraft.getInstance();
        if (session != null && valid(session, minecraft) && minecraft.screen == null
                && event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_ENTER) {
            session.editor().capture(session);
        }
    }

    @SubscribeEvent
    public static void cancelPlacement(ScreenEvent.Opening event) {
        var session = placement;
        if (session == null || !(event.getNewScreen() instanceof PauseScreen) || !valid(session, Minecraft.getInstance())) return;
        placement = null;
        var editor = session.editor();
        editor.suspended = false;
        editor.message(null, false);
        event.setNewScreen(editor);
    }

    @SubscribeEvent
    public static void placementTick(ClientTickEvent.Post event) {
        var session = placement;
        if (session != null && !valid(session, Minecraft.getInstance())) dropPlacement();
    }

    @SubscribeEvent
    public static void placementLogout(ClientPlayerNetworkEvent.LoggingOut event) { dropPlacement(); }

    private static void dropPlacement() {
        var session = placement;
        if (session == null) return;
        placement = null;
        session.editor().abandon();
    }

    @SubscribeEvent
    public static void placementHud(RenderGuiEvent.Post event) {
        var session = placement;
        Minecraft minecraft = Minecraft.getInstance();
        if (session == null || minecraft.screen != null || minecraft.options.hideGui || !valid(session, minecraft)) return;
        var editor = session.editor();
        var g = event.getGuiGraphics();
        var font = minecraft.font;
        var step = editor.step();
        boolean block = session.pick() == Pick.STEP && step != null && Action.of(step).usesBlock();
        Component instruction = editor.statusError && editor.status != null ? editor.status : tr(block ? "capture_block" : "capture_position");
        int w = Math.min(g.guiWidth() - 16, Math.max(185, font.width(instruction) + 24));
        int x = (g.guiWidth() - w) / 2;
        JourneyAtlasStyle.panel(g, x, 10, w, 29);
        g.drawCenteredString(font, font.plainSubstrByWidth(instruction.getString(), w - 16), g.guiWidth() / 2, 15, editor.statusError ? ERROR : TEXT);
        g.drawCenteredString(font, tr("capture_cancel"), g.guiWidth() / 2, 26, MUTED);
    }

    @Override
    public void tick() {
        parent.tick();
        if (!pending.isEmpty() && Util.getMillis() - pendingAt > 10000L) {
            pending = "";
            reloadNeeded = true;
            message(tr("timeout"), true);
            rebuildWidgets();
        }
    }

    private int listRows() { return Math.max(1, (bodyBottom - BODY_TOP - 33) / CARD_HEIGHT); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BACKGROUND);
        g.fill(0, 0, width, BODY_TOP - 3, SURFACE);
        g.fill(0, BODY_TOP - 4, width, BODY_TOP - 3, BRASS);
        g.drawString(font, tr("title"), 86, 12, BRASS, false);
        if (loaded) {
            if (wide || compactMap) renderMap(g, mx, my);
            if (wide || !compactMap) renderSteps(g);
            if (wide || !compactMap) {
                JourneyAtlasStyle.panel(g, formLeft, BODY_TOP, width - formLeft, bodyBottom - BODY_TOP);
                if (step() == null) g.drawWordWrap(font, tr("empty"), formLeft + 12, BODY_TOP + 12, width - formLeft - 24, MUTED);
                for (Label label : labels) {
                    g.drawString(font, font.plainSubstrByWidth(label.text().getString(), width - label.x() - 8), label.x(), label.y(), MUTED, false);
                }
                if (step() != null && formHeight > bodyBottom - BODY_TOP - 12) {
                    int track = bodyBottom - BODY_TOP - 12;
                    int thumb = Math.max(9, track * track / formHeight);
                    int offset = (track - thumb) * formScroll / Math.max(1, formHeight - track);
                    g.fill(width - 4, BODY_TOP + 5, width - 2, bodyBottom - 6, EDGE);
                    g.fill(width - 4, BODY_TOP + 5 + offset, width - 2, BODY_TOP + 5 + offset + thumb, BRASS);
                }
            }
            Component current = status == null ? tr(dirty() ? "unsaved" : "draft_saved") : status;
            int max = width - (reloadNeeded && pending.isEmpty() ? 100 : 20);
            var lines = font.split(current, max);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.drawString(font, lines.get(i), 10, height - 50 + 10 * i, statusError ? ERROR : MUTED, false);
            }
        } else if (status != null) {
            g.drawWordWrap(font, status, 10, BODY_TOP + 5, width - 20, statusError ? ERROR : MUTED);
        }
        if (dialog != Dialog.NONE) renderDialog(g, mx, my);
        super.render(g, mx, my, delta);
    }

    private void renderSteps(GuiGraphics g) {
        JourneyAtlasStyle.panel(g, 0, BODY_TOP, listWidth, bodyBottom - BODY_TOP);
        ListTag steps = steps();
        int rows = listRows();
        listOffset = Math.clamp(listOffset, 0, Math.max(0, steps.size() - rows));
        for (int index = listOffset; index < Math.min(steps.size(), listOffset + rows); index++) {
            var step = steps.getCompound(index);
            int y = BODY_TOP + 5 + (index - listOffset) * CARD_HEIGHT;
            g.fill(5, y, listWidth - 5, y + 29, index == selected ? SELECTED : RAISED);
            if (index == selected || index == stepError) g.fill(5, y, 7, y + 29, index == stepError ? ERROR : BRASS);
            g.drawString(font, Integer.toString(index + 1), 11, y + 5, BRASS, false);
            String name = step.getString("Title");
            if (name.isBlank()) name = tr("new_step").getString();
            g.drawString(font, font.plainSubstrByWidth(name, listWidth - 39), 29, y + 5, TEXT, false);
            Component caption = point(step) == null ? tr("no_location") : Action.of(step).label();
            g.drawString(font, font.plainSubstrByWidth(caption.getString(), listWidth - 39), 29, y + 17, MUTED, false);
        }
    }

    private void renderMap(GuiGraphics g, int mx, int my) {
        map.render(g, mapLeft, BODY_TOP, mapWidth, bodyBottom - BODY_TOP, mx, my);
        g.enableScissor(mapLeft, BODY_TOP, mapLeft + mapWidth, bodyBottom);
        var steps = steps();
        String dimension = map.dimensionId().toString();
        Point start = point(draft.getCompound("Start"));
        if (start != null && start.dimension().equals(dimension)) {
            int x = map.worldToScreenX(start.x() + .5);
            int y = map.worldToScreenY(start.z() + .5);
            if (visible(x, y)) {
                JourneyAtlasStyle.diamond(g, x, y, 6, 0xff8ac1aa);
                g.drawString(font, tr("start_label"), x + 11, y - 4, TEXT, true);
            }
        }
        for (int index = 0; index < steps.size(); index++) {
            var step = steps.getCompound(index);
            Point end = point(step);
            if (end == null || !end.dimension().equals(dimension)) continue;
            boolean active = index == selected;
            var path = step.getList("Route", Tag.TAG_COMPOUND);
            Point previous = null;
            for (Tag tag : path) {
                Point waypoint = point((CompoundTag) tag);
                if (waypoint == null || !waypoint.dimension().equals(dimension)) continue;
                if (previous != null) JourneyMapLayer.line(g, map, previous, waypoint, active ? BRASS : 0x806f6655);
                if (active) {
                    int x = map.worldToScreenX(waypoint.x() + .5);
                    int y = map.worldToScreenY(waypoint.z() + .5);
                    if (visible(x, y)) JourneyAtlasStyle.diamond(g, x, y, 2, BRASS);
                }
                previous = waypoint;
            }
            if (previous != null) JourneyMapLayer.line(g, map, previous, end, active ? BRASS : 0x806f6655);
        }
        for (int index = 0; index < steps.size(); index++) {
            var step = steps.getCompound(index);
            Point point = point(step);
            if (point == null || !point.dimension().equals(dimension)) continue;
            int x = map.worldToScreenX(point.x() + .5);
            int y = map.worldToScreenY(point.z() + .5);
            if (!visible(x, y)) continue;
            boolean active = index == selected;
            if (active) {
                double radius = Math.clamp(step.getInt("Radius") * map.zoom(), 0, 800);
                if (radius > 7) JourneyMapLayer.circle(g, x, y, radius, 0x90d0ad73);
            }
            var marker = new Marker("preview_" + index, point);
            marker.icon = Action.of(step).icon;
            marker.tint = active ? BRASS : 0x99a7a5;
            JourneyAtlasStyle.mark(g, x, y, marker, active, false, Util.getMillis(), true, false);
            g.drawString(font, Integer.toString(index + 1), x + 12, y - 4, active ? BRASS : TEXT, true);
        }
        g.disableScissor();
    }

    private boolean visible(int x, int y) {
        return x >= mapLeft + 2 && x < mapLeft + mapWidth - 2 && y >= BODY_TOP + 2 && y < bodyBottom - 2;
    }

    private void renderDialog(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, height, 0xb010171b);
        int w = dialogWidth();
        int x = (width - w) / 2;
        if (dialog == Dialog.ITEM) {
            JourneyAtlasStyle.panel(g, x, 24, w, height - 46);
            g.drawString(font, tr("choose_item"), x + 12, 31, BRASS, false);
            int rows = itemRows();
            itemOffset = Math.clamp(itemOffset, 0, Math.max(0, filteredItems.size() - rows));
            for (int i = itemOffset; i < Math.min(filteredItems.size(), itemOffset + rows); i++) {
                var item = filteredItems.get(i);
                int y = 70 + (i - itemOffset) * 20;
                boolean hovered = mx >= x + 12 && mx < x + w - 12 && my >= y && my < y + 19;
                g.fill(x + 12, y, x + w - 12, y + 19, hovered || i == itemSelection ? SELECTED : RAISED);
                g.renderItem(new ItemStack(item.item()), x + 15, y + 1);
                g.drawString(font, font.plainSubstrByWidth(item.name().getString(), w - 53), x + 36, y + 6, TEXT, false);
            }
            if (filteredItems.isEmpty()) g.drawString(font, tr("no_items"), x + 12, 75, MUTED, false);
        } else if (dialog == Dialog.ADD || dialog == Dialog.ACTION) {
            JourneyAtlasStyle.panel(g, x, height / 2 - 72, w, 151);
            g.drawString(font, tr("choose_action"), x + 12, height / 2 - 65, BRASS, false);
        } else if (dialog == Dialog.TEST) {
            JourneyAtlasStyle.panel(g, x, height / 2 - 43, w, 108);
            g.drawWordWrap(font, tr("choose_test"), x + 12, height / 2 - 33, w - 24, TEXT);
        } else {
            JourneyAtlasStyle.panel(g, x, height / 2 - 45, w, 96);
            String key = switch (dialog) {
                case EXIT -> "exit_confirm";
                case RELOAD -> "reload_confirm";
                case PUBLISH -> "publish_confirm";
                default -> throw new IllegalStateException();
            };
            g.drawWordWrap(font, tr(key), x + 12, height / 2 - 32, w - 24, TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0 || !pending.isEmpty()) return false;
        if (dialog == Dialog.ITEM) {
            int left = (width - dialogWidth()) / 2;
            if (x >= left + 12 && x < left + dialogWidth() - 12 && y >= 70 && y < 70 + itemRows() * 20) {
                chooseItem(itemOffset + ((int) y - 70) / 20);
            }
            return true;
        }
        if (dialog != Dialog.NONE || !loaded) return false;
        if ((wide || !compactMap) && x < listWidth && y >= BODY_TOP + 5 && y < BODY_TOP + 5 + listRows() * CARD_HEIGHT) {
            int index = listOffset + ((int) y - BODY_TOP - 5) / CARD_HEIGHT;
            if (index < steps().size()) {
                select(index);
                dragIndex = index;
            }
            return true;
        }
        if ((wide || compactMap) && x >= mapLeft && x < mapLeft + mapWidth && y >= BODY_TOP && y < bodyBottom) {
            if (pickMap && step() != null) {
                var step = step();
                Point point;
                try {
                    point = new Point(map.dimensionId().toString(), (int) Math.floor(map.screenToWorldX(x)),
                            placementHeight(step), (int) Math.floor(map.screenToWorldZ(y)));
                } catch (IllegalArgumentException invalid) {
                    message(JourneyClient.tr("error.location"), true);
                    return true;
                }
                if (setLocation(step, point)) {
                    pickMap = false;
                    compactMap = false;
                    changed();
                    message(tr("location_saved"), false);
                    rebuildWidgets();
                }
            } else {
                int hit = stepAt(x, y);
                if (hit >= 0) {
                    compactMap = false;
                    select(hit);
                    return true;
                }
                draggingMap = true;
            }
            return true;
        }
        return false;
    }

    private int stepAt(double x, double y) {
        var steps = steps();
        for (int index = steps.size() - 1; index >= 0; index--) {
            Point point = point(steps.getCompound(index));
            if (point == null || !point.dimension().equals(map.dimensionId().toString())) continue;
            int sx = map.worldToScreenX(point.x() + .5);
            int sy = map.worldToScreenY(point.z() + .5);
            if (Math.abs(x - sx) + Math.abs(y - sy) <= 12) return index;
        }
        return -1;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragIndex >= 0) return true;
        if (draggingMap && button == 0) {
            map.restoreView(map.centerX() - dx / map.zoom(), map.centerZ() - dy / map.zoom(), map.zoom());
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        draggingMap = false;
        if (dragIndex >= 0) {
            int from = dragIndex;
            dragIndex = -1;
            if (x < listWidth && y >= BODY_TOP + 5 && y < BODY_TOP + 5 + listRows() * CARD_HEIGHT) {
                move(from, listOffset + ((int) y - BODY_TOP - 5) / CARD_HEIGHT);
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dialog == Dialog.ITEM) {
            itemOffset = Math.clamp(itemOffset - (int) dy, 0, Math.max(0, filteredItems.size() - itemRows()));
            return true;
        }
        if (dialog != Dialog.NONE || !loaded || !pending.isEmpty()) return true;
        if ((wide || !compactMap) && x < listWidth) {
            listOffset = Math.clamp(listOffset - (int) dy, 0, Math.max(0, steps().size() - listRows()));
            return true;
        }
        if ((wide || !compactMap) && x >= formLeft) {
            for (var child : children()) {
                if (child instanceof MultiLineEditBox box && box.isMouseOver(x, y) && box.mouseScrolled(x, y, dx, dy)) return true;
            }
            formScroll = Math.clamp(formScroll - (int) (dy * 24), 0, Math.max(0, formHeight - (bodyBottom - BODY_TOP - 12)));
            rebuildWidgets();
            return true;
        }
        return map.mouseScrolled(x, y, dy);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (dialog == Dialog.ITEM) {
            if (key == GLFW.GLFW_KEY_ENTER && !filteredItems.isEmpty()) {
                chooseItem(itemSelection);
                return true;
            }
            if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && !filteredItems.isEmpty()) {
                itemSelection = Math.clamp(itemSelection + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, filteredItems.size() - 1);
                itemOffset = Math.clamp(itemOffset, Math.max(0, itemSelection - itemRows() + 1), itemSelection);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && dialog != Dialog.NONE) {
            show(Dialog.NONE);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && pickMap) {
            pickMap = false;
            compactMap = false;
            message(null, false);
            rebuildWidgets();
            return true;
        }
        if (key == GLFW.GLFW_KEY_S && hasControlDown() && dialog == Dialog.NONE && loaded && dirty()) {
            send("save");
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!pending.isEmpty() && !pending.equals("load")) return;
        if (dirty()) show(Dialog.EXIT);
        else returnToAtlas();
    }

    private void returnToAtlas() {
        returning = true;
        minecraft.setScreen(parent);
    }

    private void abandon() {
        suspended = false;
        parent.finishRouteEditor(false);
    }

    @Override
    public void removed() {
        if (!suspended) parent.finishRouteEditor(returning);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
