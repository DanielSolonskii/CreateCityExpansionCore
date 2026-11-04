package com.example.examplemod.cargo.center;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Pure policy for exact-name Create destinations and ownership of generated schedules. */
public final class CargoSchedulePlan {
    private CargoSchedulePlan() {}
    public static List<String> cycle(List<String> stops,int seconds) {
        if(stops.size()<2||stops.size()>32||seconds<10||seconds>300)throw new IllegalArgumentException("schedule_conditions");
        for(String name:stops)if(name==null||name.isBlank()||name.length()>256||name.indexOf('*')>=0||name.indexOf('?')>=0)
            throw new IllegalArgumentException("schedule_station_name");
        if(new HashSet<>(stops).size()!=stops.size())throw new IllegalArgumentException("schedule_station_name");
        var result=new ArrayList<>(stops);
        // Return via intermediate stops. The first stop is supplied by the next cycle, not duplicated.
        for(int i=stops.size()-2;i>0;i--)result.add(stops.get(i));
        return List.copyOf(result);
    }
    public static CompoundTag shape(CompoundTag value) {var copy=value.copy();copy.remove("Progress");return copy;}
    /** Create uses glob syntax, so punctuation in a real station name must be escaped. */
    public static String filter(String name) {
        var result=new StringBuilder();for(char c:name.toCharArray()){if("\\*?[{".indexOf(c)>=0)result.append('\\');result.append(c);}return result.toString();
    }
    public static boolean replaceable(CompoundTag current,CompoundTag generated,boolean automatic) {
        return current==null||automatic&&generated!=null&&shape(current).equals(shape(generated));
    }
}
