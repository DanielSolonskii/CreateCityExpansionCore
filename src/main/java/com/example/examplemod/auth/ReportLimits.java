package com.example.examplemod.auth;
import java.util.*;
import java.util.function.LongSupplier;
/** Bounded local anti-spam; Backend additionally enforces durable account limits. */
public final class ReportLimits {
    private final Map<UUID,Long> next=new HashMap<>(); private final LongSupplier clock;
    public ReportLimits(LongSupplier clock){this.clock=clock;}
    public static boolean valid(String text){return text!=null && text.codePointCount(0,text.length())>=5 && text.length()<=1000 && text.equals(text.strip()) && text.chars().noneMatch(Character::isISOControl);}
    public synchronized boolean allow(UUID player){long now=clock.getAsLong();next.values().removeIf(t->t<=now);if(next.size()>=1024 || next.containsKey(player))return false;next.put(player,now+60_000_000_000L);return true;}
}
