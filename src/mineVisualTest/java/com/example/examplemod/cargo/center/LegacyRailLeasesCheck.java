package com.example.examplemod.cargo.center;

import java.util.*;

/** Migration exemptions are bounded to one active train, paid phase and physical corridor. */
public final class LegacyRailLeasesCheck {
    private static int checks;
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        UUID train=UUID.randomUUID(),other=UUID.randomUUID();var cache=new LegacyRailLeases.PhaseCache<String>();int[] rebuilds={0},plans={0};
        var live=Map.of(train,List.of("old-delivery"));cache.refresh(100,()->{rebuilds[0]++;return live;});
        for(int i=0;i<1000;i++)cache.refresh(100,()->{throw new AssertionError("same tick rebuild");});require(rebuilds[0]==1,"many physical movement events build one active-order index per server tick");
        require(cache.keys(other).isEmpty()&&cache.keys(train).equals(List.of("old-delivery")),"a paid lease cannot transfer to an unrelated train");
        java.util.function.Supplier<List<RailGeometry.Span>> plan=()->{plans[0]++;return List.of(new RailGeometry.Span("main",0,100));};
        require(!cache.consume("old-delivery",plan,new RailGeometry.Span("detour",0,.001)),"a sub-centimetre movement on another road is never a prepaid pass");
        require(!cache.consume("old-delivery",plan,new RailGeometry.Span("main",100,.001+100)),"tiny movement outside paid interval is not rounded into a free pass");
        require(cache.consume("old-delivery",plan,new RailGeometry.Span("main",0,1)),"one actual paid metre consumes the old corridor");
        require(!cache.consume("old-delivery",plan,new RailGeometry.Span("main",0,1)),"extra loop or reversal cannot reuse a consumed old metre");
        for(int i=1;i<100;i++)require(cache.consume("old-delivery",plan,new RailGeometry.Span("main",i,i+1)),"subsequent actual corridor metre remains paid");
        require(plans[0]==1,"station path is frozen once per paid phase, never replanned per movement");
        cache.refresh(101,()->{rebuilds[0]++;return live;});require(!cache.consume("old-delivery",plan,new RailGeometry.Span("main",20,21))&&plans[0]==1,"next tick cannot regenerate consumed exemption or replan its path");
        cache.refresh(102,()->Map.of(train,List.of("old-return")));require(cache.frozenCount()==0,"phase transition retires the finished delivery geometry");require(cache.consume("old-return",plan,new RailGeometry.Span("main",99,100)),"genuine return has a separate prepaid phase corridor");
        cache.refresh(103,Map::of);require(cache.frozenCount()==0&&cache.keys(train).isEmpty()&&!cache.consume("old-return",plan,new RailGeometry.Span("main",98,99)),"finished legacy return leaves no permanent exemption");
        require(!LegacyRailLeases.retire(CargoCommerceBook.State.SETTLED,true),"settled goods do not retire an actual unfinished charter return");require(LegacyRailLeases.retire(CargoCommerceBook.State.SETTLED,false),"settled old order retires after return is DONE");require(LegacyRailLeases.retire(CargoCommerceBook.State.REFUNDED,false)&&LegacyRailLeases.retire(CargoCommerceBook.State.CANCELLED,false),"closed/refunded requests have no residual free road lease");require(!LegacyRailLeases.retire(CargoCommerceBook.State.ATTEMPTED,false)&&!LegacyRailLeases.retire(null,false),"unresolved orders are retained for reconciliation rather than presumed completed");
        var split=new LegacyRailLeases.PhaseCache<String>();split.refresh(1,()->Map.of(train,List.of("split")));require(!split.consume("split",()->List.of(new RailGeometry.Span("x",0,2),new RailGeometry.Span("x",3,5)),new RailGeometry.Span("x",1,4)),"a missing middle segment cannot broaden a paid corridor");require(split.consume("split",()->{throw new AssertionError("frozen path");},new RailGeometry.Span("x",0,2)),"rejected detour does not consume valid remaining coverage");
        System.out.println("PASS: "+checks+" legacy rail cache, phase retirement and paid corridor checks");
    }
}
