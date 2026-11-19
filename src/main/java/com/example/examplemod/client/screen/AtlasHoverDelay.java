package com.example.examplemod.client.screen;

import java.util.Objects;
import net.minecraft.network.chat.Component;

final class AtlasHoverDelay {
    private final long delayMillis;
    private Component previous;
    private long since;
    private boolean waitingForPointer;
    private int keyboardX, keyboardY;
    AtlasHoverDelay(long delayMillis) { this.delayMillis=delayMillis; }

    Component sample(Component topmost,long now) {
        if (!Objects.equals(previous,topmost)) { previous=topmost; since=now; }
        return now-since>=delayMillis ? topmost : null;
    }

    void keyboardInput(int x,int y) {
        previous=null;
        waitingForPointer=true;
        keyboardX=x;
        keyboardY=y;
    }

    Component samplePointer(Component topmost,long now,int x,int y) {
        if (waitingForPointer && x==keyboardX && y==keyboardY) return null;
        waitingForPointer=false;
        return sample(topmost,now);
    }
}
