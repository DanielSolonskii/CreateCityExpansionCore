package com.example.examplemod.client.map;

import com.example.examplemod.client.screen.AtlasPlaces;
import com.example.examplemod.places.AtlasPlace;
import com.example.examplemod.projects.Project;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Pixel-art symbols shared by the map, its search results, the editor and the selected card. */
public enum AtlasPlaceIcon {
    HOME(0), SHOP(1), FACTORY(2), WAREHOUSE(3), MINE(4), PUBLIC(5), OTHER(6), PERSONAL(7),
    STATION(8), TRAIN(9), CITY_PROJECT(10), SERVER_PROJECT(11);

    private static final int CELL = 20, WIDTH = CELL * values().length, HEIGHT = CELL * 4;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "createcityexpansioncore", "textures/gui/map/place_markers.png");
    private final int tile;

    AtlasPlaceIcon(int tile) {
        this.tile = tile;
    }

    public static AtlasPlaceIcon forCategory(AtlasPlace.Category category) {
        return switch (category) {
            case HOME -> HOME;
            case SHOP -> SHOP;
            case FACTORY -> FACTORY;
            case WAREHOUSE -> WAREHOUSE;
            case MINE -> MINE;
            case PUBLIC -> PUBLIC;
            case OTHER -> OTHER;
            case STATION -> STATION;
        };
    }

    public static AtlasPlaceIcon forProject(Project.Scope scope) {
        return scope == Project.Scope.SERVER ? SERVER_PROJECT : CITY_PROJECT;
    }

    public static AtlasPlaceIcon forTransport(String kind) {
        return switch (kind) {
            case "station" -> STATION;
            case "train" -> TRAIN;
            case "warehouse" -> WAREHOUSE;
            default -> null;
        };
    }

    public static AtlasPlaceIcon forSelection(CityMapPanel.Selection selection, ResourceLocation dimension) {
        if (selection == null) return null;
        if (selection.kind() == CityMapPanel.SelectionKind.INDUSTRY) return MINE;
        if (selection.atlasIcon() != null) return selection.atlasIcon();
        if (selection.kind() == CityMapPanel.SelectionKind.WAYPOINT) return PERSONAL;
        if (selection.kind() != CityMapPanel.SelectionKind.PLACE) return null;
        AtlasPlace place = AtlasPlaces.get(selection.id(), dimension);
        return place == null ? null : forCategory(place.category());
    }

    public void draw(GuiGraphics graphics, int x, int y, int size) {
        graphics.blit(TEXTURE, x, y, size, size, tile * CELL + 2, 2, 16, 16, WIDTH, HEIGHT);
    }

    static int radius(double zoom) {
        return zoom < .8 ? 7 : 10;
    }

    static boolean contains(double mouseX, double mouseY, int centerX, int centerY, int radius) {
        int left = centerX - radius, top = centerY - radius;
        if (mouseX < left || mouseX >= centerX + radius || mouseY < top || mouseY >= centerY + radius) return false;
        int corner = Math.round(4f * radius / CELL);
        return mouseX >= left + corner && mouseX < centerX + radius - corner
                || mouseY >= top + corner && mouseY < centerY + radius - corner;
    }

    public void badge(GuiGraphics graphics, int centerX, int centerY, int radius, boolean selected, boolean hovered) {
        // One textured quad keeps the frame and its artwork together in the map's managed batch.
        int row = selected ? 3 : hovered ? 2 : 1;
        float u0 = (float)(tile * CELL) / WIDTH, u1 = (float)((tile + 1) * CELL) / WIDTH;
        float v0 = (float)(row * CELL) / HEIGHT, v1 = (float)((row + 1) * CELL) / HEIGHT;
        int left = centerX - radius, top = centerY - radius, right = centerX + radius, bottom = centerY + radius;
        var matrix = graphics.pose().last().pose();
        var vertices = graphics.bufferSource().getBuffer(RenderType.text(TEXTURE));
        vertices.addVertex(matrix,left,top,0).setColor(-1).setUv(u0,v0).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix,left,bottom,0).setColor(-1).setUv(u0,v1).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix,right,bottom,0).setColor(-1).setUv(u1,v1).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix,right,top,0).setColor(-1).setUv(u1,v0).setLight(LightTexture.FULL_BRIGHT);
    }
}
