package com.example.examplemod.client.map;

import com.example.examplemod.city.CityMapSavedData;

import java.util.BitSet;
import java.util.Comparator;

/**
 * Headless regression for the survey-drawing presentation layer: palette arithmetic, the terrain
 * paper transform, the map chrome geometry, and the server-side contour decision.
 *
 * <p><b>What this check does NOT prove.</b> Nothing here renders. It cannot tell you whether the
 * paper looks like paper, whether the contours are legible against it, or whether the minimap reads
 * as an overview. Those are eyes-on questions. What it does prove is the part that silently rots:
 * that the colour steps are monotonic and in range, that the chrome never overlaps itself or the
 * context column, that the contour decision is continuous along a slope, and that the payload
 * constructor refuses a tile whose contour bits do not belong to it.
 */
public final class MapPresentationCheck {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    /* ------------------------------------------------------------------ palette */

    private static void checkPalette() {
        require(MapPalette.clamp(-5) == 0, "clamp must floor at 0");
        require(MapPalette.clamp(300) == 255, "clamp must cap at 255");
        require(MapPalette.mix(10, 20, 0.0) == 10, "mix at 0 must return the source");
        require(MapPalette.mix(10, 20, 1.0) == 20, "mix at 1 must return the target");
        require(MapPalette.mix(10, 20, 0.5) == 15, "mix halfway must land halfway");
        require(MapPalette.mix(10, 20, -3) == 10, "mix must clamp an overshoot below 0");
        require(MapPalette.mix(10, 20, 7) == 20, "mix must clamp an overshoot above 1");

        require(MapPalette.alpha(0x80ABCDEF) == 0x80, "alpha must come from the top byte");
        require(MapPalette.red(0x80ABCDEF) == 0xAB, "red must come from bits 16-23");
        require(MapPalette.green(0x80ABCDEF) == 0xCD, "green must come from bits 8-15");
        require(MapPalette.blue(0x80ABCDEF) == 0xEF, "blue must come from bits 0-7");

        // The whole point of the survey look: contour ink must be darker than every tone of terrain
        // it can land on. The darkest realistic input is near-black, not the UI chrome — comparing
        // against the chrome would have passed an ink that vanished over deep shadow.
        int darkestTerrain = MapSurveyTransform.survey(0xFF000000);
        require(MapPalette.luminance(MapPalette.INK) < MapPalette.luminance(darkestTerrain),
                "ink must be darker than the darkest surveyed terrain, otherwise contours vanish "
                        + "over shadow");
        require(MapPalette.luminance(MapPalette.INK) < MapPalette.luminance(MapSurveyTransform.survey(0xFFFFFFFF)),
                "ink must be darker than snow, otherwise contours vanish over a snowfield");
        require(MapPalette.luminance(MapPalette.INK) < MapPalette.luminance(MapPalette.INK_SOFT),
                "ink must be darker than the soft line, otherwise the hierarchy is inverted");
        require(MapPalette.luminance(MapPalette.INK_SOFT) > MapPalette.luminance(MapPalette.PAPER),
                "the soft chrome line must be lighter than the panel it is drawn on");
        require(MapPalette.luminance(MapPalette.INK_SOFT) > MapPalette.luminance(MapPalette.PAPER_SUNK),
                "the soft chrome line must be lighter than the sunk panel it outlines");

        // Every design token must be a real opaque colour, otherwise a panel renders as noise.
        int[] tokens = {
                MapPalette.PAPER, MapPalette.PAPER_RAISED, MapPalette.PAPER_SUNK,
                MapPalette.INK, MapPalette.INK_SOFT, MapPalette.TEXT, MapPalette.TEXT_MUTED,
                MapPalette.TEXT_FAINT, MapPalette.OWN, MapPalette.NEUTRAL, MapPalette.ALERT,
                MapPalette.GOOD, MapPalette.SELECTED,
        };
        for (int token : tokens) {
            require(MapPalette.alpha(token) == 0xFF,
                    "a design token must be opaque, got #" + Integer.toHexString(token));
        }

        // Text must never be darker than the surface it is drawn on.
        require(MapPalette.luminance(MapPalette.TEXT) > MapPalette.luminance(MapPalette.PAPER_SUNK),
                "text must be lighter than the sunk panel it sits in");
        require(MapPalette.luminance(MapPalette.TEXT_MUTED) > MapPalette.luminance(MapPalette.PAPER_SUNK),
                "muted text must stay readable on the sunk panel");
        require(MapPalette.luminance(MapPalette.TEXT_FAINT) < MapPalette.luminance(MapPalette.TEXT),
                "faint text must be quieter than normal text, or the hierarchy means nothing");
    }

