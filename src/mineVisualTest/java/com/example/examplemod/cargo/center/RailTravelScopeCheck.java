package com.example.examplemod.cargo.center;

import java.util.*;

/** Physical pointer scopes keep directional arrival proof independent of one stable billing meter. */
public final class RailTravelScopeCheck {
    private static int checks;
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static final class Point {
        @Override public boolean equals(Object other){return other instanceof Point;}
        @Override public int hashCode(){return 1;}
    }
    public static void main(String[] args){
        var meter=new Point();var rear=new Point();var clone=new Point();var middle=new Point();var other=new Point();
        var reverse=new RailTravelObserver.Targets<>(meter,rear);
        require(reverse.meters(meter)&&!reverse.proves(meter),"fixed first-carriage wheel remains the sole backward-trip money meter");
        require(reverse.proves(rear)&&!reverse.meters(rear),"actual rear leading wheel proves reverse arrival without billing twice");
        require(!reverse.observes(clone)&&meter.equals(clone),"position/value-equal navigation clones are excluded by object identity");
        require(!reverse.observes(null),"an absent pointer cannot become an observed wheel");
        var first=reverse.inScope(true,List.of(meter,other));var last=reverse.inScope(false,List.of(rear,other));var mid=reverse.inScope(false,List.of(middle,clone));
        require(first.meters(meter)&&!first.proves(rear),"first carriage owns only its real fixed meter in reverse travel");
        require(last.proves(rear)&&!last.meters(meter),"last carriage can hold a proof-only native scope");
        require(!mid.observes(meter)&&!mid.observes(rear)&&!mid.observes(clone),"middle-carriage and scout callbacks cannot use endpoint scopes");
        int[] money={0},proof={0};for(var point:List.of(rear,clone,other)){if(last.proves(point))proof[0]++;if(last.meters(point))money[0]++;}
        require(proof[0]==1&&money[0]==0,"proof-only callback produces no financial movement or second toll");
        var forward=new RailTravelObserver.Targets<>(meter,meter).inScope(true,List.of(meter,other));money[0]=proof[0]=0;for(var point:List.of(meter,clone,other)){if(forward.proves(point))proof[0]++;if(forward.meters(point))money[0]++;}
        require(proof[0]==1&&money[0]==1,"one shared forward endpoint supplies proof and money exactly once");
        require(!new RailTravelObserver.Targets<>(meter,meter).inScope(false,List.of(middle,other)).observes(meter),"forward shared pointer never leaks into last-carriage scope");
        System.out.println("PASS: "+checks+" exact native scope and independent proof-head checks");
    }
}
