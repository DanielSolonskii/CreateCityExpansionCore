package com.example.examplemod.client.screen;

import com.example.examplemod.projects.EngineeringPlan;
import java.util.ArrayDeque;

/** A history belongs to one working copy. Replacing the document drops both stacks. */
final class PlanHistory {
    static final int LIMIT=40;
    private EngineeringPlan current;
    private final ArrayDeque<EngineeringPlan> undo=new ArrayDeque<>(), redo=new ArrayDeque<>();
    PlanHistory(EngineeringPlan initial) { current=initial; }
    EngineeringPlan current(){return current;}
    boolean change(EngineeringPlan next) {
        if (!current.id().equals(next.id()) || !current.dimension().equals(next.dimension())) throw new IllegalArgumentException("document");
        if (next.equals(current)) return false;
        if(undo.size()>=LIMIT)undo.removeFirst();
        undo.addLast(current); redo.clear(); current=next; return true;
    }
    boolean undo() {if(undo.isEmpty())return false;redo.addLast(current);current=undo.removeLast();return true;}
    boolean redo() {if(redo.isEmpty())return false;undo.addLast(current);current=redo.removeLast();return true;}
}
