package com.example.examplemod.hud;

import com.example.examplemod.client.hud.QuietTabLayout;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** Registry-free regressions for city identity/colour and small-screen tab geometry. */
public final class QuietTabCheck {
    private static int checks;
    private static void require(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        String id=UUID.randomUUID().toString();
        for(int rgb:List.of(0xD0483C,0x3A78C4,0x4C7F6B)) {
            var label=TabCityLabel.create(id,"Северный порт",rgb);
            var name=Component.literal("Player").append(" · ").append(label);
            var city=TabCityLabel.read(name.copy());
            require(city!=null&&city.id().equals(id),"city UUID survives component composition and copy");
            require(city.name().equals("Северный порт")&&city.rgb()==rgb,"name and exact configured RGB retained");
            require(!name.getString().contains(id)&&name.getString().contains("Северный порт"),"metadata does not leak into visible text");
        }
        require(TabCityLabel.read(Component.literal("Player [Town]").withColor(0x00AAAA))==null,"colour and brackets cannot impersonate city metadata");
        require(TabCityLabel.read(null)==null,"players without a city are supported");
        var malformed=Component.translatable(TabCityLabel.KEY,"invalid","Town");
        require(TabCityLabel.read(malformed)==null,"malformed metadata ignored");
        for(int width:new int[]{240,320,480,640,960,1920})for(int height:new int[]{135,180,240,360,540})
            for(int count:new int[]{0,1,4,15,20,21,40,80})for(int bosses:new int[]{0,1,3,12}) {
                var l=QuietTabLayout.calculate(width,height,count,294,bosses);
                require(l.scale()>0&&l.scale()<=1,"valid scale");
                require(l.x()>=0&&l.x()+Math.ceil(l.width()*l.scale())<=width,"panel fits screen width");
                require(l.y()+Math.ceil(l.height()*l.scale())<=height,"panel fits below boss reservation");
                require(l.rows()*l.columns()>=count,"all listed players fit");
                require(l.columns()<=4,"bounded columns");
                if(bosses==1)require(l.y()>=39,"single raid title/bar clear of panel");
            }
        for(String locale:List.of("ru_ru","en_us")) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/createcityexpansioncore/lang/"+locale+".json"))).getAsJsonObject();
            require(json.get(TabCityLabel.KEY).getAsString().equals("%2$s"),"only city label rendered, UUID hidden");
            for(String suffix:List.of("header","map","logistics","online"))require(json.has("gui.createcityexpansioncore.tab."+suffix),"tab labels translated");
        }
        System.out.println("PASS: "+checks+" tab identity, RGB, component, locale and screen-boundary checks");
    }
}
