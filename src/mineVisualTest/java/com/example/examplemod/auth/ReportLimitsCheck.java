package com.example.examplemod.auth;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
public final class ReportLimitsCheck {
    private static void check(boolean ok){if(!ok)throw new AssertionError("Report anti-spam failed");}
    public static void main(String[] args)throws Exception{
        check(ReportLimits.valid("Сломался поезд"));check(ReportLimits.valid("a".repeat(1000)));
        for(String text:new String[]{"abcd","a".repeat(1001)," test ","hello\nadmin","hello\u0000"})check(!ReportLimits.valid(text));
        var now=new AtomicLong();var limits=new ReportLimits(now::get);UUID user=UUID.randomUUID();check(limits.allow(user));check(!limits.allow(user));
        now.set(60_000_000_000L);check(limits.allow(user));
        var race=new ReportLimits(now::get);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{start.await();return race.allow(user);});var b=pool.submit(()->{start.await();return race.allow(user);});start.countDown();check(a.get()!=b.get());}
        System.out.println("PASS report text bounds, cooldown, expiry and concurrent reservation");
    }
}
