package com.example.examplemod.client.screen;

import com.example.examplemod.city.*;
import com.example.examplemod.client.map.*;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Draft editor. Only Save sends data; Escape protects unsaved work. */
public final class CityIdentityEditorScreen extends Screen {
    private final Screen parent;
    private final CityPublicProfile profile;
    private final CityEmblemCanvas canvas;
    private String description;
    private int colour, ink = 2, variant;
    private boolean mirror, filling, drawing, waiting, discard;
    private String status = "";
    private int waitTicks;
    private java.util.UUID requestId;
    private final Button[] tools = new Button[6];
    private EditBox descriptionBox;
    private Button save;
    private Button cancel;
    private int left, span, canvasX, canvasY, scale;
    private int lastPixelX = -1, lastPixelY = -1;
    public CityIdentityEditorScreen(Screen parent, CityPublicProfile profile) {
        super(text("edit")); this.parent = parent; this.profile = profile;
        this.canvas = new CityEmblemCanvas(profile.identity().pixels());
        this.description = profile.identity().description(); this.colour = profile.colour();
    }
    private static Component text(String key) { return Component.translatable("gui.createcityexpansioncore.city.identity." + key); }
    @Override protected void init() {
        span = Math.min(560, width - 16); left = (width - span) / 2;
        scale = Math.max(1, Math.min(4, (height - 112) / 32)); canvasX = left + 8; canvasY = 26;
        int fieldX = canvasX + scale * 32 + 16, fieldWidth = Math.max(60, left + span - 8 - fieldX);
        descriptionBox = new EditBox(font, fieldX, 38, fieldWidth, 18, text("description"));
        descriptionBox.setMaxLength(CityIdentity.MAX_DESCRIPTION); descriptionBox.setValue(description);
        descriptionBox.setResponder(value -> { description = value; discard = false; }); addRenderableWidget(descriptionBox);
        String[] keys = {"brush", "fill", "mirror", "undo", "redo", "template"};
        int toolWidth = (span - 16) / keys.length;
        for (int i = 0; i < keys.length; i++) { final int tool = i;
            tools[i] = addRenderableWidget(Button.builder(text(keys[i]), b -> {
                if (waiting) return;
                switch (tool) { case 0 -> filling = false; case 1 -> filling = true; case 2 -> mirror = !mirror;
                    case 3 -> canvas.undo(); case 4 -> canvas.redo(); case 5 -> canvas.template(++variant); }
                discard = false;
            }).bounds(left + 8 + i * toolWidth, height - 54, toolWidth - 2, 18).build());
        }
        save = addRenderableWidget(Button.builder(text("save"), b -> {
            canvas.end();
            if (waiting || !CityIdentity.valid(description, canvas.image())) { status = "invalid"; return; }
            waiting = true; waitTicks = 200; status = "saving"; descriptionBox.setEditable(false);
            requestId = java.util.UUID.randomUUID();
            PacketDistributor.sendToServer(new SetCityIdentityPayload(profile.id(), requestId, profile.revision(), colour, description, canvas.image()));
        }).bounds(left + 8, height - 28, (span - 20) / 2, 20).build());
        cancel = addRenderableWidget(Button.builder(text("cancel"), b -> onClose()).bounds(left + 12 + (span - 20) / 2, height - 28, (span - 20) / 2, 20).build());
    }
    public static void accept(CityIdentityResultPayload result) {
        if (!(Minecraft.getInstance().screen instanceof CityIdentityEditorScreen screen) || !screen.profile.id().equals(result.city()) || !screen.waiting || !result.requestId().equals(screen.requestId)) return;
        screen.waiting = false; screen.status = result.result();
        screen.descriptionBox.setEditable(true);
        if (result.result().equals("saved")) Minecraft.getInstance().setScreen(screen.parent);
    }
    private boolean dirty() { return colour != profile.colour() || !description.equals(profile.identity().description()) || !canvas.image().equals(profile.identity().pixels()); }
    @Override public void onClose() {
        if (waiting) return;
        canvas.end();
        if (dirty() && !discard) { discard = true; status = "discard"; return; }
        minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (waiting && --waitTicks <= 0) { waiting = false; status = "timeout"; descriptionBox.setEditable(true); }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF510171A);
        graphics.renderOutline(left, 2, span, height - 4, 0xFF9B7845);
        graphics.drawString(font, font.plainSubstrByWidth(profile.name(), span / 2), left + 8, 10, 0xFFD3A363, false);
        if (!status.isEmpty()) graphics.drawString(font, font.plainSubstrByWidth(text(status).getString(), span / 2 - 12), left + span / 2, 10, 0xFFE7C785, false);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) graphics.fill(canvasX + x * scale, canvasY + y * scale,
                canvasX + (x + 1) * scale, canvasY + (y + 1) * scale, (x + y) % 2 == 0 ? 0xFF263036 : 0xFF303C42);
        CityEmblemRenderer.draw(graphics, new CityIdentity("", canvas.image()), colour, canvasX, canvasY, scale);
        graphics.renderOutline(canvasX - 1, canvasY - 1, 32 * scale + 2, 32 * scale + 2, 0xFFD3A363);
        int fieldX = canvasX + scale * 32 + 16;
        graphics.drawString(font, text("description"), fieldX, 26, 0xFFE7E8DB, false);
        graphics.enableScissor(fieldX, 60, left + span - 8, Math.max(61, height - 94));
        graphics.drawWordWrap(font, status.equals("conflict") || status.equals("forbidden") || status.equals("discard") || status.equals("timeout") ? text(status) : Component.literal(description), fieldX, 60, Math.max(40, left + span - fieldX - 8), 0xFFA5B3B4);
        graphics.disableScissor();
        int swatchStep = Math.min(24, (span - 88) / 16), swatchX = left + 78;
        graphics.drawString(font, text("colour"), left + 8, height - 86, 0xFFA5B3B4, false);
        graphics.drawString(font, text("ink"), left + 8, height - 70, 0xFFA5B3B4, false);
        for (int i = 0; i < 16; i++) {
            int x = swatchX + i * swatchStep;
            graphics.fill(x, height - 88, x + swatchStep - 2, height - 76, 0xFF000000 | CityColors.PALETTE.get(i));
            graphics.fill(x, height - 72, x + swatchStep - 2, height - 60, i == 0 ? 0xFF303C42 : CityIdentity.colour(i, colour));
            if (CityColors.PALETTE.get(i) == colour) graphics.renderOutline(x - 1, height - 89, swatchStep, 14, 0xFFE7E8DB);
            if (i == ink) graphics.renderOutline(x - 1, height - 73, swatchStep, 14, 0xFFE7E8DB);
        }
        save.active = !waiting && dirty() && CityIdentity.valid(description, canvas.image());
        cancel.setMessage(text(discard ? "discard_confirm" : "cancel"));
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < 3; i++) if (i == 0 && !filling || i == 1 && filling || i == 2 && mirror)
            graphics.renderOutline(tools[i].getX(), tools[i].getY(), tools[i].getWidth(), tools[i].getHeight(), 0xFFD3A363);
        if (mouseX >= canvasX && mouseX < canvasX + 32 * scale && mouseY >= canvasY && mouseY < canvasY + 32 * scale)
            graphics.renderTooltip(font, Component.translatable("gui.createcityexpansioncore.city.identity.tool_hint", text(filling ? "fill" : "brush"), mirror ? text("mirror") : Component.empty()), mouseX, mouseY);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (waiting) return true;
        if (x >= canvasX && x < canvasX + 32 * scale && y >= canvasY && y < canvasY + 32 * scale && (button == 0 || button == 1)) {
            canvas.begin(); drawing = true; discard = false;
            int px = (int)(x - canvasX) / scale, py = (int)(y - canvasY) / scale, paint = button == 1 ? 0 : ink;
            lastPixelX = px; lastPixelY = py;
            if (filling) { canvas.fill(px, py, paint); canvas.end(); drawing = false; }
            else canvas.paint(px, py, paint, mirror);
            return true;
        }
        int step = Math.min(24, (span - 88) / 16), index = (int)(x - left - 78) / step;
        if (button == 0 && x >= left + 78 && index >= 0 && index < 16) {
            if (y >= height - 88 && y < height - 76) { colour = CityColors.PALETTE.get(index); discard = false; return true; }
            if (y >= height - 72 && y < height - 60) { ink = index; return true; }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (drawing) {
            int px = (int)(x - canvasX) / scale, py = (int)(y - canvasY) / scale;
            if (x >= canvasX && y >= canvasY && px < 32 && py < 32) {
                if (lastPixelX >= 0) canvas.line(lastPixelX, lastPixelY, px, py, button == 1 ? 0 : ink, mirror);
                else canvas.paint(px, py, button == 1 ? 0 : ink, mirror);
                lastPixelX = px; lastPixelY = py;
            } else { lastPixelX = lastPixelY = -1; }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { canvas.end(); drawing = false; return super.mouseReleased(x, y, button); }
}