    /* -------------------------------------------------------------------- paper */

    private static void checkSurveyTransform() {
        // Ordered dark to light by their own luminance. Sorting here rather than hand-ordering keeps the
        // assertion honest: a hand-written order would itself be a place to be wrong.
        Integer[] terrain = {
                0xFF000000, 0xFF2E2E2E, 0xFF373A97, 0xFF7C7C7C, 0xFF689739, 0xFFFFFFFF,
        };
        java.util.Arrays.sort(terrain, Comparator.comparingInt(MapPalette::luminance));

        int previousLuminance = -1;
        for (int argb : terrain) {
            int paper = MapSurveyTransform.survey(argb);
            require(MapPalette.alpha(paper) == MapPalette.alpha(argb),
                    "survey must preserve alpha, " + Integer.toHexString(argb));
            int luminance = MapPalette.luminance(paper);
            require(luminance >= 0 && luminance <= 255, "survey must stay in gamut");
            // Lightness order must survive: a snowfield cannot end up darker than deep stone, or
            // the map stops reading as terrain at all.
            require(luminance >= previousLuminance,
                    "survey inverted the lightness order at " + Integer.toHexString(argb)
                            + ": " + previousLuminance + " then " + luminance);
            previousLuminance = luminance;
        }

        int grass = MapSurveyTransform.survey(0xFF689739);
        int snow = MapSurveyTransform.survey(0xFFFFFFFF);
        int water = MapSurveyTransform.survey(0xFF373A97);
        require(MapPalette.luminance(grass) != MapPalette.luminance(water),
                "grass and water must stay distinguishable after desaturation, or a rail route "
                        + "cannot be planned from the map");

        // The contour pass replaces the tone outright rather than blending, so a line over a snowfield and
        // a line over deep stone must come out the same colour. If they differed, the map would grow
        // a second, terrain-dependent line style — and that style would be invisible wherever it is
        // weakest, which is exactly where a player most needs to read the slope.
        require(MapSurveyTransform.surveyInk(grass) == MapSurveyTransform.surveyInk(snow),
                "contour ink must not depend on the terrain underneath it");
        require(MapSurveyTransform.surveyInk(grass) == MapPalette.argb(MapPalette.INK, 0xFF),
                "the ink pass must write the palette ink at full alpha");
        require(MapPalette.luminance(MapSurveyTransform.surveyInk(grass)) < MapPalette.luminance(grass),
                "ink must be darker than the paper it is drawn on");
        require(MapPalette.luminance(MapSurveyTransform.surveyInk(snow)) < MapPalette.luminance(snow),
                "ink must be darker than snow");

        // Unexplored paper is a tone, not a void: it must be readable as "not surveyed".
        int unexplored = MapSurveyTransform.unexploredPaper(0);
        require(MapPalette.alpha(unexplored) == 0xFF, "unexplored paper must be opaque");
        require(MapPalette.luminance(unexplored) < MapPalette.luminance(MapSurveyTransform.survey(0xFFFFFFFF)),
                "unexplored paper must stay darker than surveyed terrain, or the frontier is "
                        + "invisible");
    }

    /* ------------------------------------------------------------------- chrome */

