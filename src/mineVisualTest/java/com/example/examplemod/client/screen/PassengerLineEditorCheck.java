package com.example.examplemod.client.screen;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;

public final class PassengerLineEditorCheck {
    private PassengerLineEditorCheck() {}

    private static CompoundTag platform(int id,String name,String coordinates,boolean marked) {
        var row = new CompoundTag();
        row.putUUID("Id",new UUID(0,id));
        row.putString("Name",name);
        row.putString("Position",coordinates);
        row.putBoolean("Marked",marked);
        return row;
    }
    private static void require(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void run() {
        var stop = platform(1,"Вокзал","8, 64, 20",false);
        var marked = platform(2,"Ярмарка","58, 70, 21",true);
        var other = platform(3,"Вокзал","12, 69, 15",false);
        var entries = new ListTag();
        entries.add(other); entries.add(stop); entries.add(marked);
        var stops = new ListTag();
        stops.add(NbtUtils.createUUID(stop.getUUID("Id")));

        require(PassengerLinesScreen.matchingChoices(entries,stops,false,"").equals(List.of(marked,other)),
                "Marked platforms lead, the selected stop is omitted, and a namesake remains selectable");
        require(PassengerLinesScreen.matchingChoices(entries,stops,false,"  ВОКЗАЛ ").equals(List.of(other)),
                "Platform names accept case-insensitive Cyrillic and surrounding whitespace");
        require(PassengerLinesScreen.matchingChoices(entries,stops,false,"70, 21").equals(List.of(marked)),
                "Coordinates can distinguish platforms with similar names");
        require(PassengerLinesScreen.matchingChoices(entries,stops,true,"Вокзал").equals(List.of(stop,other)),
                "Choosing a train uses its own IDs and keeps namesakes stable by UUID");
        require(stops.size()==1 && entries.size()==3,"Search leaves the line and registration order intact");

        require(PassengerLinesScreen.forecastDetail("checking").equals("checking_detail"),"Measurement in progress");
        require(PassengerLinesScreen.forecastDetail("verified").equals("verified_detail"),"Measured arrivals ready");
        require(PassengerLinesScreen.forecastDetail("late").equals("late_detail"),"Predictions suspended after delay");
        for (String status : List.of("draft","changed","permission","invalid_names","future_status"))
            require(PassengerLinesScreen.forecastDetail(status).isEmpty(),"No timing promise for status "+status);
        System.out.println("Passenger line editor: ordered picker, coordinate search, train IDs and server timing states passed");
    }
}
