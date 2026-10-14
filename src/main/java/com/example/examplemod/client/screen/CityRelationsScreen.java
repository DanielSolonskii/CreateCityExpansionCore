package com.example.examplemod.client.screen;

import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityRelationSnapshot;
import com.example.examplemod.network.CityRelationsCommandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

public final class CityRelationsScreen extends CityPylonPageScreen {
    private final CityPylonScreen parent;
    private final CityPylonMenu menu;
    private EditBox search;
    private int scroll;

    public CityRelationsScreen(CityPylonScreen parent) {
        super(Component.translatable("gui.createcityexpansioncore.relations.title"));
        this.parent = parent;
        this.menu = parent.getMenu();
    }

    @Override protected void init() {
        search = new EditBox(font, width / 2 - 150, 42, 220, 20,
                Component.translatable("gui.createcityexpansioncore.relations.search"));
        search.setHint(Component.translatable("gui.createcityexpansioncore.relations.search"));
        search.setResponder(ignored -> scroll = 0); addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.createcityexpansioncore.common.back"),
                ignored -> onClose()).bounds(width / 2 + 80, 42, 70, 20).build());
        request(CityRelationsCommandPayload.Action.REQUEST_STATE, null);
    }

    private void request(CityRelationsCommandPayload.Action action, java.util.UUID target) {
        PacketDistributor.sendToServer(new CityRelationsCommandPayload(menu.getPylonPos(), action, target));
    }

    private List<CityRelationSnapshot> visible() {
        var state = menu.getRelationsState(); if (state == null) return List.of();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return state.cities().stream().filter(city -> query.isEmpty()
                || city.cityName().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 170, top = 20, right = width / 2 + 170;
        graphics.fill(left, top, right, height - 20, 0xE8182028);
        graphics.drawCenteredString(font, title, width / 2, 26, 0xFFE8D9AF);
        var state = menu.getRelationsState();
        if (state == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.createcityexpansioncore.relations.loading"),
                    width / 2, 90, 0xFF9AA6AF);
        } else {
            List<CityRelationSnapshot> cities = visible();
            if (cities.isEmpty()) graphics.drawCenteredString(font,
                    Component.translatable("gui.createcityexpansioncore.relations.empty"), width / 2, 90, 0xFF9AA6AF);
            int y = 72; for (int i = scroll; i < cities.size() && y + 38 < height - 22; i++, y += 42) {
                CityRelationSnapshot city = cities.get(i); graphics.fill(left + 12, y, right - 12, y + 36, 0xFF25313A);
                graphics.drawString(font, city.cityName(), left + 20, y + 7, 0xFFF3EBD3, false);
                graphics.drawString(font, Component.translatable("gui.createcityexpansioncore.relations.status."
                        + city.status().name().toLowerCase(Locale.ROOT)), left + 20, y + 21, statusColor(city.status()), false);
                Component action = Component.translatable(city.status() == CityRelationSnapshot.Status.TRUSTED
                        || city.status() == CityRelationSnapshot.Status.PARTNER
                        ? "gui.createcityexpansioncore.relations.remove" : "gui.createcityexpansioncore.relations.add");
                int actionX=right-120,actionY=y+7,actionW=108,actionH=22;
                boolean hover=mouseX>=actionX&&mouseX<actionX+actionW&&mouseY>=actionY&&mouseY<actionY+actionH;
                graphics.fill(actionX,actionY,actionX+actionW,actionY+actionH,state.canManage()?(hover?0xFF604B29:0xFF443720):0xFF252B2E);
                graphics.renderOutline(actionX,actionY,actionW,actionH,state.canManage()?0xFFB99049:0xFF4F585D);
                graphics.drawCenteredString(font, action, actionX+actionW/2, actionY+7,
                        state.canManage() ? 0xFFDFC47A : 0xFF69747B);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static int statusColor(CityRelationSnapshot.Status status) {
        return switch (status) { case PARTNER -> 0xFF72D6B2; case TRUSTED -> 0xFF8BC7E8;
            case TRUSTS_US -> 0xFFC5A8E5; case NONE -> 0xFF8A969E; };
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var state = menu.getRelationsState(); if (button == 0 && state != null && state.canManage()) {
            int left = width / 2 - 170, right = width / 2 + 170, y = 72;
            List<CityRelationSnapshot> cities = visible();
            for (int i = scroll; i < cities.size() && y + 38 < height - 22; i++, y += 42) {
                if (mouseX >= right - 120 && mouseX < right - 12 && mouseY >= y+7 && mouseY < y + 29) {
                    CityRelationSnapshot city = cities.get(i); boolean remove = city.status() == CityRelationSnapshot.Status.TRUSTED
                            || city.status() == CityRelationSnapshot.Status.PARTNER;
                    request(remove ? CityRelationsCommandPayload.Action.REMOVE_TRUST
                            : CityRelationsCommandPayload.Action.ADD_TRUST, city.cityId()); return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, visible().size() - Math.max(1, (height - 105) / 42));
        scroll = Math.max(0, Math.min(max, scroll + (vertical < 0 ? 1 : -1))); return true;
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
