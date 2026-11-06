package com.example.examplemod.cargo.center;
import net.minecraft.nbt.*;

/** Functional form schema. Static labels are translated on the client. */
public final class CenterForms {
    private CenterForms() {}
    static ListTag forms(String section,CompoundTag selected){var list=new ListTag();
        if(selected!=null&&selected.getString("Kind").equals("shipment")){if(selected.getBoolean("CanCancel"))add(list,"SHIPMENT_CANCEL",true);return list;}
        if(section.equals("trains"))add(list,"TRAIN_REGISTER",false,"Train@NativeTrains","Owner@Owners","Access=PRIVATE|PUBLIC|RESTRICTED");
        if(section.equals("points"))add(list,"POINT_CREATE",false,"Name","Owner@Owners","Access=PRIVATE|PUBLIC|RESTRICTED");
        if(section.equals("warehouses"))add(list,"WAREHOUSE_REGISTER",false,"Name","Point@Points","Owner@Owners");
        if(section.equals("lines"))add(list,"LINE_CREATE",false,"Name","Owner@Owners","FirstStation@Stations","LastStation@Stations","Train@Trains","Mode=REGULAR|CHARTER","Tariff#0","Terms");
        if(section.equals("lines")||section.equals("map"))add(list,"ROUTE_PREVIEW",false,"SourcePoint@Points","DestinationPoint@Points","Preference=CHEAPEST|FEWEST_TRANSFERS");
        if(selected!=null&&selected.getString("Kind").equals("offer"))add(list,"BUY",true,"Quantity#1","Destination@Warehouses","Owner@Owners","Preference=CHEAPEST|FEWEST_TRANSFERS","Service=ANY|REGULAR|CHARTER");
        if(selected!=null&&selected.getString("Kind").equals("warehouse"))add(list,"SEND",true,"Stock@Stock","Quantity#1","Destination@Warehouses","Owner@Owners","Preference=CHEAPEST|FEWEST_TRANSFERS");
        if(selected==null||!selected.getBoolean("Manage"))return list;
        String kind=selected.getString("Kind");if(!kind.equals("line")&&!kind.equals("offer"))add(list,"RENAME",true,"Name");
        add(list,"ACCESS",true,"Owner@Owners","Access=PRIVATE|PUBLIC|RESTRICTED");add(list,"ACL_ADD",true,"Subject@Subjects");add(list,"ACL_REMOVE",true,"Subject@Allowed");
        if(kind.equals("point")){add(list,"POINT_BIND",true);add(list,"POINT_UNBIND",true,"Router@BoundRouters");add(list,"POINT_STATION",true);add(list,"STATION_REMOVE",true,"Station@BoundStations");add(list,"POINT_POSTBOX",true);add(list,"POINT_AUTOMATIC",true,"Enabled=true|false");}
        if(kind.equals("point")){add(list,"POINT_DISPATCH",true,"Router@BoundRouters","NextPoint@Points","Train@Trains");add(list,"POINT_TRANSFER",true,"Router@BoundRouters","FinalPoint@Points","NextPoint@Points");add(list,"DISPATCH_REMOVE",true,"Router@BoundRouters","NextPoint@Points");add(list,"TRANSFER_REMOVE",true,"Router@BoundRouters","FinalPoint@Points");}
        if(kind.equals("warehouse")){add(list,"WAREHOUSE_RECEIVER",true);if(selected.contains("Receiver")&&!selected.getString("Receiver").equals("NOT_CONFIGURED"))add(list,"WAREHOUSE_RECEIVER_REMOVE",true);add(list,"PUBLISH",true,"Stock@Stock","Quantity#1","Price#1","Access=PUBLIC|PRIVATE|RESTRICTED");}
        if(kind.equals("line")){add(list,"LINE_SCHEDULE",true,"WaitSeconds#15");if(selected.getString("Mode").equals("CHARTER"))add(list,"LINE_CHARTER",true,"Enabled=true|false","Return=STAY|RETURN");add(list,"LINE_EDIT",true,"Name","Mode=REGULAR|CHARTER","Tariff#0","Terms","Enabled=true|false");add(list,"LINE_STOP_ADD",true,"Station@Stations");add(list,"LINE_STOP_REMOVE",true,"Station@Stations");add(list,"LINE_TRAIN_ADD",true,"Train@Trains");add(list,"LINE_TRAIN_REMOVE",true,"Train@Trains");}
        if(kind.equals("offer"))add(list,"OFFER_CLOSE",true);else add(list,"REMOVE",true);return list;
    }
    static void add(ListTag list,String action,boolean selected,String... fields){var t=new CompoundTag();t.putString("Action",action);t.putBoolean("Selected",selected);var f=new ListTag();for(String field:fields){var v=new CompoundTag();String[] p=field.split("[@=#]",2);v.putString("Key",p[0]);v.putString("Kind",field.contains("@")?"choice":field.contains("=")?"enum":field.contains("#")?"number":"text");if(p.length==2)v.putString("Source",p[1]);f.add(v);}t.put("Fields",f);list.add(t);}
}
