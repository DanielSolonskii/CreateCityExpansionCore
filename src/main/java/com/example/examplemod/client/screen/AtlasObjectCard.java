package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.CityMapPanel.SelectionKind;
import java.util.ArrayList;
import java.util.List;

/** The same action identities drive pointer, keyboard and narration. */
final class AtlasObjectCard {
    enum Action { TRACK, STOP, PROJECT, CARGO, CENTER, REMOVE_PIN, EDIT_CITY, ALLIANCE, RELATIONS,
        DIRECTIONS, DIRECT, ORIGIN_STATION, DESTINATION_STATION, BACK_TO_OBJECT, INSPECT_SHIPMENT,
        ROUTE, FROM, EDIT_PLACE, FAVORITE_PLACE, UNFAVORITE_PLACE, SHARE_PLACE, LINK_PLACE }
    record Row(Action action, String unavailable, com.example.examplemod.client.logistics.StationDirections.Landmark station) {
        Row(Action action, String unavailable) { this(action, unavailable, null); }
        boolean enabled() { return unavailable.isEmpty(); }
    }

    static List<Row> rows(SelectionKind kind, boolean identifiable, boolean trackable, boolean tracked,
                          boolean editable, boolean ownManagement) {
        List<Row> rows = new ArrayList<>();
        if (kind == SelectionKind.PROJECT) rows.add(new Row(Action.PROJECT, identifiable ? "" : "unavailable"));
        if (kind == SelectionKind.LOGISTICS) rows.add(new Row(Action.CARGO, identifiable ? "" : "unavailable"));
        if (kind != SelectionKind.PLAYER) rows.add(new Row(tracked ? Action.STOP : Action.TRACK,
                tracked || trackable ? "" : "target_unavailable"));
        if (kind == SelectionKind.PLACE) rows.addFirst(new Row(Action.ROUTE,""));
        else if(kind != SelectionKind.PLAYER)rows.add(new Row(Action.ROUTE,""));
        rows.add(new Row(Action.FROM,""));
        if (kind != SelectionKind.PLAYER) rows.add(new Row(Action.DIRECTIONS, ""));
        rows.add(new Row(Action.CENTER, ""));
        if (kind == SelectionKind.WAYPOINT) rows.add(new Row(Action.REMOVE_PIN, ""));
        if (kind == SelectionKind.CITY) {
            rows.add(new Row(Action.ALLIANCE, identifiable ? "" : "unavailable"));
            rows.add(new Row(Action.EDIT_CITY, editable ? "" : "permission"));
            if (ownManagement) rows.add(new Row(Action.RELATIONS, ""));
        }
        return List.copyOf(rows);
    }
    private AtlasObjectCard() {}
}
