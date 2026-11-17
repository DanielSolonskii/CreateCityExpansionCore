package com.example.examplemod.client.screen;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.BlueprintEditorClient;
import com.example.examplemod.devtools.BlueprintEditorFiles;
import com.example.examplemod.devtools.BlueprintEditorWorld.Capture;
import com.example.examplemod.multiblock.BlueprintCatalog.Plan;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.Map;

import static com.example.examplemod.client.BlueprintEditorClient.label;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

public final class BlueprintEditorScreen extends CommunityPage {
    private static final Tier[] TIERS = Tier.values();
    private Tier tier;
    private Capture capture;
    private BlueprintPreview preview;
    private Map<Item, Integer> materials;
    private Component status = Component.empty();
    private boolean choosingFolder;

    public BlueprintEditorScreen(Tier tier, Capture capture) {
        super(null, "blueprint_editor");
        this.tier = tier;
        this.capture = capture;
        refreshPreview();
    }

    @Override
    public Component getTitle() {
        return label("title");
    }

    private void refreshPreview() {
        Plan plan = capture == null ? MineBlueprints.current().plan(tier) : capture.preview(tier);
        preview = new BlueprintPreview(plan);
        materials = plan.materials(false);
    }

    @Override
    protected void draw(GuiGraphics graphics, float partial) {
        rounded(graphics, 12, 12, 596, 60, 6, RAISED);
        graphics.pose().pushPose();
        graphics.pose().translate(25, 26, 0);
        graphics.pose().scale(2, 2, 1);
        graphics.renderItem(CreateCityExpansionCore.BLUEPRINT_EDITOR.get().getDefaultInstance(), 0, 0);
        graphics.pose().popPose();
        text(graphics, getTitle(), 72, 26, 508, TEXT);
        text(graphics, label(capture == null ? "select_hint" : "confirm_title"), 72, 47, 508, MUTED);

        var project = BlueprintEditorClient.project();
        Component path = project == null ? label("choose_first") : Component.literal(project.root().toString());
        var folder = new SocialHubLayout.Rect(12, 84, 429, 27);
        rounded(graphics, folder.x(), folder.y(), folder.width(), folder.height(), 5, INSET);
        text(graphics, path, folder.x() + 9, folder.y() + 9, folder.width() - 18, MUTED);
        hint(folder, path);
        boolean ready = !choosingFolder && !BlueprintEditorClient.busy();
        button(graphics, new SocialHubLayout.Rect(448, 84, 160, 27), label("choose_folder"), this::chooseFolder, ready, false);

        clip(graphics);
        var rect = row(0, Math.max(185, 67 + materials.size() * 19));
        int x = rect.x();
        int y = rect.y();
        rounded(graphics, x, y, 333, 114, 5, INSET);
        preview.draw(graphics, x + 5, y + 4, 323, 106);
        for (int index = 0; index < TIERS.length; index++) {
            Tier selected = TIERS[index];
            button(graphics, new SocialHubLayout.Rect(x + index * 67, y + 121, 64, 25),
                    Component.translatable("gui.createcityexpansioncore.blueprint.tier", selected.name()), () -> {
                        tier = selected;
                        refreshPreview();
                    }, ready, tier == selected);
        }
        int right = x + 343;
        int width = rect.right() - right;
        card(graphics, new SocialHubLayout.Rect(right, y, width, 51), ACCENT);
        text(graphics, Component.translatable("gui.createcityexpansioncore.blueprint.mine.name"), right + 10, y + 8,
                width - 20, TEXT);
        Component sub = capture == null
                ? Component.translatable("gui.createcityexpansioncore.blueprint.mine.output", tier.outputPercent())
                : label("size", capture.max().getX() - capture.min().getX() + 1,
                        capture.max().getY() - capture.min().getY() + 1, capture.max().getZ() - capture.min().getZ() + 1);
        text(graphics, sub, right + 10, y + 29, width - 20, MUTED);
        card(graphics, new SocialHubLayout.Rect(right, y + 58, width, 22 + materials.size() * 19), 0xff77bdd2);
        text(graphics, Component.translatable("gui.createcityexpansioncore.blueprint.materials"), right + 10, y + 65,
                width - 20, TEXT);
        int itemY = y + 78;
        for (var entry : materials.entrySet()) {
            var stack = entry.getKey().getDefaultInstance();
            graphics.renderItem(stack, right + 8, itemY);
            text(graphics, stack.getHoverName(), right + 28, itemY + 5, width - 72, TEXT);
            String count = "×" + entry.getValue();
            graphics.drawString(font, count, rect.right() - font.width(count) - 9, itemY + 5, MUTED, false);
            itemY += 19;
        }
        Component file = Component.literal(BlueprintEditorFiles.relativeFile(tier));
        text(graphics, file, x + 2, y + 155, 329, MUTED);
        hint(new SocialHubLayout.Rect(x, y + 152, 333, 16), file);
        unclip(graphics);

        int bottom = layout.height() - 31;
        if (capture == null) {
            button(graphics, new SocialHubLayout.Rect(12, bottom, 208, 24), label("place"),
                    () -> BlueprintEditorClient.beginPlacement(tier), ready, true);
        } else {
            button(graphics, new SocialHubLayout.Rect(12, bottom, 208, 24), label("save"), this::save,
                    ready && project != null, true);
        }
        var statusArea = new SocialHubLayout.Rect(228, bottom, 380, 24);
        text(graphics, status, statusArea.x(), statusArea.y() + 8, statusArea.width(), MUTED);
        if (!status.getString().isEmpty()) {
            hint(statusArea, status);
        }
    }

    private void chooseFolder() {
        var project = BlueprintEditorClient.project();
        String initial = project == null ? System.getProperty("user.dir") : project.root().toString();
        choosingFolder = true;
        Minecraft client = Minecraft.getInstance();
        Thread.ofVirtual().name("blueprint-project-picker").start(() -> {
            try {
                String path = TinyFileDialogs.tinyfd_selectFolderDialog("CreateCityExpansionCore", initial);
                client.execute(() -> {
                    choosingFolder = false;
                    if (client.screen == this && path != null) {
                        BlueprintEditorClient.selectProject(Path.of(path), this::feedback, this::refreshPreview);
                    }
                });
            } catch (RuntimeException exception) {
                client.execute(() -> {
                    choosingFolder = false;
                    feedback(label("error", exception.getMessage()));
                });
            }
        });
    }

    private void save() {
        if (capture != null) {
            BlueprintEditorClient.save(tier, capture, this::feedback, () -> {
                capture = null;
                refreshPreview();
            });
        }
    }

    private void feedback(Component message) {
        status = message;
    }
}
