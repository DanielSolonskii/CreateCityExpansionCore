package com.example.examplemod.client.journey;

import com.example.examplemod.journey.JourneyModel.ObjectiveKind;
import com.example.examplemod.journey.JourneyModel.Point;
import com.example.examplemod.journey.JourneyRouteDraft;
import java.util.List;

/** Local progress for a single immutable draft. No production quest state is used here. */
public final class JourneyTrial {
    private final List<JourneyRouteDraft.Step> steps;
    private int index;

    public JourneyTrial(JourneyRouteDraft draft, int firstStep) {
        draft.validate();
        steps = List.copyOf(draft.steps());
        if (firstStep < 0 || firstStep >= steps.size()) throw new IllegalArgumentException("Invalid step");
        index = firstStep;
    }

    public int index() { return index; }
    public int total() { return steps.size(); }
    public JourneyRouteDraft.Step current() { return index < steps.size() ? steps.get(index) : null; }

    public boolean visit(String dimension, double x, double y, double z) {
        var step = current();
        if (step == null || step.type() != ObjectiveKind.VISIT || !within(step, dimension, x, y, z)) return false;
        index++;
        return true;
    }

    public boolean interact(Point target) {
        var step = current();
        if (step == null || !step.point().equals(target)) return false;
        if (step.type() != ObjectiveKind.INTERACT && step.type() != ObjectiveKind.OPEN_PYLON) return false;
        index++;
        return true;
    }

    public boolean simulateItem(String dimension, double x, double y, double z) {
        var step = current();
        if (step == null || step.type() != ObjectiveKind.ITEM || !within(step, dimension, x, y, z)) return false;
        index++;
        return true;
    }

    private static boolean within(JourneyRouteDraft.Step step, String dimension, double x, double y, double z) {
        return step.point().distance(dimension, x, y, z) <= step.radius();
    }
}
