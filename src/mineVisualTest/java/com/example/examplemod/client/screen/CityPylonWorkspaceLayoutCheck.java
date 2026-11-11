package com.example.examplemod.client.screen;

import com.example.examplemod.city.CityPlot;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class CityPylonWorkspaceLayoutCheck {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int width : new int[]{320, 427, 539, 540, 640, 854, 1024, 1920}) {
            for (int height : new int[]{180, 240, 360, 480, 720, 1080}) {
                var center = CityPylonWorkspaceLayout.calculate(width, height, false, true);
                require(center.sidebarWidth() == 0 && center.scale() > 0, "city center uses the whole panel");
                double centerRight = center.originX() + center.virtualWidth() * center.scale();
                double centerBottom = center.originY() + center.virtualHeight() * center.scale();
                require(centerRight <= center.x() + center.width() + 0.001
                        && centerBottom <= center.y() + center.height() + 0.001, "city center fits the content viewport");
                require(Math.abs(center.localX(centerRight) - center.virtualWidth()) < 0.001
                        && Math.abs(center.localY(centerBottom) - center.virtualHeight()) < 0.001,
                        "city center click positions match the scaled rendering");
                int centerFrameRight = center.frameX()+center.frameWidth();
                int closedInset = CityPylonNewspaper.trigger(center).right()-centerFrameRight;
                int openInset = CityPylonNewspaper.page(center,width).right()-centerFrameRight;
                for (int inset : new int[]{closedInset, (closedInset+openInset)/2, openInset}) {
                    var withPaper = CityPylonWorkspaceLayout.calculate(width,height,false,true,inset);
                    int frameRight = withPaper.frameX()+withPaper.frameWidth();
                    require(withPaper.frameX() >= 12 && frameRight + inset <= width - 12,
                            "newspaper rail fits beside the city center throughout its slide");
                    var trigger = CityPylonNewspaper.trigger(withPaper);
                    var paper = CityPylonNewspaper.page(withPaper,width);
                    require(trigger.x() > frameRight && trigger.right() <= paper.x()
                                    && trigger.height() >= 2 * trigger.width(),
                            "the newspaper trigger is tall, narrow and outside the city frame");
                    require(paper.y() == withPaper.frameY() && paper.height() == withPaper.frameHeight(),
                            "the newspaper shares the city frame's top and bottom edges");
                    double mapX = withPaper.originX() + withPaper.virtualWidth() * withPaper.scale() * 0.88;
                    double mapY = withPaper.originY() + withPaper.virtualHeight() * withPaper.scale() * 0.7;
                    require(withPaper.contains(mapX,mapY) && mapX < frameRight,
                            "minimap stays inside the city content while the newspaper opens");
                }
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
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var base = new CityPlot(dimension, 0, 0);
        require(CityPylonOverviewScreen.countChunks(Set.of()) == 0, "no claims cover no chunks");
        require(CityPylonOverviewScreen.countChunks(Set.of(base, new CityPlot(dimension, 1, 0))) == 28,
                "adjacent plots share chunks at the boundary");
        require(CityPylonOverviewScreen.countChunks(Set.of(base, new CityPlot(dimension, -1, 0))) == 32,
                "negative plot coordinates use floor division");
        System.out.println("PASS: "+checks+" workspace viewport, navigation and input checks");
    }
}
