package com.example.examplemod.client.logistics;

import java.util.*;
import java.util.function.Function;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;

/** Presentation only: protocol identifiers are never treated as player-facing sentences. */
public final class CenterText {
    private CenterText() {}
    public record Field(String label,String value,int icon) {}
    private static String t(String key,Function<String,String> language){return language.apply("ccec.center."+key);}
    public static String value(String code,Function<String,String> language) {
        if(code.isBlank())return "—";
        for(String group:List.of("value.","status.")) {
            String key="ccec.center."+group+code;String translated=language.apply(key);
            if(!translated.equals(key))return translated;
        }
        return t("unknown_state",language);
    }
    public static String name(CompoundTag row,Function<String,String> language) {
        String name=row.getString("Name");
        if(row.contains("ItemKey")){String key=row.getString("ItemKey"),translated=language.apply(key);if(!key.equals(translated))name=translated;}
        if(row.contains("ItemCount"))name+=" × "+row.getInt("ItemCount");
        else if(row.contains("Count"))name+=" × "+row.getInt("Count");
        if(row.contains("OwnerKind"))name+=" · "+value(row.getString("OwnerKind"),language);
        return name.isBlank()?t("missing_name",language):name;
    }
    public static String reason(String raw,Function<String,String> language) {
        String key=switch(raw) {
            case "Unexpected first parcel observation"->"parcel_origin";
            case "Unexpected parcel contents","Parcel contents changed"->"parcel_contents";
            case "Physical quantity mismatch"->"parcel_quantity";
            case "Receiving warehouse ownership changed","Warehouse ownership changed before packaging","Ownership changed"->"ownership_changed";
            case "Create request interrupted; no automatic retry","Create request interrupted"->"request_interrupted";
            default->null;
        };
        if(key!=null)return t("reason."+key,language);
        String translated=value(raw,language);
        return translated.equals(t("unknown_state",language))?t("history_unknown",language):translated;
    }
    public static String history(String raw,Function<String,String> language) {
        if(raw.isBlank())return "";
        int space=raw.indexOf(' ');String code=(space<0?raw:raw.substring(0,space)).replace(":","");
        String rest=space<0?"":raw.substring(space+1);
        if(code.equals("BLOCKED")||code.equals("FAILED"))return value(code,language)+(rest.isBlank()?"":" · "+reason(rest,language));
        if(Set.of("REQUESTED","PACKAGED","IN_TRANSIT","TRANSSHIPMENT","WAITING_POINT","WAITING_ROUTE","WAITING_TRAIN","READY","ARRIVED","DELIVERED","COMPLETED","CANCELLED","CANCELLING","RECOVERED","UNLOADED","ORDINARY").contains(code)) {
            if(code.equals("PACKAGED")&&!rest.matches("[0-9]+/[0-9]+"))rest="";
            return value(code,language)+(rest.isBlank()?"":" · "+rest);
        }
        return reason(raw,language);
    }
    public static String summary(CompoundTag row,Function<String,String> language) {
        return switch(row.getString("Kind")) {
            case "point"->String.format(Locale.ROOT,t("point_summary",language),row.getInt("RouterCount"),row.getInt("StationCount"));
            case "warehouse"->row.getString("PointName")+" · "+value(row.getString("Receiver"),language);
            case "train"->value(row.getString("ScheduleState"),language)+(row.getString("CurrentStation").isBlank()?"":" · "+row.getString("CurrentStation"));
            case "line"->value(row.getString("Mode"),language)+" · "+row.getLong("Tariff")+" CR";
            case "offer"->row.getLong("Price")+" CR × "+row.getInt("Remaining")+" · "+row.getString("WarehouseName");
            case "shipment"->history(row.getString("Detail"),language);
            default->row.getString("Detail");
        };
    }
    public static String location(CompoundTag location,Function<String,String> language) {
        if(!location.contains("Pos",4))return "—";
        String dimension=location.getString("Dimension");
        dimension=switch(dimension){case "minecraft:overworld"->t("dimension.overworld",language);case "minecraft:the_nether"->t("dimension.nether",language);case "minecraft:the_end"->t("dimension.end",language);default->dimension;};
        return dimension+" · "+BlockPos.of(location.getLong("Pos")).toShortString();
    }
    public static List<Field> fields(CompoundTag row,Function<String,String> language) {
        var fields=new ArrayList<Field>();
        add(fields,"owner",row.getString("OwnerName"),8,language);if(!row.getString("Dispatch").isBlank())add(fields,"dispatch",value(row.getString("Dispatch"),language),2,language);
        add(fields,"state",value(row.getString("Status"),language),13,language);
        if(row.contains("Access"))add(fields,"field.Access",value(row.getString("Access"),language),8,language);
        if(row.contains("Automatic"))add(fields,"automatic",value(row.getString("Automatic"),language),11,language);
        if(row.contains("RouterCount"))add(fields,"routers_count",Integer.toString(row.getInt("RouterCount")),4,language);
        if(row.contains("StationCount"))add(fields,"stations_count",Integer.toString(row.getInt("StationCount")),3,language);
        if(row.contains("PointName"))add(fields,"field.Point",row.getString("PointName"),4,language);
        if(row.contains("Receiver"))add(fields,"receiver",value(row.getString("Receiver"),language),7,language);
        if(row.contains("ReceiverLocation"))add(fields,"receiver_location",location(row.getCompound("ReceiverLocation"),language),7,language);
        if(row.contains("ScheduleState"))add(fields,"schedule",value(row.getString("ScheduleState"),language),11,language);
        if(row.contains("ScheduleEntries"))add(fields,"schedule_stops",Integer.toString(row.getInt("ScheduleEntries")),3,language);
        if(!row.getString("CurrentStation").isBlank())add(fields,"current_station",row.getString("CurrentStation"),3,language);
        if(row.contains("Mode"))add(fields,"field.Mode",value(row.getString("Mode"),language),3,language);
        if(row.contains("Tariff"))add(fields,"field.Tariff",row.getLong("Tariff")+" CR",6,language);
        if(row.contains("LineTrainCount"))add(fields,"trains_count",Integer.toString(row.getInt("LineTrainCount")),2,language);
        if(row.contains("Price"))add(fields,"field.Price",row.getLong("Price")+" CR",6,language);
        if(row.contains("Remaining"))add(fields,"remaining",Integer.toString(row.getInt("Remaining")),7,language);
        if(row.contains("WarehouseName"))add(fields,"warehouse",row.getString("WarehouseName"),5,language);
        if(row.contains("TermsCode"))add(fields,"field.Terms",t(row.getString("TermsCode"),language),0,language);
        else add(fields,"field.Terms",row.getString("Terms"),0,language);
        if(row.contains("SourceName"))add(fields,"field.SourcePoint",row.getString("SourceName"),4,language);
        if(row.contains("DestinationName"))add(fields,"field.DestinationPoint",row.getString("DestinationName"),4,language);
        if(row.contains("Quantity"))add(fields,"field.Quantity",Integer.toString(row.getInt("Quantity")),7,language);
        if(row.contains("Packaged"))add(fields,"packaged",row.getInt("Packaged")+" / "+row.getInt("Quantity"),7,language);
        if(row.contains("Delivered"))add(fields,"delivered",row.getInt("Delivered")+" / "+row.getInt("Quantity"),5,language);
        if(row.contains("OrderState"))add(fields,"payment_state",value(row.getString("OrderState"),language),6,language);
        if(row.getString("OrderState").equals("CANCELLING"))add(fields,"cancel_reserve",t("cancel_reserve_help",language),13,language);
        if(row.contains("Recovered"))add(fields,"recovered",row.getInt("Recovered")+" / "+row.getInt("Quantity"),7,language);
        if(row.contains("TotalCr"))add(fields,"confirm_price",row.getLong("TotalCr")+" CR",6,language);
        if(row.contains("Location"))add(fields,"location",location(row.getCompound("Location"),language),1,language);
        if(row.getString("Kind").equals("shipment")&&!row.getString("Detail").isBlank())add(fields,"state_reason",history(row.getString("Detail"),language),13,language);
        for(Tag raw:row.getList("Details",10)) {
            var part=(CompoundTag)raw;
            if(row.getString("Kind").equals("shipment")){add(fields,"history",history(part.getString("Name"),language),13,language);continue;}
            String label=part.getString("Label"),value=part.getString("Name");
            if(part.contains("State"))value+=(value.isBlank()?"":" · ")+value(part.getString("State"),language);
            if(!part.getString("Detail").isBlank())value+=(value.isBlank()?"":"\n")+part.getString("Detail");
            add(fields,label.isBlank()?"details":"detail."+label,value,label.equals("train")?2:label.equals("station")?3:4,language);
        }
        if(row.contains("StockAvailable")&&!row.getBoolean("StockAvailable"))add(fields,"stock",t("stock_unavailable",language),5,language);
        for(Tag raw:row.getList("Stock",10))add(fields,"stock",name((CompoundTag)raw,language),7,language);
        if(row.getBoolean("DetailsTruncated"))add(fields,"details",t("details_truncated",language),13,language);
        return List.copyOf(fields);
    }
    private static void add(List<Field> fields,String label,String value,int icon,Function<String,String> language){if(!value.isBlank())fields.add(new Field(t(label,language),value,icon));}
}
