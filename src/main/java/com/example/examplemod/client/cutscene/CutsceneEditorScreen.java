package com.example.examplemod.client.cutscene;

import com.example.examplemod.cutscene.Cutscene;
import com.example.examplemod.cutscene.CutsceneJson;
import com.example.examplemod.cutscene.CutsceneNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CutsceneEditorScreen extends Screen {
    static final int BACKGROUND = 0xF5121822;
    static final int PANEL = 0xFF1C2532;
    static final int BORDER = 0xFF354355;
    static final int INK = 0xFFF2F5F8;
    static final int MUTED = 0xFFACBACB;
    static final int ACCENT = 0xFFF3CA87;
    static final int ERROR = 0xFFFFA3A2;
    private static final int MARGIN = 10;
    private static final int BUTTON_HEIGHT = 18;

    enum Track {
        CAMERA("Камера", 0xFF85DDD5),
        SOUND("Звук", 0xFFF2BB80),
        TEXT("Текст", 0xFFCEA9ED);

        final String label;
        final int color;

        Track(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private static final Track[] TRACKS = Track.values();

    private final String id;
    private Cutscene scene;
    private Cutscene saved;
    private Cutscene pending;
    private String revision;
    private Track track = Track.CAMERA;
    private int selected = -1;
    private int playhead;
    private String durationDraft;
    private String tickDraft = "0";
    private String status;
    private boolean statusError;
    private boolean dragging;
    private EditBox durationBox;
    private EditBox tickBox;
    private Button saveButton;
    private Button closeButton;
    private Button previewButton;
    private Button addButton;
    private Button editButton;
    private Button deleteButton;
    private Button previousButton;
    private Button nextButton;
    private boolean narrow;
    private int timelineY;
    private int rulerHeight;
    private int timelineLeft;
    private int timelineRight;
    private int rowHeight;
    private int timelineBottom;

    public CutsceneEditorScreen(String id, Cutscene cutscene, boolean exists, String revision) {
        super(Component.literal("Редактор кат-сцен"));
        CutsceneCueScreen.cancelPlacement();
        this.id = id;
        this.scene = cutscene;
        this.saved = cutscene;
        this.revision = revision;
        durationDraft = Integer.toString(cutscene.durationTicks());
        status = exists ? "Сцена загружена с сервера" : "Выберите время и добавьте первый ключ";
    }

    @Override
    protected void init() {
        dragging = false;
        narrow = width < 316;
        boolean compact = height < 213;
        timelineY = compact ? 62 : 72;
        rulerHeight = compact ? 16 : 19;
        timelineLeft = MARGIN + (narrow ? 50 : 65);
        timelineRight = Math.max(timelineLeft + 1, width - MARGIN - 10);
        rowHeight = Mth.clamp((height - timelineY - rulerHeight - 59) / 3, 12, compact ? 16 : 34);
        timelineBottom = timelineY + rulerHeight + rowHeight * TRACKS.length;

        int right = width - MARGIN;
        int toolbarY = compact ? 37 : 42;
        durationBox = input("Длина сцены в тиках", MARGIN + 50, toolbarY, narrow ? 35 : 45, durationDraft);
        durationBox.setMaxLength(5);
        durationBox.setTooltip(Tooltip.create(Component.literal("20 тиков = 1 секунда")));
        durationBox.setResponder(this::changeDuration);
        closeButton = addButton(narrow ? "×" : "Закрыть", right - (narrow ? 18 : 52), toolbarY, narrow ? 18 : 52, false, this::onClose);
        closeButton.setTooltip(Tooltip.create(Component.literal("Закрыть редактор")));
        saveButton = addButton(narrow ? "Сохр." : "Сохранить", right - (narrow ? 66 : 120), toolbarY, narrow ? 44 : 64, true, this::save);
        saveButton.setTooltip(Tooltip.create(Component.literal("Записать JSON на сервере · Ctrl+S")));
        Button copy = addButton("JSON", right - (narrow ? 104 : 166), toolbarY, narrow ? 34 : 42, false, this::copyJson);
        copy.setTooltip(Tooltip.create(Component.literal("Скопировать JSON сцены")));

        int y = timelineBottom + 7;
        tickBox = input("Позиция на шкале в тиках", MARGIN + (narrow ? 12 : 42), y, narrow ? 30 : 40, tickDraft);
        tickBox.setMaxLength(5);
        tickBox.setResponder(this::changeTick);
        previousButton = addButton("‹", MARGIN + (narrow ? 46 : 88), y, 16, false, () -> selectAdjacent(-1));
        previousButton.setTooltip(Tooltip.create(Component.literal("Предыдущий ключ дорожки")));
        nextButton = addButton("›", MARGIN + (narrow ? 64 : 107), y, 16, false, () -> selectAdjacent(1));
        nextButton.setTooltip(Tooltip.create(Component.literal("Следующий ключ дорожки")));
        addButton = addButton(narrow ? "+" : "+ Ключ", MARGIN + (narrow ? 84 : 127), y, narrow ? 16 : 48, false, () -> openCue(-1));
        addButton.setTooltip(Tooltip.create(Component.literal("Добавить ключ на выбранную дорожку")));
        editButton = addButton(narrow ? "✎" : "Правка", MARGIN + (narrow ? 104 : 179), y, narrow ? 16 : 45, false, () -> openCue(selected));
        editButton.setTooltip(Tooltip.create(Component.literal("Редактировать выбранный ключ")));
        deleteButton = addButton("×", MARGIN + (narrow ? 124 : 228), y, 18, false, this::deleteCue);
        deleteButton.setTooltip(Tooltip.create(Component.literal("Удалить выбранный ключ")));
        previewButton = addButton(narrow ? "Пуск" : "▶ Пуск", right - (narrow ? 41 : 49), y, narrow ? 41 : 49, false, this::preview);
        previewButton.setTooltip(Tooltip.create(Component.literal("Предпросмотр от метки · пробел. Выход — Esc")));
        refreshControls();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(MARGIN, 10, MARGIN + 3, 33, ACCENT);
        graphics.drawString(font, title, MARGIN + 9, 11, INK, false);
        String dimension = clip(font, scene.dimension().toString(), Math.max(0, width / 2 - 18));
        int dimensionX = width - MARGIN - font.width(dimension);
        String sceneLabel = (dirty() ? "* " : "") + id;
        graphics.drawString(font, clip(font, sceneLabel, dimensionX - MARGIN - 16), MARGIN + 9, 24, MUTED, false);
        graphics.drawString(font, dimension, dimensionX, 24, MUTED, false);
        graphics.drawString(font, "Длина, т", MARGIN, durationBox.getY() + 5, MUTED, false);

        renderTimeline(graphics, mouseX, mouseY);
        graphics.drawString(font, narrow ? "т" : "Метка, т", MARGIN, timelineBottom + 12, MUTED, false);
        renderInspector(graphics);
        graphics.fill(MARGIN, height - 20, width - MARGIN, height - 19, BORDER);
        String footer = durationValid() ? status : "Длина от " + minimumDuration() + " до " + Cutscene.MAX_DURATION_TICKS + " тиков";
        graphics.drawString(font, clip(font, footer, width - 2 * MARGIN), MARGIN, height - 14,
                statusError || !durationValid() ? ERROR : MUTED, false);
        if (font.width(footer) > width - 2 * MARGIN && mouseY >= height - 19 && mouseX >= MARGIN) {
            setTooltipForNextRenderPass(Component.literal(footer));
        }
    }

    private void renderTimeline(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(MARGIN, timelineY, width - MARGIN, timelineBottom + 1, BORDER);
        graphics.fill(MARGIN + 1, timelineY + 1, width - MARGIN - 1, timelineBottom, PANEL);
        graphics.fill(timelineLeft - 6, timelineY + 1, timelineLeft - 5, timelineBottom, BORDER);
        int subdivisions = Mth.clamp((timelineRight - timelineLeft) / 44, 2, 8);
        for (int division = 0; division <= subdivisions; division++) {
            int tick = (int) ((long) scene.durationTicks() * division / subdivisions);
            int x = tickX(tick);
            String label = seconds(tick) + "с";
            int labelX = Mth.clamp(x - font.width(label) / 2, timelineLeft, width - MARGIN - 2 - font.width(label));
            if (division == 0 || tick > (long) scene.durationTicks() * (division - 1) / subdivisions) {
                graphics.fill(x, timelineY + rulerHeight - 4, x + 1, timelineBottom, BORDER);
                graphics.drawString(font, label, labelX, timelineY + 3, MUTED, false);
            }
        }

        for (Track lane : TRACKS) {
            int y = rowY(lane);
            boolean highlighted = track == lane;
            graphics.fill(MARGIN + 1, y, width - MARGIN - 1, y + 1, BORDER);
            if (highlighted) {
                graphics.fill(MARGIN + 1, y + 1, timelineLeft - 6, y + rowHeight, 0xFF2B394B);
                graphics.fill(MARGIN + 1, y + 1, MARGIN + 3, y + rowHeight, lane.color);
            }
            graphics.drawString(font, lane.label, MARGIN + 7, y + (rowHeight - 8) / 2, lane.color, false);
            int hovered = mouseY >= y && mouseY < y + rowHeight && mouseX >= timelineLeft - 4 && mouseX <= timelineRight + 4
                    ? cueAt(lane, mouseX) : -1;
            for (int i = 0; i < cueCount(lane); i++) {
                int marker = tickX(cueTick(lane, i));
                boolean current = highlighted && i == selected;
                renderCue(graphics, lane, i, marker, y, current || i == hovered);
            }
            if (highlighted && selected >= 0) {
                renderCue(graphics, lane, selected, tickX(cueTick(lane, selected)), y, true);
            }
        }

        int playheadX = tickX(playhead);
        int rulerY = timelineY + rulerHeight - 4;
        graphics.fill(playheadX, rulerY, playheadX + 1, timelineBottom, ACCENT);
        graphics.fill(playheadX - 2, rulerY, playheadX + 3, rulerY + 3, ACCENT);
    }

    private void renderCue(GuiGraphics graphics, Track lane, int index, int x, int y, boolean emphasized) {
        int mid = y + rowHeight / 2;
        int color = emphasized ? INK : lane.color;
        if (lane == Track.TEXT) {
            int end = tickX(cueEnd(lane, index));
            graphics.fill(x, mid - 4, Math.max(x + 3, end), mid + 5, emphasized ? 0xFF8972A7 : 0xFF614D7D);
            graphics.fill(x, mid - 5, x + 2, mid + 6, color);
        } else if (lane == Track.CAMERA) {
            graphics.fill(x - 1, mid - 5, x + 2, mid + 6, color);
            graphics.fill(x - 3, mid - 3, x + 4, mid + 4, color);
            graphics.fill(x - 4, mid - 1, x + 5, mid + 2, color);
        } else {
            graphics.fill(x - 2, mid - 5, x + 3, mid + 6, color);
            graphics.fill(x + 4, mid - 2, x + 6, mid + 3, color);
        }
    }

    private void renderInspector(GuiGraphics graphics) {
        int y = timelineBottom + 29;
        int count = cueCount(track);
        String label = track.label + " · " + (selected < 0 ? count : (selected + 1) + " / " + count);
        graphics.drawString(font, label, MARGIN, y, track.color, false);
        int x = MARGIN + font.width(label) + 9;
        String details = selected < 0 ? "Щёлкните по ключу или добавьте новый" : switch (track) {
            case CAMERA -> {
                var key = scene.camera().get(selected);
                yield String.format(Locale.ROOT, "%.1f  %.1f  %.1f · %s", key.x(), key.y(), key.z(), CutsceneCueScreen.easingName(key.easing()));
            }
            case SOUND -> scene.sounds().get(selected).sound().toString();
            case TEXT -> scene.texts().get(selected).text().replace('\n', ' ');
        };
        graphics.drawString(font, clip(font, details, width - MARGIN - x), x, y, MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= MARGIN && mouseX < width - MARGIN
                && mouseY >= timelineY && mouseY < timelineBottom) {
            clearFocus();
            if (mouseY >= timelineY + rulerHeight) {
                track = TRACKS[(int) (mouseY - timelineY - rulerHeight) / rowHeight];
                selected = mouseX >= timelineLeft - 4 ? cueAt(track, mouseX) : -1;
            }
            if (selected >= 0 && mouseY >= timelineY + rulerHeight) {
                setPlayhead(cueTick(track, selected));
            } else if (mouseX >= timelineLeft - 4) {
                dragging = true;
                movePlayhead(mouseX);
            }
            refreshControls();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            movePlayhead(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && hasControlDown()) {
            save();
            return true;
        }
        if (getFocused() == null && key == GLFW.GLFW_KEY_SPACE) {
            preview();
            return true;
        }
        if (getFocused() == null && key == GLFW.GLFW_KEY_DELETE) {
            deleteCue();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (pending != null) {
            return;
        }
        if (dirty()) {
            minecraft.setScreen(new ConfirmScreen(discard -> minecraft.setScreen(discard ? null : this),
                    Component.literal("Закрыть редактор?"), Component.literal("Несохранённые изменения будут потеряны.")));
        } else {
            super.onClose();
        }
    }

    public void onSaveResult(boolean success, String message, String revision) {
        if (pending == null) {
            return;
        }
        if (success) {
            saved = pending;
            this.revision = revision;
        }
        pending = null;
        status(message, !success);
        refreshControls();
    }

    public String sceneId() {
        return id;
    }

    Cutscene scene() {
        return scene;
    }

    void applyCamera(int index, Cutscene.CameraKeyframe cue) {
        for (int i = 0; i < scene.camera().size(); i++) {
            if (i != index && scene.camera().get(i).tick() == cue.tick()) {
                throw new IllegalArgumentException("На этой метке уже есть ключ камеры");
            }
        }
        Cutscene updated = new Cutscene(scene.version(), scene.dimension(), scene.durationTicks(),
                replace(scene.camera(), index, cue), scene.sounds(), scene.texts());
        apply(updated, updated.camera().indexOf(cue), cue.tick());
    }

    void applySound(int index, Cutscene.SoundCue cue) {
        Cutscene updated = new Cutscene(scene.version(), scene.dimension(), scene.durationTicks(),
                scene.camera(), replace(scene.sounds(), index, cue), scene.texts());
        apply(updated, updated.sounds().indexOf(cue), cue.tick());
    }

    void applyText(int index, Cutscene.TextCue cue) {
        Cutscene updated = new Cutscene(scene.version(), scene.dimension(), scene.durationTicks(),
                scene.camera(), scene.sounds(), replace(scene.texts(), index, cue));
        apply(updated, updated.texts().indexOf(cue), cue.tick());
    }

    private void apply(Cutscene updated, int index, int tick) {
        scene = updated;
        selected = index;
        setPlayhead(tick);
        status("Ключ обновлён", false);
        minecraft.setScreen(this);
    }

    private void openCue(int index) {
        if (pending == null && validateInput(index < 0)) {
            minecraft.setScreen(new CutsceneCueScreen(this, track, index, playhead));
        }
    }

    private void deleteCue() {
        if (selected < 0 || pending != null) {
            return;
        }
        List<Cutscene.CameraKeyframe> camera = track == Track.CAMERA ? replace(scene.camera(), selected, null) : scene.camera();
        List<Cutscene.SoundCue> sounds = track == Track.SOUND ? replace(scene.sounds(), selected, null) : scene.sounds();
        List<Cutscene.TextCue> texts = track == Track.TEXT ? replace(scene.texts(), selected, null) : scene.texts();
        scene = new Cutscene(scene.version(), scene.dimension(), scene.durationTicks(), camera, sounds, texts);
        selected = Math.min(selected, cueCount(track) - 1);
        if (selected >= 0) {
            setPlayhead(cueTick(track, selected));
        }
        changeDuration(durationDraft);
        status("Ключ удалён", false);
        refreshControls();
    }

    private void selectAdjacent(int direction) {
        if (cueCount(track) > 0) {
            int start = selected < 0 && direction < 0 ? 0 : selected;
            selected = Math.floorMod(start + direction, cueCount(track));
            setPlayhead(cueTick(track, selected));
            refreshControls();
        }
    }

    private void save() {
        if (pending != null || !validateInput(false) || !hasCues()) {
            return;
        }
        pending = scene;
        status("Сохранение на сервере…", false);
        refreshControls();
        try {
            CutsceneNetwork.save(id, scene, revision);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            onSaveResult(false, ex.getMessage(), revision);
        }
    }

    private void copyJson() {
        if (!validateInput(false)) {
            return;
        }
        try {
            minecraft.keyboardHandler.setClipboard(CutsceneJson.encode(scene));
            status("JSON скопирован в буфер обмена", false);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            status(ex.getMessage(), true);
        }
    }

    private void preview() {
        if (pending != null || !validateInput(true) || !hasCues()) {
            return;
        }
        try {
            CutscenePlayback.start(scene, Math.min(playhead, scene.durationTicks() - 1), () -> minecraft.setScreen(this));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            status(ex.getMessage(), true);
        }
    }

    private boolean hasCues() {
        if (scene.camera().isEmpty() && scene.sounds().isEmpty() && scene.texts().isEmpty()) {
            status("Добавьте хотя бы один ключ", true);
            return false;
        }
        return true;
    }

    private boolean validateInput(boolean usePlayhead) {
        if (!durationValid()) {
            status("Проверьте длину сцены", true);
            return false;
        }
        if (usePlayhead && !tickValid()) {
            status("Введите метку от 0 до " + scene.durationTicks() + " тиков", true);
            return false;
        }
        return true;
    }

    private void changeDuration(String value) {
        durationDraft = value;
        int duration = parseTick(value);
        if (duration >= minimumDuration() && duration <= Cutscene.MAX_DURATION_TICKS) {
            if (duration != scene.durationTicks()) {
                scene = new Cutscene(scene.version(), scene.dimension(), duration, scene.camera(), scene.sounds(), scene.texts());
                if (playhead > duration) {
                    setPlayhead(duration);
                }
            }
        }
        refreshControls();
    }

    private void changeTick(String value) {
        tickDraft = value;
        if (tickValid()) {
            playhead = parseTick(value);
        }
        refreshControls();
    }

    private void setPlayhead(int tick) {
        playhead = tick;
        tickDraft = Integer.toString(tick);
        if (tickBox != null) {
            tickBox.setValue(tickDraft);
        }
    }

    private void movePlayhead(double mouseX) {
        double fraction = (mouseX - timelineLeft) / (timelineRight - timelineLeft);
        setPlayhead((int) Math.round(Mth.clamp(fraction, 0, 1) * scene.durationTicks()));
    }

    private boolean durationValid() {
        return parseTick(durationDraft) == scene.durationTicks();
    }

    private boolean tickValid() {
        int value = parseTick(tickDraft);
        return value >= 0 && value <= scene.durationTicks();
    }

    private boolean dirty() {
        return !scene.equals(saved) || !durationValid();
    }

    private int minimumDuration() {
        int minimum = 1;
        for (var cue : scene.camera()) {
            minimum = Math.max(minimum, cue.tick());
        }
        for (var cue : scene.sounds()) {
            minimum = Math.max(minimum, cue.tick() + 1);
        }
        for (var cue : scene.texts()) {
            minimum = Math.max(minimum, cue.tick() + cue.durationTicks());
        }
        return minimum;
    }

    private int cueCount(Track lane) {
        return switch (lane) {
            case CAMERA -> scene.camera().size();
            case SOUND -> scene.sounds().size();
            case TEXT -> scene.texts().size();
        };
    }

    private int cueTick(Track lane, int index) {
        return switch (lane) {
            case CAMERA -> scene.camera().get(index).tick();
            case SOUND -> scene.sounds().get(index).tick();
            case TEXT -> scene.texts().get(index).tick();
        };
    }

    private int cueEnd(Track lane, int index) {
        if (lane == Track.TEXT) {
            var cue = scene.texts().get(index);
            return cue.tick() + cue.durationTicks();
        }
        return cueTick(lane, index);
    }

    private int cueAt(Track lane, double mouseX) {
        int best = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < cueCount(lane); i++) {
            int start = tickX(cueTick(lane, i));
            int end = tickX(cueEnd(lane, i));
            double next = Math.abs(mouseX - start);
            if (next < distance && (next <= 5 || (mouseX > start && mouseX <= end))) {
                best = i;
                distance = next;
            }
        }
        return best;
    }

    private int tickX(int tick) {
        return timelineLeft + Math.round((float) tick / scene.durationTicks() * (timelineRight - timelineLeft));
    }

    private int rowY(Track lane) {
        return timelineY + rulerHeight + rowHeight * lane.ordinal();
    }

    private void refreshControls() {
        if (previewButton == null) {
            return;
        }
        boolean available = pending == null;
        closeButton.active = available;
        saveButton.active = available;
        previewButton.active = available;
        addButton.active = available && cueCount(track) < Cutscene.MAX_TRACK_ITEMS;
        editButton.active = available && selected >= 0;
        deleteButton.active = editButton.active;
        previousButton.active = cueCount(track) > 0;
        nextButton.active = previousButton.active;
        durationBox.setTextColor(durationValid() ? INK : ERROR);
        tickBox.setTextColor(tickValid() ? INK : ERROR);
    }

    private void status(String message, boolean error) {
        status = message == null ? "Ошибка операции" : message;
        statusError = error;
    }

    private EditBox input(String label, int x, int y, int width, String value) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, width, BUTTON_HEIGHT, Component.literal(label)));
        box.setValue(value);
        return box;
    }

    private Button addButton(String label, int x, int y, int width, boolean primary, Runnable onPress) {
        return addRenderableWidget(button(label, x, y, width, primary, onPress));
    }

    static Button button(String label, int x, int y, int width, boolean primary, Runnable onPress) {
        return button(label, x, y, width, primary, button -> onPress.run());
    }

    static Button button(String label, int x, int y, int width, boolean primary, Button.OnPress onPress) {
        return new StudioButton(label, x, y, width, primary, onPress);
    }

    static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return width < font.width("…") ? "" : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    private static int parseTick(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String seconds(int tick) {
        int hundredths = (tick % 20) * 5;
        if (hundredths == 0) {
            return Integer.toString(tick / 20);
        }
        return (tick / 20) + "." + (hundredths % 10 == 0 ? Integer.toString(hundredths / 10)
                : hundredths < 10 ? "0" + hundredths : Integer.toString(hundredths));
    }

    private static <T> List<T> replace(List<T> source, int index, T item) {
        List<T> result = new ArrayList<>(source);
        if (item == null) {
            result.remove(index);
        } else if (index < 0) {
            result.add(item);
        } else {
            result.set(index, item);
        }
        return result;
    }

    private static final class StudioButton extends Button {
        private final boolean primary;

        StudioButton(String label, int x, int y, int width, boolean primary, Button.OnPress onPress) {
            super(x, y, width, BUTTON_HEIGHT, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean highlight = active && isHoveredOrFocused();
            int fill = primary && active ? (highlight ? 0xFFFFE0A8 : ACCENT) : (highlight ? 0xFF3D4E62 : PANEL);
            int border = highlight || primary && active ? ACCENT : BORDER;
            graphics.fill(getX(), getY(), getRight(), getBottom(), border);
            graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, fill);
            Font font = Minecraft.getInstance().font;
            String label = clip(font, getMessage().getString(), getWidth() - 8);
            graphics.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2,
                    getY() + (getHeight() - 8) / 2, active ? (primary ? PANEL : INK) : 0xFF8190A2, false);
        }
    }
}
