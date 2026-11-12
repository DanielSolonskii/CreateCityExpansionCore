package com.example.examplemod.journey;

import java.util.ArrayList;
import java.util.List;

import com.example.examplemod.journey.JourneyModel.ObjectiveKind;
import com.example.examplemod.journey.JourneyModel.Point;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Shared authoring format. Drafts can be incomplete until explicitly checked or published. */
public record JourneyRouteDraft(String title, Point start, List<Step> steps) {
    public static final int MAX_STEPS = 12;
    public static final int MAX_WAYPOINTS = 48;
    public static final int MAX_TITLE = 96;
    public static final int MAX_DESCRIPTION = 256;

    public JourneyRouteDraft {
        steps = List.copyOf(steps);
    }

    public record Step(String title, String description, ObjectiveKind type, Point point, int radius,
                       String item, int count, List<Point> route, int icon) {
        public Step {
            route = List.copyOf(route);
        }
    }

    /** The step number is one-based, or zero for route-level errors. */
    public static final class Failure extends IllegalArgumentException {
        private final int step;

        public Failure(String code, int step) {
            super(code);
            this.step = step;
        }

        public String code() { return getMessage(); }
        public int step() { return step; }
    }

    public static JourneyRouteDraft parse(CompoundTag tag) {
        String title = tag.getString("Title").strip();
        checkText(title, MAX_TITLE, false, 0);
        Point start = tag.contains("Start", Tag.TAG_COMPOUND) ? point(tag.getCompound("Start"), 0) : null;
        var rawSteps = tag.getList("Steps", Tag.TAG_COMPOUND);
        require(rawSteps.size() <= MAX_STEPS, "limit", 0);

        var steps = new ArrayList<Step>(rawSteps.size());
        int waypoints = 0;
        for (int i = 0; i < rawSteps.size(); i++) {
            CompoundTag raw = rawSteps.getCompound(i);
            int stepNumber = i + 1;
            String stepTitle = raw.getString("Title").strip();
            String description = raw.getString("Description").strip();
            checkText(stepTitle, MAX_TITLE, false, stepNumber);
            checkText(description, MAX_DESCRIPTION, false, stepNumber);
            ObjectiveKind type = readType(raw.getString("Type"), stepNumber);
            Point point = point(raw, stepNumber);
            int radius = raw.contains("Radius", Tag.TAG_ANY_NUMERIC) ? raw.getInt("Radius") : 4;
            int count = raw.contains("Count", Tag.TAG_ANY_NUMERIC) ? raw.getInt("Count") : 1;
            int icon = raw.contains("Icon", Tag.TAG_ANY_NUMERIC) ? raw.getInt("Icon") : defaultIcon(type);
            String item = raw.getString("Item").strip();
            if (type != ObjectiveKind.ITEM) {
                item = "";
                count = 1;
            }
            require(radius >= 1 && radius <= 64 && count >= 1 && count <= 4096 && icon >= 0 && icon <= 7,
                    "range", stepNumber);
            checkText(item, 128, false, stepNumber);

            var rawRoute = raw.getList("Route", Tag.TAG_COMPOUND);
            waypoints += rawRoute.size();
            require(waypoints <= MAX_WAYPOINTS, "limit", stepNumber);
            var route = new ArrayList<Point>(rawRoute.size());
            for (int j = 0; j < rawRoute.size(); j++) {
                Point waypoint = point(rawRoute.getCompound(j), stepNumber);
                require(waypoint != null, "location", stepNumber);
                route.add(waypoint);
            }
            steps.add(new Step(stepTitle, description, type, point, radius, item, count, route, icon));
        }
        return new JourneyRouteDraft(title, start, steps);
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("Title", title);
        if (start != null) tag.put("Start", JourneyCodec.point(start));
        var entries = new ListTag();
        for (Step step : steps) {
            CompoundTag entry = step.point() == null ? new CompoundTag() : JourneyCodec.point(step.point());
            entry.putString("Title", step.title());
            entry.putString("Description", step.description());
            entry.putString("Type", step.type().name());
            entry.putInt("Radius", step.radius());
            entry.putString("Item", step.item());
            entry.putInt("Count", step.count());
            entry.putInt("Icon", step.icon());
            var route = new ListTag();
            step.route().forEach(waypoint -> route.add(JourneyCodec.point(waypoint)));
            entry.put("Route", route);
            entries.add(entry);
        }
        tag.put("Steps", entries);
        return tag;
    }

    /** Structural validation shared by the local preview and server. World checks run separately. */
    public void validate() {
        checkText(title, MAX_TITLE, true, 0);
        require(!steps.isEmpty() && steps.size() <= MAX_STEPS, "steps", 0);
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            int stepNumber = i + 1;
            checkText(step.title(), MAX_TITLE, true, stepNumber);
            require(step.point() != null, "location", stepNumber);
            String dimension = step.point().dimension();
            require(step.route().stream().allMatch(waypoint -> waypoint.dimension().equals(dimension)),
                    "dimension", stepNumber);
            if (step.type() == ObjectiveKind.ITEM) {
                require(!step.item().isBlank() && ResourceLocation.tryParse(step.item()) != null,
                        "item", stepNumber);
            }
        }
    }

    private static Point point(CompoundTag tag, int step) {
        String dimension = tag.getString("Dimension");
        if (dimension.isBlank() || !tag.contains("X", Tag.TAG_ANY_NUMERIC)
                || !tag.contains("Y", Tag.TAG_ANY_NUMERIC) || !tag.contains("Z", Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        require(dimension.length() <= 128, "dimension", step);
        try {
            return JourneyCodec.point(tag);
        } catch (IllegalArgumentException invalid) {
            throw new Failure("location", step);
        }
    }

    private static ObjectiveKind readType(String type, int step) {
        if (type.isEmpty()) return ObjectiveKind.VISIT;
        return switch (type) {
            case "VISIT" -> ObjectiveKind.VISIT;
            case "INTERACT" -> ObjectiveKind.INTERACT;
            case "OPEN_PYLON" -> ObjectiveKind.OPEN_PYLON;
            case "ITEM" -> ObjectiveKind.ITEM;
            default -> throw new Failure("route_type", step);
        };
    }

    private static int defaultIcon(ObjectiveKind type) {
        return switch (type) {
            case VISIT -> 0;
            case ITEM -> 4;
            default -> 1;
        };
    }

    private static void checkText(String value, int max, boolean required, int step) {
        require(value.length() <= max && (!required || !value.isBlank())
                && value.chars().noneMatch(c -> c == 0 || c == 167), "text", step);
    }

    static void require(boolean condition, String error, int step) {
        if (!condition) throw new Failure(error, step);
    }
}
