package com.example.examplemod.client.screen;

public final class CityPylonWorkspaceLayoutCheck {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int width : new int[]{320, 427, 539, 540, 640, 854, 1024, 1920}) {
            for (int height : new int[]{180, 240, 360, 480, 720, 1080}) {
                for (boolean root : new boolean[]{false, true}) {
                    var l = CityPylonWorkspaceLayout.calculate(width, height, root);
                    require(l.scale() > 0 && Float.isFinite(l.scale()), "valid scale");
                    require(l.x()+l.width() <= width-8 && l.y()+l.height() <= height-24, "viewport inside window");
                    require(l.navY(4)+l.navHeight() <= height-34, "map navigation does not overlap Back");
                    require(l.navHeight() >= 12, "navigation remains clickable");
                    double left = root ? 180 : 0, top = root ? 76 : 0;
                    double right = left+l.virtualWidth(), bottom = top+l.virtualHeight();
                    for(double x:new double[]{left, (left+right)/2, right}) {
                        double sx=l.originX()+x*l.scale();
                        require(Math.abs(l.localX(sx)-x)<0.001,"horizontal hit test matches rendered point");
                        require(sx >= l.x()-0.001 && sx <= l.x()+l.width()+0.001,"whole page fits horizontally");
                    }
                    for(double y:new double[]{top, (top+bottom)/2, bottom}) {
                        double sy=l.originY()+y*l.scale();
                        require(Math.abs(l.localY(sy)-y)<0.001,"vertical hit test matches rendered point");
                        require(sy >= l.y()-0.001 && sy <= l.y()+l.height()+0.001,"whole page fits vertically");
                    }
                    require(l.contains(l.x(),l.y()),"top-left accepts input");
                    require(!l.contains(l.x()-1,l.y()),"sidebar cannot click content");
                    require(!l.contains(l.x()+l.width(),l.y()),"right edge excludes input");
                    require(!l.contains(l.x(),l.y()+l.height()),"footer cannot click content");
                }
            }
        }
        System.out.println("PASS: "+checks+" workspace viewport, navigation and input checks");
    }
}
