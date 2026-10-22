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
    private static final int ROW_TOP=84, ROW_HEIGHT=64;
    private record ActionBounds(int x,int y,int width,int height){boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}}
    private ActionBounds actionBounds(int rowY,boolean access){return new ActionBounds(width/2+50,rowY+(access?34:6),108,22); }

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
            int y = ROW_TOP; for (int i = scroll; i < cities.size() && y + ROW_HEIGHT < height - 22; i++, y += ROW_HEIGHT) {
                CityRelationSnapshot city = cities.get(i); graphics.fill(left + 12, y, right - 12, y + ROW_HEIGHT - 4, 0xFF25313A);
                graphics.drawString(font, font.plainSubstrByWidth(city.cityName(),190), left + 20, y + 7, 0xFFF3EBD3, false);
                graphics.drawString(font, Component.translatable("gui.createcityexpansioncore.relations.status."
                        + city.status().name().toLowerCase(Locale.ROOT)), left + 20, y + 21, statusColor(city.status()), false);
                Component action = Component.translatable(city.status() == CityRelationSnapshot.Status.TRUSTED
                        || city.status() == CityRelationSnapshot.Status.PARTNER
                        ? "gui.createcityexpansioncore.relations.remove" : "gui.createcityexpansioncore.relations.add");
                ActionBounds trustBounds=actionBounds(y,false);
                int actionX=trustBounds.x(),actionY=trustBounds.y(),actionW=trustBounds.width(),actionH=trustBounds.height();
                boolean hover=mouseX>=actionX&&mouseX<actionX+actionW&&mouseY>=actionY&&mouseY<actionY+actionH;
                graphics.fill(actionX,actionY,actionX+actionW,actionY+actionH,state.canManage()?(hover?0xFF604B29:0xFF443720):0xFF252B2E);
                graphics.renderOutline(actionX,actionY,actionW,actionH,state.canManage()?0xFFB99049:0xFF4F585D);
                graphics.drawCenteredString(font, action, actionX+actionW/2, actionY+7,
                        state.canManage() ? 0xFFDFC47A : 0xFF69747B);
                ActionBounds access=actionBounds(y,true);boolean accessHover=access.contains(mouseX,mouseY);
                int fill=city.territoryAccess()?0xFF25493B:0xFF202A30;
                graphics.fill(access.x(),access.y(),access.x()+access.width(),access.y()+access.height(),state.canManage()&&accessHover?0xFF3B5146:fill);
                graphics.renderOutline(access.x(),access.y(),access.width(),access.height(),city.territoryAccess()?0xFF72D6B2:0xFF68767E);
                graphics.drawCenteredString(font,Component.translatable("gui.createcityexpansioncore.relations.access."+(city.territoryAccess()?"on":"off")),access.x()+access.width()/2,access.y()+7,state.canManage()?0xFFE5ECE8:0xFF69747B);
                if(city.incomingTerritoryAccess())graphics.drawString(font,font.plainSubstrByWidth(Component.translatable("gui.createcityexpansioncore.relations.access.incoming").getString(),190),left+20,y+43,0xFF72D6B2,false);

            }
        }
        graphics.drawCenteredString(font,Component.translatable("gui.createcityexpansioncore.relations.access.note"),width/2,69,0xFF9AA6AF);
        super.render(graphics, mouseX, mouseY, partialTick);
        if(state!=null){int y=ROW_TOP;List<CityRelationSnapshot> rows=visible();for(int i=scroll;i<rows.size()&&y+ROW_HEIGHT<height-22;i++,y+=ROW_HEIGHT)if(actionBounds(y,true).contains(mouseX,mouseY))graphics.renderTooltip(font,font.split(Component.translatable("gui.createcityexpansioncore.relations.access.hint"),240),mouseX,mouseY);}

    }

    private static int statusColor(CityRelationSnapshot.Status status) {
        return switch (status) { case PARTNER -> 0xFF72D6B2; case TRUSTED -> 0xFF8BC7E8;
            case TRUSTS_US -> 0xFFC5A8E5; case NONE -> 0xFF8A969E; };
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var state = menu.getRelationsState(); if (button == 0 && state != null && state.canManage()) {
            int left = width / 2 - 170, right = width / 2 + 170, y = ROW_TOP;
            List<CityRelationSnapshot> cities = visible();
            for (int i = scroll; i < cities.size() && y + ROW_HEIGHT < height - 22; i++, y += ROW_HEIGHT) {
                CityRelationSnapshot row=cities.get(i);
                if(actionBounds(y,true).contains(mouseX,mouseY)){
                    request(row.territoryAccess()?CityRelationsCommandPayload.Action.DENY_TERRITORY:CityRelationsCommandPayload.Action.ALLOW_TERRITORY,row.cityId());return true;
                }
                if (actionBounds(y,false).contains(mouseX,mouseY)) {
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
        int max = Math.max(0, visible().size() - Math.max(1, (height - ROW_TOP - 22) / ROW_HEIGHT));
        scroll = Math.max(0, Math.min(max, scroll + (vertical < 0 ? 1 : -1))); return true;
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