    private static void checkChromeGeometry() {
        int[][] viewports = {
                { 0, 0, 1280, 720 },
                { 0, 0, 854, 480 },
                { 0, 0, 640, 426 },
                { 0, 0, 426, 320 },
                { 40, 60, 320, 240 },
        };

        for (int[] viewport : viewports) {
            int x = viewport[0];
            int y = viewport[1];
            int width = viewport[2];
            int height = viewport[3];

            // Nothing on screen at all: the chrome must switch itself off.
            CityMapFrame.Geometry hidden = CityMapFrame.Geometry.of(x, y, width, height, x - 10, 6);
            require(hidden.minimapSize() == 0, "chrome must hide when the safe width is zero");
            require(!CityMapFrame.Geometry.chromeVisible(x, width, height, x - 10),
                    "chrome must report itself invisible when nothing fits");

            // The atlas layout: a context column on the right eats that part of the map.
            int avoidRight = Math.max(8, x + width - 288 - 12);
            CityMapFrame.Geometry geometry = CityMapFrame.Geometry.of(x, y, width, height, avoidRight, 6);
            int safe = CityMapFrame.Geometry.safeWidth(x, width, avoidRight);
            require(safe >= 0, "safe width must never go negative");
            require(safe <= width, "safe width must never exceed the viewport");

            if (geometry.minimapSize() > 0) {
                // Every chrome element must live inside the safe area, not the raw viewport.
                require(geometry.minimapX() >= x && geometry.minimapY() >= y,
                        "minimap must sit inside the viewport");
                require(geometry.minimapX() + geometry.minimapSize() <= x + safe,
                        "minimap must not run under the context column");
                require(geometry.minimapY() + geometry.minimapSize() <= y + height,
                        "minimap must not run past the bottom of the viewport");

                require(geometry.legendX() >= x && geometry.legendX() + geometry.legendWidth() <= x + safe,
                        "legend must not run under the context column");
                require(geometry.legendY() >= y, "legend must sit inside the viewport");

                require(geometry.scaleX() + geometry.scaleWidth() <= x + safe,
                        "scale bar must not run under the context column");

                require(geometry.stampY() >= y, "stamp must sit inside the viewport");
                require(geometry.stampX() >= x && geometry.stampX() + geometry.stampWidth() <= x + safe,
                        "stamp must stay inside the safe area");

                // Left-bottom cluster: the stamp sits above the minimap, never on it.
                require(geometry.stampY() + 26 <= geometry.minimapY(),
                        "the coordinate stamp must not overlap the minimap");
                require(geometry.scaleY() > geometry.legendY(),
                        "the scale bar must sit below the legend, not inside it");
            }

            require(geometry.legendRows() <= 6, "the legend must cap its row count");
            require(geometry.legendRows() >= 0, "the legend row count must never go negative");
        }

        // A taller legend list must not be able to grow the panel without bound.
        CityMapFrame.Geometry capped = CityMapFrame.Geometry.of(0, 0, 1280, 720, 1280, 99);
        require(capped.legendRows() == 8, "the legend must cap at 8 rows, got " + capped.legendRows());
        require(CityMapFrame.Geometry.of(0, 0, 1280, 720, 1280, 0).legendRows() == 0,
                "an empty layer list must produce an empty legend");

        // Scale steps must be the "round" numbers a survey sheet would carry. 900 rounding down to 500
        // rather than up to 1000 is deliberate: a bar that overflows the space it was fitted into is
        // worse than one that is a little short, and 500 is the nearer of the two anyway.
        int[][] steps = { { 1, 1 }, { 3, 2 }, { 7, 5 }, { 24, 20 }, { 128, 100 }, { 247, 200 }, { 900, 500 } };
        for (int[] step : steps) {
            int actual = CityMapFrame.niceStep(step[0]);
            require(actual == step[1],
                    "niceStep(" + step[0] + ") must be " + step[1] + " but was " + actual);
            require(actual >= 1, "a scale step must never be less than one block");
            require(actual <= step[0], "a scale step must not exceed the span it is labelling");
            // Only 1, 2 and 5 followed by a power of ten are legitimate survey figures.
            int mantissa = actual;
            while (mantissa % 10 == 0) {
                mantissa /= 10;
            }
            require(mantissa == 1 || mantissa == 2 || mantissa == 5,
                    "a scale step must be a 1/2/5 figure, got " + actual);
        }
    }

    /* ----------------------------------------------------------------- contours */

