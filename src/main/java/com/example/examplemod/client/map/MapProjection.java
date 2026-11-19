package com.example.examplemod.client.map;

/** Camera used to paint the current frame and hit-test it until the next paint. GUI units. */
final class MapProjection {
    private double centerX, centerZ, originX, originY, zoom = 1.25;

    void frame(double x, double z, double scale, int left, int top, int width, int height) {
        centerX = x;
        centerZ = z;
        zoom = scale;
        originX = left + width * .5;
        originY = top + height * .5;
    }

    double screenX(double x) { return originX + (x-centerX)*zoom; }
    double screenY(double z) { return originY + (z-centerZ)*zoom; }
    double worldX(double sx) { return centerX + (sx-originX)/zoom; }
    double worldZ(double sy) { return centerZ + (sy-originY)/zoom; }
    double offsetX(double sx) { return sx-originX; }
    double offsetY(double sy) { return sy-originY; }
}
