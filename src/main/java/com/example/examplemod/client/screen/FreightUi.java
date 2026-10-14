package com.example.examplemod.client.screen;

import com.example.examplemod.economy.FreightPoint;
import com.example.examplemod.network.EconomyStatePayload;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

final class FreightUi {
    static Component tr(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.freight."+key,args);}
    static List<FreightPoint> points(EconomyStatePayload s){
        if(s==null)return List.of();List<FreightPoint> result=new ArrayList<>();
        for(Tag t:s.logistics().getList("Points",Tag.TAG_COMPOUND))result.add(FreightPoint.load((CompoundTag)t));return result;
    }
    static FreightPoint point(EconomyStatePayload s,UUID id){return points(s).stream().filter(p->p.id.equals(id)).findFirst().orElse(null);}
    static List<FreightPoint> choices(EconomyStatePayload s,boolean receiving){
        if(s==null)return List.of();
        return points(s).stream().filter(p->p.cityId.equals(s.cityId())&&p.enabled&&!p.removed&&(receiving?p.mode.receives():p.mode.sends())).sorted(Comparator.comparing(p->receiving?!p.defaultReceive:!p.defaultSend)).toList();
    }
    /** Unified status card for a point; the raw status string never reaches the player. */
    static com.example.examplemod.status.CcecStatus status(FreightPoint p){
        return com.example.examplemod.economy.FreightPointRules.ui(p.status);
    }
    static UUID destination(EconomyStatePayload s,UUID subject,UUID fallback){
        for(Tag raw:s.logistics().getList("Assignments",Tag.TAG_COMPOUND)){CompoundTag a=(CompoundTag)raw;if(a.getUUID("Subject").equals(subject))return a.getUUID("Destination");}return fallback;
    }
    static String route(EconomyStatePayload s,UUID source,UUID destination){
        for(Tag raw:s.logistics().getList("Routes",Tag.TAG_COMPOUND)){CompoundTag r=(CompoundTag)raw;if(r.getUUID("Source").equals(source)&&r.getUUID("Destination").equals(destination))return r.getString("State");}return "unknown";
    }
}
