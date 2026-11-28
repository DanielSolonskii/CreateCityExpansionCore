package com.example.examplemod.client.screen;

import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Traversal is rebuilt from the controls of the visible surface after each layout pass. */
final class AtlasFocus {
    record Control(String id, Component name, Component hint, WorldMapUiLayout.Rect bounds, Runnable activate) {}
    private final List<Control> visible = new ArrayList<>();
    private String id;
    void begin() { visible.clear(); }
    void reset() { id=null; }
    void add(String key,Component name,Component hint,WorldMapUiLayout.Rect bounds,Runnable activate) {
        if (bounds.width()>0 && bounds.height()>0) visible.add(new Control(key,name,hint,bounds,activate));
    }
    Control current() {
        if (id!=null) for (Control control:visible) if (id.equals(control.id())) return control;
        return null;
    }
    Control move(boolean backwards) {
        if (visible.isEmpty()) { reset(); return null; }
        int start=visible.indexOf(current());
        int next=start<0 ? (backwards?visible.size()-1:0) : Math.floorMod(start+(backwards?-1:1),visible.size());
        Control control=visible.get(next);
        id=control.id();
        return control;
    }
}
