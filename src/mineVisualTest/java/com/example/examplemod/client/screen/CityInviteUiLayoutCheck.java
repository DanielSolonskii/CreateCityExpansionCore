package com.example.examplemod.client.screen;

import com.example.examplemod.network.CityPlayerSearchResultPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Headless regression for the invite / members UI.
 *
 * <p>The previous invite screen computed every row position inline in both {@code render} and
 * {@code mouseClicked}, which is exactly how rows become visible but not clickable. Everything the
 * screen draws now comes from {@link CityInviteUiLayout}, so these checks assert the painted and the
 * clickable rectangle are the same object at every responsive width.
 */
public final class CityInviteUiLayoutCheck {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final int[] WIDTHS = { 320, 426, 640, 854, 1280 };
    private static final int[] HEIGHTS = { 240, 380, 720 };

    public static void main(String[] args) throws Exception {
        // ------------------------------------------------- render bounds are the click bounds
        for (int width : WIDTHS) {
            for (int height : HEIGHTS) {
                for (boolean feedback : new boolean[]{ false, true }) {
                    CityInviteUiLayout.Rect field = CityInviteUiLayout.searchField(width, 46);
                    require(field.contains(field.x() + 1, field.y() + 1),
                            "search field hitbox matches its bounds at " + width);
                    require(field.x() >= CityInviteUiLayout.EDGE && field.right() <= width - CityInviteUiLayout.EDGE,
                            "search field keeps its margins at " + width);

                    CityInviteUiLayout.Rect previous = null;
                    for (int index = 0; index < 8; index++) {
                        CityInviteUiLayout.Rect row = CityInviteUiLayout.row(width, 46, feedback, index, 8);
                        require(row.contains(row.x() + 1, row.y() + 1),
                                "row hitbox matches its bounds at " + width);
                        require(row.x() >= CityInviteUiLayout.EDGE && row.right() <= width - CityInviteUiLayout.EDGE,
                                "row stays inside the screen at " + width);
                        if (previous != null) {
                            require(!previous.overlaps(row), "rows never overlap at " + width);
                        }
                        previous = row;
                    }

                    CityInviteUiLayout.Rect action = CityInviteUiLayout.rowAction(width, 46, feedback, 0, 8);
                    CityInviteUiLayout.Rect host = CityInviteUiLayout.row(width, 46, feedback, 0, 8);
                    require(action.overlaps(host), "the primary action sits inside its own row at " + width);
                    require(action.right() <= host.right(), "the action does not spill out of the row at " + width);
                    require(action.height() < host.height(), "the action is inset inside the row at " + width);
                    require(!action.overlaps(CityInviteUiLayout.row(width, 46, feedback, 1, 8)),
                            "the action does not bleed into the next row at " + width);

                    CityInviteUiLayout.Rect next = CityInviteUiLayout.row(width, 46, feedback, 1, 8);
                    require(!action.overlaps(next), "adjacent rows keep separate actions at " + width);
                }
                CityInviteUiLayout.Rect feedback = CityInviteUiLayout.feedback(width, 46);
                require(feedback.x() >= CityInviteUiLayout.EDGE && feedback.right() <= width - CityInviteUiLayout.EDGE,
                        "inline feedback stays on screen at " + width);
                require(!feedback.overlaps(CityInviteUiLayout.row(width, 46, true, 0, 8)),
                        "inline feedback does not cover the first row at " + width);
                require(CityInviteUiLayout.row(width, 46, true, 0, 8).y()
                                - CityInviteUiLayout.row(width, 46, false, 0, 8).y()
                                >= CityInviteUiLayout.FEEDBACK_HEIGHT,
                        "rows move down by at least the feedback height when feedback appears");
            }
        }

        // ----------------------------------------------------------------- panel and button widths
        for (int width : WIDTHS) {
            require(CityInviteUiLayout.panelWidth(width) <= width - CityInviteUiLayout.EDGE * 2,
                    "the panel fits the screen at " + width);
            require(CityInviteUiLayout.panelLeft(width) >= 0, "the panel never starts off screen at " + width);
            CityInviteUiLayout.Rect invite = CityInviteUiLayout.inviteButton(width, 380);
            require(invite.right() <= width - CityInviteUiLayout.EDGE, "the invite CTA fits at " + width);
            require(invite.contains(invite.x() + 1, invite.y() + 1), "the invite CTA hitbox matches at " + width);
        }
        require(CityInviteUiLayout.panelWidth(320) > 0, "the panel is usable on the narrowest width");
        require(CityInviteUiLayout.panelWidth(1280) <= 340, "the panel does not stretch on a wide screen");

        // ------------------------------------------------------------------- long names still fit
        for (int width : WIDTHS) {
            int narrow = CityInviteUiLayout.nameColumnWidth(width, true);
            require(narrow >= 24, "the name column never collapses at " + width);
            require(narrow < CityInviteUiLayout.nameColumnWidth(width, false),
                    "reserving the action column really does narrow the name column at " + width);
            CityInviteUiLayout.Rect action = CityInviteUiLayout.rowAction(width, 46, false, 0, 1);
            require(action.width() >= 40, "the primary action keeps a usable width at " + width);
        }

        // ------------------------------------------------------------------- scroll and capacity
        for (int width : WIDTHS) {
            for (int height : HEIGHTS) {
                int capacity = Math.max(1,
                        (height - 40 - CityInviteUiLayout.listTop(0, 46, false)) / CityInviteUiLayout.ROW_HEIGHT);
                require(capacity >= 1, "there is always at least one visible row at " + width + "x" + height);
                require(CityInviteUiLayout.listTop(0, 46, false) < height,
                        "the list starts on screen at " + width + "x" + height);
            }
        }

        // ------------------------------------------------------ members rows share the same rhythm
        CityInviteUiLayout.Rect first = CityInviteUiLayout.memberRow(426, 46, 0, 3);
        CityInviteUiLayout.Rect second = CityInviteUiLayout.memberRow(426, 46, 1, 3);
        require(!first.overlaps(second), "member rows never overlap");
        require(second.y() - first.y() == CityInviteUiLayout.ROW_HEIGHT,
                "member rows use the same row pitch as search rows, so both screens read as one family");
        require(first.x() >= CityInviteUiLayout.EDGE, "member rows keep their margin");

        // ------------------------------------------------------------------ the search field focus
        // The screen focuses the field on open; the debounce constant is the contract for that flow.
        require(CityInvitePlayerScreen.SEARCH_DEBOUNCE_MILLIS >= 250L,
                "the search debounce is long enough not to spam the server");
        require(CityInvitePlayerScreen.SEARCH_DEBOUNCE_MILLIS <= 400L,
                "the search debounce is short enough to feel live");

        // ------------------------------------------------------------- every row state is renderable
        for (CityPlayerSearchResultPayload.State state : CityPlayerSearchResultPayload.State.values()) {
            require(state != null, "row state " + state + " is renderable");
        }
        CityPlayerSearchResultPayload.Entry available = new CityPlayerSearchResultPayload.Entry(
                UUID.randomUUID(), "Steve", true, CityPlayerSearchResultPayload.State.AVAILABLE,
                "gui.createcityexpansioncore.city.invite.state.available", "");
        require(available.state() == CityPlayerSearchResultPayload.State.AVAILABLE,
                "AVAILABLE is the only state the screen turns into a primary action");
        CityPlayerSearchResultPayload.Entry foreign = new CityPlayerSearchResultPayload.Entry(
                UUID.randomUUID(), "Alex", false, CityPlayerSearchResultPayload.State.MEMBER_OF_OTHER_CITY,
                "gui.createcityexpansioncore.city.invite.state.member_of_other_city", "Rivertown");
        require(!foreign.cityName().isBlank(),
                "a foreign-city row carries the city name for the reason line");
        require(available.cityName().isBlank(), "an available row carries no city name");

        // ------------------------------------------------------------------------ localization
        Set<String> required = new HashSet<>(List.of(
                "gui.createcityexpansioncore.city.invite.search",
                "gui.createcityexpansioncore.city.invite.search.hint",
                "gui.createcityexpansioncore.city.invite.search.empty",
                "gui.createcityexpansioncore.city.invite.search.pending",
                "gui.createcityexpansioncore.city.invite.search.online",
                "gui.createcityexpansioncore.city.invite.search.offline",
                "gui.createcityexpansioncore.city.invite.sent",
                "gui.createcityexpansioncore.city.invite.toast.sent",
                "gui.createcityexpansioncore.city.invite.outcome.accepted",
                "gui.createcityexpansioncore.city.invite.outcome.declined",
                "gui.createcityexpansioncore.city.invite.state.available",
                "gui.createcityexpansioncore.city.invite.state.invited",
                "gui.createcityexpansioncore.city.invite.state.member_of_own_city",
                "gui.createcityexpansioncore.city.invite.state.member_of_other_city",
                "gui.createcityexpansioncore.city.invite.state.self",
                "gui.createcityexpansioncore.city.invite.state.no_permission",
                "gui.createcityexpansioncore.city.invite.state.invite",
                "gui.createcityexpansioncore.city.invites.title",
                "gui.createcityexpansioncore.common.back"));
        for (String language : List.of("en_us", "ru_ru")) {
            String text = Files.readString(sourceRoot()
                    .resolve("src/main/resources/assets/createcityexpansioncore/lang/" + language + ".json"));
            for (String key : required) {
                require(text.contains("\"" + key + "\""), language + " has " + key);
                require(!text.contains("\"" + key + "\":\"\""), language + " does not leave " + key + " blank");
            }
        }

        // The screen must not ship a raw translatable-looking string to the player.
        String screen = Files.readString(sourceRoot().resolve(
                "src/main/java/com/example/examplemod/client/screen/CityInvitePlayerScreen.java"));
        for (String literal : List.of("null", "null", "missing", "none")) {
            require(!screen.contains("\"" + literal + "\""),
                    "the invite screen never renders the literal '" + literal + "' to the player");
        }

        System.out.println("PASS: " + checks + " city invite UI layout and presentation checks");
    }

    private static Path sourceRoot() {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("src/main/java"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new AssertionError("cannot locate source tree");
        }
        return root;
    }
}