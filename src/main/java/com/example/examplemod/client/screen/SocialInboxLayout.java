package com.example.examplemod.client.screen;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/** Logical rows shared by notification rendering, scrolling and hit testing. */
final class SocialInboxLayout {
    enum Group {
        CITY, FRIENDS, MESSAGES, PROJECTS, ALLIANCE, OTHER;

        static Group forEvent(CompoundTag event) {
            String kind = event.getString("Kind");
            if (event.hasUUID("Project") || kind.startsWith("project_")) return PROJECTS;
            if (event.hasUUID("Alliance") || kind.startsWith("alliance_") || kind.startsWith("council_")) return ALLIANCE;
            if (event.hasUUID("City") || kind.startsWith("city_")) return CITY;
            if (kind.startsWith("friend_")) return FRIENDS;
            if (kind.equals("message")) return MESSAGES;
            return OTHER;
        }
    }

    enum Kind { GROUP, EVENT, READ_TOGGLE }

    record Row(String key, Kind kind, Group group, CompoundTag event, int count, int top, int height) {}

    private static final int GAP = 6;

    static List<Row> rows(List<CompoundTag> events, boolean showRead) {
        var unread = new EnumMap<Group, List<CompoundTag>>(Group.class);
        var read = new EnumMap<Group, List<CompoundTag>>(Group.class);
        int readCount = 0;
        for (var event : events) {
            boolean seen = event.getBoolean("Read");
            if (seen) readCount++;
            (seen ? read : unread).computeIfAbsent(Group.forEvent(event), ignored -> new ArrayList<>()).add(event);
        }
        var rows = new ArrayList<Row>();
        appendGroups(rows, unread, "unread");
        if (readCount > 0) {
            rows.add(new Row("read", Kind.READ_TOGGLE, null, null, readCount, height(rows), 26));
            if (showRead) appendGroups(rows, read, "read");
        }
        return List.copyOf(rows);
    }

    private static void appendGroups(List<Row> rows, EnumMap<Group, List<CompoundTag>> groups, String prefix) {
        for (var entry : groups.entrySet()) {
            var group = entry.getKey();
            var events = entry.getValue();
            rows.add(new Row(prefix + "." + group, Kind.GROUP, group, null, events.size(), height(rows), 21));
            events.sort(Comparator.comparingLong((CompoundTag event) -> event.getLong("Time")).reversed());
            for (var event : events) {
                rows.add(new Row("event." + event.getUUID("Id"), Kind.EVENT, group, event, 0, height(rows), 49));
            }
        }
    }

    static int height(List<Row> rows) {
        if (rows.isEmpty()) return 0;
        var last = rows.getLast();
        return last.top() + last.height() + GAP;
    }
}
