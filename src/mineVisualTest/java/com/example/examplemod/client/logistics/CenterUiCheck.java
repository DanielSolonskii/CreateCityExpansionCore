package com.example.examplemod.client.logistics;

/** Pure presentation tests; no claim of Minecraft rendering or Create runtime acceptance. */
public final class CenterUiCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void run() {
        EquipmentWizardCheck.run();
        LogisticsTutorialCheck.run();
        LogisticsActivationCheck.run();
        responsiveWorkspace();
        for (String status : new String[]{"MISSING", "BLOCKED", "UNLOADED", "FAILED", "WAITING_POINT", "WAITING_ROUTE", "WAITING_TRAIN"})
            require(CenterPresentation.problem(status), "waiting and broken objects remain visible in problem filter");
        for (String status : new String[]{"COMPLETED", "CANCELLED", "AVAILABLE", "IN_TRANSIT"})
            require(!CenterPresentation.problem(status), "normal terminal states are not faults");
        require(!CenterPresentation.timedOut(1000, 12_999), "wait full response deadline");
        require(CenterPresentation.timedOut(1000, 13_000), "timeout becomes explicit");
        require(CenterPresentation.validNumber("Tariff", "0"), "free transport tariff is allowed");
        require(CenterPresentation.validNumber("Quantity", "1"), "positive quantity accepted");
        for (String value : new String[]{"", "0", "-1", "1.5", "9223372036854775808", "1e3"})
            require(!CenterPresentation.validNumber("Quantity", value), "invalid quantity rejected before sending");
        require(CenterPresentation.scrollLimit(50, 100) == 0 && CenterPresentation.scrollLimit(150, 100) == 50, "scroll never leaves empty overscroll");
        require(CenterPresentation.ease(-1, 160) == 0 && CenterPresentation.ease(160, 160) == 1, "motion bounded at endpoints");
        require(CenterPresentation.ease(80, 160) > 0 && CenterPresentation.ease(80, 160) < 1, "motion has intermediate frames");
        require(LogisticsMapOverlay.kindIcon("shipment")==7&&LogisticsMapOverlay.kindIcon("train")==2,"map parcels and trains have stable semantic icons");
        require(LogisticsMapOverlay.kindIcon("point")!=LogisticsMapOverlay.kindIcon("warehouse"),"terminal and warehouse markers remain visually distinct");
        System.out.println("PASS: " + checks + " logistics UI policy checks");
    }
    public static void main(String[] args) { run(); }

    private static void responsiveWorkspace() {
        for (int width : new int[]{320, 426, 500, 580, 640, 854, 1280}) {
            for (int height : new int[]{180, 240, 260, 360, 480, 720}) {
                var layout = CenterLayout.of(width, height);
                var viewport = layout.list();
                require(viewport.width() >= 200 && viewport.height() >= layout.rowStep() - 6, "a complete list row remains visible at Minecraft GUI scales");
                require(viewport.bottom() <= layout.map().y() - 6, "body never covers bottom controls");
                require(layout.search().right() <= layout.toolbar(0).x() - 4, "search and filters do not overlap");
                require(layout.toolbar(2).right() <= width - layout.margin(), "filter controls remain inside viewport");
                if (layout.detailWidth() > 0) require(!viewport.overlaps(layout.details()), "details do not obscure list rows");
                int endScroll = CenterPresentation.scrollLimit(layout.listContentHeight(32), viewport.height());
                var lastRow = layout.row(31, endScroll);
                require(lastRow.y() >= viewport.y() && lastRow.bottom() <= viewport.bottom(), "the last record can be fully read at the end of scrolling");
                var nav = layout.navigation();
                for (int i = 0; i < 9; i++) {
                    int scroll = Math.clamp((i + 1) * layout.navigationStep() - nav.height(), 0, layout.navigationLimit(9));
                    var item = layout.navigationRow(i, scroll);
                    require(item.y() >= nav.y() && item.bottom() <= nav.bottom(), "every section can scroll fully into view");
                }
                for (int i = 0; i < 4; i++) {
                    var card = layout.dashboardCard(i);
                    require(card.x() > layout.sidebar() && card.right() <= width - layout.margin(), "quick tasks stay within workspace");
                    for (int j = 0; j < i; j++) require(!card.overlaps(layout.dashboardCard(j)), "quick tasks remain distinct");
                }
                for (int index = 0; index < 32; index++) {
                    var row = layout.row(index, 17);
                    require(row.contains(row.x() + 1, row.y() + 1), "rendered row is clickable");
                    require(!row.contains(row.right(), row.bottom() - 1), "row right edge is exclusive");
                    require(!row.contains(row.x(), row.bottom()), "row gap cannot select another object");
                }
            }
        }
    }
}
