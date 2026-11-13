package com.example.examplemod.journey;

import com.example.examplemod.client.journey.JourneyTrial;
import com.example.examplemod.journey.JourneyModel.ObjectiveKind;
import com.example.examplemod.journey.JourneyModel.Point;
import java.util.List;

final class JourneyTrialCheck {
    private static final String WORLD = "minecraft:overworld";

    static void run() {
        Point welcome = new Point(WORLD, 12, 64, 20);
        Point pylon = new Point(WORLD, 30, 64, 20);
        Point pickup = new Point(WORLD, 44, 64, 20);
        var draft = new JourneyRouteDraft("First steps", new Point(WORLD, 0, 64, 20), List.of(
                new JourneyRouteDraft.Step("Walk", "", ObjectiveKind.VISIT, welcome, 3, "", 1, List.of(), 0),
                new JourneyRouteDraft.Step("Open", "", ObjectiveKind.OPEN_PYLON, pylon, 4, "", 1, List.of(), 1),
                new JourneyRouteDraft.Step("Collect", "", ObjectiveKind.ITEM, pickup, 3, "minecraft:iron_ingot", 2, List.of(), 4)));

        var trial = new JourneyTrial(JourneyRouteDraft.parse(draft.write()), 0);
        require(!trial.interact(pylon) && trial.index() == 0, "later action cannot skip the first step");
        require(!trial.visit("minecraft:the_nether", 12.5, 64, 20.5), "same coordinates in a different dimension do not count");
        require(!trial.visit(WORLD, 12.5, 69, 20.5), "different floor does not count");
        require(trial.visit(WORLD, 12.5, 64, 20.5) && trial.index() == 1, "walking into the first goal advances the draft");
        require(!trial.visit(WORLD, 30.5, 64, 20.5), "visiting the pylon does not simulate opening it");
        require(!trial.interact(new Point(WORLD, 29, 64, 20)), "a neighboring block is not the bound target");
        require(trial.interact(pylon) && trial.index() == 2, "the exact target advances the interaction step");
        require(!trial.simulateItem(WORLD, 30.5, 64, 20.5), "the item preview requires reaching its destination");
        require(trial.simulateItem(WORLD, 44.5, 64, 20.5) && trial.current() == null, "the entire draft completes");
        require(!trial.simulateItem(WORLD, 44.5, 64, 20.5), "completion cannot be repeated");
        var repeat = new JourneyTrial(draft, 1);
        require(repeat.index() == 1 && repeat.current().point().equals(pylon), "a second test can begin at a selected step");
        require(trial.current() == null && draft.steps().size() == 3, "a new test does not mutate the old run or the draft");
        System.out.println("PASS: 12 draft walkthrough checks");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
