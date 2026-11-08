package com.example.examplemod.client.logistics;

/** Pure presentation tests; no claim of Minecraft rendering or Create runtime acceptance. */
public final class CenterUiCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void run() {
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
}
