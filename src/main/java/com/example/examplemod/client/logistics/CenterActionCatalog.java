package com.example.examplemod.client.logistics;

/** Shared action grouping and semantic icons, independent of fonts and the game renderer. */
public final class CenterActionCatalog {
    private CenterActionCatalog() {}
    public static int group(String action) {
        if(action.startsWith("COMPANY_")){
            if(action.equals("COMPANY_CREATE")||action.endsWith("ASSET_REGISTER"))return 0;
            if(action.contains("CANCEL")||action.endsWith("_END")||action.endsWith("_REMOVE"))return 4;
            if(action.contains("INVITE")||action.contains("EMPLOYEE")||action.contains("SALARY"))return 3;
            if(action.contains("SHARE")||action.contains("CONTRACT")||action.contains("DEPOSIT")||action.contains("WITHDRAW"))return 1;
        }
        if(action.endsWith("_CLOSE")||action.endsWith("_DISABLE")||action.endsWith("_ABANDON"))return 4;
        if(action.equals("ROAD_REGISTER")||action.equals("FLEET_PUBLISH")||action.equals("MASTER_BIND"))return 0;
        if(action.equals("DEBT_PAY")||action.equals("CONTRACT_SUPPLY"))return 1;
        if(action.equals("REMOVE")||action.endsWith("_REMOVE")||action.equals("POINT_UNBIND")||action.equals("SHIPMENT_CANCEL"))return 4;
        if(action.equals("ACCESS")||action.startsWith("ACL_"))return 3;
        if(action.equals("TRAIN_REGISTER")||action.equals("POINT_CREATE")||action.equals("WAREHOUSE_REGISTER")||action.equals("LINE_CREATE"))return 0;
        if(action.equals("BUY")||action.equals("SEND")||action.equals("PUBLISH")||action.equals("OFFER_CLOSE")||action.equals("ROUTE_PREVIEW")||action.equals("LINE_SCHEDULE"))return 1;
        return 2;
    }
    public static int icon(String action) {
        if(action.startsWith("ROAD_"))return 3;
        if(action.startsWith("FLEET_"))return 2;
        if(action.startsWith("MASTER_"))return 13;
        if(action.equals("DEBT_PAY")||action.contains("SHARE")||action.contains("DEPOSIT")||action.contains("WITHDRAW")||action.equals("COMPANY_TAX"))return 6;
        if(action.contains("CONTRACT"))return 7;
        if(action.startsWith("COMPANY_"))return 12;
        if(action.equals("LINE_SCHEDULE"))return 11;
        if(action.equals("REMOVE")||action.equals("SHIPMENT_CANCEL"))return 9;
        if(action.endsWith("_REMOVE")||action.equals("POINT_UNBIND"))return 10;
        if(action.equals("ACCESS")||action.startsWith("ACL_"))return 8;
        if(action.equals("BUY")||action.equals("PUBLISH")||action.equals("OFFER_CLOSE"))return 6;
        if(action.equals("SEND")||action.equals("WAREHOUSE_RECEIVER"))return 7;
        if(action.startsWith("TRAIN_"))return 2;
        if(action.startsWith("LINE_")||action.equals("ROUTE_PREVIEW"))return 3;
        if(action.startsWith("POINT_"))return 4;
        if(action.startsWith("WAREHOUSE_"))return 5;
        return 12;
    }
    public static int columns(int width){return width>=640?2:1;}
    public static int buttonWidth(int width){return (Math.min(680,width-24)-24-(columns(width)-1)*10)/columns(width);}
}
