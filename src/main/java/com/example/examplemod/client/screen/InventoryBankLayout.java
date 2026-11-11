package com.example.examplemod.client.screen;
/** One rectangle for painting and clicks. No slot or recipe-book overlap, even at small GUI sizes. */
public final class InventoryBankLayout {
    public record Bounds(int x,int y,int width,int height,boolean compact){public boolean contains(double a,double b){return a>=x&&b>=y&&a<x+width&&b<y+height;}}
    public static Bounds market(int width,int height,int left,int top,int inventoryWidth,int inventoryHeight,boolean recipes){
        var bank=calculate(width,height,left,top,inventoryWidth,inventoryHeight,recipes);if(bank==null)return null;
        if(!bank.compact()&&bank.y()+bank.height()+28<=height)return new Bounds(bank.x(),bank.y()+bank.height()+5,bank.width(),23,true);
        if(bank.compact()&&bank.x()+bank.width()+62<=width-4)return new Bounds(bank.x()+bank.width()+4,bank.y(),58,bank.height(),true);
        return null;
    }
    public static Bounds calculate(int width,int height,int left,int top,int inventoryWidth,int inventoryHeight,boolean recipes){
        if(!recipes&&left>=120&&Math.max(4,top+4)+64<=height)return new Bounds(left-116,Math.max(4,top+4),108,64,false);
        int x=Math.clamp(left,4,Math.max(4,width-144));if(top>=30&&width>=152)return new Bounds(x,top-27,140,23,true);
        if(top+inventoryHeight+27<=height&&width>=152)return new Bounds(x,top+inventoryHeight+4,140,23,true);
        return null;
    }
}
