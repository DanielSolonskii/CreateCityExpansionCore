package com.example.examplemod.client.map;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/** A HUD owns one panel, scoped to the current client level instance. All callbacks run on the client thread. */
final class HudPanelLifecycle<T> {
    static final long IDLE_MILLIS = 30_000;
    private final Consumer<T> release;
    private final Runnable resetScope;
    private WeakReference<Object> level = new WeakReference<>(null);
    private boolean active;
    private boolean showing;
    private long hiddenAt;
    private T panel;

    HudPanelLifecycle(Consumer<T> release, Runnable resetScope) {
        this.release = release;
        this.resetScope = resetScope;
    }

    void update(Object currentLevel, boolean enabled, boolean visible, long now) {
        if (!enabled || currentLevel == null) {
            close();
            return;
        }
        if (!active || level.get() != currentLevel) {
            close();
            level = new WeakReference<>(currentLevel);
            active = true;
            hiddenAt = now;
        }
        if (showing && !visible) {
            hiddenAt = now;
            if (panel != null) MapDiagnostics.suspended();
        }
        if (!showing && visible) MapDiagnostics.opened();
        showing = visible;
        if (!showing && now - hiddenAt >= IDLE_MILLIS) releasePanel();
    }

    boolean visibleFor(Object currentLevel) {
        return active && showing && level.get() == currentLevel;
    }

    T panel() { return panel; }

    void install(T value) {
        if (!showing || value == null) throw new IllegalStateException("HUD panel has no visible owner");
        releasePanel();
        panel = value;
        MapDiagnostics.created();
    }

    private void releasePanel() {
        if (panel == null) return;
        release.accept(panel);
        panel = null;
        MapDiagnostics.released();
    }

    void close() {
        if (!active) return;
        releasePanel();
        level.clear();
        showing = active = false;
        resetScope.run();
    }
}
