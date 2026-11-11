package com.example.examplemod.client.cutscene;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.cutscene.CutsceneEditorScreen.Track;
import com.example.examplemod.cutscene.Cutscene;
import com.example.examplemod.cutscene.Cutscene.CameraKeyframe;
import com.example.examplemod.cutscene.Cutscene.Easing;
import com.example.examplemod.cutscene.Cutscene.SoundCue;
import com.example.examplemod.cutscene.Cutscene.TextCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

import static com.example.examplemod.client.cutscene.CutsceneEditorScreen.ACCENT;
import static com.example.examplemod.client.cutscene.CutsceneEditorScreen.BACKGROUND;
import static com.example.examplemod.client.cutscene.CutsceneEditorScreen.BORDER;
import static com.example.examplemod.client.cutscene.CutsceneEditorScreen.ERROR;
import static com.example.examplemod.client.cutscene.CutsceneEditorScreen.INK;
import static com.example.examplemod.client.cutscene.CutsceneEditorScreen.MUTED;
import static com.example.examplemod.client.cutscene.CutsceneEditorScreen.PANEL;
import static com.example.examplemod.client.cutscene.CutsceneEditorScreen.clip;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class CutsceneCueScreen extends Screen {
    private static final ResourceLocation DEFAULT_SOUND = ResourceLocation.withDefaultNamespace("block.note_block.chime");
    private static final int TEXT_COLOR = 0xFFF5F2EA;
    private static final int PLATE_COLOR = 0xD018212D;
    private static final int PLACEMENT_KEY = GLFW.GLFW_KEY_F6;
    private static CutsceneCueScreen placement;

    private enum Field {
        TICK("Начало, т", 5), X("X", 24), Y("Y", 24), Z("Z", 24),
        YAW("Поворот", 16), PITCH("Наклон", 16), SOUND("Идентификатор звука", 256),
        VOLUME("Громкость", 12), SOUND_PITCH("Тон", 12), DURATION("Длина, т", 5),
        TEXT_COLOR("Цвет текста", 9), PLATE_COLOR("Цвет плашки", 9);

        final String label;
        final int limit;

        Field(String label, int limit) {
            this.label = label;
            this.limit = limit;
        }
    }

    private record Label(String text, int x, int y) {}

    private final CutsceneEditorScreen parent;
    private final Track track;
    private final int index;
    private final EnumMap<Field, String> values = new EnumMap<>(Field.class);
    private final List<Label> labels = new ArrayList<>();
    private final List<Button> soundChoices = new ArrayList<>();
    private List<String> availableSounds = List.of();
    private EditBox soundBox;
    private SimpleSoundInstance audition;
    private String text = "";
    private String status = "";
    private boolean statusError;
    private Easing easing;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int rowStep;
    private int swatchY;
    private int choicesY;

    CutsceneCueScreen(CutsceneEditorScreen parent, Track track, int index, int tick) {
        super(Component.literal((index < 0 ? "Добавить ключ · " : "Редактировать ключ · ") + track.label));
        this.parent = parent;
        this.track = track;
        this.index = index;
        Cutscene scene = parent.scene();
        int start = track == Track.CAMERA ? tick : Math.min(tick, scene.durationTicks() - 1);
        switch (track) {
            case CAMERA -> loadCamera(index >= 0 ? scene.camera().get(index) : initialCamera(start));
            case SOUND -> loadSound(index >= 0 ? scene.sounds().get(index) : new SoundCue(start, DEFAULT_SOUND, 1, 1));
            case TEXT -> {
                if (index >= 0) {
                    TextCue cue = scene.texts().get(index);
                    loadText(cue.tick(), cue.durationTicks(), cue.text(), cue.textColor(), cue.backgroundColor());
                } else {
                    loadText(start, Math.min(60, scene.durationTicks() - start), "", TEXT_COLOR, PLATE_COLOR);
                }
            }
        }
    }

    @Override
    protected void init() {
        if (placement == this) {
            placement = null;
        }
        labels.clear();
        soundChoices.clear();
        panelWidth = Math.min(380, width - 20);
        boolean compact = height < 227;
        panelHeight = switch (track) {
            case CAMERA -> compact ? 174 : 188;
            case SOUND -> compact ? 174 : 213;
            case TEXT -> compact ? 178 : 223;
        };
        rowStep = compact ? 30 : 34;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(2, (height - panelHeight) / 2);
        int x = panelX + 12;
        int y = panelY + (compact ? 25 : 34);
        int contentWidth = panelWidth - 24;
        int third = (contentWidth - 16) / 3;
        field(Field.TICK, x, y, third);

        switch (track) {
            case CAMERA -> cameraFields(x, y, contentWidth, third);
            case SOUND -> soundFields(x, y, contentWidth, third, compact);
            case TEXT -> textFields(x, y, contentWidth, third, compact);
        }

        int bottom = panelY + panelHeight - 27;
        int right = panelX + panelWidth - 12;
        add("Отмена", right - 141, bottom, 63, false, this::onClose);
        add("Применить", right - 72, bottom, 72, true, this::apply);
    }

    private void cameraFields(int x, int y, int contentWidth, int third) {
        int easingX = x + third + 8;
        labels.add(new Label("Переход", easingX, y));
        Button easingButton = addRenderableWidget(CutsceneEditorScreen.button(easingName(easing), easingX, y + 10,
                contentWidth - third - 8, false, button -> {
            Easing[] modes = Easing.values();
            easing = modes[(easing.ordinal() + 1) % modes.length];
            button.setMessage(Component.literal(easingName(easing)));
        }));
        easingButton.setTooltip(Tooltip.create(Component.literal("Переход от этого ключа к следующему. Щёлкните для смены режима")));

        field(Field.X, x, y + rowStep, third);
        field(Field.Y, x + third + 8, y + rowStep, third);
        field(Field.Z, x + 2 * (third + 8), y + rowStep, third);
        int aimWidth = contentWidth < 225 ? 55 : 102;
        int angleWidth = (contentWidth - aimWidth - 12) / 2;
        field(Field.YAW, x, y + 2 * rowStep, angleWidth);
        field(Field.PITCH, x + angleWidth + 7, y + 2 * rowStep, angleWidth);
        Button aim = add(aimWidth == 55 ? "В мир" : "Навести в мире", x + contentWidth - aimWidth,
                y + 2 * rowStep + 10, aimWidth, false, this::startPlacement);
        aim.setTooltip(Tooltip.create(Component.literal("Переместитесь и нажмите F6 для захвата вида")));
    }

    private void soundFields(int x, int y, int contentWidth, int third, boolean compact) {
        field(Field.VOLUME, x + third + 8, y, third).setTooltip(Tooltip.create(Component.literal("От 0 до 4")));
        field(Field.SOUND_PITCH, x + 2 * (third + 8), y, third).setTooltip(Tooltip.create(Component.literal("От 0.5 до 2")));
        int auditionWidth = contentWidth < 225 ? 42 : 73;
        soundBox = field(Field.SOUND, x, y + rowStep, contentWidth - auditionWidth - 5);
        soundBox.setTooltip(Tooltip.create(Component.literal("Введите часть названия звука или полный namespace:id")));
        soundBox.setResponder(value -> {
            values.put(Field.SOUND, value);
            refreshSounds();
        });
        Button play = add(auditionWidth == 42 ? "Тест" : "Прослушать", x + contentWidth - auditionWidth,
                y + rowStep + 10, auditionWidth, false, this::audition);
        play.setTooltip(Tooltip.create(Component.literal("Прослушать звук с заданной громкостью и тоном")));
        availableSounds = minecraft.getSoundManager().getAvailableSounds().stream().map(ResourceLocation::toString).sorted().toList();
        choicesY = y + rowStep + 30;
        for (int i = 0; i < (compact ? 2 : 3); i++) {
            int choice = i;
            soundChoices.add(add("", x, choicesY + i * 19, contentWidth, false,
                    () -> soundBox.setValue(soundChoices.get(choice).getMessage().getString())));
        }
        refreshSounds();
    }

    private void textFields(int x, int y, int contentWidth, int third, boolean compact) {
        field(Field.DURATION, x + third + 8, y, third);
        labels.add(new Label("Надпись", x, y + rowStep));
        int textY = y + rowStep + 10;
        int textHeight = compact ? 31 : 44;
        MultiLineEditBox textBox = addRenderableWidget(new MultiLineEditBox(font, x, textY, contentWidth, textHeight,
                Component.literal("Текст плашки"), Component.literal("Надпись")));
        textBox.setCharacterLimit(Cutscene.MAX_TEXT_LENGTH);
        textBox.setValue(text);
        textBox.setValueListener(value -> text = value);
        int colorWidth = (contentWidth - 8) / 2;
        Tooltip colorHint = Tooltip.create(Component.literal("#AARRGGBB (прозрачность, красный, зелёный, синий) или #RRGGBB"));
        int colorY = textY + textHeight + 13;
        swatchY = colorY + 11;
        field(Field.TEXT_COLOR, x, colorY, colorWidth - 20).setTooltip(colorHint);
        field(Field.PLATE_COLOR, x + colorWidth + 8, colorY, colorWidth - 20).setTooltip(colorHint);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        int right = panelX + panelWidth;
        int bottom = panelY + panelHeight;
        graphics.fill(panelX, panelY, right, bottom, BORDER);
        graphics.fill(panelX + 1, panelY + 1, right - 1, bottom - 1, PANEL);
        graphics.fill(panelX + 1, panelY + 1, right - 1, panelY + 3, track.color);
        graphics.drawString(font, clip(font, title.getString(), panelWidth - 24), panelX + 12, panelY + 11, INK, false);
        for (Label label : labels) {
            graphics.drawString(font, label.text(), label.x(), label.y(), MUTED, false);
        }
        if (track == Track.TEXT) {
            int x = panelX + 12;
            int colorWidth = (panelWidth - 32) / 2;
            swatch(graphics, x + colorWidth - 16, swatchY, Field.TEXT_COLOR);
            swatch(graphics, x + 2 * colorWidth - 8, swatchY, Field.PLATE_COLOR);
        }
        if (track == Track.SOUND && !soundChoices.getFirst().visible) {
            String hint = clip(font, "Совпадений в звуковых ресурсах клиента нет", panelWidth - 24);
            graphics.drawString(font, hint, panelX + 12, choicesY + 4, MUTED, false);
        }
        graphics.drawString(font, clip(font, status, panelWidth - 24), panelX + 12, bottom - 40,
                statusError ? ERROR : ACCENT, false);
        if (font.width(status) > panelWidth - 24 && mouseX >= panelX && mouseX <= right
                && mouseY >= bottom - 42 && mouseY < bottom - 28) {
            setTooltipForNextRenderPass(Component.literal(status));
        }
    }

    private void swatch(GuiGraphics graphics, int x, int y, Field field) {
        int color;
        try {
            color = color(field);
        } catch (IllegalArgumentException ex) {
            color = 0;
        }
        graphics.fill(x, y, x + 16, y + 16, BORDER);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFF111722);
        graphics.fill(x + 1, y + 1, x + 8, y + 8, 0xFF677386);
        graphics.fill(x + 8, y + 8, x + 15, y + 15, 0xFF677386);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, color);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        stopAudition();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void apply() {
        try {
            int tick = integer(Field.TICK);
            int maxTick = parent.scene().durationTicks() - (track == Track.CAMERA ? 0 : 1);
            if (tick < 0 || tick > maxTick) {
                throw new IllegalArgumentException("Начало должно быть от 0 до " + maxTick + " тиков");
            }
            switch (track) {
                case CAMERA -> parent.applyCamera(index, new CameraKeyframe(tick, decimal(Field.X), decimal(Field.Y),
                        decimal(Field.Z), (float) decimal(Field.YAW), (float) decimal(Field.PITCH), easing));
                case SOUND -> parent.applySound(index, soundCue(tick));
                case TEXT -> {
                    int duration = integer(Field.DURATION);
                    if (duration < 1 || duration > parent.scene().durationTicks() - tick) {
                        throw new IllegalArgumentException("Плашка должна завершиться в пределах сцены");
                    }
                    if (text.isBlank()) {
                        throw new IllegalArgumentException("Введите текст плашки");
                    }
                    parent.applyText(index, new TextCue(tick, duration, text,
                            color(Field.TEXT_COLOR), color(Field.PLATE_COLOR)));
                }
            }
        } catch (IllegalArgumentException ex) {
            status = ex.getMessage();
            statusError = true;
        }
    }

    private CameraKeyframe initialCamera(int tick) {
        try {
            CameraKeyframe current = readCamera(parent.getMinecraft(), tick, Easing.SMOOTH);
            if (current != null) {
                return current;
            }
        } catch (IllegalArgumentException ex) {
            status = ex.getMessage();
            statusError = true;
        }
        return new CameraKeyframe(tick, 0, 64, 0, 0, 0, Easing.SMOOTH);
    }

    private CameraKeyframe readCamera(Minecraft client, int tick, Easing mode) {
        if (client.level == null || !client.level.dimension().location().equals(parent.scene().dimension())) {
            status = "Откройте редактор в измерении сцены для захвата вида";
            statusError = true;
            return null;
        }
        var camera = client.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            status = "Вид ещё загружается";
            statusError = true;
            return null;
        }
        var position = camera.getPosition();
        return new CameraKeyframe(tick, position.x, position.y, position.z, camera.getYRot(), camera.getXRot(), mode);
    }

    private void startPlacement() {
        if (minecraft.player == null || minecraft.level == null ||
                !minecraft.level.dimension().location().equals(parent.scene().dimension())) {
            status = "Захват вида доступен в измерении сцены";
            statusError = true;
            return;
        }
        placement = this;
        minecraft.setScreen(null);
        minecraft.player.displayClientMessage(Component.literal("Переместитесь и нажмите F6, чтобы записать вид"), true);
    }

    @SubscribeEvent
    public static void onPlacementKey(InputEvent.Key event) {
        if (placement == null || event.getKey() != PLACEMENT_KEY || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || client.player == null || CutscenePlayback.isPlaying()) {
            return;
        }
        CutsceneCueScreen editor = placement;
        placement = null;
        try {
            CameraKeyframe camera = editor.readCamera(client, 0, editor.easing);
            if (camera != null) {
                editor.loadCameraPosition(camera);
                editor.status = "Вид записан. Примените ключ, чтобы добавить его на шкалу";
                editor.statusError = false;
            }
        } catch (IllegalArgumentException ex) {
            editor.status = ex.getMessage();
            editor.statusError = true;
        }
        client.player.displayClientMessage(Component.empty(), true);
        client.setScreen(editor);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        cancelPlacement();
    }

    static void cancelPlacement() {
        placement = null;
    }

    private void loadCamera(CameraKeyframe key) {
        values.put(Field.TICK, Integer.toString(key.tick()));
        loadCameraPosition(key);
        easing = key.easing();
    }

    private void loadCameraPosition(CameraKeyframe key) {
        values.put(Field.X, Double.toString(key.x()));
        values.put(Field.Y, Double.toString(key.y()));
        values.put(Field.Z, Double.toString(key.z()));
        values.put(Field.YAW, Float.toString(key.yaw()));
        values.put(Field.PITCH, Float.toString(key.pitch()));
    }

    private void loadSound(SoundCue key) {
        values.put(Field.TICK, Integer.toString(key.tick()));
        values.put(Field.SOUND, key.sound().toString());
        values.put(Field.VOLUME, Float.toString(key.volume()));
        values.put(Field.SOUND_PITCH, Float.toString(key.pitch()));
    }

    private void loadText(int tick, int duration, String text, int textColor, int backgroundColor) {
        values.put(Field.TICK, Integer.toString(tick));
        values.put(Field.DURATION, Integer.toString(duration));
        values.put(Field.TEXT_COLOR, String.format(Locale.ROOT, "#%08X", textColor));
        values.put(Field.PLATE_COLOR, String.format(Locale.ROOT, "#%08X", backgroundColor));
        this.text = text;
    }

    private void refreshSounds() {
        String query = values.get(Field.SOUND).strip().toLowerCase(Locale.ROOT);
        List<String> matches = availableSounds.stream().filter(value -> value.contains(query)).limit(soundChoices.size()).toList();
        for (int i = 0; i < soundChoices.size(); i++) {
            Button button = soundChoices.get(i);
            button.visible = i < matches.size();
            if (button.visible) {
                button.setMessage(Component.literal(matches.get(i)));
                button.setTooltip(Tooltip.create(button.getMessage()));
            }
        }
    }

    private SoundCue soundCue(int tick) {
        ResourceLocation sound = ResourceLocation.tryParse(values.get(Field.SOUND).strip());
        if (sound == null || sound.getPath().isEmpty()) {
            throw new IllegalArgumentException("Введите идентификатор звука в формате namespace:id");
        }
        return new SoundCue(tick, sound, (float) decimal(Field.VOLUME), (float) decimal(Field.SOUND_PITCH));
    }

    private void audition() {
        try {
            SoundCue cue = soundCue(0);
            if (minecraft.getSoundManager().getSoundEvent(cue.sound()) == null) {
                throw new IllegalArgumentException("Звук не найден в ресурсах этого клиента");
            }
            stopAudition();
            audition = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(cue.sound()), cue.pitch(), cue.volume());
            minecraft.getSoundManager().play(audition);
            status = "Звук запущен";
            statusError = false;
        } catch (IllegalArgumentException ex) {
            status = ex.getMessage();
            statusError = true;
        }
    }

    private void stopAudition() {
        if (audition != null) {
            minecraft.getSoundManager().stop(audition);
            audition = null;
        }
    }

    private EditBox field(Field field, int x, int y, int width) {
        labels.add(new Label(field.label, x, y));
        EditBox box = addRenderableWidget(new EditBox(font, x, y + 10, width, 18, Component.literal(field.label)));
        box.setMaxLength(field.limit);
        box.setValue(values.get(field));
        box.setResponder(value -> values.put(field, value));
        return box;
    }

    private Button add(String label, int x, int y, int width, boolean primary, Runnable action) {
        return addRenderableWidget(CutsceneEditorScreen.button(label, x, y, width, primary, action));
    }

    private int integer(Field field) {
        try {
            return Integer.parseInt(values.get(field).strip());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Введите целое число в поле «" + field.label + "»");
        }
    }

    private double decimal(Field field) {
        try {
            double value = Double.parseDouble(values.get(field).strip().replace(',', '.'));
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Report the field name below.
        }
        throw new IllegalArgumentException("Проверьте число в поле «" + field.label + "»");
    }

    private int color(Field field) {
        String hex = values.get(field).strip().replaceFirst("^#", "");
        if (hex.matches("[0-9a-fA-F]{6}")) {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        }
        if (hex.matches("[0-9a-fA-F]{8}")) {
            return Integer.parseUnsignedInt(hex, 16);
        }
        throw new IllegalArgumentException("Используйте #AARRGGBB для поля «" + field.label + "»");
    }

    static String easingName(Easing mode) {
        return switch (mode) {
            case LINEAR -> "Линейно";
            case SMOOTH -> "Плавно";
            case EASE_IN -> "Разгон";
            case EASE_OUT -> "Замедление";
            case HOLD -> "Удержание";
        };
    }
}