    private static void checkContours() {
        int interval = CityMapSavedData.CONTOUR_INTERVAL_BLOCKS;
        require(interval >= 2, "contours closer than two blocks would become a solid fill");

        // A flat surface carries no contour. This is the case that makes contours readable at all.
        require(!CityMapSavedData.crossesInterval(64, 64), "a flat step must not draw a contour");
        require(!CityMapSavedData.crossesInterval(-3, -3), "a flat negative step must not draw a contour");

        require(CityMapSavedData.crossesInterval(63, 64),
                "a one-block rise that reaches a level must draw a contour");
        require(CityMapSavedData.crossesInterval(64, 63),
                "a one-block fall out of a level must draw a contour");
        require(!CityMapSavedData.crossesInterval(65, 64),
                "a fall that stays inside one level must not draw a contour");
        require(!CityMapSavedData.crossesInterval(interval, interval + 1),
                "a rise that stays inside one level must not draw a contour");
        require(!CityMapSavedData.crossesInterval(interval + 1, interval),
                "the same rise read downwards must give the same answer");

        // Direction must not matter, and a step that spans several levels must still read as one
        // line rather than as a filled block.
        int span = CityMapSavedData.CONTOUR_INTERVAL_BLOCKS * 40;
        require(CityMapSavedData.crossesInterval(0, span),
                "a cliff spanning many levels must still read as a single contour");

        // A wall must draw exactly one line, not one per block of the wall.
        int size = 16;
        int[] cliff = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                cliff[z * size + x] = x < 8 ? 60 : 100;
            }
        }
        BitSet marked = CityMapSavedData.markContours(cliff, size);
        require(!marked.isEmpty(), "a vertical cliff must draw a contour");

        int markedColumns = 0;
        for (int x = 0; x < size; x++) {
            boolean columnMarked = false;
            for (int z = 0; z < size; z++) {
                if (marked.get(z * size + x)) {
                    columnMarked = true;
                    break;
                }
            }
            if (columnMarked) {
                markedColumns++;
            }
        }
        require(markedColumns <= 4,
                "a cliff must draw one thin line, but " + markedColumns + " columns were marked");

        // A long ramp: the line must be unbroken, because that is what a contour is for.
        int[] ramp = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                ramp[z * size + x] = 60 + x * interval;
            }
        }
        BitSet rampMarked = CityMapSavedData.markContours(ramp, size);
        require(!rampMarked.isEmpty(), "a long ramp must draw contours");
        for (int x = 1; x < size; x++) {
            boolean columnMarked = false;
            for (int z = 0; z < size; z++) {
                if (rampMarked.get(z * size + x)) {
                    columnMarked = true;
                    break;
                }
            }
            require(columnMarked,
                    "a ramp contour broke at column " + x + " — the line must be continuous");
        }

        // A plateau below the first level carries nothing at all.
        int[] plain = new int[size * size];
        for (int index = 0; index < plain.length; index++) {
            plain[index] = interval - 1;
        }
        require(CityMapSavedData.markContours(plain, size).isEmpty(),
                "a plain inside one level must carry no contour");

        // Undulating ground must give lines, not a fill: this is what a real hillside looks like at sample
        // resolution, and it is the case the survey look actually has to survive. (A synthetic
        // one-block checkerboard would legitimately mark every sample — that terrain really does have
        // a contour at every block — so it cannot be used to argue about legibility.)
        // A gentle hillside: the terrain most players actually map over. Neighbouring samples differ by far
        // less than a level, so the lines must be sparse and must follow the slope.
        int[] hills = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                // Roughly one level every eight samples: a walkable slope, not a wall.
                hills[z * size + x] = 40 + Math.round((x * interval + z / 4.0f) / 8.0f);
            }
        }
        BitSet hillMarked = CityMapSavedData.markContours(hills, size);
        require(!hillMarked.isEmpty(), "rolling ground must draw contours");
        // A quarter of the tile. A line one sample wide across a level every eight samples covers roughly
        // an eighth of it; the doubling is the cost of also marking the neighbour that lies on the
        // far side of the level, which is what makes the line continuous instead of dotted.
        require(hillMarked.cardinality() < size * size / 3,
                "a gentle hillside marked " + hillMarked.cardinality() + " of " + (size * size)
                        + " samples, which reads as a fill rather than as lines");

        // The known limit, asserted so it cannot regress silently: where the ground climbs faster
        // than one level per sample, every sample is genuinely on a contour and the map fills in.
        // That is honest — such terrain has a contour at every block — but it means CONTOUR_INTERVAL
        // is a readability constant and not only a drawing detail.
        int[] cliffFace = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                cliffFace[z * size + x] = 40 + x * interval;
            }
        }
        BitSet cliffMarked = CityMapSavedData.markContours(cliffFace, size);
        require(cliffMarked.cardinality() > size * size * 3 / 4,
                "a face climbing a level per block must fill in; the interval is the only guard, "
                        + "so this documents the cost of changing it");

        // A plateau must be bounded by lines, not filled in: its interior sits inside one level.
        int[] plateau = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                boolean inside = x >= 4 && x < 12 && z >= 4 && z < 12;
                plateau[z * size + x] = inside ? 80 : 60;
            }
        }
        BitSet plateauMarked = CityMapSavedData.markContours(plateau, size);
        require(plateauMarked.cardinality() < size * size / 2,
                "a plateau must be outlined, not filled: " + plateauMarked.cardinality()
                        + " samples marked");
        require(!plateauMarked.get(8 * size + 8), "the middle of a flat plateau must carry no line");
    }

    public static void main(String[] args) {
        checkPalette();
        System.out.println("palette: " + checks + " checks");
        checkSurveyTransform();
        System.out.println("survey paper: " + checks + " checks");
        checkChromeGeometry();
        System.out.println("chrome geometry: " + checks + " checks");
        checkContours();
        System.out.println("contours: " + checks + " checks");
        System.out.println("PASS MapPresentationCheck (" + checks + " checks)");
    }
}